package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read-only, bounded projection for the canonical ModelSpec implementation dependency graph. */
public interface ModelImplementationDependencyReadPort {
    DependencyFacts readFacts(String tenantId, ModelSpecView owner);

    /**
     * Loads one bounded, revision-pinned closure for all requested current roots. Implementations
     * are returned with the same query snapshot so a planner never joins per-node reads.
     */
    default PlanFacts readPlanFacts(String tenantId, UUID planId, List<UUID> requestedModelSpecIds) {
        throw new UnsupportedOperationException("Batch dependency planning is unavailable");
    }

    record PlanFacts(DependencyFacts dependencies, Map<UUID, ImplementationView> implementations) {
        public PlanFacts {
            dependencies = dependencies == null ? new DependencyFacts(List.of(), List.of()) : dependencies;
            implementations = Map.copyOf(implementations == null ? Map.of() : implementations);
        }
    }
}
