package com.allan.atools.bases;

import com.allan.baseparty.Action0;

public interface IStageFocused {
    void addMainStageFocused(Action0 focused);

    void removeMainStageFocused(Action0 focused);

    void notifyStageFocused();
}
