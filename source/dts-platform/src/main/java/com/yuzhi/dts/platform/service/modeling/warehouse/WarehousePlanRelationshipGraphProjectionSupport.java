package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.governance.IndicatorService.IndicatorDependency;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitView;
import com.yuzhi.dts.platform.service.governance.ReferenceCodeService.RelationshipGraphReferenceCode;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.MetricRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipEdge;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipGraph;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipNode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Pure projection helpers and bounded work state for relationship graph reads. */
final class WarehousePlanRelationshipGraphProjectionSupport {

    static final int MAX_NODES = 500;
    static final int MAX_EDGES = 1000;
    static final int MAX_REFERENCE_WORK = (MAX_EDGES * 2) + MAX_NODES + 1;

    /**
     * PostgreSQL compares UUIDs by their unsigned network-order bytes, while {@link UUID#compareTo}
     * compares signed longs. Root ownership must use the database order at the 7fff/8000 boundary.
     */
    static final Comparator<UUID> POSTGRES_UUID_ORDER = (left, right) -> {
        int mostSignificant = Long.compareUnsigned(
            left.getMostSignificantBits(),
            right.getMostSignificantBits()
        );
        return mostSignificant != 0
            ? mostSignificant
            : Long.compareUnsigned(
                left.getLeastSignificantBits(),
                right.getLeastSignificantBits()
            );
    };
    static final Comparator<RelationshipNode> NODE_ORDER = Comparator
        .comparing(RelationshipNode::kind)
        .thenComparing(RelationshipNode::label, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(RelationshipNode::id);
    static final Comparator<RelationshipEdge> EDGE_ORDER = Comparator
        .comparing(RelationshipEdge::kind)
        .thenComparing(RelationshipEdge::source)
        .thenComparing(RelationshipEdge::target)
        .thenComparing(RelationshipEdge::label);

    private static final String FILTER_NEXT_HINT = "FILTER_BY_KIND_OR_QUERY";
    private static final String CURSOR_NEXT_HINT = "CONTINUE_WITH_CURSOR";

    private WarehousePlanRelationshipGraphProjectionSupport() {}

    static ReferenceCollector collectReferences(
        Collection<ModelSpecView> models,
        NodeKind kindFilter,
        WorkBudget work
    ) {
        ReferenceCollector result = new ReferenceCollector();
        for (ModelSpecView model : models) {
            ModelRevisionKey source = modelKey(model);
            if (kindFilter == null || kindFilter == NodeKind.MODEL) {
                collectModelLinks(source, model.dependsOn(), EdgeKind.DEPENDS_ON, "Depends on", result, work);
                collectModelLinks(
                    source,
                    model.dimensionRefs(),
                    EdgeKind.DIMENSION_REFERENCE,
                    "Dimension model reference",
                    result,
                    work
                );
            }
            if (kindFilter == null || kindFilter == NodeKind.DIMENSION) {
                DimensionDefinitionRef definition = model.dimensionDefinitionRef();
                if (valid(definition)) {
                    if (!work.tryConsume()) {
                        result.truncated = true;
                        break;
                    }
                    DimensionRevisionKey key = dimensionKey(definition);
                    if (!putBounded(result.dimensionDefinitions, key, definition)) {
                        result.stop(work);
                        break;
                    }
                    result.dimensionLinks.add(new DimensionLink(source, definition));
                }
            }
            if (kindFilter == null || kindFilter == NodeKind.STANDARD) {
                for (StandardBinding binding : safe(model.standardBindings())) {
                    if (!work.tryConsume()) {
                        result.truncated = true;
                        break;
                    }
                    if (binding == null) continue;
                    if (
                        binding.standardElementId() != null &&
                        positive(binding.standardElementVersion())
                    ) {
                        if (
                            !collectStandard(
                                source,
                                StandardOwnerKey.element(
                                    binding.standardElementId(),
                                    binding.standardElementVersion()
                                ),
                                standardLabel(
                                    binding.fieldName(),
                                    "Data element",
                                    binding.standardElementVersion()
                                ),
                                result,
                                work
                            )
                        ) break;
                    }
                    if (
                        binding.referenceCode() != null &&
                        !binding.referenceCode().isBlank() &&
                        positive(binding.referenceCodeVersion())
                    ) {
                        if (
                            !collectStandard(
                                source,
                                StandardOwnerKey.referenceCode(
                                    binding.referenceCode(),
                                    binding.referenceCodeVersion()
                                ),
                                standardLabel(
                                    binding.fieldName(),
                                    "Reference code",
                                    binding.referenceCodeVersion()
                                ),
                                result,
                                work
                            )
                        ) break;
                    }
                    if (
                        binding.measurementUnitId() != null &&
                        positive(binding.measurementUnitVersion())
                    ) {
                        if (
                            !collectStandard(
                                source,
                                StandardOwnerKey.measurementUnit(
                                    binding.measurementUnitId(),
                                    binding.measurementUnitVersion()
                                ),
                                standardLabel(
                                    binding.fieldName(),
                                    "Measurement unit",
                                    binding.measurementUnitVersion()
                                ),
                                result,
                                work
                            )
                        ) break;
                    }
                }
            }
            if (kindFilter == null || kindFilter == NodeKind.INDICATOR) {
                for (MetricRef metricRef : safe(model.metricRefs())) {
                    if (!work.tryConsume()) {
                        result.truncated = true;
                        break;
                    }
                    UUID indicatorId = parseUuid(metricRef == null ? null : metricRef.metricId());
                    if (indicatorId == null || metricRef.version() < 1) continue;
                    MetricRevisionKey key = new MetricRevisionKey(indicatorId, metricRef.version());
                    if (
                        (!result.indicatorIds.contains(indicatorId) &&
                            result.indicatorIds.size() >= MAX_NODES) ||
                        (!result.metricReferences.contains(key) &&
                            result.metricReferences.size() >= MAX_NODES)
                    ) {
                        result.stop(work);
                        break;
                    }
                    result.indicatorIds.add(indicatorId);
                    result.metricReferences.add(key);
                    result.metricLinks.add(new MetricLink(source, key));
                }
            }
        }
        return result;
    }

    static RelationshipGraph finish(
        UUID planId,
        Map<String, RelationshipNode> candidates,
        List<RelationshipEdge> edges,
        NodeKind kindFilter,
        String query,
        int limit,
        boolean sourceTruncated,
        String nextCursor
    ) {
        List<RelationshipNode> matching = candidates
            .values()
            .stream()
            .filter(node -> kindFilter == null || node.kind() == kindFilter)
            .filter(node -> matchesQuery(node, query))
            .sorted(NODE_ORDER)
            .toList();
        List<RelationshipNode> selected = matching.stream().limit(limit).toList();
        boolean truncated = sourceTruncated || matching.size() > selected.size() || edges.size() > MAX_EDGES;
        return new RelationshipGraph(
            planId,
            selected,
            edges.stream().limit(MAX_EDGES).toList(),
            truncated,
            nextHint(truncated, nextCursor),
            nextCursor
        );
    }

    static RelationshipNode planNode(WarehousePlanHeader plan) {
        return new RelationshipNode(
            nodeId(NodeKind.PLAN, plan.id()),
            NodeKind.PLAN,
            firstText(plan.name(), plan.code(), plan.id().toString()),
            plan.lifecycleStatus() == null ? null : plan.lifecycleStatus().name(),
            "/modeling/workbench?planId=" +
            plan.id() +
            "&module=planning&workspaceView=overview&assetKind=plan&assetId=" +
            plan.id()
        );
    }

    static RelationshipNode modelNode(ModelSpecView model) {
        return new RelationshipNode(
            modelNodeId(modelKey(model)),
            NodeKind.MODEL,
            modelLabel(model),
            model.status() == null ? null : model.status().name(),
            "/modeling/workbench?planId=" +
            model.planId() +
            "&module=models&workspaceView=model-specs&assetKind=model&assetId=" +
            model.id() +
            "&revision=" +
            model.revision() +
            "&activeStage=logical"
        );
    }

    static RelationshipNode dimensionNode(UUID planId, View definition) {
        DimensionRevisionKey key = new DimensionRevisionKey(definition.id(), definition.revision());
        return new RelationshipNode(
            dimensionNodeId(key),
            NodeKind.DIMENSION,
            firstText(definition.name(), definition.systemCode(), definition.id().toString()),
            definition.status() == null ? null : definition.status().name(),
            "/modeling/workbench?planId=" +
            planId +
            "&module=models&workspaceView=dimensions&assetKind=dimension&assetId=" +
            definition.id() +
            "&revision=" +
            definition.revision()
        );
    }

    static RelationshipNode standardElementNode(UUID planId, MetadataStandardDto standard) {
        StandardOwnerKey key = StandardOwnerKey.element(standard.getId(), standard.getVersion());
        return new RelationshipNode(
            standardNodeId(key),
            NodeKind.STANDARD,
            firstText(standard.getFieldNameCn(), standard.getFieldNameEn(), standard.getId().toString()),
            null,
            "/modeling/workbench?planId=" +
            planId +
            "&module=standards&workspaceView=elements&assetKind=standard&assetId=" +
            standard.getId() +
            "&version=" +
            standard.getVersion()
        );
    }

    static RelationshipNode referenceCodeNode(
        UUID planId,
        RelationshipGraphReferenceCode code,
        int version
    ) {
        StandardOwnerKey key = StandardOwnerKey.referenceCode(code.codeTypeId(), version);
        return new RelationshipNode(
            standardNodeId(key),
            NodeKind.STANDARD,
            firstText(code.codeTypeName(), code.codeTypeCode(), code.codeTypeId()),
            "ACTIVE",
            "/modeling/workbench?planId=" +
            planId +
            "&module=standards&workspaceView=reference-codes&assetKind=referenceCode&assetId=" +
            encode(code.codeTypeId()) +
            "&version=" +
            version
        );
    }

    static RelationshipNode measurementUnitNode(UUID planId, MeasurementUnitView unit) {
        StandardOwnerKey key = StandardOwnerKey.measurementUnit(unit.id(), unit.version());
        return new RelationshipNode(
            standardNodeId(key),
            NodeKind.STANDARD,
            firstText(unit.name(), unit.code(), unit.symbol(), unit.id().toString()),
            unit.status().name(),
            "/modeling/workbench?planId=" +
            planId +
            "&module=standards&workspaceView=measurement-units&assetKind=measurementUnit&assetId=" +
            unit.id() +
            "&version=" +
            unit.version()
        );
    }

    static RelationshipNode indicatorNode(UUID planId, IndicatorDto indicator) {
        return new RelationshipNode(
            nodeId(NodeKind.INDICATOR, indicator.getId()),
            NodeKind.INDICATOR,
            indicatorLabel(indicator),
            indicator.getStatus(),
            "/modeling/workbench?planId=" +
            planId +
            "&module=metrics&workspaceView=definitions&assetKind=indicator&assetId=" +
            indicator.getId()
        );
    }

    static boolean matchesQuery(RelationshipNode node, String query) {
        return (
            query == null ||
            node.label().toLowerCase(Locale.ROOT).contains(query) ||
            node.id().toLowerCase(Locale.ROOT).contains(query)
        );
    }

    static String indicatorLabel(IndicatorDto indicator) {
        return firstText(indicator.getName(), indicator.getCode(), indicator.getId().toString());
    }

    static NodeKind parseKind(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return NodeKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new WarehousePlanException(
                "RELATIONSHIP_GRAPH_KIND_INVALID",
                "Relationship graph kind is invalid",
                null
            );
        }
    }

