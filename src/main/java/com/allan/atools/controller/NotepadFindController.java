package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.controllerwindow.NotepadFindWindow;
import com.allan.atools.tools.modulenotepad.manager.*;
import com.allan.atools.ui.ColorPickerUtil;
import com.allan.atools.ui.JfoenixDialogUtils;
import com.allan.atools.bean.SearchParams;
import com.allan.atools.richtext.ParStyle;
import com.allan.atools.richtext.TextStyle;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.AllStagesManager;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.jfoenix.controls.*;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.fxmisc.richtext.GenericStyledArea;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedList;

@XmlPaths(paths = {"notepad", "notepad_mm_find.fxml"})
public final class NotepadFindController extends AbstractController {
    public JFXTabPane tabPane;
    public VBox normalViewVBox;
    public JFXComboBox<String> findComboBox;
    public JFXComboBox<String> replaceComboBox;
    public JFXButton startBtn;
    public JFXButton startAllBtn;

    public JFXCheckBox caseMatchCheckBox;
    public JFXCheckBox allWordsCheckBox;

    public JFXRadioButton findMode0NormalBtn;

    public JFXTextField findTextField;
    public JFXTextField replaceTextField;

    public JFXButton advanceStartBtn;
    public JFXButton advanceStartAllBtn;

    public JFXListView<HBox> advanceSearchsListView;
    public VBox floatColorsBox;
    public JFXColorPicker textColorPicker;
    public JFXColorPicker bgColorPicker;
    public JFXButton colorPickerEnterBtn;
    public AnchorPane theFloatWindowPane;
    public JFXButton findColorsBtn;
    public HBox normalViewTopViewsHBox;
    //配置方案
    public JFXButton advanceSearchesCfgSaveBtn;
    public JFXButton advanceSearchesCfgDelBtn;
    public JFXComboBox<Label> advanceSearchesCfgsCobox;

    public VBox advanceSearchBox;
    public HBox advanceBtnsBox;
    public JFXCheckBox hightlightCheckBox;
    public HBox colorParentHBox;

    public JFXRadioButton findMode1RegexBtn;
    public JFXRadioButton findMode2FakeRegexBtn;
    public JFXButton findMode1HelpBtn;
    public JFXButton findMode2HelpBtn;

    private GenericStyledArea<ParStyle, String, TextStyle> findColorsDemoArea;

    private final OthersHelper mOthers = new OthersHelper();

    public void updateSelectedText(String selectedText) {
        Log.d("update selected text: " + selectedText);
         mOthers.setFindTextField(selectedText);
        //todo index，将来有replace的时候，在做处理。
        tabPane.getSelectionModel().select(0);
    }

    private FindAdvanceListViewManager mListViewMgr;
    private final FindColorBtnsManager mColorManager = new FindColorBtnsManager(this);
    public FindColorBtnsManager getColorBtnManager() {
        return mColorManager;
    }

    private SearchParams.Type currentType() {
        if (findMode0NormalBtn.isSelected()) {
            return SearchParams.Type.Normal;
        }
        if (findMode1RegexBtn.isSelected()) {
            return SearchParams.Type.Regex;
        }
        if (findMode2FakeRegexBtn.isSelected()) {
            return SearchParams.Type.FakeRegex;
        }
        throw new RuntimeException("not impossible current type.");
    }

