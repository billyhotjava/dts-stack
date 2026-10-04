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
import java.util.Map;
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

    @Test
    void checkDbtReleaseGateCallsPlatformWithAppliedPolicyMetadata() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/etl/dbt/release-gate/check"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("""
                {
                  "models": "dws_publish_policy_summary",
                  "strictMode": true,
                  "appliedPolicySource": "platform-row-filter",
                  "appliedPredicateHash": "sha256:abc123"
                }
                """))
            .andRespond(withSuccess("""
                {
                  "decision": "PASS",
                  "appliedPolicySource": "platform-row-filter",
                  "appliedPredicateHash": "sha256:abc123"
                }
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.checkDbtReleaseGate(
            new PlatformContractClient.DbtReleaseGateRequest(
                "dws_publish_policy_summary",
                null,
                null,
                true,
                "platform-row-filter",
                "sha256:abc123"
            )
        );

        assertThat(result).containsEntry("decision", "PASS");
        assertThat(result).containsEntry("appliedPolicySource", "platform-row-filter");
        assertThat(result).containsEntry("appliedPredicateHash", "sha256:abc123");
        server.verify();
    }

    @Test
    void listCatalogAssetsCallsPlatformCatalogWithServiceAuthAndQueryParams() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/catalog/assets-v2?warehouseLayer=DWS&keyword=order+summary&page=0&size=20"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andRespond(withSuccess("""
                {"data":{"content":[],"total":0}}
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.listCatalogAssets("DWS", "order summary", 0, 20);

        assertThat(result).containsKey("data");
        server.verify();
    }

    @Test
    void getCatalogAssetSchemaContractCallsPlatformCatalogWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/catalog/assets-v2/asset-001/schema-contract"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andRespond(withSuccess("""
                {"data":{"assetId":"asset-001","columns":[]}}
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.getCatalogAssetSchemaContract("asset-001");

        assertThat(result).containsKey("data");
        server.verify();
    }

    @Test
    void registerTaggableAssetsCallsPlatformInternalContractWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder)
            .build();
        PlatformContractClient client = new PlatformContractClient(
            properties,
            builder.build()
        );

        server
            .expect(
                requestTo(
                    "http://dts-platform:8081/api/internal/catalog/taggable-assets/register"
                )
            )
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(
                header(
                    "X-DTS-Service-Token",
                    "metrics-secret"
                )
            )
            .andExpect(
                content().json(
                    """
                    {
                      "assets": [
                        {
                          "assetType": "METRIC_PACK",
                          "canonicalAssetKey": "tenant:default/env:prod/dialect:generic/metric-pack:orders/version:v1",
                          "remoteAssetId": "orders:v1",
                          "ownerDept": "D01"
                        },
                        {
                          "assetType": "METRIC",
                          "canonicalAssetKey": "metric:orders/revenue",
                          "remoteAssetId": "orders:revenue",
                          "ownerDept": "D01"
                        }
                      ]
                    }
                    """
                )
            )
            .andRespond(
                withSuccess(
                    """
                    {
                      "registered": 2,
                      "canonicalAssetKeys": [
                        "tenant:default/env:prod/dialect:generic/metric-pack:orders/version:v1",
                        "metric:orders/revenue"
                      ]
                    }
                    """,
                    MediaType.APPLICATION_JSON
                )
            );

        Map<String, Object> result = client.registerTaggableAssets(
            new PlatformContractClient.TaggableAssetsRegisterRequest(
                List.of(
                    new PlatformContractClient.TaggableAssetRegistration(
                        "METRIC_PACK",
                        "tenant:default/env:prod/dialect:generic/metric-pack:orders/version:v1",
                        "orders:v1",
                        "D01"
                    ),
                    new PlatformContractClient.TaggableAssetRegistration(
                        "METRIC",
                        "metric:orders/revenue",
                        "orders:revenue",
                        "D01"
                    )
                )
            )
        );

        assertThat(result).containsEntry("registered", 2);
        server.verify();
    }

    @Test
    void validateMetricModelCallsPlatformValidationGatewayWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/metrics/model-validation"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("""
                {
                  "modelId": "order-summary",
                  "modelName": "dws_order_summary",
                  "artifactRef": "candidate://dts-metrics/dws_order_summary",
                  "graph": {"base":"dws_order_day"},
                  "artifacts": {"dbtModelSql":"select 1 as metric_ready"},
                  "appliedPolicySource": "platform-policy-required",
                  "appliedPredicateHash": "sha256:abc123"
                }
                """))
            .andRespond(withSuccess("""
                {"decision":"PASS","valid":true}
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.validateMetricModel(
            new PlatformContractClient.MetricModelValidationRequest(
                "order-summary",
                "dws_order_summary",
                "candidate://dts-metrics/dws_order_summary",
                Map.of("base", "dws_order_day"),
                Map.of("dbtModelSql", "select 1 as metric_ready"),
                "platform-policy-required",
                "sha256:abc123"
            )
        );

        assertThat(result).containsEntry("decision", "PASS").containsEntry("valid", true);
        server.verify();
    }

    @Test
    void submitDbtReleaseCallsPlatformReleaseGatewayWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/etl/dbt/release/submit"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("""
                {
                  "modelId": "order-summary",
                  "modelName": "dws_order_summary",
                  "artifactRef": "candidate://dts-metrics/dws_order_summary",
                  "dryRun": false,
                  "appliedPolicySource": "platform-policy-required",
                  "appliedPredicateHash": "sha256:abc123"
                }
                """))
            .andRespond(withSuccess("""
                {"publishReference":"platform-release-001","decision":"PASS"}
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> result = client.submitDbtRelease(
            new PlatformContractClient.DbtReleaseSubmitRequest(
                "order-summary",
                "dws_order_summary",
                "candidate://dts-metrics/dws_order_summary",
                false,
                "platform-policy-required",
                "sha256:abc123"
            )
        );

        assertThat(result).containsEntry("publishReference", "platform-release-001").containsEntry("decision", "PASS");
        server.verify();
    }

    @Test
    void recordPolicyInjectionCallsPlatformWithServiceAuth() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        properties.getPlatform().setServiceToken("metrics-secret");

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        PlatformContractClient client = new PlatformContractClient(properties, builder.build());

        server
            .expect(requestTo("http://dts-platform:8081/api/internal/v1/asset-permission/audit/policy-injection"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-DTS-Service", "dts-metrics"))
            .andExpect(header("X-DTS-Service-Token", "metrics-secret"))
            .andExpect(content().json("""
                {
                  "actor": "ptrdemo",
                  "assetType": "DATASET",
                  "assetId": "dataset-001",
                  "action": "PREVIEW",
                  "predicates": ["dept_code = 'D01'"],
                  "maskedColumns": ["customer_phone"],
                  "policySource": "platform-row-filter+masking",
                  "predicateHash": "sha256:abc123",
                  "direction": "CONSUMER",
                  "packId": "flower-rental"
                }
                """))
            .andRespond(withSuccess("{\"recorded\":true}", MediaType.APPLICATION_JSON));

        client.recordPolicyInjection(
            new PlatformContractClient.PolicyInjectionAuditRequest(
                "ptrdemo",
                "DATASET",
                "dataset-001",
                "PREVIEW",
                List.of("dept_code = 'D01'"),
                List.of("customer_phone"),
                "platform-row-filter+masking",
                "sha256:abc123",
                "CONSUMER",
                "flower-rental"
            )
        );

        server.verify();
    }
}
