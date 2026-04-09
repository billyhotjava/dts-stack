package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class PortalSessionStatusResource {

    private final PortalSessionRepository sessionRepository;
    private final Clock clock;

    @Autowired
    public PortalSessionStatusResource(PortalSessionRepository sessionRepository) {
        this(sessionRepository, Clock.systemUTC());
    }

    PortalSessionStatusResource(PortalSessionRepository sessionRepository, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        Instant now = Instant.now(clock);
        data.put("serverNow", now.toString());

        String token = extractBearerToken(request);
        if (token == null) {
            data.put("authenticated", false);
            return ApiResponses.ok(data);
        }

        Optional<PortalSessionEntity> session = sessionRepository.findByAccessToken(token);
        if (session.isEmpty()) {
            data.put("authenticated", false);
            return ApiResponses.ok(data);
        }
        PortalSessionEntity entity = session.get();
        if (entity.getRevokedAt() != null) {
            data.put("authenticated", false);
            return ApiResponses.ok(data);
        }

        Instant expiresAt = entity.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(now)) {
            data.put("authenticated", false);
            data.put("expiresAt", expiresAt.toString());
            data.put("remainingSeconds", 0L);
            return ApiResponses.ok(data);
        }

        data.put("authenticated", true);
        data.put("username", entity.getUsername());
        String displayName = entity.getDisplayName();
        if (displayName != null && !displayName.isBlank()) {
            data.put("displayName", displayName);
        }

        if (expiresAt != null) {
            data.put("expiresAt", expiresAt.toString());
            long remainingSeconds = Math.max(0L, Duration.between(now, expiresAt).toSeconds());
            data.put("remainingSeconds", remainingSeconds);
        } else {
            data.put("remainingSeconds", null);
        }
        return ApiResponses.ok(data);
    }

    private String extractBearerToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            return null;
        }
        int idx = header.indexOf(' ');
        if (idx < 0) {
            return header.trim().isEmpty() ? null : header.trim();
        }
        String scheme = header.substring(0, idx).trim();
        if (!"Bearer".equalsIgnoreCase(scheme)) {
            return null;
        }
        String token = header.substring(idx + 1).trim();
        if (token.isEmpty()) {
            return null;
        }
        return token;
    }
}
