package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceFreshness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** Computes the only completion-state projection for the nine-stage data-building journey. */
@Service
public class WarehousePlanStageProjectionService {

    private final WarehousePlanApplicationService warehousePlanService;
    private final ModelSpecApplicationService modelSpecService;

    public WarehousePlanStageProjectionService(
        WarehousePlanApplicationService warehousePlanService,
        ModelSpecApplicationService modelSpecService
    ) {
        this.warehousePlanService = warehousePlanService;
        this.modelSpecService = modelSpecService;
    }

    public StageProjection project(String tenantId, UUID planId, AccessContext accessContext) {
        WarehousePlanHeader plan = warehousePlanService.get(tenantId, planId);
        OwnerRead<CategoryScopeView> category = ownerRead(() -> warehousePlanService.getCategoryScope(tenantId, planId).value());
        OwnerRead<PlanningPolicyView> policy = ownerRead(() -> warehousePlanService.getPlanningPolicy(tenantId, planId).value());
        OwnerRead<SourceInventoryView> sources = ownerRead(() -> warehousePlanService.getSources(tenantId, planId, accessContext));
        OwnerRead<List<ModelSpecView>> models = ownerRead(() -> modelSpecService.list(tenantId, planId, null, null, null));

        List<StageEvidence> evidence = new ArrayList<>();
        evidence.add(dataConnectionEvidence(plan.onboardingMode(), sources));
        evidence.add(sourceInventoryEvidence(sources));
        evidence.add(warehousePlanningEvidence(plan.onboardingMode(), category, policy, sources));
        evidence.add(modelDesignEvidence(planId, models));
        return compute(planId, plan.onboardingMode(), evidence, Instant.now());
    }

    public static StageProjection compute(
        UUID planId,
        OnboardingMode onboardingMode,
        List<StageEvidence> evidence,
        Instant computedAt
    ) {
        Objects.requireNonNull(planId, "planId is required");
        Objects.requireNonNull(onboardingMode, "onboardingMode is required");
        Objects.requireNonNull(computedAt, "computedAt is required");

        Map<StageCode, StageEvidence> byStage = new EnumMap<>(StageCode.class);
        if (evidence != null) {
            for (StageEvidence item : evidence) {
                if (item != null && item.stageCode() != null) {
                    byStage.put(item.stageCode(), item);
                }
            }
        }

        List<StageView> stages = new ArrayList<>(StageCode.values().length);
        for (StageCode stageCode : StageCode.values()) {
            stages.add(toView(planId, stageCode, byStage.get(stageCode)));
        }

        StageView current = selectCurrent(onboardingMode, stages);
        PrimaryBlocker primaryBlocker = current == null ? null : blocker(current);
        NextAction nextAction = current == null ? null : new NextAction(current.actionLabel(), current.actionPath());
        return new StageProjection(
            planId,
            current == null ? null : current.code(),
            primaryBlocker,
            nextAction,
            stages,
            computedAt
        );
    }

    private static StageView selectCurrent(OnboardingMode onboardingMode, List<StageView> stages) {
        Map<StageCode, StageView> byCode = new EnumMap<>(StageCode.class);
        stages.forEach(stage -> byCode.put(stage.code(), stage));
        List<StageCode> priority = onboardingMode == OnboardingMode.BUSINESS_FIRST
            ? List.of(
                StageCode.WAREHOUSE_PLANNING,
                StageCode.MODEL_DESIGN,
                StageCode.DATA_STANDARD,
                StageCode.DATA_CONNECTION,
                StageCode.SOURCE_INVENTORY,
                StageCode.BUILD_QUALITY_RELEASE,
                StageCode.DATA_ASSET,
                StageCode.METRIC_SYSTEM,
                StageCode.DATA_SERVICE_OPERATIONS
            )
            : List.of(
                StageCode.DATA_CONNECTION,
                StageCode.SOURCE_INVENTORY,
                StageCode.WAREHOUSE_PLANNING,
                StageCode.MODEL_DESIGN,
                StageCode.DATA_STANDARD,
                StageCode.BUILD_QUALITY_RELEASE,
                StageCode.DATA_ASSET,
                StageCode.METRIC_SYSTEM,
                StageCode.DATA_SERVICE_OPERATIONS
            );
        for (StageCode code : priority) {
            StageView stage = byCode.get(code);
            if (stage != null && stage.status() != StageStatus.COMPLETE) {
                return stage;
            }
        }
        return null;
    }

