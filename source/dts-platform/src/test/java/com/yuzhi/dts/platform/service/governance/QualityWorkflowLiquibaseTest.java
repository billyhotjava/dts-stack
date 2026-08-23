package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.governance.GovQualityWorkflowRun;
import jakarta.persistence.Version;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class QualityWorkflowLiquibaseTest {

    private static final Path CHANGELOG = Path.of(
        "src/main/resources/config/liquibase/changelog/20260822_01_quality_workflow_run.xml"
    );

    @Test
    void createsTheIdempotentWorkflowLedgerAndIndexesChildRuns() throws Exception {
        String xml = Files.readString(CHANGELOG);

        assertThat(xml)
            .contains("author=\"xiezm\"")
            .contains("gov_quality_workflow_run")
            .contains("idempotency_key")
            .contains("retry_of_id")
            .contains("attempt_no")
            .contains("max_retry_attempts")
            .contains("retry_backoff_seconds")
            .contains("dispatch_failure_count")
            .contains("row_version")
            .contains("uq_gov_quality_workflow_idempotency")
            .contains("idx_gov_quality_workflow_status_created")
            .contains("idx_gov_quality_workflow_task_created")
            .contains("idx_gov_quality_workflow_dataset_created")
            .contains("idx_gov_quality_workflow_retry")
            .contains("uq_gov_quality_workflow_active_task")
            .contains("idx_gov_quality_run_job")
            .contains("QUALITY_WORKFLOW_STATUS_INVALID");

        assertThat(GovQualityWorkflowRun.class.getDeclaredField("rowVersion").isAnnotationPresent(Version.class))
            .isTrue();
    }

    @Test
    void platformMasterIncludesTheWorkflowMigration() throws Exception {
        String master = Files.readString(Path.of("src/main/resources/config/liquibase/master.xml"));

        assertThat(master).contains("20260822_01_quality_workflow_run.xml");
    }
}
