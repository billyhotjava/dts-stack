package com.yuzhi.dts.platform.service.modeling.warehouse;

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
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
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
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.annotation.Transactional;

/** Bounded read-only projection over canonical modeling and governance owners. */
@Service
public class WarehousePlanRelationshipGraphService {

    static final int MAX_NODES = 500;
    static final int MAX_EDGES = 1000;
    static final int MAX_REFERENCE_WORK = (MAX_EDGES * 2) + MAX_NODES + 1;
    private static final int SOURCE_LOOKAHEAD = MAX_NODES + 1;
    private static final String FILTER_NEXT_HINT = "FILTER_BY_KIND_OR_QUERY";
    private static final String CURSOR_NEXT_HINT = "CONTINUE_WITH_CURSOR";
    private static final String CURSOR_VERSION = "1";
    private static final int MAX_CURSOR_LENGTH = 4096;
    private static final int MAX_CURSOR_NODE_ID_LENGTH = 2048;

    private static final Comparator<RelationshipNode> NODE_ORDER = Comparator
        .comparing(RelationshipNode::kind)
        .thenComparing(RelationshipNode::label, String.CASE_INSENSITIVE_ORDER)
        .thenComparing(RelationshipNode::id);
    private static final Comparator<RelationshipEdge> EDGE_ORDER = Comparator
        .comparing(RelationshipEdge::kind)
        .thenComparing(RelationshipEdge::source)
        .thenComparing(RelationshipEdge::target)
        .thenComparing(RelationshipEdge::label);

    private final ModelSpecApplicationService modelSpecs;
    private final DimensionDefinitionApplicationService dimensionDefinitions;
    private final MetadataStandardService metadataStandards;
    private final IndicatorService indicators;
    private final ReferenceCodeService referenceCodes;
    private final MeasurementUnitApplicationService measurementUnits;

