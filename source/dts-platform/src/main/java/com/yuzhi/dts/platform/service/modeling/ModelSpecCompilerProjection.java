package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencyService.Resolution;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.ModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSource;
import com.yuzhi.dts.platform.service.modeling.ModelImplementationDependencySnapshotResolver.PhysicalSourceFact;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/** Projects canonical ModelSpec revisions into the compiler's minimal immutable input. */
public final class ModelSpecCompilerProjection {

    private ModelSpecCompilerProjection() {}

    /** Immutable compiler view of one append-only implementation revision. */
    public record ImplementationProjection(
        ModelingCompilerContract.CompilerModel model,
        String tenantId,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum,
        String dbtUniqueId,
        InputMode inputMode,
        List<ImplementationInput> inputs,
        List<FieldMapping> fieldMappings,
        Map<String, Object> settings,
        String materialization,
        List<String> keyFields,
        String planId,
		List<CompilerField> typedFields,
		String dependencyChecksum
    ) {
		public ImplementationProjection(
			ModelingCompilerContract.CompilerModel model,
			String tenantId,
			String modelChecksum,
			int implementationRevision,
			String implementationChecksum,
			String dbtUniqueId,
			InputMode inputMode,
			List<ImplementationInput> inputs,
			List<FieldMapping> fieldMappings,
			Map<String, Object> settings,
			String materialization,
			List<String> keyFields,
			String planId,
			List<CompilerField> typedFields
		) {
			this(
				model,
				tenantId,
				modelChecksum,
				implementationRevision,
				implementationChecksum,
				dbtUniqueId,
				inputMode,
				inputs,
				fieldMappings,
				settings,
				materialization,
				keyFields,
				planId,
				typedFields,
				null
			);
		}

        public ImplementationProjection(
            ModelingCompilerContract.CompilerModel model,
            String tenantId,
            String modelChecksum,
            int implementationRevision,
            String implementationChecksum,
            String dbtUniqueId,
            InputMode inputMode,
            List<ImplementationInput> inputs,
            List<FieldMapping> fieldMappings,
            Map<String, Object> settings,
            String materialization
        ) {
            this(
                model,
                tenantId,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                dbtUniqueId,
                inputMode,
                inputs,
                fieldMappings,
                settings,
                materialization,
                grainKeys(model),
                "unscoped",
				List.of(),
				null
            );
        }

        public ImplementationProjection(
            ModelingCompilerContract.CompilerModel model,
            String tenantId,
            String modelChecksum,
            int implementationRevision,
            String implementationChecksum,
            String dbtUniqueId,
            InputMode inputMode,
            List<ImplementationInput> inputs,
            List<FieldMapping> fieldMappings,
            Map<String, Object> settings,
            String materialization,
            List<CompilerField> typedFields
        ) {
            this(
                model,
                tenantId,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                dbtUniqueId,
                inputMode,
                inputs,
                fieldMappings,
                settings,
                materialization,
                grainKeys(model),
                "unscoped",
				typedFields,
				null
            );
        }

        public ImplementationProjection {
            Objects.requireNonNull(model, "model is required");
            if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is required");
            if (modelChecksum == null || !modelChecksum.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("modelChecksum must be SHA-256");
            }
            if (implementationRevision < 1) throw new IllegalArgumentException("implementationRevision must be positive");
            if (implementationChecksum == null || !implementationChecksum.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("implementationChecksum must be SHA-256");
            }
            if (dbtUniqueId == null || dbtUniqueId.isBlank()) throw new IllegalArgumentException("dbtUniqueId is required");
            Objects.requireNonNull(inputMode, "inputMode is required");
            inputs = List.copyOf(inputs == null ? List.of() : inputs);
            fieldMappings = List.copyOf(fieldMappings == null ? List.of() : fieldMappings);
            settings = Map.copyOf(settings == null ? Map.of() : new TreeMap<>(settings));
            if (materialization == null || materialization.isBlank()) throw new IllegalArgumentException("materialization is required");
            materialization = materialization.trim();
            keyFields = List.copyOf(keyFields == null ? List.of() : keyFields);
            if (planId == null || planId.isBlank()) throw new IllegalArgumentException("planId is required");
            planId = planId.trim();
            typedFields = List.copyOf(typedFields == null ? List.of() : typedFields);
			if (dependencyChecksum != null && !dependencyChecksum.matches("^[0-9a-f]{64}$")) {
				throw new IllegalArgumentException("dependencyChecksum must be SHA-256");
			}
        }
    }

