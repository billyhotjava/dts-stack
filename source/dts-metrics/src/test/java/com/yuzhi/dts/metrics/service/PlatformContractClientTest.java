package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PlatformContractClientTest {

    @Test
    void internalUrlDoesNotDoublePrefixApiPath() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        PlatformContractClient client = new PlatformContractClient(properties, RestClient.builder().build());

        assertThat(client.internalUrl("/internal/asset-permission/check"))
            .isEqualTo("http://dts-platform:8081/api/internal/asset-permission/check");
        assertThat(client.internalUrl("/api/internal/asset-permission/check"))
            .isEqualTo("http://dts-platform:8081/api/internal/asset-permission/check");
    }

    @Test
    void internalUrlSupportsRootApiPath() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/");
        PlatformContractClient client = new PlatformContractClient(properties, RestClient.builder().build());

        assertThat(client.internalUrl("internal/capabilities"))
            .isEqualTo("http://dts-platform:8081/internal/capabilities");
    }

    @Test
    void resolveGlossaryTermsCallsPlatformInternalContractWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/glossary/terms/resolve"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("{\"refs\":[\"glossary.contract_amount\",\"glossary.draft_rate\"]}"))
            .andRespond(withSuccess("""
                {
                  "terms": [
                    {
                      "ref": "glossary.contract_amount",
                      "code": "glossary.contract_amount",
                      "name": "合同金额",
                      "status": "ACTIVE",
                      "active": true
                    },
                    {
                      "ref": "glossary.draft_rate",
                      "code": "glossary.draft_rate",
                      "name": "草稿指标",
                      "status": "DRAFT",
                      "active": false
                    }
                  ],
                  "missing": ["glossary.missing"],
                  "inactive": ["glossary.draft_rate"]
                }
                """, MediaType.APPLICATION_JSON));

        PlatformContractClient.GlossaryResolveResult result = client.resolveGlossaryTerms(List.of(
            "glossary.contract_amount",
            "glossary.draft_rate"
        ));

        assertThat(result.terms()).extracting(PlatformContractClient.GlossaryTermContract::ref)
            .containsExactly("glossary.contract_amount", "glossary.draft_rate");
        assertThat(result.missing()).containsExactly("glossary.missing");
        assertThat(result.inactive()).containsExactly("glossary.draft_rate");
        server.verify();
    }

    @Test
    void resolveGlossaryTermsSplitsLargeRequestsIntoPlatformBatches() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        List<String> refs = new ArrayList<>();
        for (int i = 0; i < 201; i++) {
            refs.add("glossary.term_" + i);
        }

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/glossary/terms/resolve"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"terms":[],"missing":["glossary.term_0"],"inactive":[],"ambiguous":[]}
                """, MediaType.APPLICATION_JSON));
        server
            .expect(requestTo("http://dts-platform:8081/api/internal/glossary/terms/resolve"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                {"terms":[],"missing":["glossary.term_200"],"inactive":[],"ambiguous":[]}
                """, MediaType.APPLICATION_JSON));

        PlatformContractClient.GlossaryResolveResult result = client.resolveGlossaryTerms(refs);

        assertThat(result.missing()).containsExactly("glossary.term_0", "glossary.term_200");
        server.verify();
    }

    @Test
    void resolveGlossaryTermsWrapsPlatformTransportFailures() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/glossary/terms/resolve"))
            .andRespond(withServerError());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.resolveGlossaryTerms(List.of("glossary.contract_amount")))
            .isInstanceOf(PlatformContractClient.PlatformContractException.class)
            .hasMessageContaining("glossary terms resolve");
    }
}
