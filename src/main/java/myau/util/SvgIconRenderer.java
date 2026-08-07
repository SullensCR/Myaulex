package myau.util;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class SvgIconRenderer {
    private static final Map<String, Integer> textureCache = new HashMap<>();
    private static final String SVG_DIR = "SVGs/";
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        preloadIcons();
    }

    private static void preloadIcons() {
        String[] categories = {"combat", "movement", "player", "world", "visuals", "utilities"};
        for (String cat : categories) {
            loadSvg("category-" + cat + ".svg");
        }
        loadSvg("button-remove_keybind.svg");
    }

    public static int loadSvg(String filename) {
        if (textureCache.containsKey(filename)) {
            return textureCache.get(filename);
        }
        try {
            File file = new File(SVG_DIR + filename);
            if (!file.exists()) {
                return -1;
            }
            BufferedImage bi = renderSvgToImage(file, 32, 32);
            if (bi == null) return -1;

            int texId = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
            ByteBuffer buf = BufferUtils.createByteBuffer(32 * 32 * 4);
            for (int y = 31; y >= 0; y--) {
                for (int x = 0; x < 32; x++) {
                    int rgba = bi.getRGB(x, y);
                    buf.put((byte) ((rgba >> 16) & 0xFF));
                    buf.put((byte) ((rgba >> 8) & 0xFF));
                    buf.put((byte) (rgba & 0xFF));
                    buf.put((byte) ((rgba >> 24) & 0xFF));
                }
            }
            buf.flip();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 32, 32, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            textureCache.put(filename, texId);
            return texId;
        } catch (Exception e) {
            return -1;
        }
    }

    private static BufferedImage renderSvgToImage(File svgFile, int width, int height) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(svgFile);
            Element root = doc.getDocumentElement();

            String viewBox = root.getAttribute("viewBox");
            double vx = 0, vy = 0, vw = width, vh = height;
            if (viewBox != null && !viewBox.isEmpty()) {
                String[] parts = viewBox.trim().split(" ");
                if (parts.length == 4) {
                    vx = Double.parseDouble(parts[0]);
                    vy = Double.parseDouble(parts[1]);
                    vw = Double.parseDouble(parts[2]);
                    vh = Double.parseDouble(parts[3]);
                }
            }

            BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            double scaleX = width / vw;
            double scaleY = height / vh;
            g.scale(scaleX, scaleY);
            g.translate(-vx, -vy);

            NodeList paths = root.getElementsByTagName("path");
            for (int i = 0; i < paths.getLength(); i++) {
                Element pathEl = (Element) paths.item(i);
                String d = pathEl.getAttribute("d");
                if (d == null || d.isEmpty()) continue;

                Path2D path = parseSvgPath(d);
                if (path == null) continue;

                String fill = pathEl.getAttribute("fill");
                if (fill == null || fill.isEmpty() || fill.equals("none")) {
                    g.setColor(new Color(0,0,0,0));
                } else {
                    g.setColor(Color.decode(fill));
                }
                g.fill(path);
            }
            g.dispose();
            return bi;
        } catch (Exception e) {
            return null;
        }
    }

    private static Path2D parseSvgPath(String d) {
        Path2D path = new Path2D.Double();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("([MmLlHhVvCcSsQqTtAaZz])([^MmLlHhVvCcSsQqTtAaZz]*)").matcher(d);
        double cx = 0, cy = 0;
        double startX = 0, startY = 0;
        boolean lastMoveto = false;
        String lastCmd = null;
        double lastControlX = 0, lastControlY = 0;

        while (m.find()) {
            String cmd = m.group(1);
            String argsStr = m.group(2).trim();
            double[] args = parseNumbers(argsStr);
            int idx = 0;
            boolean rel = Character.isLowerCase(cmd.charAt(0));
            char up = Character.toUpperCase(cmd.charAt(0));

            switch (up) {
                case 'M':
                    while (idx + 1 < args.length) {
                        double x = rel ? cx + args[idx] : args[idx];
                        double y = rel ? cy + args[idx+1] : args[idx+1];
                        path.moveTo(x, y);
                        startX = x; startY = y; cx = x; cy = y;
                        idx += 2;
                        if (idx == 2) break;
                    }
                    lastMoveto = true;
                    break;
                case 'L':
                    while (idx + 1 < args.length) {
                        double x = rel ? cx + args[idx] : args[idx];
                        double y = rel ? cy + args[idx+1] : args[idx+1];
                        path.lineTo(x, y);
                        cx = x; cy = y;
                        idx += 2;
                    }
                    lastMoveto = false;
                    break;
                case 'H':
                    while (idx < args.length) {
                        double x = rel ? cx + args[idx] : args[idx];
                        path.lineTo(x, cy);
                        cx = x;
                        idx++;
                    }
                    lastMoveto = false;
                    break;
                case 'V':
                    while (idx < args.length) {
                        double y = rel ? cy + args[idx] : args[idx];
                        path.lineTo(cx, y);
                        cy = y;
                        idx++;
                    }
                    lastMoveto = false;
                    break;
                case 'C':
                    while (idx + 5 < args.length) {
                        double x1 = rel ? cx + args[idx] : args[idx];
                        double y1 = rel ? cy + args[idx+1] : args[idx+1];
                        double x2 = rel ? cx + args[idx+2] : args[idx+2];
                        double y2 = rel ? cy + args[idx+3] : args[idx+3];
                        double x = rel ? cx + args[idx+4] : args[idx+4];
                        double y = rel ? cy + args[idx+5] : args[idx+5];
                        path.curveTo(x1, y1, x2, y2, x, y);
                        lastControlX = x2; lastControlY = y2;
                        cx = x; cy = y;
                        idx += 6;
                    }
                    lastMoveto = false;
                    break;
                case 'S':
                    while (idx + 3 < args.length) {
                        double x2 = rel ? cx + args[idx] : args[idx];
                        double y2 = rel ? cy + args[idx+1] : args[idx+1];
                        double x = rel ? cx + args[idx+2] : args[idx+2];
                        double y = rel ? cy + args[idx+3] : args[idx+3];
                        double x1 = cx + (cx - lastControlX);
                        double y1 = cy + (cy - lastControlY);
                        path.curveTo(x1, y1, x2, y2, x, y);
                        lastControlX = x2; lastControlY = y2;
                        cx = x; cy = y;
                        idx += 4;
                    }
                    lastMoveto = false;
                    break;
                case 'Q':
                    while (idx + 3 < args.length) {
                        double x1 = rel ? cx + args[idx] : args[idx];
                        double y1 = rel ? cy + args[idx+1] : args[idx+1];
                        double x = rel ? cx + args[idx+2] : args[idx+2];
                        double y = rel ? cy + args[idx+3] : args[idx+3];
                        path.quadTo(x1, y1, x, y);
                        lastControlX = x1; lastControlY = y1;
                        cx = x; cy = y;
                        idx += 4;
                    }
                    lastMoveto = false;
                    break;
                case 'T':
                    while (idx + 1 < args.length) {
                        double x = rel ? cx + args[idx] : args[idx];
                        double y = rel ? cy + args[idx+1] : args[idx+1];
                        double x1 = cx + (cx - lastControlX);
                        double y1 = cy + (cy - lastControlY);
                        path.quadTo(x1, y1, x, y);
                        lastControlX = x1; lastControlY = y1;
                        cx = x; cy = y;
                        idx += 2;
                    }
                    lastMoveto = false;
                    break;
                case 'A':
                case 'a':
                    while (idx + 5 < args.length) {
                        idx += 6;
                    }
                    lastMoveto = false;
                    break;
                case 'Z':
                case 'z':
                    path.closePath();
                    cx = startX; cy = startY;
                    lastMoveto = false;
                    break;
            }
            lastCmd = cmd;
        }
        return path;
    }

    private static double[] parseNumbers(String s) {
        java.util.List<Double> list = new java.util.ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[-+]?(?:\\d+\\.?\\d*|\\\\.\\d+)(?:[eE][-+]?\\d+)?").matcher(s);
        while (m.find()) {
            list.add(Double.parseDouble(m.group()));
        }
        double[] arr = new double[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    public static int getTexture(String filename) {
        return textureCache.getOrDefault(filename, -1);
    }

    public static void renderIcon(String filename, int x, int y, int size) {
        int tex = getTexture(filename);
        if (tex < 0) return;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(x + size, y);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(x + size, y + size);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(x, y + size);
        GL11.glEnd();
        GL11.glPopMatrix();
    }
}
