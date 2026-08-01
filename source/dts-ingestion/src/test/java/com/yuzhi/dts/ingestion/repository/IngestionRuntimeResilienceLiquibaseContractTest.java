package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class IngestionRuntimeResilienceLiquibaseContractTest {

    private static final String DEPLOYED_20260801_01_SHA256 =
        "0f7384266eb992e5acf64dd0963994231f46efbc6ad6b35a2c77da2f8c23e8a8";

    @Test
    void alreadyExecutedScheduledExecutionChangesetRemainsByteForByteFrozen() throws Exception {
        byte[] changelog = resource("/config/liquibase/changelog/20260801_01_ingestion_admission_dag_reconciliation.xml");

        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(changelog)))
            .isEqualTo(DEPLOYED_20260801_01_SHA256);
        assertThat(new String(changelog, StandardCharsets.UTF_8))
            .contains("20260801-04-ingestion-scheduled-execution-idempotency")
            .doesNotContain("<validCheckSum>ANY</validCheckSum>");
    }

    @Test
    void retryIndexesLiveInANewForwardChangesetIncludedAfterExistingMigrations() throws Exception {
        String master = new String(resource("/config/liquibase/master.xml"), StandardCharsets.UTF_8);
        String forward = new String(
            resource("/config/liquibase/changelog/20260801_05_ingestion_runtime_resilience.xml"),
            StandardCharsets.UTF_8
        );

        assertThat(master.indexOf("20260801_05_ingestion_runtime_resilience.xml"))
            .isGreaterThan(master.indexOf("20260801_04_ingestion_rollback_saga_outbox.xml"));
        int invalidIndexCheck = forward.indexOf("NOT index_state.indisvalid");
        int guardedDrop = forward.indexOf("'DROP INDEX %I.%I'");
        int concurrentCreate = forward.indexOf("CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS");
        assertThat(invalidIndexCheck).isGreaterThanOrEqualTo(0);
        assertThat(guardedDrop).isGreaterThan(invalidIndexCheck);
        assertThat(concurrentCreate).isGreaterThan(guardedDrop);
        assertThat(forward)
            .contains("uk_ingestion_execution_retry_parent")
            .contains("idx_ingestion_execution_retry_due_v2")
            .contains("INGESTION_RETRY_PARENT_DUPLICATES_EXIST")
            .contains("pg_catalog.pg_index", "current_schema()")
            .doesNotContain("<validCheckSum>ANY</validCheckSum>", "clearCheckSums");
    }

    private byte[] resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return input.readAllBytes();
        }
    }
}
