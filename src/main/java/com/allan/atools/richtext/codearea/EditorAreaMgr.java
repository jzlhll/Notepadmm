package com.allan.atools.richtext.codearea;

import com.allan.atools.pop.impl.TabTitleCreatorImpl;
import com.allan.atools.text.IEditorAreaState;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.beans.ReplaceParams;
import com.allan.atools.text.FinderFactory;
import com.allan.atools.text.IEditorAreaEx;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.GlobalCfgStores;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.Highlight;
import com.allan.atools.tools.modulenotepad.StaticsProf;
import com.allan.atools.tools.modulejson.JsonFormatLog;
import com.allan.atools.UIContext;
import com.allan.atools.tools.modulenotepad.base.ITextFindAndReplace;
import com.allan.atools.tools.modulenotepad.manager.AllEditorsManager;
import com.allan.atools.tools.modulenotepad.session.EditorSessionManager;
import com.allan.atools.tools.modulenotepad.session.SaveResult;
import com.allan.atools.tools.modulenotepad.session.SessionCommitResult;
import com.allan.atools.pop.GlobalPopupManager;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.atools.utils.*;
import com.allan.baseparty.*;
import com.allan.baseparty.utils.ReflectionUtils;
import com.allan.uilibs.richtexts.MyLineNumFactory;
import com.allan.baseparty.handler.TextUtils;
import com.allan.baseparty.memory.RefWatcher;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.skins.JFXTabPaneSkin;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.fxmisc.richtext.GenericStyledArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.undo.UndoManager;
import org.reactfx.Subscription;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.allan.atools.SettingPreferences.editorLineNumberVisibleKey;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Backward;
import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

public class EditorAreaMgr implements IEditorAreaEx<Collection<String>, String, Collection<String>>, ITextFindAndReplace {
    static final String TAG = "Editor";

