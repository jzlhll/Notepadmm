package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;

/**
 * Markdown 代码块整块圆角矩形背景管理器：
 * 后台解析 commonmark AST 收集围栏/缩进代码块的行区间（sourceSpans 每行一个，含围栏行），
 * 前台对代码块各行设置段落样式类（md-code-block-first/mid/last/single），
 * 由 CSS 在段落 TextFlow（Region）上绘制背景与 12px 圆角（首行上圆角、末行下圆角拼接成整块）。
 * 段落样式变更不进 undo（plainText undo 只订阅文本变更），与行内图片等其他段落样式互不覆盖。
 * 超大文档（isRealtimeProcessingLimitReached）自动停用并清理样式。
 */
public final class MarkdownCodeBlockManager {
    private static final long REFRESH_DELAY_MS = 600;
    /** 首行（含围栏行）上圆角 */
    static final String PARA_FIRST = "md-code-block-first";
    /** 中间行 */
    static final String PARA_MID = "md-code-block-mid";
    /** 末行（含围栏行）下圆角 */
    static final String PARA_LAST = "md-code-block-last";
    /** 单行代码块整体圆角 */
    static final String PARA_SINGLE = "md-code-block-single";
    private static final Set<String> PARA_CLASSES = Set.of(PARA_FIRST, PARA_MID, PARA_LAST, PARA_SINGLE);

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private final Action0 textChangedAction = this::onTextChanged;

    private EditorArea currentArea;
    private PauseTransition refreshDelay;
    /** 行号 → 当前已应用的段落样式类 */
    private Map<Integer, String> lineStyles = Map.of();
    private boolean runtimeActive;
    private boolean destroyed;
    private long requestId;
    private Future<?> parseTask;

    public MarkdownCodeBlockManager(EditorArea area) {
        bindEditor(area);
    }

    public void destroy() {
        destroyed = true;
        unbindEditor();
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
        area.getEditor().textChanged.addAction(textChangedAction);
        if (isOverLimit(area)) {
            return;
        }
        activateRuntime();
        startRefresh();
    }

    private void unbindEditor() {
        var area = currentArea;
        if (area == null) {
            return;
        }
        area.getEditor().textChanged.removeAction(textChangedAction);
        deactivateRuntime();
        clearAllStyles(area);
        currentArea = null;
    }

    private void onTextChanged() {
        invalidateRefresh();
        var area = currentArea;
        if (isOverLimit(area)) {
            deactivateRuntime();
            clearAllStyles(area);
            return;
        }
        activateRuntime();
        refreshDelay.playFromStart();
    }

    private void startRefresh() {
        invalidateRefresh();
        var area = currentArea;
        if (destroyed || !runtimeActive || !MarkdownImageManager.supports(area) || isOverLimit(area)) {
            return;
        }
        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        long currentRequestId = requestId;
        parseTask = ThreadUtils.submit(() -> {
            try {
                var blocks = parseBlocks(text);
                if (!ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> applyBlocks(area, contentVersion, currentRequestId, blocks));
                }
            } catch (RuntimeException e) {
                Log.e("parse markdown code blocks failed", e);
            }
        });
    }

    private void applyBlocks(EditorArea area, long contentVersion, long parsedRequestId,
                             List<int[]> blocks) {
        if (destroyed || area != currentArea || parsedRequestId != requestId
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        parseTask = null;
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
            if (!Objects.equals(lineStyles.get(line), newStyles.get(line))) {
                setParagraphStyleClass(area, line, newStyles.get(line));
            }
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
        boolean changed = false;
        for (String style : existing) {
            if (PARA_CLASSES.contains(style) && !style.equals(styleClass)) {
                changed = true;
            } else {
                merged.add(style);
            }
        }
        if (styleClass != null && !merged.contains(styleClass)) {
            merged.add(styleClass);
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
        refreshDelay = new PauseTransition(Duration.millis(REFRESH_DELAY_MS));
        refreshDelay.setOnFinished(event -> startRefresh());
    }

    private void deactivateRuntime() {
        runtimeActive = false;
        invalidateRefresh();
        if (refreshDelay != null) {
            refreshDelay.setOnFinished(null);
            refreshDelay = null;
        }
    }

    private void invalidateRefresh() {
        if (refreshDelay != null) {
            refreshDelay.stop();
        }
        requestId++;
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
    private static List<int[]> parseBlocks(String text) {
        var collector = new CodeBlockCollector();
        PARSER.parse(text).accept(collector);
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
