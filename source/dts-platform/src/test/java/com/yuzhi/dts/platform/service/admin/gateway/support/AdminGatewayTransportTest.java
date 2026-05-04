package com.yuzhi.dts.platform.service.admin.gateway.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AdminGatewayTransportTest {

    private AdminGatewayTransport transport;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        PlatformOutboundAdminProperties properties = new PlatformOutboundAdminProperties();
        properties.setBaseUrl("http://dts-admin.test:8081");
        properties.setApiPath("/api");
        properties.setAdminApiPath("/api/admin");
        properties.setServiceToken("svc-token");
        properties.setServiceName("dts-platform");

        transport = new AdminGatewayTransport(new RestTemplateBuilder(), properties, new AdminGatewayHeaders(properties));
        RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(transport, "restTemplate");
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void buildUriShouldResolveApiAndAdminPaths() {
        assertThat(transport.buildUri(AdminGatewayTarget.API, "/platform/orgs"))
            .hasToString("http://dts-admin.test:8081/api/platform/orgs");
        assertThat(transport.buildUri(AdminGatewayTarget.ADMIN_API, "/portal/menus"))
            .hasToString("http://dts-admin.test:8081/api/admin/portal/menus");
    }

    @Test
    void exchangeEnvelopeDataShouldIncludeServiceHeadersAndForwardedHeaders() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.25");
        request.addHeader("X-Real-IP", "198.51.100.25");
        request.addHeader("Forwarded", "for=\"198.51.100.25\"");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        server
            .expect(requestTo("http://dts-admin.test:8081/api/platform/orgs"))
            .andExpect(method(GET))
            .andExpect(header("X-DTS-Service", "dts-platform"))
            .andExpect(header("X-DTS-Service-Token", "svc-token"))
            .andExpect(header("X-Audit-Silent", "true"))
            .andExpect(header("X-Forwarded-For", "198.51.100.25"))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "SUCCESS",
                      "message": "ok",
                      "data": [
                        {"id": 1, "name": "研究所"}
                      ]
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        List<Map<String, Object>> data = transport.exchangeEnvelopeData(
            AdminGatewayTarget.API,
            GET,
            "/platform/orgs",
            null,
            new ParameterizedTypeReference<AdminGatewayEnvelope<List<Map<String, Object>>>>() {},
            AdminGatewayRequestOptions.builder().auditSilent(true).build()
        );

        assertThat(data).hasSize(1);
        assertThat(data.get(0)).containsEntry("name", "研究所");
        server.verify();
    }

    @Test
    void exchangeEnvelopeDataShouldThrowWhenEnvelopeSignalsFailure() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/platform/orgs"))
            .andExpect(method(GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "status": "ERROR",
                      "message": "upstream failed"
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        assertThatThrownBy(
            () ->
                transport.exchangeEnvelopeData(
                    AdminGatewayTarget.API,
                    GET,
                    "/platform/orgs",
                    null,
                    new ParameterizedTypeReference<AdminGatewayEnvelope<List<Map<String, Object>>>>() {},
                    AdminGatewayRequestOptions.defaults()
                )
        )
            .isInstanceOf(AdminGatewayException.class)
            .hasMessageContaining("upstream failed");
    }

    @Test
    void exchangeRawShouldReturnNonEnvelopeBody() {
        server
            .expect(requestTo("http://dts-admin.test:8081/api/platform/infra/data-lakes/default"))
            .andExpect(method(GET))
            .andRespond(
                withSuccess(
                    """
                    {
                      "name": "default_lake",
                      "writerType": "hive"
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        Map<String, Object> data = transport.exchangeRaw(
            AdminGatewayTarget.API,
            GET,
            "/platform/infra/data-lakes/default",
            null,
            new ParameterizedTypeReference<Map<String, Object>>() {},
            AdminGatewayRequestOptions.defaults()
        );

        assertThat(data).containsEntry("name", "default_lake");
        assertThat(data).containsEntry("writerType", "hive");
        server.verify();
    }
}