    private static StageEvidence dataConnectionEvidence(
        OnboardingMode onboardingMode,
        OwnerRead<SourceInventoryView> sources
    ) {
        if (!sources.available() || sources.value() == null) {
            return unknownEvidence(StageCode.DATA_CONNECTION, "DATA_CONNECTION_EVIDENCE_UNAVAILABLE");
        }
        List<SourceBindingView> included = sources
            .value()
            .bindings()
            .stream()
            .filter(Objects::nonNull)
            .filter(binding -> binding.confirmationStatus() != WarehousePlanContract.ConfirmationStatus.EXCLUDED)
            .toList();
        if (included.isEmpty()) {
            if (onboardingMode == OnboardingMode.BUSINESS_FIRST) {
                return notStartedEvidence(StageCode.DATA_CONNECTION, "DATA_CONNECTION_DEFERRED");
            }
            return blockedEvidence(StageCode.DATA_CONNECTION, "DATA_CONNECTION_REQUIRED", "A source connection must be verified");
        }
        if (
            included
                .stream()
                .anyMatch(binding ->
                    binding.resolutionStatus() == null ||
                    binding.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.FORBIDDEN ||
                    binding.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.PROVIDER_ERROR ||
                    binding.freshness() == SourceFreshness.UNKNOWN
                )
        ) {
            return unknownEvidence(StageCode.DATA_CONNECTION, "DATA_CONNECTION_EVIDENCE_UNAVAILABLE");
        }
        if (
            included
                .stream()
                .anyMatch(binding ->
                    binding.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.MISSING ||
                    binding.freshness() == SourceFreshness.STALE
                )
        ) {
            return new StageEvidence(
                StageCode.DATA_CONNECTION,
                StageStatus.BLOCKED,
                EvidenceFreshness.STALE,
                included.size(),
                "DATA_CONNECTION_STALE",
                "A registered source can no longer be verified at its confirmed version",
                null,
                null
            );
        }
        boolean verified = included
            .stream()
            .anyMatch(binding ->
                binding.resolutionStatus() == SourceReferenceResolver.ResolutionStatus.AVAILABLE &&
                binding.freshness() == SourceFreshness.CURRENT
            );
        return verified
            ? completeEvidence(StageCode.DATA_CONNECTION, included.size())
            : blockedEvidence(StageCode.DATA_CONNECTION, "DATA_CONNECTION_REQUIRED", "A source connection must be verified");
    }

    private static StageEvidence sourceInventoryEvidence(OwnerRead<SourceInventoryView> sources) {
        if (!sources.available() || sources.value() == null) {
            return unknownEvidence(StageCode.SOURCE_INVENTORY, "SOURCE_EVIDENCE_UNAVAILABLE");
        }
        SourceInventoryView inventory = sources.value();
        int evidenceCount = inventory.bindings().size();
        if (inventory.readiness() == SourceInventoryReadiness.READY) {
            return completeEvidence(StageCode.SOURCE_INVENTORY, evidenceCount);
        }
        if (inventory.readiness() == SourceInventoryReadiness.NOT_REQUIRED_YET) {
            return notStartedEvidence(StageCode.SOURCE_INVENTORY, "SOURCE_INVENTORY_DEFERRED");
        }
        boolean unknown = inventory.issues().stream().anyMatch(issue -> "SOURCE_UNKNOWN".equals(issue.code()));
        if (unknown) {
            return unknownEvidence(StageCode.SOURCE_INVENTORY, "SOURCE_EVIDENCE_UNAVAILABLE");
        }
        boolean stale = inventory.issues().stream().anyMatch(issue -> "SOURCE_STALE".equals(issue.code()));
        return new StageEvidence(
            StageCode.SOURCE_INVENTORY,
            StageStatus.BLOCKED,
            stale ? EvidenceFreshness.STALE : EvidenceFreshness.CURRENT,
            evidenceCount,
            WarehousePlanContract.SOURCE_INVENTORY_INCOMPLETE,
            "Source inventory must contain verified and confirmed sources",
            null,
            null
        );
    }

