package com.yuzhi.dts.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.audit.AuditService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowAdapter;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.DagPreheatService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.IncrementalSyncService;
import com.yuzhi.dts.ingestion.service.etl.IngestionRetryService;
import com.yuzhi.dts.ingestion.service.etl.TargetTableProvisioner;
import com.yuzhi.dts.ingestion.service.etl.api.ApiIngestionExecutor;
import com.yuzhi.dts.ingestion.service.etl.connector.SourceConnectorRegistry;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.mapper.IngestionExecutionMapper;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskMapper;
import java.util.List;
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
class IngestionTaskExecutionFilterTest {

    @Mock
    private IngestionTaskRepository taskRepository;

    @Mock
    private IngestionExecutionRepository executionRepository;

    @Mock
    private IngestionTaskMapper taskMapper;

    @Mock
    private IngestionExecutionMapper executionMapper;

    @Mock
    private AddaxJobService addaxJobService;

    @Mock
    private AirflowAdapter airflowAdapter;

    @Mock
    private com.yuzhi.dts.ingestion.service.etl.AirflowClient airflowClient;

    @Mock
    private AirflowDagService airflowDagService;

    @Mock
    private com.yuzhi.dts.ingestion.service.etl.IngestionSourceResolver sourceResolver;

    @Mock
    private TargetTableProvisioner targetTableProvisioner;

    @Mock
    private IncrementalSyncService incrementalSyncService;

    @Mock
    private AuditService auditService;

    @Mock
    private IngestionTaskChangeLogService changeLogService;

    @Mock
    private ExcelParseService excelParseService;

    @Mock
    private FileUploadService fileUploadService;

    @Mock
    private CsvParseService csvParseService;

    @Mock
    private IngestionRetryService retryService;

    @Mock
    private DagPreheatService dagPreheatService;

    @Mock
    private PlatformInfraClient platformInfraClient;

    @Mock
    private SourceConnectorRegistry sourceConnectorRegistry;

    @Mock
    private ApiIngestionExecutor apiIngestionExecutor;

    @Mock
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private IngestionTaskService ingestionTaskService;

    @BeforeEach
    void setUp() {
        ingestionTaskService = new IngestionTaskService(
            taskRepository,
            executionRepository,
            taskMapper,
            executionMapper,
            addaxJobService,
            airflowAdapter,
            airflowClient,
            airflowDagService,
            sourceResolver,
            targetTableProvisioner,
            incrementalSyncService,
            auditService,
            changeLogService,
            excelParseService,
            fileUploadService,
            csvParseService,
            retryService,
            dagPreheatService,
            platformInfraClient,
            sourceConnectorRegistry,
            apiIngestionExecutor,
            transactionManager,
            command -> command.run()
        );
    }

    @Test
    void getExecutions_shouldApplyStatusAndFailureCategoryFilter() {
        Long taskId = 1L;
        Pageable pageable = PageRequest.of(0, 20);
        Page<IngestionExecution> page = new PageImpl<>(List.of(new IngestionExecution()), pageable, 1);

        when(executionRepository.findByTaskIdAndStatusIgnoreCaseAndFailureCategoryIgnoreCase(
            taskId,
            "failed",
            "PERMISSION_ERROR",
            pageable
        )).thenReturn(page);
        when(executionMapper.toDto(org.mockito.ArgumentMatchers.any(IngestionExecution.class))).thenReturn(new IngestionExecutionDTO());

        Page<IngestionExecutionDTO> result = ingestionTaskService.getExecutions(taskId, pageable, "failed", "PERMISSION_ERROR");

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(executionRepository).findByTaskIdAndStatusIgnoreCaseAndFailureCategoryIgnoreCase(
            eq(taskId),
            eq("failed"),
            eq("PERMISSION_ERROR"),
            eq(pageable)
        );
    }
}
