package com.yuzhi.dts.platform.service.admin.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdminAuditGatewayTest {

    @Test
    void recordEventShouldUseServiceTokenHeaderInsteadOfOauthAuthorization() {
        PlatformOutboundAdminProperties properties = new PlatformOutboundAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setServiceName("dts-platform");
        properties.setServiceToken("Bearer svc-token");
        AdminAuditGateway gateway = new AdminAuditGateway(new RestTemplateBuilder(), properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(gateway, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server
            .expect(requestTo("http://dts-admin.test:8081/api/audit-events"))
            .andExpect(method(POST))
            .andExpect(header("X-DTS-Service", "dts-platform"))
            .andExpect(header("X-DTS-Service-Token", "svc-token"))
            .andExpect(request -> assertThat(request.getHeaders()).doesNotContainKey(HttpHeaders.AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThat(gateway.recordEvent(Map.of("actor", "xiezm", "action", "CATALOG_ASSET_LIST"))).isTrue();
        server.verify();
    }
}
