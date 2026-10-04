package com.yuzhi.dts.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yuzhi.dts.analytics.web.rest.errors.ScreenSpecValidationException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ScreenSpecValidator {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    private static final Set<String> COMPONENT_TYPES = Set.of(
            // ECharts 图表
            "line-chart",
            "bar-chart",
            "pie-chart",
            "gauge-chart",
            "scatter-chart",
            "radar-chart",
            "funnel-chart",
            "map-chart",
            "combo-chart",
            "wordcloud-chart",
            "treemap-chart",
            "sunburst-chart",
            "waterfall-chart",
            "gantt-chart",
            // DataV 装饰
            "border-box",
            "decoration",
            "scroll-board",
            "scroll-ranking",
            "water-level",
            "digital-flop",
            "flyline-chart",
            "percent-pond",
            // 基础组件
            "title",
            "markdown-text",
            "number-card",
            "progress-bar",
            "tab-switcher",
            "carousel",
            "countdown",
            "marquee",
            "shape",
            "container",
            "datetime",
            "image",
            "video",
            "iframe",
            "table",
            "filter-input",
            "filter-select",
            "filter-date-range",
            "richtext",
            // 企业组件
            "section-panel",
            "divider",
            "stat-card",
            // 3D 可视化 (echarts-gl)
            "globe-chart",
            "bar3d-chart",
            "scatter3d-chart");

    private static final Set<String> DATA_SOURCE_TYPES = Set.of("static", "api", "card", "sql", "dataset", "metric", "database");
    private static final Set<String> VARIABLE_TYPES = Set.of("string", "number", "date");
    private static final Set<String> VISIBILITY_MATCH_MODES = Set.of(
            "equals",
            "not-equals",
            "not-in",
            "contains",
            "not-contains",
            "starts-with",
            "ends-with",
            "empty",
            "not-empty");
    private static final Set<String> INTERACTION_TRANSFORMS = Set.of("raw", "string", "number", "lowercase", "uppercase");
    private static final Pattern VARIABLE_KEY_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_:\\.-]{0,63}$");

    public ValidationResult validateForWrite(JsonNode payload) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (payload == null || payload.isNull()) {
            return new ValidationResult(List.of(), List.of());
        }
        if (!payload.isObject()) {
            throw new ScreenSpecValidationException("SCREEN_SPEC_INVALID", List.of("screen payload must be object"));
        }

        validateSchemaVersion(payload.path("schemaVersion"), errors, warnings);
        validateSize(payload.path("width"), "width", 200, 7680, errors);
        validateSize(payload.path("height"), "height", 120, 4320, errors);
        validateComponents(payload.path("components"), errors, warnings);
        validateGlobalVariables(payload.path("globalVariables"), errors);

        if (!errors.isEmpty()) {
            throw new ScreenSpecValidationException("SCREEN_SPEC_INVALID", errors);
        }
        return new ValidationResult(List.of(), List.copyOf(warnings));
    }

    private void validateSchemaVersion(JsonNode node, List<String> errors, List<String> warnings) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            warnings.add("schemaVersion missing, server treats payload as v2");
            return;
        }
        if (!node.canConvertToInt()) {
            errors.add("schemaVersion must be integer");
            return;
        }
        int schemaVersion = node.asInt(CURRENT_SCHEMA_VERSION);
        if (schemaVersion > CURRENT_SCHEMA_VERSION) {
            errors.add("schemaVersion " + schemaVersion + " is newer than server-supported v" + CURRENT_SCHEMA_VERSION);
        } else if (schemaVersion < CURRENT_SCHEMA_VERSION) {
            warnings.add("schemaVersion " + schemaVersion + " accepted and treated as v" + CURRENT_SCHEMA_VERSION);
        }
    }

    private void validateSize(JsonNode node, String field, int min, int max, List<String> errors) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isNumber()) {
            errors.add(field + " must be number");
            return;
        }
        int value = node.asInt();
        if (value < min || value > max) {
            errors.add(field + " out of range [" + min + "," + max + "]");
        }
    }

    private void validateComponents(JsonNode componentsNode, List<String> errors, List<String> warnings) {
        if (componentsNode == null || componentsNode.isMissingNode() || componentsNode.isNull()) {
            return;
        }
        if (!componentsNode.isArray()) {
            errors.add("components must be array");
            return;
        }
        for (int i = 0; i < componentsNode.size(); i++) {
            JsonNode item = componentsNode.get(i);
            String path = "components[" + i + "]";
            if (item == null || !item.isObject()) {
                errors.add(path + " must be object");
                continue;
            }
            validateComponentItem(item, path, errors, warnings);
        }
    }

    private void validateComponentItem(JsonNode item, String path, List<String> errors, List<String> warnings) {
        String id = trimToNull(item.path("id").asText(null));
        if (id == null) {
            errors.add(path + ".id is required");
        }
        String type = trimToNull(item.path("type").asText(null));
        boolean pluginDeclared = hasPluginMarker(item.path("config"));
        if (type == null) {
            errors.add(path + ".type is required");
        } else if (!COMPONENT_TYPES.contains(type) && !pluginDeclared) {
            errors.add(path + ".type is unsupported: " + type);
        } else if (!COMPONENT_TYPES.contains(type) && pluginDeclared) {
            warnings.add(path + ".type(" + type + ") accepted via plugin marker");
        }

        validateNumberField(item.path("x"), path + ".x", false, errors);
        validateNumberField(item.path("y"), path + ".y", false, errors);
        validateNumberField(item.path("width"), path + ".width", true, errors);
        validateNumberField(item.path("height"), path + ".height", true, errors);
        validateDataSource(item.path("dataSource"), path + ".dataSource", errors);
        validateVisibilityRule(item.path("config"), path + ".config", errors);
        validateInteraction(item.path("interaction"), path + ".interaction", errors);
        boolean hasExecutableDrillChain = validateDrillDown(item.path("drillDown"), path + ".drillDown", errors);
        validateDrillActions(item.path("actions"), path + ".actions", hasExecutableDrillChain, errors);
    }

    private void validateNumberField(JsonNode node, String path, boolean positiveOnly, List<String> errors) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isNumber()) {
            errors.add(path + " must be number");
            return;
        }
        if (positiveOnly && node.asDouble(0) <= 0) {
            errors.add(path + " must be > 0");
        }
    }

    private void validateDataSource(JsonNode node, String path, List<String> errors) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (!node.isObject()) {
            errors.add(path + " must be object");
            return;
        }
        String sourceType = trimToNull(node.path("sourceType").asText(null));
        if (sourceType == null) {
            sourceType = trimToNull(node.path("type").asText(null));
        }
        if (sourceType == null) {
            return;
        }
        String normalized = sourceType.toLowerCase();
        if (!DATA_SOURCE_TYPES.contains(normalized)) {
            errors.add(path + ".sourceType is invalid: " + sourceType);
            return;
        }
        if ("sql".equals(normalized) || "database".equals(normalized)) {
            JsonNode sql = node.path("sqlConfig").isObject() ? node.path("sqlConfig") : node.path("databaseConfig");
            if (sql == null || !sql.isObject()) {
                errors.add(path + " requires sqlConfig/databaseConfig");
                return;
            }
            String query = trimToNull(sql.path("query").asText(null));
            if (query == null) {
                errors.add(path + ".query is required for sql source");
            }
            validateNumberField(sql.path("maxRows"), path + ".maxRows", true, errors);
            validateNumberField(sql.path("queryTimeoutSeconds"), path + ".queryTimeoutSeconds", true, errors);
        }
        if ("card".equals(normalized)) {
            JsonNode cardConfig = node.path("cardConfig");
            if (cardConfig == null || !cardConfig.isObject() || cardConfig.path("cardId").asLong(0L) <= 0L) {
                errors.add(path + ".cardConfig.cardId is required for card source");
            }
        }
        if ("metric".equals(normalized)) {
            JsonNode metricConfig = node.path("metricConfig");
            boolean metricValid = metricConfig != null
                    && metricConfig.isObject()
                    && (metricConfig.path("metricId").asLong(0L) > 0L || metricConfig.path("cardId").asLong(0L) > 0L);
            if (!metricValid) {
                errors.add(path + ".metricConfig.metricId/cardId is required for metric source");
            }
        }
    }

    private void validateVisibilityRule(JsonNode configNode, String path, List<String> errors) {
        if (configNode == null || !configNode.isObject()) {
            return;
        }
        if (!configNode.path("visibilityRuleEnabled").asBoolean(false)) {
            return;
        }
        String variableKey = trimToNull(configNode.path("visibilityVariableKey").asText(null));
        if (variableKey == null) {
            errors.add(path + ".visibilityVariableKey is required when visibilityRuleEnabled=true");
        }
        String mode = trimToNull(configNode.path("visibilityMatchMode").asText(null));
        String normalizedMode = mode == null ? "equals" : mode.toLowerCase();
        if (!VISIBILITY_MATCH_MODES.contains(normalizedMode)) {
            errors.add(path + ".visibilityMatchMode is invalid: " + normalizedMode);
            return;
        }
        if ("empty".equals(normalizedMode) || "not-empty".equals(normalizedMode)) {
            return;
        }
        JsonNode matchValues = configNode.path("visibilityMatchValues");
        if (matchValues.isArray() && matchValues.size() > 200) {
            errors.add(path + ".visibilityMatchValues too many items (max 200)");
        }
    }

    private void validateInteraction(JsonNode interactionNode, String path, List<String> errors) {
        if (interactionNode == null || interactionNode.isMissingNode() || interactionNode.isNull()) {
            return;
        }
        if (!interactionNode.isObject()) {
            errors.add(path + " must be object");
            return;
        }
        JsonNode mappingsNode = interactionNode.path("mappings");
        if (mappingsNode == null || mappingsNode.isMissingNode() || mappingsNode.isNull()) {
            return;
        }
        if (!mappingsNode.isArray()) {
            errors.add(path + ".mappings must be array");
            return;
        }
        for (int i = 0; i < mappingsNode.size(); i++) {
            JsonNode mapping = mappingsNode.get(i);
            String mappingPath = path + ".mappings[" + i + "]";
            if (mapping == null || !mapping.isObject()) {
                errors.add(mappingPath + " must be object");
                continue;
            }
            String variableKey = trimToNull(mapping.path("variableKey").asText(null));
            if (variableKey == null) {
                errors.add(mappingPath + ".variableKey is required");
            }
            String sourcePath = trimToNull(mapping.path("sourcePath").asText(null));
            if (sourcePath == null) {
                errors.add(mappingPath + ".sourcePath is required");
            }
            String transform = trimToNull(mapping.path("transform").asText(null));
            String normalizedTransform = transform == null ? "raw" : transform.toLowerCase();
            if (!INTERACTION_TRANSFORMS.contains(normalizedTransform)) {
                errors.add(mappingPath + ".transform is invalid: " + normalizedTransform);
            }
        }
    }

    private boolean validateDrillDown(JsonNode drillDownNode, String path, List<String> errors) {
        if (drillDownNode == null || drillDownNode.isMissingNode() || drillDownNode.isNull()) {
            return false;
        }
        if (!drillDownNode.isObject()) {
            errors.add(path + " must be object");
            return false;
        }
        JsonNode enabledNode = drillDownNode.path("enabled");
        if (!enabledNode.isMissingNode() && !enabledNode.isBoolean()) {
            errors.add(path + ".enabled must be boolean");
        }
        JsonNode levelsNode = drillDownNode.path("levels");
        if (!levelsNode.isArray()) {
            errors.add(path + ".levels must be array");
            return false;
        }

        boolean hasExecutableLevel = false;
        for (int i = 0; i < levelsNode.size(); i++) {
            JsonNode level = levelsNode.get(i);
            String levelPath = path + ".levels[" + i + "]";
            int errorCount = errors.size();
            if (level == null || !level.isObject()) {
                errors.add(levelPath + " must be object");
                continue;
            }
            if (trimToNull(level.path("label").asText(null)) == null) {
                errors.add(levelPath + ".label is required");
            }
            boolean legacy = level.path("cardId").asLong(0L) > 0L
                    && trimToNull(level.path("paramName").asText(null)) != null;
            JsonNode dataSource = level.path("dataSource");
            JsonNode mappings = level.path("mappings");
            boolean generic = dataSource.isObject() && mappings.isArray() && !mappings.isEmpty();
            if (!legacy && !generic) {
                errors.add(levelPath + " requires dataSource and mappings, or legacy cardId and paramName");
            }
            if (dataSource.isObject()) {
                validateDrillTargetDataSource(dataSource, levelPath + ".dataSource", errors);
            }
            if (!mappings.isMissingNode() && !mappings.isNull()) {
                validateInteraction(level, levelPath, errors);
            }
            if ((legacy || generic) && errors.size() == errorCount) {
                hasExecutableLevel = true;
            }
        }
        return drillDownNode.path("enabled").asBoolean(false) && hasExecutableLevel;
    }

    private void validateDrillTargetDataSource(JsonNode node, String path, List<String> errors) {
        int errorCount = errors.size();
        validateDataSource(node, path, errors);
        if (!node.isObject() || errors.size() > errorCount) {
            return;
        }
        String sourceType = trimToNull(node.path("sourceType").asText(null));
        if (sourceType == null) {
            sourceType = trimToNull(node.path("type").asText(null));
        }
        if (sourceType == null) {
            errors.add(path + ".sourceType is required");
            return;
        }
        String normalized = sourceType.toLowerCase();
        if ("sql".equals(normalized) || "database".equals(normalized)) {
            JsonNode sql = node.path("sqlConfig").isObject() ? node.path("sqlConfig") : node.path("databaseConfig");
            JsonNode databaseId = !sql.path("databaseId").isMissingNode()
                    ? sql.path("databaseId")
                    : sql.path("connectionId");
            if (databaseId.asLong(0L) <= 0L) {
                errors.add(path + ".databaseId must be > 0");
            }
        } else if ("api".equals(normalized)) {
            if (trimToNull(node.path("apiConfig").path("url").asText(null)) == null) {
                errors.add(path + ".apiConfig.url is required");
            }
        } else if ("metric".equals(normalized)) {
            if (node.path("metricConfig").path("cardId").asLong(0L) <= 0L) {
                errors.add(path + ".metricConfig.cardId is required for runtime execution");
            }
        } else if ("dataset".equals(normalized)) {
            JsonNode queryBody = node.path("datasetConfig").path("queryBody");
            if (!queryBody.isObject()) {
                errors.add(path + ".datasetConfig.queryBody must be object");
                return;
            }
            if (queryBody.path("database").asLong(0L) <= 0L) {
                errors.add(path + ".datasetConfig.queryBody.database must be > 0");
            }
            String queryType = queryBody.path("type").asText("query").trim().toLowerCase();
            if ("native".equals(queryType)) {
                if (trimToNull(queryBody.path("native").path("query").asText(null)) == null) {
                    errors.add(path + ".datasetConfig.queryBody.native.query is required");
                }
            } else if (!queryBody.path("query").isObject()) {
                errors.add(path + ".datasetConfig.queryBody.query must be object");
            }
        }
    }

    private void validateDrillActions(JsonNode actionsNode, String path, boolean hasExecutableDrillChain, List<String> errors) {
        if (actionsNode == null || actionsNode.isMissingNode() || actionsNode.isNull()) {
            return;
        }
        if (!actionsNode.isArray()) {
            errors.add(path + " must be array");
            return;
        }
        for (int i = 0; i < actionsNode.size(); i++) {
            JsonNode action = actionsNode.get(i);
            if (action == null || !action.isObject()) {
                errors.add(path + "[" + i + "] must be object");
                continue;
            }
            String actionType = trimToNull(action.path("type").asText(null));
            if (("drill-down".equals(actionType) || "drill-up".equals(actionType)) && !hasExecutableDrillChain) {
                errors.add(path + "[" + i + "] requires an enabled drillDown chain");
            }
        }
    }

    private void validateGlobalVariables(JsonNode variablesNode, List<String> errors) {
        if (variablesNode == null || variablesNode.isMissingNode() || variablesNode.isNull()) {
            return;
        }
        if (!variablesNode.isArray()) {
            errors.add("globalVariables must be array");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < variablesNode.size(); i++) {
            JsonNode item = variablesNode.get(i);
            String path = "globalVariables[" + i + "]";
            if (item == null || !item.isObject()) {
                errors.add(path + " must be object");
                continue;
            }
            String key = trimToNull(item.path("key").asText(null));
            if (key == null) {
                errors.add(path + ".key is required");
                continue;
            }
            if (!VARIABLE_KEY_PATTERN.matcher(key).matches()) {
                errors.add(path + ".key format invalid: " + key);
            }
            if (!seen.add(key)) {
                errors.add(path + ".key duplicated: " + key);
            }
            String varType = trimToNull(item.path("type").asText(null));
            if (varType != null && !VARIABLE_TYPES.contains(varType)) {
                errors.add(path + ".type invalid: " + varType);
            }
        }
    }

    private static boolean hasPluginMarker(JsonNode configNode) {
        if (configNode == null || !configNode.isObject()) {
            return false;
        }
        JsonNode pluginNode = configNode.path("__plugin");
        return pluginNode != null && pluginNode.isObject() && trimToNull(pluginNode.path("id").asText(null)) != null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ValidationResult(List<String> errors, List<String> warnings) {}
}
