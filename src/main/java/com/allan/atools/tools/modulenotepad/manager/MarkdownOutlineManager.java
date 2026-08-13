package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.UIContext;
import com.allan.atools.controller.NotepadController;
import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.control.ListCell;
import javafx.scene.input.MouseButton;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

/** 管理“当前文档”中的 Markdown 标题目录。 */
public final class MarkdownOutlineManager {
    private static final long REFRESH_DELAY_MS = 2000;
    private static final int LEVEL_INDENT = 12;

    private final NotepadController controller;
    private final Action0 textChangedAction = this::onTextChanged;
    private final PauseTransition refreshDelay = new PauseTransition(Duration.millis(REFRESH_DELAY_MS));
    private final ChangeListener<EditorArea> currentAreaChanged =
            (observable, oldValue, newValue) -> bindEditor(newValue);
    private final ChangeListener<Number> workspaceTabChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();
    private final ChangeListener<Parent> workspaceParentChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();
    private final ChangeListener<Boolean> workspaceVisibleChanged =
            (observable, oldValue, newValue) -> onOutlineVisibilityChanged();

    private EditorArea currentArea;
    private List<MarkdownHeading> shownHeadings = List.of();
    private boolean outlineDirty;
    private boolean destroyed;
    private long requestId;
    private long shownContentVersion = -1;
    private Future<?> parseTask;

    public MarkdownOutlineManager(NotepadController controller) {
        this.controller = controller;
        refreshDelay.setOnFinished(event -> startRefresh());
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
        refreshDelay.setOnFinished(null);
    }

    private void bindEditor(EditorArea area) {
        invalidateRefresh();
        unbindEditor();
        currentArea = area;
        clearOutline();

        if (!isMarkdown(area)) {
            outlineDirty = false;
            return;
        }

        area.getEditor().textChanged.addAction(textChangedAction);
        outlineDirty = true;
        if (isOverLimit(area)) {
            outlineDirty = false;
            return;
        }
        if (isOutlineShown()) {
            startRefresh();
        }
    }

    private void unbindEditor() {
        if (currentArea != null) {
            currentArea.getEditor().textChanged.removeAction(textChangedAction);
            currentArea = null;
        }
    }

    private void onTextChanged() {
        invalidateRefresh();
        outlineDirty = true;
        if (isOverLimit(currentArea)) {
            clearOutline();
            outlineDirty = false;
            return;
        }
        if (!isOutlineShown()) {
            return;
        }
        refreshDelay.playFromStart();
    }

    private void onOutlineVisibilityChanged() {
        if (!isOutlineShown()) {
            invalidateRefresh();
        } else if (outlineDirty) {
            startRefresh();
        }
    }

