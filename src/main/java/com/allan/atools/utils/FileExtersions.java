package com.allan.atools.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class FileExtersions {
    public static final List<String> RefuseExtensionList = new ArrayList<>(32);
    public static final List<String> MajorExtensionList = new ArrayList<>(8);
    public static final List<String> CodingExtensionList = new ArrayList<>(32);
    public static final List<String> MediaExtensionList = new ArrayList<>(32);
    public static final List<String> ImageExtensionList = new ArrayList<>(4);

    public static String getExtensionLowerCase(String s) {
        String ex;
        try {
            ex = s.substring(s.lastIndexOf('.') + 1);
        } catch (Exception e) {
            //ignore
            ex = s;
        }
        ex = ex.toLowerCase();
        return ex;
    }

    public static boolean isSupportTxt(String ex) {
        return MajorExtensionList.contains(ex) || CodingExtensionList.contains(ex);
    }

    public static boolean isSupportPicture(String ex) {
        return ImageExtensionList.contains(ex);
    }

    static {
        MajorExtensionList.add("pro");
        MajorExtensionList.add("rtf");
        MajorExtensionList.add("log");
        MajorExtensionList.add("txt");
        MajorExtensionList.add("list");
        MajorExtensionList.add("md");

        CodingExtensionList.add("java");
        CodingExtensionList.add("c");
        CodingExtensionList.add("cc");
        CodingExtensionList.add("cpp");
        CodingExtensionList.add("hpp");
        CodingExtensionList.add("h");
        CodingExtensionList.add("xml");
        CodingExtensionList.add("fxml");
        CodingExtensionList.add("css");
        CodingExtensionList.add("sh");
        CodingExtensionList.add("bat");
        CodingExtensionList.add("json");
        CodingExtensionList.add("cs");
        CodingExtensionList.add("php");
        CodingExtensionList.add("py");
        CodingExtensionList.add("pyc");
        CodingExtensionList.add("kt");
        CodingExtensionList.add("go");
        CodingExtensionList.add("dart");
        CodingExtensionList.add("html");

        RefuseExtensionList.add("apk");
        RefuseExtensionList.add("dmg");
        RefuseExtensionList.add("pkg");
        RefuseExtensionList.add("aar");
        RefuseExtensionList.add("exe");
        RefuseExtensionList.add("tar");
        RefuseExtensionList.add("tgz");
        RefuseExtensionList.add("gz");
        RefuseExtensionList.add("7z");
        RefuseExtensionList.add("bz");
        RefuseExtensionList.add("jar");
        RefuseExtensionList.add("zip");
        RefuseExtensionList.add("rar");
        RefuseExtensionList.add("doc");
        RefuseExtensionList.add("docx");
        RefuseExtensionList.add("pdf");
        RefuseExtensionList.add("xls");
        RefuseExtensionList.add("xlsx");
        RefuseExtensionList.add("ppt");
        RefuseExtensionList.add("pptx");
        RefuseExtensionList.add("so");
        RefuseExtensionList.add("dll");

        ImageExtensionList.add("jpg");
        ImageExtensionList.add("ico");
        ImageExtensionList.add("jpeg");
        ImageExtensionList.add("gif");
        ImageExtensionList.add("bmp");
        ImageExtensionList.add("png");
        ImageExtensionList.add("webp");

        MediaExtensionList.add("icns");
        MediaExtensionList.add("psd");
        MediaExtensionList.add("mov");
        MediaExtensionList.add("mpg");
        MediaExtensionList.add("mpeg");
        MediaExtensionList.add("mkv");
        MediaExtensionList.add("webp");
        MediaExtensionList.add("mp4");
        MediaExtensionList.add("m4v");
        MediaExtensionList.add("mp4v");
        MediaExtensionList.add("wmv");
        MediaExtensionList.add("rm");
        MediaExtensionList.add("rmvb");
        MediaExtensionList.add("flc");
        MediaExtensionList.add("avi");
        MediaExtensionList.add("flv");
        MediaExtensionList.add("yuv");
        MediaExtensionList.add("aac");
        MediaExtensionList.add("flac");
        MediaExtensionList.add("m4a");
        MediaExtensionList.add("mp3");
        MediaExtensionList.add("wav");
        MediaExtensionList.add("wma");
    }
}
