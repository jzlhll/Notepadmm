package com.allan.atools.richtext.codearea;

import com.allan.baseparty.collections.SimpleNoNullList;

public abstract class BaseChanged<At> {
    protected SimpleNoNullList<At> mActions;

    protected boolean isSet = false;
    public synchronized void addAction(At action) {
        if (mActions == null) {
            mActions = new SimpleNoNullList<>();
        }
        if (!mActions.contains(action)) {
            mActions.add(action);
            modifiedActions();
        }
    }

    public synchronized void removeAction(At action) {
        if (mActions != null) {
            mActions.remove(action);
            if (mActions.size() == 0) {
                mActions = null;
            }
            modifiedActions();
        }
    }

    public abstract void modifiedActions();

    public abstract void destroy();
    public abstract void init();
}
