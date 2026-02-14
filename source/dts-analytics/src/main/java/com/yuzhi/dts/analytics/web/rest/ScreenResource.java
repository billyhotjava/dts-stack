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
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final PublicLinkService publicLinkService;
    private final ObjectMapper objectMapper;

    public ScreenResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            AnalyticsScreenVersionRepository screenVersionRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService,
            PublicLinkService publicLinkService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenVersionRepository = screenVersionRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
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
