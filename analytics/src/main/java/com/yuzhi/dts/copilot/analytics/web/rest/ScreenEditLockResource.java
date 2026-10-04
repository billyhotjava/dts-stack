package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAclService;
import com.yuzhi.dts.copilot.analytics.service.ScreenEditLockService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screens")
@Transactional
public class ScreenEditLockResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final ScreenAclService screenAclService;
    private final ScreenEditLockService screenEditLockService;
    private final ObjectMapper objectMapper;

    public ScreenEditLockResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            ScreenAclService screenAclService,
            ScreenEditLockService screenEditLockService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenAclService = screenAclService;
        this.screenEditLockService = screenEditLockService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/{id}/edit-lock", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> current(@PathVariable("id") long id, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreen screen = loadEditableScreen(id, user.get(), request, ScreenAclService.Permission.READ);
        if (screen == null) {
            return screenResponse(id, user.get(), request, ScreenAclService.Permission.READ);
        }
        return ResponseEntity.ok(toResponse(screenEditLockService.current(screen.getId(), user.get().getId())));
    }

    @PostMapping(path = "/{id}/edit-lock/acquire", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> acquire(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreen screen = loadEditableScreen(id, user.get(), request, ScreenAclService.Permission.EDIT);
        if (screen == null) {
            return screenResponse(id, user.get(), request, ScreenAclService.Permission.EDIT);
        }
        Integer ttlSeconds = body != null && body.has("ttlSeconds") ? body.path("ttlSeconds").asInt(120) : null;
        boolean forceTakeover = body != null && body.has("forceTakeover") && body.path("forceTakeover").asBoolean(false);
        if (forceTakeover) {
            PlatformContext context = PlatformContext.from(request);
            if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.MANAGE)) {
                return forbidden();
            }
        }
        ScreenEditLockService.LockAcquireResult result = screenEditLockService.acquire(
                screen.getId(),
                user.get(),
                requestIdFrom(request),
                ttlSeconds,
                forceTakeover);
        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(toResponse(result.snapshot()));
            case CONFLICT -> ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON).body(toResponse(result.snapshot()));
            case INVALID -> ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Invalid lock request");
            case NOT_FOUND -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("Lock not found");
        };
    }

    @PostMapping(path = "/{id}/edit-lock/heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> heartbeat(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreen screen = loadEditableScreen(id, user.get(), request, ScreenAclService.Permission.EDIT);
        if (screen == null) {
            return screenResponse(id, user.get(), request, ScreenAclService.Permission.EDIT);
        }
        Integer ttlSeconds = body != null && body.has("ttlSeconds") ? body.path("ttlSeconds").asInt(120) : null;
        ScreenEditLockService.LockAcquireResult result = screenEditLockService.heartbeat(
                screen.getId(),
                user.get(),
                requestIdFrom(request),
                ttlSeconds);
        return switch (result.status()) {
            case SUCCESS -> ResponseEntity.ok(toResponse(result.snapshot()));
            case CONFLICT -> ResponseEntity.status(409).contentType(MediaType.APPLICATION_JSON).body(toResponse(result.snapshot()));
            case NOT_FOUND -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body("Lock not found or expired");
            case INVALID -> ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Invalid lock request");
        };
    }

    @PostMapping(path = "/{id}/edit-lock/release", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> release(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return unauthorized();
        }
        AnalyticsScreen screen = loadEditableScreen(id, user.get(), request, ScreenAclService.Permission.EDIT);
        if (screen == null) {
            return screenResponse(id, user.get(), request, ScreenAclService.Permission.EDIT);
        }
        screenEditLockService.release(screen.getId(), user.get().getId());
        return ResponseEntity.ok(toResponse(screenEditLockService.current(screen.getId(), user.get().getId())));
    }

    private AnalyticsScreen loadEditableScreen(
            long screenId,
            AnalyticsUser user,
            HttpServletRequest request,
            ScreenAclService.Permission permission) {
        AnalyticsScreen screen = screenRepository.findById(screenId).orElse(null);
        if (screen == null || screen.isArchived()) {
            return null;
        }
        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user, context, permission)) {
            return null;
        }
        return screen;
    }

    private ResponseEntity<?> screenResponse(
            long screenId,
            AnalyticsUser user,
            HttpServletRequest request,
            ScreenAclService.Permission permission) {
        AnalyticsScreen screen = screenRepository.findById(screenId).orElse(null);
        if (screen == null || screen.isArchived()) {
            return ResponseEntity.notFound().build();
        }
        PlatformContext context = PlatformContext.from(request);
        if (!screenAclService.hasPermission(screen, user, context, permission)) {
            return forbidden();
        }
        return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body("Unexpected lock state");
    }

    private ObjectNode toResponse(ScreenEditLockService.LockSnapshot snapshot) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("active", snapshot.active());
        node.putPOJO("screenId", snapshot.screenId());
        node.putPOJO("ownerId", snapshot.ownerId());
        if (snapshot.ownerName() != null) {
            node.put("ownerName", snapshot.ownerName());
        } else {
            node.putNull("ownerName");
        }
        node.put("mine", snapshot.mine());
        if (snapshot.requestId() != null) {
            node.put("requestId", snapshot.requestId());
        } else {
            node.putNull("requestId");
        }
        node.putPOJO("acquiredAt", snapshot.acquiredAt());
        node.putPOJO("heartbeatAt", snapshot.heartbeatAt());
        node.putPOJO("expireAt", snapshot.expireAt());
        node.put("ttlSeconds", snapshot.ttlSeconds());
        return node;
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
}