    public record CompilerField(
        String name,
        String dataType,
        boolean nullable
    ) {
        public CompilerField {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
            if (dataType == null || dataType.isBlank()) throw new IllegalArgumentException("dataType is required");
            name = name.trim();
            dataType = dataType.trim();
        }
    }

    public static ModelingCompilerContract.CompilerModel project(
        ModelSpecView view,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver
    ) {
        validatePinnedReferences(view.dependsOn(), revisionResolver, false);
        validatePinnedReferences(view.dimensionRefs(), revisionResolver, true);
        List<ModelingCompilerContract.SourceRef> sources = projectSources(view, revisionResolver);
        List<String> dimensions = view.fields()
            .stream()
            .filter(Objects::nonNull)
            .filter(field -> field.role() != FieldRole.MEASURE)
            .map(ModelSpecContract.ModelField::name)
            .filter(ModelSpecCompilerProjection::notBlank)
            .toList();
        List<String> metrics = view.fields()
            .stream()
            .filter(Objects::nonNull)
            .filter(field -> field.role() == FieldRole.MEASURE)
            .map(ModelSpecContract.ModelField::name)
            .filter(ModelSpecCompilerProjection::notBlank)
            .toList();
        List<ModelingCompilerContract.StandardBinding> bindings = view.standardBindings()
            .stream()
            .filter(Objects::nonNull)
            .map(binding -> new ModelingCompilerContract.StandardBinding(
                binding.fieldName(),
                binding.standardElementId() == null ? null : binding.standardElementId().toString()
            ))
            .toList();
        return new ModelingCompilerContract.CompilerModel(
            view.id().toString(),
            ModelingCompilerContract.Layer.valueOf(view.layer().name()),
            ModelingCompilerContract.ModelType.valueOf(view.modelType().name()),
            ModelingCompilerContract.ImplementationMode.valueOf(view.implementationMode().name()),
            view.name(),
            view.grain() == null ? null : new ModelingCompilerContract.Grain(view.grain().statement(), view.grain().keys()),
            bindings,
            sources,
            dimensions,
            metrics,
            view.revision()
        );
    }

    /** Projects the canonical model together with the exact implementation revision that owns compiler input. */
    public static ImplementationProjection project(
        ModelSpecView view,
        ImplementationView implementation,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver
    ) {
        return project(
            view,
            implementation,
            revisionResolver,
            "unscoped",
            input -> view.sourceRefs()
                .stream()
                .filter(source -> source != null && input.sourceBindingId().equals(source.sourceBindingId()))
                .filter(source -> input.resolvedVersion().equals(source.resolvedVersion()))
                .findFirst()
                .orElse(null)
        );
    }

