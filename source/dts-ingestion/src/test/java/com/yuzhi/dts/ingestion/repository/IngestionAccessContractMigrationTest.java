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
        String reconciliationMigration = Files.readString(
            Path.of("src/main/resources/config/liquibase/changelog/20260801_01_ingestion_admission_dag_reconciliation.xml")
        );
        String secretMigration = Files.readString(
            Path.of("src/main/resources/config/liquibase/changelog/20260801_02_ingestion_task_secret_migration.xml")
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
        assertThat(reconciliationMigration)
            .contains("dag_deployment_status")
            .contains("ck_ingestion_revision_dag_deployment_status")
            .contains("ck_ingestion_revision_dag_deployment_metadata")
            .contains("ADD CONSTRAINT fk_ingestion_execution_task_revision")
            .contains("NOT VALID")
            .contains("VALIDATE CONSTRAINT fk_ingestion_execution_task_revision")
            .contains("DROP INDEX CONCURRENTLY IF EXISTS idx_ingestion_execution_task_revision")
            .contains("CREATE INDEX CONCURRENTLY idx_ingestion_execution_task_revision")
            .contains("WHERE task_revision_id IS NOT NULL")
            .contains("ck_ingestion_task_revision_runtime_snapshot");
        assertThat(reconciliationMigration.indexOf("DROP INDEX CONCURRENTLY IF EXISTS idx_ingestion_execution_task_revision"))
            .isLessThan(reconciliationMigration.indexOf("CREATE INDEX CONCURRENTLY idx_ingestion_execution_task_revision"));
        assertThat(secretMigration)
            .contains("ingestion_task_secret_migration")
            .contains("PENDING")
            .contains("LEGACY_ENCRYPTED")
            .contains("BLOCKED")
            .contains("onDelete=\"CASCADE\"")
            .contains("ON CONFLICT (task_id) DO NOTHING")
            .contains("INGESTION_SECRET_RESTORE_AUDIT_EVIDENCE_IMMUTABLE")
            .contains("INGESTION_SECRET_RESTORE_AUDIT_DELIVERY_TRANSITION_INVALID")
            .contains("INGESTION_SECRET_RESTORE_AUDIT_DELETE_FORBIDDEN")
            .contains("INGESTION_SECRET_RESTORE_AUDIT_TRUNCATE_FORBIDDEN")
            .contains("LOCK TABLE ingestion_secret_restore_audit_outbox IN ACCESS EXCLUSIVE MODE")
            .contains("ROLLBACK_BLOCKED_INGESTION_SECRET_RESTORE_AUDIT_EVIDENCE_EXISTS")
            .doesNotContain("<dropTable tableName=\"ingestion_secret_restore_audit_outbox\"");
        assertThat(master)
            .contains("20260731_01_ingestion_access_contract.xml")
            .contains("20260801_01_ingestion_admission_dag_reconciliation.xml")
            .contains("20260801_02_ingestion_task_secret_migration.xml");
    }
}
