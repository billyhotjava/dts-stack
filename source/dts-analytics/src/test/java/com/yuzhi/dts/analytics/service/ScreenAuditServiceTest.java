package com.yuzhi.dts.analytics.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenAuditLogRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsUserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class ScreenAuditServiceTest {

    @Test
    void shouldForwardPlatformUsernameAsAuditActor() {
        AnalyticsScreenAuditLogRepository auditRepository = mock(AnalyticsScreenAuditLogRepository.class);
        AnalyticsUserRepository userRepository = mock(AnalyticsUserRepository.class);
        AnalyticsUser actor = user();
        when(userRepository.findById(7L)).thenReturn(Optional.of(actor));
        ScreenAuditService service = new ScreenAuditService(
            auditRepository,
            userRepository,
            new ObjectMapper(),
            new RestTemplateBuilder(),
            props()
        );
        MockRestServiceServer server = bindServer(service);
        server
            .expect(requestTo("http://admin.test/api/audit-events"))
            .andExpect(method(POST))
            .andExpect(jsonPath("$.sourceSystem").value("analytics"))
            .andExpect(jsonPath("$.actor").value("xiezm"))
            .andExpect(jsonPath("$.buttonCode").value("SCREEN_UPDATE"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        AnalyticsScreenAuditLog auditLog = new AnalyticsScreenAuditLog();
        auditLog.setScreenId(42L);
        auditLog.setActorId(7L);
        auditLog.setAction("screen.update");

        service.forwardToAdmin(auditLog, "10.20.0.1");

        server.verify();
    }

    private MockRestServiceServer bindServer(ScreenAuditService service) {
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
