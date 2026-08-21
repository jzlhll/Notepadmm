package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Paragraph;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.IntFunction;
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
    private static final long REFRESH_DELAY_MS = 600;
    /** 原图尺寸未知时（后台加载完成前）的占位显示高度（逻辑像素） */
    private static final double PLACEHOLDER_IMAGE_HEIGHT = 180;
    private static final double GAP_TOP = 4;
    private static final double GAP_BOTTOM = 8;
    /** 图片框 CSS 边框宽度（与 editor_markdown.css 中 .markdown-image-frame 保持一致） */
    private static final double FRAME_BORDER = 1;
    private static final double PLACEHOLDER_WIDTH = 320;
    private static final double PLACEHOLDER_HEIGHT = 48;
    private static final int IMAGE_CACHE_LIMIT = 48;
    /** 与 editor.css 中 .paragraph-text 左内边距一致（未换行 / 换行），图片水平位置据此与文本对齐 */
    private static final double TEXT_LEFT_PADDING = 65;
    private static final double TEXT_LEFT_PADDING_WRAPPED = 105;
    /** 行号区域补偿宽度：不动态测量行号宽度，图片在文本左内边距基础上再右移该值，避免压到行号 */
    private static final double LINE_NO_COMPENSATE = 100;
    private static final String IMAGE_PARA_CLASS = "markdown-image-para";
    private static final String PREF_HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREF_HEIGHT_PREFIX;

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    /** HTML <img> 标签：属性值带引号时内部可含 '>'，尾部 '/' 不计入属性 */
    private static final Pattern HTML_IMG_TAG = Pattern.compile(
            "(?i)<img\\b((?:\"[^\"]*\"|'[^']*'|[^'\">])*?)/?>");
    /** 标签属性 key="val" / key='val' / key=val */
    private static final Pattern HTML_IMG_ATTR = Pattern.compile(
            "(?i)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'=<>`]+))");
    /** Typora 风格 style="zoom:40%" */
    private static final Pattern STYLE_ZOOM_PATTERN = Pattern.compile(
            "(?i)zoom\\s*:\\s*(\\d+(?:\\.\\d+)?)\\s*%");
    private boolean composingFactory;
    private final Action0 textChangedAction = this::onTextChanged;
    /** 外部（行号开关等）更换 graphic 工厂时重新包一层，保证图片与行号共存 */
    private final ChangeListener<IntFunction<? extends Node>> factoryListener = (obs, old, now) -> {
        if (composingFactory) {
            return;
        }
        baseGraphicFactory = now;
        installComposedFactory();
    };

    private final LinkedHashMap<String, Image> imageCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > IMAGE_CACHE_LIMIT;
        }
    };

    private EditorArea currentArea;
    private PauseTransition refreshDelay;
    /** 行号等基础 graphic 工厂（可能为 null） */
    private IntFunction<? extends Node> baseGraphicFactory;
    private Map<Integer, MarkdownImage> imageByLine = Map.of();
    private boolean runtimeActive;
    private boolean destroyed;
    private long requestId;
    private long shownContentVersion = -1;
    private Future<?> parseTask;
    private double cachedLineHeight = -1;

    public MarkdownImageManager(EditorArea area) {
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
        if (!supports(area)) {
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
        clearAllImageStyles(area);
        currentArea = null;
    }

    private void onTextChanged() {
        invalidateRefresh();
        if (isOverLimit(currentArea)) {
            deactivateRuntime();
            clearAllImageStyles(currentArea);
            return;
        }
        activateRuntime();
        refreshDelay.playFromStart();
    }

    private void startRefresh() {
        invalidateRefresh();
        var area = currentArea;
        if (destroyed || !runtimeActive || !supports(area) || isOverLimit(area)) {
            return;
        }
        long contentVersion = area.getEditor().getContentVersion();
        String text = area.getText();
        File mdFile = area.getEditor().getSourceFile();
        long currentRequestId = requestId;
        parseTask = ThreadUtils.submit(() -> {
            try {
                var images = parseImages(text, mdFile);
                if (images != null && !ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                    Platform.runLater(() -> applyImages(area, contentVersion, currentRequestId, images));
                }
            } catch (RuntimeException e) {
                Log.e("parse markdown images failed", e);
            }
        });
    }

    private void applyImages(EditorArea area, long contentVersion, long parsedRequestId,
                             List<MarkdownImage> parsed) {
        if (destroyed || area != currentArea || parsedRequestId != requestId
                || area.getEditor().getContentVersion() != contentVersion || isOverLimit(area)) {
            return;
        }
        parseTask = null;
        cachedLineHeight = -1;
        var newByLine = new LinkedHashMap<Integer, MarkdownImage>();
        for (var info : parsed) {
            newByLine.putIfAbsent(info.lineIndex, info);
        }
        for (var line : imageByLine.keySet()) {
            if (!newByLine.containsKey(line)) {
                clearImageParagraphStyle(area, line);
            }
        }
        imageByLine = newByLine;
        for (var entry : newByLine.entrySet()) {
            applyImageParagraphStyle(area, entry.getKey(), entry.getValue());
        }
        shownContentVersion = contentVersion;
    }

    private void activateRuntime() {
        var area = currentArea;
        if (runtimeActive || area == null) {
            return;
        }
        runtimeActive = true;
        refreshDelay = new PauseTransition(Duration.millis(REFRESH_DELAY_MS));
        refreshDelay.setOnFinished(event -> startRefresh());
        baseGraphicFactory = area.paragraphGraphicFactoryProperty().get();
        area.paragraphGraphicFactoryProperty().addListener(factoryListener);
        installComposedFactory();
    }

    private void installComposedFactory() {
        var area = currentArea;
        if (area == null) {
            return;
        }
        composingFactory = true;
        try {
            area.setParagraphGraphicFactory(this::createGraphic);
        } finally {
            composingFactory = false;
        }
    }

    private void deactivateRuntime() {
        var area = currentArea;
        if (area != null) {
            area.paragraphGraphicFactoryProperty().removeListener(factoryListener);
            composingFactory = true;
            try {
                area.setParagraphGraphicFactory(baseGraphicFactory);
            } finally {
                composingFactory = false;
            }
        }
        runtimeActive = false;
        invalidateRefresh();
        if (refreshDelay != null) {
            refreshDelay.setOnFinished(null);
            refreshDelay = null;
        }
    }

    /** 段落 graphic：行号节点 + 图片（零宽容器，图片经子节点溢出绘制在标签行下方） */
    private Node createGraphic(int index) {
        var area = currentArea;
        if (area == null) {
            return null;
        }
        Node base = baseGraphicFactory != null ? baseGraphicFactory.apply(index) : null;
        MarkdownImage info = imageByLine.get(index);
        if (info == null) {
            return base;
        }

        double baseWidth = base != null ? base.prefWidth(-1) : 0;
        var box = new Pane();
        box.setMinWidth(baseWidth);
        box.setPrefWidth(baseWidth);
        box.setMaxWidth(baseWidth);

        // 图片放底层、行号放顶层：水平滚动时图片会滚过行号区域，图片背景横条不能盖住行号
        Node imageNode = createImageNode(area, index, info);
        double nodeHeight = imageNode.prefHeight(-1);
        double reserved = totalParagraphHeight(area, info);
        // 行号背景撑满图片段落整个高度：ParagraphBox 只把 graphic（本容器）拉伸到段落高，
        // 容器内的行号 Label 仍保持单行高度，行号列会透出编辑器背景形成"白条"；
        // 撑高后不透明行号背景铺满行号列，水平滚动时也能遮住滑入行号列的图片
        if (base instanceof Region region) {
            region.setMinHeight(reserved);
            region.setPrefHeight(reserved);
            region.setMaxHeight(reserved);
        }
        double textLeft = area.isWrapText() ? TEXT_LEFT_PADDING_WRAPPED : TEXT_LEFT_PADDING;
        double baseX = textLeft + LINE_NO_COMPENSATE;
        imageNode.relocate(baseX,
                Math.max(lineHeight(area) + GAP_TOP, reserved - nodeHeight - GAP_BOTTOM));
        // 行号 graphic 固定在左侧（ParagraphBox.graphicOffset 绑定 scrollX），文本随水平滚动平移；
        // 图片在 graphic 内须反向减去 scrollX 才能与文本保持同步，否则左滑（水平滚动）时图片悬浮不动
        imageNode.layoutXProperty().bind(Bindings.createDoubleBinding(
                () -> baseX - area.estimatedScrollXProperty().getValue(),
                area.estimatedScrollXProperty()));
        box.getChildren().add(imageNode);
        if (base != null) {
            box.getChildren().add(base);
        }
        return box;
    }

    private Node createImageNode(EditorArea area, int index, MarkdownImage info) {
        Image image = cachedImage(info.key);
        if (image == null) {
            if (isKnownFailure(info.key)) {
                return createPlaceholder(info, Locales.str("markdownImageLoadFailed"));
            }
            beginLoad(area, index, info);
            return createPlaceholder(info, Locales.str("markdownImageLoading"));
        }
        if (image.isError()) {
            return createPlaceholder(info, Locales.str("markdownImageLoadFailed"));
        }
        if (image.getProgress() < 1.0) {
            // 远程图片后台加载中
            trackProgress(area, index, info, image);
            return createPlaceholder(info, Locales.str("markdownImageLoading"));
        }

        var view = new ImageView(image);
        view.setPreserveRatio(true);
        double[] size = displaySize(area, info);
        view.setFitHeight(size[0]);
        var frame = new StackPane(view);
        frame.getStyleClass().add("markdown-image-frame");
        frame.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        frame.setPrefSize(size[1] + FRAME_BORDER * 2, size[0] + FRAME_BORDER * 2);
        return frame;
    }

    private Node createPlaceholder(MarkdownImage info, String message) {
        String text = info.alt == null || info.alt.isBlank() ? message : info.alt + " - " + message;
        var label = new Label(text);
        label.getStyleClass().add("markdown-image-placeholder");
        var frame = new StackPane(label);
        frame.getStyleClass().add("markdown-image-frame");
        frame.setPrefSize(PLACEHOLDER_WIDTH, PLACEHOLDER_HEIGHT);
        return frame;
    }

    /** [0]=显示高度 [1]=显示宽度；zoom 按原图尺寸百分比缩放（Typora 语义），原图未加载时退回占位高度，宽度超限时等比收缩 */
    private double[] displaySize(EditorArea area, MarkdownImage info) {
        Image image = cachedImage(info.key);
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            info.imageWidth = image.getWidth();
            info.imageHeight = image.getHeight();
        }
        double aspect = info.imageWidth > 0 && info.imageHeight > 0
                ? info.imageWidth / info.imageHeight : 4.0 / 3.0;
        // zoom 相对原图尺寸（Typora 语义）：无 zoom 按原图显示；原图未加载完成前用占位高度，
        // 加载完成后 onImageLoaded 会重算段落高度并刷新 graphic
        double height = info.imageHeight > 0
                ? info.imageHeight * info.styleZoom : PLACEHOLDER_IMAGE_HEIGHT;
        double maxWidth = Math.max(160.0, area.getWidth() - 48);
        if (height * aspect > maxWidth) {
            height = maxWidth / aspect;
        }
        return new double[]{height, height * aspect};
    }

    private int totalParagraphHeight(EditorArea area, MarkdownImage info) {
        double nodeHeight = isFailedImage(info) ? PLACEHOLDER_HEIGHT : displaySize(area, info)[0] + FRAME_BORDER * 2;
        return (int) Math.ceil(lineHeight(area) + GAP_TOP + nodeHeight + GAP_BOTTOM);
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
        }
        imageByLine = Map.of();
    }

    private void beginLoad(EditorArea area, int index, MarkdownImage info) {
        if (info.loading) {
            return;
        }
        info.loading = true;
        ThreadUtils.submit(() -> {
            Image image;
            try {
                image = loadImage(info);
            } catch (Exception e) {
                Log.e("load markdown image failed: " + info.key, e);
                image = null;
            }
            cacheImage(info.key, image);
            Image loaded = image;
            if (!ThreadUtils.sBeClosing && !Thread.currentThread().isInterrupted()) {
                Platform.runLater(() -> onImageLoaded(area, index, info, loaded));
            }
        });
    }

    private void onImageLoaded(EditorArea area, int index, MarkdownImage info, Image image) {
        info.loading = false;
        if (destroyed || area != currentArea
                || shownContentVersion != area.getEditor().getContentVersion()
                || imageByLine.get(index) != info) {
            return;
        }
        if (image != null && !image.isError() && image.getWidth() > 0) {
            info.imageWidth = image.getWidth();
            info.imageHeight = image.getHeight();
        }
        applyImageParagraphStyle(area, index, info);
        area.recreateParagraphGraphic(index);
    }

    /** 远程图片后台加载进度监听：完成后刷新 graphic（onImageLoaded 幂等，两个监听独立触发） */
    private void trackProgress(EditorArea area, int index, MarkdownImage info, Image image) {
        image.progressProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
                if (newValue.doubleValue() >= 1.0) {
                    image.progressProperty().removeListener(this);
                    onImageLoaded(area, index, info, image);
                }
            }
        });
        image.errorProperty().addListener(new ChangeListener<>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (newValue != null && newValue) {
                    image.errorProperty().removeListener(this);
                    onImageLoaded(area, index, info, image);
                }
            }
        });
    }

    private Image loadImage(MarkdownImage info) {
        var resolved = info.resolved;
        if (resolved.remote()) {
            return new Image(resolved.url(), true);
        }
        File file = resolved.file();
        if (file == null || !file.isFile()) {
            return null;
        }
        Image image = new Image(resolved.url(), false);
        if (!image.isError()) {
            return image;
        }
        // JavaFX 不支持的格式（webp 等）走 ImageIO + twelvemonkeys
        try {
            var bufferedImage = ImageIO.read(file);
            if (bufferedImage != null) {
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
        } catch (IOException | RuntimeException e) {
            Log.e("decode markdown image failed: " + file, e);
        }
        return null;
    }

    private synchronized Image cachedImage(String key) {
        return imageCache.get(key);
    }

    private synchronized void cacheImage(String key, Image image) {
        imageCache.put(key, image);
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

    public static boolean supports(EditorArea area) {
        if (area == null || area.getEditor().getSourceFile() == null) {
            return false;
        }
        String name = area.getEditor().getSourceFile().getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    private static List<MarkdownImage> parseImages(String text, File mdFile) {
        var collector = new ImageCollector();
        PARSER.parse(text).accept(collector);
        if (collector.found.isEmpty()) {
            return List.of();
        }
        var images = new ArrayList<MarkdownImage>();
        for (var found : collector.found) {
            var resolved = resolve(mdFile, found.destination());
            if (resolved == null) {
                continue;
            }
            images.add(new MarkdownImage(found.lineIndex(), found.alt(), resolved, found.styleZoom()));
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
                            image.getDestination(), altOf(image), 1.0));
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
                            parsed.src(), parsed.alt(), parsed.zoom()));
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
        return new ImgAttr(src.trim(), alt, zoom);
    }

    private record ImgAttr(String src, String alt, double zoom) {
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
                File decoded = new File(mdFile.getParentFile(),
                        URLDecoder.decode(path, StandardCharsets.UTF_8));
                if (decoded.isFile()) {
                    relative = decoded;
                }
            }
            return new Resolved(relative.toURI().toString(), relative, false);
        }
        return null;
    }

    private record FoundImage(int lineIndex, String destination, String alt, double styleZoom) {
    }

    private record Resolved(String url, File file, boolean remote) {
    }

    private static final class MarkdownImage {
        final int lineIndex;
        final String alt;
        final String key;
        final Resolved resolved;
        /** {@code <img style="zoom:xx%">} 的显示缩放系数（1.0 = 不缩放） */
        final double styleZoom;
        double imageWidth = -1;
        double imageHeight = -1;
        boolean loading;

        MarkdownImage(int lineIndex, String alt, Resolved resolved, double styleZoom) {
            this.lineIndex = lineIndex;
            this.alt = alt;
            this.key = resolved.url();
            this.resolved = resolved;
            this.styleZoom = styleZoom;
        }
    }
}
