package com.allan.atools.tools.moduledraws;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Affine;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.Locale;

/** 将静态 Android VectorDrawable 转换为 JavaFX 图形。 */
public final class DrawsVectorParser {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private DrawsVectorParser() {
    }

    public record VectorDrawable(Group root, double viewportWidth, double viewportHeight) {
    }

    public static final class UnsupportedDrawableException extends Exception {
        private final String rootTag;

        public UnsupportedDrawableException(String rootTag) {
            this.rootTag = rootTag;
        }

        public String rootTag() {
            return rootTag;
        }
    }

    public static final class XmlParseException extends Exception {
        public XmlParseException(Throwable cause) {
            super(cause);
        }
    }

    public static final class VectorDrawableParseException extends Exception {
        public VectorDrawableParseException(Throwable cause) {
            super(cause);
        }
    }

    public static VectorDrawable parse(Path file)
            throws UnsupportedDrawableException, XmlParseException, VectorDrawableParseException {
        Element vector;
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    throw exception;
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    throw exception;
                }
            });
            vector = builder.parse(file.toFile()).getDocumentElement();
        } catch (Exception e) {
            throw new XmlParseException(e);
        }

        if (vector == null) {
            throw new XmlParseException(new IllegalArgumentException("XML root is empty"));
        }
        String rootTag = tagName(vector);
        if (!"vector".equals(rootTag)) {
            throw new UnsupportedDrawableException(rootTag);
        }

        try {
            double viewportWidth = numberAttr(vector, "viewportWidth", 24.0);
            double viewportHeight = numberAttr(vector, "viewportHeight", 24.0);
            if (viewportWidth <= 0.0 || viewportHeight <= 0.0) {
                throw new IllegalArgumentException("Vector viewport is invalid");
            }

            var root = new Group();
            var viewportBounds = new Rectangle(viewportWidth, viewportHeight, Color.TRANSPARENT);
            viewportBounds.setMouseTransparent(true);
            root.getChildren().add(viewportBounds);
            parseChildren(vector, root);
            root.setOpacity(alphaAttr(vector, "alpha", 1.0));
            return new VectorDrawable(root, viewportWidth, viewportHeight);
        } catch (RuntimeException e) {
            throw new VectorDrawableParseException(e);
        }
    }

    private static void parseChildren(Element source, Group output) {
        Group currentOutput = output;
        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node childNode = children.item(i);
            if (!(childNode instanceof Element child)) {
                continue;
            }

            switch (tagName(child)) {
                case "group" -> {
                    var group = new Group();
                    applyGroupTransform(child, group);
                    currentOutput.getChildren().add(group);
                    parseChildren(child, group);
                }
                case "path" -> currentOutput.getChildren().add(createPath(child, false));
                case "clip-path" -> {
                    var clippedOutput = new Group();
                    clippedOutput.setClip(createPath(child, true));
                    currentOutput.getChildren().add(clippedOutput);
                    currentOutput = clippedOutput;
                }
                default -> {
                }
            }
        }
    }

    private static SVGPath createPath(Element element, boolean clipPath) {
        String pathData = attr(element, "pathData");
        if (pathData.isBlank()) {
            throw new IllegalArgumentException("Vector pathData is empty");
        }

        var path = new SVGPath();
        path.setContent(pathData);
        if (clipPath) {
            path.setFill(Color.BLACK);
            return path;
        }

        String fillType = attr(element, "fillType");
        if ("evenOdd".equalsIgnoreCase(fillType)) {
            path.setFillRule(FillRule.EVEN_ODD);
        }

        Color fillColor = parseColor(attr(element, "fillColor"), Color.BLACK);
        path.setFill(withAlpha(fillColor, alphaAttr(element, "fillAlpha", 1.0)));

        String strokeColorText = attr(element, "strokeColor");
        if (!strokeColorText.isBlank()) {
            Color strokeColor = parseColor(strokeColorText, Color.TRANSPARENT);
            path.setStroke(withAlpha(strokeColor, alphaAttr(element, "strokeAlpha", 1.0)));
            path.setStrokeWidth(numberAttr(element, "strokeWidth", 0.0));
            path.setStrokeLineCap(parseLineCap(attr(element, "strokeLineCap")));
            path.setStrokeLineJoin(parseLineJoin(attr(element, "strokeLineJoin")));
            path.setStrokeMiterLimit(numberAttr(element, "strokeMiterLimit", 4.0));
        }
        return path;
    }

    private static void applyGroupTransform(Element element, Group group) {
        double pivotX = numberAttr(element, "pivotX", 0.0);
        double pivotY = numberAttr(element, "pivotY", 0.0);
        double scaleX = numberAttr(element, "scaleX", 1.0);
        double scaleY = numberAttr(element, "scaleY", 1.0);
        double rotation = numberAttr(element, "rotation", 0.0);
        double translateX = numberAttr(element, "translateX", 0.0);
        double translateY = numberAttr(element, "translateY", 0.0);

        var affine = new Affine();
        affine.appendTranslation(translateX + pivotX, translateY + pivotY);
        affine.appendRotation(rotation);
        affine.appendScale(scaleX, scaleY);
        affine.appendTranslation(-pivotX, -pivotY);
        group.getTransforms().add(affine);
    }

    private static StrokeLineCap parseLineCap(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "round" -> StrokeLineCap.ROUND;
            case "square" -> StrokeLineCap.SQUARE;
            default -> StrokeLineCap.BUTT;
        };
    }

    private static StrokeLineJoin parseLineJoin(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "round" -> StrokeLineJoin.ROUND;
            case "bevel" -> StrokeLineJoin.BEVEL;
            default -> StrokeLineJoin.MITER;
        };
    }

    private static Color parseColor(String value, Color fallback) {
        if (value.isBlank()) {
            return fallback;
        }
        String color = value.trim();
        if ("@android:color/transparent".equals(color)) {
            return Color.TRANSPARENT;
        }
        if (color.startsWith("@") || color.startsWith("?")) {
            return fallback;
        }

        try {
            if (color.startsWith("#")) {
                return parseHexColor(color);
            }
            return Color.web(color);
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static Color parseHexColor(String color) {
        String hex = color.substring(1);
        return switch (hex.length()) {
            case 3 -> color(
                    duplicateHex(hex.charAt(0)),
                    duplicateHex(hex.charAt(1)),
                    duplicateHex(hex.charAt(2)),
                    255);
            case 4 -> color(
                    duplicateHex(hex.charAt(1)),
                    duplicateHex(hex.charAt(2)),
                    duplicateHex(hex.charAt(3)),
                    duplicateHex(hex.charAt(0)));
            case 6 -> color(
                    hexByte(hex, 0),
                    hexByte(hex, 2),
                    hexByte(hex, 4),
                    255);
            case 8 -> color(
                    hexByte(hex, 2),
                    hexByte(hex, 4),
                    hexByte(hex, 6),
                    hexByte(hex, 0));
            default -> throw new IllegalArgumentException("Unsupported color");
        };
    }

    private static Color color(int red, int green, int blue, int alpha) {
        return Color.rgb(red, green, blue, alpha / 255.0);
    }

    private static int duplicateHex(char value) {
        int digit = Character.digit(value, 16);
        if (digit < 0) {
            throw new IllegalArgumentException("Invalid color");
        }
        return digit * 17;
    }

    private static int hexByte(String value, int start) {
        return Integer.parseInt(value.substring(start, start + 2), 16);
    }

    private static Color withAlpha(Color color, double alpha) {
        double targetAlpha = clampAlpha(color.getOpacity() * alpha);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), targetAlpha);
    }

    private static double alphaAttr(Element element, String name, double defaultValue) {
        return clampAlpha(numberAttr(element, name, defaultValue));
    }

    private static double clampAlpha(double alpha) {
        if (alpha < 0.0) {
            return 0.0;
        }
        if (alpha > 1.0) {
            return 1.0;
        }
        return alpha;
    }

    private static double numberAttr(Element element, String name, double defaultValue) {
        String value = attr(element, name);
        if (value.isBlank()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String attr(Element element, String name) {
        String value = element.getAttributeNS(ANDROID_NS, name);
        if (!value.isBlank()) {
            return value;
        }
        value = element.getAttribute("android:" + name);
        return value.isBlank() ? element.getAttribute(name) : value;
    }

    private static String tagName(Element element) {
        String localName = element.getLocalName();
        return localName == null ? element.getTagName() : localName;
    }
}
