package com.allan.atools.tools.modulenotepad.manager;

import com.allan.atools.richtext.codearea.EditorArea;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.utils.Locales;
import com.allan.atools.utils.Log;
import com.allan.baseparty.Action0;
import com.allan.uilibs.richtexts.CodeArea;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.AbstractVisitor;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

/**
 * Markdown 行内图片显示管理器：
 * 独立成段的图片标签 ![alt](src) 通过段落样式 {@link CodeArea#PARAGRAPH_PREF_HEIGHT_PREFIX}
 * 撑高行高（richtextfx ParagraphBox.computePrefHeight 只算文本高度，graphic 不撑高行），
 * 图片本体放在 paragraph graphic 的零宽左沟槽容器中，经子节点溢出绘制在标签行下方，不会推移文本。
 * 交互：图片上滚轮缩放、双击复位。段落样式变更不进 undo（plainText undo 只订阅文本变更）。
 */
public final class MarkdownImageManager {
    private static final long REFRESH_DELAY_MS = 600;
    /** 默认显示高度（逻辑像素） */
    private static final double DEFAULT_IMAGE_HEIGHT = 180;
    private static final double MIN_IMAGE_HEIGHT = 60;
    private static final double MAX_IMAGE_HEIGHT = 1400;
    private static final double GAP_TOP = 4;
    private static final double GAP_BOTTOM = 8;
    private static final double IMAGE_LEFT_GAP = 2;
    /** 图片框 CSS 边框宽度（与 editor_markdown.css 中 .markdown-image-frame 保持一致） */
    private static final double FRAME_BORDER = 1;
    private static final double PLACEHOLDER_WIDTH = 320;
    private static final double PLACEHOLDER_HEIGHT = 48;
    private static final int IMAGE_CACHE_LIMIT = 48;
    private static final double ZOOM_MIN = MIN_IMAGE_HEIGHT / DEFAULT_IMAGE_HEIGHT;
    private static final double ZOOM_MAX = MAX_IMAGE_HEIGHT / DEFAULT_IMAGE_HEIGHT;
    private static final String IMAGE_PARA_CLASS = "markdown-image-para";
    private static final String PREF_HEIGHT_PREFIX = CodeArea.PARAGRAPH_PREF_HEIGHT_PREFIX;

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

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
    /** 每张图（按地址）记忆的缩放比例 */
    private final HashMap<String, Double> zoomByDestination = new HashMap<>();
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

        var box = new Pane();
        double baseWidth = 0;
        if (base != null) {
            box.getChildren().add(base);
            baseWidth = base.prefWidth(-1);
        }
        box.setMinWidth(baseWidth);
        box.setPrefWidth(baseWidth);
        box.setMaxWidth(baseWidth);

        Node imageNode = createImageNode(area, index, info);
        double nodeHeight = imageNode.prefHeight(-1);
        double reserved = totalParagraphHeight(area, info);
        imageNode.relocate(baseWidth + IMAGE_LEFT_GAP,
                Math.max(lineHeight(area) + GAP_TOP, reserved - nodeHeight - GAP_BOTTOM));
        box.getChildren().add(imageNode);
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
        frame.addEventHandler(ScrollEvent.SCROLL, event -> {
            if (zoomImage(area, index, info, event.getDeltaY() > 0)) {
                event.consume();
            }
        });
        frame.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() >= 2 && resetZoom(area, index, info)) {
                event.consume();
            }
        });
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

    private boolean zoomImage(EditorArea area, int index, MarkdownImage info, boolean zoomIn) {
        double current = zoomOf(info);
        double next = current * (zoomIn ? 1.2 : 1 / 1.2);
        next = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, next));
        if (Math.abs(next - current) < 0.01) {
            return false;
        }
        zoomByDestination.put(info.key, next);
        applyImageParagraphStyle(area, index, info);
        return true;
    }

    private boolean resetZoom(EditorArea area, int index, MarkdownImage info) {
        Double current = zoomByDestination.get(info.key);
        if (current == null || Math.abs(current - 1.0) < 0.01) {
            return false;
        }
        zoomByDestination.remove(info.key);
        applyImageParagraphStyle(area, index, info);
        return true;
    }

    private double zoomOf(MarkdownImage info) {
        Double zoom = zoomByDestination.get(info.key);
        return zoom == null ? 1.0 : zoom;
    }

    /** [0]=显示高度 [1]=显示宽度 */
    private double[] displaySize(EditorArea area, MarkdownImage info) {
        double aspect = info.imageWidth > 0 && info.imageHeight > 0
                ? info.imageWidth / info.imageHeight : 4.0 / 3.0;
        Image image = cachedImage(info.key);
        if (image != null && image.getWidth() > 0 && image.getHeight() > 0) {
            aspect = image.getWidth() / image.getHeight();
            info.imageWidth = image.getWidth();
            info.imageHeight = image.getHeight();
        }
        double height = DEFAULT_IMAGE_HEIGHT * zoomOf(info);
        double maxWidth = Math.max(160.0, area.getWidth() - 48);
        if (height * aspect > maxWidth) {
            height = maxWidth / aspect;
        }
        height = Math.max(MIN_IMAGE_HEIGHT, Math.min(MAX_IMAGE_HEIGHT, height));
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
            images.add(new MarkdownImage(found.lineIndex(), found.alt(), resolved));
        }
        return List.copyOf(images);
    }

    /** 收集"整段只有一个图片节点"的段落（含列表项、引用块中的独立图片行） */
    private static final class ImageCollector extends AbstractVisitor {
        final List<FoundImage> found = new ArrayList<>();

        @Override
        public void visit(Paragraph paragraph) {
            if (paragraph.getFirstChild() instanceof org.commonmark.node.Image image
                    && image.getNext() == null) {
                var spans = image.getSourceSpans();
                if (!spans.isEmpty()) {
                    found.add(new FoundImage(spans.get(0).getLineIndex(),
                            image.getDestination(), altOf(image)));
                }
            }
            visitChildren(paragraph);
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
        File direct = new File(dest);
        if (direct.isAbsolute()) {
            return new Resolved(direct.toURI().toString(), direct, false);
        }
        if (mdFile != null && mdFile.getParentFile() != null) {
            File relative = new File(mdFile.getParentFile(), dest);
            if (!relative.isFile()) {
                File decoded = new File(mdFile.getParentFile(),
                        URLDecoder.decode(dest, StandardCharsets.UTF_8));
                if (decoded.isFile()) {
                    relative = decoded;
                }
            }
            return new Resolved(relative.toURI().toString(), relative, false);
        }
        return null;
    }

    private record FoundImage(int lineIndex, String destination, String alt) {
    }

    private record Resolved(String url, File file, boolean remote) {
    }

    private static final class MarkdownImage {
        final int lineIndex;
        final String alt;
        final String key;
        final Resolved resolved;
        double imageWidth = -1;
        double imageHeight = -1;
        boolean loading;

        MarkdownImage(int lineIndex, String alt, Resolved resolved) {
            this.lineIndex = lineIndex;
            this.alt = alt;
            this.key = resolved.url();
            this.resolved = resolved;
        }
    }
}
