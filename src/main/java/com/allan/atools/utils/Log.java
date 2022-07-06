package com.allan.atools.utils;

import com.allan.atools.UIContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.Calendar;

public final class Log {
    private Log() {}

    private static final boolean DEBUG = UIContext.DEBUG;
    private static final boolean WARN = UIContext.WARN;

    public static void v(String log) {
        if (!DEBUG) {return;}
        System.out.printf("%s: DEBUG: %s\n", time(), log);
    }

    public static void v(String tag, String log) {
        if (!DEBUG) {return;}
        System.out.printf("%s: DEBUG: [%s]: %s\n", time(), tag, log);
    }

    public static void d(String log) {
        if (!DEBUG) {return;}
        System.out.printf("%s: INFO: %s\n", time(), log);
    }

    public static void d(String tag, String log) {
        if (!DEBUG) {return;}
        System.out.printf("%s: INFO: [%s]: %s\n", time(), tag, log);
    }

    public static void w(String log) {
        if (!WARN) {return;}
        System.out.printf("%s: WARN: %s\n", time(), log);
    }

    public static void w(String tag, String log) {
        if (!WARN) {return;}
        System.out.printf("%s: WARN: [%s]: %s\n", time(), tag, log);
    }

    public static void e(String log) {
        log = String.format("%s: ERROR: %s", time(), log);
        System.out.println(log);
        FileLog.write(log, false);
    }

    public static void w(String log, Throwable ex) {
        log = String.format("%s: WARN: %s %s", time(), log, getStackTraceString(ex));
        System.out.println(log);
    }

    public static void e(String log, Throwable ex) {
        log = String.format("%s: ERROR: %s %s", time(), log, getStackTraceString(ex));
        System.out.println(log);
        FileLog.write(log, false);
    }

    public static String getStackTraceString(Throwable tr) {
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

    public static String time() {
        Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR_OF_DAY);
        int minute = c.get(Calendar.MINUTE);
        int second = c.get(Calendar.SECOND);
        int ms = c.get(Calendar.MILLISECOND);
        return String.format("%02d:%02d:%02d.%02d", hour, minute, second, ms / 10);
    }

    public static void largeLog(String s) {
        if (s == null)
            return;
        int index = 1000;
        int len = s.length();
        while (index < len) {
            System.out.println(s.substring(index - 1000, index));
            index += 1000;
        }
        System.out.println(s.substring(index - 1000));
    }

    public static void largeLogWithLine(String s) {
        if (s == null)
            return;
        String[] ss = s.split("\n");
        for (String line : ss)
            System.out.println(line);
    }
}
