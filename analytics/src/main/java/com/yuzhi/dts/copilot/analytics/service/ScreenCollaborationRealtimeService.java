package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsUserRepository;
import com.yuzhi.dts.copilot.analytics.web.support.PlatformContext;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Service
public class ScreenCollaborationRealtimeService {

    private static final int PRESENCE_TTL_SECONDS = 90;
    private static final String ACTION_COMMENT_ADD = "screen.comment.add";
    private static final String ACTION_COMMENT_RESOLVE = "screen.comment.resolve";
    private static final String ACTION_COMMENT_REOPEN = "screen.comment.reopen";

    private final ObjectMapper objectMapper;
    private final AnalyticsScreenRepository screenRepository;
    private final AnalyticsUserRepository userRepository;
    private final ScreenAclService screenAclService;
    private final ScreenAuditService screenAuditService;
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<String>> screenSockets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, WebSocketSession> socketSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SocketBinding> socketBindings = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, PresenceState>> screenPresence =
            new ConcurrentHashMap<>();

    public ScreenCollaborationRealtimeService(
            ObjectMapper objectMapper,
            AnalyticsScreenRepository screenRepository,
            AnalyticsUserRepository userRepository,
            ScreenAclService screenAclService,
            ScreenAuditService screenAuditService) {
        this.objectMapper = objectMapper;
        this.screenRepository = screenRepository;
        this.userRepository = userRepository;
        this.screenAclService = screenAclService;
        this.screenAuditService = screenAuditService;
    }

    public void register(
            WebSocketSession socket,
            long screenId,
            String presenceSessionId,
            Long userId,
            String displayName,
            String clientType,
            String roles) {
        SocketBinding binding = new SocketBinding(screenId, presenceSessionId, userId, displayName, clientType, roles);
        socketSessions.put(socket.getId(), socket);
        socketBindings.put(socket.getId(), binding);
        screenSockets.computeIfAbsent(screenId, key -> new CopyOnWriteArraySet<>()).add(socket.getId());

        PresenceState state = new PresenceState();
        state.sessionId = presenceSessionId;
        state.userId = userId;
        state.displayName = displayName;
        state.clientType = clientType;
        state.lastSeenAt = Instant.now();
        upsertPresence(screenId, state);

        sendEvent(socket, "ready", Map.of(
                "screenId", screenId,
                "sessionId", presenceSessionId,
                "ttlSeconds", PRESENCE_TTL_SECONDS));
        broadcastPresence(screenId);
    }

    public void unregister(WebSocketSession socket) {
        if (socket == null) {
            return;
        }
        String socketId = socket.getId();
        SocketBinding binding = socketBindings.remove(socketId);
        socketSessions.remove(socketId);
        if (binding == null) {
            return;
        }
        CopyOnWriteArraySet<String> sockets = screenSockets.get(binding.screenId);
        if (sockets != null) {
            sockets.remove(socketId);
            if (sockets.isEmpty()) {
                screenSockets.remove(binding.screenId, sockets);
            }
        }
        removePresence(binding.screenId, binding.presenceSessionId);
        broadcastPresence(binding.screenId);
    }

    public void handleMessage(WebSocketSession socket, String payloadText) {
        SocketBinding binding = socketBindings.get(socket.getId());
        if (binding == null) {
            return;
        }
        JsonNode payload;
        try {
            payload = objectMapper.readTree(payloadText == null ? "{}" : payloadText);
        } catch (Exception ex) {
            sendError(socket, "invalid_json");
            return;
        }
        String type = normalize(payload.path("type").asText(null));
        if (type == null || "presence.heartbeat".equals(type) || "presence.update".equals(type)) {
            updatePresenceFromPayload(binding, payload);
            broadcastPresence(binding.screenId);
            return;
        }
        if ("presence.leave".equals(type)) {
            removePresence(binding.screenId, binding.presenceSessionId);
            broadcastPresence(binding.screenId);
            return;
        }
        if ("ping".equals(type)) {
            sendEvent(socket, "pong", Map.of("at", Instant.now().toString()));
            return;
        }
        if ("comment.create".equals(type)) {
            JsonNode command = payload.path("payload").isObject() ? payload.path("payload") : payload;
            handleCommentCreate(socket, binding, command);
            return;
        }
        if ("comment.resolve".equals(type) || "comment.reopen".equals(type)) {
            JsonNode command = payload.path("payload").isObject() ? payload.path("payload") : payload;
            handleCommentStatusChange(socket, binding, command, "comment.resolve".equals(type));
            return;
        }
        sendError(socket, "unsupported_type");
    }

