package com.allan.atools.keyevent;

import com.allan.atools.KeyEventDispatchCenter;
import com.allan.atools.utils.Log;
import javafx.scene.Parent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * 借鉴android的事件分发。如果level最高的页面消费掉了事件，则level低的父节点不做使用。
 */
public final class KeyEventDispatcher {
    private KeyEventDispatcher() {}

    public static final KeyEventDispatcher instance = new KeyEventDispatcher();

    public static void log(String s) {
        if(ShortCutKeys.DEBUG_KEY) Log.d("KeyEvent: " + s);
    }

    public static final int LEVEL_0_ROOT = 0;
    public static final int LEVEL_1_CHILD = 1;

    private boolean dispatch(String[] event) {
        boolean isAccepted = false;
        var key = ShortCutKeys.parse(event);
        if (key == ShortCutKeys.CombineKey.NotAccept) {
            return false;
        }
        for (IKeyDispatcherLeaf owner : KeyEventDispatchCenter.mKeyListeners) {
            if (owner.level() == LEVEL_1_CHILD) {
                isAccepted = owner.accept(key);
                if (isAccepted) {
                    break;
                }
            }
        }

        if (!isAccepted) {
            for (IKeyDispatcherLeaf owner : KeyEventDispatchCenter.mKeyListeners) {
                if (owner.level() == LEVEL_0_ROOT) {
                    isAccepted = owner.accept(key);
                    if (isAccepted) {
                        break;
                    }
                }
            }
        }
        return isAccepted;
    }

    private static final String[] STR_TO_ARR = new String[0];

    public void init(Parent root) {
        // 仅记录当前窗口已处理的快捷键，用于阻止长按重复触发；修饰键状态以当前事件为准。
        var pressedKeyCodes = new HashSet<KeyCode>(4);
        root.addEventFilter(KeyEvent.KEY_RELEASED, event -> pressedKeyCodes.remove(event.getCode()));
        var scene = root.getScene();
        if (scene != null) {
            var window = scene.getWindow();
            if (window != null) {
                window.focusedProperty().addListener((observable, oldValue, newValue) -> pressedKeyCodes.clear());
            }
        }

        root.setOnKeyPressed(event -> {
            if (event.isConsumed() || event.getCode() == KeyCode.UNDEFINED
                    || !event.isControlDown() && !event.isMetaDown()) {
                return;
            }
            if (pressedKeyCodes.contains(event.getCode())) {
                event.consume();
                return;
            }
            var keys = new ArrayList<String>(4);
            if (event.isControlDown()) {
                keys.add("Ctrl");
            }
            if (event.isMetaDown()) {
                keys.add("Command");
            }
            if (event.isShiftDown()) {
                keys.add("Shift");
            }
            if (event.isAltDown()) {
                keys.add("Alt");
            }
            keys.add(event.getCode().getName());
            if (dispatch(keys.toArray(STR_TO_ARR))) {
                pressedKeyCodes.add(event.getCode());
                event.consume();
            }
        });
    }
}
