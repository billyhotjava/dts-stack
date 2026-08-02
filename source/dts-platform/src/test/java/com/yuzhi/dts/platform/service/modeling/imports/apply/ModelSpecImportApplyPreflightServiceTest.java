package com.yuzhi.dts.platform.service.modeling.imports.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyIssue;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplySummary;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.AttemptStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Severity;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.ApplyPlan;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.DomainBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.ModelOwnershipSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredApplyPlan;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelSpecImportApplyPreflightServiceTest {

    private static final String TENANT = "default";
    private static final String PROJECT = "pjm";
    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID RUN_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID FACT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID SUMMARY_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelSpecImportPreviewRepository repository = mock(ModelSpecImportPreviewRepository.class);
    private final ModelSpecImportApplyPayloadCodec payloadCodec = mock(ModelSpecImportApplyPayloadCodec.class);
    private final ModelSpecImportApplyPreflightService preflight = new ModelSpecImportApplyPreflightService(
        repository,
        payloadCodec,
        objectMapper
    );

    @BeforeEach
    void setUp() {
        when(payloadCodec.isValid(anyString(), anyString())).thenReturn(true);
        when(repository.findPlan(TENANT, PLAN_ID)).thenReturn(Optional.of(plan()));
        when(repository.findDomainBindings(TENANT, PLAN_ID)).thenReturn(List.of());
        when(repository.findSourceBindings(TENANT, PLAN_ID)).thenReturn(List.of());
    }

    @Test
    void initialApplyRejectsEmptySelectionAsBadRequest() {
        ModelSpecImportApplyService service = new ModelSpecImportApplyService(
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            TENANT
        );

        assertThatThrownBy(() -> service.apply(new ApplyRequest(RUN_ID, "a".repeat(64), List.of(), "apply-key")))
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception -> {
                assertThat(exception.kind()).isEqualTo(Kind.BAD_REQUEST);
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_SELECTION_REQUIRED");
            });
    }

    @Test
    void missingFrozenTimestampOnlyMatchesNullCurrentTimestamp() {
        UUID domainId = UUID.fromString("10000000-0000-0000-0000-000000000005");
        when(repository.findDomainBindings(TENANT, PLAN_ID)).thenReturn(
            List.of(new DomainBindingSnapshot(domainId, "CONFIRMED", NOW))
        );
        StoredApplyPlan stored = stored(
            """
            {
              "plan": {
                "planId": "%s",
                "tenantId": "default",
                "lifecycleStatus": "DESIGNING",
                "version": 3,
                "businessScopeVersion": 4,
                "sourcesVersion": 5
              },
              "domains": [{"domainId": "%s", "confirmationStatus": "CONFIRMED"}],
              "sources": []
            }
            """.formatted(PLAN_ID, domainId),
            List.of(candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]"))
        );

        assertThatThrownBy(() ->
            preflight.prepare(TENANT, stored, "preview-hash", List.of("model.pjm.fact"), null, NOW)
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_PREVIEW_STALE")
            );
    }

    @Test
    void retryValidatesSuccessfulDependencyAgainstPreviousResultPins() {
        Candidate fact = candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]");
        Candidate summary = candidate(
            "model.pjm.summary",
            SUMMARY_ID,
            "UPDATE",
            1,
            "summary-before",
            1,
            "summary-implementation-before",
            """
            [{"dbtUniqueId":"model.pjm.fact"}]
            """
        );
        StoredApplyPlan stored = stored(baseContext(), List.of(fact, summary));
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.fact")).thenReturn(
            Optional.of(ownership(FACT_ID, 1, "fact-after", 1, "fact-implementation-after"))
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.summary")).thenReturn(
            Optional.of(ownership(SUMMARY_ID, 1, "summary-before", 1, "summary-implementation-before"))
        );
        Attempt retrySource = retrySource(
            successfulResult("model.pjm.fact", FACT_ID, "fact-after", "fact-implementation-after"),
            failedResult("model.pjm.summary")
        );

        ModelSpecImportApplyPreflightService.PreparedApply prepared = preflight.prepare(
            TENANT,
            stored,
            "preview-hash",
            List.of(),
            retrySource,
            NOW
        );

        assertThat(prepared.selectedUniqueIds()).containsExactly("model.pjm.summary");
        assertThat(prepared.selectedClosure()).containsExactly("model.pjm.summary");
        assertThat(prepared.candidates()).extracting(Candidate::dbtUniqueId).containsExactly("model.pjm.summary");
    }

    @Test
    void retryRejectsDependencyDriftFromPreviousResultPins() {
        Candidate fact = candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]");
        Candidate summary = candidate(
            "model.pjm.summary",
            SUMMARY_ID,
            "UPDATE",
            1,
            "summary-before",
            1,
            "summary-implementation-before",
            """
            [{"dbtUniqueId":"model.pjm.fact"}]
            """
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.fact")).thenReturn(
            Optional.of(ownership(FACT_ID, 1, "fact-drifted", 1, "fact-implementation-after"))
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.summary")).thenReturn(
            Optional.of(ownership(SUMMARY_ID, 1, "summary-before", 1, "summary-implementation-before"))
        );

        assertThatThrownBy(() ->
            preflight.prepare(
                TENANT,
                stored(baseContext(), List.of(fact, summary)),
                "preview-hash",
                List.of(),
                retrySource(
                    successfulResult("model.pjm.fact", FACT_ID, "fact-after", "fact-implementation-after"),
                    failedResult("model.pjm.summary")
                ),
                NOW
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_PREVIEW_STALE")
            );
    }

    @Test
    void retryRejectsAncestorSuccessDriftEvenWhenItIsNotADirectDependency() {
        Candidate ancestor = candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]");
        Candidate leaf = candidate(
            "model.pjm.summary",
            SUMMARY_ID,
            "UPDATE",
            1,
            "summary-before",
            1,
            "summary-implementation-before",
            "[]"
        );
        CandidateResult ancestorPin = successfulResult(
            "model.pjm.fact",
            FACT_ID,
            "fact-after",
            "fact-implementation-after"
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.fact")).thenReturn(
            Optional.of(ownership(FACT_ID, 1, "fact-drifted", 1, "fact-implementation-after"))
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.summary")).thenReturn(
            Optional.of(ownership(SUMMARY_ID, 1, "summary-before", 1, "summary-implementation-before"))
        );

        assertThatThrownBy(() ->
            preflight.prepare(
                TENANT,
                stored(baseContext(), List.of(ancestor, leaf)),
                "preview-hash",
                List.of(),
                retrySource(failedResult("model.pjm.summary")),
                Map.of("model.pjm.fact", ancestorPin),
                Map.of(),
                NOW
            )
        )
            .isInstanceOfSatisfying(ModelSpecImportApplyException.class, exception ->
                assertThat(exception.code()).isEqualTo("MODEL_IMPORT_PREVIEW_STALE")
            );
    }

    @Test
    void selectedClosureExcludesIndependentUnselectedBlockedCandidate() {
        Candidate ready = candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]");
        Candidate blocked = candidate(
            "model.pjm.blocked",
            SUMMARY_ID,
            "BLOCKED",
            0,
            null,
            0,
            null,
            "[]"
        );
        when(repository.findOwnership(TENANT, PROJECT, "model.pjm.fact")).thenReturn(Optional.empty());

        ModelSpecImportApplyPreflightService.PreparedApply prepared = preflight.prepare(
            TENANT,
            stored(baseContext(), List.of(ready, blocked)),
            "preview-hash",
            List.of("model.pjm.fact"),
            null,
            NOW
        );

        assertThat(prepared.selectedClosure()).containsExactly("model.pjm.fact");
        assertThat(prepared.candidates()).extracting(Candidate::dbtUniqueId).containsExactly("model.pjm.fact");
    }

    @Test
    void retrySelectionUsesOnlyPersistedRetryableFailureFacts() {
        Candidate retryable = candidate("model.pjm.fact", FACT_ID, "CREATE", 0, null, 0, null, "[]");
        Candidate terminal = candidate("model.pjm.summary", SUMMARY_ID, "CREATE", 0, null, 0, null, "[]");
        when(repository.findOwnership(TENANT, PROJECT, retryable.dbtUniqueId())).thenReturn(Optional.empty());
        Attempt source = new Attempt(
            UUID.randomUUID(),
            RUN_ID,
            PLAN_ID,
            null,
            TENANT,
            1,
            "preview-hash",
            List.of(retryable.dbtUniqueId(), terminal.dbtUniqueId()),
            List.of(retryable.dbtUniqueId(), terminal.dbtUniqueId()),
            "first-key",
            "first-hash",
            AttemptStatus.FAILED,
            new ApplySummary(2, 0, 0, 0, 0, 0, 2, 0),
            "actor",
            NOW,
            NOW,
            List.of(
                failedResult(retryable.dbtUniqueId(), true),
                failedResult(terminal.dbtUniqueId(), false)
            )
        );

        ModelSpecImportApplyPreflightService.PreparedApply prepared = preflight.prepare(
            TENANT,
            stored(baseContext(), List.of(retryable, terminal)),
            "preview-hash",
            List.of(),
            source,
            NOW
        );

        assertThat(prepared.selectedUniqueIds()).containsExactly(retryable.dbtUniqueId());
        assertThat(prepared.selectedClosure()).containsExactly(retryable.dbtUniqueId());
        assertThat(prepared.candidates()).extracting(Candidate::dbtUniqueId).containsExactly(retryable.dbtUniqueId());
    }

    private StoredApplyPlan stored(String contextJson, List<Candidate> candidates) {
        List<String> topology = candidates.stream().map(Candidate::dbtUniqueId).toList();
        ApplyPlan plan = new ApplyPlan(
            RUN_ID,
            PLAN_ID,
            "preview-hash",
            "package-checksum",
            "payload-checksum",
            topology,
            candidates
        );
        return new StoredApplyPlan(
            RUN_ID,
            PLAN_ID,
            "preview-hash",
            RunStatus.PREVIEWED,
            NOW.plusSeconds(3600),
            "package-checksum",
            "payload-checksum",
            "plan-checksum",
            "{}",
            contextJson,
            """
            {"schemaVersion":"dts.model-import.apply-payload/v1","dbt":{"projectName":"pjm"}}
            """,
            plan
        );
    }

    private static Candidate candidate(
        String uniqueId,
        UUID modelSpecId,
        String action,
        int expectedModelRevision,
        String expectedModelChecksum,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        String dependencies
    ) {
        return new Candidate(
            uniqueId,
            modelSpecId,
            expectedModelRevision + 1,
            expectedImplementationRevision + 1,
            expectedModelRevision,
            expectedModelChecksum,
            "DRAFT",
            expectedImplementationRevision,
            expectedImplementationChecksum,
            "model-after",
            "implementation-after",
            action,
            "DBT_BACKED",
            "{}",
            "{}",
            "{}",
            "{}",
            """
            {"canonicalDependencies":%s}
            """.formatted(dependencies),
            "{}"
        );
    }

    private static Attempt retrySource(CandidateResult... results) {
        return new Attempt(
            UUID.fromString("10000000-0000-0000-0000-000000000006"),
            RUN_ID,
            PLAN_ID,
            null,
            TENANT,
            1,
            "preview-hash",
            List.of("model.pjm.summary"),
            List.of("model.pjm.fact", "model.pjm.summary"),
            "first-key",
            "first-hash",
            AttemptStatus.PARTIAL,
            new ApplySummary(2, 0, 1, 1, 0, 0, 1, 0),
            "actor",
            NOW,
            NOW,
            List.of(results)
        );
    }

    private static CandidateResult successfulResult(
        String uniqueId,
        UUID modelSpecId,
        String modelChecksum,
        String implementationChecksum
    ) {
        return new CandidateResult(
            UUID.randomUUID(),
            0,
            uniqueId,
            "candidate-key-fact",
            "candidate-hash-fact",
            ResultStatus.CREATED,
            modelSpecId,
            1,
            modelChecksum,
            1,
            implementationChecksum,
            4,
            List.of(),
            NOW
        );
    }

    private static CandidateResult failedResult(String uniqueId) {
        return failedResult(uniqueId, true);
    }

    private static CandidateResult failedResult(String uniqueId, boolean retryable) {
        return new CandidateResult(
            UUID.randomUUID(),
            1,
            uniqueId,
            "candidate-key-summary",
            "candidate-hash-summary",
            ResultStatus.FAILED,
            null,
            null,
            null,
            null,
            null,
            0,
            List.of(
                new ApplyIssue(
                    "MODEL_IMPORT_CANDIDATE_FAILED",
                    Severity.ERROR,
                    "APPLY",
                    "INTERNAL",
                    retryable,
                    "$.items[1]",
                    uniqueId,
                    null,
                    "Candidate apply failed",
                    "RETRY",
                    "correlation-" + uniqueId
                )
            ),
            NOW
        );
    }

    private static ModelOwnershipSnapshot ownership(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        return new ModelOwnershipSnapshot(
            UUID.randomUUID(),
            modelSpecId,
            PLAN_ID,
            modelRevision,
            modelChecksum,
            implementationRevision,
            implementationChecksum,
            "DBT_MANAGED",
            PROJECT,
            modelSpecId.equals(FACT_ID) ? "model.pjm.fact" : "model.pjm.summary",
            modelRevision,
            modelChecksum,
            "FACT",
            "DRAFT",
            null,
            null,
            "sql-checksum"
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

    private static String baseContext() {
        return """
        {
          "plan": {
            "planId": "%s",
            "tenantId": "default",
            "lifecycleStatus": "DESIGNING",
            "version": 3,
            "businessScopeVersion": 4,
            "sourcesVersion": 5
          },
          "domains": [],
          "sources": []
        }
        """.formatted(PLAN_ID);
    }
}
