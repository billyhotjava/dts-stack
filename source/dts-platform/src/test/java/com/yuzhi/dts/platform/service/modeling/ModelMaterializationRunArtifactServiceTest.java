package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository.RunGroupRecord;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.AvailabilityPin;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationAvailabilityPinRepository.PinnedSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationSourceSnapshotRepository.InputSnapshot;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.SourceBindingState;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository.ObservationWrite;
import com.yuzhi.dts.platform.security.modeling.ModelingIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogMaterializationSourceAvailabilityPort;
import com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.GenerationCheck;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationSourceAvailabilityGuard.GenerationDrift;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.ResolvedSource;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class ModelMaterializationRunArtifactServiceTest {

    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID PLAN_ID =
        UUID.fromString("60000000-0000-0000-0000-000000000006");
    private static final UUID MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SECOND_MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000006");
    private static final UUID INVOCATION_ID =
        UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final String BUNDLE = "a".repeat(64);
    private static final String UNIQUE_ID =
        "model.dts_test.model_30000000_0000_0000_0000_000000000003";
    private static final String RELEASE_SCOPED_UNIQUE_ID =
        "model.dts.model_30000000_0000_0000_0000_000000000003";
    private static final String SECOND_UNIQUE_ID =
        "model.dts_test.model_30000000_0000_0000_0000_000000000006";
    private static final Instant NOW =
        Instant.parse("2026-07-27T15:00:00Z");

    @TempDir
    Path project;

    private ModelMaterializationRunRepository runs;
    private ModelMaterializationBuildRepository builds;
    private ModelMaterializationSourceAvailabilityGuard sourceAvailability;
    private DbtScopedProjectService scoped;
    private PhysicalRelationInspector inspector;
    private PhysicalRelationInspectorRegistry inspectors;
    private PhysicalRelationObservationRepository observations;
    private ModelReleaseCandidateService candidates;
    private CandidateQualityAssetRegistrationService qualityAssets;
    private AuditService auditService;
    private ModelMaterializationRunArtifactService service;

    @BeforeEach
    void setUp() {
        runs = mock(ModelMaterializationRunRepository.class);
        builds = mock(ModelMaterializationBuildRepository.class);
        sourceAvailability = mock(
            ModelMaterializationSourceAvailabilityGuard.class
        );
        scoped = mock(DbtScopedProjectService.class);
        inspector = mock(PhysicalRelationInspector.class);
        inspectors = mock(
            PhysicalRelationInspectorRegistry.class
        );
        observations = mock(
            PhysicalRelationObservationRepository.class
        );
        candidates = mock(ModelReleaseCandidateService.class);
        qualityAssets = mock(CandidateQualityAssetRegistrationService.class);
        auditService = mock(AuditService.class);
        service = new ModelMaterializationRunArtifactService(
            runs,
            builds,
            sourceAvailability,
            scoped,
            inspectors,
            observations,
            candidates,
            qualityAssets,
            auditService,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            passthroughTransactions()
        );
        when(runs.findRunGroup(GROUP_ID)).thenReturn(
            java.util.Optional.of(
                runGroup(3, "BUILDING", "SUBMITTED", null)
            )
        );
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
        when(
            sourceAvailability.checkPinnedCurrentForUpdate(GROUP_ID)
        ).thenReturn(GenerationCheck.currentCheck());
        ModelReleaseCandidateContract.CandidateView builtCandidate = mock(ModelReleaseCandidateContract.CandidateView.class);
        when(builtCandidate.status()).thenReturn(ModelLifecycleContract.DeliveryStatus.BUILT);
        ModelReleaseCandidateContract.CommandResult transitionResult = new ModelReleaseCandidateContract.CommandResult(builtCandidate, false, List.of());
        when(candidates.transition(any(), any(), any(), any())).thenReturn(transitionResult);
        when(scoped.verifyCandidateProject(BUNDLE))
            .thenReturn(project);
        when(inspectors.require("postgres"))
            .thenReturn(inspector);
        when(inspector.adapter()).thenReturn("postgres");
        when(inspector.dataTypeMatches(any(), any()))
            .thenAnswer(invocation ->
                invocation
                    .getArgument(0, String.class)
                    .equals(
                        invocation.getArgument(1, String.class)
                    )
            );
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(
                        1,
                        "project_id",
                        "uuid",
                        false
                    ),
                    new PhysicalColumn(
                        2,
                        "amount",
                        "numeric(18,2)",
                        true
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            )
        );
    }

    @ParameterizedTest
    @CsvSource({
        "sync, CURRENT", "finalize, CURRENT",
        "sync, OTHER_PLAN", "finalize, OTHER_PLAN",
        "sync, OTHER_TENANT", "finalize, OTHER_TENANT",
        "sync, VERSION_CHANGED", "finalize, VERSION_CHANGED"
    })
    void machineCallbacksValidateRealSourcesWithinThePersistedExecutionScope(String callback, String sourceScenario)
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(runs.finalizeSucceeded(GROUP_ID, NOW)).thenReturn(1);
        SourceReferenceResolver resolver = installRealSourceGuard(sourceScenario);
        assertThat(ModelingIdentity.optional()).isEmpty();
        assertThat(ModelingSystemExecution.permits("tenant-a", PLAN_ID)).isFalse();

        if ("CURRENT".equals(sourceScenario)) {
            assertThat(invokeMachineCallback(callback).status()).isEqualTo("BUILT");
            verify(resolver, times("sync".equals(callback) ? 2 : 1)).resolveForExecution(
                eq(SourceType.CATALOG_TABLE), any(SourceLocator.class), eq(new AccessContext("tenant-a", "SYSTEM", "dept-a"))
            );
        } else {
            assertThatThrownBy(() -> invokeMachineCallback(callback))
                .isInstanceOf(ModelMaterializationRuntimeException.class)
                .extracting(error -> ((ModelMaterializationRuntimeException) error).code())
                .isEqualTo(ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE);
            verify(runs, never()).markDbtSucceeded(any(), any(), anyInt(), any());
            verify(runs, never()).finalizeSucceeded(any(), any());
            verify(observations, never()).appendAll(any());
        }

        // Both normal completion and a rejected source must clear the callback's ThreadLocal.
        assertThat(ModelingSystemExecution.permits("tenant-a", PLAN_ID)).isFalse();
        assertThat(ModelingIdentity.optional()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "sync", "finalize" })
    void callbackWithoutPersistedPlanFailsBeforeSourceValidation(String callback) {
        RunGroupRecord missingPlan = mock(RunGroupRecord.class);
        when(missingPlan.groupId()).thenReturn(GROUP_ID);
        when(missingPlan.tenantId()).thenReturn("tenant-a");
        when(runs.findRunGroup(GROUP_ID)).thenReturn(Optional.of(missingPlan));

        assertThatThrownBy(() -> invokeMachineCallback(callback))
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error -> ((ModelMaterializationRuntimeException) error).code())
            .isEqualTo("MODEL_DBT_RUN_IDENTITY_MISMATCH");
        verify(sourceAvailability, never()).checkPinnedCurrentForUpdate(any());
        verify(runs, never()).finalizeSucceeded(any(), any());
        assertThat(ModelingSystemExecution.permits("tenant-a", null)).isFalse();
    }

    private ModelMaterializationRunArtifactService.RunArtifactView invokeMachineCallback(String callback) {
        return "sync".equals(callback)
            ? service.syncAndProbe(GROUP_ID, new ModelMaterializationRunArtifactService.SyncProbeCommand("RELEASE_BUILD", BUNDLE))
            : service.finalizeRun(GROUP_ID, new ModelMaterializationRunArtifactService.FinalizeCommand("SUCCEEDED"));
    }

    private SourceReferenceResolver installRealSourceGuard(String sourceScenario) {
        String sourceTenant = "OTHER_TENANT".equals(sourceScenario) ? "tenant-b" : "tenant-a";
        UUID sourcePlan = "OTHER_PLAN".equals(sourceScenario) ? CANDIDATE_ID : PLAN_ID;
        UUID bindingId = UUID.fromString("70000000-0000-0000-0000-000000000007");
        UUID assetId = UUID.fromString("80000000-0000-0000-0000-000000000008");
        SourceLocator locator = new SourceLocator(assetId, null, null, null, null, null, null);
        ModelSpecRepository sourceRepository = mock(ModelSpecRepository.class);
        SourceReferenceResolver resolver = mock(SourceReferenceResolver.class);
        ModelSpecSourceValidationAdapter validation = new ModelSpecSourceValidationAdapter(
            sourceRepository, resolver, mock(WarehousePlanActorProvider.class), new ObjectMapper(), mock(ModelSpecPlanWriteAccessPort.class)
        );
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(any(String.class), eq(UUID.class), eq(sourceTenant), eq(bindingId))).thenReturn(sourcePlan);
        ReflectionTestUtils.setField(validation, "jdbc", jdbc);
        CatalogSourceReferenceReadPort catalog = mock(CatalogSourceReferenceReadPort.class);
        when(catalog.findDatasetAssetKeyByTableId(assetId)).thenReturn(Optional.of("fixture-source"));
        // An ODS source has no modeling producer; use the real scope guard with an empty producer lookup.
        ReflectionTestUtils.setField(validation, "sourceScope", new ModelingSourceScopeGuard(jdbc, catalog, mock(ModelSpecAccessService.class)));
        when(sourceRepository.findSourceBinding(sourceTenant, sourcePlan, bindingId)).thenReturn(Optional.of(new SourceBindingState(
            bindingId, "CATALOG_TABLE", assetId.toString(), "v1", "CONFIRMED",
            "{\"assetId\":\"" + assetId + "\"}", "owner", "dept-a"
        )));
        when(resolver.resolveForExecution(eq(SourceType.CATALOG_TABLE), eq(locator), any(AccessContext.class)))
            .thenReturn(ResolvedSource.available("项目任务贴源", "VERSION_CHANGED".equals(sourceScenario) ? "v2" : "v1"));

        ModelMaterializationSourceSnapshotRepository snapshots = mock(ModelMaterializationSourceSnapshotRepository.class);
        String inputs = "[{\"sourceBindingId\":\"" + bindingId + "\",\"resolvedVersion\":\"v1\"}]";
        when(snapshots.findDispatchInputs(GROUP_ID)).thenReturn(List.of(new InputSnapshot(
            sourceTenant, sourcePlan, MODEL_ID, 7, "c".repeat(64), "PHYSICAL_ASSET", inputs, null
        )));
        ModelMaterializationAvailabilityPinRepository pins = mock(ModelMaterializationAvailabilityPinRepository.class);
        when(pins.lockSnapshot(GROUP_ID)).thenReturn(new PinnedSnapshot(GROUP_ID, true, NOW, 1, List.of(
            new AvailabilityPin(bindingId, CatalogAssetType.DATASET, "fixture-source", "AVAILABLE", 0, 0, "legacy-default", "v1")
        )));
        CatalogMaterializationSourceAvailabilityPort availability = mock(CatalogMaterializationSourceAvailabilityPort.class);
        when(availability.lockAndCompare(any())).thenReturn(List.of());
        ReflectionTestUtils.setField(service, "sourceAvailability", new ModelMaterializationSourceAvailabilityGuard(
            snapshots, validation, availability, pins, new ObjectMapper()
        ));
        return resolver;
    }

    @Test
    void probesSchemaOnlyMaterializationAsAnOrdinaryTable() throws Exception {
        writeArtifacts("success", "b".repeat(64));
        Path manifest = project.resolve("target/manifest.json");
        Files.writeString(manifest, Files.readString(manifest).replace("\"materialized\": \"table\"", "\"materialized\": \"dts_schema_only\""));
        var result = service.syncAndProbe(GROUP_ID, new ModelMaterializationRunArtifactService.SyncProbeCommand("RELEASE_BUILD", BUNDLE));
        assertThat(result.status()).isEqualTo("BUILT");
        verify(qualityAssets, never()).ensureRegistered(any());
    }

    @Test
    void acceptsOnlyRevisionBoundManifestAndSuccessfulModelResult()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));

        var result = service.syncAndProbe(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.SyncProbeCommand(
                "RELEASE_BUILD",
                BUNDLE
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        assertThat(result.dbtInvocationId()).isEqualTo(INVOCATION_ID);
        assertThat(result.modelCount()).isEqualTo(1);
        verify(qualityAssets, never()).ensureRegistered(any());
        verify(runs).markDbtSucceeded(
            GROUP_ID,
            INVOCATION_ID,
            1,
            NOW
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ObservationWrite>> observationsCaptor =
            ArgumentCaptor.forClass(List.class);
        verify(observations).appendAll(
            observationsCaptor.capture()
        );
        assertThat(
            observationsCaptor
                .getValue()
                .getFirst()
                .expectedColumnsChecksum()
        )
            .isEqualTo(
                digest(
                    List.of(
                        "project_id\u0000uuid",
                        "amount\u0000numeric(18,2)"
                    )
                )
            )
            .isNotEqualTo(
                digest(List.of("project_id", "amount"))
            );
        verify(runs).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
        verify(candidates, never()).transition(any(), any(), any(), any());
        verify(runs, never()).markFailed(
            GROUP_ID,
            "MODEL_DBT_ARTIFACT_INVALID",
            NOW
        );
        ArgumentCaptor<Object> auditPayload =
            ArgumentCaptor.forClass(Object.class);
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-run:" + GROUP_ID + ":artifacts-synced:" + INVOCATION_ID),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_ARTIFACTS_SYNCED"),
            eq(AuditStage.SUCCESS),
            eq(GROUP_ID.toString()),
            auditPayload.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> safeAuditPayload =
            (Map<String, Object>) auditPayload.getValue();
        assertThat(safeAuditPayload)
            .doesNotContainKeys(
                "checksum",
                "digest",
                "token",
                "credential",
                "selector",
                "path"
            );
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-run:" + GROUP_ID + ":relations-verified:" + INVOCATION_ID),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_RELATIONS_VERIFIED"),
            eq(AuditStage.SUCCESS),
            eq(GROUP_ID.toString()),
            any()
        );
    }

    @Test
    void physicalColumnOrderDoesNotInventSchemaDrift()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(
                        1,
                        "amount",
                        "numeric(18,2)",
                        true
                    ),
                    new PhysicalColumn(
                        2,
                        "project_id",
                        "uuid",
                        false
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            )
        );

        var result = service.syncAndProbe(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.SyncProbeCommand(
                "RELEASE_BUILD",
                BUNDLE
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        verify(runs).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
    }

    @Test
    void acceptsReleaseScopedRuntimePackageWhenImmutableMetaMatches()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        replaceRuntimeUniqueId(RELEASE_SCOPED_UNIQUE_ID);

        var result = service.syncAndProbe(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.SyncProbeCommand(
                "RELEASE_BUILD",
                BUNDLE
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        verify(runs).markDbtSucceeded(
            GROUP_ID,
            INVOCATION_ID,
            1,
            NOW
        );
    }

    @Test
    void auditOutboxFailureDoesNotInventBusinessFailure() throws Exception {
        writeArtifacts("success", "b".repeat(64));
        doThrow(new IllegalStateException("audit unavailable"))
            .when(auditService)
            .auditActionAsStrict(
                eq("airflow"),
                eq("model-materialization-run:" + GROUP_ID + ":artifacts-synced:" + INVOCATION_ID),
                eq(NOW),
                eq("MODEL_MATERIALIZATION_ARTIFACTS_SYNCED"),
                eq(AuditStage.SUCCESS),
                eq(GROUP_ID.toString()),
                any()
            );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Machine audit persistence failed");

        verify(runs, never()).markFailed(eq(GROUP_ID), any(), any());
        verify(candidates, never()).transition(
            eq("tenant-a"),
            eq("service:dts-airflow"),
            eq(CANDIDATE_ID),
            any()
        );
    }

    @Test
    void rejectsManifestThatDoesNotMatchImmutableImplementation()
        throws Exception {
        writeArtifacts("success", "f".repeat(64));

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo("MODEL_DBT_MANIFEST_IDENTITY_MISMATCH");
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_DBT_MANIFEST_IDENTITY_MISMATCH",
            NOW
        );
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-run:" + GROUP_ID + ":failed:MODEL_DBT_MANIFEST_IDENTITY_MISMATCH"),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_RUN_FAILED"),
            eq(AuditStage.FAIL),
            eq(GROUP_ID.toString()),
            any()
        );
        verify(runs, never()).markDbtSucceeded(
            GROUP_ID,
            INVOCATION_ID,
            1,
            NOW
        );
    }

    @Test
    void rejectsSkippedOrMissingDbtModelResult() throws Exception {
        writeArtifacts("skipped", "b".repeat(64));

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo("MODEL_DBT_BUILD_RESULT_FAILED");
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_DBT_BUILD_RESULT_FAILED",
            NOW
        );
        verify(runs).recordDbtResults(
            GROUP_ID,
            INVOCATION_ID,
            Map.of(scope().entries().getFirst().pipelineRunId(), "SKIPPED_DEPENDENCY_FAILED"),
            Map.of(),
            NOW
        );
    }

    @Test
    void preservesDirectFailureAndDownstreamDependencySkipPerCandidateEntry() throws Exception {
        CandidateBuildScope scope = twoEntryScope();
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID)).thenReturn(scope);
        writeTwoEntryArtifacts("error", "skipped");

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand("RELEASE_BUILD", BUNDLE)
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error -> ((ModelMaterializationRuntimeException) error).code())
            .isEqualTo("MODEL_DBT_BUILD_RESULT_FAILED");

        verify(runs).recordDbtResults(
            GROUP_ID,
            INVOCATION_ID,
            Map.of(
                scope.entries().get(0).pipelineRunId(),
                "FAILED",
                scope.entries().get(1).pipelineRunId(),
                "SKIPPED_DEPENDENCY_FAILED"
            ),
            Map.of(),
            NOW
        );
        verify(runs).markFailed(GROUP_ID, "MODEL_DBT_BUILD_RESULT_FAILED", NOW);
    }

    @Test
    void dbtFailureMessageIsPersistedPerCandidateEntry() throws Exception {
        CandidateBuildScope scope = twoEntryScope();
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID)).thenReturn(scope);
        writeTwoEntryArtifacts("error", "skipped");
        ObjectMapper messages = new ObjectMapper();
        Path resultsPath = project.resolve("target/run_results.json");
        var runResults = (com.fasterxml.jackson.databind.node.ObjectNode) messages.readTree(
            resultsPath.toFile()
        );
        (
            (com.fasterxml.jackson.databind.node.ObjectNode) runResults
                .path("results")
                .get(0)
        ).put("message", "column \"task_total\" does not exist");
        Files.writeString(
            resultsPath,
            messages.writeValueAsString(runResults),
            StandardCharsets.UTF_8
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand("RELEASE_BUILD", BUNDLE)
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error -> ((ModelMaterializationRuntimeException) error).code())
            .isEqualTo("MODEL_DBT_BUILD_RESULT_FAILED");

        verify(runs).recordDbtResults(
            GROUP_ID,
            INVOCATION_ID,
            Map.of(
                scope.entries().get(0).pipelineRunId(),
                "FAILED",
                scope.entries().get(1).pipelineRunId(),
                "SKIPPED_DEPENDENCY_FAILED"
            ),
            Map.of(
                scope.entries().get(0).pipelineRunId(),
                "column \"task_total\" does not exist"
            ),
            NOW
        );
        verify(runs).markFailed(GROUP_ID, "MODEL_DBT_BUILD_RESULT_FAILED", NOW);
    }

    @Test
    void relationAbsenceCannotBePromotedFromSuccessfulDbtResult()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                false,
                null,
                List.of(),
                null,
                NOW,
                "MODEL_PHYSICAL_RELATION_NOT_FOUND"
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo("MODEL_PHYSICAL_RELATION_NOT_FOUND");
        verify(observations).appendAll(any());
        verify(runs, never()).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_PHYSICAL_RELATION_NOT_FOUND",
            NOW
        );
    }

    @Test
    void multiEntryCandidateRequiresEveryRelationToBeVerified()
        throws Exception {
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(twoEntryScope());
        writeTwoEntryArtifacts();
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(
                        1,
                        "project_id",
                        "uuid",
                        false
                    ),
                    new PhysicalColumn(
                        2,
                        "amount",
                        "numeric(18,2)",
                        true
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            ),
            new PhysicalRelationObservation(
                false,
                null,
                List.of(),
                null,
                NOW,
                "MODEL_PHYSICAL_RELATION_NOT_FOUND"
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo("MODEL_PHYSICAL_RELATION_NOT_FOUND");
        verify(runs).markDbtSucceeded(
            GROUP_ID,
            INVOCATION_ID,
            2,
            NOW
        );
        verify(observations).appendAll(any());
        verify(runs, never()).markRelationsVerified(
            GROUP_ID,
            2,
            NOW
        );
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_PHYSICAL_RELATION_NOT_FOUND",
            NOW
        );
    }

    @Test
    void relationTypeMismatchCannotBePromotedToBuilt()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.VIEW,
                List.of(
                    new PhysicalColumn(
                        1,
                        "project_id",
                        "uuid",
                        false
                    ),
                    new PhysicalColumn(
                        2,
                        "amount",
                        "numeric(18,2)",
                        true
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo(
                "MODEL_PHYSICAL_RELATION_TYPE_MISMATCH"
            );
        verify(observations).appendAll(any());
        verify(runs, never()).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
    }

    @Test
    void relationColumnDriftCannotBePromotedToBuilt()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(
                        1,
                        "project_id",
                        "uuid",
                        false
                    ),
                    new PhysicalColumn(
                        2,
                        "unexpected_amount",
                        "numeric(18,2)",
                        true
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo(
                "MODEL_PHYSICAL_RELATION_COLUMNS_MISMATCH"
            );
        verify(observations).appendAll(any());
        verify(runs, never()).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
    }

    @Test
    void relationColumnTypeDriftCannotBePromotedToBuilt()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspector.observe(any(), any())).thenReturn(
            new PhysicalRelationObservation(
                true,
                ExpectedRelationType.TABLE,
                List.of(
                    new PhysicalColumn(
                        1,
                        "project_id",
                        "uuid",
                        false
                    ),
                    new PhysicalColumn(
                        2,
                        "amount",
                        "text",
                        true
                    )
                ),
                "f".repeat(64),
                NOW,
                null
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo(
                "MODEL_PHYSICAL_RELATION_COLUMN_TYPE_MISMATCH"
            );
        verify(observations).appendAll(any());
        verify(runs, never()).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
    }

    @Test
    void generatedModelManifestMustDeclareEveryColumnType()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        removeManifestColumnTypes();

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo(
                "MODEL_DBT_MANIFEST_COLUMN_TYPE_REQUIRED"
            );
        verify(observations, never()).appendAll(any());
    }

    @Test
    void dbtManagedManifestMayExplicitlyOmitAllColumnTypes()
        throws Exception {
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope("DBT_MANAGED"));
        writeArtifacts("success", "b".repeat(64));
        removeManifestColumnTypes();

        var result = service.syncAndProbe(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.SyncProbeCommand(
                "RELEASE_BUILD",
                BUNDLE
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        verify(runs).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
    }

    @Test
    void dbtManagedManifestUsesPinnedColumnsWhenSchemaMetadataIsAbsent()
        throws Exception {
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope("DBT_MANAGED"));
        writeArtifacts("success", "b".repeat(64));
        removeManifestColumns();

        var result = service.syncAndProbe(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.SyncProbeCommand(
                "RELEASE_BUILD",
                BUNDLE
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        ArgumentCaptor<PhysicalRelationInspector.RelationLocator> locator =
            ArgumentCaptor.forClass(
                PhysicalRelationInspector.RelationLocator.class
            );
        verify(inspector).observe(any(), locator.capture());
        assertThat(locator.getValue().expectedColumns())
            .containsExactly("project_id", "amount");
    }

    @Test
    void unsupportedAdapterFailsClosedWithoutInventingEvidence()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(inspectors.require("postgres")).thenThrow(
            new PhysicalRelationInspectionException(
                "MODEL_PHYSICAL_RELATION_ADAPTER_UNSUPPORTED",
                "unsupported"
            )
        );

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo(
                "MODEL_PHYSICAL_RELATION_ADAPTER_UNSUPPORTED"
            );
        verify(observations, never()).appendAll(any());
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_PHYSICAL_RELATION_ADAPTER_UNSUPPORTED",
            NOW
        );
    }

    @Test
    void candidateCompletionFailureCannotLeavePartialBuiltTruth()
        throws Exception {
        when(runs.finalizeSucceeded(GROUP_ID, NOW)).thenReturn(1);
        doThrow(new IllegalStateException("candidate conflict"))
            .when(candidates)
            .transition(
                "tenant-a",
                "service:dts-airflow",
                CANDIDATE_ID,
                new ModelReleaseCandidateContract.TransitionCommand(
                    3,
                    ModelLifecycleContract.DeliveryStatus.BUILT,
                    "materialization-built-" + GROUP_ID,
                    "dbt build and physical relation evidence verified"
                )
            );

        assertThatThrownBy(() ->
            service.finalizeRun(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.FinalizeCommand("SUCCEEDED")
            )
        )
            .isInstanceOf(
                ModelMaterializationRuntimeException.class
            )
            .extracting(error ->
                (
                    (ModelMaterializationRuntimeException) error
                ).code()
            )
            .isEqualTo("MODEL_DBT_FINALIZE_PRECONDITION_FAILED");
        verify(runs).finalizeFailed(
            GROUP_ID,
            "MODEL_DBT_FINALIZE_PRECONDITION_FAILED",
            NOW
        );
        verify(candidates).transition(
            "tenant-a",
            "service:dts-airflow",
            CANDIDATE_ID,
            new ModelReleaseCandidateContract.TransitionCommand(
                3,
                ModelLifecycleContract.DeliveryStatus.BUILD_FAILED,
                "materialization-failed-" + GROUP_ID,
                "MODEL_DBT_FINALIZE_PRECONDITION_FAILED"
            )
        );
    }

    @Test
    void postSyncFailureDoesNotRewriteAlreadyPersistedDbtResults() throws Exception {
        writeArtifacts("success", "b".repeat(64));
        doThrow(new IllegalStateException("relation persistence failed"))
            .when(runs).markRelationsVerified(GROUP_ID, 1, NOW);

        assertThatThrownBy(() -> service.syncAndProbe(
            GROUP_ID, new ModelMaterializationRunArtifactService.SyncProbeCommand("RELEASE_BUILD", BUNDLE)
        )).isInstanceOf(ModelMaterializationRuntimeException.class);

        verify(runs, never()).recordDbtResults(any(), any(), any(), any());
        verify(runs).markFailed(GROUP_ID, "MODEL_DBT_ARTIFACT_SYNC_FAILED", NOW);
    }

    @Test
    void verifiedMaterializationDoesNotDependOnCatalogRegistration() {
        when(runs.finalizeSucceeded(GROUP_ID, NOW)).thenReturn(1);
        var result = service.finalizeRun(GROUP_ID, new ModelMaterializationRunArtifactService.FinalizeCommand("SUCCEEDED"));
        assertThat(result.status()).isEqualTo("BUILT");
        verify(qualityAssets, never()).ensureRegistered(any());
        verify(runs, never()).finalizeFailed(any(), any(), any());
    }

    @Test
    void completedFinalizeCanBeRepeatedWithoutAnotherCandidateTransition() {
        when(runs.findRunGroup(GROUP_ID)).thenReturn(java.util.Optional.of(runGroup(4, "BUILT", "COMPLETED", null)));
        when(runs.finalizeSucceeded(GROUP_ID, NOW)).thenReturn(1);

        var result = service.finalizeRun(GROUP_ID, new ModelMaterializationRunArtifactService.FinalizeCommand("SUCCEEDED"));

        assertThat(result.status()).isEqualTo("BUILT");
        verify(candidates, never()).transition(any(), any(), any(), any());
        verify(qualityAssets, never()).ensureRegistered(any());
    }

    @Test
    void successfulFinalizeRequiresAllRowsToAlreadyBeBuilt() {
        when(runs.finalizeSucceeded(GROUP_ID, NOW)).thenReturn(1);

        var result = service.finalizeRun(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.FinalizeCommand(
                "SUCCEEDED"
            )
        );

        assertThat(result.status()).isEqualTo("BUILT");
        assertThat(result.modelCount()).isEqualTo(1);
        var order = org.mockito.Mockito.inOrder(runs, candidates, qualityAssets);
        order.verify(runs).finalizeSucceeded(GROUP_ID, NOW);
        order.verify(candidates).transition(
            "tenant-a", "service:dts-airflow", CANDIDATE_ID,
            new ModelReleaseCandidateContract.TransitionCommand(
                3, ModelLifecycleContract.DeliveryStatus.BUILT,
                "materialization-built-" + GROUP_ID,
                "dbt build and physical relation evidence verified"
            )
        );
        verify(qualityAssets, never()).ensureRegistered(any());
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-run:" + GROUP_ID + ":finalized:succeeded"),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_RUN_FINALIZED"),
            eq(AuditStage.SUCCESS),
            eq(GROUP_ID.toString()),
            any()
        );
    }

    @Test
    void failedFinalizePreservesFailureAsTerminalTruth() {
        when(
            runs.finalizeFailed(
                GROUP_ID,
                "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
                NOW
            )
        ).thenReturn(1);

        var result = service.finalizeRun(
            GROUP_ID,
            new ModelMaterializationRunArtifactService.FinalizeCommand(
                "FAILED"
            )
        );

        assertThat(result.status()).isEqualTo("FAILED");
        verify(runs).finalizeFailed(
            GROUP_ID,
            "MODEL_DBT_AIRFLOW_UPSTREAM_FAILED",
            NOW
        );
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq("model-materialization-run:" + GROUP_ID + ":finalized:failed"),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_RUN_FAILED"),
            eq(AuditStage.FAIL),
            eq(GROUP_ID.toString()),
            any()
        );
    }

    @Test
    void staleGenerationAtArtifactBoundaryCannotPublishSuccessEvidence()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        GenerationCheck stale = staleGeneration();
        when(
            sourceAvailability.checkPinnedCurrentForUpdate(GROUP_ID)
        ).thenReturn(stale);
        when(
            runs.markAvailabilityStale(
                GROUP_ID,
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE,
                NOW
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            );

        verify(runs, never()).markDbtSucceeded(any(), any(), anyInt(), any());
        verify(runs, never()).markRelationsVerified(any(), anyInt(), any());
        verify(candidates).markAvailabilityStale(
            "tenant-a",
            "service:dts-airflow",
            CANDIDATE_ID,
            new ModelReleaseCandidateContract.TransitionCommand(
                3,
                ModelLifecycleContract.DeliveryStatus.STALE,
                "materialization-availability-stale-" + GROUP_ID,
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            )
        );
        ArgumentCaptor<Object> auditPayload = ArgumentCaptor.forClass(
            Object.class
        );
        verify(auditService).auditActionAsStrict(
            eq("airflow"),
            eq(
                "model-materialization-run:" +
                GROUP_ID +
                ":availability-stale"
            ),
            eq(NOW),
            eq("MODEL_MATERIALIZATION_RUN_FAILED"),
            eq(AuditStage.FAIL),
            eq(GROUP_ID.toString()),
            auditPayload.capture()
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) auditPayload.getValue();
        assertThat(payload)
            .containsEntry("candidate", CANDIDATE_ID)
            .containsEntry("dispatch", GROUP_ID)
            .containsEntry("attempt", 1)
            .containsEntry(
                "reasonCode",
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            )
            .doesNotContainKeys(
                "assetKey",
                "token",
                "credential",
                "selector",
                "path"
            );
        assertThat(payload.get("generations").toString())
            .contains("pinnedEpoch=7")
            .contains("currentSourceSequence=101")
            .doesNotContain("catalog://sensitive-asset-key");
    }

    @Test
    void generationDriftBetweenArtifactSyncAndBuiltPublicationWins()
        throws Exception {
        writeArtifacts("success", "b".repeat(64));
        when(
            sourceAvailability.checkPinnedCurrentForUpdate(GROUP_ID)
        ).thenReturn(GenerationCheck.currentCheck(), staleGeneration());
        when(
            runs.markAvailabilityStale(
                GROUP_ID,
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE,
                NOW
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            service.syncAndProbe(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.SyncProbeCommand(
                    "RELEASE_BUILD",
                    BUNDLE
                )
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            );

        verify(runs).markDbtSucceeded(GROUP_ID, INVOCATION_ID, 1, NOW);
        verify(runs, never()).markRelationsVerified(GROUP_ID, 1, NOW);
        verify(runs, never()).markFailed(eq(GROUP_ID), any(), any());
    }

    @Test
    void staleGenerationAtFinalizeCannotCompleteDispatch() {
        when(runs.findRunGroup(GROUP_ID)).thenReturn(
            java.util.Optional.of(
                runGroup(4, "BUILT", "SUBMITTED", null)
            )
        );
        when(
            sourceAvailability.checkPinnedCurrentForUpdate(GROUP_ID)
        ).thenReturn(staleGeneration());
        when(
            runs.markAvailabilityStale(
                GROUP_ID,
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE,
                NOW
            )
        ).thenReturn(true);

        assertThatThrownBy(() ->
            service.finalizeRun(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.FinalizeCommand(
                    "SUCCEEDED"
                )
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            );

        verify(runs, never()).finalizeSucceeded(GROUP_ID, NOW);
        verify(candidates).markAvailabilityStale(
            "tenant-a",
            "service:dts-airflow",
            CANDIDATE_ID,
            new ModelReleaseCandidateContract.TransitionCommand(
                4,
                ModelLifecycleContract.DeliveryStatus.STALE,
                "materialization-availability-stale-" + GROUP_ID,
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            )
        );
    }

    @Test
    void repeatedSuccessCallbackCannotOverwritePersistedStaleTruth() {
        when(runs.findRunGroup(GROUP_ID)).thenReturn(
            java.util.Optional.of(
                runGroup(
                    4,
                    "STALE",
                    "FAILED",
                    ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
                )
            )
        );

        assertThatThrownBy(() ->
            service.finalizeRun(
                GROUP_ID,
                new ModelMaterializationRunArtifactService.FinalizeCommand(
                    "SUCCEEDED"
                )
            )
        )
            .isInstanceOf(ModelMaterializationRuntimeException.class)
            .extracting(error ->
                ((ModelMaterializationRuntimeException) error).code()
            )
            .isEqualTo(
                ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
            );

        verify(sourceAvailability, never()).checkPinnedCurrentForUpdate(
            GROUP_ID
        );
        verify(runs, never()).finalizeSucceeded(GROUP_ID, NOW);
        verify(runs, never()).markAvailabilityStale(any(), any(), any());
        verify(auditService, never()).auditActionAsStrict(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    private static RunGroupRecord runGroup(
        int candidateCurrentVersion,
        String candidateCurrentStatus,
        String dispatchStatus,
        String lastErrorCode
    ) {
        return new RunGroupRecord(
            GROUP_ID,
            "tenant-a",
            PLAN_ID,
            CANDIDATE_ID,
            3,
            1,
            candidateCurrentVersion,
            candidateCurrentStatus,
            "RELEASE_BUILD",
            BUNDLE,
            dispatchStatus,
            lastErrorCode,
            "postgres-primary",
            "postgres",
            "sha256:" + "e".repeat(64)
        );
    }

    private static GenerationCheck staleGeneration() {
        return GenerationCheck.stale(
            ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE,
            List.of(
                new GenerationDrift(
                    UUID.fromString(
                        "50000000-0000-0000-0000-000000000005"
                    ),
                    "DATASET",
                    7L,
                    100L,
                    "available-100",
                    "FENCED",
                    7L,
                    101L,
                    "fence-101",
                    ModelMaterializationSourceAvailabilityGuard.SOURCE_GENERATION_STALE
                )
            )
        );
    }

    private void writeArtifacts(
        String resultStatus,
        String implementationChecksum
    ) throws Exception {
        Files.createDirectories(project.resolve("target"));
        String manifest = """
            {
              "metadata": {
                "invocation_id": "%s"
              },
              "nodes": {
                "%s": {
                  "unique_id": "%s",
                  "database": "warehouse",
                  "schema": "finance",
                  "alias": "dwd_finance",
                  "resource_type": "model",
                  "config": {
                    "materialized": "table",
                    "meta": {
                      "modelSpecId": "%s",
                      "modelRevision": 7,
                      "modelChecksum": "%s",
                      "implementationRevision": 5,
                      "implementationChecksum": "%s"
                    }
                  },
                  "columns": {
                    "project_id": {
                      "name": "project_id",
                      "data_type": "uuid"
                    },
                    "amount": {
                      "name": "amount",
                      "data_type": "numeric(18,2)"
                    }
                  }
                }
              }
            }
            """.formatted(
            INVOCATION_ID,
            UNIQUE_ID,
            UNIQUE_ID,
            MODEL_ID,
            "c".repeat(64),
            implementationChecksum
        );
        String runResults = """
            {
              "metadata": {
                "invocation_id": "%s"
              },
              "results": [
                {
                  "unique_id": "%s",
                  "status": "%s"
                }
              ]
            }
            """.formatted(INVOCATION_ID, UNIQUE_ID, resultStatus);
        Files.writeString(
            project.resolve("target/manifest.json"),
            manifest,
            StandardCharsets.UTF_8
        );
        Files.writeString(
            project.resolve("target/run_results.json"),
            runResults,
            StandardCharsets.UTF_8
        );
    }

    private void replaceRuntimeUniqueId(String runtimeUniqueId)
        throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Path manifestPath = project.resolve("target/manifest.json");
        var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            manifestPath.toFile()
        );
        var nodes = (com.fasterxml.jackson.databind.node.ObjectNode) manifest.path(
            "nodes"
        );
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) nodes.remove(
            UNIQUE_ID
        );
        node.put("unique_id", runtimeUniqueId);
        nodes.set(runtimeUniqueId, node);
        Files.writeString(
            manifestPath,
            mapper.writeValueAsString(manifest),
            StandardCharsets.UTF_8
        );

        Path resultsPath = project.resolve("target/run_results.json");
        var results = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
            resultsPath.toFile()
        );
        (
            (com.fasterxml.jackson.databind.node.ObjectNode) results
                .path("results")
                .get(0)
        ).put("unique_id", runtimeUniqueId);
        Files.writeString(
            resultsPath,
            mapper.writeValueAsString(results),
            StandardCharsets.UTF_8
        );
    }

    private void writeTwoEntryArtifacts() throws Exception {
        writeTwoEntryArtifacts("success", "success");
    }

    private void writeTwoEntryArtifacts(String firstStatus, String secondStatus) throws Exception {
        Files.createDirectories(project.resolve("target"));
        String manifest = """
            {
              "metadata": {
                "invocation_id": "%s"
              },
              "nodes": {
                "%s": {
                  "unique_id": "%s",
                  "database": "warehouse",
                  "schema": "finance",
                  "alias": "dwd_finance",
                  "resource_type": "model",
                  "config": {
                    "materialized": "table",
                    "meta": {
                      "modelSpecId": "%s",
                      "modelRevision": 7,
                      "modelChecksum": "%s",
                      "implementationRevision": 5,
                      "implementationChecksum": "%s"
                    }
                  },
                  "columns": {
                    "project_id": {"name": "project_id", "data_type": "uuid"},
                    "amount": {"name": "amount", "data_type": "numeric(18,2)"}
                  }
                },
                "%s": {
                  "unique_id": "%s",
                  "database": "warehouse",
                  "schema": "finance",
                  "alias": "dwd_finance_summary",
                  "resource_type": "model",
                  "config": {
                    "materialized": "table",
                    "meta": {
                      "modelSpecId": "%s",
                      "modelRevision": 8,
                      "modelChecksum": "%s",
                      "implementationRevision": 6,
                      "implementationChecksum": "%s"
                    }
                  },
                  "columns": {
                    "project_id": {"name": "project_id", "data_type": "uuid"},
                    "amount": {"name": "amount", "data_type": "numeric(18,2)"}
                  }
                }
              }
            }
            """.formatted(
            INVOCATION_ID,
            UNIQUE_ID,
            UNIQUE_ID,
            MODEL_ID,
            "c".repeat(64),
            "b".repeat(64),
            SECOND_UNIQUE_ID,
            SECOND_UNIQUE_ID,
            SECOND_MODEL_ID,
            "1".repeat(64),
            "2".repeat(64)
        );
        String runResults = """
            {
              "metadata": {
                "invocation_id": "%s"
              },
              "results": [
                {"unique_id": "%s", "status": "%s"},
                {"unique_id": "%s", "status": "%s"}
              ]
            }
            """.formatted(
            INVOCATION_ID,
            UNIQUE_ID,
            firstStatus,
            SECOND_UNIQUE_ID,
            secondStatus
        );
        Files.writeString(
            project.resolve("target/manifest.json"),
            manifest,
            StandardCharsets.UTF_8
        );
        Files.writeString(
            project.resolve("target/run_results.json"),
            runResults,
            StandardCharsets.UTF_8
        );
    }

    private void removeManifestColumnTypes() throws Exception {
        Path manifestPath = project.resolve("target/manifest.json");
        ObjectMapper mapper = new ObjectMapper();
        var manifest = mapper.readTree(manifestPath.toFile());
        var columns = manifest
            .path("nodes")
            .path(UNIQUE_ID)
            .path("columns");
        (
            (com.fasterxml.jackson.databind.node.ObjectNode) columns.path(
                    "project_id"
                )
        ).remove("data_type");
        (
            (com.fasterxml.jackson.databind.node.ObjectNode) columns.path(
                    "amount"
                )
        ).remove("data_type");
        Files.writeString(
            manifestPath,
            mapper.writeValueAsString(manifest),
            StandardCharsets.UTF_8
        );
    }

    private void removeManifestColumns() throws Exception {
        Path manifestPath = project.resolve("target/manifest.json");
        ObjectMapper mapper = new ObjectMapper();
        var manifest = mapper.readTree(manifestPath.toFile());
        (
            (com.fasterxml.jackson.databind.node.ObjectNode) manifest
                .path("nodes")
                .path(UNIQUE_ID)
        ).set("columns", mapper.createObjectNode());
        Files.writeString(
            manifestPath,
            mapper.writeValueAsString(manifest),
            StandardCharsets.UTF_8
        );
    }

    private static CandidateBuildScope scope() {
        return scope("DESIGNER_GENERATED");
    }

    private static CandidateBuildScope scope(
        String implementationMode
    ) {
        return new CandidateBuildScope(
            "tenant-a",
            CANDIDATE_ID,
            3,
            GROUP_ID,
            "postgres-primary",
            "d".repeat(64),
            List.of(
                new CandidateBuildEntry(
                    UUID.fromString(
                        "50000000-0000-0000-0000-000000000005"
                    ),
                    MODEL_ID,
                    7,
                    "c".repeat(64),
                    5,
                    "b".repeat(64),
                    implementationMode,
                    UNIQUE_ID,
                    "dwd_finance",
                    List.of(),
                    List.of("project_id", "amount")
                )
            )
        );
    }

    private static CandidateBuildScope twoEntryScope() {
        return new CandidateBuildScope(
            "tenant-a",
            CANDIDATE_ID,
            3,
            GROUP_ID,
            "postgres-primary",
            "d".repeat(64),
            List.of(
                scope().entries().getFirst(),
                new CandidateBuildEntry(
                    UUID.fromString(
                        "50000000-0000-0000-0000-000000000006"
                    ),
                    SECOND_MODEL_ID,
                    8,
                    "1".repeat(64),
                    6,
                    "2".repeat(64),
                    "DESIGNER_GENERATED",
                    SECOND_UNIQUE_ID,
                    "dwd_finance_summary",
                    List.of(),
                    List.of("project_id", "amount")
                )
            )
        );
    }

    private static TransactionOperations passthroughTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }

    private static String digest(List<String> values) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        for (String value : values) {
            digest.update(
                value.getBytes(StandardCharsets.UTF_8)
            );
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
