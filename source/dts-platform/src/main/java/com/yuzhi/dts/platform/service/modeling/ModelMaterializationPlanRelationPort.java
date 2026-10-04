package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Bounded read port for latest target-environment physical relation observations. */
public interface ModelMaterializationPlanRelationPort {
    Map<UUID, RelationObservation> findLatest(
        String tenantId,
        UUID planId,
        String environment,
        String executionTargetKey,
        String adapter,
        List<Snapshot> snapshots
    );

    record RelationObservation(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        UUID relationEvidenceId,
        String evidenceChecksum,
        boolean verified,
        boolean relationExists,
        String executionTargetKey,
        String adapter,
        String databaseName,
        String schemaName,
        String identifier
    ) {
        public String targetRelation() {
            if (databaseName == null || schemaName == null || identifier == null) return null;
            return databaseName + "." + schemaName + "." + identifier;
        }
    }
}
