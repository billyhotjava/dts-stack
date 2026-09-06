package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkbenchView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.WorkspaceAction;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringContract.AuthoringContextView;
import com.yuzhi.dts.platform.service.modeling.authoring.ModelAuthoringDraftService;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticSyncCommandService.ServingSyncView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only, identity-bound projection for the modeling delivery wizard. */
@Service
public class ModelDeliveryStatusQueryService {
    private final ModelSpecApplicationService models;
    private final ModelReleaseCandidateApplicationService candidates;
    private final ModelAuthoringDraftService authoring;
    private final CatalogModelSemanticSyncCommandService serving;
    private final CandidateQualityRuleContextService qualityContexts;

    public ModelDeliveryStatusQueryService(
        ModelSpecApplicationService models,
        ModelReleaseCandidateApplicationService candidates,
        ModelAuthoringDraftService authoring,
        CatalogModelSemanticSyncCommandService serving,
        CandidateQualityRuleContextService qualityContexts
    ) {
        this.models = models;
        this.candidates = candidates;
        this.authoring = authoring;
        this.serving = serving;
        this.qualityContexts = qualityContexts;
    }

    @Transactional(readOnly = true)
    public DeliveryStatusView get(String tenantId, String actorId, UUID modelSpecId, String environment, UUID candidateId) {
        ModelSpecView model = models.get(tenantId, modelSpecId);
        AuthoringContextView authoringContext = authoring.context(tenantId, actorId, modelSpecId, null, null, true);
        WorkbenchView workspace = candidateId == null ? candidates.workspaceForCurrentModel(
            tenantId, actorId, model.planId(), model.id(), model.revision(), model.checksum(), environment
        ) :
            candidates.workspaceForCandidate(tenantId, actorId, model.planId(), candidateId);
        CandidateView candidate = workspace.candidate();
        if (candidate != null && environment != null && !environment.isBlank() && !environment.trim().equals(candidate.environment())) {
            if (candidateId != null) throw new ModelReleaseCandidateException("MODEL_DELIVERY_STATUS_ENVIRONMENT_MISMATCH", "Release candidate environment does not match request", ModelReleaseCandidateException.Kind.NOT_FOUND);
            candidate = null;
            workspace = null;
        }
        EntryView entry = candidate == null ? null : candidate.entries().stream().filter(item -> item.modelSpecId().equals(model.id())).findFirst().orElse(null);
        if (candidateId != null && entry == null) {
            throw new ModelReleaseCandidateException("MODEL_DELIVERY_STATUS_CANDIDATE_SCOPE_NOT_FOUND", "Release candidate does not belong to this model", ModelReleaseCandidateException.Kind.NOT_FOUND);
        }
        if (candidateId == null && entry == null && candidate != null) { candidate = null; workspace = null; }
        CandidateView selectedCandidate = candidate;
        boolean modelCurrent = entry != null && entry.revision() == model.revision() && entry.checksum().equals(model.checksum()) && entry.planId().equals(model.planId());
        boolean current = modelCurrent && implementationMatches(entry, workspace, authoringContext);
        List<DeliveryAction> allowed = selectedCandidate == null ? List.of() : nullSafe(candidates.allowedActionsForRead(tenantId, actorId, model.planId(), selectedCandidate.id()));
        List<ActionView> deliveryActions = allowed.stream().map(action -> candidateAction(action, selectedCandidate)).toList();
        List<ActionView> workspaceActions = nullSafe(workspace == null ? null : workspace.allowedActions()).stream()
            .map(action -> workspaceAction(action, selectedCandidate)).toList();
        List<ActionView> authoringActions = nullSafe(authoringContext.allowedActions()).stream().map(action -> new ActionView(action.name(), true, null, model.id(), model.revision())).toList();
        List<ActionView> actions = java.util.stream.Stream.of(authoringActions, workspaceActions, deliveryActions)
            .flatMap(List::stream).collect(java.util.stream.Collectors.toMap(ActionView::code, action -> action, (first, ignored) -> first, java.util.LinkedHashMap::new))
            .values().stream().toList();
        boolean canMaintainDelivery = candidates.canMaintainForRead(tenantId, actorId, model.planId());
        CandidateQualityRuleContextService.CandidateQualityContextView qualityContext = selectedCandidate == null || !current || qualityContexts == null
            ? null : qualityContexts.context(selectedCandidate, null);
        actions = withQualityAction(actions, qualityContext, selectedCandidate, canMaintainDelivery);
        ServingSyncView servingView = serving.get(tenantId, model.id());
        List<StepView> steps = List.of(
            step("materialization", materialization(selectedCandidate, current), current, selectedCandidate),
            qualityStep(selectedCandidate, workspace, current),
            step("publication", publication(selectedCandidate, current), current, selectedCandidate),
            catalog(servingView, model, selectedCandidate, current, qualityContext),
            analysis(servingView, model, selectedCandidate, current)
        );
        return new DeliveryStatusView(model.id(), model.revision(), model.checksum(), model.planId(), selectedCandidate == null ? null : selectedCandidate.environment(),
            selectedCandidate == null ? null : new CandidateSummary(selectedCandidate.id(), selectedCandidate.version(), selectedCandidate.status().name(), entry.revision(), entry.checksum(), current, selectedCandidate.lastModifiedAt()),
            Instant.now(), recommended(selectedCandidate, current), workspace, wizard(authoringContext, selectedCandidate, workspace, current, actions, qualityContext), steps, actions);
    }

