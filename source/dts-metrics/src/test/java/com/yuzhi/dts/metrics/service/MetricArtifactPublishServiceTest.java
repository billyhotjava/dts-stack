package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MetricArtifactPublishServiceTest {

    @Test
    void publishDryRunReResolvesPlatformPolicyAndChangesPredicateHash() {
        CountingPlatformContractClient platform = new CountingPlatformContractClient();
        MetricArtifactGenerationService generationService = new MetricArtifactGenerationService(
            new MetricPackValidationService(),
            new MetricFormulaSqlGenerator(),
            platform
        );
        MetricArtifactPublishService publishService = new MetricArtifactPublishService(generationService, platform);

        var first = publishService.publishDryRun(
            manifestWithSourceAsset("dwd_demo_detail"),
            new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL")
        );
        var second = publishService.publishDryRun(
            manifestWithSourceAsset("dwd_demo_detail"),
            new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D02", "INTERNAL")
        );

        assertThat(first.valid()).isTrue();
        assertThat(second.valid()).isTrue();
        assertThat(platform.policyResolveCount).isEqualTo(2);
        assertThat(platform.releaseGateRequests).hasSize(2);
        assertThat(first.appliedPolicySource()).isEqualTo("platform-row-filter");
        assertThat(first.appliedPredicateHash()).isNotEqualTo(second.appliedPredicateHash());
        assertThat(first.artifacts().get("dbtModelSql")).contains("dept_code = 'D01'");
        assertThat(second.artifacts().get("dbtModelSql")).contains("dept_code = 'D02'");
        assertThat(platform.releaseGateRequests.get(0).appliedPolicySource()).isEqualTo(first.appliedPolicySource());
        assertThat(platform.releaseGateRequests.get(0).appliedPredicateHash()).isEqualTo(first.appliedPredicateHash());
    }

    private static String manifestWithSourceAsset(String sourceModel) {
        return """
            pack_id: publish-policy
            pack_name: Publish Policy Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: %s
            dimensions:
              - stat_month
            metrics:
              - metric_code: contract_amount
                metric_name: Contract Amount
                term_ids:
                  - glossary.contract_amount
                formula:
                  type: aggregation
                  aggregation: sum
                  field: contract_amount
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets:
                - type: DATASET
                  id: %s
                  asset_classification: INTERNAL
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """.formatted(sourceModel, sourceModel);
    }

    private static final class CountingPlatformContractClient extends PlatformContractClient {

        private int policyResolveCount;
        private final java.util.ArrayList<DbtReleaseGateRequest> releaseGateRequests = new java.util.ArrayList<>();

        private CountingPlatformContractClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            return new PermissionCheckResult(
                true,
                "READ",
                "explicit_grant",
                "READ",
                request.action(),
                request.asset().type(),
                request.asset().id(),
                request.asset().key(),
                "ALLOWED",
                "explicit_grant"
            );
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            policyResolveCount += 1;
            return new RlsPolicyResult(
                true,
                List.of("dept_code = '" + request.userDeptCode() + "'"),
                List.of(),
                "platform-row-filter"
            );
        }

        @Override
        public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
            return new GlossaryResolveResult(
                refs.stream().map(ref -> new GlossaryTermContract(ref, null, ref, ref, "ACTIVE", true)).toList(),
                List.of(),
                List.of(),
                List.of()
            );
        }

        @Override
        public DomainResolveResult resolveDomains(List<String> refs) {
            return new DomainResolveResult(
                refs.stream().map(ref -> new DomainContract(ref, null, ref, ref, null)).toList(),
                List.of(),
                List.of()
            );
        }

        @Override
        public DataStandardResolveResult resolveDataStandards(List<String> refs) {
            return DataStandardResolveResult.empty();
        }

        @Override
        public Map<String, Object> checkDbtReleaseGate(DbtReleaseGateRequest request) {
            releaseGateRequests.add(request);
            return Map.of(
                "decision",
                "PASS",
                "appliedPolicySource",
                request.appliedPolicySource(),
                "appliedPredicateHash",
                request.appliedPredicateHash()
            );
        }
    }
}
