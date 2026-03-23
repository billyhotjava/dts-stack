package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class IngestionTaskQueryServiceTest {

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private IngestionTaskMapper taskMapper;

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private IncrementalSyncService incrementalSyncService;

    private IngestionTaskQueryService queryService;

    @BeforeEach
    void setup() {
        queryService = new IngestionTaskQueryService(
            taskRepository,
            executionRepository,
            taskMapper,
            incrementalSyncService
        );
    }

    @Test
    void shouldFindAllTasksByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        IngestionTask entity = createTask(1L, "demo-task");
        IngestionTaskDTO dto = createTaskDto(1L, "demo-task");
        Page<IngestionTask> page = new PageImpl<>(List.of(entity), pageable, 1);

        when(taskRepository.findByStatus("active", pageable)).thenReturn(page);
        when(taskMapper.toDto(entity)).thenReturn(dto);

        Page<IngestionTaskDTO> result = queryService.findAll("active", pageable);

        assertThat(result.getContent()).containsExactly(dto);
    }

    @Test
    void shouldFindTasksBySourceDataSourceIdWithoutDeletedTasks() {
        UUID sourceId = UUID.randomUUID();
        IngestionTask active = createTask(1L, "active-task");
        active.setSourceDataSourceId(sourceId);
        active.setStatus("draft");
        IngestionTask deleted = createTask(2L, "deleted-task");
        deleted.setSourceDataSourceId(sourceId);
        deleted.setStatus("deleted");

        IngestionTaskDTO activeDto = createTaskDto(1L, "active-task");
        when(taskRepository.findBySourceDataSourceId(sourceId)).thenReturn(List.of(active, deleted));
        when(taskMapper.toDto(active)).thenReturn(activeDto);

        List<IngestionTaskDTO> result = queryService.findBySourceDataSourceId(sourceId, false);

        assertThat(result).containsExactly(activeDto);
    }

    @Test
    void shouldFindTaskById() {
        IngestionTask entity = createTask(1L, "demo-task");
        IngestionTaskDTO dto = createTaskDto(1L, "demo-task");
        when(taskRepository.findById(1L)).thenReturn(Optional.of(entity));
        when(taskMapper.toDto(entity)).thenReturn(dto);

        Optional<IngestionTaskDTO> result = queryService.findOne(1L);

        assertThat(result).contains(dto);
    }

    @Test
    void shouldRejectIncrementalStateQueryWhenTaskIsMissing() {
        when(taskRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> queryService.getIncrementalStates(99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Task not found: 99");
    }

    @Test
    void shouldReturnIncrementalStatesForExistingTask() {
        IngestionIncrementalStateDTO state = new IngestionIncrementalStateDTO();
        state.setTaskId(7L);
        state.setSourceTable("ods_demo");
        when(taskRepository.existsById(7L)).thenReturn(true);
        when(incrementalSyncService.listCheckpointStates(7L)).thenReturn(List.of(state));

        List<IngestionIncrementalStateDTO> result = queryService.getIncrementalStates(7L);

        assertThat(result).containsExactly(state);
    }

    private IngestionTask createTask(Long id, String name) {
        IngestionTask task = new IngestionTask();
        task.setId(id);
        task.setName(name);
        return task;
    }

    private IngestionTaskDTO createTaskDto(Long id, String name) {
        IngestionTaskDTO dto = new IngestionTaskDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }
}
