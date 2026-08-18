package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.config.ModelMaterializationProperties;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.PlanResolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.ResolvedModel;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyRole;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Action;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Blocker;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.OrderedEntry;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Preview;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.PreviewCommand;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanContract.Strategy;
import com.yuzhi.dts.platform.service.modeling.ModelMaterializationPlanRelationPort.RelationObservation;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ScopeEntryCommand;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Server-owned BUILD/REUSE planner layered on the canonical F6 dependency snapshot. */
@Service
public class ModelMaterializationPlanService {

    public static final String STALE_ERROR_CODE = "MODEL_MATERIALIZATION_PLAN_STALE";
    public static final String BLOCKED_ERROR_CODE = "MODEL_MATERIALIZATION_PLAN_BLOCKED";

    private final ModelImplementationDependencyService dependencies;
    private final ModelMaterializationPlanRelationPort relations;
    private final ModelMaterializationProperties properties;

    public ModelMaterializationPlanService(
        ModelImplementationDependencyService dependencies,
        ModelMaterializationPlanRelationPort relations,
        ModelMaterializationProperties properties
    ) {
        this.dependencies = Objects.requireNonNull(dependencies, "dependencies is required");
        this.relations = Objects.requireNonNull(relations, "relations is required");
        this.properties = Objects.requireNonNull(properties, "properties is required");
    }

    @Transactional(readOnly = true)
    public Preview preview(String tenantId, PreviewCommand command) {
        Objects.requireNonNull(command, "command is required");
        if (!properties.isEnabled()) {
            return blocked(
                command,
                new Blocker(
                    "MODEL_MATERIALIZATION_DISABLED",
                    null,
                    "Materialization is disabled on this server",
                    Map.of()
                )
            );
        }
        String executionTargetKey = requiredTarget(properties.getExecutionTargetKey(), "executionTargetKey");
        String adapter = requiredTarget(properties.getAdapter(), "adapter").toLowerCase(Locale.ROOT);
        PlanResolution resolution;
        try {
            resolution = dependencies.resolvePlan(tenantId, command.planId(), command.requestedModelSpecIds());
        } catch (ModelSpecException failure) {
            return blocked(
                command,
                new Blocker(failure.code(), modelId(failure.details()), failure.getMessage(), details(failure.details()))
            );
        }

        List<Snapshot> snapshots = resolution
            .models()
            .values()
            .stream()
            .map(ResolvedModel::dependencySnapshot)
            .sorted(java.util.Comparator.comparing(Snapshot::modelSpecId))
            .toList();
        Map<UUID, RelationObservation> observations = relations.findLatest(
            tenantId,
            command.planId(),
            command.environment(),
            executionTargetKey,
            adapter,
            snapshots
        );
        Set<UUID> requested = Set.copyOf(command.requestedModelSpecIds());
        Set<UUID> included = requiredNodes(resolution, requested, observations, executionTargetKey, adapter);
        Map<UUID, Set<DependencyRole>> roles = dependencyRoles(resolution, requested, included);
        Map<UUID, Integer> levels = topologyLevels(resolution, included);
        List<Blocker> blockers = new ArrayList<>();
        List<OrderedEntry> entries = resolution
            .models()
            .values()
            .stream()
            .filter(node -> included.contains(node.model().id()))
            .map(node -> {
                Snapshot snapshot = node.dependencySnapshot();
                RelationObservation observation = observations.get(node.model().id());
                boolean root = requested.contains(node.model().id());
                boolean reusable = !root && reusable(snapshot, observation, executionTargetKey, adapter);
                Action action = reusable ? Action.REUSE : Action.BUILD;
                if (!root && action == Action.BUILD && command.strategy() == Strategy.CURRENT_ONLY) {
                    blockers.add(
                        new Blocker(
                            "MODEL_MATERIALIZATION_UPSTREAM_NOT_CURRENT",
                            node.model().id(),
                            "CURRENT_ONLY requires every upstream relation to match the current immutable pins",
                            Map.of("revision", node.model().revision())
                        )
                    );
                }
                return new OrderedEntry(
                    node.model().id(),
                    node.model().name(),
                    snapshot.modelRevision(),
                    snapshot.modelChecksum(),
                    snapshot.implementationRevision(),
                    snapshot.implementationChecksum(),
                    snapshot.dependencyChecksum(),
                    node.model().layer(),
                    role(node.model().id(), requested, roles),
                    levels.getOrDefault(node.model().id(), 0),
                    action,
                    root
                        ? "REQUESTED_MODEL"
                        : reusable ? "EXACT_VERIFIED_RELATION" : "UPSTREAM_MATERIALIZATION_REQUIRED",
                    reusable ? observation.relationEvidenceId() : null,
                    reusable ? observation.targetRelation() : null
                );
            })
            .sorted(OrderedEntry.STABLE_ORDER)
            .toList();
        List<Blocker> stableBlockers = blockers
            .stream()
            .sorted(java.util.Comparator.comparing(Blocker::code).thenComparing(blocker -> Objects.toString(blocker.modelSpecId(), "")))
            .toList();
        String checksum = checksum(command, entries, stableBlockers, observations);
        return new Preview(
            command.planId(),
            command.environment(),
            command.strategy(),
            checksum,
            stableBlockers.isEmpty(),
            command.requestedModelSpecIds(),
            entries,
            stableBlockers
        );
    }

