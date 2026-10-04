package com.yuzhi.dts.platform.service.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.DispatchEnvelope;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.DispatchView;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RollbackDispatchWorkerTest {

    private static final UUID RECEIPT_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");

    @Test
    void claimedCommandIsDeliveredAndLeftSentUntilAuthoritativeCallback() {
        RollbackInvalidationService invalidations = mock(RollbackInvalidationService.class);
        IngestionServiceClient ingestion = mock(IngestionServiceClient.class);
        DispatchEnvelope envelope = envelope();
        when(invalidations.claimNextDispatch()).thenReturn(Optional.of(envelope));
        when(ingestion.rollbackExecute(envelope.command()))
            .thenReturn(new ApiResponse<>(200, "ok", Map.of("success", true)));
        when(invalidations.markDispatchSent(RECEIPT_ID, 1)).thenReturn(view("SENT"));
        RollbackDispatchWorker worker = new RollbackDispatchWorker(invalidations, ingestion);

        var result = worker.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("SENT");
        verify(ingestion).rollbackExecute(envelope.command());
        verify(invalidations).markDispatchSent(RECEIPT_ID, 1);
    }

    @Test
    void transportFailurePersistsRetryAndReconciliationInsteadOfLosingClaim() {
        RollbackInvalidationService invalidations = mock(RollbackInvalidationService.class);
        IngestionServiceClient ingestion = mock(IngestionServiceClient.class);
        DispatchEnvelope envelope = envelope();
        when(invalidations.claimNextDispatch()).thenReturn(Optional.of(envelope));
        when(ingestion.rollbackExecute(envelope.command())).thenThrow(new IllegalStateException("connection reset"));
        when(invalidations.recordDispatchFailure(any(), anyInt(), anyString(), any()))
            .thenReturn(view("RETRY"));
        RollbackDispatchWorker worker = new RollbackDispatchWorker(invalidations, ingestion);

        var result = worker.dispatchNext().orElseThrow();

        assertThat(result.status()).isEqualTo("RETRY");
        verify(invalidations).recordDispatchFailure(
            RECEIPT_ID,
            1,
            "ROLLBACK_DISPATCH_TRANSPORT_FAILURE",
            Map.of("transportFailure", "IllegalStateException")
        );
    }

    private DispatchEnvelope envelope() {
        return new DispatchEnvelope(
            RECEIPT_ID,
            1,
            "a".repeat(64),
            Map.of(
                "level",
                1,
                "scope",
                "task",
                "taskId",
                7,
                "dryRun",
                false,
                "rollbackId",
                RECEIPT_ID.toString()
            )
        );
    }

    private DispatchView view(String status) {
        return new DispatchView(RECEIPT_ID, status, 1, null, null, null, null);
    }
}
