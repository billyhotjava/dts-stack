package com.yuzhi.dts.copilot.analytics.web.rest;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.security.AnalyticsApiKeyAuthFilter;
import com.yuzhi.dts.copilot.analytics.security.AnalyticsSecurityConfiguration;
import com.yuzhi.dts.copilot.analytics.security.ApiKeyAuthService;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.CopilotAgentChatClient;
import com.yuzhi.dts.copilot.analytics.service.CopilotChatDataSourceResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CopilotChatResource.class)
@Import({AnalyticsSecurityConfiguration.class, AnalyticsApiKeyAuthFilter.class})
class CopilotChatStreamingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsSessionService sessionService;

    @MockBean
    private CopilotAgentChatClient copilotAgentChatClient;

    @MockBean
    private CopilotChatDataSourceResolver chatDataSourceResolver;

    @MockBean
    private ApiKeyAuthService apiKeyAuthService;

    @Test
    void sendStreamRemainsAuthorizedAcrossAsyncDispatch() throws Exception {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("alice");

        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(sessionService.resolveSessionId(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(chatDataSourceResolver.resolveSelectedDatasourceId(any())).thenReturn(null);
        doAnswer(invocation -> {
            var output = invocation.getArgument(7, java.io.OutputStream.class);
            output.write("event: done\ndata: {}\n\n".getBytes(StandardCharsets.UTF_8));
            output.flush();
            return null;
        }).when(copilotAgentChatClient)
                .sendMessageStream(anyString(), isNull(), anyString(), isNull(), anyMap(), isNull(), isNull(), any());

        MvcResult mvcResult = mockMvc.perform(post("/api/copilot/chat/send-stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("""
                                {"userMessage":"hi"}
                                """))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("event: done")));
    }

    @Test
    void sendMessageForwardsAssumptionOverridesAndClarificationAnswers() throws Exception {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("alice");

        when(sessionService.resolveUser(any())).thenReturn(Optional.of(user));
        when(sessionService.resolveSessionId(any())).thenReturn(Optional.of(UUID.randomUUID()));
        when(chatDataSourceResolver.resolveSelectedDatasourceId(any())).thenReturn(7L);
        when(copilotAgentChatClient.sendMessage(
                anyString(), anyString(), anyString(), anyLong(), anyMap(), anyMap(), anyMap()))
                .thenReturn(java.util.Map.of("response", "ok"));

        mockMvc.perform(post("/api/copilot/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "sess-1",
                                  "userMessage": "重算利润",
                                  "datasourceId": "7",
                                  "assumptionOverrides": {"period": "2026-05"},
                                  "clarificationAnswers": {"scope": "billing"}
                                }
                                """))
                .andExpect(status().isOk());

        verify(copilotAgentChatClient).sendMessage(
                eq("alice"),
                eq("sess-1"),
                eq("重算利润"),
                eq(7L),
                anyMap(),
                eq(java.util.Map.of("period", "2026-05")),
                eq(java.util.Map.of("scope", "billing")));
    }
}
