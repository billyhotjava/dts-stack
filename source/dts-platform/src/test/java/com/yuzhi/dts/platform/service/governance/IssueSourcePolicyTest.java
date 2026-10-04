package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovIssueTicket;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class IssueSourcePolicyTest {

    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000096");
    private static final UUID SOURCE_DATASET_ID = UUID.fromString("20000000-0000-0000-0000-000000000096");
    private static final UUID REQUEST_DATASET_ID = UUID.fromString("30000000-0000-0000-0000-000000000096");

    @Test
    void rejectsUnreadableRunEvenWhenRequestNamesAnotherReadableDataset() {
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(SOURCE_DATASET_ID);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(readGuard.requireReadable(SOURCE_DATASET_ID, "dept-a"))
            .thenThrow(new AccessDeniedException("denied"));
        IssueTicketUpsertRequest request = request();
        request.setDatasetId(REQUEST_DATASET_ID);

        assertThatThrownBy(() -> new IssueSourcePolicy(runRepository, readGuard).validateClientCreate(request, "dept-a"))
            .isInstanceOf(AccessDeniedException.class);
        verify(readGuard).requireReadable(SOURCE_DATASET_ID, "dept-a");
    }

    @Test
    void bindsValidatedRunDatasetAndRejectsCrossDatasetSourcePairs() {
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(SOURCE_DATASET_ID);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(SOURCE_DATASET_ID);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        when(readGuard.requireReadable(SOURCE_DATASET_ID, "dept-a")).thenReturn(dataset);
        IssueSourcePolicy policy = new IssueSourcePolicy(runRepository, readGuard);
        IssueTicketUpsertRequest valid = request();

        policy.validateClientCreate(valid, "dept-a");

        assertThat(valid.getDatasetId()).isEqualTo(SOURCE_DATASET_ID);
        IssueTicketUpsertRequest mismatched = request();
        mismatched.setDatasetId(REQUEST_DATASET_ID);
        assertThatThrownBy(() -> policy.validateClientCreate(mismatched, "dept-a"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("问题来源与数据资产不一致");
    }

    @Test
    void issueSourcePairIsImmutableAfterCreation() {
        GovIssueTicket ticket = sourcedTicket();
        IssueTicketUpsertRequest same = request();
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(SOURCE_DATASET_ID);
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        IssueSourcePolicy policy = new IssueSourcePolicy(runRepository, readGuard);
        policy.assertImmutable(ticket, same, "dept-a");

        IssueTicketUpsertRequest changed = request();
        changed.setSourceId(UUID.fromString("40000000-0000-0000-0000-000000000096"));
        assertThatThrownBy(() -> policy.assertImmutable(ticket, changed, "dept-a"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("问题来源创建后不可修改");
    }

    @Test
    void rejectsCrossDatasetUpdateForQualityRunSource() {
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(SOURCE_DATASET_ID);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        GovIssueTicket ticket = sourcedTicket();
        IssueTicketUpsertRequest update = new IssueTicketUpsertRequest();
        update.setDatasetId(REQUEST_DATASET_ID);

        assertThatThrownBy(() -> new IssueSourcePolicy(runRepository, readGuard).assertImmutable(ticket, update, "dept-a"))
            .isInstanceOf(AccessDeniedException.class)
            .hasMessage("问题来源与数据资产不一致");
        verify(readGuard).requireReadable(SOURCE_DATASET_ID, "dept-a");
    }

    @Test
    void omittedDatasetPreservesQualityRunSourceDataset() {
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun run = new GovQualityRun();
        run.setDatasetId(SOURCE_DATASET_ID);
        when(runRepository.findById(RUN_ID)).thenReturn(Optional.of(run));
        GovIssueTicket ticket = sourcedTicket();
        IssueTicketUpsertRequest update = new IssueTicketUpsertRequest();

        new IssueSourcePolicy(runRepository, readGuard).assertImmutable(ticket, update, "dept-a");

        assertThat(update.getDatasetId()).isEqualTo(SOURCE_DATASET_ID);
        verify(readGuard).requireReadable(SOURCE_DATASET_ID, "dept-a");
    }

    private static GovIssueTicket sourcedTicket() {
        GovIssueTicket ticket = new GovIssueTicket();
        ticket.setSourceType("QUALITY_RUN");
        ticket.setSourceRefId(RUN_ID);
        ticket.setDatasetId(SOURCE_DATASET_ID);
        return ticket;
    }

    private static IssueTicketUpsertRequest request() {
        IssueTicketUpsertRequest request = new IssueTicketUpsertRequest();
        request.setSourceType("QUALITY_RUN");
        request.setSourceId(RUN_ID);
        return request;
    }
}
