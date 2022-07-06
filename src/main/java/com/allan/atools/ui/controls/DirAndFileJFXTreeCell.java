package com.allan.atools.ui.controls;

import com.allan.baseparty.utils.ReflectionUtils;
import com.allan.uilibs.controls.TreeItemEx;
import com.jfoenix.controls.JFXRippler;
import com.jfoenix.utils.JFXNodeUtils;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static com.allan.atools.utils.FileExtersions.*;

/**
 * JFXTreeCell is simple material design implementation of a tree cell.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2017-02-15
 */
public final class DirAndFileJFXTreeCell<T> extends TreeCell<T> {
    private static final class JFXRipplerImpl extends JFXRippler {
        JFXRipplerImpl(DirAndFileJFXTreeCell<?> cell) {
            super(cell);
        }

        @Override
        protected Node getMask() {
            Region clip = new Region();
            JFXNodeUtils.updateBackground(getBackground(), clip);
            double width = control.getLayoutBounds().getWidth();
            double height = control.getLayoutBounds().getHeight();
            clip.resize(width, height);
            return clip;
        }

        @Override
        protected void positionControl(Node control) {
            // do nothing
        }

        @Override
        public void releaseRipple() {
            super.releaseRipple();
        }

        Group getRippleGenerator() {
            return rippler;
        }
    }

    final JFXRipplerImpl cellRippler = new JFXRipplerImpl(this);

    private Method ripplerClearMethod; //cellRippler.rippler.clear();

    private HBox hbox;
    private final StackPane selectedPane = new StackPane();

    public DirAndFileJFXTreeCell(boolean isDir) {
        selectedPane.getStyleClass().add("selection-bar");
        selectedPane.setBackground(new Background(new BackgroundFill(Color.RED, CornerRadii.EMPTY, Insets.EMPTY)));
        selectedPane.setPrefWidth(3);
        selectedPane.setMouseTransparent(true);
        selectedProperty().addListener((o, oldVal, newVal) -> selectedPane.setVisible(newVal));

        setPadding(new Insets(0,0,0,-8)); //*** 牛逼的搞法 label移除graphic和string之间的间距
    }

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!getChildren().contains(selectedPane)) {
            getChildren().add(0, cellRippler);
            var rippler = cellRippler.getRippleGenerator();
            if (rippler != null) {
                ripplerClearMethod = ReflectionUtils.iteratorGetPrivateMethod(rippler, "clear");
            }
            if (ripplerClearMethod != null) {
                try {
                    ripplerClearMethod.invoke(rippler);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
            //cellRippler.rippler.clear();
            getChildren().add(0, selectedPane);
        }
        cellRippler.resizeRelocate(0, 0, getWidth(), getHeight());
        cellRippler.releaseRipple();
        selectedPane.resizeRelocate(0, 0, selectedPane.prefWidth(-1), getHeight());
        selectedPane.setVisible(isSelected());
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

    private void updateDisplay(T item, boolean empty) {
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

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        updateDisplay(item, empty);
        setMouseTransparent(item == null || empty);
    }
}
