package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.BeginDisposition;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.ApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredRun;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanAuthorizationGuard;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyServiceTest {

    private static final String TENANT = "default";
    private static final String PREVIEW_HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
    private static final UUID RUN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    private final ModelSpecImportPreviewRepository previewRepository = mock(ModelSpecImportPreviewRepository.class);
    private final ModelSpecImportApplyRepository applyRepository = mock(ModelSpecImportApplyRepository.class);
    private final ModelSpecImportApplyPreflightService preflight = mock(ModelSpecImportApplyPreflightService.class);
    private final WarehousePlanActorProvider actorProvider = mock(WarehousePlanActorProvider.class);
    private final WarehousePlanAuthorizationGuard authorizationGuard = mock(WarehousePlanAuthorizationGuard.class);
    private final ModelSpecImportApplyService service = new ModelSpecImportApplyService(
        previewRepository,
        applyRepository,
        preflight,
        mock(ModelSpecImportCandidateTransactionWorker.class),
        actorProvider,
        authorizationGuard,
        mock(ModelSpecImportApplyPayloadCodec.class),
        new ObjectMapper(),
        TENANT
    );

    @BeforeEach
    void setUp() {
        when(actorProvider.currentActor()).thenReturn(new WarehousePlanActor("actor", "dept"));
        when(previewRepository.findPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(plan()));
    }

    @Test
    void replaysSameCanonicalRawRequestBeforeMutablePreflight() {
        List<String> canonical = List.of("model.pjm.fact", "model.pjm.summary");
        when(previewRepository.findRun(TENANT, RUN_ID)).thenReturn(Optional.of(run(freshExpiry())));
        when(previewRepository.findApplyPlan(TENANT, RUN_ID)).thenReturn(Optional.of(stored(canonical)));
        when(applyRepository.findByIdempotencyKey(TENANT, "same-key")).thenReturn(
            Optional.of(attempt(AttemptStatus.SUCCEEDED, canonical))
        );

        var response = service.apply(
            new ApplyRequest(
                RUN_ID,
                PREVIEW_HASH,
                List.of("model.pjm.summary", "model.pjm.fact"),
                "same-key"
            )
        );

        assertThat(response.disposition()).isEqualTo(BeginDisposition.REPLAY);
        verify(preflight, never()).prepare(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyList(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void latestRestoresExpiredAttemptButNewApplyStillRequiresFreshPreview() {
        when(previewRepository.findRun(TENANT, RUN_ID)).thenReturn(
            Optional.of(run(Instant.now().minusSeconds(60)))
        );
        when(applyRepository.findLatest(TENANT, RUN_ID)).thenReturn(
            Optional.of(attempt(AttemptStatus.SUCCEEDED, List.of("model.pjm.fact")))
        );

        assertThat(service.latest(RUN_ID).status()).isEqualTo(AttemptStatus.SUCCEEDED);
        assertThatThrownBy(() ->
            service.apply(
                new ApplyRequest(RUN_ID, PREVIEW_HASH, List.of("model.pjm.fact"), "expired-apply-key")
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.kind()).isEqualTo(Kind.GONE)
            );
        verify(previewRepository, never()).findApplyPlan(TENANT, RUN_ID);
    }

    @Test
    void latestRecoversExpiredRunningLeaseEvenAfterPreviewExpires() {
        Attempt recovered = attempt(AttemptStatus.FAILED, List.of("model.pjm.fact"));
        when(previewRepository.findRun(TENANT, RUN_ID)).thenReturn(
            Optional.of(run(Instant.now().minusSeconds(60)))
        );
        when(applyRepository.recoverExpiredRunning(TENANT, RUN_ID, "actor")).thenReturn(
            Optional.of(recovered)
        );
        when(applyRepository.findLatest(TENANT, RUN_ID)).thenReturn(Optional.of(recovered));

        assertThat(service.latest(RUN_ID).status()).isEqualTo(AttemptStatus.FAILED);
        verify(applyRepository).recoverExpiredRunning(TENANT, RUN_ID, "actor");
    }

    @Test
    void replaysAttemptThatWinsRaceWhileMutablePreflightFails() {
        List<String> selected = List.of("model.pjm.fact");
        Attempt winner = attempt(AttemptStatus.RUNNING, selected);
        when(previewRepository.findRun(TENANT, RUN_ID)).thenReturn(Optional.of(run(freshExpiry())));
        when(previewRepository.findApplyPlan(TENANT, RUN_ID)).thenReturn(Optional.of(stored(selected)));
        when(applyRepository.findByIdempotencyKey(TENANT, "race-key")).thenReturn(
            Optional.empty(),
            Optional.of(winner)
        );
        when(
            preflight.prepare(
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(PREVIEW_HASH),
                org.mockito.ArgumentMatchers.eq(selected),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any()
            )
        ).thenThrow(
            new ModelSpecImportApplyException(
                "MODEL_IMPORT_PREVIEW_STALE",
                "racing mutable state",
                Kind.CONFLICT,
                null
            )
        );

        assertThat(
            service.apply(new ApplyRequest(RUN_ID, PREVIEW_HASH, selected, "race-key")).disposition()
        ).isEqualTo(BeginDisposition.RUNNING);
    }

    @Test
    void assignsSharedTechnicalNodeOnceAndBlocksSharerOnlyWhenOwnerFails() {
        Candidate owner = technicalCandidate("model.pjm.owner", "model.pjm.shared_stg");
        Candidate sharer = technicalCandidate("model.pjm.sharer", "model.pjm.shared_stg");
        ModelSpecImportApplyService.TechnicalAllocation allocation = service.technicalAllocation(
            stored(List.of(owner.dbtUniqueId(), sharer.dbtUniqueId()), List.of(owner, sharer)),
            List.of(owner.dbtUniqueId(), sharer.dbtUniqueId()),
            Map.of()
        );

        assertThat(allocation.ownedTechnicalNodeIds().get(owner.dbtUniqueId()))
            .containsExactly("model.pjm.shared_stg");
        assertThat(allocation.ownedTechnicalNodeIds()).doesNotContainKey(sharer.dbtUniqueId());
        assertThat(allocation.syntheticDependencies().get(sharer.dbtUniqueId()))
            .containsExactly(owner.dbtUniqueId());
        assertThat(
            ModelSpecImportApplyService.hasFailedDependency(
                sharer.dbtUniqueId(),
                allocation.syntheticDependencies(),
                Map.of(owner.dbtUniqueId(), ResultStatus.CREATED)
            )
        ).isFalse();
        assertThat(
            ModelSpecImportApplyService.hasFailedDependency(
                sharer.dbtUniqueId(),
                allocation.syntheticDependencies(),
                Map.of(owner.dbtUniqueId(), ResultStatus.FAILED)
            )
        ).isTrue();
    }

    @Test
    void aggregatesSuccessfulPinsAcrossMultiHopRetryAncestors() {
        UUID rootAttemptId = UUID.fromString("20000000-0000-0000-0000-000000000010");
        UUID latestAttemptId = UUID.fromString("20000000-0000-0000-0000-000000000011");
        CandidateResult ownerSuccess = result("model.pjm.owner", ResultStatus.CREATED);
        Attempt root = attempt(
            rootAttemptId,
            null,
            AttemptStatus.PARTIAL,
            List.of("model.pjm.owner", "model.pjm.sharer"),
            List.of(ownerSuccess, result("model.pjm.sharer", ResultStatus.FAILED))
        );
        Attempt latest = attempt(
            latestAttemptId,
            rootAttemptId,
            AttemptStatus.FAILED,
            List.of("model.pjm.sharer"),
            List.of(result("model.pjm.sharer", ResultStatus.FAILED))
        );
        when(applyRepository.find(TENANT, rootAttemptId)).thenReturn(Optional.of(root));

        ModelSpecImportApplyService.RetryHistory history = service.retryHistory(latest);

        assertThat(history.rootSelectedClosure()).containsExactly(
            "model.pjm.owner",
            "model.pjm.sharer"
        );
        assertThat(history.successfulResults()).containsEntry("model.pjm.owner", ownerSuccess);
    }

    @Test
    void enforcesApplyCommandBoundariesBeforeRepositoryAccess() {
        assertThatThrownBy(() ->
            new ApplyRequest(RUN_ID, "not-a-checksum", List.of("model.pjm.fact"), "key")
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new ApplyRequest(
                RUN_ID,
                PREVIEW_HASH,
                java.util.stream.IntStream.rangeClosed(0, 200).mapToObj(index -> "model.pjm.n" + index).toList(),
                "key"
            )
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new ApplyRequest(RUN_ID, PREVIEW_HASH, List.of("x".repeat(513)), "key")
        ).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
            new ApplyRequest(RUN_ID, PREVIEW_HASH, List.of("model.pjm.fact"), "k".repeat(257))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sanitizesUnknownCandidateFailuresAndOnlyAllowsFixedApplyMessages() {
        ModelSpecImportApplyService.SanitizedFailure unknown =
            ModelSpecImportApplyService.sanitizeCandidateFailure(
                new RuntimeException("select secret from /tmp/customer.sql")
            );
        ModelSpecImportApplyService.SanitizedFailure allowlisted =
            ModelSpecImportApplyService.sanitizeCandidateFailure(
                new ModelSpecImportApplyException(
                    "MODEL_IMPORT_IDEMPOTENCY_CONFLICT",
                    "raw database detail",
                    Kind.CONFLICT,
                    null
                )
            );

        assertThat(unknown.code()).isEqualTo("MODEL_IMPORT_CANDIDATE_FAILED");
        assertThat(unknown.message()).doesNotContain("secret", "/tmp", "select");
        assertThat(allowlisted.code()).isEqualTo("MODEL_IMPORT_IDEMPOTENCY_CONFLICT");
        assertThat(allowlisted.message()).isEqualTo(
            "Candidate request conflicts with a previously persisted apply result"
        );
        assertThat(allowlisted.message()).doesNotContain("raw database detail");
    }

    private static StoredRun run(Instant expiresAt) {
        return new StoredRun(
            RUN_ID,
            PLAN_ID,
            PREVIEW_HASH,
            "payload-checksum",
            RunStatus.PREVIEWED,
            expiresAt,
            new PreviewSummary(1, 1, 0, 1, 0, 0, 0),
            List.of()
        );
    }

    private static Instant freshExpiry() {
        return Instant.now().plusSeconds(3600);
    }

    private static StoredApplyPlan stored(List<String> topology) {
        return stored(topology, List.of());
    }

    private static StoredApplyPlan stored(List<String> topology, List<Candidate> candidates) {
        return new StoredApplyPlan(
            RUN_ID,
            PLAN_ID,
            PREVIEW_HASH,
            RunStatus.PREVIEWED,
            freshExpiry(),
            "package-checksum",
            "payload-checksum",
            "plan-checksum",
            "{}",
            "{}",
            "{}",
            new ApplyPlan(
                RUN_ID,
                PLAN_ID,
                PREVIEW_HASH,
                "package-checksum",
                "payload-checksum",
                topology,
                candidates
            )
        );
    }

    private static Candidate technicalCandidate(String uniqueId, String technicalNodeId) {
        return new Candidate(
            uniqueId,
            UUID.randomUUID(),
            1,
            1,
            0,
            null,
            "DRAFT",
            0,
            null,
            "model-checksum",
            "implementation-checksum",
            "CREATE",
            "DBT_BACKED",
            "{}",
            "{}",
            "{}",
            "{}",
            """
            {"canonicalDependencies":[],"technicalPathNodes":["%s"]}
            """.formatted(technicalNodeId),
            "{}"
        );
    }

    private static Attempt attempt(AttemptStatus status, List<String> selected) {
        return attempt(
            UUID.fromString("20000000-0000-0000-0000-000000000003"),
            null,
            status,
            selected,
            List.of()
        );
    }

    private static Attempt attempt(
        UUID attemptId,
        UUID retrySourceAttemptId,
        AttemptStatus status,
        List<String> selected,
        List<CandidateResult> results
    ) {
        return new Attempt(
            attemptId,
            RUN_ID,
            PLAN_ID,
            retrySourceAttemptId,
            TENANT,
            1,
            PREVIEW_HASH,
            selected,
            selected,
            "same-key",
            "request-hash",
            status,
            ApplySummary.EMPTY,
            "actor",
            NOW,
            status == AttemptStatus.RUNNING ? null : NOW,
            results
        );
    }

    private static CandidateResult result(String uniqueId, ResultStatus status) {
        boolean successful = status == ResultStatus.CREATED;
        return new CandidateResult(
            UUID.randomUUID(),
            0,
            uniqueId,
            "candidate-key-" + uniqueId,
            "candidate-hash-" + uniqueId,
            status,
            successful ? UUID.randomUUID() : null,
            successful ? 1 : null,
            successful ? "model-checksum" : null,
            successful ? 1 : null,
            successful ? "implementation-checksum" : null,
            successful ? 4 : 0,
            List.of(),
            NOW
        );
    }

    private static PlanSnapshot plan() {
        return new PlanSnapshot(
            PLAN_ID,
            TENANT,
            "wp_apply",
            "Apply plan",
            "Import models",
            "PJM",
            "actor",
            "dept",
            OnboardingMode.BUSINESS_FIRST,
            LifecycleStatus.DESIGNING,
            3,
            4,
            5
        );
    }
}
