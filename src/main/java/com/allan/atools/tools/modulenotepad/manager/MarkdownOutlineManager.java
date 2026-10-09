package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadController;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.MarkdownEditorSupport;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;

import java.util.List;
import java.util.concurrent.Future;

/** 管理“当前文档”中的 Markdown 标题目录。 */
public final class MarkdownOutlineManager {
    private static final long REFRESH_DELAY_MS = 350;
    private static final long MAX_REFRESH_WAIT_MS = 700;
    private static final int LEVEL_INDENT = 12;

    private final NotepadController controller;
    private final Action0 textChangedAction = this::onTextChanged;
    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(REFRESH_DELAY_MS, MAX_REFRESH_WAIT_MS, this::startRefresh);
    private final ChangeListener<EditorArea> currentAreaChanged =
            (observable, oldValue, newValue) -> bindEditor(newValue);
    private final ChangeListener<Number> workspaceTabChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();
    private final ChangeListener<Parent> workspaceParentChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();
    private final ChangeListener<Boolean> workspaceVisibleChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();

    private EditorArea currentArea;
    private boolean boundMarkdown;
    private boolean boundOverLimit;
    private List<MarkdownHeading> shownHeadings = List.of();
    private boolean outlineDirty;
    private boolean destroyed;
    private long shownContentVersion = -1;
    private Future<?> parseTask;
    private final ChangeListener<Number> caretChanged = (observable, old, value) -> followCaret();

    public MarkdownOutlineManager(NotepadController controller) {
        this.controller = controller;
        controller.currentDocumentOutlineList.setCellFactory(list -> new HeadingCell());
        controller.workspaceTabPane.getSelectionModel().selectedIndexProperty().addListener(workspaceTabChanged);
        controller.workspaceVBox.parentProperty().addListener(workspaceParentChanged);
        controller.workspaceVBox.visibleProperty().addListener(workspaceVisibleChanged);
        UIContext.currentAreaProp.addListener(currentAreaChanged);
        bindEditor(UIContext.currentAreaProp.get());
    }

    public void destroy() {
        destroyed = true;
        invalidateRefresh();
        unbindEditor();
        UIContext.currentAreaProp.removeListener(currentAreaChanged);
        controller.workspaceTabPane.getSelectionModel().selectedIndexProperty().removeListener(workspaceTabChanged);
        controller.workspaceVBox.parentProperty().removeListener(workspaceParentChanged);
        controller.workspaceVBox.visibleProperty().removeListener(workspaceVisibleChanged);
        refreshScheduler.dispose();
    }

    private void bindEditor(EditorArea area) {
        boolean markdown = MarkdownEditorSupport.supportsMarkdown(area);
        boolean overLimit = isOverLimit(area);
        if (currentArea == area && boundMarkdown == markdown && boundOverLimit == overLimit) return;
        invalidateRefresh();
        unbindEditor();
        currentArea = area;
        boundMarkdown = markdown;
        boundOverLimit = overLimit;
        clearOutline();

        if (!markdown) {
            outlineDirty = false;
            return;
        }

        area.getEditor().textChanged.addAction(textChangedAction);
        area.caretPositionProperty().addListener(caretChanged);
        outlineDirty = true;
        if (overLimit) {
            outlineDirty = false;
            return;
        }
        if (isOutlineShown()) {
            refreshScheduler.startNow();
        }
    }

    private void unbindEditor() {
        if (currentArea != null) {
            currentArea.getEditor().textChanged.removeAction(textChangedAction);
            currentArea.caretPositionProperty().removeListener(caretChanged);
            currentArea = null;
        }
    }

    private void onTextChanged() {
        outlineDirty = true;
        boundOverLimit = isOverLimit(currentArea);
        if (boundOverLimit) {
            invalidateRefresh();
            clearOutline();
            outlineDirty = false;
            return;
        }
        if (!isOutlineShown()) {
            return;
        }
        refreshScheduler.request();
    }

    private void onOutlineVisibilityChanged() {
        if (!isOutlineShown()) {
            invalidateRefresh();
        } else if (outlineDirty) {
            refreshScheduler.startNow();
        }
    }

