package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAssetAuditLog;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenTemplateRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAssetAuditService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
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
                String industry = trimToNull(metadata.path("industry").asText(null));
                String hardwareProfile = trimToNull(metadata.path("hardwareProfile").asText(null));
                String deploymentMode = trimToNull(metadata.path("deploymentMode").asText(null));
                if (industry == null) {
                    warnings.add("metadata.industry is missing");
                } else if (!isKnownIndustry(industry)) {
                    warnings.add("metadata.industry is not in preset list: " + industry);
                    recommendations.add("Use /api/screen-packs/presets industries to align industry id.");
                }
                if (hardwareProfile == null) {
                    warnings.add("metadata.hardwareProfile is missing");
                } else if (!isKnownHardwareProfile(hardwareProfile)) {
                    warnings.add("metadata.hardwareProfile is not in preset list: " + hardwareProfile);
                }
                if (deploymentMode == null) {
                    warnings.add("metadata.deploymentMode is missing");
                } else if (!isKnownDeploymentMode(deploymentMode)) {
                    errors.add("metadata.deploymentMode must be online/offline/isolated");
                }
                JsonNode connectors = metadata.path("connectorTemplates");
                if (!connectors.isArray() || connectors.isEmpty()) {
                    warnings.add("metadata.connectorTemplates is empty");
                } else {
                    for (JsonNode connector : connectors) {
                        if (!connector.isObject()) {
                            warnings.add("metadata.connectorTemplates contains non-object item");
                            continue;
                        }
                        String connectorId = trimToNull(connector.path("id").asText(null));
                        String protocol = trimToNull(connector.path("protocol").asText(null));
                        if (connectorId == null) {
                            warnings.add("connector template item missing id");
                        }
                        if (protocol == null) {
                            warnings.add("connector template item missing protocol");
                        }
                    }
                }
                JsonNode opsRunbook = metadata.path("opsRunbook");
                if (!opsRunbook.isObject()) {
                    warnings.add("metadata.opsRunbook is missing");
                }
                JsonNode offlineBundle = metadata.path("offlineBundle");
                boolean offlineMode = "offline".equalsIgnoreCase(deploymentMode) || "isolated".equalsIgnoreCase(deploymentMode);
                if (offlineMode && (!offlineBundle.isObject() || !offlineBundle.path("enabled").asBoolean(false))) {
                    warnings.add("offline/isolated mode should include metadata.offlineBundle.enabled=true");
                }
                if (offlineMode && (!connectors.isArray() || connectors.isEmpty())) {
                    recommendations.add("Offline/isolated deployment should prepackage connectorTemplates for local ingestion.");
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

    @GetMapping(path = "/ops/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> opsHealth(
            @RequestParam(value = "deploymentMode", required = false) String deploymentModeRaw,
            @RequestParam(value = "includeRuntime", required = false, defaultValue = "false") boolean includeRuntime,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        String deploymentMode = trimToNull(deploymentModeRaw);
        if (deploymentMode == null) {
            deploymentMode = "online";
        }
        boolean offlineMode = "offline".equalsIgnoreCase(deploymentMode) || "isolated".equalsIgnoreCase(deploymentMode);

        List<AnalyticsScreenTemplate> templates = user.get().isSuperuser()
                ? screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc()
                : screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                        .filter(t -> user.get().getId().equals(t.getCreatorId()))
                        .toList();
        int total = templates.size();
        int listed = (int) templates.stream().filter(AnalyticsScreenTemplate::isListed).count();
        int unlisted = Math.max(0, total - listed);

        List<AnalyticsScreenAssetAuditLog> recentAudits = screenAssetAuditService.listRecent("industry_pack", 60);
        long failedCount = recentAudits.stream().filter(this::isAuditFailed).count();
        long successCount = recentAudits.stream().filter(this::isAuditSuccess).count();

        ArrayNode checks = objectMapper.createArrayNode();
        checks.add(checkItem(
                "templates",
                "模板资产数量",
                total > 0 ? "pass" : "fail",
                total > 0 ? "模板资产可用于站点初始化" : "未发现可用模板",
                Map.of("total", total)));
        checks.add(checkItem(
                "listed-ratio",
                "模板上架覆盖率",
                total == 0 ? "warn" : (listed * 1.0 / Math.max(1, total) >= 0.5 ? "pass" : "warn"),
                total == 0 ? "缺少模板上架数据" : ("已上架 " + listed + " / " + total),
                Map.of("listed", listed, "unlisted", unlisted)));
        checks.add(checkItem(
                "audit-failure-rate",
                "近期行业包审计稳定性",
                failedCount == 0 ? "pass" : (failedCount <= 2 ? "warn" : "fail"),
                failedCount == 0 ? "最近导入/导出链路稳定" : ("最近存在 " + failedCount + " 次失败/拒绝"),
                Map.of("failed", failedCount, "success", successCount)));
        checks.add(checkItem(
                "offline-connectors",
                "离线连接器预置",
                !offlineMode || buildConnectorTemplates(null).size() > 0 ? "pass" : "fail",
                !offlineMode ? "在线部署无需离线连接器预置"
                        : "离线模式建议校验 connectorTemplates 与采集任务草案",
                Map.of("deploymentMode", deploymentMode, "offlineMode", offlineMode)));
        if (includeRuntime) {
            RuntimeProbeSummary runtime = runRuntimeTargets(defaultRuntimeTargets(), 1200);
            String runtimeStatus = runtime.fail() > 0 ? "fail" : (runtime.warn() > 0 ? "warn" : "pass");
            checks.add(checkItem(
                    "runtime-connectivity",
                    "运行时依赖探测",
                    runtimeStatus,
                    runtime.fail() > 0
                            ? ("存在 " + runtime.fail() + " 个关键依赖不可达")
                            : ("通过 " + runtime.pass() + "，告警 " + runtime.warn()),
                    Map.of(
                            "total", runtime.total(),
                            "pass", runtime.pass(),
                            "warn", runtime.warn(),
                            "fail", runtime.fail(),
                            "timeoutMs", runtime.timeoutMs())));
        }

        int score = 0;
        for (JsonNode node : checks) {
            String status = node.path("status").asText("");
            if ("pass".equals(status)) {
                score += 25;
            } else if ("warn".equals(status)) {
                score += 15;
            }
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("score", score);
        summary.put("deploymentMode", deploymentMode);
        summary.put("templateCount", total);
        summary.put("listedCount", listed);
        summary.put("auditSamples", recentAudits.size());
        summary.put("failedAudits", failedCount);
        summary.put("runtimeIncluded", includeRuntime);

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", Instant.now());
        result.set("summary", summary);
        result.set("checks", checks);
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/ops/runtime-probe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> runtimeProbe(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        String source = trimToNull(body == null ? null : body.path("source").asText(null));
        if (source == null) {
            source = "manual";
        }
        int timeoutMs = body == null ? 1500 : body.path("timeoutMs").asInt(1500);
        int safeTimeoutMs = Math.max(300, Math.min(timeoutMs, 5000));

        List<RuntimeTarget> targets = parseRuntimeTargets(body == null ? null : body.path("targets"));
        if (targets.isEmpty()) {
            targets = defaultRuntimeTargets();
        }

        RuntimeProbeSummary runtime = runRuntimeTargets(targets, safeTimeoutMs);
        ArrayNode rows = runtime.rows();
        int pass = runtime.pass();
        int warn = runtime.warn();
        int fail = runtime.fail();

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("total", rows.size());
        summary.put("pass", pass);
        summary.put("warn", warn);
        summary.put("fail", fail);
        summary.put("timeoutMs", runtime.timeoutMs());

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", Instant.now());
        result.set("summary", summary);
        result.set("rows", rows);

        screenAssetAuditService.log(
                "industry_pack",
                null,
                user.get().getId(),
                "pack.ops-runtime-probe",
                source,
                fail > 0 ? "partial" : "success",
                Map.of("total", rows.size(), "pass", pass, "warn", warn, "fail", fail, "timeoutMs", runtime.timeoutMs()),
                requestIdFrom(request));

        return ResponseEntity.ok(result);
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

    @PostMapping(path = "/connectors/plan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generateConnectorPlan(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        String source = trimToNull(body == null ? null : body.path("source").asText(null));
        if (source == null) {
            source = "manual";
        }

        JsonNode connectorsNode = body == null ? null : body.path("connectorTemplates");
        if (connectorsNode == null || connectorsNode.isMissingNode() || connectorsNode.isNull()) {
            connectorsNode = body == null ? null : body.path("metadata").path("connectorTemplates");
        }
        if (connectorsNode == null || !connectorsNode.isArray() || connectorsNode.isEmpty()) {
            connectorsNode = buildConnectorTemplates(null);
        }

        ArrayNode plans = objectMapper.createArrayNode();
        int seq = 1;
        for (JsonNode connector : connectorsNode) {
            if (connector == null || !connector.isObject()) {
                continue;
            }
            String connectorId = trimToNull(connector.path("id").asText(null));
            if (connectorId == null) {
                connectorId = "connector-" + seq;
            }
            String protocol = trimToNull(connector.path("protocol").asText(null));
            if (protocol == null) {
                protocol = "generic";
            }

            ObjectNode item = objectMapper.createObjectNode();
            item.put("jobId", "ingestion-" + connectorId + "-" + seq);
            item.put("connectorId", connectorId);
            item.put("protocol", protocol);
            item.put("service", "dts-ingestion");
            item.put("mode", resolveConnectorMode(protocol));
            item.put("schedule", resolveConnectorSchedule(protocol));
            item.set("taskConfig", buildConnectorTaskConfig(protocol, connector.path("defaults")));
            plans.add(item);
            seq++;
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", Instant.now());
        result.put("templateCount", connectorsNode.size());
        result.put("jobCount", plans.size());
        result.set("items", plans);

        screenAssetAuditService.log(
                "industry_pack",
                null,
                user.get().getId(),
                "pack.connector-plan",
                source,
                "success",
                Map.of("templateCount", connectorsNode.size(), "jobCount", plans.size()),
                requestIdFrom(request));

        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/connectors/probe", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> probeConnectors(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        String source = trimToNull(body == null ? null : body.path("source").asText(null));
        if (source == null) {
            source = "manual";
        }
        int timeoutMs = body == null ? 1500 : body.path("timeoutMs").asInt(1500);
        int safeTimeoutMs = Math.max(300, Math.min(timeoutMs, 5000));

        JsonNode connectorsNode = body == null ? null : body.path("connectorTemplates");
        if (connectorsNode == null || connectorsNode.isMissingNode() || connectorsNode.isNull()) {
            connectorsNode = body == null ? null : body.path("metadata").path("connectorTemplates");
        }
        if (connectorsNode == null || !connectorsNode.isArray() || connectorsNode.isEmpty()) {
            connectorsNode = buildConnectorTemplates(null);
        }

        ArrayNode rows = objectMapper.createArrayNode();
        int pass = 0;
        int warn = 0;
        int fail = 0;
        for (JsonNode connector : connectorsNode) {
            if (connector == null || !connector.isObject()) {
                continue;
            }
            String connectorId = trimToNull(connector.path("id").asText(null));
            if (connectorId == null) {
                connectorId = "connector";
            }
            String protocol = trimToNull(connector.path("protocol").asText(null));
            if (protocol == null) {
                protocol = "generic";
            }

            ObjectNode taskConfig = buildConnectorTaskConfig(protocol, connector.path("defaults"));
            List<ProbeTarget> targets = resolveProbeTargets(protocol, taskConfig);
            ObjectNode row = objectMapper.createObjectNode();
            row.put("connectorId", connectorId);
            row.put("protocol", protocol);

            if (targets.isEmpty()) {
                row.put("status", "warn");
                row.put("message", "no host/port endpoint found");
                row.set("targets", objectMapper.createArrayNode());
                warn++;
                rows.add(row);
                continue;
            }

            ArrayNode targetResults = objectMapper.createArrayNode();
            boolean allPass = true;
            for (ProbeTarget target : targets) {
                long started = System.currentTimeMillis();
                String status = "pass";
                String message = "ok";
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new InetSocketAddress(target.host, target.port), safeTimeoutMs);
                } catch (Exception ex) {
                    status = "fail";
                    message = rootMessage(ex);
                    allPass = false;
                }
                ObjectNode targetResult = objectMapper.createObjectNode();
                targetResult.put("label", target.label);
                targetResult.put("host", target.host);
                targetResult.put("port", target.port);
                targetResult.put("status", status);
                targetResult.put("message", message);
                targetResult.put("latencyMs", Math.max(0, System.currentTimeMillis() - started));
                targetResults.add(targetResult);
            }

            row.set("targets", targetResults);
            row.put("status", allPass ? "pass" : "fail");
            row.put("message", allPass ? "all targets reachable" : "some targets unreachable");
            if (allPass) {
                pass++;
            } else {
                fail++;
            }
            rows.add(row);
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("total", rows.size());
        summary.put("pass", pass);
        summary.put("warn", warn);
        summary.put("fail", fail);
        summary.put("timeoutMs", safeTimeoutMs);

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", Instant.now());
        result.set("summary", summary);
        result.set("rows", rows);

        screenAssetAuditService.log(
                "industry_pack",
                null,
                user.get().getId(),
                "pack.connector-probe",
                source,
                fail > 0 ? "partial" : "success",
                Map.of("total", rows.size(), "pass", pass, "warn", warn, "fail", fail, "timeoutMs", safeTimeoutMs),
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

    private String resolveConnectorMode(String protocol) {
        String normalized = trimToNull(protocol);
        if (normalized == null) {
            return "batch";
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        if ("mqtt".equals(value) || "opc-ua".equals(value) || "modbus-tcp".equals(value)) {
            return "stream";
        }
        return "batch";
    }

    private String resolveConnectorSchedule(String protocol) {
        String normalized = trimToNull(protocol);
        if (normalized == null) {
            return "*/5 * * * *";
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        if ("mqtt".equals(value)) {
            return "@continuous";
        }
        if ("opc-ua".equals(value) || "modbus-tcp".equals(value)) {
            return "*/1 * * * *";
        }
        return "*/5 * * * *";
    }

    private ObjectNode buildConnectorTaskConfig(String protocol, JsonNode defaultsNode) {
        ObjectNode config = objectMapper.createObjectNode();
        if (defaultsNode != null && defaultsNode.isObject()) {
            config.setAll((ObjectNode) defaultsNode.deepCopy());
        }
        String normalized = trimToNull(protocol);
        if (normalized == null) {
            config.put("taskType", "generic");
            return config;
        }
        String value = normalized.toLowerCase(Locale.ROOT);
        switch (value) {
            case "modbus-tcp" -> {
                config.put("taskType", "plc-modbus-polling");
                if (!config.has("pollIntervalMs")) {
                    config.put("pollIntervalMs", 1000);
                }
                if (!config.has("host")) {
                    config.put("host", "127.0.0.1");
                }
                if (!config.has("port")) {
                    config.put("port", 502);
                }
            }
            case "mqtt" -> {
                config.put("taskType", "mqtt-subscription");
                if (!config.has("brokerUrl")) {
                    config.put("brokerUrl", "tcp://127.0.0.1:1883");
                }
                if (!config.has("qos")) {
                    config.put("qos", 1);
                }
                if (!config.has("topic")) {
                    config.put("topic", "factory/+/metrics");
                }
            }
            case "opc-ua" -> {
                config.put("taskType", "opcua-subscription");
                if (!config.has("endpoint")) {
                    config.put("endpoint", "opc.tcp://127.0.0.1:4840");
                }
                if (!config.has("securityMode")) {
                    config.put("securityMode", "SignAndEncrypt");
                }
            }
            case "jdbc" -> {
                config.put("taskType", "jdbc-incremental");
                if (!config.has("jdbcUrl")) {
                    config.put("jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/postgres");
                }
                if (!config.has("query")) {
                    config.put("query", "select * from public.demo limit 1000");
                }
            }
            default -> config.put("taskType", "generic");
        }
        return config;
    }

    private List<ProbeTarget> resolveProbeTargets(String protocol, JsonNode config) {
        List<ProbeTarget> targets = new ArrayList<>();
        String normalized = trimToNull(protocol);
        String value = normalized == null ? "generic" : normalized.toLowerCase(Locale.ROOT);
        if ("modbus-tcp".equals(value)) {
            String host = trimToNull(config.path("host").asText(null));
            int port = config.path("port").asInt(502);
            if (host != null && port > 0) {
                targets.add(new ProbeTarget("modbus", host, port));
            }
            return targets;
        }
        if ("mqtt".equals(value)) {
            ProbeTarget target = parseTargetFromUri(trimToNull(config.path("brokerUrl").asText(null)), 1883, "mqtt");
            if (target != null) {
                targets.add(target);
            }
            return targets;
        }
        if ("opc-ua".equals(value)) {
            ProbeTarget target = parseTargetFromUri(trimToNull(config.path("endpoint").asText(null)), 4840, "opc-ua");
            if (target != null) {
                targets.add(target);
            }
            return targets;
        }
        if ("jdbc".equals(value)) {
            ProbeTarget target = parseJdbcTarget(trimToNull(config.path("jdbcUrl").asText(null)));
            if (target != null) {
                targets.add(target);
            }
            return targets;
        }
        String host = trimToNull(config.path("host").asText(null));
        int port = config.path("port").asInt(0);
        if (host != null && port > 0) {
            targets.add(new ProbeTarget("generic", host, port));
        }
        return targets;
    }

    private ProbeTarget parseTargetFromUri(String raw, int defaultPort, String label) {
        if (raw == null) {
            return null;
        }
        try {
            URI uri = URI.create(raw);
            String host = trimToNull(uri.getHost());
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            if (host != null && port > 0) {
                return new ProbeTarget(label, host, port);
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private ProbeTarget parseJdbcTarget(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String value = jdbcUrl.trim();
        if (!value.startsWith("jdbc:")) {
            return null;
        }
        String uriPart = value.substring(5);
        int defaultPort = 5432;
        if (uriPart.startsWith("mysql://")) {
            defaultPort = 3306;
        } else if (uriPart.startsWith("sqlserver://")) {
            defaultPort = 1433;
        } else if (uriPart.startsWith("oracle:thin:@")) {
            String hostPort = uriPart.substring("oracle:thin:@".length());
            String host = hostPort;
            int port = 1521;
            int slashIdx = hostPort.indexOf('/');
            if (slashIdx >= 0) {
                host = hostPort.substring(0, slashIdx);
            }
            int colonIdx = host.indexOf(':');
            if (colonIdx >= 0) {
                String rawHost = host.substring(0, colonIdx);
                int parsedPort = parsePort(host.substring(colonIdx + 1), 1521);
                if (rawHost.isBlank()) {
                    return null;
                }
                return new ProbeTarget("jdbc", rawHost, parsedPort);
            }
            return new ProbeTarget("jdbc", host, port);
        }
        try {
            URI uri = URI.create(uriPart);
            String host = trimToNull(uri.getHost());
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort;
            if (host != null && port > 0) {
                return new ProbeTarget("jdbc", host, port);
            }
        } catch (Exception ignore) {
            // fallback below
        }

        int prefixIdx = uriPart.indexOf("://");
        if (prefixIdx < 0) {
            return null;
        }
        String remainder = uriPart.substring(prefixIdx + 3);
        int slashIdx = remainder.indexOf('/');
        String hostPort = slashIdx >= 0 ? remainder.substring(0, slashIdx) : remainder;
        hostPort = URLDecoder.decode(hostPort, StandardCharsets.UTF_8);
        int colonIdx = hostPort.lastIndexOf(':');
        if (colonIdx > 0) {
            String host = hostPort.substring(0, colonIdx).trim();
            int port = parsePort(hostPort.substring(colonIdx + 1), defaultPort);
            if (!host.isBlank() && port > 0) {
                return new ProbeTarget("jdbc", host, port);
            }
        } else if (!hostPort.isBlank()) {
            return new ProbeTarget("jdbc", hostPort.trim(), defaultPort);
        }
        return null;
    }

    private int parsePort(String raw, int fallback) {
        String text = trimToNull(raw);
        if (text == null) {
            return fallback;
        }
        try {
            int n = Integer.parseInt(text);
            return n > 0 ? n : fallback;
        } catch (NumberFormatException ignore) {
            return fallback;
        }
    }

    private List<RuntimeTarget> parseRuntimeTargets(JsonNode node) {
        List<RuntimeTarget> targets = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return targets;
        }
        int index = 1;
        for (JsonNode item : node) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String host = trimToNull(item.path("host").asText(null));
            int port = item.path("port").asInt(0);
            String id = trimToNull(item.path("id").asText(null));
            if (id == null) {
                id = "target-" + index;
            }
            String name = trimToNull(item.path("name").asText(null));
            if (name == null) {
                name = id;
            }
            String url = trimToNull(item.path("url").asText(null));
            String protocol = normalizeRuntimeProtocol(trimToNull(item.path("protocol").asText(null)));
            if (protocol == null && url != null) {
                RuntimeUrlParts inferred = parseRuntimeUrlParts(url, null);
                if (inferred != null) {
                    protocol = inferred.protocol();
                }
            }
            if (protocol == null) {
                protocol = "tcp";
            }
            String path = trimToNull(item.path("path").asText(null));
            if (url != null && ("http".equals(protocol) || "https".equals(protocol))) {
                RuntimeUrlParts urlParts = parseRuntimeUrlParts(url, protocol);
                if (urlParts != null) {
                    if (host == null) {
                        host = urlParts.host();
                    }
                    if (port <= 0) {
                        port = urlParts.port();
                    }
                    if (path == null) {
                        path = urlParts.path();
                    }
                    protocol = urlParts.protocol();
                }
            }
            boolean required = !item.has("required") || item.path("required").asBoolean(true);
            String expectedBodyContains = trimToNull(item.path("expectedBodyContains").asText(null));
            if (host == null || port <= 0) {
                continue;
            }
            int[] expectedStatusRange = parseExpectedStatusRange(item.path("expectedStatus"), 200, 499);
            if ("http".equals(protocol) || "https".equals(protocol)) {
                if (path == null) {
                    path = "/";
                }
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                targets.add(new RuntimeTarget(
                        id,
                        name,
                        host,
                        port,
                        required,
                        protocol,
                        path,
                        expectedStatusRange[0],
                        expectedStatusRange[1],
                        expectedBodyContains));
            } else {
                String socketProtocol = "mqtt".equals(protocol) ? "mqtt" : "tcp";
                targets.add(new RuntimeTarget(id, name, host, port, required, socketProtocol, null, 0, 0, null));
            }
            index++;
        }
        return targets;
    }

    private List<RuntimeTarget> defaultRuntimeTargets() {
        List<RuntimeTarget> targets = new ArrayList<>();
        targets.add(new RuntimeTarget(
                "analytics-api",
                "Analytics API",
                envOrDefault("DTS_ANALYTICS_HOST", "127.0.0.1"),
                envOrDefaultPort("DTS_ANALYTICS_PORT", 3000),
                true,
                "http",
                envOrDefault("DTS_ANALYTICS_HTTP_PATH", "/analytics"),
                200,
                499,
                trimToNull(envOrDefault("DTS_ANALYTICS_HTTP_BODY_CONTAINS", null))));
        targets.add(new RuntimeTarget(
                "platform-api",
                "Platform API",
                envOrDefault("DTS_PLATFORM_HOST", "dts-platform"),
                envOrDefaultPort("DTS_PLATFORM_PORT", 8080),
                false,
                "http",
                envOrDefault("DTS_PLATFORM_HTTP_PATH", "/"),
                200,
                499,
                trimToNull(envOrDefault("DTS_PLATFORM_HTTP_BODY_CONTAINS", null))));
        targets.add(new RuntimeTarget(
                "ingestion-api",
                "Ingestion API",
                envOrDefault("DTS_INGESTION_HOST", "dts-ingestion"),
                envOrDefaultPort("DTS_INGESTION_PORT", 8080),
                false,
                "http",
                envOrDefault("DTS_INGESTION_HTTP_PATH", "/"),
                200,
                499,
                trimToNull(envOrDefault("DTS_INGESTION_HTTP_BODY_CONTAINS", null))));
        targets.add(new RuntimeTarget(
                "connector-task-status",
                "Connector Task Status API",
                envOrDefault("DTS_CONNECTOR_STATUS_HOST", envOrDefault("DTS_INGESTION_HOST", "dts-ingestion")),
                envOrDefaultPort("DTS_CONNECTOR_STATUS_PORT", envOrDefaultPort("DTS_INGESTION_PORT", 8080)),
                false,
                "http",
                envOrDefault("DTS_CONNECTOR_STATUS_HTTP_PATH", "/api/connector/tasks/status"),
                200,
                499,
                trimToNull(envOrDefault("DTS_CONNECTOR_STATUS_HTTP_BODY_CONTAINS", null))));
        targets.add(new RuntimeTarget(
                "analytics-db",
                "Analytics DB",
                envOrDefault("DTS_ANALYTICS_DB_HOST", "dts-postgresql"),
                envOrDefaultPort("DTS_ANALYTICS_DB_PORT", 5432),
                true,
                "tcp",
                null,
                0,
                0,
                null));
        String mqttHost = trimToNull(System.getenv("DTS_MQTT_HOST"));
        if (mqttHost != null) {
            targets.add(new RuntimeTarget(
                    "edge-mqtt",
                    "Edge MQTT",
                    mqttHost,
                    envOrDefaultPort("DTS_MQTT_PORT", 1883),
                    false,
                    "mqtt",
                    null,
                    0,
                    0,
                    null));
        }
        return targets;
    }

    private RuntimeProbeOutcome probeRuntimeTarget(RuntimeTarget target, int timeoutMs) {
        long started = System.currentTimeMillis();
        String status = "pass";
        String message = "ok";
        Integer httpStatus = null;
        String url = null;
        String bodyPreview = null;
        Boolean bodyMatched = null;
        try {
            if ("http".equals(target.protocol()) || "https".equals(target.protocol())) {
                String path = target.path() == null ? "/" : target.path();
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                URL endpoint = new URL(target.protocol(), target.host(), target.port(), path);
                url = endpoint.toString();
                HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
                connection.setConnectTimeout(timeoutMs);
                connection.setReadTimeout(timeoutMs);
                connection.setRequestMethod("GET");
                connection.setInstanceFollowRedirects(false);
                connection.connect();
                httpStatus = connection.getResponseCode();
                bodyPreview = readHttpBodyPreview(connection, 1200);
                List<String> issues = new ArrayList<>();
                if (httpStatus < target.expectedStatusMin() || httpStatus > target.expectedStatusMax()) {
                    issues.add("unexpected http status " + httpStatus);
                }
                String expectedBodyContains = trimToNull(target.expectedBodyContains());
                if (expectedBodyContains != null) {
                    bodyMatched = containsIgnoreCase(bodyPreview, expectedBodyContains);
                    if (!bodyMatched) {
                        issues.add("response body missing expected text: " + expectedBodyContains);
                    }
                }
                if (!issues.isEmpty()) {
                    status = target.required() ? "fail" : "warn";
                    message = String.join("; ", issues);
                }
            } else if ("mqtt".equals(target.protocol())) {
                url = "mqtt://" + target.host() + ":" + target.port();
                message = probeMqttConnect(target, timeoutMs);
            } else {
                try (java.net.Socket socket = new java.net.Socket()) {
                    socket.connect(new InetSocketAddress(target.host(), target.port()), timeoutMs);
                }
            }
        } catch (Exception ex) {
            status = target.required() ? "fail" : "warn";
            message = rootMessage(ex);
        }
        long latencyMs = Math.max(0, System.currentTimeMillis() - started);
        return new RuntimeProbeOutcome(status, message, latencyMs, httpStatus, url, bodyPreview, bodyMatched);
    }

    private String probeMqttConnect(RuntimeTarget target, int timeoutMs) throws Exception {
        String clientId = "dts-probe-" + Math.abs((target.host() + ":" + target.port()).hashCode());
        byte[] packet = buildMqttConnectPacket(clientId);
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new InetSocketAddress(target.host(), target.port()), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(packet);
            out.flush();
            byte[] connack = in.readNBytes(4);
            if (connack.length < 4) {
                throw new IllegalStateException("mqtt connack timeout");
            }
            int packetType = connack[0] & 0xFF;
            int remainingLength = connack[1] & 0xFF;
            int returnCode = connack[3] & 0xFF;
            if (packetType != 0x20 || remainingLength != 0x02) {
                throw new IllegalStateException("unexpected mqtt connack packet");
            }
            if (returnCode != 0) {
                throw new IllegalStateException("mqtt connack returnCode=" + returnCode);
            }
            out.write(new byte[] {(byte) 0xC0, 0x00});
            out.flush();
            byte[] pingResp = in.readNBytes(2);
            if (pingResp.length < 2 || (pingResp[0] & 0xFF) != 0xD0 || (pingResp[1] & 0xFF) != 0x00) {
                throw new IllegalStateException("mqtt heartbeat (pingresp) timeout");
            }
            return "mqtt connack + heartbeat ok";
        }
    }

    private byte[] buildMqttConnectPacket(String clientId) {
        byte[] clientBytes = clientId.getBytes(StandardCharsets.UTF_8);
        int remainingLength = 10 + 2 + clientBytes.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x10);
        writeMqttRemainingLength(out, remainingLength);
        out.write(0x00);
        out.write(0x04);
        out.write('M');
        out.write('Q');
        out.write('T');
        out.write('T');
        out.write(0x04);
        out.write(0x02);
        out.write(0x00);
        out.write(0x0A);
        out.write((clientBytes.length >> 8) & 0xFF);
        out.write(clientBytes.length & 0xFF);
        out.writeBytes(clientBytes);
        return out.toByteArray();
    }

    private void writeMqttRemainingLength(ByteArrayOutputStream out, int value) {
        int remaining = Math.max(0, value);
        do {
            int digit = remaining % 128;
            remaining = remaining / 128;
            if (remaining > 0) {
                digit = digit | 0x80;
            }
            out.write(digit);
        } while (remaining > 0);
    }

    private RuntimeProbeSummary runRuntimeTargets(List<RuntimeTarget> targets, int timeoutMs) {
        ArrayNode rows = objectMapper.createArrayNode();
        int pass = 0;
        int warn = 0;
        int fail = 0;
        int safeTimeoutMs = Math.max(300, Math.min(timeoutMs, 5000));
        for (RuntimeTarget target : targets) {
            RuntimeProbeOutcome outcome = probeRuntimeTarget(target, safeTimeoutMs);
            String status = outcome.status();
            if ("pass".equals(status)) {
                pass++;
            } else if ("warn".equals(status)) {
                warn++;
            } else {
                fail++;
            }

            ObjectNode row = objectMapper.createObjectNode();
            row.put("id", target.id());
            row.put("name", target.name());
            row.put("host", target.host());
            row.put("port", target.port());
            row.put("required", target.required());
            row.put("protocol", target.protocol());
            if (target.path() != null) {
                row.put("path", target.path());
            } else {
                row.putNull("path");
            }
            if (target.expectedBodyContains() != null) {
                row.put("expectedBodyContains", target.expectedBodyContains());
            } else {
                row.putNull("expectedBodyContains");
            }
            if (outcome.url() != null) {
                row.put("url", outcome.url());
            } else {
                row.putNull("url");
            }
            if (outcome.httpStatus() != null) {
                row.put("httpStatus", outcome.httpStatus());
            } else {
                row.putNull("httpStatus");
            }
            if (outcome.bodyMatched() != null) {
                row.put("bodyMatched", outcome.bodyMatched());
            } else {
                row.putNull("bodyMatched");
            }
            if (outcome.bodyPreview() != null) {
                row.put("bodyPreview", outcome.bodyPreview());
            } else {
                row.putNull("bodyPreview");
            }
            row.put("status", status);
            row.put("message", outcome.message());
            row.put("latencyMs", outcome.latencyMs());
            rows.add(row);
        }
        return new RuntimeProbeSummary(rows, rows.size(), pass, warn, fail, safeTimeoutMs);
    }

    private String normalizeRuntimeProtocol(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        if ("http".equals(normalized) || "https".equals(normalized) || "tcp".equals(normalized) || "mqtt".equals(normalized)) {
            return normalized;
        }
        return "tcp";
    }

    private RuntimeUrlParts parseRuntimeUrlParts(String url, String fallbackProtocol) {
        try {
            URL parsed = new URL(url);
            String protocol = normalizeRuntimeProtocol(parsed.getProtocol());
            if (protocol == null || "tcp".equals(protocol)) {
                String fallback = normalizeRuntimeProtocol(fallbackProtocol);
                protocol = (fallback == null || "tcp".equals(fallback)) ? "http" : fallback;
            }
            String host = trimToNull(parsed.getHost());
            if (host == null) {
                return null;
            }
            int defaultPort = "https".equals(protocol) ? 443 : 80;
            int port = parsed.getPort() > 0 ? parsed.getPort() : defaultPort;
            String path = trimToNull(parsed.getPath());
            if (path == null) {
                path = "/";
            }
            String query = trimToNull(parsed.getQuery());
            if (query != null) {
                path = path + "?" + query;
            }
            return new RuntimeUrlParts(protocol, host, port, path);
        } catch (Exception ex) {
            return null;
        }
    }

    private int[] parseExpectedStatusRange(JsonNode node, int defaultMin, int defaultMax) {
        int min = defaultMin;
        int max = defaultMax;
        if (node == null || node.isNull()) {
            return new int[] {min, max};
        }
        if (node.isNumber()) {
            int code = node.asInt(defaultMin);
            code = Math.max(100, Math.min(599, code));
            return new int[] {code, code};
        }
        String text = trimToNull(node.asText(null));
        if (text == null) {
            return new int[] {min, max};
        }
        int dashIdx = text.indexOf('-');
        if (dashIdx > 0) {
            String left = text.substring(0, dashIdx).trim();
            String right = text.substring(dashIdx + 1).trim();
            int parsedMin = parsePort(left, min);
            int parsedMax = parsePort(right, max);
            min = Math.max(100, Math.min(599, Math.min(parsedMin, parsedMax)));
            max = Math.max(100, Math.min(599, Math.max(parsedMin, parsedMax)));
            return new int[] {min, max};
        }
        int code = parsePort(text, min);
        code = Math.max(100, Math.min(599, code));
        return new int[] {code, code};
    }

    private String readHttpBodyPreview(HttpURLConnection connection, int maxBytes) {
        if (connection == null || maxBytes <= 0) {
            return null;
        }
        InputStream stream = null;
        try {
            int status = connection.getResponseCode();
            stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                return null;
            }
            byte[] bytes = stream.readNBytes(maxBytes);
            if (bytes.length == 0) {
                return "";
            }
            return sanitizeBodyPreview(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return null;
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (Exception ignore) {
                    // ignore close exception
                }
            }
        }
    }

    private String sanitizeBodyPreview(String body) {
        String value = trimToNull(body);
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 240) {
            return compact;
        }
        return compact.substring(0, 240) + "...";
    }

    private boolean containsIgnoreCase(String source, String expected) {
        String left = trimToNull(source);
        String right = trimToNull(expected);
        if (left == null || right == null) {
            return false;
        }
        return left.toLowerCase(Locale.ROOT).contains(right.toLowerCase(Locale.ROOT));
    }

    private String envOrDefault(String key, String fallback) {
        String value = trimToNull(System.getenv(key));
        return value == null ? fallback : value;
    }

    private int envOrDefaultPort(String key, int fallback) {
        String value = trimToNull(System.getenv(key));
        if (value == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown";
        }
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = trimToNull(current.getMessage());
        if (message != null) {
            return message;
        }
        return current.getClass().getSimpleName();
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

    private boolean isKnownIndustry(String industry) {
        if (industry == null) {
            return false;
        }
        for (JsonNode node : buildIndustryPresets()) {
            if (industry.equalsIgnoreCase(node.path("id").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownHardwareProfile(String hardwareProfile) {
        if (hardwareProfile == null) {
            return false;
        }
        for (JsonNode node : buildHardwareProfiles()) {
            if (hardwareProfile.equalsIgnoreCase(node.path("id").asText(""))) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownDeploymentMode(String deploymentMode) {
        if (deploymentMode == null) {
            return false;
        }
        return "online".equalsIgnoreCase(deploymentMode)
                || "offline".equalsIgnoreCase(deploymentMode)
                || "isolated".equalsIgnoreCase(deploymentMode);
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

    private boolean isAuditFailed(AnalyticsScreenAssetAuditLog log) {
        String value = trimToNull(log == null ? null : log.getResult());
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return "failed".equals(normalized) || "rejected".equals(normalized);
    }

    private boolean isAuditSuccess(AnalyticsScreenAssetAuditLog log) {
        String value = trimToNull(log == null ? null : log.getResult());
        if (value == null) {
            return false;
        }
        return "success".equalsIgnoreCase(value);
    }

    private ObjectNode checkItem(String id, String name, String status, String message, Map<String, Object> details) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", id);
        node.put("name", name);
        node.put("status", status);
        node.put("message", message);
        node.putPOJO("details", details == null ? Map.of() : details);
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

    private record ProbeTarget(String label, String host, int port) {}

    private record RuntimeTarget(
            String id,
            String name,
            String host,
            int port,
            boolean required,
            String protocol,
            String path,
            int expectedStatusMin,
            int expectedStatusMax,
            String expectedBodyContains) {}

    private record RuntimeProbeOutcome(
            String status,
            String message,
            long latencyMs,
            Integer httpStatus,
            String url,
            String bodyPreview,
            Boolean bodyMatched) {}

    private record RuntimeUrlParts(String protocol, String host, int port, String path) {}

    private record RuntimeProbeSummary(ArrayNode rows, int total, int pass, int warn, int fail, int timeoutMs) {}
}
