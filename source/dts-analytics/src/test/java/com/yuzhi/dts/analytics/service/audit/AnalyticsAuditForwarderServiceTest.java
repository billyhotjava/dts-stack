package com.yuzhi.dts.analytics.service.audit;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AnalyticsAuditForwarderServiceTest {

    @Test
    void recordShouldForwardStableActionCodeForAdminCatalogResolution() {
        AnalyticsAuditForwarderService service = new AnalyticsAuditForwarderService(props(), new RestTemplateBuilder());
        MockRestServiceServer server = bindServer(service);
        server
            .expect(requestTo("http://admin.test/api/audit-events"))
            .andExpect(method(POST))
            .andExpect(header("X-DTS-Service", "dts-analytics"))
            .andExpect(header("X-DTS-Service-Token", "analytics-secret"))
            .andExpect(jsonPath("$.sourceSystem").value("analytics"))
            .andExpect(jsonPath("$.buttonCode").value("SCREEN_VIEW"))
            .andExpect(jsonPath("$.operationCode").value("SCREEN_VIEW"))
            .andExpect(jsonPath("$.operationName").value("查看大屏"))
            .andExpect(jsonPath("$.summary").value("查看大屏"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.record(
            new AnalyticsAuditEvent(
                "xiezm",
                "测试xiezm",
                "analytics.screen",
                "SCREEN_VIEW",
                "查看大屏",
                "READ",
                "analytics.screen",
                "42",
                "SUCCESS",
                "GET",
                "/api/screen/42",
                "10.20.0.1",
                "JUnit",
                12
            )
        );

        server.verify();
    }

    private MockRestServiceServer bindServer(AnalyticsAuditForwarderService service) {
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        return MockRestServiceServer.bindTo(restTemplate).build();
    }

    private DtsAdminProperties props() {
        DtsAdminProperties props = new DtsAdminProperties();
        props.setEnabled(true);
        props.setBaseUrl("http://admin.test");
        props.setApiPath("/api");
        props.setServiceName("dts-analytics");
        props.setServiceToken("analytics-secret");
        return props;
    }
}
