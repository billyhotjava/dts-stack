package com.yuzhi.dts.platform.service.modeling.warehouse;

import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.MAX_NODES;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.collectIncomingModelLinks;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.modelKey;
import static com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.nullToEmpty;

import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.ModelRevisionKey;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.ReferenceCollector;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanRelationshipGraphProjectionSupport.WorkBudget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Assembles authorized incoming model context without adding it to cursor-owned candidates. */
final class WarehousePlanRelationshipGraphInboundModelSupport {

    private static final int SOURCE_LOOKAHEAD = MAX_NODES + 1;

    private WarehousePlanRelationshipGraphInboundModelSupport() {}

    static InboundModelProjection load(
        WarehousePlanRelationshipGraphInboundModelReader reader,
        ModelSpecApplicationService modelSpecs,
        String tenantId,
        UUID planId,
        UUID afterId,
        Map<ModelRevisionKey, ModelSpecView> rootModels,
        ReferenceCollector references,
        WorkBudget work
    ) {
        Set<ModelRevisionKey> canonicalRootKeys = new LinkedHashSet<>(
            rootModels.keySet()
        );
        if (reader == null) {
            return new InboundModelProjection(
                canonicalRootKeys,
                List.of(),
                false
            );
        }
        List<ModelRevisionRef> unresolvedRootRefs = references
            .modelReferences
            .stream()
            .filter(reference ->
                !rootModels.containsKey(modelKey(reference))
            )
            .toList();
        for (ModelRevisionRef target : reader.listCurrentTargets(
            tenantId,
            planId,
            unresolvedRootRefs
        )) {
            if (
                target != null &&
                references.modelReferences.contains(target)
            ) {
                canonicalRootKeys.add(modelKey(target));
            }
        }
        if (afterId == null || rootModels.isEmpty()) {
            return new InboundModelProjection(
                canonicalRootKeys,
                List.of(),
                false
            );
        }

        List<ModelRevisionRef> incomingSourceRefs = nullToEmpty(
            reader.listEarlierSources(
                tenantId,
                planId,
                afterId,
                references(rootModels.keySet()),
                SOURCE_LOOKAHEAD
            )
        );
        boolean truncated = incomingSourceRefs.size() > MAX_NODES;
        if (truncated) {
            incomingSourceRefs = incomingSourceRefs.subList(
                0,
                MAX_NODES
            );
        }
        if (incomingSourceRefs.isEmpty()) {
            return new InboundModelProjection(
                canonicalRootKeys,
                List.of(),
                truncated
            );
        }

        Set<ModelRevisionRef> requestedSources = new LinkedHashSet<>(
            incomingSourceRefs
        );
        List<ModelSpecView> incomingSources = new ArrayList<>();
        for (ModelSpecView source : nullToEmpty(
            modelSpecs.revisionsForRelationshipGraph(
                tenantId,
                incomingSourceRefs,
                MAX_NODES
            )
        )) {
            if (
                source == null ||
                source.id() == null ||
                source.revision() < 1 ||
                !Objects.equals(planId, source.planId()) ||
                !requestedSources.contains(
                    new ModelRevisionRef(
                        source.id(),
                        source.revision()
                    )
                )
            ) continue;
            incomingSources.add(source);
        }
        truncated |= collectIncomingModelLinks(
            incomingSources,
            rootModels.keySet(),
            references,
            work
        );
        return new InboundModelProjection(
            Set.copyOf(canonicalRootKeys),
            List.copyOf(incomingSources),
            truncated
        );
    }

    private static List<ModelRevisionRef> references(
        Collection<ModelRevisionKey> keys
    ) {
        return keys
            .stream()
            .map(key ->
                new ModelRevisionRef(key.id(), key.revision())
            )
            .toList();
    }

    record InboundModelProjection(
        Set<ModelRevisionKey> canonicalRootKeys,
        List<ModelSpecView> sources,
        boolean truncated
    ) {}
}