    private static String materialization(CandidateView candidate, boolean current) {
        if (candidate == null) return "NOT_STARTED";
        if (!current) return "UNKNOWN";
        return switch (candidate.status()) { case BUILDING -> "RUNNING"; case BUILD_FAILED -> "FAILED"; case BUILT, QUALITY_RUNNING, QUALITY_FAILED, QUALITY_PASSED, REVIEW_PENDING, APPROVED, PUBLISHING, PARTIAL, PUBLISHED -> "SUCCEEDED"; default -> "NOT_STARTED"; };
    }
    static StepView qualityStep(CandidateView candidate, WorkbenchView workspace, boolean current) {
        if (candidate == null) return step("quality", "NOT_STARTED", false, null);
        if (!current || workspace == null) return step("quality", "UNKNOWN", false, candidate);
        String state = switch (candidate.status()) { case QUALITY_RUNNING -> "RUNNING"; case QUALITY_FAILED -> "FAILED"; case QUALITY_PASSED, REVIEW_PENDING, APPROVED, PUBLISHING, PARTIAL, PUBLISHED -> governanceState(workspace); case BUILT -> "WAITING_INPUT"; default -> "NOT_STARTED"; };
        String reason = state.equals("SUCCEEDED") ? null : workspace.governanceQuality() == null ? "MODEL_DELIVERY_QUALITY_EVIDENCE_UNAVAILABLE" : workspace.governanceQuality().code();
        return new StepView("quality", state, reason, qualityMessage(state), candidate.version(), true, candidate.id().toString(), candidate.lastModifiedAt(), List.of());
    }
    static String governanceState(WorkbenchView workspace) {
        if (workspace == null) return "WAITING_INPUT";
        if (workspace.governanceQuality() == null || !workspace.governanceQuality().required()) return "SUCCEEDED";
        if (workspace.governanceQuality().passed()) return "SUCCEEDED";
        return switch (workspace.governanceQuality().state()) { case RUNNING -> "RUNNING"; case FAILED, STALE -> "FAILED"; default -> "WAITING_INPUT"; };
    }
    private static String publication(CandidateView candidate, boolean current) {
        if (candidate == null || !current) return candidate == null ? "NOT_STARTED" : "UNKNOWN";
        return switch (candidate.status()) { case PUBLISHING -> "RUNNING"; case PARTIAL -> "FAILED"; case PUBLISHED -> "SUCCEEDED"; case QUALITY_PASSED, REVIEW_PENDING, APPROVED -> "WAITING_INPUT"; default -> "NOT_STARTED"; };
    }
    private static StepView step(String key, String state, boolean current, CandidateView candidate) {
        String reason = current ? null : candidate == null ? "MODEL_DELIVERY_CANDIDATE_REQUIRED" : "MODEL_DELIVERY_EVIDENCE_STALE";
        String message = current ? stepMessage(key, state) : "当前模型版本没有匹配的交付证据";
        return new StepView(key, state, reason, message, candidate == null ? null : candidate.version(), current, candidate == null ? null : candidate.id().toString(), candidate == null ? null : candidate.lastModifiedAt(), List.of());
    }
    private static StepView catalog(
        ServingSyncView serving,
        ModelSpecView model,
        CandidateView candidate,
        boolean current,
        CandidateQualityRuleContextService.CandidateQualityContextView qualityContext
    ) {
        boolean published = current && matchesPublication(serving, model, candidate);
        List<CandidateQualityRuleContextService.QualityAssetView> assets = qualityContext == null ? List.of() : qualityContext.assets().stream()
            .filter(asset -> model.id().equals(asset.modelSpecId()) && model.revision() == asset.modelRevision())
            .toList();
        List<OutputView> outputs = assets.stream().map(asset -> {
            boolean registered = asset.configurationBlockerCode() == null || !"QUALITY_DATASET_REGISTRATION_MISSING".equals(asset.configurationBlockerCode());
            String state = registered ? "SUCCEEDED" : "NOT_STARTED";
            String code = registered ? null : asset.configurationBlockerCode();
            String resourceId = asset.datasetId() == null ? null : asset.datasetId().toString();
            return new OutputView(resourceId, state, code, registered ? "资产已登记" : "资产尚未登记", current, null);
        }).toList();
        if (outputs.isEmpty() && published) {
            String datasetId = physicalAssetId(serving);
            boolean registered = datasetId != null && serving.catalogAssetKey() != null;
            outputs = List.of(new OutputView(datasetId, registered ? "SUCCEEDED" : "NOT_STARTED", registered ? null : "MODEL_DELIVERY_CATALOG_NOT_REGISTERED", registered ? "资产已登记" : "资产尚未登记", true, serving.updatedAt()));
        }
        boolean matched = current && (!outputs.isEmpty());
        boolean registered = matched && outputs.stream().allMatch(output -> "SUCCEEDED".equals(output.state()));
        String state = !matched ? "UNKNOWN" : registered ? "SUCCEEDED" : "NOT_STARTED";
        String code = !matched ? "MODEL_DELIVERY_EVIDENCE_STALE" : registered ? null : outputs.stream().map(OutputView::reasonCode).filter(java.util.Objects::nonNull).findFirst().orElse("MODEL_DELIVERY_CATALOG_NOT_REGISTERED");
        String datasetId = outputs.isEmpty() ? null : outputs.get(0).resourceId();
        return new StepView("catalog", state, code, registered ? "资产已登记" : "资产尚未登记", null, matched, datasetId, published ? serving.updatedAt() : null, outputs);
    }
    private static StepView analysis(ServingSyncView serving, ModelSpecView model, CandidateView candidate, boolean current) {
        boolean matched = current && matchesPublication(serving, model, candidate);
        String state = !matched ? "UNKNOWN" : serving.servingReady() ? "SUCCEEDED" : "NOT_REGISTERED".equals(serving.syncStatus()) ? "NOT_STARTED" : "SYNC_FAILED".equals(serving.syncStatus()) ? "FAILED" : "RUNNING";
        String code = !matched ? "MODEL_DELIVERY_EVIDENCE_STALE" : serving.lastSyncError();
        String message = serving.servingReady() ? "分析准备已完成" : serving.lastSyncError() == null ? "分析准备处理中" : "分析准备失败";
        String datasetId = physicalAssetId(serving);
        return new StepView("analysis", state, code, message, null, matched, datasetId, serving.updatedAt(), List.of(new OutputView(datasetId, state, code, message, matched, serving.updatedAt())));
    }
    static boolean matchesPublication(ServingSyncView serving, ModelSpecView model, CandidateView candidate) {
        var published = serving.latestPublishedRef();
        return published != null && published.modelSpecId().equals(model.id()) && published.modelRevision() == model.revision() && published.modelChecksum().equals(model.checksum()) && (candidate == null || published.candidateId().equals(candidate.id()));
    }
    private static String physicalAssetId(ServingSyncView serving) {
        if (serving.latestPublishedRef() != null && serving.latestPublishedRef().physicalAssetId() != null) return serving.latestPublishedRef().physicalAssetId().toString();
        return serving.servingRef() == null || serving.servingRef().physicalAssetId() == null ? null : serving.servingRef().physicalAssetId().toString();
    }
    private static String qualityMessage(String state) { return switch (state) { case "SUCCEEDED" -> "质量检查已通过"; case "RUNNING" -> "质量检查处理中"; case "FAILED" -> "质量检查未通过"; case "WAITING_INPUT" -> "等待配置或执行质量检查"; default -> "尚未开始质量检查"; }; }
    private static String stepMessage(String key, String state) {
        String subject = switch (key) { case "materialization" -> "物化"; case "publication" -> "发布"; default -> "交付"; };
        return switch (state) { case "SUCCEEDED" -> subject + "已完成"; case "RUNNING" -> subject + "处理中"; case "FAILED" -> subject + "失败"; case "WAITING_INPUT" -> "等待" + subject; default -> "尚未开始" + subject; };
    }
    private static String recommended(CandidateView candidate, boolean current) {
        if (candidate == null || !current) return "implementation";
        return switch (candidate.status()) { case DRAFT, BUILDING, BUILD_FAILED, BUILT, QUALITY_RUNNING, QUALITY_FAILED -> "verification"; default -> "delivery"; };
    }
    private static List<WizardPageView> wizard(AuthoringContextView context, CandidateView candidate, WorkbenchView workspace, boolean current, List<ActionView> actions, CandidateQualityRuleContextService.CandidateQualityContextView qualityContext) {
        boolean editModel = nullSafe(context.allowedActions()).stream().anyMatch(action -> action.name().equals("EDIT_MODEL") || action.name().equals("FORK_DRAFT"));
        boolean editImplementation = nullSafe(context.allowedActions()).stream().anyMatch(action -> action.name().equals("EDIT_IMPLEMENTATION") || action.name().equals("SAVE") || action.name().equals("VALIDATE") || action.name().equals("COMMIT"));
        boolean forkRequired = context.publishedForkRequired() && nullSafe(context.allowedActions()).stream().anyMatch(action -> action.name().equals("FORK_DRAFT"));
        ActionView definition = !editModel ? null : forkRequired ? action("FORK_DRAFT", actions) : new ActionView("SAVE_DEFINITION", true, null, null, null);
        ActionView implementation = context.implementation() != null && context.openDraft() == null
            ? new ActionView("NEXT", true, null, null, null)
            : first(actions, "COMMIT", "VALIDATE", "EDIT_IMPLEMENTATION", "SAVE");
        ActionView verification = candidate == null
            ? first(actions, "CREATE_CANDIDATE")
            : !current ? first(actions, "REFRESH_CANDIDATE", "CREATE_REPLACEMENT_CANDIDATE", "REMATERIALIZE")
            : qualityPrimary(qualityContext, actions, candidate);
        if (verification == null && current && candidate != null) verification = verificationAction(candidate, actions);
        boolean verificationEditable = current || verification != null;
        if (verification == null && current && selectedQualityPassed(candidate) && "SUCCEEDED".equals(governanceState(workspace))) {
            verification = new ActionView("NEXT", true, null, null, null);
        }
        ActionView delivery = !current ? null : first(actions,
            "SUBMIT_REVIEW", "APPROVE", "REJECT", "PUBLISH", "RETRY_PUBLICATION", "CREATE_REPLACEMENT_CANDIDATE", "ROLLBACK");
        return List.of(
            new WizardPageView("definition", true, editModel, editModel ? null : "MODEL_AUTHORING_READONLY", definition),
            new WizardPageView("implementation", true, editImplementation, editImplementation ? null : "MODEL_AUTHORING_READONLY", implementation),
            new WizardPageView("verification", true, verificationEditable, current ? null : "MODEL_DELIVERY_EVIDENCE_STALE", verification),
            new WizardPageView("delivery", true, current, current ? null : "MODEL_DELIVERY_EVIDENCE_STALE", delivery)
        );
    }

