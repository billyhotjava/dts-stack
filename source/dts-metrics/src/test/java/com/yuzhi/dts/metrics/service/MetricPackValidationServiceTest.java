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

    @Test
    void rejectsUnsafePackIdentityAndFilePaths() {
        String manifest = """
            pack_id: 花卉租赁
            pack_name: 花卉租赁经营指标包
            version: v1
            industry: flower_rental
            edition_required: premium
            files:
              domains: ../domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies: {}
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
            .contains(
                "pack_id must be lowercase ASCII letters, numbers, hyphen or underscore, and start with a letter",
                "version must use semantic version format, for example 0.1.0",
                "edition_required must be one of foundation, professional, enterprise",
                "files.domains must be a relative .yml/.yaml/.json file path inside the metric pack"
            );
    }

    @Test
    void rejectsRawSqlKeyVariants() {
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
              rawSql: drop table asset_grant
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("raw_sql is not allowed in metric-pack v0.1");
    }

    @Test
    void rejectsUnsafePlatformAssetReference() {
        String manifest = """
            pack_id: unsafe-asset
            pack_name: Unsafe Asset Pack
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
              platform_assets:
                - type: DATASET
                  id: dwd_demo;drop table users
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("dependencies.platform_assets[0].id must be a safe asset reference");
    }
}
