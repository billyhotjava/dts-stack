package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class SemanticPublishAuthorizationContractTest {

    @Test
    void semanticPublishRequiresTheDedicatedAnalyticsServiceAuthority() throws Exception {
        PreAuthorize authorization = SemanticPublishResource.class
            .getMethod("publish", JsonNode.class, HttpServletRequest.class)
            .getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('ROLE_ANALYTICS_SERVICE')");
    }
}
