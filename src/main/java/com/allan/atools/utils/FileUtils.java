package com.allan.atools.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public final class FileUtils {
    /* public static List<String> readLines(String filename) {
        EncodingUtil.EncodingInfo info = EncodingUtil.ultimateEncodeDetect(filename);
        List<String> nameList = null;
        boolean isFailed = false;
        try (var reader = Files.newBufferedReader(Path.of(filename), Charset.forName(info.encoding))) {
            if(info.bomSize > 0) reader.skip(info.bomSize); //skip bom size
            String tmp = reader.readLine();
            nameList = new ArrayList<>();
            while (tmp != null && tmp.trim().length() > 0) {
                nameList.add(tmp);
                tmp = reader.readLine();
            }
        } catch (MalformedInputException e1) {
            isFailed = true;
            e1.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!isFailed) {
            return nameList;
        }
        return readLines1_6(filename);
    } */

    public static List<String> readLines(String filename) {
        return readLines(filename, null, null);
    }

    public static List<String> readLines(String filename, String forceEncoding, String[] callbackEncoding) {
        EncodingUtil.EncodingInfo info;
        if (forceEncoding != null) {
            info = EncodingUtil.forceEncoding(forceEncoding);
        } else {
            info = EncodingUtil.ultimateEncodeDetect(filename);
        }

        List<String> nameList = new ArrayList<String>();
        FileInputStream fis = null;
        BufferedReader reader = null;

        try {
            fis = new FileInputStream(filename);
            reader = new BufferedReader(new InputStreamReader(fis, info.encoding));
            if(callbackEncoding != null) callbackEncoding[0] = info.encoding;
            //对比有bom的，本来打算跳过字节个数。目前来看，这些不用跳过，java api指定了编码它会帮忙跳过。
            //if(info.bomSize() > 0) reader.skip(info.bomSize());
            String tmp = reader.readLine();
            while (tmp != null) {
                nameList.add(tmp);
                tmp = reader.readLine();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("encoding action" + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return nameList;
    }

    public static StringBuilder readString(String filename) {
        return readString(filename, null, null);
    }

    public static StringBuilder readString(String filename, String encoding) {
        return readString(filename, encoding, null);
    }

    public static StringBuilder readString(String filename, String[] callbackEncoding) {
        return readString(filename, null, callbackEncoding);
    }

    public static StringBuilder readString(String filename, String forceEncoding, String[] callbackEncoding) {
        StringBuilder sb = new StringBuilder();
        List<String> list = readLines(filename, forceEncoding, callbackEncoding);
        int i = 0, count = list.size();
        for (; i < count - 1; i++) {
            sb.append(list.get(i)).append(System.lineSeparator());
        }
        if (list.size() > 0) {
            sb.append(list.get(i));
        }
        return sb;
    }
}