    public void broadcastCommentChange(long screenId, Object payload) {
        CopyOnWriteArraySet<String> sockets = screenSockets.get(screenId);
        if (sockets == null || sockets.isEmpty()) {
            return;
        }
        for (String socketId : sockets) {
            WebSocketSession socket = socketSessions.get(socketId);
            if (socket == null || !socket.isOpen()) {
                continue;
            }
            sendEvent(socket, "comment-change", payload);
        }
    }

    private void handleCommentCreate(WebSocketSession socket, SocketBinding binding, JsonNode payload) {
        String requestId = normalize(payload.path("requestId").asText(null));
        if (binding.userId == null || binding.userId <= 0) {
            sendError(socket, "unauthorized", requestId);
            return;
        }

        AnalyticsScreen screen = screenRepository.findById(binding.screenId).orElse(null);
        if (screen == null || screen.isArchived()) {
            sendError(socket, "screen_not_found", requestId);
            return;
        }
        AnalyticsUser user = userRepository.findById(binding.userId).orElse(null);
        if (user == null) {
            sendError(socket, "unauthorized", requestId);
            return;
        }
        PlatformContext context = new PlatformContext(null, null, binding.roles);
        if (!screenAclService.hasPermission(screen, user, context, ScreenAclService.Permission.EDIT)) {
            sendError(socket, "forbidden", requestId);
            return;
        }

        String message = normalizeComment(payload.path("message").asText(null));
        if (message == null) {
            sendError(socket, "comment_message_required", requestId);
            return;
        }
        if (message.length() > 2000) {
            sendError(socket, "comment_message_too_long", requestId);
            return;
        }
        String componentId = normalize(payload.path("componentId").asText(null));
        JsonNode anchor = payload.path("anchor");
        JsonNode mentions = payload.path("mentions");

        ObjectNode after = objectMapper.createObjectNode();
        after.put("message", message);
        if (componentId != null) {
            after.put("componentId", componentId);
        }
        if (anchor != null && anchor.isObject()) {
            after.set("anchor", anchor.deepCopy());
        }
        if (mentions != null && mentions.isArray()) {
            after.set("mentions", mentions.deepCopy());
        }

        String auditRequestId = requestId == null ? ("ws:" + binding.presenceSessionId) : requestId;
        AnalyticsScreenAuditLog log = screenAuditService.logAndReturn(
                screen.getId(),
                user.getId(),
                ACTION_COMMENT_ADD,
                null,
                after,
                auditRequestId);
        if (log == null || log.getId() == null) {
            sendError(socket, "comment_create_failed", requestId);
            return;
        }

        ObjectNode response = objectMapper.createObjectNode();
        response.put("id", log.getId());
        response.putPOJO("screenId", screen.getId());
        if (componentId == null) {
            response.putNull("componentId");
        } else {
            response.put("componentId", componentId);
        }
        response.put("message", message);
        if (anchor != null && anchor.isObject()) {
            response.set("anchor", anchor.deepCopy());
        } else {
            response.putNull("anchor");
        }
        if (mentions != null && mentions.isArray()) {
            response.set("mentions", mentions.deepCopy());
        } else {
            response.set("mentions", objectMapper.createArrayNode());
        }
        response.putPOJO("createdBy", log.getActorId());
        response.putPOJO("createdAt", log.getCreatedAt());
        response.put("status", "open");
        response.putNull("resolvedBy");
        response.putNull("resolvedAt");
        response.putNull("resolutionNote");
        response.put("requestId", requestId == null ? auditRequestId : requestId);

        sendEvent(socket, "comment-created", response);
        broadcastCommentChange(binding.screenId, response);
    }

