package com.yuzhi.dts.copilot.analytics.web.socket;

import com.yuzhi.dts.copilot.analytics.service.ScreenCollaborationRealtimeService;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class ScreenCollaborationWebSocketHandler extends TextWebSocketHandler {

    private final ScreenCollaborationRealtimeService realtimeService;

    public ScreenCollaborationWebSocketHandler(ScreenCollaborationRealtimeService realtimeService) {
        this.realtimeService = realtimeService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        long screenId = parseScreenId(uri);
        if (screenId <= 0) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }
        Map<String, String> query = parseQuery(uri);
        String presenceSessionId = normalizeSessionId(query.get("sessionId"));
        if (presenceSessionId == null) {
            presenceSessionId = "ws-" + UUID.randomUUID();
        }
        String clientType = trimToNull(query.get("clientType"));
        String roles = trimToNull((String) session.getAttributes().get("roles"));
        Long userId = readLong(session.getAttributes().get("userId"));
        String displayName = trimToNull((String) session.getAttributes().get("displayName"));
        if (displayName == null) {
            displayName = userId == null ? "匿名协作者" : "用户#" + userId;
        }
        realtimeService.register(session, screenId, presenceSessionId, userId, displayName, clientType, roles);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        realtimeService.handleMessage(session, message == null ? null : message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        realtimeService.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        realtimeService.unregister(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private long parseScreenId(URI uri) {
        if (uri == null) {
            return 0L;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            return 0L;
        }
        String[] segments = path.split("/");
        for (int i = 0; i < segments.length; i++) {
            if ("screens".equals(segments[i]) && i + 1 < segments.length) {
                try {
                    long value = Long.parseLong(segments[i + 1]);
                    return Math.max(0L, value);
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> out = new HashMap<>();
        if (uri == null || uri.getQuery() == null || uri.getQuery().isBlank()) {
            return out;
        }
        String[] parts = uri.getQuery().split("&");
        for (String item : parts) {
            if (item == null || item.isBlank()) {
                continue;
            }
            int idx = item.indexOf('=');
            if (idx <= 0 || idx >= item.length() - 1) {
                continue;
            }
            String key = item.substring(0, idx).trim();
            String value = item.substring(idx + 1).trim();
            if (!key.isEmpty() && !value.isEmpty()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private String normalizeSessionId(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        if (text.length() > 128) {
            return text.substring(0, 128);
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    private Long readLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long longValue) {
            return longValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
