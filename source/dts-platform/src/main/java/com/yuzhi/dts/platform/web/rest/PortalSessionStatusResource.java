package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.security.PortalSessionCloseReason;
import com.yuzhi.dts.platform.domain.security.PortalSessionEntity;
import com.yuzhi.dts.platform.repository.security.PortalSessionRepository;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class PortalSessionStatusResource {

    private final PortalSessionRepository sessionRepository;
    private final Clock clock;
    private final PortalSessionCookieService cookieService;

    @Autowired
    public PortalSessionStatusResource(PortalSessionRepository sessionRepository, PortalSessionCookieService cookieService) {
        this(sessionRepository, Clock.systemUTC(), cookieService);
    }

    PortalSessionStatusResource(PortalSessionRepository sessionRepository, Clock clock) {
        this(sessionRepository, clock, null);
    }

    PortalSessionStatusResource(PortalSessionRepository sessionRepository, Clock clock, PortalSessionCookieService cookieService) {
        this.sessionRepository = sessionRepository;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.cookieService = cookieService;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> status(HttpServletRequest request) {
        Map<String, Object> data = new LinkedHashMap<>();
        Instant now = Instant.now(clock);
        data.put("serverNow", now.toString());

        String token = extractToken(request);
        if (token == null) {
            data.put("authenticated", false);
            return ApiResponses.ok(data);
        }

        Optional<PortalSessionEntity> session = sessionRepository.findByAccessToken(token);
        if (session.isEmpty()) {
            data.put("authenticated", false);
            appendReason(data, PortalSessionCloseReason.EXPIRED);
            data.put("remainingSeconds", 0L);
            return ApiResponses.ok(data);
        }
        PortalSessionEntity entity = session.orElseThrow();
        if (entity.getRevokedAt() != null) {
            data.put("authenticated", false);
            appendReason(data, entity.getRevokedReason());
            return ApiResponses.ok(data);
        }

        Instant expiresAt = entity.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(now)) {
            data.put("authenticated", false);
            appendReason(data, PortalSessionCloseReason.EXPIRED);
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
        data.put("roles", entity.getRoles() == null ? List.of() : List.copyOf(entity.getRoles()));
        data.put("permissions", entity.getPermissions() == null ? List.of() : List.copyOf(entity.getPermissions()));
        if (StringUtils.hasText(entity.getDeptCode())) {
            data.put("deptCode", entity.getDeptCode());
        }
        if (StringUtils.hasText(entity.getPersonnelLevel())) {
            data.put("personnelLevel", entity.getPersonnelLevel());
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

    private void appendReason(Map<String, Object> data, PortalSessionCloseReason reason) {
        if (data == null || reason == null) {
            return;
        }
        data.put("reason", reason.name());
    }

    private String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String cookieToken = cookieService == null ? null : cookieService.resolvePortalSessionToken(request);
        if (StringUtils.hasText(cookieToken)) {
            return cookieToken.trim();
        }
        return null;
    }
}
