package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorAreaMgrCode;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import javafx.application.Platform;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.reactfx.Subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import static org.fxmisc.richtext.model.TwoDimensional.Bias.Forward;

/**
 * Markdown 代码块整块圆角矩形背景管理器：
 * 后台解析 commonmark AST 收集围栏/缩进代码块的行区间（sourceSpans 每行一个，含围栏行），
 * 前台对代码块各行设置段落样式类（md-code-block-first/mid/last/single），
 * 由 CSS 在段落 TextFlow（Region）上绘制背景与 12px 圆角（首行上圆角、末行下圆角拼接成整块）。
 * 段落样式变更不进 undo（plainText undo 只订阅文本变更），与行内图片等其他段落样式互不覆盖。
 * 超大文档（isRealtimeProcessingLimitReached）自动停用并清理样式。
 */
public final class MarkdownCodeBlockManager {
    private static final long REFRESH_DELAY_MS = 180;
    private static final long MAX_REFRESH_WAIT_MS = 400;
    /** 首行（含围栏行）上圆角 */
    static final String PARA_FIRST = "md-code-block-first";
    /** 中间行 */
    static final String PARA_MID = "md-code-block-mid";
    /** 末行（含围栏行）下圆角 */
    static final String PARA_LAST = "md-code-block-last";
    /** 单行代码块整体圆角 */
    static final String PARA_SINGLE = "md-code-block-single";
    /** 无文本或仅含空白的代码块行 */
    static final String PARA_EMPTY = "md-code-block-empty";
    private static final Set<String> PARA_CLASSES = Set.of(
            PARA_FIRST, PARA_MID, PARA_LAST, PARA_SINGLE, PARA_EMPTY);

    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(REFRESH_DELAY_MS, MAX_REFRESH_WAIT_MS, this::startRefresh);

    private EditorArea currentArea;
    private Subscription textChangeSubscription;
    /** 行号 → 当前已应用的段落样式类 */
    private Map<Integer, String> lineStyles = Map.of();
    private boolean runtimeActive;
    private boolean destroyed;
    private Future<?> parseTask;

    public MarkdownCodeBlockManager(EditorArea area) {
        bindEditor(area);
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
        refreshScheduler.dispose();
    }

    public void refreshCurrentFile(EditorArea area) {
        bindEditor(area);
    }

    private void bindEditor(EditorArea area) {
        unbindEditor();
        currentArea = area;
        if (!MarkdownImageManager.supports(area)) {
            return;
        }
        textChangeSubscription = area.plainTextChanges().subscribe(change ->
                onTextChanged(change.getPosition(), change.getRemoved(), change.getInserted()));
        if (isOverLimit(area)) {
            return;
        }
        activateRuntime();
        refreshScheduler.startNow();
    }

    private void unbindEditor() {
        var area = currentArea;
        if (area == null) {
            return;
        }
        if (textChangeSubscription != null) {
            textChangeSubscription.unsubscribe();
            textChangeSubscription = null;
        }
        deactivateRuntime();
        clearAllStyles(area);
        currentArea = null;
    }

    private void onTextChanged(int position, String removed, String inserted) {
        var area = currentArea;
        if (isOverLimit(area)) {
            deactivateRuntime();
            clearAllStyles(area);
            return;
        }
        activateRuntime();
        updateLineStylesAfterEdit(area, position, removed, inserted);
        refreshScheduler.request();
    }

    private void updateLineStylesAfterEdit(EditorArea area, int position,
                                           String removed, String inserted) {
        int editLine = area.offsetToPosition(Math.min(position, area.getLength()), Forward).getMajor();
        int removedLines = countNewlines(removed);
        int insertedLines = countNewlines(inserted);
        int oldEndLine = editLine + removedLines;
        int lineDelta = insertedLines - removedLines;
        var adjusted = new HashMap<Integer, String>();
        for (var entry : lineStyles.entrySet()) {
            int line = entry.getKey();
            if (line < editLine) {
                adjusted.put(line, entry.getValue());
            } else if (line > oldEndLine) {
                adjusted.put(line + lineDelta, entry.getValue());
            }
        }
        lineStyles = adjusted.isEmpty() ? Map.of() : adjusted;
        for (int line = editLine; line <= editLine + insertedLines; line++) {
            setParagraphStyleClass(area, line, null);
        }
    }

