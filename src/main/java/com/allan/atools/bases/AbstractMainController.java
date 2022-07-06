package com.allan.atools.bases;

import com.allan.baseparty.Action0;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractMainController extends AbstractController implements IStageFocused{
    private final Object focusLock = new Object();

    private final List<WeakReference<Action0>> focusedList = new ArrayList<>(4);

    @Override
    public void addMainStageFocused(Action0 focused) {
        synchronized (focusLock) {
            focusedList.removeIf(cur -> cur.get() == null || cur.get() == focused);
            focusedList.add(new WeakReference<>(focused));
        }
    }

    @Override
    public void removeMainStageFocused(Action0 focused) {
        synchronized (focusLock) {
            focusedList.removeIf(cur -> cur.get() == null || cur.get() == focused);
        }
    }

    @Override
    public void notifyStageFocused() {
        synchronized (focusLock) {
            var it = focusedList.iterator();
            while (it.hasNext()) {
                var action = it.next();
                var real = action.get();
                if (real != null) {
                    real.invoke();
                } else {
                    it.remove();
                }
            }
        }
    }
}
