package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.awt.BasicStroke;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    static {
        System.setProperty("java.awt.headless", "true");
    }

    public byte[] renderPng(JsonNode screenSpec, boolean watermarkEnabled, String watermarkText, double pixelRatio) throws IOException {
        RenderContext context = resolveContext(screenSpec);
        BufferedImage image = renderToImage(context, watermarkEnabled, watermarkText, pixelRatio);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }

    public byte[] renderPdf(JsonNode screenSpec, boolean watermarkEnabled, String watermarkText, double pixelRatio) throws IOException {
        RenderContext context = resolveContext(screenSpec);
        BufferedImage image = renderToImage(context, watermarkEnabled, watermarkText, pixelRatio);
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

    private BufferedImage renderToImage(RenderContext context, boolean watermarkEnabled, String watermarkText, double pixelRatioRaw) {
        double pixelRatio = normalizePixelRatio(pixelRatioRaw);
        int renderWidth = clamp((int) Math.round(context.width * pixelRatio), 640, 16384);
        int renderHeight = clamp((int) Math.round(context.height * pixelRatio), 360, 16384);
        BufferedImage image = new BufferedImage(renderWidth, renderHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.scale(pixelRatio, pixelRatio);
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

    private double normalizePixelRatio(double raw) {
        if (Double.isNaN(raw) || Double.isInfinite(raw)) {
            return 1.0d;
        }
        if (raw < 1.0d) {
            return 1.0d;
        }
        return Math.min(raw, 3.0d);
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
        List<JsonNode> components = new ArrayList<>();
        if (context.components != null && context.components.isArray()) {
            for (JsonNode component : context.components) {
                if (component != null && component.isObject()) {
                    components.add(component);
                }
            }
        }
        components.sort(Comparator.comparingInt(item -> item.path("zIndex").asInt(0)));

        for (JsonNode component : components) {
            if (component == null || !component.isObject()) {
                continue;
            }
            if (component.path("visible").isBoolean() && !component.path("visible").asBoolean(true)) {
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
            JsonNode config = component.path("config");
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
            String componentName = resolveComponentTitle(component, config, type);
            drawTruncatedText(graphics, componentName, x + 12, y + 22, drawWidth - 24);
            paintComponentBody(
                    context,
                    graphics,
                    type,
                    config,
                    x + 10,
                    y + 30,
                    Math.max(40, drawWidth - 20),
                    Math.max(24, drawHeight - 40));
        }
    }

    private String resolveComponentTitle(JsonNode component, JsonNode config, String type) {
        String fromConfig = trimToNull(config.path("title").asText(null));
        if (fromConfig != null) {
            return fromConfig;
        }
        String fallback = trimToNull(component.path("name").asText(null));
        if (fallback != null) {
            return fallback;
        }
        return type == null ? "component" : type;
    }

    private void paintComponentBody(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        if (width <= 24 || height <= 18) {
            return;
        }
        String key = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if ("line-chart".equals(key) || "bar-chart".equals(key) || "scatter-chart".equals(key)) {
            paintCartesianChart(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("radar-chart".equals(key)) {
            paintRadarChart(context, graphics, config, x, y, width, height);
            return;
        }
        if ("gauge-chart".equals(key)) {
            paintGaugeChart(context, graphics, config, x, y, width, height);
            return;
        }
        if ("pie-chart".equals(key) || "funnel-chart".equals(key)) {
            paintPieChart(context, graphics, config, x, y, width, height);
            return;
        }
        if ("number-card".equals(key)) {
            paintNumberCard(context, graphics, config, x, y, width, height);
            return;
        }
        if ("table".equals(key) || "scroll-board".equals(key)) {
            paintTable(context, graphics, config, x, y, width, height);
            return;
        }
        if ("scroll-ranking".equals(key)) {
            paintRankingBoard(context, graphics, config, x, y, width, height);
            return;
        }
        if ("progress-bar".equals(key) || "percent-pond".equals(key) || "water-level".equals(key)) {
            paintProgressLike(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("digital-flop".equals(key)) {
            paintDigitalFlop(context, graphics, config, x, y, width, height);
            return;
        }
        if ("filter-input".equals(key) || "filter-select".equals(key) || "filter-date-range".equals(key)) {
            paintFilterControl(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("image".equals(key) || "video".equals(key) || "iframe".equals(key)) {
            paintMediaPlaceholder(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("border-box".equals(key) || "decoration".equals(key) || "container".equals(key) || "shape".equals(key)) {
            paintStructuralComponent(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("flyline-chart".equals(key)) {
            paintFlyline(context, graphics, config, x, y, width, height);
            return;
        }
        if ("title".equals(key) || "markdown-text".equals(key) || "marquee".equals(key) || "datetime".equals(key)
                || "countdown".equals(key) || "carousel".equals(key) || "tab-switcher".equals(key)) {
            paintTextualComponent(context, graphics, key, config, x, y, width, height);
            return;
        }
        if ("map-chart".equals(key)) {
            paintMapSummary(context, graphics, config, x, y, width, height);
            return;
        }
        paintComponentTypeHint(context, graphics, type, x, y, width, height);
    }

    private void paintCartesianChart(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        int left = x + 34;
        int top = y + 8;
        int right = x + width - 10;
        int bottom = y + height - 24;
        if (right - left < 40 || bottom - top < 30) {
            paintComponentTypeHint(context, graphics, type, x, y, width, height);
            return;
        }

        List<SeriesData> seriesList = resolveSeriesData(config);
        if (seriesList.isEmpty()) {
            paintComponentTypeHint(context, graphics, type, x, y, width, height);
            return;
        }
        int maxPoints = seriesList.stream().mapToInt(item -> item.values().size()).max().orElse(0);
        if (maxPoints <= 0) {
            paintComponentTypeHint(context, graphics, type, x, y, width, height);
            return;
        }

        List<String> categories = toStringList(config.path("xAxisData"), maxPoints);
        if (categories.isEmpty()) {
            categories = new ArrayList<>();
            for (int i = 0; i < maxPoints; i++) {
                categories.add(String.valueOf(i + 1));
            }
        }

        double maxValue = 0d;
        for (SeriesData series : seriesList) {
            for (Double value : series.values()) {
                maxValue = Math.max(maxValue, value == null ? 0d : value);
            }
        }
        if (maxValue <= 0d) {
            maxValue = 1d;
        }

        Color axisColor = context.darkTheme ? new Color(148, 163, 184, 220) : new Color(100, 116, 139, 220);
        Color labelColor = context.darkTheme ? new Color(191, 219, 254, 235) : new Color(71, 85, 105, 235);
        graphics.setColor(axisColor);
        graphics.setStroke(new BasicStroke(1.2f));
        graphics.drawLine(left, top, left, bottom);
        graphics.drawLine(left, bottom, right, bottom);

        int pointCount = Math.min(categories.size(), maxPoints);
        if (pointCount <= 0) {
            return;
        }
        int plotWidth = Math.max(1, right - left - 6);
        int plotHeight = Math.max(1, bottom - top - 4);
        double step = pointCount <= 1 ? plotWidth : (double) plotWidth / (pointCount - 1);

        if ("bar-chart".equals(type)) {
            int seriesCount = Math.max(1, seriesList.size());
            double groupWidth = pointCount <= 0 ? 0d : (double) plotWidth / pointCount;
            double barWidth = Math.max(3d, Math.min(22d, groupWidth / (seriesCount + 0.8d)));
            for (int i = 0; i < pointCount; i++) {
                double groupStart = left + i * groupWidth + Math.max(1d, (groupWidth - barWidth * seriesCount) / 2d);
                for (int s = 0; s < seriesList.size(); s++) {
                    List<Double> values = seriesList.get(s).values();
                    double value = i < values.size() ? Math.max(0d, values.get(i)) : 0d;
                    int barHeight = (int) Math.round((value / maxValue) * plotHeight);
                    int barX = (int) Math.round(groupStart + s * barWidth);
                    int barY = bottom - barHeight;
                    graphics.setColor(resolveSeriesColor(s, context.darkTheme));
                    graphics.fill(new RoundRectangle2D.Double(barX, barY, barWidth - 1d, barHeight, 4, 4));
                }
            }
        } else if ("scatter-chart".equals(type)) {
            for (int s = 0; s < seriesList.size(); s++) {
                graphics.setColor(resolveSeriesColor(s, context.darkTheme));
                List<Double> values = seriesList.get(s).values();
                for (int i = 0; i < Math.min(pointCount, values.size()); i++) {
                    double value = Math.max(0d, values.get(i));
                    int cx = (int) Math.round(left + i * step);
                    int cy = bottom - (int) Math.round((value / maxValue) * plotHeight);
                    graphics.fill(new Ellipse2D.Double(cx - 3, cy - 3, 6, 6));
                }
            }
        } else {
            for (int s = 0; s < seriesList.size(); s++) {
                graphics.setColor(resolveSeriesColor(s, context.darkTheme));
                graphics.setStroke(new BasicStroke(2.0f));
                List<Double> values = seriesList.get(s).values();
                Path2D path = new Path2D.Double();
                boolean started = false;
                for (int i = 0; i < Math.min(pointCount, values.size()); i++) {
                    double value = Math.max(0d, values.get(i));
                    double px = left + i * step;
                    double py = bottom - (value / maxValue) * plotHeight;
                    if (!started) {
                        path.moveTo(px, py);
                        started = true;
                    } else {
                        path.lineTo(px, py);
                    }
                    graphics.fill(new Ellipse2D.Double(px - 2.5d, py - 2.5d, 5d, 5d));
                }
                if (started) {
                    graphics.draw(path);
                }
            }
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.setColor(labelColor);
        int labelStride = Math.max(1, pointCount / 6);
        for (int i = 0; i < pointCount; i += labelStride) {
            String label = categories.get(i);
            int px = (int) Math.round(left + i * step);
            drawTruncatedText(graphics, label, px - 16, bottom + 14, 34);
        }
        if ((pointCount - 1) % labelStride != 0) {
            int last = pointCount - 1;
            String label = categories.get(last);
            int px = (int) Math.round(left + last * step);
            drawTruncatedText(graphics, label, px - 18, bottom + 14, 36);
        }
    }

    private void paintPieChart(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        List<NamedValue> entries = toNamedValues(config.path("data"), 8);
        if (entries.isEmpty()) {
            paintComponentTypeHint(context, graphics, "pie-chart", x, y, width, height);
            return;
        }
        double total = entries.stream().mapToDouble(NamedValue::value).sum();
        if (total <= 0d) {
            total = 1d;
        }

        int diameter = Math.min(Math.max(30, height - 16), Math.max(30, width / 2));
        int pieX = x + 6;
        int pieY = y + Math.max(2, (height - diameter) / 2);
        double start = 90d;
        for (int i = 0; i < entries.size(); i++) {
            NamedValue item = entries.get(i);
            double extent = -360d * (item.value() / total);
            graphics.setColor(resolveSeriesColor(i, context.darkTheme));
            graphics.fill(new Arc2D.Double(pieX, pieY, diameter, diameter, start, extent, Arc2D.PIE));
            start += extent;
        }

        int donut = Math.max(20, (int) Math.round(diameter * 0.46d));
        int donutOffset = (diameter - donut) / 2;
        graphics.setColor(context.darkTheme ? new Color(17, 24, 39, 230) : new Color(255, 255, 255, 220));
        graphics.fill(new Ellipse2D.Double(pieX + donutOffset, pieY + donutOffset, donut, donut));

        int legendX = pieX + diameter + 12;
        int legendY = y + 12;
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        graphics.setColor(context.darkTheme ? new Color(226, 232, 240) : new Color(51, 65, 85));
        for (int i = 0; i < entries.size(); i++) {
            NamedValue item = entries.get(i);
            int rowY = legendY + i * 18;
            if (rowY > y + height - 6) {
                break;
            }
            graphics.setColor(resolveSeriesColor(i, context.darkTheme));
            graphics.fill(new RoundRectangle2D.Double(legendX, rowY - 8, 8, 8, 2, 2));
            graphics.setColor(context.darkTheme ? new Color(226, 232, 240) : new Color(51, 65, 85));
            String text = item.name() + " " + Math.round(item.value());
            drawTruncatedText(graphics, text, legendX + 12, rowY, Math.max(16, x + width - legendX - 12));
        }
    }

    private void paintNumberCard(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        String title = trimToNull(config.path("title").asText(null));
        String prefix = config.path("prefix").asText("");
        String suffix = config.path("suffix").asText("");
        int precision = clamp(config.path("precision").asInt(0), 0, 4);
        String valueText;
        if (config.path("value").isNumber()) {
            valueText = formatNumber(config.path("value").asDouble(0d), precision);
        } else {
            valueText = config.path("value").asText("--");
        }

        Color textColor = context.darkTheme ? new Color(248, 250, 252) : new Color(30, 41, 59);
        graphics.setColor(textColor);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if (title != null) {
            drawCenteredText(graphics, title, x, y + 18, width);
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(16, Math.min(32, height / 3))));
        drawCenteredText(graphics, prefix + valueText + suffix, x, y + Math.max(30, height / 2), width);
    }

    private void paintTable(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        List<String> headers = toStringList(config.path("header"), 12);
        List<List<String>> rows = toTableRows(config.path("data"), 40, 12);
        if (headers.isEmpty()) {
            int maxColumns = rows.stream().mapToInt(List::size).max().orElse(3);
            for (int i = 0; i < Math.max(1, maxColumns); i++) {
                headers.add("列" + (i + 1));
            }
        }
        int columnCount = Math.max(1, headers.size());
        int headerHeight = 22;
        int rowHeight = 20;
        int maxRows = Math.max(1, (height - headerHeight - 6) / rowHeight);
        int displayRows = Math.min(maxRows, rows.size());
        int colWidth = Math.max(24, width / columnCount);

        Color headerBg = context.darkTheme ? new Color(30, 41, 59, 220) : new Color(203, 213, 225, 220);
        Color gridColor = context.darkTheme ? new Color(148, 163, 184, 120) : new Color(100, 116, 139, 100);
        Color textColor = context.darkTheme ? new Color(226, 232, 240) : new Color(51, 65, 85);
        Color evenBg = context.darkTheme ? new Color(15, 23, 42, 150) : new Color(255, 255, 255, 150);
        Color oddBg = context.darkTheme ? new Color(30, 41, 59, 130) : new Color(241, 245, 249, 160);

        graphics.setColor(headerBg);
        graphics.fill(new RoundRectangle2D.Double(x, y, width, headerHeight, 6, 6));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        graphics.setColor(textColor);
        for (int c = 0; c < columnCount; c++) {
            int cellX = x + c * colWidth;
            drawTruncatedText(graphics, headers.get(c), cellX + 6, y + 15, colWidth - 10);
        }

        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int r = 0; r < displayRows; r++) {
            int rowY = y + headerHeight + r * rowHeight;
            graphics.setColor(r % 2 == 0 ? evenBg : oddBg);
            graphics.fill(new RoundRectangle2D.Double(x, rowY, width, rowHeight, 0, 0));
            List<String> row = rows.get(r);
            graphics.setColor(textColor);
            for (int c = 0; c < columnCount; c++) {
                int cellX = x + c * colWidth;
                String value = c < row.size() ? row.get(c) : "";
                drawTruncatedText(graphics, value, cellX + 6, rowY + 14, colWidth - 10);
            }
        }

        graphics.setColor(gridColor);
        for (int c = 1; c < columnCount; c++) {
            int lineX = x + c * colWidth;
            graphics.drawLine(lineX, y, lineX, y + headerHeight + displayRows * rowHeight);
        }
        for (int r = 0; r <= displayRows; r++) {
            int lineY = y + headerHeight + r * rowHeight;
            graphics.drawLine(x, lineY, x + width, lineY);
        }
    }

    private void paintTextualComponent(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        Color textColor = context.darkTheme ? new Color(241, 245, 249) : new Color(30, 41, 59);
        graphics.setColor(textColor);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        if ("title".equals(type)) {
            String text = trimToNull(config.path("text").asText(null));
            drawCenteredText(graphics, text == null ? "标题" : text, x, y + Math.max(20, height / 2), width);
            return;
        }
        if ("datetime".equals(type)) {
            drawCenteredText(graphics, DATETIME_FORMATTER.format(Instant.now()), x, y + Math.max(18, height / 2), width);
            return;
        }
        if ("countdown".equals(type)) {
            String target = trimToNull(config.path("targetTime").asText(null));
            String text = "倒计时 --";
            if (target != null) {
                try {
                    Duration d = Duration.between(Instant.now(), Instant.parse(target));
                    long seconds = Math.max(0L, d.getSeconds());
                    long days = seconds / 86_400;
                    long hours = (seconds % 86_400) / 3600;
                    long minutes = (seconds % 3600) / 60;
                    text = String.format(Locale.ROOT, "倒计时 %d天 %02d:%02d", days, hours, minutes);
                } catch (Exception ignore) {
                    text = "倒计时 --";
                }
            }
            drawCenteredText(graphics, text, x, y + Math.max(18, height / 2), width);
            return;
        }
        if ("tab-switcher".equals(type)) {
            List<String> options = toOptionLabels(config.path("options"), 8);
            if (options.isEmpty()) {
                options = List.of("总览", "产线", "设备");
            }
            int count = Math.max(1, options.size());
            int gap = 6;
            int tabWidth = Math.max(28, (width - gap * (count - 1)) / count);
            int tabHeight = Math.min(24, Math.max(18, height - 6));
            for (int i = 0; i < options.size(); i++) {
                int tx = x + i * (tabWidth + gap);
                int ty = y + 3;
                graphics.setColor(i == 0
                        ? (context.darkTheme ? new Color(56, 189, 248, 220) : new Color(14, 165, 233, 220))
                        : (context.darkTheme ? new Color(51, 65, 85, 190) : new Color(226, 232, 240, 220)));
                graphics.fill(new RoundRectangle2D.Double(tx, ty, tabWidth, tabHeight, 10, 10));
                graphics.setColor(i == 0 ? new Color(15, 23, 42) : textColor);
                graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                drawCenteredText(graphics, options.get(i), tx, ty + 15, tabWidth);
            }
            return;
        }

        String text;
        if ("markdown-text".equals(type)) {
            text = trimToNull(config.path("markdown").asText(null));
        } else if ("marquee".equals(type)) {
            text = trimToNull(config.path("text").asText(null));
        } else if ("carousel".equals(type)) {
            List<String> items = toStringList(config.path("items"), 10);
            text = items.isEmpty() ? null : items.get(0);
        } else {
            text = null;
        }
        if (text == null) {
            text = type;
        }
        paintMultilineText(graphics, text, x + 4, y + 14, width - 8, Math.max(1, (height - 6) / 16));
    }

    private void paintMapSummary(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        List<NamedValue> entries = toNamedValues(config.path("regions"), 6);
        if (entries.isEmpty()) {
        paintComponentTypeHint(context, graphics, "map-chart", x, y, width, height);
            return;
        }
        entries.sort(Comparator.comparingDouble(NamedValue::value).reversed());
        double maxValue = Math.max(1d, entries.stream().mapToDouble(NamedValue::value).max().orElse(1d));
        int barMaxWidth = Math.max(24, width - 88);
        int rowHeight = Math.max(16, Math.min(24, height / Math.max(1, entries.size())));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (int i = 0; i < entries.size(); i++) {
            NamedValue row = entries.get(i);
            int rowY = y + 8 + i * rowHeight;
            double ratio = Math.max(0d, Math.min(1d, row.value() / maxValue));
            int barWidth = (int) Math.round(barMaxWidth * ratio);
            graphics.setColor(resolveSeriesColor(i, context.darkTheme));
            graphics.fill(new RoundRectangle2D.Double(x + 64, rowY - 8, barWidth, 12, 6, 6));
            graphics.setColor(context.darkTheme ? new Color(226, 232, 240) : new Color(51, 65, 85));
            drawTruncatedText(graphics, row.name(), x + 4, rowY + 2, 56);
            drawTruncatedText(graphics, String.valueOf(Math.round(row.value())), x + width - 24, rowY + 2, 20);
        }
    }

    private void paintRadarChart(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        List<NamedValue> values = toNamedValues(config.path("data"), 8);
        if (values.size() < 3) {
            paintComponentTypeHint(context, graphics, "radar-chart", x, y, width, height);
            return;
        }
        int cx = x + width / 2;
        int cy = y + height / 2;
        int radius = Math.max(24, Math.min(width, height) / 2 - 16);
        if (radius < 20) {
            paintComponentTypeHint(context, graphics, "radar-chart", x, y, width, height);
            return;
        }

        double max = Math.max(1d, values.stream().mapToDouble(NamedValue::value).max().orElse(1d));
        Color gridColor = context.darkTheme ? new Color(148, 163, 184, 140) : new Color(100, 116, 139, 120);
        for (int layer = 1; layer <= 4; layer++) {
            double scale = layer / 4d;
            Path2D grid = new Path2D.Double();
            for (int i = 0; i < values.size(); i++) {
                double angle = -Math.PI / 2 + (Math.PI * 2 * i / values.size());
                double px = cx + Math.cos(angle) * radius * scale;
                double py = cy + Math.sin(angle) * radius * scale;
                if (i == 0) {
                    grid.moveTo(px, py);
                } else {
                    grid.lineTo(px, py);
                }
            }
            grid.closePath();
            graphics.setColor(gridColor);
            graphics.draw(grid);
        }
        Path2D area = new Path2D.Double();
        for (int i = 0; i < values.size(); i++) {
            NamedValue item = values.get(i);
            double ratio = Math.max(0d, Math.min(1d, item.value() / max));
            double angle = -Math.PI / 2 + (Math.PI * 2 * i / values.size());
            double px = cx + Math.cos(angle) * radius * ratio;
            double py = cy + Math.sin(angle) * radius * ratio;
            if (i == 0) {
                area.moveTo(px, py);
            } else {
                area.lineTo(px, py);
            }
            graphics.setColor(resolveSeriesColor(i, context.darkTheme));
            graphics.fill(new Ellipse2D.Double(px - 2, py - 2, 4, 4));
        }
        area.closePath();
        Color fill = resolveSeriesColor(0, context.darkTheme);
        graphics.setColor(new Color(fill.getRed(), fill.getGreen(), fill.getBlue(), 90));
        graphics.fill(area);
        graphics.setColor(fill);
        graphics.setStroke(new BasicStroke(1.8f));
        graphics.draw(area);
    }

    private void paintGaugeChart(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        double value = resolvePrimaryValue(config, 62d);
        double clampedValue = Math.max(0d, Math.min(100d, value));
        int diameter = Math.max(48, Math.min(width - 12, height * 2 - 20));
        int gx = x + (width - diameter) / 2;
        int gy = y + Math.max(4, height - diameter - 6);
        int stroke = Math.max(6, Math.min(14, diameter / 10));

        graphics.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(context.darkTheme ? new Color(51, 65, 85, 220) : new Color(203, 213, 225, 220));
        graphics.draw(new Arc2D.Double(gx, gy, diameter, diameter, 180, 180, Arc2D.OPEN));
        Color active = clampedValue >= 75d
                ? resolveSeriesColor(1, context.darkTheme)
                : (clampedValue >= 40d ? resolveSeriesColor(2, context.darkTheme) : resolveSeriesColor(5, context.darkTheme));
        graphics.setColor(active);
        graphics.draw(new Arc2D.Double(gx, gy, diameter, diameter, 180, 180d * (clampedValue / 100d), Arc2D.OPEN));

        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(14, Math.min(26, diameter / 5))));
        graphics.setColor(context.darkTheme ? new Color(241, 245, 249) : new Color(30, 41, 59));
        drawCenteredText(graphics, String.format(Locale.ROOT, "%.0f%%", clampedValue), x, y + height / 2 + 8, width);
    }

    private void paintProgressLike(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        double value = resolvePrimaryValue(config, 58d);
        double ratio = Math.max(0d, Math.min(1d, value / 100d));
        if ("water-level".equals(type)) {
            int diameter = Math.max(40, Math.min(width, height) - 8);
            int cx = x + width / 2;
            int cy = y + height / 2;
            int r = diameter / 2;
            graphics.setColor(context.darkTheme ? new Color(30, 41, 59, 220) : new Color(226, 232, 240, 220));
            graphics.fill(new Ellipse2D.Double(cx - r, cy - r, diameter, diameter));
            graphics.setColor(resolveSeriesColor(0, context.darkTheme));
            int fillHeight = (int) Math.round(diameter * ratio);
            graphics.fill(new Ellipse2D.Double(cx - r + 2, cy + r - fillHeight, diameter - 4, fillHeight));
            graphics.setColor(context.darkTheme ? new Color(241, 245, 249) : new Color(30, 41, 59));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(13, diameter / 6)));
            drawCenteredText(graphics, String.format(Locale.ROOT, "%.0f%%", ratio * 100d), cx - r, cy + 6, diameter);
            return;
        }

        int barY = y + Math.max(6, height / 2 - 10);
        int barHeight = Math.max(14, Math.min(22, height - 16));
        int barWidth = Math.max(36, width - 16);
        graphics.setColor(context.darkTheme ? new Color(51, 65, 85, 220) : new Color(226, 232, 240, 220));
        graphics.fill(new RoundRectangle2D.Double(x + 8, barY, barWidth, barHeight, barHeight, barHeight));
        graphics.setColor(resolveSeriesColor(0, context.darkTheme));
        graphics.fill(new RoundRectangle2D.Double(x + 8, barY, Math.max(4, (int) Math.round(barWidth * ratio)), barHeight, barHeight, barHeight));
        graphics.setColor(context.darkTheme ? new Color(241, 245, 249) : new Color(30, 41, 59));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        drawCenteredText(graphics, String.format(Locale.ROOT, "%.0f%%", ratio * 100d), x, barY + barHeight - 4, width);
    }

    private void paintDigitalFlop(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        String valueText;
        if (config.path("value").isNumber()) {
            valueText = formatNumber(config.path("value").asDouble(0d), clamp(config.path("precision").asInt(0), 0, 2));
        } else {
            valueText = trimToNull(config.path("value").asText(null));
            if (valueText == null) {
                valueText = "12,560";
            }
        }
        graphics.setColor(context.darkTheme ? new Color(15, 23, 42, 225) : new Color(241, 245, 249, 230));
        graphics.fill(new RoundRectangle2D.Double(x + 4, y + 4, width - 8, height - 8, 10, 10));
        graphics.setColor(context.darkTheme ? new Color(34, 197, 94) : new Color(5, 150, 105));
        graphics.setFont(new Font(Font.MONOSPACED, Font.BOLD, Math.max(16, Math.min(34, height - 14))));
        drawCenteredText(graphics, valueText, x, y + Math.max(28, height / 2 + 8), width);
    }

    private void paintRankingBoard(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        List<NamedValue> items = toNamedValues(config.path("data"), 8);
        if (items.isEmpty()) {
            items = List.of(
                    new NamedValue("产线A", 98),
                    new NamedValue("产线B", 92),
                    new NamedValue("产线C", 86),
                    new NamedValue("产线D", 81));
        }
        double max = Math.max(1d, items.stream().mapToDouble(NamedValue::value).max().orElse(1d));
        int rowHeight = Math.max(16, Math.min(24, height / Math.max(1, items.size())));
        int startY = y + 8;
        for (int i = 0; i < items.size(); i++) {
            NamedValue row = items.get(i);
            int rowY = startY + i * rowHeight;
            double ratio = Math.max(0d, Math.min(1d, row.value() / max));
            int barWidth = (int) Math.round((width - 96) * ratio);
            graphics.setColor(resolveSeriesColor(i, context.darkTheme));
            graphics.fill(new RoundRectangle2D.Double(x + 62, rowY - 8, barWidth, 10, 6, 6));
            graphics.setColor(context.darkTheme ? new Color(241, 245, 249) : new Color(30, 41, 59));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            drawTruncatedText(graphics, String.valueOf(i + 1), x + 4, rowY + 2, 16);
            drawTruncatedText(graphics, row.name(), x + 18, rowY + 2, 40);
            drawTruncatedText(graphics, String.format(Locale.ROOT, "%.0f", row.value()), x + width - 28, rowY + 2, 24);
        }
    }

    private void paintFilterControl(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        String label = trimToNull(config.path("label").asText(null));
        if (label == null) {
            label = switch (type) {
                case "filter-select" -> "选择器";
                case "filter-date-range" -> "日期区间";
                default -> "输入筛选";
            };
        }
        graphics.setColor(context.darkTheme ? new Color(15, 23, 42, 230) : new Color(255, 255, 255, 235));
        graphics.fill(new RoundRectangle2D.Double(x + 4, y + 8, width - 8, Math.max(24, height - 16), 8, 8));
        graphics.setColor(context.darkTheme ? new Color(148, 163, 184, 220) : new Color(100, 116, 139, 220));
        graphics.draw(new RoundRectangle2D.Double(x + 4, y + 8, width - 8, Math.max(24, height - 16), 8, 8));
        graphics.setColor(context.darkTheme ? new Color(226, 232, 240) : new Color(30, 41, 59));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        drawTruncatedText(graphics, label, x + 12, y + Math.max(26, height / 2 + 6), width - 36);
        if (!"filter-input".equals(type)) {
            graphics.drawLine(x + width - 20, y + height / 2 - 2, x + width - 14, y + height / 2 - 2);
            graphics.drawLine(x + width - 20, y + height / 2 - 2, x + width - 17, y + height / 2 + 2);
            graphics.drawLine(x + width - 14, y + height / 2 - 2, x + width - 17, y + height / 2 + 2);
        }
    }

    private void paintMediaPlaceholder(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        graphics.setColor(context.darkTheme ? new Color(2, 6, 23, 220) : new Color(226, 232, 240, 230));
        graphics.fill(new RoundRectangle2D.Double(x + 2, y + 2, width - 4, height - 4, 8, 8));
        graphics.setColor(context.darkTheme ? new Color(148, 163, 184, 220) : new Color(71, 85, 105, 220));
        graphics.draw(new RoundRectangle2D.Double(x + 2, y + 2, width - 4, height - 4, 8, 8));
        String source = trimToNull(config.path("src").asText(null));
        if (source == null) {
            source = trimToNull(config.path("url").asText(null));
        }
        graphics.setColor(context.darkTheme ? new Color(191, 219, 254) : new Color(30, 64, 175));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        drawCenteredText(graphics, type.toUpperCase(Locale.ROOT), x, y + Math.max(20, height / 2 - 6), width);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        drawTruncatedText(graphics, source == null ? "no-source" : source, x + 8, y + Math.max(36, height / 2 + 10), width - 16);
    }

    private void paintStructuralComponent(
            RenderContext context,
            Graphics2D graphics,
            String type,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        Color edge = context.darkTheme ? new Color(56, 189, 248, 180) : new Color(14, 116, 144, 180);
        if ("shape".equals(type)) {
            String shapeType = trimToNull(config.path("shapeType").asText(null));
            if ("ellipse".equalsIgnoreCase(shapeType)) {
                graphics.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 80));
                graphics.fill(new Ellipse2D.Double(x + 6, y + 6, width - 12, height - 12));
                graphics.setColor(edge);
                graphics.draw(new Ellipse2D.Double(x + 6, y + 6, width - 12, height - 12));
            } else {
                graphics.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 80));
                graphics.fill(new RoundRectangle2D.Double(x + 6, y + 6, width - 12, height - 12, 10, 10));
                graphics.setColor(edge);
                graphics.draw(new RoundRectangle2D.Double(x + 6, y + 6, width - 12, height - 12, 10, 10));
            }
            return;
        }
        if ("decoration".equals(type)) {
            graphics.setColor(edge);
            int midY = y + height / 2;
            graphics.setStroke(new BasicStroke(2f));
            graphics.drawLine(x + 8, midY, x + width - 8, midY);
            for (int i = 0; i < 5; i++) {
                int px = x + 10 + i * Math.max(16, (width - 20) / 5);
                graphics.fill(new Ellipse2D.Double(px, midY - 3, 6, 6));
            }
            return;
        }
        graphics.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 55));
        graphics.fill(new RoundRectangle2D.Double(x + 4, y + 4, width - 8, height - 8, 12, 12));
        graphics.setColor(edge);
        graphics.draw(new RoundRectangle2D.Double(x + 4, y + 4, width - 8, height - 8, 12, 12));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        graphics.setColor(context.darkTheme ? new Color(191, 219, 254) : new Color(30, 41, 59));
        drawCenteredText(graphics, type, x, y + height / 2 + 4, width);
    }

    private void paintFlyline(
            RenderContext context,
            Graphics2D graphics,
            JsonNode config,
            int x,
            int y,
            int width,
            int height) {
        graphics.setColor(context.darkTheme ? new Color(15, 23, 42, 220) : new Color(241, 245, 249, 230));
        graphics.fill(new RoundRectangle2D.Double(x + 3, y + 3, width - 6, height - 6, 8, 8));
        graphics.setColor(context.darkTheme ? new Color(56, 189, 248, 220) : new Color(14, 116, 144, 220));
        int count = 5;
        for (int i = 0; i < count; i++) {
            int sx = x + 16 + i * Math.max(20, (width - 32) / count);
            int ex = x + width - 16 - i * 8;
            int sy = y + height - 14;
            int ey = y + 14 + i * 8;
            Path2D path = new Path2D.Double();
            path.moveTo(sx, sy);
            path.quadTo((sx + ex) / 2d, y + height / 2d - 18 - i * 2, ex, ey);
            graphics.draw(path);
            graphics.fill(new Ellipse2D.Double(ex - 2, ey - 2, 4, 4));
        }
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        graphics.setColor(context.darkTheme ? new Color(191, 219, 254) : new Color(30, 41, 59));
        drawTruncatedText(graphics, trimToNull(config.path("map").asText(null)) == null ? "飞线网络" : config.path("map").asText(""), x + 8, y + 16, width - 16);
    }

    private void paintComponentTypeHint(
            RenderContext context,
            Graphics2D graphics,
            String type,
            int x,
            int y,
            int width,
            int height) {
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        graphics.setColor(context.darkTheme ? new Color(191, 219, 254) : new Color(71, 85, 105));
        drawTruncatedText(graphics, type == null ? "component" : type, x + 2, y + Math.min(height, 16), width - 4);
    }

    private List<SeriesData> resolveSeriesData(JsonNode config) {
        List<SeriesData> out = new ArrayList<>();
        JsonNode seriesNode = config.path("series");
        if (seriesNode.isArray()) {
            for (int i = 0; i < seriesNode.size(); i++) {
                JsonNode row = seriesNode.get(i);
                if (row == null || !row.isObject()) {
                    continue;
                }
                List<Double> values = toNumberList(row.path("data"), 64);
                if (values.isEmpty()) {
                    continue;
                }
                String name = trimToNull(row.path("name").asText(null));
                out.add(new SeriesData(name == null ? ("系列" + (i + 1)) : name, values));
            }
        }
        if (!out.isEmpty()) {
            return out;
        }
        List<Double> single = toNumberList(config.path("data"), 64);
        if (!single.isEmpty()) {
            out.add(new SeriesData("系列1", single));
        }
        return out;
    }

    private List<String> toStringList(JsonNode node, int limit) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            String value = trimToNull(item.asText(null));
            if (value == null) {
                continue;
            }
            out.add(value);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<Double> toNumberList(JsonNode node, int limit) {
        List<Double> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            if (item == null || item.isNull()) {
                continue;
            }
            double value;
            if (item.isNumber()) {
                value = item.asDouble(0d);
            } else {
                String text = trimToNull(item.asText(null));
                if (text == null) {
                    continue;
                }
                try {
                    value = Double.parseDouble(text);
                } catch (NumberFormatException ex) {
                    continue;
                }
            }
            out.add(value);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<NamedValue> toNamedValues(JsonNode node, int limit) {
        List<NamedValue> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (int i = 0; i < node.size(); i++) {
            JsonNode row = node.get(i);
            if (row == null || !row.isObject()) {
                continue;
            }
            String name = trimToNull(row.path("name").asText(null));
            if (name == null) {
                name = "项" + (i + 1);
            }
            double value = row.path("value").asDouble(0d);
            out.add(new NamedValue(name, value));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private List<List<String>> toTableRows(JsonNode node, int maxRows, int maxCols) {
        List<List<String>> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode row : node) {
            if (row == null || !row.isArray()) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : row) {
                String value = cell == null || cell.isNull() ? "" : cell.asText("");
                cells.add(value);
                if (cells.size() >= maxCols) {
                    break;
                }
            }
            out.add(cells);
            if (out.size() >= maxRows) {
                break;
            }
        }
        return out;
    }

    private List<String> toOptionLabels(JsonNode node, int limit) {
        List<String> out = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return out;
        }
        for (JsonNode row : node) {
            if (row == null || !row.isObject()) {
                continue;
            }
            String label = trimToNull(row.path("label").asText(null));
            if (label == null) {
                label = trimToNull(row.path("value").asText(null));
            }
            if (label == null) {
                continue;
            }
            out.add(label);
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private double resolvePrimaryValue(JsonNode config, double fallback) {
        if (config != null && config.path("value").isNumber()) {
            return config.path("value").asDouble(fallback);
        }
        if (config != null && config.path("percent").isNumber()) {
            return config.path("percent").asDouble(fallback);
        }
        List<Double> values = toNumberList(config == null ? null : config.path("data"), 1);
        if (!values.isEmpty()) {
            return values.get(0);
        }
        return fallback;
    }

    private Color resolveSeriesColor(int index, boolean darkTheme) {
        Color[] palette = darkTheme
                ? new Color[] {
                    new Color(56, 189, 248),
                    new Color(34, 197, 94),
                    new Color(251, 191, 36),
                    new Color(244, 114, 182),
                    new Color(129, 140, 248),
                    new Color(248, 113, 113),
                }
                : new Color[] {
                    new Color(14, 116, 144),
                    new Color(22, 163, 74),
                    new Color(217, 119, 6),
                    new Color(190, 24, 93),
                    new Color(67, 56, 202),
                    new Color(220, 38, 38),
                };
        return palette[Math.floorMod(index, palette.length)];
    }

    private String formatNumber(double value, int precision) {
        return switch (precision) {
            case 0 -> String.format(Locale.ROOT, "%,.0f", value);
            case 1 -> String.format(Locale.ROOT, "%,.1f", value);
            case 2 -> String.format(Locale.ROOT, "%,.2f", value);
            case 3 -> String.format(Locale.ROOT, "%,.3f", value);
            default -> String.format(Locale.ROOT, "%,.4f", value);
        };
    }

    private void drawCenteredText(Graphics2D graphics, String text, int x, int baselineY, int width) {
        if (text == null || text.isBlank()) {
            return;
        }
        FontMetrics metrics = graphics.getFontMetrics();
        int textWidth = Math.min(metrics.stringWidth(text), width);
        int startX = x + Math.max(0, (width - textWidth) / 2);
        drawTruncatedText(graphics, text, startX, baselineY, Math.max(12, width - 2));
    }

    private void paintMultilineText(
            Graphics2D graphics,
            String text,
            int x,
            int y,
            int width,
            int maxLines) {
        if (text == null || text.isBlank() || maxLines <= 0) {
            return;
        }
        String[] lines = text.replace("\r", "").split("\n");
        int row = 0;
        for (String line : lines) {
            if (row >= maxLines) {
                break;
            }
            String normalized = line.strip();
            if (normalized.startsWith("-")) {
                normalized = "• " + normalized.substring(1).strip();
            }
            if (normalized.startsWith("#")) {
                normalized = normalized.replaceFirst("^#+\\s*", "");
            }
            drawTruncatedText(graphics, normalized, x, y + row * 16, width);
            row++;
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private record SeriesData(String name, List<Double> values) {
    }

    private record NamedValue(String name, double value) {
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
