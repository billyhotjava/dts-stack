package com.yuzhi.dts.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.config.metrics.DtsMetricsCapabilityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class MetricsInternalAccessTest {

    @Test
    void acceptsConfiguredMetricsServicePrincipal() {
        DtsMetricsCapabilityProperties properties = new DtsMetricsCapabilityProperties();
        properties.setServiceName("metrics-v2");
        MetricsInternalAccess access = new MetricsInternalAccess(properties);

        assertThat(access.isMetricsService(token("service:metrics-v2"))).isTrue();
    }

    @Test
    void rejectsOtherServicePrincipals() {
        MetricsInternalAccess access = new MetricsInternalAccess(new DtsMetricsCapabilityProperties());

        assertThat(access.isMetricsService(token("service:dts-analytics"))).isFalse();
    }

    private static UsernamePasswordAuthenticationToken token(String principal) {
        return new UsernamePasswordAuthenticationToken(principal, "n/a", AuthorityUtils.NO_AUTHORITIES);
    }
}
