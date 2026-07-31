package com.yuzhi.dts.ingestion.service.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.ExecutionFailureClassifier;
import org.junit.jupiter.api.Test;

class IngestionExecutionMapperTest {

    private final IngestionExecutionMapper mapper = new IngestionExecutionMapper();

    @Test
    void toDto_shouldKeepPersistedFailureClassification() {
        IngestionExecution entity = new IngestionExecution();
        entity.setBatchId("batch-task-1-test");
        entity.setErrorMessage("permission denied");
        entity.setFailureCategory(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        entity.setFailureAdvice("custom advice");

        IngestionExecutionDTO dto = mapper.toDto(entity);

        assertThat(dto.getBatchId()).isEqualTo("batch-task-1-test");
        assertThat(dto.getFailureCategory()).isEqualTo(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        assertThat(dto.getFailureAdvice()).isEqualTo("custom advice");
    }

    @Test
    void toEntity_shouldMapBatchId() {
        IngestionExecutionDTO dto = new IngestionExecutionDTO();
        dto.setBatchId("batch-task-2-test");
        dto.setRevisionNumber(3);
        dto.setEffectiveConfigChecksum("checksum-3");
        dto.setQualityPolicyRef("dataset:00000000-0000-0000-0000-000000000003");
        dto.setQualityRunId("quality-run-3");

        IngestionExecution entity = mapper.toEntity(dto);

        assertThat(entity.getBatchId()).isEqualTo("batch-task-2-test");
        assertThat(entity.getRevisionNumber()).isEqualTo(3);
        assertThat(entity.getEffectiveConfigChecksum()).isEqualTo("checksum-3");
        assertThat(entity.getQualityPolicyRef()).isEqualTo("dataset:00000000-0000-0000-0000-000000000003");
        assertThat(entity.getQualityRunId()).isEqualTo("quality-run-3");
    }

    @Test
    void toDto_shouldFallbackToDerivedFailureClassificationWhenMissingPersistedFields() {
        IngestionExecution entity = new IngestionExecution();
        entity.setErrorMessage("permission denied");

        IngestionExecutionDTO dto = mapper.toDto(entity);

        assertThat(dto.getFailureCategory()).isEqualTo(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        assertThat(dto.getFailureAdvice()).isEqualTo(ExecutionFailureClassifier.advice(ExecutionFailureClassifier.CATEGORY_PERMISSION));
    }
}