    private void startRefresh() {
        invalidateRefresh();
        var area = currentArea;
        if (destroyed || !outlineDirty || !isOutlineShown() || !isMarkdown(area)) {
            return;
        }
        if (isOverLimit(area)) {
            clearOutline();
            outlineDirty = false;
            return;
        }

        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        long currentRequestId = requestId;
        parseTask = ThreadUtils.submit(() -> {
            try {
                var headings = parseHeadings(text);
                if (headings != null && !ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> applyHeadings(area, contentVersion, currentRequestId, headings));
                }
            } catch (RuntimeException e) {
                Log.e("parse markdown outline failed", e);
            }
        });
    }

    private void applyHeadings(EditorArea area, long contentVersion, long parsedRequestId,
                               List<MarkdownHeading> headings) {
        if (destroyed || area != currentArea || parsedRequestId != requestId || !isOutlineShown()
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }

        parseTask = null;
        if (!shownHeadings.equals(headings)) {
            controller.currentDocumentOutlineList.getItems().setAll(headings);
            shownHeadings = headings;
        }
        shownContentVersion = contentVersion;
        outlineDirty = false;
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

    private static boolean isMarkdown(EditorArea area) {
        if (area == null || area.getEditor().getSourceFile() == null) {
            return false;
        }
        String name = area.getEditor().getSourceFile().getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    private void invalidateRefresh() {
        refreshDelay.stop();
        requestId++;
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

    private static List<MarkdownHeading> parseHeadings(String text) {
        var headings = new ArrayList<MarkdownHeading>();
        boolean inFence = false;
        char fenceCharacter = 0;
        int fenceLength = 0;
        int lineIndex = 0;
        int lineStart = 0;

        while (lineStart < text.length()) {
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
            int nextLineStart = text.indexOf('\n', lineStart);
            int lineEnd = nextLineStart >= 0 ? nextLineStart : text.length();
            if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }

            int contentStart = skipLeadingSpaces(text, lineStart, lineEnd);
            int currentFenceLength = countFenceCharacters(text, contentStart, lineEnd);
            if (currentFenceLength >= 3) {
                char currentFenceCharacter = text.charAt(contentStart);
                if (!inFence) {
                    inFence = true;
                    fenceCharacter = currentFenceCharacter;
                    fenceLength = currentFenceLength;
                } else if (currentFenceCharacter == fenceCharacter && currentFenceLength >= fenceLength
                        && isOnlyHorizontalWhitespace(text, contentStart + currentFenceLength, lineEnd)) {
                    inFence = false;
                }
            } else if (!inFence) {
                addHeading(text, contentStart, lineEnd, lineIndex, headings);
            }

            if (nextLineStart < 0) {
                break;
            }
            lineStart = nextLineStart + 1;
            lineIndex++;
        }
        return List.copyOf(headings);
    }

    private static int skipLeadingSpaces(String text, int start, int end) {
        int index = start;
        int count = 0;
        while (index < end && count < 3 && text.charAt(index) == ' ') {
            index++;
            count++;
        }
        return index;
    }

    private static int countFenceCharacters(String text, int start, int end) {
        if (start >= end) {
            return 0;
        }
        char character = text.charAt(start);
        if (character != '`' && character != '~') {
            return 0;
        }
        int index = start;
        while (index < end && text.charAt(index) == character) {
            index++;
        }
        return index - start;
    }

    private static boolean isOnlyHorizontalWhitespace(String text, int start, int end) {
        for (int index = start; index < end; index++) {
            char character = text.charAt(index);
            if (character != ' ' && character != '\t') {
                return false;
            }
        }
        return true;
    }

    private static void addHeading(String text, int start, int end, int lineIndex,
                                   List<MarkdownHeading> headings) {
        int markerEnd = start;
        while (markerEnd < end && markerEnd - start < 6 && text.charAt(markerEnd) == '#') {
            markerEnd++;
        }
        int level = markerEnd - start;
        if (level == 0 || markerEnd < end && text.charAt(markerEnd) == '#') {
            return;
        }
        if (markerEnd < end && !isHorizontalWhitespace(text.charAt(markerEnd))) {
            return;
        }

        int titleStart = markerEnd;
        while (titleStart < end && isHorizontalWhitespace(text.charAt(titleStart))) {
            titleStart++;
        }
        int titleEnd = end;
        while (titleEnd > titleStart && isHorizontalWhitespace(text.charAt(titleEnd - 1))) {
            titleEnd--;
        }

        int closingStart = titleEnd;
        while (closingStart > titleStart && text.charAt(closingStart - 1) == '#') {
            closingStart--;
        }
        boolean hasClosingSpace = closingStart > titleStart
                ? isHorizontalWhitespace(text.charAt(closingStart - 1))
                : titleStart > markerEnd;
        if (closingStart < titleEnd && hasClosingSpace) {
            titleEnd = closingStart > titleStart ? closingStart - 1 : closingStart;
            while (titleEnd > titleStart && isHorizontalWhitespace(text.charAt(titleEnd - 1))) {
                titleEnd--;
            }
        }

        headings.add(new MarkdownHeading(level, text.substring(titleStart, titleEnd), lineIndex));
    }

    private static boolean isHorizontalWhitespace(char character) {
        return character == ' ' || character == '\t';
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
