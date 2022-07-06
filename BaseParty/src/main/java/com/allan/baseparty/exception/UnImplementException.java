package com.allan.baseparty.exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;

public class UnImplementException extends RuntimeException{
    public UnImplementException(String name) {
        super(name);
    }

    public UnImplementException() {
        super(getStackTraceString(new Exception()));
    }

    private static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }

        // This is to reduce the amount of log spew that apps do in the non-error
        // condition of the network being unavailable.
        Throwable t = tr;
        while (t != null) {
            if (t instanceof UnknownHostException) {
                return "";
            }
            t = t.getCause();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

}