    private static StageEvidence warehousePlanningEvidence(
        OnboardingMode onboardingMode,
        OwnerRead<CategoryScopeView> category,
        OwnerRead<PlanningPolicyView> policy,
        OwnerRead<SourceInventoryView> sources
    ) {
        if (!category.available() || category.value() == null) {
            return unknownEvidence(StageCode.WAREHOUSE_PLANNING, "CATEGORY_SCOPE_EVIDENCE_UNAVAILABLE");
        }
        if (category.value().readiness() != CategoryReadiness.READY) {
            return blockedEvidence(
                StageCode.WAREHOUSE_PLANNING,
                WarehousePlanContract.CATEGORY_SCOPE_INCOMPLETE,
                "At least one available business category must be confirmed"
            );
        }
        if (!policy.available() || policy.value() == null) {
            return unknownEvidence(StageCode.WAREHOUSE_PLANNING, "PLANNING_POLICY_EVIDENCE_UNAVAILABLE");
        }
        if (
            policy.value().readiness() != PlanningPolicyReadiness.MODEL_DESIGN_READY &&
            policy.value().readiness() != PlanningPolicyReadiness.IMPLEMENTATION_READY
        ) {
            return blockedEvidence(
                StageCode.WAREHOUSE_PLANNING,
                WarehousePlanContract.PLANNING_POLICY_INCOMPLETE,
                "A supported warehouse layer scheme must be selected"
            );
        }
        if (!sources.available() || sources.value() == null) {
            return unknownEvidence(StageCode.WAREHOUSE_PLANNING, "SOURCE_EVIDENCE_UNAVAILABLE");
        }
        boolean sourceReady = sources.value().readiness() == SourceInventoryReadiness.READY;
        boolean conceptualDeferral =
            onboardingMode == OnboardingMode.BUSINESS_FIRST &&
            sources.value().readiness() == SourceInventoryReadiness.NOT_REQUIRED_YET &&
            policy.value().conceptualDesignAllowed();
        if (!sourceReady && !conceptualDeferral) {
            return blockedEvidence(
                StageCode.WAREHOUSE_PLANNING,
                WarehousePlanContract.SOURCE_INVENTORY_INCOMPLETE,
                "A verified source is required unless conceptual dimension design is explicitly allowed"
            );
        }
        return completeEvidence(StageCode.WAREHOUSE_PLANNING, 3);
    }

    private static StageEvidence modelDesignEvidence(UUID planId, OwnerRead<List<ModelSpecView>> models) {
        if (!models.available() || models.value() == null) {
            return unknownEvidence(StageCode.MODEL_DESIGN, "MODEL_DESIGN_EVIDENCE_UNAVAILABLE");
        }
        long validModels = models
            .value()
            .stream()
            .filter(Objects::nonNull)
            .filter(model -> model.contractVersion() == ModelSpecContract.CONTRACT_VERSION)
            .filter(model -> Objects.equals(planId, model.planId()))
            .filter(model -> model.modelType() != null)
            .filter(model -> model.compatibilityMode() == ModelSpecContract.CompatibilityMode.CANONICAL)
            .filter(model -> model.status() != null && model.status() != ModelSpecContract.ModelStatus.ARCHIVED)
            .count();
        if (validModels == 0) {
            return blockedEvidence(StageCode.MODEL_DESIGN, "MODEL_SPEC_REQUIRED", "Create at least one valid model");
        }
        return completeEvidence(StageCode.MODEL_DESIGN, Math.toIntExact(validModels));
    }

    private static StageEvidence completeEvidence(StageCode stageCode, int count) {
        return new StageEvidence(stageCode, StageStatus.COMPLETE, EvidenceFreshness.CURRENT, count, null, null, null, null);
    }

