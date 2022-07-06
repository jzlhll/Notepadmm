package com.allan.atools.tools.moduletransfer;

import com.allan.atools.tools.sockets.BaseSocket;
import com.allan.atools.tools.sockets.ISocketMessagePipe;

import java.util.ArrayList;
import java.util.List;

public final class TransferHelper
        implements ISocketMessagePipe {
    private BaseSocket mSocketServer;
    private final List<BaseSocket> mSocketClients = new ArrayList<>(1);

    public final void createAServer(IOver over) {
    }

    public void onMessage(String msg) {
        System.out.println(msg);
    }

    public interface IOver {
        void over();
    }
}
