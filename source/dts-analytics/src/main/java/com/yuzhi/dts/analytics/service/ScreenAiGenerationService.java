package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ScreenAiGenerationService {

    private static final Pattern REFRESH_SECONDS_PATTERN = Pattern.compile("(\\d{1,4})\\s*(秒|s|sec|second)");
    private static final Pattern REFRESH_MINUTES_PATTERN = Pattern.compile("(\\d{1,3})\\s*(分|分钟|min|minute)");
    private static final Pattern TAB_WORD_PATTERN = Pattern.compile("\\btab\\b");
    private static final int MAX_CONTEXT_ITEMS = 16;
    private static final int MAX_CONTEXT_ITEM_LENGTH = 240;
    private static final int MAX_KEYWORD_LENGTH = 6000;

    private final ObjectMapper objectMapper;

    public ScreenAiGenerationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode generate(String prompt, Integer width, Integer height) {
        String normalizedPrompt = prompt == null ? "" : prompt.trim();
        int canvasWidth = width == null || width <= 0 ? 1920 : width;
        int canvasHeight = height == null || height <= 0 ? 1080 : height;

        String keyword = normalizedPrompt.toLowerCase(Locale.ROOT);
        IntentProfile intent = parseIntent(normalizedPrompt, keyword);
        String theme = resolveTheme(keyword);
        String backgroundColor = resolveBackground(theme);

        ObjectNode screenSpec = objectMapper.createObjectNode();
        screenSpec.put("name", resolveName(normalizedPrompt));
        screenSpec.put("description", normalizedPrompt.isBlank() ? "AI 自动生成大屏草稿" : normalizedPrompt);
        screenSpec.put("width", canvasWidth);
        screenSpec.put("height", canvasHeight);
        screenSpec.put("backgroundColor", backgroundColor);
        screenSpec.put("theme", theme);
        screenSpec.set("components", buildComponents(keyword, canvasWidth, canvasHeight, theme, intent));
        screenSpec.set("globalVariables", buildGlobalVariables(keyword, intent));
        screenSpec.put("refreshIntervalSeconds", recommendedRefreshSeconds(intent));
        if (hasTabSwitchIntent(keyword)) {
            addTabScenarioSwitcher(screenSpec, keyword);
        }

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
        suggestions.add("可继续输入优化指令：改成4K、放大字体、增加指标卡、删除表格、刷新30秒。");
        quality.set("suggestions", suggestions);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("engine", "heuristic-v1");
        result.put("prompt", normalizedPrompt);
        result.set("intent", toIntentNode(intent));
        result.set("semanticModelHints", buildSemanticModelHints(intent));
        result.set("queryRecommendations", buildQueryRecommendations(intent));
        result.set("sqlBlueprints", buildSqlBlueprints(intent));
        result.set("vizRecommendations", buildVizRecommendations(intent));
        result.set("screenSpec", screenSpec);
        result.set("quality", quality);
        return result;
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode) {
        return revise(instruction, screenSpecNode, List.of(), true);
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode, List<String> context) {
        return revise(instruction, screenSpecNode, context, true);
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode, List<String> context, boolean applyChanges) {
        String normalized = instruction == null ? "" : instruction.trim();
        String keyword = buildKeyword(normalized, context);
        IntentProfile intent = parseIntent(normalized, keyword);

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

        if (hasRemoveTabSwitchIntent(keyword)) {
            int changed = removeTabScenarioSwitcher(screenSpec);
            if (changed > 0) {
                actions.add("已移除 Tab 场景切换并清理 " + changed + " 处关联配置");
            }
        } else if (hasTabSwitchIntent(keyword)) {
            int affected = addTabScenarioSwitcher(screenSpec, keyword);
            if (affected >= 0) {
                actions.add("已生成 Tab 场景切换并配置 " + affected + " 个组件显隐规则");
            }
        }

        if (containsAny(keyword, "4k", "3840", "2160", "超清")) {
            resizeCanvas(screenSpec, 3840, 2160);
            actions.add("已切换为 4K 画布");
        } else if (containsAny(keyword, "1080p", "全高清", "fhd")) {
            resizeCanvas(screenSpec, 1920, 1080);
            actions.add("已切换为 1080P 画布");
        }

        if (containsAny(keyword, "放大字体", "字体大", "字号大", "font larger")) {
            int changed = scaleTypography(screenSpec, 1.18);
            if (changed > 0) {
                actions.add("已放大 " + changed + " 项字体");
            }
        } else if (containsAny(keyword, "缩小字体", "字体小", "字号小", "font smaller")) {
            int changed = scaleTypography(screenSpec, 0.88);
            if (changed > 0) {
                actions.add("已缩小 " + changed + " 项字体");
            }
        }

        if (containsAny(keyword, "增加指标卡", "新增指标卡", "增加kpi", "新增kpi")) {
            if (addNumberCard(screenSpec)) {
                actions.add("已新增指标卡");
            }
        }

        if (containsAny(keyword, "删除明细表", "删除表格", "移除表格", "去掉表格")) {
            int removed = removeComponentsByType(screenSpec, "table");
            if (removed > 0) {
                actions.add("已移除 " + removed + " 个表格组件");
            }
        }

        Integer refreshSeconds = parseRefreshIntervalSeconds(keyword);
        if (refreshSeconds != null) {
            screenSpec.put("refreshIntervalSeconds", refreshSeconds);
            actions.add("已设置刷新间隔为 " + refreshSeconds + " 秒");
        }

        ObjectNode quality = objectMapper.createObjectNode();
        quality.put("score", Math.min(97, 78 + actions.size() * 4));
        ArrayNode warnings = objectMapper.createArrayNode();
        if (actions.isEmpty()) {
            warnings.add("未识别到可执行的优化指令，请尝试“重排布局/改成柱状图/改成浅色主题/加tab切换场景”。");
        } else {
            warnings.add("优化结果为启发式调整，请进入设计器确认细节。");
            if (!applyChanges) {
                warnings.add("当前为建议模式，结果仅供预览，不会自动覆盖已发布内容。");
            }
        }
        quality.set("warnings", warnings);
        ArrayNode suggestions = objectMapper.createArrayNode();
        suggestions.add("可继续输入：改成三列布局 / 换成饼图 / 加地区筛选 / 加tab切换场景 / 放大字体 / 刷新30秒。");
        quality.set("suggestions", suggestions);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("engine", "heuristic-v1-revise");
        result.put("prompt", normalized);
        result.put("contextCount", context == null ? 0 : context.size());
        result.put("usedContextCount", countUsableContext(context));
        result.put("applyMode", applyChanges ? "apply" : "suggest");
        result.put("applied", applyChanges);
        result.set("intent", toIntentNode(intent));
        result.set("semanticModelHints", buildSemanticModelHints(intent));
        result.set("queryRecommendations", buildQueryRecommendations(intent));
        result.set("sqlBlueprints", buildSqlBlueprints(intent));
        result.set("vizRecommendations", buildVizRecommendations(intent));
        result.set("screenSpec", screenSpec);
        result.set("quality", quality);
        result.set("actions", actions);
        return result;
    }

    private String buildKeyword(String instruction, List<String> context) {
        List<String> lines = normalizeContextLines(context);
        String normalizedInstruction = instruction == null ? "" : instruction.trim();
        if (!normalizedInstruction.isEmpty()) {
            lines.add(clipText(normalizedInstruction, MAX_CONTEXT_ITEM_LENGTH * 2));
        }
        String merged = String.join(" ", lines);
        if (merged.length() > MAX_KEYWORD_LENGTH) {
            merged = merged.substring(merged.length() - MAX_KEYWORD_LENGTH);
        }
        return merged.toLowerCase(Locale.ROOT);
    }

    private int countUsableContext(List<String> context) {
        return normalizeContextLines(context).size();
    }

    private List<String> normalizeContextLines(List<String> context) {
        List<String> lines = new ArrayList<>();
        if (context == null || context.isEmpty()) {
            return lines;
        }
        Set<String> dedupe = new LinkedHashSet<>();
        for (String item : context) {
            String text = item == null ? "" : item.trim();
            if (text.isEmpty()) {
                continue;
            }
            String clipped = clipText(text, MAX_CONTEXT_ITEM_LENGTH);
            String key = clipped.toLowerCase(Locale.ROOT);
            if (!dedupe.add(key)) {
                continue;
            }
            lines.add(clipped);
            if (lines.size() >= MAX_CONTEXT_ITEMS) {
                break;
            }
        }
        return lines;
    }

    private String clipText(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLen));
    }

    private ArrayNode buildComponents(String keyword, int width, int height, String theme, IntentProfile intent) {
        ArrayNode components = objectMapper.createArrayNode();
        String titleColor = "glacier".equals(theme) ? "#1f2937" : "#e2f4ff";
        String subColor = "glacier".equals(theme) ? "#4b5563" : "#9cc9ff";
        List<String> metricSlots = metricSlots(intent);

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
        components.add(numberCard("ai-card-1", metricSlots.get(0), firstCardX, cardTop, cardWidth, 100, 80, 128560, "q-kpi"));
        components.add(numberCard("ai-card-2", metricSlots.get(1), firstCardX + (cardWidth + gap), cardTop, cardWidth, 100, 80, 32490, "q-kpi"));
        components.add(numberCard("ai-card-3", metricSlots.get(2), firstCardX + (cardWidth + gap) * 2, cardTop, cardWidth, 100, 80, 98.7, "q-kpi"));
        components.add(numberCard("ai-card-4", metricSlots.get(3), firstCardX + (cardWidth + gap) * 3, cardTop, cardWidth, 100, 80, 8743, "q-kpi"));

        components.add(lineChart("ai-line", chartTitle(keyword, "趋势分析"), 40, chartTop, 920, 410, 60, "q-trend"));
        components.add(barChart("ai-bar", chartTitle(keyword, "结构对比"), 990, chartTop, 470, 410, 60, "q-compare"));
        components.add(pieChart("ai-pie", chartTitle(keyword, "占比分析"), 1480, chartTop, 400, 410, 60, "q-share"));
        components.add(tableComp("ai-table", chartTitle(keyword, "明细列表"), 40, bottomTop, width - 80, height - bottomTop - 40, 40, "q-detail"));

        return components;
    }

    private ObjectNode numberCard(
            String id,
            String title,
            int x,
            int y,
            int width,
            int height,
            int zIndex,
            double value,
            String queryRef) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("value", value);
        config.put("precision", value % 1 == 0 ? 0 : 1);
        config.put("prefix", "");
        config.put("suffix", "");
        config.put("titleColor", "#9cc9ff");
        config.put("valueColor", "#f8fbff");
        config.put("queryRef", queryRef);
        config.put("bindMode", "suggested");
        return component(id, "number-card", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode lineChart(String id, String title, int x, int y, int width, int height, int zIndex, String queryRef) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "top");
        config.put("lineSmooth", true);
        config.put("areaStyle", true);
        config.put("queryRef", queryRef);

        ArrayNode xAxis = objectMapper.createArrayNode();
        xAxis.add("周一").add("周二").add("周三").add("周四").add("周五").add("周六").add("周日");
        config.set("xAxisData", xAxis);

        ArrayNode series = objectMapper.createArrayNode();
        series.add(objectMapper.createObjectNode().put("name", "本期").set("data", toNumberArray(120, 132, 141, 154, 169, 180, 176)));
        series.add(objectMapper.createObjectNode().put("name", "上期").set("data", toNumberArray(102, 118, 129, 136, 148, 157, 161)));
        config.set("series", series);

        return component(id, "line-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode barChart(String id, String title, int x, int y, int width, int height, int zIndex, String queryRef) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "top");
        config.put("queryRef", queryRef);

        ArrayNode xAxis = objectMapper.createArrayNode();
        xAxis.add("A类").add("B类").add("C类").add("D类").add("E类");
        config.set("xAxisData", xAxis);

        ArrayNode series = objectMapper.createArrayNode();
        series.add(objectMapper.createObjectNode().put("name", "数量").set("data", toNumberArray(32, 48, 41, 36, 52)));
        series.add(objectMapper.createObjectNode().put("name", "目标").set("data", toNumberArray(35, 45, 44, 40, 50)));
        config.set("series", series);

        return component(id, "bar-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode pieChart(String id, String title, int x, int y, int width, int height, int zIndex, String queryRef) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("titleFontSize", 14);
        config.put("legendPosition", "bottom");
        config.put("queryRef", queryRef);

        ArrayNode data = objectMapper.createArrayNode();
        data.add(objectMapper.createObjectNode().put("name", "渠道A").put("value", 335));
        data.add(objectMapper.createObjectNode().put("name", "渠道B").put("value", 287));
        data.add(objectMapper.createObjectNode().put("name", "渠道C").put("value", 192));
        data.add(objectMapper.createObjectNode().put("name", "渠道D").put("value", 140));
        config.set("data", data);

        return component(id, "pie-chart", title, x, y, width, height, zIndex, config);
    }

    private ObjectNode tableComp(String id, String title, int x, int y, int width, int height, int zIndex, String queryRef) {
        ObjectNode config = objectMapper.createObjectNode();
        config.put("title", title);
        config.put("queryRef", queryRef);

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

    private ArrayNode buildGlobalVariables(String keyword, IntentProfile intent) {
        ArrayNode variables = objectMapper.createArrayNode();
        if (containsAny(keyword, "时间", "日期", "趋势", "同比", "环比", "day", "week", "month")
                || intent.timeRange() != null) {
            variables.add(objectMapper.createObjectNode()
                    .put("key", "date_range")
                    .put("label", "时间范围")
                    .put("type", "string")
                    .put("defaultValue", intent.timeRange() == null ? "最近7天" : intent.timeRange())
                    .put("description", "用于驱动卡片参数中的时间范围"));
        }
        if (containsAny(keyword, "地区", "区域", "省份", "城市", "region", "area")
                || intent.dimensions().stream().anyMatch(dim -> containsAny(dim.toLowerCase(Locale.ROOT), "区域", "地区", "省", "市"))) {
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
            if (!hasVariableKey(vars, "startDate")) {
                vars.add(objectMapper.createObjectNode()
                        .put("key", "startDate")
                        .put("label", "开始日期")
                        .put("type", "date")
                        .put("defaultValue", ""));
            }
            if (!hasVariableKey(vars, "endDate")) {
                vars.add(objectMapper.createObjectNode()
                        .put("key", "endDate")
                        .put("label", "结束日期")
                        .put("type", "date")
                        .put("defaultValue", ""));
            }
        }
    }

    private int addTabScenarioSwitcher(ObjectNode screenSpec, String keyword) {
        JsonNode compsNode = screenSpec.path("components");
        JsonNode varsNode = screenSpec.path("globalVariables");
        if (!compsNode.isArray() || !varsNode.isArray()) {
            return -1;
        }
        ArrayNode components = (ArrayNode) compsNode;
        ArrayNode vars = (ArrayNode) varsNode;
        ArrayNode options = resolveTabOptions(keyword);
        List<String> tabValues = extractTabValues(options);
        if (tabValues.isEmpty()) {
            tabValues = List.of("overview");
            options = objectMapper.createArrayNode().add(objectMapper.createObjectNode()
                    .put("label", "总览")
                    .put("value", "overview"));
        }

        String defaultValue = tabValues.get(0);
        if (!hasVariableKey(vars, "tabKey")) {
            vars.add(objectMapper.createObjectNode()
                    .put("key", "tabKey")
                    .put("label", "场景切换")
                    .put("type", "string")
                    .put("defaultValue", defaultValue)
                    .put("description", "用于 Tab 场景显隐联动"));
        }

        ObjectNode existingSwitcher = null;
        int maxZ = 0;
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) continue;
            ObjectNode comp = (ObjectNode) item;
            maxZ = Math.max(maxZ, comp.path("zIndex").asInt(0));
            if ("tab-switcher".equals(comp.path("type").asText(""))) {
                existingSwitcher = comp;
            }
        }

        if (existingSwitcher == null) {
            ObjectNode config = objectMapper.createObjectNode()
                    .put("label", "场景切换")
                    .put("variableKey", "tabKey")
                    .put("optionSourceMode", "manual")
                    .put("defaultValue", defaultValue)
                    .put("compact", false)
                    .put("activeTextColor", "#0f172a")
                    .put("activeBackgroundColor", "#38bdf8")
                    .put("inactiveTextColor", "#94a3b8")
                    .put("inactiveBackgroundColor", "#0f172a");
            config.set("options", options);
            components.add(component(
                    "ai-tab-switcher",
                    "tab-switcher",
                    "场景切换",
                    660,
                    72,
                    460,
                    56,
                    maxZ + 2,
                    config));
        } else {
            ObjectNode config = existingSwitcher.path("config").isObject()
                    ? (ObjectNode) existingSwitcher.path("config")
                    : objectMapper.createObjectNode();
            config.put("label", "场景切换");
            config.put("variableKey", "tabKey");
            config.put("optionSourceMode", "manual");
            config.put("defaultValue", defaultValue);
            config.set("options", options);
            existingSwitcher.set("config", config);
        }

        Set<String> targetTypes = Set.of(
                "line-chart", "bar-chart", "pie-chart", "map-chart", "table",
                "scroll-board", "scroll-ranking", "funnel-chart", "scatter-chart", "radar-chart", "gauge-chart");
        int assigned = 0;
        int index = 0;
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) continue;
            ObjectNode comp = (ObjectNode) item;
            String type = comp.path("type").asText("");
            if (!targetTypes.contains(type)) continue;
            ObjectNode config = comp.path("config").isObject()
                    ? (ObjectNode) comp.path("config")
                    : objectMapper.createObjectNode();
            ArrayNode matchValues = objectMapper.createArrayNode();
            String matchValue = tabValues.get(index % tabValues.size());
            matchValues.add(matchValue);
            config.put("visibilityRuleEnabled", true);
            config.put("visibilityVariableKey", "tabKey");
            config.put("visibilityMatchMode", "equals");
            config.set("visibilityMatchValues", matchValues);
            comp.set("config", config);
            assigned++;
            index++;
        }
        return assigned;
    }

    private int removeTabScenarioSwitcher(ObjectNode screenSpec) {
        JsonNode compsNode = screenSpec.path("components");
        int changed = 0;
        Set<String> tabVariableKeys = new LinkedHashSet<>();
        // Backward compatibility: always clean historical tabKey linkage if present.
        tabVariableKeys.add("tabKey");
        if (compsNode.isArray()) {
            ArrayNode source = (ArrayNode) compsNode;
            ArrayNode filtered = objectMapper.createArrayNode();
            for (JsonNode item : source) {
                if (item == null || !item.isObject()) {
                    filtered.add(item);
                    continue;
                }
                ObjectNode comp = (ObjectNode) item;
                if ("tab-switcher".equals(comp.path("type").asText(""))) {
                    String varKey = extractTabVariableKey(comp);
                    if (varKey != null && !varKey.isBlank()) {
                        tabVariableKeys.add(varKey);
                    }
                    changed++;
                    continue;
                }
                filtered.add(comp);
            }
            for (JsonNode item : filtered) {
                if (item == null || !item.isObject()) {
                    continue;
                }
                ObjectNode comp = (ObjectNode) item;
                ObjectNode config = comp.path("config").isObject()
                        ? (ObjectNode) comp.path("config")
                        : null;
                if (config != null) {
                    String visibilityVarKey = config.path("visibilityVariableKey").asText("");
                    if (tabVariableKeys.contains(visibilityVarKey)) {
                        config.remove("visibilityRuleEnabled");
                        config.remove("visibilityVariableKey");
                        config.remove("visibilityMatchMode");
                        config.remove("visibilityMatchValues");
                        config.remove("visibilityMatchValue");
                        changed++;
                    }
                }
            }
            screenSpec.set("components", filtered);
        }

        JsonNode varsNode = screenSpec.path("globalVariables");
        if (varsNode.isArray()) {
            ArrayNode vars = (ArrayNode) varsNode;
            ArrayNode filteredVars = objectMapper.createArrayNode();
            int removed = 0;
            for (JsonNode item : vars) {
                if (item != null && item.isObject() && tabVariableKeys.contains(item.path("key").asText())) {
                    removed++;
                    continue;
                }
                filteredVars.add(item);
            }
            if (removed > 0) {
                changed += removed;
                screenSpec.set("globalVariables", filteredVars);
            }
        }
        return changed;
    }

    private String extractTabVariableKey(ObjectNode component) {
        if (component == null) {
            return "tabKey";
        }
        JsonNode config = component.path("config");
        String key = config == null ? null : config.path("variableKey").asText(null);
        if (key == null || key.isBlank()) {
            return "tabKey";
        }
        return key.trim();
    }

    private ArrayNode resolveTabOptions(String keyword) {
        ArrayNode options = objectMapper.createArrayNode();
        if (containsAny(keyword, "设备", "告警", "监控")) {
            options.add(objectMapper.createObjectNode().put("label", "总览").put("value", "overview"));
            options.add(objectMapper.createObjectNode().put("label", "设备").put("value", "device"));
            options.add(objectMapper.createObjectNode().put("label", "告警").put("value", "alert"));
            return options;
        }
        if (containsAny(keyword, "区域", "地区", "省", "市")) {
            options.add(objectMapper.createObjectNode().put("label", "总览").put("value", "overview"));
            options.add(objectMapper.createObjectNode().put("label", "区域").put("value", "region"));
            options.add(objectMapper.createObjectNode().put("label", "明细").put("value", "detail"));
            return options;
        }
        options.add(objectMapper.createObjectNode().put("label", "总览").put("value", "overview"));
        options.add(objectMapper.createObjectNode().put("label", "趋势").put("value", "trend"));
        options.add(objectMapper.createObjectNode().put("label", "明细").put("value", "detail"));
        return options;
    }

    private List<String> extractTabValues(ArrayNode options) {
        List<String> out = new ArrayList<>();
        for (JsonNode item : options) {
            if (item == null || !item.isObject()) continue;
            String value = item.path("value").asText("").trim();
            if (!value.isEmpty()) {
                out.add(value);
            }
        }
        return out;
    }

    private boolean hasTabSwitchIntent(String keyword) {
        if (containsAny(keyword, "tab切换", "tab组件", "标签页", "分场景", "场景切换", "切换场景", "分视图", "视图切换")) {
            return true;
        }
        return TAB_WORD_PATTERN.matcher(keyword).find();
    }

    private boolean hasRemoveTabSwitchIntent(String keyword) {
        return containsAny(keyword, "删除tab", "移除tab", "取消tab", "去掉tab", "移除标签页", "取消场景切换", "关闭场景切换");
    }

    private boolean hasVariableKey(ArrayNode vars, String key) {
        for (JsonNode item : vars) {
            if (item != null && item.isObject() && key.equals(item.path("key").asText())) {
                return true;
            }
        }
        return false;
    }

    private Integer parseRefreshIntervalSeconds(String keyword) {
        Matcher secondsMatcher = REFRESH_SECONDS_PATTERN.matcher(keyword);
        if (secondsMatcher.find()) {
            int seconds = safeParseInt(secondsMatcher.group(1));
            return clamp(seconds, 5, 3600);
        }
        Matcher minutesMatcher = REFRESH_MINUTES_PATTERN.matcher(keyword);
        if (minutesMatcher.find()) {
            int minutes = safeParseInt(minutesMatcher.group(1));
            return clamp(minutes * 60, 5, 3600);
        }
        if (containsAny(keyword, "实时刷新", "高频刷新")) {
            return 5;
        }
        return null;
    }

    private int safeParseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private int scaleTypography(ObjectNode screenSpec, double factor) {
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return 0;
        }
        int changed = 0;
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode config = item.path("config").isObject() ? (ObjectNode) item.path("config") : null;
            if (config == null) {
                continue;
            }
            changed += scaleTypographyFields(config, factor);
        }
        return changed;
    }

    private int scaleTypographyFields(ObjectNode config, double factor) {
        int changed = 0;
        List<String> keys = List.of("fontSize", "titleFontSize", "labelFontSize", "valueFontSize");
        for (String key : keys) {
            JsonNode valueNode = config.get(key);
            if (valueNode != null && valueNode.isNumber()) {
                int current = valueNode.asInt();
                int scaled = Math.max(10, Math.min(72, (int) Math.round(current * factor)));
                if (scaled != current) {
                    config.put(key, scaled);
                    changed++;
                }
            }
        }
        return changed;
    }

    private boolean addNumberCard(ObjectNode screenSpec) {
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return false;
        }
        ArrayNode components = (ArrayNode) node;
        int maxZ = 0;
        int maxY = 0;
        int count = 0;
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String type = item.path("type").asText("");
            maxZ = Math.max(maxZ, item.path("zIndex").asInt(0));
            maxY = Math.max(maxY, item.path("y").asInt(0));
            if ("number-card".equals(type)) {
                count++;
            }
        }
        String id = "ai-card-extra-" + (count + 1);
        components.add(numberCard(
                id,
                "新增指标" + (count + 1),
                40 + (count % 4) * 300,
                Math.max(100, maxY + 30),
                280,
                100,
                maxZ + 1,
                0,
                "q-kpi"));
        return true;
    }

    private int removeComponentsByType(ObjectNode screenSpec, String type) {
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return 0;
        }
        ArrayNode source = (ArrayNode) node;
        ArrayNode filtered = objectMapper.createArrayNode();
        int removed = 0;
        for (JsonNode item : source) {
            if (item != null && item.isObject() && type.equals(item.path("type").asText(""))) {
                removed++;
                continue;
            }
            filtered.add(item);
        }
        if (removed > 0) {
            screenSpec.set("components", filtered);
        }
        return removed;
    }

    private void resizeCanvas(ObjectNode screenSpec, int width, int height) {
        int oldWidth = Math.max(1, screenSpec.path("width").asInt(width));
        int oldHeight = Math.max(1, screenSpec.path("height").asInt(height));
        double ratioX = width / (double) oldWidth;
        double ratioY = height / (double) oldHeight;
        screenSpec.put("width", width);
        screenSpec.put("height", height);
        JsonNode node = screenSpec.path("components");
        if (!node.isArray()) {
            return;
        }
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode comp = (ObjectNode) item;
            comp.put("x", (int) Math.round(comp.path("x").asInt(0) * ratioX));
            comp.put("y", (int) Math.round(comp.path("y").asInt(0) * ratioY));
            comp.put("width", Math.max(120, (int) Math.round(comp.path("width").asInt(100) * ratioX)));
            comp.put("height", Math.max(60, (int) Math.round(comp.path("height").asInt(60) * ratioY)));
        }
    }

    private IntentProfile parseIntent(String prompt, String keyword) {
        Set<String> metrics = new LinkedHashSet<>();
        if (containsAny(keyword, "销售", "收入", "gmv")) {
            metrics.add("销售额");
            metrics.add("订单量");
            metrics.add("客单价");
            metrics.add("转化率");
        }
        if (containsAny(keyword, "制造", "产线", "设备", "良率", "quality")) {
            metrics.add("产量");
            metrics.add("良率");
            metrics.add("停机时长");
            metrics.add("告警数");
        }
        if (containsAny(keyword, "能耗", "电耗", "碳排", "环保")) {
            metrics.add("总能耗");
            metrics.add("单位能耗");
            metrics.add("碳排放量");
        }
        if (metrics.isEmpty()) {
            metrics.add("核心指标A");
            metrics.add("核心指标B");
            metrics.add("核心指标C");
            metrics.add("核心指标D");
        }

        Set<String> dimensions = new LinkedHashSet<>();
        if (containsAny(keyword, "区域", "地区", "省", "市")) {
            dimensions.add("区域");
        }
        if (containsAny(keyword, "产品", "品类", "型号")) {
            dimensions.add("产品");
        }
        if (containsAny(keyword, "班组", "人员", "团队", "组织", "部门")) {
            dimensions.add("组织");
        }
        if (dimensions.isEmpty()) {
            dimensions.add("日期");
            dimensions.add("区域");
        }

        Set<String> filters = new LinkedHashSet<>();
        if (containsAny(keyword, "筛选", "过滤")) {
            filters.add("region");
            filters.add("date_range");
        }
        if (containsAny(keyword, "top", "排名", "前")) {
            filters.add("top_n");
        }

        String timeRange = resolveTimeRange(keyword);
        String granularity = resolveGranularity(keyword);
        String domain = resolveDomain(keyword);
        if (prompt != null && prompt.length() > 120 && !filters.contains("keyword")) {
            filters.add("keyword");
        }

        return new IntentProfile(
                domain,
                List.copyOf(metrics),
                List.copyOf(dimensions),
                timeRange,
                List.copyOf(filters),
                granularity);
    }

    private String resolveDomain(String keyword) {
        if (containsAny(keyword, "制造", "产线", "设备", "质检")) {
            return "manufacturing";
        }
        if (containsAny(keyword, "销售", "客户", "订单", "营收")) {
            return "sales";
        }
        if (containsAny(keyword, "能耗", "环保", "碳")) {
            return "energy";
        }
        return "operations";
    }

    private String resolveTimeRange(String keyword) {
        if (containsAny(keyword, "近24小时", "24h")) {
            return "近24小时";
        }
        if (containsAny(keyword, "近7天", "最近7天", "周报")) {
            return "最近7天";
        }
        if (containsAny(keyword, "近30天", "最近30天", "月报")) {
            return "最近30天";
        }
        if (containsAny(keyword, "近12个月", "最近一年", "年报")) {
            return "最近12个月";
        }
        return "最近7天";
    }

    private String resolveGranularity(String keyword) {
        if (containsAny(keyword, "按小时", "小时")) {
            return "hour";
        }
        if (containsAny(keyword, "按周", "每周", "周")) {
            return "week";
        }
        if (containsAny(keyword, "按月", "每月", "月")) {
            return "month";
        }
        return "day";
    }

    private List<String> metricSlots(IntentProfile intent) {
        List<String> source = intent.metrics();
        List<String> slots = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            if (i < source.size()) {
                slots.add(source.get(i));
            } else {
                slots.add("核心指标" + (char) ('A' + i));
            }
        }
        return slots;
    }

    private int recommendedRefreshSeconds(IntentProfile intent) {
        if ("hour".equals(intent.granularity())) {
            return 300;
        }
        if ("day".equals(intent.granularity())) {
            return 900;
        }
        return 1800;
    }

    private ObjectNode toIntentNode(IntentProfile intent) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("domain", intent.domain());
        node.put("timeRange", intent.timeRange());
        node.put("granularity", intent.granularity());
        node.putPOJO("metrics", intent.metrics());
        node.putPOJO("dimensions", intent.dimensions());
        node.putPOJO("filters", intent.filters());
        return node;
    }

    private ArrayNode buildQueryRecommendations(IntentProfile intent) {
        ArrayNode queries = objectMapper.createArrayNode();
        String factTable = resolveFactTable(intent);
        String timeField = resolveTimeField(intent);
        queries.add(queryRec(
                "q-kpi",
                "kpi-summary",
                intent,
                List.of("sum(" + intent.metrics().get(0) + ")", "sum(" + intent.metrics().get(1) + ")"),
                factTable,
                timeField));
        queries.add(queryRec(
                "q-trend",
                "trend",
                intent,
                List.of(intent.metrics().get(0), intent.metrics().get(1)),
                factTable,
                timeField));
        queries.add(queryRec(
                "q-compare",
                "compare",
                intent,
                List.of(intent.metrics().get(0)),
                factTable,
                timeField));
        queries.add(queryRec(
                "q-share",
                "share",
                intent,
                List.of(intent.metrics().get(0)),
                factTable,
                timeField));
        queries.add(queryRec(
                "q-detail",
                "detail",
                intent,
                intent.metrics().subList(0, Math.min(3, intent.metrics().size())),
                factTable,
                timeField));
        return queries;
    }

    private ObjectNode queryRec(
            String id,
            String purpose,
            IntentProfile intent,
            List<String> metricExpr,
            String factTable,
            String timeField) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("purpose", purpose);
        node.put("mode", "semantic-suggested");
        node.put("semanticLayer", "heuristic");
        node.put("domain", intent.domain());
        node.put("timeRange", intent.timeRange());
        node.put("granularity", intent.granularity());
        node.put("factTable", factTable);
        node.put("timeField", timeField);
        node.putPOJO("dimensions", intent.dimensions());
        node.putPOJO("metrics", metricExpr);
        node.putPOJO("filters", intent.filters());
        node.put("sqlHint", buildSqlTemplate(purpose, metricExpr, intent, factTable, timeField));
        return node;
    }

    private ObjectNode buildSemanticModelHints(IntentProfile intent) {
        ObjectNode hints = objectMapper.createObjectNode();
        String factTable = resolveFactTable(intent);
        String timeField = resolveTimeField(intent);
        hints.put("domain", intent.domain());
        hints.put("factTable", factTable);
        hints.put("timeField", timeField);
        hints.putPOJO("dimensions", intent.dimensions());

        ArrayNode metricMappings = objectMapper.createArrayNode();
        for (String metric : intent.metrics()) {
            ObjectNode metricNode = objectMapper.createObjectNode();
            metricNode.put("name", metric);
            metricNode.put("expression", inferMetricExpression(metric));
            metricMappings.add(metricNode);
        }
        hints.set("metricMappings", metricMappings);
        return hints;
    }

    private ArrayNode buildSqlBlueprints(IntentProfile intent) {
        ArrayNode blueprints = objectMapper.createArrayNode();
        ArrayNode queryRecommendations = buildQueryRecommendations(intent);
        for (JsonNode item : queryRecommendations) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("queryId", item.path("id").asText(""));
            node.put("purpose", item.path("purpose").asText(""));
            node.put("sql", item.path("sqlHint").asText(""));
            node.put("factTable", item.path("factTable").asText(""));
            node.put("timeField", item.path("timeField").asText(""));
            blueprints.add(node);
        }
        return blueprints;
    }

    private String resolveFactTable(IntentProfile intent) {
        return switch (intent.domain()) {
            case "manufacturing" -> "fact_manufacturing_event";
            case "sales" -> "fact_sales_order";
            case "energy" -> "fact_energy_consumption";
            default -> "fact_operation_event";
        };
    }

    private String resolveTimeField(IntentProfile intent) {
        return switch (intent.granularity()) {
            case "hour" -> "event_hour";
            case "week" -> "event_week";
            case "month" -> "event_month";
            default -> "event_date";
        };
    }

    private String inferMetricExpression(String metric) {
        String value = metric == null ? "" : metric.toLowerCase(Locale.ROOT);
        if (containsAny(value, "率", "占比", "比率", "转化")) {
            return "avg(" + metric + ")";
        }
        if (containsAny(value, "时长", "耗时", "分钟", "小时")) {
            return "sum(" + metric + ")";
        }
        if (containsAny(value, "量", "数", "次数", "订单", "告警", "产量", "能耗")) {
            return "sum(" + metric + ")";
        }
        return "sum(" + metric + ")";
    }

    private String buildSqlTemplate(
            String purpose,
            List<String> metricExpr,
            IntentProfile intent,
            String factTable,
            String timeField) {
        List<String> dimensions = intent.dimensions().isEmpty() ? List.of(timeField) : intent.dimensions();
        String dimension = dimensions.get(0);
        String metricPart = metricExpr == null || metricExpr.isEmpty()
                ? "count(*) as value"
                : metricExpr.stream()
                        .map(expr -> expr + " as " + sanitizeAlias(expr))
                        .collect(java.util.stream.Collectors.joining(", "));
        String where = "WHERE ${date_range} AND ${filters}";
        if ("detail".equals(purpose)) {
            return "SELECT " + String.join(", ", dimensions) + ", " + metricPart + " FROM " + factTable + " "
                    + where + " ORDER BY " + timeField + " DESC LIMIT 500";
        }
        if ("share".equals(purpose)) {
            return "SELECT " + dimension + ", " + metricPart + " FROM " + factTable + " " + where
                    + " GROUP BY " + dimension + " ORDER BY 2 DESC";
        }
        if ("compare".equals(purpose)) {
            return "SELECT " + dimension + ", " + metricPart + " FROM " + factTable + " " + where
                    + " GROUP BY " + dimension + " ORDER BY 2 DESC LIMIT 20";
        }
        if ("trend".equals(purpose)) {
            return "SELECT " + timeField + ", " + metricPart + " FROM " + factTable + " " + where
                    + " GROUP BY " + timeField + " ORDER BY " + timeField;
        }
        return "SELECT " + metricPart + " FROM " + factTable + " " + where;
    }

    private String sanitizeAlias(String expr) {
        String text = expr == null ? "metric" : expr.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                out.append(c);
            } else if (out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
            if (out.length() >= 32) {
                break;
            }
        }
        String alias = out.toString().replaceAll("^_+|_+$", "");
        return alias.isBlank() ? "metric" : alias;
    }

    private ArrayNode buildVizRecommendations(IntentProfile intent) {
        ArrayNode viz = objectMapper.createArrayNode();
        viz.add(vizRec("q-kpi", "number-card", "指标总览"));
        viz.add(vizRec("q-trend", "line-chart", "趋势分析"));
        viz.add(vizRec("q-compare", "bar-chart", "结构对比"));
        viz.add(vizRec("q-share", "pie-chart", "占比分析"));
        viz.add(vizRec("q-detail", "table", "明细列表"));
        return viz;
    }

    private ObjectNode vizRec(String queryId, String componentType, String title) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("queryId", queryId);
        node.put("componentType", componentType);
        node.put("title", title);
        return node;
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

    private record IntentProfile(
            String domain,
            List<String> metrics,
            List<String> dimensions,
            String timeRange,
            List<String> filters,
            String granularity) {}
}