    private static StageEvidence blockedEvidence(StageCode stageCode, String blockerCode, String blockerMessage) {
        return new StageEvidence(
            stageCode,
            StageStatus.BLOCKED,
            EvidenceFreshness.CURRENT,
            0,
            blockerCode,
            blockerMessage,
            null,
            null
        );
    }

    private static StageEvidence notStartedEvidence(StageCode stageCode, String blockerCode) {
        return new StageEvidence(
            stageCode,
            StageStatus.NOT_STARTED,
            EvidenceFreshness.UNAVAILABLE,
            0,
            blockerCode,
            "This input is deferred by the selected onboarding mode",
            null,
            null
        );
    }

    private static StageEvidence unknownEvidence(StageCode stageCode, String blockerCode) {
        return new StageEvidence(
            stageCode,
            StageStatus.UNKNOWN,
            EvidenceFreshness.UNAVAILABLE,
            0,
            blockerCode,
            "The owning service could not provide current evidence",
            null,
            null
        );
    }

    private static <T> OwnerRead<T> ownerRead(Supplier<T> supplier) {
        try {
            T value = supplier.get();
            return value == null ? OwnerRead.unavailable() : OwnerRead.available(value);
        } catch (RuntimeException exception) {
            return OwnerRead.unavailable();
        }
    }

    private static StageView toView(UUID planId, StageCode stageCode, StageEvidence evidence) {
        if (evidence == null) {
            Action action = defaultAction(planId, stageCode, repairTarget(stageCode, null));
            return new StageView(
                stageCode,
                StageStatus.NOT_STARTED,
                EvidenceFreshness.UNAVAILABLE,
                0,
                stageCode.name() + "_NOT_STARTED",
                "No completion evidence is available for this stage",
                action.label(),
                action.path()
            );
        }

        StageStatus effectiveStatus = evidence.status() == null ? StageStatus.UNKNOWN : evidence.status();
        EvidenceFreshness freshness = evidence.freshness() == null ? EvidenceFreshness.UNAVAILABLE : evidence.freshness();
        String blockerCode = evidence.blockerCode();
        String blockerMessage = evidence.blockerMessage();
        if (effectiveStatus == StageStatus.COMPLETE && freshness != EvidenceFreshness.CURRENT) {
            effectiveStatus = StageStatus.UNKNOWN;
            blockerCode = freshness == EvidenceFreshness.STALE ? "EVIDENCE_STALE" : "EVIDENCE_UNAVAILABLE";
            blockerMessage = "Completion evidence is not current";
        }
        if (effectiveStatus != StageStatus.COMPLETE && isBlank(blockerCode)) {
            blockerCode = stageCode.name() + "_" + effectiveStatus.name();
            blockerMessage = "This stage has not produced current completion evidence";
        }
        Action action = defaultAction(planId, stageCode, repairTarget(stageCode, blockerCode));
        return new StageView(
            stageCode,
            effectiveStatus,
            freshness,
            Math.max(0, evidence.evidenceCount()),
            blockerCode,
            blockerMessage,
            action.label(),
            action.path()
        );
    }

    private static PrimaryBlocker blocker(StageView stage) {
        return new PrimaryBlocker(stage.code(), stage.blockerCode(), stage.blockerMessage());
    }

    private static Action defaultAction(UUID planId, StageCode stageCode, RepairTarget repairTarget) {
        String planRoot = "/modeling/plans/" + planId;
        String planQuery = "?planId=" + planId;
        return switch (stageCode) {
            case DATA_CONNECTION -> new Action("Check data connections", "/foundation/data-sources");
            case SOURCE_INVENTORY -> new Action("Continue source inventory", planRoot + "/baseline?tab=sources");
            case WAREHOUSE_PLANNING -> new Action(
                "Complete warehouse planning",
                planRoot + "/baseline?tab=" + planningTab(repairTarget)
            );
            case DATA_STANDARD -> new Action("Bind data standards", "/modeling/models" + planQuery);
            case MODEL_DESIGN -> new Action("Continue model design", "/modeling/models" + planQuery);
            case BUILD_QUALITY_RELEASE -> new Action("Review build and quality gates", planRoot + "/implementation");
            case DATA_ASSET -> new Action("Review registered assets", "/catalog/assets" + planQuery);
            case METRIC_SYSTEM -> new Action("Review metric bindings", "/modeling/metric-workbench" + planQuery);
            case DATA_SERVICE_OPERATIONS -> new Action("Review services and runs", "/ops/instances" + planQuery);
        };
    }

