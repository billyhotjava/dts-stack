package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAssetAuditLog;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenTemplateRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.ScreenAssetAuditService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screen-packs")
@Transactional
public class ScreenIndustryPackResource {

    private static final String PACKAGE_TYPE = "dts.industry-pack";
    private static final String SPEC_VERSION = "1.1";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenTemplateRepository screenTemplateRepository;
    private final ScreenAssetAuditService screenAssetAuditService;
    private final ObjectMapper objectMapper;

    public ScreenIndustryPackResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenTemplateRepository screenTemplateRepository,
            ScreenAssetAuditService screenAssetAuditService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenTemplateRepository = screenTemplateRepository;
        this.screenAssetAuditService = screenAssetAuditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/presets", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> presets(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        ObjectNode result = objectMapper.createObjectNode();
        result.set("industries", buildIndustryPresets());
        result.set("hardwareProfiles", buildHardwareProfiles());
        result.set("connectorTemplates", buildConnectorTemplates(null));
        result.set("deploymentModes", toStringArray("online", "offline", "isolated"));
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validatePack(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        ArrayNode errors = objectMapper.createArrayNode();
        ArrayNode warnings = objectMapper.createArrayNode();
        ArrayNode recommendations = objectMapper.createArrayNode();

        if (body == null || !body.isObject()) {
            errors.add("payload must be object");
        } else {
            String packageType = trimToNull(body.path("packageType").asText(null));
            if (packageType == null) {
                warnings.add("packageType is missing, recommended: " + PACKAGE_TYPE);
            } else if (!PACKAGE_TYPE.equals(packageType)) {
                errors.add("packageType must be " + PACKAGE_TYPE);
            }

            JsonNode templatesNode = body.path("templates");
            if (!templatesNode.isArray() || templatesNode.isEmpty()) {
                errors.add("templates must be a non-empty array");
            }

            JsonNode metadata = body.path("metadata");
            if (!metadata.isObject()) {
                warnings.add("metadata is missing, recommend providing industry/hardware/deployment profile");
            } else {
                String hardwareProfile = trimToNull(metadata.path("hardwareProfile").asText(null));
                String deploymentMode = trimToNull(metadata.path("deploymentMode").asText(null));
                if (hardwareProfile == null) {
                    warnings.add("metadata.hardwareProfile is missing");
                }
                if (deploymentMode == null) {
                    warnings.add("metadata.deploymentMode is missing");
                }
                JsonNode connectors = metadata.path("connectorTemplates");
                if (!connectors.isArray() || connectors.isEmpty()) {
                    warnings.add("metadata.connectorTemplates is empty");
                }
                JsonNode opsRunbook = metadata.path("opsRunbook");
                if (!opsRunbook.isObject()) {
                    warnings.add("metadata.opsRunbook is missing");
                }
            }
        }

        recommendations.add("For offline or isolated networks, set metadata.deploymentMode to offline/isolated.");
        recommendations.add("Include PLC/MQTT/OPC-UA connector templates for edge hardware projects.");
        recommendations.add("Add opsRunbook health checks and alert channels for on-site operations.");

        ObjectNode result = objectMapper.createObjectNode();
        result.put("valid", errors.isEmpty());
        result.set("errors", errors);
        result.set("warnings", warnings);
        result.set("recommendations", recommendations);
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> audit(
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }

        List<ObjectNode> rows = screenAssetAuditService.listRecent("industry_pack", limit).stream()
                .map(this::toAuditRow)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping(path = "/export", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> exportPack(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        Long actorId = user.get().getId();
        boolean superuser = user.get().isSuperuser();
        String source = trimToNull(body == null ? null : body.path("source").asText(null));
        if (source == null) {
            source = "manual";
        }
        Set<Long> includeIds = parseIdSet(body == null ? null : body.path("templateIds"));
        List<AnalyticsScreenTemplate> allTemplates = screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc();

        if (!superuser && !includeIds.isEmpty()) {
            boolean containsForbiddenTemplate = allTemplates.stream()
                    .filter(t -> includeIds.contains(t.getId()))
                    .anyMatch(t -> !actorId.equals(t.getCreatorId()));
            if (containsForbiddenTemplate) {
                screenAssetAuditService.log(
                        "industry_pack",
                        null,
                        actorId,
                        "pack.export",
                        source,
                        "rejected",
                        Map.of("reason", "selected templates contain entries without manage permission"),
                        requestIdFrom(request));
                return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
            }
        }

        List<AnalyticsScreenTemplate> templates = allTemplates.stream()
                .filter(t -> superuser || actorId.equals(t.getCreatorId()))
                .filter(t -> includeIds.isEmpty() || includeIds.contains(t.getId()))
                .toList();

        String industry = trimToNull(body == null ? null : body.path("industry").asText(null));
        if (industry == null) {
            industry = inferIndustry(templates);
        }
        String hardwareProfile = trimToNull(body == null ? null : body.path("hardwareProfile").asText(null));
        if (hardwareProfile == null) {
            hardwareProfile = "edge-box-standard";
        }
        String deploymentMode = trimToNull(body == null ? null : body.path("deploymentMode").asText(null));
        if (deploymentMode == null) {
            deploymentMode = "online";
        }
        boolean includeConnectorTemplates = body == null || !body.has("includeConnectorTemplates")
                || body.path("includeConnectorTemplates").asBoolean(true);
        ArrayNode requestedConnectorTypes = parseStringArray(body == null ? null : body.path("connectorTypes"));

        ObjectNode pack = objectMapper.createObjectNode();
        pack.put("packageType", PACKAGE_TYPE);
        pack.put("specVersion", SPEC_VERSION);
        pack.putPOJO("exportedAt", Instant.now());
        pack.put("exportedBy", String.valueOf(user.get().getId()));

        ArrayNode templatesNode = objectMapper.createArrayNode();
        for (AnalyticsScreenTemplate template : templates) {
            templatesNode.add(toTemplateNode(template));
        }
        pack.set("templates", templatesNode);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("industry", industry);
        metadata.put("hardwareProfile", hardwareProfile);
        metadata.put("deploymentMode", deploymentMode);
        metadata.set("resolution", resolveHardwareResolution(hardwareProfile));
        metadata.set("offlineBundle", buildOfflineBundle(deploymentMode));
        metadata.set("opsRunbook", buildOpsRunbook(industry));
        metadata.set("hardwarePreset", resolveHardwarePreset(hardwareProfile));
        metadata.set(
                "connectorTemplates",
                includeConnectorTemplates ? buildConnectorTemplates(requestedConnectorTypes) : objectMapper.createArrayNode());
        pack.set("metadata", metadata);

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("templateCount", templatesNode.size());
        summary.put("scope", includeIds.isEmpty() ? (superuser ? "all" : "owned") : "selected");
        summary.put("industry", industry);
        summary.put("hardwareProfile", hardwareProfile);
        summary.put("deploymentMode", deploymentMode);
        summary.put("connectorTemplateCount", metadata.path("connectorTemplates").size());
        pack.set("summary", summary);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("templateCount", templatesNode.size());
        details.put("scope", summary.path("scope").asText());
        details.put("selectedCount", includeIds.size());
        details.put("industry", industry);
        details.put("hardwareProfile", hardwareProfile);
        details.put("deploymentMode", deploymentMode);
        screenAssetAuditService.log(
                "industry_pack",
                null,
                actorId,
                "pack.export",
                source,
                "success",
                details,
                requestIdFrom(request));

        return ResponseEntity.ok(pack);
    }

    @PostMapping(path = "/import", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importPack(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        Long actorId = user.get().getId();
        boolean superuser = user.get().isSuperuser();
        String source = trimToNull(body == null ? null : body.path("source").asText(null));
        if (source == null) {
            source = "package";
        }

        if (body == null || !body.isObject()) {
            screenAssetAuditService.log(
                    "industry_pack",
                    null,
                    actorId,
                    "pack.import",
                    source,
                    "failed",
                    Map.of("reason", "invalid package payload"),
                    requestIdFrom(request));
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("invalid package payload");
        }

        String packageType = trimToNull(body.path("packageType").asText(null));
        if (packageType != null && !PACKAGE_TYPE.equals(packageType)) {
            screenAssetAuditService.log(
                    "industry_pack",
                    null,
                    actorId,
                    "pack.import",
                    source,
                    "failed",
                    Map.of("reason", "unsupported packageType", "packageType", packageType),
                    requestIdFrom(request));
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("unsupported packageType");
        }

        JsonNode templatesNode = body.path("templates");
        if (!templatesNode.isArray() || templatesNode.isEmpty()) {
            screenAssetAuditService.log(
                    "industry_pack",
                    null,
                    actorId,
                    "pack.import",
                    source,
                    "failed",
                    Map.of("reason", "templates is required"),
                    requestIdFrom(request));
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("templates is required");
        }

        Set<String> existingNames = new HashSet<>();
        screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().forEach(t -> {
            String normalized = normalizeName(t.getName());
            if (normalized != null) {
                existingNames.add(normalized);
            }
        });

        int imported = 0;
        int failed = 0;
        ArrayNode items = objectMapper.createArrayNode();

        for (JsonNode node : templatesNode) {
            ObjectNode item = objectMapper.createObjectNode();
            try {
                AnalyticsScreenTemplate entity = fromTemplateNode(
                        node,
                        actorId,
                        existingNames,
                        superuser,
                        body.path("metadata"));
                screenTemplateRepository.save(entity);
                imported++;
                item.put("status", "imported");
                item.put("name", entity.getName());
                item.putPOJO("id", entity.getId());
            } catch (Exception ex) {
                failed++;
                item.put("status", "failed");
                item.put("reason", ex.getMessage() == null ? "unknown" : ex.getMessage());
            }
            items.add(item);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("imported", imported);
        result.put("failed", failed);
        result.set("items", items);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("imported", imported);
        details.put("failed", failed);
        details.put("templateCount", templatesNode.size());
        details.put("industry", trimToNull(body.path("metadata").path("industry").asText(null)));
        details.put("hardwareProfile", trimToNull(body.path("metadata").path("hardwareProfile").asText(null)));
        details.put("deploymentMode", trimToNull(body.path("metadata").path("deploymentMode").asText(null)));
        screenAssetAuditService.log(
                "industry_pack",
                null,
                actorId,
                "pack.import",
                source,
                failed == 0 ? "success" : (imported == 0 ? "failed" : "partial"),
                details,
                requestIdFrom(request));
        return ResponseEntity.ok(result);
    }

    private ObjectNode toTemplateNode(AnalyticsScreenTemplate template) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", template.getName());
        node.put("description", template.getDescription());
        node.put("category", template.getCategory());
        node.put("thumbnail", template.getThumbnail());
        node.set("tags", parseArrayOrEmpty(template.getTagsJson()));
        node.put("visibilityScope", template.getVisibilityScope());
        node.put("listed", template.isListed());
        node.put("themePack", parseObjectOrEmpty(template.getThemePackJson()));

        ObjectNode config = objectMapper.createObjectNode();
        config.put("width", template.getWidth() == null ? 1920 : template.getWidth());
        config.put("height", template.getHeight() == null ? 1080 : template.getHeight());
        config.put("backgroundColor", template.getBackgroundColor());
        config.put("backgroundImage", template.getBackgroundImage());
        config.put("theme", template.getTheme());
        config.set("components", parseArrayOrEmpty(template.getComponentsJson()));
        config.set("globalVariables", parseArrayOrEmpty(template.getVariablesJson()));
        node.set("config", config);
        return node;
    }

    private String inferIndustry(List<AnalyticsScreenTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return "general";
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AnalyticsScreenTemplate template : templates) {
            String key = trimToNull(template == null ? null : template.getCategory());
            if (key == null) {
                continue;
            }
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }
        String best = "general";
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    private ArrayNode buildIndustryPresets() {
        ArrayNode array = objectMapper.createArrayNode();
        array.add(preset("discrete-manufacturing", "离散制造", "设备状态、产能、良率、告警闭环"));
        array.add(preset("energy-carbon", "能源双碳", "电/水/气/碳排指标与异常监控"));
        array.add(preset("gov-enterprise-security", "政企安全", "风险态势、事件分析、合规看板"));
        array.add(preset("campus-ops", "园区运维", "综合设施、安防、设备巡检与工单"));
        return array;
    }

    private ArrayNode buildHardwareProfiles() {
        ArrayNode array = objectMapper.createArrayNode();
        array.add(hardwarePreset("edge-box-standard", "边缘盒标准版", 1920, 1080, "linux-arm64", "single-screen"));
        array.add(hardwarePreset("ipc-dual-4k", "工控机双屏4K", 3840, 2160, "linux-amd64", "dual-screen"));
        array.add(hardwarePreset("wall-controller", "拼接屏控制器", 7680, 2160, "linux-amd64", "video-wall"));
        return array;
    }

    private ObjectNode resolveHardwarePreset(String hardwareProfile) {
        String profile = trimToNull(hardwareProfile);
        if (profile == null) {
            profile = "edge-box-standard";
        }
        for (JsonNode node : buildHardwareProfiles()) {
            if (profile.equalsIgnoreCase(node.path("id").asText(""))) {
                return (ObjectNode) node;
            }
        }
        return hardwarePreset(profile, profile, 1920, 1080, "linux-amd64", "single-screen");
    }

    private ObjectNode resolveHardwareResolution(String hardwareProfile) {
        JsonNode preset = resolveHardwarePreset(hardwareProfile);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("width", preset.path("width").asInt(1920));
        node.put("height", preset.path("height").asInt(1080));
        return node;
    }

    private ObjectNode buildOfflineBundle(String deploymentMode) {
        String mode = trimToNull(deploymentMode);
        boolean offline = "offline".equalsIgnoreCase(mode) || "isolated".equalsIgnoreCase(mode);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("enabled", offline);
        node.put("upgradeStrategy", offline ? "offline-tarball" : "registry-pull");
        node.put("integrityPolicy", "sha256");
        node.put("signatureRequired", true);
        return node;
    }

    private ObjectNode buildOpsRunbook(String industry) {
        ObjectNode node = objectMapper.createObjectNode();
        node.set("healthChecks", toStringArray(
                "screen render latency < 1.5s",
                "data query error rate < 2%",
                "edge connector heartbeat >= 1/min"));
        node.set("alertChannels", toStringArray("sms", "email", "wechat-work"));
        node.set("sop", toStringArray(
                "1) 检查网络与网关连通性",
                "2) 检查数据连接模板状态",
                "3) 回滚到上一个稳定行业包"));
        node.put("industryHint", trimToNull(industry) == null ? "general" : industry);
        return node;
    }

    private ArrayNode buildConnectorTemplates(ArrayNode requestedConnectorTypes) {
        Set<String> filter = new HashSet<>();
        if (requestedConnectorTypes != null) {
            for (JsonNode node : requestedConnectorTypes) {
                String type = trimToNull(node == null ? null : node.asText(null));
                if (type != null) {
                    filter.add(type.toLowerCase(Locale.ROOT));
                }
            }
        }
        ArrayNode array = objectMapper.createArrayNode();
        addConnectorTemplate(array, filter, "plc", "PLC 采集模板", "modbus-tcp", Map.of("pollIntervalMs", 1000));
        addConnectorTemplate(array, filter, "mqtt", "MQTT 边缘模板", "mqtt", Map.of("qos", 1, "topic", "factory/+/metrics"));
        addConnectorTemplate(array, filter, "opcua", "OPC-UA 采集模板", "opc-ua", Map.of("securityMode", "SignAndEncrypt"));
        addConnectorTemplate(array, filter, "postgresql", "PostgreSQL 模板", "jdbc", Map.of("schema", "public"));
        return array;
    }

    private void addConnectorTemplate(
            ArrayNode output,
            Set<String> filter,
            String id,
            String name,
            String protocol,
            Map<String, Object> defaults) {
        if (!filter.isEmpty() && !filter.contains(id.toLowerCase(Locale.ROOT))) {
            return;
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("name", name);
        node.put("protocol", protocol);
        node.putPOJO("defaults", defaults);
        output.add(node);
    }

    private ObjectNode preset(String id, String name, String description) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("name", name);
        node.put("description", description);
        return node;
    }

    private ObjectNode hardwarePreset(String id, String name, int width, int height, String os, String mode) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("name", name);
        node.put("width", width);
        node.put("height", height);
        node.put("os", os);
        node.put("displayMode", mode);
        return node;
    }

    private AnalyticsScreenTemplate fromTemplateNode(
            JsonNode node,
            Long creatorId,
            Set<String> existingNames,
            boolean superuser,
            JsonNode metadata) {
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException("template item must be object");
        }

        String baseName = trimToNull(node.path("name").asText(null));
        if (baseName == null) {
            baseName = "导入模板";
        }
        String finalName = dedupeName(baseName, existingNames);

        JsonNode config = node.path("config");

        AnalyticsScreenTemplate template = new AnalyticsScreenTemplate();
        template.setName(finalName);
        template.setDescription(node.path("description").isMissingNode() || node.path("description").isNull()
                ? null
                : node.path("description").asText(null));
        String category = trimToNull(node.path("category").asText(null));
        if (category == null) {
            category = "custom";
        }
        if (isProtectedCategory(category) && !superuser) {
            throw new IllegalArgumentException("protected category requires superuser");
        }
        template.setCategory(category);
        template.setThumbnail(node.path("thumbnail").isMissingNode() || node.path("thumbnail").isNull()
                ? "🧩"
                : node.path("thumbnail").asText());
        template.setTagsJson(node.path("tags").isArray() ? node.path("tags").toString() : "[]");
        template.setVisibilityScope(normalizeVisibilityScope(node.path("visibilityScope").asText("team")));
        template.setListed(!node.has("listed") || node.path("listed").asBoolean(true));
        template.setThemePackJson(node.path("themePack").isObject() ? node.path("themePack").toString() : null);

        template.setWidth(config.path("width").asInt(1920));
        template.setHeight(config.path("height").asInt(1080));
        template.setBackgroundColor(config.path("backgroundColor").isMissingNode() || config.path("backgroundColor").isNull()
                ? null
                : config.path("backgroundColor").asText(null));
        template.setBackgroundImage(config.path("backgroundImage").isMissingNode() || config.path("backgroundImage").isNull()
                ? null
                : config.path("backgroundImage").asText(null));
        template.setTheme(config.path("theme").isMissingNode() || config.path("theme").isNull()
                ? null
                : config.path("theme").asText(null));
        template.setComponentsJson(config.path("components").isArray() ? config.path("components").toString() : "[]");
        template.setVariablesJson(config.path("globalVariables").isArray() ? config.path("globalVariables").toString() : "[]");

        String industry = trimToNull(metadata == null ? null : metadata.path("industry").asText(null));
        String hardwareProfile = trimToNull(metadata == null ? null : metadata.path("hardwareProfile").asText(null));
        if (industry != null || hardwareProfile != null) {
            ArrayNode tags = parseArrayOrEmpty(template.getTagsJson());
            if (industry != null) {
                tags.add("industry:" + industry);
            }
            if (hardwareProfile != null) {
                tags.add("hardware:" + hardwareProfile);
            }
            template.setTagsJson(tags.toString());
        }

        template.setTemplateVersion(1);
        template.setCreatorId(creatorId);
        template.setArchived(false);
        return template;
    }

    private ArrayNode parseArrayOrEmpty(String json) {
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node != null && node.isArray()) {
                    return (ArrayNode) node;
                }
            } catch (Exception ignored) {
                return objectMapper.createArrayNode();
            }
        }
        return objectMapper.createArrayNode();
    }

    private ObjectNode parseObjectOrEmpty(String json) {
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node != null && node.isObject()) {
                    return (ObjectNode) node;
                }
            } catch (Exception ignored) {
                return objectMapper.createObjectNode();
            }
        }
        return objectMapper.createObjectNode();
    }

    private ArrayNode parseStringArray(JsonNode node) {
        ArrayNode result = objectMapper.createArrayNode();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item != null && item.isTextual()) {
                String v = trimToNull(item.asText(null));
                if (v != null) {
                    result.add(v);
                }
            }
        }
        return result;
    }

    private ArrayNode toStringArray(String... values) {
        ArrayNode array = objectMapper.createArrayNode();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null) {
                array.add(value);
            }
        }
        return array;
    }

    private Set<Long> parseIdSet(JsonNode node) {
        Set<Long> result = new HashSet<>();
        if (node == null || !node.isArray()) {
            return result;
        }
        for (JsonNode item : node) {
            if (item != null && item.canConvertToLong()) {
                result.add(item.asLong());
            }
        }
        return result;
    }

    private String dedupeName(String input, Set<String> existingNames) {
        String base = input.trim();
        String candidate = base;
        int seq = 1;
        while (existingNames.contains(normalizeName(candidate))) {
            candidate = base + " (导入" + seq + ")";
            seq++;
        }
        existingNames.add(normalizeName(candidate));
        return candidate;
    }

    private String normalizeName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String normalizeVisibilityScope(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return "team";
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("personal".equals(normalized) || "team".equals(normalized) || "global".equals(normalized)) {
            return normalized;
        }
        return "team";
    }

    private boolean isProtectedCategory(String category) {
        if (category == null) {
            return false;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return "builtin".equals(normalized) || "official".equals(normalized) || "industry".equals(normalized);
    }

    private ObjectNode toAuditRow(AnalyticsScreenAssetAuditLog log) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putPOJO("id", log.getId());
        node.put("assetType", log.getAssetType());
        node.putPOJO("assetId", log.getAssetId());
        node.put("action", log.getAction());
        node.putPOJO("actorId", log.getActorId());
        node.put("source", log.getSource());
        node.put("result", log.getResult());
        node.put("requestId", log.getRequestId());
        node.putPOJO("createdAt", log.getCreatedAt());
        if (log.getDetailsJson() != null && !log.getDetailsJson().isBlank()) {
            try {
                node.set("details", objectMapper.readTree(log.getDetailsJson()));
            } catch (Exception e) {
                node.put("details", log.getDetailsJson());
            }
        } else {
            node.set("details", objectMapper.createObjectNode());
        }
        return node;
    }

    private String requestIdFrom(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String rid = trimToNull(request.getHeader("X-Request-Id"));
        if (rid != null) {
            return rid;
        }
        return trimToNull((String) request.getAttribute("requestId"));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
