package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAcl;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenVersionRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.PublicLinkService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAclService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAuditService;
import com.yuzhi.dts.copilot.analytics.service.ScreenEditLockService;
import com.yuzhi.dts.copilot.analytics.service.ScreenWarmupService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAiGenerationService;
import com.yuzhi.dts.copilot.analytics.service.ScreenComplianceService;
import com.yuzhi.dts.copilot.analytics.service.ScreenServerRenderExportService;
import com.yuzhi.dts.copilot.analytics.service.ScreenSpecValidator;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing screen designer resources.
 */
@RestController
@RequestMapping("/api/screens")
@Transactional
public class ScreenResource {

    private static final String SCREEN_VERSION_STATUS_PUBLISHED = "PUBLISHED";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsScreenVersionRepository screenVersionRepository;
    private final ScreenAclService screenAclService;
    private final ScreenAuditService screenAuditService;
    private final ScreenEditLockService screenEditLockService;
    private final ScreenWarmupService screenWarmupService;
    private final ScreenAiGenerationService screenAiGenerationService;
    private final ScreenComplianceService screenComplianceService;
    private final ScreenServerRenderExportService screenServerRenderExportService;
    private final ScreenSpecValidator screenSpecValidator;
    private final PublicLinkService publicLinkService;
    private final ObjectMapper objectMapper;

    public ScreenResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService,
            ScreenEditLockService screenEditLockService,
            ScreenWarmupService screenWarmupService,
            ScreenAiGenerationService screenAiGenerationService,
            ScreenComplianceService screenComplianceService,
            ScreenServerRenderExportService screenServerRenderExportService,
            ScreenSpecValidator screenSpecValidator,
            PublicLinkService publicLinkService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
        this.screenEditLockService = screenEditLockService;
        this.screenWarmupService = screenWarmupService;
        this.screenAiGenerationService = screenAiGenerationService;
        this.screenComplianceService = screenComplianceService;
        this.screenServerRenderExportService = screenServerRenderExportService;
        this.screenSpecValidator = screenSpecValidator;
        this.publicLinkService = publicLinkService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        PlatformContext context = PlatformContext.from(request);
        List<ObjectNode> result = screenRepository.findAllByArchivedFalseOrderByIdDesc().stream()
                .map(screen -> {
                    ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
                    if (!permissions.canRead()) {
                        return null;
                    }
                    AnalyticsScreenVersion currentPublished =
                            screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
                    return toListResponse(screen, currentPublished, permissions);
                })
                .filter(node -> node != null)
                .toList();

        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/ai/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generateAiDraft(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        String prompt = body == null ? null : trimToNull(body.path("prompt").asText(null));
        Integer width = body != null && body.has("width") ? body.path("width").asInt(1920) : 1920;
        Integer height = body != null && body.has("height") ? body.path("height").asInt(1080) : 1080;

        ObjectNode result;
        try {
            result = screenAiGenerationService.generate(prompt, width, height);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN).body(ex.getMessage());
        }
        result.putPOJO("generatedBy", user.get().getId());
        result.putPOJO("generatedAt", Instant.now());
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/ai/revise", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reviseAiDraft(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        String prompt = body == null ? null : trimToNull(body.path("prompt").asText(null));
        JsonNode screenSpec = body == null ? null : body.path("screenSpec");
        List<String> context = parseAiContext(body == null ? null : body.path("context"));
        String mode = body == null ? null : trimToNull(body.path("mode").asText(null));
        boolean applyChanges = mode == null || !"suggest".equalsIgnoreCase(mode);
        ObjectNode result;
        try {
            result = screenAiGenerationService.revise(prompt, screenSpec, context, applyChanges);
        } catch (RuntimeException ex) {
            return ResponseEntity.status(503).contentType(MediaType.TEXT_PLAIN).body(ex.getMessage());
        }
        result.putPOJO("generatedBy", user.get().getId());
        result.putPOJO("generatedAt", Instant.now());
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/validate-spec", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> validateSpec(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        ScreenSpecValidator.ValidationResult validation = screenSpecValidator.validateForWrite(body);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("valid", true);
        result.putPOJO("warnings", validation.warnings());
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(
            @PathVariable("id") long id,
            @RequestParam(value = "mode", required = false, defaultValue = "draft") String mode,
            @RequestParam(value = "fallbackDraft", required = false, defaultValue = "true") boolean fallbackDraft,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canRead()) {
            return forbidden();
        }

