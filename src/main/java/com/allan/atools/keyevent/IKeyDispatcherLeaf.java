package com.allan.atools.keyevent;

import com.allan.atools.KeyEventDispatchCenter;

public interface IKeyDispatcherLeaf {
    int level();
    //boolean accept(KeyEvent event, KeyEventDispatcher.KeyMode mode);
    boolean accept(ShortCutKeys.CombineKey parsedEvent);

    default void addKeyListener() {
        KeyEventDispatchCenter.addKeyListener(this);
    }
    default void removeKeyListener() {
        //Event Listener
        KeyEventDispatchCenter.rmKeyListener(this);
    }
}
