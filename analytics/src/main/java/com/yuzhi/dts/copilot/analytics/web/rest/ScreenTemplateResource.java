package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAssetAuditLog;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplate;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenTemplateVersion;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenTemplateRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenTemplateVersionRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAclService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAssetAuditService;
import com.yuzhi.dts.copilot.analytics.service.ScreenTemplateVersionService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

@RestController
@RequestMapping("/api/screen-templates")
@Transactional
public class ScreenTemplateResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenTemplateRepository screenTemplateRepository;
    private final AnalyticsScreenTemplateVersionRepository screenTemplateVersionRepository;
    private final AnalyticsScreenRepository screenRepository;
    private final ScreenAclService screenAclService;
    private final ScreenAssetAuditService screenAssetAuditService;
    private final ScreenTemplateVersionService screenTemplateVersionService;
    private final ObjectMapper objectMapper;

    public ScreenTemplateResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenTemplateRepository screenTemplateRepository,
            AnalyticsScreenTemplateVersionRepository screenTemplateVersionRepository,
            AnalyticsScreenRepository screenRepository,
            ScreenAclService screenAclService,
            ScreenAssetAuditService screenAssetAuditService,
            ScreenTemplateVersionService screenTemplateVersionService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenTemplateRepository = screenTemplateRepository;
        this.screenTemplateVersionRepository = screenTemplateVersionRepository;
        this.screenRepository = screenRepository;
        this.screenAclService = screenAclService;
        this.screenAssetAuditService = screenAssetAuditService;
        this.screenTemplateVersionService = screenTemplateVersionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(value = "q", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "listed", required = false) Boolean listed,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        PlatformContext context = PlatformContext.from(request);
        String normalizedKeyword = trimToNull(keyword);
        String normalizedCategory = trimToNull(category);
        String normalizedTag = trimToNull(tag);
        String normalizedVisibility = normalizeVisibilityScope(visibility);

        List<ObjectNode> result = screenTemplateRepository.findAllByArchivedFalseOrderByUpdatedAtDesc().stream()
                .filter(t -> canReadTemplate(t, user.get(), context))
                .filter(t -> filterKeyword(t, normalizedKeyword))
                .filter(t -> filterCategory(t, normalizedCategory))
                .filter(t -> filterTag(t, normalizedTag))
                .filter(t -> filterVisibility(t, normalizedVisibility))
                .filter(t -> filterListed(t, listed))
                .map(this::toTemplateSummary)
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext context = PlatformContext.from(request);
        if (!canReadTemplate(template, user.get(), context)) {
            return forbidden();
        }
        return ResponseEntity.ok(toTemplateDetail(template));
    }

    @GetMapping(path = "/{id}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> versions(
            @PathVariable("id") long id,
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext context = PlatformContext.from(request);
        if (!canReadTemplate(template, user.get(), context)) {
            return forbidden();
        }

        List<ObjectNode> result = screenTemplateVersionService.listVersions(template.getId(), limit).stream()
                .map(this::toTemplateVersionRow)
                .toList();
        return ResponseEntity.ok(result);
    }

    @PostMapping(path = "/{id}/restore/{versionNo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> restoreVersion(
            @PathVariable("id") long id,
            @PathVariable("versionNo") int versionNo,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManageTemplate(template, user.get())) {
            return forbidden();
        }
        AnalyticsScreenTemplateVersion version = screenTemplateVersionRepository
                .findByTemplateIdAndVersionNo(template.getId(), versionNo)
                .orElse(null);
        if (version == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> before = toTemplateAuditSnapshot(template);
        applyTemplateSnapshot(template, version.getSnapshotJson());
        template = screenTemplateRepository.save(template);
        int nextVersion = screenTemplateVersionService.appendVersion(template, user.get().getId(), "template.restore", versionNo);
        template.setTemplateVersion(nextVersion);
        template = screenTemplateRepository.save(template);

        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.restore",
                "version:" + versionNo,
                "success",
                Map.of("before", before, "after", toTemplateAuditSnapshot(template), "restoredFromVersion", versionNo),
                requestIdFrom(request));
        return ResponseEntity.ok(toTemplateDetail(template));
    }

    @PutMapping(path = "/{id}/listing", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateListing(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManageTemplate(template, user.get())) {
            return forbidden();
        }

        boolean listed = body == null || body.path("listed").asBoolean(true);
        Map<String, Object> before = toTemplateAuditSnapshot(template);
        template.setListed(listed);
        template = screenTemplateRepository.save(template);
        int nextVersion = screenTemplateVersionService.appendVersion(
                template,
                user.get().getId(),
                listed ? "template.list" : "template.unlist",
                null);
        template.setTemplateVersion(nextVersion);
        template = screenTemplateRepository.save(template);

        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                listed ? "template.list" : "template.unlist",
                "manual",
                "success",
                Map.of("before", before, "after", toTemplateAuditSnapshot(template)),
                requestIdFrom(request));
        return ResponseEntity.ok(toTemplateDetail(template));
    }

    @GetMapping(path = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> audit(
            @RequestParam(value = "limit", required = false, defaultValue = "100") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        if (!user.get().isSuperuser()) {
            return forbidden();
        }

        List<ObjectNode> rows = screenAssetAuditService.listRecent("template", limit).stream()
                .map(this::toTemplateAuditRow)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        PlatformContext context = PlatformContext.from(request);

        AnalyticsScreenTemplate template = new AnalyticsScreenTemplate();
        applyTemplatePayload(template, body);
        if (trimToNull(template.getName()) == null) {
            template.setName("未命名模板");
        }
        if (trimToNull(template.getCategory()) == null) {
            template.setCategory("custom");
        }
        if (trimToNull(template.getComponentsJson()) == null) {
            template.setComponentsJson("[]");
        }
        if (trimToNull(template.getVariablesJson()) == null) {
            template.setVariablesJson("[]");
        }
        if (trimToNull(template.getTagsJson()) == null) {
            template.setTagsJson("[]");
        }
        if (isProtectedCategory(template.getCategory()) && !user.get().isSuperuser()) {
            screenAssetAuditService.log(
                    "template",
                    null,
                    user.get().getId(),
                    "template.create",
                    "manual",
                    "rejected",
                    rejectedCategoryDetails(template.getCategory()),
                    requestIdFrom(request));
            return forbidden();
        }
        template.setCreatorId(user.get().getId());
        template.setOwnerDept(context.dept());
        if (trimToNull(template.getVisibilityScope()) == null) {
            template.setVisibilityScope("team");
        }
        if (body == null || !body.has("listed")) {
            template.setListed(true);
        }
        template.setArchived(false);

        template = screenTemplateRepository.save(template);
        int firstVersion = screenTemplateVersionService.appendVersion(template, user.get().getId(), "template.create", null);
        template.setTemplateVersion(firstVersion);
        template = screenTemplateRepository.save(template);
        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.create",
                "manual",
                "success",
                toTemplateAuditSnapshot(template),
                requestIdFrom(request));
        return ResponseEntity.ok(toTemplateDetail(template));
    }

    @PostMapping(path = "/from-screen/{screenId}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createFromScreen(
            @PathVariable("screenId") long screenId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreen screen = screenRepository.findById(screenId).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }

        AnalyticsScreenTemplate template = new AnalyticsScreenTemplate();
        template.setName(screen.getName() == null ? "未命名模板" : screen.getName());
        template.setDescription(screen.getDescription());
        template.setCategory("custom");
        template.setThumbnail("🧩");
        template.setTagsJson("[]");
        template.setWidth(screen.getWidth() == null ? 1920 : screen.getWidth());
        template.setHeight(screen.getHeight() == null ? 1080 : screen.getHeight());
        template.setBackgroundColor(screen.getBackgroundColor());
        template.setBackgroundImage(screen.getBackgroundImage());
        template.setTheme(screen.getTheme());
        template.setComponentsJson(defaultJson(screen.getComponentsJson(), "[]"));
        template.setVariablesJson(defaultJson(screen.getVariablesJson(), "[]"));
        template.setSourceScreenId(screen.getId());
        template.setVisibilityScope("team");
        template.setOwnerDept(context.dept());
        template.setListed(true);
        template.setTemplateVersion(1);
        template.setCreatorId(user.get().getId());
        template.setArchived(false);

        applyTemplatePayload(template, body);
        if (trimToNull(template.getName()) == null) {
            template.setName(screen.getName() == null ? "未命名模板" : screen.getName());
        }
        if (isProtectedCategory(template.getCategory()) && !user.get().isSuperuser()) {
            screenAssetAuditService.log(
                    "template",
                    null,
                    user.get().getId(),
                    "template.create_from_screen",
                    "screen:" + screen.getId(),
                    "rejected",
                    rejectedCategoryDetails(template.getCategory()),
                    requestIdFrom(request));
            return forbidden();
        }

        template = screenTemplateRepository.save(template);
        int firstVersion = screenTemplateVersionService.appendVersion(
                template,
                user.get().getId(),
                "template.create_from_screen",
                null);
        template.setTemplateVersion(firstVersion);
        template = screenTemplateRepository.save(template);
        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.create_from_screen",
                "screen:" + screen.getId(),
                "success",
                toTemplateAuditSnapshot(template),
                requestIdFrom(request));
        return ResponseEntity.ok(toTemplateDetail(template));
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

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManageTemplate(template, user.get())) {
            screenAssetAuditService.log(
                    "template",
                    template.getId(),
                    user.get().getId(),
                    "template.update",
                    "manual",
                    "rejected",
                    Map.of("reason", "permission denied"),
                    requestIdFrom(request));
            return forbidden();
        }

        Map<String, Object> before = toTemplateAuditSnapshot(template);
        applyTemplatePayload(template, body);
        if (isProtectedCategory(template.getCategory()) && !user.get().isSuperuser()) {
            screenAssetAuditService.log(
                    "template",
                    template.getId(),
                    user.get().getId(),
                    "template.update",
                    "manual",
                    "rejected",
                    rejectedCategoryDetails(template.getCategory()),
                    requestIdFrom(request));
            return forbidden();
        }
        template = screenTemplateRepository.save(template);
        int nextVersion = screenTemplateVersionService.appendVersion(template, user.get().getId(), "template.update", null);
        template.setTemplateVersion(nextVersion);
        template = screenTemplateRepository.save(template);
        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.update",
                "manual",
                "success",
                Map.of("before", before, "after", toTemplateAuditSnapshot(template)),
                requestIdFrom(request));
        return ResponseEntity.ok(toTemplateDetail(template));
    }

    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        if (!canManageTemplate(template, user.get())) {
            screenAssetAuditService.log(
                    "template",
                    template.getId(),
                    user.get().getId(),
                    "template.delete",
                    "manual",
                    "rejected",
                    Map.of("reason", "permission denied"),
                    requestIdFrom(request));
            return forbidden();
        }

        Map<String, Object> before = toTemplateAuditSnapshot(template);
        template.setArchived(true);
        screenTemplateRepository.save(template);
        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.delete",
                "manual",
                "success",
                before,
                requestIdFrom(request));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/create-screen", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createScreenFromTemplate(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }

        AnalyticsScreenTemplate template = screenTemplateRepository.findByIdAndArchivedFalse(id).orElse(null);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext context = PlatformContext.from(request);
        if (!canReadTemplate(template, user.get(), context)) {
            return forbidden();
        }

        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setName(trimToNull(body == null ? null : body.path("name").asText(null)) == null
                ? template.getName()
                : body.path("name").asText());
        screen.setDescription(body != null && body.has("description")
                ? body.path("description").asText(null)
                : template.getDescription());
        screen.setWidth(template.getWidth() == null ? 1920 : template.getWidth());
        screen.setHeight(template.getHeight() == null ? 1080 : template.getHeight());
        screen.setBackgroundColor(template.getBackgroundColor());
        screen.setBackgroundImage(template.getBackgroundImage());
        screen.setTheme(template.getTheme());
        screen.setComponentsJson(defaultJson(template.getComponentsJson(), "[]"));
        screen.setVariablesJson(defaultJson(template.getVariablesJson(), "[]"));
        screen.setCreatorId(user.get().getId());
        screen.setArchived(false);

        screen = screenRepository.save(screen);
        screenAclService.ensureCreatorManage(screen);
        Map<String, Object> createScreenAudit = new LinkedHashMap<>();
        createScreenAudit.put("targetScreenId", screen.getId());
        createScreenAudit.put("targetScreenName", screen.getName());
        createScreenAudit.put("templateVersion", template.getTemplateVersion());
        screenAssetAuditService.log(
                "template",
                template.getId(),
                user.get().getId(),
                "template.create_screen",
                "template:" + template.getId(),
                "success",
                createScreenAudit,
                requestIdFrom(request));

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", screen.getId());
        response.put("name", screen.getName());
        response.put("description", screen.getDescription());
        response.put("width", screen.getWidth());
        response.put("height", screen.getHeight());
        response.put("backgroundColor", screen.getBackgroundColor());
        response.put("backgroundImage", screen.getBackgroundImage());
        response.put("theme", screen.getTheme());
        response.set("components", parseArray(screen.getComponentsJson()));
        response.set("globalVariables", parseArray(screen.getVariablesJson()));
        response.put("sourceMode", "draft");
        response.putPOJO("createdAt", screen.getCreatedAt());
        response.putPOJO("updatedAt", screen.getUpdatedAt());
        return ResponseEntity.ok(response);
    }

    private boolean filterKeyword(AnalyticsScreenTemplate template, String keyword) {
        if (keyword == null) {
            return true;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        String hay = ((template.getName() == null ? "" : template.getName()) + " "
                + (template.getDescription() == null ? "" : template.getDescription()) + " "
                + (template.getCategory() == null ? "" : template.getCategory()))
                .toLowerCase(Locale.ROOT);
        return hay.contains(needle);
    }

    private boolean filterCategory(AnalyticsScreenTemplate template, String category) {
        if (category == null || "all".equalsIgnoreCase(category)) {
            return true;
        }
        return category.equalsIgnoreCase(template.getCategory());
    }

    private boolean filterTag(AnalyticsScreenTemplate template, String tag) {
        if (tag == null) {
            return true;
        }
        ArrayNode tags = parseArray(template.getTagsJson());
        String needle = tag.toLowerCase(Locale.ROOT);
        for (JsonNode node : tags) {
            if (node != null && node.isTextual() && node.asText("").toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean filterVisibility(AnalyticsScreenTemplate template, String visibility) {
        if (visibility == null || "all".equalsIgnoreCase(visibility)) {
            return true;
        }
        return visibility.equalsIgnoreCase(normalizeVisibilityScope(template.getVisibilityScope()));
    }

    private boolean filterListed(AnalyticsScreenTemplate template, Boolean listed) {
        if (listed == null) {
            return true;
        }
        return template.isListed() == listed;
    }

    private boolean canReadTemplate(AnalyticsScreenTemplate template, AnalyticsUser user, PlatformContext context) {
        if (template == null || user == null) {
            return false;
        }
        if (user.isSuperuser()) {
            return true;
        }
        boolean isCreator = template.getCreatorId() != null && template.getCreatorId().equals(user.getId());
        if (!template.isListed() && !isCreator) {
            return false;
        }
        String scope = normalizeVisibilityScope(template.getVisibilityScope());
        if ("personal".equals(scope)) {
            return isCreator;
        }
        if ("team".equals(scope)) {
            if (isCreator) {
                return true;
            }
            return sameDept(template.getOwnerDept(), context == null ? null : context.dept());
        }
        return true;
    }

    private boolean canManageTemplate(AnalyticsScreenTemplate template, AnalyticsUser user) {
        if (user.isSuperuser()) {
            return true;
        }
        return template.getCreatorId() != null && template.getCreatorId().equals(user.getId());
    }

    private boolean isProtectedCategory(String category) {
        if (category == null) {
            return false;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        return "builtin".equals(normalized) || "official".equals(normalized) || "industry".equals(normalized);
    }

    private ObjectNode toTemplateSummary(AnalyticsScreenTemplate template) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", template.getId());
        node.put("name", template.getName());
        node.put("description", template.getDescription());
        node.put("category", template.getCategory());
        node.put("thumbnail", template.getThumbnail());
        node.set("tags", parseArray(template.getTagsJson()));
        node.put("theme", template.getTheme());
        node.put("width", template.getWidth());
        node.put("height", template.getHeight());
        node.put("visibilityScope", normalizeVisibilityScope(template.getVisibilityScope()));
        node.put("ownerDept", template.getOwnerDept());
        node.put("listed", template.isListed());
        node.putPOJO("sourceScreenId", template.getSourceScreenId());
        node.putPOJO("sourceTemplateId", template.getSourceTemplateId());
        node.put("templateVersion", template.getTemplateVersion() == null ? 1 : template.getTemplateVersion());
        node.set("themePack", parseObject(template.getThemePackJson()));
        node.putPOJO("creatorId", template.getCreatorId());
        node.putPOJO("updatedAt", template.getUpdatedAt());
        node.putPOJO("createdAt", template.getCreatedAt());
        return node;
    }

    private ObjectNode toTemplateDetail(AnalyticsScreenTemplate template) {
        ObjectNode node = toTemplateSummary(template);
        node.put("backgroundColor", template.getBackgroundColor());
        node.put("backgroundImage", template.getBackgroundImage());
        node.set("components", parseArray(template.getComponentsJson()));
        node.set("globalVariables", parseArray(template.getVariablesJson()));
        node.putPOJO("sourceScreenId", template.getSourceScreenId());
        node.putPOJO("sourceTemplateId", template.getSourceTemplateId());
        node.put("templateVersion", template.getTemplateVersion() == null ? 1 : template.getTemplateVersion());
        node.put("visibilityScope", normalizeVisibilityScope(template.getVisibilityScope()));
        node.put("ownerDept", template.getOwnerDept());
        node.put("listed", template.isListed());
        node.set("themePack", parseObject(template.getThemePackJson()));
        return node;
    }

    private Map<String, Object> toTemplateAuditSnapshot(AnalyticsScreenTemplate template) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", template.getId());
        snapshot.put("name", template.getName());
        snapshot.put("category", template.getCategory());
        snapshot.put("creatorId", template.getCreatorId());
        snapshot.put("templateVersion", template.getTemplateVersion());
        snapshot.put("visibilityScope", normalizeVisibilityScope(template.getVisibilityScope()));
        snapshot.put("ownerDept", template.getOwnerDept());
        snapshot.put("listed", template.isListed());
        snapshot.put("sourceScreenId", template.getSourceScreenId());
        snapshot.put("sourceTemplateId", template.getSourceTemplateId());
        snapshot.put("archived", template.isArchived());
        return snapshot;
    }

    private ObjectNode toTemplateVersionRow(AnalyticsScreenTemplateVersion version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.putPOJO("id", version.getId());
        node.putPOJO("templateId", version.getTemplateId());
        node.put("versionNo", version.getVersionNo());
        node.put("action", version.getAction());
        node.putPOJO("actorId", version.getActorId());
        node.putPOJO("createdAt", version.getCreatedAt());
        node.putPOJO("restoredFromVersion", version.getRestoredFromVersion());
        node.set("snapshot", parseObject(version.getSnapshotJson()));
        return node;
    }

    private Map<String, Object> rejectedCategoryDetails(String category) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reason", "protected category requires superuser");
        details.put("category", category);
        return details;
    }

    private ObjectNode toTemplateAuditRow(AnalyticsScreenAssetAuditLog log) {
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

    private void applyTemplatePayload(AnalyticsScreenTemplate template, JsonNode body) {
        if (template == null || body == null || body.isNull()) {
            return;
        }
        if (body.has("name")) {
            String name = trimToNull(body.path("name").asText(null));
            if (name != null) {
                template.setName(name);
            }
        }
        if (body.has("description")) {
            template.setDescription(body.path("description").isNull() ? null : body.path("description").asText(null));
        }
        if (body.has("category")) {
            String category = trimToNull(body.path("category").asText(null));
            if (category != null) {
                template.setCategory(category);
            }
        }
        if (body.has("thumbnail")) {
            template.setThumbnail(body.path("thumbnail").isNull() ? null : body.path("thumbnail").asText(null));
        }
        if (body.has("tags") && body.path("tags").isArray()) {
            template.setTagsJson(body.path("tags").toString());
        }
        if (body.has("visibilityScope")) {
            template.setVisibilityScope(normalizeVisibilityScope(body.path("visibilityScope").asText(null)));
        }
        if (body.has("listed")) {
            template.setListed(body.path("listed").asBoolean(true));
        }
        if (body.has("sourceTemplateId")) {
            if (body.path("sourceTemplateId").isNull()) {
                template.setSourceTemplateId(null);
            } else if (body.path("sourceTemplateId").canConvertToLong()) {
                template.setSourceTemplateId(body.path("sourceTemplateId").asLong());
            }
        }
        if (body.has("themePack")) {
            if (body.path("themePack").isObject()) {
                template.setThemePackJson(body.path("themePack").toString());
            } else if (body.path("themePack").isNull()) {
                template.setThemePackJson(null);
            }
        }
        if (body.has("config") && body.path("config").isObject()) {
            applyConfigPayload(template, body.path("config"));
            return;
        }
        applyConfigPayload(template, body);
    }

    private void applyConfigPayload(AnalyticsScreenTemplate template, JsonNode config) {
        if (config == null || config.isNull() || !config.isObject()) {
            return;
        }
        if (config.has("width")) {
            template.setWidth(config.path("width").asInt(1920));
        }
        if (config.has("height")) {
            template.setHeight(config.path("height").asInt(1080));
        }
        if (config.has("backgroundColor")) {
            template.setBackgroundColor(config.path("backgroundColor").asText(null));
        }
        if (config.has("backgroundImage")) {
            template.setBackgroundImage(config.path("backgroundImage").isNull() ? null : config.path("backgroundImage").asText(null));
        }
        if (config.has("theme")) {
            template.setTheme(config.path("theme").isNull() ? null : config.path("theme").asText(null));
        }
        if (config.has("components") && config.path("components").isArray()) {
            template.setComponentsJson(config.path("components").toString());
        }
        if (config.has("globalVariables") && config.path("globalVariables").isArray()) {
            template.setVariablesJson(config.path("globalVariables").toString());
        }
        if (config.has("themePack")) {
            if (config.path("themePack").isObject()) {
                template.setThemePackJson(config.path("themePack").toString());
            } else if (config.path("themePack").isNull()) {
                template.setThemePackJson(null);
            }
        }
    }

    private void applyTemplateSnapshot(AnalyticsScreenTemplate template, String snapshotJson) {
        ObjectNode snapshot = parseObject(snapshotJson);
        if (snapshot.isEmpty()) {
            return;
        }
        String name = trimToNull(snapshot.path("name").asText(null));
        if (name != null) {
            template.setName(name);
        }
        template.setDescription(snapshot.path("description").isNull() ? null : snapshot.path("description").asText(null));
        String category = trimToNull(snapshot.path("category").asText(null));
        if (category != null) {
            template.setCategory(category);
        }
        template.setThumbnail(snapshot.path("thumbnail").isNull() ? null : snapshot.path("thumbnail").asText(null));
        if (snapshot.has("tagsJson")) {
            template.setTagsJson(snapshot.path("tagsJson").isNull() ? "[]" : snapshot.path("tagsJson").asText("[]"));
        }
        template.setWidth(snapshot.path("width").asInt(1920));
        template.setHeight(snapshot.path("height").asInt(1080));
        template.setBackgroundColor(snapshot.path("backgroundColor").isNull() ? null : snapshot.path("backgroundColor").asText(null));
        template.setBackgroundImage(snapshot.path("backgroundImage").isNull() ? null : snapshot.path("backgroundImage").asText(null));
        template.setTheme(snapshot.path("theme").isNull() ? null : snapshot.path("theme").asText(null));
        if (snapshot.has("componentsJson")) {
            template.setComponentsJson(snapshot.path("componentsJson").asText("[]"));
        }
        if (snapshot.has("variablesJson")) {
            template.setVariablesJson(snapshot.path("variablesJson").asText("[]"));
        }
        if (snapshot.path("sourceScreenId").canConvertToLong()) {
            template.setSourceScreenId(snapshot.path("sourceScreenId").asLong());
        } else if (snapshot.has("sourceScreenId") && snapshot.path("sourceScreenId").isNull()) {
            template.setSourceScreenId(null);
        }
        if (snapshot.path("sourceTemplateId").canConvertToLong()) {
            template.setSourceTemplateId(snapshot.path("sourceTemplateId").asLong());
        } else if (snapshot.has("sourceTemplateId") && snapshot.path("sourceTemplateId").isNull()) {
            template.setSourceTemplateId(null);
        }
        template.setVisibilityScope(normalizeVisibilityScope(snapshot.path("visibilityScope").asText(template.getVisibilityScope())));
        template.setOwnerDept(trimToNull(snapshot.path("ownerDept").asText(null)));
        if (snapshot.has("listed")) {
            template.setListed(snapshot.path("listed").asBoolean(true));
        }
        if (snapshot.has("themePackJson")) {
            template.setThemePackJson(snapshot.path("themePackJson").isNull() ? null : snapshot.path("themePackJson").asText(null));
        }
    }

    private ArrayNode parseArray(String json) {
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

    private ObjectNode parseObject(String json) {
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

    private String defaultJson(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private boolean sameDept(String expectedDept, String actualDept) {
        String left = trimToNull(expectedDept);
        String right = trimToNull(actualDept);
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private ResponseEntity<String> unauthorized() {
        return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
    }

    private ResponseEntity<String> forbidden() {
        return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
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
}
