package com.yuzhi.dts.ingestion.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import org.junit.jupiter.api.Test;

class IngestionTaskMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IngestionTaskMapper mapper = new IngestionTaskMapper();

    @Test
    void shouldMapPreCheckFieldsBothWays() {
        IngestionTask task = new IngestionTask();
        task.setId(1L);
        task.setName("file-import");
        task.setSourceType("excelreader");
        task.setSyncMode("full_refresh");
        task.setQualityPreCheckEnabled(true);
        task.setStagingTableName("tmp_ingestion_1234567890abcdef1234567890abcdef");
        task.setPreCheckStatus("FAILED");

        IngestionTaskDTO dto = mapper.toDto(task);

        assertThat(dto.getQualityPreCheckEnabled()).isTrue();
        assertThat(dto.getStagingTableName()).isEqualTo("tmp_ingestion_1234567890abcdef1234567890abcdef");
        assertThat(dto.getPreCheckStatus()).isEqualTo("FAILED");

        IngestionTask entity = mapper.toEntity(dto);

        assertThat(entity.getQualityPreCheckEnabled()).isTrue();
        assertThat(entity.getStagingTableName()).isEqualTo("tmp_ingestion_1234567890abcdef1234567890abcdef");
        assertThat(entity.getPreCheckStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldMapGraphDslBothWays() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setId(2L);
        task.setName("visual-flow");
        task.setSourceType("mysql");
        task.setSyncMode("full_refresh");
        task.setGraphDsl(objectMapper.readTree("{\"dslVersion\":\"1.0\",\"nodes\":[],\"edges\":[],\"viewport\":{\"x\":0,\"y\":0,\"zoom\":1}}"));

        IngestionTaskDTO dto = mapper.toDto(task);

        assertThat(dto.getGraphDsl()).isEqualTo(task.getGraphDsl());

        IngestionTask entity = mapper.toEntity(dto);

        assertThat(entity.getGraphDsl()).isEqualTo(task.getGraphDsl());
    }

    @Test
    void partialUpdateShouldApplyPreCheckFields() {
        IngestionTask task = new IngestionTask();
        task.setQualityPreCheckEnabled(false);
        task.setStagingTableName("old_staging");
        task.setPreCheckStatus("PENDING");
        task.setGraphDsl(objectMapper.createObjectNode().put("dslVersion", "0.9"));

        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setQualityPreCheckEnabled(true);
        dto.setStagingTableName("new_staging");
        dto.setPreCheckStatus("PASSED");
        dto.setGraphDsl(objectMapper.createObjectNode().put("dslVersion", "1.0"));

        mapper.partialUpdate(task, dto);

        assertThat(task.getQualityPreCheckEnabled()).isTrue();
        assertThat(task.getStagingTableName()).isEqualTo("new_staging");
        assertThat(task.getPreCheckStatus()).isEqualTo("PASSED");
        assertThat(task.getGraphDsl().path("dslVersion").asText()).isEqualTo("1.0");
    }
}
