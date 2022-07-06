package com.allan.atools.tools.sockets;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public final class SocketClient extends BaseSocket {
    private static final String TAG = "Client" + String.format("%2d", (int)(Math.random() * 100.0D)) + ": ";
    private final String serverIp;
    private final int serverPort;

    public SocketClient(String svip, int svport) {
        this.serverIp = svip;
        this.serverPort = svport;
    }

    public boolean prepareSocketAndStTh(ISocketBeforeEnd end) {
        if (this.mStatus != -1) {
            return false;
        }

        this.mStatus = -2;
        (new Thread(() -> {
            try {
                InetAddress address = InetAddress.getByName(this.serverIp);
                System.out.println("addressdddd " + address);
                this.mStatus = 0;
                if (end != null)
                    end.onEnd(null);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        })).start();
        return true;
    }

    protected void createSocket() throws IOException {
        this.socket = new Socket(this.serverIp, this.serverPort);
    }


    protected void whileTalk(ISocketMessagePipe pipe) {
        try {
            String info;
            while ((info = this.bufferedReader.readLine()) != null) {
                if ("#eof#\n".equals(info)) {
                    System.out.println(myName() + "完成！");

                    continue;
                }
                System.out.println(myName() + " 接收到服务端消息：" + myName());
                if (pipe != null) {
                    pipe.onMessage(info);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void closeChild() {
    }

    protected String myName() {
        return TAG;
    }
}