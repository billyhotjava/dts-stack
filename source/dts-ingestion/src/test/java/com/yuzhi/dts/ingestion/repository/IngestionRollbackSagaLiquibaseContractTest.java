package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IngestionRollbackSagaLiquibaseContractTest {

    @Test
    void changelogIsFailClosedAppendOnlyAndForwardOnly() throws Exception {
        String sagaChangelog = resource(
            "/config/liquibase/changelog/20260801_04_ingestion_rollback_saga_outbox.xml"
        );
        String truncateGuardChangelog = resource(
            "/config/liquibase/changelog/20260801_06_ingestion_rollback_truncate_guards.xml"
        );
        String master = resource("/config/liquibase/master.xml");
        String focusedTestChangelog = resource("/config/liquibase/ingestion-rollback-saga-postgres-it.xml");

        assertThat(sagaChangelog)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("ingestion_rollback_operation")
            .contains("level in (1, 2, 3)")
            .contains("ingestion_rollback_affected_object")
            .contains("ingestion_rollback_outbox")
            .contains("ROLLBACK_EVIDENCE_APPEND_ONLY")
            .contains("<validCheckSum>9:74731bf50dea3b48c98c65fcfbb7a033</validCheckSum>")
            .doesNotContain("<validCheckSum>ANY</validCheckSum>", "clearCheckSums")
            .doesNotContain("trg_rollback_audit_no_truncate", "trg_ingestion_rollback_evidence_no_truncate")
            .contains("ROLLBACK_BLOCKED_INGESTION_ROLLBACK_SAGA_FORWARD_ONLY");
        assertThat(truncateGuardChangelog)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("trg_rollback_audit_no_truncate")
            .contains("trg_ingestion_rollback_evidence_no_truncate")
            .contains("BEFORE TRUNCATE ON rollback_audit_log")
            .contains("BEFORE TRUNCATE ON ingestion_rollback_affected_object")
            .contains("INGESTION_ROLLBACK_TRUNCATE_GUARD_CONFLICT")
            .contains("ROLLBACK_BLOCKED_INGESTION_ROLLBACK_TRUNCATE_GUARDS_FORWARD_ONLY");
        assertOrdered(
            master,
            "20260801_05_ingestion_runtime_resilience.xml",
            "20260801_06_ingestion_rollback_truncate_guards.xml"
        );
        assertOrdered(
            focusedTestChangelog,
            "20260801_04_ingestion_rollback_saga_outbox.xml",
            "20260801_06_ingestion_rollback_truncate_guards.xml"
        );
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).as(path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void assertOrdered(String document, String first, String second) {
        assertThat(document).contains(first, second);
        assertThat(document.indexOf(second)).isGreaterThan(document.indexOf(first));
    }
}
