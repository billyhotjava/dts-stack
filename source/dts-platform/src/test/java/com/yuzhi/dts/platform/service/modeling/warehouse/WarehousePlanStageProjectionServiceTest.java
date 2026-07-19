package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.ASSET_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryReadiness.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.ConfirmationStatus.CONFIRMED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LayerScheme.CLASSIC_ODS_DWD_DWS_ADS;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyReadiness.MODEL_DESIGN_READY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryReadiness.NOT_REQUIRED_YET;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType.CATALOG_TABLE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness.CURRENT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.EvidenceFreshness.STALE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.DATA_CONNECTION;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.DATA_STANDARD;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.MODEL_DESIGN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.SOURCE_INVENTORY;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageCode.WAREHOUSE_PLANNING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.BLOCKED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.COMPLETE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.IN_PROGRESS;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.NOT_STARTED;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanStageProjectionService.StageStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.SourceReferenceResolver.AccessContext;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.CategoryScopeView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.PlanningPolicyView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceBindingView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceInventoryView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.Versioned;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanStageProjectionServiceTest {

    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final Instant COMPUTED_AT = Instant.parse("2026-07-18T09:00:00Z");
    private static final AccessContext ACTOR = new AccessContext("tenant-1", "actor-1", "department-1");

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
            ASSET_FIRST,
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

    @Test
    void exposesOnlyCanonicalActionPathsWithCompletePlanContext() {
        Map<WarehousePlanStageProjectionService.StageCode, String> paths = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            BUSINESS_FIRST,
            List.of(),
            COMPUTED_AT
        )
            .stages()
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                WarehousePlanStageProjectionService.StageView::code,
                WarehousePlanStageProjectionService.StageView::actionPath
            ));

        assertThat(paths).containsExactlyInAnyOrderEntriesOf(Map.of(
            DATA_CONNECTION,
            "/foundation/data-sources",
            SOURCE_INVENTORY,
            "/modeling/plans/" + PLAN_ID + "/baseline?tab=sources",
            WAREHOUSE_PLANNING,
            "/modeling/plans/" + PLAN_ID + "/baseline?tab=categories",
            WarehousePlanStageProjectionService.StageCode.DATA_STANDARD,
            "/modeling/models?planId=" + PLAN_ID,
            WarehousePlanStageProjectionService.StageCode.MODEL_DESIGN,
            "/modeling/models?planId=" + PLAN_ID,
            WarehousePlanStageProjectionService.StageCode.BUILD_QUALITY_RELEASE,
            "/modeling/plans/" + PLAN_ID + "/implementation",
            WarehousePlanStageProjectionService.StageCode.DATA_ASSET,
            "/catalog/assets?planId=" + PLAN_ID,
            WarehousePlanStageProjectionService.StageCode.METRIC_SYSTEM,
            "/modeling/metric-workbench?planId=" + PLAN_ID,
            WarehousePlanStageProjectionService.StageCode.DATA_SERVICE_OPERATIONS,
            "/ops/instances?planId=" + PLAN_ID
        ));
    }

    @Test
    void routesWarehousePlanningToTheSpecificMissingBaselineInput() {
        WarehousePlanStageProjectionService.StageProjection projection = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            BUSINESS_FIRST,
            List.of(
                evidence(DATA_CONNECTION, COMPLETE, CURRENT, null),
                evidence(SOURCE_INVENTORY, COMPLETE, CURRENT, null),
                evidence(WAREHOUSE_PLANNING, BLOCKED, CURRENT, "PLANNING_POLICY_INCOMPLETE")
            ),
            COMPUTED_AT
        );

        assertThat(projection.nextAction().path())
            .isEqualTo("/modeling/plans/" + PLAN_ID + "/baseline?tab=layers");
    }

    @Test
    void routesPlanningPolicyProviderFailureToTheTypedLayerRepairTarget() {
        WarehousePlanStageProjectionService.StageProjection projection = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            BUSINESS_FIRST,
            List.of(
                evidence(DATA_CONNECTION, COMPLETE, CURRENT, null),
                evidence(SOURCE_INVENTORY, COMPLETE, CURRENT, null),
                evidence(WAREHOUSE_PLANNING, UNKNOWN, CURRENT, "PLANNING_POLICY_EVIDENCE_UNAVAILABLE")
            ),
            COMPUTED_AT
        );

        assertThat(projection.nextAction().path())
            .isEqualTo("/modeling/plans/" + PLAN_ID + "/baseline?tab=layers");
    }

    @Test
    void businessFirstStartsWithCategoriesAndPolicyWithoutBeingBlockedByEmptySources() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(BUSINESS_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(category(DRAFT), 1));
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(draftPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(sources(NOT_REQUIRED_YET, List.of()));
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of());

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, DATA_CONNECTION).status()).isEqualTo(NOT_STARTED);
        assertThat(stage(projection, SOURCE_INVENTORY).status()).isEqualTo(NOT_STARTED);
        assertThat(projection.currentStage()).isEqualTo(WAREHOUSE_PLANNING);
        assertThat(projection.primaryBlocker().code()).isEqualTo("CATEGORY_SCOPE_INCOMPLETE");
        assertThat(projection.nextAction().path())
            .isEqualTo("/modeling/plans/" + PLAN_ID + "/baseline?tab=categories");
    }

    @Test
    void assetFirstMustVerifyARealSourceBeforeWarehousePlanning() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(ASSET_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.DRAFT, List.of())
        );
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of());

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(projection.currentStage()).isEqualTo(DATA_CONNECTION);
        assertThat(projection.primaryBlocker().code()).isEqualTo("DATA_CONNECTION_REQUIRED");
        assertThat(projection.nextAction().path()).isEqualTo("/foundation/data-sources");
        assertThat(stage(projection, SOURCE_INVENTORY).status()).isEqualTo(BLOCKED);
    }

    @Test
    void projectsWarehouseAndModelOwnersWithoutAnyStageEvidenceRows() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(ASSET_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.READY, List.of(currentSource()))
        );
        ModelSpecView model = canonicalModel();
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of(model));

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, DATA_CONNECTION).status()).isEqualTo(COMPLETE);
        assertThat(stage(projection, SOURCE_INVENTORY).status()).isEqualTo(COMPLETE);
        assertThat(stage(projection, WAREHOUSE_PLANNING).status()).isEqualTo(COMPLETE);
        assertThat(stage(projection, MODEL_DESIGN).status()).isEqualTo(COMPLETE);
        assertThat(stage(projection, DATA_STANDARD).status()).isEqualTo(BLOCKED);
        assertThat(stage(projection, DATA_STANDARD).blockerCode()).isEqualTo("MODEL_STANDARD_BINDING_REQUIRED");
        assertThat(projection.currentStage()).isEqualTo(DATA_STANDARD);
    }

    @Test
    void completesDataStandardOnlyWhenEveryDeclaredFieldHasSavedBindingEvidence() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(ASSET_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.READY, List.of(currentSource()))
        );
        ModelSpecView model = canonicalModel();
        when(model.standardBindings()).thenReturn(
            List.of(new ModelSpecContract.StandardBinding("record_id", null, null, null, null, null, null, "INTERNAL"))
        );
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of(model));

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, DATA_STANDARD).status()).isEqualTo(COMPLETE);
        assertThat(stage(projection, DATA_STANDARD).evidenceCount()).isEqualTo(1);
        assertThat(projection.currentStage()).isEqualTo(
            WarehousePlanStageProjectionService.StageCode.BUILD_QUALITY_RELEASE
        );
    }

    @Test
    void doesNotCompleteModelDesignFromAnIncompleteModelShell() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(ASSET_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.READY, List.of(currentSource()))
        );
        ModelSpecView incompleteModel = incompleteModelShell();
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of(incompleteModel));

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, MODEL_DESIGN).status()).isEqualTo(BLOCKED);
        assertThat(stage(projection, MODEL_DESIGN).blockerCode()).isEqualTo("MODEL_SPEC_REQUIRED");
        assertThat(projection.currentStage()).isEqualTo(MODEL_DESIGN);
    }

    @Test
    void routesDataStandardToAServerOwnedModelStandardsTab() {
        UUID emptyModelId = UUID.fromString("40000000-0000-0000-0000-000000000000");
        UUID fullyBoundModelId = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID repairModelId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(ASSET_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.READY, List.of(currentSource()))
        );
        ModelSpecView emptyModel = canonicalModel();
        when(emptyModel.id()).thenReturn(emptyModelId);
        when(emptyModel.fields()).thenReturn(List.of());
        ModelSpecView fullyBoundModel = canonicalModel();
        when(fullyBoundModel.id()).thenReturn(fullyBoundModelId);
        when(fullyBoundModel.standardBindings()).thenReturn(
            List.of(new ModelSpecContract.StandardBinding("record_id", null, null, null, null, null, null, "INTERNAL"))
        );
        ModelSpecView repairModel = canonicalModel();
        when(repairModel.id()).thenReturn(repairModelId);
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(
            List.of(emptyModel, fullyBoundModel, repairModel)
        );

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, DATA_STANDARD).status()).isEqualTo(IN_PROGRESS);
        assertThat(stage(projection, DATA_STANDARD).evidenceCount()).isEqualTo(1);
        assertThat(stage(projection, DATA_STANDARD).blockerCode()).isEqualTo("MODEL_STANDARD_BINDING_INCOMPLETE");
        assertThat(stage(projection, DATA_STANDARD).actionPath())
            .isEqualTo("/modeling/models/" + repairModelId + "?tab=standards&planId=" + PLAN_ID);
        assertThat(projection.nextAction().path()).isEqualTo(stage(projection, DATA_STANDARD).actionPath());
    }

    @Test
    void isolatesSourceProviderFailureAsUnknownInsteadOfFailingTheProjection() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(BUSINESS_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenThrow(new IllegalStateException("provider down"));
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenReturn(List.of());

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, DATA_CONNECTION).status()).isEqualTo(UNKNOWN);
        assertThat(stage(projection, SOURCE_INVENTORY).status()).isEqualTo(UNKNOWN);
        assertThat(stage(projection, WAREHOUSE_PLANNING).status()).isEqualTo(UNKNOWN);
        assertThat(projection.currentStage()).isEqualTo(WAREHOUSE_PLANNING);
    }

    @Test
    void defersDataStandardUntilAModelExistsAndKeepsModelProviderFailureUnknown() {
        WarehousePlanApplicationService planService = mock(WarehousePlanApplicationService.class);
        ModelSpecApplicationService modelSpecService = mock(ModelSpecApplicationService.class);
        when(planService.get("tenant-1", PLAN_ID)).thenReturn(plan(BUSINESS_FIRST));
        when(planService.getCategoryScope("tenant-1", PLAN_ID)).thenReturn(
            new Versioned<>(category(WarehousePlanContract.CategoryReadiness.READY), 1)
        );
        when(planService.getPlanningPolicy("tenant-1", PLAN_ID)).thenReturn(new Versioned<>(readyPolicy(), 1));
        when(planService.getSources("tenant-1", PLAN_ID, ACTOR)).thenReturn(
            sources(WarehousePlanContract.SourceInventoryReadiness.READY, List.of(currentSource()))
        );
        when(modelSpecService.list("tenant-1", PLAN_ID, null, null, null)).thenThrow(new IllegalStateException("model owner down"));

        WarehousePlanStageProjectionService.StageProjection projection = new WarehousePlanStageProjectionService(
            planService,
            modelSpecService
        ).project("tenant-1", PLAN_ID, ACTOR);

        assertThat(stage(projection, MODEL_DESIGN).status()).isEqualTo(UNKNOWN);
        assertThat(stage(projection, DATA_STANDARD).status()).isEqualTo(UNKNOWN);
        assertThat(projection.currentStage()).isEqualTo(MODEL_DESIGN);
    }

    @Test
    void ignoresUntrustedEvidenceActionTextAndPath() {
        WarehousePlanStageProjectionService.StageProjection projection = WarehousePlanStageProjectionService.compute(
            PLAN_ID,
            ASSET_FIRST,
            List.of(
                new WarehousePlanStageProjectionService.StageEvidence(
                    DATA_CONNECTION,
                    BLOCKED,
                    CURRENT,
                    0,
                    "DATA_CONNECTION_REQUIRED",
                    "Connection required",
                    "Open attacker site",
                    "https://attacker.invalid/steal"
                )
            ),
            COMPUTED_AT
        );

        assertThat(stage(projection, DATA_CONNECTION).actionLabel()).isEqualTo("Check data connections");
        assertThat(stage(projection, DATA_CONNECTION).actionPath()).isEqualTo("/foundation/data-sources");
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

    private static WarehousePlanHeader plan(WarehousePlanContract.OnboardingMode mode) {
        return new WarehousePlanHeader(
            PLAN_ID,
            "tenant-1",
            "wp-test",
            "Test plan",
            "Test objective",
            null,
            "owner-1",
            "department-1",
            mode,
            WarehousePlanContract.LifecycleStatus.DRAFT,
            1
        );
    }

    private static CategoryScopeView category(WarehousePlanContract.CategoryReadiness readiness) {
        return new CategoryScopeView(List.of(), readiness, List.of(), COMPUTED_AT);
    }

    private static PlanningPolicyView draftPolicy() {
        return new PlanningPolicyView(null, null, null, null, false, WarehousePlanContract.PlanningPolicyReadiness.DRAFT, List.of());
    }

    private static PlanningPolicyView readyPolicy() {
        return new PlanningPolicyView(CLASSIC_ODS_DWD_DWS_ADS, null, null, null, false, MODEL_DESIGN_READY, List.of());
    }

    private static SourceInventoryView sources(
        WarehousePlanContract.SourceInventoryReadiness readiness,
        List<SourceBindingView> bindings
    ) {
        return new SourceInventoryView(bindings, readiness, List.of(), 1, "\"sources:1\"", COMPUTED_AT);
    }

    private static SourceBindingView currentSource() {
        return new SourceBindingView(
            SOURCE_ID,
            CATALOG_TABLE,
            new WarehousePlanContract.SourceLocator(SOURCE_ID, null, null, null, null, null, null),
            SOURCE_ID.toString(),
            CONFIRMED,
            null,
            "Source",
            "v1",
            "v1",
            SourceReferenceResolver.ResolutionStatus.AVAILABLE,
            WarehousePlanContract.SourceFreshness.CURRENT,
            COMPUTED_AT
        );
    }

    private static ModelSpecView canonicalModel() {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.id()).thenReturn(UUID.fromString("40000000-0000-0000-0000-000000000099"));
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.domainId()).thenReturn(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        when(model.modelType()).thenReturn(ModelSpecContract.ModelType.DIMENSION);
        when(model.layer()).thenReturn(ModelSpecContract.Layer.DWD);
        when(model.name()).thenReturn("generic_dimension");
        when(model.implementationMode()).thenReturn(ModelSpecContract.ImplementationMode.DESIGNER_GENERATED);
        when(model.grain()).thenReturn(new ModelSpecContract.Grain("one row per record", List.of("record_id")));
        when(model.fields()).thenReturn(
            List.of(new ModelSpecContract.ModelField("record_id", "varchar", false, null, ModelSpecContract.FieldRole.KEY, null))
        );
        when(model.sourceRefs()).thenReturn(List.of());
        when(model.dependsOn()).thenReturn(List.of());
        when(model.dimensionRefs()).thenReturn(List.of());
        when(model.metricRefs()).thenReturn(List.of());
        when(model.standardBindings()).thenReturn(List.of());
        when(model.status()).thenReturn(ModelSpecContract.ModelStatus.DRAFT);
        when(model.revision()).thenReturn(1);
        when(model.compatibilityMode()).thenReturn(ModelSpecContract.CompatibilityMode.CANONICAL);
        return model;
    }

    private static ModelSpecView incompleteModelShell() {
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.modelType()).thenReturn(ModelSpecContract.ModelType.DIMENSION);
        when(model.status()).thenReturn(ModelSpecContract.ModelStatus.DRAFT);
        when(model.compatibilityMode()).thenReturn(ModelSpecContract.CompatibilityMode.CANONICAL);
        return model;
    }

    private static WarehousePlanStageProjectionService.StageView stage(
        WarehousePlanStageProjectionService.StageProjection projection,
        WarehousePlanStageProjectionService.StageCode code
    ) {
        return projection.stages().stream().filter(item -> item.code() == code).findFirst().orElseThrow();
    }
}
