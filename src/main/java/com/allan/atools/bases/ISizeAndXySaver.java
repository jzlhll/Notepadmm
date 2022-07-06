package com.allan.atools.bases;

import com.allan.baseparty.Action;
import com.allan.atools.beans.SizeAndXy;

public abstract class ISizeAndXySaver {
    public abstract void loadCached();
    public abstract void afterSetData();
    public abstract void setXyChangedListener(Action<SizeAndXy> changed);
    /**
     * 这里会判断是否应该显示，避免出现问题
     */
    public abstract void setSizeAndXy();
}
