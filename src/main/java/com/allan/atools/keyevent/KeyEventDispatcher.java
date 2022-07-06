package com.allan.atools.keyevent;

import com.allan.atools.KeyEventDispatchCenter;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.utils.Log;
import javafx.scene.Parent;

import java.util.Vector;

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

    private void dispatch(String[] event) {
        boolean isAccepted = false;
        var key = ShortCutKeys.parse(event);
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
    }

    private static final Vector<String> pressedKeyCodes = new Vector<>(2);

    public void init(Parent root) {
        root.setOnKeyReleased(event -> {
            String[] sb;
            StringBuilder stringBuilder = new StringBuilder();
            var n = event.getCode().getName();
            synchronized (pressedKeyCodes) {
                boolean isEq = false;
                for (var keyCode : pressedKeyCodes) {
                    if (TextUtils.equals(keyCode, n)) {
                        isEq = true;break;
                    }
                }
                if (!isEq) {
                    log("Release added n " + n);
                    pressedKeyCodes.add(n);
                }

                pressedKeyCodes.forEach(s -> stringBuilder.append(s).append("+"));
                sb = pressedKeyCodes.toArray(new String[0]);

                pressedKeyCodes.remove(n);
                log("pressedKeyCodes clear: " + stringBuilder + " siz= " + pressedKeyCodes.size());
            }

            dispatch(sb);
        });

//        root.setOnKeyTyped(event -> {
//            dispatch(event, KeyMode.Typed);
//        });

        root.setOnKeyPressed(event -> {
            synchronized (pressedKeyCodes) {
                pressedKeyCodes.add(event.getCode().getName());
                log("%%Pressed after added: " + event.getCode().getName() + " siz " + pressedKeyCodes.size());
            }
            //dispatch(event, KeyMode.Pressed);
        });
    }
}
