package com.yuzhi.dts.platform.service.modeling.serving;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yuzhi.dts.platform.config.DtsAnalyticsProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class AnalyticsSemanticPublishClientTest {

    private MockRestServiceServer server;
    private AnalyticsSemanticPublishClient client;

    @BeforeEach
    void setUp() {
        DtsAnalyticsProperties properties = new DtsAnalyticsProperties();
        properties.setBaseUrl("http://analytics.test");
        properties.setServiceName("dts-platform");
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new AnalyticsSemanticPublishClient(restTemplate, properties);
    }

    @Test
    void publishesWithStablePlatformSourceIdentityAndServiceHeader() {
        var payload = new CatalogModelSemanticContract.PublishPayload(
            "10000000-0000-0000-0000-000000000001",
            "model_spec_30000000000000000000000000000001",
            "pjm_dws_budget_execution",
            "public",
            "项目预算执行汇总",
            "项目预算执行汇总模型",
            "INTERNAL",
            "每个项目每月一行",
            "r4",
            true,
            List.of(),
            List.of(),
            List.of()
        );
        server.expect(requestTo("http://analytics.test/api/semantic/publish"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-platform"))
            .andExpect(jsonPath("$.platformDataSourceId").value("10000000-0000-0000-0000-000000000001"))
            .andExpect(jsonPath("$.modelName").value("model_spec_30000000000000000000000000000001"))
            .andRespond(withSuccess("{\"modelName\":\"model_spec_30000000000000000000000000000001\"}", MediaType.APPLICATION_JSON));

        client.publish(payload);

        server.verify();
    }
}
