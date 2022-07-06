package com.allan.atools.tools.sockets;

import java.io.*;
import java.net.Socket;

public abstract class BaseSocket {
    static final int ST_NULL = -1;
    static final int ST_NULL_TO_PRE = -2;
    static final int ST_PREPARED = 0;
    static final int ST_PREPARED_TO_WORK = 1;
    static final int ST_WORK = 2;
    static final int ST_WORK_NULL = -3;

    public enum BlockingRetCode {
        ALREADY_SUC,
        FAIL,
        BLOCKING_FAIL_OVER,
        BLOCKING_SUC_OVER;
    }

    protected int mStatus = -1;

    protected Socket socket;

    private InputStream inputStream;

    private OutputStream outputStream;
    private PrintWriter printWriter;
    private InputStreamReader inputStreamReader;
    protected BufferedReader bufferedReader;

    protected final void createStreams(Socket socket) throws IOException {
        this.inputStream = socket.getInputStream();
        this.inputStreamReader = new InputStreamReader(this.inputStream);
        this.bufferedReader = new BufferedReader(this.inputStreamReader);

        this.outputStream = socket.getOutputStream();
        this.printWriter = new PrintWriter(this.outputStream);
    }


    public abstract boolean prepareSocketAndStTh(ISocketBeforeEnd paramISocketBeforeEnd);


    public final BlockingRetCode workBlocking(ISocketMessagePipe pipe) {
        if (this.mStatus == 2) {
            return BlockingRetCode.ALREADY_SUC;
        }

        if (this.mStatus != 0) {
            return BlockingRetCode.FAIL;
        }

        this.mStatus = 1;

        try {
            createStreams(this.socket);
            this.mStatus = 2;
            whileTalk(pipe);
        } catch (IOException e) {
            e.printStackTrace();
            return BlockingRetCode.FAIL;
        }

        return BlockingRetCode.BLOCKING_SUC_OVER;
    }

    protected void whileTalk(ISocketMessagePipe pipe) {
        System.out.println("end of whileTalk.");
    }

    public final void sendToRemote(String s) {
        if (this.mStatus == 2) {
            this.printWriter.write(s);
            this.printWriter.flush();
        }
    }

    public final void close() {
        if (this.mStatus != 2) {
            return;
        }
        this.mStatus = -3;
        try {
            this.socket.shutdownInput();


            sendToRemote(myName() + "byte!");

            this.printWriter.close();
            this.outputStream.close();

            this.bufferedReader.close();
            this.inputStreamReader.close();
            this.inputStream.close();
            this.socket.close();

            closeChild();

            this.mStatus = -1;
            System.out.println("服务端正常关闭！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected abstract void closeChild();

    public final int getStatus() {
        return this.mStatus;
    }

    protected abstract String myName();
}
