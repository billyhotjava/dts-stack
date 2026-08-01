package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IngestionDirectDbtRetirementContractTest {

    @Test
    void runtimeHasNoDirectDbtTriggerOrRetiredTaskConfiguration() throws Exception {
        assertThat(source("service/etl/AirflowExecutionSyncService.java"))
            .doesNotContain("triggerDbtIfConfigured")
            .doesNotContain("INGESTION_TASK_DBT_TRIGGER");
        assertThat(source("service/infra/PlatformInfraClient.java"))
            .doesNotContain("triggerDbtRun")
            .doesNotContain("/etl/dbt/run");
        assertThat(source("domain/IngestionTask.java"))
            .doesNotContain("dbtModelSelector")
            .doesNotContain("dbtDagSelector");
        assertThat(source("service/dto/IngestionTaskDTO.java"))
            .doesNotContain("getDbtModelSelector", "setDbtModelSelector", "getDbtDagSelector", "setDbtDagSelector")
            .contains("@JsonAnySetter")
            .contains("DIRECT_DBT_SELECTOR_RETIRED");
    }

    @Test
    void migrationFailsClosedForConfiguredModelSelectorsAndIsForwardOnly() throws Exception {
        String changelog = resource("20260801_03_retire_ingestion_task_direct_dbt.xml");
        assertThat(changelog)
            .contains("onFail=\"HALT\"")
            .contains("onError=\"HALT\"")
            .contains("nullif(btrim(dbt_model_selector), '') IS NOT NULL")
            .contains("nullif(btrim(dbt_dag_selector), '') IS NOT NULL")
            .contains("ROLLBACK_BLOCKED_INGESTION_DIRECT_DBT_RETIREMENT_FORWARD_ONLY")
            .doesNotContain("cascadeConstraints=\"true\"");
    }

    private String source(String relativePath) throws Exception {
        Path module = Path.of("src/main/java/com/yuzhi/dts/ingestion", relativePath);
        Path repository = Path.of("source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion", relativePath);
        return Files.readString(Files.exists(module) ? module : repository, StandardCharsets.UTF_8);
    }

    private String resource(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/config/liquibase/changelog/" + name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
