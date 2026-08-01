package com.yuzhi.dts.platform.service.rollback;

import com.yuzhi.dts.platform.service.ingestion.IngestionServiceClient;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.DispatchEnvelope;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.DispatchView;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class RollbackDispatchWorker {

    private static final Logger LOG = LoggerFactory.getLogger(RollbackDispatchWorker.class);
    private static final int MAX_BATCH_SIZE = 20;

    private final RollbackInvalidationService invalidations;
    private final IngestionServiceClient ingestionClient;

    public RollbackDispatchWorker(
        RollbackInvalidationService invalidations,
        IngestionServiceClient ingestionClient
    ) {
        this.invalidations = invalidations;
        this.ingestionClient = ingestionClient;
    }

    @Scheduled(fixedDelayString = "${dts.rollback.dispatch-delay-ms:2000}")
    public void dispatchPending() {
        for (int index = 0; index < MAX_BATCH_SIZE; index++) {
            try {
                if (dispatchNext().isEmpty()) {
                    return;
                }
            } catch (RuntimeException failure) {
                LOG.error("Rollback dispatch worker failed before the delivery outcome was persisted", failure);
                return;
            }
        }
    }

    public Optional<DeliveryResult> dispatchNext() {
        return invalidations.claimNextDispatch().map(this::deliver);
    }

    private DeliveryResult deliver(DispatchEnvelope dispatch) {
        ApiResponse<Object> response;
        try {
            response = ingestionClient.rollbackExecute(dispatch.command());
        } catch (RuntimeException failure) {
            DispatchView state = invalidations.recordDispatchFailure(
                dispatch.receiptId(),
                dispatch.claimAttempt(),
                "ROLLBACK_DISPATCH_TRANSPORT_FAILURE",
                Map.of("transportFailure", failure.getClass().getSimpleName())
            );
            return new DeliveryResult(dispatch.receiptId(), state.status(), dispatch.claimAttempt(), null);
        }

        if (explicitSuccess(response)) {
            DispatchView state = invalidations.markDispatchSent(
                dispatch.receiptId(),
                dispatch.claimAttempt()
            );
            return new DeliveryResult(dispatch.receiptId(), state.status(), dispatch.claimAttempt(), response);
        }
        DispatchView state = invalidations.recordDispatchFailure(
            dispatch.receiptId(),
            dispatch.claimAttempt(),
            "ROLLBACK_DISPATCH_NOT_CONFIRMED",
            downstreamSummary(response)
        );
        return new DeliveryResult(dispatch.receiptId(), state.status(), dispatch.claimAttempt(), response);
    }

    private boolean explicitSuccess(ApiResponse<Object> response) {
        return response != null &&
            response.getStatus() >= 200 &&
            response.getStatus() < 300 &&
            response.getData() instanceof Map<?, ?> data &&
            Boolean.TRUE.equals(data.get("success"));
    }

    private Map<String, Object> downstreamSummary(ApiResponse<Object> response) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", response == null ? 0 : response.getStatus());
        summary.put("code", response == null || response.getCode() == null ? "" : response.getCode());
        if (response != null && response.getData() instanceof Map<?, ?> data) {
            summary.put("success", Boolean.TRUE.equals(data.get("success")));
            copyDiagnostic(data, summary, "sideEffectStatus");
            copyDiagnostic(data, summary, "sideEffectsApplied");
            copyDiagnostic(data, summary, "receiptId");
            copyDiagnostic(data, summary, "outcome");
            copyDiagnostic(data, summary, "completionEventId");
        }
        return Map.copyOf(summary);
    }

    private void copyDiagnostic(Map<?, ?> source, Map<String, Object> target, String field) {
        Object value = source.get(field);
        if (value instanceof String text && !text.isBlank()) {
            target.put(field, text.trim());
        } else if (value instanceof Boolean flag) {
            target.put(field, flag);
        }
    }

    public record DeliveryResult(
        java.util.UUID receiptId,
        String status,
        int attempt,
        ApiResponse<Object> response
    ) {}
}