    private void startRefresh(long requestId) {
        var area = currentArea;
        if (destroyed || !outlineDirty
                || !isOutlineShown() || !MarkdownEditorSupport.supportsMarkdown(area)) {
            refreshScheduler.complete(requestId, null);
            return;
        }
        if (isOverLimit(area)) {
            clearOutline();
            outlineDirty = false;
            refreshScheduler.complete(requestId, null);
            return;
        }
        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        parseTask = ThreadUtils.submit(() -> {
            List<MarkdownHeading> headings = null;
            try {
                headings = parseHeadings(area, text);
            } catch (RuntimeException e) {
                Log.e("parse markdown outline failed", e);
            }
            var result = headings;
            Platform.runLater(() -> finishRefresh(
                    area, contentVersion, requestId, result));
        });
    }

    private void finishRefresh(EditorArea area, long contentVersion, long parsedRequestId,
                               List<MarkdownHeading> headings) {
        refreshScheduler.complete(parsedRequestId, () -> {
            parseTask = null;
            if (headings != null) {
                applyHeadings(area, contentVersion, headings);
            }
        });
    }

    private void applyHeadings(EditorArea area, long contentVersion, List<MarkdownHeading> headings) {
        if (destroyed || area != currentArea || !isOutlineShown()
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        if (!shownHeadings.equals(headings)) {
            controller.currentDocumentOutlineList.getItems().setAll(headings);
            shownHeadings = headings;
        }
        shownContentVersion = contentVersion;
        outlineDirty = false;
        followCaret();
    }

    private void followCaret() {
        var area = currentArea;
        if (area == null || !isOutlineShown() || shownContentVersion != area.getEditor().getContentVersion()) return;
        int index = -1;
        for (int i = 0; i < shownHeadings.size(); i++) {
            if (shownHeadings.get(i).lineIndex() <= area.getCurrentParagraph()) index = i;
            else break;
        }
        if (index >= 0 && controller.currentDocumentOutlineList.getSelectionModel().getSelectedIndex() != index) {
            controller.currentDocumentOutlineList.getSelectionModel().select(index);
            controller.currentDocumentOutlineList.scrollTo(index);
        }
    }

    public void refreshCurrentFile() {
        bindEditor(UIContext.currentAreaProp.get());
    }

    private void jumpToHeading(MarkdownHeading heading) {
        var area = currentArea;
        if (heading == null || area == null || area.getEditor().getContentVersion() != shownContentVersion) {
            return;
        }
        if (heading.lineIndex() >= area.getParagraphs().size()) {
            return;
        }
        area.moveTo(heading.lineIndex(), 0);
        area.showParagraphAtTop(heading.lineIndex());
        area.requestFollowCaret();
        area.requestFocus();
    }

    private boolean isOutlineShown() {
        return controller.workspaceVBox.getParent() != null
                && controller.workspaceVBox.isVisible()
                && controller.workspaceTabPane.getSelectionModel().getSelectedIndex() == 1;
    }

    private boolean isOverLimit(EditorArea area) {
        return area == null || area.getEditor().isRealtimeProcessingLimitReached();
    }

    private void invalidateRefresh() {
        refreshScheduler.invalidate();
        var task = parseTask;
        parseTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    private void clearOutline() {
        controller.currentDocumentOutlineList.getItems().clear();
        shownHeadings = List.of();
        shownContentVersion = -1;
    }

    private static List<MarkdownHeading> parseHeadings(EditorArea area, String text) {
        var state = ((com.allan.atools.richtext.codearea.EditorAreaMgrCode) area.getEditor()).markdownSnapshot(text);
        return state.getHeadings().stream().map(heading ->
                new MarkdownHeading(heading.getLevel(), heading.getTitle(), heading.getLine())).toList();
    }

    public record MarkdownHeading(int level, String title, int lineIndex) {
    }

    private final class HeadingCell extends ListCell<MarkdownHeading> {
        private HeadingCell() {
            getStyleClass().add("markdown-outline-cell");
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && !isEmpty()) {
                    jumpToHeading(getItem());
                }
            });
        }

        @Override
        protected void updateItem(MarkdownHeading heading, boolean empty) {
            super.updateItem(heading, empty);
            if (empty || heading == null) {
                setText(null);
                setPadding(Insets.EMPTY);
                setCursor(Cursor.DEFAULT);
                return;
            }
            setText(heading.title());
            setPadding(new Insets(4, 4, 4, 4 + (heading.level() - 1) * LEVEL_INDENT));
            setCursor(Cursor.HAND);
        }
    }
}