    private static final int MAX_LINE_COUNT_FOR_STYLE = 10000;
    private static final ExecutorService SAVE_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        var thread = new Thread(runnable, "editor-save-io");
        thread.setDaemon(true);
        return thread;
    });

    private final EditorAreaState state;
    private final EditorDocumentState documentState;

    @Override
    public IEditorAreaState getState() {
        return state;
    }

    @Override
    public boolean isEditorCodeMode() {
        return false;
    }

    public class TextChanged extends BaseChanged<Action0> {
        private Subscription textChangedSubscription;

        private void onTextChanged() {
            contentVersion.incrementAndGet();
            mContentSizeReachedStyleLimit = area.getLength() >= StaticsProf.getMaxFileSizeForStyle();
            mLineCountReachedStyleLimit = area.getParagraphs().size() >= MAX_LINE_COUNT_FOR_STYLE;
            disableStylerIfNeeded(null);
            if(EditorArea.DEBUG_EDITOR) Log.v("text changed !!");
            if (mActions != null) {
                for (var a : mActions) {
                    a.invoke();
                }
            }

            updateDirtyFromUndo();
        }

        @Override
        public void modifiedActions() {
            //do nothing...
        }

        @Override
        public void destroy() {
            if (mActions != null) {
                mActions.clear();
                mActions = null;
            }
            if (textChangedSubscription != null) {
                textChangedSubscription.unsubscribe();
                textChangedSubscription = null;
            }
        }

        @Override
        public void init() {
            textChangedSubscription = area.plainTextChanges().subscribe(change -> onTextChanged());
        }
    }

    public class VisibleParagraphChanged extends BaseChanged<Action0>{
        private int lastFirstVisibleParagraph = -1;
        private int lastLastVisibleParagraph = -1;
        private final ChangeListener<Double> _yChanged = (observable, oldValue, newValue) -> {
            int firstVisibleParagraph = area.firstVisibleParToAllParIndex();
            int lastVisibleParagraph = area.lastVisibleParToAllParIndex();
            if (lastFirstVisibleParagraph != firstVisibleParagraph || lastLastVisibleParagraph != lastVisibleParagraph) {
                if(EditorArea.DEBUG_EDITOR) Log.v("area: visible paragraphs changed " + firstVisibleParagraph + "-" + lastVisibleParagraph);
                lastFirstVisibleParagraph = firstVisibleParagraph;
                lastLastVisibleParagraph = lastVisibleParagraph;
                if (mActions != null) {
                    for (var a : mActions) {
                        a.invoke();
                    }
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.estimatedScrollYProperty().addListener(_yChanged);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.estimatedScrollYProperty().removeListener(_yChanged);
                    isSet = false;
                    lastFirstVisibleParagraph = -1;
                    lastLastVisibleParagraph = -1;
                }
            }
        }

        @Override
        public void destroy() {
            if (mActions != null) {
                mActions.clear();
                mActions = null;
            }
            if (isSet) {
                area.estimatedScrollYProperty().removeListener(_yChanged);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing.
        }
    }

    public class SelectionChanged extends BaseChanged<Action<String>> {
        private final ChangeListener<IndexRange> mSelectionChanged = (observable, oldValue, newValue) -> {
            if (EditorArea.DEBUG_EDITOR) Log.v("selection changed " + newValue.getLength());

            if (mActions != null) {
                String s = newValue.getLength() == 0 ? null : area.getSelectedText();
                for (var a : mActions) {
                    a.invoke(s);
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.selectionProperty().addListener(mSelectionChanged);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.selectionProperty().removeListener(mSelectionChanged);
                    isSet = false;
                }
            }
        }

        @Override
        public void destroy() {
            mActions.clear();
            mActions = null;
            if (isSet) {
                area.selectionProperty().removeListener(mSelectionChanged);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing
        }
    }

    public class CaretPosChanged extends BaseChanged<Action5<Integer, Integer, Integer, Integer, Integer>> {
        private final ChangeListener<Integer> mListener = (observable, oldValue, newValue) -> {
            if (EditorArea.DEBUG_EDITOR) Log.v("caret pos changed " + newValue);
            var selection = area.getSelection();
            var s = area.offsetToPosition(selection.getStart(), Forward);
            var s2  = area.offsetToPosition(selection.getEnd(), Backward);

            if (mActions != null) {
                for (var a : mActions) {
                    a.invoke(area.getCaretPosition(),
                            s.getMajor(), s.getMinor(),
                            selection.getLength(),
                            s2.getMajor() - s.getMajor() + 1);
                }
            }
        };

        @Override
        public void modifiedActions() {
            if (mActions != null) {
                if (!isSet) {
                    area.caretPositionProperty().addListener(mListener);
                    isSet = true;
                }
            } else {
                if (isSet) {
                    area.caretPositionProperty().removeListener(mListener);
                    isSet = false;
                }
            }
        }

        @Override
        public void destroy() {
            mActions.clear();
            mActions = null;
            if (isSet) {
                area.caretPositionProperty().removeListener(mListener);
                isSet = false;
            }
        }

        @Override
        public void init() {
            //do nothing.
        }
    }

    private EditorArea area;

    @Override
    public GenericStyledArea<Collection<String>, String, Collection<String>> getArea() {
        return area;
    }

    public final BaseChanged<Action0> textChanged = new TextChanged();
    public final BaseChanged<Action0> visibleParagraphChanged = new VisibleParagraphChanged();
    public final BaseChanged<Action<String>> selectionChanged = new SelectionChanged();
    final BaseChanged<Action5<Integer, Integer, Integer, Integer, Integer>> caretPosChanged = new CaretPosChanged();

    private final EditorBaseFocus editorFocus = new EditorBaseFocus(this);
    public File getSourceFile() {
        return documentState.getSourceFile();
    }

    public EditorDocumentState getDocumentState() {
        return documentState;
    }

    Tab tab;
    public Tab getTab() {
        return tab;
    }

    private final AtomicLong contentVersion = new AtomicLong();
    private final Object saveLock = new Object();
    private CompletableFuture<Void> saveChain = CompletableFuture.completedFuture(null);
    private volatile CompletableFuture<SaveResult> pendingSave =
            CompletableFuture.completedFuture(SaveResult.SUCCESS_CLEAN);
    private boolean programmaticReplace;
    private volatile boolean mSourceFileSizeReachedStyleLimit;
    private volatile boolean mContentSizeReachedStyleLimit;
    private volatile boolean mLineCountReachedStyleLimit;
    private final AtomicBoolean mStylerWasAllowed = new AtomicBoolean();

    public long getContentVersion() {
        return contentVersion.get();
    }

    public boolean isRealtimeProcessingLimitReached() {
        return mSourceFileSizeReachedStyleLimit
                || mContentSizeReachedStyleLimit
                || mLineCountReachedStyleLimit;
    }

    /** 统一限制语法高亮和 Markdown 目录等全文实时处理。 */
    public boolean disableStylerIfNeeded(Action0 endAction) {
        if (!isRealtimeProcessingLimitReached()) {
            mStylerWasAllowed.set(true);
            return false;
        }

        boolean shouldClearStyle = mStylerWasAllowed.getAndSet(false);
        Runnable disableAction = () -> {
            if (shouldClearStyle && isRealtimeProcessingLimitReached()
                    && area != null && area.getLength() > 0) {
                area.setStyle(0, area.getLength(), area.getInitialTextStyle());
            }
            if (endAction != null) {
                endAction.invoke();
            }
        };
        if (Platform.isFxApplicationThread()) {
            disableAction.run();
        } else if (shouldClearStyle || endAction != null) {
            Platform.runLater(disableAction);
        }
        return true;
    }
    /**
     * 反射找出本tab的title tabLabel
     */
    private Label tabLabel;

    private final ChangeListener<Boolean> lineNumberChanged = (observable, oldValue, newValue) ->
            updateLineNumberVisible(newValue);

    private void updateLineNumberVisible(boolean visible) {
        if (area != null) {
            area.setParagraphGraphicFactory(visible ? MyLineNumFactory.get(area) : null);
        }
    }

    @Override
    public void destroy() {
        SettingPreferences.getBoolProp(editorLineNumberVisibleKey).removeListener(lineNumberChanged);
        if (tabLabel != null) {
            tabLabel.setOnMouseClicked(null);
            tabLabel.setTooltip(null);
            tabLabel = null;
        }

        textChanged.destroy();
        visibleParagraphChanged.destroy();
        selectionChanged.destroy();
        caretPosChanged.destroy();

        editorFocus.removeFocusChanged();

        if (area != null) {
            EditorSessionManager.getInstance().onDestroyed(area);
        }

        var sourceFile = getSourceFile();
        if (sourceFile != null) {
            UIContext.allOpenedFileList.remove(sourceFile);
        }

        tab = null;
        area = null;
    }

    @Override
    public boolean isDestroyed() {
        return area == null || tab == null;
    }

    private enum ConfirmStat {
        Normal,
        DialogOpened,
    }

    private ConfirmStat mConfirmStat = ConfirmStat.Normal;

    public void closeTab() {
        requestClose();
    }

    public void requestClose() {
        if (mConfirmStat != ConfirmStat.Normal || isDestroyed()) {
            return;
        }
        mConfirmStat = ConfirmStat.DialogOpened;
        if (!documentState.isDirty()) {
            closeAfterCommit(EditorSessionManager.getInstance().untrackAndCommit(area));
            return;
        }
        JfoenixDialogUtils.confirm(Locales.ALERT(),
                Locales.str("ifUwantCloseIt").replace("%s", documentState.getDisplayName()),
                0, 0,
                new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept,
                        Locales.str("save"), () -> save().whenComplete((saveResult, throwable) ->
                                Platform.runLater(() -> {
                                    if (throwable == null && saveResult == SaveResult.SUCCESS_CLEAN) {
                                        closeAfterCommit(EditorSessionManager.getInstance()
                                                .untrackAndCommit(area));
                                    } else {
                                        mConfirmStat = ConfirmStat.Normal;
                                        if (throwable != null) {
                                            JfoenixDialogUtils.alert(Locales.ALERT(),
                                                    Locales.str("saveFileFailed"));
                                        }
                                    }
                                }))),
                new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Extra,
                        Locales.str("notSave"), () -> closeAfterCommit(
                                EditorSessionManager.getInstance().untrackAndCommit(area))),
                new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel,
                        null, () -> mConfirmStat = ConfirmStat.Normal));
    }

    /** 会话提交成功后关闭标签；失败则恢复确认状态并提示。 */
    private void closeAfterCommit(CompletionStage<SessionCommitResult> commit) {
        commit.whenComplete((commitResult, throwable) -> Platform.runLater(() -> {
            if (throwable == null && commitResult == SessionCommitResult.SUCCESS) {
                closeWithoutConfirm();
            } else {
                mConfirmStat = ConfirmStat.Normal;
                JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sessionWriteFailed"));
            }
        }));
    }

    private void closeWithoutConfirm() {
        mConfirmStat = ConfirmStat.Normal;
        AllEditorsManager.Instance.closeTabImmediately(tab);
    }

    EditorAreaMgr(EditorArea area, File sourceFile, Tab tab,
                  EditorDocumentState restoredState) {
        documentState = restoredState != null
                ? restoredState
                : sourceFile == null
                ? EditorDocumentState.untitled(
                        "New", null,
                        StandardCharsets.UTF_8.name())
                : EditorDocumentState.named(sourceFile, StandardCharsets.UTF_8.name());
        sourceFile = documentState.getSourceFile();
        int maxRealtimeProcessingSize = StaticsProf.getMaxFileSizeForStyle();
        mSourceFileSizeReachedStyleLimit = sourceFile != null
                && sourceFile.length() >= maxRealtimeProcessingSize;
        mContentSizeReachedStyleLimit = area.getLength() >= maxRealtimeProcessingSize;
        mLineCountReachedStyleLimit = area.getParagraphs().size() >= MAX_LINE_COUNT_FOR_STYLE;
        state = new EditorAreaState(area, documentState);
        state.setFileEncoding(documentState.getEncoding());
        if (sourceFile != null) {
            UIContext.allOpenedFileList.add(sourceFile);
            Log.w("new EditorBase:: " + sourceFile.lastModified());
        }
        this.tab = tab;
        tab.setUserData(documentState);
        updateTabTitle();
        tab.setOnCloseRequest(event -> {
            event.consume();
            requestClose();
        });

        this.area = area;
        initArea();
        if (documentState.isDirty()) {
            documentState.invalidateSavedUndoPosition();
        } else {
            establishUndoBaseline();
        }
        markCurrentFileTs();
        RefWatcher.watchs(this, sourceFile == null ? documentState.getDisplayName() : sourceFile.getPath());
    }

    private void establishUndoBaseline() {
        UndoManager.UndoPosition position = area.getUndoManager().getCurrentPosition();
        position.mark();
        documentState.setSavedUndoPosition(position);
        documentState.setDirty(false);
        updateTabTitle();
    }

    private void updateDirtyFromUndo() {
        if (programmaticReplace) {
            return;
        }
        documentState.setDirty(documentState.isSavedUndoPositionValid()
                ? !area.getUndoManager().isAtMarkedPosition()
                : true);
        updateTabTitle();
        EditorSessionManager.getInstance().onTextChanged(area, contentVersion.get());
    }

    private void updateTabTitle() {
        if (tab != null) {
            tab.setText(documentState.getDisplayName() + (documentState.isDirty() ? " *" : ""));
        }
    }

    private void markCurrentFileTs() {
        var sourceFile = getSourceFile();
        editorFocus.mLastFileChangedTs = sourceFile == null ? 0L : sourceFile.lastModified();
        editorFocus.mLastFileSize = sourceFile == null ? 0L : sourceFile.length();
    }

    public CompletionStage<SaveResult> save() {
        if (!documentState.isDirty() && !documentState.isUntitled()
                && documentState.getExternalState() == EditorDocumentState.ExternalState.UNCHANGED) {
            return CompletableFuture.completedFuture(SaveResult.SUCCESS_CLEAN);
        }
        var result = new CompletableFuture<SaveResult>();
        resolveSaveTarget(false, result);
        pendingSave = result;
        return result;
    }

    public void saveContent(ActionEvent event, boolean forceSave) {
        if (!forceSave) {
            save();
            return;
        }
        var result = new CompletableFuture<SaveResult>();
        var sourceFile = getSourceFile();
        if (sourceFile == null) {
            resolveSaveTarget(false, result);
        } else {
            prepareSave(sourceFile, result);
        }
        pendingSave = result;
    }

    private void resolveSaveTarget(boolean forceSaveAs, CompletableFuture<SaveResult> result) {
        File target = forceSaveAs || documentState.isUntitled() ? chooseSaveTarget() : getSourceFile();
        if (target == null) {
            result.complete(SaveResult.CANCELLED);
            return;
        }
        var openedArea = AllEditorsManager.Instance.getAreaByFilePath(target);
        if (openedArea != null && openedArea != area) {
            JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("fileOpenedInOtherTab"));
            result.complete(SaveResult.CANCELLED);
            return;
        }
        if (!forceSaveAs && documentState.getExternalState() != EditorDocumentState.ExternalState.UNCHANGED) {
            String acceptText = documentState.getExternalState() == EditorDocumentState.ExternalState.DELETED
                    ? Locales.str("reCreateFile") : Locales.str("overwriteDiskFile");
            JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("fileChangedOnDisk"),
                    0, 0,
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept,
                            acceptText, () -> prepareSave(target, result)),
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Extra,
                            Locales.str("saveAsFile"), () -> resolveSaveTarget(true, result)),
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel,
                            null, () -> result.complete(SaveResult.CANCELLED)));
            return;
        }
        prepareSave(target, result);
    }

    private File chooseSaveTarget() {
        var chooser = new FileChooser();
        chooser.setTitle(Locales.str("editor.saveFile"));
        chooser.setInitialFileName(documentState.isUntitled()
                ? documentState.getDisplayName() + ".txt"
                : documentState.getDisplayName());
        var initialDirectory = firstWritableDirectory();
        if (initialDirectory != null) {
            chooser.setInitialDirectory(initialDirectory);
        }
        var selected = chooser.showSaveDialog(UIContext.mainWindow);
        if (selected == null) {
            return null;
        }
        String name = selected.getName();
        int extensionIndex = name.lastIndexOf('.');
        if (extensionIndex <= 0 || extensionIndex == name.length() - 1) {
            selected = new File(selected.getParentFile(), name + ".txt");
        }
        return selected.toPath().toAbsolutePath().normalize().toFile();
    }

    private File firstWritableDirectory() {
        var initial = documentState.getInitialSaveDirectory();
        if (initial != null) {
            var directory = new File(initial);
            if (directory.isDirectory() && directory.canRead() && directory.canWrite()) {
                return directory;
            }
        }
        var last = GlobalCfgStores.user().getString(SettingPreferences.lastSaveDirKey, "");
        if (!last.isBlank()) {
            var directory = new File(last);
            if (directory.isDirectory() && directory.canRead() && directory.canWrite()) {
                return directory;
            }
        }
        var home = new File(System.getProperty("user.home"));
        return home.isDirectory() && home.canRead() && home.canWrite() ? home : null;
    }

    private void prepareSave(File target, CompletableFuture<SaveResult> result) {
        String sourceCode = area.getText();
        String encoding = state.getFileEncoding();
        if (encoding == null) {
            encoding = StandardCharsets.UTF_8.name();
        }
        Charset charset;
        try {
            charset = Charset.forName(encoding);
        } catch (RuntimeException e) {
            JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("encodingInvalid"));
            result.complete(SaveResult.FAILED);
            return;
        }
        if (!charset.newEncoder().canEncode(sourceCode)) {
            JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("encodingCannotRepresent"),
                    0, 0,
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept,
                            Locales.str("saveWithUtf8"), () -> {
                        state.setFileEncoding(StandardCharsets.UTF_8.name());
                        startSave(target, sourceCode, StandardCharsets.UTF_8.name(), result);
                    }),
                    new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel,
                            null, () -> result.complete(SaveResult.CANCELLED)));
            return;
        }
        startSave(target, sourceCode, encoding, result);
    }

    private void startSave(File target, String sourceCode, String encoding,
                           CompletableFuture<SaveResult> result) {
        long capturedVersion = contentVersion.get();
        UndoManager.UndoPosition savedPosition = area.getUndoManager().getCurrentPosition();
        CompletableFuture<Boolean> write;
        synchronized (saveLock) {
            write = saveChain.handle((ignored, throwable) -> null)
                    .thenApplyAsync(ignored -> writeSourceFile(target, sourceCode, encoding), SAVE_EXECUTOR);
            saveChain = write.handle((ignored, throwable) -> null);
        }
        write.whenComplete((success, throwable) -> Platform.runLater(() -> {
            if (throwable != null || !Boolean.TRUE.equals(success) || isDestroyed()) {
                if (throwable != null) {
                    Log.e("save content failed: " + target, throwable);
                }
                if (!isDestroyed()) {
                    JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("saveFileFailed"));
                }
                result.complete(SaveResult.FAILED);
                return;
            }
            bindSavedFile(target);
            documentState.updateBaseFileMetadata();
            documentState.setExternalState(EditorDocumentState.ExternalState.UNCHANGED);
            if (savedPosition.isValid()) {
                savedPosition.mark();
                documentState.setSavedUndoPosition(savedPosition);
            }
            boolean clean = savedPosition.isValid()
                    && contentVersion.get() == capturedVersion
                    && area.getUndoManager().isAtMarkedPosition();
            documentState.setDirty(!clean);
            updateTabTitle();
            var saveResult = clean ? SaveResult.SUCCESS_CLEAN : SaveResult.SUCCESS_DIRTY;
            EditorSessionManager.getInstance().onSaved(area, saveResult);
            AllEditorsManager.delayToSaveRecentFile(target.getAbsolutePath());
            notifyWorkspaceRefreshDelayed(target);
            result.complete(saveResult);
        }));
    }

    private boolean writeSourceFile(File target, String sourceCode, String encoding) {
        try {
            var path = target.toPath();
            var parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, sourceCode, Charset.forName(encoding));
            return true;
        } catch (IOException | RuntimeException e) {
            Log.e("save content failed: " + target.getAbsolutePath(), e);
            return false;
        }
    }

    private void bindSavedFile(File target) {
        var oldFile = getSourceFile();
        if (oldFile != null) {
            UIContext.allOpenedFileList.remove(oldFile);
        }
        documentState.bindSourceFile(target);
        documentState.setDisplayName(target.getName());
        state.saveDocumentOptions();
        UIContext.allOpenedFileList.add(target);
        if (tab != null) {
            tab.setUserData(documentState);
        }
        if (this instanceof EditorAreaMgrCode codeEditor) {
            codeEditor.bindKeywordHelper(target);
        }
        var parent = target.getParentFile();
        if (parent != null) {
            GlobalCfgStores.user().setString(SettingPreferences.lastSaveDirKey, parent.getAbsolutePath());
        }
        mSourceFileSizeReachedStyleLimit = target.length() >= StaticsProf.getMaxFileSizeForStyle();
        markCurrentFileTs();
        if (tabLabel != null) {
            tabLabel.setTooltip(new Tooltip(target.getAbsolutePath()));
        }
        if (UIContext.currentAreaProp.get() == area) {
            UIContext.context().refreshCurrentDocumentInfo();
        }
    }

    private void initArea() {
        var lineNumberVisibleProperty = SettingPreferences.getBoolProp(editorLineNumberVisibleKey);
        updateLineNumberVisible(lineNumberVisibleProperty.get());
        lineNumberVisibleProperty.addListener(lineNumberChanged);

        textChanged.init();
        visibleParagraphChanged.init();
        caretPosChanged.init();
        selectionChanged.init();

        caretPosChanged.addAction((caretPos, lineNum, colNum, length, lineCount) -> {
            Log.d("area: caretPos changed!");
            state.selectLineCount = lineCount;
            var s = length > 0 ? String.format(Locales.str("editor.caretIndicate"), /*caretPos,*/ lineNum, colNum, length, lineCount)
                    : String.format(Locales.str("editor.caretIndicate.short"), lineNum, colNum);
            state.selectedLength = length;
            state.currentCaretPos = caretPos;
            state.currentCaretColNum = colNum;
            state.currentCaretLineNum = lineNum;
            UIContext.bottomIndicateProp.set(s);
        });

        ContextMenu contextMenu = createMenu();
        area.setContextMenu(contextMenu);

        initTitleClick();
    }

    private void initTitleClick() {
        ThreadUtils.executeDelay(1000, ()-> {
            if (area == null) {
                return;
            }

            var tp = tab.getTabPane();
            JFXTabPaneSkin skin = null;
            int maxCount = 0;
            do {
                if (tp != null) {
                    skin = (JFXTabPaneSkin) tp.getSkin();
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (ThreadUtils.sBeClosing || tab == null || area == null) {
                    return;
                }
                if(maxCount++ == 100) break;
            } while (skin == null);

            if (skin == null) {
                return;
            }

            try {
                var head = ReflectionUtils.getPrivateFieldValue(skin, "header");
                var headersRegion = ReflectionUtils.getPrivateFieldValue(head, "headersRegion");
                if (headersRegion instanceof StackPane headerRegionStackPane) {
                    for (Node cur : headerRegionStackPane.getChildren()) {
                        var tabInNode = ReflectionUtils.getPrivateFieldValue(cur, "tab");
                        if (tabInNode == this.tab) {
                            var tl = ReflectionUtils.getPrivateFieldValue(cur, "tabLabel");
                            if (tl instanceof Label) {
                                tabLabel = (Label) tl;
                            }

                            break;
                        }
                    }
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }

            Platform.runLater(()-> {
                //再init title的点击事件和悬浮提示
                if (tabLabel != null) {
                    tabLabel.setOnMouseClicked(event-> {
                        if (event.getButton() == MouseButton.SECONDARY) {
                            var sourceFile = getSourceFile();
                            var pinned = sourceFile != null
                                    && AllEditorsManager.isPinnedRecentFile(sourceFile.getAbsolutePath());
                            var region = new TabTitleCreatorImpl().createPop(ev -> {
                                Log.d("ev " + ev);
                                if (TabTitleCreatorImpl.EVENT_MODIFY_NAME.equals(ev)) {
                                    rename();
                                } else if (TabTitleCreatorImpl.EVENT_CLOSE_OTHERS.equals(ev)) {
                                    AllEditorsManager.Instance.removeAllOtherTabs(tab);
                                } else if (TabTitleCreatorImpl.EVENT_OPEN_TO_EXPLORE.equals(ev)) {
                                    openCurrentFolder(null);
                                } else if (TabTitleCreatorImpl.EVENT_COPY_FULL_PATH.equals(ev)) {
                                    var path = sourceFile == null ? "" : sourceFile.getAbsolutePath();
                                    if (!path.isEmpty()) {
                                        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
                                        SnackbarUtils.show(Locales.str("fullPathCopied"));
                                    }
                                } else if (TabTitleCreatorImpl.EVENT_PIN_RECENT_FILE.equals(ev)) {
                                    if (sourceFile != null) {
                                        var pinnedNow = AllEditorsManager.togglePinnedRecentFile(sourceFile.getAbsolutePath());
                                        SnackbarUtils.show(Locales.str(pinnedNow ? "editor.pinnedRecentFileDone" : "editor.unpinnedRecentFileDone"));
                                    }
                                }
                                GlobalPopupManager.instance().hide();
                            }, pinned);

                            GlobalPopupManager.instance().setContent(region).setHeight(300).show(tabLabel, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT);
                        }
                    });

                    var sourceFile = getSourceFile();
                    if (sourceFile != null) {
                        tabLabel.setTooltip(new Tooltip(sourceFile.getAbsolutePath()));
                    }
                }
            });
        });
    }

    @Override
    public void rename() {
        var sourceFile = getSourceFile();
        if (sourceFile == null) {
            return;
        }
        JfoenixDialogUtils.editInput(Locales.ALERT(), sourceFile.getName(), s -> {
            if (!TextUtils.isEmpty(s)) {
                if (!pendingSave.isDone()) {
                    return;
                }
                var ans = Utils.rename(sourceFile, s);
                if (ans == null) {
                    SnackbarUtils.show("maybe you do not save this file!");
                } else if (ans.newFullPath() == null && ans.run() != null) {
                    JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("doUWantReplaceOldFile"),
                            0, 0,
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, null, () -> {
                                if (!pendingSave.isDone()) {
                                    return;
                                }
                                var newFullPa = ans.run().invoke();
                                if (newFullPa != null) {
                                    afterRename(newFullPa);
                                }
                            }),
                            new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, null, null));
                } else if (ans.newFullPath() != null) {
                    Log.d("changed name " + ans.newFullPath());
                    afterRename(ans.newFullPath());
                }
            }
        });
    }

    @Override
    public void resetText(String text) {
        var sourceFile = getSourceFile();
        mSourceFileSizeReachedStyleLimit = sourceFile != null
                && sourceFile.length() >= StaticsProf.getMaxFileSizeForStyle();
        programmaticReplace = true;
        try {
            area.replaceText(text);
            area.getUndoManager().forgetHistory();
            area.getUndoManager().mark();
            establishUndoBaseline();
        } finally {
            programmaticReplace = false;
        }
        documentState.setExternalState(EditorDocumentState.ExternalState.UNCHANGED);
        documentState.updateBaseFileMetadata();
        markCurrentFileTs();
        EditorSessionManager.getInstance().onSaved(area, SaveResult.SUCCESS_CLEAN);
    }

    @Override
    public boolean canClosed() {
        return tab == null || !documentState.isDirty();
    }

    @Override
    public void removeStageFocus() {
        editorFocus.removeFocusChanged();
    }

    @Override
    public void addStageFocus() {
        editorFocus.addFocusChanged();
    }

    @Override
    public void checkFileIfChanged() {
        Log.d(TAG, "check file if changed!!!");
        editorFocus.checkFileChangedTs();
    }

    private void afterRename(String newFile) {
        editorFocus.removeFocusChanged();
        var newf = new File(newFile);
        var oldFile = getSourceFile();
        if (oldFile != null) {
            UIContext.allOpenedFileList.remove(oldFile);
        }
        documentState.bindSourceFile(newf);
        state.saveDocumentOptions();
        UIContext.allOpenedFileList.add(newf);

        tab.setUserData(documentState);
        updateTabTitle();
        if (this instanceof EditorAreaMgrCode codeEditor) {
            codeEditor.bindKeywordHelper(newf);
        }
        mSourceFileSizeReachedStyleLimit = newf.length() >= StaticsProf.getMaxFileSizeForStyle();
        UIContext.context().refreshCurrentDocumentInfo();
        AllEditorsManager.delayToSaveRecentFile(newFile);
        EditorSessionManager.getInstance().onCaretOrStructureChanged();

        markCurrentFileTs();
        if (tabLabel.getTooltip() == null) {
            tabLabel.setTooltip(new Tooltip(newf.getAbsolutePath()));
        } else {
            tabLabel.getTooltip().setText(newf.getAbsolutePath());
        }

        ThreadUtils.globalHandler().postDelayed(editorFocus::addFocusChanged, 180);
        notifyWorkspaceRefreshDelayed(newf);
    }

    private void notifyWorkspaceRefreshDelayed(File changedFile) {
        ThreadUtils.globalHandler().postDelayed(()->
                Platform.runLater(()->
                        UIContext.context().getWorkspaceManager().ifRefreshWorkspace(changedFile)),
                200);
    }

    @Override
    public ContextMenu createMenu() {
        //Create Menu Items
        //MenuItem save =             new MenuItem("保存");
        MenuItem copy =             new MenuItem(Locales.str("editor.copySelect"));
        MenuItem copyLine =             new MenuItem(Locales.str("editor.copyLine"));
        MenuItem openFolder =       new MenuItem(Locales.str("editor.openHereDir"));
        MenuItem openTerminal =     new MenuItem(Locales.str("editor.programTerminal"));
        MenuItem openTerminalHere = new MenuItem(Locales.str("editor.directoryTerminal"));
        MenuItem reload = new MenuItem(Locales.str("editor.reload"));
        MenuItem screenShot =       new MenuItem(Locales.str("editor.screenshot"));
        MenuItem removeUnknownSymbols = new MenuItem(Locales.str("removeUnknownSymbols"));
        MenuItem formatJson =        new MenuItem(Locales.str("jsonFormat"));
        //Add Event Handler
        //save.setOnAction(this::saveContent);

        copyLine.setOnAction(e -> {
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var s = area.getText(Highlight.getCurrentCaretLineNum(area));
            var trans = new StringSelection(s);
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });

        copy.setOnAction(actionEvent -> {
            // 获取系统剪贴板
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            // 封装文本内容
            var trans = new StringSelection(area.getSelectedText());
            // 把文本内容设置到系统剪贴板
            clipboard.setContents(trans, null);
        });
        openFolder.setOnAction(this::openCurrentFolder);
        openTerminal.setOnAction(this::openTerminal);
        openTerminalHere.setOnAction(this::openTerminalHere);
        reload.setOnAction(event -> reloadFromDisk());
        screenShot.setOnAction(this::captureScreenShot);
        removeUnknownSymbols.setOnAction(event -> {
            var newText = new JsonFormatLog().removeFanxieExtraQuote(area.getSelectedText());
            replaceSelectedTextWithPadding(newText);
        });
        formatJson.setOnAction(event -> {
            var fmt = new JsonFormatLog();
            var newText = fmt.format(fmt.removeEnter(area.getSelectedText()));
            replaceSelectedTextWithPadding(newText);
        });

        //Add Menu items in Context Menu
        ContextMenu contextMenu = new ContextMenu();

        //contextMenu.getItems().add(save);
        contextMenu.getItems().add(copyLine);
        contextMenu.getItems().add(screenShot);
        contextMenu.getItems().add(copy);
        contextMenu.getItems().add(removeUnknownSymbols);
        contextMenu.getItems().add(formatJson);
        // contextMenu.getItems().add(openTerminal);
        contextMenu.getItems().add(openTerminalHere);
        contextMenu.getItems().add(openFolder);
        contextMenu.getItems().add(reload);

        contextMenu.setOnShowing(event -> {
            boolean hasSelection = !area.getSelectedText().isEmpty();
            copy.setVisible(hasSelection);
            removeUnknownSymbols.setVisible(hasSelection);
            formatJson.setVisible(hasSelection);
            copyLine.setVisible(!hasSelection);
            screenShot.setVisible(!hasSelection);
            boolean named = getSourceFile() != null;
            openTerminalHere.setVisible(!hasSelection && named);
            openFolder.setVisible(!hasSelection && named);
            reload.setVisible(!hasSelection && named);
        });

        return contextMenu;
    }

    private void reloadFromDisk() {
        var sourceFile = getSourceFile();
        if (sourceFile == null || !sourceFile.isFile()) {
            return;
        }
        Runnable reload = () -> AllEditorsManager.Instance.reOpenCurrentFile(
                tab, sourceFile, state.getFileEncoding());
        if (!documentState.isDirty()) {
            reload.run();
            return;
        }
        JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("confirmReloadDiscard"),
                0, 0,
                new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept,
                        null, reload::run),
                new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel,
                        null, null));
    }

    private void replaceSelectedTextWithPadding(String newText) {
        var selection = area.getSelection();
        if (selection.getLength() == 0) {
            return;
        }

        var fullText = area.getText();
        var before = fullText.substring(0, selection.getStart());
        var after = fullText.substring(selection.getEnd());
        var replacement = new StringBuilder();

        if (!before.isBlank()) {
            int lineBreaks = countBoundaryLineBreaks(before, before.length() - 1, -1);
            if (lineBreaks < 4) {
                replacement.append("\n".repeat(4 - lineBreaks));
            }
        }

        replacement.append(newText);

        if (!after.isBlank()) {
            int lineBreaks = countBoundaryLineBreaks(after, 0, 1);
            if (lineBreaks < 4) {
                replacement.append("\n".repeat(4 - lineBreaks));
            }
        }

        area.replaceText(selection.getStart(), selection.getEnd(), replacement.toString());
    }

    private int countBoundaryLineBreaks(String text, int index, int step) {
        int lineBreaks = 0;
        for (int i = index; i >= 0 && i < text.length(); i += step) {
            char c = text.charAt(i);
            if (c == '\n') {
                lineBreaks++;
            } else if (!Character.isWhitespace(c)) {
                break;
            }
        }
        return lineBreaks;
    }

    private void captureScreenShot(ActionEvent event) {
        Platform.runLater(Utils::captureScreenShot);
    }

    private void openCurrentFolder(ActionEvent event) {
        var sourceFile = getSourceFile();
        if (sourceFile != null) {
            if (sourceFile.getParentFile() != null) {
                Utils.openFolderExplore(sourceFile);
            }
        }
    }

    private void openTerminal(ActionEvent event) {
        Utils.openTerminal();
    }

    private void openTerminalHere(ActionEvent event) {
        var sourceFile = getSourceFile();
        if (sourceFile != null)
            if (sourceFile.getParentFile() != null)
                Utils.openTerminalHere(sourceFile.getParentFile());
    }

    @Override
    public void find(SearchParams params, Action<OneFileSearchResults> action) {
        ThreadUtils.execute(()-> {
            String text = area.getText();
            int[] totalLines = {0};
            var ans = FinderFactory.find(text,
                    SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey),
                    new SearchParams[] {params},
                    totalLines);
            var result = new OneFileSearchResults()
                    .addFile(getSourceFile())
                    .addSessionId(documentState.getSessionId())
                    .addDisplayName(documentState.getDisplayName())
                    .addArea(this)
                    .addTotalLen(text == null ? 0 : text.length())
                    .addResults(ans);
            action.invoke(result);
        });
    }

    @Override
    public void findAdvance(SearchParams[] params, Action<OneFileSearchResults> action) {
        ThreadUtils.execute(()-> {
            String text = area.getText();
            int[] totalLines = new int[]{0};
            var ans = FinderFactory.find(text,
                    SettingPreferences.getBoolean(SettingPreferences.searchResultHasNumberKey),
                    params,
                    totalLines);
            var result = new OneFileSearchResults()
                    .addFile(getSourceFile())
                    .addSessionId(documentState.getSessionId())
                    .addDisplayName(documentState.getDisplayName())
                    .addArea(this)
                    .addTotalLen(text == null ? 0 : text.length())
                    .addResults(ans);
            action.invoke(result);
        });
    }

    @Override
    public StringBuilder replace(ReplaceParams params) {
        return null;
    }

    public void trigger(SearchParams temporaryText, SearchParams searchText, Action0 end) {
        //do nothing...
        throw new RuntimeException("base should not call trigger in EditorBase");
    }

    public void triggerWithSnapshot(String text, long contentVersion,
                                    StyleSpans<Collection<String>> currentSpans,
                                    SearchParams temporaryText, SearchParams searchText, Action0 end) {
        trigger(temporaryText, searchText, end);
    }

    private static class EditorBaseFocus {
        private final EditorAreaMgr editor;

        EditorBaseFocus(EditorAreaMgr base) {
            editor = base;
        }

        private boolean mIsAddedFocusChanged = false;

        private long mLastFileChangedTs;
        private long mLastFileSize;

        private Action0 mFocused;

        private void checkFileChangedTs() {
            var sourceFile = editor.getSourceFile();
            if (sourceFile == null) {
                return;
            }
            if (!sourceFile.exists()) {
                if (editor.documentState.getExternalState() == EditorDocumentState.ExternalState.DELETED) {
                    return;
                }
                editor.documentState.setExternalState(EditorDocumentState.ExternalState.DELETED);
                if (editor.documentState.isDirty()) {
                    JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sourceDeletedKept"));
                    return;
                }
                JfoenixDialogUtils.confirm(Locales.ALERT(), Locales.str("fileNotExsit"),
                        0, 0,
                        new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Accept, Locales.str("reCreateFile"), ()-> {
                            removeFocusChanged();
                            editor.saveContent(null, true);
                            ThreadUtils.globalHandler().postDelayed(this::addFocusChanged, 250);
                        }),
                        new JfoenixDialogUtils.DialogActionInfo(JfoenixDialogUtils.ConfirmMode.Cancel, Locales.str("close"), ()-> {
                            //说明不想要了。我们先移除监听再说
                            removeFocusChanged();
                            editor.closeTab();
                        }));
                return;
            }

            if (mLastFileChangedTs == sourceFile.lastModified() && mLastFileSize == sourceFile.length()) {
                return;
            }
            if (editor.documentState.isDirty()) {
                if (editor.documentState.getExternalState() != EditorDocumentState.ExternalState.MODIFIED) {
                    editor.documentState.setExternalState(EditorDocumentState.ExternalState.MODIFIED);
                    JfoenixDialogUtils.alert(Locales.ALERT(), Locales.str("sourceChangedKept"));
                }
                return;
            }
            AllEditorsManager.Instance.reOpenCurrentFile(editor.tab, sourceFile, null);
        }

        private void removeFocusChanged() {
            if (mIsAddedFocusChanged) {
                Log.d(TAG, "remove focusChanged " + editor.getSourceFile());
                UIContext.context().removeMainStageFocused(mFocused);
                mFocused = null;
                mIsAddedFocusChanged = false;
            }
        }

        private void addFocusChanged() {
            if (mFocused == null) {
                mFocused = this::checkFileChangedTs;
            }

            if (!mIsAddedFocusChanged) {
                Log.d(TAG, "add focusChanged " + editor.getSourceFile());
                UIContext.context().addMainStageFocused(mFocused);
                mIsAddedFocusChanged = true;
            }
        }
    }
}