    static int requireLimit(int limit) {
        if (limit < 1 || limit > MAX_NODES) {
            throw new WarehousePlanException(
                "RELATIONSHIP_GRAPH_LIMIT_INVALID",
                "Relationship graph limit must be between 1 and 500",
                null
            );
        }
        return limit;
    }

    static String nextHint(boolean truncated, String nextCursor) {
        if (nextCursor != null) return CURSOR_NEXT_HINT;
        return truncated ? FILTER_NEXT_HINT : null;
    }

    static String normalizeQuery(String query) {
        if (query == null || query.isBlank()) return null;
        String normalized = Normalizer.normalize(query.trim(), Normalizer.Form.NFKC);
        if (normalized.length() > 128) {
            throw new WarehousePlanException(
                "RELATIONSHIP_GRAPH_QUERY_INVALID",
                "Relationship graph query must not exceed 128 characters",
                null
            );
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    static int numericVersion(String value) {
        if (value == null || value.isBlank()) return -1;
        String normalized = value.trim();
        if (normalized.length() > 1 && (normalized.charAt(0) == 'v' || normalized.charAt(0) == 'V')) {
            normalized = normalized.substring(1);
        }
        try {
            int parsed = Integer.parseInt(normalized);
            return parsed > 0 ? parsed : -1;
        } catch (NumberFormatException invalid) {
            return -1;
        }
    }

    static boolean valid(ModelRevisionRef reference) {
        return reference != null && reference.modelSpecId() != null && reference.revision() > 0;
    }

    static boolean valid(DimensionDefinitionRef reference) {
        return (
            reference != null &&
            reference.dimensionDefinitionId() != null &&
            reference.revision() > 0
        );
    }

    static void putNode(Map<String, RelationshipNode> nodes, RelationshipNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    static String nodeId(NodeKind kind, UUID id) {
        return kind.name() + ":" + id;
    }

    static String modelNodeId(ModelRevisionKey key) {
        return nodeId(NodeKind.MODEL, key.id()) + "@" + key.revision();
    }

    static String dimensionNodeId(DimensionRevisionKey key) {
        return nodeId(NodeKind.DIMENSION, key.id()) + "@" + key.revision();
    }

    static String standardNodeId(StandardOwnerKey key) {
        return "STANDARD:" + key.type().name() + ":" + encode(key.ownerId()) + "@" + key.version();
    }

    static ModelRevisionKey modelKey(ModelSpecView model) {
        return new ModelRevisionKey(model.id(), model.revision());
    }

    static ModelRevisionKey modelKey(ModelRevisionRef reference) {
        return new ModelRevisionKey(reference.modelSpecId(), reference.revision());
    }

    static DimensionRevisionKey dimensionKey(DimensionDefinitionRef reference) {
        return new DimensionRevisionKey(reference.dimensionDefinitionId(), reference.revision());
    }

    static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    static String modelLinkOwnerNodeId(
        ModelReferenceLink link,
        Set<ModelRevisionKey> canonicalRootKeys
    ) {
        ModelRevisionKey target = modelKey(link.target());
        if (
            canonicalRootKeys.contains(target) &&
            POSTGRES_UUID_ORDER.compare(link.source().id(), target.id()) < 0
        ) {
            return modelNodeId(target);
        }
        return modelNodeId(link.source());
    }

    static boolean collectIncomingModelLinks(
        Collection<ModelSpecView> incomingSources,
        Set<ModelRevisionKey> currentRootKeys,
        ReferenceCollector destination,
        WorkBudget work
    ) {
        ReferenceCollector incoming = collectReferences(
            incomingSources,
            NodeKind.MODEL,
            work
        );
        incoming
            .modelLinks
            .stream()
            .filter(link ->
                currentRootKeys.contains(modelKey(link.target())) &&
                POSTGRES_UUID_ORDER.compare(
                    link.source().id(),
                    link.target().modelSpecId()
                ) <
                0
            )
            .forEach(destination.modelLinks::add);
        return incoming.truncated;
    }

    /**
     * Applies {@code limit} only to cursor-owned nodes, then repeats any required edge endpoints as
     * context. Context nodes do not advance the cursor and may make the response larger than the
     * requested owned-node limit.
     */
    static List<RelationshipNode> selfContainedPageNodes(
        List<RelationshipNode> selectedNodes,
        List<RelationshipEdge> selectedEdges,
        Map<String, RelationshipNode> visibleNodes
    ) {
        Map<String, RelationshipNode> pageNodes = new LinkedHashMap<>();
        selectedNodes.forEach(node -> putNode(pageNodes, node));
        for (RelationshipEdge edge : selectedEdges) {
            putRequiredNode(pageNodes, visibleNodes, edge.source());
            putRequiredNode(pageNodes, visibleNodes, edge.target());
        }
        return pageNodes.values().stream().sorted(NODE_ORDER).toList();
    }

    private static void putRequiredNode(
        Map<String, RelationshipNode> pageNodes,
        Map<String, RelationshipNode> visibleNodes,
        String nodeId
    ) {
        RelationshipNode node = visibleNodes.get(nodeId);
        if (node == null) {
            throw new IllegalStateException(
                "Relationship graph edge endpoint is not visible: " + nodeId
            );
        }
        putNode(pageNodes, node);
    }

    private static void collectModelLinks(
        ModelRevisionKey source,
        List<ModelRevisionRef> references,
        EdgeKind kind,
        String label,
        ReferenceCollector result,
        WorkBudget work
    ) {
        for (ModelRevisionRef reference : safe(references)) {
            if (!work.tryConsume()) {
                result.truncated = true;
                return;
            }
            if (!valid(reference)) continue;
            if (!result.modelReferences.contains(reference) && result.modelReferences.size() >= MAX_NODES) {
                result.stop(work);
                return;
            }
            result.modelReferences.add(reference);
            result.modelLinks.add(new ModelReferenceLink(source, reference, kind, label + " r" + reference.revision()));
        }
    }

    private static boolean collectStandard(
        ModelRevisionKey source,
        StandardOwnerKey owner,
        String label,
        ReferenceCollector result,
        WorkBudget work
    ) {
        if (!work.tryConsume()) {
            result.truncated = true;
            return false;
        }
        if (!result.standardOwners.contains(owner) && result.standardOwners.size() >= MAX_NODES) {
            result.stop(work);
            return false;
        }
        result.standardOwners.add(owner);
        result.standardLinks.add(new StandardLink(source, owner, label));
        return true;
    }

    private static String modelLabel(ModelSpecView model) {
        return firstText(model.name(), model.id().toString());
    }

    private static String standardLabel(String fieldName, String type, int version) {
        return firstText(fieldName, "Standard binding") + " · " + type + " v" + version;
    }

    private static String firstText(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) return candidate.trim();
        }
        return "";
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private static <K, V> boolean putBounded(Map<K, V> target, K key, V value) {
        if (target.containsKey(key)) return true;
        if (target.size() >= MAX_NODES) return false;
        target.put(key, value);
        return true;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    record ModelRevisionKey(UUID id, int revision) {}

    record DimensionRevisionKey(UUID id, int revision) {}

    record ModelReferenceLink(
        ModelRevisionKey source,
        ModelRevisionRef target,
        EdgeKind kind,
        String label
    ) {}

    record DimensionLink(ModelRevisionKey source, DimensionDefinitionRef reference) {}

    enum StandardOwnerType {
        ELEMENT,
        REFERENCE_CODE,
        MEASUREMENT_UNIT,
    }

    record StandardOwnerKey(StandardOwnerType type, String ownerId, int version) {
        static StandardOwnerKey element(UUID id, int version) {
            return new StandardOwnerKey(StandardOwnerType.ELEMENT, id.toString(), version);
        }

        static StandardOwnerKey referenceCode(String id, int version) {
            return new StandardOwnerKey(StandardOwnerType.REFERENCE_CODE, id.trim(), version);
        }

        static StandardOwnerKey measurementUnit(UUID id, int version) {
            return new StandardOwnerKey(StandardOwnerType.MEASUREMENT_UNIT, id.toString(), version);
        }
    }

    record StandardLink(ModelRevisionKey source, StandardOwnerKey owner, String label) {}

    record MetricRevisionKey(UUID id, int version) {}

    record MetricLink(ModelRevisionKey source, MetricRevisionKey reference) {}

    record IndicatorOwners(
        Map<UUID, IndicatorDto> visible,
        List<IndicatorDependency> dependencies,
        boolean truncated
    ) {}

    record EdgeKey(String source, String target, EdgeKind kind) {}

    static final class ReferenceCollector {

        final LinkedHashSet<ModelRevisionRef> modelReferences = new LinkedHashSet<>();
        final List<ModelReferenceLink> modelLinks = new ArrayList<>();
        final LinkedHashMap<DimensionRevisionKey, DimensionDefinitionRef> dimensionDefinitions =
            new LinkedHashMap<>();
        final List<DimensionLink> dimensionLinks = new ArrayList<>();
        final LinkedHashSet<StandardOwnerKey> standardOwners = new LinkedHashSet<>();
        final List<StandardLink> standardLinks = new ArrayList<>();
        final LinkedHashSet<UUID> indicatorIds = new LinkedHashSet<>();
        final LinkedHashSet<MetricRevisionKey> metricReferences = new LinkedHashSet<>();
        final List<MetricLink> metricLinks = new ArrayList<>();
        boolean truncated;

        void stop(WorkBudget work) {
            truncated = true;
            work.exhaust();
        }
    }

    static final class WorkBudget {

        private int remaining;

        WorkBudget(int remaining) {
            this.remaining = remaining;
        }

        boolean tryConsume() {
            if (remaining < 1) return false;
            remaining--;
            return true;
        }

        int remaining() {
            return remaining;
        }

        boolean exhausted() {
            return remaining < 1;
        }

        void exhaust() {
            remaining = 0;
        }
    }

    static final class EdgeWork {

        private final WorkBudget budget;
        boolean truncated;

        EdgeWork(int limit) {
            this.budget = new WorkBudget(limit);
        }

        boolean add(
            EdgeAccumulator edges,
            RelationshipEdge edge,
            String ownerNodeId
        ) {
            if (!budget.tryConsume()) {
                truncated = true;
                return false;
            }
            return edges.add(edge, ownerNodeId);
        }

        boolean available() {
            return !truncated;
        }
    }

    static final class EdgeAccumulator {

        private final Set<String> selectedNodeIds;
        private final Set<String> eligibleNodeIds;
        private final Map<EdgeKey, RelationshipEdge> edges = new LinkedHashMap<>();
        private boolean truncated;

        EdgeAccumulator(
            Set<String> selectedNodeIds,
            Set<String> eligibleNodeIds
        ) {
            this.selectedNodeIds = selectedNodeIds;
            this.eligibleNodeIds = eligibleNodeIds;
        }

        boolean add(RelationshipEdge edge, String ownerNodeId) {
            if (
                !selectedNodeIds.contains(ownerNodeId) ||
                !eligibleNodeIds.contains(edge.source()) ||
                !eligibleNodeIds.contains(edge.target())
            ) {
                return true;
            }
            EdgeKey key = new EdgeKey(edge.source(), edge.target(), edge.kind());
            if (edges.containsKey(key)) return true;
            if (edges.size() >= MAX_EDGES + 1) {
                truncated = true;
                return false;
            }
            edges.put(key, edge);
            if (edges.size() > MAX_EDGES) {
                truncated = true;
                return false;
            }
            return true;
        }

        List<RelationshipEdge> values() {
            return new ArrayList<>(edges.values());
        }

        boolean truncated() {
            return truncated;
        }
    }
}