        AnalyticsScreenVersion publishedVersion =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        boolean usePublished = "published".equalsIgnoreCase(mode) || "preview".equalsIgnoreCase(mode);
        if (usePublished) {
            if (publishedVersion != null) {
                return ResponseEntity.ok(toDetailResponse(screen, publishedVersion, publishedVersion, "published", permissions));
            }
            if (!fallbackDraft) {
                return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN).body("No published version");
            }
        }

        return ResponseEntity.ok(toDetailResponse(screen, null, publishedVersion, "draft", permissions));
    }

    @GetMapping(path = "/{id}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> versions(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }

        return ResponseEntity.ok(
                screenVersionRepository.findAllByScreenIdOrderByVersionNoDesc(screen.getId()).stream().map(this::toVersionResponse)
                        .toList());
    }

    @GetMapping(path = "/{id}/versions/compare", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> compareVersions(
            @PathVariable("id") long id,
            @RequestParam("fromVersionId") long fromVersionId,
            @RequestParam("toVersionId") long toVersionId,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }
        AnalyticsScreenVersion fromVersion = screenVersionRepository.findByIdAndScreenId(fromVersionId, screen.getId()).orElse(null);
        AnalyticsScreenVersion toVersion = screenVersionRepository.findByIdAndScreenId(toVersionId, screen.getId()).orElse(null);
        if (fromVersion == null || toVersion == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(buildVersionDiffSummary(fromVersion, toVersion));
    }

    @GetMapping(path = "/{id}/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> audit(
            @PathVariable("id") long id,
            @RequestParam(value = "limit", required = false, defaultValue = "200") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.MANAGE)) {
            return forbidden();
        }

        return ResponseEntity.ok(screenAuditService.listByScreenId(screen.getId(), limit).stream()
                .map(log -> {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("id", log.getId());
                    node.put("screenId", log.getScreenId());
                    node.putPOJO("actorId", log.getActorId());
                    node.put("action", log.getAction());
                    node.put("requestId", log.getRequestId());
                    node.putPOJO("createdAt", log.getCreatedAt());
                    node.set("before", parseObject(log.getBeforeJson()));
                    node.set("after", parseObject(log.getAfterJson()));
                    return node;
                })
                .toList());
    }

    @GetMapping(path = "/{id}/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> health(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }

        AnalyticsScreenVersion publishedVersion =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("screenId", screen.getId());
        result.putPOJO("generatedAt", Instant.now());
        result.put("requestId", requestIdFrom(request));
        result.put("baselineTargetComponents", 100);
        result.set("draft", buildHealthStats(screen.getComponentsJson()));
        if (publishedVersion != null) {
            result.set("published", buildHealthStats(publishedVersion.getComponentsJson()));
            result.put("publishedVersionNo", publishedVersion.getVersionNo());
            result.putPOJO("publishedAt", publishedVersion.getPublishedAt());
        } else {
            result.putNull("publishedVersionNo");
            result.putNull("publishedAt");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/{id}/export-prepare", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> prepareExport(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canRead()) {
            return forbidden();
        }

        String requestId = requestIdFrom(request);
        String format = trimToNull(body == null ? null : body.path("format").asText(null));
        if (format == null) {
            format = "png";
        }
        format = format.toLowerCase();
        if (!("png".equals(format) || "pdf".equals(format) || "json".equals(format))) {
            format = "png";
        }
        String mode = trimToNull(body == null ? null : body.path("mode").asText(null));
        if (mode == null) {
            mode = "draft";
        }
        mode = mode.toLowerCase();
        if (!("draft".equals(mode) || "published".equals(mode) || "preview".equals(mode))) {
            mode = "draft";
        }
        String device = trimToNull(body == null ? null : body.path("device").asText(null));
        if (device != null) {
            device = device.toLowerCase();
            if (!("pc".equals(device) || "tablet".equals(device) || "mobile".equals(device))) {
                device = null;
            }
        }
        boolean includeScreenSpec = body == null
                || !body.has("includeScreenSpec")
                || body.path("includeScreenSpec").asBoolean(true);

        AnalyticsScreenVersion publishedVersion =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        AnalyticsScreenVersion effectiveVersion = null;
        String resolvedMode = mode;
        if ("published".equals(mode) || "preview".equals(mode)) {
            if (publishedVersion != null) {
                effectiveVersion = publishedVersion;
                resolvedMode = "published";
            } else {
                resolvedMode = "draft";
            }
        }

        ObjectNode policy = screenComplianceService.currentPolicy();
        boolean exportApprovalRequired = policy.path("exportApprovalRequired").asBoolean(false);
        if (exportApprovalRequired && !permissions.canManage()) {
            ObjectNode denied = objectMapper.createObjectNode();
            denied.put("code", "SCREEN_EXPORT_APPROVAL_REQUIRED");
            denied.put("retryable", false);
            denied.put("requestId", requestId);
            denied.put("message", "当前大屏导出受合规策略限制，需要管理员审批后再导出");
            denied.put("screenId", screen.getId());
            denied.put("format", format);
            denied.put("mode", mode);
            screenAuditService.log(screen.getId(), user.get().getId(), "screen.export.denied", null, denied, requestId);
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Error-Code", "SCREEN_EXPORT_APPROVAL_REQUIRED")
                    .header("X-Error-Retryable", "false")
                    .body(denied);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("allowed", true);
        payload.put("screenId", screen.getId());
        payload.put("format", format);
        payload.put("mode", mode);
        if (device == null) {
            payload.putNull("device");
        } else {
            payload.put("device", device);
        }
        payload.put("requestId", requestId);
        payload.put("requestedMode", mode);
        payload.put("resolvedMode", resolvedMode);
        ObjectNode policySnapshot = objectMapper.createObjectNode();
        policySnapshot.put("policyVersion", policy.path("policyVersion").asInt(1));
        policySnapshot.put("exportApprovalRequired", exportApprovalRequired);
        policySnapshot.put("watermarkEnabled", policy.path("watermarkEnabled").asBoolean(false));
        policySnapshot.put("watermarkText", policy.path("watermarkText").asText(""));
        payload.set("policy", policySnapshot);
        if (effectiveVersion != null) {
            payload.putPOJO("publishedVersionNo", effectiveVersion.getVersionNo());
            payload.putPOJO("publishedAt", effectiveVersion.getPublishedAt());
        } else {
            payload.putNull("publishedVersionNo");
            payload.putNull("publishedAt");
        }

        if (includeScreenSpec) {
            ObjectNode screenSpec = buildExportScreenSpec(screen, effectiveVersion);
            payload.set("screenSpec", screenSpec);
            payload.put("specDigest", computeSpecDigest(screenSpec));
        }

        StringBuilder previewUrl = new StringBuilder("/analytics/screens/");
        previewUrl.append(screen.getId()).append("/preview");
        List<String> queryParts = new ArrayList<>();
        if (!"draft".equals(mode)) {
            queryParts.add("mode=" + mode);
        }
        if (device != null) {
            queryParts.add("device=" + device);
        }
        if (!queryParts.isEmpty()) {
            previewUrl.append("?").append(String.join("&", queryParts));
        }
        payload.put("previewUrl", previewUrl.toString());

        ObjectNode auditPayload = payload.deepCopy();
        if (auditPayload.has("screenSpec")) {
            JsonNode screenSpec = auditPayload.path("screenSpec");
            int componentCount = screenSpec.path("components").isArray() ? screenSpec.path("components").size() : 0;
            auditPayload.put("screenComponentCount", componentCount);
            auditPayload.remove("screenSpec");
        }
        screenAuditService.log(screen.getId(), user.get().getId(), "screen.export.prepare", null, auditPayload, requestId);
        return ResponseEntity.ok(payload);
    }

    @PostMapping(path = "/{id}/export-report", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reportExport(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canRead()) {
            return forbidden();
        }

        String requestId = requestIdFrom(request);
        String status = trimToNull(body == null ? null : body.path("status").asText(null));
        if (status == null) {
            status = "unknown";
        }
        status = status.toLowerCase();
        if (!("success".equals(status) || "failed".equals(status) || "fallback".equals(status))) {
            status = "unknown";
        }

        String format = trimToNull(body == null ? null : body.path("format").asText(null));
        if (format == null) {
            format = "png";
        }
        format = format.toLowerCase();
        String mode = trimToNull(body == null ? null : body.path("mode").asText(null));
        if (mode == null) {
            mode = "draft";
        }
        mode = mode.toLowerCase();
        String resolvedMode = trimToNull(body == null ? null : body.path("resolvedMode").asText(null));
        if (resolvedMode == null) {
            resolvedMode = mode;
        }
        resolvedMode = resolvedMode.toLowerCase();
        String device = trimToNull(body == null ? null : body.path("device").asText(null));
        if (device != null) {
            device = device.toLowerCase();
        }
        String clientRequestId = trimToNull(body == null ? null : body.path("requestId").asText(null));
        String message = trimToNull(body == null ? null : body.path("message").asText(null));
        String specDigest = trimToNull(body == null ? null : body.path("specDigest").asText(null));

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("accepted", true);
        payload.put("status", status);
        payload.put("screenId", screen.getId());
        payload.put("format", format);
        payload.put("mode", mode);
        payload.put("resolvedMode", resolvedMode);
        if (device == null) {
            payload.putNull("device");
        } else {
            payload.put("device", device);
        }
        if (clientRequestId == null) {
            payload.putNull("clientRequestId");
        } else {
            payload.put("clientRequestId", clientRequestId);
        }
        if (message == null) {
            payload.putNull("message");
        } else {
            payload.put("message", message);
        }
        if (specDigest == null) {
            payload.putNull("specDigest");
        } else {
            payload.put("specDigest", specDigest);
        }
        payload.put("requestId", requestId);
        payload.putPOJO("reportedAt", Instant.now());

        String action = switch (status) {
            case "success" -> "screen.export.success";
            case "fallback" -> "screen.export.fallback";
            case "failed" -> "screen.export.failed";
            default -> "screen.export.report";
        };
        screenAuditService.log(screen.getId(), user.get().getId(), action, null, payload, requestId);
        return ResponseEntity.ok(payload);
    }

    @PostMapping(path = "/{id}/export-render", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> renderExport(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canRead()) {
            return forbidden();
        }

        String requestId = requestIdFrom(request);
        String format = trimToNull(body == null ? null : body.path("format").asText(null));
        if (format == null) {
            format = "png";
        }
        format = format.toLowerCase();
        if (!("png".equals(format) || "pdf".equals(format))) {
            format = "png";
        }
        String mode = trimToNull(body == null ? null : body.path("mode").asText(null));
        if (mode == null) {
            mode = "draft";
        }
        mode = mode.toLowerCase();
        if (!("draft".equals(mode) || "published".equals(mode) || "preview".equals(mode))) {
            mode = "draft";
        }
        String device = trimToNull(body == null ? null : body.path("device").asText(null));
        if (device != null) {
            device = device.toLowerCase();
            if (!("pc".equals(device) || "tablet".equals(device) || "mobile".equals(device))) {
                device = null;
            }
        }
        String resolvedMode = mode;
        AnalyticsScreenVersion publishedVersion =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        AnalyticsScreenVersion effectiveVersion = null;
        if ("published".equals(mode) || "preview".equals(mode)) {
            if (publishedVersion != null) {
                effectiveVersion = publishedVersion;
                resolvedMode = "published";
            } else {
                resolvedMode = "draft";
            }
        }

        ObjectNode policy = screenComplianceService.currentPolicy();
        boolean exportApprovalRequired = policy.path("exportApprovalRequired").asBoolean(false);
        if (exportApprovalRequired && !permissions.canManage()) {
            ObjectNode denied = objectMapper.createObjectNode();
            denied.put("code", "SCREEN_EXPORT_APPROVAL_REQUIRED");
            denied.put("retryable", false);
            denied.put("requestId", requestId);
            denied.put("message", "当前大屏导出受合规策略限制，需要管理员审批后再导出");
            denied.put("screenId", screen.getId());
            denied.put("format", format);
            denied.put("mode", mode);
            screenAuditService.log(screen.getId(), user.get().getId(), "screen.export.denied", null, denied, requestId);
            return ResponseEntity.status(403)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Error-Code", "SCREEN_EXPORT_APPROVAL_REQUIRED")
                    .header("X-Error-Retryable", "false")
                    .body(denied);
        }

        JsonNode incomingSpec = body == null ? null : body.path("screenSpec");
        ObjectNode screenSpec;
        if (incomingSpec != null && incomingSpec.isObject()) {
            screenSpec = ((ObjectNode) incomingSpec).deepCopy();
        } else {
            screenSpec = buildExportScreenSpec(screen, effectiveVersion);
        }
        int hiddenByDevice = filterExportComponentsByDevice(screenSpec, device);
        String specDigest = computeSpecDigest(screenSpec);
        boolean watermarkEnabled = policy.path("watermarkEnabled").asBoolean(false);
        String watermarkText = policy.path("watermarkText").asText("");
        double pixelRatio = normalizeExportPixelRatio(
                body == null ? Double.NaN : body.path("pixelRatio").asDouble(Double.NaN),
                format);

        try {
            byte[] bytes = "pdf".equals(format)
                    ? screenServerRenderExportService.renderPdf(screenSpec, watermarkEnabled, watermarkText, pixelRatio)
                    : screenServerRenderExportService.renderPng(screenSpec, watermarkEnabled, watermarkText, pixelRatio);
            String ext = "pdf".equals(format) ? "pdf" : "png";
            String fileName = "screen-" + screen.getId() + "-" + resolvedMode + "." + ext;
            MediaType contentType = "pdf".equals(format) ? MediaType.APPLICATION_PDF : MediaType.IMAGE_PNG;

            ObjectNode auditPayload = objectMapper.createObjectNode();
            auditPayload.put("screenId", screen.getId());
            auditPayload.put("requestId", requestId);
            auditPayload.put("format", format);
            auditPayload.put("mode", mode);
            auditPayload.put("resolvedMode", resolvedMode);
            auditPayload.put("byteSize", bytes.length);
            auditPayload.put("specDigest", specDigest);
            auditPayload.put("watermarkEnabled", watermarkEnabled);
            auditPayload.put("pixelRatio", pixelRatio);
            auditPayload.put("hiddenByDevice", hiddenByDevice);
            auditPayload.put("renderEngine", "server-render-v2");
            screenAuditService.log(screen.getId(), user.get().getId(), "screen.export.server.render", null, auditPayload, requestId);

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header("X-Request-Id", requestId == null ? "" : requestId)
                    .header("X-Screen-Spec-Digest", specDigest == null ? "" : specDigest)
                    .header("X-Screen-Resolved-Mode", resolvedMode)
                    .header("X-Screen-Render-Engine", "server-render-v2")
                    .header("X-Screen-Render-Pixel-Ratio", Double.toString(pixelRatio))
                    .header("X-Screen-Device-Mode", device == null ? "" : device)
                    .header("X-Screen-Hidden-By-Device", Integer.toString(Math.max(0, hiddenByDevice)))
                    .body(bytes);
        } catch (Exception ex) {
            ObjectNode failure = objectMapper.createObjectNode();
            failure.put("code", "SCREEN_EXPORT_SERVER_RENDER_FAILED");
            failure.put("requestId", requestId);
            failure.put("message", ex.getMessage() == null ? "服务端渲染导出失败" : ex.getMessage());
            failure.put("format", format);
            failure.put("mode", mode);
            failure.put("resolvedMode", resolvedMode);
            failure.put("specDigest", specDigest);
            failure.put("renderEngine", "server-render-v2");
            screenAuditService.log(screen.getId(), user.get().getId(), "screen.export.server.render.failed", null, failure, requestId);
            return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(failure);
        }
    }

    @GetMapping(path = "/{id}/acl", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getAcl(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.MANAGE)) {
            return forbidden();
        }

        return ResponseEntity.ok(screenAclService.listEntries(screen.getId()).stream().map(this::toAclResponse).toList());
    }

    @PutMapping(path = "/{id}/acl", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateAcl(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.MANAGE)) {
            return forbidden();
        }

        List<ObjectNode> before = screenAclService.listEntries(screen.getId()).stream().map(this::toAclResponse).toList();
        List<AnalyticsScreenAcl> entries = screenAclService.parseEntriesFromBody(screen.getId(), user.get().getId(), body);
        screenAclService.replaceEntries(screen, user.get().getId(), entries);
        List<ObjectNode> after = screenAclService.listEntries(screen.getId()).stream().map(this::toAclResponse).toList();

        screenAuditService.log(
                screen.getId(),
                user.get().getId(),
                "acl.update",
                before,
                after,
                requestIdFrom(request));

        return ResponseEntity.ok(after);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        ScreenSpecValidator.ValidationResult specValidation = screenSpecValidator.validateForWrite(body);

        String name = body == null ? null : trimToNull(body.path("name").asText(null));
        if (name == null) {
            name = "未命名大屏";
        }

        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setName(name);
        screen.setDescription(body != null && body.has("description") && !body.path("description").isNull()
                ? body.path("description").asText(null)
                : null);
        screen.setWidth(body != null && body.has("width") ? body.path("width").asInt(1920) : 1920);
        screen.setHeight(body != null && body.has("height") ? body.path("height").asInt(1080) : 1080);
        screen.setBackgroundColor(
                body != null && body.has("backgroundColor") ? body.path("backgroundColor").asText(null) : "#0d1b2a");
        screen.setBackgroundImage(body != null && body.has("backgroundImage") && !body.path("backgroundImage").isNull()
                ? body.path("backgroundImage").asText(null)
                : null);
        screen.setTheme(body != null && body.has("theme") && !body.path("theme").isNull()
                ? body.path("theme").asText(null)
                : null);
        screen.setComponentsJson(body != null && body.has("components") ? body.path("components").toString() : "[]");
        screen.setVariablesJson(body != null && body.has("globalVariables") ? body.path("globalVariables").toString() : "[]");
        screen.setCreatorId(user.get().getId());
        screen.setArchived(false);

        screen = screenRepository.save(screen);
        screenAclService.ensureCreatorManage(screen);

        ScreenAclService.PermissionSnapshot permissions = new ScreenAclService.PermissionSnapshot(true, true, true, true);
        ObjectNode detail = toDetailResponse(screen, null, null, "draft", permissions);
        applySpecWarnings(detail, specValidation.warnings());

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.create", null, detail, requestIdFrom(request));

        return ResponseEntity.ok(detail);
    }

    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canEdit()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.get().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        ConflictResolution resolution = resolveUpdateConflict(screen, body);
        if (resolution.conflictPayload != null) {
            return ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON).body(resolution.conflictPayload);
        }
        JsonNode effectiveBody = resolution.mergedBody == null ? body : resolution.mergedBody;
        ScreenSpecValidator.ValidationResult specValidation = screenSpecValidator.validateForWrite(effectiveBody);

        AnalyticsScreenVersion beforePublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode before = toAuditScreenSnapshot(screen, beforePublished);

        if (effectiveBody != null && effectiveBody.has("name")) {
            String name = trimToNull(effectiveBody.path("name").asText(null));
            if (name != null) {
                screen.setName(name);
            }
        }
        if (effectiveBody != null && effectiveBody.has("description")) {
            screen.setDescription(effectiveBody.path("description").isNull() ? null : effectiveBody.path("description").asText(null));
        }
        if (effectiveBody != null && effectiveBody.has("width")) {
            screen.setWidth(effectiveBody.path("width").asInt(1920));
        }
        if (effectiveBody != null && effectiveBody.has("height")) {
            screen.setHeight(effectiveBody.path("height").asInt(1080));
        }
        if (effectiveBody != null && effectiveBody.has("backgroundColor")) {
            screen.setBackgroundColor(effectiveBody.path("backgroundColor").asText(null));
        }
        if (effectiveBody != null && effectiveBody.has("backgroundImage")) {
            screen.setBackgroundImage(effectiveBody.path("backgroundImage").isNull() ? null : effectiveBody.path("backgroundImage").asText(null));
        }
        if (effectiveBody != null && effectiveBody.has("theme")) {
            screen.setTheme(effectiveBody.path("theme").isNull() ? null : effectiveBody.path("theme").asText(null));
        }
        if (effectiveBody != null && effectiveBody.has("components")) {
            screen.setComponentsJson(effectiveBody.path("components").toString());
        }
        if (effectiveBody != null && effectiveBody.has("globalVariables")) {
            screen.setVariablesJson(effectiveBody.path("globalVariables").toString());
        }

        screenRepository.save(screen);
        AnalyticsScreenVersion currentPublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode detail = toDetailResponse(screen, null, currentPublished, "draft", permissions);
        applySpecWarnings(detail, specValidation.warnings());

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.update", before, detail, requestIdFrom(request));

        return ResponseEntity.ok(detail);
    }

    @PostMapping(path = "/{id}/publish", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publish(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canPublish()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.get().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        AnalyticsScreenVersion beforePublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode before = toAuditScreenSnapshot(screen, beforePublished);

        int nextVersionNo = screenVersionRepository.findFirstByScreenIdOrderByVersionNoDesc(screen.getId())
                .map(v -> v.getVersionNo() == null ? 1 : v.getVersionNo() + 1)
                .orElse(1);

        screenVersionRepository.clearCurrentPublished(screen.getId());
        AnalyticsScreenVersion version = createVersionFromScreen(
                screen,
                user.get().getId(),
                nextVersionNo,
                true,
                Instant.now());
        version = screenVersionRepository.save(version);

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode detail = toDetailResponse(screen, version, version, "published", permissions);
        response.set("screen", detail);
        response.set("version", toVersionResponse(version));

        ScreenWarmupService.WarmupSummary warmupSummary = screenWarmupService.warmupForPublishedScreen(screen, user.get().getId());
        response.putPOJO("warmup", warmupSummary);

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.publish", before, response, requestIdFrom(request));

        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{id}/rollback/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rollback(
            @PathVariable("id") long id,
            @PathVariable("versionId") long versionId,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        ScreenAclService.PermissionSnapshot permissions = screenAclService.snapshot(screen, user.get(), context);
        if (!permissions.canPublish()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.get().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        AnalyticsScreenVersion targetVersion = screenVersionRepository.findByIdAndScreenId(versionId, screen.getId()).orElse(null);
        if (targetVersion == null) {
            return ResponseEntity.notFound().build();
        }

        AnalyticsScreenVersion beforePublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode before = toAuditScreenSnapshot(screen, beforePublished);

        applyVersionToScreen(targetVersion, screen);
        screenRepository.save(screen);

        screenVersionRepository.clearCurrentPublished(screen.getId());
        targetVersion.setCurrentPublished(true);
        targetVersion.setPublishedAt(Instant.now());
        targetVersion = screenVersionRepository.save(targetVersion);

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode detail = toDetailResponse(screen, targetVersion, targetVersion, "published", permissions);
        response.set("screen", detail);
        response.set("version", toVersionResponse(targetVersion));

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.rollback", before, response, requestIdFrom(request));

        return ResponseEntity.ok(response);
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.MANAGE)) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.get().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        ObjectNode before = toAuditScreenSnapshot(
                screen,
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null));

        screen.setArchived(true);
        screenRepository.save(screen);
        screenEditLockService.release(screen.getId(), user.get().getId());

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.delete", before, null, requestIdFrom(request));

        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/public_link", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPublicLink(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext ctx = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), ctx, ScreenAclService.Permission.PUBLISH)) {
            return forbidden();
        }

        if (screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).isEmpty()) {
            return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN).body("No published version");
        }

        AnalyticsPublicLink beforeLink = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        ObjectNode before = toPublicLinkPolicyResponse(beforeLink == null ? null : beforeLink.getPublicUuid(), beforeLink);

        String uuid;
        try {
            uuid = publicLinkService.getOrCreateScoped(
                    PublicLinkService.MODEL_SCREEN, id, user.get().getId(), ctx.dept(), ctx.classification());
        } catch (IllegalStateException e) {
            return forbidden();
        }

        AnalyticsPublicLink link = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        if (link != null) {
            applyPublicLinkPolicyFromBody(link, body);
            link = publicLinkService.save(link);
        }

        ObjectNode after = toPublicLinkPolicyResponse(uuid, link);
        screenAuditService.log(screen.getId(), user.get().getId(), "screen.public_link.create", before, after, requestIdFrom(request));

        return ResponseEntity.ok(after);
    }

    @PutMapping(path = "/{id}/public_link/policy", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePublicLinkPolicy(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext ctx = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), ctx, ScreenAclService.Permission.PUBLISH)) {
            return forbidden();
        }

        AnalyticsPublicLink link = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        if (link == null) {
            return ResponseEntity.notFound().build();
        }

        ObjectNode before = toPublicLinkPolicyResponse(link.getPublicUuid(), link);
        applyPublicLinkPolicyFromBody(link, body);
        link = publicLinkService.save(link);
        ObjectNode after = toPublicLinkPolicyResponse(link.getPublicUuid(), link);

        screenAuditService.log(screen.getId(), user.get().getId(), "screen.public_link.policy", before, after, requestIdFrom(request));

        return ResponseEntity.ok(after);
    }

    @DeleteMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePublicLink(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext ctx = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), ctx, ScreenAclService.Permission.MANAGE)) {
            return forbidden();
        }

        AnalyticsPublicLink beforeLink = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        ObjectNode before = toPublicLinkPolicyResponse(beforeLink == null ? null : beforeLink.getPublicUuid(), beforeLink);

        publicLinkService.delete(PublicLinkService.MODEL_SCREEN, id);
        screenAuditService.log(screen.getId(), user.get().getId(), "screen.public_link.delete", before, null, requestIdFrom(request));

        return ResponseEntity.noContent().build();
    }

    private AnalyticsScreenVersion createVersionFromScreen(
            AnalyticsScreen screen,
            Long creatorId,
            int versionNo,
            boolean currentPublished,
            Instant publishedAt) {
        AnalyticsScreenVersion version = new AnalyticsScreenVersion();
        version.setScreenId(screen.getId());
        version.setVersionNo(versionNo);
        version.setStatus(SCREEN_VERSION_STATUS_PUBLISHED);
        version.setName(screen.getName());
        version.setDescription(screen.getDescription());
        version.setWidth(screen.getWidth());
        version.setHeight(screen.getHeight());
        version.setBackgroundColor(screen.getBackgroundColor());
        version.setBackgroundImage(screen.getBackgroundImage());
        version.setTheme(screen.getTheme());
        version.setComponentsJson(screen.getComponentsJson());
        version.setVariablesJson(screen.getVariablesJson());
        version.setCreatorId(creatorId);
        version.setCurrentPublished(currentPublished);
        version.setPublishedAt(publishedAt);
        return version;
    }

    private void applyVersionToScreen(AnalyticsScreenVersion version, AnalyticsScreen screen) {
        screen.setName(version.getName());
        screen.setDescription(version.getDescription());
        screen.setWidth(version.getWidth());
        screen.setHeight(version.getHeight());
        screen.setBackgroundColor(version.getBackgroundColor());
        screen.setBackgroundImage(version.getBackgroundImage());
        screen.setTheme(version.getTheme());
        screen.setComponentsJson(version.getComponentsJson());
        screen.setVariablesJson(version.getVariablesJson());
    }

    private ObjectNode toListResponse(
            AnalyticsScreen screen,
            AnalyticsScreenVersion currentPublishedVersion,
            ScreenAclService.PermissionSnapshot permissions) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", screen.getId());
        node.put("name", screen.getName());
        node.put("description", screen.getDescription());
        node.put("width", screen.getWidth());
        node.put("height", screen.getHeight());
        node.put("theme", screen.getTheme());
        node.putPOJO("createdAt", screen.getCreatedAt());
        node.putPOJO("updatedAt", screen.getUpdatedAt());
        node.put("canRead", permissions.canRead());
        node.put("canEdit", permissions.canEdit());
        node.put("canPublish", permissions.canPublish());
        node.put("canManage", permissions.canManage());
        if (currentPublishedVersion != null) {
            node.put("publishedVersionNo", currentPublishedVersion.getVersionNo());
            node.putPOJO("publishedAt", currentPublishedVersion.getPublishedAt());
        } else {
            node.putNull("publishedVersionNo");
            node.putNull("publishedAt");
        }
        return node;
    }

    private ObjectNode toVersionResponse(AnalyticsScreenVersion version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", version.getId());
        node.put("screenId", version.getScreenId());
        node.put("versionNo", version.getVersionNo());
        node.put("status", version.getStatus());
        node.put("name", version.getName());
        node.put("description", version.getDescription());
        node.put("currentPublished", version.isCurrentPublished());
        node.putPOJO("publishedAt", version.getPublishedAt());
        node.putPOJO("createdAt", version.getCreatedAt());
        node.putPOJO("creatorId", version.getCreatorId());
        return node;
    }

    private ObjectNode buildVersionDiffSummary(AnalyticsScreenVersion fromVersion, AnalyticsScreenVersion toVersion) {
        JsonNode fromComponents = parseComponents(fromVersion.getComponentsJson());
        JsonNode toComponents = parseComponents(toVersion.getComponentsJson());
        JsonNode fromVariables = parseGlobalVariables(fromVersion.getVariablesJson());
        JsonNode toVariables = parseGlobalVariables(toVersion.getVariablesJson());

        Map<String, String> fromComponentTypeMap = collectComponentTypeMap(fromComponents);
        Map<String, String> toComponentTypeMap = collectComponentTypeMap(toComponents);
        Set<String> fromComponentIds = fromComponentTypeMap.keySet();
        Set<String> toComponentIds = toComponentTypeMap.keySet();
        Set<String> fromTypes = new HashSet<>(fromComponentTypeMap.values());
        Set<String> toTypes = new HashSet<>(toComponentTypeMap.values());

        List<String> addedComponentIds = collectDiffItems(toComponentIds, fromComponentIds);
        List<String> removedComponentIds = collectDiffItems(fromComponentIds, toComponentIds);
        List<String> addedTypeNames = collectDiffItems(toTypes, fromTypes);
        List<String> removedTypeNames = collectDiffItems(fromTypes, toTypes);
        List<ObjectNode> changedTypeComponents = collectChangedTypeComponents(fromComponentTypeMap, toComponentTypeMap);

        Set<String> fromVarKeys = collectVariableKeys(fromVariables);
        Set<String> toVarKeys = collectVariableKeys(toVariables);
        List<String> addedVariableKeys = collectDiffItems(toVarKeys, fromVarKeys);
        List<String> removedVariableKeys = collectDiffItems(fromVarKeys, toVarKeys);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("from", toVersionResponse(fromVersion));
        node.set("to", toVersionResponse(toVersion));
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("componentCountFrom", fromComponentIds.size());
        summary.put("componentCountTo", toComponentIds.size());
        summary.put("addedComponents", addedComponentIds.size());
        summary.put("removedComponents", removedComponentIds.size());
        summary.put("addedComponentTypes", addedTypeNames.size());
        summary.put("removedComponentTypes", removedTypeNames.size());
        summary.put("changedTypeComponents", changedTypeComponents.size());
        summary.put("addedVariables", addedVariableKeys.size());
        summary.put("removedVariables", removedVariableKeys.size());
        node.set("summary", summary);

        ObjectNode details = objectMapper.createObjectNode();
        details.putPOJO("addedComponentIds", addedComponentIds);
        details.putPOJO("removedComponentIds", removedComponentIds);
        details.putPOJO("addedComponentTypes", addedTypeNames);
        details.putPOJO("removedComponentTypes", removedTypeNames);
        details.putPOJO("addedVariableKeys", addedVariableKeys);
        details.putPOJO("removedVariableKeys", removedVariableKeys);
        details.putPOJO("changedTypeComponents", changedTypeComponents);
        node.set("details", details);
        return node;
    }

    private Map<String, String> collectComponentTypeMap(JsonNode components) {
        Map<String, String> map = new LinkedHashMap<>();
        if (components == null || !components.isArray()) {
            return map;
        }
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = trimToNull(item.path("id").asText(null));
            String type = trimToNull(item.path("type").asText(null));
            if (id != null) {
                map.put(id, type == null ? "" : type);
            }
        }
        return map;
    }

    private Set<String> collectVariableKeys(JsonNode variables) {
        Set<String> keys = new HashSet<>();
        if (variables == null || !variables.isArray()) {
            return keys;
        }
        for (JsonNode item : variables) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String key = trimToNull(item.path("key").asText(null));
            if (key != null) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> collectDiffItems(Set<String> left, Set<String> right) {
        List<String> out = new ArrayList<>();
        for (String value : left) {
            if (value != null && !right.contains(value)) {
                out.add(value);
            }
        }
        out.sort(String::compareTo);
        return out;
    }

    private List<ObjectNode> collectChangedTypeComponents(Map<String, String> fromMap, Map<String, String> toMap) {
        List<String> ids = new ArrayList<>(fromMap.keySet());
        ids.retainAll(toMap.keySet());
        ids.sort(String::compareTo);

        List<ObjectNode> out = new ArrayList<>();
        for (String id : ids) {
            String fromType = fromMap.get(id);
            String toType = toMap.get(id);
            if (valueEquals(fromType, toType)) {
                continue;
            }
            ObjectNode item = objectMapper.createObjectNode();
            item.put("id", id);
            item.put("fromType", fromType == null ? "" : fromType);
            item.put("toType", toType == null ? "" : toType);
            out.add(item);
        }
        return out;
    }

    private ObjectNode toAclResponse(AnalyticsScreenAcl acl) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", acl.getId());
        node.put("screenId", acl.getScreenId());
        node.put("subjectType", acl.getSubjectType());
        node.put("subjectId", acl.getSubjectId());
        node.put("perm", acl.getPerm());
        node.putPOJO("creatorId", acl.getCreatorId());
        node.putPOJO("createdAt", acl.getCreatedAt());
        node.putPOJO("updatedAt", acl.getUpdatedAt());
        return node;
    }

    private ObjectNode toDetailResponse(
            AnalyticsScreen screen,
            AnalyticsScreenVersion effectiveVersion,
            AnalyticsScreenVersion currentPublishedVersion,
            String sourceMode,
            ScreenAclService.PermissionSnapshot permissions) {
        ObjectNode node = toListResponse(screen, currentPublishedVersion, permissions);
        if (effectiveVersion != null) {
            node.put("name", effectiveVersion.getName());
            node.put("description", effectiveVersion.getDescription());
            node.put("width", effectiveVersion.getWidth());
            node.put("height", effectiveVersion.getHeight());
            node.put("theme", effectiveVersion.getTheme());
            node.put("backgroundColor", effectiveVersion.getBackgroundColor());
            node.put("backgroundImage", effectiveVersion.getBackgroundImage());
            node.set("components", parseComponents(effectiveVersion.getComponentsJson()));
            node.set("globalVariables", parseGlobalVariables(effectiveVersion.getVariablesJson()));
        } else {
            node.put("backgroundColor", screen.getBackgroundColor());
            node.put("backgroundImage", screen.getBackgroundImage());
            node.set("components", parseComponents(screen.getComponentsJson()));
            node.set("globalVariables", parseGlobalVariables(screen.getVariablesJson()));
        }
        node.put("sourceMode", sourceMode);
        return node;
    }

    private ObjectNode toAuditScreenSnapshot(AnalyticsScreen screen, AnalyticsScreenVersion currentPublishedVersion) {
        ObjectNode node = objectMapper.createObjectNode();
        if (screen == null) {
            return node;
        }
        node.put("id", screen.getId());
        node.put("name", screen.getName());
        node.put("description", screen.getDescription());
        node.put("width", screen.getWidth());
        node.put("height", screen.getHeight());
        node.put("backgroundColor", screen.getBackgroundColor());
        node.put("backgroundImage", screen.getBackgroundImage());
        node.put("theme", screen.getTheme());
        node.set("components", parseComponents(screen.getComponentsJson()));
        node.set("globalVariables", parseGlobalVariables(screen.getVariablesJson()));
        if (currentPublishedVersion != null) {
            node.put("publishedVersionNo", currentPublishedVersion.getVersionNo());
            node.putPOJO("publishedAt", currentPublishedVersion.getPublishedAt());
        } else {
            node.putNull("publishedVersionNo");
            node.putNull("publishedAt");
        }
        return node;
    }

    private ObjectNode toPublicLinkPolicyResponse(String uuid, AnalyticsPublicLink link) {
        ObjectNode node = objectMapper.createObjectNode();
        if (uuid != null) {
            node.put("uuid", uuid);
        } else {
            node.putNull("uuid");
        }
        if (link != null) {
            node.putPOJO("expireAt", link.getExpireAt());
            node.put("hasPassword", link.getPasswordHash() != null && !link.getPasswordHash().isBlank());
            node.put("ipAllowlist", link.getIpAllowlist());
            node.put("disabled", link.isDisabled());
        } else {
            node.putNull("expireAt");
            node.put("hasPassword", false);
            node.putNull("ipAllowlist");
            node.put("disabled", false);
        }
        return node;
    }

    private static void applySpecWarnings(ObjectNode node, List<String> warnings) {
        if (node == null || warnings == null || warnings.isEmpty()) {
            return;
        }
        node.putPOJO("specWarnings", warnings);
    }

    private void applyPublicLinkPolicyFromBody(AnalyticsPublicLink link, JsonNode body) {
        if (link == null || body == null || body.isNull()) {
            return;
        }

        if (body.has("expireAt")) {
            JsonNode expireNode = body.path("expireAt");
            if (expireNode.isNull() || expireNode.asText("").isBlank()) {
                link.setExpireAt(null);
            } else {
                String value = expireNode.asText(null);
                try {
                    link.setExpireAt(value == null ? null : Instant.parse(value));
                } catch (DateTimeParseException ignored) {
                    link.setExpireAt(null);
                }
            }
        }

        if (body.has("password")) {
            link.setPasswordHash(PublicLinkService.hashPassword(trimToNull(body.path("password").asText(null))));
        }

        if (body.has("ipAllowlist")) {
            link.setIpAllowlist(trimToNull(body.path("ipAllowlist").asText(null)));
        }

        if (body.has("disabled")) {
            link.setDisabled(body.path("disabled").asBoolean(false));
        }
    }

    private ConflictResolution resolveUpdateConflict(AnalyticsScreen screen, JsonNode body) {
        if (screen == null || body == null || !body.isObject()) {
            return ConflictResolution.pass(body);
        }
        JsonNode conflict = body.path("_conflict");
        if (conflict == null || !conflict.isObject()) {
            return ConflictResolution.pass(body);
        }

        Instant baseUpdatedAt = parseInstantSafe(trimToNull(conflict.path("baseUpdatedAt").asText(null)));
        if (baseUpdatedAt == null || screen.getUpdatedAt() == null || !screen.getUpdatedAt().isAfter(baseUpdatedAt)) {
            return ConflictResolution.pass(body);
        }

        String mode = trimToNull(conflict.path("mode").asText(null));
        if (!"component".equalsIgnoreCase(mode)) {
            return ConflictResolution.blocked(buildConflictPayload(
                    "SCREEN_UPDATE_CONFLICT",
                    "Screen has been updated by another user",
                    List.of(),
                    List.of("screen")));
        }

        JsonNode incomingComponentsNode = body.path("components");
        if (!incomingComponentsNode.isArray()) {
            return ConflictResolution.blocked(buildConflictPayload(
                    "SCREEN_UPDATE_CONFLICT",
                    "Payload missing components for component merge mode",
                    List.of(),
                    List.of("components")));
        }

        Map<String, JsonNode> baseComponents = parseBaseComponentMap(conflict.path("baseComponents"));
        Map<String, JsonNode> currentComponents = parseComponentMap(parseComponents(screen.getComponentsJson()));
        Map<String, JsonNode> incomingComponents = parseComponentMap(incomingComponentsNode);

        ComponentMergeResult componentMerge = mergeComponentsByBase(baseComponents, currentComponents, incomingComponents);

        JsonNode currentVars = parseGlobalVariables(screen.getVariablesJson());
        JsonNode incomingVars = body.path("globalVariables").isArray() ? body.path("globalVariables") : objectMapper.createArrayNode();
        JsonNode baseVars = conflict.path("baseVariables").isArray() ? conflict.path("baseVariables") : objectMapper.createArrayNode();
        boolean varsRemoteChanged = !jsonEquals(currentVars, baseVars);
        boolean varsLocalChanged = !jsonEquals(incomingVars, baseVars);
        boolean varsConflict = varsRemoteChanged && varsLocalChanged && !jsonEquals(currentVars, incomingVars);

        JsonNode baseScreen = conflict.path("baseScreen");
        List<String> scalarConflicts = new ArrayList<>();
        ScalarMergeResult nameMerged = mergeTextScalar("name", trimToNull(screen.getName()), body.path("name"), baseScreen, scalarConflicts);
        ScalarMergeResult descriptionMerged = mergeNullableTextScalar(
                "description",
                trimToNull(screen.getDescription()),
                body.path("description"),
                baseScreen,
                scalarConflicts);
        ScalarMergeResult widthMerged = mergeIntScalar("width", screen.getWidth(), body.path("width"), baseScreen, scalarConflicts);
        ScalarMergeResult heightMerged = mergeIntScalar("height", screen.getHeight(), body.path("height"), baseScreen, scalarConflicts);
        ScalarMergeResult backgroundColorMerged = mergeNullableTextScalar(
                "backgroundColor",
                trimToNull(screen.getBackgroundColor()),
                body.path("backgroundColor"),
                baseScreen,
                scalarConflicts);
        ScalarMergeResult backgroundImageMerged = mergeNullableTextScalar(
                "backgroundImage",
                trimToNull(screen.getBackgroundImage()),
                body.path("backgroundImage"),
                baseScreen,
                scalarConflicts);
        ScalarMergeResult themeMerged = mergeNullableTextScalar(
                "theme",
                trimToNull(screen.getTheme()),
                body.path("theme"),
                baseScreen,
                scalarConflicts);

        List<String> allConflicts = new ArrayList<>();
        allConflicts.addAll(componentMerge.conflictComponentIds);
        if (varsConflict) {
            allConflicts.add("globalVariables");
        }
        allConflicts.addAll(scalarConflicts);
        if (!allConflicts.isEmpty()) {
            return ConflictResolution.blocked(buildConflictPayload(
                    "SCREEN_UPDATE_CONFLICT",
                    "Concurrent edits conflict on overlapping fields/components",
                    componentMerge.conflictComponentIds,
                    mergeConflictFields(scalarConflicts, varsConflict)));
        }

        ObjectNode mergedBody = body.deepCopy();
        mergedBody.remove("_conflict");
        mergedBody.set("components", componentMerge.mergedComponents);
        mergedBody.set("globalVariables", varsLocalChanged ? incomingVars.deepCopy() : currentVars.deepCopy());
        mergedBody.put("name", nameMerged.textValue == null ? "" : nameMerged.textValue);
        if (descriptionMerged.textValue == null) {
            mergedBody.putNull("description");
        } else {
            mergedBody.put("description", descriptionMerged.textValue);
        }
        mergedBody.put("width", widthMerged.intValue);
        mergedBody.put("height", heightMerged.intValue);
        if (backgroundColorMerged.textValue == null) {
            mergedBody.putNull("backgroundColor");
        } else {
            mergedBody.put("backgroundColor", backgroundColorMerged.textValue);
        }
        if (backgroundImageMerged.textValue == null) {
            mergedBody.putNull("backgroundImage");
        } else {
            mergedBody.put("backgroundImage", backgroundImageMerged.textValue);
        }
        if (themeMerged.textValue == null) {
            mergedBody.putNull("theme");
        } else {
            mergedBody.put("theme", themeMerged.textValue);
        }
        return ConflictResolution.pass(mergedBody);
    }

    private static List<String> mergeConflictFields(List<String> scalarConflicts, boolean varsConflict) {
        List<String> out = new ArrayList<>(scalarConflicts);
        if (varsConflict) {
            out.add("globalVariables");
        }
        return out;
    }

    private ObjectNode buildConflictPayload(String code, String message, List<String> componentIds, List<String> fields) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("code", code);
        payload.put("message", message);
        payload.putPOJO("componentIds", componentIds == null ? List.of() : componentIds);
        payload.putPOJO("fields", fields == null ? List.of() : fields);
        return payload;
    }

    private Map<String, JsonNode> parseBaseComponentMap(JsonNode baseComponents) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (baseComponents == null || !baseComponents.isArray()) {
            return out;
        }
        for (JsonNode item : baseComponents) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = trimToNull(item.path("id").asText(null));
            JsonNode component = item.path("component");
            if (id == null || component == null || !component.isObject()) {
                continue;
            }
            out.put(id, component.deepCopy());
        }
        return out;
    }

    private Map<String, JsonNode> parseComponentMap(JsonNode components) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        if (components == null || !components.isArray()) {
            return out;
        }
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = trimToNull(item.path("id").asText(null));
            if (id == null) {
                continue;
            }
            out.put(id, item.deepCopy());
        }
        return out;
    }

    private ComponentMergeResult mergeComponentsByBase(
            Map<String, JsonNode> baseComponents,
            Map<String, JsonNode> currentComponents,
            Map<String, JsonNode> incomingComponents) {
        Set<String> orderedIds = new LinkedHashSet<>();
        orderedIds.addAll(incomingComponents.keySet());
        orderedIds.addAll(currentComponents.keySet());
        orderedIds.addAll(baseComponents.keySet());

        List<String> conflicts = new ArrayList<>();
        Map<String, JsonNode> mergedById = new LinkedHashMap<>();
        for (String id : orderedIds) {
            JsonNode base = baseComponents.get(id);
            JsonNode current = currentComponents.get(id);
            JsonNode incoming = incomingComponents.get(id);
            boolean remoteChanged = !jsonEquals(current, base);
            boolean localChanged = !jsonEquals(incoming, base);
            if (remoteChanged && localChanged && !jsonEquals(current, incoming)) {
                conflicts.add(id);
                continue;
            }
            JsonNode selected = localChanged ? incoming : current;
            if (selected != null && !selected.isNull()) {
                mergedById.put(id, selected.deepCopy());
            }
        }

        com.fasterxml.jackson.databind.node.ArrayNode mergedArray = objectMapper.createArrayNode();
        for (String id : orderedIds) {
            JsonNode node = mergedById.get(id);
            if (node != null) {
                mergedArray.add(node);
            }
        }
        return new ComponentMergeResult(mergedArray, conflicts);
    }

    private ScalarMergeResult mergeTextScalar(
            String field,
            String currentValue,
            JsonNode incomingNode,
            JsonNode baseScreen,
            List<String> conflicts) {
        String incoming = trimToNull(incomingNode.isMissingNode() || incomingNode.isNull() ? null : incomingNode.asText(null));
        String base = trimToNull(baseScreen.path(field).asText(null));
        return mergeScalar(field, currentValue, incoming, base, conflicts);
    }

    private ScalarMergeResult mergeNullableTextScalar(
            String field,
            String currentValue,
            JsonNode incomingNode,
            JsonNode baseScreen,
            List<String> conflicts) {
        String incoming = incomingNode.isMissingNode() || incomingNode.isNull() ? null : trimToNull(incomingNode.asText(null));
        String base = baseScreen.path(field).isNull() ? null : trimToNull(baseScreen.path(field).asText(null));
        return mergeScalar(field, currentValue, incoming, base, conflicts);
    }

    private ScalarMergeResult mergeIntScalar(
            String field,
            Integer currentValue,
            JsonNode incomingNode,
            JsonNode baseScreen,
            List<String> conflicts) {
        Integer incoming = incomingNode.isInt() || incomingNode.isLong() ? incomingNode.asInt() : null;
        Integer base = baseScreen.path(field).isInt() || baseScreen.path(field).isLong() ? baseScreen.path(field).asInt() : null;
        boolean remoteChanged = !valueEquals(currentValue, base);
        boolean localChanged = !valueEquals(incoming, base);
        if (remoteChanged && localChanged && !valueEquals(currentValue, incoming)) {
            conflicts.add(field);
            return new ScalarMergeResult(currentValue, null);
        }
        Integer merged = localChanged ? incoming : currentValue;
        return new ScalarMergeResult(merged == null ? 0 : merged, null);
    }

    private ScalarMergeResult mergeScalar(
            String field,
            String currentValue,
            String incomingValue,
            String baseValue,
            List<String> conflicts) {
        boolean remoteChanged = !valueEquals(currentValue, baseValue);
        boolean localChanged = !valueEquals(incomingValue, baseValue);
        if (remoteChanged && localChanged && !valueEquals(currentValue, incomingValue)) {
            conflicts.add(field);
            return new ScalarMergeResult(null, currentValue);
        }
        String merged = localChanged ? incomingValue : currentValue;
        return new ScalarMergeResult(null, merged);
    }

    private static boolean jsonEquals(JsonNode left, JsonNode right) {
        if (left == null || left.isMissingNode() || left.isNull()) {
            return right == null || right.isMissingNode() || right.isNull();
        }
        if (right == null || right.isMissingNode() || right.isNull()) {
            return false;
        }
        return left.equals(right);
    }

    private static boolean valueEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    private Instant parseInstantSafe(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignore) {
            return null;
        }
    }

    private static class ConflictResolution {
        private final JsonNode mergedBody;
        private final ObjectNode conflictPayload;

        private ConflictResolution(JsonNode mergedBody, ObjectNode conflictPayload) {
            this.mergedBody = mergedBody;
            this.conflictPayload = conflictPayload;
        }

        static ConflictResolution pass(JsonNode mergedBody) {
            return new ConflictResolution(mergedBody, null);
        }

        static ConflictResolution blocked(ObjectNode payload) {
            return new ConflictResolution(null, payload);
        }
    }

    private static class ComponentMergeResult {
        private final JsonNode mergedComponents;
        private final List<String> conflictComponentIds;

        private ComponentMergeResult(JsonNode mergedComponents, List<String> conflictComponentIds) {
            this.mergedComponents = mergedComponents;
            this.conflictComponentIds = conflictComponentIds == null ? List.of() : conflictComponentIds;
        }
    }

    private static class ScalarMergeResult {
        private final Integer intValue;
        private final String textValue;

        private ScalarMergeResult(Integer intValue, String textValue) {
            this.intValue = intValue;
            this.textValue = textValue;
        }
    }

    private JsonNode parseComponents(String componentsJson) {
        if (componentsJson != null && !componentsJson.isBlank()) {
            try {
                JsonNode components = objectMapper.readTree(componentsJson);
                if (components == null || components.isNull()) {
                    return objectMapper.createArrayNode();
                }
                return components;
            } catch (Exception e) {
                return objectMapper.createArrayNode();
            }
        }
        return objectMapper.createArrayNode();
    }

    private JsonNode parseGlobalVariables(String variablesJson) {
        if (variablesJson != null && !variablesJson.isBlank()) {
            try {
                JsonNode variables = objectMapper.readTree(variablesJson);
                if (variables != null && variables.isArray()) {
                    return variables;
                }
            } catch (Exception ignore) {
                return objectMapper.createArrayNode();
            }
        }
        return objectMapper.createArrayNode();
    }

    private ObjectNode buildHealthStats(String componentsJson) {
        JsonNode components = parseComponents(componentsJson);
        int componentCount = components.isArray() ? components.size() : 0;
        int dataBound = 0;
        int refreshable = 0;
        int interactive = 0;
        int heavy = 0;
        int warmupEligible = 0;
        Set<String> types = new HashSet<>();
        List<String> recommendations = new ArrayList<>();

        if (components.isArray()) {
            for (JsonNode component : components) {
                if (component == null || !component.isObject()) {
                    continue;
                }
                String type = trimToNull(component.path("type").asText(null));
                if (type != null) {
                    types.add(type);
                }
                if (isHeavyType(type)) {
                    heavy++;
                }

                JsonNode dataSource = component.path("dataSource");
                if (dataSource != null && dataSource.isObject()) {
                    String dsType = resolveSourceType(dataSource);
                    if (dsType != null && !"static".equalsIgnoreCase(dsType)) {
                        dataBound++;
                    }
                    if ("sql".equalsIgnoreCase(dsType)) {
                        JsonNode sqlConfig = resolveSqlConfig(dataSource);
                        long dbId = parseDatabaseId(sqlConfig);
                        String query = trimToNull(sqlConfig == null ? null : sqlConfig.path("query").asText(null));
                        if (dbId > 0 && query != null && !query.contains("{{")) {
                            warmupEligible++;
                        }
                    }
                    int refreshInterval = parsePositiveInt(dataSource.path("refreshInterval"));
                    if (refreshInterval <= 0) {
                        refreshInterval = parsePositiveInt(dataSource.path("cardConfig").path("refreshInterval"));
                    }
                    if (refreshInterval > 0) {
                        refreshable++;
                    }
                }

                JsonNode interaction = component.path("interaction");
                if (interaction != null && interaction.isObject()) {
                    boolean enabled = interaction.path("enabled").asBoolean(false);
                    int mappings = interaction.path("mappings").isArray() ? interaction.path("mappings").size() : 0;
                    if (enabled && mappings > 0) {
                        interactive++;
                    }
                }
            }
        }

        int complexity = componentCount + dataBound * 3 + refreshable * 2 + interactive * 2 + heavy * 2;
        boolean pass = componentCount <= 100 && complexity <= 260;

        if (componentCount > 100) {
            recommendations.add("组件数超过100，建议拆分子屏或按场景分页。");
        }
        if (dataBound > 50) {
            recommendations.add("数据绑定组件较多，建议统一刷新频率并开启缓存预热。");
        }
        if (refreshable > 30) {
            recommendations.add("高频刷新组件较多，建议提升刷新间隔或分层刷新。");
        }
        if (heavy > 20) {
            recommendations.add("重组件占比较高，建议减少iframe/video/map并使用静态快照。");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("当前大屏达到P0性能与稳定性基线。");
        }

        ObjectNode node = objectMapper.createObjectNode();
        node.put("componentCount", componentCount);
        node.put("dataBoundComponentCount", dataBound);
        node.put("refreshableComponentCount", refreshable);
        node.put("interactiveComponentCount", interactive);
        node.put("heavyComponentCount", heavy);
        node.put("warmupEligibleDatabaseSources", warmupEligible);
        node.put("uniqueComponentTypes", types.size());
        node.put("estimatedComplexity", complexity);
        node.put("pass", pass);
        node.putPOJO("recommendations", recommendations);
        return node;
    }

    private static boolean isHeavyType(String type) {
        if (type == null) {
            return false;
        }
        return "table".equals(type)
                || "iframe".equals(type)
                || "video".equals(type)
                || "map-chart".equals(type)
                || "flyline-chart".equals(type);
    }

    private static int parsePositiveInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0;
        }
        if (node.isInt() || node.isLong()) {
            return Math.max(0, node.asInt(0));
        }
        if (node.isTextual()) {
            String text = trimToNull(node.asText(null));
            if (text == null) {
                return 0;
            }
            try {
                return Math.max(0, Integer.parseInt(text));
            } catch (NumberFormatException ignore) {
                return 0;
            }
        }
        return 0;
    }

    private static String resolveSourceType(JsonNode dataSource) {
        if (dataSource == null || !dataSource.isObject()) {
            return null;
        }
        String sourceType = trimToNull(dataSource.path("sourceType").asText(null));
        if (sourceType != null) {
            return sourceType;
        }
        String type = trimToNull(dataSource.path("type").asText(null));
        if ("database".equalsIgnoreCase(type)) {
            return "sql";
        }
        return type;
    }

    private static JsonNode resolveSqlConfig(JsonNode dataSource) {
        if (dataSource == null || !dataSource.isObject()) {
            return null;
        }
        JsonNode sqlConfig = dataSource.path("sqlConfig");
        if (sqlConfig != null && sqlConfig.isObject()) {
            return sqlConfig;
        }
        JsonNode legacy = dataSource.path("databaseConfig");
        if (legacy != null && legacy.isObject()) {
            return legacy;
        }
        return null;
    }

    private static long parseDatabaseId(JsonNode dbConfig) {
        if (dbConfig == null || dbConfig.isMissingNode()) {
            return 0L;
        }
        long dbId = dbConfig.path("databaseId").asLong(0L);
        if (dbId > 0) {
            return dbId;
        }
        String connectionId = trimToNull(dbConfig.path("connectionId").asText(null));
        if (connectionId == null) {
            return 0L;
        }
        try {
            long parsed = Long.parseLong(connectionId);
            return Math.max(parsed, 0L);
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private JsonNode parseObject(String json) {
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node != null && !node.isNull()) {
                    return node;
                }
            } catch (Exception ignore) {
                return objectMapper.createObjectNode();
            }
        }
        return objectMapper.createObjectNode();
    }

    private ObjectNode buildExportScreenSpec(AnalyticsScreen screen, AnalyticsScreenVersion effectiveVersion) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", 2);
        if (effectiveVersion != null) {
            node.put("name", effectiveVersion.getName());
            node.put("description", effectiveVersion.getDescription());
            node.put("width", effectiveVersion.getWidth());
            node.put("height", effectiveVersion.getHeight());
            node.put("theme", effectiveVersion.getTheme());
            node.put("backgroundColor", effectiveVersion.getBackgroundColor());
            node.put("backgroundImage", effectiveVersion.getBackgroundImage());
            node.set("components", parseComponents(effectiveVersion.getComponentsJson()));
            node.set("globalVariables", parseGlobalVariables(effectiveVersion.getVariablesJson()));
            return node;
        }
        node.put("name", screen.getName());
        node.put("description", screen.getDescription());
        node.put("width", screen.getWidth());
        node.put("height", screen.getHeight());
        node.put("theme", screen.getTheme());
        node.put("backgroundColor", screen.getBackgroundColor());
        node.put("backgroundImage", screen.getBackgroundImage());
        node.set("components", parseComponents(screen.getComponentsJson()));
        node.set("globalVariables", parseGlobalVariables(screen.getVariablesJson()));
        return node;
    }

    private String computeSpecDigest(JsonNode node) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(objectMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                int v = b & 0xff;
                if (v < 0x10) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(v));
            }
            return hex.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String requestIdFrom(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String[] candidates = {
            request.getHeader("X-Request-Id"),
            request.getHeader("X-Request-ID"),
            request.getHeader("X-Correlation-Id")
        };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
    }

    private ResponseEntity<String> forbidden() {
        return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
    }

    private ResponseEntity<ObjectNode> lockConflict(ScreenEditLockService.LockSnapshot lock) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("code", "SCREEN_EDIT_LOCKED");
        body.put("message", "Screen is currently locked by another editor");
        body.set("lock", toLockSnapshot(lock));
        return ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private ObjectNode toLockSnapshot(ScreenEditLockService.LockSnapshot lock) {
        ObjectNode node = objectMapper.createObjectNode();
        if (lock == null) {
            node.put("active", false);
            return node;
        }
        node.put("active", lock.active());
        node.putPOJO("screenId", lock.screenId());
        node.putPOJO("ownerId", lock.ownerId());
        if (lock.ownerName() != null) {
            node.put("ownerName", lock.ownerName());
        } else {
            node.putNull("ownerName");
        }
        node.put("mine", lock.mine());
        node.putPOJO("acquiredAt", lock.acquiredAt());
        node.putPOJO("heartbeatAt", lock.heartbeatAt());
        node.putPOJO("expireAt", lock.expireAt());
        node.put("ttlSeconds", lock.ttlSeconds());
        return node;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private static double normalizeExportPixelRatio(double raw, String format) {
        double fallback = "pdf".equals(format) ? 1.5d : 2.0d;
        if (Double.isNaN(raw) || Double.isInfinite(raw) || raw <= 0d) {
            return fallback;
        }
        if (raw < 1.0d) {
            return 1.0d;
        }
        return Math.min(raw, 3.0d);
    }

    private static int filterExportComponentsByDevice(ObjectNode screenSpec, String device) {
        if (screenSpec == null || device == null || device.isBlank()) {
            return 0;
        }
        JsonNode componentsNode = screenSpec.path("components");
        if (!(componentsNode instanceof ArrayNode components)) {
            return 0;
        }
        ArrayNode filtered = components.arrayNode();
        int hidden = 0;
        for (JsonNode item : components) {
            if (!isVisibleForDevice(item, device)) {
                hidden += 1;
                continue;
            }
            filtered.add(item.deepCopy());
        }
        screenSpec.set("components", filtered);
        return hidden;
    }

    private static boolean isVisibleForDevice(JsonNode component, String device) {
        if (component == null || !component.isObject()) {
            return true;
        }
        JsonNode visibleOn = component.path("config").path("visibleOn");
        if (!visibleOn.isArray() || visibleOn.isEmpty()) {
            return true;
        }
        for (JsonNode node : visibleOn) {
            String value = trimToNull(node == null ? null : node.asText(null));
            if (value != null && value.equalsIgnoreCase(device)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseAiContext(JsonNode contextNode) {
        if (contextNode == null || !contextNode.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : contextNode) {
            String text = trimToNull(item == null ? null : item.asText(null));
            if (text != null) {
                out.add(text.length() > 400 ? text.substring(0, 400) : text);
                if (out.size() >= 24) {
                    break;
                }
            }
        }
        return out;
    }
}
