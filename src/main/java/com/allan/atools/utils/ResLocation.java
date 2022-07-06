package com.allan.atools.utils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ResLocation {
    public static final boolean isWindow = System.getProperty("os.name").toLowerCase().contains("win");
    public static final boolean isOsx = System.getProperty("os.name").toLowerCase().contains("mac");

    private static String RESOURCES_PATH;
    public static String getRESOURCES_ROOT_PATH() {
        if (RESOURCES_PATH == null) {
            initBeforeAnything();
        }
        return RESOURCES_PATH;
    }

    private static void initBeforeAnything() {
        String projectResPath = (new File("")).getAbsolutePath() + File.separatorChar
                + "src" + File.separatorChar + "main" + File.separatorChar + "resources" + File.separatorChar;
        if ((new File(projectResPath)).isDirectory()) {
            RESOURCES_PATH = projectResPath;
        } else {
            if (isOsx) {
                String path = Objects.requireNonNull(ResLocation.class.getResource("")).getPath();
                path = path.replace("file://", "");
                String tp = path.substring(0, path.indexOf("/mods") + 1) + "resources/";
                if ((new File(tp)).isDirectory()) {
                    RESOURCES_PATH = tp;
                } else {
                    tp = path.substring(0, path.indexOf(".jar!"));
                    String[] splits = tp.split("/");
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0, len = splits.length; i < len - 2; i++)
                        sb.append(splits[i]).append('/');
                    sb.append("resources/");
                    RESOURCES_PATH = sb.toString();
                }
            } else if (isWindow) {
                File directory = new File("");//参数为空
                String path;
                try {
                    path = directory.getCanonicalPath();//标准的路径 ;
                    path = path.endsWith(File.separator) ? path.substring(0, path.length() - 1) : path;
                    path = IO.combinePathWithInclineEnd(path, "app", "resources");
                    RESOURCES_PATH = path;
                } catch (IOException e) {
                    e.printStackTrace();
                    throw new RuntimeException("todo 修改 RootPath");
                }
            } else {
                RESOURCES_PATH = "";
                Log.e("todo 修改rootPath");
                throw new RuntimeException("todo 修改 RootPath");
            }
        }
    }

    public static String getRealPath(String... paths) {
        if (paths == null || paths.length == 0)
            return getRESOURCES_ROOT_PATH();
        StringBuilder sb = new StringBuilder(getRESOURCES_ROOT_PATH());
        int i = 0;
        for (int len = paths.length; i < len - 1; i++)
            sb.append(paths[i]).append(File.separatorChar);
        sb.append(paths[i]);
        return sb.toString();
    }

    public static URL getURL(String... paths) throws MalformedURLException {
        String r = getRealPath(paths);
        return (new File(r)).toURI().toURL();
    }

    public static URL getURLByRealPath(String realPath) throws MalformedURLException {
        return (new File(realPath)).toURI().toURL();
    }

    public static String getURLStr(String... paths) {
        try {
            var uri = getURL(paths);
            return convert(uri.toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String file2UrlStr(File file) {
        try {
            return convert(file.toURI().toURL().toString());
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String convert(String url) {
        return URLDecoder.decode(url, StandardCharsets.UTF_8);
        //return url.replaceAll("%20", " ");
    }

}
