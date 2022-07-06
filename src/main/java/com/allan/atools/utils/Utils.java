package com.allan.atools.utils;

import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.baseparty.ActionR0;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public final class Utils {
    public record StringAndActionR0Str(String newFullPath, ActionR0<String> run) {}

    public static StringAndActionR0Str rename(File sourceFile, String newFileName) {
        if (!sourceFile.exists() || !sourceFile.isFile()) { // 判断原文件是否存在（防止文件名冲突）
            return null;
        }
        if (TextUtils.isEmpty(newFileName)) {
            return null;
        }
        var fullPath = sourceFile.getAbsolutePath();
        var oldName = sourceFile.getName();

        String dir = fullPath.substring(0, fullPath.length() - oldName.length());
        String newFullPath = dir + newFileName;
        var newFile = new File(newFullPath);

        ActionR0<String> r = () -> {
            try {
                if(sourceFile.renameTo(newFile)) {
                    return newFullPath;
                } else {
                    return null;
                }
            } catch (Exception err) {
                err.printStackTrace();
                return null;
            }
        };

        if (newFile.exists()) {
            return new StringAndActionR0Str(null, r);
        } else {
            return new StringAndActionR0Str(r.invoke(), null);
        }
    }

    public static void openTerminal() {
        if (ResLocation.isWindow) {
            openWindowsTerminal();
        } else {
            openUnixTerminal();
        }
    }

    public static String getStrBetween(String s, String left, String right) {
        //    src: url("font_custom34.ttf");
        var index1 = s.indexOf(left);
        var index2 = s.indexOf(right);
        return s.substring(index1 + left.length(), index2);
    }

    public static void openTerminalHere(File file) {
        if (ResLocation.isWindow) {
            openWindowsTerminalHere(file);
        } else {
            openUnixTerminalHere(file);
        }
    }

    private static void openWindowsTerminal() {
        try {
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("cmd.exe /c start", null);
        } catch (IOException e) {
            final String errorMessage = "Can't Launch Windows Terminal";
            //Debugging Console
            //Warning UI
            SnackbarUtils.show(errorMessage);
        }
    }

    /**
     * @param file : open windows terminal in file path
     */
    private static void openWindowsTerminalHere(File file) {
        try {
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("cmd.exe /c start", null, file);
        } catch (IOException e) {
            final String errorMessage = "Can't Launch Windows Terminal Here";
            //Debugging Console
            //Warning UI
            SnackbarUtils.show(errorMessage);
        }
    }

    private static void openUnixTerminal() {
        try {
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("bash -c start", null);
        } catch (IOException e) {
            final String errorMessage = "Can't Launch Unix Based Terminal";
            //Debugging Console
            //Warning UI
            SnackbarUtils.show(errorMessage);
        }
    }

    /**
     * @param file : open Unix terminal in file path
     */
    private static void openUnixTerminalHere(File file) {
        try {
            Runtime runtime = Runtime.getRuntime();
            runtime.exec("bash -c start", null, file);
        } catch (IOException e) {
            final String errorMessage = "Can't Launch Unix Based Terminal Here";
            //Debugging Console
            //Warning UI
            SnackbarUtils.show(errorMessage);
        }
    }

    /**
     * Take ScreenShot for Editor Screen
     */
    public static void captureScreenShot() {
        try {
            Robot robot = new Robot();
            int xPosition = (int) AllStagesManager.getInstance().getMainStage().getX();
            int yPosition = (int) AllStagesManager.getInstance().getMainStage().getY();
            int width = (int) AllStagesManager.getInstance().getMainStage().getWidth();
            int height = (int) AllStagesManager.getInstance().getMainStage().getHeight();
            Rectangle screenRectangle = new Rectangle(xPosition, yPosition, width, height);
            BufferedImage bufferedImage = robot.createScreenCapture(screenRectangle);
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("screenshot");
            File image = fileChooser.showSaveDialog(null);
            ImageIO.write(bufferedImage, "png", image);
        } catch (Exception ex) {
            String errorMessage = "Can't Capture Screen";
            //Debugging Warning
            Log.e(ex.getMessage());
            //Debugging For UI
            SnackbarUtils.show(errorMessage);
        }
    }

    /**
     * @param directory : The Path To Open it in new window
     */
    public static void openFolderExplore(File directory){
        Desktop desktop = Desktop.getDesktop();
        try {
            desktop.open(directory);
        } catch (IllegalArgumentException | IOException e) {
            String errorMessage = "File Not Found";
            SnackbarUtils.show(errorMessage);
        }
    }

    public static void getAllFilesInDir(Set<File> files, File dir) {
        File[] fs = dir.listFiles();
        if (fs != null) {
            for (File f : fs) {
                if (f.isDirectory())
                    getAllFilesInDir(files, f);
                if (f.isFile()) {
                    files.add(f);
                }
            }
        }
    }

    public static String combine(String parent, String name) {
        if (ResLocation.isWindow) {
            if (!parent.endsWith("\\")) {
                parent += '\\';
            }
            parent += name;
        } else {
            if (!parent.endsWith("/")) {
                parent += '/';
            }
            parent += name;
        }
        return parent;
    }

    public static void getAllFilesInDirWithFilter(Set<File> files, File dir, IFileFilter fileFilter, IDirFilter dirFilter) {
        File[] fs = dir.listFiles();
        if (fs != null)
            for (File f : fs) {
                if (f.isDirectory()) {

                    if (dirFilter == null || dirFilter.filter(f)) {
                        getAllFilesInDirWithFilter(files, f, fileFilter, dirFilter);
                    }
                } else if (f.isFile()) {

                    if (fileFilter == null || fileFilter.filter(f))
                        files.add(f);
                }
            }
    }

    public interface IDirFilter {
        boolean filter(File f);
    }

    public interface IFileFilter {
        boolean filter(File f);
    }
}