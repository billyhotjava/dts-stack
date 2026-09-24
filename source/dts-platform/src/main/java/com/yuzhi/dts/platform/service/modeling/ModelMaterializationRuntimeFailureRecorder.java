package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persists a runtime-spec refusal on its dispatch. The consume transaction holds the dispatch row
 * lock and rolls back on failure, so the write runs in its own transaction after that completes.
 */
@Service
public class ModelMaterializationRuntimeFailureRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMaterializationRuntimeFailureRecorder.class);

    private final ModelMaterializationDispatchRepository dispatches;
    private final ModelMaterializationRunArtifactService runArtifacts;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public ModelMaterializationRuntimeFailureRecorder(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationRunArtifactService runArtifacts,
        PlatformTransactionManager transactionManager
    ) {
        this(dispatches, runArtifacts, transactionManager, Clock.systemUTC());
    }

    ModelMaterializationRuntimeFailureRecorder(
        ModelMaterializationDispatchRepository dispatches,
        ModelMaterializationRunArtifactService runArtifacts,
        PlatformTransactionManager transactionManager,
        Clock clock
    ) {
        this.dispatches = Objects.requireNonNull(dispatches, "dispatches is required");
        this.runArtifacts = Objects.requireNonNull(runArtifacts, "runArtifacts is required");
        this.transactions = new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager is required")
        );
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    public void recordAfterRollback(UUID dispatchId, String errorCode) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record(dispatchId, errorCode);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) record(dispatchId, errorCode);
                }
            }
        );
    }

    private void record(UUID dispatchId, String errorCode) {
        try {
            transactions.executeWithoutResult(status -> {
                if (
                    ModelMaterializationSourceAvailabilityGuard.SOURCE_UNAVAILABLE.equals(errorCode) ||
                    ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE.equals(errorCode) ||
                    ModelMaterializationSourceAvailabilityGuard.SOURCE_PIN_MISSING.equals(errorCode)
                ) {
                    runArtifacts.recordPreparationSourceFailure(dispatchId, errorCode, clock.instant());
                } else {
                    dispatches.recordRuntimeFailure(dispatchId, errorCode, clock.instant());
                }
            });
        } catch (RuntimeException failure) {
            // Diagnostics only: the caller's original failure must still reach Airflow.
            LOG.warn(
                "event=model_runtime_spec_failure_record_failed dispatchId={} code={} failureType={}",
                dispatchId,
                errorCode,
                failure.getClass().getSimpleName()
            );
        }
    }
}
