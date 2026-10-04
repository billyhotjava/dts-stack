package com.yuzhi.dts.platform.service.modeling.imports.apply;

import java.util.List;
import java.util.UUID;

/**
 * Immutable hand-off from preview to apply. JSON projections are canonical strings so callers
 * cannot mutate the persisted preview state while preparing an apply command.
 */
public final class ModelSpecImportApplyPlanContract {

    public static final String APPLY_PAYLOAD_SCHEMA_VERSION = "dts.model-import.apply-payload/v1";

    private ModelSpecImportApplyPlanContract() {}

    public record ApplyPlan(
        UUID runId,
        UUID planId,
        String previewHash,
        String packageChecksum,
        String applyPayloadChecksum,
        List<String> topology,
        List<Candidate> candidates
    ) {
        public ApplyPlan {
            topology = topology == null ? List.of() : List.copyOf(topology);
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
        }
    }

    public record Candidate(
        String dbtUniqueId,
        UUID targetModelSpecId,
        int targetRevision,
        int targetImplementationRevision,
        int expectedModelRevision,
        String expectedModelChecksum,
        String expectedModelStatus,
        int expectedImplementationRevision,
        String expectedImplementationChecksum,
        String proposedModelSpecChecksum,
        String proposedImplementationChecksum,
        String action,
        String conversionMode,
        String modelSpecJson,
        String implementationJson,
        String artifactJson,
        String evidenceJson,
        String dependencyPinsJson,
        String sourcePinsJson,
        String incomingExternalChecksum,
        List<String> dependencyUniqueIds
    ) {
        public Candidate {
            dependencyUniqueIds = dependencyUniqueIds == null ? List.of() : List.copyOf(dependencyUniqueIds);
        }

        /** Compatibility constructor for frozen v1 apply plans created before reconciliation checkpoints. */
        public Candidate(
            String dbtUniqueId,
            UUID targetModelSpecId,
            int targetRevision,
            int targetImplementationRevision,
            int expectedModelRevision,
            String expectedModelChecksum,
            String expectedModelStatus,
            int expectedImplementationRevision,
            String expectedImplementationChecksum,
            String proposedModelSpecChecksum,
            String proposedImplementationChecksum,
            String action,
            String conversionMode,
            String modelSpecJson,
            String implementationJson,
            String artifactJson,
            String evidenceJson,
            String dependencyPinsJson,
            String sourcePinsJson
        ) {
            this(
                dbtUniqueId,
                targetModelSpecId,
                targetRevision,
                targetImplementationRevision,
                expectedModelRevision,
                expectedModelChecksum,
                expectedModelStatus,
                expectedImplementationRevision,
                expectedImplementationChecksum,
                proposedModelSpecChecksum,
                proposedImplementationChecksum,
                action,
                conversionMode,
                modelSpecJson,
                implementationJson,
                artifactJson,
                evidenceJson,
                dependencyPinsJson,
                sourcePinsJson,
                proposedImplementationChecksum,
                List.of()
            );
        }
    }
}
