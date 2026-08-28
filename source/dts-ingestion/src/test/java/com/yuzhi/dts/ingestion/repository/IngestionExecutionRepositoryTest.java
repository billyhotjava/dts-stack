package com.yuzhi.dts.ingestion.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IngestionExecutionRepositoryTest {

    @Autowired
    private IngestionTaskRepository taskRepository;

    @Autowired
    private IngestionExecutionRepository executionRepository;

    private Long taskId;

    @BeforeEach
    void setUp() {
        IngestionTask task = new IngestionTask();
        task.setName("revision-filter-task");
        task.setSourceType("postgresqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("full_refresh");
        task.setStatus("active");
        task = taskRepository.saveAndFlush(task);
        taskId = task.getId();

        executionRepository.saveAllAndFlush(List.of(
            execution(task, 1, "success", null),
            execution(task, 2, "failed", "PERMISSION_ERROR"),
            execution(task, 2, "success", null)
        ));
    }

    @Test
    void taskRevisionQueryDoesNotLeakOtherVersions() {
        Page<IngestionExecution> result = executionRepository.findByTaskIdAndRevisionNumber(
            taskId,
            2,
            PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).hasSize(2).allMatch(execution -> execution.getRevisionNumber() == 2);
    }

    @Test
    void taskRevisionQueryCombinesStatusAndFailureFilters() {
        Page<IngestionExecution> result = executionRepository.findByTaskIdAndRevisionNumberWithFilters(
            taskId,
            2,
            "FAILED",
            List.of("PERMISSION_ERROR"),
            true,
            PageRequest.of(0, 10)
        );

        assertThat(result.getContent())
            .singleElement()
            .satisfies(execution -> {
                assertThat(execution.getRevisionNumber()).isEqualTo(2);
                assertThat(execution.getStatus()).isEqualTo("failed");
                assertThat(execution.getFailureCategory()).isEqualTo("PERMISSION_ERROR");
            });
    }

    private IngestionExecution execution(IngestionTask task, int revisionNumber, String status, String failureCategory) {
        IngestionExecution execution = new IngestionExecution();
        execution.setTask(task);
        execution.setRevisionNumber(revisionNumber);
        execution.setStatus(status);
        execution.setFailureCategory(failureCategory);
        return execution;
    }
}
