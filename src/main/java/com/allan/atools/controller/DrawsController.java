package com.allan.atools.controller;

import com.allan.atools.UIContext;
import com.allan.atools.bases.AbstractController;
import com.allan.atools.bases.XmlPaths;
import com.allan.atools.threads.ThreadUtils;
import com.allan.atools.tools.moduledraws.DrawsVectorParser;
import com.google.gson.Gson;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXTextArea;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/** 展示目录中的 VectorDrawable 与普通图片。 */
@XmlPaths(paths = {"pages", "content_draws.fxml"})
public final class DrawsController extends AbstractController {
    private static final String CONFIG_KEY = "drawsPaths";
    private static final Gson GSON = new Gson();
    private static final List<String> IMAGE_EXTENSIONS = List.of("png", "jpg", "jpeg", "webp");
    private static final String BG_CHECKER = "棋盘格";
    private static final String BG_LIGHT = "浅色";
    private static final String BG_DARK = "深色";
    private static final String BG_GRAY = "灰色";
    private static final String BG_ORANGE_20 = "20% 透明度橙色";
    private static final double NAME_AREA_HEIGHT = 44.0;
    private static final double NAME_TEXT_HEIGHT = 36.0;

    @FXML
    private VBox pageRoot;
    @FXML
    private JFXTextArea directoryInput;
    @FXML
    private JFXButton loadButton;
    @FXML
    private JFXButton clearButton;
    @FXML
    private JFXComboBox<Integer> sizeMode;
    @FXML
    private JFXComboBox<String> backgroundMode;
    @FXML
    private Label countLabel;
    @FXML
    private Label loadingLabel;
    @FXML
    private VBox emptyState;
    @FXML
    private VBox contentArea;
    @FXML
    private VBox vectorSection;
    @FXML
    private Label vectorCountLabel;
    @FXML
    private FlowPane vectorGrid;
    @FXML
    private VBox imageSection;
    @FXML
    private Label imageCountLabel;
    @FXML
    private FlowPane imageGrid;
    @FXML
    private VBox unsupportedBox;
    @FXML
    private VBox unsupportedList;

    private final List<PreviewCard> previewCards = new ArrayList<>();
    private Task<ScanResult> currentTask;
    private int currentCellSize = 120;
    private String currentBackground = BG_GRAY;
    private final ChangeListener<Number> pageWidthListener =
            (observable, oldValue, newValue) -> updateAdaptiveCardSize();

    @Override
    public void init(Stage stage) {
        super.init(stage);
        configureActions();
        restoreDirectories();
    }

