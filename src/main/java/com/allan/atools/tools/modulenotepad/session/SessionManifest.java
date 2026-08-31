package com.allan.atools.tools.modulenotepad.session;

import java.util.ArrayList;
import java.util.List;

public final class SessionManifest {
    public int version = 1;
    public String activeSessionId;
    public List<SessionTab> tabs = new ArrayList<>();
}
