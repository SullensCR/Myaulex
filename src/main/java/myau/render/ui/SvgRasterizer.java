package myau.render.ui;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small dependency-free SVG rasterizer for the supplied notification vectors. */
final class SvgRasterizer {
    private static final Pattern COMMANDS = Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)");
    private static final Pattern NUMBERS = Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?");

    private SvgRasterizer() {
    }

    static BufferedImage render(String resourcePath) {
        try (InputStream input = UiResource.open(resourcePath)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            Document document = factory.newDocumentBuilder().parse(input);
            Element root = document.getDocumentElement();

            float[] viewBox = viewBox(root);
            int width = Math.max(1, Math.round(number(root.getAttribute("width"), viewBox[2])));
            int height = Math.max(1, Math.round(number(root.getAttribute("height"), viewBox[3])));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.scale(width / viewBox[2], height / viewBox[3]);
            graphics.translate(-viewBox[0], -viewBox[1]);

            NodeList paths = root.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Element element = (Element) paths.item(i);
                Path2D path = parsePath(element.getAttribute("d"));
                if (path == null) continue;
                Paint paint = paintFor(document, element.getAttribute("fill"), viewBox);
                if (paint != null) {
                    graphics.setPaint(paint);
                    graphics.fill(path);
                }
            }
            graphics.dispose();
            return image;
        } catch (Exception failure) {
            throw new IllegalStateException("Unable to rasterize SVG resource /assets/myau/" + resourcePath, failure);
        }
    }

    private static Paint paintFor(Document document, String fill, float[] viewBox) {
        if (fill == null || fill.isEmpty() || "none".equals(fill)) return null;
        if (!fill.startsWith("url(#") || !fill.endsWith(")")) return color(fill);

        String id = fill.substring(5, fill.length() - 1);
        NodeList gradients = document.getElementsByTagName("linearGradient");
        for (int i = 0; i < gradients.getLength(); i++) {
            Element gradient = (Element) gradients.item(i);
            if (!id.equals(gradient.getAttribute("id"))) continue;

            NodeList stops = gradient.getElementsByTagName("stop");
            if (stops.getLength() < 2) return null;
            Color first = stopColor((Element) stops.item(0));
            Color last = stopColor((Element) stops.item(stops.getLength() - 1));
            float x1 = number(gradient.getAttribute("x1"), viewBox[0]);
            float y1 = number(gradient.getAttribute("y1"), viewBox[1]);
            float x2 = number(gradient.getAttribute("x2"), viewBox[2]);
            float y2 = number(gradient.getAttribute("y2"), viewBox[3]);
            return new GradientPaint(x1, y1, first, x2, y2, last);
        }
        return null;
    }

    private static Color stopColor(Element stop) {
        Color color = color(stop.getAttribute("stop-color"));
        String opacity = stop.getAttribute("stop-opacity");
        if (opacity.isEmpty()) return color;
        int alpha = Math.max(0, Math.min(255, Math.round(number(opacity, 1.0F) * 255.0F)));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color color(String value) {
        if (value == null || value.isEmpty() || "none".equals(value)) return null;
        if ("white".equalsIgnoreCase(value)) return Color.WHITE;
        if ("black".equalsIgnoreCase(value)) return Color.BLACK;
        if (value.startsWith("#")) {
            String hex = value.substring(1);
            if (hex.length() == 3) {
                hex = "" + hex.charAt(0) + hex.charAt(0)
                        + hex.charAt(1) + hex.charAt(1)
                        + hex.charAt(2) + hex.charAt(2);
            }
            return new Color(Integer.parseInt(hex, 16));
        }
        return Color.WHITE;
    }

    private static float[] viewBox(Element root) {
        String[] parts = root.getAttribute("viewBox").trim().split("[ ,]+");
        if (parts.length == 4) {
            return new float[]{number(parts[0], 0), number(parts[1], 0),
                    number(parts[2], 1), number(parts[3], 1)};
        }
        return new float[]{0, 0, number(root.getAttribute("width"), 1), number(root.getAttribute("height"), 1)};
    }

    private static float number(String value, float fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        String normalized = value.trim().replace("px", "");
        try {
            return Float.parseFloat(normalized);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Path2D parsePath(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        Path2D path = new Path2D.Double();
        Matcher matcher = COMMANDS.matcher(value);
        double x = 0, y = 0, startX = 0, startY = 0;
        double controlX = 0, controlY = 0;
        while (matcher.find()) {
            char command = matcher.group(1).charAt(0);
            boolean relative = Character.isLowerCase(command);
            char upper = Character.toUpperCase(command);
            double[] args = numbers(matcher.group(2));
            int index = 0;
            switch (upper) {
                case 'M':
                    while (index + 1 < args.length) {
                        double nextX = relative ? x + args[index] : args[index];
                        double nextY = relative ? y + args[index + 1] : args[index + 1];
                        path.moveTo(nextX, nextY);
                        x = startX = nextX;
                        y = startY = nextY;
                        index += 2;
                        if (index == 2) relative = Character.isLowerCase(command);
                    }
                    break;
                case 'L':
                    while (index + 1 < args.length) {
                        x = relative ? x + args[index] : args[index];
                        y = relative ? y + args[index + 1] : args[index + 1];
                        path.lineTo(x, y);
                        index += 2;
                    }
                    break;
                case 'H':
                    while (index < args.length) {
                        x = relative ? x + args[index] : args[index];
                        path.lineTo(x, y);
                        index++;
                    }
                    break;
                case 'V':
                    while (index < args.length) {
                        y = relative ? y + args[index] : args[index];
                        path.lineTo(x, y);
                        index++;
                    }
                    break;
                case 'C':
                    while (index + 5 < args.length) {
                        double x1 = relative ? x + args[index] : args[index];
                        double y1 = relative ? y + args[index + 1] : args[index + 1];
                        double x2 = relative ? x + args[index + 2] : args[index + 2];
                        double y2 = relative ? y + args[index + 3] : args[index + 3];
                        x = relative ? x + args[index + 4] : args[index + 4];
                        y = relative ? y + args[index + 5] : args[index + 5];
                        path.curveTo(x1, y1, x2, y2, x, y);
                        controlX = x2;
                        controlY = y2;
                        index += 6;
                    }
                    break;
                case 'S':
                    while (index + 3 < args.length) {
                        double x1 = 2 * x - controlX;
                        double y1 = 2 * y - controlY;
                        double x2 = relative ? x + args[index] : args[index];
                        double y2 = relative ? y + args[index + 1] : args[index + 1];
                        x = relative ? x + args[index + 2] : args[index + 2];
                        y = relative ? y + args[index + 3] : args[index + 3];
                        path.curveTo(x1, y1, x2, y2, x, y);
                        controlX = x2;
                        controlY = y2;
                        index += 4;
                    }
                    break;
                case 'Q':
                    while (index + 3 < args.length) {
                        double x1 = relative ? x + args[index] : args[index];
                        double y1 = relative ? y + args[index + 1] : args[index + 1];
                        x = relative ? x + args[index + 2] : args[index + 2];
                        y = relative ? y + args[index + 3] : args[index + 3];
                        path.quadTo(x1, y1, x, y);
                        controlX = x1;
                        controlY = y1;
                        index += 4;
                    }
                    break;
                case 'T':
                    while (index + 1 < args.length) {
                        double x1 = 2 * x - controlX;
                        double y1 = 2 * y - controlY;
                        x = relative ? x + args[index] : args[index];
                        y = relative ? y + args[index + 1] : args[index + 1];
                        path.quadTo(x1, y1, x, y);
                        controlX = x1;
                        controlY = y1;
                        index += 2;
                    }
                    break;
                case 'A':
                    // The supplied notification assets contain no elliptical arcs.
                    break;
                case 'Z':
                    path.closePath();
                    x = startX;
                    y = startY;
                    break;
                default:
                    break;
            }
        }
        return path;
    }

    private static double[] numbers(String value) {
        List<Double> result = new ArrayList<>();
        Matcher matcher = NUMBERS.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(Double.parseDouble(matcher.group()));
        double[] values = new double[result.size()];
        for (int i = 0; i < values.length; i++) values[i] = result.get(i);
        return values;
    }
}