    @Transactional(readOnly = true)
    public ValidatedPlan requireCurrent(String tenantId, PreviewCommand command, String expectedChecksum) {
        if (expectedChecksum == null || !expectedChecksum.matches("^[0-9a-f]{64}$")) {
            throw new ModelReleaseCandidateException(
                STALE_ERROR_CODE,
                "A valid materialization plan checksum is required",
                Kind.PRECONDITION_REQUIRED,
                Map.of("planId", command.planId())
            );
        }
        Preview current = preview(tenantId, command);
        if (!Objects.equals(expectedChecksum, current.planChecksum())) {
            throw new ModelReleaseCandidateException(
                STALE_ERROR_CODE,
                "Materialization dependencies or relation evidence changed after preview",
                Kind.CONFLICT,
                Map.of("expectedChecksum", expectedChecksum, "currentChecksum", current.planChecksum())
            );
        }
        if (!current.canStart()) {
            throw new ModelReleaseCandidateException(
                BLOCKED_ERROR_CODE,
                "Materialization plan contains one or more blockers",
                Kind.UNPROCESSABLE,
                current.blockers()
            );
        }
        List<ScopeEntryCommand> buildEntries = current
            .orderedEntries()
            .stream()
            .filter(entry -> entry.action() == Action.BUILD)
            .map(entry ->
                new ScopeEntryCommand(
                    entry.modelSpecId(),
                    0,
                    entry.dependencyRole() == ModelMaterializationPlanContract.DependencyRole.ROOT
                        ? "MATERIALIZATION_ROOT"
                        : "AUTO_DEPENDENCY"
                )
            )
            .toList();
        List<ScopeEntryCommand> ordered = new ArrayList<>(buildEntries.size());
        for (int index = 0; index < buildEntries.size(); index++) {
            ScopeEntryCommand entry = buildEntries.get(index);
            ordered.add(new ScopeEntryCommand(entry.modelSpecId(), index, entry.selectedReason()));
        }
        return new ValidatedPlan(current, ordered);
    }

    private static Set<UUID> requiredNodes(
        PlanResolution resolution,
        Set<UUID> requested,
        Map<UUID, RelationObservation> observations,
        String executionTargetKey,
        String adapter
    ) {
        LinkedHashSet<UUID> included = new LinkedHashSet<>();
        requested.stream().sorted().forEach(root -> includeRequired(
                root,
                true,
                resolution,
                observations,
                executionTargetKey,
                adapter,
                included
            ));
        return Set.copyOf(included);
    }

