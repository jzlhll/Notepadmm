package com.allan.atools.utils;

import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.ui.SnackbarUtils;
import com.allan.baseparty.ActionR0;
import javafx.application.Platform;
import javafx.stage.FileChooser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public final class Utils {
    private static final String APP_VERSION = "2.0";

    public static String getAppVersion() {
        return APP_VERSION;
    }

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

    public static String getStrBetween(String s, String left, String right) {
        //    src: url("font_custom34.ttf");
        var index1 = s.indexOf(left);
        var index2 = s.indexOf(right);
        return s.substring(index1 + left.length(), index2);
    }

    /** 在系统终端中打开目录，路径通过独立参数或工作目录传递。 */
    public static void openTerminalHere(File directory) {
        try {
            if (!directory.isDirectory()) throw new IOException("Terminal directory does not exist: " + directory);
            ProcessBuilder builder;
            if (ResLocation.isWindow) {
                builder = new ProcessBuilder("cmd.exe", "/c", "start", "\"\"", "cmd.exe").directory(directory);
            } else if (ResLocation.isOsx) {
                builder = new ProcessBuilder("/usr/bin/open", "-a", "Terminal", directory.getAbsolutePath());
            } else {
                throw new IOException("Unsupported terminal platform");
            }
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start().onExit().thenAccept(process -> {
                        if (process.exitValue() != 0) {
                            Log.e("Open terminal failed, exit code: " + process.exitValue() + ", directory: " + directory);
                            Platform.runLater(() -> SnackbarUtils.show(Locales.str("editor.openTerminalFailed")));
                        }
                    });
        } catch (IOException | SecurityException e) {
            Log.e("Open terminal failed, directory: " + directory, e);
            SnackbarUtils.show(Locales.str("editor.openTerminalFailed"));
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

    /** 在系统文件管理器中定位目标文件。 */
    public static void openFolderExplore(File file) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                try {
                    desktop.browseFileDirectory(file);
                    return;
                } catch (IllegalArgumentException | UnsupportedOperationException | SecurityException ignored) {
                    // 定位失败时回退为打开目标所在目录。
                }
            }

            File directory = file.isDirectory() ? file : file.getParentFile();
            if (directory == null) {
                throw new IllegalArgumentException();
            }
            desktop.open(directory);
        } catch (IllegalArgumentException | IOException | UnsupportedOperationException | SecurityException e) {
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
