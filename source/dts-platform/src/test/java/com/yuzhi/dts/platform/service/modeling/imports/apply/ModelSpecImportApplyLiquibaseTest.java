package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyLiquibaseTest {

    private static final String CHANGELOG =
        "/config/liquibase/changelog/20260725_04_model_spec_import_apply.xml";
    private static final String LEASE_CHANGELOG =
        "/config/liquibase/changelog/20260725_05_model_spec_import_apply_lease.xml";

    @Test
    void definesAttemptResultIdempotencyRetryAndFrozenClosureControlPlane() throws Exception {
        String xml = read(CHANGELOG);
        var document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(requiredStream(CHANGELOG));

        assertThat(xml)
            .contains("modeling_model_spec_import_apply_attempt")
            .contains("modeling_model_spec_import_apply_result")
            .contains("retry_source_attempt_id")
            .contains("uk_model_spec_import_apply_attempt_scope")
            .contains("baseColumnNames=\"tenant_id,run_id,retry_source_attempt_id\"")
            .contains("referencedColumnNames=\"tenant_id,run_id,id\"")
            .contains("selected_unique_ids")
            .contains("selected_closure_json")
            .contains("idempotency_key")
            .contains("request_hash")
            .contains("candidate_idempotency_key")
            .contains("candidate_request_hash")
            .contains("RUNNING', 'SUCCEEDED', 'PARTIAL', 'FAILED")
            .contains("CREATED', 'UPDATED', 'SKIPPED', 'REPLAYED', 'FAILED', 'BLOCKED")
            .contains("uk_model_spec_import_apply_idempotency")
            .contains("uk_model_spec_import_apply_run_attempt")
            .contains("uk_model_spec_import_apply_running")
            .contains("ON modeling_model_spec_import_apply_attempt (tenant_id, run_id)")
            .contains("WHERE status = 'RUNNING'")
            .contains("uk_model_spec_import_apply_result_candidate")
            .contains("idx_model_spec_import_apply_result_retry");
        assertThat(document.getElementsByTagName("createTable").getLength()).isEqualTo(2);
    }

    @Test
    void migratesDbtSlotToFullRevisionAndNodeIdentityAndBlocksRollback() throws IOException {
        String xml = read(CHANGELOG);

        assertThat(xml)
            .contains("dropIndex")
            .contains("uk_modeling_dbt_artifact_dbt_slot")
            .contains(
                "model_spec_id,\n" +
                "                    revision,\n" +
                "                    implementation_revision,\n" +
                "                    project_key,\n" +
                "                    dbt_unique_id,\n" +
                "                    node_kind,\n" +
                "                    artifact_type"
            )
            .contains("WHERE ownership = 'DBT_MANAGED'")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_IMPORT_APPLY_FORWARD_ONLY");
    }

    @Test
    void masterIncludesApplyChangelogExactlyOnce() throws IOException {
        String master = read("/config/liquibase/master.xml");

        assertThat(master.split("20260725_04_model_spec_import_apply.xml", -1)).hasSize(2);
        assertThat(master.split("20260725_05_model_spec_import_apply_lease.xml", -1)).hasSize(2);
    }

    @Test
    void addsInternalOwnerLeaseWithoutChangingTheApplyResponseContract() throws IOException {
        String xml = read(LEASE_CHANGELOG);

        assertThat(xml)
            .contains("owner_token")
            .contains("lease_expires_at")
            .contains("idx_model_spec_import_apply_lease")
            .contains("ck_model_spec_import_apply_lease")
            .contains("status = 'RUNNING'")
            .contains("ROLLBACK_BLOCKED_MODEL_SPEC_IMPORT_APPLY_LEASE_FORWARD_ONLY");
    }

    private static InputStream requiredStream(String resource) {
        InputStream stream = ModelSpecImportApplyLiquibaseTest.class.getResourceAsStream(resource);
        assertThat(stream).as("Liquibase resource %s", resource).isNotNull();
        return stream;
    }

    private static String read(String resource) throws IOException {
        try (InputStream stream = requiredStream(resource)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