    private static void includeRequired(
        UUID modelSpecId,
        boolean root,
        PlanResolution resolution,
        Map<UUID, RelationObservation> observations,
        String executionTargetKey,
        String adapter,
        Set<UUID> included
    ) {
        if (!included.add(modelSpecId)) return;
        ResolvedModel node = resolution.models().get(modelSpecId);
        if (node == null) return;
        if (!root && reusable(node.dependencySnapshot(), observations.get(modelSpecId), executionTargetKey, adapter)) {
            return;
        }
        node
            .dependencySnapshot()
            .modelInputs()
            .stream()
            .map(ModelInput::modelSpecId)
            .distinct()
            .sorted()
            .forEach(upstream -> includeRequired(
                    upstream,
                    false,
                    resolution,
                    observations,
                    executionTargetKey,
                    adapter,
                    included
                ));
    }

    private static Map<UUID, Set<DependencyRole>> dependencyRoles(
        PlanResolution resolution,
        Set<UUID> requested,
        Set<UUID> included
    ) {
        Map<UUID, Set<DependencyRole>> roles = new LinkedHashMap<>();
        requested.forEach(id -> roles.put(id, new LinkedHashSet<>()));
        resolution
            .models()
            .values()
            .stream()
            .filter(owner -> included.contains(owner.model().id()))
            .forEach(owner -> owner
                .dependencySnapshot()
                .modelInputs()
                .stream()
                .filter(input -> included.contains(input.modelSpecId()))
                .forEach(input -> roles.computeIfAbsent(input.modelSpecId(), ignored -> new LinkedHashSet<>()).add(input.role())));
        return roles;
    }

    private static ModelMaterializationPlanContract.DependencyRole role(
        UUID modelSpecId,
        Set<UUID> requested,
        Map<UUID, Set<DependencyRole>> roles
    ) {
        if (requested.contains(modelSpecId)) return ModelMaterializationPlanContract.DependencyRole.ROOT;
        Set<DependencyRole> usages = roles.getOrDefault(modelSpecId, Set.of());
        if (usages.size() > 1) return ModelMaterializationPlanContract.DependencyRole.MIXED;
        return usages.contains(DependencyRole.DIMENSION)
            ? ModelMaterializationPlanContract.DependencyRole.DIMENSION
            : ModelMaterializationPlanContract.DependencyRole.UPSTREAM;
    }

    private static Map<UUID, Integer> topologyLevels(PlanResolution resolution, Set<UUID> included) {
        Map<UUID, List<UUID>> upstreams = new LinkedHashMap<>();
        resolution
            .models()
            .values()
            .stream()
            .filter(owner -> included.contains(owner.model().id()))
            .forEach(owner -> upstreams.put(
                    owner.model().id(),
                    owner
                        .dependencySnapshot()
                        .modelInputs()
                        .stream()
                        .map(ModelInput::modelSpecId)
                        .filter(included::contains)
                        .distinct()
                        .sorted()
                        .toList()
                ));
        Map<UUID, Integer> result = new HashMap<>();
        included.forEach(id -> topologyLevel(id, upstreams, new HashSet<>(), result));
        return result;
    }

    private static int topologyLevel(
        UUID modelSpecId,
        Map<UUID, List<UUID>> upstreams,
        Set<UUID> path,
        Map<UUID, Integer> memo
    ) {
        Integer cached = memo.get(modelSpecId);
        if (cached != null) return cached;
        if (!path.add(modelSpecId)) return 0;
        int level = 0;
        for (UUID upstream : upstreams.getOrDefault(modelSpecId, List.of())) {
            level = Math.max(level, topologyLevel(upstream, upstreams, path, memo) + 1);
        }
        path.remove(modelSpecId);
        memo.put(modelSpecId, level);
        return level;
    }