    private static List<ActionView> withQualityAction(
        List<ActionView> actions,
        CandidateQualityRuleContextService.CandidateQualityContextView qualityContext,
        CandidateView candidate,
        boolean canMaintainDelivery
    ) {
        if (qualityContext == null || candidate == null || qualityContext.primaryAction() == null) return actions;
        String code = qualityContext.primaryAction().code();
        if ("NONE".equals(code) || ("RUN_QUALITY".equals(code) && action(code, actions) == null)) return actions;
        if (("CONFIGURE_QUALITY_RULES".equals(code) || "RERUN_GOVERNANCE_QUALITY".equals(code)) && !canMaintainDelivery) return actions;
        if (action(code, actions) != null) return actions;
        return java.util.stream.Stream.concat(actions.stream(), java.util.stream.Stream.of(
            new ActionView(code, true, qualityContext.primaryAction().reasonCode(), candidate.id(), candidate.version())
        )).toList();
    }

    private static ActionView qualityPrimary(
        CandidateQualityRuleContextService.CandidateQualityContextView qualityContext,
        List<ActionView> actions,
        CandidateView candidate
    ) {
        if (qualityContext == null || qualityContext.primaryAction() == null) return null;
        String code = qualityContext.primaryAction().code();
        if ("CONFIGURE_QUALITY_RULES".equals(code) || "RERUN_GOVERNANCE_QUALITY".equals(code)) return action(code, actions);
        if ("RUN_QUALITY".equals(code) && action("RUN_QUALITY", actions) != null) return action("RUN_QUALITY", actions);
        return null;
    }

