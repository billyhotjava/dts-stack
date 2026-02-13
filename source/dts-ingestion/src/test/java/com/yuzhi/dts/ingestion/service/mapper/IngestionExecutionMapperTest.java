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
        entity.setErrorMessage("permission denied");
        entity.setFailureCategory(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        entity.setFailureAdvice("custom advice");

        IngestionExecutionDTO dto = mapper.toDto(entity);

        assertThat(dto.getFailureCategory()).isEqualTo(ExecutionFailureClassifier.CATEGORY_PERMISSION);
        assertThat(dto.getFailureAdvice()).isEqualTo("custom advice");
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
