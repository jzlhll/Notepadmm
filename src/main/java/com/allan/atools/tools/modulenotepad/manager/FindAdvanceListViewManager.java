package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadFindController;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.controller.NotepadController;
import com.allan.uilibs.controls.MyHBox;
import com.allan.uilibs.controls.MyJFXButton;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.tools.modulenotepad.StaticsProf;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;

import java.util.LinkedList;

public final class FindAdvanceListViewManager {
    private final FindAdvanceSearchParamsManager advanceSearchParamsManager;
    public FindAdvanceSearchParamsManager getAdvanceSearchParamsManager() {
        return advanceSearchParamsManager;
    }

    private final NotepadFindController controller;

    public FindAdvanceListViewManager(NotepadFindController controller) {
        advanceSearchParamsManager = new FindAdvanceSearchParamsManager(controller, this::createItem);
        this.controller = controller;
    }

    private HBox createItem(SearchParams params) {
        var hbox = new MyHBox<SearchParams>();
        {
            hbox.setEx(params);
            hbox.setSpacing(3);
            hbox.setAlignment(Pos.CENTER);
            hbox.getStyleClass().add("custom-main-bg");
            hbox.setPrefHeight(36);
        }

        var check = new JFXCheckBox();
        check.setPrefWidth(36);
        check.setSelected(params.enable);
        check.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            params.enable = t1;
            advanceSearchParamsManager.saveParam();
        });

        var word = new JFXTextField();
        word.setPrefWidth(179);
        word.setText(params.words);
        word.textProperty().addListener((observableValue, s, t1) -> {
            params.words = t1;
            advanceSearchParamsManager.saveParam();
        });

        var caseMatch = new JFXCheckBox();
        caseMatch.setPrefWidth(36);
        caseMatch.setSelected(params.useCaseMatch);
        caseMatch.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            params.useCaseMatch = t1;
            advanceSearchParamsManager.saveParam();
        });

        var useWholeWords = new JFXCheckBox();
        useWholeWords.setPrefWidth(36);
        useWholeWords.setSelected(params.useWholeWords);
        useWholeWords.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            params.useWholeWords = t1;
            advanceSearchParamsManager.saveParam();
        });

        var highlight = new JFXCheckBox();
        highlight.setPrefWidth(36);
        highlight.setSelected(params.highLight);
        highlight.selectedProperty().addListener((observableValue, aBoolean, t1) -> {
            params.highLight = t1;
            advanceSearchParamsManager.saveParam();
        });

        var type = new JFXComboBox<String>();
        type.setPrefWidth(100);
        type.getStyleClass().add("jfx-combo-box");
        type.getItems().addAll(Locales.str("findWindow.normal"), Locales.str("regex")); //Locales.str("fakeRegex"),
        type.getSelectionModel().select(params.typeToIndex());
        type.getSelectionModel().selectedIndexProperty().addListener((observableValue, number, t1) -> {
            params.indexToType(t1.intValue());
            advanceSearchParamsManager.saveParam();
        });

        var colors = new MyJFXButton();
        colors.setPrefHeight(36);
        colors.ex = params;
        controller.getColorBtnManager().initListViewColorBtn(colors);

        var addBtn = new JFXButton();
        addBtn.setPrefWidth(36);
        addBtn.setPrefHeight(36);
        addBtn.getStyleClass().add("custom-jfx-button-small-text-big");
        addBtn.setText("+");
        addBtn.setOnMouseClicked(mouseEvent -> {
            advanceSearchParamsManager.copyItem(params.copy(), params);
        });

        var rmBtn = new JFXButton();
        rmBtn.setPrefWidth(36);
        rmBtn.setPrefHeight(36);
        rmBtn.getStyleClass().add("custom-jfx-button-small-text-big");
        rmBtn.setText("-");
        rmBtn.setOnMouseClicked(mouseEvent -> {
            advanceSearchParamsManager.deleteItem(hbox);
        });

