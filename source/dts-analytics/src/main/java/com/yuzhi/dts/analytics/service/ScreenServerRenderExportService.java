package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

@Service
public class ScreenServerRenderExportService {

    public byte[] renderPng(JsonNode screenSpec, boolean watermarkEnabled, String watermarkText) throws IOException {
        RenderContext context = resolveContext(screenSpec);
        BufferedImage image = renderToImage(context, watermarkEnabled, watermarkText);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public byte[] renderPdf(JsonNode screenSpec, boolean watermarkEnabled, String watermarkText) throws IOException {
        RenderContext context = resolveContext(screenSpec);
        BufferedImage image = renderToImage(context, watermarkEnabled, watermarkText);
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(new PDRectangle(context.width, context.height));
            document.addPage(page);
            PDImageXObject pageImage = LosslessFactory.createFromImage(document, image);
            try (var stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(document, page)) {
                stream.drawImage(pageImage, 0, 0, context.width, context.height);
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private BufferedImage renderToImage(RenderContext context, boolean watermarkEnabled, String watermarkText) {
        BufferedImage image = new BufferedImage(context.width, context.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(context.backgroundColor);
            graphics.fillRect(0, 0, context.width, context.height);

            paintHeader(context, graphics);
            paintComponents(context, graphics);
            if (watermarkEnabled && watermarkText != null && !watermarkText.isBlank()) {
                paintWatermark(context, graphics, watermarkText.trim());
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private void paintHeader(RenderContext context, Graphics2D graphics) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(0.9f));
        graphics.setColor(context.darkTheme ? new Color(15, 23, 42, 210) : new Color(255, 255, 255, 210));
        graphics.fill(new RoundRectangle2D.Double(24, 20, Math.max(320, context.width - 48), 64, 16, 16));
        graphics.setComposite(AlphaComposite.SrcOver);
        graphics.setColor(context.darkTheme ? new Color(226, 232, 240) : new Color(31, 41, 55));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        drawTruncatedText(graphics, context.name, 44, 62, context.width - 120);
    }

    private void paintComponents(RenderContext context, Graphics2D graphics) {
        for (JsonNode component : context.components) {
            if (component == null || !component.isObject()) {
                continue;
            }
            int x = clamp(component.path("x").asInt(0), 0, context.width);
            int y = clamp(component.path("y").asInt(0), 0, context.height);
            int width = clamp(component.path("width").asInt(200), 120, context.width);
            int height = clamp(component.path("height").asInt(120), 60, context.height);
            if (x >= context.width || y >= context.height) {
                continue;
            }
            int drawWidth = Math.min(width, context.width - x - 1);
            int drawHeight = Math.min(height, context.height - y - 1);
            if (drawWidth <= 8 || drawHeight <= 8) {
                continue;
            }

            String type = component.path("type").asText("component");
            Color fill = resolveComponentColor(type, context.darkTheme);
            Color border = context.darkTheme ? new Color(148, 163, 184, 180) : new Color(100, 116, 139, 180);
            Color titleColor = context.darkTheme ? new Color(226, 232, 240) : new Color(31, 41, 55);

            graphics.setComposite(AlphaComposite.SrcOver.derive(0.95f));
            graphics.setColor(fill);
            graphics.fill(new RoundRectangle2D.Double(x, y, drawWidth, drawHeight, 12, 12));
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(border);
            graphics.draw(new RoundRectangle2D.Double(x, y, drawWidth, drawHeight, 12, 12));

            graphics.setColor(titleColor);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
            String componentName = component.path("name").asText(type);
            drawTruncatedText(graphics, componentName, x + 12, y + 22, drawWidth - 24);

            graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            graphics.setColor(context.darkTheme ? new Color(191, 219, 254) : new Color(71, 85, 105));
            drawTruncatedText(graphics, type, x + 12, y + 40, drawWidth - 24);
        }
    }

    private void paintWatermark(RenderContext context, Graphics2D graphics, String watermarkText) {
        graphics.setComposite(AlphaComposite.SrcOver.derive(context.darkTheme ? 0.14f : 0.1f));
        graphics.setColor(context.darkTheme ? new Color(248, 250, 252) : new Color(17, 24, 39));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 5; col++) {
                int x = 60 + col * Math.max(220, context.width / 5);
                int y = 140 + row * Math.max(140, context.height / 6);
                graphics.rotate(Math.toRadians(-16), x, y);
                graphics.drawString(watermarkText, x, y);
                graphics.rotate(Math.toRadians(16), x, y);
            }
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    private RenderContext resolveContext(JsonNode screenSpec) {
        JsonNode source = screenSpec != null && screenSpec.isObject() ? screenSpec : null;
        int width = clamp(source == null ? 1920 : source.path("width").asInt(1920), 640, 7680);
        int height = clamp(source == null ? 1080 : source.path("height").asInt(1080), 360, 4320);
        String name = source == null ? "DTS 大屏导出" : source.path("name").asText("DTS 大屏导出");
        String theme = source == null ? null : source.path("theme").asText(null);
        String background = source == null ? "#0d1b2a" : source.path("backgroundColor").asText("#0d1b2a");
        Color backgroundColor = parseColor(background, new Color(13, 27, 42));
        boolean darkTheme = isDarkTheme(theme, backgroundColor);
        JsonNode components = source == null ? null : source.path("components");
        return new RenderContext(width, height, name, backgroundColor, darkTheme, components);
    }

    private boolean isDarkTheme(String theme, Color background) {
        if (theme != null) {
            String text = theme.trim().toLowerCase(Locale.ROOT);
            if ("glacier".equals(text)) {
                return false;
            }
            if (!text.isEmpty()) {
                return true;
            }
        }
        int brightness = (background.getRed() + background.getGreen() + background.getBlue()) / 3;
        return brightness < 150;
    }

    private Color resolveComponentColor(String type, boolean darkTheme) {
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (key.contains("number")) {
            return darkTheme ? new Color(30, 58, 138, 220) : new Color(219, 234, 254, 230);
        }
        if (key.contains("table")) {
            return darkTheme ? new Color(30, 41, 59, 220) : new Color(241, 245, 249, 240);
        }
        if (key.contains("chart") || key.contains("map")) {
            return darkTheme ? new Color(15, 118, 110, 205) : new Color(204, 251, 241, 235);
        }
        if (key.contains("filter")) {
            return darkTheme ? new Color(76, 29, 149, 205) : new Color(237, 233, 254, 235);
        }
        return darkTheme ? new Color(51, 65, 85, 210) : new Color(226, 232, 240, 240);
    }

    private Color parseColor(String text, Color fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String value = text.trim();
        if (value.startsWith("#")) {
            try {
                if (value.length() == 7) {
                    return new Color(Integer.parseInt(value.substring(1), 16));
                }
                if (value.length() == 9) {
                    int rgb = Integer.parseInt(value.substring(1), 16);
                    int alpha = rgb & 0xff;
                    return new Color((rgb >> 24) & 0xff, (rgb >> 16) & 0xff, (rgb >> 8) & 0xff, alpha);
                }
            } catch (NumberFormatException ignore) {
                return fallback;
            }
        }
        return fallback;
    }

    private void drawTruncatedText(Graphics2D graphics, String text, int x, int y, int maxWidth) {
        String value = text == null ? "" : text;
        if (value.isBlank()) {
            return;
        }
        FontMetrics metrics = graphics.getFontMetrics();
        if (metrics.stringWidth(value) <= maxWidth) {
            graphics.drawString(value, x, y);
            return;
        }
        String ellipsis = "...";
        int targetWidth = Math.max(12, maxWidth - metrics.stringWidth(ellipsis));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            String next = out.toString() + value.charAt(i);
            if (metrics.stringWidth(next) > targetWidth) {
                break;
            }
            out.append(value.charAt(i));
        }
        graphics.drawString(out + ellipsis, x, y);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record RenderContext(
            int width,
            int height,
            String name,
            Color backgroundColor,
            boolean darkTheme,
            JsonNode components) {
    }
}
