package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationDispatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class ModelMaterializationRuntimeFailureRecorderTest {

    private static final UUID DISPATCH_ID = UUID.fromString("10000000-0000-0000-0000-000000000009");
    private static final Instant NOW = Instant.parse("2026-09-23T14:02:43Z");

    private ModelMaterializationDispatchRepository dispatches;
    private ModelMaterializationRuntimeFailureRecorder recorder;

    @BeforeEach
    void setUp() {
        dispatches = mock(ModelMaterializationDispatchRepository.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        recorder = new ModelMaterializationRuntimeFailureRecorder(
            dispatches,
            transactions,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void writesImmediatelyWithoutAnOuterTransaction() {
        recorder.recordAfterRollback(DISPATCH_ID, "DBT_TARGET_DATASOURCE_NOT_FOUND");

        verify(dispatches).recordRuntimeFailure(DISPATCH_ID, "DBT_TARGET_DATASOURCE_NOT_FOUND", NOW);
    }

    @Test
    void defersTheWriteUntilTheLockingTransactionHasRolledBack() {
        TransactionSynchronizationManager.initSynchronization();

        recorder.recordAfterRollback(DISPATCH_ID, "DBT_TARGET_DATASOURCE_NOT_FOUND");
        verify(dispatches, never()).recordRuntimeFailure(any(), any(), any());

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        verify(dispatches).recordRuntimeFailure(DISPATCH_ID, "DBT_TARGET_DATASOURCE_NOT_FOUND", NOW);
    }

    @Test
    void neverMasksTheOriginalFailureWhenTheWriteFails() {
        doThrow(new IllegalStateException("db down"))
            .when(dispatches)
            .recordRuntimeFailure(eq(DISPATCH_ID), any(), any());

        assertThatCode(() -> recorder.recordAfterRollback(DISPATCH_ID, "DBT_TARGET_DATASOURCE_NOT_FOUND"))
            .doesNotThrowAnyException();
    }
}
