package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateLiquibaseTest {

    private static final String CHANGELOG = "/config/liquibase/changelog/20260724_03_model_release_candidate.xml";

    @Test
    void createsTenantScopedCandidateHeaderAndOrderedEntries() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("tableName=\"modeling_model_release_candidate\"")
            .contains("tableName=\"modeling_model_release_candidate_entry\"")
            .contains("columnNames=\"tenant_id, id\"")
            .contains("constraintName=\"uk_model_release_candidate_tenant_id\"")
            .contains("columnNames=\"tenant_id, id, plan_id\"")
            .contains("constraintName=\"uk_model_release_candidate_scope\"")
            .contains("name=\"plan_id\"")
            .contains("columnNames=\"tenant_id, candidate_id, model_spec_id\"")
            .contains("constraintName=\"uk_model_release_candidate_entry_model\"")
            .contains("columnNames=\"tenant_id, candidate_id, sort_order\"")
            .contains("constraintName=\"uk_model_release_candidate_entry_order\"")
            .contains("idx_model_release_candidate_plan_status")
            .contains("idx_model_release_candidate_history")
            .contains("uk_model_release_candidate_idempotency")
            .contains("ck_model_release_candidate_status")
            .contains("ck_model_release_candidate_audit")
            .contains("ck_model_release_candidate_entry_payload")
            .contains("idempotency_key")
            .contains("request_hash")
            .contains("request_hash IS NOT NULL")
            .contains("sort_order >= 0")
            .contains("checksum ~ '^[0-9a-f]{64}$'");
    }

    @Test
    void pinsCandidateModelRevisionChecksumAndOptionalImplementationScopeWithoutCascade() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("constraintName=\"uk_model_spec_release_scope\"")
            .contains("columnNames=\"tenant_id, plan_id, id, implementation_mode\"")
            .contains("constraintName=\"uk_model_spec_revision_release_scope\"")
            .contains("columnNames=\"tenant_id, model_spec_id, revision, content_checksum\"")
            .contains("constraintName=\"uk_modeling_implementation_release_scope\"")
            .contains(
                "columnNames=\"tenant_id, id, plan_id, model_spec_id, model_revision, model_checksum, ownership\""
            )
            .contains("baseTableName=\"modeling_model_release_candidate\"")
            .contains("baseColumnNames=\"tenant_id, plan_id\"")
            .contains("referencedTableName=\"modeling_warehouse_plan\"")
            .contains("baseTableName=\"modeling_model_release_candidate_entry\"")
            .contains("baseColumnNames=\"tenant_id, candidate_id, plan_id\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_candidate_scope\"")
            .contains("referencedTableName=\"modeling_model_release_candidate\"")
            .contains("baseColumnNames=\"tenant_id, plan_id, model_spec_id, implementation_mode\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_model_scope\"")
            .contains("referencedTableName=\"modeling_model_spec\"")
            .contains("referencedColumnNames=\"tenant_id, plan_id, id, implementation_mode\"")
            .contains("baseColumnNames=\"tenant_id, model_spec_id, revision, checksum\"")
            .contains("constraintName=\"fk_model_release_candidate_entry_revision\"")
            .contains("referencedColumnNames=\"tenant_id, model_spec_id, revision, content_checksum\"")
            .contains(
                "baseColumnNames=\"tenant_id, implementation_id, plan_id, model_spec_id, revision, checksum, implementation_mode\""
            )
            .contains("constraintName=\"fk_model_release_candidate_entry_implementation\"")
            .contains(
                "referencedColumnNames=\"tenant_id, id, plan_id, model_spec_id, model_revision, model_checksum, ownership\""
            )
            .contains("idx_model_release_candidate_entry_candidate_scope")
            .contains("idx_model_release_candidate_entry_model_scope")
            .contains("idx_model_release_candidate_entry_revision")
            .contains("idx_model_release_candidate_entry_implementation")
            .contains("onDelete=\"RESTRICT\"")
            .doesNotContain("onDelete=\"CASCADE\"")
            .doesNotContain("cascadeConstraints=\"true\"");
    }

    @Test
    void enforcesStatusAuditCompletenessChronologyAndSeparation() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("submitted_date >= created_date")
            .contains("approved_date >= submitted_date")
            .contains("published_date >= approved_date")
            .contains("btrim(submitted_by) <> btrim(approved_by)")
            .contains("btrim(submitted_by) <> btrim(published_by)")
            .contains("environment = btrim(environment)")
            .contains("created_by = btrim(created_by)")
            .contains("last_modified_by = btrim(last_modified_by)")
            .contains("idempotency_key = btrim(idempotency_key)")
            .contains("submitted_by = btrim(submitted_by)")
            .contains("approved_by = btrim(approved_by)")
            .contains("published_by = btrim(published_by)")
            .contains("'REVIEW_PENDING', 'REJECTED', 'APPROVED', 'PUBLISHING'")
            .contains("status NOT IN ('APPROVED', 'PUBLISHING', 'PARTIAL', 'PUBLISHED', 'ROLLED_BACK')")
            .contains("status NOT IN ('PARTIAL', 'PUBLISHED', 'ROLLED_BACK')")
            .contains("status = 'STALE'");
    }

    @Test
    void includesChangeAfterConcurrentDimensionAndImplementationChanges() throws Exception {
        String master = read("/config/liquibase/master.xml");

        assertThat(master)
            .containsSubsequence(
                "config/liquibase/changelog/20260724_01_dimension_definition.xml",
                "config/liquibase/changelog/20260724_02_model_implementation_inputs.xml",
                "config/liquibase/changelog/20260724_03_model_release_candidate.xml"
            );
    }

    @Test
    void permitsEmptyRollbackButRejectsSilentDataLoss() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_DATA_EXISTS")
            .contains("IN ACCESS EXCLUSIVE MODE")
            .contains("IF EXISTS (SELECT 1 FROM modeling_model_release_candidate_entry)")
            .contains("IF EXISTS (SELECT 1 FROM modeling_model_release_candidate)")
            .containsSubsequence(
                "dropTable tableName=\"modeling_model_release_candidate_entry\"",
                "dropTable tableName=\"modeling_model_release_candidate\"",
                "constraintName=\"uk_modeling_implementation_release_scope\"",
                "constraintName=\"uk_model_spec_revision_release_scope\"",
                "constraintName=\"uk_model_spec_release_scope\""
            )
            .doesNotContain("Forward-only");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
