package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowClient;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IngestionExecutionQueryServiceTest {

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private AirflowAdapter airflowAdapter;

    @Mock
    private AirflowClient airflowClient;

    @Mock
    private AirflowDagService airflowDagService;

    private IngestionExecutionQueryService queryService;

    @BeforeEach
    void setup() {
        queryService = new IngestionExecutionQueryService(
            taskRepository,
            executionRepository,
            airflowAdapter,
            airflowClient,
            airflowDagService
        );
    }

    @Test
    void shouldReturnStableFallbackWhenAirflowIsDisabled() {
        IngestionTask task = new IngestionTask();
        task.setId(1L);
        task.setAirflowEnabled(true);
        IngestionExecution execution = new IngestionExecution();
        execution.setId(9L);
        execution.setTask(task);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(executionRepository.findById(9L)).thenReturn(Optional.of(execution));
        when(airflowAdapter.isEnabled()).thenReturn(false);

        Map<String, Object> result = queryService.fetchExecutionLog(1L, 9L, null, null, null);

        assertThat(result)
            .containsEntry("taskId", 1L)
            .containsEntry("executionId", 9L)
            .containsEntry("message", "Airflow 未启用，暂无日志");
    }
}
