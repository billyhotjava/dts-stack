package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import org.junit.jupiter.api.Test;

class MetricArtifactGenerationServiceTest {

    private final MetricArtifactGenerationService service = new MetricArtifactGenerationService(
        new MetricPackValidationService(),
        new MetricFormulaSqlGenerator()
    );

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
            """;

        MetricArtifactPreviewResult result = service.preview(manifest);

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
        MetricArtifactPreviewResult result = service.preview("pack_id: unsafe\nraw_sql: drop table users");

        assertThat(result.valid()).isFalse();
        assertThat(result.artifacts()).isEmpty();
        assertThat(result.errors()).isNotEmpty();
    }
}