    private int initTab(String index) {
        int id = 0;
        try {
            id = Integer.parseInt(index);
        } catch (Exception e) {
            //do nothing.
        }

        //todo 暂不支持tab第三。
        if (id == 2) {
            id = 1;
        }

        if (id == 0) {
            advanceSearchBox.setVisible(false);
            normalViewVBox.setVisible(true);
            AnchorPane.setTopAnchor(normalViewVBox, 60.0d);
            //VBox.setMargin(jichugouxuanLinesBox, new Insets(50, 0, 0, 12));
            replaceComboBox.setVisible(false);
            replaceTextField.setVisible(false);
            startBtn.setText(Locales.str("search"));
            startAllBtn.setText(Locales.str("allSearch"));
            getStage().setWidth(550.0);
            getStage().setHeight(400.0);
        } else if (id == 1) {
            advanceSearchBox.setVisible(true);
            normalViewVBox.setVisible(false);
            //VBox.setMargin(jichugouxuanLinesBox, new Insets(18, 0, 0, 12));
            AnchorPane.setTopAnchor(normalViewVBox, 50.0d);
            replaceComboBox.setVisible(false);
            replaceTextField.setVisible(false);
            startBtn.setText(Locales.str("search"));
            startAllBtn.setText(Locales.str("allSearch"));

            getStage().setWidth(660.0);
            getStage().setHeight(550.0);

            if (mListViewMgr == null) {
                mListViewMgr = new FindAdvanceListViewManager(this);
                mListViewMgr.initListView();
            }
        } else if (id == 2) {
            advanceSearchBox.setVisible(false);
            normalViewVBox.setVisible(true);
            AnchorPane.setTopAnchor(normalViewVBox, 60.0d);
            //VBox.setMargin(jichugouxuanLinesBox, new Insets(50, 0, 0, 12));
            replaceComboBox.setVisible(true);
            replaceTextField.setVisible(true);
            startBtn.setText(Locales.str("replace"));
            startAllBtn.setText(Locales.str("allReplace"));
            getStage().setWidth(550.0);
            getStage().setHeight(400.0);
        }
        return id;
    }

