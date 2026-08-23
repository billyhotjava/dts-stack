package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import liquibase.changelog.ChangeLogParameters;
import liquibase.parser.core.xml.XMLChangeLogSAXParser;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class IngestionQualityWorkflowLiquibaseContractTest {

    @Test
    void persistsTheAggregateQualityWorkflowIdentityOnEachExecution() throws Exception {
        String master = resource("/config/liquibase/master.xml");
        String migration = resource(
            "/config/liquibase/changelog/20260822_01_ingestion_quality_workflow_link.xml"
        );

        assertThat(master).contains("20260822_01_ingestion_quality_workflow_link.xml");
        assertThat(migration)
            .contains("author=\"xiezm\"")
            .contains("quality_workflow_id")
            .contains("quality_workflow_status")
            .contains("quality_workflow_attempt_count")
            .contains("quality_workflow_next_retry_at")
            .contains("quality_workflow_error")
            .contains("idx_ingestion_execution_quality_workflow")
            .contains("idx_ingestion_execution_quality_retry")
            .contains("ck_ingestion_execution_quality_attempt_count")
            .contains("<rollback>");

        var parsed = new XMLChangeLogSAXParser()
            .parse(
                "config/liquibase/changelog/20260822_01_ingestion_quality_workflow_link.xml",
                new ChangeLogParameters(),
                new ClassLoaderResourceAccessor()
            );
        assertThat(parsed.getChangeSets()).hasSize(1);
    }

    private String resource(String path) throws Exception {
        try (var input = getClass().getResourceAsStream(path)) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
