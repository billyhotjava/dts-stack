package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;

class PortalSessionStatusResourceTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void statusShouldReportAuthenticatedSessionWithRemainingSeconds() {
        Instant now = Instant.parse("2026-04-09T10:00:00Z");
        DefaultOAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
            Map.of(
                OAuth2TokenIntrospectionClaimNames.USERNAME,
                "alice",
                "preferred_username",
                "alice",
                "displayName",
                "Alice",
                OAuth2TokenIntrospectionClaimNames.EXP,
                Instant.parse("2026-04-09T10:30:45Z")
            ),
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "n/a", principal.getAuthorities())
        );

        PortalSessionStatusResource resource = new PortalSessionStatusResource(Clock.fixed(now, ZoneOffset.UTC));

        ApiResponse<Map<String, Object>> response = resource.status();

        assertThat(response.getData())
            .containsEntry("authenticated", true)
            .containsEntry("username", "alice")
            .containsEntry("displayName", "Alice")
            .containsEntry("expiresAt", "2026-04-09T10:30:45Z")
            .containsEntry("serverNow", "2026-04-09T10:00:00Z")
            .containsEntry("remainingSeconds", 1845L);
    }

    @Test
    void statusShouldReportUnauthenticatedWhenContextIsEmpty() {
        PortalSessionStatusResource resource = new PortalSessionStatusResource(
            Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
        );

        ApiResponse<Map<String, Object>> response = resource.status();

        assertThat(response.getData()).containsEntry("authenticated", false).containsEntry("serverNow", "2026-04-09T10:00:00Z");
    }
}
