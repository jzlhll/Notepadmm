package com.allan.atools;

import com.allan.atools.keyevent.IKeyDispatcherLeaf;

import java.util.HashSet;
import java.util.Set;

public final class KeyEventDispatchCenter {
    private KeyEventDispatchCenter() {}

    public static final Set<IKeyDispatcherLeaf> mKeyListeners = new HashSet<>(4);

    public static void addKeyListener(IKeyDispatcherLeaf listener) {
        mKeyListeners.add(listener);
    }

    public static void rmKeyListener(IKeyDispatcherLeaf listener) {
        mKeyListeners.remove(listener);
    }
}
