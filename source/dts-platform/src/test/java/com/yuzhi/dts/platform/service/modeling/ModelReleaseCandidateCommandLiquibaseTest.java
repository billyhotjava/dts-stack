package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ModelReleaseCandidateCommandLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260724_04_model_release_candidate_command.xml";

    @Test
    void storesAppendOnlyAuditAndTenantScopedIdempotencyReceipts() throws Exception {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("tableName=\"modeling_model_release_candidate_command\"")
            .contains("constraintName=\"uk_model_release_candidate_command_idempotency\"")
            .contains("columnNames=\"tenant_id, idempotency_key\"")
            .contains("constraintName=\"uk_model_release_candidate_command_version\"")
            .contains("columnNames=\"tenant_id, candidate_id, candidate_version\"")
            .contains("constraintName=\"fk_model_release_candidate_command_candidate\"")
            .contains("baseColumnNames=\"tenant_id, candidate_id, plan_id\"")
            .contains("event_type IN ('CREATED', 'SCOPE_REPLACED', 'STATUS_CHANGED', 'STALE_DETECTED')")
            .contains("event_type = 'CREATED' AND from_status IS NULL")
            .contains("request_hash ~ '^[0-9a-f]{64}$'")
            .contains("jsonb_typeof(response_snapshot) = 'object'")
            .contains("trg_model_release_candidate_command_append_only")
            .contains("BEFORE UPDATE OR DELETE")
            .contains("trg_model_release_candidate_command_no_truncate")
            .contains("BEFORE TRUNCATE")
            .contains("MODEL_RELEASE_CANDIDATE_COMMAND_APPEND_ONLY")
            .contains("onDelete=\"RESTRICT\"")
            .doesNotContain("onDelete=\"CASCADE\"");
    }

    @Test
    void followsCandidateSchemaAndBlocksRollbackDataLoss() throws Exception {
        String master = read("/config/liquibase/master.xml");
        String xml = read(CHANGELOG);

        assertThat(master)
            .containsSubsequence(
                "config/liquibase/changelog/20260724_03_model_release_candidate.xml",
                "config/liquibase/changelog/20260724_04_model_release_candidate_command.xml"
            );
        assertThat(xml)
            .contains("ROLLBACK_BLOCKED_MODEL_RELEASE_CANDIDATE_COMMAND_DATA_EXISTS")
            .contains("LOCK TABLE modeling_model_release_candidate_command IN ACCESS EXCLUSIVE MODE")
            .contains("IF EXISTS (SELECT 1 FROM modeling_model_release_candidate_command)")
            .contains("dropTable tableName=\"modeling_model_release_candidate_command\"");
    }

    private String read(String resource) throws Exception {
        try (var input = getClass().getResourceAsStream(resource)) {
            assertThat(input).as(resource).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
