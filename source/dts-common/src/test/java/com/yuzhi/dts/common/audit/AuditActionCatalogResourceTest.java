package com.yuzhi.dts.common.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AuditActionCatalogResourceTest {

    @Test
    void registersWarehousePlanCategoryAndPolicyWritesAsModelingActions() throws Exception {
        String catalog;
        try (var input = getClass().getResourceAsStream("/config/audit-action-catalog.json")) {
            assertThat(input).isNotNull();
            catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(catalog)
            .contains("\"code\": \"MODELING_WAREHOUSE_PLAN_CREATE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_PLAN_HEADER_UPDATE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_PLAN_ARCHIVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_BUSINESS_SCOPE_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_CATEGORY_SCOPE_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_POLICY_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_SOURCE_INVENTORY_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_SOURCE_MAPPINGS_SAVE\"")
            .contains("\"code\": \"MODELING_WAREHOUSE_BASELINE_CONFIRM\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_CREATE\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_REPLACEMENT_CREATE\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_SCOPE_REPLACE\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_STATUS_CHANGE\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_PUBLISH\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_PUBLICATION_RETRY\"")
            .contains("\"code\": \"MODEL_RELEASE_CANDIDATE_ROLLBACK\"")
            .contains("\"key\": \"modeling.plan\"");
    }

    @Test
    void registersCanonicalModelSpecAndDimensionDefinitionWritesAsModelingActions() throws Exception {
        String catalog;
        try (var input = getClass().getResourceAsStream("/config/audit-action-catalog.json")) {
            assertThat(input).isNotNull();
            catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(catalog)
            .contains("\"key\": \"modeling.model-spec\"")
            .contains("\"code\": \"MODELING_MODEL_SPEC_CREATE\"")
            .contains("\"code\": \"MODELING_DIMENSION_MODEL_CREATE\"")
            .containsPattern(
                "(?s)\\\"code\\\": \\\"MODELING_DIMENSION_MODEL_CREATE\\\".*?\\\"phases\\\": \\[.*?\\\"SUCCESS\\\".*?\\\"FAIL\\\".*?\\]"
            )
            .contains("\"code\": \"MODELING_MODEL_SPEC_IMPORT_CREATE\"")
            .contains("\"code\": \"MODELING_MODEL_SPEC_UPDATE\"")
            .contains("\"code\": \"MODELING_MODEL_SPEC_RECLASSIFY\"")
            .contains("\"code\": \"MODELING_MODEL_SPEC_DELETE_DRAFT\"")
            .contains("\"key\": \"modeling.dimension-definition\"")
            .contains("\"code\": \"MODELING_DIMENSION_DEFINITION_CREATE\"")
            .contains("\"code\": \"MODELING_DIMENSION_DEFINITION_UPDATE\"")
            .contains("\"code\": \"MODELING_DIMENSION_DEFINITION_CONFIRM\"")
            .contains("\"code\": \"MODELING_DIMENSION_DEFINITION_RETIRE\"")
            .doesNotContain("\"key\": \"modeling.plans\"")
            .doesNotContain("\"key\": \"modeling.sql-model\"")
            .doesNotContain("\"code\": \"MODELING_PLAN_")
            .doesNotContain("\"code\": \"MODELING_SQL_MODEL_");
    }

    @Test
    void registersWarehouseLayerGovernanceActionsInAllThreeRuntimeCopies() throws Exception {
        java.nio.file.Path repo = java.nio.file.Paths.get(System.getProperty("user.dir")).getParent().getParent();
        String canonical = new String(java.nio.file.Files.readAllBytes(
            repo.resolve("source/dts-common/src/main/resources/config/audit-action-catalog.json")
        ), StandardCharsets.UTF_8);
        String platformFallback = new String(java.nio.file.Files.readAllBytes(
            repo.resolve("source/dts-platform/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json")
        ), StandardCharsets.UTF_8);
        String adminFallback = new String(java.nio.file.Files.readAllBytes(
            repo.resolve("source/dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json")
        ), StandardCharsets.UTF_8);

        for (String catalog : java.util.List.of(canonical, platformFallback, adminFallback)) {
            assertThat(catalog)
                .contains("\"code\": \"MODELING_WAREHOUSE_LAYER_CREATE\"")
                .contains("\"code\": \"MODELING_WAREHOUSE_LAYER_DELETE\"")
                .contains("\"resourceType\": \"WAREHOUSE_LAYER\"");
        }
    }

    @Test
    void registersGovernedAnalysisQueryInEveryRuntimeCatalogAndUpgradeMigration() throws Exception {
        java.nio.file.Path repo = java.nio.file.Paths.get(System.getProperty("user.dir")).getParent().getParent();
        for (String relativePath : java.util.List.of(
            "source/dts-common/src/main/resources/config/audit-action-catalog.json",
            "source/dts-platform/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json",
            "source/dts-admin/src/main/docker/dts-common-fallback/src/main/resources/config/audit-action-catalog.json"
        )) {
            String catalog = java.nio.file.Files.readString(repo.resolve(relativePath));
            assertThat(catalog)
                .contains("\"code\": \"ANALYSIS_QUERY\"")
                .contains("执行治理分析查询");
        }

        String migration = java.nio.file.Files.readString(repo.resolve(
            "source/dts-admin/src/main/resources/config/liquibase/changelog/20260819-01_sprint94_analysis_audit_catalog.xml"
        ));
        String master = java.nio.file.Files.readString(repo.resolve(
            "source/dts-admin/src/main/resources/config/liquibase/master.xml"
        ));
        assertThat(migration).contains("ANALYSIS_QUERY");
        assertThat(master).contains("20260819-01_sprint94_analysis_audit_catalog.xml");
    }

    @Test
    void registersModelMaterializationMachineAuditActions() throws Exception {
        String catalog;
        try (var input = getClass().getResourceAsStream("/config/audit-action-catalog.json")) {
            assertThat(input).isNotNull();
            catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(catalog)
            .contains("\"key\": \"modeling.materialization\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_DISPATCH_SUBMITTED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_DISPATCH_UNKNOWN\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_DISPATCH_BLOCKED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_SOURCE_FENCE_DENIED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_RUNTIME_SPEC_CONSUMED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_RUNTIME_SPEC_BLOCKED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_ARTIFACTS_SYNCED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_RELATIONS_VERIFIED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_RUN_FAILED\"")
            .contains("\"code\": \"MODEL_MATERIALIZATION_RUN_FINALIZED\"")
            .contains("\"key\": \"modeling.rollback-invalidation\"")
            .contains("\"code\": \"ROLLBACK_INVALIDATION_PREPARE\"")
            .contains("\"code\": \"ROLLBACK_INVALIDATION_APPLY\"")
            .contains("\"code\": \"ROLLBACK_INVALIDATION_ABORT\"")
            .contains("\"code\": \"SOURCE_AVAILABILITY_RESTORE\"");
    }

    @Test
    void registersCanonicalModelImplementationLifecycleWrites() throws Exception {
        String catalog;
        try (var input = getClass().getResourceAsStream("/config/audit-action-catalog.json")) {
            assertThat(input).isNotNull();
            catalog = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(catalog)
            .contains("\"key\": \"modeling.implementation-lifecycle\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_SAVE\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_IMPORT\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_OWNERSHIP_CONVERT\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_CLAIM\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_COMPILE\"")
            .contains("\"code\": \"MODEL_IMPLEMENTATION_TEST_EVIDENCE_RECORD\"");
    }
}
