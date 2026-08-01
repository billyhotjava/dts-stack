package com.yuzhi.dts.ingestion.service.etl.rollback;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.etl.AddaxJobService;
import com.yuzhi.dts.ingestion.service.etl.AirflowDagService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.etl.TableOperationService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DataRollbackServiceSagaContractTest {

    private final IngestionTaskRepository taskRepository = mock(IngestionTaskRepository.class);
    private final IngestionExecutionRepository executionRepository = mock(IngestionExecutionRepository.class);
    private final TableOperationService tableOperationService = mock(TableOperationService.class);
    private final ConfirmationPolicy confirmationPolicy = mock(ConfirmationPolicy.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final AddaxJobService addaxJobService = mock(AddaxJobService.class);
    private final AirflowDagService airflowDagService = mock(AirflowDagService.class);
    private final RollbackSagaService sagaService = mock(RollbackSagaService.class);
    private DataRollbackService service;

    @BeforeEach
    void setUp() {
        service = new DataRollbackService(
            taskRepository,
            executionRepository,
            tableOperationService,
            confirmationPolicy,
            fileUploadService,
            addaxJobService,
            airflowDagService,
            sagaService
        );
    }

    @Test
    void executeRejectsDryRunBeforeResolvingOrMutatingAnything() {
        RollbackRequest request = request(3, true, true);
        assertThatThrownBy(() -> service.execute(request, "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ROLLBACK_EXECUTE_DRY_RUN_FORBIDDEN");
        verifyNoInteractions(taskRepository, tableOperationService, sagaService);
    }

    @Test
    void everyDestructiveLevelRequiresACommittedPlatformFence() {
        RollbackRequest unfenced = request(1, false, false);
        assertThatThrownBy(() -> service.execute(unfenced, "operator"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ROLLBACK_PREPARED_FENCE_REQUIRED");
        verifyNoInteractions(taskRepository, tableOperationService, sagaService);
    }

    @Test
    void levelOneReceiptIsPersistedBeforeTheFirstPhysicalTruncate() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        IngestionTask task = new IngestionTask();
        task.setId(41L);
        task.setStatus("active");
        task.setSourceDataSourceId(sourceId);
        task.setSourceType("mysqlreader");
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(tableOperationService.resolveTargetTables(task)).thenReturn(List.of("public.ods_orders"));
        when(executionRepository.countByTaskIdAndStatusesIgnoreCase(any(), any())).thenReturn(0L);
        when(confirmationPolicy.confirmationType(RollbackLevel.TRUNCATE_DATA)).thenReturn("TEXT_CONFIRM");
        when(sagaService.begin(any(), any(), any(), any()))
            .thenReturn(new RollbackSagaService.BeginDecision(true, null));
        RollbackResult committed = new RollbackResult(
            true,
            List.of("TRUNCATED: public.ods_orders"),
            List.of(),
            List.of(41L)
        );
        when(sagaService.complete(any(), any(), any(), any(), any())).thenReturn(committed);

        service.execute(request(1, false, true), "operator");

        InOrder order = inOrder(sagaService, tableOperationService);
        order.verify(sagaService).begin(any(), any(), any(), any());
        order.verify(tableOperationService).truncateTable(any(), any(), any());
        order.verify(sagaService).complete(any(), any(), any(), any(), any());
    }

    @Test
    void sagaReceiptIsPersistedBeforeTheFirstPhysicalDrop() {
        UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        IngestionTask task = new IngestionTask();
        task.setId(41L);
        task.setStatus("active");
        task.setSourceDataSourceId(sourceId);
        task.setSourceType("mysqlreader");
        when(taskRepository.findById(41L)).thenReturn(Optional.of(task));
        when(tableOperationService.resolveTargetTables(task)).thenReturn(List.of("public.ods_orders"));
        when(executionRepository.countByTaskIdAndStatusesIgnoreCase(any(), any())).thenReturn(0L);
        when(confirmationPolicy.confirmationType(RollbackLevel.REBUILD_SCHEMA)).thenReturn("TEXT_CONFIRM");
        when(sagaService.begin(any(), any(), any(), any()))
            .thenReturn(new RollbackSagaService.BeginDecision(true, null));
        RollbackResult committed = new RollbackResult(true, List.of("DROPPED: public.ods_orders"), List.of(), List.of(41L));
        when(sagaService.complete(any(), any(), any(), any(), any())).thenReturn(committed);

        service.execute(request(2, false, true), "operator");

        InOrder order = inOrder(sagaService, tableOperationService);
        order.verify(sagaService).begin(any(), any(), any(), any());
        order.verify(tableOperationService).dropTable(any(), any(), any());
        verify(executionRepository, never()).deleteByTaskId(any());
    }

    @Test
    void runtimeSourceContainsNoHistoryDeletionOrDirectDbtTail() throws Exception {
        Path module = Path.of("src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java");
        Path repository = Path.of(
            "source/dts-ingestion/src/main/java/com/yuzhi/dts/ingestion/service/etl/rollback/DataRollbackService.java"
        );
        String source = Files.readString(Files.exists(module) ? module : repository, StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(source)
            .doesNotContain("deleteByTaskId")
            .doesNotContain("clearCheckpointByTaskId")
            .doesNotContain("deleteByTaskId(taskId)")
            .doesNotContain("rebuildDbt")
            .doesNotContain("dbtFullRefreshNeeded");
    }

    private RollbackRequest request(int level, boolean dryRun, boolean fenced) {
        UUID receipt = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID source = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return new RollbackRequest(
            level,
            "task",
            41L,
            null,
            List.of(),
            dryRun,
            fenced ? receipt : null,
            fenced ? "platform:" + receipt : null,
            fenced ? "0".repeat(64) : null,
            fenced ? new RollbackRequest.AvailabilityFence(receipt, source, 7L, "PREPARED", 1) : null
        );
    }
}
