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
            tenant_namespace: flowerbiz
            security:
              apply_rls: true
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets: []
              pack_dependencies: []
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).containsEntry("packId", "flower-rental");
    }

    @Test
    void treatsApplyRlsAsDeclarationOnlyForPlatformAssets() {
        String manifest = """
            pack_id: declaration-only
            pack_name: Declaration Only Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: false
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
                  id: dwd_demo
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsRawSql() {
        String manifest = """
            pack_id: unsafe
            pack_name: Unsafe Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
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
            tenant_namespace: 花卉
            security:
              apply_rls: true
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
                "tenant_namespace must be lowercase ASCII letters, numbers, hyphen or underscore, and start with a letter",
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
            tenant_namespace: demo
            security:
              apply_rls: true
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
    void rejectsNestedRawSqlInsideLists() {
        String manifest = """
            pack_id: nested-unsafe
            pack_name: Nested Unsafe Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            metrics:
              - metric_code: demo_metric
                metric_name: Demo Metric
                term_ids:
                  - glossary:demo
                formula:
                  type: aggregation
                  aggregation: sum
                  field: amount
                nested:
                  - raw_sql: drop table asset_grant
            dependencies:
              platform_assets: []
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
            tenant_namespace: demo
            security:
              apply_rls: true
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

    @Test
    void rejectsPlatformInternalAssetTypesInMetricPacks() {
        String manifest = """
            pack_id: unsafe-platform-asset
            pack_name: Unsafe Platform Asset Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            files:
              domains: domains.yml
              business_objects: business-objects.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets:
                - type: SECURITY_POLICY
                  id: policy.demo
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("dependencies.platform_assets[0].type is not allowed for metric-pack references");
    }

    @Test
    void rejectsInlineMetricsWithoutGlossaryTerms() {
        String manifest = """
            pack_id: missing-terms
            pack_name: Missing Terms Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            metrics:
              - metric_code: contract_amount
                metric_name: 合同金额
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
              platform_assets: []
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("metrics[0] must bind at least one glossary term via term_ids");
    }

    @Test
    void rejectsUnsafeGlossaryTermReference() {
        String manifest = """
            pack_id: unsafe-term
            pack_name: Unsafe Term Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            metrics:
              - metric_code: contract_amount
                metric_name: 合同金额
                term_ids:
                  - bad;drop table
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
              platform_assets: []
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("metrics[0].term_ids[0] must be a safe glossary term reference");
    }

    @Test
    void rejectsInlineMetricTermNotDeclaredAsPlatformGlossaryAsset() {
        String manifest = """
            pack_id: undeclared-term
            pack_name: Undeclared Term Pack
            version: 0.1.0
            industry: demo
            edition_required: professional
            tenant_namespace: demo
            security:
              apply_rls: true
            metrics:
              - metric_code: contract_amount
                metric_name: 合同金额
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
              platform_assets: []
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
            .contains("metrics[0].term_ids[0] must be declared as GLOSSARY_TERM in dependencies.platform_assets");
    }

    @Test
    void requiresRlsAndTenantScopeForPlatformAssets() {
        String manifest = """
            pack_id: missing-scope
            pack_name: Missing Scope Pack
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
                  id: dwd_demo
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors())
            .contains(
                "dependencies.platform_assets[0] must declare owner_namespace or inherit top-level tenant_namespace",
                "security.apply_rls must be declared as a boolean when platform assets are referenced"
            );
    }

    @Test
    void validatesPackDependencies() {
        String manifest = """
            pack_id: flower-extension
            pack_name: Flower Extension Pack
            version: 0.1.0
            industry: flower_rental
            edition_required: professional
            tenant_namespace: flowerbiz
            security:
              apply_rls: true
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
                  id: dwd_demo
              pack_dependencies:
                - pack_id: common-customer
                  version_constraint: ">=0.1.0 <1.0.0"
            """;

        MetricPackValidationResult result = service.validateManifest(manifest);

        assertThat(result.valid()).isTrue();
        assertThat(result.summary()).containsEntry("packDependencyCount", 1);
    }
}