    private void configureActions() {
        sizeMode.getItems().setAll(90, 120, 150);
        sizeMode.getSelectionModel().select(Integer.valueOf(currentCellSize));
        sizeMode.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            currentCellSize = newValue;
            updateAdaptiveCardSize();
        });
        pageRoot.widthProperty().addListener(pageWidthListener);

        backgroundMode.getItems().setAll(BG_CHECKER, BG_LIGHT, BG_DARK, BG_GRAY, BG_ORANGE_20);
        backgroundMode.getSelectionModel().select(currentBackground);
        backgroundMode.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            currentBackground = newValue;
            previewCards.forEach(card -> card.updateBackground(currentBackground));
        });

        loadButton.setOnAction(event -> loadFromInput(true));
        clearButton.setOnAction(event -> clearAll());
        directoryInput.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && event.isShortcutDown()) {
                loadFromInput(true);
                event.consume();
            }
        });
    }

    private void restoreDirectories() {
        String saved = UIContext.sharedPref.getString(CONFIG_KEY, null);
        if (saved == null || saved.isBlank()) {
            renderResult(ScanResult.empty());
            return;
        }

        try {
            String[] paths = GSON.fromJson(saved, String[].class);
            if (paths == null || paths.length == 0) {
                renderResult(ScanResult.empty());
                return;
            }
            directoryInput.setText(String.join(System.lineSeparator(), paths));
            loadFromInput(false);
        } catch (RuntimeException ignored) {
            UIContext.sharedPref.edit().remove(CONFIG_KEY).commit();
            renderResult(ScanResult.empty());
        }
    }

    private void loadFromInput(boolean saveConfig) {
        List<String> directories = parseDirectories(directoryInput.getText());
        directoryInput.setText(String.join(System.lineSeparator(), directories));
        if (saveConfig) {
            UIContext.sharedPref.edit().putString(CONFIG_KEY, GSON.toJson(directories)).commit();
        }

        cancelCurrentTask();
        if (directories.isEmpty()) {
            renderResult(ScanResult.empty());
            return;
        }

        clearResultNodes();
        setLoading(true);
        var task = new Task<ScanResult>() {
            @Override
            protected ScanResult call() {
                return scanDirectories(directories, this::isCancelled);
            }
        };
        currentTask = task;
        task.setOnSucceeded(event -> {
            if (currentTask != task) {
                return;
            }
            currentTask = null;
            setLoading(false);
            renderResult(task.getValue());
        });
        task.setOnCancelled(event -> {
            if (currentTask == task) {
                currentTask = null;
                setLoading(false);
            }
        });
        task.setOnFailed(event -> {
            if (currentTask != task) {
                return;
            }
            currentTask = null;
            setLoading(false);
            String message = task.getException() == null || task.getException().getMessage() == null
                    ? "未知错误"
                    : task.getException().getMessage();
            renderResult(ScanResult.empty());
            countLabel.setText("加载失败：" + message);
            setVisibleManaged(countLabel, true);
        });
        ThreadUtils.run(task);
    }

    private List<String> parseDirectories(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        String[] tokens = input.split("[\\r\\n,;，；]+");
        Map<String, String> uniquePaths = new LinkedHashMap<>();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        for (String token : tokens) {
            String value = stripWrappingQuotes(token.trim());
            if (value.isBlank()) {
                continue;
            }
            String normalized = normalizePath(value);
            String uniqueKey = windows ? normalized.toLowerCase(Locale.ROOT) : normalized;
            uniquePaths.putIfAbsent(uniqueKey, normalized);
        }
        return new ArrayList<>(uniquePaths.values());
    }

    private String normalizePath(String value) {
        String expanded = value;
        if ("~".equals(value)) {
            expanded = System.getProperty("user.home");
        } else if (value.startsWith("~/") || value.startsWith("~\\")) {
            expanded = System.getProperty("user.home") + File.separator + value.substring(2);
        }
        try {
            return Path.of(expanded).toAbsolutePath().normalize().toString();
        } catch (InvalidPathException ignored) {
            return expanded;
        }
    }

    private String stripWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private ScanResult scanDirectories(List<String> directories, BooleanSupplier cancelled) {
        var previews = new ArrayList<PreviewItem>();
        var unsupported = new ArrayList<UnsupportedFile>();
        var directoryIssues = new ArrayList<DirectoryIssue>();
        var importedPaths = new LinkedHashSet<String>();
        int totalFiles = 0;

        for (String directoryText : directories) {
            if (cancelled.getAsBoolean()) {
                break;
            }
            Path directory;
            try {
                directory = Path.of(directoryText);
            } catch (InvalidPathException e) {
                directoryIssues.add(new DirectoryIssue(directoryText, "目录地址无效"));
                continue;
            }
            if (!Files.exists(directory)) {
                directoryIssues.add(new DirectoryIssue(directoryText, "目录不存在"));
                continue;
            }
            if (!Files.isDirectory(directory)) {
                directoryIssues.add(new DirectoryIssue(directoryText, "不是目录"));
                continue;
            }
            if (!Files.isReadable(directory)) {
                directoryIssues.add(new DirectoryIssue(directoryText, "目录不可读"));
                continue;
            }

            List<Path> files;
            try (Stream<Path> stream = Files.list(directory)) {
                files = stream
                        .filter(Files::isRegularFile)
                        .filter(path -> !path.getFileName().toString().startsWith("."))
                        .sorted(Comparator.comparing(
                                path -> path.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .toList();
            } catch (IOException | SecurityException e) {
                directoryIssues.add(new DirectoryIssue(directoryText, "读取目录失败"));
                continue;
            }

            for (Path file : files) {
                if (cancelled.getAsBoolean()) {
                    break;
                }
                String uniquePath = realPath(file);
                if (!importedPaths.add(uniquePath)) {
                    continue;
                }
                totalFiles++;
                loadFile(file, previews, unsupported);
            }
        }
        return new ScanResult(previews, unsupported, directoryIssues, totalFiles);
    }

    private String realPath(Path file) {
        try {
            return file.toRealPath().toString();
        } catch (IOException ignored) {
            return file.toAbsolutePath().normalize().toString();
        }
    }

    private void loadFile(
            Path file,
            List<PreviewItem> previews,
            List<UnsupportedFile> unsupported) {
        String name = file.getFileName().toString();
        int separatorIndex = name.lastIndexOf('.');
        String extension = separatorIndex < 0
                ? "未知"
                : name.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
        if ("xml".equals(extension)) {
            loadXmlFile(file, previews, unsupported);
            return;
        }
        if (!IMAGE_EXTENSIONS.contains(extension)) {
            unsupported.add(new UnsupportedFile(file, extension, "文件类型不支持"));
            return;
        }
        try {
            var bufferedImage = ImageIO.read(file.toFile());
            if (bufferedImage == null) {
                unsupported.add(new UnsupportedFile(file, extension, "图片解码失败"));
                return;
            }
            Image image = SwingFXUtils.toFXImage(bufferedImage, null);
            previews.add(PreviewItem.image(file, image));
        } catch (Exception e) {
            unsupported.add(new UnsupportedFile(file, extension, "图片解码失败"));
        }
    }

    private void loadXmlFile(
            Path file,
            List<PreviewItem> previews,
            List<UnsupportedFile> unsupported) {
        try {
            var vector = DrawsVectorParser.parse(file);
            previews.add(PreviewItem.vector(file, vector));
        } catch (DrawsVectorParser.UnsupportedDrawableException e) {
            unsupported.add(new UnsupportedFile(file, "xml", "不支持 " + e.rootTag()));
        } catch (DrawsVectorParser.VectorDrawableParseException e) {
            unsupported.add(new UnsupportedFile(file, "xml", "VectorDrawable 解析失败"));
        } catch (DrawsVectorParser.XmlParseException e) {
            unsupported.add(new UnsupportedFile(file, "xml", "XML 解析失败"));
        }
    }

    private void renderResult(ScanResult result) {
        clearResultNodes();
        int vectorCount = 0;
        int imageCount = 0;
        for (PreviewItem preview : result.previews()) {
            var card = new PreviewCard(preview);
            previewCards.add(card);
            if (preview.type() == PreviewType.VECTOR) {
                vectorGrid.getChildren().add(card.root());
                vectorCount++;
            } else {
                imageGrid.getChildren().add(card.root());
                imageCount++;
            }
        }
        updateAdaptiveCardSize();

        vectorCountLabel.setText("VectorDrawable（" + vectorCount + "）");
        imageCountLabel.setText("普通图片（" + imageCount + "）");
        setVisibleManaged(vectorSection, vectorCount > 0);
        setVisibleManaged(imageSection, imageCount > 0);
        setVisibleManaged(contentArea, vectorCount + imageCount > 0);
        setVisibleManaged(emptyState, vectorCount + imageCount == 0);
        renderUnsupported(result.unsupported(), result.directoryIssues());

        int loaded = result.previews().size();
        int unsupportedCount = result.unsupported().size();
        int issueCount = result.directoryIssues().size();
        if (result.totalFiles() == 0 && issueCount == 0) {
            setVisibleManaged(countLabel, false);
        } else {
            countLabel.setText("扫描 " + result.totalFiles()
                    + "，已加载 " + loaded
                    + "，不支持 " + unsupportedCount
                    + "，目录错误 " + issueCount);
            setVisibleManaged(countLabel, true);
        }
    }

    private void renderUnsupported(
            List<UnsupportedFile> unsupportedFiles,
            List<DirectoryIssue> directoryIssues) {
        if (!directoryIssues.isEmpty()) {
            unsupportedList.getChildren().add(groupTitle("目录错误（" + directoryIssues.size() + "）"));
            for (DirectoryIssue issue : directoryIssues) {
                unsupportedList.getChildren().add(detailLabel(issue.path() + " — " + issue.reason()));
            }
        }

        Map<String, List<UnsupportedFile>> groups = new TreeMap<>();
        for (UnsupportedFile unsupported : unsupportedFiles) {
            groups.computeIfAbsent(unsupported.extension(), key -> new ArrayList<>()).add(unsupported);
        }
        for (var entry : groups.entrySet()) {
            unsupportedList.getChildren().add(groupTitle("." + entry.getKey() + "（" + entry.getValue().size() + "）"));
            for (UnsupportedFile file : entry.getValue()) {
                unsupportedList.getChildren().add(detailLabel(file.path() + " — " + file.reason()));
            }
        }
        setVisibleManaged(unsupportedBox, !directoryIssues.isEmpty() || !unsupportedFiles.isEmpty());
    }

    private Label groupTitle(String text) {
        var label = new Label(text);
        label.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -au-default-text-color;");
        return label;
    }

    private Label detailLabel(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-font-size: 13px; -fx-text-fill: -au-default-desc-color; -fx-padding: 0 0 4 12;");
        return label;
    }

    private void clearAll() {
        cancelCurrentTask();
        directoryInput.clear();
        UIContext.sharedPref.edit().remove(CONFIG_KEY).commit();
        renderResult(ScanResult.empty());
    }

    private void clearResultNodes() {
        previewCards.clear();
        vectorGrid.getChildren().clear();
        imageGrid.getChildren().clear();
        unsupportedList.getChildren().clear();
        setVisibleManaged(vectorSection, false);
        setVisibleManaged(imageSection, false);
        setVisibleManaged(contentArea, false);
        setVisibleManaged(unsupportedBox, false);
        setVisibleManaged(emptyState, false);
        setVisibleManaged(countLabel, false);
    }

    private void setLoading(boolean loading) {
        loadButton.setDisable(loading);
        setVisibleManaged(loadingLabel, loading);
    }

    private void cancelCurrentTask() {
        if (currentTask != null) {
            currentTask.cancel(true);
            currentTask = null;
            setLoading(false);
        }
    }

    private void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void updateAdaptiveCardSize() {
        double availableWidth = pageRoot.getWidth()
                - pageRoot.getInsets().getLeft()
                - pageRoot.getInsets().getRight();
        if (availableWidth <= 0.0 || previewCards.isEmpty()) {
            return;
        }
        double gap = vectorGrid.getHgap();
        int columnCount = (int) ((availableWidth + gap) / (currentCellSize + gap));
        if (columnCount < 1) {
            columnCount = 1;
        }
        double cellSize = (availableWidth - gap * (columnCount - 1)) / columnCount;
        previewCards.forEach(card -> card.updateSize(cellSize));
    }

    @Override
    public void destroy() {
        cancelCurrentTask();
        previewCards.clear();
        pageRoot.widthProperty().removeListener(pageWidthListener);
        super.destroy();
    }

    private enum PreviewType {
        VECTOR,
        IMAGE
    }

    private record PreviewItem(
            Path path,
            PreviewType type,
            DrawsVectorParser.VectorDrawable vector,
            Image image) {
        static PreviewItem vector(Path path, DrawsVectorParser.VectorDrawable vector) {
            return new PreviewItem(path, PreviewType.VECTOR, vector, null);
        }

        static PreviewItem image(Path path, Image image) {
            return new PreviewItem(path, PreviewType.IMAGE, null, image);
        }
    }

    private record UnsupportedFile(Path path, String extension, String reason) {
    }

    private record DirectoryIssue(String path, String reason) {
    }

    private record ScanResult(
            List<PreviewItem> previews,
            List<UnsupportedFile> unsupported,
            List<DirectoryIssue> directoryIssues,
            int totalFiles) {
        static ScanResult empty() {
            return new ScanResult(List.of(), List.of(), List.of(), 0);
        }
    }

    private final class PreviewCard {
        private final VBox root = new VBox();
        private final StackPane thumbnail = new StackPane();
        private final Canvas background = new Canvas();
        private final StackPane nameArea = new StackPane();
        private final Label name = new Label();
        private final Rectangle cardClip = new Rectangle();
        private ImageView imageView;
        private Pane vectorPane;
        private Group vectorScaleGroup;
        private Scale vectorScale;
        private DrawsVectorParser.VectorDrawable vector;

        PreviewCard(PreviewItem preview) {
            root.setAlignment(Pos.TOP_CENTER);
            root.setEffect(new DropShadow(3.0, Color.rgb(0, 0, 0, 0.3)));
            root.setClip(cardClip);
            thumbnail.setAlignment(Pos.CENTER);
            thumbnail.getChildren().add(background);

            if (preview.type() == PreviewType.IMAGE) {
                imageView = new ImageView(preview.image());
                imageView.setPreserveRatio(true);
                thumbnail.getChildren().add(imageView);
            } else {
                vector = preview.vector();
                vectorPane = new Pane();
                vectorScaleGroup = new Group(vector.root());
                vectorScale = new Scale(1.0, 1.0, 0.0, 0.0);
                vectorScaleGroup.getTransforms().add(vectorScale);
                vectorPane.getChildren().add(vectorScaleGroup);
                thumbnail.getChildren().add(vectorPane);
            }

            name.setText(preview.path().getFileName().toString());
            name.setWrapText(true);
            name.setAlignment(Pos.CENTER);
            name.setTextAlignment(TextAlignment.CENTER);
            name.setTextOverrun(OverrunStyle.ELLIPSIS);
            nameArea.setAlignment(Pos.CENTER);
            nameArea.getChildren().add(name);
            Tooltip.install(name, new Tooltip(preview.path().toAbsolutePath().normalize().toString()));
            root.getChildren().addAll(thumbnail, nameArea);
            updateSize(currentCellSize);
        }

        VBox root() {
            return root;
        }

        void updateSize(double cellSize) {
            root.setPrefWidth(cellSize);
            root.setMinWidth(cellSize);
            root.setMaxWidth(cellSize);
            root.setPrefHeight(cellSize + NAME_AREA_HEIGHT);
            root.setMinHeight(cellSize + NAME_AREA_HEIGHT);
            root.setMaxHeight(cellSize + NAME_AREA_HEIGHT);
            cardClip.setWidth(cellSize);
            cardClip.setHeight(cellSize + NAME_AREA_HEIGHT);
            cardClip.setArcWidth(8.0);
            cardClip.setArcHeight(8.0);

            thumbnail.setPrefSize(cellSize, cellSize);
            thumbnail.setMinSize(cellSize, cellSize);
            thumbnail.setMaxSize(cellSize, cellSize);
            background.setWidth(cellSize);
            background.setHeight(cellSize);

            double previewSize = cellSize - 20.0;
            if (imageView != null) {
                imageView.setFitWidth(previewSize);
                imageView.setFitHeight(previewSize);
            } else if (vectorPane != null) {
                vectorPane.setPrefSize(previewSize, previewSize);
                vectorPane.setMinSize(previewSize, previewSize);
                vectorPane.setMaxSize(previewSize, previewSize);
                vectorPane.setClip(new Rectangle(previewSize, previewSize));
                double widthScale = previewSize / vector.viewportWidth();
                double heightScale = previewSize / vector.viewportHeight();
                double scale = Math.min(widthScale, heightScale);
                vectorScale.setX(scale);
                vectorScale.setY(scale);
                vectorScaleGroup.setLayoutX((previewSize - vector.viewportWidth() * scale) / 2.0);
                vectorScaleGroup.setLayoutY((previewSize - vector.viewportHeight() * scale) / 2.0);
            }

            nameArea.setPrefSize(cellSize, NAME_AREA_HEIGHT);
            nameArea.setMinSize(cellSize, NAME_AREA_HEIGHT);
            nameArea.setMaxSize(cellSize, NAME_AREA_HEIGHT);
            nameArea.setClip(new Rectangle(cellSize, NAME_AREA_HEIGHT));
            name.setPrefSize(cellSize - 6.0, NAME_TEXT_HEIGHT);
            name.setMinSize(cellSize - 6.0, NAME_TEXT_HEIGHT);
            name.setMaxSize(cellSize - 6.0, NAME_TEXT_HEIGHT);
            updateBackground(currentBackground);
        }

        void updateBackground(String mode) {
            var graphics = background.getGraphicsContext2D();
            double width = background.getWidth();
            double height = background.getHeight();
            graphics.clearRect(0.0, 0.0, width, height);
            if (BG_DARK.equals(mode)) {
                graphics.setFill(Color.web("#222222"));
                graphics.fillRect(0.0, 0.0, width, height);
                name.setStyle("-fx-font-size: 13px; -fx-text-fill: #eeeeee;");
                nameArea.setStyle("-fx-background-color: rgba(0,0,0,0.6);");
            } else {
                Color backgroundColor;
                if (BG_GRAY.equals(mode)) {
                    backgroundColor = Color.web("#eeeeee");
                } else if (BG_ORANGE_20.equals(mode)) {
                    backgroundColor = Color.rgb(255, 165, 0, 0.2);
                } else {
                    backgroundColor = Color.WHITE;
                }
                graphics.setFill(backgroundColor);
                graphics.fillRect(0.0, 0.0, width, height);
                if (BG_CHECKER.equals(mode)) {
                    graphics.setFill(Color.web("#cccccc"));
                    double blockSize = 8.0;
                    for (int y = 0; y < height / blockSize + 1; y++) {
                        for (int x = 0; x < width / blockSize + 1; x++) {
                            if ((x + y) % 2 == 0) {
                                graphics.fillRect(x * blockSize, y * blockSize, blockSize, blockSize);
                            }
                        }
                    }
                }
                name.setStyle("-fx-font-size: 13px; -fx-text-fill: #333333;");
                nameArea.setStyle("-fx-background-color: rgba(255,255,255,0.9);");
            }
        }
    }
}
