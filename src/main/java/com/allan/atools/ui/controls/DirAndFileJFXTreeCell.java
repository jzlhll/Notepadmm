package com.allan.atools.ui.controls;

import com.allan.uilibs.controls.TreeItemEx;
import com.jfoenix.controls.JFXTreeCell;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.allan.atools.utils.FileExtersions.*;

/**
 * 工作区文件树单元格，按文件类型显示样式并保留横向滚动余量。
 */
public final class DirAndFileJFXTreeCell<T> extends JFXTreeCell<T> {
    private static final double HORIZONTAL_SCROLL_END_SPACE = 32;

    private HBox hbox;

    public DirAndFileJFXTreeCell(boolean isDir) {
        setTextOverrun(OverrunStyle.CLIP);
        setPadding(new Insets(0,0,0,-8)); // 移除 graphic 与文字之间的多余间距
    }

    @Override
    protected double computePrefWidth(double height) {
        double width = super.computePrefWidth(height);
        return getText() == null ? width : width + HORIZONTAL_SCROLL_END_SPACE;
    }

    public enum OpenMode {
        Text,
        Image,
        Null
    }

    public static OpenMode IsSupportOpenFile(File file) {
        String extension;
        try {
            var name= file.getName();
            extension = file.getName().substring(name.lastIndexOf('.') + 1).toLowerCase();
        } catch (Exception e) {
            //
            extension = null;
        }

        if (extension == null) {
            return OpenMode.Text; //没有后缀
        }

        if (ImageExtensionList.contains(extension)) {
            return OpenMode.Image;
        }

        if (MediaExtensionList.contains(extension) || RefuseExtensionList.contains(extension)) {
            return OpenMode.Null;
        }
        return OpenMode.Text;
    }

    private static String ExtensionToStyle(@NotNull String extension) {
        if (CodingExtensionList.contains(extension)) {
            return "tree-cell-coding";
        }
        if (ImageExtensionList.contains(extension)) {
            return "tree-cell-image";
        }
        if (MediaExtensionList.contains(extension) || RefuseExtensionList.contains(extension)) {
            return "tree-cell-media";
        }
        if (MajorExtensionList.contains(extension)) {
            return "tree-cell-major";
        }

        return "tree-cell-other";
    }

    @Override
    protected void updateDisplay(T item, boolean empty) {
        if (item == null || empty) {
            hbox = null;
            setText(null);
            setGraphic(null);
        } else {
            TreeItem<T> treeItem = getTreeItem();
            boolean isDir = false;
            if (treeItem instanceof TreeItemEx<?> treeItemEx) {
                if(treeItemEx.ex instanceof File file) {
                    isDir = file.isDirectory();
                }
            }

            if (treeItem != null && treeItem.getGraphic() != null) {
                if (item instanceof Node) {
                    setText(null);
                    if (hbox == null) {
                        hbox = new HBox(-10);
                    }
                    hbox.getChildren().setAll(treeItem.getGraphic(), (Node) item);
                    setGraphic(hbox);
                } else {
                    hbox = null;
                    setText(item.toString());
                    setGraphic(treeItem.getGraphic());
                }
            } else {
                hbox = null;
                if (item instanceof Node) {
                    setText(null);
                    setGraphic((Node) item);
                } else {
                    String s = item.toString();
                    setText(s);
                    setGraphic(null);
                    getStyleClass().removeAll("tree-cell", "tree-cell-dir", "tree-cell-major", "tree-cell-media", "tree-cell-image", "tree-cell-coding", "tree-cell-other");

                    if ((isDir && s.length() > 2 && s.charAt(2) == '.') || (!isDir && s.length() > 0 && s.charAt(0) == '.')) {
                        getStyleClass().add("tree-cell-other");
                    } else if (isDir) {
                        getStyleClass().add("tree-cell-dir");
                    } else {
                        String ex;
                        try {
                            ex = s.substring(s.lastIndexOf('.') + 1);
                        } catch (Exception e) {
                            //ignore
                            ex = s;
                        }
                        getStyleClass().add(ExtensionToStyle(ex.toLowerCase()));
                    }
                }
            }
        }
    }
}
