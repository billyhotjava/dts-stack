package com.yuzhi.dts.platform.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.security.session.PortalSessionActivityService.ValidationResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortalSessionRegistryTest {

    private final PortalSessionActivityService activityService = new PortalSessionActivityService(15);
    private final PortalSessionRegistry registry = new PortalSessionRegistry(15, activityService);

    @Test
    void findByAccessTokenDoesNotInvalidateCurrentSession() {
        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);

        ValidationResult initial = activityService.touch(session.accessToken(), Instant.now().plusSeconds(1));
        assertThat(initial).isEqualTo(ValidationResult.ACTIVE);

        registry.findByAccessToken(session.accessToken()).orElseThrow();

        ValidationResult afterIntrospect = activityService.touch(session.accessToken(), Instant.now().plusSeconds(2));
        assertThat(afterIntrospect).isEqualTo(ValidationResult.ACTIVE);
    }

    @Test
    void refreshSessionMarksPreviousTokenExpired() {
        var session = registry.createSession("portaluser", List.of("ROLE_USER"), List.of("portal.view"), null);
        var priorToken = session.accessToken();
        var priorRefresh = session.refreshToken();

        var refreshed = registry.refreshSession(priorRefresh, existing -> existing.adminTokens());

        assertThat(refreshed.accessToken()).isNotEqualTo(priorToken);
        var oldTokenState = activityService.touch(priorToken, Instant.now().plusSeconds(1));
        assertThat(oldTokenState).isEqualTo(ValidationResult.EXPIRED);

        var newTokenState = activityService.touch(refreshed.accessToken(), Instant.now().plusSeconds(2));
        assertThat(newTokenState).isEqualTo(ValidationResult.ACTIVE);
    }
}