    @Autowired
    public WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits
    ) {
        this.modelSpecs = modelSpecs;
        this.dimensionDefinitions = dimensionDefinitions;
        this.metadataStandards = metadataStandards;
        this.indicators = indicators;
        this.referenceCodes = referenceCodes;
        this.measurementUnits = measurementUnits;
    }

    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators
    ) {
        this(modelSpecs, dimensionDefinitions, metadataStandards, indicators, null, null);
    }

    WarehousePlanRelationshipGraphService(ModelSpecApplicationService modelSpecs) {
        this(modelSpecs, null, null, null, null, null);
    }

    public RelationshipGraph read(String tenantId, WarehousePlanHeader plan) {
        return read(tenantId, plan, null, null, null, MAX_NODES);
    }

    @Transactional(readOnly = true, timeout = 5)
    public RelationshipGraph read(
        String tenantId,
        WarehousePlanHeader plan,
        String activeDepartmentId,
        String kind,
        String query,
        int limit
    ) {
        return read(tenantId, plan, activeDepartmentId, kind, query, limit, null);
    }

    @Transactional(readOnly = true, timeout = 5)
    public RelationshipGraph read(
        String tenantId,
        WarehousePlanHeader plan,
        String activeDepartmentId,
        String kind,
        String query,
        int limit,
        String cursor
    ) {
        Objects.requireNonNull(plan, "plan is required");
        Objects.requireNonNull(plan.id(), "plan id is required");
        NodeKind kindFilter = parseKind(kind);
        int boundedLimit = requireLimit(limit);
        String normalizedQuery = normalizeQuery(query);
        GraphCursor graphCursor = parseCursor(
            cursor,
            tenantId,
            plan.id(),
            activeDepartmentId,
            kindFilter,
            normalizedQuery
        );
        if (kindFilter == NodeKind.PLAN && graphCursor != null) {
            throw cursorInvalid();
        }
        try {
            return project(
                tenantId,
                plan,
                activeDepartmentId,
                kindFilter,
                normalizedQuery,
                boundedLimit,
                graphCursor
            );
        } catch (QueryTimeoutException | TransactionTimedOutException timeout) {
            throw new WarehousePlanException(
                "RELATIONSHIP_GRAPH_TIMEOUT",
                "Relationship graph projection timed out",
                null
            );
        }
    }

    private RelationshipGraph project(
        String tenantId,
        WarehousePlanHeader plan,
        String activeDepartmentId,
        NodeKind kindFilter,
        String query,
        int limit,
        GraphCursor cursor
    ) {
        Map<String, RelationshipNode> candidates = new LinkedHashMap<>();
        if (kindFilter == NodeKind.PLAN) {
            putNode(candidates, planNode(plan));
            return finish(plan.id(), candidates, List.of(), kindFilter, query, limit, false, null);
        }

        UUID afterId = cursor == null ? null : cursor.rootAfter();
        if (afterId == null) {
            putNode(candidates, planNode(plan));
        }
        List<ModelSpecView> loadedModels = nullToEmpty(afterId == null
            ? modelSpecs.listForRelationshipGraph(tenantId, plan.id(), SOURCE_LOOKAHEAD)
            : modelSpecs.listForRelationshipGraph(tenantId, plan.id(), afterId, SOURCE_LOOKAHEAD));
        int rootCapacity = kindFilter == null && afterId == null ? MAX_NODES - 1 : MAX_NODES;
        LinkedHashMap<ModelRevisionKey, ModelSpecView> rootModels = new LinkedHashMap<>();
        boolean hasMoreRoots = false;
        for (ModelSpecView model : loadedModels) {
            if (
                model == null ||
                model.id() == null ||
                model.revision() < 1 ||
                !Objects.equals(plan.id(), model.planId())
            ) continue;
            ModelRevisionKey key = modelKey(model);
            if (rootModels.containsKey(key)) continue;
            if (rootModels.size() >= rootCapacity) {
                hasMoreRoots = true;
                break;
            }
            rootModels.put(key, model);
        }
        UUID rootWindowEnd = rootModels.isEmpty()
            ? null
            : rootModels.values().stream().reduce((left, right) -> right).orElseThrow().id();
        boolean projectionIncomplete = false;
        rootModels.values().forEach(model -> putNode(candidates, modelNode(model)));

        WorkBudget referenceWork = new WorkBudget(MAX_REFERENCE_WORK);
        ReferenceCollector references = collectReferences(rootModels.values(), kindFilter, referenceWork);
        projectionIncomplete |= references.truncated;

        LinkedHashMap<ModelRevisionKey, ModelSpecView> visibleModels = new LinkedHashMap<>(rootModels);
        if (kindFilter == null || kindFilter == NodeKind.MODEL) {
            List<ModelRevisionRef> missingModelRefs = references.modelReferences
                .stream()
                .filter(reference -> !visibleModels.containsKey(modelKey(reference)))
                .toList();
            if (!missingModelRefs.isEmpty()) {
                for (ModelSpecView model : nullToEmpty(
                    modelSpecs.revisionsForRelationshipGraph(tenantId, missingModelRefs, MAX_NODES)
                )) {
                    if (model == null || model.id() == null || model.revision() < 1) continue;
                    ModelRevisionKey key = modelKey(model);
                    if (!references.modelReferences.contains(new ModelRevisionRef(key.id(), key.revision()))) continue;
                    visibleModels.putIfAbsent(key, model);
                    putNode(candidates, modelNode(model));
                }
            }
        }

        Map<DimensionRevisionKey, View> visibleDefinitions = hydrateDimensions(
            tenantId,
            plan.id(),
            kindFilter,
            references,
            candidates
        );
        Map<StandardOwnerKey, RelationshipNode> visibleStandards = hydrateStandards(
            plan.id(),
            activeDepartmentId,
            kindFilter,
            references,
            candidates
        );
        IndicatorOwners indicatorOwners = hydrateIndicators(
            plan.id(),
            activeDepartmentId,
            kindFilter,
            references,
            referenceWork,
            candidates
        );
        projectionIncomplete |= indicatorOwners.truncated;

        List<RelationshipNode> matchingNodes = candidates
            .values()
            .stream()
            .filter(node -> kindFilter == null || node.kind() == kindFilter)
            .filter(node -> matchesQuery(node, query))
            .sorted(NODE_ORDER)
            .toList();
        int selectedStart = cursorNodeStart(matchingNodes, cursor);
        int selectedEnd = Math.min(selectedStart + limit, matchingNodes.size());
        List<RelationshipNode> selectedNodes = matchingNodes.subList(selectedStart, selectedEnd);
        boolean hasMoreMatchingNodes = selectedEnd < matchingNodes.size();
        Set<String> selectedIds = selectedNodes
            .stream()
            .map(RelationshipNode::id)
            .collect(java.util.stream.Collectors.toSet());

        EdgeAccumulator edges = new EdgeAccumulator(selectedIds);
        EdgeWork edgeWork = new EdgeWork(MAX_REFERENCE_WORK);
        String planNodeId = nodeId(NodeKind.PLAN, plan.id());
        for (ModelSpecView root : rootModels.values()) {
            if (!edgeWork.add(edges, new RelationshipEdge(planNodeId, modelNodeId(modelKey(root)), EdgeKind.CONTAINS, "Plan model"))) {
                break;
            }
        }
        if (edgeWork.available()) {
            for (ModelReferenceLink link : references.modelLinks) {
                if (!visibleModels.containsKey(modelKey(link.target()))) continue;
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            modelNodeId(link.source()),
                            modelNodeId(modelKey(link.target())),
                            link.kind(),
                            link.label()
                        )
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (DimensionLink link : references.dimensionLinks) {
                DimensionRevisionKey target = dimensionKey(link.reference());
                if (!visibleDefinitions.containsKey(target)) continue;
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            modelNodeId(link.source()),
                            dimensionNodeId(target),
                            EdgeKind.DIMENSION_DEFINITION_REFERENCE,
                            "Dimension definition r" + target.revision()
                        )
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (StandardLink link : references.standardLinks) {
                if (!visibleStandards.containsKey(link.owner())) continue;
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            modelNodeId(link.source()),
                            standardNodeId(link.owner()),
                            EdgeKind.STANDARD_BINDING,
                            link.label()
                        )
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (MetricLink link : references.metricLinks) {
                IndicatorDto indicator = indicatorOwners.visible.get(link.reference().id());
                if (indicator == null || numericVersion(indicator.getVersion()) != link.reference().version()) continue;
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            modelNodeId(link.source()),
                            nodeId(NodeKind.INDICATOR, link.reference().id()),
                            EdgeKind.INDICATOR_REFERENCE,
                            "Indicator reference v" + link.reference().version()
                        )
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (IndicatorDependency dependency : indicatorOwners.dependencies) {
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            nodeId(NodeKind.INDICATOR, dependency.sourceId()),
                            nodeId(NodeKind.INDICATOR, dependency.targetId()),
                            EdgeKind.INDICATOR_DEPENDS_ON,
                            "Indicator dependency"
                        )
                    )
                ) break;
            }
        }
        projectionIncomplete |= edgeWork.truncated || edges.truncated();
        boolean truncated = projectionIncomplete || hasMoreMatchingNodes || hasMoreRoots;
        String nextCursor = null;
        if (!projectionIncomplete) {
            if (hasMoreMatchingNodes) {
                nextCursor = encodeCursor(
                    new GraphCursor(afterId, selectedNodes.getLast().id()),
                    tenantId,
                    plan.id(),
                    activeDepartmentId,
                    kindFilter,
                    query
                );
            } else if (hasMoreRoots && rootWindowEnd != null) {
                nextCursor = encodeCursor(
                    new GraphCursor(rootWindowEnd, null),
                    tenantId,
                    plan.id(),
                    activeDepartmentId,
                    kindFilter,
                    query
                );
            }
        }
        List<RelationshipEdge> selectedEdges = edges.values().stream().sorted(EDGE_ORDER).limit(MAX_EDGES).toList();
        return new RelationshipGraph(
            plan.id(),
            selectedNodes,
            selectedEdges,
            truncated,
            nextHint(truncated, nextCursor),
            nextCursor
        );
    }

    private Map<DimensionRevisionKey, View> hydrateDimensions(
        String tenantId,
        UUID planId,
        NodeKind kindFilter,
        ReferenceCollector references,
        Map<String, RelationshipNode> candidates
    ) {
        Map<DimensionRevisionKey, View> visible = new LinkedHashMap<>();
        if (
            dimensionDefinitions == null ||
            (kindFilter != null && kindFilter != NodeKind.DIMENSION) ||
            references.dimensionDefinitions.isEmpty()
        ) return visible;
        for (View definition : nullToEmpty(
            dimensionDefinitions.revisionsForRelationshipGraph(
                tenantId,
                List.copyOf(references.dimensionDefinitions.values()),
                MAX_NODES
            )
        )) {
            if (definition == null || definition.id() == null || definition.revision() < 1) continue;
            DimensionRevisionKey key = new DimensionRevisionKey(definition.id(), definition.revision());
            if (!references.dimensionDefinitions.containsKey(key)) continue;
            visible.putIfAbsent(key, definition);
            putNode(candidates, dimensionNode(planId, definition));
        }
        return visible;
    }

    private Map<StandardOwnerKey, RelationshipNode> hydrateStandards(
        UUID planId,
        String activeDepartmentId,
        NodeKind kindFilter,
        ReferenceCollector references,
        Map<String, RelationshipNode> candidates
    ) {
        Map<StandardOwnerKey, RelationshipNode> visible = new LinkedHashMap<>();
        if ((kindFilter != null && kindFilter != NodeKind.STANDARD) || references.standardOwners.isEmpty()) {
            return visible;
        }
        Set<UUID> elementIds = new LinkedHashSet<>();
        Set<String> codeIds = new LinkedHashSet<>();
        Set<UUID> unitIds = new LinkedHashSet<>();
        for (StandardOwnerKey key : references.standardOwners) {
            switch (key.type()) {
                case ELEMENT -> elementIds.add(UUID.fromString(key.ownerId()));
                case REFERENCE_CODE -> codeIds.add(key.ownerId());
                case MEASUREMENT_UNIT -> unitIds.add(UUID.fromString(key.ownerId()));
            }
        }
        if (metadataStandards != null && !elementIds.isEmpty()) {
            for (MetadataStandardDto standard : nullToEmpty(
                metadataStandards.listForRelationshipGraph(elementIds, MAX_NODES)
            )) {
                if (standard == null || standard.getId() == null || standard.getVersion() == null) continue;
                StandardOwnerKey key = StandardOwnerKey.element(standard.getId(), standard.getVersion());
                if (!references.standardOwners.contains(key)) continue;
                RelationshipNode node = standardElementNode(planId, standard);
                visible.putIfAbsent(key, node);
                putNode(candidates, node);
            }
        }
        if (referenceCodes != null && !codeIds.isEmpty()) {
            for (RelationshipGraphReferenceCode code : nullToEmpty(
                referenceCodes.listForRelationshipGraph(codeIds, activeDepartmentId, MAX_NODES)
            )) {
                int version = numericVersion(code == null ? null : code.version());
                if (code == null || code.codeTypeId() == null || version < 1 || !Integer.valueOf(1).equals(code.status())) {
                    continue;
                }
                StandardOwnerKey key = StandardOwnerKey.referenceCode(code.codeTypeId(), version);
                if (!references.standardOwners.contains(key)) continue;
                RelationshipNode node = referenceCodeNode(planId, code, version);
                visible.putIfAbsent(key, node);
                putNode(candidates, node);
            }
        }
        if (measurementUnits != null && !unitIds.isEmpty()) {
            for (MeasurementUnitView unit : nullToEmpty(
                measurementUnits.listForRelationshipGraph(unitIds, MAX_NODES)
            )) {
                if (unit == null || unit.id() == null || unit.version() < 1 || unit.status() != MeasurementUnitStatus.ACTIVE) {
                    continue;
                }
                StandardOwnerKey key = StandardOwnerKey.measurementUnit(unit.id(), unit.version());
                if (!references.standardOwners.contains(key)) continue;
                RelationshipNode node = measurementUnitNode(planId, unit);
                visible.putIfAbsent(key, node);
                putNode(candidates, node);
            }
        }
        return visible;
    }

    private IndicatorOwners hydrateIndicators(
        UUID planId,
        String activeDepartmentId,
        NodeKind kindFilter,
        ReferenceCollector references,
        WorkBudget referenceWork,
        Map<String, RelationshipNode> candidates
    ) {
        if (
            indicators == null ||
            (kindFilter != null && kindFilter != NodeKind.INDICATOR) ||
            references.indicatorIds.isEmpty()
        ) return new IndicatorOwners(Map.of(), List.of(), false);
        int dependencyLimit = Math.min(MAX_EDGES, Math.max(1, referenceWork.remaining()));
        RelationshipGraphProjection projection = indicators.projectForRelationshipGraph(
            references.indicatorIds,
            activeDepartmentId,
            MAX_NODES,
            dependencyLimit
        );
        if (projection == null) return new IndicatorOwners(Map.of(), List.of(), false);
        Map<UUID, IndicatorDto> authorized = new LinkedHashMap<>();
        for (IndicatorDto indicator : nullToEmpty(projection.indicators())) {
            if (indicator != null && indicator.getId() != null) authorized.putIfAbsent(indicator.getId(), indicator);
        }
        Map<UUID, IndicatorDto> visible = new LinkedHashMap<>();
        for (MetricRevisionKey reference : references.metricReferences) {
            IndicatorDto indicator = authorized.get(reference.id());
            if (indicator != null && numericVersion(indicator.getVersion()) == reference.version()) {
                visible.putIfAbsent(reference.id(), indicator);
            }
        }
        List<IndicatorDependency> dependencies = new ArrayList<>();
        boolean truncated = projection.truncated();
        for (IndicatorDependency dependency : nullToEmpty(projection.dependencies())) {
            if (!referenceWork.tryConsume()) {
                truncated = true;
                break;
            }
            if (
                dependency == null ||
                !visible.containsKey(dependency.sourceId()) ||
                !authorized.containsKey(dependency.targetId())
            ) continue;
            visible.putIfAbsent(dependency.targetId(), authorized.get(dependency.targetId()));
            dependencies.add(dependency);
        }
        visible
            .values()
            .stream()
            .sorted(
                Comparator
                    .comparing(WarehousePlanRelationshipGraphService::indicatorLabel, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(indicator -> indicator.getId().toString())
            )
            .map(indicator -> indicatorNode(planId, indicator))
            .forEach(node -> putNode(candidates, node));
        return new IndicatorOwners(visible, List.copyOf(dependencies), truncated);
    }

    private static ReferenceCollector collectReferences(
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
                        if (!collectStandard(
                            source,
                            StandardOwnerKey.element(binding.standardElementId(), binding.standardElementVersion()),
                            standardLabel(binding.fieldName(), "Data element", binding.standardElementVersion()),
                            result,
                            work
                        )) break;
                    }
                    if (
                        binding.referenceCode() != null &&
                        !binding.referenceCode().isBlank() &&
                        positive(binding.referenceCodeVersion())
                    ) {
                        if (!collectStandard(
                            source,
                            StandardOwnerKey.referenceCode(binding.referenceCode(), binding.referenceCodeVersion()),
                            standardLabel(binding.fieldName(), "Reference code", binding.referenceCodeVersion()),
                            result,
                            work
                        )) break;
                    }
                    if (
                        binding.measurementUnitId() != null &&
                        positive(binding.measurementUnitVersion())
                    ) {
                        if (!collectStandard(
                            source,
                            StandardOwnerKey.measurementUnit(binding.measurementUnitId(), binding.measurementUnitVersion()),
                            standardLabel(binding.fieldName(), "Measurement unit", binding.measurementUnitVersion()),
                            result,
                            work
                        )) break;
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
                        (!result.indicatorIds.contains(indicatorId) && result.indicatorIds.size() >= MAX_NODES) ||
                        (!result.metricReferences.contains(key) && result.metricReferences.size() >= MAX_NODES)
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

    private static RelationshipGraph finish(
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

    private static RelationshipNode planNode(WarehousePlanHeader plan) {
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

    private static RelationshipNode modelNode(ModelSpecView model) {
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

    private static RelationshipNode dimensionNode(UUID planId, View definition) {
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

    private static RelationshipNode standardElementNode(UUID planId, MetadataStandardDto standard) {
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

    private static RelationshipNode referenceCodeNode(
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

    private static RelationshipNode measurementUnitNode(UUID planId, MeasurementUnitView unit) {
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

    private static RelationshipNode indicatorNode(UUID planId, IndicatorDto indicator) {
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

    private static boolean matchesQuery(RelationshipNode node, String query) {
        return (
            query == null ||
            node.label().toLowerCase(Locale.ROOT).contains(query) ||
            node.id().toLowerCase(Locale.ROOT).contains(query)
        );
    }

    private static String modelLabel(ModelSpecView model) {
        return firstText(model.name(), model.id().toString());
    }

    private static String indicatorLabel(IndicatorDto indicator) {
        return firstText(indicator.getName(), indicator.getCode(), indicator.getId().toString());
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

    private static NodeKind parseKind(String value) {
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

    private static int requireLimit(int limit) {
        if (limit < 1 || limit > MAX_NODES) {
            throw new WarehousePlanException(
                "RELATIONSHIP_GRAPH_LIMIT_INVALID",
                "Relationship graph limit must be between 1 and 500",
                null
            );
        }
        return limit;
    }

    private static GraphCursor parseCursor(
        String cursor,
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        if (cursor == null) return null;
        try {
            if (cursor.isEmpty() || cursor.length() > MAX_CURSOR_LENGTH || !isBase64Url(cursor)) {
                throw new IllegalArgumentException("invalid cursor envelope");
            }
            String raw = decodeBase64Url(cursor);
            String[] parts = raw.split("\\.", -1);
            if (parts.length != 5 || !CURSOR_VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("unsupported cursor version");
            }
            String unsigned = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            if (
                !isLowerHex(parts[3]) ||
                !isLowerHex(parts[4]) ||
                !constantTimeEquals(sha256(unsigned), parts[4])
            ) {
                throw new IllegalArgumentException("invalid cursor integrity");
            }
            String expectedScope = cursorScope(tenantId, planId, activeDepartmentId, kind, query);
            if (!constantTimeEquals(expectedScope, parts[3])) {
                throw new IllegalArgumentException("cursor scope mismatch");
            }
            UUID rootAfter = parts[1].isEmpty() ? null : parseCanonicalUuid(parts[1]);
            String nodeAfter = parts[2].isEmpty() ? null : decodeBase64Url(parts[2]);
            if (
                (rootAfter == null && nodeAfter == null) ||
                (nodeAfter != null && !validCursorNodeId(nodeAfter, kind))
            ) {
                throw new IllegalArgumentException("invalid cursor position");
            }
            return new GraphCursor(rootAfter, nodeAfter);
        } catch (IllegalArgumentException invalid) {
            throw cursorInvalid();
        }
    }

    private static String encodeCursor(
        GraphCursor cursor,
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        String rootAfter = cursor.rootAfter() == null ? "" : cursor.rootAfter().toString();
        String nodeAfter = cursor.nodeAfter() == null ? "" : encodeBase64Url(cursor.nodeAfter());
        String unsigned = String.join(
            ".",
            CURSOR_VERSION,
            rootAfter,
            nodeAfter,
            cursorScope(tenantId, planId, activeDepartmentId, kind, query)
        );
        return encodeBase64Url(unsigned + "." + sha256(unsigned));
    }

    private static int cursorNodeStart(List<RelationshipNode> matchingNodes, GraphCursor cursor) {
        if (cursor == null || cursor.nodeAfter() == null) return 0;
        for (int index = 0; index < matchingNodes.size(); index++) {
            if (matchingNodes.get(index).id().equals(cursor.nodeAfter())) {
                return index + 1;
            }
        }
        throw cursorInvalid();
    }

    private static UUID parseCanonicalUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("non-canonical UUID");
        }
        return parsed;
    }

    private static boolean validCursorNodeId(String value, NodeKind kind) {
        if (
            value.isEmpty() ||
            value.length() > MAX_CURSOR_NODE_ID_LENGTH ||
            !value.equals(value.trim())
        ) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) return false;
        }
        if (kind != null) return value.startsWith(kind.name() + ":");
        for (NodeKind candidate : NodeKind.values()) {
            if (value.startsWith(candidate.name() + ":")) return true;
        }
        return false;
    }

    private static String cursorScope(
        String tenantId,
        UUID planId,
        String activeDepartmentId,
        NodeKind kind,
        String query
    ) {
        return sha256(
            cursorScopePart(tenantId) +
            cursorScopePart(planId.toString()) +
            cursorScopePart(activeDepartmentId) +
            cursorScopePart(kind == null ? null : kind.name()) +
            cursorScopePart(query)
        );
    }

    private static String cursorScopePart(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static String encodeBase64Url(String value) {
        return Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeBase64Url(String value) {
        if (!isBase64Url(value)) {
            throw new IllegalArgumentException("invalid base64url");
        }
        byte[] decoded = Base64.getUrlDecoder().decode(value);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(decoded).equals(value)) {
            throw new IllegalArgumentException("non-canonical base64url");
        }
        try {
            return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(decoded))
                .toString();
        } catch (CharacterCodingException invalid) {
            throw new IllegalArgumentException("invalid UTF-8", invalid);
        }
    }

    private static boolean isBase64Url(String value) {
        if (value.isEmpty()) return false;
        for (int index = 0; index < value.length(); index++) {
            char candidate = value.charAt(index);
            if (
                (candidate >= 'a' && candidate <= 'z') ||
                (candidate >= 'A' && candidate <= 'Z') ||
                (candidate >= '0' && candidate <= '9') ||
                candidate == '-' ||
                candidate == '_'
            ) continue;
            return false;
        }
        return true;
    }

    private static boolean isLowerHex(String value) {
        if (value.length() != 64) return false;
        for (int index = 0; index < value.length(); index++) {
            char candidate = value.charAt(index);
            if (
                (candidate >= '0' && candidate <= '9') ||
                (candidate >= 'a' && candidate <= 'f')
            ) continue;
            return false;
        }
        return true;
    }

    private static String sha256(String value) {
        try {
            return HexFormat
                .of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
                );
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.US_ASCII),
            right.getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static WarehousePlanException cursorInvalid() {
        return new WarehousePlanException(
            "RELATIONSHIP_GRAPH_CURSOR_INVALID",
            "Relationship graph cursor is invalid",
            null
        );
    }

    private static String nextHint(boolean truncated, String nextCursor) {
        if (nextCursor != null) return CURSOR_NEXT_HINT;
        return truncated ? FILTER_NEXT_HINT : null;
    }

    private static String normalizeQuery(String query) {
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

    private static int numericVersion(String value) {
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

    private static boolean valid(ModelRevisionRef reference) {
        return reference != null && reference.modelSpecId() != null && reference.revision() > 0;
    }

    private static boolean valid(DimensionDefinitionRef reference) {
        return (
            reference != null &&
            reference.dimensionDefinitionId() != null &&
            reference.revision() > 0
        );
    }

    private static <K, V> boolean putBounded(Map<K, V> target, K key, V value) {
        if (target.containsKey(key)) return true;
        if (target.size() >= MAX_NODES) return false;
        target.put(key, value);
        return true;
    }

    private static void putNode(Map<String, RelationshipNode> nodes, RelationshipNode node) {
        nodes.putIfAbsent(node.id(), node);
    }

    private static String nodeId(NodeKind kind, UUID id) {
        return kind.name() + ":" + id;
    }

    private static String modelNodeId(ModelRevisionKey key) {
        return nodeId(NodeKind.MODEL, key.id()) + "@" + key.revision();
    }

    private static String dimensionNodeId(DimensionRevisionKey key) {
        return nodeId(NodeKind.DIMENSION, key.id()) + "@" + key.revision();
    }

    private static String standardNodeId(StandardOwnerKey key) {
        return "STANDARD:" + key.type().name() + ":" + encode(key.ownerId()) + "@" + key.version();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ModelRevisionKey modelKey(ModelSpecView model) {
        return new ModelRevisionKey(model.id(), model.revision());
    }

    private static ModelRevisionKey modelKey(ModelRevisionRef reference) {
        return new ModelRevisionKey(reference.modelSpecId(), reference.revision());
    }

    private static DimensionRevisionKey dimensionKey(DimensionDefinitionRef reference) {
        return new DimensionRevisionKey(reference.dimensionDefinitionId(), reference.revision());
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record GraphCursor(UUID rootAfter, String nodeAfter) {}

    private record ModelRevisionKey(UUID id, int revision) {}

    private record DimensionRevisionKey(UUID id, int revision) {}

    private record ModelReferenceLink(
        ModelRevisionKey source,
        ModelRevisionRef target,
        EdgeKind kind,
        String label
    ) {}

    private record DimensionLink(ModelRevisionKey source, DimensionDefinitionRef reference) {}

    private enum StandardOwnerType {
        ELEMENT,
        REFERENCE_CODE,
        MEASUREMENT_UNIT,
    }

    private record StandardOwnerKey(StandardOwnerType type, String ownerId, int version) {
        private static StandardOwnerKey element(UUID id, int version) {
            return new StandardOwnerKey(StandardOwnerType.ELEMENT, id.toString(), version);
        }

        private static StandardOwnerKey referenceCode(String id, int version) {
            return new StandardOwnerKey(StandardOwnerType.REFERENCE_CODE, id.trim(), version);
        }

        private static StandardOwnerKey measurementUnit(UUID id, int version) {
            return new StandardOwnerKey(StandardOwnerType.MEASUREMENT_UNIT, id.toString(), version);
        }
    }

    private record StandardLink(ModelRevisionKey source, StandardOwnerKey owner, String label) {}

    private record MetricRevisionKey(UUID id, int version) {}

    private record MetricLink(ModelRevisionKey source, MetricRevisionKey reference) {}

    private record IndicatorOwners(
        Map<UUID, IndicatorDto> visible,
        List<IndicatorDependency> dependencies,
        boolean truncated
    ) {}

    private record EdgeKey(String source, String target, EdgeKind kind) {}

    private static final class ReferenceCollector {

        private final LinkedHashSet<ModelRevisionRef> modelReferences = new LinkedHashSet<>();
        private final List<ModelReferenceLink> modelLinks = new ArrayList<>();
        private final LinkedHashMap<DimensionRevisionKey, DimensionDefinitionRef> dimensionDefinitions =
            new LinkedHashMap<>();
        private final List<DimensionLink> dimensionLinks = new ArrayList<>();
        private final LinkedHashSet<StandardOwnerKey> standardOwners = new LinkedHashSet<>();
        private final List<StandardLink> standardLinks = new ArrayList<>();
        private final LinkedHashSet<UUID> indicatorIds = new LinkedHashSet<>();
        private final LinkedHashSet<MetricRevisionKey> metricReferences = new LinkedHashSet<>();
        private final List<MetricLink> metricLinks = new ArrayList<>();
        private boolean truncated;

        private void stop(WorkBudget work) {
            truncated = true;
            work.exhaust();
        }
    }

    private static final class WorkBudget {

        private int remaining;

        private WorkBudget(int remaining) {
            this.remaining = remaining;
        }

        private boolean tryConsume() {
            if (remaining < 1) return false;
            remaining--;
            return true;
        }

        private int remaining() {
            return remaining;
        }

        private boolean exhausted() {
            return remaining < 1;
        }

        private void exhaust() {
            remaining = 0;
        }
    }

    private static final class EdgeWork {

        private final WorkBudget budget;
        private boolean truncated;

        private EdgeWork(int limit) {
            this.budget = new WorkBudget(limit);
        }

        private boolean add(EdgeAccumulator edges, RelationshipEdge edge) {
            if (!budget.tryConsume()) {
                truncated = true;
                return false;
            }
            return edges.add(edge);
        }

        private boolean available() {
            return !truncated;
        }
    }

    private static final class EdgeAccumulator {

        private final Set<String> selectedNodeIds;
        private final Map<EdgeKey, RelationshipEdge> edges = new LinkedHashMap<>();
        private boolean truncated;

        private EdgeAccumulator(Set<String> selectedNodeIds) {
            this.selectedNodeIds = selectedNodeIds;
        }

        private boolean add(RelationshipEdge edge) {
            if (!selectedNodeIds.contains(edge.source()) || !selectedNodeIds.contains(edge.target())) {
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

        private List<RelationshipEdge> values() {
            return new ArrayList<>(edges.values());
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
