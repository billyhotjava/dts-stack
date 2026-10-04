package com.yuzhi.dts.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityProblemSupportTest {

    @Test
    void writesSerializableUnauthorizedProblemWithTimestamp() throws Exception {
        SecurityProblemSupport support = new SecurityProblemSupport();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/internal/screens");
        MockHttpServletResponse response = new MockHttpServletResponse();

        support.commence(request, response, new BadCredentialsException("Access denied"));

        JsonNode payload = new ObjectMapper().readTree(response.getContentAsByteArray());
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("X-Error-Code")).isEqualTo("SEC_UNAUTHORIZED");
        assertThat(payload.path("timestamp").isTextual()).isTrue();
        assertThat(payload.path("timestamp").asText()).isNotBlank();
        assertThat(payload.path("code").asText()).isEqualTo("SEC_UNAUTHORIZED");
        assertThat(payload.path("path").asText()).isEqualTo("/api/internal/screens");
    }
}
