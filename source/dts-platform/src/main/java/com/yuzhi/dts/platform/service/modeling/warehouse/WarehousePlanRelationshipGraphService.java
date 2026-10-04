package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphCursorCodec.cursorInvalid;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphCursorCodec.runtimeSpecSigningKey;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.*;

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
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.MetadataStandardService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanApplicationService.WarehousePlanException;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.WarehousePlanHeader;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.EdgeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.NodeKind;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipEdge;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipGraph;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphContract.RelationshipNode;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphCursorCodec.GraphCursor;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphInboundModelSupport.InboundModelProjection;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.DimensionLink;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.DimensionRevisionKey;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.EdgeAccumulator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.EdgeWork;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.IndicatorOwners;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.MetricLink;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.MetricRevisionKey;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.ModelReferenceLink;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.ModelRevisionKey;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.ReferenceCollector;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.StandardLink;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.StandardOwnerKey;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.WorkBudget;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

    static final int MAX_NODES = WarehousePlanRelationshipGraphProjectionSupport.MAX_NODES;
    static final int MAX_EDGES = WarehousePlanRelationshipGraphProjectionSupport.MAX_EDGES;
    static final int MAX_REFERENCE_WORK =
        WarehousePlanRelationshipGraphProjectionSupport.MAX_REFERENCE_WORK;
    private static final int SOURCE_LOOKAHEAD = MAX_NODES + 1;

    private final ModelSpecApplicationService modelSpecs;
    private final DimensionDefinitionApplicationService dimensionDefinitions;
    private final MetadataStandardService metadataStandards;
    private final IndicatorService indicators;
    private final ReferenceCodeService referenceCodes;
    private final MeasurementUnitApplicationService measurementUnits;
    private final WarehousePlanRelationshipGraphInboundModelReader inboundModels;
    private final WarehousePlanRelationshipGraphCursorCodec cursorCodec;

    public WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits,
        ModelMaterializationProperties materializationProperties
    ) {
        this(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            referenceCodes,
            measurementUnits,
            null,
            runtimeSpecSigningKey(materializationProperties)
        );
    }

    @Autowired
    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits,
        WarehousePlanRelationshipGraphInboundModelReader inboundModels,
        ModelMaterializationProperties materializationProperties
    ) {
        this(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            referenceCodes,
            measurementUnits,
            inboundModels,
            runtimeSpecSigningKey(materializationProperties)
        );
    }

    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits,
        String cursorSigningSecret
    ) {
        this(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            referenceCodes,
            measurementUnits,
            null,
            cursorSigningSecret
        );
    }

    private WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        ReferenceCodeService referenceCodes,
        MeasurementUnitApplicationService measurementUnits,
        WarehousePlanRelationshipGraphInboundModelReader inboundModels,
        String cursorSigningSecret
    ) {
        this.modelSpecs = modelSpecs;
        this.dimensionDefinitions = dimensionDefinitions;
        this.metadataStandards = metadataStandards;
        this.indicators = indicators;
        this.referenceCodes = referenceCodes;
        this.measurementUnits = measurementUnits;
        this.inboundModels = inboundModels;
        this.cursorCodec = new WarehousePlanRelationshipGraphCursorCodec(
            cursorSigningSecret
        );
    }

    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionApplicationService dimensionDefinitions,
        MetadataStandardService metadataStandards,
        IndicatorService indicators,
        String cursorSigningSecret
    ) {
        this(
            modelSpecs,
            dimensionDefinitions,
            metadataStandards,
            indicators,
            null,
            null,
            cursorSigningSecret
        );
    }

    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        WarehousePlanRelationshipGraphInboundModelReader inboundModels,
        String cursorSigningSecret
    ) {
        this(
            modelSpecs,
            null,
            null,
            null,
            null,
            null,
            inboundModels,
            cursorSigningSecret
        );
    }

    WarehousePlanRelationshipGraphService(
        ModelSpecApplicationService modelSpecs,
        String cursorSigningSecret
    ) {
        this(
            modelSpecs,
            null,
            null,
            null,
            null,
            null,
            cursorSigningSecret
        );
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
        GraphCursor graphCursor = cursorCodec.parse(
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
        Map<String, RelationshipNode> visibleNodes = new LinkedHashMap<>(
            candidates
        );
        putNode(visibleNodes, planNode(plan));

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
                    putNode(visibleNodes, modelNode(model));
                }
            }
        }
        InboundModelProjection inboundProjection =
            WarehousePlanRelationshipGraphInboundModelSupport.load(
                kindFilter == null || kindFilter == NodeKind.MODEL
                    ? inboundModels
                    : null,
                modelSpecs,
                tenantId,
                plan.id(),
                afterId,
                rootModels,
                references,
                referenceWork
            );
        Set<ModelRevisionKey> canonicalRootKeys =
            inboundProjection.canonicalRootKeys();
        projectionIncomplete |= inboundProjection.truncated();
        for (ModelSpecView source : inboundProjection.sources()) {
            visibleModels.putIfAbsent(modelKey(source), source);
            putNode(visibleNodes, modelNode(source));
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
        projectionIncomplete |= indicatorOwners.truncated();
        candidates
            .values()
            .forEach(node -> putNode(visibleNodes, node));

        List<RelationshipNode> matchingNodes = candidates
            .values()
            .stream()
            .filter(node -> kindFilter == null || node.kind() == kindFilter)
            .filter(node -> matchesQuery(node, query))
            .sorted(NODE_ORDER)
            .toList();
        String currentWindowFingerprint = cursorCodec.windowFingerprint(
            afterId,
            rootModels.values(),
            hasMoreRoots,
            matchingNodes
        );
        cursorCodec.requireCurrentWindow(cursor, currentWindowFingerprint);
        int selectedStart = cursorCodec.nodeStart(matchingNodes, cursor);
        int selectedEnd = Math.min(selectedStart + limit, matchingNodes.size());
        List<RelationshipNode> selectedNodes = matchingNodes.subList(selectedStart, selectedEnd);
        boolean hasMoreMatchingNodes = selectedEnd < matchingNodes.size();
        Set<String> selectedIds = selectedNodes
            .stream()
            .map(RelationshipNode::id)
            .collect(java.util.stream.Collectors.toSet());
        Set<String> eligibleIds = visibleNodes
            .values()
            .stream()
            .filter(node ->
                kindFilter == null || node.kind() == kindFilter
            )
            .filter(node -> matchesQuery(node, query))
            .map(RelationshipNode::id)
            .collect(
                java.util.stream.Collectors.toCollection(
                    LinkedHashSet::new
                )
            );

        EdgeAccumulator edges = new EdgeAccumulator(selectedIds, eligibleIds);
        EdgeWork edgeWork = new EdgeWork(MAX_REFERENCE_WORK);
        String planNodeId = nodeId(NodeKind.PLAN, plan.id());
        for (ModelSpecView root : rootModels.values()) {
            String targetNodeId = modelNodeId(modelKey(root));
            if (
                !edgeWork.add(
                    edges,
                    new RelationshipEdge(planNodeId, targetNodeId, EdgeKind.CONTAINS, "Plan model"),
                    targetNodeId
                )
            ) {
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
                        ),
                        modelLinkOwnerNodeId(
                            link,
                            canonicalRootKeys
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
                        ),
                        modelNodeId(link.source())
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
                        ),
                        modelNodeId(link.source())
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (MetricLink link : references.metricLinks) {
                IndicatorDto indicator = indicatorOwners.visible().get(link.reference().id());
                if (indicator == null || numericVersion(indicator.getVersion()) != link.reference().version()) continue;
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            modelNodeId(link.source()),
                            nodeId(NodeKind.INDICATOR, link.reference().id()),
                            EdgeKind.INDICATOR_REFERENCE,
                            "Indicator reference v" + link.reference().version()
                        ),
                        modelNodeId(link.source())
                    )
                ) break;
            }
        }
        if (edgeWork.available()) {
            for (IndicatorDependency dependency : indicatorOwners.dependencies()) {
                if (
                    !edgeWork.add(
                        edges,
                        new RelationshipEdge(
                            nodeId(NodeKind.INDICATOR, dependency.sourceId()),
                            nodeId(NodeKind.INDICATOR, dependency.targetId()),
                            EdgeKind.INDICATOR_DEPENDS_ON,
                            "Indicator dependency"
                        ),
                        nodeId(NodeKind.INDICATOR, dependency.sourceId())
                    )
                ) break;
            }
        }
        projectionIncomplete |= edgeWork.truncated || edges.truncated();
        boolean truncated = projectionIncomplete || hasMoreMatchingNodes || hasMoreRoots;
        String nextCursor = null;
        if (!projectionIncomplete) {
            if (hasMoreMatchingNodes) {
                nextCursor = cursorCodec.encode(
                    new GraphCursor(
                        afterId,
                        selectedNodes.getLast().id(),
                        currentWindowFingerprint
                    ),
                    tenantId,
                    plan.id(),
                    activeDepartmentId,
                    kindFilter,
                    query
                );
            } else if (hasMoreRoots && rootWindowEnd != null) {
                nextCursor = cursorCodec.encode(
                    new GraphCursor(rootWindowEnd, null, null),
                    tenantId,
                    plan.id(),
                    activeDepartmentId,
                    kindFilter,
                    query
                );
            }
        }
        List<RelationshipEdge> selectedEdges = edges
            .values()
            .stream()
            .sorted(EDGE_ORDER)
            .limit(MAX_EDGES)
            .toList();
        List<RelationshipNode> responseNodes = selfContainedPageNodes(
            selectedNodes,
            selectedEdges,
            visibleNodes
        );
        return new RelationshipGraph(
            plan.id(),
            responseNodes,
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
        if (indicators == null || (kindFilter != null && kindFilter != NodeKind.INDICATOR)) {
            return new IndicatorOwners(Map.of(), List.of(), false);
        }
        boolean catalogProjection = references.indicatorIds.isEmpty();
        int dependencyLimit = Math.min(MAX_EDGES, Math.max(1, referenceWork.remaining()));
        RelationshipGraphProjection projection = indicators.projectForRelationshipGraph(
            catalogProjection ? Set.of() : references.indicatorIds,
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
        if (catalogProjection) {
            visible.putAll(authorized);
        } else {
            for (MetricRevisionKey reference : references.metricReferences) {
                IndicatorDto indicator = authorized.get(reference.id());
                if (indicator != null && numericVersion(indicator.getVersion()) == reference.version()) {
                    visible.putIfAbsent(reference.id(), indicator);
                }
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
                    .comparing(
                        WarehousePlanRelationshipGraphProjectionSupport::indicatorLabel,
                        String.CASE_INSENSITIVE_ORDER
                    )
                    .thenComparing(indicator -> indicator.getId().toString())
            )
            .map(indicator -> indicatorNode(planId, indicator))
            .forEach(node -> putNode(candidates, node));
        return new IndicatorOwners(visible, List.copyOf(dependencies), truncated);
    }
}
