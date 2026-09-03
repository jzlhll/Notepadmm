package com.allan.atools.richtext.codearea;

import org.fxmisc.undo.UndoManager;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

/** 编辑器标签的文档状态与会话标识。 */
public final class EditorDocumentState {
    public enum ExternalState {
        UNCHANGED,
        MODIFIED,
        DELETED
    }

    private final String sessionId;
    private String displayName;
    private String sourcePath;
    private boolean untitled;
    private boolean dirty;
    private ExternalState externalState = ExternalState.UNCHANGED;
    private String encoding;
    private long baseLastModified;
    private long baseFileSize;
    private String initialSaveDirectory;
    private transient UndoManager.UndoPosition savedUndoPosition;
    private transient String savedText;

    public EditorDocumentState(String sessionId, String displayName, File sourceFile,
                               boolean untitled, String encoding, File initialSaveDirectory) {
        this.sessionId = sessionId == null ? UUID.randomUUID().toString() : sessionId;
        this.displayName = displayName;
        this.untitled = untitled;
        this.encoding = encoding;
        this.initialSaveDirectory = normalize(initialSaveDirectory);
        bindSourceFile(sourceFile);
    }

    public static EditorDocumentState named(File sourceFile, String encoding) {
        return new EditorDocumentState(null, sourceFile.getName(), sourceFile, false, encoding, null);
    }

    public static EditorDocumentState untitled(String displayName, File initialSaveDirectory, String encoding) {
        return new EditorDocumentState(null, displayName, null, true, encoding, initialSaveDirectory);
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public File getSourceFile() {
        return sourcePath == null ? null : new File(sourcePath);
    }

    public void bindSourceFile(File sourceFile) {
        sourcePath = normalize(sourceFile);
        if (sourceFile != null) {
            displayName = sourceFile.getName();
            untitled = false;
            updateBaseFileMetadata();
        }
    }

    public boolean isUntitled() {
        return untitled;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public ExternalState getExternalState() {
        return externalState;
    }

    public void setExternalState(ExternalState externalState) {
        this.externalState = externalState;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public long getBaseLastModified() {
        return baseLastModified;
    }

    public void setBaseLastModified(long baseLastModified) {
        this.baseLastModified = baseLastModified;
    }

    public long getBaseFileSize() {
        return baseFileSize;
    }

    public void setBaseFileSize(long baseFileSize) {
        this.baseFileSize = baseFileSize;
    }

    public String getInitialSaveDirectory() {
        return initialSaveDirectory;
    }

    public void setSavedUndoPosition(UndoManager.UndoPosition savedUndoPosition) {
        this.savedUndoPosition = savedUndoPosition;
    }

    public boolean isSavedUndoPositionValid() {
        return savedUndoPosition != null && savedUndoPosition.isValid();
    }

    public void invalidateSavedUndoPosition() {
        savedUndoPosition = null;
    }

    public String getSavedText() {
        return savedText;
    }

    public void setSavedText(String savedText) {
        this.savedText = savedText;
    }

    public void updateBaseFileMetadata() {
        var file = getSourceFile();
        if (file != null && file.exists()) {
            baseLastModified = file.lastModified();
            baseFileSize = file.length();
        }
    }

    private static String normalize(File file) {
        if (file == null) {
            return null;
        }
        Path path = file.toPath().toAbsolutePath().normalize();
        return path.toString();
    }
}
