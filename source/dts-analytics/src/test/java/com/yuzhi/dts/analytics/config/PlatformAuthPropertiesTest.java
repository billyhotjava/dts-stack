package com.yuzhi.dts.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformAuthPropertiesTest {

    @Test
    void defaultsBearerFallbackToDisabled() {
        PlatformAuthProperties properties = new PlatformAuthProperties(
                true,
                true,
                null,
                null,
                null,
                null,
                0);

        assertThat(properties.allowBearerFallback()).isFalse();
        assertThat(properties.superuserRoles()).isEqualTo(List.of("ROLE_OP_ADMIN"));
        assertThat(properties.forwardAuthUrl()).isEqualTo("http://dts-platform:8081/api/forward-auth");
    }
}
