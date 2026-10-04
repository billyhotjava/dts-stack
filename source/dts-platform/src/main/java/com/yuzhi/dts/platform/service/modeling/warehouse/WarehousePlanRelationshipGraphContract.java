package com.yuzhi.dts.platform.service.modeling.warehouse;

import java.util.List;
import java.util.UUID;

/** Read-only graph projection contract for one canonical warehouse plan. */
public final class WarehousePlanRelationshipGraphContract {

    private WarehousePlanRelationshipGraphContract() {}

    public enum NodeKind {
        PLAN,
        MODEL,
        DIMENSION,
        STANDARD,
        INDICATOR,
    }

    public enum EdgeKind {
        CONTAINS,
        DEPENDS_ON,
        DIMENSION_REFERENCE,
        DIMENSION_DEFINITION_REFERENCE,
        STANDARD_BINDING,
        INDICATOR_REFERENCE,
        INDICATOR_DEPENDS_ON,
    }

    public record RelationshipNode(String id, NodeKind kind, String label, String status, String route) {}

    public record RelationshipEdge(String source, String target, EdgeKind kind, String label) {}

    public record RelationshipGraph(
        UUID planId,
        List<RelationshipNode> nodes,
        List<RelationshipEdge> edges,
        boolean truncated,
        String nextHint,
        String nextCursor
    ) {
        public RelationshipGraph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            edges = edges == null ? List.of() : List.copyOf(edges);
        }

        public RelationshipGraph(
            UUID planId,
            List<RelationshipNode> nodes,
            List<RelationshipEdge> edges,
            boolean truncated
        ) {
            this(planId, nodes, edges, truncated, null, null);
        }

        public RelationshipGraph(
            UUID planId,
            List<RelationshipNode> nodes,
            List<RelationshipEdge> edges,
            boolean truncated,
            String nextHint
        ) {
            this(planId, nodes, edges, truncated, nextHint, null);
        }
    }
}
