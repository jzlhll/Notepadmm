package com.allan.atools.tools.modulenotepad.session;

import com.allan.atools.GlobalCfgStores;
import com.allan.atools.SettingPreferences;
import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorDocumentState;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.Locales;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/** 负责编辑器会话、未保存正文快照和启动恢复。 */
public final class EditorSessionManager {
    private static final String TAG = "EditorSession";
    private static final int MANIFEST_VERSION = 1;
    private static final long SNAPSHOT_DEBOUNCE_MS = 1_000L;
    private static final long SNAPSHOT_INTERVAL_MS = 120_000L;
    private static final Pattern BACKUP_NAME = Pattern.compile("^[0-9a-fA-F-]+-\\d+\\.txt$");
    private static final Pattern LEGACY_HIDDEN_NAME = Pattern.compile("^\\.temp\\d{2}_\\d{2}_\\d{2}(?:_\\d+)*\\.txt$");
    private static final EditorSessionManager INSTANCE = new EditorSessionManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path sessionDir = Path.of(System.getProperty("user.home"), ".atools_notepadmm", "session");
    private final Path backupsDir = sessionDir.resolve("backups");
    private final Path manifestFile = sessionDir.resolve("session.json");
    private final Path previousManifestFile = sessionDir.resolve("session.json.prev");
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "editor-session-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService manifestExecutor = Executors.newSingleThreadExecutor(runnable -> {
        var thread = new Thread(runnable, "editor-session-manifest");
        thread.setDaemon(true);
        return thread;
    });
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "editor-session-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, TrackedDocument> trackedDocuments = new ConcurrentHashMap<>();
    private final AtomicBoolean closing = new AtomicBoolean();
    private final Object manifestWriteLock = new Object();
    private volatile ScheduledFuture<?> manifestDebounce;
    private volatile boolean restoring = true;

    private EditorSessionManager() {
    }

    public static EditorSessionManager getInstance() {
        return INSTANCE;
    }

    public boolean isRestoring() {
        return restoring;
    }

    public void track(EditorArea area) {
        var state = area.getEditor().getDocumentState();
        var tracked = new TrackedDocument(area);
        trackedDocuments.put(state.getSessionId(), tracked);
        if (!restoring && !closing.get()) {
            scheduleManifestCommit();
        }
    }

    public void registerRestoredBackup(EditorArea area, SessionTab entry) {
        var tracked = trackedDocuments.get(area.getEditor().getDocumentState().getSessionId());
        if (tracked == null) {
            return;
        }
        tracked.backupVersion = entry.backupVersion;
        tracked.backupFile = entry.backupFile;
        tracked.backupByteSize = entry.backupByteSize;
        tracked.backupSha256 = entry.backupSha256;
        tracked.lastSnapshotAt = entry.backupFile == null ? 0L : System.currentTimeMillis();
        tracked.contentVersion = area.getEditor().getContentVersion();
    }

    public void onTextChanged(EditorArea area, long contentVersion) {
        if (closing.get() || restoring) {
            return;
        }
        var state = area.getEditor().getDocumentState();
        var tracked = trackedDocuments.get(state.getSessionId());
        if (tracked == null) {
            return;
        }
        tracked.lastEditAt = System.currentTimeMillis();
        tracked.contentVersion = contentVersion;
        cancel(tracked.debounceTask);
        if (!state.isDirty()) {
            tracked.lifecycleGeneration++;
            cancel(tracked.compensationTask);
            clearBackupReference(tracked);
            commitCurrentState(true);
            return;
        }
        tracked.debounceTask = scheduler.schedule(
                () -> Platform.runLater(() -> trySnapshot(state.getSessionId())),
                SNAPSHOT_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    public void onCaretOrStructureChanged() {
        if (!closing.get() && !restoring) {
            scheduleManifestCommit();
        }
    }

    public void trySnapshot(String sessionId) {
        if (closing.get()) {
            return;
        }
        var tracked = trackedDocuments.get(sessionId);
        if (tracked == null || tracked.area == null || tracked.area.getEditor().isDestroyed()) {
            return;
        }
        var state = tracked.area.getEditor().getDocumentState();
        if (!state.isDirty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long sinceEdit = now - tracked.lastEditAt;
        if (sinceEdit < SNAPSHOT_DEBOUNCE_MS) {
            scheduleCompensation(tracked, SNAPSHOT_DEBOUNCE_MS - sinceEdit);
            return;
        }
        long sinceSnapshot = now - tracked.lastSnapshotAt;
        if (tracked.lastSnapshotAt > 0L && sinceSnapshot < SNAPSHOT_INTERVAL_MS) {
            scheduleCompensation(tracked, SNAPSHOT_INTERVAL_MS - sinceSnapshot);
            return;
        }
        captureAndWrite(tracked, tracked.area.getEditor().getContentVersion());
    }

    public void onSaved(EditorArea area, SaveResult result) {
        var state = area.getEditor().getDocumentState();
        var tracked = trackedDocuments.get(state.getSessionId());
        if (tracked == null) {
            return;
        }
        tracked.lifecycleGeneration++;
        cancel(tracked.debounceTask);
        cancel(tracked.compensationTask);
        if (result == SaveResult.SUCCESS_DIRTY) {
            captureAndWrite(tracked, area.getEditor().getContentVersion());
        } else if (result == SaveResult.SUCCESS_CLEAN) {
            clearBackupReference(tracked);
            commitCurrentState(true);
        }
    }

    public CompletionStage<SessionCommitResult> untrackAndCommit(EditorArea area) {
        var state = area.getEditor().getDocumentState();
        var tracked = trackedDocuments.remove(state.getSessionId());
        if (tracked != null) {
            tracked.lifecycleGeneration++;
            cancel(tracked.debounceTask);
            cancel(tracked.compensationTask);
        }
        return commitCurrentState(true).thenApply(result -> {
            if (result == SessionCommitResult.FAILED && tracked != null) {
                trackedDocuments.putIfAbsent(state.getSessionId(), tracked);
                Platform.runLater(() -> {
                    if (!area.getEditor().isDestroyed() && state.isDirty()) {
                        onTextChanged(area, area.getEditor().getContentVersion());
                    }
                });
            }
            return result;
        });
    }

    public void onDestroyed(EditorArea area) {
        var tracked = trackedDocuments.remove(area.getEditor().getDocumentState().getSessionId());
        if (tracked != null) {
            tracked.lifecycleGeneration++;
            cancel(tracked.debounceTask);
            cancel(tracked.compensationTask);
        }
    }

    public CompletionStage<RestoreResult> restoreSession() {
        restoring = true;
        return CompletableFuture.supplyAsync(this::loadRestoreData, ioExecutor)
                .thenCompose(data -> runOnFx(() -> applyRestoreData(data)))
                .whenComplete((result, throwable) -> restoring = false);
    }

    public FlushResult flushAndWait(Duration timeout) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("flushAndWait must run on JavaFX thread");
        }
        closing.set(true);
        cancel(manifestDebounce);
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            long remaining = deadline - System.nanoTime();
            CompletableFuture.runAsync(() -> { }, manifestExecutor)
                    .get(remaining, TimeUnit.NANOSECONDS);

            var areas = AllEditorsManager.Instance.getAllAreas();
            var writes = new LinkedHashMap<TrackedDocument, CompletableFuture<BackupData>>();
            for (var area : areas) {
                var state = area.getEditor().getDocumentState();
                var tracked = trackedDocuments.get(state.getSessionId());
                if (tracked == null) {
                    continue;
                }
                cancel(tracked.debounceTask);
                cancel(tracked.compensationTask);
                String text = area.getText();
                if (state.isDirty() || state.isUntitled() && !text.isEmpty()) {
                    writes.put(tracked, enqueueBackup(tracked, state.getSessionId(),
                            ++tracked.backupVersion, text));
                }
            }
            for (var entry : writes.entrySet()) {
                remaining = deadline - System.nanoTime();
                if (remaining <= 0L) {
                    throw new TimeoutException();
                }
                var backup = entry.getValue().get(remaining, TimeUnit.NANOSECONDS);
                var owner = entry.getKey();
                if (owner.backupVersion == backup.version) {
                    applyBackup(owner, backup);
                } else {
                    deleteQuietly(backupsDir.resolve(backup.fileName));
                }
            }
            var manifest = captureManifest();
            remaining = deadline - System.nanoTime();
            if (remaining <= 0L) {
                throw new TimeoutException();
            }
            CompletableFuture.runAsync(() -> {
                try {
                    writeManifest(manifest, false);
                    cleanupBackups();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, manifestExecutor).get(remaining, TimeUnit.NANOSECONDS);
            return FlushResult.SUCCESS;
        } catch (Exception e) {
            Log.e("flush session failed", e);
            return FlushResult.FAILED;
        }
    }

    public void resumeAfterFailedFlush() {
        closing.set(false);
        for (var tracked : trackedDocuments.values()) {
            var area = tracked.area;
            if (area != null && !area.getEditor().isDestroyed()
                    && area.getEditor().getDocumentState().isDirty()) {
                onTextChanged(area, area.getEditor().getContentVersion());
            }
        }
    }

    public void destroy() {
        closing.set(true);
        cancel(manifestDebounce);
        for (var tracked : trackedDocuments.values()) {
            cancel(tracked.debounceTask);
            cancel(tracked.compensationTask);
        }
        scheduler.shutdown();
        manifestExecutor.shutdown();
        ioExecutor.shutdown();
    }

    private void captureAndWrite(TrackedDocument tracked, long contentVersion) {
        var area = tracked.area;
        if (area == null || area.getEditor().isDestroyed()
                || !area.getEditor().getDocumentState().isDirty()) {
            return;
        }
        String text = area.getText();
        long generation = tracked.lifecycleGeneration;
        long backupVersion = ++tracked.backupVersion;
        enqueueBackup(tracked, area.getEditor().getDocumentState().getSessionId(),
                backupVersion, text)
                .whenComplete((backup, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        Log.e("snapshot write failed", throwable);
                        return;
                    }
                    if (closing.get()
                            || tracked.lifecycleGeneration != generation
                            || tracked.backupVersion != backup.version
                            || tracked.area.getEditor().getContentVersion() != contentVersion
                            || !tracked.area.getEditor().getDocumentState().isDirty()) {
                        deleteQuietly(backupsDir.resolve(backup.fileName));
                        return;
                    }
                    applyBackup(tracked, backup);
                    tracked.lastSnapshotAt = System.currentTimeMillis();
                    commitCurrentState(false);
                }));
    }

    private CompletableFuture<BackupData> enqueueBackup(TrackedDocument tracked,
                                                         String sessionId,
                                                         long backupVersion,
                                                         String text) {
        synchronized (tracked) {
            var write = tracked.writeChain.handle((ignored, throwable) -> null)
                    .thenApplyAsync(ignored -> writeBackup(sessionId, backupVersion, text), ioExecutor);
            tracked.writeChain = write.handle((ignored, throwable) -> null);
            return write;
        }
    }

    private void scheduleCompensation(TrackedDocument tracked, long delayMs) {
        cancel(tracked.compensationTask);
        long delay = delayMs < 1L ? 1L : delayMs;
        tracked.compensationTask = scheduler.schedule(() -> Platform.runLater(() -> {
            var area = tracked.area;
            if (area != null && !area.getEditor().isDestroyed()) {
                trySnapshot(area.getEditor().getDocumentState().getSessionId());
            }
        }), delay, TimeUnit.MILLISECONDS);
    }

    private void scheduleManifestCommit() {
        cancel(manifestDebounce);
        manifestDebounce = scheduler.schedule(
                () -> Platform.runLater(() -> commitCurrentState(false)),
                SNAPSHOT_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private CompletionStage<SessionCommitResult> commitCurrentState(boolean synchronizePrevious) {
        var manifest = captureManifest();
        return CompletableFuture.supplyAsync(() -> {
            try {
                writeManifest(manifest, synchronizePrevious);
                cleanupBackups();
                return SessionCommitResult.SUCCESS;
            } catch (Exception e) {
                Log.e("commit session failed", e);
                return SessionCommitResult.FAILED;
            }
        }, manifestExecutor);
    }

    private SessionManifest captureManifest() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("captureManifest must run on JavaFX thread");
        }
        var manifest = new SessionManifest();
        var current = UIContext.currentAreaProp.get();
        if (current != null) {
            manifest.activeSessionId = current.getEditor().getDocumentState().getSessionId();
        }
        var areas = AllEditorsManager.Instance.getAllAreas();
        for (int i = 0; i < areas.length; i++) {
            var area = areas[i];
            var state = area.getEditor().getDocumentState();
            var tracked = trackedDocuments.get(state.getSessionId());
            if (tracked != null) {
                manifest.tabs.add(toSessionTab(area, tracked, i));
            }
        }
        return manifest;
    }

    private SessionTab toSessionTab(EditorArea area, TrackedDocument tracked, int order) {
        var state = area.getEditor().getDocumentState();
        var tab = new SessionTab();
        tab.sessionId = state.getSessionId();
        tab.order = order;
        tab.displayName = state.getDisplayName();
        tab.sourcePath = state.getSourcePath();
        tab.untitled = state.isUntitled();
        tab.dirty = state.isDirty();
        tab.encoding = state.getEncoding();
        tab.baseLastModified = state.getBaseLastModified();
        tab.baseFileSize = state.getBaseFileSize();
        tab.caretPosition = area.getCaretPosition();
        tab.initialSaveDirectory = state.getInitialSaveDirectory();
        if (state.isDirty() || state.isUntitled() && !area.getText().isEmpty()) {
            tab.backupVersion = tracked.backupVersion;
            tab.backupFile = tracked.backupFile;
            tab.backupByteSize = tracked.backupByteSize;
            tab.backupSha256 = tracked.backupSha256;
            if (state.isDirty() && tracked.backupFile == null) {
                tab.dirty = false;
            }
        }
        return tab;
    }

    private RestoreData loadRestoreData() {
        var warnings = new ArrayList<String>();
        try {
            Files.createDirectories(backupsDir);
            var manifest = readBestManifest(warnings);
            if (manifest == null) {
                manifest = importLegacyData(warnings);
            }
            var previous = readManifest(previousManifestFile);
            var previousById = new HashMap<String, SessionTab>();
            if (previous != null && previous.tabs != null) {
                for (var tab : previous.tabs) {
                    previousById.put(tab.sessionId, tab);
                }
            }
            boolean restoreSaved = SettingPreferences.getBoolean(
                    SettingPreferences.restoreSavedFilesOnStartupKey);
            var restored = new ArrayList<SessionTab>();
            if (manifest.tabs != null) {
                manifest.tabs.stream().sorted(Comparator.comparingInt(tab -> tab.order)).forEach(tab -> {
                    if (!tab.dirty && !tab.untitled && !restoreSaved) {
                        return;
                    }
                    if (loadTabText(tab, warnings)) {
                        restored.add(tab);
                        return;
                    }
                    var previousTab = previousById.get(tab.sessionId);
                    if (previousTab != null && previousTab.dirty && loadBackup(previousTab)) {
                        previousTab.order = tab.order;
                        restored.add(previousTab);
                        warnings.add(Locales.str("sessionRestoredOlder").replace("%s", tab.displayName));
                    } else {
                        degradeTab(tab, restored, warnings);
                    }
                });
            }
            return new RestoreData(manifest.activeSessionId, restored, warnings);
        } catch (Exception e) {
            Log.e("load session failed", e);
            warnings.add(Locales.str("sessionReadFailed"));
            return new RestoreData(null, List.of(), warnings);
        }
    }

    private RestoreResult applyRestoreData(RestoreData data) {
        int restoredCount = 0;
        EditorArea activeArea = null;
        for (var entry : data.tabs) {
            var area = AllEditorsManager.Instance.restoreSessionEntry(entry, entry.restoredText);
            if (area == null) {
                continue;
            }
            restoredCount++;
            if (entry.sessionId.equals(data.activeSessionId)) {
                activeArea = area;
            }
        }
        if (activeArea != null) {
            AllEditorsManager.Instance.bringAreaToFront(activeArea);
        } else if (restoredCount > 0) {
            var areas = AllEditorsManager.Instance.getAllAreas();
            AllEditorsManager.Instance.bringAreaToFront(areas[0]);
        }
        commitCurrentState(true);
        return new RestoreResult(restoredCount, String.join("\n", data.warnings));
    }

    private boolean loadTabText(SessionTab tab, List<String> warnings) {
        if (tab.dirty) {
            return loadBackup(tab);
        }
        if (tab.untitled) {
            tab.restoredText = "";
            return true;
        }
        var source = pathOrNull(tab.sourcePath);
        if (source == null || !Files.isRegularFile(source)) {
            warnings.add(Locales.str("sessionFileNotExist").replace("%s", tab.displayName));
            return false;
        }
        try {
            var encoding = tab.encoding == null ? StandardCharsets.UTF_8 : Charset.forName(tab.encoding);
            tab.restoredText = Files.readString(source, encoding);
            return true;
        } catch (Exception e) {
            warnings.add(Locales.str("sessionFileReadFailed").replace("%s", tab.displayName));
            return false;
        }
    }

    private boolean loadBackup(SessionTab tab) {
        if (tab.backupFile == null || !BACKUP_NAME.matcher(tab.backupFile).matches()) {
            return false;
        }
        try {
            var backup = backupsDir.resolve(tab.backupFile).normalize();
            if (!backup.startsWith(backupsDir) || !Files.isRegularFile(backup)) {
                return false;
            }
            byte[] bytes = Files.readAllBytes(backup);
            if (bytes.length != tab.backupByteSize || !sha256(bytes).equals(tab.backupSha256)) {
                return false;
            }
            tab.restoredText = decodeUtf8(bytes);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void degradeTab(SessionTab tab, List<SessionTab> restored, List<String> warnings) {
        var source = pathOrNull(tab.sourcePath);
        if (source != null && Files.isRegularFile(source)) {
            tab.dirty = false;
            tab.backupFile = null;
            if (loadTabText(tab, warnings)) {
                restored.add(tab);
                warnings.add(Locales.str("sessionUnsavedNotRecoverable").replace("%s", tab.displayName));
            }
        } else if (tab.untitled) {
            tab.dirty = false;
            tab.restoredText = "";
            restored.add(tab);
            warnings.add(Locales.str("sessionContentNotRecoverable").replace("%s", tab.displayName));
        } else {
            warnings.add(Locales.str("sessionAndSourceMissing").replace("%s", tab.displayName));
        }
    }

    private SessionManifest readBestManifest(List<String> warnings) throws IOException {
        var current = readManifest(manifestFile);
        if (current != null) {
            return current;
        }
        var previous = readManifest(previousManifestFile);
        if (previous != null) {
            warnings.add(Locales.str("sessionUsedPrevious"));
            return previous;
        }
        if (Files.exists(manifestFile) || Files.exists(previousManifestFile)) {
            archiveInvalid(manifestFile);
            archiveInvalid(previousManifestFile);
            GlobalCfgStores.user().setInt(SettingPreferences.sessionMigrationVersionKey, 1);
            warnings.add(Locales.str("sessionCorrupted"));
            return new SessionManifest();
        }
        return null;
    }

    private SessionManifest readManifest(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            var manifest = gson.fromJson(Files.readString(path, StandardCharsets.UTF_8), SessionManifest.class);
            if (manifest == null || manifest.version != MANIFEST_VERSION || manifest.tabs == null) {
                return null;
            }
            var sessionIds = new HashSet<String>();
            for (var tab : manifest.tabs) {
                if (tab == null || !isUuid(tab.sessionId)
                        || tab.displayName == null || tab.displayName.isBlank()
                        || !sessionIds.add(tab.sessionId)) {
                    return null;
                }
            }
            return manifest;
        } catch (Exception e) {
            return null;
        }
    }

    private SessionManifest importLegacyData(List<String> warnings) throws IOException {
        var manifest = new SessionManifest();
        int migrationVersion = GlobalCfgStores.user().getInt(SettingPreferences.sessionMigrationVersionKey, 0);
        if (migrationVersion >= 1) {
            return manifest;
        }
        int order = 0;
        for (var path : GlobalCfgStores.user().getStringList("lastFile", List.of())) {
            var file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            var tab = new SessionTab();
            tab.sessionId = UUID.randomUUID().toString();
            tab.order = order++;
            tab.displayName = file.getName();
            tab.sourcePath = file.getAbsolutePath();
            tab.encoding = StandardCharsets.UTF_8.name();
            tab.baseLastModified = file.lastModified();
            tab.baseFileSize = file.length();
            manifest.tabs.add(tab);
        }
        var legacyFiles = new ArrayList<Path>();
        var legacyDirText = GlobalCfgStores.user().getString(SettingPreferences.newFileDirKey, "");
        if (!legacyDirText.isBlank()) {
            var legacyDir = Path.of(legacyDirText);
            if (Files.isDirectory(legacyDir)) {
                try (var stream = Files.list(legacyDir)) {
                    stream.filter(Files::isRegularFile)
                            .filter(path -> LEGACY_HIDDEN_NAME.matcher(path.getFileName().toString()).matches())
                            .sorted()
                            .forEach(legacyFiles::add);
                }
            }
        }
        int newIndex = 1;
        for (var legacy : legacyFiles) {
            String text = Files.readString(legacy, StandardCharsets.UTF_8);
            var tab = new SessionTab();
            tab.sessionId = UUID.randomUUID().toString();
            tab.order = order++;
            tab.displayName = "New" + newIndex++;
            tab.untitled = true;
            tab.dirty = true;
            tab.encoding = StandardCharsets.UTF_8.name();
            var backup = writeBackup(tab.sessionId, 1L, text);
            tab.backupVersion = backup.version;
            tab.backupFile = backup.fileName;
            tab.backupByteSize = backup.byteSize;
            tab.backupSha256 = backup.sha256;
            manifest.tabs.add(tab);
        }
        if (!manifest.tabs.isEmpty()) {
            manifest.activeSessionId = manifest.tabs.get(0).sessionId;
        }
        writeManifest(manifest, true);
        for (var legacy : legacyFiles) {
            Files.deleteIfExists(legacy);
        }
        GlobalCfgStores.user().setInt(SettingPreferences.sessionMigrationVersionKey, 1);
        GlobalCfgStores.user().remove("lastFile");
        GlobalCfgStores.user().remove(SettingPreferences.autoSaveOnExitKey);
        GlobalCfgStores.user().remove(SettingPreferences.newFileDirKey);
        GlobalCfgStores.user().remove(SettingPreferences.saveLastOpenedFileKey);
        if (!legacyFiles.isEmpty()) {
            warnings.add(Locales.str("sessionImportedTemp"));
        }
        return manifest;
    }

    private BackupData writeBackup(String sessionId, long version, String text) {
        try {
            Files.createDirectories(backupsDir);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            String fileName = sessionId + "-" + version + ".txt";
            var target = backupsDir.resolve(fileName);
            var temporary = Files.createTempFile(backupsDir, sessionId + "-", ".tmp");
            Files.write(temporary, bytes);
            moveReplace(temporary, target);
            return new BackupData(version, fileName, bytes.length, sha256(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void applyBackup(TrackedDocument tracked, BackupData backup) {
        tracked.backupVersion = backup.version;
        tracked.backupFile = backup.fileName;
        tracked.backupByteSize = backup.byteSize;
        tracked.backupSha256 = backup.sha256;
    }

    private static void clearBackupReference(TrackedDocument tracked) {
        tracked.lastSnapshotAt = 0L;
        tracked.backupFile = null;
        tracked.backupByteSize = 0L;
        tracked.backupSha256 = null;
    }

    private void writeManifest(SessionManifest manifest, boolean synchronizePrevious) throws IOException {
        synchronized (manifestWriteLock) {
            Files.createDirectories(sessionDir);
            if (Files.isRegularFile(manifestFile)) {
                writeAtomic(previousManifestFile, Files.readString(manifestFile, StandardCharsets.UTF_8));
            }
            String json = gson.toJson(manifest);
            writeAtomic(manifestFile, json);
            if (synchronizePrevious) {
                writeAtomic(previousManifestFile, json);
            }
        }
    }

    private void writeAtomic(Path target, String text) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        moveReplace(temporary, target);
    }

    private void cleanupBackups() {
        try {
            var referenced = new HashSet<String>();
            collectReferences(readManifest(manifestFile), referenced);
            collectReferences(readManifest(previousManifestFile), referenced);
            if (!Files.isDirectory(backupsDir)) {
                return;
            }
            try (var stream = Files.list(backupsDir)) {
                stream.filter(Files::isRegularFile)
                        .filter(path -> !referenced.contains(path.getFileName().toString()))
                        .forEach(EditorSessionManager::deleteQuietly);
            }
        } catch (IOException e) {
            Log.e("cleanup backups failed", e);
        }
    }

    private static void collectReferences(SessionManifest manifest, Set<String> target) {
        if (manifest == null || manifest.tabs == null) {
            return;
        }
        for (var tab : manifest.tabs) {
            if (tab.backupFile != null) {
                target.add(tab.backupFile);
            }
        }
    }

    private void archiveInvalid(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try {
            var target = path.resolveSibling(path.getFileName() + "." + System.currentTimeMillis() + ".invalid");
            Files.move(path, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Log.e("archive invalid session failed", e);
        }
    }

    private static void moveReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        var digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        return HexFormat.of().formatHex(digest);
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Path pathOrNull(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Path.of(value);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            Log.e("delete session file failed: " + path, e);
        }
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static <T> CompletionStage<T> runOnFx(Callable<T> callable) {
        var future = new CompletableFuture<T>();
        Platform.runLater(() -> {
            try {
                future.complete(callable.call());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static final class TrackedDocument {
        final EditorArea area;
        volatile long lastEditAt;
        volatile long lastSnapshotAt;
        volatile long contentVersion;
        volatile long lifecycleGeneration;
        volatile long backupVersion;
        volatile String backupFile;
        volatile long backupByteSize;
        volatile String backupSha256;
        volatile ScheduledFuture<?> debounceTask;
        volatile ScheduledFuture<?> compensationTask;
        CompletableFuture<Void> writeChain = CompletableFuture.completedFuture(null);

        TrackedDocument(EditorArea area) {
            this.area = area;
        }
    }

    private record BackupData(long version, String fileName, long byteSize, String sha256) {
    }

    private record RestoreData(String activeSessionId, List<SessionTab> tabs, List<String> warnings) {
    }
}
