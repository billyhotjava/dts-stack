package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.DependencyFacts;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.Snapshot;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyReadPort.PlanFacts;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Single application seam shared by manual dbt, generated dbt and materialization planning. */
@Service
public class ModelImplementationDependencyService {

    private static final String PROVISIONAL_CHECKSUM = "0".repeat(64);

    private final ModelImplementationDependencyReadPort readPort;
    private final ModelImplementationDependencySnapshotResolver resolver;

    @Autowired
    public ModelImplementationDependencyService(ModelImplementationDependencyReadPort readPort) {
        this(readPort, new ModelImplementationDependencySnapshotResolver());
    }

    ModelImplementationDependencyService(
        ModelImplementationDependencyReadPort readPort,
        ModelImplementationDependencySnapshotResolver resolver
    ) {
        this.readPort = readPort;
        this.resolver = resolver;
    }

    @Transactional(readOnly = true)
    public Resolution resolveForDraft(
        String tenantId,
        ModelSpecView owner,
        ImplementationView implementation,
        String projectKey,
        String targetName
    ) {
		return resolveCurrent(tenantId, owner, implementation, projectKey, targetName);
	}

	@Transactional(readOnly = true)
	public Resolution resolveCurrent(
		String tenantId,
		ModelSpecView owner,
		ImplementationView implementation,
		String projectKey,
		String targetName
	) {
        DependencyFacts facts = ModelSchemaOnlySupport.isSchemaOnly(implementation)
            ? new DependencyFacts(List.of(), List.of()) : readPort.readFacts(tenantId, owner);
        ImplementationView effective = implementation == null
            ? provisionalImplementation(owner, projectKey, targetName)
            : implementation;
        Snapshot snapshot = resolver.resolve(owner, effective, projectKey, facts);
        LinkedHashMap<UUID, PhysicalSourceFact> physicalSources = new LinkedHashMap<>();
        facts
            .physicalSources()
            .stream()
            .sorted(java.util.Comparator.comparing(PhysicalSourceFact::sourceBindingId))
            .forEach(source -> physicalSources.put(source.sourceBindingId(), source));
        return new Resolution(snapshot, Map.copyOf(physicalSources));
    }

