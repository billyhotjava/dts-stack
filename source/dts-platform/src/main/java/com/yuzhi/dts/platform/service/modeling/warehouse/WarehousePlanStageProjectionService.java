package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningBaseline;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes the only completion-state projection for the nine-stage data-building journey. */
@Service
public class WarehousePlanStageProjectionService {

    private final JdbcTemplate jdbcTemplate;
    private final WarehousePlanApplicationService warehousePlanService;

    public WarehousePlanStageProjectionService(
        JdbcTemplate jdbcTemplate,
        WarehousePlanApplicationService warehousePlanService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.warehousePlanService = warehousePlanService;
    }

    @Transactional(readOnly = true)
    public StageProjection project(String tenantId, UUID planId) {
        WarehousePlanHeader plan = warehousePlanService.get(tenantId, planId);
        List<StageEvidence> evidence = new ArrayList<>(loadEvidence(tenantId, planId));
        PlanningBaseline baseline = warehousePlanService.getBaseline(tenantId, planId);
        evidence.add(planningEvidence(baseline));
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

        StageView current = stages.stream().filter(stage -> stage.status() != StageStatus.COMPLETE).findFirst().orElse(null);
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

    private List<StageEvidence> loadEvidence(String tenantId, UUID planId) {
        return jdbcTemplate.query(
            """
            select stage_code, status, freshness
              from modeling_warehouse_plan_stage_evidence
             where tenant_id = ? and plan_id = ?
             order by stage_code
            """,
            (row, rowNumber) -> {
                StageCode stageCode = StageCode.valueOf(row.getString("stage_code"));
                return new StageEvidence(
                    stageCode,
                    parseStatus(row.getString("status")),
                    parseFreshness(row.getString("freshness")),
                    1,
                    null,
                    null,
                    null,
                    null
                );
            },
            tenantId,
            planId
        );
    }

    private static StageEvidence planningEvidence(PlanningBaseline baseline) {
        if (baseline.ready()) {
            return new StageEvidence(
                StageCode.WAREHOUSE_PLANNING,
                StageStatus.COMPLETE,
                EvidenceFreshness.CURRENT,
                1,
                null,
                null,
                null,
                null
            );
        }
        String blocker = baseline.missingCodes().isEmpty() ? "WAREHOUSE_PLANNING_INCOMPLETE" : baseline.missingCodes().getFirst();
        return new StageEvidence(
            StageCode.WAREHOUSE_PLANNING,
            StageStatus.BLOCKED,
            EvidenceFreshness.CURRENT,
            0,
            blocker,
            "Warehouse planning baseline is incomplete",
            null,
            null
        );
    }

    private static StageView toView(UUID planId, StageCode stageCode, StageEvidence evidence) {
        if (evidence == null) {
            Action action = defaultAction(planId, stageCode);
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
        Action action = defaultAction(planId, stageCode);
        return new StageView(
            stageCode,
            effectiveStatus,
            freshness,
            Math.max(0, evidence.evidenceCount()),
            blockerCode,
            blockerMessage,
            isBlank(evidence.actionLabel()) ? action.label() : evidence.actionLabel(),
            isBlank(evidence.actionPath()) ? action.path() : evidence.actionPath()
        );
    }

    private static PrimaryBlocker blocker(StageView stage) {
        return new PrimaryBlocker(stage.code(), stage.blockerCode(), stage.blockerMessage());
    }

    private static Action defaultAction(UUID planId, StageCode stageCode) {
        String planRoot = "/modeling/plans/" + planId;
        return switch (stageCode) {
            case DATA_CONNECTION -> new Action("Check data connections", "/data-development/integration/data-sources");
            case SOURCE_INVENTORY -> new Action("Continue source inventory", planRoot + "/baseline?tab=sources");
            case WAREHOUSE_PLANNING -> new Action("Complete warehouse planning", planRoot + "/baseline?tab=business-scope");
            case DATA_STANDARD -> new Action("Bind data standards", planRoot + "/models?tab=standards");
            case MODEL_DESIGN -> new Action("Continue model design", planRoot + "/models");
            case BUILD_QUALITY_RELEASE -> new Action("Review build and quality gates", planRoot + "/implementation");
            case DATA_ASSET -> new Action("Review registered assets", planRoot + "/deliverables?tab=assets");
            case METRIC_SYSTEM -> new Action("Review metric bindings", planRoot + "/deliverables?tab=metrics");
            case DATA_SERVICE_OPERATIONS -> new Action("Review services and runs", planRoot + "/deliverables?tab=services");
        };
    }

    private static StageStatus parseStatus(String value) {
        try {
            return StageStatus.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return StageStatus.UNKNOWN;
        }
    }

    private static EvidenceFreshness parseFreshness(String value) {
        try {
            return EvidenceFreshness.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EvidenceFreshness.UNAVAILABLE;
        }
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
}
