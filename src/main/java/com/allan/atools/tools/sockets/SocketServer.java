package com.allan.atools.tools.sockets;

import java.io.IOException;
import java.net.Socket;

public final class SocketServer extends BaseSocket {
    private static final String TAG = "local@Server: ";
    private final Socket client;

    public SocketServer(Socket socket) {
        this.client = socket;
    }

    public static class SocketCreatedInfo {
        public int port;
        public String ip;
        public String name;

        public String toString() {
            return "SocketCreatedInfo{port=" + this.port + ", ip='" + this.ip + "', name='" + this.name + "'}";
        }
    }

    public boolean prepareSocketAndStTh(ISocketBeforeEnd end) {
        throw new RuntimeException("Should not be called");
    }

    protected void whileTalk(ISocketMessagePipe pipe) {
        try {
            String info;
            while ((info = this.bufferedReader.readLine()) != null) {
                if ("#eof#\n".equals(info)) {
                    sendToRemote(myName() + " Thanks for your once msg.\n#eof#\n");

                    continue;
                }
                System.out.println(myName() + " 接收来自客户端的信息：" + myName());
                if (pipe != null) {
                    pipe.onMessage(info);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeChild() {
    }

    protected String myName() {
        return "local@Server: ";
    }
}
