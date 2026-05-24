package com.yuzhi.dts.analytics.service;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SemanticAuditServiceTest {

    @Test
    void shouldForwardPlatformUsernameAsAuditActor() {
        SemanticAuditService service = new SemanticAuditService(new RestTemplateBuilder(), props());
        MockRestServiceServer server = bindServer(service);
        server
            .expect(requestTo("http://admin.test/api/audit-events"))
            .andExpect(method(POST))
            .andExpect(jsonPath("$.sourceSystem").value("analytics"))
            .andExpect(jsonPath("$.actor").value("xiezm"))
            .andExpect(jsonPath("$.buttonCode").value("SEMANTIC_WORKBENCH_VIEW"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        service.record("SEMANTIC_WORKBENCH_VIEW", "查看语义工作台", user(), null, "SUCCESS", null, Map.of(), null);

        server.verify();
    }

    private MockRestServiceServer bindServer(SemanticAuditService service) {
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

    private AnalyticsUser user() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        user.setPlatformUsername("xiezm");
        user.setEmail("xiezm@platform.local");
        user.setFirstName("测试");
        user.setLastName("xiezm");
        return user;
    }
}
