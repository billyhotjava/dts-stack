package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing screen designer (大屏编辑器) resources.
 */
@RestController
@RequestMapping("/api/screens")
@Transactional
public class ScreenResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final PublicLinkService publicLinkService;
    private final ObjectMapper objectMapper;

    public ScreenResource(AnalyticsSessionService sessionService, AnalyticsScreenRepository screenRepository,
            PublicLinkService publicLinkService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.publicLinkService = publicLinkService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/screens : List all non-archived screens.
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        return ResponseEntity
                .ok(screenRepository.findAllByArchivedFalseOrderByIdDesc().stream().map(this::toListResponse).toList());
    }

    /**
     * GET /api/screens/{id} : Get a specific screen by ID.
     */
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> get(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDetailResponse(screen));
    }

    /**
     * POST /api/screens : Create a new screen.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> create(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
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
        screen.setComponentsJson(body != null && body.has("components") ? body.path("components").toString() : "[]");
        screen.setCreatorId(user.get().getId());
        screen.setArchived(false);

        screen = screenRepository.save(screen);
        return ResponseEntity.ok(toDetailResponse(screen));
    }

    /**
     * PUT /api/screens/{id} : Update an existing screen.
     */
    @PutMapping(path = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> update(@PathVariable("id") long id, @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }

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
            screen.setBackgroundImage(
                    body.path("backgroundImage").isNull() ? null : body.path("backgroundImage").asText(null));
        }
        if (body != null && body.has("components")) {
            screen.setComponentsJson(body.path("components").toString());
        }

        screenRepository.save(screen);
        return ResponseEntity.ok(toDetailResponse(screen));
    }

    /**
     * DELETE /api/screens/{id} : Archive (soft delete) a screen.
     */
    @DeleteMapping(path = "/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<ResponseEntity<String>> auth = MetabaseAuth.requireUser(sessionService, request);
        if (auth.isPresent()) {
            return auth.get();
        }
        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null) {
            return ResponseEntity.notFound().build();
        }
        screen.setArchived(true);
        screenRepository.save(screen);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createPublicLink(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        AnalyticsScreen screen = screenRepository.findById(id).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext ctx = PlatformContext.from(request);
        String uuid;
        try {
            uuid = publicLinkService.getOrCreateScoped(
                    PublicLinkService.MODEL_SCREEN, id, user.get().getId(), ctx.dept(), ctx.classification());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        return ResponseEntity.ok(Map.of("uuid", uuid));
    }

    @DeleteMapping(path = "/{id}/public_link", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deletePublicLink(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthenticated");
        }
        if (!screenRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        publicLinkService.delete(PublicLinkService.MODEL_SCREEN, id);
        return ResponseEntity.noContent().build();
    }

    private ObjectNode toListResponse(AnalyticsScreen screen) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", screen.getId());
        node.put("name", screen.getName());
        node.put("description", screen.getDescription());
        node.put("width", screen.getWidth());
        node.put("height", screen.getHeight());
        node.putPOJO("createdAt", screen.getCreatedAt());
        node.putPOJO("updatedAt", screen.getUpdatedAt());
        return node;
    }

    private ObjectNode toDetailResponse(AnalyticsScreen screen) {
        ObjectNode node = toListResponse(screen);
        node.put("backgroundColor", screen.getBackgroundColor());
        node.put("backgroundImage", screen.getBackgroundImage());

        // Parse components JSON
        if (screen.getComponentsJson() != null && !screen.getComponentsJson().isBlank()) {
            try {
                JsonNode components = objectMapper.readTree(screen.getComponentsJson());
                node.set("components", components);
            } catch (Exception e) {
                node.set("components", objectMapper.createArrayNode());
            }
        } else {
            node.set("components", objectMapper.createArrayNode());
        }

        return node;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
