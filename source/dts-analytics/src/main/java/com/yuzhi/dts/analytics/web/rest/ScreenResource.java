package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAcl;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenVersion;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenVersionRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.ScreenAclService;
import com.yuzhi.dts.analytics.service.ScreenAuditService;
import com.yuzhi.dts.analytics.service.ScreenWarmupService;
import com.yuzhi.dts.analytics.service.ScreenAiGenerationService;
import com.yuzhi.dts.analytics.service.ScreenSpecValidator;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    private final ScreenWarmupService screenWarmupService;
    private final ScreenAiGenerationService screenAiGenerationService;
    private final ScreenSpecValidator screenSpecValidator;
    private final PublicLinkService publicLinkService;
    private final ObjectMapper objectMapper;

    public ScreenResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService,
            ScreenWarmupService screenWarmupService,
            ScreenAiGenerationService screenAiGenerationService,
            ScreenSpecValidator screenSpecValidator,
            PublicLinkService publicLinkService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
        this.screenWarmupService = screenWarmupService;
        this.screenAiGenerationService = screenAiGenerationService;
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

        ObjectNode result = screenAiGenerationService.generate(prompt, width, height);
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
        ObjectNode result = screenAiGenerationService.revise(prompt, screenSpec);
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
        ScreenSpecValidator.ValidationResult specValidation = screenSpecValidator.validateForWrite(body);

        AnalyticsScreenVersion beforePublished =
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null);
        ObjectNode before = toAuditScreenSnapshot(screen, beforePublished);

        if (body != null && body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name != null) {
                screen.setName(name);
            }
        }
        if (body != null && body.has("description")) {
            screen.setDescription(body.path("description").isNull() ? null : body.path("description").asText(null));
        }
        if (body != null && body.has("width")) {
            screen.setWidth(body.path("width").asInt(1920));
        }
        if (body != null && body.has("height")) {
            screen.setHeight(body.path("height").asInt(1080));
        }
        if (body != null && body.has("backgroundColor")) {
            screen.setBackgroundColor(body.path("backgroundColor").asText(null));
        }
        if (body != null && body.has("backgroundImage")) {
            screen.setBackgroundImage(body.path("backgroundImage").isNull() ? null : body.path("backgroundImage").asText(null));
        }
        if (body != null && body.has("theme")) {
            screen.setTheme(body.path("theme").isNull() ? null : body.path("theme").asText(null));
        }
        if (body != null && body.has("components")) {
            screen.setComponentsJson(body.path("components").toString());
        }
        if (body != null && body.has("globalVariables")) {
            screen.setVariablesJson(body.path("globalVariables").toString());
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

        ObjectNode before = toAuditScreenSnapshot(
                screen,
                screenVersionRepository.findFirstByScreenIdAndCurrentPublishedTrue(screen.getId()).orElse(null));

        screen.setArchived(true);
        screenRepository.save(screen);

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

        Set<String> fromComponentIds = new HashSet<>();
        Set<String> toComponentIds = new HashSet<>();
        Set<String> fromTypes = new HashSet<>();
        Set<String> toTypes = new HashSet<>();
        fillComponentStats(fromComponents, fromComponentIds, fromTypes);
        fillComponentStats(toComponents, toComponentIds, toTypes);

        int addedComponents = countDiff(toComponentIds, fromComponentIds);
        int removedComponents = countDiff(fromComponentIds, toComponentIds);
        int addedTypes = countDiff(toTypes, fromTypes);
        int removedTypes = countDiff(fromTypes, toTypes);

        Set<String> fromVarKeys = collectVariableKeys(fromVariables);
        Set<String> toVarKeys = collectVariableKeys(toVariables);
        int addedVariables = countDiff(toVarKeys, fromVarKeys);
        int removedVariables = countDiff(fromVarKeys, toVarKeys);

        ObjectNode node = objectMapper.createObjectNode();
        node.set("from", toVersionResponse(fromVersion));
        node.set("to", toVersionResponse(toVersion));
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("componentCountFrom", fromComponentIds.size());
        summary.put("componentCountTo", toComponentIds.size());
        summary.put("addedComponents", addedComponents);
        summary.put("removedComponents", removedComponents);
        summary.put("addedComponentTypes", addedTypes);
        summary.put("removedComponentTypes", removedTypes);
        summary.put("addedVariables", addedVariables);
        summary.put("removedVariables", removedVariables);
        node.set("summary", summary);
        return node;
    }

    private void fillComponentStats(JsonNode components, Set<String> ids, Set<String> types) {
        if (components == null || !components.isArray()) {
            return;
        }
        for (JsonNode item : components) {
            if (item == null || !item.isObject()) {
                continue;
            }
            String id = trimToNull(item.path("id").asText(null));
            if (id != null) {
                ids.add(id);
            }
            String type = trimToNull(item.path("type").asText(null));
            if (type != null) {
                types.add(type);
            }
        }
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

    private int countDiff(Set<String> left, Set<String> right) {
        int count = 0;
        for (String value : left) {
            if (!right.contains(value)) {
                count++;
            }
        }
        return count;
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