//        var upBtn = new JFXButton();
//        upBtn.setPrefWidth(36);
//        upBtn.setPrefHeight(36);
//        upBtn.getStyleClass().add("custom-jfx-button-small-text-big");
//        upBtn.setText("↑");
//        upBtn.setOnMouseClicked(mouseEvent -> {
//            advanceSearchParamsManager.upItem(hbox);
//        });
//
//        var downBtn = new JFXButton();
//        downBtn.setPrefWidth(36);
//        downBtn.setPrefHeight(36);
//        downBtn.getStyleClass().add("custom-jfx-button-small-text-big");
//        downBtn.setText("↓");
//        downBtn.setOnMouseClicked(mouseEvent -> {
//            advanceSearchParamsManager.downItem(hbox);
//        });

        hbox.getChildren().addAll(check, word, caseMatch, useWholeWords, highlight, type, colors, addBtn, rmBtn);//, upBtn, downBtn
        return hbox;
    }

    public void initListView() {
        advanceSearchParamsManager.initAdvanceCfgs();

        controller.advanceSearchesCfgSaveBtn.setOnMouseClicked(event -> {
            if (advanceSearchParamsManager.getAdvCfgsSize() >= StaticsProf.getsMaxCfgSize()) {
                JfoenixDialogUtils.setWindow(NotepadFindWindow.getInstance().getWindow());
                ThreadUtils.globalHandler().postDelayed(()->{
                    JfoenixDialogUtils.setWindow(UIContext.mainWindow);
                }, 250);
                JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("AdvCfgMaxSizeReached"));
                return;
            }

            JfoenixDialogUtils.setWindow(NotepadFindWindow.getInstance().getWindow());
            ThreadUtils.globalHandler().postDelayed(()->{
                JfoenixDialogUtils.setWindow(UIContext.mainWindow);
            }, 250);
            JfoenixDialogUtils.editInput(Locales.str("pleaseInputName"), "", newName -> {
                if (newName.trim().length() > 0) {
                    advanceSearchParamsManager.copyCurrentNameCfg(newName.trim());
                }
            });
        });

        controller.advanceSearchesCfgDelBtn.setOnMouseClicked(event -> {
            if (advanceSearchParamsManager.isDefault()) {
                JfoenixDialogUtils.setWindow(NotepadFindWindow.getInstance().getWindow());
                ThreadUtils.globalHandler().postDelayed(()->{
                    JfoenixDialogUtils.setWindow(UIContext.mainWindow);
                }, 250);
                JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("defaultOneCannotDelete"));
                return;
            }

            advanceSearchParamsManager.removeCurrentNameCfg();
        });

        controller.advanceStartBtn.setOnMouseClicked(mouseEvent -> {
            NotepadController mainController = UIContext.context();
            var curArea = UIContext.currentAreaProp.get();
            if (curArea != null) {
                var arr = advanceSearchParamsManager.getCurrentParamsUseful();
                if (arr.length == 0) {
                    JfoenixDialogUtils.setWindow(NotepadFindWindow.getInstance().getWindow());
                    ThreadUtils.globalHandler().postDelayed(()->{
                        JfoenixDialogUtils.setWindow(UIContext.mainWindow);
                    }, 250);
                    JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("noSearchParams"));
                    return;
                }

                curArea.getEditor().findAdvance(arr, results -> {
                    var list = new LinkedList<OneFileSearchResults>();
                    list.add(results);
                    StringBuilder sb = new StringBuilder();
                    for (var sp : arr) {
                        sb.append(sp.words).append(" + ");
                    }
                    AllFilesSearchResults r = new AllFilesSearchResults()
                            .addAllResults(list)
                            .addMask(sb.substring(0, sb.length() - 3));
                    Platform.runLater(() -> {
                        ResultAreaManager.Instance.updateSearchLayout(r);
                    });
                });
            }
        });
    }
}
