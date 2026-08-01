package com.yuzhi.dts.platform.service.rollback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.AvailabilitySnapshot;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.CompletionEvent;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.DispatchRecord;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.Receipt;
import com.yuzhi.dts.platform.repository.rollback.RollbackInvalidationRepository.Target;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.ingestion.RollbackCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionCommand;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.CompletionOutcome;
import com.yuzhi.dts.platform.service.rollback.RollbackInvalidationService.PrepareCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RollbackInvalidationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-01T01:00:00Z");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RECEIPT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private RollbackInvalidationRepository repository;
    private AuditService auditService;
    private RollbackInvalidationService service;

    @BeforeEach
    void setUp() {
        repository = mock(RollbackInvalidationRepository.class);
        auditService = mock(AuditService.class);
        service = new RollbackInvalidationService(
            repository,
            auditService,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(repository.findCompletionEvent(any(), anyString())).thenReturn(Optional.empty());
    }

    @Test
    void prepareDurablyFencesBeforeReturningAndWritesStrictEvidence() {
        Target target = target();
        when(repository.findReceiptByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.discoverTargets(SOURCE_ID, List.of("orders"))).thenReturn(List.of(target));
        when(repository.nextSourceSequence()).thenReturn(10L);
        when(repository.insertReceipt(any(), eq(NOW))).thenReturn(true);

        RollbackInvalidationService.PreparedInvalidation prepared = service.prepare(
            new PrepareCommand(plan(), SOURCE_ID, "alice", "one-time-confirmation")
        );

        assertThat(prepared.state()).isEqualTo(RollbackInvalidationService.PREPARED);
        assertThat(prepared.sourceSequence()).isEqualTo(10L);
        assertThat(prepared.targetCount()).isOne();
        assertThat(prepared.requestHash()).hasSize(64);
        ArgumentCaptor<Receipt> receipt = ArgumentCaptor.forClass(Receipt.class);
        verify(repository).insertReceipt(receipt.capture(), eq(NOW));
        verify(repository).insertTargets(receipt.getValue().id(), List.of(target), NOW);
        verify(repository).fenceTargets(
            eq(receipt.getValue().id()),
            eq(10L),
            anyString(),
            anyString(),
            eq(List.of(target)),
            eq(NOW)
        );
        ArgumentCaptor<String> commandHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> commandJson = ArgumentCaptor.forClass(String.class);
        verify(repository).insertDispatch(
            eq(receipt.getValue().id()),
            commandHash.capture(),
            commandJson.capture(),
            eq(NOW)
        );
        assertThat(commandHash.getValue()).hasSize(64);
        assertThat(commandJson.getValue())
            .contains("\"rollbackId\":\"" + receipt.getValue().id() + "\"")
            .contains("\"idempotencyKey\":\"platform:" + receipt.getValue().id() + "\"")
            .contains("\"sourceDataSourceId\":\"" + SOURCE_ID + "\"")
            .contains("\"sourceSequence\":10");
        verify(auditService).auditActionStrict(
            eq("ROLLBACK_INVALIDATION_PREPARE"),
            eq(AuditStage.SUCCESS),
            eq(receipt.getValue().id().toString()),
            any()
        );
        verify(repository).insertPlatformEvent(
            anyString(),
            anyString(),
            eq("RollbackInvalidationPrepared"),
            eq(receipt.getValue().id().toString()),
            eq("PREPARE"),
            eq(RollbackInvalidationService.PREPARED),
            eq("alice"),
            eq("ROLLBACK_INVALIDATION_PREPARE"),
            anyString(),
            eq(NOW)
        );
    }

    @Test
    void prepareAdvancesGlobalSequenceWhenExistingAvailabilityIsAheadOfSequenceAllocator() {
        Target target = new Target(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            RECEIPT_ID,
            "DATASET",
            "source:" + SOURCE_ID + "/schema:ods/table:orders",
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            true,
            new AvailabilitySnapshot(
                RollbackInvalidationRepository.AVAILABLE,
                4L,
                100L,
                "external-availability-100",
                "c".repeat(64),
                "external restore"
            )
        );
        when(repository.findReceiptByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(repository.discoverTargets(SOURCE_ID, List.of("orders"))).thenReturn(List.of(target));
        when(repository.nextSourceSequence()).thenReturn(10L);
        when(repository.insertReceipt(any(), eq(NOW))).thenReturn(true);

        var prepared = service.prepare(
            new PrepareCommand(plan(), SOURCE_ID, "alice", "advanced-source-sequence")
        );

        assertThat(prepared.sourceSequence()).isEqualTo(101L);
        verify(repository).advanceSourceSequence(101L);
        verify(repository).fenceTargets(
            eq(prepared.receiptId()),
            eq(101L),
            anyString(),
            anyString(),
            eq(List.of(target)),
            eq(NOW)
        );
    }

    @Test
    void sameCompletionEventAndHashIsNoOpButChangedPayloadConflicts() {
        Receipt prepared = receipt(RollbackInvalidationService.PREPARED, null, null, null);
        AtomicReference<Receipt> current = new AtomicReference<>(prepared);
        when(repository.findReceiptForUpdate(RECEIPT_ID)).thenAnswer(invocation -> Optional.of(current.get()));
        when(repository.findTargets(RECEIPT_ID)).thenReturn(List.of(target()));
        org.mockito.Mockito.doAnswer(invocation -> {
            current.set(
                receipt(
                    invocation.getArgument(1),
                    invocation.getArgument(2),
                    invocation.getArgument(3),
                    invocation.getArgument(4)
                )
            );
            return null;
        }).when(repository).updateReceiptState(
            eq(RECEIPT_ID),
            anyString(),
            anyString(),
            anyString(),
            anyLong(),
            anyString(),
            eq(NOW)
        );
        CompletionCommand command = new CompletionCommand(
            RECEIPT_ID,
            "ingestion.rollback.42.applied",
            11L,
            CompletionOutcome.APPLY,
            "a",
            false,
            "b\u0000false\u0000c"
        );

        assertThat(service.complete(command).state()).isEqualTo(RollbackInvalidationService.APPLIED);
        assertThat(service.complete(command).idempotentReplay()).isTrue();
        verify(repository, times(1)).applyTargets(
            any(),
            any(),
            eq("ingestion.rollback.42.applied"),
            anyString(),
            eq(11L),
            eq("a"),
            eq(NOW)
        );
        verify(repository).advanceSourceSequence(11L);
        verify(repository).insertCompletionEvent(any(CompletionEvent.class));
        verify(repository, times(2)).markDispatchCompletedByCallback(RECEIPT_ID, NOW);

        CompletionCommand changed = new CompletionCommand(
            RECEIPT_ID,
            command.eventId(),
            command.sourceSequence(),
            command.outcome(),
            "a\u0000false\u0000b",
            false,
            "c"
        );
        assertThatThrownBy(() -> service.complete(changed))
            .isInstanceOf(RollbackInvalidationException.class)
            .hasMessageContaining("different payload");
    }

    @Test
    void abortRequiresExplicitZeroSideEffectProofAndStaleEventsCannotOverwriteFence() {
        when(repository.findReceiptForUpdate(RECEIPT_ID))
            .thenReturn(Optional.of(receipt(RollbackInvalidationService.PREPARED, null, null, null)));

        CompletionCommand unprovenAbort = new CompletionCommand(
            RECEIPT_ID,
            "ingestion.rollback.42.failed",
            11L,
            CompletionOutcome.ABORT_NO_SIDE_EFFECT,
            "failure",
            false,
            "operation-42"
        );
        assertThatThrownBy(() -> service.complete(unprovenAbort))
            .isInstanceOf(RollbackInvalidationException.class)
            .extracting(error -> ((RollbackInvalidationException) error).code())
            .isEqualTo("ROLLBACK_INVALIDATION_ABORT_NOT_PROVEN");
        verify(repository, never()).abortTargets(any(), any(), anyString(), anyString(), anyLong(), anyString(), any());

        CompletionCommand staleApply = new CompletionCommand(
            RECEIPT_ID,
            "ingestion.rollback.42.applied",
            10L,
            CompletionOutcome.APPLY,
            "late success",
            false,
            "operation-42"
        );
        assertThatThrownBy(() -> service.complete(staleApply))
            .isInstanceOf(RollbackInvalidationException.class)
            .extracting(error -> ((RollbackInvalidationException) error).code())
            .isEqualTo("ROLLBACK_INVALIDATION_STALE_EVENT");
        verify(repository, never()).applyTargets(any(), any(), anyString(), anyString(), anyLong(), anyString(), any());
    }

    @Test
    void acknowledgedDeliveryStopsAfterBoundedAttemptsWhenCompletionCallbackNeverArrives() {
        when(repository.findReceiptForUpdate(RECEIPT_ID))
            .thenReturn(Optional.of(receipt(RollbackInvalidationService.PREPARED, null, null, null)));
        when(repository.findTargets(RECEIPT_ID)).thenReturn(List.of(target()));
        when(repository.findDispatch(RECEIPT_ID))
            .thenReturn(
                Optional.of(
                    new DispatchRecord(
                        RECEIPT_ID,
                        RECEIPT_ID,
                        "b".repeat(64),
                        "{}",
                        "CLAIMED",
                        20,
                        20,
                        NOW,
                        null,
                        NOW,
                        null,
                        null
                    )
                ),
                Optional.of(
                    new DispatchRecord(
                        RECEIPT_ID,
                        RECEIPT_ID,
                        "b".repeat(64),
                        "{}",
                        "DEAD",
                        20,
                        20,
                        null,
                        null,
                        NOW,
                        null,
                        "ROLLBACK_COMPLETION_CALLBACK_NOT_OBSERVED"
                    )
                )
            );

        var dispatch = service.markDispatchSent(RECEIPT_ID, 20);

        assertThat(dispatch.status()).isEqualTo("DEAD");
        verify(repository).updateReceiptState(
            eq(RECEIPT_ID),
            eq(RollbackInvalidationService.RECONCILIATION_REQUIRED),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull(),
            anyString(),
            eq(NOW)
        );
        verify(repository).markDispatchDead(
            RECEIPT_ID,
            20,
            "ROLLBACK_COMPLETION_CALLBACK_NOT_OBSERVED",
            NOW
        );
        verify(repository, never()).markDispatchSent(any(), anyInt(), any(), any());
    }

    @Test
    void replayGenerationGetsFreshRetryBudgetWithoutResettingCumulativeClaimFence() {
        when(repository.findReceiptForUpdate(RECEIPT_ID))
            .thenReturn(Optional.of(receipt(RollbackInvalidationService.PREPARED, null, null, null)));
        DispatchRecord active = new DispatchRecord(
            RECEIPT_ID,
            RECEIPT_ID,
            "b".repeat(64),
            "{}",
            "CLAIMED",
            21,
            1,
            NOW,
            null,
            NOW.minusSeconds(60),
            null,
            null
        );
        DispatchRecord sent = new DispatchRecord(
            RECEIPT_ID,
            RECEIPT_ID,
            "b".repeat(64),
            "{}",
            "SENT",
            21,
            1,
            null,
            NOW.plusSeconds(30),
            NOW,
            null,
            null
        );
        when(repository.findDispatch(RECEIPT_ID))
            .thenReturn(Optional.of(active), Optional.of(sent));

        var dispatch = service.markDispatchSent(RECEIPT_ID, 21);

        assertThat(dispatch.status()).isEqualTo("SENT");
        assertThat(dispatch.attempts()).isEqualTo(21);
        verify(repository).markDispatchSent(RECEIPT_ID, 21, NOW.plusSeconds(30), NOW);
        verify(repository, never()).markDispatchDead(any(), anyInt(), anyString(), any());
    }

    private RollbackCommand plan() {
        return new RollbackCommand(1, "task", 7L, null, List.of("orders"), false);
    }

    private Target target() {
        return new Target(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            RECEIPT_ID,
            "DATASET",
            "source:" + SOURCE_ID + "/schema:ods/table:orders",
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            true,
            AvailabilitySnapshot.legacyAvailable()
        );
    }

    private Receipt receipt(String state, String eventId, String eventHash, Long completionSequence) {
        return new Receipt(
            RECEIPT_ID,
            "idempotency",
            "a".repeat(64),
            state,
            3,
            "task",
            7L,
            SOURCE_ID,
            10L,
            "alice",
            eventId,
            eventHash,
            completionSequence
        );
    }
}
