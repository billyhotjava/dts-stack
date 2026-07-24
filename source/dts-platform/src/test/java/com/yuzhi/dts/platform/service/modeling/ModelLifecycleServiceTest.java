package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStep;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.RegistrationStepView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ModelLifecycleServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");

    @Test
    void validationReturnsStableInputKindErrorBeforeSaving() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        ModelImplementationCompatibilityAdapter adapter = new ModelImplementationCompatibilityAdapter(
            modelSpecs, modelSpecRepository, lifecycle, mock(ModelSpecSourceValidationPort.class)
        );
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelReleaseRegistrationPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            mock(ModelLifecyclePublicationService.class),
            mock(ModelingVNextApplicationService.class),
            adapter,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ModelLifecycleService.ImplementationValidationView result = service.validateImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new SaveImplementationCommand(
                InputMode.GENERATED,
                List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
                List.of(),
                Map.of(),
                ImplementationMode.DESIGNER_GENERATED,
                "table",
                "validate-fact-generated"
            )
        );

        assertThat(result).extracting(ModelLifecycleService.ImplementationValidationView::code)
            .isEqualTo("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
    }

    @Test
    void recordsTestStatusFromPersistedDbtRunInsteadOfClientAssertion() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecStageGateService gates = mock(ModelSpecStageGateService.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelLifecycleCompilerPort compiler = mock(ModelLifecycleCompilerPort.class);
        ModelReleaseRegistrationPort registrations = mock(ModelReleaseRegistrationPort.class);
        ModelLifecycleTestEvidencePort testEvidence = mock(ModelLifecycleTestEvidencePort.class);
        ModelLifecyclePublicationService publication = mock(ModelLifecyclePublicationService.class);
        ModelingVNextApplicationService runtime = mock(ModelingVNextApplicationService.class);
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            compiler,
            registrations,
            testEvidence,
            publication,
            runtime,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        ImplementationView owner = new ImplementationView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, ImplementationMode.DBT_MANAGED, "pjm", "model.pjm.fact", "ACTIVE"
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(owner));
        when(lifecycle.lockImplementation("tenant-a", MODEL_ID, owner)).thenReturn(true);
        when(lifecycle.listArtifacts("tenant-a", MODEL_ID, 7)).thenReturn(List.of(
            artifact("SQL"), artifact("SCHEMA"), artifact("TEST")
        ));
        when(lifecycle.hasPassedEvidence("tenant-a", MODEL_ID, 7, 1, CHECKSUM, EventType.COMPILE)).thenReturn(true);
        ModelLifecycleTestEvidencePort.VerificationRequest verification =
            new ModelLifecycleTestEvidencePort.VerificationRequest(
                "dbt-run-7",
                "tenant-a",
                MODEL_ID,
                1,
                CHECKSUM,
                "pjm",
                "model.pjm.fact"
            );
        when(testEvidence.verify(verification)).thenReturn(
            new ModelLifecycleTestEvidencePort.TestEvidence("dbt-run-7", "PASSED", "dbt test succeeded")
        );
        LifecycleEventView persisted = new LifecycleEventView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, EventType.TEST, "PASSED", "test-7",
            "alice", "verified", "dbt-run-7", Map.of("artifactRevision", 7), NOW
        );
        when(lifecycle.recordEvent(
            "tenant-a", "alice", model, EventType.TEST, "PASSED", "test-7", "verified", "dbt-run-7",
            Map.of("artifactRevision", 7), owner, NOW
        )).thenReturn(persisted);

        LifecycleEventView result = service.recordTest(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 1, CHECKSUM),
            new TestEvidenceCommand("PASSED", "dbt-run-7", "verified", "test-7")
        );

        assertThat(result.externalRef()).isEqualTo("dbt-run-7");
        verify(testEvidence).verify(verification);
    }

    @Test
    void retriesOnlyTheFailedRegistrationStepAfterAPartialPublish() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecStageGateService gates = mock(ModelSpecStageGateService.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelLifecycleCompilerPort compiler = mock(ModelLifecycleCompilerPort.class);
        ModelReleaseRegistrationPort registrations = mock(ModelReleaseRegistrationPort.class);
        ModelLifecycleTestEvidencePort testEvidence = mock(ModelLifecycleTestEvidencePort.class);
        ModelLifecyclePublicationService publication = mock(ModelLifecyclePublicationService.class);
        ModelingVNextApplicationService runtime = mock(ModelingVNextApplicationService.class);
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            compiler,
            registrations,
            testEvidence,
            publication,
            runtime,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        UUID releaseId = UUID.randomUUID();
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);

        LifecycleEventView release = release(releaseId, "PARTIAL");
        LifecycleEventView published = release(releaseId, "PUBLISHED");
        when(lifecycle.findEvent("tenant-a", releaseId)).thenReturn(Optional.of(release));
        when(lifecycle.findEvent(releaseId)).thenReturn(Optional.of(release), Optional.of(published));
        when(lifecycle.listArtifacts("tenant-a", MODEL_ID, 7)).thenReturn(List.of());
        when(lifecycle.startRegistration(eq(releaseId), any(RegistrationStep.class), eq(NOW))).thenAnswer(invocation ->
            registration(releaseId, invocation.getArgument(1), "RUNNING", null, 1)
        );

        List<RegistrationStepView> partial = List.of(
            registration(releaseId, RegistrationStep.CATALOG_ASSET, "SUCCEEDED", "catalog-1", 1),
            registration(releaseId, RegistrationStep.BI_DATASET, "FAILED", null, 1),
            registration(releaseId, RegistrationStep.LINEAGE, "SUCCEEDED", "lineage-1", 1)
        );
        List<RegistrationStepView> complete = List.of(
            registration(releaseId, RegistrationStep.CATALOG_ASSET, "SUCCEEDED", "catalog-1", 1),
            registration(releaseId, RegistrationStep.BI_DATASET, "SUCCEEDED", "dataset-1", 2),
            registration(releaseId, RegistrationStep.LINEAGE, "SUCCEEDED", "lineage-1", 1)
        );
        when(lifecycle.listRegistrations(releaseId)).thenReturn(List.of(), partial, partial, complete);

        AtomicInteger datasetAttempts = new AtomicInteger();
        when(
            registrations.register(
                any(RegistrationStep.class),
                eq("tenant-a"),
                eq("alice"),
                eq(releaseId),
                eq(model),
                eq(1),
                eq(CHECKSUM),
                anyList(),
                nullable(String.class)
            )
        ).thenAnswer(invocation -> {
            RegistrationStep step = invocation.getArgument(0);
            if (step == RegistrationStep.BI_DATASET && datasetAttempts.getAndIncrement() == 0) {
                throw new IllegalStateException("BI registration unavailable");
            }
            return new ModelReleaseRegistrationPort.RegistrationResult(step.name().toLowerCase());
        });

        ModelLifecycleContract.ReleaseView first = service.retryRegistration("tenant-a", "alice", MODEL_ID, releaseId);
        ModelLifecycleContract.ReleaseView retried = service.retryRegistration("tenant-a", "alice", MODEL_ID, releaseId);

        assertThat(first.status()).isEqualTo("PARTIAL");
        assertThat(retried.status()).isEqualTo("PUBLISHED");
        assertThat(datasetAttempts.get()).isEqualTo(2);
        verify(registrations, times(4)).register(
            any(RegistrationStep.class),
            eq("tenant-a"),
            eq("alice"),
            eq(releaseId),
            eq(model),
            eq(1),
            eq(CHECKSUM),
            anyList(),
            nullable(String.class)
        );
        verify(lifecycle).completeRegistration(
            eq(releaseId),
            eq(RegistrationStep.BI_DATASET),
            eq("FAILED"),
            nullable(String.class),
            eq("BI registration unavailable"),
            eq(NOW)
        );
        verify(lifecycle).completeRegistration(
            eq(releaseId),
            eq(RegistrationStep.BI_DATASET),
            eq("SUCCEEDED"),
            eq("bi_dataset"),
            nullable(String.class),
            eq(NOW)
        );
    }

    private static LifecycleEventView release(UUID releaseId, String status) {
        return new LifecycleEventView(
            releaseId,
            MODEL_ID,
            PLAN_ID,
            7,
            CHECKSUM,
            EventType.RELEASE,
            status,
            "release-7",
            "alice",
            "release",
            null,
            Map.of("implementationRevision", 1, "implementationChecksum", CHECKSUM),
            NOW
        );
    }

    private static RegistrationStepView registration(
        UUID releaseId,
        RegistrationStep step,
        String status,
        String externalRef,
        int attempts
    ) {
        return new RegistrationStepView(
            UUID.randomUUID(),
            releaseId,
            step,
            status,
            externalRef,
            attempts,
            "FAILED".equals(status) ? "BI registration unavailable" : null,
            NOW
        );
    }

    private static ArtifactView artifact(String type) {
        return new ArtifactView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, ImplementationMode.DBT_MANAGED,
            type, type.toLowerCase(), "b".repeat(64), "COMPILED"
        );
    }
}