    private static ActionView verificationAction(CandidateView candidate, List<ActionView> actions) {
        return switch (candidate.status()) {
            case DRAFT -> first(actions, "START_BUILD", "REFRESH_CANDIDATE");
            case BUILD_FAILED -> first(actions, "RETRY_BUILD", "REFRESH_CANDIDATE");
            case BUILT, QUALITY_FAILED -> first(actions, "RUN_QUALITY", "REMATERIALIZE", "START_BUILD", "REFRESH_CANDIDATE");
            default -> first(actions, "REFRESH_CANDIDATE", "REMATERIALIZE", "START_BUILD", "RETRY_BUILD", "RUN_QUALITY");
        };
    }

    private static ActionView first(List<ActionView> actions, String... codes) {
        for (String code : codes) {
            ActionView match = action(code, actions);
            if (match != null) return match;
        }
        return null;
    }

    private static ActionView action(String code, List<ActionView> actions) {
        return actions.stream().filter(action -> action.code().equals(code)).findFirst().orElse(null);
    }

    private static ActionView candidateAction(DeliveryAction action, CandidateView candidate) {
        return new ActionView(action.name(), true, null, candidate.id(), candidate.version());
    }

    private static ActionView workspaceAction(WorkspaceAction action, CandidateView candidate) {
        return new ActionView(action.name(), true, null, candidate == null ? null : candidate.id(), candidate == null ? null : candidate.version());
    }

