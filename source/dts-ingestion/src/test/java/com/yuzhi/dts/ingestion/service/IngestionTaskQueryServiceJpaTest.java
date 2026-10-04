package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskDTO;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({ IngestionTaskQueryService.class, IngestionTaskMapper.class })
class IngestionTaskQueryServiceJpaTest {

    @Autowired
    private IngestionTaskRepository taskRepository;

    @Autowired
    private IngestionTaskQueryService queryService;

    @MockBean
    private IncrementalSyncService incrementalSyncService;

    @MockBean
    private IngestionAccessContractService accessContractService;

    @BeforeEach
    void setUp() {
        taskRepository.save(task("active-plan", "active"));
        taskRepository.save(task("deleted-plan", "deleted"));
        taskRepository.flush();
    }

    @Test
    void defaultListExcludesSoftDeletedTasks() {
        Page<IngestionTaskDTO> result = queryService.findAll(null, PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(IngestionTaskDTO::getName).containsExactly("active-plan");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void deletedStatusStillListsRetiredTasks() {
        Page<IngestionTaskDTO> result = queryService.findAll("deleted", PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(IngestionTaskDTO::getName).containsExactly("deleted-plan");
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    private IngestionTask task(String name, String status) {
        IngestionTask task = new IngestionTask();
        task.setName(name);
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("full_refresh");
        task.setStatus(status);
        return task;
    }
}