    public static ImplementationProjection project(
        ModelSpecView view,
        ImplementationView implementation,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver,
        String tenantId,
        Function<PhysicalAssetInput, SourceRef> physicalResolver
    ) {
        if (implementation == null) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_REQUIRED",
                "A current implementation revision is required before compilation",
                ModelSpecException.Kind.CONFLICT
            );
        }
        ModelingCompilerContract.CompilerModel canonical = project(view, revisionResolver);
        ModelingCompilerContract.CompilerModel implementationBound = new ModelingCompilerContract.CompilerModel(
            canonical.id(), canonical.layer(), canonical.modelType(),
            canonical.implementationMode(), canonical.name(), canonical.grain(), canonical.standardBindings(),
            projectImplementationSources(implementation, revisionResolver, physicalResolver), canonical.dimensions(), canonical.metrics(),
            canonical.revision()
        );
        return new ImplementationProjection(
            implementationBound,
            tenantId,
            view.checksum(),
            implementation.implementationRevision(),
            implementation.implementationChecksum(),
            implementation.dbtUniqueId(),
            implementation.inputMode(),
            implementation.inputs(),
            implementation.fieldMappings(),
            implementation.settings(),
            implementation.materialization(),
            canonicalKeyFields(view),
            view.planId().toString(),
            compilerFields(view)
        );
    }

	/** Projects compiler inputs from the same fixed dependency snapshot used by dbt drafts and materialization. */
	public static ImplementationProjection project(
		ModelSpecView view,
		ImplementationView implementation,
		Function<ModelRevisionRef, ModelSpecView> revisionResolver,
		String tenantId,
		Resolution dependencies
	) {
		if (dependencies == null || dependencies.snapshot() == null) {
			throw new ModelSpecException(
				"MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
				"A fixed dependency snapshot is required for generated compilation",
				ModelSpecException.Kind.CONFLICT
			);
		}
		ImplementationProjection base = project(
			view,
			implementation,
			revisionResolver,
			tenantId,
			input -> view.sourceRefs()
				.stream()
				.filter(source -> source != null && input.sourceBindingId().equals(source.sourceBindingId()))
				.filter(source -> input.resolvedVersion().equals(source.resolvedVersion()))
				.findFirst()
				.orElse(null)
		);
		List<ModelingCompilerContract.SourceRef> sources = projectDependencySources(dependencies, revisionResolver);
		ModelingCompilerContract.CompilerModel model = base.model();
		ModelingCompilerContract.CompilerModel dependencyBound = new ModelingCompilerContract.CompilerModel(
			model.id(),
			model.layer(),
			model.modelType(),
			model.implementationMode(),
			model.name(),
			model.grain(),
			model.standardBindings(),
			sources,
			model.dimensions(),
			model.metrics(),
			model.revision()
		);
		return new ImplementationProjection(
			dependencyBound,
			base.tenantId(),
			base.modelChecksum(),
			base.implementationRevision(),
			base.implementationChecksum(),
			base.dbtUniqueId(),
			base.inputMode(),
			base.inputs(),
			base.fieldMappings(),
			base.settings(),
			base.materialization(),
			base.keyFields(),
			base.planId(),
			base.typedFields(),
			dependencies.snapshot().dependencyChecksum()
		);
	}

	private static List<ModelingCompilerContract.SourceRef> projectDependencySources(
		Resolution dependencies,
		Function<ModelRevisionRef, ModelSpecView> revisionResolver
	) {
		List<ModelingCompilerContract.SourceRef> result = new ArrayList<>();
		for (PhysicalSource source : dependencies.snapshot().physicalSources()) {
			PhysicalSourceFact fact = dependencies.physicalSourceFacts().get(source.sourceBindingId());
			if (
				fact == null ||
				!fact.current() ||
				!Objects.equals(source.resolvedVersion(), fact.resolvedVersion()) ||
				!notBlank(fact.executableRef())
			) {
				throw dependencyStale(source.sourceBindingId());
			}
			boolean dbtNode = "DBT_NODE".equalsIgnoreCase(fact.sourceType());
			result.add(new ModelingCompilerContract.SourceRef(
				dbtNode ? "DBT_MODEL" : "TABLE",
				dbtNode ? dbtResourceName(fact.executableRef()) : fact.executableRef(),
				ModelingCompilerContract.Layer.ODS
			));
		}
		for (ModelInput input : dependencies.snapshot().modelInputs()) {
			ModelRevisionRef reference = new ModelRevisionRef(input.modelSpecId(), input.revision());
			ModelSpecView dependency = revisionResolver.apply(reference);
			if (
				dependency == null ||
				dependency.revision() != input.revision() ||
				!Objects.equals(dependency.checksum(), input.checksum())
			) {
				throw dependencyStale(input.modelSpecId());
			}
			result.add(new ModelingCompilerContract.SourceRef(
				"DBT_MODEL",
				dbtResourceName(input.dbtUniqueId()),
				ModelingCompilerContract.Layer.valueOf(dependency.layer().name())
			));
		}
		return List.copyOf(result);
	}

	private static ModelSpecException dependencyStale(Object dependency) {
		return new ModelSpecException(
			"MODEL_IMPLEMENTATION_DEPENDENCY_PIN_STALE",
			"A fixed implementation dependency is unavailable",
			ModelSpecException.Kind.CONFLICT,
			Map.of("dependency", dependency)
		);
	}

    private static List<CompilerField> compilerFields(ModelSpecView view) {
        if (view == null || view.fields() == null) return List.of();
        return view
            .fields()
            .stream()
            .filter(Objects::nonNull)
            .filter(field -> notBlank(field.name()))
            .map(field ->
                new CompilerField(
                    field.name(),
                    field.dataType(),
                    Boolean.TRUE.equals(field.nullable())
                )
            )
            .toList();
    }

    private static List<String> canonicalKeyFields(ModelSpecView view) {
        if (view == null || view.fields() == null) return List.of();
        return view
            .fields()
            .stream()
            .filter(Objects::nonNull)
            .filter(field -> field.role() == FieldRole.KEY)
            .map(ModelSpecContract.ModelField::name)
            .filter(ModelSpecCompilerProjection::notBlank)
            .distinct()
            .toList();
    }

    private static List<String> grainKeys(ModelingCompilerContract.CompilerModel model) {
        return model == null || model.grain() == null || model.grain().keys() == null
            ? List.of()
            : model.grain().keys();
    }

    /** Compiler inputs are authoritative: canonical sourceRefs are validation evidence only. */
    private static List<ModelingCompilerContract.SourceRef> projectImplementationSources(
        ImplementationView implementation,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver,
        Function<PhysicalAssetInput, SourceRef> physicalResolver
    ) {
        return switch (implementation.inputMode()) {
            case PHYSICAL_ASSET -> implementation.inputs().stream().map(PhysicalAssetInput.class::cast).map(input -> {
                SourceRef source = physicalResolver.apply(input);
                if (
                    source == null ||
                    !input.sourceBindingId().equals(source.sourceBindingId()) ||
                    !input.resolvedVersion().equals(source.resolvedVersion())
                ) {
                    throw new ModelSpecException(
                    "MODEL_IMPLEMENTATION_INPUT_STALE", "Physical implementation input is no longer revision-bound", ModelSpecException.Kind.CONFLICT
                    );
                }
                return projectSource(source);
            }).toList();
            case UPSTREAM_MODEL -> implementation.inputs().stream().map(UpstreamModelInput.class::cast).map(input -> {
                ModelSpecView upstream = revisionResolver.apply(new ModelRevisionRef(input.modelSpecId(), input.revision()));
                if (
                    upstream == null ||
                    upstream.revision() != input.revision() ||
                    !Objects.equals(upstream.checksum(), input.checksum()) ||
                    !input.implementationPinned()
                ) {
                    throw new ModelSpecException(
                        "MODEL_IMPLEMENTATION_INPUT_STALE", "Pinned upstream implementation input is unavailable", ModelSpecException.Kind.CONFLICT
                    );
                }
                return new ModelingCompilerContract.SourceRef(
                    "DBT_MODEL", dbtResourceName(input.dbtUniqueId()), ModelingCompilerContract.Layer.valueOf(upstream.layer().name())
                );
            }).toList();
            case GENERATED -> List.of();
        };
    }

    private static List<ModelingCompilerContract.SourceRef> projectSources(
        ModelSpecView view,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver
    ) {
        if (!view.sourceRefs().isEmpty()) {
            return view.sourceRefs().stream().filter(Objects::nonNull).map(ModelSpecCompilerProjection::projectSource).toList();
        }
        List<ModelingCompilerContract.SourceRef> sources = new ArrayList<>();
        for (ModelRevisionRef ref : view.dependsOn()) {
            ModelSpecView upstream = revisionResolver.apply(ref);
            if (upstream == null || upstream.revision() != ref.revision()) {
                throw new ModelSpecException(
                    "MODEL_SPEC_UPSTREAM_REVISION_MISSING",
                    "Pinned upstream ModelSpec revision is unavailable",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            sources.add(new ModelingCompilerContract.SourceRef(
                "DBT_MODEL",
                upstream.name(),
                ModelingCompilerContract.Layer.valueOf(upstream.layer().name())
            ));
        }
        return List.copyOf(sources);
    }

    private static void validatePinnedReferences(
        List<ModelRevisionRef> references,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver,
        boolean dimensionOnly
    ) {
        for (ModelRevisionRef reference : references) {
            ModelSpecView target = revisionResolver.apply(reference);
            if (target == null || target.revision() != reference.revision()) {
                throw new ModelSpecException(
                    "MODEL_SPEC_UPSTREAM_REVISION_MISSING",
                    "Pinned upstream ModelSpec revision is unavailable",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            if (dimensionOnly && target.modelType() != ModelSpecContract.ModelType.DIMENSION) {
                throw new ModelSpecException(
                    "MODEL_SPEC_DIMENSION_REF_TYPE_INVALID",
                    "Dimension references must resolve to DIMENSION ModelSpecs",
                    ModelSpecException.Kind.CONFLICT
                );
            }
        }
    }

    private static ModelingCompilerContract.SourceRef projectSource(SourceRef source) {
        return new ModelingCompilerContract.SourceRef(
            source.kind().name(),
            source.ref(),
            ModelingCompilerContract.Layer.valueOf(source.layer().name())
        );
    }

    private static String dbtResourceName(String dbtUniqueId) {
        int separator = dbtUniqueId == null ? -1 : dbtUniqueId.lastIndexOf('.');
        if (separator < 0 || separator == dbtUniqueId.length() - 1) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_INPUT_STALE",
                "Pinned upstream dbt node identity is invalid",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return dbtUniqueId.substring(separator + 1);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
