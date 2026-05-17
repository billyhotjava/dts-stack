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

    @Test
    void resolveDomainsCallsPlatformInternalContractWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/domains/resolve"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("{\"refs\":[\"flower_rental\",\"missing_domain\"]}"))
            .andRespond(withSuccess("""
                {
                  "domains": [
                    {
                      "ref": "flower_rental",
                      "code": "flower_rental",
                      "name": "花卉租赁"
                    }
                  ],
                  "missing": ["missing_domain"],
                  "ambiguous": []
                }
                """, MediaType.APPLICATION_JSON));

        PlatformContractClient.DomainResolveResult result = client.resolveDomains(List.of("flower_rental", "missing_domain"));

        assertThat(result.domains()).extracting(PlatformContractClient.DomainContract::ref).containsExactly("flower_rental");
        assertThat(result.missing()).containsExactly("missing_domain");
        server.verify();
    }

    @Test
    void resolveDataStandardsCallsPlatformInternalContractWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/data-standards/resolve"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("{\"refs\":[\"contract_amount\",\"draft_amount\"]}"))
            .andRespond(withSuccess("""
                {
                  "standards": [
                    {
                      "ref": "contract_amount",
                      "code": "contract_amount",
                      "name": "合同金额",
                      "status": "ACTIVE",
                      "active": true
                    },
                    {
                      "ref": "draft_amount",
                      "code": "draft_amount",
                      "name": "草稿金额",
                      "status": "DRAFT",
                      "active": false
                    }
                  ],
                  "missing": [],
                  "inactive": ["draft_amount"],
                  "ambiguous": []
                }
                """, MediaType.APPLICATION_JSON));

        PlatformContractClient.DataStandardResolveResult result = client.resolveDataStandards(List.of("contract_amount", "draft_amount"));

        assertThat(result.standards()).extracting(PlatformContractClient.DataStandardContract::ref)
            .containsExactly("contract_amount", "draft_amount");
        assertThat(result.inactive()).containsExactly("draft_amount");
        server.verify();
    }

    @Test
    void resolveRlsPolicyCallsVersionedPlatformContractWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/v1/asset-permission/policy"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("""
                {
                  "username": "xiezm",
                  "userRoles": ["ROLE_USER"],
                  "userDeptCode": "D01",
                  "userClassification": "INTERNAL",
                  "assetClassification": "INTERNAL",
                  "action": "PREVIEW",
                  "asset": {
                    "type": "DATASET",
                    "id": "dataset-001",
                    "key": "source:ptr/schema:dwd/table:contract_detail"
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "applyRls": true,
                  "predicates": ["dept_code = 'D01'"],
                  "maskedColumns": ["customer_phone"],
                  "policySource": "platform-row-filter"
                }
                """, MediaType.APPLICATION_JSON));

        PlatformContractClient.RlsPolicyResult result = client.resolveRlsPolicy(
            new PlatformContractClient.RlsPolicyRequest(
                "xiezm",
                List.of("ROLE_USER"),
                "D01",
                "INTERNAL",
                "INTERNAL",
                "PREVIEW",
                new PlatformContractClient.PermissionAsset(
                    "DATASET",
                    "dataset-001",
                    "source:ptr/schema:dwd/table:contract_detail"
                )
            )
        );

        assertThat(result.applyRls()).isTrue();
        assertThat(result.predicates()).containsExactly("dept_code = 'D01'");
        assertThat(result.maskedColumns()).containsExactly("customer_phone");
        assertThat(result.policySource()).isEqualTo("platform-row-filter");
        server.verify();
    }
}
