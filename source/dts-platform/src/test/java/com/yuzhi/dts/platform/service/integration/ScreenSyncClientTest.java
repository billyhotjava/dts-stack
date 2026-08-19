package com.yuzhi.dts.platform.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.platform.config.DtsAnalyticsProperties;
import com.yuzhi.dts.platform.service.integration.dto.ScreenSummary;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * Sprint-17 / F1 — wire-level test for {@link ScreenSyncClient}: confirms it
 * sends the service identity and token headers and parses the JSON contract
 * dts-bi exposes via {@code /api/internal/screens}.
 */
class ScreenSyncClientTest {

    private static final String PLATFORM_TOKEN = "platform-analytics-pair-token-20260819";

    private DtsAnalyticsProperties props;
    private ScreenSyncClient client;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        props = new DtsAnalyticsProperties();
        props.setBaseUrl("http://dts-analytics:3000");
        props.setServiceName("dts-platform");
        props.setEnabled(true);

        RestTemplate rt = new RestTemplate();
        server = MockRestServiceServer.bindTo(rt).build();
        client = new ScreenSyncClient(rt, props);
    }

    @Test
    @DisplayName("sends X-DTS-Service header and parses screen list")
    void parsesScreens() {
        props.setServiceToken(PLATFORM_TOKEN);
        server.expect(requestTo("http://dts-analytics:3000/api/internal/screens"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-DTS-Service", "dts-platform"))
            .andExpect(header("X-DTS-Service-Token", PLATFORM_TOKEN))
            .andRespond(withSuccess("""
                [
                  {"id":1,"name":"Sales","description":null,"classification":"INTERNAL",
                   "ownerDeptCode":"DEPT_A","archived":false,"updatedAt":"2026-04-25T08:00:00Z"},
                  {"id":2,"name":"Ops","description":"d","classification":"SECRET",
                   "ownerDeptCode":"DEPT_B","archived":true,"updatedAt":"2026-04-25T09:00:00Z"}
                ]
                """, MediaType.APPLICATION_JSON));

        List<ScreenSummary> result = client.listScreens();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).archived()).isFalse();
        assertThat(result.get(1).archived()).isTrue();
    }

    @Test
    @DisplayName("throws NotConfiguredException when base-url empty")
    void notConfigured() {
        DtsAnalyticsProperties blank = new DtsAnalyticsProperties();
        blank.setBaseUrl("");
        ScreenSyncClient blankClient = new ScreenSyncClient(new RestTemplate(), blank);

        assertThatThrownBy(blankClient::listScreens)
            .isInstanceOf(NotConfiguredException.class);
    }

    @Test
    @DisplayName("throws NotConfiguredException when integration disabled")
    void disabled() {
        DtsAnalyticsProperties off = new DtsAnalyticsProperties();
        off.setBaseUrl("http://x");
        off.setEnabled(false);
        ScreenSyncClient offClient = new ScreenSyncClient(new RestTemplate(), off);

        assertThatThrownBy(offClient::listScreens)
            .isInstanceOf(NotConfiguredException.class);
    }
}