    private static int countNewlines(String text) {
        int count = 0;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void startRefresh(long requestId) {
        var area = currentArea;
        if (destroyed || !runtimeActive
                || !MarkdownImageManager.supports(area) || isOverLimit(area)) {
            refreshScheduler.complete(requestId, null);
            return;
        }
        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        parseTask = ThreadUtils.submit(() -> {
            List<int[]> blocks = null;
            try {
                blocks = parseBlocks(area, text);
            } catch (RuntimeException e) {
                Log.e("parse markdown code blocks failed", e);
            }
            var result = blocks;
            Platform.runLater(() -> finishRefresh(
                    area, contentVersion, requestId, result));
        });
    }

    private void finishRefresh(EditorArea area, long contentVersion, long parsedRequestId,
                               List<int[]> blocks) {
        refreshScheduler.complete(parsedRequestId, () -> {
            parseTask = null;
            if (blocks != null && !destroyed && area == currentArea
                    && area.getEditor().getContentVersion() == contentVersion && !isOverLimit(area)) {
                applyBlocks(area, contentVersion, blocks);
            }
        });
    }

    private void applyBlocks(EditorArea area, long contentVersion, List<int[]> blocks) {
        if (destroyed || area != currentArea
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        var newStyles = new HashMap<Integer, String>();
        for (int[] block : blocks) {
            for (int line = block[0]; line <= block[1]; line++) {
                newStyles.put(line, block[0] == block[1] ? PARA_SINGLE
                        : line == block[0] ? PARA_FIRST
                        : line == block[1] ? PARA_LAST
                        : PARA_MID);
            }
        }
        var lines = new HashSet<Integer>(lineStyles.keySet());
        lines.addAll(newStyles.keySet());
        for (var line : lines) {
            setParagraphStyleClass(area, line, newStyles.get(line));
        }
        lineStyles = newStyles;
    }

    /** 设置/清除某行的代码块背景类（null 为清除），保留图片等其他段落样式条目 */
    private void setParagraphStyleClass(EditorArea area, int index, String styleClass) {
        if (index < 0 || index >= area.getParagraphs().size()) {
            return;
        }
        var existing = new ArrayList<String>(area.getParagraph(index).getParagraphStyle());
        var merged = new ArrayList<String>();
        boolean empty = styleClass != null && area.getText(index).isBlank();
        boolean changed = false;
        for (String style : existing) {
            if (PARA_CLASSES.contains(style)
                    && !style.equals(styleClass) && !(empty && style.equals(PARA_EMPTY))) {
                changed = true;
            } else {
                merged.add(style);
            }
        }
        if (styleClass != null && !merged.contains(styleClass)) {
            merged.add(styleClass);
            changed = true;
        }
        if (empty && !merged.contains(PARA_EMPTY)) {
            merged.add(PARA_EMPTY);
            changed = true;
        }
        if (changed) {
            area.setParagraphStyle(index, merged);
        }
    }

    private void clearAllStyles(EditorArea area) {
        if (area == null) {
            return;
        }
        for (var line : lineStyles.keySet()) {
            setParagraphStyleClass(area, line, null);
        }
        lineStyles = Map.of();
    }

    private void activateRuntime() {
        if (runtimeActive) {
            return;
        }
        runtimeActive = true;
    }

    private void deactivateRuntime() {
        runtimeActive = false;
        invalidateRefresh();
    }

    private void invalidateRefresh() {
        refreshScheduler.invalidate();
        var task = parseTask;
        parseTask = null;
        if (task != null) {
            task.cancel(true);
        }
    }

    private static boolean isOverLimit(EditorArea area) {
        return area == null || area.getEditor().isRealtimeProcessingLimitReached();
    }

    /** 收集代码块行区间 [firstLine, lastLine]（sourceSpans 每行一个 span） */
    private static List<int[]> parseBlocks(EditorArea area, String text) {
        var collector = new CodeBlockCollector();
        ((EditorAreaMgrCode) area.getEditor()).parseMarkdown(text).accept(collector);
        return collector.blocks;
    }

    private static final class CodeBlockCollector extends AbstractVisitor {
        final List<int[]> blocks = new ArrayList<>();

        @Override
        public void visit(FencedCodeBlock block) {
            collect(block);
        }

        @Override
        public void visit(IndentedCodeBlock block) {
            collect(block);
        }

        private void collect(org.commonmark.node.Node node) {
            var spans = node.getSourceSpans();
            if (!spans.isEmpty()) {
                blocks.add(new int[]{
                        spans.get(0).getLineIndex(),
                        spans.get(spans.size() - 1).getLineIndex()});
            }
        }
    }
}
