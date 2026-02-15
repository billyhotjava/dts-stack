package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class ScreenAiGenerationService {

    private final ObjectMapper objectMapper;

    public ScreenAiGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(String prompt, Integer width, Integer height) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        int canvasWidth = width == null || width <= 0 ? 1920 : width;
        int canvasHeight = height == null || height <= 0 ? 1080 : height;

        String keyword = normalizedPrompt.toLowerCase(Locale.ROOT);
        String theme = resolveTheme(keyword);
        String backgroundColor = resolveBackground(theme);

        ObjectNode screenSpec = objectMapper.createObjectNode();
        screenSpec.put("name", resolveName(normalizedPrompt));
        screenSpec.put("description", normalizedPrompt.isBlank() ? "AI 自动生成大屏草稿" : normalizedPrompt);
        screenSpec.put("width", canvasWidth);
        screenSpec.put("height", canvasHeight);
        screenSpec.put("backgroundColor", backgroundColor);
        screenSpec.put("theme", theme);
        screenSpec.set("components", buildComponents(keyword, canvasWidth, canvasHeight, theme));
        screenSpec.set("globalVariables", buildGlobalVariables(keyword));

        ObjectNode quality = objectMapper.createObjectNode();
        quality.put("score", estimateScore(normalizedPrompt, keyword));
        ArrayNode warnings = objectMapper.createArrayNode();
        warnings.add("当前为启发式生成，图表数据为示例值，请绑定真实数据源。");
        if (!containsAny(keyword, "销售", "生产", "设备", "能耗", "质量", "库存", "告警", "财务", "运营")) {
            warnings.add("提示词业务领域不够明确，建议补充行业、指标和时间范围。");
        }
        quality.set("warnings", warnings);

        ArrayNode suggestions = objectMapper.createArrayNode();
        suggestions.add("在编辑器中为核心组件绑定 Card/数据库数据源。" );
        suggestions.add("通过全局变量配置时间范围、区域、组织联动。" );
        quality.set("suggestions", suggestions);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("engine", "heuristic-v1");
        result.put("prompt", normalizedPrompt);
        result.set("screenSpec", screenSpec);
        result.set("quality", quality);
        return result;
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode) {
        String normalized = instruction == null ? "" : instruction.trim();
        String keyword = normalized.toLowerCase(Locale.ROOT);

        ObjectNode screenSpec = screenSpecNode != null && screenSpecNode.isObject()
                ? ((ObjectNode) screenSpecNode).deepCopy()
                : objectMapper.createObjectNode();
        if (!screenSpec.has("width")) {
            screenSpec.put("width", 1920);
        }
        if (!screenSpec.has("height")) {
            screenSpec.put("height", 1080);
        }
        if (!screenSpec.has("components") || !screenSpec.path("components").isArray()) {
            screenSpec.set("components", objectMapper.createArrayNode());
        }
        if (!screenSpec.has("globalVariables") || !screenSpec.path("globalVariables").isArray()) {
            screenSpec.set("globalVariables", objectMapper.createArrayNode());
        }

        ArrayNode actions = objectMapper.createArrayNode();

        if (containsAny(keyword, "深色", "暗色", "夜间", "legacy-dark")) {
            screenSpec.put("theme", "legacy-dark");
            screenSpec.put("backgroundColor", "#0d1b2a");
            actions.add("已切换到深色主题");
        } else if (containsAny(keyword, "浅色", "亮色", "商务", "glacier")) {
            screenSpec.put("theme", "glacier");
            screenSpec.put("backgroundColor", "#f6f7f9");
            actions.add("已切换到浅色商务主题");
        } else if (containsAny(keyword, "钛", "工业", "titanium")) {
            screenSpec.put("theme", "titanium");
            screenSpec.put("backgroundColor", "#1a1d23");
            actions.add("已切换到工业钛合金主题");
        }

        if (containsAny(keyword, "重排", "紧凑", "重新布局", "三列", "两列")) {
            relayout(screenSpec, keyword);
            actions.add("已重排布局");
        }

        if (containsAny(keyword, "柱状图", "bar chart")) {
            if (replaceFirstChartType(screenSpec, "bar-chart")) {
                actions.add("已将首个图表切换为柱状图");
            }
        } else if (containsAny(keyword, "折线图", "line chart")) {
            if (replaceFirstChartType(screenSpec, "line-chart")) {
                actions.add("已将首个图表切换为折线图");
            }
        } else if (containsAny(keyword, "饼图", "pie chart")) {
            if (replaceFirstChartType(screenSpec, "pie-chart")) {
                actions.add("已将首个图表切换为饼图");
            }
        } else if (containsAny(keyword, "地图", "map")) {
            if (replaceFirstChartType(screenSpec, "map-chart")) {
                actions.add("已将首个图表切换为地图组件");
            }
        }

        if (containsAny(keyword, "筛选", "过滤", "filter")) {
            addFilterComponents(screenSpec);
            actions.add("已补充筛选器组件");
        }

        ObjectNode quality = objectMapper.createObjectNode();
        quality.put("score", Math.min(97, 78 + actions.size() * 4));
        ArrayNode warnings = objectMapper.createArrayNode();
        if (actions.isEmpty()) {
            warnings.add("未识别到可执行的优化指令，请尝试“重排布局/改成柱状图/改成浅色主题”。");
        } else {
            warnings.add("优化结果为启发式调整，请进入设计器确认细节。");
        }
        quality.set("warnings", warnings);
        ArrayNode suggestions = objectMapper.createArrayNode();
        suggestions.add("可继续输入：改成三列布局 / 换成饼图 / 加地区筛选。");
        quality.set("suggestions", suggestions);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("engine", "heuristic-v1-revise");
        result.put("prompt", normalized);
        result.set("screenSpec", screenSpec);
        result.set("quality", quality);
        result.set("actions", actions);
        return result;
    }

    private ArrayNode buildComponents(String keyword, int width, int height, String theme) {
        ArrayNode components = objectMapper.createArrayNode();
        String titleColor = "glacier".equals(theme) ? "#1f2937" : "#e2f4ff";
        String subColor = "glacier".equals(theme) ? "#4b5563" : "#9cc9ff";

        int top = 20;
        int cardTop = 100;
        int chartTop = 230;
        int bottomTop = 670;

        components.add(component(
                "ai-title",
                "title",
                "大屏标题",
                width / 2 - 320,
                top,
                640,
                56,
                100,
                objectMapper.createObjectNode()
                        .put("text", resolveTitle(keyword))
                        .put("fontSize", 40)
                        .put("fontWeight", "bold")
                        .put("color", titleColor)
                        .put("textAlign", "center")));

        components.add(component(
                "ai-datetime",
                "datetime",
                "时间",
                width - 320,
                top + 8,
                280,
                36,
                99,
                objectMapper.createObjectNode()
                        .put("format", "YYYY-MM-DD HH:mm:ss")
                        .put("fontSize", 18)
                        .put("color", subColor)));

        int cardWidth = 280;
        int gap = 30;
        int firstCardX = (width - cardWidth * 4 - gap * 3) / 2;
        components.add(numberCard("ai-card-1", "核心指标A", firstCardX, cardTop, cardWidth, 100, 80, 128560));
        components.add(numberCard("ai-card-2", "核心指标B", firstCardX + (cardWidth + gap), cardTop, cardWidth, 100, 80, 32490));
        components.add(numberCard("ai-card-3", "核心指标C", firstCardX + (cardWidth + gap) * 2, cardTop, cardWidth, 100, 80, 98.7));
        components.add(numberCard("ai-card-4", "核心指标D", firstCardX + (cardWidth + gap) * 3, cardTop, cardWidth, 100, 80, 8743));

        components.add(lineChart("ai-line", chartTitle(keyword, "趋势分析"), 40, chartTop, 920, 410, 60));
        components.add(barChart("ai-bar", chartTitle(keyword, "结构对比"), 990, chartTop, 470, 410, 60));
        components.add(pieChart("ai-pie", chartTitle(keyword, "占比分析"), 1480, chartTop, 400, 410, 60));
        components.add(tableComp("ai-table", chartTitle(keyword, "明细列表"), 40, bottomTop, width - 80, height - bottomTop - 40, 40));

        return components;
    }

    private ObjectNode numberCard(String id, String title, int x, int y, int width, int height, int zIndex, double value) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("value", value);
        config.put("precision", value % 1 == 0 ? 0 : 1);
        config.put("prefix", "");
        config.put("suffix", "");
        config.put("titleColor", "#9cc9ff");
        config.put("valueColor", "#f8fbff");
        return component(id, "number-card", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode lineChart(String id, String title, int x, int y, int width, int height, int zIndex) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "top");
        config.put("lineSmooth", true);
        config.put("areaStyle", true);

        ArrayNode xAxis = objectMapper.createArrayNode();
        xAxis.add("周一").add("周二").add("周三").add("周四").add("周五").add("周六").add("周日");
        config.set("xAxisData", xAxis);

        ArrayNode series = objectMapper.createArrayNode();
        series.add(objectMapper.createObjectNode().put("name", "本期").set("data", toNumberArray(120, 132, 141, 154, 169, 180, 176)));
        series.add(objectMapper.createObjectNode().put("name", "上期").set("data", toNumberArray(102, 118, 129, 136, 148, 157, 161)));
        config.set("series", series);

        return component(id, "line-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode barChart(String id, String title, int x, int y, int width, int height, int zIndex) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "top");

        ArrayNode xAxis = objectMapper.createArrayNode();
        xAxis.add("A类").add("B类").add("C类").add("D类").add("E类");
        config.set("xAxisData", xAxis);

        ArrayNode series = objectMapper.createArrayNode();
        series.add(objectMapper.createObjectNode().put("name", "数量").set("data", toNumberArray(32, 48, 41, 36, 52)));
        series.add(objectMapper.createObjectNode().put("name", "目标").set("data", toNumberArray(35, 45, 44, 40, 50)));
        config.set("series", series);

        return component(id, "bar-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode pieChart(String id, String title, int x, int y, int width, int height, int zIndex) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "bottom");

        ArrayNode data = objectMapper.createArrayNode();
        data.add(objectMapper.createObjectNode().put("name", "渠道A").put("value", 335));
        data.add(objectMapper.createObjectNode().put("name", "渠道B").put("value", 287));
        data.add(objectMapper.createObjectNode().put("name", "渠道C").put("value", 192));
        data.add(objectMapper.createObjectNode().put("name", "渠道D").put("value", 140));
        config.set("data", data);

        return component(id, "pie-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode tableComp(String id, String title, int x, int y, int width, int height, int zIndex) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);

        ArrayNode header = objectMapper.createArrayNode();
        header.add("维度").add("本期").add("上期").add("变化");
        config.set("header", header);

        ArrayNode data = objectMapper.createArrayNode();
        data.add(toStringArray("华北", "1,260", "1,080", "+16.7%"));
        data.add(toStringArray("华东", "1,920", "1,780", "+7.9%"));
        data.add(toStringArray("华南", "1,430", "1,520", "-5.9%"));
        data.add(toStringArray("西南", "890", "760", "+17.1%"));
        config.set("data", data);

        config.put("fontSize", 13);
        config.put("headerBackground", "rgba(148,163,184,0.18)");
        config.put("bodyBackground", "transparent");
        config.put("borderColor", "rgba(148,163,184,0.24)");

        return component(id, "table", title, x, y, width, height, zIndex, config);
    }

    private ArrayNode buildGlobalVariables(String keyword) {
        ArrayNode variables = objectMapper.createArrayNode();
        if (containsAny(keyword, "时间", "日期", "趋势", "同比", "环比", "day", "week", "month")) {
            variables.add(objectMapper.createObjectNode()
                    .put("key", "date_range")
                    .put("label", "时间范围")
                    .put("type", "string")
                    .put("defaultValue", "最近7天")
                    .put("description", "用于驱动卡片参数中的时间范围"));
        }
        if (containsAny(keyword, "地区", "区域", "省份", "城市", "region", "area")) {
            variables.add(objectMapper.createObjectNode()
                    .put("key", "region")
                    .put("label", "区域")
                    .put("type", "string")
                    .put("defaultValue", "全部")
                    .put("description", "用于区域筛选联动"));
        }
        return variables;
    }

    private ObjectNode component(
            String id,
            String type,
            String name,
            int x,
            int y,
            int width,
            int height,
            int zIndex,
            ObjectNode config) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("type", type);
        node.put("name", name);
        node.put("x", x);
        node.put("y", y);
        node.put("width", width);
        node.put("height", height);
        node.put("zIndex", zIndex);
        node.put("locked", false);
        node.put("visible", true);
        node.set("config", config == null ? objectMapper.createObjectNode() : config);
        return node;
    }

    private ArrayNode toNumberArray(double... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (double v : values) {
            array.add(v);
        }
        return array;
    }

    private ArrayNode toStringArray(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        for (String v : values) {
            array.add(v);
        }
        return array;
    }

    private void relayout(ObjectNode screenSpec, String keyword) {
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return;
        }
        ArrayNode components = (ArrayNode) node;
        int width = screenSpec.path("width").asInt(1920);
        int margin = 24;
        int columns = containsAny(keyword, "两列", "2列") ? 2 : 3;
        int colWidth = Math.max(220, (width - margin * (columns + 1)) / columns);
        int x = margin;
        int y = 90;
        int rowHeight = 220;
        int idx = 0;
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode comp = (ObjectNode) item;
            String type = comp.path("type").asText("");
            if ("title".equals(type) || "datetime".equals(type)) {
                continue;
            }
            int col = idx % columns;
            int row = idx / columns;
            comp.put("x", margin + col * (colWidth + margin));
            comp.put("y", y + row * (rowHeight + margin));
            comp.put("width", colWidth);
            comp.put("height", rowHeight);
            idx++;
        }
    }

    private boolean replaceFirstChartType(ObjectNode screenSpec, String targetType) {
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return false;
        }
        ArrayNode components = (ArrayNode) node;
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode comp = (ObjectNode) item;
            String type = comp.path("type").asText("");
            if ("line-chart".equals(type) || "bar-chart".equals(type) || "pie-chart".equals(type) || "map-chart".equals(type)) {
                comp.put("type", targetType);
                if (!comp.path("name").asText("").contains("图")) {
                    comp.put("name", "AI图表");
                }
                return true;
            }
        }
        return false;
    }

    private void addFilterComponents(ObjectNode screenSpec) {
        JsonNode compsNode = screenSpec.path("components");
        if (!compsNode.isArray()) {
            return;
        }
        ArrayNode components = (ArrayNode) compsNode;
        boolean hasFilter = false;
        int maxZ = 0;
        for (JsonNode item : components) {
            if (item != null && item.isObject()) {
                String type = item.path("type").asText("");
                if (type.startsWith("filter-")) {
                    hasFilter = true;
                }
                maxZ = Math.max(maxZ, item.path("zIndex").asInt(0));
            }
        }
        if (hasFilter) {
            return;
        }
        components.add(component(
                "ai-filter-region",
                "filter-select",
                "区域筛选",
                40,
                72,
                260,
                54,
                maxZ + 2,
                objectMapper.createObjectNode()
                        .put("label", "区域")
                        .put("variableKey", "region")
                        .put("placeholder", "请选择区域")
                        .set("options", toStringArray("华北", "华东", "华南", "西南", "西北", "东北"))));
        components.add(component(
                "ai-filter-date",
                "filter-date-range",
                "日期筛选",
                320,
                68,
                320,
                64,
                maxZ + 2,
                objectMapper.createObjectNode()
                        .put("label", "日期区间")
                        .put("startKey", "startDate")
                        .put("endKey", "endDate")));

        JsonNode varsNode = screenSpec.path("globalVariables");
        if (varsNode.isArray()) {
            ArrayNode vars = (ArrayNode) varsNode;
            vars.add(objectMapper.createObjectNode()
                    .put("key", "startDate")
                    .put("label", "开始日期")
                    .put("type", "date")
                    .put("defaultValue", ""));
            vars.add(objectMapper.createObjectNode()
                    .put("key", "endDate")
                    .put("label", "结束日期")
                    .put("type", "date")
                    .put("defaultValue", ""));
        }
    }

    private String resolveTheme(String keyword) {
        if (containsAny(keyword, "白", "light", "商务", "简洁", "glacier")) {
            return "glacier";
        }
        if (containsAny(keyword, "工业", "制造", "金属", "钛", "titanium")) {
            return "titanium";
        }
        return "legacy-dark";
    }

    private String resolveBackground(String theme) {
        return switch (theme) {
            case "glacier" -> "#f6f7f9";
            case "titanium" -> "#1a1d23";
            default -> "#0d1b2a";
        };
    }

    private String resolveName(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return "AI生成大屏草稿";
        }
        String trimmed = prompt.trim();
        if (trimmed.length() <= 24) {
            return trimmed;
        }
        return trimmed.substring(0, 24) + "...";
    }

    private String resolveTitle(String keyword) {
        if (containsAny(keyword, "设备", "产线", "制造", "quality", "良率")) {
            return "智能制造运营中心";
        }
        if (containsAny(keyword, "销售", "订单", "客户", "收入", "gmv")) {
            return "经营增长分析中心";
        }
        if (containsAny(keyword, "能耗", "碳", "环保", "电", "水", "气")) {
            return "能源与碳排监测中心";
        }
        return "AI 智能分析大屏";
    }

    private String chartTitle(String keyword, String fallback) {
        if (containsAny(keyword, "告警", "异常", "风险")) {
            return "风险与告警" + fallback;
        }
        return fallback;
    }

    private int estimateScore(String prompt, String keyword) {
        int score = 72;
        int length = prompt == null ? 0 : prompt.trim().length();
        if (length >= 20) {
            score += 6;
        } else if (length >= 10) {
            score += 3;
        }
        if (containsAny(keyword, "指标", "趋势", "对比", "排名", "占比", "时间", "区域")) {
            score += 8;
        }
        if (containsAny(keyword, "销售", "制造", "能耗", "质量", "库存", "财务", "运营")) {
            score += 6;
        }
        return Math.min(95, score);
    }

    private boolean containsAny(String text, String... keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank() && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
