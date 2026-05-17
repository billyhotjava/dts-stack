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
                List.of("glossary.draft_rate")
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
            new PlatformContractClient.GlossaryResolveResult(List.of(), List.of("glossary.missing"), List.of())
        ).preview(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).contains("glossary terms are missing in platform: glossary.missing");
    }

    private static MetricArtifactGenerationService serviceWithActiveTerms(String... refs) {
        List<PlatformContractClient.GlossaryTermContract> terms = Arrays
            .stream(refs)
            .map(ref -> new PlatformContractClient.GlossaryTermContract(ref, null, ref, ref, "ACTIVE", true))
            .toList();
        return serviceWithGlossaryResult(new PlatformContractClient.GlossaryResolveResult(terms, List.of(), List.of()));
    }

    private static MetricArtifactGenerationService serviceWithGlossaryResult(PlatformContractClient.GlossaryResolveResult result) {
        return new MetricArtifactGenerationService(
            new MetricPackValidationService(),
            new MetricFormulaSqlGenerator(),
            new StubPlatformContractClient(result)
        );
    }

    private static final class StubPlatformContractClient extends PlatformContractClient {

        private final GlossaryResolveResult result;

        private StubPlatformContractClient(GlossaryResolveResult result) {
            super(new DtsMetricsProperties(), RestClient.builder().build());
            this.result = result;
        }

        @Override
        public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
            return result;
        }
    }
}
