package com.allan.atools.tools.modulenotepad.session;

public final class SessionTab {
    public String sessionId;
    public int order;
    public String displayName;
    public String sourcePath;
    public boolean untitled;
    public boolean dirty;
    public String encoding;
    public long backupVersion;
    public String backupFile;
    public long backupByteSize;
    public String backupSha256;
    public long baseLastModified;
    public long baseFileSize;
    public int caretPosition;
    public String initialSaveDirectory;

    public transient String restoredText;
}
