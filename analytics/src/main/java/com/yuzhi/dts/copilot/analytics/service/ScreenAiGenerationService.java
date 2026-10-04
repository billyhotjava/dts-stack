package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ScreenAiGenerationService {

    private static final int DEFAULT_WIDTH = 1920;
    private static final int DEFAULT_HEIGHT = 1080;
    private static final int MAX_CONTEXT_ITEMS = 12;
    private static final int MAX_CONTEXT_ITEM_CHARS = 240;
    private static final List<String> DANGEROUS_SQL_KEYWORDS = List.of(
            " insert ",
            " update ",
            " delete ",
            " drop ",
            " truncate ",
            " alter ",
            " create ",
            " merge ",
            " grant ",
            " revoke ",
            " call ",
            " execute ");

    private final ObjectMapper objectMapper;
    private final PlatformAiNativeClient platformAiNativeClient;

    @Autowired
    public ScreenAiGenerationService(
            ObjectMapper objectMapper,
            @Autowired(required = false) PlatformAiNativeClient platformAiNativeClient) {
        this.objectMapper = objectMapper;
        this.platformAiNativeClient = platformAiNativeClient;
    }

    ScreenAiGenerationService(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    public ObjectNode generate(String prompt, Integer width, Integer height) {
        String normalizedPrompt = normalizeText(prompt);
        if (!StringUtils.hasText(normalizedPrompt)) {
            throw new IllegalArgumentException("prompt is required");
        }
        int canvasWidth = normalizeCanvas(width, DEFAULT_WIDTH);
        int canvasHeight = normalizeCanvas(height, DEFAULT_HEIGHT);
        Optional<ObjectNode> fetched = fetchGenerateFromPlatform(normalizedPrompt, canvasWidth, canvasHeight);
        ObjectNode result = fetched
                .map(JsonNode::deepCopy)
                .map(node -> (ObjectNode) node)
                .orElseGet(() -> fallbackGenerateResult(normalizedPrompt, canvasWidth, canvasHeight));
        return normalizeAiResult(result, normalizedPrompt, canvasWidth, canvasHeight, List.of(), true, false);
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode) {
        return revise(instruction, screenSpecNode, List.of(), true);
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode, List<String> context) {
        return revise(instruction, screenSpecNode, context, true);
    }

    public ObjectNode revise(String instruction, JsonNode screenSpecNode, List<String> context, boolean applyChanges) {
        String normalizedInstruction = normalizeText(instruction);
        if (!StringUtils.hasText(normalizedInstruction)) {
            throw new IllegalArgumentException("prompt is required");
        }
        ObjectNode inputScreenSpec = screenSpecNode != null && screenSpecNode.isObject()
                ? ((ObjectNode) screenSpecNode).deepCopy()
                : objectMapper.createObjectNode();
        int canvasWidth = normalizeCanvas(inputScreenSpec.path("width").asInt(DEFAULT_WIDTH), DEFAULT_WIDTH);
        int canvasHeight = normalizeCanvas(inputScreenSpec.path("height").asInt(DEFAULT_HEIGHT), DEFAULT_HEIGHT);
        inputScreenSpec.put("width", canvasWidth);
        inputScreenSpec.put("height", canvasHeight);
        List<String> normalizedContext = normalizeContext(context);
        Optional<ObjectNode> fetched =
                fetchReviseFromPlatform(normalizedInstruction, inputScreenSpec, normalizedContext, applyChanges);
        ObjectNode result = fetched
                .map(JsonNode::deepCopy)
                .map(node -> (ObjectNode) node)
                .orElseGet(() -> fallbackReviseResult(inputScreenSpec, normalizedContext, applyChanges));
        return normalizeAiResult(result, normalizedInstruction, canvasWidth, canvasHeight, normalizedContext, applyChanges, true);
    }

    protected Optional<ObjectNode> fetchGenerateFromPlatform(String prompt, int width, int height) {
        if (platformAiNativeClient == null) {
            return Optional.empty();
        }
        return platformAiNativeClient.generateScreen(prompt, width, height);
    }

    protected Optional<ObjectNode> fetchReviseFromPlatform(
            String prompt,
            JsonNode screenSpec,
            List<String> context,
            boolean applyChanges) {
        if (platformAiNativeClient == null) {
            return Optional.empty();
        }
        return platformAiNativeClient.reviseScreen(prompt, screenSpec, context, applyChanges);
    }

    private ObjectNode fallbackGenerateResult(String prompt, int width, int height) {
        String domain = inferFallbackDomain(prompt);
        String factTable = inferFallbackFactTable(domain);

        ObjectNode result = objectMapper.createObjectNode();
        ObjectNode intent = result.putObject("intent");
        intent.put("domain", domain);
        intent.put("timeRange", "recent_30_days");
        intent.put("granularity", "day");

        ObjectNode semanticHints = result.putObject("semanticModelHints");
        semanticHints.put("domain", domain);
        semanticHints.put("factTable", factTable);
        semanticHints.put("timeField", "event_time");

        ArrayNode recommendations = result.putArray("queryRecommendations");
        ObjectNode recommendation = recommendations.addObject();
        recommendation.put("id", "q1");
        recommendation.put("purpose", "fallback");
        recommendation.put("domain", domain);
        recommendation.put("factTable", factTable);
        recommendation.put("timeRange", "recent_30_days");
        recommendation.put("granularity", "day");

        ArrayNode blueprints = result.putArray("sqlBlueprints");
        ObjectNode blueprint = blueprints.addObject();
        blueprint.put("queryId", "q1");
        blueprint.put("purpose", "fallback");
        blueprint.put("sql", buildFallbackSql(domain, factTable));
        blueprint.put("factTable", factTable);

        ArrayNode vizRecommendations = result.putArray("vizRecommendations");
        vizRecommendations.addObject().put("queryId", "q1").put("componentType", "line-chart").put("title", "趋势分析");

        ObjectNode diagnostics = result.putObject("nl2sqlDiagnostics");
        diagnostics.put("stage", "ai-native-fallback");
        diagnostics.put("status", "degraded");
        diagnostics.put("executionReadiness", "ready");
        diagnostics.put("fallback", true);
        diagnostics.put("fallbackReason", "platform_ai_unavailable");

        ObjectNode quality = result.putObject("quality");
        quality.put("score", 70);
        quality.putArray("warnings").add("AI Native 服务暂不可用，已返回降级 SQL 草案。");
        quality.putArray("suggestions")
                .add("请在平台侧完成 AI Provider 配置，获取更准确的业务 SQL。")
                .add("当前 SQL 为兜底草案，建议结合语义模型字段进一步修订。");

        ObjectNode screenSpec = result.putObject("screenSpec");
        screenSpec.put("name", "AI 生成大屏");
        screenSpec.put("description", prompt);
        screenSpec.put("width", width);
        screenSpec.put("height", height);
        screenSpec.put("theme", "glacier");
        screenSpec.put("backgroundColor", "#f6f7f9");
        screenSpec.set("components", objectMapper.createArrayNode());
        screenSpec.set("globalVariables", objectMapper.createArrayNode());
        return result;
    }

    private ObjectNode fallbackReviseResult(ObjectNode inputScreenSpec, List<String> context, boolean applyChanges) {
        ObjectNode result = objectMapper.createObjectNode();
        result.set("screenSpec", inputScreenSpec == null ? objectMapper.createObjectNode() : inputScreenSpec.deepCopy());
        result.set("sqlBlueprints", objectMapper.createArrayNode());
        result.set("queryRecommendations", objectMapper.createArrayNode());
        result.set("vizRecommendations", objectMapper.createArrayNode());
        result.set("metricLensReferences", objectMapper.createArrayNode());
        result.set("semanticRecall", objectMapper.createObjectNode());
        result.putArray("actions").add("AI Native 服务不可用，已保留原始布局。");

        ObjectNode diagnostics = result.putObject("nl2sqlDiagnostics");
        diagnostics.put("stage", "ai-native-fallback");
        diagnostics.put("status", "degraded");
        diagnostics.put("executionReadiness", "ready");
        diagnostics.put("fallback", true);
        diagnostics.put("fallbackReason", "platform_ai_unavailable");
        diagnostics.put("contextCount", context == null ? 0 : context.size());

        ObjectNode quality = result.putObject("quality");
        quality.put("score", 70);
        quality.putArray("warnings").add("AI Native 修订不可用，已返回原始配置。");
        quality.putArray("suggestions")
                .add("请检查平台 AI 配置后重试。")
                .add(applyChanges ? "当前为 apply 模式，未执行自动改写。" : "当前为 suggest 模式，未生成建议。");
        return result;
    }

    private String inferFallbackDomain(String prompt) {
        String text = normalizeText(prompt);
        if (!StringUtils.hasText(text)) {
            return "generic";
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "销售", "订单", "客户", "sales", "order", "customer")) {
            return "sales";
        }
        if (containsAny(lower, "能耗", "能源", "电", "energy", "power")) {
            return "energy";
        }
        if (containsAny(lower, "生产", "工单", "产线", "production", "manufacturing")) {
            return "production";
        }
        if (containsAny(lower, "质量", "缺陷", "良率", "quality", "defect")) {
            return "quality";
        }
        if (containsAny(lower, "库存", "仓储", "inventory", "stock")) {
            return "inventory";
        }
        if (containsAny(lower, "财务", "收入", "成本", "finance", "revenue", "cost")) {
            return "finance";
        }
        return "generic";
    }

    private String inferFallbackFactTable(String domain) {
        return switch (domain) {
            case "sales" -> "fact_sales_order";
            case "energy" -> "fact_energy_usage";
            case "production" -> "fact_production_event";
            case "quality" -> "fact_quality_inspection";
            case "inventory" -> "fact_inventory_snapshot";
            case "finance" -> "fact_finance_entry";
            default -> "fact_business_event";
        };
    }

    private String buildFallbackSql(String domain, String factTable) {
        String safeDomain = normalizeText(domain);
        if (!StringUtils.hasText(safeDomain)) {
            safeDomain = "generic";
        }
        String escapedDomain = safeDomain.replace("'", "''");
        return "SELECT CURRENT_TIMESTAMP AS generated_at, '" + escapedDomain + "' AS inferred_domain, 0 AS metric_value";
    }

    private ObjectNode normalizeAiResult(
            ObjectNode raw,
            String prompt,
            int width,
            int height,
            List<String> context,
            boolean applyChanges,
            boolean revisionMode) {
        ObjectNode out = raw == null ? objectMapper.createObjectNode() : raw.deepCopy();
        String defaultEngine = revisionMode ? "platform-llm-v1-revise" : "platform-llm-v1";
        out.put("engine", defaultEngine);
        out.put("prompt", prompt);

        ObjectNode intent = asObject(out.path("intent"));
        out.set("intent", intent);

        ObjectNode semanticModelHints = asObject(out.path("semanticModelHints"));
        out.set("semanticModelHints", semanticModelHints);

        ArrayNode queryRecommendations = asArray(out.path("queryRecommendations"));
        out.set("queryRecommendations", queryRecommendations);

        ArrayNode sqlBlueprints = sanitizeSqlBlueprints(asArray(out.path("sqlBlueprints")));
        out.set("sqlBlueprints", sqlBlueprints);

        ArrayNode vizRecommendations = asArray(out.path("vizRecommendations"));
        out.set("vizRecommendations", vizRecommendations);

        ObjectNode semanticRecall = asObject(out.path("semanticRecall"));
        out.set("semanticRecall", semanticRecall);

        ArrayNode metricLensReferences = asArray(out.path("metricLensReferences"));
        out.set("metricLensReferences", metricLensReferences);

        ObjectNode diagnostics = asObject(out.path("nl2sqlDiagnostics"));
        applyDiagnosticsDefaults(diagnostics, queryRecommendations, sqlBlueprints);
        out.set("nl2sqlDiagnostics", diagnostics);

        ObjectNode quality = asObject(out.path("quality"));
        applyQualityDefaults(quality, diagnostics);
        out.set("quality", quality);

        ObjectNode screenSpec = resolveScreenSpec(out.path("screenSpec"), prompt, width, height);
        out.set("screenSpec", screenSpec);

        if (revisionMode) {
            out.put("contextCount", context == null ? 0 : context.size());
            out.put("usedContextCount", context == null ? 0 : context.size());
            out.put("applyMode", applyChanges ? "apply" : "suggest");
            out.put("applied", applyChanges);
            out.set("actions", asArray(out.path("actions")));
        }
        return out;
    }

    private ObjectNode resolveScreenSpec(JsonNode node, String prompt, int width, int height) {
        ObjectNode out = node != null && node.isObject()
                ? ((ObjectNode) node).deepCopy()
                : objectMapper.createObjectNode();
        putTextIfMissing(out, "name", "AI 生成大屏");
        putTextIfMissing(out, "description", prompt);
        out.put("width", normalizeCanvas(out.path("width").asInt(width), width));
        out.put("height", normalizeCanvas(out.path("height").asInt(height), height));
        putTextIfMissing(out, "theme", "glacier");
        putTextIfMissing(out, "backgroundColor", "#f6f7f9");
        if (!out.path("components").isArray()) {
            out.set("components", objectMapper.createArrayNode());
        }
        if (!out.path("globalVariables").isArray()) {
            out.set("globalVariables", objectMapper.createArrayNode());
        }
        return out;
    }

    private ArrayNode sanitizeSqlBlueprints(ArrayNode blueprints) {
        ArrayNode out = objectMapper.createArrayNode();
        for (int i = 0; i < blueprints.size(); i++) {
            JsonNode node = blueprints.get(i);
            if (!node.isObject()) {
                continue;
            }
            ObjectNode row = ((ObjectNode) node).deepCopy();
            String queryId = normalizeText(row.path("queryId").asText(null));
            if (!StringUtils.hasText(queryId)) {
                row.put("queryId", "q" + (i + 1));
            }
            String sql = normalizeText(row.path("sql").asText(null));
            boolean readOnly = isReadOnlySql(sql);
            if (!readOnly) {
                row.put("safetyStatus", "blocked");
                row.put("blockedReason", "non_read_only_sql");
            } else {
                row.put("safetyStatus", "safe");
            }
            out.add(row);
        }
        return out;
    }

    private void applyDiagnosticsDefaults(ObjectNode diagnostics, ArrayNode queryRecommendations, ArrayNode sqlBlueprints) {
        int safeCount = 0;
        int blockedCount = 0;
        ArrayNode blockedQueryIds = objectMapper.createArrayNode();
        for (JsonNode item : sqlBlueprints) {
            String status = normalizeText(item.path("safetyStatus").asText(""));
            if ("safe".equalsIgnoreCase(status)) {
                safeCount += 1;
            } else {
                blockedCount += 1;
                String queryId = normalizeText(item.path("queryId").asText(null));
                if (StringUtils.hasText(queryId)) {
                    blockedQueryIds.add(queryId);
                }
            }
        }
        putTextIfMissing(diagnostics, "stage", "ai-native");
        putIntIfMissing(diagnostics, "queryRecommendationCount", queryRecommendations.size());
        putIntIfMissing(diagnostics, "sqlBlueprintCount", sqlBlueprints.size());
        putIntIfMissing(diagnostics, "safeCount", safeCount);
        putIntIfMissing(diagnostics, "blockedCount", blockedCount);
        putTextIfMissing(diagnostics, "executionReadiness", blockedCount > 0 ? "blocked" : "ready");
        putTextIfMissing(diagnostics, "status", blockedCount > 0 ? "blocked" : "ready");
        if (!diagnostics.path("blockedQueryIds").isArray()) {
            diagnostics.set("blockedQueryIds", blockedQueryIds);
        }
    }

    private void applyQualityDefaults(ObjectNode quality, ObjectNode diagnostics) {
        int blockedCount = diagnostics.path("blockedCount").asInt(0);
        putIntIfMissing(quality, "score", Math.max(50, 92 - blockedCount * 8));

        ArrayNode warnings = asArray(quality.path("warnings"));
        if (blockedCount > 0 && !containsText(warnings, "检测到存在高风险 SQL，已标记为 blocked。")) {
            warnings.add("检测到存在高风险 SQL，已标记为 blocked。");
        }
        quality.set("warnings", warnings);

        ArrayNode suggestions = asArray(quality.path("suggestions"));
        if (suggestions.isEmpty()) {
            suggestions.add("可继续补充业务指标、维度和时间范围，提升 SQL 生成准确度。");
            suggestions.add("建议在创建大屏前先核验 SQL 与口径。");
        }
        quality.set("suggestions", suggestions);
    }

    private List<String> normalizeContext(List<String> context) {
        if (context == null || context.isEmpty()) {
            return List.of();
        }
        Set<String> dedupe = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String item : context) {
            String text = normalizeText(item);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (text.length() > MAX_CONTEXT_ITEM_CHARS) {
                text = text.substring(0, MAX_CONTEXT_ITEM_CHARS);
            }
            String key = text.toLowerCase(Locale.ROOT);
            if (!dedupe.add(key)) {
                continue;
            }
            out.add(text);
            if (out.size() >= MAX_CONTEXT_ITEMS) {
                break;
            }
        }
        return out;
    }

    private boolean isReadOnlySql(String sql) {
        String normalized = normalizeText(sql);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String compact = normalized.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!(compact.startsWith("select ") || compact.startsWith("with "))) {
            return false;
        }
        String wrapped = " " + compact + " ";
        for (String keyword : DANGEROUS_SQL_KEYWORDS) {
            if (wrapped.contains(keyword)) {
                return false;
            }
        }
        return true;
    }

    private ObjectNode asObject(JsonNode node) {
        if (node != null && node.isObject()) {
            return ((ObjectNode) node).deepCopy();
        }
        return objectMapper.createObjectNode();
    }

    private ArrayNode asArray(JsonNode node) {
        if (node != null && node.isArray()) {
            return ((ArrayNode) node).deepCopy();
        }
        return objectMapper.createArrayNode();
    }

    private boolean containsText(ArrayNode array, String value) {
        for (JsonNode item : array) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String text, String... candidates) {
        if (!StringUtils.hasText(text) || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate) && text.contains(candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private int normalizeCanvas(Integer value, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.min(7680, Math.max(640, value));
    }

    private String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String out = text.trim();
        return out.isEmpty() ? null : out;
    }

    private void putTextIfMissing(ObjectNode node, String field, String value) {
        if (!StringUtils.hasText(node.path(field).asText(null))) {
            node.put(field, value);
        }
    }

    private void putIntIfMissing(ObjectNode node, String field, int value) {
        if (!node.path(field).canConvertToInt()) {
            node.put(field, value);
        }
    }
}
