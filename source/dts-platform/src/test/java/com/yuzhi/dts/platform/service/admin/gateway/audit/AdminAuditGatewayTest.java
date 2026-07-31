package com.yuzhi.dts.platform.service.admin.gateway.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway.AuditSubmissionResult;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import java.time.Duration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AdminAuditGatewayTest {

    @Test
    void submitEventShouldUseServiceIdentityAndRejectLegacy202Response() {
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

        assertThat(gateway.submitEvent(Map.of("actor", "xiezm", "action", "CATALOG_ASSET_LIST")))
            .isEqualTo(AuditSubmissionResult.RETRYABLE_FAILURE);
        server.verify();
    }

    @Test
    void submitEventShouldAcceptOnlyExactIdempotentSuccessContracts() {
        PlatformOutboundAdminProperties properties = new PlatformOutboundAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setServiceName("dts-platform");
        properties.setServiceToken("svc-token");
        AdminAuditGateway gateway = new AdminAuditGateway(new RestTemplateBuilder(), properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(gateway, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server
            .expect(requestTo("http://dts-admin.test:8081/api/audit-events"))
            .andRespond(
                withStatus(HttpStatus.CREATED)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"RECORDED\",\"eventId\":\"event-1\"}")
            );

        assertThat(gateway.submitEvent(Map.of("eventId", "event-1"))).isEqualTo(AuditSubmissionResult.RECORDED);
        server.verify();
    }

    @Test
    void submitEventShouldAcceptExactDuplicateAndRejectMismatchedAcknowledgement() {
        PlatformOutboundAdminProperties properties = new PlatformOutboundAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setServiceName("dts-platform");
        properties.setServiceToken("svc-token");
        AdminAuditGateway gateway = new AdminAuditGateway(new RestTemplateBuilder(), properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(gateway, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server
            .expect(requestTo("http://dts-admin.test:8081/api/audit-events"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"DUPLICATE\",\"eventId\":\"event-1\"}")
            );
        server
            .expect(requestTo("http://dts-admin.test:8081/api/audit-events"))
            .andRespond(
                withStatus(HttpStatus.CREATED)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"status\":\"RECORDED\",\"eventId\":\"different-event\"}")
            );

        assertThat(gateway.submitEvent(Map.of("eventId", "event-1"))).isEqualTo(AuditSubmissionResult.DUPLICATE);
        assertThat(gateway.submitEvent(Map.of("eventId", "event-1")))
            .isEqualTo(AuditSubmissionResult.RETRYABLE_FAILURE);
        server.verify();
    }

    @Test
    void submitEventShouldRetryRateLimitsAndHonorRetryAfter() {
        PlatformOutboundAdminProperties properties = new PlatformOutboundAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setServiceName("dts-platform");
        properties.setServiceToken("svc-token");
        AdminAuditGateway gateway = new AdminAuditGateway(new RestTemplateBuilder(), properties);
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(gateway, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

        server
            .expect(requestTo("http://dts-admin.test:8081/api/audit-events"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "17"));

        AuditSubmissionResult result = gateway.submitEvent(Map.of("eventId", "event-1"));

        assertThat(result.outcome()).isEqualTo(AuditSubmissionResult.Outcome.RETRYABLE_FAILURE);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(17));
        server.verify();
    }
}