    private static RepairTarget repairTarget(StageCode stageCode, String blockerCode) {
        return switch (stageCode) {
            case DATA_CONNECTION -> RepairTarget.DATA_CONNECTIONS;
            case SOURCE_INVENTORY -> RepairTarget.SOURCE_INVENTORY;
            case WAREHOUSE_PLANNING -> {
                if (
                    WarehousePlanContract.PLANNING_POLICY_INCOMPLETE.equals(blockerCode) ||
                    "PLANNING_POLICY_EVIDENCE_UNAVAILABLE".equals(blockerCode)
                ) {
                    yield RepairTarget.WAREHOUSE_LAYERS;
                }
                if (
                    WarehousePlanContract.SOURCE_INVENTORY_INCOMPLETE.equals(blockerCode) ||
                    "SOURCE_EVIDENCE_UNAVAILABLE".equals(blockerCode)
                ) {
                    yield RepairTarget.SOURCE_INVENTORY;
                }
                yield RepairTarget.BUSINESS_CATEGORIES;
            }
            case DATA_STANDARD, MODEL_DESIGN -> RepairTarget.MODELS;
            case BUILD_QUALITY_RELEASE -> RepairTarget.IMPLEMENTATION;
            case DATA_ASSET -> RepairTarget.ASSETS;
            case METRIC_SYSTEM -> RepairTarget.METRICS;
            case DATA_SERVICE_OPERATIONS -> RepairTarget.OPERATIONS;
        };
    }

    private static String planningTab(RepairTarget repairTarget) {
        if (repairTarget == RepairTarget.WAREHOUSE_LAYERS) return "layers";
        if (repairTarget == RepairTarget.SOURCE_INVENTORY) return "sources";
        return "categories";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public enum StageCode {
        DATA_CONNECTION,
        SOURCE_INVENTORY,
        WAREHOUSE_PLANNING,
        DATA_STANDARD,
        MODEL_DESIGN,
        BUILD_QUALITY_RELEASE,
        DATA_ASSET,
        METRIC_SYSTEM,
        DATA_SERVICE_OPERATIONS,
    }

    public enum StageStatus {
        NOT_STARTED,
        IN_PROGRESS,
        BLOCKED,
        COMPLETE,
        UNKNOWN,
    }

    public enum EvidenceFreshness {
        CURRENT,
        STALE,
        UNAVAILABLE,
    }

    private enum RepairTarget {
        DATA_CONNECTIONS,
        SOURCE_INVENTORY,
        BUSINESS_CATEGORIES,
        WAREHOUSE_LAYERS,
        MODELS,
        IMPLEMENTATION,
        ASSETS,
        METRICS,
        OPERATIONS,
    }

    public record StageEvidence(
        StageCode stageCode,
        StageStatus status,
        EvidenceFreshness freshness,
        int evidenceCount,
        String blockerCode,
        String blockerMessage,
        String actionLabel,
        String actionPath
    ) {}

    public record StageView(
        StageCode code,
        StageStatus status,
        EvidenceFreshness freshness,
        int evidenceCount,
        String blockerCode,
        String blockerMessage,
        String actionLabel,
        String actionPath
    ) {}

    public record PrimaryBlocker(StageCode stageCode, String code, String message) {}

    public record NextAction(String label, String path) {}

    public record StageProjection(
        UUID planId,
        StageCode currentStage,
        PrimaryBlocker primaryBlocker,
        NextAction nextAction,
        List<StageView> stages,
        Instant computedAt
    ) {
        public StageProjection {
            stages = List.copyOf(stages);
        }
    }

    private record Action(String label, String path) {}

    private record OwnerRead<T>(boolean available, T value) {
        private static <T> OwnerRead<T> available(T value) {
            return new OwnerRead<>(true, value);
        }

        private static <T> OwnerRead<T> unavailable() {
            return new OwnerRead<>(false, null);
        }
    }
}
