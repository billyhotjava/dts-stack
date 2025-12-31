package com.yuzhi.dts.analytics.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.DtsAnalyticsApp;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = DtsAnalyticsApp.class)
@AutoConfigureMockMvc
class DiagnosticsResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void echoShouldReturnPayload() throws Exception {
        mockMvc.perform(get("/api/echo")
                        .with(httpBasic("admin", "admin"))
                        .queryParam("q", "hello")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.echo").value("hello"))
                .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void missingParamShouldReturnApiError() throws Exception {
        mockMvc.perform(get("/api/echo")
                        .with(httpBasic("admin", "admin"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/api/echo"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void missingParamEchoesProvidedRequestId() throws Exception {
        String requestId = "ctx-xyz";
        mockMvc.perform(
                        get("/api/echo")
                                .with(httpBasic("admin", "admin"))
                                .header("X-Request-Id", requestId)
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", requestId))
                .andExpect(jsonPath("$.requestId").value(requestId));
    }

    @Test
    void echoReturnsUnauthorizedWhenNoCredentials() throws Exception {
        mockMvc.perform(get("/api/echo").queryParam("q", "hello").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/echo"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void echoReturnsForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(
                        get("/api/echo")
                                .with(httpBasic("user", "user"))
                                .queryParam("q", "hello")
                                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/echo"))
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Nested
    class RequestContextEndpoint {
        @Test
        void returnsContextFromRequest() throws Exception {
            String requestId = "req-123";
            mockMvc.perform(get("/api/request-context")
                            .with(httpBasic("admin", "admin"))
                            .header("X-Request-Id", requestId)
                            .header("X-Forwarded-For", "192.168.1.10")
                            .header("X-Forwarded-Proto", "https")
                            .header("X-Forwarded-Host", "example.test")
                            .header("X-Timezone", "Asia/Shanghai")
                            .header("Accept-Language", "fr-FR,fr;q=0.8")
                            .header("User-Agent", "JUnit")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").value(requestId))
                    .andExpect(jsonPath("$.clientIp").value("192.168.1.10"))
                    .andExpect(jsonPath("$.userAgent").value("JUnit"))
                    .andExpect(jsonPath("$.scheme").value("https"))
                    .andExpect(jsonPath("$.host").value("example.test"))
                    .andExpect(jsonPath("$.path").value("/api/request-context"))
                    .andExpect(jsonPath("$.locale").value("fr-FR"))
                    .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
                    .andExpect(header().string("X-Request-Id", requestId));
        }

        @Test
        void fallsBackToRemoteAddressWhenNoForwardedHeader() throws Exception {
            mockMvc.perform(
                            get("/api/request-context")
                                    .with(httpBasic("admin", "admin"))
                                    .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requestId").isNotEmpty())
                    .andExpect(jsonPath("$.clientIp").isNotEmpty())
                    .andExpect(jsonPath("$.scheme").isNotEmpty())
                    .andExpect(jsonPath("$.host").isNotEmpty())
                    .andExpect(jsonPath("$.path").value("/api/request-context"))
                    .andExpect(jsonPath("$.locale").isNotEmpty())
                    .andExpect(jsonPath("$.timezone").isNotEmpty());
        }

        @Test
        void returnsUnauthorizedWithoutCredentials() throws Exception {
            mockMvc.perform(get("/api/request-context").accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.path").value("/api/request-context"))
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }

        @Test
        void returnsForbiddenForNonAdminUser() throws Exception {
            mockMvc.perform(get("/api/request-context")
                            .with(httpBasic("user", "user"))
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.error").value("Forbidden"))
                    .andExpect(jsonPath("$.path").value("/api/request-context"))
                    .andExpect(header().exists("X-Request-Id"))
                    .andExpect(jsonPath("$.requestId").isNotEmpty());
        }
    }
}
