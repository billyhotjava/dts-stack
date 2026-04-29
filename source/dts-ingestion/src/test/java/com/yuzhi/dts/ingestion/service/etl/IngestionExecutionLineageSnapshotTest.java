package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionExecutionLineageSnapshotTest {

    @Test
    void applyShouldSnapshotSourceAndTargetTablesFromTaskMapping() {
        IngestionTask task = new IngestionTask();
        task.setSourceDataSourceId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgreswriter");

        ArrayNode mappings = JsonNodeFactory.instance.arrayNode();
        ObjectNode mapping = JsonNodeFactory.instance.objectNode();
        mapping.put("source", "erp.project");
        mapping.put("target", "ods.ods_erp_project");
        mappings.add(mapping);
        task.setTableMapping(mappings);

        IngestionExecution execution = new IngestionExecution();

        boolean changed = IngestionExecutionLineageSnapshot.apply(execution, task);

        assertThat(changed).isTrue();
        assertThat(execution.getSourceTables()).hasSize(1);
        assertThat(execution.getSourceTables().get(0).get("namespace").asText()).isEqualTo("erp");
        assertThat(execution.getSourceTables().get(0).get("name").asText()).isEqualTo("project");
        assertThat(execution.getSourceTables().get(0).get("dataSourceId").asText()).isEqualTo("00000000-0000-0000-0000-000000000001");
        assertThat(execution.getTargetTables()).hasSize(1);
        assertThat(execution.getTargetTables().get(0).get("namespace").asText()).isEqualTo("ods");
        assertThat(execution.getTargetTables().get(0).get("name").asText()).isEqualTo("ods_erp_project");
    }
}