    /** Resolves all requested roots and their transitive pins from one bounded repository snapshot. */
    @Transactional(readOnly = true, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public PlanResolution resolvePlan(String tenantId, UUID planId, List<UUID> requestedModelSpecIds) {
        List<UUID> requested = requestedModelSpecIds == null
            ? List.of()
            : requestedModelSpecIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
        PlanFacts planFacts = readPort.readPlanFacts(tenantId, planId, requested);
        DependencyFacts dependencies = planFacts.dependencies();
        rejectConflictingRevisionPins(dependencies.models());
        LinkedHashMap<UUID, PhysicalSourceFact> physicalSources = new LinkedHashMap<>();
        dependencies
            .physicalSources()
            .stream()
            .sorted(java.util.Comparator.comparing(PhysicalSourceFact::sourceBindingId))
            .forEach(source -> physicalSources.put(source.sourceBindingId(), source));

        LinkedHashMap<UUID, ResolvedModel> resolved = new LinkedHashMap<>();
        dependencies
            .models()
            .stream()
            .sorted(java.util.Comparator.comparing(fact -> fact.model().id()))
            .forEach(fact -> {
                ModelSpecView model = fact.model();
                ImplementationView implementation = planFacts.implementations().get(model.id());
                if (implementation == null) {
                    throw new ModelSpecException(
                        "MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
                        "A materialization dependency has no current implementation",
                        ModelSpecException.Kind.CONFLICT,
                        Map.of("modelSpecId", model.id(), "revision", model.revision())
                    );
                }
                Snapshot snapshot = resolver.resolve(model, implementation, implementation.projectKey(), dependencies);
                resolved.put(model.id(), new ResolvedModel(model, implementation, snapshot));
            });
        for (UUID requestedId : requested) {
            if (!resolved.containsKey(requestedId)) {
                throw new ModelSpecException(
                    "MODEL_MATERIALIZATION_PLAN_ROOT_UNAVAILABLE",
                    "A requested materialization model is unavailable in the plan",
                    ModelSpecException.Kind.NOT_FOUND,
                    Map.of("modelSpecId", requestedId, "planId", planId)
                );
            }
        }
        return new PlanResolution(requested, Map.copyOf(resolved), Map.copyOf(physicalSources));
    }

    private static void rejectConflictingRevisionPins(
        List<ModelImplementationDependencySnapshotResolver.ModelFact> models
    ) {
        LinkedHashMap<UUID, Set<String>> pinsByModel = new LinkedHashMap<>();
        (models == null ? List.<ModelImplementationDependencySnapshotResolver.ModelFact>of() : models)
            .stream()
            .filter(Objects::nonNull)
            .map(ModelImplementationDependencySnapshotResolver.ModelFact::model)
            .filter(Objects::nonNull)
            .forEach(model ->
                pinsByModel
                    .computeIfAbsent(model.id(), ignored -> new LinkedHashSet<>())
                    .add(model.revision() + ":" + model.checksum())
            );
        pinsByModel.forEach((modelSpecId, pins) -> {
            if (pins.size() > 1) {
                throw new ModelSpecException(
                    "MODEL_MATERIALIZATION_DEPENDENCY_SELECTOR_CONFLICT",
                    "The materialization dependency closure pins one model to multiple revisions",
                    ModelSpecException.Kind.CONFLICT,
                    Map.of("modelSpecId", modelSpecId, "pins", pins.stream().sorted().toList())
                );
            }
        });
    }

    public ModelImplementationDependencySnapshotResolver.Reconciliation reconcile(
        Snapshot snapshot,
        List<String> parsedDependencies
    ) {
        return resolver.reconcileParsedDependencies(snapshot, parsedDependencies);
    }

    private static ImplementationView provisionalImplementation(
        ModelSpecView owner,
        String projectKey,
        String targetName
    ) {
        String normalizedTarget = targetName == null || targetName.isBlank() ? "pending" : targetName.trim();
        String dbtUniqueId = "model." + projectKey + "." + normalizedTarget;
        return new ImplementationView(
            UUID.nameUUIDFromBytes((owner.id() + ":provisional-dbt").getBytes(StandardCharsets.UTF_8)),
            owner.id(),
            owner.planId(),
            owner.revision(),
            owner.checksum(),
            ImplementationMode.DBT_MANAGED,
            projectKey,
            dbtUniqueId,
            "ACTIVE",
            1,
            PROVISIONAL_CHECKSUM,
            InputMode.GENERATED,
            List.of(new GeneratedInput("DBT", Map.of("provisional", true))),
            List.of(),
            Map.of(),
            owner.materialization()
        );
    }

    public record Resolution(Snapshot snapshot, Map<UUID, PhysicalSourceFact> physicalSourceFacts) {
        public Resolution {
            physicalSourceFacts = Map.copyOf(physicalSourceFacts == null ? Map.of() : physicalSourceFacts);
        }
    }

    public record ResolvedModel(
        ModelSpecView model,
        ImplementationView implementation,
        Snapshot dependencySnapshot
    ) {}

    public record PlanResolution(
        List<UUID> requestedModelSpecIds,
        Map<UUID, ResolvedModel> models,
        Map<UUID, PhysicalSourceFact> physicalSourceFacts
    ) {
        public PlanResolution {
            requestedModelSpecIds = List.copyOf(requestedModelSpecIds == null ? List.of() : requestedModelSpecIds);
            models = Map.copyOf(models == null ? Map.of() : models);
            physicalSourceFacts = Map.copyOf(physicalSourceFacts == null ? Map.of() : physicalSourceFacts);
        }
    }
}
