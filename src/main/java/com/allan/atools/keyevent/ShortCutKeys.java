package com.allan.atools.keyevent;

import com.allan.atools.UIContext;

import static com.allan.atools.keyevent.ShortCutKeys.CombineKey.*;

public final class ShortCutKeys {
    public static final boolean DEBUG_KEY = UIContext.DEBUG;

    public enum CombineKey {
        NotAccept,
        Find,
        FindS,
        Replace,
        ReplaceS,
        Save,
        Copy,
        Cut,
        Delete,
        Esc,
    }

    public static CombineKey parse(String[] keys) {
        String cmd = toString(keys);
        KeyEventDispatcher.log("dispatch key " + cmd);
        return switch (cmd) {
            case "Command+Shift+F+", "Shift+Command+F+", "Ctrl+Shift+F+", "Shift+Ctrl+F+" -> FindS;
            case "Command+Shift+R+", "Shift+Command+R+", "Ctrl+Shift+R+", "Shift+Ctrl+R+" -> ReplaceS;
            case "Command+F+", "Ctrl+F+" -> Find;
            case "Command+R+", "Ctrl+R+" -> Replace;
            case "Command+S+", "Ctrl+S+" -> Save;
            case "Command+C+", "Ctrl+C+" -> Copy;
            case "Command+X+", "Ctrl+X+" -> Cut;
            default -> NotAccept;
        };
    }

    private static String toString(String[] keys) {
        if (keys == null || keys.length == 0) {
            throw new RuntimeException("Shortcut key error #1");
        }
        StringBuilder sb = new StringBuilder();
        for (String k : keys) {
            sb.append(k).append("+");
        }
        return sb.toString();
    }
}
