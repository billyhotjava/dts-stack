package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import static com.yuzhi.dts.common.audit.AuditStage.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationChecksumCodec;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecSnapshotCodec;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardRestoreService.ForwardRestoreCommand;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportForwardRestoreService.ForwardRestoreResult;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationRepository.HistoricalImplementation;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationRepository.UndoEligibility;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelSpecImportForwardRestoreServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000083");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000083");
    private static final String POST_MODEL_CHECKSUM = "a".repeat(64);
    private static final String POST_IMPLEMENTATION_CHECKSUM = "b".repeat(64);
    private static final String RESTORE_MODEL_CHECKSUM = "c".repeat(64);
    private static final String RESTORE_IMPLEMENTATION_CHECKSUM = "d".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-02T00:00:00Z");

    @Test
    void loadsHistoricalPayloadServerSideAndAppendsBothRestoredHeadsAgainstExactPostPins() {
        Fixture fixture = fixture();
        ModelSpecView historicalModel = model(8, RESTORE_MODEL_CHECKSUM);
        ModelSpecView restoredModel = model(12, RESTORE_MODEL_CHECKSUM);
        SaveImplementationCommand historicalCommand = command();
        ImplementationView restoredImplementation = implementation(restoredModel, RESTORE_IMPLEMENTATION_CHECKSUM);
        ForwardRestoreCommand restore = restoreCommand();
        when(fixture.reconciliation().evaluateUndoEligibility(any(), any(), any(), any()))
            .thenReturn(UndoEligibility.eligible());
        when(fixture.modelSpecs().revision("tenant-a", restore.restoreModel())).thenReturn(historicalModel);
        when(fixture.reconciliation().findHistoricalImplementation(
            "tenant-a", MODEL_ID, restore.restorePins(), restore.idempotencyKey() + ":implementation"
        )).thenReturn(Optional.of(new HistoricalImplementation("finance", "model.finance.budget", historicalCommand)));
        when(fixture.checksums().contentChecksum(historicalCommand)).thenReturn(RESTORE_IMPLEMENTATION_CHECKSUM);
        when(fixture.modelSpecs().update(eq("tenant-a"), eq("alice"), eq(MODEL_ID), any(), any()))
            .thenReturn(restoredModel);
        when(fixture.lifecycle().restoreImportedDbtImplementation(
            eq("tenant-a"), eq("alice"), eq(restoredModel), eq("finance"), eq("model.finance.budget"),
            eq(historicalCommand), eq(11), eq(POST_MODEL_CHECKSUM), eq(6),
            eq(POST_IMPLEMENTATION_CHECKSUM), eq(RESTORE_IMPLEMENTATION_CHECKSUM), eq(NOW)
        )).thenReturn(1);
        when(fixture.lifecycle().findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(restoredImplementation));

        ForwardRestoreResult result = fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restore);

        assertThat(result.modelSpec()).isSameAs(restoredModel);
        assertThat(result.implementation()).isSameAs(restoredImplementation);
        ArgumentCaptor<Object> auditPayload = ArgumentCaptor.forClass(Object.class);
        verify(fixture.audit()).auditActionStrict(
            eq("MODELING_DBT_IMPORT_FORWARD_UNDO_ITEM"),
            eq(SUCCESS),
            eq("undo-83:" + MODEL_ID),
            auditPayload.capture()
        );
        assertThat(auditPayload.getValue()).isInstanceOfSatisfying(Map.class, payload ->
            assertThat(payload)
                .containsEntry("modelSpecId", MODEL_ID)
                .containsEntry("modelRevision", 12)
                .containsEntry("implementationRevision", 7)
                .doesNotContainKeys("modelChecksum", "implementationChecksum", "sql", "path", "sample")
        );
    }

    @Test
    void failsClosedBeforeAnyWriteWhenHistoricalModelChecksumDoesNotMatchCheckpoint() {
        Fixture fixture = fixture();
        ForwardRestoreCommand restore = restoreCommand();
        ModelSpecView mismatchedHistoricalModel = model(8, "e".repeat(64));
        when(fixture.reconciliation().evaluateUndoEligibility(any(), any(), any(), any()))
            .thenReturn(UndoEligibility.eligible());
        when(fixture.modelSpecs().revision("tenant-a", restore.restoreModel()))
            .thenReturn(mismatchedHistoricalModel);

        assertThatThrownBy(() -> fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restore))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPORT_UNDO_MODEL_HISTORY_MISMATCH");
        verify(fixture.modelSpecs(), never()).update(any(), any(), any(), any(), any());
        verify(fixture.lifecycle(), never()).restoreImportedDbtImplementation(
            any(), any(), any(), any(), any(), any(), anyInt(), any(),
            anyInt(), any(), any(), any()
        );
    }

    @Test
    void returnsBlockedBeforeReadingHistoryWhenEligibilityGuardRejectsTheRestore() {
        Fixture fixture = fixture();
        ForwardRestoreCommand restore = restoreCommand();
        when(fixture.reconciliation().evaluateUndoEligibility(any(), any(), any(), any()))
            .thenReturn(UndoEligibility.blocked("DOWNSTREAM_MODEL_PIN"));

        ForwardRestoreResult result = fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restore);

        assertThat(result.blocked()).isTrue();
        assertThat(result.blockedReasonCode()).isEqualTo("DOWNSTREAM_MODEL_PIN");
        verify(fixture.modelSpecs(), never()).revision(any(), any());
        verify(fixture.modelSpecs(), never()).update(any(), any(), any(), any(), any());
    }

    @Test
    void appendsAnExplicitModelRevisionWhenTheHistoricalContentEqualsTheCurrentHead() {
        Fixture fixture = fixture();
        ForwardRestoreCommand restore = restoreCommand(POST_MODEL_CHECKSUM);
        ModelSpecView historicalModel = model(8, POST_MODEL_CHECKSUM);
        ModelSpecView currentModel = model(11, POST_MODEL_CHECKSUM);
        ModelSpecView appendedModel = model(12, POST_MODEL_CHECKSUM);
        SaveImplementationCommand historicalCommand = command();
        ImplementationView restoredImplementation = implementation(appendedModel, RESTORE_IMPLEMENTATION_CHECKSUM);
        when(fixture.reconciliation().evaluateUndoEligibility(any(), any(), any(), any()))
            .thenReturn(UndoEligibility.eligible());
        when(fixture.modelSpecs().revision("tenant-a", restore.restoreModel())).thenReturn(historicalModel);
        when(fixture.reconciliation().findHistoricalImplementation(any(), any(), any(), any()))
            .thenReturn(Optional.of(new HistoricalImplementation("finance", "model.finance.budget", historicalCommand)));
        when(fixture.checksums().contentChecksum(historicalCommand)).thenReturn(RESTORE_IMPLEMENTATION_CHECKSUM);
        when(fixture.modelSpecs().update(eq("tenant-a"), eq("alice"), eq(MODEL_ID), any(), any()))
            .thenReturn(currentModel);
        when(fixture.snapshots().toUpdatedView(eq(currentModel), any(), eq(12), eq(NOW))).thenReturn(appendedModel);
        when(fixture.snapshots().write(appendedModel)).thenReturn("{\"revision\":12}");
        when(fixture.modelSpecRepository().appendUnchangedV2RevisionForForwardUndo(
            "tenant-a", "alice", 11, POST_MODEL_CHECKSUM, appendedModel, "{\"revision\":12}"
        )).thenReturn(1);
        when(fixture.lifecycle().restoreImportedDbtImplementation(
            eq("tenant-a"), eq("alice"), eq(appendedModel), eq("finance"), eq("model.finance.budget"),
            eq(historicalCommand), eq(11), eq(POST_MODEL_CHECKSUM), eq(6),
            eq(POST_IMPLEMENTATION_CHECKSUM), eq(RESTORE_IMPLEMENTATION_CHECKSUM), eq(NOW)
        )).thenReturn(1);
        when(fixture.lifecycle().findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(restoredImplementation));

        ForwardRestoreResult result = fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restore);

        assertThat(result.modelSpec()).isSameAs(appendedModel);
        assertThat(result.implementation()).isSameAs(restoredImplementation);
    }

    @Test
    void failsTheTransactionBoundaryWhenImplementationCasLoses() {
        Fixture fixture = preparedNormalRestore();

        assertThatThrownBy(() -> fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restoreCommand()))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPORT_UNDO_CURRENT_PINS_CHANGED");
        verify(fixture.audit(), never()).auditActionStrict(any(), any(), any(), any());
    }

    @Test
    void propagatesStrictAuditFailureSoTheWorkerTransactionRollsBackBothHeads() {
        Fixture fixture = preparedNormalRestore();
        ModelSpecView restoredModel = model(12, RESTORE_MODEL_CHECKSUM);
        ImplementationView restoredImplementation = implementation(restoredModel, RESTORE_IMPLEMENTATION_CHECKSUM);
        when(fixture.lifecycle().restoreImportedDbtImplementation(any(), any(), any(), any(), any(), any(),
            anyInt(), any(), anyInt(), any(), any(), any())).thenReturn(1);
        when(fixture.lifecycle().findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(restoredImplementation));
        RuntimeException auditFailure = new RuntimeException("audit unavailable");
        org.mockito.Mockito.doThrow(auditFailure).when(fixture.audit()).auditActionStrict(any(), any(), any(), any());

        assertThatThrownBy(() -> fixture.service().restoreImportedDbtRevision("tenant-a", "alice", restoreCommand()))
            .isSameAs(auditFailure);
    }

    private static Fixture preparedNormalRestore() {
        Fixture fixture = fixture();
        ForwardRestoreCommand restore = restoreCommand();
        ModelSpecView historicalModel = model(8, RESTORE_MODEL_CHECKSUM);
        ModelSpecView restoredModel = model(12, RESTORE_MODEL_CHECKSUM);
        SaveImplementationCommand historicalCommand = command();
        when(fixture.reconciliation().evaluateUndoEligibility(any(), any(), any(), any()))
            .thenReturn(UndoEligibility.eligible());
        when(fixture.modelSpecs().revision("tenant-a", restore.restoreModel())).thenReturn(historicalModel);
        when(fixture.reconciliation().findHistoricalImplementation(any(), any(), any(), any()))
            .thenReturn(Optional.of(new HistoricalImplementation("finance", "model.finance.budget", historicalCommand)));
        when(fixture.checksums().contentChecksum(historicalCommand)).thenReturn(RESTORE_IMPLEMENTATION_CHECKSUM);
        when(fixture.modelSpecs().update(eq("tenant-a"), eq("alice"), eq(MODEL_ID), any(), any()))
            .thenReturn(restoredModel);
        when(fixture.lifecycle().restoreImportedDbtImplementation(any(), any(), any(), any(), any(), any(),
            anyInt(), any(), anyInt(), any(), any(), any())).thenReturn(0);
        return fixture;
    }

    private static Fixture fixture() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelSpecSnapshotCodec snapshots = mock(ModelSpecSnapshotCodec.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecImportReconciliationRepository reconciliation = mock(ModelSpecImportReconciliationRepository.class);
        ModelImplementationChecksumCodec checksums = mock(ModelImplementationChecksumCodec.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecImportForwardRestoreService service = new ModelSpecImportForwardRestoreService(
            modelSpecs,
            modelSpecRepository,
            snapshots,
            lifecycle,
            reconciliation,
            checksums,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        return new Fixture(service, modelSpecs, modelSpecRepository, snapshots, lifecycle, reconciliation, checksums, audit);
    }

    private static ForwardRestoreCommand restoreCommand() {
        return restoreCommand(RESTORE_MODEL_CHECKSUM);
    }

    private static ForwardRestoreCommand restoreCommand(String restoreModelChecksum) {
        return new ForwardRestoreCommand(
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 11, POST_MODEL_CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 6, POST_IMPLEMENTATION_CHECKSUM),
            new RevisionPins(8, restoreModelChecksum, 3, RESTORE_IMPLEMENTATION_CHECKSUM),
            "finance",
            "model.finance.budget",
            "undo-83"
        );
    }

    private static ModelSpecView model(int revision, String checksum) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(revision);
        when(model.checksum()).thenReturn(checksum);
        when(model.status()).thenReturn(ModelStatus.DRAFT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        return model;
    }

    private static ImplementationView implementation(ModelSpecView restoredModel, String implementationChecksum) {
        return new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000083"),
            MODEL_ID,
            PLAN_ID,
            restoredModel.revision(),
            restoredModel.checksum(),
            ImplementationMode.DBT_MANAGED,
            "finance",
            "model.finance.budget",
            "ACTIVE",
            7,
            implementationChecksum,
            InputMode.GENERATED,
            command().inputs(),
            List.of(),
            Map.of(),
            "table"
        );
    }

    private static SaveImplementationCommand command() {
        return new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", "finance", "dbtUniqueId", "model.finance.budget"))),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "undo-83:implementation"
        );
    }

    private record Fixture(
        ModelSpecImportForwardRestoreService service,
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelSpecSnapshotCodec snapshots,
        ModelLifecycleRepository lifecycle,
        ModelSpecImportReconciliationRepository reconciliation,
        ModelImplementationChecksumCodec checksums,
        AuditService audit
    ) {}
}
