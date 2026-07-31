package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode.CANONICAL;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode.DESIGNER_GENERATED;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer.DWD;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.FACT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.LifecycleStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.CONTAINS;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.DEPENDS_ON;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.DIMENSION_DEFINITION_REFERENCE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.DIMENSION_REFERENCE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.INDICATOR_REFERENCE;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.INDICATOR_DEPENDS_ON;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.STANDARD_BINDING;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind.DIMENSION;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind.INDICATOR;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind.MODEL;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind.PLAN;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind.STANDARD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.governance.IndicatorService;
import com.yuzhi.dts.platform.service.governance.IndicatorService.IndicatorDependency;
import com.yuzhi.dts.platform.service.governance.IndicatorService.RelationshipGraphProjection;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitApplicationService;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.RelationshipGraphReferenceCode;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.MetricRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipEdge;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipGraph;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WarehousePlanRelationshipGraphServiceTest {

    private static final String TENANT = "tenant-1";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID FACT_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID UPSTREAM_ID = UUID.fromString("30000000-0000-0000-0000-000000000002");
    private static final UUID DIMENSION_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID UNKNOWN_ID = UUID.fromString("30000000-0000-0000-0000-000000000099");
    private static final String CURSOR_SIGNING_SECRET = Base64
        .getEncoder()
        .encodeToString(
            "relationship-graph-test-signing-secret-v1".getBytes(
                StandardCharsets.UTF_8
            )
        );

    @Test
    void projectsOnlyVisibleTypedModelReferencesWithStableIdsRoutesAndOrdering() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecView fact = model(
            FACT_ID,
            "z_fact",
            List.of(new ModelRevisionRef(UPSTREAM_ID, 2), new ModelRevisionRef(UNKNOWN_ID, 1)),
            List.of(new ModelRevisionRef(DIMENSION_ID, 4))
        );
        ModelSpecView upstream = model(UPSTREAM_ID, "a_upstream", 2, List.of(), List.of());
        ModelSpecView dimension = model(DIMENSION_ID, "m_dimension", 4, List.of(), List.of());
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(List.of(fact));
        when(
            modelSpecs.revisionsForRelationshipGraph(
                TENANT,
                List.of(
                    new ModelRevisionRef(UPSTREAM_ID, 2),
                    new ModelRevisionRef(UNKNOWN_ID, 1),
                    new ModelRevisionRef(DIMENSION_ID, 4)
                ),
                500
            )
        )
            .thenReturn(List.of(dimension, upstream));

        RelationshipGraph graph = service(modelSpecs).read(TENANT, plan());

        assertThat(graph.planId()).isEqualTo(PLAN_ID);
        assertThat(graph.truncated()).isFalse();
        assertThat(graph.nextHint()).isNull();
        assertThat(graph.nodes())
            .extracting(RelationshipNode::id)
            .containsExactly(
                "PLAN:" + PLAN_ID,
                "MODEL:" + UPSTREAM_ID + "@2",
                "MODEL:" + DIMENSION_ID + "@4",
                "MODEL:" + FACT_ID + "@1"
            );
        assertThat(graph.nodes().getFirst())
            .satisfies(node -> {
                assertThat(node.kind()).isEqualTo(PLAN);
                assertThat(node.label()).isEqualTo("Warehouse plan");
                assertThat(node.status()).isEqualTo("DRAFT");
                assertThat(node.route()).contains("module=planning").contains("assetKind=plan");
            });
        assertThat(graph.nodes().subList(1, graph.nodes().size()))
            .allSatisfy(node -> {
                assertThat(node.kind()).isEqualTo(MODEL);
                assertThat(node.route()).contains("module=models").contains("assetKind=model");
            });
        assertThat(graph.edges())
            .extracting(RelationshipEdge::kind)
            .containsExactly(CONTAINS, DEPENDS_ON, DIMENSION_REFERENCE);
        assertThat(graph.edges())
            .filteredOn(edge -> edge.kind() == DEPENDS_ON)
            .singleElement()
            .satisfies(edge -> {
                assertThat(edge.source()).isEqualTo("MODEL:" + FACT_ID + "@1");
                assertThat(edge.target()).isEqualTo("MODEL:" + UPSTREAM_ID + "@2");
            });
        assertThat(graph.edges())
            .filteredOn(edge -> edge.kind() == DIMENSION_REFERENCE)
            .singleElement()
            .satisfies(edge -> assertThat(edge.target()).isEqualTo("MODEL:" + DIMENSION_ID + "@4"));
        assertThat(graph.nodes()).noneMatch(node -> node.id().equals("MODEL:" + UNKNOWN_ID + "@1"));
        assertThat(graph.edges()).noneMatch(edge -> edge.target().equals("MODEL:" + UNKNOWN_ID + "@1"));
        verify(modelSpecs).listForRelationshipGraph(TENANT, PLAN_ID, 501);
    }

    @Test
    void hydratesCanonicalDimensionStandardAndIndicatorOwnersWithoutGuessingEdges() {
        UUID definitionId = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID standardId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID indicatorId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        DimensionDefinitionApplicationService definitions = mock(DimensionDefinitionApplicationService.class);
        MetadataStandardService standards = mock(MetadataStandardService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        ModelSpecView fact = richModel(FACT_ID, definitionId, standardId, indicatorId);
        View definition = definition(definitionId);
        MetadataStandardDto standard = standard(standardId);
        IndicatorDto indicator = indicator(indicatorId);
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(List.of(fact));
        when(
            definitions.revisionsForRelationshipGraph(
                TENANT,
                List.of(new DimensionDefinitionRef(definitionId, 2)),
                500
            )
        )
            .thenReturn(List.of(definition));
        when(standards.listForRelationshipGraph(java.util.Set.of(standardId), 500)).thenReturn(List.of(standard));
        when(indicators.projectForRelationshipGraph(java.util.Set.of(indicatorId), "department-1", 500, 1000))
            .thenReturn(new RelationshipGraphProjection(List.of(indicator), List.of(), false));
        WarehousePlanRelationshipGraphService service = service(
            modelSpecs,
            definitions,
            standards,
            indicators
        );

        RelationshipGraph graph = service.read(TENANT, plan(), "department-1", null, null, 500);

        assertThat(graph.nodes())
            .extracting(RelationshipNode::kind)
            .containsExactly(PLAN, MODEL, DIMENSION, STANDARD, INDICATOR);
        assertThat(graph.edges())
            .extracting(RelationshipEdge::kind)
            .containsExactly(CONTAINS, DIMENSION_DEFINITION_REFERENCE, STANDARD_BINDING, INDICATOR_REFERENCE);
        assertThat(graph.edges())
            .filteredOn(edge -> edge.kind() == DIMENSION_DEFINITION_REFERENCE)
            .singleElement()
            .satisfies(edge -> {
                assertThat(edge.target()).isEqualTo("DIMENSION:" + definitionId + "@2");
                assertThat(edge.label()).contains("r2");
            });
        assertThat(graph.nodes())
            .filteredOn(node -> node.kind() == DIMENSION)
            .singleElement()
            .satisfies(
                node ->
                    assertThat(node.route())
                        .contains("module=models")
                        .contains("workspaceView=dimensions")
                        .contains("assetId=" + definitionId)
            );
        assertThat(graph.nodes())
            .filteredOn(node -> node.kind() == STANDARD)
            .singleElement()
            .satisfies(node -> assertThat(node.route()).contains("module=standards").contains("assetId=" + standardId));
        assertThat(graph.nodes())
            .filteredOn(node -> node.kind() == INDICATOR)
            .singleElement()
            .satisfies(node -> assertThat(node.route()).contains("module=metrics").contains("assetId=" + indicatorId));

        RelationshipGraph filtered = service.read(TENANT, plan(), "department-1", "standard", "Ｃustomer", 10);
        assertThat(filtered.nodes()).extracting(RelationshipNode::kind).containsExactly(STANDARD);
        assertThat(filtered.edges()).isEmpty();
    }

    @Test
    void projectsEveryVersionedStandardBindingSubtypeFromItsAuthorizedCurrentOwner() {
        UUID elementId = UUID.fromString("70000000-0000-0000-0000-000000000001");
        UUID unitId = UUID.fromString("70000000-0000-0000-0000-000000000002");
        String referenceCodeId = "CUSTOMER_TYPE";
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        MetadataStandardService standards = mock(MetadataStandardService.class);
        ReferenceCodeService referenceCodes = mock(ReferenceCodeService.class);
        MeasurementUnitApplicationService units = mock(MeasurementUnitApplicationService.class);
        StandardBinding binding = new StandardBinding(
            "customer_id",
            elementId,
            4,
            referenceCodeId,
            3,
            unitId,
            2,
            null
        );
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501))
            .thenReturn(List.of(modelWithGovernance(List.of(), List.of(binding), null)));
        when(standards.listForRelationshipGraph(java.util.Set.of(elementId), 500))
            .thenReturn(List.of(standard(elementId)));
        when(referenceCodes.listForRelationshipGraph(java.util.Set.of(referenceCodeId), "department-1", 500))
            .thenReturn(
                List.of(
                    new RelationshipGraphReferenceCode(
                        referenceCodeId,
                        referenceCodeId,
                        "Customer type",
                        1,
                        "v3"
                    )
                )
            );
        when(units.listForRelationshipGraph(java.util.Set.of(unitId), 500))
            .thenReturn(
                List.of(
                    new MeasurementUnitView(
                        unitId,
                        "COUNT",
                        "Count",
                        "n",
                        "COUNT",
                        BigDecimal.ONE,
                        null,
                        0,
                        MeasurementUnitStatus.ACTIVE,
                        2,
                        "a".repeat(64),
                        Instant.EPOCH,
                        Instant.EPOCH
                    )
                )
            );
        WarehousePlanRelationshipGraphService service = service(
            modelSpecs,
            mock(DimensionDefinitionApplicationService.class),
            standards,
            mock(IndicatorService.class),
            referenceCodes,
            units
        );

        RelationshipGraph graph = service.read(TENANT, plan(), "department-1", null, null, 500);

        assertThat(graph.nodes())
            .filteredOn(node -> node.kind() == STANDARD)
            .extracting(RelationshipNode::id)
            .containsExactlyInAnyOrder(
                "STANDARD:ELEMENT:" + elementId + "@4",
                "STANDARD:MEASUREMENT_UNIT:" + unitId + "@2",
                "STANDARD:REFERENCE_CODE:" + referenceCodeId + "@3"
            );
        assertThat(graph.edges())
            .filteredOn(edge -> edge.kind() == STANDARD_BINDING)
            .extracting(RelationshipEdge::target)
            .containsExactly(
                "STANDARD:ELEMENT:" + elementId + "@4",
                "STANDARD:MEASUREMENT_UNIT:" + unitId + "@2",
                "STANDARD:REFERENCE_CODE:" + referenceCodeId + "@3"
            );
    }

    @Test
    void projectsAuthorizedOneHopIndicatorDependencies() {
        UUID sourceId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        UUID targetId = UUID.fromString("80000000-0000-0000-0000-000000000002");
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorDto source = indicator(sourceId, "v2");
        IndicatorDto target = indicator(targetId, "v4");
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501))
            .thenReturn(
                List.of(
                    modelWithGovernance(
                        List.of(new MetricRef(sourceId.toString(), 2)),
                        List.of(),
                        null
                    )
                )
            );
        when(indicators.projectForRelationshipGraph(java.util.Set.of(sourceId), "department-1", 500, 1000))
            .thenReturn(
                new RelationshipGraphProjection(
                    List.of(source, target),
                    List.of(new IndicatorDependency(sourceId, targetId)),
                    false
                )
            );
        WarehousePlanRelationshipGraphService service = service(
            modelSpecs,
            mock(DimensionDefinitionApplicationService.class),
            mock(MetadataStandardService.class),
            indicators
        );

        RelationshipGraph graph = service.read(TENANT, plan(), "department-1", null, null, 500);

        assertThat(graph.nodes())
            .filteredOn(node -> node.kind() == INDICATOR)
            .extracting(RelationshipNode::id)
            .containsExactly("INDICATOR:" + sourceId, "INDICATOR:" + targetId);
        assertThat(graph.edges())
            .filteredOn(edge -> edge.kind() == INDICATOR_DEPENDS_ON)
            .containsExactly(
                new RelationshipEdge(
                    "INDICATOR:" + sourceId,
                    "INDICATOR:" + targetId,
                    INDICATOR_DEPENDS_ON,
                    "Indicator dependency"
                )
            );
    }

    @Test
    void stopsTraversingOversizedSnapshotArraysAtTheGlobalWorkBudget() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        AtomicInteger reads = new AtomicInteger();
        List<ModelRevisionRef> oversized = new AbstractList<>() {
            @Override
            public ModelRevisionRef get(int index) {
                if (reads.incrementAndGet() > WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK + 1) {
                    throw new AssertionError("relationship graph traversed beyond its global work budget");
                }
                return new ModelRevisionRef(UNKNOWN_ID, 1);
            }

            @Override
            public int size() {
                return Integer.MAX_VALUE;
            }
        };
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501))
            .thenReturn(List.of(model(FACT_ID, "fact", oversized, List.of())));

        RelationshipGraph graph = service(modelSpecs).read(TENANT, plan());

        assertThat(graph.truncated()).isTrue();
        assertThat(graph.nextHint()).isEqualTo("FILTER_BY_KIND_OR_QUERY");
        assertThat(reads).hasValueLessThanOrEqualTo(WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK + 1);
    }

    @Test
    void exposesStableKeysetCursorAndMakesTheSixHundredthModelReachableWithoutDuplicates() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        List<ModelSpecView> firstWindow = new ArrayList<>();
        List<ModelSpecView> secondWindow = new ArrayList<>();
        for (int index = 1; index <= 501; index++) {
            firstWindow.add(model(new UUID(0, index), "model-" + index, List.of(), List.of()));
        }
        for (int index = 501; index <= 600; index++) {
            String label = index == 600 ? "needle-600" : "model-" + index;
            secondWindow.add(model(new UUID(0, index), label, List.of(), List.of()));
        }
        UUID firstCursor = new UUID(0, 500);
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(firstWindow);
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, firstCursor, 501)).thenReturn(secondWindow);
        WarehousePlanRelationshipGraphService service = service(modelSpecs);

        RelationshipGraph first = service.read(TENANT, plan(), null, "model", null, 500, null);
        RelationshipGraph repeated = service.read(TENANT, plan(), null, "model", null, 500, null);
        RelationshipGraph second = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            500,
            first.nextCursor()
        );
        RelationshipGraph searchFirst = service.read(
            TENANT,
            plan(),
            null,
            "model",
            "needle-600",
            500,
            null
        );
        RelationshipGraph searched = service.read(
            TENANT,
            plan(),
            null,
            "model",
            "needle-600",
            500,
            searchFirst.nextCursor()
        );

        assertThat(first.nextCursor())
            .isNotBlank()
            .isNotEqualTo(firstCursor.toString())
            .isEqualTo(repeated.nextCursor());
        assertThat(first.nextHint()).isEqualTo("CONTINUE_WITH_CURSOR");
        assertThat(second.nextCursor()).isNull();
        assertThat(second.nodes()).extracting(RelationshipNode::id).contains("MODEL:" + new UUID(0, 600) + "@1");
        assertThat(searchFirst.nodes()).isEmpty();
        assertThat(searchFirst.nextCursor()).isNotBlank().isNotEqualTo(first.nextCursor());
        assertThat(searched.nodes()).extracting(RelationshipNode::label).containsExactly("needle-600");
        assertThat(first.nodes())
            .extracting(RelationshipNode::id)
            .doesNotContainAnyElementsOf(second.nodes().stream().map(RelationshipNode::id).toList());
        verify(modelSpecs, org.mockito.Mockito.times(2))
            .listForRelationshipGraph(TENANT, PLAN_ID, firstCursor, 501);
    }

    @Test
    void traversesSixHundredModelsWithSmallPagesWithoutDuplicatesOrGaps() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> firstWindow = new ArrayList<>();
        List<ModelSpecView> secondWindow = new ArrayList<>();
        List<String> expectedIds = new ArrayList<>();
        for (int index = 1; index <= 600; index++) {
            ModelSpecView model = model(
                new UUID(0, index),
                "model-%03d".formatted(index),
                List.of(),
                List.of()
            );
            expectedIds.add("MODEL:" + model.id() + "@1");
            if (index <= 501) {
                firstWindow.add(model);
            }
            if (index >= 501) {
                secondWindow.add(model);
            }
        }
        UUID firstWindowEnd = new UUID(0, 500);
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(firstWindow);
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                firstWindowEnd,
                501
            )
        )
            .thenReturn(secondWindow);
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);

        List<RelationshipNode> traversed = traverse(
            service,
            "model",
            null,
            25,
            30
        );

        assertThat(traversed)
            .extracting(RelationshipNode::id)
            .containsExactlyElementsOf(expectedIds)
            .doesNotHaveDuplicates();
    }

    @Test
    void traversesMoreThanOnePageOfStandardsDerivedFromOneRoot() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        MetadataStandardService standards = mock(
            MetadataStandardService.class
        );
        List<StandardBinding> bindings = new ArrayList<>();
        List<MetadataStandardDto> standardViews = new ArrayList<>();
        Set<UUID> standardIds = new LinkedHashSet<>();
        for (int index = 1; index <= 60; index++) {
            UUID standardId = new UUID(7, index);
            standardIds.add(standardId);
            bindings.add(
                new StandardBinding(
                    "field-%03d".formatted(index),
                    standardId,
                    4,
                    null,
                    null,
                    null,
                    null,
                    null
                )
            );
            standardViews.add(
                standard(
                    standardId,
                    "standard-%03d".formatted(index)
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(
                List.of(
                    modelWithGovernance(
                        List.of(),
                        bindings,
                        null
                    )
                )
            );
        when(
            standards.listForRelationshipGraph(
                standardIds,
                500
            )
        )
            .thenReturn(standardViews);
        WarehousePlanRelationshipGraphService service =
            service(
                modelSpecs,
                mock(DimensionDefinitionApplicationService.class),
                standards,
                mock(IndicatorService.class)
            );

        List<RelationshipNode> traversed = traverse(
            service,
            "standard",
            null,
            25,
            5
        );

        assertThat(traversed)
            .hasSize(60)
            .extracting(RelationshipNode::id)
            .doesNotHaveDuplicates();
    }

    @Test
    void advancesPastAnEmptyMatchingRootWindowBeforeEnding() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> firstWindow = new ArrayList<>();
        for (int index = 1; index <= 501; index++) {
            firstWindow.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        UUID firstWindowEnd = new UUID(0, 500);
        ModelSpecView match = model(
            new UUID(0, 600),
            "needle-600",
            List.of(),
            List.of()
        );
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(firstWindow);
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                firstWindowEnd,
                501
            )
        )
            .thenReturn(List.of(match));
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);

        RelationshipGraph empty = service.read(
            TENANT,
            plan(),
            null,
            "model",
            "needle",
            25,
            null
        );
        RelationshipGraph matched = service.read(
            TENANT,
            plan(),
            null,
            "model",
            "needle",
            25,
            empty.nextCursor()
        );

        assertThat(empty.nodes()).isEmpty();
        assertThat(empty.nextHint())
            .isEqualTo("CONTINUE_WITH_CURSOR");
        assertThat(empty.nextCursor())
            .isNotNull()
            .isNotEqualTo(firstWindowEnd.toString());
        assertThat(matched.nodes())
            .extracting(RelationshipNode::id)
            .containsExactly("MODEL:" + match.id() + "@1");
        assertThat(matched.nextCursor()).isNull();
        assertThat(matched.nextHint()).isNull();
    }

    @Test
    void rejectsTamperedNonCanonicalWrongFilterAndStaleCursors() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> originalWindow = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            originalWindow.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        List<ModelSpecView> changedWindow = originalWindow
            .stream()
            .filter(model -> !model.id().equals(new UUID(0, 10)))
            .toList();
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(originalWindow, changedWindow);
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );
        String cursor = first.nextCursor();

        assertThat(cursor).isNotBlank();
        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                tamper(cursor)
            )
        );
        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                cursor + "="
            )
        );
        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "standard",
                null,
                10,
                cursor
            )
        );
        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                "changed",
                10,
                cursor
            )
        );
        assertStaleCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                cursor
            )
        );
    }

    @Test
    void rejectsCursorAsStaleWhenTheSameNodeChangesItsSortOrDisplayState() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> originalWindow = new ArrayList<>();
        List<ModelSpecView> changedWindow = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            UUID id = new UUID(0, index);
            originalWindow.add(
                model(
                    id,
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
            changedWindow.add(
                model(
                    id,
                    index == 1 ? "zzzz-model-001" : "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(originalWindow, changedWindow);
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );

        assertStaleCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                first.nextCursor()
            )
        );
    }

    @Test
    void rejectsCursorAsStaleWhenDerivedNodesAreAddedOrRemoved() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        IndicatorService indicators = mock(IndicatorService.class);
        List<MetricRef> metricRefs = new ArrayList<>();
        List<IndicatorDto> initialIndicators = new ArrayList<>();
        Set<UUID> indicatorIds = new LinkedHashSet<>();
        for (int index = 1; index <= 30; index++) {
            UUID id = new UUID(8, index);
            indicatorIds.add(id);
            metricRefs.add(new MetricRef(id.toString(), 3));
            initialIndicators.add(
                indicator(
                    id,
                    "indicator-%03d".formatted(index),
                    "v3"
                )
            );
        }
        UUID addedId = new UUID(8, 31);
        List<IndicatorDto> addedIndicators = new ArrayList<>(
            initialIndicators
        );
        addedIndicators.add(
            indicator(addedId, "indicator-031", "v3")
        );
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(
                List.of(
                    modelWithGovernance(
                        metricRefs,
                        List.of(),
                        null
                    )
                )
            );
        when(
            indicators.projectForRelationshipGraph(
                indicatorIds,
                "department-1",
                500,
                1000
            )
        )
            .thenReturn(
                new RelationshipGraphProjection(
                    initialIndicators,
                    List.of(),
                    false
                ),
                new RelationshipGraphProjection(
                    addedIndicators,
                    List.of(
                        new IndicatorDependency(
                            initialIndicators.getFirst().getId(),
                            addedId
                        )
                    ),
                    false
                ),
                new RelationshipGraphProjection(
                    initialIndicators.subList(
                        0,
                        initialIndicators.size() - 1
                    ),
                    List.of(),
                    false
                )
            );
        WarehousePlanRelationshipGraphService service =
            service(
                modelSpecs,
                mock(DimensionDefinitionApplicationService.class),
                mock(MetadataStandardService.class),
                indicators
            );
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            "department-1",
            "indicator",
            null,
            10,
            null
        );

        assertStaleCursor(() ->
            service.read(
                TENANT,
                plan(),
                "department-1",
                "indicator",
                null,
                10,
                first.nextCursor()
            )
        );
        assertStaleCursor(() ->
            service.read(
                TENANT,
                plan(),
                "department-1",
                "indicator",
                null,
                10,
                first.nextCursor()
            )
        );
    }

    @Test
    void rejectsCursorAsStaleWhenANonMatchingRootChanges() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> originalWindow = new ArrayList<>();
        List<ModelSpecView> changedWindow = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            UUID id = new UUID(0, index);
            ModelSpecView root = model(
                id,
                index <= 20
                    ? "match-%03d".formatted(index)
                    : "other-%03d".formatted(index),
                List.of(),
                List.of()
            );
            originalWindow.add(root);
            if (index < 30) changedWindow.add(root);
        }
        changedWindow.add(
            model(
                new UUID(0, 31),
                "other-031",
                List.of(),
                List.of()
            )
        );
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(originalWindow, changedWindow);
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            "match",
            10,
            null
        );

        assertStaleCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                "match",
                10,
                first.nextCursor()
            )
        );
    }

    @Test
    void rejectsAPlainShaResignedCursorThatMovesTheNodePosition() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> models = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            models.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(models);
        WarehousePlanRelationshipGraphService service =
            service(modelSpecs);
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );
        String forged = forgeWithPlainSha(
            first.nextCursor(),
            "MODEL:" + new UUID(0, 20) + "@1"
        );

        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                forged
            )
        );
    }

    @Test
    void rejectsCursorSignedDirectlyWithTheRawRuntimeSigningKey() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> models = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            models.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(models);
        WarehousePlanRelationshipGraphService service =
            productionService(
                modelSpecs,
                materializationProperties(CURSOR_SIGNING_SECRET)
            );
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );
        String forged = forgeWithRawRuntimeKey(
            first.nextCursor(),
            CURSOR_SIGNING_SECRET
        );

        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                forged
            )
        );
    }

    @Test
    void continuesAnUnchangedWindowWithTheCorrectSecretAndRejectsTheWrongSecret() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> models = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            models.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(models);
        WarehousePlanRelationshipGraphService issuer = productionService(
            modelSpecs,
            materializationProperties(CURSOR_SIGNING_SECRET)
        );
        RelationshipGraph first = issuer.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );

        RelationshipGraph second = issuer.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            first.nextCursor()
        );

        assertThat(second.nodes())
            .extracting(RelationshipNode::id)
            .containsExactlyElementsOf(
                models
                    .subList(10, 20)
                    .stream()
                    .map(model ->
                        "MODEL:" + model.id() + "@1"
                    )
                    .toList()
            );
        String wrongSecret = Base64
            .getEncoder()
            .encodeToString(
                "relationship-graph-wrong-signing-secret".getBytes(
                    StandardCharsets.UTF_8
                )
            );
        WarehousePlanRelationshipGraphService wrongKeyService =
            new WarehousePlanRelationshipGraphService(
                modelSpecs,
                wrongSecret
            );
        assertInvalidCursor(() ->
            wrongKeyService.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                first.nextCursor()
            )
        );
    }

    @Test
    void springConstructorUsesTheExistingMaterializationSigningKeySeam() {
        var constructors =
            WarehousePlanRelationshipGraphService.class.getConstructors();

        assertThat(constructors).hasSize(1);
        Class<?>[] parameterTypes = constructors[0].getParameterTypes();
        assertThat(parameterTypes[parameterTypes.length - 1])
            .isEqualTo(ModelMaterializationProperties.class);
    }

    @Test
    void missingOrShortSigningKeyFailsClosedOnlyWhenACursorIsUsed() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> models = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            models.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(
                List.of(models.getFirst()),
                models
            );
        WarehousePlanRelationshipGraphService missingKeyService =
            productionService(
                modelSpecs,
                new ModelMaterializationProperties()
            );

        assertThat(
            missingKeyService.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                null
            ).nextCursor()
        )
            .isNull();
        assertSigningUnavailable(() ->
            missingKeyService.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                null
            )
        );

        RelationshipGraph issued = service(modelSpecs).read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );
        WarehousePlanRelationshipGraphService shortKeyService =
            productionService(
                modelSpecs,
                materializationProperties("too-short")
            );
        assertSigningUnavailable(() ->
            shortKeyService.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                issued.nextCursor()
            )
        );
    }

    @Test
    void rejectsACursorWhoseWindowFingerprintWasModified() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelSpecView> models = new ArrayList<>();
        for (int index = 1; index <= 30; index++) {
            models.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(models);
        WarehousePlanRelationshipGraphService service = service(
            modelSpecs
        );
        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            "model",
            null,
            10,
            null
        );

        assertInvalidCursor(() ->
            service.read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                10,
                mutateTokenPart(first.nextCursor(), 3)
            )
        );
    }

    @Test
    void neverAdvancesRootCursorWhenReferenceWorkIsIncomplete() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        List<ModelRevisionRef> oversized = java.util.Collections.nCopies(
            WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK + 1,
            new ModelRevisionRef(UNKNOWN_ID, 1)
        );
        List<ModelSpecView> roots = new ArrayList<>();
        roots.add(
            model(
                new UUID(0, 1),
                "model-001",
                oversized,
                List.of()
            )
        );
        for (int index = 2; index <= 501; index++) {
            roots.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(roots);

        RelationshipGraph graph =
            service(modelSpecs).read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                500,
                null
            );

        assertThat(graph.truncated()).isTrue();
        assertThat(graph.nextHint())
            .isEqualTo("FILTER_BY_KIND_OR_QUERY");
        assertThat(graph.nextCursor()).isNull();
    }

    @Test
    void neverAdvancesRootCursorWhenEdgeWorkIsIncomplete() {
        ModelSpecApplicationService modelSpecs = mock(
            ModelSpecApplicationService.class
        );
        UUID targetId = new UUID(0, 2);
        List<ModelRevisionRef> duplicateLinks =
            java.util.Collections.nCopies(
                WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK -
                499,
                new ModelRevisionRef(targetId, 1)
            );
        List<ModelSpecView> roots = new ArrayList<>();
        roots.add(
            model(
                new UUID(0, 1),
                "model-001",
                duplicateLinks,
                List.of()
            )
        );
        for (int index = 2; index <= 501; index++) {
            roots.add(
                model(
                    new UUID(0, index),
                    "model-%03d".formatted(index),
                    List.of(),
                    List.of()
                )
            );
        }
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                501
            )
        )
            .thenReturn(roots);

        RelationshipGraph graph =
            service(modelSpecs).read(
                TENANT,
                plan(),
                null,
                "model",
                null,
                500,
                null
            );

        assertThat(graph.truncated()).isTrue();
        assertThat(graph.nextHint())
            .isEqualTo("FILTER_BY_KIND_OR_QUERY");
        assertThat(graph.nextCursor()).isNull();
    }

    @Test
    void rejectsMalformedCursorWithAStableBadRequestCode() {
        WarehousePlanRelationshipGraphService service = service(
            mock(ModelSpecApplicationService.class)
        );

        assertThatThrownBy(() -> service.read(TENANT, plan(), null, "model", null, 500, "not-a-uuid"))
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error -> ((WarehousePlanException) error).code())
            .isEqualTo("RELATIONSHIP_GRAPH_CURSOR_INVALID");
    }

    @Test
    void doesNotReportTruncationWhenTheReferenceWorkBudgetIsConsumedExactly() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        List<ModelRevisionRef> exactBudget = java.util.Collections.nCopies(
            WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK,
            null
        );
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501))
            .thenReturn(List.of(model(FACT_ID, "fact", exactBudget, List.of())));

        RelationshipGraph graph = service(modelSpecs).read(TENANT, plan());

        assertThat(graph.truncated()).isFalse();
        assertThat(graph.nextHint()).isNull();
        assertThat(graph.nextCursor()).isNull();
    }

    @Test
    void reportsTruncationOnlyAfterAnExtraDimensionCandidateExceedsTheExactWorkBudget() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.id()).thenReturn(FACT_ID);
        when(model.planId()).thenReturn(PLAN_ID);
        when(model.revision()).thenReturn(1);
        when(model.name()).thenReturn("fact");
        when(model.dependsOn())
            .thenReturn(
                java.util.Collections.nCopies(
                    WarehousePlanRelationshipGraphService.MAX_REFERENCE_WORK,
                    null
                )
            );
        when(model.dimensionRefs()).thenReturn(List.of());
        when(model.dimensionDefinitionRef())
            .thenReturn(new DimensionDefinitionRef(UUID.fromString("60000000-0000-0000-0000-000000000099"), 1));
        when(model.standardBindings()).thenReturn(List.of());
        when(model.metricRefs()).thenReturn(List.of());
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(List.of(model));

        RelationshipGraph graph = service(modelSpecs).read(TENANT, plan());

        assertThat(graph.truncated()).isTrue();
        assertThat(graph.nextHint()).isEqualTo("FILTER_BY_KIND_OR_QUERY");
        assertThat(graph.nextCursor()).isNull();
    }

    @Test
    void rejectsInvalidKindQueryAndLimitWithStableCodes() {
        WarehousePlanRelationshipGraphService service = service(
            mock(ModelSpecApplicationService.class)
        );

        assertThatThrownBy(() -> service.read(TENANT, plan(), null, "unknown", null, 500))
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error -> ((WarehousePlanException) error).code())
            .isEqualTo("RELATIONSHIP_GRAPH_KIND_INVALID");
        assertThatThrownBy(() -> service.read(TENANT, plan(), null, null, "x".repeat(129), 500))
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error -> ((WarehousePlanException) error).code())
            .isEqualTo("RELATIONSHIP_GRAPH_QUERY_INVALID");
        assertThatThrownBy(() -> service.read(TENANT, plan(), null, null, null, 501))
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error -> ((WarehousePlanException) error).code())
            .isEqualTo("RELATIONSHIP_GRAPH_LIMIT_INVALID");
    }

    @Test
    void capsNodesAtFiveHundredWithoutCreatingDanglingEdges() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        List<ModelSpecView> visible = new ArrayList<>();
        for (int index = 0; index < 500; index++) {
            UUID id = new UUID(0, index + 1L);
            visible.add(model(id, "model-" + index, List.of(), List.of()));
        }
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(visible);

        RelationshipGraph graph = service(modelSpecs).read(TENANT, plan());

        assertThat(graph.nodes()).hasSize(500);
        assertThat(graph.edges()).hasSize(499);
        assertThat(graph.truncated()).isTrue();
        assertThat(graph.nextHint()).isEqualTo("CONTINUE_WITH_CURSOR");
        assertThat(graph.nextCursor()).isNotBlank().isNotEqualTo(new UUID(0, 499).toString());
        assertThat(graph.edges()).allMatch(edge ->
            graph.nodes().stream().anyMatch(node -> node.id().equals(edge.source())) &&
            graph.nodes().stream().anyMatch(node -> node.id().equals(edge.target()))
        );
    }

    @Test
    void capsEdgesAtOneThousandAfterDeterministicSorting() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        List<ModelSpecView> visible = new ArrayList<>();
        for (int index = 0; index < 499; index++) {
            UUID id = new UUID(0, index + 1L);
            UUID targetId = new UUID(0, ((index + 1) % 499) + 1L);
            ModelRevisionRef target = new ModelRevisionRef(targetId, 1);
            visible.add(model(id, "model-" + index, List.of(target), List.of(target)));
        }
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(visible);

        WarehousePlanRelationshipGraphService service = service(modelSpecs);
        RelationshipGraph first = service.read(TENANT, plan());
        RelationshipGraph second = service.read(TENANT, plan());

        assertThat(first.nodes()).hasSize(500);
        assertThat(first.edges()).hasSize(1000);
        assertThat(first.truncated()).isTrue();
        assertThat(second).isEqualTo(first);
    }

    private static WarehousePlanRelationshipGraphService service(
        ModelSpecApplicationService modelSpecs
    ) {
        return new WarehousePlanRelationshipGraphService(
            modelSpecs,
            CURSOR_SIGNING_SECRET
        );
    }

    private static WarehousePlanRelationshipGraphService productionService(
        ModelSpecApplicationService modelSpecs,
        ModelMaterializationProperties properties
    ) {
        return new WarehousePlanRelationshipGraphService(
            modelSpecs,
            mock(DimensionDefinitionApplicationService.class),
            mock(MetadataStandardService.class),
            mock(IndicatorService.class),
            mock(ReferenceCodeService.class),
            mock(MeasurementUnitApplicationService.class),
            properties
        );
    }

    private static ModelMaterializationProperties materializationProperties(
        String signingKey
    ) {
        ModelMaterializationProperties properties =
            new ModelMaterializationProperties();
        properties.setRuntimeSpecSigningKey(signingKey);
        return properties;
    }

    private static WarehousePlanRelationshipGraphService service(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators
    ) {
        return new WarehousePlanRelationshipGraphService(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            CURSOR_SIGNING_SECRET
        );
    }

    private static WarehousePlanRelationshipGraphService service(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits
    ) {
        return new WarehousePlanRelationshipGraphService(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            referenceCodes,
            measurementUnits,
            CURSOR_SIGNING_SECRET
        );
    }

    private static WarehousePlanHeader plan() {
        return new WarehousePlanHeader(
            PLAN_ID,
            TENANT,
            "warehouse-plan",
            "Warehouse plan",
            null,
            null,
            "owner-1",
            "department-1",
            BUSINESS_FIRST,
            DRAFT,
            1
        );
    }

    private static ModelSpecView model(
        UUID id,
        String name,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs
    ) {
        return model(id, name, 1, dependsOn, dimensionRefs);
    }

    private static ModelSpecView model(
        UUID id,
        String name,
        int revision,
        List<ModelRevisionRef> dependsOn,
        List<ModelRevisionRef> dimensionRefs
    ) {
        return new ModelSpecView(
            ModelSpecContract.CONTRACT_VERSION,
            id,
            PLAN_ID,
            DOMAIN_ID,
            FACT,
            DWD,
            name,
            null,
            DESIGNER_GENERATED,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            dependsOn,
            dimensionRefs,
            List.of(),
            List.of(),
            null,
            com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus.DRAFT,
            revision,
            null,
            null,
            null,
            CANONICAL,
            null
        );
    }

    private static ModelSpecView richModel(UUID id, UUID definitionId, UUID standardId, UUID indicatorId) {
        return modelWithGovernance(
            List.of(new MetricRef(indicatorId.toString(), 3)),
            List.of(new StandardBinding("customer_id", standardId, 4, null, null, null, null, null)),
            new DimensionDefinitionRef(definitionId, 2)
        );
    }

    private static ModelSpecView modelWithGovernance(
        List<MetricRef> metricRefs,
        List<StandardBinding> standardBindings,
        DimensionDefinitionRef definitionRef
    ) {
        return new ModelSpecView(
            ModelSpecContract.CONTRACT_VERSION,
            FACT_ID,
            PLAN_ID,
            DOMAIN_ID,
            FACT,
            DWD,
            "fact_customer",
            null,
            DESIGNER_GENERATED,
            null,
            null,
            null,
            null,
            null,
            null,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            metricRefs,
            standardBindings,
            null,
            null,
            definitionRef,
            ModelSpecContract.ModelStatus.DRAFT,
            1,
            null,
            null,
            null,
            CANONICAL,
            null
        );
    }

    private static View definition(UUID id) {
        Instant now = Instant.parse("2026-07-30T00:00:00Z");
        return new View(
            id,
            "dim_customer",
            DOMAIN_ID,
            "Customer",
            "Customer dimension",
            "owner-1",
            DimensionDefinitionContract.ReuseScope.DOMAIN,
            List.of(),
            DimensionDefinitionContract.Status.CURRENT,
            2,
            "a".repeat(64),
            0,
            now,
            now
        );
    }

    private static MetadataStandardDto standard(UUID id) {
        return standard(id, "Customer ID");
    }

    private static MetadataStandardDto standard(
        UUID id,
        String label
    ) {
        MetadataStandardDto value = new MetadataStandardDto();
        value.setId(id);
        value.setFieldNameCn(label);
        value.setFieldNameEn("customer_id");
        value.setVersion(4);
        return value;
    }

    private static List<RelationshipNode> traverse(
        WarehousePlanRelationshipGraphService service,
        String kind,
        String query,
        int limit,
        int maximumPages
    ) {
        List<RelationshipNode> result = new ArrayList<>();
        String cursor = null;
        for (int page = 0; page < maximumPages; page++) {
            RelationshipGraph graph = service.read(
                TENANT,
                plan(),
                null,
                kind,
                query,
                limit,
                cursor
            );
            result.addAll(graph.nodes());
            cursor = graph.nextCursor();
            if (cursor == null) {
                assertThat(graph.nextHint()).isNull();
                return result;
            }
            assertThat(graph.nextHint())
                .isEqualTo("CONTINUE_WITH_CURSOR");
        }
        throw new AssertionError(
            "relationship graph cursor did not terminate"
        );
    }

    private static void assertInvalidCursor(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error ->
                ((WarehousePlanException) error).code()
            )
            .isEqualTo("RELATIONSHIP_GRAPH_CURSOR_INVALID");
    }

    private static void assertStaleCursor(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error ->
                ((WarehousePlanException) error).code()
            )
            .isEqualTo("RELATIONSHIP_GRAPH_CURSOR_STALE");
    }

    private static void assertSigningUnavailable(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable operation
    ) {
        assertThatThrownBy(operation)
            .isInstanceOf(WarehousePlanException.class)
            .extracting(error ->
                ((WarehousePlanException) error).code()
            )
            .isEqualTo(
                "RELATIONSHIP_GRAPH_CURSOR_SIGNING_UNAVAILABLE"
            );
    }

    private static String tamper(String cursor) {
        char last = cursor.charAt(cursor.length() - 1);
        return (
            cursor.substring(0, cursor.length() - 1) +
            (last == 'A' ? 'B' : 'A')
        );
    }

    private static String forgeWithPlainSha(
        String cursor,
        String nodeAfter
    ) {
        String raw = new String(
            Base64.getUrlDecoder().decode(cursor),
            StandardCharsets.UTF_8
        );
        String[] parts = raw.split("\\.", -1);
        parts[2] = Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                nodeAfter.getBytes(StandardCharsets.UTF_8)
            );
        int signatureIndex = parts.length - 1;
        String unsigned = String.join(
            ".",
            java.util.Arrays.copyOf(parts, signatureIndex)
        );
        parts[signatureIndex] = sha256(unsigned);
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                String.join(".", parts).getBytes(
                    StandardCharsets.UTF_8
                )
            );
    }

    private static String forgeWithRawRuntimeKey(
        String cursor,
        String runtimeSigningKey
    ) {
        String raw = new String(
            Base64.getUrlDecoder().decode(cursor),
            StandardCharsets.UTF_8
        );
        String[] parts = raw.split("\\.", -1);
        int signatureIndex = parts.length - 1;
        String unsigned = String.join(
            ".",
            java.util.Arrays.copyOf(parts, signatureIndex)
        );
        parts[signatureIndex] = hmacSha256(
            runtimeSigningKey.trim(),
            unsigned
        );
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                String.join(".", parts).getBytes(
                    StandardCharsets.UTF_8
                )
            );
    }

    private static String mutateTokenPart(
        String cursor,
        int partIndex
    ) {
        String raw = new String(
            Base64.getUrlDecoder().decode(cursor),
            StandardCharsets.UTF_8
        );
        String[] parts = raw.split("\\.", -1);
        char first = parts[partIndex].charAt(0);
        parts[partIndex] =
            (first == 'a' ? 'b' : 'a') +
            parts[partIndex].substring(1);
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                String.join(".", parts).getBytes(
                    StandardCharsets.UTF_8
                )
            );
    }

    private static String sha256(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256")
                        .digest(
                            value.getBytes(StandardCharsets.UTF_8)
                        )
                );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hmacSha256(String key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256"
                )
            );
            return HexFormat
                .of()
                .formatHex(
                    mac.doFinal(
                        value.getBytes(StandardCharsets.UTF_8)
                    )
                );
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static IndicatorDto indicator(UUID id) {
        return indicator(id, "v3");
    }

    private static IndicatorDto indicator(UUID id, String version) {
        return indicator(id, "Customer count", version);
    }

    private static IndicatorDto indicator(
        UUID id,
        String label,
        String version
    ) {
        IndicatorDto value = new IndicatorDto();
        value.setId(id);
        value.setCode("CUSTOMER_COUNT");
        value.setName(label);
        value.setStatus("PUBLISHED");
        value.setVersion(version);
        return value;
    }
}