    private void handleCommentStatusChange(
            WebSocketSession socket,
            SocketBinding binding,
            JsonNode payload,
            boolean resolveAction) {
        String requestId = normalize(payload.path("requestId").asText(null));
        if (binding.userId == null || binding.userId <= 0) {
            sendError(socket, "unauthorized", requestId);
            return;
        }

        AnalyticsScreen screen = screenRepository.findById(binding.screenId).orElse(null);
        if (screen == null || screen.isArchived()) {
            sendError(socket, "screen_not_found", requestId);
            return;
        }
        AnalyticsUser user = userRepository.findById(binding.userId).orElse(null);
        if (user == null) {
            sendError(socket, "unauthorized", requestId);
            return;
        }
        PlatformContext context = new PlatformContext(null, null, binding.roles);
        if (!screenAclService.hasPermission(screen, user, context, ScreenAclService.Permission.EDIT)) {
            sendError(socket, "forbidden", requestId);
            return;
        }

        long commentId = resolveCommentId(payload);
        if (commentId <= 0L) {
            sendError(socket, "comment_id_required", requestId);
            return;
        }
        String note = normalizeComment(payload.path("note").asText(null));

        Map<Long, CommentState> commentMap = rebuildCommentStateMap(screen.getId(), 1000);
        CommentState target = commentMap.get(commentId);
        if (target == null) {
            sendError(socket, "comment_not_found", requestId);
            return;
        }

        boolean alreadyResolved = "resolved".equals(target.status);
        if ((resolveAction && !alreadyResolved) || (!resolveAction && alreadyResolved)) {
            ObjectNode changePayload = objectMapper.createObjectNode();
            changePayload.put("commentId", commentId);
            if (note != null) {
                changePayload.put("note", note);
            }
            String auditRequestId = requestId == null ? ("ws:" + binding.presenceSessionId) : requestId;
            AnalyticsScreenAuditLog actionLog = screenAuditService.logAndReturn(
                    screen.getId(),
                    user.getId(),
                    resolveAction ? ACTION_COMMENT_RESOLVE : ACTION_COMMENT_REOPEN,
                    null,
                    changePayload,
                    auditRequestId);
            if (resolveAction) {
                target.status = "resolved";
                target.resolvedBy = user.getId();
                target.resolvedAt = actionLog == null ? Instant.now() : actionLog.getCreatedAt();
                target.resolutionNote = note;
            } else {
                target.status = "open";
                target.resolvedBy = null;
                target.resolvedAt = null;
                target.resolutionNote = null;
            }
            target.requestId = requestId == null ? auditRequestId : requestId;
        } else {
            target.requestId = requestId;
        }

        ObjectNode response = toCommentResponse(target);
        response.put("requestId", requestId == null ? response.path("requestId").asText("") : requestId);
        sendEvent(socket, "comment-updated", response);
        broadcastCommentChange(binding.screenId, response);
    }

    private void updatePresenceFromPayload(SocketBinding binding, JsonNode payload) {
        PresenceState state = new PresenceState();
        state.sessionId = binding.presenceSessionId;
        state.userId = binding.userId;
        state.displayName = binding.displayName;
        state.clientType = normalize(payload.path("clientType").asText(null));
        if (state.clientType == null) {
            state.clientType = binding.clientType;
        }
        state.componentId = normalize(payload.path("componentId").asText(null));
        state.cursorId = normalize(payload.path("cursorId").asText(null));
        state.typing = payload.path("typing").asBoolean(false);
        List<String> selectedIds = parseSelectedIds(payload.path("selectedIds"));
        state.selectedCount = selectedIds.size();
        if (!selectedIds.isEmpty()) {
            state.selectionPreview = String.join(",", selectedIds.subList(0, Math.min(selectedIds.size(), 3)));
        }
        state.lastSeenAt = Instant.now();
        upsertPresence(binding.screenId, state);
    }

    private void upsertPresence(long screenId, PresenceState state) {
        ConcurrentHashMap<String, PresenceState> map =
                screenPresence.computeIfAbsent(screenId, key -> new ConcurrentHashMap<>());
        prunePresence(map, Instant.now());
        if (map.size() >= 160) {
            pruneBySize(map, 120);
        }
        map.put(state.sessionId, state);
    }

    private void removePresence(long screenId, String presenceSessionId) {
        ConcurrentHashMap<String, PresenceState> map = screenPresence.get(screenId);
        if (map == null) {
            return;
        }
        map.remove(presenceSessionId);
        if (map.isEmpty()) {
            screenPresence.remove(screenId, map);
        }
    }

