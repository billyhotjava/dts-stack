package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DbtImplementationDraftLiquibaseContractTest {

    @Test
    void migrationDefinesTenantScopedCasDraftsAndBoundedFilesWithoutEditingMaster() throws Exception {
        Path migration = Path.of(
            "src/main/resources/config/liquibase/changelog/20260802_02_modeling_dbt_implementation_draft.xml"
        );
        String xml = Files.readString(migration);

        assertThat(xml).contains("modeling_dbt_implementation_draft", "modeling_dbt_implementation_draft_file");
        assertThat(xml).contains("tenant_id", "plan_id", "model_spec_id", "actor_id", "etag");
        assertThat(xml).contains("base_model_revision", "base_model_checksum");
        assertThat(xml).contains("base_implementation_revision", "base_implementation_checksum");
        assertThat(xml).contains("validated_checksum", "implementation_revision", "implementation_checksum");
        assertThat(xml).contains("bundle_checksum", "bundle_manifest", "project_checksum");
        assertThat(xml).contains("uk_modeling_dbt_draft_idempotency", "uk_modeling_dbt_draft_file_path");
        assertThat(xml).contains("idx_modeling_dbt_draft_expiry");
        assertThat(xml).contains("DRAFT", "VALIDATED", "COMMITTING", "COMMITTED");
    }
}