    @Override
    public void init(Stage stage) {
        super.init(stage);

        var preColors = ColorPickerUtil.preDefColors();
        textColorPicker.setPreDefinedColors(preColors);
        bgColorPicker.setPreDefinedColors(preColors);

        //避免其他控件选中，出现样式
        stage.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) {
                AllStagesManager.getInstance().getMainStage().show();
            } else {
                tabPane.requestFocus();
            }
        });

        String tabIndex = UIContext.sharedPref.getString("findWindowTabIndex", null);
        var tabIndexInt = initTab(tabIndex);
        tabPane.getSelectionModel().select(tabIndexInt);
        tabPane.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            UIContext.sharedPref.edit().putString("findWindowTabIndex", newValue.toString()).commit();
            initTab(newValue.toString());
        });
        mOthers.init();

        startBtn.setOnAction(e -> {
            var mainController = UIContext.context();
            var mgr = AllEditorsManager.Instance;
            var currentCodeArea = UIContext.currentAreaProp.get();
            if (currentCodeArea != null) {
                var searchParam = //SearchParams.generate(findTextField.getText(), mgr.getCurrentTabFilePath());
                        SearchParams.generate(findTextField.getText(), caseMatchCheckBox.isSelected(),
                                allWordsCheckBox.isSelected(), currentType(), mgr.getCurrentTabFilePath());
                searchParam.highLight = hightlightCheckBox.isSelected();
                searchParam.bgColor = mColorManager.normalBgColor;
                searchParam.textColor = mColorManager.normalTextColor;

                Log.d("search start with: " + searchParam);
                currentCodeArea.getEditor().find(searchParam, results -> {
                    var list = new LinkedList<OneFileSearchResults>();
                    list.add(results);
                    AllFilesSearchResults r = new AllFilesSearchResults()
                            .addAllResults(list)
                            .addMask(searchParam.words);
                    Platform.runLater(() -> {
                        ResultAreaManager.Instance.updateSearchLayout(r);
                        saveOneWords(searchParam.words);
                    });
                });
            }
        });

        startAllBtn.setOnAction(event -> {
            //todo 实现多文件搜索
            if (true) {
                JfoenixDialogUtils.setWindow(NotepadFindWindow.getInstance().getWindow());
                ThreadUtils.globalHandler().postDelayed(()->{
                    JfoenixDialogUtils.setWindow(UIContext.mainWindow);
                }, 250);
                JfoenixDialogUtils.alert(Locales.str("notification"), Locales.str("todoImpl"));
                return;
            }
            var mgr = AllEditorsManager.Instance;
            var searchParam = SearchParams.generate(findTextField.getText(), mgr.getAllTabsFilePaths());
            mgr.multiFind(searchParam, allFilesSearchResults -> {
                var s = allFilesSearchResults.toMappingResults(null);
                Platform.runLater(() -> {
                    ResultAreaManager.Instance.updateSearchLayout(allFilesSearchResults);
                    saveOneWords(searchParam.words);
                });
            });
        });

        mColorManager.initColorBtn(findColorsBtn);
    }

    private void saveOneWords(String words) {
        if (findComboBox.getItems().contains(words)) {
            findComboBox.getItems().remove(words);
            findComboBox.getItems().add(0, words);
        } else {
            findComboBox.getItems().add(0, words);
            if (findComboBox.getItems().size() > 10) {
                findComboBox.getItems().remove(findComboBox.getItems().size() - 1);
            }
        }
        findComboBox.getSelectionModel().select(0);
        mOthers.saveFindComboxData(FindAdvanceSearchParamsManager.SAVE_DELAY_TS);
    }

    @Override
    public void destroy() {
        Log.d("DESTROY: notepad find controller");
        mOthers.destroy();
    }

    ////////////////////////////////////
    public void saveAdvanceParams() {
        mListViewMgr.getAdvanceSearchParamsManager().saveParam();
    }

    private class OthersHelper {
        private final SimpleBooleanProperty mCaseMatchProp = new SimpleBooleanProperty();
        private final SimpleBooleanProperty mAllWordsProp = new SimpleBooleanProperty();
        private final SimpleBooleanProperty mHighlightProp = new SimpleBooleanProperty();

        private final JFXRadioButton[] radioGroups = new JFXRadioButton[3];
        private static final int FIND_MODE_NORMAL = 0;
        private static final int FIND_MODE_REGEX = 1;
        private static final int FIND_MODE_FAKE_REGEX = 2;

        private final ChangeListener<String> listViewListener = (observableValue, stringSingleSelectionModel, t1) -> {
            setFindTextField(t1);
        };

        private final ChangeListener<String> findTextFieldChangeListener = (observable, oldValue, newValue) -> {
            if (findComboBox.getSelectionModel().getSelectedIndex() >= 0) {
                Log.d("findText Filed: text changed: clearSelection: " + newValue);
                findComboBox.getSelectionModel().selectedItemProperty().removeListener(mOthers.listViewListener);
                findComboBox.getSelectionModel().clearSelection();
                findComboBox.getSelectionModel().selectedItemProperty().addListener(mOthers.listViewListener);
            }
        };

        private void setFindTextField(String text) {
            if (text == null) {
                text = "";
            }
            Log.d("findText Filed: set: " + text);
            findTextField.textProperty().removeListener(findTextFieldChangeListener);
            findTextField.setText(text);
            findTextField.textProperty().addListener(findTextFieldChangeListener);
        }

        private void saveFindMode(int mode) {
            UIContext.sharedPref.edit().putInt("findWindowFindMode", mode).commit();
        }

        public void destroy() {
            Log.d("DESTROY: notepad find other controller");
        }

        private void initFindComboxData() {
            var saveBase64 = UIContext.sharedPref.getString("findComboBox", null);
            if (saveBase64 == null) {
                return;
            }
            var bytes = Base64.getDecoder().decode(saveBase64);
            String orig = new String(bytes, StandardCharsets.UTF_8);
            String[] origs = orig.split("\n");
            for (String s : origs) {
                findComboBox.getItems().add(s);
            }
        }

        private String mFindComboBoxBase64Str;
        private final Runnable mSaveFindComboxDataRunnable = () -> {
            UIContext.sharedPref.edit().putString("findComboBox", mFindComboBoxBase64Str).commit();
        };

        private void saveFindComboxData(long ms) {
            StringBuilder sb = new StringBuilder();
            for (String s : findComboBox.getItems()) {
                sb.append(s).append("\n");
            }
            String sbs = sb.toString();
            String s = sbs.substring(0, sbs.length() - 1);
            mFindComboBoxBase64Str = Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));

            ThreadUtils.globalHandler().removeCallback(mSaveFindComboxDataRunnable);
            ThreadUtils.globalHandler().postDelayed(mSaveFindComboxDataRunnable, ms);
        }

        public void init() {
            {
                radioGroups[FIND_MODE_NORMAL] = findMode0NormalBtn;
                radioGroups[FIND_MODE_REGEX] = findMode1RegexBtn;
                radioGroups[FIND_MODE_FAKE_REGEX] = findMode2FakeRegexBtn;

                ToggleGroup toggleGroup = new ToggleGroup();
                for (var b : radioGroups) {
                    b.setToggleGroup(toggleGroup);
                }
                int mod = UIContext.sharedPref.getInt("findWindowFindMode", FIND_MODE_NORMAL);
                toggleGroup.selectToggle(radioGroups[mod >= 3 ? 0 : mod]); //先设置后监听

                toggleGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
                    int i = 0;
                    for (; i < radioGroups.length; i++) {
                        if (radioGroups[i] == newValue) {
                            saveFindMode(i);
                            break;
                        }
                    }
                });
            }

            findTextField.textProperty().addListener(findTextFieldChangeListener);

            caseMatchCheckBox.selectedProperty().bindBidirectional(mCaseMatchProp);
            allWordsCheckBox.selectedProperty().bindBidirectional(mAllWordsProp);
            hightlightCheckBox.selectedProperty().bindBidirectional(mHighlightProp);

            boolean mod = UIContext.sharedPref.getBoolean("findWindowCaseMatchCheck", false);
            mCaseMatchProp.set(!mod);
            mod = UIContext.sharedPref.getBoolean("findWindowAllWordsCheck", false);
            mAllWordsProp.set(mod);
            mod = UIContext.sharedPref.getBoolean("findWindowHighlightCheck", false);
            mHighlightProp.set(mod);

            mCaseMatchProp.addListener((observable, oldValue, newValue) -> {
                Log.d("save case match " + newValue);
                UIContext.sharedPref.edit().putBoolean("findWindowCaseMatchCheck", !newValue).commit();
            });

            mAllWordsProp.addListener((observable, oldValue, newValue) -> {
                Log.d("save all words " + newValue);
                UIContext.sharedPref.edit().putBoolean("findWindowAllWordsCheck", newValue).commit();
            });

            mHighlightProp.addListener((observable, oldValue, newValue) -> {
                Log.d("save highlight " + newValue);
                UIContext.sharedPref.edit().putBoolean("findWindowHighlightCheck", newValue).commit();
            });

            findMode2HelpBtn.setOnMouseClicked(mouseEvent -> {
                var tip = Locales.str("TipsFakeRegexSupport");
                var lb = new Label(" \n"
                        + "  " + tip + "  "
                        + "\n");
                lb.setFont(new Font(16));
                var popup = new JFXPopup(lb);
                popup.show(findMode2HelpBtn, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT);
            });

            findMode1HelpBtn.setOnMouseClicked(mouseEvent -> {
                var tip = Locales.str("TipsRealRegexSupport");
                var lb = new Label(" \n"
                        + "  " + tip + "  "
                        + "\n");
                lb.setFont(new Font(16));
                var popup = new JFXPopup(lb);
                popup.show(findMode1HelpBtn, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT);
            });

            initFindComboxData();
            var s = findComboBox.getItems().size() > 0 ? findComboBox.getItems().get(0) : "";
            setFindTextField(s);
            findComboBox.getSelectionModel().selectedItemProperty().addListener(listViewListener);
        }
    }
}