    private void broadcastPresence(long screenId) {
        CopyOnWriteArraySet<String> sockets = screenSockets.get(screenId);
        if (sockets == null || sockets.isEmpty()) {
            return;
        }
        for (String socketId : sockets) {
            WebSocketSession socket = socketSessions.get(socketId);
            SocketBinding binding = socketBindings.get(socketId);
            if (socket == null || binding == null || !socket.isOpen()) {
                continue;
            }
            ObjectNode payload = buildPresencePayload(screenId, binding.presenceSessionId);
            sendEvent(socket, "presence-change", payload);
        }
    }

    private ObjectNode buildPresencePayload(long screenId, String meSessionId) {
        Instant now = Instant.now();
        ConcurrentHashMap<String, PresenceState> map =
                screenPresence.computeIfAbsent(screenId, key -> new ConcurrentHashMap<>());
        prunePresence(map, now);

        List<PresenceState> rows = new ArrayList<>(map.values());
        rows.sort(
                Comparator.comparing((PresenceState item) -> item.lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(item -> item.sessionId == null ? "" : item.sessionId));

        ArrayNode rowNodes = objectMapper.createArrayNode();
        for (PresenceState row : rows) {
            if (row == null) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("sessionId", row.sessionId == null ? "" : row.sessionId);
            if (row.userId == null) {
                node.putNull("userId");
            } else {
                node.putPOJO("userId", row.userId);
            }
            node.put("displayName", row.displayName == null ? "" : row.displayName);
            if (row.componentId == null) {
                node.putNull("componentId");
            } else {
                node.put("componentId", row.componentId);
            }
            if (row.cursorId == null) {
                node.putNull("cursorId");
            } else {
                node.put("cursorId", row.cursorId);
            }
            node.put("typing", row.typing != null && row.typing);
            if (row.clientType == null) {
                node.putNull("clientType");
            } else {
                node.put("clientType", row.clientType);
            }
            if (row.selectedCount == null) {
                node.putNull("selectedCount");
            } else {
                node.put("selectedCount", row.selectedCount);
            }
            if (row.selectionPreview == null) {
                node.putNull("selectionPreview");
            } else {
                node.put("selectionPreview", row.selectionPreview);
            }
            node.putPOJO("lastSeenAt", row.lastSeenAt);
            long idleSeconds = 0;
            if (row.lastSeenAt != null) {
                idleSeconds = Math.max(0, now.getEpochSecond() - row.lastSeenAt.getEpochSecond());
            }
            node.put("idleSeconds", idleSeconds);
            node.put("mine", row.sessionId != null && row.sessionId.equals(meSessionId));
            rowNodes.add(node);
        }

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", now);
        result.put("ttlSeconds", PRESENCE_TTL_SECONDS);
        if (meSessionId == null) {
            result.putNull("meSessionId");
        } else {
            result.put("meSessionId", meSessionId);
        }
        result.put("activeCount", rowNodes.size());
        result.set("rows", rowNodes);
        return result;
    }

    private void prunePresence(ConcurrentHashMap<String, PresenceState> map, Instant now) {
        if (map == null || map.isEmpty()) {
            return;
        }
        Set<String> expired = map.values().stream()
                .filter(item -> item == null
                        || item.lastSeenAt == null
                        || now.getEpochSecond() - item.lastSeenAt.getEpochSecond() > PRESENCE_TTL_SECONDS)
                .map(item -> item == null ? null : item.sessionId)
                .filter(item -> item != null && !item.isBlank())
                .collect(Collectors.toSet());
        for (String key : expired) {
            map.remove(key);
        }
    }

    private void pruneBySize(ConcurrentHashMap<String, PresenceState> map, int targetSize) {
        List<PresenceState> sorted = map.values().stream()
                .filter(item -> item != null && item.sessionId != null)
                .sorted(Comparator.comparing(item -> item.lastSeenAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .toList();
        int toRemove = Math.max(0, sorted.size() - targetSize);
        for (int i = 0; i < toRemove; i++) {
            map.remove(sorted.get(i).sessionId);
        }
    }

    private List<String> parseSelectedIds(JsonNode selectedIdsNode) {
        if (selectedIdsNode == null || !selectedIdsNode.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : selectedIdsNode) {
            String value = normalize(item == null ? null : item.asText(null));
            if (value == null) {
                continue;
            }
            out.add(value);
            if (out.size() >= 20) {
                break;
            }
        }
        return out;
    }

    private Map<Long, CommentState> rebuildCommentStateMap(Long screenId, int scanLimit) {
        List<AnalyticsScreenAuditLog> timeline = screenAuditService.listByScreenId(screenId, scanLimit);
        timeline.sort(
                Comparator.comparing(
                                AnalyticsScreenAuditLog::getCreatedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(log -> log.getId() == null ? 0L : log.getId()));

        Map<Long, CommentState> comments = new ConcurrentHashMap<>();
        for (AnalyticsScreenAuditLog log : timeline) {
            if (log == null || log.getId() == null) {
                continue;
            }
            String action = normalize(log.getAction());
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
                if (targetId <= 0L) {
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
                    target.resolutionNote = normalize(after.path("note").asText(null));
                } else {
                    target.status = "open";
                    target.resolvedAt = null;
                    target.resolvedBy = null;
                    target.resolutionNote = null;
                }
                target.requestId = normalize(log.getRequestId());
            }
        }
        return comments;
    }

    private CommentState parseCommentCreate(AnalyticsScreenAuditLog log, JsonNode payload) {
        String message = normalizeComment(payload.path("message").asText(null));
        if (message == null) {
            return null;
        }
        CommentState state = new CommentState();
        state.id = log.getId();
        state.screenId = log.getScreenId();
        state.componentId = normalize(payload.path("componentId").asText(null));
        state.message = message;
        state.anchor = payload.path("anchor").isObject() ? payload.path("anchor").deepCopy() : null;
        state.mentions = payload.path("mentions").isArray() ? payload.path("mentions").deepCopy() : null;
        state.createdBy = log.getActorId();
        state.createdAt = log.getCreatedAt();
        state.status = "open";
        state.requestId = normalize(log.getRequestId());
        return state;
    }

    private ObjectNode toCommentResponse(CommentState state) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("id", state.id);
        node.putPOJO("screenId", state.screenId);
        if (state.componentId == null) {
            node.putNull("componentId");
        } else {
            node.put("componentId", state.componentId);
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
        if (state.resolutionNote == null) {
            node.putNull("resolutionNote");
        } else {
            node.put("resolutionNote", state.resolutionNote);
        }
        if (state.requestId == null) {
            node.putNull("requestId");
        } else {
            node.put("requestId", state.requestId);
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
        String value = normalize(commentIdNode.asText(null));
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignore) {
            return 0L;
        }
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() > 128 ? value.substring(0, 128) : value;
    }

    private String normalizeComment(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        return value.isEmpty() ? null : value;
    }

    private void sendEvent(WebSocketSession socket, String event, Object payload) {
        if (socket == null || !socket.isOpen()) {
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("event", event);
            if (payload == null) {
                root.putNull("payload");
            } else {
                root.set("payload", objectMapper.valueToTree(payload));
            }
            socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(root)));
        } catch (IOException ignored) {
            // keep realtime flow non-blocking
        }
    }

    private void sendError(WebSocketSession socket, String code) {
        sendError(socket, code, null);
    }

    private void sendError(WebSocketSession socket, String code, String requestId) {
        String message = code == null ? "error" : code.toLowerCase(Locale.ROOT);
        if (requestId == null) {
            sendEvent(socket, "error", Map.of("code", message));
            return;
        }
        sendEvent(socket, "error", Map.of("code", message, "requestId", requestId));
    }

    private static final class SocketBinding {
        private final long screenId;
        private final String presenceSessionId;
        private final Long userId;
        private final String displayName;
        private final String clientType;
        private final String roles;

        private SocketBinding(
                long screenId,
                String presenceSessionId,
                Long userId,
                String displayName,
                String clientType,
                String roles) {
            this.screenId = screenId;
            this.presenceSessionId = presenceSessionId;
            this.userId = userId;
            this.displayName = displayName;
            this.clientType = clientType;
            this.roles = roles;
        }
    }

    private static final class PresenceState {
        private String sessionId;
        private Long userId;
        private String displayName;
        private String componentId;
        private String cursorId;
        private Boolean typing;
        private String clientType;
        private Integer selectedCount;
        private String selectionPreview;
        private Instant lastSeenAt;
    }

    private static final class CommentState {
        private long id;
        private Long screenId;
        private String componentId;
        private String message;
        private JsonNode anchor;
        private JsonNode mentions;
        private Long createdBy;
        private Instant createdAt;
        private String status;
        private Long resolvedBy;
        private Instant resolvedAt;
        private String resolutionNote;
        private String requestId;
    }
}
