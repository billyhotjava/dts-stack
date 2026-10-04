package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAclService;
import com.yuzhi.dts.copilot.analytics.service.ScreenAuditService;
import com.yuzhi.dts.copilot.analytics.service.ScreenCollaborationRealtimeService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Screen collaboration endpoints (lightweight comments/annotations).
 */
@RestController
@RequestMapping("/api/screens")
@Transactional
public class ScreenCollaborationResource {

    private static final String ACTION_COMMENT_ADD = "screen.comment.add";
    private static final String ACTION_COMMENT_RESOLVE = "screen.comment.resolve";
    private static final String ACTION_COMMENT_REOPEN = "screen.comment.reopen";
    private static final int DEFAULT_PRESENCE_TTL_SECONDS = 45;
    private static final int PRESENCE_LIMIT_MAX = 100;
    private static final ConcurrentHashMap<Long, ConcurrentHashMap<String, PresenceState>> SCREEN_PRESENCE =
            new ConcurrentHashMap<>();

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final ScreenAclService screenAclService;
    private final ScreenAuditService screenAuditService;
    private final ScreenCollaborationRealtimeService realtimeService;
    private final ObjectMapper objectMapper;

    public ScreenCollaborationResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService,
            ScreenCollaborationRealtimeService realtimeService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
        this.realtimeService = realtimeService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> listComments(
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
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }

