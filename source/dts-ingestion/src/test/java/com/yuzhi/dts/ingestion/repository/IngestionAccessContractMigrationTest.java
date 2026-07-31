package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IngestionAccessContractMigrationTest {

    @Test
    void migrationShouldMarkLegacyRevisionUnsealedWithoutBindingHistoricalExecutions() throws Exception {
        String migration = Files.readString(
            Path.of("src/main/resources/config/liquibase/changelog/20260731_01_ingestion_access_contract.xml")
        );
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));

        assertThat(migration)
            .contains("ingestion_access_default_policy")
            .contains("uk_ingestion_access_policy_active")
            .contains("ingestion_task_revision")
            .contains("uk_ingestion_task_revision_active")
            .contains("revision_number")
            .contains("effective_config_checksum")
            .contains("field_classifications")
            .contains("runtime_snapshot")
            .contains("runtime_snapshot_iv")
            .contains("quality_policy_ref")
            .contains("airflow_dag_id")
            .contains("FROM ingestion_task t")
            .contains("'LEGACY_UNSEALED'")
            .doesNotContain("UPDATE ingestion_execution execution");
        assertThat(master).contains("20260731_01_ingestion_access_contract.xml");
    }
}
