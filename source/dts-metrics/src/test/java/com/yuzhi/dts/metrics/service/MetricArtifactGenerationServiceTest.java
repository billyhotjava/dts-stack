package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MetricArtifactGenerationServiceTest {

    @Test
    void generatesDwsAdsCandidateArtifactsFromInlineMetricDsl() {
        String manifest = """
            pack_id: flower-rental
            pack_name: 花卉租赁经营指标包
            version: 0.1.0
            industry: flower_rental
            edition_required: professional
            tenant_namespace: flowerbiz
            security:
              apply_rls: true
            source_model: dwd_flower_contract_detail
            dimensions:
              - stat_month
              - dept_name
            metrics:
              - metric_code: contract_amount
                metric_name: 合同金额
                term_ids:
                  - glossary.contract_amount
                formula:
                  type: aggregation
                  aggregation: sum
                  field: contract_amount
              - metric_code: direct_cost_execution_rate
                metric_name: 直接成本执行率
                term_ids:
                  - glossary.direct_cost_execution_rate
                formula:
                  type: ratio
                  numerator:
                    type: aggregation
                    aggregation: sum
                    field: direct_cost_amount
                  denominator:
                    type: aggregation
                    aggregation: sum
                    field: direct_cost_control_amount
                  multiply: 100
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
                  id: dwd_flower_contract_detail
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
                - type: GLOSSARY_TERM
                  id: glossary.direct_cost_execution_rate
            """;

        MetricArtifactPreviewResult result = serviceWithActiveTerms(
            "glossary.contract_amount",
            "glossary.direct_cost_execution_rate"
        ).preview(manifest);

        assertThat(result.valid()).isTrue();
        assertThat(result.artifacts()).containsKeys("dbtModelSql", "schemaYml", "metricDoc");
        assertThat(result.artifacts().get("dbtModelSql"))
            .contains("{{ ref('dwd_flower_contract_detail') }}")
            .contains("sum(coalesce(contract_amount, 0)) as contract_amount")
            .contains("direct_cost_execution_rate")
            .contains("group by")
            .contains("stat_month")
            .contains("dept_name");
    }

    @Test
    void injectsPlatformRlsPredicateIntoGeneratedDbtSql() {
        String manifest = manifestWithSourceAsset("dwd_demo_detail");

        MetricArtifactPreviewResult result = serviceWithPlatform(
            glossaryResult("glossary.contract_amount"),
            allowedPermission(),
            null,
            null,
            new PlatformContractClient.RlsPolicyResult(true, List.of("dept_code = 'D01'"), List.of(), "platform-row-filter")
        ).preview(manifest, new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL"));

        assertThat(result.valid()).isTrue();
        assertThat(result.artifacts().get("dbtModelSql"))
            .contains("-- dts-platform RLS: platform-row-filter")
            .contains("where\n    (dept_code = 'D01')\n")
            .contains("group by\n    stat_month");
    }

    @Test
    void invalidManifestDoesNotGenerateArtifacts() {
        MetricArtifactPreviewResult result = serviceWithActiveTerms().preview("pack_id: unsafe\nraw_sql: drop table users");

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).isNotEmpty();
    }

    @Test
    void missingSourceModelStillGeneratesPlaceholderArtifacts() {
        String manifest = """
            pack_id: placeholder-source
            pack_name: Placeholder Source Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
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
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """;

        MetricArtifactPreviewResult result = serviceWithActiveTerms("glossary.contract_amount").preview(manifest);

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).containsEntry("sourceModel", "replace_with_dwd_model");
    }

    @Test
    void sourceModelMustBeDeclaredAsPlatformAssetBeforeGeneratingArtifacts() {
        String manifest = """
            pack_id: missing-source-asset
            pack_name: Missing Source Asset Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: dwd_demo_detail
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
                  id: dwd_other_detail
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """;

        MetricArtifactPreviewResult result = serviceWithActiveTerms("glossary.contract_amount").preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("source_model must be declared in dependencies.platform_assets before artifact preview");
    }

    @Test
    void sourceModelDoesNotMatchSimilarSuffixPlatformAssetRef() {
        String manifest = """
            pack_id: similar-source-asset
            pack_name: Similar Source Asset Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: foo
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
                  id: other_foo
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """;

        MetricArtifactPreviewResult result = serviceWithActiveTerms("glossary.contract_amount").preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("source_model must be declared in dependencies.platform_assets before artifact preview");
    }

    @Test
    void previewChecksSourceAssetPermissionForCallerBeforeGeneratingArtifacts() {
        String manifest = manifestWithSourceAsset("dwd_demo_detail");

        MetricArtifactPreviewResult result = serviceWithPlatform(
            glossaryResult("glossary.contract_amount"),
            PlatformContractClient.PermissionCheckResult.denied("denied")
        ).preview(manifest, new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL"));

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("platform asset permission check failed before artifact preview");
    }

    @Test
    void previewHidesAssetDetailsWhenPermissionCheckFails() {
        String manifest = manifestWithSourceAsset("dwd_top_secret_table");

        MetricArtifactPreviewResult result = serviceWithPlatform(
            glossaryResult("glossary.contract_amount"),
            PlatformContractClient.PermissionCheckResult.denied("denied")
        ).preview(manifest, new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL"));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).containsExactly("platform asset permission check failed before artifact preview");
        assertThat(String.join("\n", result.errors())).doesNotContain("dwd_top_secret_table");
    }

    @Test
    void platformDomainMustResolveBeforeGeneratingArtifacts() {
        String manifest = manifestWithSourceAsset("dwd_demo_detail");

        MetricArtifactPreviewResult result = serviceWithPlatform(
            glossaryResult("glossary.contract_amount"),
            allowedPermission(),
            new PlatformContractClient.DomainResolveResult(List.of(), List.of("demo"), List.of()),
            null
        ).preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("data domains are missing in platform: demo");
    }

    @Test
    void dataStandardsMustResolveToActivePlatformStandardsBeforeGeneratingArtifacts() {
        String manifest = """
            pack_id: inactive-standard
            pack_name: Inactive Standard Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: dwd_demo_detail
            dimensions:
              - field: contract_amount
                standard_code: contract_amount
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
              data_standards:
                - contract_amount
                - draft_amount
              platform_assets:
                - type: DATASET
                  id: dwd_demo_detail
                  asset_classification: INTERNAL
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """;

        MetricArtifactPreviewResult result = serviceWithPlatform(
            glossaryResult("glossary.contract_amount"),
            allowedPermission(),
            null,
            new PlatformContractClient.DataStandardResolveResult(
                List.of(new PlatformContractClient.DataStandardContract(
                    "contract_amount",
                    null,
                    "contract_amount",
                    "合同金额",
                    "flower_rental",
                    "DECIMAL",
                    false,
                    "ACTIVE",
                    true
                )),
                List.of(),
                List.of("draft_amount"),
                List.of()
            )
        ).preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("data standards are not ACTIVE in platform: draft_amount");
    }

    @Test
    void glossaryTermsMustResolveToActivePlatformTermsBeforeGeneratingArtifacts() {
        String manifest = """
            pack_id: inactive-term
            pack_name: Inactive Term Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: dwd_demo_detail
            dimensions:
              - stat_month
            metrics:
              - metric_code: contract_amount
                metric_name: Contract Amount
                term_ids:
                  - glossary.contract_amount
                  - glossary.draft_rate
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
                  id: dwd_demo_detail
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
                - type: GLOSSARY_TERM
                  id: glossary.draft_rate
            """;

        MetricArtifactPreviewResult result = serviceWithGlossaryResult(
            new PlatformContractClient.GlossaryResolveResult(
                List.of(new PlatformContractClient.GlossaryTermContract(
                    "glossary.contract_amount",
                    null,
                    "glossary.contract_amount",
                    "合同金额",
                    "ACTIVE",
                    true
                )),
                List.of(),
                List.of("glossary.draft_rate"),
                List.of()
            )
        ).preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("glossary terms are not ACTIVE in platform: glossary.draft_rate");
    }

    @Test
    void missingGlossaryTermsStopArtifactPreview() {
        String manifest = """
            pack_id: missing-term
            pack_name: Missing Term Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            source_model: dwd_demo_detail
            dimensions:
              - stat_month
            metrics:
              - metric_code: contract_amount
                metric_name: Contract Amount
                term_ids:
                  - glossary.missing
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
                  id: dwd_demo_detail
                - type: GLOSSARY_TERM
                  id: glossary.missing
            """;

        MetricArtifactPreviewResult result = serviceWithGlossaryResult(
            new PlatformContractClient.GlossaryResolveResult(List.of(), List.of("glossary.missing"), List.of(), List.of())
        ).preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("glossary terms are missing in platform: glossary.missing");
    }

    @Test
    void platformContractOutageStopsPreviewWithReadableMessage() {
        String manifest = manifestWithSourceAsset("dwd_demo_detail");

        MetricArtifactPreviewResult result = serviceWithThrowingPlatform().preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("platform contract unavailable while resolving glossary terms; retry later");
    }

    private static MetricArtifactGenerationService serviceWithActiveTerms(String... refs) {
        return serviceWithGlossaryResult(glossaryResult(refs));
    }

    private static MetricArtifactGenerationService serviceWithGlossaryResult(PlatformContractClient.GlossaryResolveResult result) {
        return serviceWithPlatform(
            result,
            new PlatformContractClient.PermissionCheckResult(
                true,
                "READ",
                "explicit_grant",
                "READ",
                "PREVIEW",
                "DATASET",
                "dwd_demo_detail",
                null,
                "ALLOWED",
                "explicit_grant"
            )
        );
    }

    private static PlatformContractClient.GlossaryResolveResult glossaryResult(String... refs) {
        List<PlatformContractClient.GlossaryTermContract> terms = Arrays
            .stream(refs)
            .map(ref -> new PlatformContractClient.GlossaryTermContract(ref, null, ref, ref, "ACTIVE", true))
            .toList();
        return new PlatformContractClient.GlossaryResolveResult(terms, List.of(), List.of(), List.of());
    }

    private static MetricArtifactGenerationService serviceWithPlatform(
        PlatformContractClient.GlossaryResolveResult result,
        PlatformContractClient.PermissionCheckResult permission
    ) {
        return serviceWithPlatform(result, permission, null, null);
    }

    private static MetricArtifactGenerationService serviceWithPlatform(
        PlatformContractClient.GlossaryResolveResult result,
        PlatformContractClient.PermissionCheckResult permission,
        PlatformContractClient.DomainResolveResult domains,
        PlatformContractClient.DataStandardResolveResult standards
    ) {
        return serviceWithPlatform(result, permission, domains, standards, PlatformContractClient.RlsPolicyResult.empty());
    }

    private static MetricArtifactGenerationService serviceWithPlatform(
        PlatformContractClient.GlossaryResolveResult result,
        PlatformContractClient.PermissionCheckResult permission,
        PlatformContractClient.DomainResolveResult domains,
        PlatformContractClient.DataStandardResolveResult standards,
        PlatformContractClient.RlsPolicyResult rlsPolicy
    ) {
        return new MetricArtifactGenerationService(
            new MetricPackValidationService(),
            new MetricFormulaSqlGenerator(),
            new StubPlatformContractClient(result, permission, domains, standards, rlsPolicy)
        );
    }

    private static PlatformContractClient.PermissionCheckResult allowedPermission() {
        return new PlatformContractClient.PermissionCheckResult(
            true,
            "READ",
            "explicit_grant",
            "READ",
            "PREVIEW",
            "DATASET",
            "dwd_demo_detail",
            null,
            "ALLOWED",
            "explicit_grant"
        );
    }

    private static MetricArtifactGenerationService serviceWithThrowingPlatform() {
        return new MetricArtifactGenerationService(
            new MetricPackValidationService(),
            new MetricFormulaSqlGenerator(),
            new ThrowingPlatformContractClient()
        );
    }

    private static final class StubPlatformContractClient extends PlatformContractClient {

        private final GlossaryResolveResult result;
        private final PermissionCheckResult permission;
        private final DomainResolveResult domains;
        private final DataStandardResolveResult standards;
        private final RlsPolicyResult rlsPolicy;

        private StubPlatformContractClient(
            GlossaryResolveResult result,
            PermissionCheckResult permission,
            DomainResolveResult domains,
            DataStandardResolveResult standards,
            RlsPolicyResult rlsPolicy
        ) {
            super(new DtsMetricsProperties(), RestClient.builder().build());
            this.result = result;
            this.permission = permission;
            this.domains = domains;
            this.standards = standards;
            this.rlsPolicy = rlsPolicy;
        }

        @Override
        public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
            return result;
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            return permission;
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            return rlsPolicy != null ? rlsPolicy : RlsPolicyResult.empty();
        }

        @Override
        public DomainResolveResult resolveDomains(List<String> refs) {
            if (domains != null) {
                return domains;
            }
            return new DomainResolveResult(
                refs.stream().map(ref -> new DomainContract(ref, null, ref, ref, null)).toList(),
                List.of(),
                List.of()
            );
        }

        @Override
        public DataStandardResolveResult resolveDataStandards(List<String> refs) {
            if (standards != null) {
                return standards;
            }
            return new DataStandardResolveResult(
                refs.stream().map(ref -> new DataStandardContract(ref, null, ref, ref, null, "STRING", true, "ACTIVE", true)).toList(),
                List.of(),
                List.of(),
                List.of()
            );
        }
    }

    private static final class ThrowingPlatformContractClient extends PlatformContractClient {

        private ThrowingPlatformContractClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
            throw new PlatformContractClient.PlatformContractException("glossary terms resolve");
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            return new PermissionCheckResult(
                true,
                "READ",
                "explicit_grant",
                "READ",
                "PREVIEW",
                "DATASET",
                "dwd_demo_detail",
                null,
                "ALLOWED",
                "explicit_grant"
            );
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            return RlsPolicyResult.empty();
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
    }

    private static String manifestWithSourceAsset(String sourceModel) {
        return """
            pack_id: permission-check
            pack_name: Permission Check Pack
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
}
