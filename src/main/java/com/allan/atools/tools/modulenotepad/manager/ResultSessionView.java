package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.SettingPreferences;
import com.allan.atools.pop.AppContextMenu;
import com.allan.atools.richtext.ParStyle;
import com.allan.atools.richtext.TextStyle;
import com.allan.atools.richtext.codearea.ResultAreaImpl;
import com.allan.atools.text.beans.AllFilesSearchResults;
import com.allan.atools.text.beans.OneFileSearchResults;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.modulenotepad.local.AdvanceSearchedStyledDocument;
import com.allan.atools.utils.Locales;
import com.allan.uilibs.richtexts.MyVirtualScrollPane;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 搜索结果只保留一块正文区：左侧是会话列表，正文上方是当前会话命中的文件标签。
 * 会话和文件都只做切换，不再为每次搜索追加新的折叠标题，元素数量保持恒定。
 */
public final class ResultSessionView extends BorderPane {
    private static final double SESSION_PANE_WIDTH = 190;
    /** 折叠后只保留分线处按钮的窄条宽度。 */
    private static final double SESSION_COLLAPSED_WIDTH = 32;
    private static final double TAB_BAR_HEIGHT = 34;
    private static final double DELETE_VISIBLE_MILLIS = 2000;

    private final VBox sessionPane = new VBox();
    private final BorderPane sessionHost = new BorderPane();
    private final VBox sessionRows = new VBox(2);
    private final ScrollPane sessionScroll = new ScrollPane(sessionRows);
    private final HBox fileTabBar = new HBox(2);
    private final ScrollPane fileTabScroll = new ScrollPane(fileTabBar);
    private final ToggleGroup fileTabGroup = new ToggleGroup();
    private final Button sessionToggle = new Button("‹");
    private final Label statusHint = new Label();

    private final ResultAreaImpl area;
    private final MyVirtualScrollPane<ResultAreaImpl> areaPane;

    private final List<SessionEntry> sessions = new ArrayList<>();
    private SessionEntry current;
    private boolean sessionCollapsed;
    private long renderToken;
    private Runnable onEmpty;
    private ChangeListener<Boolean> wrapListener;

    public ResultSessionView() {
        getStyleClass().add("result-session-view");
        int sizeMode = SettingPreferences.getInt(SettingPreferences.mainUiSizeModeKey);
        getStyleClass().add(sizeMode == 1 ? "main-ui-size-large"
                : sizeMode == 2 ? "main-ui-size-larger" : "main-ui-size-default");
        // 分屏宿主会按最小高度分配空间，这里固定一个下限，避免被压成一条滚动条。
        setMinHeight(140);

        area = new ResultAreaImpl();
        areaPane = new MyVirtualScrollPane<>(area);
        area.setWrapText(SettingPreferences.getBoolean(SettingPreferences.searchResultAreaIsWrapKey));
        area.setDoubleClickListener((lineNum, col) ->
                ResultAreaManager.clickOnLine(lineNum, col, (AllFilesSearchResults) area.getObject1()));
        area.setClearSelf(target -> {
            if (current != null) {
                removeSession(current);
            }
        });
        area.setClearOthers(target -> {
            if (current != null) {
                removeOtherSessions(current);
            }
        });
        area.setClearAll(this::clearSessions);

        buildSessionPane();
        buildContentPane();
        listenWrapSetting();
        addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
    }

    private void buildSessionPane() {
        var title = new Label(Locales.str("result.sessions"));
        title.getStyleClass().add("result-session-header-title");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var header = new HBox(title, spacer);
        header.getStyleClass().add("result-session-header");

        sessionRows.setPadding(new Insets(4, 6, 6, 6));
        sessionScroll.setFitToWidth(true);
        sessionScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sessionScroll.getStyleClass().add("result-session-scroll");
        VBox.setVgrow(sessionScroll, Priority.ALWAYS);

        sessionPane.getChildren().addAll(header, sessionScroll);

        // 折叠／展开按钮放在会话栏右侧、贴着中间分线，竖向由 BorderPane 自动居中。
        sessionToggle.getStyleClass().add("result-session-toggle");
        sessionToggle.setFocusTraversable(false);
        sessionToggle.setOnAction(event -> toggleSessionPane());

        sessionHost.setCenter(sessionPane);
        sessionHost.setRight(sessionToggle);
        sessionHost.getStyleClass().add("result-session-pane");
        setLeft(sessionHost);
        applySessionPaneState();
    }