    private static boolean reusable(
        Snapshot snapshot,
        RelationObservation observation,
        String executionTargetKey,
        String adapter
    ) {
        return observation != null &&
            observation.verified() &&
            observation.relationExists() &&
            snapshot.modelRevision() == observation.modelRevision() &&
            snapshot.implementationRevision() == observation.implementationRevision() &&
            Objects.equals(snapshot.modelChecksum(), observation.modelChecksum()) &&
            Objects.equals(snapshot.implementationChecksum(), observation.implementationChecksum()) &&
            Objects.equals(executionTargetKey, observation.executionTargetKey()) &&
            adapter.equalsIgnoreCase(Objects.toString(observation.adapter(), "")) &&
            observation.relationEvidenceId() != null &&
            observation.evidenceChecksum() != null &&
            observation.evidenceChecksum().matches("^[0-9a-f]{64}$") &&
            observation.targetRelation() != null;
    }

    private static String checksum(
        PreviewCommand command,
        List<OrderedEntry> entries,
        List<Blocker> blockers,
        Map<UUID, RelationObservation> observations
    ) {
        StringBuilder canonical = new StringBuilder("materialization-plan-v1\n")
            .append(command.planId()).append('\n')
            .append(command.environment()).append('\n')
            .append(command.strategy()).append('\n');
        command.requestedModelSpecIds().forEach(id -> canonical.append("root\t").append(id).append('\n'));
        entries.forEach(entry -> {
            RelationObservation observation = observations.get(entry.modelSpecId());
            canonical
                .append("entry\t")
                .append(entry.modelSpecId()).append('\t')
                .append(entry.modelRevision()).append('\t')
                .append(entry.modelChecksum()).append('\t')
                .append(entry.implementationRevision()).append('\t')
                .append(entry.implementationChecksum()).append('\t')
                .append(entry.dependencyChecksum()).append('\t')
                .append(entry.dependencyRole()).append('\t')
                .append(entry.topologyLevel()).append('\t')
                .append(entry.action()).append('\t')
                .append(observation == null ? "-" : Objects.toString(observation.relationEvidenceId(), "-"))
                .append('\t')
                .append(observation == null ? "-" : Objects.toString(observation.evidenceChecksum(), "-"))
                .append('\n');
        });
        blockers.forEach(blocker -> canonical
            .append("blocker\t")
            .append(blocker.code()).append('\t')
            .append(Objects.toString(blocker.modelSpecId(), "-")).append('\n'));
        return sha256(canonical.toString());
    }

    private static Preview blocked(PreviewCommand command, Blocker blocker) {
        String checksum = sha256(
            "materialization-plan-v1\n" +
            command.planId() + "\n" +
            command.environment() + "\n" +
            command.strategy() + "\n" +
            blocker.code() + "\n" +
            Objects.toString(blocker.modelSpecId(), "-")
        );
        return new Preview(
            command.planId(),
            command.environment(),
            command.strategy(),
            checksum,
            false,
            command.requestedModelSpecIds(),
            List.of(),
            List.of(blocker)
        );
    }

    private static String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String requiredTarget(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new ModelReleaseCandidateException(
                "MODEL_MATERIALIZATION_TARGET_UNAVAILABLE",
                "The server-owned materialization target is unavailable",
                Kind.UNPROCESSABLE,
                Map.of("field", field)
            );
        }
        return value.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> details(Object details) {
        return details instanceof Map<?, ?> map
            ? (Map<String, Object>) map
            : details == null ? Map.of() : Map.of("evidence", details.toString());
    }

    private static UUID modelId(Object details) {
        if (!(details instanceof Map<?, ?> map)) return null;
        Object value = map.get("modelSpecId");
        if (value instanceof UUID id) return id;
        try {
            return value == null ? null : UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public record ValidatedPlan(Preview preview, List<ScopeEntryCommand> buildEntries) {
        public ValidatedPlan {
            buildEntries = List.copyOf(buildEntries == null ? List.of() : buildEntries);
        }
    }
}
