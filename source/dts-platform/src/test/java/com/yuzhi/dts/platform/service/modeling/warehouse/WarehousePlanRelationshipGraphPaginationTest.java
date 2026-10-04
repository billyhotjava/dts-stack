package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode.CANONICAL;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode.DESIGNER_GENERATED;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer.DWD;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus.DRAFT;
import static com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType.FACT;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.OnboardingMode.BUSINESS_FIRST;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind.CONTAINS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipEdge;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipGraph;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehousePlanRelationshipGraphPaginationTest {

    private static final String TENANT = "tenant-pagination";
    private static final UUID PLAN_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID DOMAIN_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final String CURSOR_SIGNING_SECRET = Base64
        .getEncoder()
        .encodeToString("relationship-graph-pagination-secret".getBytes(StandardCharsets.UTF_8));

    @Test
    void returnsEveryContainsEdgeExactlyOnceAcrossNodeAndRootPages() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        List<ModelSpecView> allModels = models(1, 501);
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(allModels);
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, new UUID(0, 499), 501))
            .thenReturn(models(500, 501));
        WarehousePlanRelationshipGraphService service = new WarehousePlanRelationshipGraphService(
            modelSpecs,
            CURSOR_SIGNING_SECRET
        );

        Map<String, RelationshipNode> nodes = new LinkedHashMap<>();
        Set<EdgeKey> edgeKeys = new LinkedHashSet<>();
        int returnedEdgeCount = 0;
        String cursor = null;
        for (int page = 0; page < 30; page++) {
            RelationshipGraph graph = service.read(TENANT, plan(), null, null, null, 25, cursor);
            assertSelfContained(graph);
            graph.nodes().forEach(node -> nodes.putIfAbsent(node.id(), node));
            for (RelationshipEdge edge : graph.edges()) {
                returnedEdgeCount++;
                edgeKeys.add(new EdgeKey(edge.source(), edge.target(), edge.kind()));
            }
            cursor = graph.nextCursor();
            if (cursor == null) break;
        }

        assertThat(cursor).isNull();
        assertThat(nodes).hasSize(502);
        assertThat(returnedEdgeCount).isEqualTo(edgeKeys.size());
        assertThat(edgeKeys).hasSize(501).allMatch(edge -> edge.kind() == CONTAINS);
        assertThat(edgeKeys)
            .allSatisfy(edge -> {
                assertThat(nodes).containsKey(edge.source());
                assertThat(nodes).containsKey(edge.target());
            });
    }

    @Test
    void keepsCrossRootReferencesAsContextUntilTheCanonicalRootOwnsThem() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        WarehousePlanRelationshipGraphInboundModelReader inboundModels = mock(
            WarehousePlanRelationshipGraphInboundModelReader.class
        );
        ModelRevisionRef futureReference = new ModelRevisionRef(new UUID(0, 500), 1);
        ModelRevisionRef lastReference = new ModelRevisionRef(new UUID(0, 501), 1);
        ModelRevisionRef sourceReference = new ModelRevisionRef(new UUID(0, 1), 1);
        List<ModelSpecView> firstWindow = models(1, 501);
        firstWindow.set(
            0,
            model(new UUID(0, 1), "model-001", List.of(futureReference))
        );
        ModelSpecView sourceModel = firstWindow.getFirst();
        ModelSpecView futureModel = model(new UUID(0, 500), "model-500");
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501)).thenReturn(firstWindow);
        when(modelSpecs.revisionsForRelationshipGraph(TENANT, List.of(futureReference), 500))
            .thenReturn(List.of(futureModel));
        when(
            inboundModels.listCurrentTargets(
                TENANT,
                PLAN_ID,
                List.of(futureReference)
            )
        )
            .thenReturn(List.of(futureReference));
        when(
            inboundModels.listEarlierSources(
                TENANT,
                PLAN_ID,
                new UUID(0, 499),
                List.of(futureReference, lastReference),
                501
            )
        )
            .thenReturn(List.of(sourceReference));
        when(modelSpecs.revisionsForRelationshipGraph(TENANT, List.of(sourceReference), 500))
            .thenReturn(List.of(sourceModel));
        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, new UUID(0, 499), 501))
            .thenReturn(models(500, 501));
        WarehousePlanRelationshipGraphService service = new WarehousePlanRelationshipGraphService(
            modelSpecs,
            inboundModels,
            CURSOR_SIGNING_SECRET
        );

        RelationshipGraph first = service.read(TENANT, plan(), null, null, null, 500, null);
        assertSelfContained(first);
        RelationshipGraph second = service.read(
            TENANT,
            plan(),
            null,
            null,
            null,
            500,
            first.nextCursor()
        );
        assertSelfContained(second);

        String planNodeId = "PLAN:" + PLAN_ID;
        String sourceNodeId = "MODEL:" + new UUID(0, 1) + "@1";
        String futureNodeId = "MODEL:" + futureReference.modelSpecId() + "@1";
        String lastNodeId = "MODEL:" + new UUID(0, 501) + "@1";
        List<RelationshipEdge> allEdges = new ArrayList<>(first.edges());
        allEdges.addAll(second.edges());

        assertThat(first.nodes()).hasSize(500);
        assertThat(first.nodes()).extracting(RelationshipNode::id).doesNotContain(futureNodeId);
        assertThat(first.edges())
            .filteredOn(edge -> edge.kind() == EdgeKind.DEPENDS_ON)
            .isEmpty();
        assertThat(first.edges())
            .noneMatch(edge -> edge.kind() == CONTAINS && edge.target().equals(futureNodeId));
        assertThat(second.nodes()).hasSize(4);
        assertThat(second.nodes())
            .extracting(RelationshipNode::id)
            .containsExactlyInAnyOrder(planNodeId, sourceNodeId, futureNodeId, lastNodeId);
        assertThat(second.edges())
            .filteredOn(edge -> edge.kind() == EdgeKind.DEPENDS_ON)
            .containsExactly(
                new RelationshipEdge(
                    sourceNodeId,
                    futureNodeId,
                    EdgeKind.DEPENDS_ON,
                    "Depends on r1"
                )
            );
        assertThat(second.nextCursor()).isNull();
        assertThat(allEdges)
            .filteredOn(edge -> edge.kind() == CONTAINS)
            .extracting(RelationshipEdge::target)
            .hasSize(501)
            .doesNotHaveDuplicates()
            .contains(futureNodeId, lastNodeId);
        assertThat(allEdges)
            .filteredOn(edge -> edge.kind() == EdgeKind.DEPENDS_ON)
            .hasSize(1);
    }

    @Test
    void usesPostgresUuidOrderWhenAnEdgeCrossesTheSignedLongBoundary() {
        ModelSpecApplicationService modelSpecs = mock(ModelSpecApplicationService.class);
        WarehousePlanRelationshipGraphInboundModelReader inboundModels = mock(
            WarehousePlanRelationshipGraphInboundModelReader.class
        );
        UUID sourceId = UUID.fromString(
            "7fffffff-ffff-ffff-ffff-ffffffffffff"
        );
        UUID targetId = UUID.fromString(
            "80000000-0000-0000-0000-000000000000"
        );
        UUID lastId = UUID.fromString(
            "80000000-0000-0000-0000-000000000001"
        );
        ModelRevisionRef sourceReference = new ModelRevisionRef(sourceId, 1);
        ModelRevisionRef targetReference = new ModelRevisionRef(targetId, 1);
        ModelRevisionRef lastReference = new ModelRevisionRef(lastId, 1);
        ModelSpecView sourceModel = model(
            sourceId,
            "boundary-source",
            List.of(targetReference)
        );
        ModelSpecView targetModel = model(targetId, "boundary-target");
        ModelSpecView lastModel = model(lastId, "boundary-last");
        List<ModelSpecView> firstWindow = models(1, 498);
        firstWindow.add(sourceModel);
        firstWindow.add(targetModel);
        firstWindow.add(lastModel);

        when(modelSpecs.listForRelationshipGraph(TENANT, PLAN_ID, 501))
            .thenReturn(firstWindow);
        when(
            modelSpecs.revisionsForRelationshipGraph(
                TENANT,
                List.of(targetReference),
                500
            )
        )
            .thenReturn(List.of(targetModel));
        when(
            inboundModels.listCurrentTargets(
                TENANT,
                PLAN_ID,
                List.of(targetReference)
            )
        )
            .thenReturn(List.of(targetReference));
        when(
            modelSpecs.listForRelationshipGraph(
                TENANT,
                PLAN_ID,
                sourceId,
                501
            )
        )
            .thenReturn(List.of(targetModel, lastModel));
        when(
            inboundModels.listEarlierSources(
                TENANT,
                PLAN_ID,
                sourceId,
                List.of(targetReference, lastReference),
                501
            )
        )
            .thenReturn(List.of(sourceReference));
        when(
            modelSpecs.revisionsForRelationshipGraph(
                TENANT,
                List.of(sourceReference),
                500
            )
        )
            .thenReturn(List.of(sourceModel));
        WarehousePlanRelationshipGraphService service =
            new WarehousePlanRelationshipGraphService(
                modelSpecs,
                inboundModels,
                CURSOR_SIGNING_SECRET
            );

        RelationshipGraph first = service.read(
            TENANT,
            plan(),
            null,
            null,
            null,
            500,
            null
        );
        RelationshipGraph second = service.read(
            TENANT,
            plan(),
            null,
            null,
            null,
            500,
            first.nextCursor()
        );
        String sourceNodeId = "MODEL:" + sourceId + "@1";
        String targetNodeId = "MODEL:" + targetId + "@1";
        RelationshipEdge dependency = new RelationshipEdge(
            sourceNodeId,
            targetNodeId,
            EdgeKind.DEPENDS_ON,
            "Depends on r1"
        );

        assertSelfContained(first);
        assertSelfContained(second);
        assertThat(first.nodes())
            .extracting(RelationshipNode::id)
            .doesNotContain(targetNodeId);
        assertThat(first.edges()).doesNotContain(dependency);
        assertThat(second.nodes())
            .extracting(RelationshipNode::id)
            .contains(sourceNodeId, targetNodeId);
        assertThat(second.edges()).containsExactlyInAnyOrder(
            new RelationshipEdge(
                "PLAN:" + PLAN_ID,
                targetNodeId,
                CONTAINS,
                "Plan model"
            ),
            new RelationshipEdge(
                "PLAN:" + PLAN_ID,
                "MODEL:" + lastId + "@1",
                CONTAINS,
                "Plan model"
            ),
            dependency
        );
        assertThat(second.nextCursor()).isNull();
    }

    private static List<ModelSpecView> models(int first, int last) {
        List<ModelSpecView> result = new ArrayList<>(last - first + 1);
        for (int index = first; index <= last; index++) {
            result.add(model(new UUID(0, index), "model-%03d".formatted(index)));
        }
        return result;
    }

    private static ModelSpecView model(UUID id, String name) {
        return model(id, name, List.of());
    }

    private static ModelSpecView model(
        UUID id,
        String name,
        List<ModelRevisionRef> dependsOn
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
            List.of(),
            List.of(),
            List.of(),
            null,
            DRAFT,
            1,
            null,
            null,
            null,
            CANONICAL,
            null
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
            WarehousePlanContract.LifecycleStatus.DRAFT,
            1
        );
    }

    private static void assertSelfContained(RelationshipGraph graph) {
        Set<String> nodeIds = graph
            .nodes()
            .stream()
            .map(RelationshipNode::id)
            .collect(java.util.stream.Collectors.toSet());
        assertThat(graph.edges())
            .allSatisfy(edge -> {
                assertThat(nodeIds).contains(edge.source());
                assertThat(nodeIds).contains(edge.target());
            });
    }

    private record EdgeKey(String source, String target, EdgeKind kind) {}
}