    private void buildContentPane() {
        fileTabBar.setAlignment(Pos.CENTER_LEFT);
        fileTabBar.setPadding(new Insets(0, 6, 0, 4));
        fileTabScroll.setFitToHeight(true);
        fileTabScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        fileTabScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        fileTabScroll.setPannable(true);
        fileTabScroll.getStyleClass().add("result-file-tab-scroll");
        fileTabScroll.setMinHeight(TAB_BAR_HEIGHT);
        fileTabScroll.setPrefHeight(TAB_BAR_HEIGHT);
        fileTabScroll.setMaxHeight(TAB_BAR_HEIGHT);
        HBox.setHgrow(fileTabScroll, Priority.ALWAYS);

        // 文件名与匹配数已经合并到标签上，标签栏右侧只保留切换提示，不再单独占一条状态栏。
        statusHint.getStyleClass().add("result-status-hint");
        statusHint.setText(Locales.str("result.switchFileHint"));
        statusHint.setVisible(false);
        statusHint.setMinWidth(Region.USE_PREF_SIZE);

        var tabBar = new HBox(fileTabScroll, statusHint);
        tabBar.setAlignment(Pos.CENTER_LEFT);
        tabBar.getStyleClass().add("result-file-tab-bar");

        var center = new VBox(tabBar, areaPane);
        VBox.setVgrow(areaPane, Priority.ALWAYS);
        center.getStyleClass().add("result-content-pane");
        setCenter(center);
    }

    private void toggleSessionPane() {
        sessionCollapsed = !sessionCollapsed;
        applySessionPaneState();
    }

    private void applySessionPaneState() {
        sessionPane.setVisible(!sessionCollapsed);
        sessionPane.setManaged(!sessionCollapsed);
        sessionToggle.setText(sessionCollapsed ? "›" : "‹");
        double width = sessionCollapsed ? SESSION_COLLAPSED_WIDTH : SESSION_PANE_WIDTH;
        sessionHost.setMinWidth(width);
        sessionHost.setPrefWidth(width);
        sessionHost.setMaxWidth(width);
    }

    private void listenWrapSetting() {
        wrapListener = (observable, oldValue, newValue) -> area.setWrapText(newValue);
        SettingPreferences.getBoolProp(SettingPreferences.searchResultAreaIsWrapKey).addListener(wrapListener);
    }

    public void setOnEmpty(Runnable onEmpty) {
        this.onEmpty = onEmpty;
    }

    public ResultAreaImpl getArea() {
        return area;
    }

    /** 新会话插到列表最前面；超过上限时丢弃最旧的一条，避免结果常驻内存无限增长。 */
    public void addSession(AllFilesSearchResults results, int maxSessions) {
        if (results == null || results.allResults == null || results.allResults.isEmpty()) {
            return;
        }
        var entry = new SessionEntry(results);
        sessions.add(0, entry);
        sessionRows.getChildren().add(0, entry.row);
        while (sessions.size() > maxSessions) {
            var removed = sessions.remove(sessions.size() - 1);
            sessionRows.getChildren().remove(removed.row);
            removed.hideTimer.stop();
            if (removed == current) {
                current = null;
            }
        }
        selectSession(entry);
    }

    /** 结果区被整体关闭前调用，只释放资源，不触发空列表回调。 */
    public void dispose() {
        renderToken++;
        if (wrapListener != null) {
            SettingPreferences.getBoolProp(SettingPreferences.searchResultAreaIsWrapKey).removeListener(wrapListener);
            wrapListener = null;
        }
        for (var entry : sessions) {
            entry.hideTimer.stop();
        }
        sessions.clear();
        sessionRows.getChildren().clear();
        current = null;
        if (!area.isDestroyed()) {
            area.destroy();
        }
        areaPane.removeContent();
    }

    private void selectSession(SessionEntry entry) {
        if (entry == null) {
            return;
        }
        if (current != entry) {
            if (current != null) {
                current.row.getStyleClass().remove("result-session-row-selected");
            }
            current = entry;
            entry.row.getStyleClass().add("result-session-row-selected");
            rebuildFileTabs(entry);
        }
        renderCurrent();
        updateSwitchHint();
        showDeleteTemporarily(entry);
    }

