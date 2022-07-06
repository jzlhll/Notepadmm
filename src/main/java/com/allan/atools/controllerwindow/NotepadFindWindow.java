package com.allan.atools.controllerwindow;

import com.allan.atools.bases.AbstractController;
import com.allan.baseparty.handler.TextUtils;
import com.allan.atools.beans.SubWindowCreatorInfo;
import com.allan.atools.controller.NotepadFindController;
import com.allan.atools.keyevent.KeyEventDispatcher;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.ResLocation;
import javafx.scene.Parent;
import javafx.stage.Stage;
import javafx.stage.Window;

public final class NotepadFindWindow {
    private NotepadFindWindow() {
    }

    private static class HOLD {
        private static final NotepadFindWindow Self = new NotepadFindWindow();
    }

    public static NotepadFindWindow getInstance() {
        return HOLD.Self;
    }

    private Stage findStage;
    public Window getWindow() {
        return findStage == null ? null : findStage.getScene().getWindow();
    }

    NotepadFindController controller;

    private void assetFindStage() {
        if (findStage == null) {
            SubWindowCreatorInfo info = new SubWindowCreatorInfo();
            info.title = Locales.str("search");
            info.width = 550;
            info.height = 650;
            info.iconPath = ResLocation.getURLStr("pictures", "search.png");
            info.resizable = false;
            info.sizeAndLocateCachePrefixName = "notepad_findWindow_";
            Parent root;
            try {
                controller = AbstractController.load(NotepadFindController.class);
                assert controller != null;
                root = controller.getRootView();
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("notepad find fxml error!");
            }
            findStage = AllStagesManager.getInstance().newStage(info, root, false, null);

            controller.init(findStage);
            KeyEventDispatcher.instance.init(root);
        }
    }

    /**
     * 这种显示模式show()的时候，我们将用这个填充findWindow的搜索文字
     */
    public void show(String selectedText) {
        assetFindStage();
        if (!TextUtils.isEmpty(selectedText)) {
            controller.updateSelectedText(selectedText);
        }
        if (findStage.isIconified()) {
            findStage.setIconified(false);
        } else if (findStage.isShowing()) {
            findStage.toFront();
        } else {
            findStage.show();
        }
    }

    public void show() {
        show(null);
    }

    public void hide() {
        if (controller != null) {
            controller.destroy();
        }

        if (findStage == null) {
            return;
        }
        findStage.hide();
        findStage.close();
    }
}
