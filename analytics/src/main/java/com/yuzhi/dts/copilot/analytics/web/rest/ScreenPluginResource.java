package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screen-plugins")
@Transactional(readOnly = true)
public class ScreenPluginResource {

    private static final Pattern COMPONENT_TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    private final AnalyticsSessionService sessionService;
    private final ObjectMapper objectMapper;

    public ScreenPluginResource(AnalyticsSessionService sessionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        ArrayNode result = objectMapper.createArrayNode();
        result.add(buildDemoPlugin());
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validate(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        ArrayNode errors = objectMapper.createArrayNode();
        if (body == null || !body.isObject()) {
            errors.add("payload must be object");
        } else {
            validateStringRequired(body, "id", errors);
            validateStringRequired(body, "name", errors);
            validateStringRequired(body, "version", errors);

            JsonNode components = body.path("components");
            if (!components.isArray() || components.isEmpty()) {
                errors.add("components must be non-empty array");
            } else {
                Set<String> componentIds = new HashSet<>();
                for (JsonNode item : components) {
                    if (!item.isObject()) {
                        errors.add("component item must be object");
                        continue;
                    }
                    String cid = trimToNull(item.path("id").asText(null));
                    String cname = trimToNull(item.path("name").asText(null));
                    String baseType = trimToNull(item.path("baseType").asText(null));
                    if (cid == null) {
                        errors.add("component.id is required");
                    } else if (!componentIds.add(cid)) {
                        errors.add("duplicate component.id: " + cid);
                    }
                    if (cname == null) {
                        errors.add("component.name is required");
                    }
                    if (baseType == null) {
                        errors.add("component.baseType is required");
                    } else if (!COMPONENT_TYPE_PATTERN.matcher(baseType).matches()) {
                        errors.add("invalid component.baseType: " + baseType);
                    }
                    JsonNode propertySchema = item.path("propertySchema");
                    if (!propertySchema.isMissingNode() && !propertySchema.isNull() && !propertySchema.isObject()) {
                        errors.add("component.propertySchema must be object");
                    }
                    JsonNode dataContract = item.path("dataContract");
                    if (!dataContract.isMissingNode() && !dataContract.isNull() && !dataContract.isObject()) {
                        errors.add("component.dataContract must be object");
                    }
                }
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("valid", errors.isEmpty());
        result.set("errors", errors);
        return ResponseEntity.ok(result);
    }

    private ObjectNode buildDemoPlugin() {
        ObjectNode plugin = objectMapper.createObjectNode();
        plugin.put("id", "demo-stat-pack");
        plugin.put("name", "Demo 统计组件包");
        plugin.put("version", "1.0.0");
        plugin.put("enabled", true);
        plugin.put("signatureRequired", false);

        ArrayNode components = objectMapper.createArrayNode();

        ObjectNode kpiPro = objectMapper.createObjectNode();
        kpiPro.put("id", "kpi-card-pro");
        kpiPro.put("name", "KPI卡片-Pro");
        kpiPro.put("icon", "🧭");
        kpiPro.put("baseType", "number-card");
        kpiPro.put("defaultWidth", 280);
        kpiPro.put("defaultHeight", 110);
        kpiPro.set("defaultConfig", objectMapper.createObjectNode()
                .put("title", "KPI-Pro")
                .put("value", 1280)
                .put("prefix", "")
                .put("suffix", "")
                .put("titleColor", "#93c5fd")
                .put("valueColor", "#f8fafc")
                .put("backgroundColor", "rgba(2,132,199,0.2)")
                .put("valueFontSize", 34));
        kpiPro.set("propertySchema", objectMapper.createObjectNode()
                .put("version", "1.0")
                .set("fields", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("key", "title").put("label", "标题").put("type", "string"))
                        .add(objectMapper.createObjectNode().put("key", "value").put("label", "值").put("type", "number"))));
        kpiPro.set("dataContract", objectMapper.createObjectNode()
                .put("version", "1.0")
                .put("kind", "kv")
                .put("description", "value/title"));
        components.add(kpiPro);

        ObjectNode compactTrend = objectMapper.createObjectNode();
        compactTrend.put("id", "compact-trend");
        compactTrend.put("name", "紧凑趋势图");
        compactTrend.put("icon", "📉");
        compactTrend.put("baseType", "line-chart");
        compactTrend.put("defaultWidth", 460);
        compactTrend.put("defaultHeight", 240);
        ObjectNode compactTrendConfig = objectMapper.createObjectNode();
        compactTrendConfig.put("title", "趋势-插件版");
        compactTrendConfig.put("lineSmooth", true);
        compactTrendConfig.put("areaStyle", true);
        compactTrendConfig.put("displayMode", "area");
        compactTrendConfig.set("xAxisData", objectMapper.createArrayNode().add("Mon").add("Tue").add("Wed").add("Thu").add("Fri"));
        compactTrendConfig.set("series", objectMapper.createArrayNode().add(
                objectMapper.createObjectNode()
                        .put("name", "趋势")
                        .set("data", objectMapper.createArrayNode().add(22).add(31).add(27).add(36).add(40))));
        compactTrend.set("defaultConfig", compactTrendConfig);
        compactTrend.set("propertySchema", objectMapper.createObjectNode()
                .put("version", "1.0")
                .set("fields", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("key", "title").put("label", "标题").put("type", "string"))
                        .add(objectMapper.createObjectNode()
                                .put("key", "displayMode")
                                .put("label", "展示模式")
                                .put("type", "select")
                                .set("options", objectMapper.createArrayNode()
                                        .add(objectMapper.createObjectNode().put("label", "面积").put("value", "area"))
                                        .add(objectMapper.createObjectNode().put("label", "折线").put("value", "line"))))
                        .add(objectMapper.createObjectNode().put("key", "series").put("label", "序列").put("type", "array"))));
        compactTrend.set("dataContract", objectMapper.createObjectNode()
                .put("version", "1.0")
                .put("kind", "series")
                .put("description", "xAxisData/series"));
        components.add(compactTrend);

        ObjectNode tableMatrix = objectMapper.createObjectNode();
        tableMatrix.put("id", "table-matrix");
        tableMatrix.put("name", "矩阵表格");
        tableMatrix.put("icon", "🧮");
        tableMatrix.put("baseType", "table");
        tableMatrix.put("defaultWidth", 520);
        tableMatrix.put("defaultHeight", 260);
        ObjectNode tableConfig = objectMapper.createObjectNode();
        tableConfig.set("header", objectMapper.createArrayNode().add("维度").add("值A").add("值B"));
        tableConfig.set("data", objectMapper.createArrayNode()
                .add(objectMapper.createArrayNode().add("华北").add(120).add(80))
                .add(objectMapper.createArrayNode().add("华东").add(156).add(93))
                .add(objectMapper.createArrayNode().add("华南").add(99).add(75)));
        tableMatrix.set("defaultConfig", tableConfig);
        tableMatrix.set("propertySchema", objectMapper.createObjectNode()
                .put("version", "1.0")
                .set("fields", objectMapper.createArrayNode()
                        .add(objectMapper.createObjectNode().put("key", "header").put("label", "表头").put("type", "array"))
                        .add(objectMapper.createObjectNode().put("key", "data").put("label", "数据").put("type", "array"))));
        tableMatrix.set("dataContract", objectMapper.createObjectNode()
                .put("version", "1.0")
                .put("kind", "table")
                .put("description", "header/data"));
        components.add(tableMatrix);

        plugin.set("components", components);

        ArrayNode dataSources = objectMapper.createArrayNode();
        dataSources.add(objectMapper.createObjectNode()
                .put("id", "rest-json")
                .put("name", "REST JSON 数据源")
                .put("type", "api")
                .put("sdkVersion", "1.0"));
        plugin.set("dataSources", dataSources);

        return plugin;
    }

    private void validateStringRequired(JsonNode body, String field, ArrayNode errors) {
        String value = trimToNull(body.path(field).asText(null));
        if (value == null) {
            errors.add(field + " is required");
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
