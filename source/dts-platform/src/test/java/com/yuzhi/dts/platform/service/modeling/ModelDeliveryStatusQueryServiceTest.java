package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.GovernanceQualitySummaryView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.ServingSyncView;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelDeliveryStatusQueryServiceTest {
    @Test
    void doesNotTreatCandidateQualityPassedAsSucceededWhenRequiredGovernanceEvidenceFailed() {
        WorkbenchView workspace = mock(WorkbenchView.class);
        when(workspace.governanceQuality()).thenReturn(new GovernanceQualitySummaryView(true, EvidenceState.FAILED, "QUALITY_EVIDENCE_FAILED", "失败", 60, java.util.List.of()));
        assertThat(ModelDeliveryStatusQueryService.governanceState(workspace)).isEqualTo("FAILED");
    }

    @Test
    void treatsNoRequiredGovernancePolicyAsSatisfiedWithoutInventingAQualityRun() {
        WorkbenchView workspace = mock(WorkbenchView.class);
        when(workspace.governanceQuality()).thenReturn(GovernanceQualitySummaryView.notEvaluated());
        assertThat(ModelDeliveryStatusQueryService.governanceState(workspace)).isEqualTo("SUCCEEDED");
    }

    @Test
    void rejectsPublishedEvidenceFromAnotherModelOrCandidate() {
        ModelSpecView model = mock(ModelSpecView.class);
        CandidateView candidate = mock(CandidateView.class);
        UUID modelId = UUID.randomUUID();
        UUID otherModel = UUID.randomUUID();
        when(model.id()).thenReturn(modelId); when(model.revision()).thenReturn(4); when(model.checksum()).thenReturn("a".repeat(64));
        when(candidate.id()).thenReturn(UUID.randomUUID());
        ServingSyncView serving = serving(new PublishedRef(otherModel, 4, "a".repeat(64), 1, "b".repeat(64), UUID.randomUUID(), 2, 2, null, Instant.now()));
        assertThat(ModelDeliveryStatusQueryService.matchesPublication(serving, model, candidate)).isFalse();
    }

    @Test
    void rejectsOldRevisionOrChecksumEvidence() {
        ModelSpecView model = mock(ModelSpecView.class);
        UUID modelId = UUID.randomUUID(); UUID candidateId = UUID.randomUUID();
        when(model.id()).thenReturn(modelId); when(model.revision()).thenReturn(5); when(model.checksum()).thenReturn("a".repeat(64));
        CandidateView candidate = mock(CandidateView.class); when(candidate.id()).thenReturn(candidateId);
        assertThat(ModelDeliveryStatusQueryService.matchesPublication(serving(new PublishedRef(modelId, 4, "a".repeat(64), 1, "b".repeat(64), candidateId, 2, 2, null, Instant.now())), model, candidate)).isFalse();
        assertThat(ModelDeliveryStatusQueryService.matchesPublication(serving(new PublishedRef(modelId, 5, "c".repeat(64), 1, "b".repeat(64), candidateId, 2, 2, null, Instant.now())), model, candidate)).isFalse();
    }

    @Test
    void explicitCandidateFromAnotherModelIsRejectedInsteadOfBecomingWorkspaceEvidence() {
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        ModelReleaseCandidateApplicationService candidates = mock(ModelReleaseCandidateApplicationService.class);
        com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService authoring = mock(com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService.class);
        com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService servingService = mock(com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.class);
        ModelDeliveryStatusQueryService service = new ModelDeliveryStatusQueryService(models, candidates, authoring, servingService, mock(CandidateQualityRuleContextService.class));
        UUID modelId = UUID.randomUUID(); UUID planId = UUID.randomUUID(); UUID candidateId = UUID.randomUUID();
        ModelSpecView model = mock(ModelSpecView.class); when(model.id()).thenReturn(modelId); when(model.planId()).thenReturn(planId);
        when(models.get("tenant", modelId)).thenReturn(model);
        when(authoring.context("tenant", "actor", modelId, null, null, true)).thenReturn(mock(com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView.class));
        CandidateView candidate = mock(CandidateView.class); when(candidate.entries()).thenReturn(java.util.List.of());
        WorkbenchView workspace = mock(WorkbenchView.class); when(workspace.candidate()).thenReturn(candidate);
        when(candidates.workspaceForCandidate("tenant", "actor", planId, candidateId)).thenReturn(workspace);
        assertThatThrownBy(() -> service.get("tenant", "actor", modelId, null, candidateId)).isInstanceOf(ModelReleaseCandidateException.class);
    }

    private static ServingSyncView serving(PublishedRef published) {
        return new ServingSyncView(published.modelSpecId(), "dataset-key", "SYNC_FAILED", 1, "failure", null, Instant.now(), 1, false, published, null);
    }

    @Test
    void defaultCandidateForAnotherModelDoesNotExposeItsWorkspaceOrEvidence() {
        Fixture fixture = new Fixture();
        CandidateView otherCandidate = mock(CandidateView.class);
        when(otherCandidate.entries()).thenReturn(java.util.List.of());
        WorkbenchView workspace = mock(WorkbenchView.class);
        when(workspace.candidate()).thenReturn(otherCandidate);
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        assertThat(result.candidate()).isNull();
        assertThat(result.workspace()).isNull();
        assertThat(result.steps()).allMatch(step -> !step.matchesCurrentTarget());
    }

    @Test
    void explicitCandidateFromAnotherEnvironmentIsRejectedWhileDefaultSelectionIsCleared() {
        Fixture fixture = new Fixture();
        CandidateView candidate = fixture.currentCandidate("prod", DeliveryStatus.DRAFT, fixture.modelId, fixture.modelRevision, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(candidate, java.util.List.of());
        when(fixture.candidates.workspaceForCandidate("tenant", "actor", fixture.planId, fixture.candidateId)).thenReturn(workspace);
        assertThatThrownBy(() -> fixture.service.get("tenant", "actor", fixture.modelId, "dev", fixture.candidateId))
            .isInstanceOf(ModelReleaseCandidateException.class);

        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);
        var defaultResult = fixture.service.get("tenant", "actor", fixture.modelId, "dev", null);
        assertThat(defaultResult.candidate()).isNull();
        assertThat(defaultResult.workspace()).isNull();
    }

    @Test
    void changedModelRevisionOrChecksumMakesOldCandidateEvidenceUnknown() {
        Fixture fixture = new Fixture();
        CandidateView oldCandidate = fixture.currentCandidate("prod", DeliveryStatus.PUBLISHED, fixture.modelId, fixture.modelRevision - 1, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(oldCandidate, java.util.List.of());
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        assertThat(result.candidate().matchesCurrentModel()).isFalse();
        assertThat(result.steps()).allMatch(step -> step.state().equals("UNKNOWN") || step.state().equals("NOT_STARTED"));
        assertThat(result.wizard().stream().filter(page -> page.key().equals("delivery")).findFirst().orElseThrow().primaryAction()).isNull();
    }

    @Test
    void staleCandidateCanOfferOnlyItsServerAuthorizedRefreshAction() {
        Fixture fixture = new Fixture();
        CandidateView stale = fixture.currentCandidate("prod", DeliveryStatus.DRAFT, fixture.modelId, fixture.modelRevision - 1, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(stale, java.util.List.of(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction.REFRESH_CANDIDATE));
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        var verification = result.wizard().stream().filter(page -> page.key().equals("verification")).findFirst().orElseThrow();
        assertThat(verification.canEdit()).isTrue();
        assertThat(verification.primaryAction().code()).isEqualTo("REFRESH_CANDIDATE");
        assertThat(result.steps()).allMatch(step -> !step.matchesCurrentTarget());
    }

    @Test
    void pinsBuiltEvidenceToCurrentImplementationRevision() {
        Fixture fixture = new Fixture();
        UUID implementationId = UUID.randomUUID();
        CandidateView candidate = fixture.currentCandidate("prod", DeliveryStatus.BUILT, fixture.modelId, fixture.modelRevision, fixture.checksum, implementationId);
        WorkbenchView workspace = fixture.workspace(candidate, java.util.List.of());
        com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView implementation = mock(com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView.class);
        when(implementation.id()).thenReturn(implementationId);
        when(implementation.implementationRevision()).thenReturn(9);
        when(fixture.authoringContext.implementation()).thenReturn(implementation);
        com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView evidence = mock(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView.class);
        when(evidence.candidateEntryId()).thenReturn(fixture.entryId);
        when(evidence.implementationRevision()).thenReturn(8);
        when(workspace.entryEvidence()).thenReturn(java.util.List.of(evidence));
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        assertThat(result.candidate().matchesCurrentModel()).isFalse();
        assertThat(result.steps().get(0).state()).isEqualTo("UNKNOWN");
    }

    @Test
    void keepsCurrentTargetIdentityForFailedQualityAndAnalysisEvidence() {
        Fixture fixture = new Fixture();
        CandidateView candidate = fixture.currentCandidate("prod", DeliveryStatus.QUALITY_FAILED, fixture.modelId, fixture.modelRevision, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(candidate, java.util.List.of());
        when(workspace.governanceQuality()).thenReturn(new GovernanceQualitySummaryView(true, EvidenceState.FAILED, "QUALITY_FAILED", "failed", 60, java.util.List.of()));
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);
        UUID physicalAssetId = UUID.randomUUID();
        PublishedRef published = new PublishedRef(fixture.modelId, fixture.modelRevision, fixture.checksum, 1, "b".repeat(64), fixture.candidateId, 2, 2, physicalAssetId, Instant.now());
        when(fixture.serving.get("tenant", fixture.modelId)).thenReturn(serving(published));

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        assertThat(result.steps().stream().filter(step -> step.key().equals("quality")).findFirst().orElseThrow().matchesCurrentTarget()).isTrue();
        var analysis = result.steps().stream().filter(step -> step.key().equals("analysis")).findFirst().orElseThrow();
        assertThat(analysis.state()).isEqualTo("FAILED");
        assertThat(analysis.matchesCurrentTarget()).isTrue();
    }

    @Test
    void usesCurrentMaterializedCatalogAssetBeforePublicationExists() {
        Fixture fixture = new Fixture();
        CandidateView candidate = fixture.currentCandidate("prod", DeliveryStatus.BUILT, fixture.modelId, fixture.modelRevision, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(candidate, java.util.List.of());
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);
        UUID datasetId = UUID.randomUUID();
        var asset = new CandidateQualityRuleContextService.QualityAssetView(fixture.modelId, fixture.modelRevision, datasetId, "lake:db.table", "db.table", false, "QUALITY_DATASET_NOT_DEFAULT_LAKE", java.util.List.of());
        var qualityContext = new CandidateQualityRuleContextService.CandidateQualityContextView(fixture.candidateId, 3, DeliveryStatus.BUILT, java.util.List.of(asset), new CandidateQualityRuleContextService.QualityActionView("NONE", null), null, null);
        when(fixture.qualityContexts.context(candidate, null)).thenReturn(qualityContext);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        var catalog = result.steps().stream().filter(step -> step.key().equals("catalog")).findFirst().orElseThrow();
        assertThat(catalog.state()).isEqualTo("SUCCEEDED");
        assertThat(catalog.resourceId()).isEqualTo(datasetId.toString());
        assertThat(catalog.outputs()).allMatch(output -> output.matchesCurrentTarget() && output.state().equals("SUCCEEDED"));
    }

    @Test
    void mapsQualityContextPrimaryOnlyWhenItsExactCandidateIsCurrent() {
        Fixture fixture = new Fixture();
        CandidateView candidate = fixture.currentCandidate("prod", DeliveryStatus.BUILT, fixture.modelId, fixture.modelRevision, fixture.checksum, null);
        WorkbenchView workspace = fixture.workspace(candidate, java.util.List.of());
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(workspace);
        when(fixture.candidates.canMaintainForRead("tenant", "actor", fixture.planId)).thenReturn(true);
        var qualityContext = new CandidateQualityRuleContextService.CandidateQualityContextView(
            fixture.candidateId, 3, DeliveryStatus.BUILT, java.util.List.of(),
            new CandidateQualityRuleContextService.QualityActionView("CONFIGURE_QUALITY_RULES", "MODEL_SPEC_GOVERNANCE_QUALITY_RULE_REQUIRED"),
            "MODEL_SPEC_GOVERNANCE_QUALITY_RULE_REQUIRED", "configure"
        );
        when(fixture.qualityContexts.context(candidate, null)).thenReturn(qualityContext);

        var result = fixture.service.get("tenant", "actor", fixture.modelId, null, null);

        var primary = result.wizard().stream().filter(page -> page.key().equals("verification")).findFirst().orElseThrow().primaryAction();
        assertThat(primary.code()).isEqualTo("CONFIGURE_QUALITY_RULES");
        assertThat(primary.targetId()).isEqualTo(fixture.candidateId);
        assertThat(result.actions()).extracting(ModelDeliveryStatusQueryService.ActionView::code).contains("CONFIGURE_QUALITY_RULES");
    }

    @Test
    void mapsAuthoritativeWorkspaceActionsToOnePrimaryActionPerWizardPage() {
        Fixture fixture = new Fixture();
        CandidateView noCandidate = null;
        WorkbenchView empty = mock(WorkbenchView.class);
        when(empty.candidate()).thenReturn(noCandidate);
        when(empty.allowedActions()).thenReturn(java.util.List.of(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction.CREATE_CANDIDATE));
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(empty);
        var emptyResult = fixture.service.get("tenant", "actor", fixture.modelId, null, null);
        assertThat(emptyResult.wizard().stream().filter(page -> page.key().equals("verification")).findFirst().orElseThrow().primaryAction().code()).isEqualTo("CREATE_CANDIDATE");

        CandidateView reviewCandidate = fixture.currentCandidate("prod", DeliveryStatus.REVIEW_PENDING, fixture.modelId, fixture.modelRevision, fixture.checksum, null);
        WorkbenchView review = fixture.workspace(reviewCandidate, java.util.List.of(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction.APPROVE));
        when(fixture.candidates.workspace("tenant", "actor", fixture.planId)).thenReturn(review);
        var reviewResult = fixture.service.get("tenant", "actor", fixture.modelId, null, null);
        assertThat(reviewResult.wizard().stream().filter(page -> page.key().equals("delivery")).findFirst().orElseThrow().primaryAction().code()).isEqualTo("APPROVE");
    }

    private static final class Fixture {
        final ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        final ModelReleaseCandidateApplicationService candidates = mock(ModelReleaseCandidateApplicationService.class);
        final com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService authoring = mock(com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService.class);
        final com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService serving = mock(com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.class);
        final CandidateQualityRuleContextService qualityContexts = mock(CandidateQualityRuleContextService.class);
        final ModelDeliveryStatusQueryService service = new ModelDeliveryStatusQueryService(models, candidates, authoring, serving, qualityContexts);
        final UUID modelId = UUID.randomUUID(); final UUID planId = UUID.randomUUID(); final UUID candidateId = UUID.randomUUID(); final UUID entryId = UUID.randomUUID();
        final int modelRevision = 5; final String checksum = "a".repeat(64);
        final com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView authoringContext = mock(com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView.class);

        Fixture() {
            ModelSpecView model = mock(ModelSpecView.class);
            when(model.id()).thenReturn(modelId); when(model.planId()).thenReturn(planId); when(model.revision()).thenReturn(modelRevision); when(model.checksum()).thenReturn(checksum);
            when(models.get("tenant", modelId)).thenReturn(model);
            when(authoring.context("tenant", "actor", modelId, null, null, true)).thenReturn(authoringContext);
            when(authoringContext.allowedActions()).thenReturn(java.util.List.of());
            ServingSyncView servingView = mock(ServingSyncView.class);
            when(serving.get("tenant", modelId)).thenReturn(servingView);
        }

        CandidateView currentCandidate(String environment, DeliveryStatus status, UUID scopedModelId, int revision, String candidateChecksum, UUID implementationId) {
            EntryView entry = mock(EntryView.class);
            when(entry.id()).thenReturn(entryId); when(entry.modelSpecId()).thenReturn(scopedModelId); when(entry.planId()).thenReturn(planId);
            when(entry.revision()).thenReturn(revision); when(entry.checksum()).thenReturn(candidateChecksum); when(entry.implementationId()).thenReturn(implementationId);
            CandidateView candidate = mock(CandidateView.class);
            when(candidate.id()).thenReturn(candidateId); when(candidate.planId()).thenReturn(planId); when(candidate.environment()).thenReturn(environment);
            when(candidate.status()).thenReturn(status); when(candidate.version()).thenReturn(3); when(candidate.entries()).thenReturn(java.util.List.of(entry)); when(candidate.lastModifiedAt()).thenReturn(Instant.now());
            return candidate;
        }

        WorkbenchView workspace(CandidateView candidate, java.util.List<com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction> actions) {
            WorkbenchView workspace = mock(WorkbenchView.class);
            when(workspace.candidate()).thenReturn(candidate); when(workspace.allowedActions()).thenReturn(actions); when(workspace.entryEvidence()).thenReturn(java.util.List.of());
            return workspace;
        }
    }
}