        List<CommentState> comments = rebuildCommentStates(screen.getId(), limit);
        return ResponseEntity.ok(comments.stream().map(this::toCommentResponse).toList());
    }

    @GetMapping(path = "/{id}/comments/changes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> listCommentChanges(
            @PathVariable("id") long id,
            @RequestParam(value = "sinceId", required = false, defaultValue = "0") long sinceId,
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
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.READ)) {
            return forbidden();
        }

        return ResponseEntity.ok(buildCommentChanges(screen.getId(), sinceId, limit));
    }

    @GetMapping(path = "/{id}/comments/live", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> listCommentChangesLive(
            @PathVariable("id") long id,
            @RequestParam(value = "sinceId", required = false, defaultValue = "0") long sinceId,
            @RequestParam(value = "limit", required = false, defaultValue = "200") int limit,
            @RequestParam(value = "waitMs", required = false, defaultValue = "12000") int waitMs,
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

        int safeWaitMs = Math.max(1000, Math.min(waitMs, 30000));
        long deadline = System.currentTimeMillis() + safeWaitMs;
        ObjectNode result = null;
        while (true) {
            result = buildCommentChanges(screen.getId(), sinceId, limit);
            int changedRows = result.path("rows").isArray() ? result.path("rows").size() : 0;
            boolean fullReload = result.path("fullReload").asBoolean(false);
            long cursor = result.path("cursor").asLong(sinceId);
            if (fullReload || changedRows > 0 || cursor > sinceId || System.currentTimeMillis() >= deadline) {
                break;
            }
            try {
                Thread.sleep(700L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (result == null) {
            result = buildCommentChanges(screen.getId(), sinceId, limit);
        }
        result.put("waitMs", safeWaitMs);
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/{id}/comments/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Object streamCommentChanges(
            @PathVariable("id") long id,
            @RequestParam(value = "sinceId", required = false, defaultValue = "0") long sinceId,
            @RequestParam(value = "limit", required = false, defaultValue = "200") int limit,
            @RequestParam(value = "durationSec", required = false, defaultValue = "45") int durationSec,
            @RequestParam(value = "waitMs", required = false, defaultValue = "1200") int waitMs,
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

        int safeDurationSec = Math.max(15, Math.min(durationSec, 120));
        int safeWaitMs = Math.max(500, Math.min(waitMs, 5000));
        SseEmitter emitter = new SseEmitter((safeDurationSec + 5L) * 1000L);

        CompletableFuture.runAsync(() -> {
            long cursor = Math.max(0L, sinceId);
            long deadline = System.currentTimeMillis() + safeDurationSec * 1000L;
            try {
                emitter.send(SseEmitter.event()
                        .name("ready")
                        .data(Map.of("cursor", cursor, "durationSec", safeDurationSec, "waitMs", safeWaitMs)));

                while (System.currentTimeMillis() < deadline) {
                    ObjectNode delta = buildCommentChanges(screen.getId(), cursor, limit);
                    int changedRows = delta.path("rows").isArray() ? delta.path("rows").size() : 0;
                    boolean fullReload = delta.path("fullReload").asBoolean(false);
                    long nextCursor = delta.path("cursor").asLong(cursor);
                    if (fullReload || changedRows > 0 || nextCursor > cursor) {
                        cursor = Math.max(cursor, nextCursor);
                        emitter.send(SseEmitter.event().name("comment-change").data(delta));
                    } else {
                        emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("cursor", cursor)));
                    }
                    Thread.sleep(safeWaitMs);
                }

                emitter.send(SseEmitter.event().name("stream-end").data(Map.of("cursor", cursor)));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @GetMapping(path = "/{id}/collaboration/presence", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ResponseEntity<?> listPresence(
            @PathVariable("id") long id,
            @RequestParam(value = "ttlSeconds", required = false, defaultValue = "45") int ttlSeconds,
            @RequestParam(value = "sessionId", required = false) String sessionId,
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
        int safeTtlSeconds = sanitizeTtlSeconds(ttlSeconds);
        String safeSessionId = resolvePresenceSessionIdFromRaw(sessionId, request, user.get());
        return ResponseEntity.ok(buildPresenceResponse(screen.getId(), safeTtlSeconds, safeSessionId));
    }

    @GetMapping(path = "/{id}/collaboration/presence/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Object streamPresence(
            @PathVariable("id") long id,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "ttlSeconds", required = false, defaultValue = "45") int ttlSeconds,
            @RequestParam(value = "durationSec", required = false, defaultValue = "45") int durationSec,
            @RequestParam(value = "waitMs", required = false, defaultValue = "1200") int waitMs,
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
        int safeTtlSeconds = sanitizeTtlSeconds(ttlSeconds);
        int safeDurationSec = Math.max(15, Math.min(durationSec, 120));
        int safeWaitMs = Math.max(500, Math.min(waitMs, 5000));
        String safeSessionId = resolvePresenceSessionIdFromRaw(sessionId, request, user.get());

        SseEmitter emitter = new SseEmitter((safeDurationSec + 5L) * 1000L);
        CompletableFuture.runAsync(() -> {
            long deadline = System.currentTimeMillis() + safeDurationSec * 1000L;
            String digest = null;
            try {
                ObjectNode initial = buildPresenceResponse(screen.getId(), safeTtlSeconds, safeSessionId);
                digest = buildPresenceDigest(initial);
                emitter.send(SseEmitter.event()
                        .name("ready")
                        .data(Map.of(
                                "sessionId", safeSessionId,
                                "durationSec", safeDurationSec,
                                "waitMs", safeWaitMs,
                                "ttlSeconds", safeTtlSeconds)));
                emitter.send(SseEmitter.event().name("presence-change").data(initial));

                while (System.currentTimeMillis() < deadline) {
                    ObjectNode snapshot = buildPresenceResponse(screen.getId(), safeTtlSeconds, safeSessionId);
                    String nextDigest = buildPresenceDigest(snapshot);
                    if (digest == null || !digest.equals(nextDigest)) {
                        digest = nextDigest;
                        emitter.send(SseEmitter.event().name("presence-change").data(snapshot));
                    } else {
                        emitter.send(SseEmitter.event().name("heartbeat").data(Map.of(
                                "sessionId", safeSessionId,
                                "activeCount", snapshot.path("activeCount").asInt(0))));
                    }
                    Thread.sleep(safeWaitMs);
                }
                emitter.send(SseEmitter.event().name("stream-end").data(Map.of("sessionId", safeSessionId)));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    @PostMapping(path = "/{id}/collaboration/presence/heartbeat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> heartbeatPresence(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            @RequestParam(value = "ttlSeconds", required = false, defaultValue = "45") int ttlSeconds,
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
        int safeTtlSeconds = sanitizeTtlSeconds(ttlSeconds);
        String sessionId = resolvePresenceSessionId(body, request, user.get());
        String componentId = trimToNull(body == null ? null : body.path("componentId").asText(null));
        boolean typing = body != null && body.path("typing").asBoolean(false);
        String clientType = trimToNull(body == null ? null : body.path("clientType").asText(null));
        List<String> selectedIds = parseSelectedIds(body == null ? null : body.path("selectedIds"));

        PresenceState state = new PresenceState();
        state.sessionId = sessionId;
        state.userId = user.get().getId();
        state.displayName = resolveDisplayName(user.get());
        state.componentId = componentId;
        state.typing = typing;
        state.clientType = clientType;
        state.selectedCount = selectedIds.size();
        state.selectionPreview = selectedIds.isEmpty()
                ? null
                : String.join(",", selectedIds.subList(0, Math.min(selectedIds.size(), 3)));
        state.lastSeenAt = Instant.now();
        upsertPresence(screen.getId(), state);
        return ResponseEntity.ok(buildPresenceResponse(screen.getId(), safeTtlSeconds, sessionId));
    }

    @PostMapping(path = "/{id}/collaboration/presence/leave", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> leavePresence(
            @PathVariable("id") long id,
            @RequestBody(required = false) JsonNode body,
            @RequestParam(value = "ttlSeconds", required = false, defaultValue = "45") int ttlSeconds,
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
        int safeTtlSeconds = sanitizeTtlSeconds(ttlSeconds);
        String sessionId = resolvePresenceSessionId(body, request, user.get());
        removePresence(screen.getId(), sessionId);
        return ResponseEntity.ok(buildPresenceResponse(screen.getId(), safeTtlSeconds, sessionId));
    }

    @PostMapping(path = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createComment(
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
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.EDIT)) {
            return forbidden();
        }

        String message = trimToNull(body == null ? null : body.path("message").asText(null));
        if (message == null) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Comment message is required");
        }
        if (message.length() > 2000) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("Comment message is too long");
        }

        String componentId = trimToNull(body == null ? null : body.path("componentId").asText(null));
        JsonNode anchor = body == null ? null : body.path("anchor");
        JsonNode mentions = body == null ? null : body.path("mentions");

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", message);
        if (componentId != null) {
            payload.put("componentId", componentId);
        }
        if (anchor != null && anchor.isObject()) {
            payload.set("anchor", anchor.deepCopy());
        }
        if (mentions != null && mentions.isArray()) {
            payload.set("mentions", mentions.deepCopy());
        }

        AnalyticsScreenAuditLog log = screenAuditService.logAndReturn(
                screen.getId(),
                user.get().getId(),
                ACTION_COMMENT_ADD,
                null,
                payload,
                requestIdFrom(request));
        if (log == null || log.getId() == null) {
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN).body("Create comment failed");
        }

        CommentState state = new CommentState();
        state.id = log.getId();
        state.screenId = screen.getId();
        state.componentId = componentId;
        state.message = message;
        state.anchor = payload.path("anchor").isObject() ? payload.path("anchor").deepCopy() : null;
        state.mentions = payload.path("mentions").isArray() ? payload.path("mentions").deepCopy() : null;
        state.createdBy = log.getActorId();
        state.createdAt = log.getCreatedAt();
        state.requestId = log.getRequestId();
        state.status = "open";
        ObjectNode response = toCommentResponse(state);
        realtimeService.broadcastCommentChange(screen.getId(), response);
        return ResponseEntity.ok(response);
    }

    @PostMapping(path = "/{id}/comments/{commentId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> resolveComment(
            @PathVariable("id") long id,
            @PathVariable("commentId") long commentId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return updateCommentStatus(id, commentId, true, body, request);
    }

    @PostMapping(path = "/{id}/comments/{commentId}/reopen", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> reopenComment(
            @PathVariable("id") long id,
            @PathVariable("commentId") long commentId,
            @RequestBody(required = false) JsonNode body,
            HttpServletRequest request) {
        return updateCommentStatus(id, commentId, false, body, request);
    }

    private ResponseEntity<?> updateCommentStatus(
            long id,
            long commentId,
            boolean resolve,
            JsonNode body,
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
        if (!screenAclService.hasPermission(screen, user.get(), context, ScreenAclService.Permission.EDIT)) {
            return forbidden();
        }

        Map<Long, CommentState> commentMap = rebuildCommentStateMap(screen.getId(), 1000);
        CommentState target = commentMap.get(commentId);
        if (target == null) {
            return ResponseEntity.notFound().build();
        }

        boolean alreadyResolved = "resolved".equals(target.status);
        if ((resolve && alreadyResolved) || (!resolve && !alreadyResolved)) {
            return ResponseEntity.ok(toCommentResponse(target));
        }

        String note = trimToNull(body == null ? null : body.path("note").asText(null));
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("commentId", commentId);
        if (note != null) {
            payload.put("note", note);
        }

        AnalyticsScreenAuditLog actionLog = screenAuditService.logAndReturn(
                screen.getId(),
                user.get().getId(),
                resolve ? ACTION_COMMENT_RESOLVE : ACTION_COMMENT_REOPEN,
                null,
                payload,
                requestIdFrom(request));

        if (resolve) {
            target.status = "resolved";
            target.resolvedAt = actionLog == null ? Instant.now() : actionLog.getCreatedAt();
            target.resolvedBy = user.get().getId();
            target.resolutionNote = note;
        } else {
            target.status = "open";
            target.resolvedAt = null;
            target.resolvedBy = null;
            target.resolutionNote = null;
        }
        ObjectNode response = toCommentResponse(target);
        realtimeService.broadcastCommentChange(screen.getId(), response);
        return ResponseEntity.ok(response);
    }

    private List<CommentState> rebuildCommentStates(Long screenId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int scanLimit = Math.max(safeLimit * 6, safeLimit);
        scanLimit = Math.min(scanLimit, 1000);

        Map<Long, CommentState> stateMap = rebuildCommentStateMap(screenId, scanLimit);
        List<CommentState> result = new ArrayList<>(stateMap.values());
        result.sort(
                Comparator.comparing((CommentState item) -> item.createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing((CommentState item) -> item.id, Comparator.reverseOrder()));

        if (result.size() > safeLimit) {
            return new ArrayList<>(result.subList(0, safeLimit));
        }
        return result;
    }

    private ObjectNode buildCommentChanges(Long screenId, long sinceId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int scanLimit = Math.max(safeLimit * 8, 800);
        scanLimit = Math.min(scanLimit, 2000);
        List<AnalyticsScreenAuditLog> timeline = screenAuditService.listByScreenId(screenId, scanLimit);
        timeline.sort(
                Comparator.comparing(
                                AnalyticsScreenAuditLog::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(log -> log.getId() == null ? 0L : log.getId()));

        long minEventId = 0L;
        long maxEventId = 0L;
        Set<Long> changedCommentIds = new LinkedHashSet<>();
        for (AnalyticsScreenAuditLog log : timeline) {
            if (log == null || log.getId() == null || log.getId() <= 0) {
                continue;
            }
            long eventId = log.getId();
            if (minEventId == 0L || eventId < minEventId) {
                minEventId = eventId;
            }
            if (eventId > maxEventId) {
                maxEventId = eventId;
            }
            if (eventId <= sinceId) {
                continue;
            }
            String action = trimToNull(log.getAction());
            if (ACTION_COMMENT_ADD.equals(action)) {
                changedCommentIds.add(eventId);
                continue;
            }
            if (ACTION_COMMENT_RESOLVE.equals(action) || ACTION_COMMENT_REOPEN.equals(action)) {
                long commentId = resolveCommentId(parseObject(log.getAfterJson()));
                if (commentId > 0L) {
                    changedCommentIds.add(commentId);
                }
            }
        }

        Map<Long, CommentState> stateMap = rebuildCommentStateMap(screenId, scanLimit);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("cursor", maxEventId);
        result.put("sinceId", sinceId);

        boolean fullReload = sinceId > 0 && minEventId > 0 && sinceId < minEventId;
        result.put("fullReload", fullReload);
        if (fullReload) {
            List<CommentState> rows = rebuildCommentStates(screenId, safeLimit);
            result.set("rows", objectMapper.valueToTree(rows.stream().map(this::toCommentResponse).toList()));
            return result;
        }

        if (sinceId <= 0) {
            List<CommentState> rows = rebuildCommentStates(screenId, safeLimit);
            result.set("rows", objectMapper.valueToTree(rows.stream().map(this::toCommentResponse).toList()));
            return result;
        }

        List<CommentState> changedRows = changedCommentIds.stream()
                .map(stateMap::get)
                .filter(item -> item != null)
                .sorted(
                        Comparator.comparing((CommentState item) -> item.createdAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing((CommentState item) -> item.id, Comparator.reverseOrder()))
                .limit(safeLimit)
                .toList();
        result.set("rows", objectMapper.valueToTree(changedRows.stream().map(this::toCommentResponse).toList()));
        return result;
    }

    private Map<Long, CommentState> rebuildCommentStateMap(Long screenId, int scanLimit) {
        List<AnalyticsScreenAuditLog> timeline = screenAuditService.listByScreenId(screenId, scanLimit);
        timeline.sort(
                Comparator.comparing(
                        AnalyticsScreenAuditLog::getCreatedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(log -> log.getId() == null ? 0L : log.getId()));

        Map<Long, CommentState> comments = new LinkedHashMap<>();
        for (AnalyticsScreenAuditLog log : timeline) {
            if (log == null || log.getId() == null) {
                continue;
            }
            String action = trimToNull(log.getAction());
            if (action == null) {
                continue;
            }
            JsonNode after = parseObject(log.getAfterJson());
            if (ACTION_COMMENT_ADD.equals(action)) {
                CommentState created = parseCommentCreate(log, after);
                if (created != null) {
                    comments.put(created.id, created);
                }
                continue;
            }

            if (ACTION_COMMENT_RESOLVE.equals(action) || ACTION_COMMENT_REOPEN.equals(action)) {
                long targetId = resolveCommentId(after);
                if (targetId <= 0) {
                    continue;
                }
                CommentState target = comments.get(targetId);
                if (target == null) {
                    continue;
                }
                if (ACTION_COMMENT_RESOLVE.equals(action)) {
                    target.status = "resolved";
                    target.resolvedAt = log.getCreatedAt();
                    target.resolvedBy = log.getActorId();
                    target.resolutionNote = trimToNull(after.path("note").asText(null));
                } else {
                    target.status = "open";
                    target.resolvedAt = null;
                    target.resolvedBy = null;
                    target.resolutionNote = null;
                }
            }
        }
        return comments;
    }

    private ObjectNode buildPresenceResponse(Long screenId, int ttlSeconds, String sessionId) {
        Instant now = Instant.now();
        ConcurrentHashMap<String, PresenceState> screenMap =
                SCREEN_PRESENCE.computeIfAbsent(screenId, key -> new ConcurrentHashMap<>());
        prunePresence(screenMap, now, ttlSeconds);

        List<ObjectNode> rows = screenMap.values().stream()
                .sorted(
                        Comparator.comparing((PresenceState item) -> item.lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                                .thenComparing(item -> item.sessionId == null ? "" : item.sessionId))
                .limit(PRESENCE_LIMIT_MAX)
                .map(item -> toPresenceResponse(item, now, sessionId))
                .toList();

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", now);
        result.put("ttlSeconds", ttlSeconds);
        if (sessionId != null) {
            result.put("meSessionId", sessionId);
        } else {
            result.putNull("meSessionId");
        }
        result.put("activeCount", rows.size());
        result.set("rows", objectMapper.valueToTree(rows));
        return result;
    }

    private String buildPresenceDigest(ObjectNode payload) {
        if (payload == null) {
            return "";
        }
        JsonNode rows = payload.path("rows");
        int activeCount = payload.path("activeCount").asInt(0);
        return activeCount + "|" + rows.toString();
    }

    private ObjectNode toPresenceResponse(PresenceState state, Instant now, String meSessionId) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sessionId", state.sessionId == null ? "" : state.sessionId);
        node.putPOJO("userId", state.userId);
        node.put("displayName", state.displayName == null ? "" : state.displayName);
        if (state.componentId != null) {
            node.put("componentId", state.componentId);
        } else {
            node.putNull("componentId");
        }
        node.put("typing", state.typing);
        if (state.clientType != null) {
            node.put("clientType", state.clientType);
        } else {
            node.putNull("clientType");
        }
        if (state.selectedCount != null) {
            node.put("selectedCount", state.selectedCount);
        } else {
            node.putNull("selectedCount");
        }
        if (state.selectionPreview != null) {
            node.put("selectionPreview", state.selectionPreview);
        } else {
            node.putNull("selectionPreview");
        }
        node.putPOJO("lastSeenAt", state.lastSeenAt);
        long idleSeconds = 0L;
        if (state.lastSeenAt != null) {
            long delta = now.getEpochSecond() - state.lastSeenAt.getEpochSecond();
            idleSeconds = Math.max(0L, delta);
        }
        node.put("idleSeconds", idleSeconds);
        node.put("mine", meSessionId != null && meSessionId.equals(state.sessionId));
        return node;
    }

    private void upsertPresence(Long screenId, PresenceState state) {
        if (screenId == null || state == null || state.sessionId == null || state.sessionId.isBlank()) {
            return;
        }
        ConcurrentHashMap<String, PresenceState> screenMap =
                SCREEN_PRESENCE.computeIfAbsent(screenId, key -> new ConcurrentHashMap<>());
        Instant now = state.lastSeenAt == null ? Instant.now() : state.lastSeenAt;
        prunePresence(screenMap, now, DEFAULT_PRESENCE_TTL_SECONDS);
        screenMap.put(state.sessionId, state);
        trimPresenceSize(screenMap, PRESENCE_LIMIT_MAX * 2);
    }

    private void removePresence(Long screenId, String sessionId) {
        if (screenId == null || sessionId == null || sessionId.isBlank()) {
            return;
        }
        ConcurrentHashMap<String, PresenceState> screenMap = SCREEN_PRESENCE.get(screenId);
        if (screenMap == null) {
            return;
        }
        screenMap.remove(sessionId);
        if (screenMap.isEmpty()) {
            SCREEN_PRESENCE.remove(screenId, screenMap);
        }
    }

    private void prunePresence(ConcurrentHashMap<String, PresenceState> screenMap, Instant now, int ttlSeconds) {
        if (screenMap == null || screenMap.isEmpty()) {
            return;
        }
        long ttl = Math.max(10, ttlSeconds);
        for (Map.Entry<String, PresenceState> entry : screenMap.entrySet()) {
            PresenceState state = entry.getValue();
            Instant seenAt = state == null ? null : state.lastSeenAt;
            if (seenAt == null) {
                screenMap.remove(entry.getKey());
                continue;
            }
            long idleSeconds = now.getEpochSecond() - seenAt.getEpochSecond();
            if (idleSeconds > ttl) {
                screenMap.remove(entry.getKey());
            }
        }
    }

    private void trimPresenceSize(ConcurrentHashMap<String, PresenceState> screenMap, int maxSize) {
        if (screenMap == null || screenMap.size() <= maxSize || maxSize <= 0) {
            return;
        }
        List<Map.Entry<String, PresenceState>> entries = new ArrayList<>(screenMap.entrySet());
        entries.sort(Comparator.comparing(
                entry -> entry.getValue() == null ? null : entry.getValue().lastSeenAt,
                Comparator.nullsFirst(Comparator.naturalOrder())));
        int removeCount = Math.max(0, entries.size() - maxSize);
        for (int i = 0; i < removeCount; i++) {
            Map.Entry<String, PresenceState> entry = entries.get(i);
            if (entry == null || entry.getKey() == null) {
                continue;
            }
            screenMap.remove(entry.getKey());
        }
    }

    private int sanitizeTtlSeconds(int ttlSeconds) {
        if (ttlSeconds <= 0) {
            return DEFAULT_PRESENCE_TTL_SECONDS;
        }
        return Math.max(15, Math.min(ttlSeconds, 180));
    }

    private String resolvePresenceSessionId(JsonNode body, HttpServletRequest request, AnalyticsUser user) {
        String bodySessionId = trimToNull(body == null ? null : body.path("sessionId").asText(null));
        return resolvePresenceSessionIdFromRaw(bodySessionId, request, user);
    }

    private String resolvePresenceSessionIdFromRaw(String rawSessionId, HttpServletRequest request, AnalyticsUser user) {
        String bodySessionId = trimToNull(rawSessionId);
        if (bodySessionId != null) {
            return bodySessionId.length() > 96 ? bodySessionId.substring(0, 96) : bodySessionId;
        }
        String requestId = requestIdFrom(request);
        if (requestId != null) {
            return requestId.length() > 96 ? requestId.substring(0, 96) : requestId;
        }
        String remote = request == null ? "unknown" : trimToNull(request.getRemoteAddr());
        String userId = user == null || user.getId() == null ? "anon" : String.valueOf(user.getId());
        String mixed = "u" + userId + "-" + (remote == null ? "local" : remote.replace(':', '_').replace('.', '_'));
        return mixed.length() > 96 ? mixed.substring(0, 96) : mixed;
    }

    private String resolveDisplayName(AnalyticsUser user) {
        if (user == null) {
            return "unknown";
        }
        String first = trimToNull(user.getFirstName());
        String last = trimToNull(user.getLastName());
        if (first != null && last != null) {
            return first + " " + last;
        }
        if (first != null) {
            return first;
        }
        if (last != null) {
            return last;
        }
        String uname = trimToNull(user.getUsername());
        if (uname != null) {
            return uname;
        }
        return user.getId() == null ? "unknown" : ("user#" + user.getId());
    }

    private CommentState parseCommentCreate(AnalyticsScreenAuditLog log, JsonNode payload) {
        String message = trimToNull(payload.path("message").asText(null));
        if (message == null) {
            return null;
        }

        CommentState state = new CommentState();
        state.id = log.getId();
        state.screenId = log.getScreenId();
        state.componentId = trimToNull(payload.path("componentId").asText(null));
        state.message = message;
        state.anchor = payload.path("anchor").isObject() ? payload.path("anchor").deepCopy() : null;
        state.mentions = payload.path("mentions").isArray() ? payload.path("mentions").deepCopy() : null;
        state.createdBy = log.getActorId();
        state.createdAt = log.getCreatedAt();
        state.requestId = trimToNull(log.getRequestId());
        state.status = "open";
        return state;
    }

    private ObjectNode toCommentResponse(CommentState state) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", state.id);
        node.putPOJO("screenId", state.screenId);
        if (state.componentId != null) {
            node.put("componentId", state.componentId);
        } else {
            node.putNull("componentId");
        }
        node.put("message", state.message == null ? "" : state.message);
        if (state.anchor != null) {
            node.set("anchor", state.anchor.deepCopy());
        } else {
            node.putNull("anchor");
        }
        if (state.mentions != null) {
            node.set("mentions", state.mentions.deepCopy());
        } else {
            node.set("mentions", objectMapper.createArrayNode());
        }
        node.putPOJO("createdBy", state.createdBy);
        node.putPOJO("createdAt", state.createdAt);
        node.put("status", state.status == null ? "open" : state.status);
        node.putPOJO("resolvedBy", state.resolvedBy);
        node.putPOJO("resolvedAt", state.resolvedAt);
        if (state.resolutionNote != null) {
            node.put("resolutionNote", state.resolutionNote);
        } else {
            node.putNull("resolutionNote");
        }
        if (state.requestId != null) {
            node.put("requestId", state.requestId);
        } else {
            node.putNull("requestId");
        }
        return node;
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

    private long resolveCommentId(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            return 0L;
        }
        JsonNode commentIdNode = payload.path("commentId");
        if (commentIdNode.isNumber()) {
            return commentIdNode.asLong(0L);
        }
        String value = trimToNull(commentIdNode.asText(null));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return 0L;
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

    private List<String> parseSelectedIds(JsonNode selectedIdsNode) {
        if (selectedIdsNode == null || !selectedIdsNode.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : selectedIdsNode) {
            String value = trimToNull(item == null ? null : item.asText(null));
            if (value == null) {
                continue;
            }
            result.add(value);
            if (result.size() >= 20) {
                break;
            }
        }
        return result;
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

    private static class CommentState {
        long id;
        Long screenId;
        String componentId;
        String message;
        JsonNode anchor;
        JsonNode mentions;
        Long createdBy;
        Instant createdAt;
        String status;
        Long resolvedBy;
        Instant resolvedAt;
        String resolutionNote;
        String requestId;
    }

    private static class PresenceState {
        String sessionId;
        Long userId;
        String displayName;
        String componentId;
        boolean typing;
        String clientType;
        Integer selectedCount;
        String selectionPreview;
        Instant lastSeenAt;
    }
}
