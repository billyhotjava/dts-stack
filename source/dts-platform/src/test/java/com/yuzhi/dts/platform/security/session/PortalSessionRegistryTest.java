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
}
