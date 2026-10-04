package com.allan.atools.tools.modulenotepad.manager;

import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.supportsMarkdown;
import static com.allan.atools.richtext.codearea.MarkdownEditorSupport.textLeftPadding;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.richtext.codearea.EditorAreaMgrCode;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Paragraph;
import org.reactfx.Subscription;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.HashSet;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 行内图片显示管理器：
 * 独立成段的图片标签 ![alt](src) 或 HTML {@code <img src alt style=zoom:xx%>}（独立成行的 HtmlBlock，
 * 或段落内唯一节点的 HtmlInline）通过段落样式 {@link CodeArea#PARAGRAPH_PREF_HEIGHT_PREFIX}
 * 撑高行高（richtextfx ParagraphBox.computePrefHeight 只算文本高度，graphic 不撑高行），
 * 图片本体放在 paragraph graphic 的容器中（水平位置 = 文本左内边距 + 行号补偿宽度，
 * 不动态测量行号宽度），经子节点溢出绘制在标签行下方，不会推移文本。
 * 相对路径基于 md 文件目录解析，兼容 Windows 反斜杠写法；
 * 图片按原图尺寸显示，style=zoom:xx% 按原图尺寸百分比缩放（Typora 语义，不支持手势/鼠标缩放），
 * 宽度超出编辑器可视宽时等比收缩。
 * 段落样式变更不进 undo（plainText undo 只订阅文本变更）。
 */
public final class MarkdownImageManager {
    private static final long REFRESH_DELAY_MS = 220;
    private static final long MAX_REFRESH_WAIT_MS = 450;
    /** 原图尺寸未知时（后台加载完成前）的占位显示高度（逻辑像素） */
    private static final double PLACEHOLDER_IMAGE_HEIGHT = 180;
    private static final double GAP_TOP = 4;
    private static final double GAP_BOTTOM = 8;
    /** 图片框 CSS 边框宽度（与 editor_markdown.css 中 .markdown-image-frame 保持一致） */
    private static final double FRAME_BORDER = 1;
    private static final double PLACEHOLDER_WIDTH = 320;
    private static final double PLACEHOLDER_HEIGHT = 48;
    private static final int IMAGE_CACHE_LIMIT = 48;
    /** 行号区域补偿宽度：不动态测量行号宽度，图片在文本左内边距基础上再右移该值，避免压到行号 */
    private static final double LINE_NO_COMPENSATE = 100;
    private static final String IMAGE_PARA_CLASS = "markdown-image-para";
    private static final String PREF_HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREF_HEIGHT_PREFIX;

    /** HTML <img> 标签：属性值带引号时内部可含 '>'，尾部 '/' 不计入属性 */
    private static final Pattern HTML_IMG_TAG = Pattern.compile(
            "(?i)<img\\b((?:\"[^\"]*\"|'[^']*'|[^'\">])*?)/?>");
    /** 标签属性 key="val" / key='val' / key=val */
    private static final Pattern HTML_IMG_ATTR = Pattern.compile(
            "(?i)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    /** Typora 风格 style="zoom:40%" */
    private static final Pattern STYLE_ZOOM_PATTERN = Pattern.compile(
            "(?i)zoom\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    private Subscription textChanges;

    private record CachedImage(java.lang.ref.WeakReference<Image> image, double width, double height) {}
    // 图片节点和共享缓存负责强引用，正文索引只保留尺寸和弱引用。
    private final LinkedHashMap<String, CachedImage> imageCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CachedImage> eldest) {
            return size() > IMAGE_CACHE_LIMIT;
        }
    };
    private EditorArea currentArea;
    private final LatestRefreshScheduler refreshScheduler =
            new LatestRefreshScheduler(REFRESH_DELAY_MS, MAX_REFRESH_WAIT_MS, this::startRefresh);
    private Map<Integer, MarkdownImage> imageByLine = Map.of();
    private boolean runtimeActive;
    private boolean destroyed;
    private Future<?> parseTask;
    private double cachedLineHeight = -1;
    private boolean layoutPending;
    private final InvalidationListener layoutChanged = observable -> {
        if (layoutPending) return;
        layoutPending = true;
        Platform.runLater(() -> {
            layoutPending = false;
            var area = currentArea;
            if (destroyed || !runtimeActive || area == null) return;
            cachedLineHeight = -1;
            area.suspendVisibleParsWhileInvoke(() -> {
                for (var info : imageByLine.values()) applyImageParagraphStyle(area, info.lineIndex, info);
            });
        });
    };

    public MarkdownImageManager(EditorArea area) {
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
        if (!supportsMarkdown(area)) {
            return;
        }
        textChanges = area.plainTextChanges().subscribe(change ->
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
        if (textChanges != null) {
            textChanges.unsubscribe();
            textChanges = null;
        }
        deactivateRuntime();
        clearAllImageStyles(area);
        currentArea = null;
    }

    private void onTextChanged(int position, String removed, String inserted) {
        if (isOverLimit(currentArea)) {
            deactivateRuntime();
            clearAllImageStyles(currentArea);
            return;
        }
        activateRuntime();
        var area = currentArea;
        int first = area.offsetToPosition(Math.min(position, area.getLength()),
                org.fxmisc.richtext.model.TwoDimensional.Bias.Forward).getMajor();
        int removedLines = (int) removed.chars().filter(c -> c == '\n').count();
        int insertedLines = (int) inserted.chars().filter(c -> c == '\n').count();
        int delta = insertedLines - removedLines;
        var next = new LinkedHashMap<Integer, MarkdownImage>();
        var moved = new HashSet<Integer>();
        boolean touched = false;
        for (var info : imageByLine.values()) {
            if (info.lineIndex >= first && info.lineIndex <= first + removedLines) {
                MarkdownImageTasks.cancel(info.loadTask);
                touched = true;
                continue;
            }
            if (info.lineIndex > first + removedLines && delta != 0) {
                info.lineIndex += delta;
                moved.add(info.lineIndex);
            }
            next.put(info.lineIndex, info);
        }
        imageByLine = next;
        // 只展开正在改动的图片行，其余图片保留节点、加载状态和缓存。
        if (touched) for (int line = first; line <= first + insertedLines; line++) {
            clearImageParagraphStyle(area, line);
            area.recreateParagraphGraphic(line);
        }
        for (int line : moved) area.recreateParagraphGraphic(line);
        refreshScheduler.request();
    }

    private void startRefresh(long requestId) {
        var area = currentArea;
        if (destroyed || !runtimeActive || !supportsMarkdown(area) || isOverLimit(area)) {
            refreshScheduler.complete(requestId, null);
            return;
        }
        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        File mdFile = area.getEditor().getSourceFile();
        parseTask = ThreadUtils.submit(() -> {
            List<MarkdownImage> images = null;
            try {
                images = parseImages(area, text, mdFile);
            } catch (RuntimeException e) {
                Log.e("parse markdown images failed", e);
            }
            var result = images;
            Platform.runLater(() -> area.runAfterMarkdownComposition(() -> finishRefresh(
                    area, contentVersion, requestId, result)));
        });
    }

    private void finishRefresh(EditorArea area, long contentVersion, long parsedRequestId,
                               List<MarkdownImage> parsed) {
        refreshScheduler.complete(parsedRequestId, () -> {
            parseTask = null;
            if (parsed != null && !destroyed && area == currentArea
                    && area.getEditor().getContentVersion() == contentVersion && !isOverLimit(area)) {
                applyImages(area, contentVersion, parsed);
            }
        });
    }

    private void applyImages(EditorArea area, long contentVersion, List<MarkdownImage> parsed) {
        if (destroyed || area != currentArea
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        cachedLineHeight = -1;
        var newByLine = new LinkedHashMap<Integer, MarkdownImage>();
        for (var info : parsed) {
            var old = imageByLine.get(info.lineIndex);
            boolean same = old != null && old.key.equals(info.key) && Objects.equals(old.alt, info.alt)
                    && old.width == info.width && old.styleZoom == info.styleZoom;
            newByLine.putIfAbsent(info.lineIndex, same ? old : info);
        }
        var changed = new HashSet<Integer>();
        for (var line : imageByLine.keySet()) {
            var old = imageByLine.get(line);
            if (newByLine.get(line) != old) {
                MarkdownImageTasks.cancel(old.loadTask);
            }
            if (!newByLine.containsKey(line)) {
                clearImageParagraphStyle(area, line);
                changed.add(line);
            }
        }
        for (var entry : newByLine.entrySet()) {
            if (imageByLine.get(entry.getKey()) != entry.getValue()) changed.add(entry.getKey());
        }
        imageByLine = newByLine;
        for (var entry : newByLine.entrySet()) {
            applyImageParagraphStyle(area, entry.getKey(), entry.getValue());
        }
        for (int line : changed) area.recreateParagraphGraphic(line);
    }

    private void activateRuntime() {
        var area = currentArea;
        if (runtimeActive || area == null) {
            return;
        }
        runtimeActive = true;
        area.widthProperty().addListener(layoutChanged);
        area.paddingProperty().addListener(layoutChanged);
        area.addParagraphGraphicDecorator(this, this::createGraphic);
    }

    private void deactivateRuntime() {
        var area = currentArea;
        if (area != null) {
            area.removeParagraphGraphicDecorator(this);
            area.widthProperty().removeListener(layoutChanged);
            area.paddingProperty().removeListener(layoutChanged);
        }
        for (var info : imageByLine.values()) MarkdownImageTasks.cancel(info.loadTask);
        runtimeActive = false;
        invalidateRefresh();
    }

    /** 段落 graphic：行号节点 + 图片（零宽容器，图片经子节点溢出绘制在标签行下方） */
    private Node createGraphic(int index, Node base) {
        var area = currentArea;
        if (area == null) {
            return base;
        }
        MarkdownImage info = imageByLine.get(index);
        if (info == null) {
            return base;
        }

        // 图片放底层、行号放顶层；图片布局使用当前源码段落的实际文字下沿。
        Node imageNode = createImageNode(area, info);
        var box = new MarkdownImageGraphic(base, imageNode,
                info.sourceHeight > 0 ? info.sourceHeight : lineHeight(area), GAP_TOP, GAP_BOTTOM, height -> {
            if (Math.abs(info.sourceHeight - height) >= 0.5) {
                info.sourceHeight = height;
                // 下一次脉冲再回填样式，避免在段落布局过程中修改文档样式。
                Platform.runLater(() -> {
                    if (!destroyed && area == currentArea && !isOverLimit(area)
                            && imageByLine.get(info.lineIndex) == info) {
                        applyImageParagraphStyle(area, info.lineIndex, info);
                    }
                });
            }
        });
        // 行号 graphic 固定在左侧（ParagraphBox.graphicOffset 绑定 scrollX），文本随水平滚动平移；
        // 图片在 graphic 内须反向减去 scrollX 才能与文本保持同步，否则左滑（水平滚动）时图片悬浮不动
        imageNode.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> textLeftPadding(area) + LINE_NO_COMPENSATE - area.estimatedScrollXProperty().getValue(),
                area.paddingProperty(), area.estimatedScrollXProperty()));
        return box;
    }

    private Node createImageNode(EditorArea area, MarkdownImage info) {
        Image image = cachedImage(info.key);
        if (image == null) {
            if (isKnownFailure(info.key)) {
                return createPlaceholder(area, info, Locales.str("markdownImageLoadFailed"));
            }
            beginLoad(area, info);
            return createPlaceholder(area, info, Locales.str("markdownImageLoading"));
        }
        if (image.isError()) {
            return createPlaceholder(area, info, Locales.str("markdownImageLoadFailed"));
        }
        var view = new ImageView(image);
        view.setPreserveRatio(true);
        view.fitHeightProperty().bind(Bindings.createDoubleBinding(
                () -> displaySize(area, info)[0], area.widthProperty(), area.paddingProperty()));
        view.fitWidthProperty().bind(Bindings.createDoubleBinding(
                () -> displaySize(area, info)[1], area.widthProperty(), area.paddingProperty()));
        var frame = new StackPane(view);
        frame.getStyleClass().add("markdown-image-frame");
        frame.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        frame.prefWidthProperty().bind(view.fitWidthProperty().add(FRAME_BORDER * 2));
        frame.prefHeightProperty().bind(view.fitHeightProperty().add(FRAME_BORDER * 2));
        return frame;
    }

    private Node createPlaceholder(EditorArea owner, MarkdownImage info, String message) {
        String text = info.alt == null || info.alt.isBlank() ? message : info.alt + " - " + message;
        var label = new Label(text);
        label.getStyleClass().add("markdown-image-placeholder");
        var frame = new StackPane(label);
        frame.getStyleClass().add("markdown-image-frame");
        frame.setPrefHeight(PLACEHOLDER_HEIGHT);
        frame.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> Math.min(PLACEHOLDER_WIDTH, availableImageWidth(owner)), owner.widthProperty(), owner.paddingProperty()));
        frame.setMinWidth(Region.USE_PREF_SIZE);
        label.setWrapText(true);
        frame.setOnMouseClicked(event -> {
            var area = currentArea;
            if (area == null || destroyed || imageByLine.get(info.lineIndex) != info) return;
            if (info.loading) return;
            if (info.loadLog != null) info.loadLog.event("retry requested");
            removeCachedImage(info.key);
            info.loading = false;
            beginLoad(area, info);
            area.recreateParagraphGraphic(info.lineIndex);
        });
        return frame;
    }

    /** [0]=显示高度 [1]=显示宽度；zoom 按原图尺寸百分比缩放（Typora 语义），原图未加载时退回占位高度，宽度超限时等比收缩 */
    private double[] displaySize(EditorArea area, MarkdownImage info) {
        synchronized (this) {
            var cached = imageCache.get(info.key);
            if (cached != null) {
                info.imageWidth = cached.width();
                info.imageHeight = cached.height();
            }
        }
        double aspect = info.imageWidth > 0 && info.imageHeight > 0
                ? info.imageWidth / info.imageHeight : 4.0 / 3.0;
        // zoom 相对原图尺寸（Typora 语义）：无 zoom 按原图显示；原图未加载完成前用占位高度，
        // 加载完成后 onImageLoaded 会重算段落高度并刷新 graphic
        double height = info.imageHeight > 0
                ? info.imageHeight * info.styleZoom : PLACEHOLDER_IMAGE_HEIGHT;
        if (info.width > 0) height = info.width / aspect;
        double maxWidth = availableImageWidth(area);
        if (height * aspect > maxWidth) {
            height = maxWidth / aspect;
        }
        return new double[]{height, height * aspect};
    }

    private double availableImageWidth(EditorArea area) {
        return Math.max(1.0, area.getWidth() - area.getInsets().getLeft() - area.getInsets().getRight()
                - textLeftPadding(area) - LINE_NO_COMPENSATE - FRAME_BORDER * 2);
    }

    private int totalParagraphHeight(EditorArea area, MarkdownImage info) {
        double nodeHeight = isFailedImage(info) ? PLACEHOLDER_HEIGHT : displaySize(area, info)[0] + FRAME_BORDER * 2;
        double sourceHeight = info.sourceHeight > 0 ? info.sourceHeight : lineHeight(area);
        return (int) Math.ceil(sourceHeight + GAP_TOP + nodeHeight + GAP_BOTTOM);
    }

    private void applyImageParagraphStyle(EditorArea area, int index, MarkdownImage info) {
        if (index < 0 || index >= area.getParagraphs().size()) {
            return;
        }
        var existing = new ArrayList<String>(area.getParagraph(index).getParagraphStyle());
        var merged = new ArrayList<String>();
        for (String style : existing) {
            if (style.equals(IMAGE_PARA_CLASS) || style.startsWith(PREF_HEIGHT_PREFIX)) {
                continue;
            }
            merged.add(style);
        }
        merged.add(IMAGE_PARA_CLASS);
        merged.add(PREF_HEIGHT_PREFIX + totalParagraphHeight(area, info));
        if (!merged.equals(existing)) {
            area.setParagraphStyle(index, merged);
        }
    }

    private void clearImageParagraphStyle(EditorArea area, int index) {
        if (index < 0 || index >= area.getParagraphs().size()) {
            return;
        }
        var existing = new ArrayList<String>(area.getParagraph(index).getParagraphStyle());
        var merged = new ArrayList<String>();
        boolean changed = false;
        for (String style : existing) {
            if (style.equals(IMAGE_PARA_CLASS) || style.startsWith(PREF_HEIGHT_PREFIX)) {
                changed = true;
                continue;
            }
            merged.add(style);
        }
        if (changed) {
            area.setParagraphStyle(index, merged);
        }
    }

    private void clearAllImageStyles(EditorArea area) {
        if (area == null) {
            return;
        }
        for (var line : imageByLine.keySet()) {
            clearImageParagraphStyle(area, line);
            area.recreateParagraphGraphic(line);
        }
        imageByLine = Map.of();
    }

    private void beginLoad(EditorArea area, MarkdownImage info) {
        if (info.loading) {
            return;
        }
        info.loading = true;
        var diagnostic = new MarkdownImageLoadLog("body", info.key,
                area.getEditor().getSourceFile(), info.lineIndex + 1);
        info.loadLog = diagnostic;
        info.loadTask = MarkdownImageTasks.submit(() -> {
            MarkdownImageDecoder.Result decoded;
            try {
                decoded = loadImage(info, diagnostic);
            } catch (Exception e) {
                diagnostic.fail("load threw exception", e);
                decoded = null;
            }
            if (Thread.currentThread().isInterrupted()) return;
            Image image = decoded == null ? null : decoded.getImage();
            diagnostic.complete(image);
            cacheImage(info.key, decoded);
            Image loaded = image;
            if (!ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                Platform.runLater(() -> onImageLoaded(area, info, loaded, diagnostic));
            } else {
                diagnostic.event("result skipped application closing or worker interrupted");
            }
        });
    }

    private void onImageLoaded(EditorArea area, MarkdownImage info, Image image, MarkdownImageLoadLog diagnostic) {
        if (diagnostic != null) diagnostic.complete(image);
        if (area.getMarkdownComposing()) {
            area.runAfterMarkdownComposition(() -> onImageLoaded(area, info, image, diagnostic));
            return;
        }
        info.loading = false;
        info.loadTask = null;
        int index = info.lineIndex;
        if (destroyed || area != currentArea
                || isOverLimit(area)
                || imageByLine.get(index) != info) {
            if (diagnostic != null) diagnostic.event("result skipped stale image or inactive document");
            return;
        }
        applyImageParagraphStyle(area, index, info);
        area.recreateParagraphGraphic(index);
    }

    private MarkdownImageDecoder.Result loadImage(MarkdownImage info, MarkdownImageLoadLog diagnostic) throws IOException {
        var resolved = info.resolved;
        if (resolved.remote()) {
            return MarkdownRemoteImageLoader.loadDecoded(resolved.url(), diagnostic, 0, 0);
        }
        File file = resolved.file();
        if (file == null || !file.isFile()) throw new IOException("Local image file missing");
        return MarkdownImageDecoder.decode(file, 0, 0);
    }

    private synchronized Image cachedImage(String key) {
        var cached = imageCache.get(key);
        return cached == null ? null : cached.image().get();
    }

    private synchronized void cacheImage(String key, MarkdownImageDecoder.Result decoded) {
        imageCache.put(key, decoded == null ? null : new CachedImage(
                new java.lang.ref.WeakReference<>(decoded.getImage()), decoded.getWidth(), decoded.getHeight()));
    }

    private synchronized void removeCachedImage(String key) {
        imageCache.remove(key);
    }

    private synchronized boolean isKnownFailure(String key) {
        return imageCache.containsKey(key) && imageCache.get(key) == null;
    }

    private boolean isFailedImage(MarkdownImage info) {
        Image image = cachedImage(info.key);
        return image == null ? isKnownFailure(info.key) : image.isError();
    }

    private double lineHeight(EditorArea area) {
        if (cachedLineHeight > 0) {
            return cachedLineHeight;
        }
        Font font = null;
        for (Node node : area.lookupAll(".text")) {
            if (node instanceof Text text && !text.getText().isBlank()) {
                font = text.getFont();
                break;
            }
        }
        if (font == null) {
            font = Font.font("monospace", 14);
        }
        Text probe = new Text("Ag");
        probe.setFont(font);
        cachedLineHeight = probe.getLayoutBounds().getHeight();
        return cachedLineHeight;
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
        return area == null || !area.getMarkdownPreviewEnabled() || area.getEditor().isRealtimeProcessingLimitReached();
    }

    private static List<MarkdownImage> parseImages(EditorArea area, String text, File mdFile) {
        var collector = new ImageCollector();
        ((EditorAreaMgrCode) area.getEditor()).parseMarkdown(text).accept(collector);
        if (collector.found.isEmpty()) {
            return List.of();
        }
        var images = new ArrayList<MarkdownImage>();
        for (var found : collector.found) {
            var resolved = resolve(mdFile, found.destination());
            if (resolved == null) {
                continue;
            }
            images.add(new MarkdownImage(found.lineIndex(), found.alt(), resolved, found.styleZoom(), found.width()));
        }
        return List.copyOf(images);
    }

    /**
     * 收集"整段只有一个图片"的段落与 HTML {@code <img>} 标签：
     * 独立成行的 {@code <img>} 由 commonmark 解析为 HtmlBlock（含列表项、引用块内），
     * 段落内唯一节点的 HtmlInline 也按独立图片行处理。
     */
    private static final class ImageCollector extends AbstractVisitor {
        final List<FoundImage> found = new ArrayList<>();

        @Override
        public void visit(Paragraph paragraph) {
            org.commonmark.node.Node first = paragraph.getFirstChild();
            if (first instanceof org.commonmark.node.Image image && image.getNext() == null) {
                var spans = image.getSourceSpans();
                if (!spans.isEmpty()) {
                    found.add(new FoundImage(spans.get(0).getLineIndex(),
                            image.getDestination(), altOf(image), 1.0, 0));
                }
            } else if (first instanceof HtmlInline inline && inline.getNext() == null) {
                addHtmlImgs(inline.getLiteral(), firstLine(inline), found);
            }
            visitChildren(paragraph);
        }

        @Override
        public void visit(HtmlBlock block) {
            addHtmlImgs(block.getLiteral(), firstLine(block), found);
        }

        private static int firstLine(org.commonmark.node.Node node) {
            var spans = node.getSourceSpans();
            return spans.isEmpty() ? 0 : spans.get(0).getLineIndex();
        }

        /** 从原始 HTML 中扫描 <img> 标签，行号按标签在块内跨过的换行数叠加 */
        private static void addHtmlImgs(String literal, int startLine, List<FoundImage> found) {
            if (literal == null) {
                return;
            }
            Matcher matcher = HTML_IMG_TAG.matcher(literal);
            while (matcher.find()) {
                var parsed = parseImgTag(matcher.group(1));
                if (parsed != null) {
                    found.add(new FoundImage(startLine + countNewlines(literal, 0, matcher.start()),
                            parsed.src(), parsed.alt(), parsed.zoom(), parsed.width()));
                }
            }
        }

        private static int countNewlines(String s, int from, int to) {
            int count = 0;
            for (int i = from; i < to; i++) {
                if (s.charAt(i) == '\n') {
                    count++;
                }
            }
            return count;
        }

        private static String altOf(org.commonmark.node.Image image) {
            var builder = new StringBuilder();
            for (var child = image.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof org.commonmark.node.Text text) {
                    builder.append(text.getLiteral());
                }
            }
            return builder.toString();
        }
    }

    /** 解析 <img> 标签属性，缺 src 或非图片标签时返回 null */
    private static ImgAttr parseImgTag(String attributes) {
        String src = null;
        String alt = null;
        double zoom = 1.0;
        double width = 0;
        Matcher matcher = HTML_IMG_ATTR.matcher(attributes);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = matcher.group(3) != null ? matcher.group(3)
                    : matcher.group(4) != null ? matcher.group(4) : matcher.group(5);
            if (value == null) {
                continue;
            }
            switch (name) {
                case "src" -> src = value;
                case "alt" -> alt = value;
                case "width" -> {
                    try { width = Math.max(0, Math.min(10000, Double.parseDouble(value))); } catch (NumberFormatException ignored) { }
                }
                case "style" -> {
                    Matcher zoomMatcher = STYLE_ZOOM_PATTERN.matcher(value);
                    if (zoomMatcher.find()) {
                        zoom = Double.parseDouble(zoomMatcher.group(1)) / 100.0;
                    }
                }
                default -> { }
            }
        }
        if (src == null || src.isBlank()) {
            return null;
        }
        return new ImgAttr(src.trim(), alt, zoom, width);
    }

    private record ImgAttr(String src, String alt, double zoom, double width) {
    }

    private static Resolved resolve(File mdFile, String destination) {
        String dest = destination.trim();
        if (dest.isEmpty()) {
            return null;
        }
        String lower = dest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return new Resolved(dest, null, true);
        }
        if (lower.startsWith("data:")) {
            return null;
        }
        if (lower.startsWith("file://")) {
            try {
                return new Resolved(dest, new File(new URI(dest)), false);
            } catch (Exception e) {
                return null;
            }
        }
        // 兼容 Windows 反斜杠路径（如 ..\pictures\x.png）
        String path = dest.replace('\\', '/');
        File direct = new File(path);
        if (direct.isAbsolute()) {
            return new Resolved(direct.toURI().toString(), direct, false);
        }
        if (mdFile != null && mdFile.getParentFile() != null) {
            File relative = new File(mdFile.getParentFile(), path);
            if (!relative.isFile()) {
                try {
                    File decoded = new File(mdFile.getParentFile(), new URI(path.replace(" ", "%20")).getPath());
                    if (decoded.isFile()) relative = decoded;
                } catch (java.net.URISyntaxException ignored) { }
            }
            return new Resolved(relative.toURI().toString(), relative, false);
        }
        return null;
    }

    private record FoundImage(int lineIndex, String destination, String alt, double styleZoom, double width) {
    }

    private record Resolved(String url, File file, boolean remote) {
    }

    private static final class MarkdownImage {
        int lineIndex;
        final String alt;
        final String key;
        final Resolved resolved;
        /** {@code <img style="zoom:xx%">} 的显示缩放系数（1.0 = 不缩放） */
        final double styleZoom;
        final double width;
        double imageWidth = -1;
        double imageHeight = -1;
        double sourceHeight = -1;
        boolean loading;
        Future<?> loadTask;
        MarkdownImageLoadLog loadLog;

        MarkdownImage(int lineIndex, String alt, Resolved resolved, double styleZoom, double width) {
            this.lineIndex = lineIndex;
            this.alt = alt;
            this.key = resolved.url();
            this.resolved = resolved;
            this.styleZoom = styleZoom;
            this.width = width;
        }
    }
}
