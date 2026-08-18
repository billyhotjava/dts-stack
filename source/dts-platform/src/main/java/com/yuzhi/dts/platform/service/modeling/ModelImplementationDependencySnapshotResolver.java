package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves the canonical ModelSpec dependency graph and implementation evidence into one
 * deterministic, immutable snapshot. The resolver is deliberately storage-agnostic so ZIP,
 * manual designer and advanced dbt paths can share the exact same consistency rules.
 */
public final class ModelImplementationDependencySnapshotResolver {

    private static final Pattern PROJECT_KEY = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,127}$");
    private static final Comparator<ModelRevisionRef> REF_ORDER = Comparator
        .comparing(ModelRevisionRef::modelSpecId)
        .thenComparingInt(ModelRevisionRef::revision);

    public Snapshot resolve(
        ModelSpecView owner,
        ImplementationView implementation,
        String projectKey,
        DependencyFacts facts
    ) {
        requireOwnerPins(owner, implementation);
        String normalizedProjectKey = normalizedProjectKey(projectKey);
        DependencyFacts normalizedFacts = facts == null ? new DependencyFacts(List.of(), List.of()) : facts;
        Map<ModelRevisionRef, ModelFact> models = modelFacts(normalizedFacts.models());
        detectCycle(owner, models);

        List<PhysicalSource> physicalSources = physicalSources(
            owner,
            implementation,
            normalizedProjectKey,
            normalizedFacts.physicalSources()
        );
        List<ModelInput> modelInputs = modelInputs(owner, implementation, models);
        String dependencyChecksum = checksum(owner, physicalSources, modelInputs);
        return new Snapshot(
            owner.id(),
            owner.revision(),
            owner.checksum(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            physicalSources,
            modelInputs,
            dependencyChecksum
        );
    }

    public Reconciliation reconcileParsedDependencies(Snapshot snapshot, Collection<String> parsedDependencies) {
        Objects.requireNonNull(snapshot, "snapshot is required");
        Set<String> expected = new LinkedHashSet<>();
        snapshot.physicalSources().forEach(source -> expected.add(source.dbtSourceUniqueId()));
        snapshot.modelInputs().forEach(input -> expected.add(input.dbtUniqueId()));
        Set<String> parsed = new LinkedHashSet<>();
        (parsedDependencies == null ? List.<String>of() : parsedDependencies)
            .stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> value.startsWith("source.") || value.startsWith("model."))
            .sorted()
            .forEach(parsed::add);

        List<String> undeclared = parsed.stream().filter(value -> !expected.contains(value)).toList();
        if (!undeclared.isEmpty()) {
            throw failure(
                "MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED",
                "The implementation references dependencies that are not declared by ModelSpec",
                Map.of("dependencies", undeclared)
            );
        }
        List<String> missing = expected.stream().filter(value -> !parsed.contains(value)).sorted().toList();
        if (!missing.isEmpty()) {
            throw failure(
                "MODEL_IMPLEMENTATION_DEPENDENCY_MISSING",
                "The implementation does not use every required ModelSpec dependency",
                Map.of("dependencies", missing)
            );
        }
        return new Reconciliation(expected.stream().sorted().toList(), List.of(), List.of());
    }

    private static void requireOwnerPins(ModelSpecView owner, ImplementationView implementation) {
        if (owner == null || owner.id() == null || owner.revision() < 1 || !checksum(owner.checksum())) {
            throw failure(
                "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
                "The owning ModelSpec pin is unavailable",
                null
            );
        }
        if (
            implementation == null ||
            !Objects.equals(owner.id(), implementation.modelSpecId()) ||
            owner.revision() != implementation.revision() ||
            !Objects.equals(owner.checksum(), implementation.modelChecksum()) ||
            implementation.implementationRevision() < 1 ||
            !checksum(implementation.implementationChecksum()) ||
            !"ACTIVE".equals(implementation.status())
        ) {
            throw failure(
                "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
                "The owning implementation pin is unavailable",
                null
            );
        }
    }

    private static List<PhysicalSource> physicalSources(
        ModelSpecView owner,
        ImplementationView implementation,
        String projectKey,
        List<PhysicalSourceFact> facts
    ) {
        Map<UUID, SourceRef> declared = new LinkedHashMap<>();
        for (SourceRef source : safe(owner.sourceRefs())) {
            if (source == null || source.sourceBindingId() == null || source.resolvedVersion() == null) {
                throw failure("MODEL_SOURCE_BINDING_STALE", "A declared source binding is incomplete", null);
            }
            if (declared.putIfAbsent(source.sourceBindingId(), source) != null) {
                throw failure("MODEL_SOURCE_BINDING_STALE", "A source binding is declared more than once", null);
            }
        }

        Set<UUID> actual = new LinkedHashSet<>();
        if (implementation.inputMode() == InputMode.PHYSICAL_ASSET) {
            for (ImplementationInput input : safe(implementation.inputs())) {
                if (!(input instanceof PhysicalAssetInput physical)) {
                    throw undeclared("Implementation inputs do not match the declared input mode", null);
                }
                if (!actual.add(physical.sourceBindingId())) {
                    throw undeclared("A physical implementation input is duplicated", physical.sourceBindingId());
                }
                SourceRef source = declared.get(physical.sourceBindingId());
                if (source == null) {
                    throw undeclared("A physical implementation input is not declared by ModelSpec", physical.sourceBindingId());
                }
                if (!Objects.equals(source.resolvedVersion(), physical.resolvedVersion())) {
                    throw stalePin("A physical implementation input uses a stale resolved version", physical.sourceBindingId());
                }
            }
        } else if (!declared.isEmpty() && !isManagedDbt(implementation)) {
            throw missing("Declared physical sources are not bound by the implementation", declared.keySet());
        }
        if (implementation.inputMode() == InputMode.PHYSICAL_ASSET) {
            List<UUID> missing = declared.keySet().stream().filter(id -> !actual.contains(id)).sorted().toList();
            if (!missing.isEmpty()) throw missing("Declared physical sources are not bound by the implementation", missing);
        }

        Map<UUID, PhysicalSourceFact> factsById = new LinkedHashMap<>();
        for (PhysicalSourceFact fact : safe(facts)) {
            if (fact == null || fact.sourceBindingId() == null || factsById.putIfAbsent(fact.sourceBindingId(), fact) != null) {
                throw failure("MODEL_SOURCE_BINDING_STALE", "Physical source evidence is invalid", null);
            }
        }
        List<PhysicalSource> result = new ArrayList<>();
        declared.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            PhysicalSourceFact fact = factsById.get(entry.getKey());
            if (
                fact == null ||
                !fact.current() ||
                !Objects.equals(entry.getValue().resolvedVersion(), fact.resolvedVersion())
            ) {
                throw failure(
                    "MODEL_SOURCE_BINDING_STALE",
                    "A physical source binding is unavailable or has drifted",
                    Map.of("sourceBindingId", entry.getKey())
                );
            }
            result.add(
                new PhysicalSource(
                    entry.getKey(),
                    fact.resolvedVersion(),
                    sourceUniqueId(projectKey, entry.getKey())
                )
            );
        });
        return List.copyOf(result);
    }

    private static List<ModelInput> modelInputs(
        ModelSpecView owner,
        ImplementationView implementation,
        Map<ModelRevisionRef, ModelFact> models
    ) {
        Map<ModelRevisionRef, DependencyRole> declared = new LinkedHashMap<>();
        safe(owner.dependsOn()).stream().filter(Objects::nonNull).sorted(REF_ORDER).forEach(ref -> declare(declared, ref, DependencyRole.UPSTREAM));
        safe(owner.dimensionRefs()).stream().filter(Objects::nonNull).sorted(REF_ORDER).forEach(ref -> declare(declared, ref, DependencyRole.DIMENSION));

        Map<ModelRevisionRef, UpstreamModelInput> actual = new LinkedHashMap<>();
        if (implementation.inputMode() == InputMode.UPSTREAM_MODEL) {
            for (ImplementationInput input : safe(implementation.inputs())) {
                if (!(input instanceof UpstreamModelInput upstream)) {
                    throw undeclared("Implementation inputs do not match the declared input mode", null);
                }
                ModelRevisionRef ref = new ModelRevisionRef(upstream.modelSpecId(), upstream.revision());
                if (actual.putIfAbsent(ref, upstream) != null) {
                    throw undeclared("An upstream implementation input is duplicated", upstream.modelSpecId());
                }
                if (!declared.containsKey(ref) || declared.get(ref) == DependencyRole.DIMENSION) {
                    throw undeclared("An upstream implementation input is not declared by ModelSpec", upstream.modelSpecId());
                }
            }
        } else if (!safe(owner.dependsOn()).isEmpty() && !isManagedDbt(implementation)) {
            throw missing("Declared upstream models are not bound by the implementation", owner.dependsOn());
        }
        if (implementation.inputMode() == InputMode.UPSTREAM_MODEL) {
            List<ModelRevisionRef> missing = safe(owner.dependsOn())
                .stream()
                .filter(Objects::nonNull)
                .filter(ref -> !actual.containsKey(ref))
                .sorted(REF_ORDER)
                .toList();
            if (!missing.isEmpty()) throw missing("Declared upstream models are not bound by the implementation", missing);
        }

        List<ModelInput> result = new ArrayList<>();
        declared.entrySet().stream().sorted(Map.Entry.comparingByKey(REF_ORDER)).forEach(entry -> {
            ModelFact fact = models.get(entry.getKey());
            if (fact == null || fact.model() == null) {
                throw missing("A declared ModelSpec revision is unavailable", List.of(entry.getKey()));
            }
            ModelSpecView target = fact.model();
            if (
                !Objects.equals(target.id(), entry.getKey().modelSpecId()) ||
                target.revision() != entry.getKey().revision() ||
                !checksum(target.checksum())
            ) {
                throw stalePin("A declared ModelSpec revision has drifted", entry.getKey().modelSpecId());
            }
            ImplementationPin pin = fact.implementation();
            if (
                pin == null ||
                pin.modelRevision() != target.revision() ||
                !Objects.equals(pin.modelChecksum(), target.checksum()) ||
                pin.implementationRevision() < 1 ||
                !checksum(pin.implementationChecksum()) ||
                pin.dbtUniqueId() == null ||
                pin.dbtUniqueId().isBlank() ||
                !"ACTIVE".equals(pin.status())
            ) {
                throw stalePin("A declared model implementation pin is unavailable", target.id());
            }
            UpstreamModelInput input = actual.get(entry.getKey());
            if (
                input != null &&
                (
                    !Objects.equals(input.checksum(), target.checksum()) ||
                    input.implementationRevision() != pin.implementationRevision() ||
                    !Objects.equals(input.implementationChecksum(), pin.implementationChecksum()) ||
                    !Objects.equals(input.dbtUniqueId(), pin.dbtUniqueId())
                )
            ) {
                throw stalePin("An upstream implementation input pin has drifted", target.id());
            }
            result.add(
                new ModelInput(
                    target.id(),
                    target.revision(),
                    target.checksum(),
                    pin.implementationRevision(),
                    pin.implementationChecksum(),
                    pin.dbtUniqueId(),
                    entry.getValue()
                )
            );
        });
        return List.copyOf(result);
    }

    private static void declare(
        Map<ModelRevisionRef, DependencyRole> declared,
        ModelRevisionRef reference,
        DependencyRole role
    ) {
        if (reference.modelSpecId() == null || reference.revision() < 1) {
            throw stalePin("A declared model reference is invalid", reference.modelSpecId());
        }
        DependencyRole previous = declared.putIfAbsent(reference, role);
        if (previous != null && previous != role) {
            throw undeclared("A model cannot be both an upstream and dimension dependency", reference.modelSpecId());
        }
    }

    private static Map<ModelRevisionRef, ModelFact> modelFacts(List<ModelFact> facts) {
        Map<ModelRevisionRef, ModelFact> result = new LinkedHashMap<>();
        for (ModelFact fact : safe(facts)) {
            if (fact == null || fact.model() == null || fact.model().id() == null || fact.model().revision() < 1) {
                throw stalePin("Model dependency evidence is invalid", null);
            }
            ModelRevisionRef ref = new ModelRevisionRef(fact.model().id(), fact.model().revision());
            if (result.putIfAbsent(ref, fact) != null) throw stalePin("Model dependency evidence is duplicated", fact.model().id());
        }
        return Map.copyOf(result);
    }

    private static void detectCycle(ModelSpecView owner, Map<ModelRevisionRef, ModelFact> models) {
        ModelRevisionRef root = new ModelRevisionRef(owner.id(), owner.revision());
        LinkedHashSet<ModelRevisionRef> visiting = new LinkedHashSet<>();
        Set<ModelRevisionRef> visited = new LinkedHashSet<>();
        Deque<ModelRevisionRef> path = new ArrayDeque<>();
        visit(root, owner, models, visiting, visited, path);
    }

    private static void visit(
        ModelRevisionRef ref,
        ModelSpecView model,
        Map<ModelRevisionRef, ModelFact> models,
        Set<ModelRevisionRef> visiting,
        Set<ModelRevisionRef> visited,
        Deque<ModelRevisionRef> path
    ) {
        if (visited.contains(ref)) return;
        if (!visiting.add(ref)) {
            List<String> cycle = new ArrayList<>();
            boolean started = false;
            for (ModelRevisionRef item : path) {
                if (item.equals(ref)) started = true;
                if (started) cycle.add(item.modelSpecId() + "@r" + item.revision());
            }
            cycle.add(ref.modelSpecId() + "@r" + ref.revision());
            throw failure(
                "MODEL_IMPLEMENTATION_DEPENDENCY_CYCLE",
                "The ModelSpec dependency graph contains a cycle",
                Map.of("path", cycle)
            );
        }
        path.addLast(ref);
        for (ModelRevisionRef child : references(model)) {
            if (child == null) continue;
            ModelFact childFact = models.get(child);
            if (childFact == null || childFact.model() == null) {
                throw missing("A declared ModelSpec revision is unavailable", List.of(child));
            }
            visit(child, childFact.model(), models, visiting, visited, path);
        }
        path.removeLast();
        visiting.remove(ref);
        visited.add(ref);
    }

    private static List<ModelRevisionRef> references(ModelSpecView model) {
        List<ModelRevisionRef> result = new ArrayList<>();
        result.addAll(safe(model.dependsOn()));
        result.addAll(safe(model.dimensionRefs()));
        result.sort(REF_ORDER);
        return List.copyOf(result);
    }

    private static String checksum(
        ModelSpecView owner,
        List<PhysicalSource> physicalSources,
        List<ModelInput> modelInputs
    ) {
        StringBuilder canonical = new StringBuilder("model-implementation-dependencies-v1\n")
            .append(owner.id()).append('\n');
        physicalSources.forEach(source -> canonical
            .append("source\t")
            .append(source.sourceBindingId()).append('\t')
            .append(source.resolvedVersion()).append('\t')
            .append(source.dbtSourceUniqueId()).append('\n'));
        modelInputs.forEach(input -> canonical
            .append("model\t")
            .append(input.modelSpecId()).append('\t')
            .append(input.revision()).append('\t')
            .append(input.checksum()).append('\t')
            .append(input.implementationRevision()).append('\t')
            .append(input.implementationChecksum()).append('\t')
            .append(input.dbtUniqueId()).append('\t')
            .append(input.role()).append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String sourceUniqueId(String projectKey, UUID sourceBindingId) {
        String token = sourceBindingId.toString().replace("-", "").substring(0, 12).toLowerCase(Locale.ROOT);
        return "source." + projectKey + ".dts_src_" + token + ".src_" + token;
    }

    private static String normalizedProjectKey(String projectKey) {
        if (projectKey == null || !PROJECT_KEY.matcher(projectKey.trim()).matches()) {
            throw stalePin("The dbt project identity is unavailable", null);
        }
        return projectKey.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isManagedDbt(ImplementationView implementation) {
        if (implementation.inputMode() != InputMode.GENERATED || implementation.inputs().size() != 1) return false;
        return implementation.inputs().getFirst() instanceof GeneratedInput generated && "DBT".equals(generated.generatorType());
    }

    private static boolean checksum(String value) {
        return value != null && value.matches("^[0-9a-f]{64}$");
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static ModelSpecException undeclared(String message, Object dependency) {
        return failure(
            "MODEL_IMPLEMENTATION_DEPENDENCY_UNDECLARED",
            message,
            dependency == null ? null : Map.of("dependency", dependency)
        );
    }

    private static ModelSpecException missing(String message, Object dependencies) {
        return failure(
            "MODEL_IMPLEMENTATION_DEPENDENCY_MISSING",
            message,
            dependencies == null ? null : Map.of("dependencies", dependencies)
        );
    }

    private static ModelSpecException stalePin(String message, Object dependency) {
        return failure(
            "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
            message,
            dependency == null ? null : Map.of("dependency", dependency)
        );
    }

    private static ModelSpecException failure(String code, String message, Object details) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.CONFLICT, details);
    }

    public enum DependencyRole {
        UPSTREAM,
        DIMENSION,
    }

    public record PhysicalSource(UUID sourceBindingId, String resolvedVersion, String dbtSourceUniqueId) {}

    public record ModelInput(
        UUID modelSpecId,
        int revision,
        String checksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        DependencyRole role
    ) {}

    public record Snapshot(
        UUID modelSpecId,
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        List<PhysicalSource> physicalSources,
        List<ModelInput> modelInputs,
        String dependencyChecksum
    ) {
        public Snapshot {
            physicalSources = List.copyOf(physicalSources == null ? List.of() : physicalSources);
            modelInputs = List.copyOf(modelInputs == null ? List.of() : modelInputs);
        }
    }

    public record Reconciliation(List<String> matched, List<String> missing, List<String> undeclared) {
        public Reconciliation {
            matched = List.copyOf(matched == null ? List.of() : matched);
            missing = List.copyOf(missing == null ? List.of() : missing);
            undeclared = List.copyOf(undeclared == null ? List.of() : undeclared);
        }
    }

    public record PhysicalSourceFact(
        UUID sourceBindingId,
        String resolvedVersion,
        boolean current,
        String sourceType,
        String executableRef
    ) {
        public PhysicalSourceFact(UUID sourceBindingId, String resolvedVersion, boolean current) {
            this(sourceBindingId, resolvedVersion, current, null, null);
        }
    }

    public record ImplementationPin(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        String status
    ) {}

    public record ModelFact(ModelSpecView model, ImplementationPin implementation) {}

    public record DependencyFacts(List<ModelFact> models, List<PhysicalSourceFact> physicalSources) {
        public DependencyFacts {
            models = List.copyOf(models == null ? List.of() : models);
            physicalSources = List.copyOf(physicalSources == null ? List.of() : physicalSources);
        }
    }
}
