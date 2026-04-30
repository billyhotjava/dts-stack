package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAccess;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenVersionRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.ScreenAuditService;
import com.yuzhi.dts.analytics.service.ScreenEditLockService;
import com.yuzhi.dts.analytics.service.ScreenWarmupService;
import com.yuzhi.dts.analytics.service.ScreenAiGenerationService;
import com.yuzhi.dts.analytics.service.ScreenComplianceService;
import com.yuzhi.dts.analytics.service.ScreenServerRenderExportService;
import com.yuzhi.dts.analytics.service.ScreenOwnershipService;
import com.yuzhi.dts.analytics.service.ScreenPermissionService;
import com.yuzhi.dts.analytics.service.ScreenSpecValidator;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    private static final Logger LOG = LoggerFactory.getLogger(ScreenResource.class);
    private static final String SCREEN_VERSION_STATUS_PUBLISHED = "PUBLISHED";

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsScreenVersionRepository screenVersionRepository;
    private final AnalyticsUserRepository userRepository;
    private final ScreenPermissionService screenPermissionService;
    private final ScreenOwnershipService screenOwnershipService;
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
            AnalyticsUserRepository userRepository,
            ScreenPermissionService screenPermissionService,
            ScreenOwnershipService screenOwnershipService,
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
        this.userRepository = userRepository;
        this.screenPermissionService = screenPermissionService;
        this.screenOwnershipService = screenOwnershipService;
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

        List<Long> accessibleIds = screenPermissionService.listAccessibleScreenIds(user.orElseThrow(), context);

        List<AnalyticsScreen> screens;
        if (screenPermissionService.isAllAccessible(accessibleIds)) {
            screens = screenRepository.findAllByArchivedFalseOrderByIdDesc();
        } else if (accessibleIds.isEmpty()) {
            screens = List.of();
        } else {
            screens = screenRepository.findAllByIdInAndArchivedFalse(accessibleIds);
        }

        List<ObjectNode> result = screens.stream()
                .map(screen -> {
                    ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
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

        ObjectNode result = screenAiGenerationService.generate(prompt, width, height);
        result.putPOJO("generatedBy", user.orElseThrow().getId());
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
        ObjectNode result = screenAiGenerationService.revise(prompt, screenSpec, context, applyChanges);
        result.putPOJO("generatedBy", user.orElseThrow().getId());
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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
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
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).canRead()) {
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
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).canRead()) {
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
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).isOwner()) {
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
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).canRead()) {
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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
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
        if (exportApprovalRequired && !permissions.isOwner()) {
            ObjectNode denied = objectMapper.createObjectNode();
            denied.put("code", "SCREEN_EXPORT_APPROVAL_REQUIRED");
            denied.put("retryable", false);
            denied.put("requestId", requestId);
            denied.put("message", "当前大屏导出受合规策略限制，需要管理员审批后再导出");
            denied.put("screenId", screen.getId());
            denied.put("format", format);
            denied.put("mode", mode);
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.export.denied", null, denied, requestId);
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
        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.export.prepare", null, auditPayload, requestId);
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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
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
        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), action, null, payload, requestId);
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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
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
        if (exportApprovalRequired && !permissions.isOwner()) {
            ObjectNode denied = objectMapper.createObjectNode();
            denied.put("code", "SCREEN_EXPORT_APPROVAL_REQUIRED");
            denied.put("retryable", false);
            denied.put("requestId", requestId);
            denied.put("message", "当前大屏导出受合规策略限制，需要管理员审批后再导出");
            denied.put("screenId", screen.getId());
            denied.put("format", format);
            denied.put("mode", mode);
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.export.denied", null, denied, requestId);
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
            auditPayload.put("renderEngine", "server-heuristic-v2");
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.export.server.render", null, auditPayload, requestId);

            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .header("X-Request-Id", requestId == null ? "" : requestId)
                    .header("X-Screen-Spec-Digest", specDigest == null ? "" : specDigest)
                    .header("X-Screen-Resolved-Mode", resolvedMode)
                    .header("X-Screen-Render-Engine", "server-heuristic-v2")
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
            failure.put("renderEngine", "server-heuristic-v2");
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.export.server.render.failed", null, failure, requestId);
            return ResponseEntity.internalServerError().contentType(MediaType.APPLICATION_JSON).body(failure);
        }
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

        // Sprint-24 F3/T03：创建大屏强制选择密级，从源头消除 classification=null 裸屏。
        // 历史数据通过 F4 盘点入口暴露 + owner 主动补登，不在此回填。
        String rawClassification = body == null ? null : body.path("classification").asText(null);
        String classificationUpper;
        try {
            classificationUpper = normalizeRequiredClassification(rawClassification);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }

        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setName(name);
        screen.setClassification(classificationUpper);
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
        screen.setPagesJson(body != null && body.has("pages") ? body.path("pages").toString() : "[]");
        screen.setCarouselJson(body != null && body.has("carouselConfig") && body.path("carouselConfig").isObject()
                ? body.path("carouselConfig").toString()
                : null);
        // Sprint-12 F2/T03: 透传 v2Spec（schemaVersion / layout / referenceViewport）
        screen.setV2SpecJson(body != null && body.has("v2Spec") && body.path("v2Spec").isObject()
                ? body.path("v2Spec").toString()
                : null);
        screen.setCreatorId(user.orElseThrow().getId());
        screen.setArchived(false);

        screen = screenRepository.save(screen);

        AnalyticsUser creator = user.orElseThrow();
        String creatorGranteeId = creator.getPlatformUsername() != null && !creator.getPlatformUsername().isBlank()
                ? creator.getPlatformUsername() : String.valueOf(creator.getId());
        screenOwnershipService.createGrant(screen.getId(), "USER", creatorGranteeId, "OWNER", creator.getId());

        ScreenPermissionService.PermissionSnapshot permissions = ScreenPermissionService.PermissionSnapshot.all();
        ObjectNode detail = toDetailResponse(screen, null, null, "draft", permissions);
        applySpecWarnings(detail, specValidation.warnings());

        // Sprint-12 follow-up: distinguish v1→v2 migration from a regular create.
        // The frontend V1LegacyBanner posts the source screen id under `migrationFrom` so that
        // the audit trail records "screen.migrate" for the new copy and downstream tooling can
        // join the old/new pair.
        boolean isMigration = body != null && body.has("migrationFrom") && !body.path("migrationFrom").isNull();
        String createAction = isMigration ? "screen.migrate" : "screen.create";
        ObjectNode auditDetail = detail.deepCopy();
        if (isMigration) {
            auditDetail.put("migrationFrom", body.path("migrationFrom").asText());
            auditDetail.put("schemaVersionFrom", 1);
            auditDetail.put("schemaVersionTo", 2);
        }
        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), createAction, null, auditDetail, requestIdFrom(request));

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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!permissions.canEdit()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.orElseThrow().getId());
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
        if (effectiveBody != null && effectiveBody.has("pages")) {
            screen.setPagesJson(effectiveBody.path("pages").toString());
        }
        if (effectiveBody != null && effectiveBody.has("carouselConfig")) {
            screen.setCarouselJson(effectiveBody.path("carouselConfig").isObject()
                    ? effectiveBody.path("carouselConfig").toString()
                    : null);
        }
        // Sprint-12 F2/T03: 透传 v2Spec（客户端可 explicit set null 清理 v2 扩展）
        if (effectiveBody != null && effectiveBody.has("v2Spec")) {
            screen.setV2SpecJson(effectiveBody.path("v2Spec").isObject()
                    ? effectiveBody.path("v2Spec").toString()
                    : null);
        }

        screenRepository.save(screen);
        AnalyticsScreenVersion currentPublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode detail = toDetailResponse(screen, null, currentPublished, "draft", permissions);
        applySpecWarnings(detail, specValidation.warnings());

        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.update", before, detail, requestIdFrom(request));

        return ResponseEntity.ok(detail);
    }

    @PostMapping(path = "/{id}/publish", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> publish(
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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!permissions.canEdit()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.orElseThrow().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        String classification = body == null ? null : trimToNull(body.path("classification").asText(null));
        if (classification != null) {
            String upper = classification.toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL").contains(upper)) {
                return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.createObjectNode().put("error", "classification must be PUBLIC, INTERNAL, SECRET, or CONFIDENTIAL"));
            }
            screen.setClassification(upper);
            screenRepository.save(screen);
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
                user.orElseThrow().getId(),
                nextVersionNo,
                true,
                Instant.now());
        version = screenVersionRepository.save(version);

        ObjectNode response = objectMapper.createObjectNode();
        ObjectNode detail = toDetailResponse(screen, version, version, "published", permissions);
        response.set("screen", detail);
        response.set("version", toVersionResponse(version));

        ScreenWarmupService.WarmupSummary warmupSummary = screenWarmupService.warmupForPublishedScreen(screen, user.orElseThrow().getId());
        response.putPOJO("warmup", warmupSummary);

        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.publish", before, response, requestIdFrom(request));

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
        ScreenPermissionService.PermissionSnapshot permissions = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!permissions.canEdit()) {
            return forbidden();
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.orElseThrow().getId());
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

        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.rollback", before, response, requestIdFrom(request));

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
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).isOwner()) {
            return ResponseEntity.status(403).contentType(MediaType.APPLICATION_JSON).body(
                objectMapper.createObjectNode().put("error", "Only the owner can delete this screen"));
        }
        ScreenEditLockService.LockSnapshot blockingLock =
                screenEditLockService.currentBlockingLock(screen.getId(), user.orElseThrow().getId());
        if (blockingLock != null) {
            return lockConflict(blockingLock);
        }

        ObjectNode before = toAuditScreenSnapshot(
                screen,
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null));

        screen.setArchived(true);
        screenRepository.save(screen);
        screenEditLockService.release(screen.getId(), user.orElseThrow().getId());
        screenOwnershipService.removeAllGrants(screen.getId());

        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.delete", before, null, requestIdFrom(request));

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

        PlatformContext context = PlatformContext.from(request);
        String dept = trimToNull(request.getHeader("X-DTS-Dept"));
        String classification = trimToNull(request.getHeader("X-DTS-Classification"));
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).isOwner()) {
            return forbidden();
        }

        // Published-version gate removed: allow creating public links for unpublished screens.
        // The public viewer page will show a "not yet published" message if there is no content.

        AnalyticsPublicLink beforeLink = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        ObjectNode before = toPublicLinkPolicyResponse(beforeLink == null ? null : beforeLink.getPublicUuid(), beforeLink);

        String uuid;
        try {
            uuid = publicLinkService.getOrCreateScoped(
                    PublicLinkService.MODEL_SCREEN, id, user.orElseThrow().getId(), dept, classification);
        } catch (IllegalStateException e) {
            return forbidden();
        }

        AnalyticsPublicLink link = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        if (link != null) {
            applyPublicLinkPolicyFromBody(link, body);
            link = publicLinkService.save(link);
        }

        ObjectNode after = toPublicLinkPolicyResponse(uuid, link);
        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.public_link.create", before, after, requestIdFrom(request));

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

        PlatformContext context = PlatformContext.from(request);
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).isOwner()) {
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

        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.public_link.policy", before, after, requestIdFrom(request));

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

        PlatformContext context = PlatformContext.from(request);
        if (!screenPermissionService.snapshot(screen, user.orElseThrow(), context).isOwner()) {
            return forbidden();
        }

        AnalyticsPublicLink beforeLink = publicLinkService.findByModelAndModelId(PublicLinkService.MODEL_SCREEN, id).orElse(null);
        ObjectNode before = toPublicLinkPolicyResponse(beforeLink == null ? null : beforeLink.getPublicUuid(), beforeLink);

        publicLinkService.delete(PublicLinkService.MODEL_SCREEN, id);
        screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "screen.public_link.delete", before, null, requestIdFrom(request));

        return ResponseEntity.noContent().build();
    }

    // ── Grant management (local analytics_screen_access table) ──────────

    @GetMapping(path = "/{id}/grants", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getGrants(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) return unauthorized();

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) return ResponseEntity.notFound().build();

        PlatformContext context = PlatformContext.from(request);
        ScreenPermissionService.PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!perms.isOwner()) return forbidden();

        // H3：把异常按类型映射到合适的状态码，避免 raw message 泄露内部信息。
        try {
            List<Map<String, Object>> grants = screenOwnershipService.listGrants(screen.getId());
            return ResponseEntity.ok(grants);
        } catch (DataAccessException ex) {
            LOG.warn("DB error listing grants screenId={}: {}", screen.getId(), ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        } catch (Exception ex) {
            LOG.error("Unexpected error listing grants screenId={}", screen.getId(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        }
    }

    @PutMapping(path = "/{id}/grants", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addGrant(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) return unauthorized();

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) return ResponseEntity.notFound().build();

        PlatformContext context = PlatformContext.from(request);
        ScreenPermissionService.PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!perms.isOwner()) return forbidden();

        // Parse body: {
        //   granteeType: "USER"|"ROLE",
        //   granteeId: "...",
        //   permission: "VIEWER"|"MANAGER"|"READ"|"EDIT",
        //   levelOverride: boolean (optional, only meaningful for VIEWER)
        // }
        String granteeType = body != null ? trimToNull(body.path("granteeType").asText(null)) : null;
        String granteeId = body != null ? trimToNull(body.path("granteeId").asText(null)) : null;
        String permission = body != null ? trimToNull(body.path("permission").asText(null)) : null;
        boolean levelOverride = body != null && body.path("levelOverride").asBoolean(false);

        if (granteeType == null || granteeId == null || permission == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "granteeType, granteeId, and permission are required"));
        }
        if (!Set.of("USER", "ROLE").contains(granteeType.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "granteeType must be USER or ROLE"));
        }
        if (!Set.of("VIEWER", "MANAGER", "READ", "EDIT").contains(permission.toUpperCase())) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be VIEWER or MANAGER (READ/EDIT accepted for backward compatibility)"));
        }

        // Translate legacy permission values to the new local table vocabulary
        String resolvedPermission = switch (permission.toUpperCase()) {
            case "READ" -> "VIEWER";
            case "EDIT" -> "MANAGER";
            default -> permission.toUpperCase();
        };
        if (!Set.of("VIEWER", "MANAGER").contains(resolvedPermission)) {
            return ResponseEntity.badRequest().body(Map.of("error", "permission must be VIEWER or MANAGER"));
        }
        // 仅 VIEWER 类 grant 才能携带 level_override；MANAGER 本就豁免密级。
        // service 层也会兜底强制（双重保险）；这里直接拒绝以给前端清晰错误信息。
        if (levelOverride && !"VIEWER".equals(resolvedPermission)) {
            return ResponseEntity.badRequest().body(Map.of("error", "levelOverride is only allowed on VIEWER grants"));
        }
        // H2：MANAGER 不再传递。授 MANAGER 仅大屏真 owner 或 superuser 可以做，
        // 与 dts-platform 端 DashboardAccessGuard.canGrant 的策略 1 对齐——避免
        // 被授权的 MANAGER 互相提权造帝国。VIEWER 仍然允许任何 MANAGER/owner 授予。
        if ("MANAGER".equals(resolvedPermission) && !isManagerGrantAllowed(screen, user.orElseThrow())) {
            return ResponseEntity
                .status(403)
                .body(Map.of("error", "only screen owner or superuser may grant MANAGER permission"));
        }
        try {
            Long grantedById = user.orElseThrow().getId();
            AnalyticsScreenAccess grant = screenOwnershipService.createGrant(
                screen.getId(), granteeType.toUpperCase(), granteeId, resolvedPermission, grantedById, levelOverride);
            java.util.LinkedHashMap<String, Object> grantMap = new java.util.LinkedHashMap<>();
            grantMap.put("id", grant.getId());
            grantMap.put("screenId", grant.getScreenId());
            grantMap.put("granteeType", grant.getGranteeType());
            grantMap.put("granteeId", grant.getGranteeId());
            grantMap.put("permission", grant.getPermission());
            grantMap.put("levelOverride", grant.isLevelOverride());
            grantMap.put("grantedBy", grant.getGrantedBy() != null ? grant.getGrantedBy() : "");
            grantMap.put("grantedAt", grant.getGrantedAt() != null ? grant.getGrantedAt().toString() : "");
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "grant.add", null,
                objectMapper.valueToTree(grantMap), requestIdFrom(request));
            return ResponseEntity.ok(grantMap);
        } catch (IllegalArgumentException ex) {
            // H3：业务校验类异常应当 400 而不是 503，且不暴露内部细节。
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (DataAccessException ex) {
            LOG.warn("DB error creating grant screenId={}: {}", screen.getId(), ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        } catch (Exception ex) {
            LOG.error("Unexpected error creating grant screenId={}", screen.getId(), ex);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        }
    }

    /**
     * 大屏密级独立修改入口。
     *
     * <p>大屏 PUT /{id} 只接受 ScreenWritePayload（结构 / 主题 / 组件等），不含
     * classification；publish endpoint 虽然能改 classification 但会触发版本切换，
     * 不适合日常调整。本端点提供轻量原地修改，仅 owner 可调，写审计。
     */
    @PatchMapping(path = "/{id}/classification", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateClassification(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) return unauthorized();

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) return ResponseEntity.notFound().build();

        PlatformContext context = PlatformContext.from(request);
        ScreenPermissionService.PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!perms.isOwner()) return forbidden();

        String classification = body == null ? null : trimToNull(body.path("classification").asText(null));
        if (classification == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "classification is required"));
        }
        String upper = classification.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL").contains(upper)) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "classification must be PUBLIC, INTERNAL, SECRET, or CONFIDENTIAL"));
        }
        String before = screen.getClassification();
        if (upper.equals(before)) {
            return ResponseEntity.ok(Map.of("classification", upper, "changed", false));
        }
        screen.setClassification(upper);
        screenRepository.save(screen);
        screenAuditService.log(
            screen.getId(),
            user.orElseThrow().getId(),
            "screen.classification.update",
            Map.of("before", before == null ? "" : before),
            Map.of("after", upper),
            requestIdFrom(request));
        return ResponseEntity.ok(Map.of("classification", upper, "changed", true));
    }

    @DeleteMapping(path = "/{id}/grants/{grantId}")
    public ResponseEntity<?> revokeGrant(
            @PathVariable("id") long id,
            @PathVariable("grantId") long grantId,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) return unauthorized();

        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) return ResponseEntity.notFound().build();

        PlatformContext context = PlatformContext.from(request);
        ScreenPermissionService.PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), context);
        if (!perms.isOwner()) return forbidden();

        try {
            boolean deleted = screenOwnershipService.revokeGrantForScreen(grantId, screen.getId());
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }
            screenAuditService.log(screen.getId(), user.orElseThrow().getId(), "grant.revoke",
                Map.of("grantId", grantId), null, requestIdFrom(request));
            return ResponseEntity.ok(Map.of("deleted", true));
        } catch (DataAccessException ex) {
            LOG.warn("DB error revoking grant screenId={} grantId={}: {}", screen.getId(), grantId, ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        } catch (Exception ex) {
            LOG.error("Unexpected error revoking grant screenId={} grantId={}", screen.getId(), grantId, ex);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        }
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
        version.setPagesJson(screen.getPagesJson());
        version.setCarouselJson(screen.getCarouselJson());
        version.setV2SpecJson(screen.getV2SpecJson());
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
        screen.setPagesJson(version.getPagesJson());
        screen.setCarouselJson(version.getCarouselJson());
        screen.setV2SpecJson(version.getV2SpecJson());
    }

    private ObjectNode toListResponse(
            AnalyticsScreen screen,
            AnalyticsScreenVersion currentPublishedVersion,
            ScreenPermissionService.PermissionSnapshot permissions) {
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
        node.put("canPublish", permissions.canEdit());
        node.put("canManage", permissions.isOwner());
        node.put("canDelete", permissions.isOwner());
        node.put("isOwner", permissions.isOwner());
        node.put("classification", screen.getClassification());
        node.put("ownerDeptCode", screen.getOwnerDeptCode());
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

    private ObjectNode toDetailResponse(
            AnalyticsScreen screen,
            AnalyticsScreenVersion effectiveVersion,
            AnalyticsScreenVersion currentPublishedVersion,
            String sourceMode,
            ScreenPermissionService.PermissionSnapshot permissions) {
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
            node.set("pages", parsePages(effectiveVersion.getPagesJson()));
            JsonNode carouselConfig = parseCarouselConfig(effectiveVersion.getCarouselJson());
            if (carouselConfig != null) {
                node.set("carouselConfig", carouselConfig);
            }
            // Sprint-12 F2/T03: v2Spec 从 version 快照回吐
            JsonNode v2Spec = parseV2Spec(effectiveVersion.getV2SpecJson());
            if (v2Spec != null) {
                node.set("v2Spec", v2Spec);
            }
        } else {
            node.put("backgroundColor", screen.getBackgroundColor());
            node.put("backgroundImage", screen.getBackgroundImage());
            node.set("components", parseComponents(screen.getComponentsJson()));
            node.set("globalVariables", parseGlobalVariables(screen.getVariablesJson()));
            node.set("pages", parsePages(screen.getPagesJson()));
            JsonNode carouselConfig = parseCarouselConfig(screen.getCarouselJson());
            if (carouselConfig != null) {
                node.set("carouselConfig", carouselConfig);
            }
            // Sprint-12 F2/T03: v2Spec 从 draft screen 回吐
            JsonNode v2Spec = parseV2Spec(screen.getV2SpecJson());
            if (v2Spec != null) {
                node.set("v2Spec", v2Spec);
            }
        }
        node.put("sourceMode", sourceMode);
        return node;
    }

    /** Sprint-12 F2/T03: 解析透传存储的 v2 扩展 JSON。解析失败返回 null。 */
    private JsonNode parseV2Spec(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonNode parsed = objectMapper.readTree(raw);
            return parsed.isObject() ? parsed : null;
        } catch (Exception ex) {
            return null;
        }
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
        node.set("pages", parsePages(screen.getPagesJson()));
        JsonNode carouselConfig = parseCarouselConfig(screen.getCarouselJson());
        if (carouselConfig != null) {
            node.set("carouselConfig", carouselConfig);
        }
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
            node.set("pages", parsePages(effectiveVersion.getPagesJson()));
            JsonNode carouselConfig = parseCarouselConfig(effectiveVersion.getCarouselJson());
            if (carouselConfig != null) {
                node.set("carouselConfig", carouselConfig);
            }
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
        node.set("pages", parsePages(screen.getPagesJson()));
        JsonNode carouselConfig = parseCarouselConfig(screen.getCarouselJson());
        if (carouselConfig != null) {
            node.set("carouselConfig", carouselConfig);
        }
        return node;
    }

    private JsonNode parsePages(String pagesJson) {
        if (pagesJson != null && !pagesJson.isBlank()) {
            try {
                JsonNode pages = objectMapper.readTree(pagesJson);
                if (pages == null || pages.isNull()) {
                    return objectMapper.createArrayNode();
                }
                return pages.isArray() ? pages : objectMapper.createArrayNode();
            } catch (Exception ignore) {
                return objectMapper.createArrayNode();
            }
        }
        return objectMapper.createArrayNode();
    }

    private JsonNode parseCarouselConfig(String carouselJson) {
        if (carouselJson != null && !carouselJson.isBlank()) {
            try {
                JsonNode carousel = objectMapper.readTree(carouselJson);
                if (carousel == null || carousel.isNull() || !carousel.isObject()) {
                    return null;
                }
                return carousel;
            } catch (Exception ignore) {
                return null;
            }
        }
        return null;
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

    /**
     * H2：判定 caller 是否被允许授予 MANAGER 权限。仅大屏真正的 creator 或 superuser
     * 才可以；被授权的 MANAGER 即便能管理 ACL，也不得继续授予 MANAGER。
     * 与 dts-platform 的 DashboardAccessGuard.canGrant 策略 1 对齐。
     * 抽成 package-private static 便于单元测试。
     */
    static boolean isManagerGrantAllowed(AnalyticsScreen screen, AnalyticsUser caller) {
        if (caller == null) return false;
        if (caller.isSuperuser()) return true;
        if (screen == null || screen.getCreatorId() == null || caller.getId() == null) return false;
        return screen.getCreatorId().equals(caller.getId());
    }

    /**
     * Sprint-24 F3/T03：校验创建大屏请求中的 classification 字段。
     *
     * 创建路径强制必填，从源头消除 classification=null 裸屏；老数据走 F4 盘点回收。
     * 抽成 package-private static 便于单元测试，避免为每条分支启动 Spring 上下文。
     *
     * @return normalized 大写值（PUBLIC/INTERNAL/SECRET/CONFIDENTIAL）
     * @throws IllegalArgumentException 当 raw 为 null/blank 或不在白名单时
     */
    static String normalizeRequiredClassification(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException(
                "classification is required: must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                "classification is required: must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL");
        }
        String upper = trimmed.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL").contains(upper)) {
            throw new IllegalArgumentException(
                "classification must be one of PUBLIC/INTERNAL/SECRET/CONFIDENTIAL");
        }
        return upper;
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

    /**
     * Sprint-24 F4：裸屏盘点端点。列出所有 archived=false 且 classification 为 null
     * 或空白的大屏，供 OP_ADMIN / superuser 通知 owner 去补登密级，收敛存量裸屏。
     *
     * 鉴权：复用 MetabaseAuth.requireSuperuser，与同文件下的 backfill-grants 端点
     * 一致；这是合规盘点工具，仅 superuser 可调。
     *
     * 不静默回填默认密级 —— 那会误判真实 SECRET 数据为 INTERNAL。回填由 owner
     * 在编辑器属性面板（F1 入口）手动完成，每次改动都会写一条
     * screen.classification.update 审计。
     *
     * 响应字段：id / name / creatorId / creatorEmail / createdAt。lastVisitedAt
     * 暂不查（dts-analytics 本地没有访问日志表，需要跨服务联表，留给后续 sprint）。
     */
    @GetMapping(path = "/admin/unclassified", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listUnclassified(HttpServletRequest request) {
        Optional<ResponseEntity<String>> authError = MetabaseAuth.requireSuperuser(sessionService, request);
        if (authError.isPresent()) {
            return authError.orElseThrow();
        }
        Optional<AnalyticsUser> caller = MetabaseAuth.currentUser(sessionService, request);
        try {
            List<AnalyticsScreen> rows = screenRepository.findUnclassified();
            // 一次性 batch 查 creator 信息，避免 N+1。
            Set<Long> creatorIds = new LinkedHashSet<>();
            for (AnalyticsScreen s : rows) {
                if (s.getCreatorId() != null) creatorIds.add(s.getCreatorId());
            }
            Map<Long, AnalyticsUser> creatorMap = new HashMap<>();
            if (!creatorIds.isEmpty()) {
                userRepository.findAllById(creatorIds).forEach(u -> creatorMap.put(u.getId(), u));
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (AnalyticsScreen s : rows) {
                AnalyticsUser creator = s.getCreatorId() == null ? null : creatorMap.get(s.getCreatorId());
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", s.getId());
                item.put("name", s.getName());
                item.put("creatorId", s.getCreatorId());
                item.put("creatorEmail", creator == null ? null : creator.getEmail());
                item.put("creatorPlatformUsername", creator == null ? null : creator.getPlatformUsername());
                item.put("createdAt", s.getCreatedAt() == null ? null : s.getCreatedAt().toString());
                items.add(item);
            }

            // 端点本身写审计：合规盘点的访问行为本身需要可追溯。
            // analytics_screen_audit_log.screen_id 是 NOT NULL 不能装"无 screen 上下文"
            // 的合规事件，因此走 ScreenAuditService.logCrossScreenEvent —— 跳过本地表，
            // 仅同步到 dts-admin 中央审计。
            screenAuditService.logCrossScreenEvent(
                caller.map(AnalyticsUser::getId).orElse(null),
                "screen.compliance.audit_unclassified",
                Map.of("count", items.size()),
                requestIdFrom(request)
            );

            return ResponseEntity.ok(Map.of("count", items.size(), "items", items));
        } catch (DataAccessException ex) {
            LOG.warn("DB error listing unclassified screens: {}", ex.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        } catch (Exception ex) {
            LOG.error("Unexpected error listing unclassified screens", ex);
            return ResponseEntity.status(500).body(Map.of("error", "internal_error"));
        }
    }

    /**
     * One-time admin endpoint: backfill OWNER grants in the local analytics_screen_access table
     * for screens created before the local permission table was introduced.
     */
    @PostMapping(path = "/admin/backfill-grants", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> backfillGrants(HttpServletRequest request) {
        Optional<ResponseEntity<String>> authError = MetabaseAuth.requireSuperuser(sessionService, request);
        if (authError.isPresent()) {
            return authError.orElseThrow();
        }

        List<AnalyticsScreen> allScreens = screenRepository.findAll();
        int created = 0;
        int skipped = 0;

        for (AnalyticsScreen screen : allScreens) {
            Long creatorId = screen.getCreatorId();
            if (creatorId == null) {
                skipped++;
                continue;
            }
            AnalyticsUser creator = userRepository.findById(creatorId).orElse(null);
            String granteeId = (creator != null && creator.getPlatformUsername() != null && !creator.getPlatformUsername().isBlank())
                    ? creator.getPlatformUsername() : String.valueOf(creatorId);
            // Check if OWNER grant already exists
            boolean hasOwner = screenOwnershipService.listGrants(screen.getId()).stream()
                .anyMatch(g -> "USER".equals(g.get("granteeType")) && granteeId.equals(g.get("granteeId"))
                    && "OWNER".equals(g.get("permission")));
            if (hasOwner) {
                skipped++;
                continue;
            }
            screenOwnershipService.createGrant(screen.getId(), "USER", granteeId, "OWNER", creatorId);
            created++;
        }

        return ResponseEntity.ok(Map.of(
            "total", allScreens.size(),
            "created", created,
            "skipped", skipped));
    }

    /**
     * One-time admin endpoint: rewrite legacy `screen-ref:{name}|...` jump-url
     * action templates in every draft screen's components_json into the new
     * canonical id-based form `/bi/screens/{id}/preview`.
     *
     * The legacy form looks up the target screen by user-editable name, which
     * silently breaks the moment a user renames the screen or imports it into
     * a different environment. The new form is permanent because id is the
     * only stable identity for a screen object.
     *
     * Lookup strategy per ref:
     *   1. exact name match against analytics_screen.name
     *   2. normalized fuzzy match (strip "GPMC " prefix / trailing "v2"
     *      / trailing "(实例)" parens / collapse whitespace / lowercase)
     *      Multiple normalized hits are disambiguated by most-recent updatedAt.
     *
     * Refs that match nothing are listed in the response and left untouched.
     *
     * Call with `?dryRun=true` (default) first to preview the rewrite plan,
     * then call with `?dryRun=false` to apply. Response includes per-ref
     * before/after so the operator can audit exactly what was changed.
     *
     * Restricted to superuser. Bypasses the per-screen edit lock — this is
     * a maintenance operation, not a normal edit, and the rewrite is
     * idempotent (running it twice on the same screen is a no-op).
     */
    @PostMapping(path = "/admin/migrate-jump-refs", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> migrateJumpRefs(
            @RequestParam(value = "dryRun", defaultValue = "true") boolean dryRun,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> authError = MetabaseAuth.requireSuperuser(sessionService, request);
        if (authError.isPresent()) {
            return authError.orElseThrow();
        }

        List<AnalyticsScreen> allScreens = screenRepository.findAllByArchivedFalseOrderByIdDesc();

        // Build name → screen index. We keep two indexes:
        //   - exactByName: literal match on trimmed name
        //   - normByName : normalized fuzzy match (multiple buckets per key)
        Map<String, AnalyticsScreen> exactByName = new LinkedHashMap<>();
        Map<String, List<AnalyticsScreen>> normByName = new LinkedHashMap<>();
        for (AnalyticsScreen s : allScreens) {
            String name = s.getName();
            if (name == null || name.isBlank()) continue;
            exactByName.putIfAbsent(name.trim(), s);
            String norm = normalizeScreenName(name);
            if (!norm.isBlank()) {
                normByName.computeIfAbsent(norm, k -> new ArrayList<>()).add(s);
            }
        }

        int updatedScreens = 0;
        int rewrittenRefs = 0;
        int unresolvedRefs = 0;
        ArrayNode rewrittenLog = objectMapper.createArrayNode();
        ArrayNode unresolvedLog = objectMapper.createArrayNode();
        String requestId = requestIdFrom(request);

        for (AnalyticsScreen screen : allScreens) {
            String json = screen.getComponentsJson();
            // Cheap pre-filter: skip screens with no legacy refs at all.
            if (json == null || json.isBlank() || !json.contains("screen-ref:")) {
                continue;
            }

            JsonNode parsed;
            try {
                parsed = objectMapper.readTree(json);
            } catch (Exception ex) {
                ObjectNode err = unresolvedLog.addObject();
                err.put("screenId", screen.getId());
                err.put("screenName", screen.getName());
                err.put("error", "componentsJson parse failed: " + ex.getMessage());
                unresolvedRefs++;
                continue;
            }
            if (!(parsed instanceof ArrayNode components)) {
                continue;
            }

            boolean dirty = false;
            String beforeJson = json;

            for (JsonNode component : components) {
                JsonNode actions = component.path("actions");
                if (!actions.isArray()) continue;
                for (JsonNode action : actions) {
                    if (!"jump-url".equals(action.path("type").asText(""))) continue;
                    String tmpl = action.path("jumpUrlTemplate").asText("");
                    if (!tmpl.startsWith("screen-ref:")) continue;

                    String raw = tmpl.substring("screen-ref:".length());
                    int pipe = raw.indexOf('|');
                    String namePart = pipe >= 0 ? raw.substring(0, pipe) : raw;
                    String oldName;
                    try {
                        oldName = java.net.URLDecoder
                                .decode(namePart, java.nio.charset.StandardCharsets.UTF_8)
                                .trim();
                    } catch (Exception ex) {
                        continue;
                    }
                    if (oldName.isBlank()) continue;

                    AnalyticsScreen target = exactByName.get(oldName);
                    String strategy = "exact";
                    if (target == null) {
                        String norm = normalizeScreenName(oldName);
                        List<AnalyticsScreen> hits = normByName.getOrDefault(norm, List.of());
                        if (hits.size() == 1) {
                            target = hits.get(0);
                            strategy = "normalize";
                        } else if (hits.size() > 1) {
                            // Disambiguate ambiguous normalized matches by most-recent updatedAt.
                            target = hits.stream()
                                    .filter(s -> s.getUpdatedAt() != null)
                                    .max((a, b) -> a.getUpdatedAt().compareTo(b.getUpdatedAt()))
                                    .orElse(hits.get(0));
                            strategy = "normalize-ambiguous";
                        }
                    }

                    if (target == null) {
                        ObjectNode entry = unresolvedLog.addObject();
                        entry.put("screenId", screen.getId());
                        entry.put("screenName", screen.getName());
                        entry.put("componentName", component.path("name").asText(null));
                        entry.put("oldRef", oldName);
                        unresolvedRefs++;
                        continue;
                    }

                    String newUrl = "/bi/screens/" + target.getId() + "/preview";
                    ObjectNode entry = rewrittenLog.addObject();
                    entry.put("screenId", screen.getId());
                    entry.put("screenName", screen.getName());
                    entry.put("componentName", component.path("name").asText(null));
                    entry.put("oldRef", oldName);
                    entry.put("targetId", target.getId());
                    entry.put("targetName", target.getName());
                    entry.put("newUrl", newUrl);
                    entry.put("strategy", strategy);

                    if (action instanceof ObjectNode actionObj) {
                        actionObj.put("jumpUrlTemplate", newUrl);
                        dirty = true;
                        rewrittenRefs++;
                    }
                }
            }

            if (dirty) {
                updatedScreens++;
                if (!dryRun) {
                    String afterJson = parsed.toString();
                    screen.setComponentsJson(afterJson);
                    screenRepository.save(screen);

                    ObjectNode auditBefore = objectMapper.createObjectNode();
                    auditBefore.put("componentsJson", beforeJson);
                    ObjectNode auditAfter = objectMapper.createObjectNode();
                    auditAfter.put("componentsJson", afterJson);
                    screenAuditService.log(
                        screen.getId(),
                        null,
                        "screen.migrate-jump-refs",
                        auditBefore,
                        auditAfter,
                        requestId);
                }
            }
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.put("dryRun", dryRun);
        result.put("totalScreens", allScreens.size());
        result.put("updatedScreens", updatedScreens);
        result.put("rewrittenRefs", rewrittenRefs);
        result.put("unresolvedRefs", unresolvedRefs);
        result.set("rewritten", rewrittenLog);
        result.set("unresolved", unresolvedLog);
        return ResponseEntity.ok(result);
    }

    /**
     * Normalize a screen name for one-shot fuzzy matching during the
     * migrate-jump-refs admin operation.
     *
     * Strips: "GPMC " prefix, trailing "(实例)"-style parens, trailing
     * version markers like " v2", and collapses whitespace + lowercases.
     *
     * NOT used at runtime navigation — runtime resolution is strict id-only
     * (see InteractionLayer.resolveScreenReferenceUrl). This helper exists
     * solely as a one-shot heuristic to absorb the historical naming drift
     * between v2 instance template JSON and the user-renamed screens in db.
     */
    private static String normalizeScreenName(String name) {
        if (name == null) return "";
        String s = name;
        s = s.replaceAll("(?i)^GPMC[\\s\\u3000]+", "");
        s = s.replaceAll("[\\s\\u3000]*[(（][^)）]*[)）][\\s\\u3000]*$", "");
        s = s.replaceAll("(?i)[\\s\\u3000]+v\\d+[\\s\\u3000]*$", "");
        s = s.replaceAll("[\\s\\u3000]+", " ").trim().toLowerCase();
        return s;
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
