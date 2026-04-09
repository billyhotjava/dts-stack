package com.yuzhi.dts.platform.web.rest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class PortalSessionStatusResource {

    private final Clock clock;

    public PortalSessionStatusResource() {
        this(Clock.systemUTC());
    }

    PortalSessionStatusResource(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        Instant now = Instant.now(clock);
        data.put("serverNow", now.toString());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            data.put("authenticated", false);
            return ApiResponses.ok(data);
        }

        data.put("authenticated", true);
        data.put("username", resolveUsername(auth));
        String displayName = resolveDisplayName(auth);
        if (displayName != null && !displayName.isBlank()) {
            data.put("displayName", displayName);
        }

        Instant expiresAt = resolveExpiresAt(auth);
        if (expiresAt != null) {
            data.put("expiresAt", expiresAt.toString());
            long remainingSeconds = Math.max(0L, Duration.between(now, expiresAt).toSeconds());
            data.put("remainingSeconds", remainingSeconds);
        } else {
            data.put("remainingSeconds", null);
        }
        return ApiResponses.ok(data);
    }

    private String resolveUsername(Authentication auth) {
        if (auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            String username = firstText(
                principal.getAttribute(OAuth2TokenIntrospectionClaimNames.USERNAME),
                principal.getAttribute("preferred_username"),
                principal.getAttribute("sub")
            );
            if (username != null) {
                return username;
            }
        }
        return auth.getName();
    }

    private String resolveDisplayName(Authentication auth) {
        if (auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
            return firstText(principal.getAttribute("displayName"), principal.getAttribute("full_name"), principal.getAttribute("name"));
        }
        return null;
    }

    private Instant resolveExpiresAt(Authentication auth) {
        if (!(auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal)) {
            return null;
        }
        return toInstant(principal.getAttribute(OAuth2TokenIntrospectionClaimNames.EXP));
    }

    private String firstText(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            if (candidate instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (String.valueOf(Math.abs(epoch)).length() > 10) {
                return Instant.ofEpochMilli(epoch);
            }
            return Instant.ofEpochSecond(epoch);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException ignored) {
                try {
                    long epoch = Long.parseLong(text);
                    return toInstant(epoch);
                } catch (NumberFormatException ignoredAgain) {
                    return null;
                }
            }
        }
        return null;
    }
}