    private void rebuildFileTabs(SessionEntry entry) {
        fileTabGroup.getToggles().clear();
        fileTabBar.getChildren().clear();
        var files = entry.results.allResults;
        for (int i = 0; i < files.size(); i++) {
            final int index = i;
            var one = files.get(i);
            // 文件名与匹配数合并成一条标签文案，正文区不再重复显示这些信息。
            var toggle = new ToggleButton(String.format(Locales.str("result.fileTabLabel"),
                    nameOf(one), one.results.size()));
            toggle.getStyleClass().add("result-file-tab");
            Tooltip.install(toggle, new Tooltip(one.truncated
                    ? nameOf(one) + " · " + Locales.str("search.resultsLimited")
                    : nameOf(one)));
            toggle.setToggleGroup(fileTabGroup);
            toggle.setFocusTraversable(false);
            toggle.setUserData(index);
            toggle.setSelected(index == entry.selectedFile);
            toggle.setOnAction(event -> {
                // ToggleButton 允许再次点击取消选中，这里保持当前文件始终有且只有一个选中项。
                if (!toggle.isSelected()) {
                    toggle.setSelected(true);
                    return;
                }
                selectFile(entry, index);
            });
            fileTabBar.getChildren().add(toggle);
        }
    }

    private void selectFile(SessionEntry entry, int index) {
        if (entry != current || index == entry.selectedFile) {
            return;
        }
        entry.selectedFile = index;
        renderCurrent();
        updateSwitchHint();
        area.requestFocus();
    }

    private void switchFile(int delta) {
        if (current == null) {
            return;
        }
        int size = current.results.allResults.size();
        if (size <= 1) {
            return;
        }
        int target = Math.floorMod(current.selectedFile + delta, size);
        current.selectedFile = target;
        syncTabSelection();
        renderCurrent();
        updateSwitchHint();
    }

    private void syncTabSelection() {
        if (current == null) {
            return;
        }
        for (var node : fileTabBar.getChildren()) {
            if (node instanceof ToggleButton toggle && toggle.getUserData() instanceof Integer index) {
                toggle.setSelected(index == current.selectedFile);
            }
        }
    }

    private void renderCurrent() {
        long token = ++renderToken;
        var entry = current;
        if (entry == null) {
            area.setObject1(null);
            area.clear();
            return;
        }
        var one = entry.results.allResults.get(entry.selectedFile);
        var subset = new AllFilesSearchResults();
        subset.allResults = List.of(one);
        subset.mask = entry.results.mask;
        subset.skipFileHeader = true;
        area.setObject1(subset);

        var initialTextStyle = area.getInitialTextStyle();
        var initialParagraphStyle = area.getInitialParagraphStyle();
        var segmentOps = area.getSegOps();

        ThreadUtils.execute(() -> {
            AdvanceSearchedStyledDocument<ParStyle, String, TextStyle> document;
            try {
                document = AdvanceSearchedStyledDocument.from(
                        initialTextStyle, initialParagraphStyle, segmentOps, subset);
            } catch (Throwable throwable) {
                return;
            }
            Platform.runLater(() -> {
                if (token != renderToken || area.isDestroyed()) {
                    return;
                }
                area.replace(0, area.getLength(), document);
                area.refreshTheme();
                area.applyCurrentFontSize();
                area.moveTo(0);
                area.requestFollowCaret();
            });
        });
    }

    private void updateSwitchHint() {
        var entry = current;
        statusHint.setVisible(entry != null && entry.results.allResults.size() > 1);
    }

    private void removeSession(SessionEntry entry) {
        int index = sessions.indexOf(entry);
        if (index < 0) {
            return;
        }
        sessions.remove(index);
        sessionRows.getChildren().remove(entry.row);
        entry.hideTimer.stop();
        if (entry != current) {
            return;
        }
        current = null;
        if (sessions.isEmpty()) {
            clearContent();
            fireEmpty();
        } else {
            selectSession(sessions.get(Math.min(index, sessions.size() - 1)));
        }
    }

    private void removeOtherSessions(SessionEntry keep) {
        for (var entry : new ArrayList<>(sessions)) {
            if (entry == keep) {
                continue;
            }
            entry.hideTimer.stop();
            sessions.remove(entry);
            sessionRows.getChildren().remove(entry.row);
        }
        if (current != keep) {
            current = null;
            selectSession(keep);
        }
    }

    private void clearSessions() {
        for (var entry : sessions) {
            entry.hideTimer.stop();
        }
        sessions.clear();
        sessionRows.getChildren().clear();
        current = null;
        clearContent();
        fireEmpty();
    }

    private void clearContent() {
        renderToken++;
        area.setObject1(null);
        area.clear();
        fileTabGroup.getToggles().clear();
        fileTabBar.getChildren().clear();
        statusHint.setVisible(false);
    }

    private void fireEmpty() {
        if (onEmpty != null) {
            onEmpty.run();
        }
    }

    private void showDeleteTemporarily(SessionEntry entry) {
        entry.armed = false;
        showDelete(entry);
        entry.hideTimer.playFromStart();
    }