    private static boolean implementationMatches(EntryView entry, WorkbenchView workspace, AuthoringContextView context) {
        if (entry.implementationId() == null) return true;
        if (context.implementation() == null || !entry.implementationId().equals(context.implementation().id())) return false;
        Integer pinnedRevision = nullSafe(workspace == null ? null : workspace.entryEvidence()).stream()
            .filter(evidence -> entry.id().equals(evidence.candidateEntryId()))
            .map(com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView::implementationRevision)
            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        return pinnedRevision == null || pinnedRevision == context.implementation().implementationRevision();
    }

    private static <T> List<T> nullSafe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static boolean selectedQualityPassed(CandidateView candidate) { return candidate != null && candidate.status() == DeliveryStatus.QUALITY_PASSED; }
    public record DeliveryStatusView(UUID modelSpecId, int modelRevision, String modelChecksum, UUID planId, String environment, CandidateSummary candidate, Instant observedAt, String recommendedStep, WorkbenchView workspace, List<WizardPageView> wizard, List<StepView> steps, List<ActionView> actions) {}
    public record CandidateSummary(UUID id, int version, String status, int entryRevision, String entryChecksum, boolean matchesCurrentModel, Instant updatedAt) {}
    public record WizardPageView(String key, boolean canView, boolean canEdit, String reasonCode, ActionView primaryAction) {}
    public record StepView(String key, String state, String reasonCode, String message, Integer evidenceRevision, boolean matchesCurrentTarget, String resourceId, Instant updatedAt, List<OutputView> outputs) { public StepView { outputs = List.copyOf(outputs == null ? List.of() : outputs); } }
    public record OutputView(String resourceId, String state, String reasonCode, String message, boolean matchesCurrentTarget, Instant updatedAt) {}
    public record ActionView(String code, boolean enabled, String reasonCode, UUID targetId, Integer expectedVersion) {}
}
