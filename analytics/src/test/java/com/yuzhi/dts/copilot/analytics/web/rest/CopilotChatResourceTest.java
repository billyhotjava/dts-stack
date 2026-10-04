package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CopilotAgentChatClient;
import com.yuzhi.dts.copilot.analytics.service.CopilotChatDataSourceResolver;
import com.yuzhi.dts.copilot.analytics.service.elt.EltWatermarkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.RestClientException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CopilotChatResourceTest {

    @Test
    void routeTelemetryProxiesThroughAuthenticatedAnalyticsUser() {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        CopilotAgentChatClient chatClient = mock(CopilotAgentChatClient.class);
        CopilotChatDataSourceResolver dataSourceResolver = mock(CopilotChatDataSourceResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EltWatermarkService> watermarkServiceProvider = mock(ObjectProvider.class);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("alice");
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(chatClient.getRouteTelemetry(14, 5)).thenReturn(Map.of(
                "days", 14,
                "tierCounts", Map.of("TIER_5_DIRECT_DETAIL", 3)));

        CopilotChatResource resource = new CopilotChatResource(
                sessionService, chatClient, dataSourceResolver, watermarkServiceProvider);

        var response = resource.getRouteTelemetry(14, 5, new MockHttpServletRequest());

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body).containsEntry("days", 14);
    }

    @Test
    void sendMessageStreamSkipsErrorEventWhenStreamingIsInterrupted() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        CopilotAgentChatClient chatClient = mock(CopilotAgentChatClient.class);
        CopilotChatDataSourceResolver dataSourceResolver = mock(CopilotChatDataSourceResolver.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EltWatermarkService> watermarkServiceProvider = mock(ObjectProvider.class);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("alice");
        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(dataSourceResolver.resolveSelectedDatasourceId(null)).thenReturn(null);
        when(watermarkServiceProvider.getIfAvailable()).thenReturn(null);
        RuntimeException interrupted = new RestClientException(
                "Streaming chat failed: java.lang.InterruptedException",
                new InterruptedException("stream interrupted"));
        org.mockito.Mockito.doThrow(interrupted).when(chatClient)
                .sendMessageStream(anyString(), isNull(), anyString(), isNull(), any(Map.class), isNull(), isNull(), any());

        CopilotChatResource resource = new CopilotChatResource(
                sessionService, chatClient, dataSourceResolver, watermarkServiceProvider);
        CopilotChatResource.ChatSendRequest request =
                new CopilotChatResource.ChatSendRequest(null, "hello", null, null, null);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        resource.sendMessageStream(request, new MockHttpServletRequest()).writeTo(output);

        assertThat(output.toString(StandardCharsets.UTF_8)).doesNotContain("event: error");
    }
}
