package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable API and candidate-fence contract for dependency-aware materialization planning. */
public final class ModelMaterializationPlanContract {

    public static final int MAX_REQUESTED_MODELS = 64;

    private ModelMaterializationPlanContract() {}

    public enum Strategy {
        WITH_MISSING_UPSTREAMS,
        CURRENT_ONLY,
    }

    public enum Action {
        BUILD,
        REUSE,
    }

    public enum DependencyRole {
        ROOT,
        UPSTREAM,
        DIMENSION,
        MIXED,
    }

    public record PreviewCommand(
        UUID planId,
        String environment,
        List<UUID> requestedModelSpecIds,
        Strategy strategy
    ) {
        public PreviewCommand {
            if (planId == null) throw new IllegalArgumentException("planId is required");
            if (environment == null || environment.isBlank() || environment.trim().length() > 64) {
                throw new IllegalArgumentException("environment is required and must not exceed 64 characters");
            }
            environment = environment.trim();
            strategy = strategy == null ? Strategy.WITH_MISSING_UPSTREAMS : strategy;
            requestedModelSpecIds = requestedModelSpecIds == null
                ? List.of()
                : requestedModelSpecIds
                    .stream()
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .toList();
            if (requestedModelSpecIds.isEmpty() || requestedModelSpecIds.size() > MAX_REQUESTED_MODELS) {
                throw new IllegalArgumentException("requestedModelSpecIds must contain between 1 and 64 models");
            }
        }
    }

    public record Blocker(
        String code,
        UUID modelSpecId,
        String message,
        Map<String, Object> details
    ) {
        public Blocker {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("blocker code is required");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("blocker message is required");
            details = Map.copyOf(details == null ? Map.of() : details);
        }
    }

    public record OrderedEntry(
        UUID modelSpecId,
        String modelName,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dependencyChecksum,
        Layer layer,
        DependencyRole dependencyRole,
        int topologyLevel,
        Action action,
        String reasonCode,
        UUID relationEvidenceId,
        String targetRelation
    ) {
        public OrderedEntry {
            Objects.requireNonNull(modelSpecId, "modelSpecId is required");
            Objects.requireNonNull(dependencyRole, "dependencyRole is required");
            Objects.requireNonNull(action, "action is required");
            if (modelRevision < 1 || implementationRevision < 1 || topologyLevel < 0) {
                throw new IllegalArgumentException("materialization plan revisions and topology level are invalid");
            }
        }

        public static final Comparator<OrderedEntry> STABLE_ORDER = Comparator
            .comparingInt(OrderedEntry::topologyLevel)
            .thenComparing(OrderedEntry::modelSpecId);
    }

    public record Preview(
        UUID planId,
        String environment,
        Strategy strategy,
        String planChecksum,
        boolean canStart,
        List<UUID> requestedModelSpecIds,
        List<OrderedEntry> orderedEntries,
        List<Blocker> blockers
    ) {
        public Preview {
            requestedModelSpecIds = List.copyOf(requestedModelSpecIds == null ? List.of() : requestedModelSpecIds);
            orderedEntries = List.copyOf(orderedEntries == null ? List.of() : orderedEntries);
            blockers = List.copyOf(blockers == null ? List.of() : blockers);
        }
    }
}
