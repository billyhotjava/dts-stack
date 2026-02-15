package com.yuzhi.dts.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.ScreenAclService;
import com.yuzhi.dts.analytics.service.ScreenAuditService;
import com.yuzhi.dts.analytics.web.support.MetabaseAuth;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenRepository screenRepository;
    private final ScreenAclService screenAclService;
    private final ScreenAuditService screenAuditService;
    private final ObjectMapper objectMapper;

    public ScreenCollaborationResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenRepository screenRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService,
            ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.screenRepository = screenRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping(path = "/{id}/comments", produces = MediaType.APPLICATION_JSON_VALUE)
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

        return ResponseEntity.ok(toCommentResponse(state));
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
        return ResponseEntity.ok(toCommentResponse(target));
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
}