    private static void showDelete(SessionEntry entry) {
        entry.deleteButton.setVisible(true);
    }

    private static void hideDelete(SessionEntry entry) {
        entry.deleteButton.setVisible(false);
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!event.isControlDown()) {
            return;
        }
        if (event.getCode() == KeyCode.PAGE_DOWN) {
            switchFile(1);
            event.consume();
        } else if (event.getCode() == KeyCode.PAGE_UP) {
            switchFile(-1);
            event.consume();
        }
    }

    private static String nameOf(OneFileSearchResults results) {
        if (results.displayName != null) {
            return results.displayName;
        }
        return results.file == null ? "" : results.file.getName();
    }

    private static String formatTime(long timeMillis) {
        return new SimpleDateFormat("HH:mm").format(new Date(timeMillis));
    }

    private final class SessionEntry {
        final AllFilesSearchResults results;
        final StackPane row;
        final Button deleteButton;
        final PauseTransition hideTimer = new PauseTransition(
                Duration.millis(DELETE_VISIBLE_MILLIS));
        int selectedFile;
        /** 选中后 2 秒自动隐藏；此后只有鼠标重新进入该行才再次显示。 */
        boolean armed = true;

        SessionEntry(AllFilesSearchResults results) {
            this.results = results;
            // 默认停在命中数最多的文件上，之后切到别的文件再回来仍保持上次的位置。
            this.selectedFile = defaultFileIndex();

            var mask = results.mask == null ? "" : results.mask;
            var title = new Label(mask);
            title.getStyleClass().add("result-session-title");
            title.setMaxWidth(Double.MAX_VALUE);
            Tooltip.install(title, new Tooltip(mask));

            int hits = 0;
            for (var one : results.allResults) {
                hits += one.results.size();
            }
            var meta = new HBox(5);
            var time = new Label(formatTime(System.currentTimeMillis()));
            time.getStyleClass().add("result-session-meta");
            var dot = new Label("·");
            dot.getStyleClass().add("result-session-meta-dim");
            var count = new Label(String.format(Locales.str("result.fileAndHits"),
                    results.allResults.size(), hits));
            count.getStyleClass().add("result-session-meta");
            meta.getChildren().addAll(time, dot, count);

            var text = new VBox(2, title, meta);
            text.setPadding(new Insets(0, 20, 0, 0));

            deleteButton = new Button("×");
            deleteButton.getStyleClass().add("result-session-delete");
            deleteButton.setFocusTraversable(false);
            deleteButton.setVisible(false);
            deleteButton.setOnAction(event -> {
                event.consume();
                removeSession(this);
            });

            row = new StackPane(text, deleteButton);
            StackPane.setAlignment(deleteButton, Pos.TOP_RIGHT);
            StackPane.setMargin(deleteButton, new Insets(3, 3, 0, 0));
            row.getStyleClass().add("result-session-row");
            var menu = new AppContextMenu();
            var deleteItem = new MenuItem(Locales.str("result.clear"));
            deleteItem.setOnAction(event -> removeSession(this));
            var keepItem = new MenuItem(Locales.str("result.clearOthers"));
            keepItem.setOnAction(event -> removeOtherSessions(this));
            var clearItem = new MenuItem(Locales.str("result.clearAll"));
            clearItem.setOnAction(event -> clearSessions());
            menu.getItems().addAll(deleteItem, keepItem, clearItem);
            row.setOnContextMenuRequested(event -> menu.show(row, event.getScreenX(), event.getScreenY()));
            row.setOnMouseClicked(event -> {
                if (event.getButton() != MouseButton.PRIMARY || isInsideDelete(event.getTarget())) {
                    return;
                }
                selectSession(this);
            });
            row.setOnMouseEntered(event -> {
                if (armed) {
                    showDelete(this);
                }
            });
            row.setOnMouseExited(event -> {
                armed = true;
                hideDelete(this);
                hideTimer.stop();
            });
            hideTimer.setOnFinished(event -> {
                armed = true;
                hideDelete(this);
            });
        }

        int defaultFileIndex() {
            int best = 0;
            int bestHits = -1;
            var files = results.allResults;
            for (int i = 0; i < files.size(); i++) {
                if (files.get(i).results.size() > bestHits) {
                    bestHits = files.get(i).results.size();
                    best = i;
                }
            }
            return best;
        }

        private boolean isInsideDelete(Object target) {
            if (!(target instanceof Node node)) {
                return false;
            }
            Node cursor = node;
            while (cursor != null) {
                if (cursor == deleteButton) {
                    return true;
                }
                cursor = cursor.getParent();
            }
            return false;
        }
    }
}
