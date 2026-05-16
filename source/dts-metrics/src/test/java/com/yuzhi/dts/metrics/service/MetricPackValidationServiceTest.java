package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import org.junit.jupiter.api.Test;

class MetricPackValidationServiceTest {

    private final MetricPackValidationService service = new MetricPackValidationService();

    @Test
    void validatesManifestMinimumContract() {
        String manifest = """
            pack_id: flower-rental
            pack_name: 花卉租赁经营指标包
            version: 0.1.0
            industry: flower_rental
            edition_required: professional
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets: []
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).containsEntry("packId", "flower-rental");
    }

    @Test
    void rejectsRawSql() {
        String manifest = """
            pack_id: unsafe
            pack_name: Unsafe Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              raw_sql: drop table asset_grant
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("raw_sql is not allowed in metric-pack v0.1");
    }
}
