package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.richtext.codearea.ResultAreaImpl;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.atools.utils.TimerCounter;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.SettingPreferences;
import com.allan.atools.tools.modulenotepad.local.AdvanceSearchedStyledDocument;
import com.allan.uilibs.richtexts.MyVirtualScrollPane;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Accordion;
import javafx.scene.control.TitledPane;

/**
 * 这里的泛型是为了传导给searchResults的泛型。所以是Editor的泛型
 */
public abstract class AbstractResultUpdater {
    static final int MAX_RESULTS_COUNT = UIContext.DEBUG ? 5 : 10;

    static Accordion mResultRoot; //只需要一份
    private ChangeListener<Boolean> searchResultWrapChanged;

    static AbstractResultUpdater createInstance() {
        return SettingPreferences.getBoolean(SettingPreferences.resultAreaInNewWindowKey) ? new ResultUpdaterNewWindowImpl() : new ResultUpdaterSplitPaneImpl();
    }

    abstract void assetRoot();

    public abstract boolean bringToFront();

    void initPropertiesListener() {
        if (searchResultWrapChanged != null) {
            return;
        }
        var prop = SettingPreferences.getBoolProp(SettingPreferences.searchResultAreaIsWrapKey);
        searchResultWrapChanged = (observableValue, oldValue, newValue) -> {
            if (mResultRoot != null) {
                for (TitledPane tp : mResultRoot.getPanes()) {
                    if (tp.getContent() instanceof MyVirtualScrollPane<?> vpane) {
                        if (vpane.getContent() instanceof ResultAreaImpl area) {
                            area.setWrapText(newValue);
                        }
                    }
                }
            }
        };
        prop.addListener(searchResultWrapChanged);
    }

    private void destroyPropertiesListener() {
        if (searchResultWrapChanged == null) {
            return;
        }
        SettingPreferences.getBoolProp(SettingPreferences.searchResultAreaIsWrapKey)
                .removeListener(searchResultWrapChanged);
        searchResultWrapChanged = null;
    }

    private void destroyArea(TitledPane titledPane) {
        if (titledPane.getContent() instanceof MyVirtualScrollPane<?> pane
                && pane.getContent() instanceof ResultAreaImpl area) {
            area.destroy();
            pane.removeContent();
            titledPane.setContent(null);
        }
    }

    void destroyResultRoot() {
        if (mResultRoot != null) {
            for (TitledPane titledPane : mResultRoot.getPanes()) {
                destroyArea(titledPane);
            }
            mResultRoot.getPanes().clear();
        }
        destroyPropertiesListener();
    }

    ResultAreaImpl createArea(AllFilesSearchResults results) {
        var area = new ResultAreaImpl();
        var isWrap = SettingPreferences.getBoolean(SettingPreferences.searchResultAreaIsWrapKey);
        area.setWrapText(isWrap);
        area.setDoubleClickListener((lineNum, col) -> ResultAreaManager.clickOnLine(lineNum, col, (AllFilesSearchResults) area.getObject1()));

        area.setObject1(results); //标记数据
        var initialTextStyle = area.getInitialTextStyle();
        var initialParagraphStyle = area.getInitialParagraphStyle();
        var segmentOps = area.getSegOps();

        ThreadUtils.execute(()-> {
            if (area.isDestroyed()) {
                return;
            }
            //统计成indexes；
            TimerCounter.start("cvt result maps");
            var value = AdvanceSearchedStyledDocument.from(
                    initialTextStyle, initialParagraphStyle, segmentOps, results);

            Log.d(TimerCounter.end("cvt result maps"));
            Platform.runLater(()-> {
                if (area.isDestroyed()) {
                    return;
                }
                TimerCounter.start("show area text");
                area.replace(0, 0, value);
                Log.d(TimerCounter.end("show area text"));
            });
        });
        return area;
    }

    private void afterClearPane() {
        if (mResultRoot.getPanes().size() == 0) {
            ResultAreaManager.Instance.closeResult();
        }
    }

    void setClears(ResultAreaImpl area) {
        area.setClearAll(() -> {
            for (TitledPane titledPane : mResultRoot.getPanes()) {
                destroyArea(titledPane);
            }
            mResultRoot.getPanes().clear();
            afterClearPane();
        });

        area.setClearOthers((co) -> {
            TitledPane reserved = null;
            for (TitledPane tp : mResultRoot.getPanes()) {
                if (tp.getContent() instanceof MyVirtualScrollPane<?> pane) {
                    if (pane.getContent() instanceof ResultAreaImpl resultArea) {
                        if (resultArea != co) {
                            destroyArea(tp);
                        } else {
                            reserved = tp;
                        }
                    }
                }
            }

            mResultRoot.getPanes().clear();
            if(reserved != null) mResultRoot.getPanes().add(reserved);
        });

        area.setClearSelf((cs) -> {
            TitledPane target = null;
            for (TitledPane tp : mResultRoot.getPanes()) {
                if (tp.getContent() instanceof MyVirtualScrollPane vpane) {
                    if (vpane.getContent() == cs) {
                        target = tp;
                        break;
                    }
                }
            }

            if (target != null) {
                mResultRoot.getPanes().remove(target);
                destroyArea(target);
            }

            afterClearPane();
        });
    }

    void areaIntoVPaneIntoTitledPane(ResultAreaImpl area, AllFilesSearchResults results) {
        var tp = new TitledPane();
        tp.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.SECONDARY) {
                return;
            }

            if (event.getSource() instanceof TitledPane titledPane
                    && titledPane.getContent() instanceof MyVirtualScrollPane<?> pane
                    && pane.getContent() instanceof ResultAreaImpl resultArea) {
                if (!resultArea.getContextMenu().isShowing()) {
                    resultArea.requestFocus();
                    ContextMenu menu = resultArea.getContextMenu();
                    if (menu != null) {
                        double x = event.getScreenX() + resultArea.getContextMenuXOffset();
                        double y = event.getScreenY() + resultArea.getContextMenuYOffset();
                        menu.show(resultArea, x, y );
                    }

                } //不能else，因为跟窗口内的点击冲突
            }
        });

        tp.setWrapText(false);
        tp.setAnimated(false);
        int totalMatched = 0;
        for (var oneResults : results.allResults) {
            totalMatched += oneResults.results.size();
        }
        tp.setText(Locales.str("search") + " `" + results.mask
                + "`  (" + results.allResults.size() + Locales.str("result.timesFiles")
                + ", " + Locales.str("total")
                + totalMatched
                + Locales.str("result.times") + ")");
        MyVirtualScrollPane<ResultAreaImpl> vpane = new MyVirtualScrollPane<>(area);
        tp.setContent(vpane);

        if (mResultRoot.getPanes().size() >= MAX_RESULTS_COUNT) {
            var removed = mResultRoot.getPanes().remove(mResultRoot.getPanes().size() - 1);
            destroyArea(removed);
        }
        mResultRoot.getPanes().add(0, tp);

        afterShown();
    }

    abstract void afterShown();

    public abstract void close();

    void requestFocus() {}
}
