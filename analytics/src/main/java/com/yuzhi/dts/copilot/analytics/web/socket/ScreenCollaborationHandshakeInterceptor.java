package com.yuzhi.dts.copilot.analytics.web.socket;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class ScreenCollaborationHandshakeInterceptor implements HandshakeInterceptor {

    private final AnalyticsSessionService sessionService;

    public ScreenCollaborationHandshakeInterceptor(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest raw = servletRequest.getServletRequest();
            Optional<AnalyticsUser> user = sessionService.resolveUser(raw);
            if (user.isPresent()) {
                attributes.put("userId", user.get().getId());
                attributes.put("displayName", resolveDisplayName(user.get()));
            }
            String roles = trimToNull(raw.getHeader("X-DTS-Roles"));
            if (roles != null) {
                attributes.put("roles", roles);
            }
        }
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // no-op
    }

    private String resolveDisplayName(AnalyticsUser user) {
        if (user == null) {
            return "匿名协作者";
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        if (!full.isBlank()) {
            return full;
        }
        if (user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername().trim();
        }
        if (user.getId() != null) {
            return "用户#" + user.getId();
        }
        return "匿名协作者";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
