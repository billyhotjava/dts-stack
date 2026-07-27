package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildEntry;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationBuildRepository.CandidateBuildScope;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelMaterializationRunRepository.RunGroupRecord;
import com.yuzhi.dts.platform.repository.modeling.PhysicalRelationObservationRepository;
import com.yuzhi.dts.platform.service.etl.DbtScopedProjectService;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

class ModelMaterializationRunArtifactServiceTest {

    private static final UUID GROUP_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID CANDIDATE_ID =
        UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID SECOND_MODEL_ID =
        UUID.fromString("30000000-0000-0000-0000-000000000006");
    private static final UUID INVOCATION_ID =
        UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final String BUNDLE = "a".repeat(64);
    private static final String UNIQUE_ID =
        "model.dts_test.model_30000000_0000_0000_0000_000000000003";
    private static final String SECOND_UNIQUE_ID =
        "model.dts_test.model_30000000_0000_0000_0000_000000000006";
    private static final Instant NOW =
        Instant.parse("2026-07-27T15:00:00Z");

    @TempDir
    Path project;

    private ModelMaterializationRunRepository runs;
    private ModelMaterializationBuildRepository builds;
    private DbtScopedProjectService scoped;
    private PhysicalRelationInspector inspector;
    private PhysicalRelationInspectorRegistry inspectors;
    private PhysicalRelationObservationRepository observations;
    private ModelReleaseCandidateService candidates;
    private ModelMaterializationRunArtifactService service;

    @BeforeEach
    void setUp() {
        runs = mock(ModelMaterializationRunRepository.class);
        builds = mock(ModelMaterializationBuildRepository.class);
        scoped = mock(DbtScopedProjectService.class);
        inspector = mock(PhysicalRelationInspector.class);
        inspectors = mock(
            PhysicalRelationInspectorRegistry.class
        );
        observations = mock(
            PhysicalRelationObservationRepository.class
        );
        candidates = mock(ModelReleaseCandidateService.class);
        service = new ModelMaterializationRunArtifactService(
            runs,
            builds,
            scoped,
            inspectors,
            observations,
            candidates,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            passthroughTransactions()
        );
        when(runs.findRunGroup(GROUP_ID)).thenReturn(
            java.util.Optional.of(
                new RunGroupRecord(
                    GROUP_ID,
                    "tenant-a",
                    CANDIDATE_ID,
                    3,
                    "RELEASE_BUILD",
                    BUNDLE,
                    "SUBMITTED",
                    "postgres-primary",
                    "postgres",
                    "sha256:" + "e".repeat(64)
                )
            )
        );
        when(builds.loadCandidateBuildScope("tenant-a", GROUP_ID))
            .thenReturn(scope());
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
        verify(runs).markDbtSucceeded(
            GROUP_ID,
            INVOCATION_ID,
            1,
            NOW
        );
        verify(observations).appendAll(any());
        verify(runs).markRelationsVerified(
            GROUP_ID,
            1,
            NOW
        );
        verify(candidates).transition(
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
        verify(runs, never()).markFailed(
            GROUP_ID,
            "MODEL_DBT_ARTIFACT_INVALID",
            NOW
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
        writeArtifacts("success", "b".repeat(64));
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
            .isEqualTo("MODEL_DBT_ARTIFACT_SYNC_FAILED");
        verify(runs).markFailed(
            GROUP_ID,
            "MODEL_DBT_ARTIFACT_SYNC_FAILED",
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
                "MODEL_DBT_ARTIFACT_SYNC_FAILED"
            )
        );
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
        verify(runs).finalizeSucceeded(GROUP_ID, NOW);
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

    private void writeTwoEntryArtifacts() throws Exception {
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
                {"unique_id": "%s", "status": "success"},
                {"unique_id": "%s", "status": "success"}
              ]
            }
            """.formatted(
            INVOCATION_ID,
            UNIQUE_ID,
            SECOND_UNIQUE_ID
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

    private static CandidateBuildScope scope() {
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
                    UNIQUE_ID,
                    "dwd_finance",
                    List.of()
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
                    SECOND_UNIQUE_ID,
                    "dwd_finance_summary",
                    List.of()
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
}
