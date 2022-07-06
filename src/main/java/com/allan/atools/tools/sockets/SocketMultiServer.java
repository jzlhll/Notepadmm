package com.allan.atools.tools.sockets;

import com.allan.baseparty.handler.Handler;
import com.allan.baseparty.handler.Looper;
import com.allan.baseparty.handler.Message;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class SocketMultiServer
        extends BaseSocket {
    private static final int PORT = 19847;
    private ServerSocket serverSocket;
    private static final String TAG = SocketMultiServer.class.getSimpleName();

    private final List<SocketServer> mSockServers = new ArrayList<>(1);
    private ISocketBeforeEnd mEnd;
    private static final int MSG_INIT = 0;
    private static final int MSG_ACCEPT = 1;
    private static final int MSG_STOP = 2;

    private static class MyHandler
            extends Handler {
        private WeakReference<SocketMultiServer> mHold;

        MyHandler(SocketMultiServer holder, Looper looper) {
            super(looper);
            this.mHold = new WeakReference<>(holder);
        }

        public void handleMessage(Message msg) {
            SocketMultiServer self = this.mHold.get();
            if (self == null) {
                return;
            }

            if (msg.what == 0) {
                try {
                    InetAddress address = InetAddress.getLocalHost();
                    String n = address.getHostName();
                    String addr = address.getHostAddress();
                    SocketServer.SocketCreatedInfo info = new SocketServer.SocketCreatedInfo();
                    info.name = n;
                    info.ip = addr;
                    info.port = 19847;

                    if (self.mEnd != null) {
                        self.mEnd.onEnd(info);
                    }

                    self.mStatus = 0;
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            } else {
                if (msg.what == 1)
                    while (true) {
                        try {
                            while (true) {
                                self.serverSocket = new ServerSocket(19847);
                                Socket socket = self.serverSocket.accept();
                                SocketServer ss = new SocketServer(socket);
                                self.mSockServers.add(ss);

                                break;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                if (msg.what == 2) ;
            }

        }
    }

    public boolean prepareSocketAndStTh(ISocketBeforeEnd end) {
        if (this.mStatus != -1) {
            return false;
        }

        this.mStatus = -2;
        this.mEnd = end;
        return true;
    }

    protected void closeChild() {
    }

    protected String myName() {
        return TAG;
    }

    protected void whileTalk(ISocketMessagePipe pipe) {
        super.whileTalk(pipe);
    }
}
