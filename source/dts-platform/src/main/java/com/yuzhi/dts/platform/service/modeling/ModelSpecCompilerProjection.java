package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Pure compatibility projection. It adapts v2 snapshots without changing the CRITICAL compiler. */
public final class ModelSpecCompilerProjection {

    private ModelSpecCompilerProjection() {}

    public static ModelingVNextContract.ModelSpec project(
        ModelSpecView view,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver
    ) {
        validatePinnedReferences(view.dependsOn(), revisionResolver, false);
        validatePinnedReferences(view.dimensionRefs(), revisionResolver, true);
        List<ModelingVNextContract.SourceRef> sources = projectSources(view, revisionResolver);
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
        List<ModelingVNextContract.StandardBinding> bindings = view.standardBindings()
            .stream()
            .filter(Objects::nonNull)
            .map(binding -> new ModelingVNextContract.StandardBinding(
                binding.fieldName(),
                binding.standardElementId() == null ? null : binding.standardElementId().toString(),
                binding.referenceCode(),
                binding.securityLevel()
            ))
            .toList();
        return new ModelingVNextContract.ModelSpec(
            view.id().toString(),
            null,
            view.modelType() == ModelSpecContract.ModelType.FACT ? view.businessActivityRef() : null,
            ModelingVNextContract.Layer.valueOf(view.layer().name()),
            ModelingVNextContract.ModelType.valueOf(view.modelType().name()),
            ModelingVNextContract.ImplementationMode.valueOf(view.implementationMode().name()),
            view.name(),
            view.grain() == null ? null : new ModelingVNextContract.Grain(view.grain().statement(), view.grain().keys()),
            bindings,
            sources,
            dimensions,
            metrics,
            view.materialization(),
            view.revision(),
            view.dependsOn().stream().map(ref -> ref.modelSpecId().toString()).toList(),
            null
        );
    }

    private static List<ModelingVNextContract.SourceRef> projectSources(
        ModelSpecView view,
        Function<ModelRevisionRef, ModelSpecView> revisionResolver
    ) {
        if (!view.sourceRefs().isEmpty()) {
            return view.sourceRefs().stream().filter(Objects::nonNull).map(ModelSpecCompilerProjection::projectSource).toList();
        }
        List<ModelingVNextContract.SourceRef> sources = new ArrayList<>();
        for (ModelRevisionRef ref : view.dependsOn()) {
            ModelSpecView upstream = revisionResolver.apply(ref);
            if (upstream == null || upstream.revision() != ref.revision()) {
                throw new ModelSpecException(
                    "MODEL_SPEC_UPSTREAM_REVISION_MISSING",
                    "Pinned upstream ModelSpec revision is unavailable",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            sources.add(new ModelingVNextContract.SourceRef(
                "DBT_MODEL",
                upstream.name(),
                ModelingVNextContract.Layer.valueOf(upstream.layer().name())
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

    private static ModelingVNextContract.SourceRef projectSource(SourceRef source) {
        return new ModelingVNextContract.SourceRef(
            source.kind().name(),
            source.ref(),
            ModelingVNextContract.Layer.valueOf(source.layer().name())
        );
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
