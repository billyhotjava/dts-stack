package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleCommandReceiptRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ArtifactWrite;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ClaimImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.EventType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.LifecycleEventView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.TestEvidenceCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.GateView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecStageGateService.Stage;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelLifecycleServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final String CHECKSUM = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-20T08:00:00Z");
    private static final String SYSTEM_PROJECT_KEY = "dts";
    private static final String SYSTEM_DBT_UNIQUE_ID =
        "model." + SYSTEM_PROJECT_KEY + ".model_30000000_0000_0000_0000_000000000001";

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
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        ModelImplementationInputPolicy adapter = new ModelImplementationInputPolicy(
            modelSpecs, modelSpecRepository, lifecycle, mock(ModelSpecSourceValidationPort.class)
        );
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            adapter,
            mock(ModelLifecycleCommandReceiptRepository.class),
            null,
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
    void validationReturnsTheCanonicalExecutionPlanForTheRealUiSettings() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.modelType()).thenReturn(ModelType.DIMENSION);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(model.fields()).thenReturn(
            List.of(new ModelField("calendar_date", "date", false, null, FieldRole.KEY, null))
        );
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        ModelImplementationInputPolicy adapter = new ModelImplementationInputPolicy(
            modelSpecs, modelSpecRepository, lifecycle, mock(ModelSpecSourceValidationPort.class)
        );
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            adapter,
            mock(ModelLifecycleCommandReceiptRepository.class),
            null,
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
                Map.of(
                    "targetPhysicalName", "dwd_calendar_day",
                    "loadStrategy", "FULL",
                    "partitionFields", List.of(),
                    "retentionDays", 365
                ),
                ImplementationMode.DESIGNER_GENERATED,
                "table",
                "validate-calendar"
            )
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.code()).isEqualTo("MODEL_IMPLEMENTATION_VALID");
        assertThat(result.blockers()).isEmpty();
        assertThat(result.executionPlan()).satisfies(plan -> {
            assertThat(plan.nodeUniqueId()).isEqualTo(
                "model.dts.model_30000000_0000_0000_0000_000000000001"
            );
            assertThat(plan.targetIdentifier()).isEqualTo("dwd_calendar_day");
            assertThat(plan.effectiveMaterialization()).isEqualTo("table");
        });

        SaveImplementationCommand unsupportedPartition = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(
                "targetPhysicalName", "dwd_calendar_day",
                "loadStrategy", "FULL",
                "partitionFields", List.of("calendar_date")
            ),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            "save-calendar"
        );
        assertThatThrownBy(() -> service.saveImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 0, null),
            "plan_finance",
            "model.plan_finance.model_calendar_day",
            unsupportedPartition
        ))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("IMPLEMENTATION_PARTITION_UNSUPPORTED");
        verify(lifecycle, never()).saveImplementation(
            eq("tenant-a"),
            eq("alice"),
            eq(model),
            any(String.class),
            any(String.class),
            any(SaveImplementationCommand.class),
            anyInt(),
            nullable(String.class),
            any(Instant.class)
        );
    }

    @Test
    void importedDbtCommitTransitionsThePinnedDesignerImplementation() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelLifecycleCommandReceiptRepository receipts = mock(ModelLifecycleCommandReceiptRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecView dbtManagedModel = mock(ModelSpecView.class);
        when(dbtManagedModel.id()).thenReturn(MODEL_ID);
        when(dbtManagedModel.planId()).thenReturn(PLAN_ID);
        when(dbtManagedModel.revision()).thenReturn(3);
        when(dbtManagedModel.checksum()).thenReturn("b".repeat(64));
        when(dbtManagedModel.status()).thenReturn(ModelStatus.DRAFT);
        when(dbtManagedModel.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(dbtManagedModel.materialization()).thenReturn("table");
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(dbtManagedModel);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID))
            .thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        when(receipts.payloadHash(any())).thenReturn("e".repeat(64));

        String projectKey = "prjdemo";
        String dbtUniqueId = "model.prjdemo.project_task_snapshot";
        String designerImplementationChecksum = "c".repeat(64);
        SaveImplementationCommand command = new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("projectKey", projectKey, "dbtUniqueId", dbtUniqueId))),
            List.of(),
            Map.of(),
            ImplementationMode.DBT_MANAGED,
            "table",
            "commit-dbt-draft"
        );
        ImplementationView designer = new ImplementationView(
            UUID.fromString("60000000-0000-0000-0000-000000000010"),
            MODEL_ID,
            PLAN_ID,
            2,
            CHECKSUM,
            ImplementationMode.DESIGNER_GENERATED,
            SYSTEM_PROJECT_KEY,
            SYSTEM_DBT_UNIQUE_ID,
            "ACTIVE",
            1,
            designerImplementationChecksum,
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(),
            "table"
        );
        ImplementationView transitioned = new ImplementationView(
            designer.id(),
            MODEL_ID,
            PLAN_ID,
            3,
            dbtManagedModel.checksum(),
            ImplementationMode.DBT_MANAGED,
            projectKey,
            dbtUniqueId,
            "ACTIVE",
            2,
            "d".repeat(64),
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            command.settings(),
            command.materialization()
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID))
            .thenReturn(Optional.of(designer), Optional.of(transitioned));
        when(lifecycle.transitionDesignerImplementationToDbtManaged(
            eq("tenant-a"), eq("alice"), eq(dbtManagedModel), eq(projectKey), eq(dbtUniqueId), eq(command),
            eq(2), eq(CHECKSUM), eq(1), eq(designerImplementationChecksum), eq(NOW)
        )).thenReturn(1);
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            mock(ModelImplementationInputPolicy.class),
            receipts,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ImplementationView result = service.saveImportedDbtImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 3, dbtManagedModel.checksum()),
            ModelStatus.DRAFT,
            new ExpectedImplementationVersion(MODEL_ID, 1, designerImplementationChecksum),
            projectKey,
            dbtUniqueId,
            command
        );

        assertThat(result).isEqualTo(transitioned);
        verify(lifecycle).transitionDesignerImplementationToDbtManaged(
            "tenant-a", "alice", dbtManagedModel, projectKey, dbtUniqueId, command,
            2, CHECKSUM, 1, designerImplementationChecksum, NOW
        );
        verify(lifecycle, never()).saveImportedDbtImplementation(
            any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any()
        );
    }

    @Test
    void auditsCanonicalImplementationSaveAndOwnershipConversion() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelImplementationInputPolicy inputPolicy = mock(ModelImplementationInputPolicy.class);
        ModelLifecycleCommandReceiptRepository receipts = mock(ModelLifecycleCommandReceiptRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecView model = canonicalDimension(modelSpecs, modelSpecRepository, writeAccess);
        SaveImplementationCommand command = generatedCommand("save-calendar");
        when(inputPolicy.pinCurrentUpstreamImplementations("tenant-a", command)).thenReturn(command);
        when(inputPolicy.validate("tenant-a", model, command))
            .thenReturn(new ModelImplementationInputPolicy.ValidationResult(true, null));

        ImplementationView saved = implementation(
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            ImplementationMode.DESIGNER_GENERATED,
            1,
            "b".repeat(64),
            command
        );
        ImplementationView dbtManaged = implementation(
            saved.id(),
            ImplementationMode.DBT_MANAGED,
            1,
            "c".repeat(64),
            command
        );
        ImplementationView converted = implementation(
            saved.id(),
            ImplementationMode.DESIGNER_GENERATED,
            2,
            "d".repeat(64),
            command
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(
            Optional.empty(),
            Optional.of(saved),
            Optional.of(dbtManaged),
            Optional.of(converted)
        );
        when(lifecycle.saveImplementation(
            eq("tenant-a"), eq("alice"), eq(model), eq(SYSTEM_PROJECT_KEY), eq(SYSTEM_DBT_UNIQUE_ID),
            eq(command), eq(0), nullable(String.class), eq(NOW)
        )).thenReturn(1);
        when(lifecycle.convertImplementationOwnership(
            eq("tenant-a"), eq("alice"), eq(model), eq(SYSTEM_PROJECT_KEY), eq(SYSTEM_DBT_UNIQUE_ID),
            eq(command), eq(1), eq("c".repeat(64)), eq(NOW)
        )).thenReturn(1);
        ModelLifecycleService service = auditedService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            inputPolicy,
            receipts,
            audit
        );

        service.saveImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 0, null),
            "client-project-must-not-win",
            "model.client.must_not_win",
            command
        );
        service.convertToDesignerGenerated(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 1, "c".repeat(64)),
            "client-project-must-not-win",
            "model.client.must_not_win",
            command
        );

        verify(audit).auditActionStrict(
            eq("MODEL_IMPLEMENTATION_SAVE"),
            eq(AuditStage.SUCCESS),
            eq("MODEL_IMPLEMENTATION_SAVE:" + MODEL_ID + ":1"),
            any(Map.class)
        );
        verify(receipts).append(
            eq("tenant-a"), eq(MODEL_ID), eq("SAVE"), eq("save-calendar"), nullable(String.class),
            eq(saved), eq("alice"), eq(NOW)
        );
        verify(receipts).append(
            eq("tenant-a"), eq(MODEL_ID), eq("OWNERSHIP_CONVERT"), eq("save-calendar"), nullable(String.class),
            eq(converted), eq("alice"), eq(NOW)
        );
        verify(audit).auditActionStrict(
            eq("MODEL_IMPLEMENTATION_OWNERSHIP_CONVERT"),
            eq(AuditStage.SUCCESS),
            eq("MODEL_IMPLEMENTATION_OWNERSHIP_CONVERT:" + MODEL_ID + ":2"),
            any(Map.class)
        );
    }

    @Test
    void replaysTheExactSavedImplementationAndRejectsDifferentPayloadWithoutMutationOrAudit() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelLifecycleCommandReceiptRepository receipts = mock(ModelLifecycleCommandReceiptRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        AuditService audit = mock(AuditService.class);
        canonicalDimension(modelSpecs, modelSpecRepository, writeAccess);
        SaveImplementationCommand command = generatedCommand("save-once");
        ImplementationView original = implementation(
            UUID.fromString("60000000-0000-0000-0000-000000000099"),
            ImplementationMode.DESIGNER_GENERATED,
            3,
            "9".repeat(64),
            command
        );
        when(receipts.payloadHash(any())).thenReturn("1".repeat(64), "2".repeat(64));
        when(receipts.find("tenant-a", MODEL_ID, "SAVE", "save-once")).thenReturn(
            Optional.of(new ModelLifecycleCommandReceiptRepository.Receipt("1".repeat(64), original))
        );
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            mock(ModelSpecStageGateService.class),
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            mock(ModelImplementationInputPolicy.class),
            receipts,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        ImplementationView replay = service.saveImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 2, "8".repeat(64)),
            "ignored-client-project",
            "model.ignored.client",
            command
        );
        assertThat(replay).isSameAs(original);

        assertThatThrownBy(() -> service.saveImplementation(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 2, "8".repeat(64)),
            "ignored-client-project",
            "model.ignored.client",
            command
        ))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_IMPLEMENTATION_IDEMPOTENCY_CONFLICT");

        verifyNoInteractions(lifecycle, audit);
        verify(receipts, never()).append(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void allImplementationWritesAuthorizeBeforeReceiptLookupAndStaleVersionChecks() {
        for (boolean receiptExists : List.of(false, true)) {
            ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
            ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
            ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
            ModelLifecycleCommandReceiptRepository receipts = mock(ModelLifecycleCommandReceiptRepository.class);
            ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
            ModelSpecView model = canonicalDimension(modelSpecs, modelSpecRepository, writeAccess);
            when(writeAccess.canMaintain("tenant-a", PLAN_ID, "mallory")).thenReturn(false);
            SaveImplementationCommand command = generatedCommand("unauthorized-write");
            if (receiptExists) {
                ImplementationView replay = implementation(
                    UUID.fromString("60000000-0000-0000-0000-000000000098"),
                    ImplementationMode.DESIGNER_GENERATED,
                    3,
                    "9".repeat(64),
                    command
                );
                when(receipts.find(any(String.class), any(UUID.class), any(String.class), any(String.class)))
                    .thenReturn(Optional.of(new ModelLifecycleCommandReceiptRepository.Receipt("1".repeat(64), replay)));
            }
            ModelLifecycleService service = new ModelLifecycleService(
                modelSpecs,
                modelSpecRepository,
                lifecycle,
                mock(ModelSpecStageGateService.class),
                writeAccess,
                mock(ModelLifecycleCompilerPort.class),
                mock(ModelLifecycleTestEvidencePort.class),
                mock(ModelImplementationInputPolicy.class),
                receipts,
                mock(AuditService.class),
                Clock.fixed(NOW, ZoneOffset.UTC)
            );
            ExpectedVersion staleModel = new ExpectedVersion(MODEL_ID, 6, "f".repeat(64));
            ExpectedImplementationVersion staleImplementation = new ExpectedImplementationVersion(
                MODEL_ID,
                2,
                "e".repeat(64)
            );

            assertPlanForbidden(() -> service.saveImplementation(
                "tenant-a", "mallory", MODEL_ID, staleModel, staleImplementation,
                "ignored", "model.ignored.save", command
            ));
            assertPlanForbidden(() -> service.saveImportedDbtImplementation(
                "tenant-a", "mallory", MODEL_ID, staleModel, ModelStatus.DRAFT, staleImplementation,
                "ignored", "model.ignored.import", command
            ));
            assertPlanForbidden(() -> service.convertToDesignerGenerated(
                "tenant-a", "mallory", MODEL_ID, staleModel, staleImplementation,
                "ignored", "model.ignored.convert", command
            ));
            assertPlanForbidden(() -> service.claim(
                "tenant-a", "mallory", MODEL_ID, staleModel, staleImplementation,
                new ClaimImplementationCommand(
                    ImplementationMode.DESIGNER_GENERATED,
                    "ignored",
                    "model.ignored.claim",
                    "unauthorized-claim"
                )
            ));

            verifyNoInteractions(receipts);
        }
    }

    private static void assertPlanForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable invocation) {
        assertThatThrownBy(invocation)
            .isInstanceOf(ModelSpecException.class)
            .satisfies(error -> {
                ModelSpecException exception = (ModelSpecException) error;
                assertThat(exception.kind()).isEqualTo(ModelSpecException.Kind.FORBIDDEN);
                assertThat(exception.code()).isEqualTo("MODEL_SPEC_PLAN_FORBIDDEN");
            });
    }

    @Test
    void auditsClaimAndCompileUsingPersistedRevisionBoundFacts() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelSpecStageGateService gates = mock(ModelSpecStageGateService.class);
        ModelLifecycleCompilerPort compiler = mock(ModelLifecycleCompilerPort.class);
        ModelLifecycleCommandReceiptRepository receipts = mock(ModelLifecycleCommandReceiptRepository.class);
        AuditService audit = mock(AuditService.class);
        ModelSpecView model = canonicalDimension(modelSpecs, modelSpecRepository, writeAccess);
        SaveImplementationCommand implementationCommand = generatedCommand("claimed");
        ImplementationView claimed = implementation(
            UUID.fromString("60000000-0000-0000-0000-000000000002"),
            ImplementationMode.DESIGNER_GENERATED,
            1,
            "b".repeat(64),
            implementationCommand
        );
        GateView ready = new GateView(MODEL_ID, 7, CHECKSUM, Stage.IMPLEMENTATION_READY, GateStatus.READY, List.of());
        when(gates.evaluateAll("tenant-a", MODEL_ID)).thenReturn(List.of(ready));
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(
            Optional.empty(),
            Optional.of(claimed),
            Optional.of(claimed)
        );
        when(lifecycle.claimImplementation(
            "tenant-a", "alice", model, ImplementationMode.DESIGNER_GENERATED, "pjm", "model.pjm.calendar",
            "claim-7", 0, null, NOW
        )).thenReturn(1);
        when(lifecycle.lockImplementation("tenant-a", MODEL_ID, claimed)).thenReturn(true);
        List<ArtifactWrite> writes = List.of(
            new ArtifactWrite("SQL", "models/private/calendar.sql", "1".repeat(64), "select 1"),
            new ArtifactWrite("SCHEMA", "models/private/calendar.yml", "2".repeat(64), "version: 2"),
            new ArtifactWrite("TEST", "tests/private/calendar.sql", "3".repeat(64), "select 0")
        );
        when(compiler.compile("tenant-a", model, claimed)).thenReturn(writes);
        UUID eventId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        LifecycleEventView event = new LifecycleEventView(
            eventId,
            MODEL_ID,
            PLAN_ID,
            7,
            CHECKSUM,
            EventType.COMPILE,
            "PASSED",
            "compile-7",
            "alice",
            null,
            SYSTEM_DBT_UNIQUE_ID,
            Map.of("artifactCount", 3),
            NOW
        );
        when(lifecycle.recordEvent(
            eq("tenant-a"), eq("alice"), eq(model), eq(EventType.COMPILE), eq("PASSED"), eq("compile-7"),
            nullable(String.class), eq(SYSTEM_DBT_UNIQUE_ID), any(Map.class), eq(claimed), eq(NOW)
        )).thenReturn(event);
        when(lifecycle.listArtifacts("tenant-a", MODEL_ID, 7)).thenReturn(List.of());
        ModelLifecycleService service = auditedService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            compiler,
            mock(ModelLifecycleTestEvidencePort.class),
            mock(ModelImplementationInputPolicy.class),
            receipts,
            audit
        );

        service.claim(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 0, null),
            new ClaimImplementationCommand(ImplementationMode.DESIGNER_GENERATED, "pjm", "model.pjm.calendar", "claim-7")
        );
        service.compile(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 1, "b".repeat(64)),
            "compile-7"
        );

        verify(audit).auditActionStrict(
            eq("MODEL_IMPLEMENTATION_CLAIM"),
            eq(AuditStage.SUCCESS),
            eq("MODEL_IMPLEMENTATION_CLAIM:" + MODEL_ID + ":1"),
            any(Map.class)
        );
        verify(receipts).append(
            eq("tenant-a"), eq(MODEL_ID), eq("CLAIM"), eq("claim-7"), nullable(String.class),
            eq(claimed), eq("alice"), eq(NOW)
        );
        ArgumentCaptor<Object> compilePayload = ArgumentCaptor.forClass(Object.class);
        verify(audit).auditAction(
            eq("MODEL_IMPLEMENTATION_COMPILE"),
            eq(AuditStage.SUCCESS),
            eq("MODEL_IMPLEMENTATION_COMPILE:" + eventId),
            compilePayload.capture()
        );
        assertThat(compilePayload.getValue()).isInstanceOfSatisfying(Map.class, value ->
            assertThat(value)
                .containsEntry("artifactCount", 3)
                .containsEntry("outcome", "PASSED")
                .doesNotContainKeys("artifacts", "sql", "content", "path", "dbtUniqueId", "checksum")
        );
    }

    @Test
    void recordsTestStatusFromPersistedDbtRunInsteadOfClientAssertion() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecStageGateService gates = mock(ModelSpecStageGateService.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelLifecycleCompilerPort compiler = mock(ModelLifecycleCompilerPort.class);
        ModelLifecycleTestEvidencePort testEvidence = mock(ModelLifecycleTestEvidencePort.class);
        AuditService audit = mock(AuditService.class);
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            compiler,
            testEvidence,
            mock(ModelImplementationInputPolicy.class),
            mock(ModelLifecycleCommandReceiptRepository.class),
            audit,
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
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        ImplementationView owner = new ImplementationView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, ImplementationMode.DBT_MANAGED, "pjm", "model.pjm.fact", "ACTIVE",
            1, CHECKSUM, InputMode.GENERATED, List.of(new GeneratedInput("DBT", Map.of())), List.of(), Map.of(), "table"
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(owner));
        when(lifecycle.lockImplementation("tenant-a", MODEL_ID, owner)).thenReturn(true);
        when(lifecycle.listArtifacts("tenant-a", MODEL_ID, 7)).thenReturn(List.of(
            artifact("SQL"), artifact("SCHEMA")
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
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(audit).auditAction(
            eq("MODEL_IMPLEMENTATION_TEST_EVIDENCE_RECORD"),
            eq(AuditStage.SUCCESS),
            eq("MODEL_IMPLEMENTATION_TEST_EVIDENCE_RECORD:" + persisted.id()),
            payload.capture()
        );
        assertThat(payload.getValue()).isInstanceOfSatisfying(Map.class, value ->
            assertThat(value)
                .containsEntry("actor", "alice")
                .containsEntry("tenantId", "tenant-a")
                .containsEntry("modelSpecId", MODEL_ID)
                .containsEntry("modelRevision", 7)
                .containsEntry("implementationRevision", 1)
                .containsEntry("ownership", "DBT_MANAGED")
                .containsEntry("outcome", "PASSED")
                .containsEntry("artifactCount", 2)
                .doesNotContainKeys(
                    "comment",
                    "externalRunId",
                    "checksum",
                    "dbtUniqueId",
                    "projectKey",
                    "sql",
                    "path",
                    "token",
                    "credential"
                )
        );

        when(lifecycle.hasPassedEvidence("tenant-a", MODEL_ID, 7, 1, CHECKSUM, EventType.COMPILE)).thenReturn(false);
        assertThatThrownBy(() ->
            service.recordTest(
                "tenant-a",
                "alice",
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
                new ExpectedImplementationVersion(MODEL_ID, 1, CHECKSUM),
                new TestEvidenceCommand("PASSED", "dbt-run-8", "verified", "test-8")
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_TEST_COMPILE_REQUIRED");
    }

    @Test
    void rejectsDbtCompilationWhenTheImportedArtifactSetContainsUnexpectedTypes() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecRepository modelSpecRepository = mock(ModelSpecRepository.class);
        ModelLifecycleRepository lifecycle = mock(ModelLifecycleRepository.class);
        ModelSpecStageGateService gates = mock(ModelSpecStageGateService.class);
        ModelSpecPlanWriteAccessPort writeAccess = mock(ModelSpecPlanWriteAccessPort.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.implementationMode()).thenReturn(ImplementationMode.DBT_MANAGED);
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        ImplementationView owner = new ImplementationView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, ImplementationMode.DBT_MANAGED, "pjm", "model.pjm.fact", "ACTIVE",
            1, CHECKSUM, InputMode.GENERATED, List.of(new GeneratedInput("DBT", Map.of())), List.of(), Map.of(), "table"
        );
        when(lifecycle.findImplementation("tenant-a", MODEL_ID)).thenReturn(Optional.of(owner));
        when(lifecycle.lockImplementation("tenant-a", MODEL_ID, owner)).thenReturn(true);
        when(lifecycle.currentArtifactTypes("tenant-a", MODEL_ID, owner)).thenReturn(Set.of("SQL", "SCHEMA", "DOC"));
        when(gates.evaluateAll("tenant-a", MODEL_ID)).thenReturn(
            List.of(
                new ModelSpecStageGateService.GateView(
                    MODEL_ID,
                    7,
                    CHECKSUM,
                    ModelSpecStageGateService.Stage.IMPLEMENTATION_READY,
                    ModelSpecStageGateService.GateStatus.READY,
                    List.of()
                )
            )
        );
        ModelLifecycleService service = new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            mock(ModelLifecycleCompilerPort.class),
            mock(ModelLifecycleTestEvidencePort.class),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() ->
            service.compile(
                "tenant-a",
                "alice",
                MODEL_ID,
                new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
                new ExpectedImplementationVersion(MODEL_ID, 1, CHECKSUM),
                "compile-7"
            )
        )
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo("MODEL_DBT_IMPORT_REQUIRED");

        when(lifecycle.currentArtifactTypes("tenant-a", MODEL_ID, owner)).thenReturn(
            Set.of("SQL", "SCHEMA", "CONFIG", "DEPENDENCY")
        );
        LifecycleEventView compiled = new LifecycleEventView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, EventType.COMPILE, "PASSED", "compile-ui-draft",
            "alice", null, owner.dbtUniqueId(), Map.of("artifactCount", 4, "ownership", "DBT_MANAGED"), NOW
        );
        when(lifecycle.recordEvent(
            eq("tenant-a"), eq("alice"), eq(model), eq(EventType.COMPILE), eq("PASSED"), eq("compile-ui-draft"),
            nullable(String.class), eq(owner.dbtUniqueId()), any(Map.class), eq(owner), eq(NOW)
        )).thenReturn(compiled);
        when(lifecycle.listArtifacts("tenant-a", MODEL_ID, 7)).thenReturn(List.of());

        assertThat(service.compile(
            "tenant-a",
            "alice",
            MODEL_ID,
            new ExpectedVersion(MODEL_ID, 7, CHECKSUM),
            new ExpectedImplementationVersion(MODEL_ID, 1, CHECKSUM),
            "compile-ui-draft"
        ).event()).isEqualTo(compiled);
        verify(lifecycle).promoteImportedArtifactsToCompiled("tenant-a", MODEL_ID, owner, NOW);
    }

    private static ModelSpecView canonicalDimension(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelSpecPlanWriteAccessPort writeAccess
    ) {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(7);
        when(model.checksum()).thenReturn(CHECKSUM);
        when(model.modelType()).thenReturn(ModelType.DIMENSION);
        when(model.implementationMode()).thenReturn(ImplementationMode.DESIGNER_GENERATED);
        when(model.materialization()).thenReturn("table");
        when(model.fields()).thenReturn(
            List.of(new ModelField("calendar_date", "date", false, null, FieldRole.KEY, null))
        );
        when(modelSpecs.get("tenant-a", MODEL_ID)).thenReturn(model);
        when(modelSpecRepository.lockPlan("tenant-a", PLAN_ID)).thenReturn(Optional.of(new PlanState(PLAN_ID, "DRAFT")));
        when(writeAccess.canMaintain("tenant-a", PLAN_ID, "alice")).thenReturn(true);
        when(writeAccess.canEdit("tenant-a", MODEL_ID, "alice")).thenReturn(true);
        return model;
    }

    private static SaveImplementationCommand generatedCommand(String idempotencyKey) {
        return new SaveImplementationCommand(
            InputMode.GENERATED,
            List.of(new GeneratedInput("DATE_DIMENSION", Map.of())),
            List.of(),
            Map.of(
                "targetPhysicalName", "dwd_calendar_day",
                "loadStrategy", "FULL",
                "partitionFields", List.of()
            ),
            ImplementationMode.DESIGNER_GENERATED,
            "table",
            idempotencyKey
        );
    }

    private static ImplementationView implementation(
        UUID id,
        ImplementationMode ownership,
        int implementationRevision,
        String implementationChecksum,
        SaveImplementationCommand command
    ) {
        return new ImplementationView(
            id,
            MODEL_ID,
            PLAN_ID,
            7,
            CHECKSUM,
            ownership,
            SYSTEM_PROJECT_KEY,
            SYSTEM_DBT_UNIQUE_ID,
            "ACTIVE",
            implementationRevision,
            implementationChecksum,
            command.inputMode(),
            command.inputs(),
            command.fieldMappings(),
            command.settings(),
            command.materialization()
        );
    }

    private static ModelLifecycleService auditedService(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository modelSpecRepository,
        ModelLifecycleRepository lifecycle,
        ModelSpecStageGateService gates,
        ModelSpecPlanWriteAccessPort writeAccess,
        ModelLifecycleCompilerPort compiler,
        ModelLifecycleTestEvidencePort testEvidence,
        ModelImplementationInputPolicy inputPolicy,
        ModelLifecycleCommandReceiptRepository receipts,
        AuditService audit
    ) {
        return new ModelLifecycleService(
            modelSpecs,
            modelSpecRepository,
            lifecycle,
            gates,
            writeAccess,
            compiler,
            testEvidence,
            inputPolicy,
            receipts,
            audit,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private static ArtifactView artifact(String type) {
        return new ArtifactView(
            UUID.randomUUID(), MODEL_ID, PLAN_ID, 7, CHECKSUM, ImplementationMode.DBT_MANAGED,
            type, type.toLowerCase(), "b".repeat(64), "COMPILED"
        );
    }
}
