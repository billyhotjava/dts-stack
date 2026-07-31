package com.yuzhi.dts.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.admin.config.AdminInboundServiceAuthProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdminInboundServiceAuthenticatorTest {

    @Test
    void acceptsOnlyTheConfiguredPairwiseServiceAndToken() {
        var properties = new AdminInboundServiceAuthProperties();
        properties.setTrustedServices(Map.of("dts-platform", "pairwise-secret"));
        var authenticator = new AdminInboundServiceAuthenticator(properties);
        var accepted = request("dts-platform", "pairwise-secret");
        var wrongService = request("dts-airflow", "pairwise-secret");
        var wrongToken = request("dts-platform", "wrong");

        assertThat(authenticator.authenticate(accepted, "dts-platform").accepted())
            .isTrue();
        assertThat(
            authenticator.authenticate(wrongService, "dts-platform").accepted()
        )
            .isFalse();
        assertThat(
            authenticator.authenticate(wrongToken, "dts-platform").accepted()
        )
            .isFalse();
    }

    @Test
    void rejectsWhenThePairwiseTokenIsNotConfigured() {
        var authenticator = new AdminInboundServiceAuthenticator(
            new AdminInboundServiceAuthProperties()
        );

        assertThat(
            authenticator
                .authenticate(request("dts-platform", "anything"), "dts-platform")
                .accepted()
        )
            .isFalse();
    }

    @Test
    void authenticatesAnyDeclaredServiceAgainstItsOwnPairwiseToken() {
        var properties = new AdminInboundServiceAuthProperties();
        properties.setTrustedServices(
            Map.of(
                "dts-platform",
                "platform-secret",
                "dts-analytics",
                "analytics-secret"
            )
        );
        var authenticator = new AdminInboundServiceAuthenticator(properties);

        var analytics = authenticator.authenticate(
            request("DTS-ANALYTICS", "analytics-secret")
        );
        var forgedPlatform = authenticator.authenticate(
            request("dts-platform", "analytics-secret")
        );

        assertThat(analytics.accepted()).isTrue();
        assertThat(analytics.serviceName()).isEqualTo("dts-analytics");
        assertThat(forgedPlatform.accepted()).isFalse();
    }

    @Test
    void startupValidationRejectsDuplicateOrWeakPairwiseCredentials() {
        var duplicate = new AdminInboundServiceAuthProperties();
        duplicate.setTrustedServices(
            Map.of(
                "dts-platform",
                "a".repeat(32),
                "dts-analytics",
                "a".repeat(32)
            )
        );
        var weak = new AdminInboundServiceAuthProperties();
        weak.setTrustedServices(Map.of("dts-platform", "short-secret"));

        assertThatThrownBy(duplicate::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate");
        assertThatThrownBy(weak::afterPropertiesSet)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32");
    }

    @Test
    void startupValidationAllowsEmptyFailClosedConfiguration() {
        var properties = new AdminInboundServiceAuthProperties();

        properties.afterPropertiesSet();

        assertThat(properties.getTrustedServices()).isEmpty();
    }

    private static MockHttpServletRequest request(
        String service,
        String token
    ) {
        var request = new MockHttpServletRequest();
        request.addHeader(AdminInboundServiceAuthenticator.SERVICE_HEADER, service);
        request.addHeader(AdminInboundServiceAuthenticator.TOKEN_HEADER, token);
        return request;
    }
}
