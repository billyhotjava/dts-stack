package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthResourceIT {

    @Autowired
    private MockMvc mockMvc;

    @SpyBean
    private AnalyticsSessionService sessionService;

    @Test
    void healthShouldReturnStatusUp() throws Exception {
        clearInvocations(sessionService);

        MvcResult result = mockMvc.perform(get("/api/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().exists("X-Request-Id"))
                .andReturn();

        String requestId = result.getResponse().getHeader("X-Request-Id");
        assertThat(requestId).isNotBlank();
        verify(sessionService, never()).resolveSessionId(any(HttpServletRequest.class));
        verify(sessionService, never()).resolveUserFromSession(any(HttpServletRequest.class));
        verify(sessionService, never()).resolveUser(any(HttpServletRequest.class));
    }

    @Test
    void shouldPropagateIncomingRequestId() throws Exception {
        String providedId = "abc-123";
        mockMvc.perform(get("/api/health")
                        .header("X-Request-Id", providedId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", providedId));
    }
}
