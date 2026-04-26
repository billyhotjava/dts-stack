package com.yuzhi.dts.ingestion.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import org.junit.jupiter.api.Test;

class IngestionTaskMapperTest {

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
    void partialUpdateShouldApplyPreCheckFields() {
        IngestionTask task = new IngestionTask();
        task.setQualityPreCheckEnabled(false);
        task.setStagingTableName("old_staging");
        task.setPreCheckStatus("PENDING");

        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setQualityPreCheckEnabled(true);
        dto.setStagingTableName("new_staging");
        dto.setPreCheckStatus("PASSED");

        mapper.partialUpdate(task, dto);

        assertThat(task.getQualityPreCheckEnabled()).isTrue();
        assertThat(task.getStagingTableName()).isEqualTo("new_staging");
        assertThat(task.getPreCheckStatus()).isEqualTo("PASSED");
    }
}
