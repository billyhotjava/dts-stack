package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.ASSET_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness.CURRENT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness.STALE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.DATA_CONNECTION;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.SOURCE_INVENTORY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.WAREHOUSE_PLANNING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.BLOCKED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.COMPLETE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanStageProjectionServiceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant COMPUTED_AT = Instant.parse("2026-07-18T09:00:00Z");

    @Test
    void usesTheSameNineStableStagesForBothOnboardingModes() {
        List<WarehousePlanStageProjectionService.StageCode> businessStages = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            BUSINESS_FIRST,
            List.of(),
            COMPUTED_AT
        )
            .stages()
            .stream()
            .map(WarehousePlanStageProjectionService.StageView::code)
            .toList();
        List<WarehousePlanStageProjectionService.StageCode> assetStages = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            ASSET_FIRST,
            List.of(),
            COMPUTED_AT
        )
            .stages()
            .stream()
            .map(WarehousePlanStageProjectionService.StageView::code)
            .toList();

        assertThat(businessStages).containsExactly(WarehousePlanStageProjectionService.StageCode.values());
        assertThat(assetStages).isEqualTo(businessStages);
    }

    @Test
    void selectsTheFirstRealBlockerByStableStageOrder() {
        WarehousePlanStageProjectionService.StageProjection projection = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            ASSET_FIRST,
            List.of(
                evidence(DATA_CONNECTION, COMPLETE, CURRENT, null),
                evidence(WAREHOUSE_PLANNING, BLOCKED, CURRENT, "PLANNING_POLICY_INCOMPLETE"),
                evidence(SOURCE_INVENTORY, BLOCKED, CURRENT, "SOURCE_BUSINESS_MAPPING_INCOMPLETE")
            ),
            COMPUTED_AT
        );

        assertThat(projection.currentStage()).isEqualTo(SOURCE_INVENTORY);
        assertThat(projection.primaryBlocker().code()).isEqualTo("SOURCE_BUSINESS_MAPPING_INCOMPLETE");
        assertThat(projection.primaryBlocker().stageCode()).isEqualTo(SOURCE_INVENTORY);
        assertThat(projection.nextAction().path()).isEqualTo("/modeling/plans/" + PLAN_ID + "/baseline?tab=sources");
    }

    @Test
    void neverTreatsStaleOrUnknownEvidenceAsComplete() {
        WarehousePlanStageProjectionService.StageProjection projection = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            BUSINESS_FIRST,
            List.of(
                evidence(DATA_CONNECTION, COMPLETE, STALE, null),
                evidence(SOURCE_INVENTORY, UNKNOWN, CURRENT, "SOURCE_EVIDENCE_UNAVAILABLE")
            ),
            COMPUTED_AT
        );

        assertThat(projection.stages().getFirst().status()).isEqualTo(UNKNOWN);
        assertThat(projection.stages().getFirst().freshness()).isEqualTo(STALE);
        assertThat(projection.stages().get(1).status()).isEqualTo(UNKNOWN);
        assertThat(projection.currentStage()).isEqualTo(DATA_CONNECTION);
        assertThat(projection.primaryBlocker().code()).isEqualTo("EVIDENCE_STALE");
    }

    private static WarehousePlanStageProjectionService.StageEvidence evidence(
        WarehousePlanStageProjectionService.StageCode code,
        WarehousePlanStageProjectionService.StageStatus status,
        WarehousePlanStageProjectionService.EvidenceFreshness freshness,
        String blockerCode
    ) {
        return new WarehousePlanStageProjectionService.StageEvidence(
            code,
            status,
            freshness,
            1,
            blockerCode,
            blockerCode,
            "Continue",
            null
        );
    }
}
