package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.GenerationStrategy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Bridges legacy ModelSpec input fields to the revision-bound implementation input contract.
 *
 * <p>This adapter deliberately does not pick one legacy kind when more than one is present. A
 * mixed legacy definition needs an explicit migration before a writer can persist it.
 */
@Component
public class ModelImplementationCompatibilityAdapter {

    public static final String DATE_DIMENSION_GENERATOR = "DATE_DIMENSION";

    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecRepository repository;
    private final ModelLifecycleRepository lifecycle;
    private final ModelSpecSourceValidationPort sourceValidation;

    public ModelImplementationCompatibilityAdapter(
        ModelSpecApplicationService modelSpecs,
        ModelSpecRepository repository,
        ModelLifecycleRepository lifecycle,
        ModelSpecSourceValidationPort sourceValidation
    ) {
        this.modelSpecs = modelSpecs;
        this.repository = repository;
        this.lifecycle = lifecycle;
        this.sourceValidation = sourceValidation;
    }

    public CompatibilityProjection projectLegacy(String tenantId, ModelSpecView model) {
        if (model == null) return CompatibilityProjection.invalid("MODEL_IMPLEMENTATION_INPUT_REQUIRED");
        boolean physical = !model.sourceRefs().isEmpty();
        boolean upstream = !model.dependsOn().isEmpty();
        boolean generated = model.generationStrategy() != null;
        int kinds = (physical ? 1 : 0) + (upstream ? 1 : 0) + (generated ? 1 : 0);
        if (kinds == 0) return CompatibilityProjection.invalid("MODEL_IMPLEMENTATION_INPUT_REQUIRED");
        if (kinds > 1) return CompatibilityProjection.invalid("MODEL_IMPLEMENTATION_LEGACY_INPUT_CONFLICT");
        if (physical) {
            List<ImplementationInput> inputs = model
                .sourceRefs()
                .stream()
                .filter(source -> source != null && source.sourceBindingId() != null && !isBlank(source.resolvedVersion()))
                .map(source -> (ImplementationInput) new PhysicalAssetInput(source.sourceBindingId(), source.resolvedVersion()))
                .toList();
            return inputs.isEmpty()
                ? CompatibilityProjection.invalid("PHYSICAL_ASSET_NOT_CONFIRMED")
                : CompatibilityProjection.of(InputMode.PHYSICAL_ASSET, inputs);
        }
        if (upstream) {
            try {
                return CompatibilityProjection.of(
                    InputMode.UPSTREAM_MODEL,
                    model.dependsOn().stream().map(ref -> {
                        ModelSpecView target = modelSpecs.revision(tenantId, ref);
                        ModelLifecycleContract.ImplementationView implementation = currentImplementation(tenantId, ref.modelSpecId());
                        return (ImplementationInput) new UpstreamModelInput(
                            ref.modelSpecId(),
                            ref.revision(),
                            target.checksum(),
                            implementation.implementationRevision(),
                            implementation.implementationChecksum(),
                            implementation.dbtUniqueId()
                        );
                    }).toList()
                );
            } catch (RuntimeException unavailable) {
                return CompatibilityProjection.invalid("MODEL_IMPLEMENTATION_INPUT_STALE");
            }
        }
        GenerationStrategy strategy = model.generationStrategy();
        if (isBlank(strategy.type())) return CompatibilityProjection.invalid("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        return CompatibilityProjection.of(
            InputMode.GENERATED,
            List.of(new GeneratedInput(strategy.type(), strategy.reference() == null ? java.util.Map.of() : java.util.Map.of("reference", strategy.reference())))
        );
    }

    public ValidationResult validate(String tenantId, ModelSpecView owner, SaveImplementationCommand command) {
        if (command == null || command.inputs() == null || command.inputs().isEmpty()) {
            return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_REQUIRED");
        }
        if (new HashSet<>(command.inputs()).size() != command.inputs().size()) {
            return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_DUPLICATE");
        }
        if (!allows(owner == null ? null : owner.modelType(), command.inputMode())) {
            return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
        }
        for (ImplementationInput input : command.inputs()) {
            if (input == null || input.mode() != command.inputMode()) {
                return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
            }
            ValidationResult result = switch (command.inputMode()) {
                case PHYSICAL_ASSET -> validatePhysical(tenantId, owner, (PhysicalAssetInput) input);
                case UPSTREAM_MODEL -> validateUpstream(tenantId, owner, (UpstreamModelInput) input);
                case GENERATED -> validateGenerated((GeneratedInput) input);
            };
            if (!result.valid()) return result;
        }
        return ValidationResult.ok();
    }

    /**
     * Converts unpinned API selections into immutable upstream implementation references. Already
     * pinned inputs are never upgraded implicitly; a client must explicitly send an unpinned
     * selection after choosing the current upstream version.
     */
    public SaveImplementationCommand pinCurrentUpstreamImplementations(String tenantId, SaveImplementationCommand command) {
        if (command == null || command.inputMode() != InputMode.UPSTREAM_MODEL) return command;
        List<ImplementationInput> inputs = command.inputs().stream().map(input -> {
            UpstreamModelInput upstream = (UpstreamModelInput) input;
            if (upstream.implementationPinned()) return upstream;
            ModelLifecycleContract.ImplementationView implementation = currentImplementation(tenantId, upstream.modelSpecId());
            if (
                implementation.revision() != upstream.revision() ||
                !Objects.equals(implementation.modelChecksum(), upstream.checksum())
            ) {
                throw new ModelSpecException(
                    "MODEL_IMPLEMENTATION_INPUT_STALE",
                    "The selected upstream model revision has no matching current implementation",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            return (ImplementationInput) new UpstreamModelInput(
                upstream.modelSpecId(),
                upstream.revision(),
                upstream.checksum(),
                implementation.implementationRevision(),
                implementation.implementationChecksum(),
                implementation.dbtUniqueId()
            );
        }).toList();
        return new SaveImplementationCommand(
            command.inputMode(),
            inputs,
            command.fieldMappings(),
            command.settings(),
            command.ownership(),
            command.materialization(),
            command.idempotencyKey()
        );
    }

    public ValidationResult validateLegacyProjection(String tenantId, ModelSpecView owner) {
        CompatibilityProjection projection = projectLegacy(tenantId, owner);
        if (!projection.valid()) return ValidationResult.invalid(projection.code());
        return validate(
            tenantId,
            owner,
            new SaveImplementationCommand(
                projection.inputMode(), projection.inputs(), List.of(), java.util.Map.of(), owner.implementationMode(),
                owner.materialization() == null || owner.materialization().isBlank() ? "table" : owner.materialization(), "legacy-projection"
            )
        );
    }

    private ValidationResult validatePhysical(String tenantId, ModelSpecView owner, PhysicalAssetInput input) {
        if (owner == null || input == null) return ValidationResult.invalid("PHYSICAL_ASSET_NOT_CONFIRMED");
        if (
            sourceValidation == null ||
            !sourceValidation.isCurrentBindingForGate(tenantId, owner.planId(), input.sourceBindingId(), input.resolvedVersion())
        ) {
            return ValidationResult.invalid("PHYSICAL_ASSET_NOT_CONFIRMED");
        }
        return ValidationResult.ok();
    }

    private ValidationResult validateUpstream(String tenantId, ModelSpecView owner, UpstreamModelInput input) {
        if (owner == null || input == null) return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_STALE");
        if (Objects.equals(owner.id(), input.modelSpecId())) return ValidationResult.invalid("MODEL_IMPLEMENTATION_SELF_REFERENCE");
        try {
            ModelRevisionRef reference = new ModelRevisionRef(input.modelSpecId(), input.revision());
            ModelSpecView target = modelSpecs.revision(tenantId, reference);
            ModelSpecRepository.StoredModelSpec current = repository.findCurrent(tenantId, input.modelSpecId()).orElse(null);
            ModelLifecycleContract.ImplementationView implementation = currentImplementation(tenantId, input.modelSpecId());
            if (
                target == null ||
                current == null ||
                current.revision() != input.revision() ||
                !Objects.equals(target.checksum(), input.checksum()) ||
                !input.implementationPinned() ||
                implementation.revision() != input.revision() ||
                !Objects.equals(implementation.modelChecksum(), input.checksum()) ||
                implementation.implementationRevision() != input.implementationRevision() ||
                !Objects.equals(implementation.implementationChecksum(), input.implementationChecksum()) ||
                !Objects.equals(implementation.dbtUniqueId(), input.dbtUniqueId())
            ) {
                return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_STALE");
            }
            if (!Objects.equals(owner.planId(), target.planId()) && target.status() != ModelStatus.PUBLISHED) {
                return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_STALE");
            }
            if (!allowsImplementationUpstream(owner.modelType(), target)) {
                return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
            }
            return reachesOwner(tenantId, owner.id(), target, new HashSet<>())
                ? ValidationResult.invalid("MODEL_IMPLEMENTATION_SELF_REFERENCE")
                : ValidationResult.ok();
        } catch (RuntimeException unavailable) {
            return ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_STALE");
        }
    }

    private ModelLifecycleContract.ImplementationView currentImplementation(String tenantId, UUID modelSpecId) {
        if (lifecycle == null) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_INPUT_STALE",
                "The upstream implementation is unavailable",
                ModelSpecException.Kind.CONFLICT
            );
        }
        ModelLifecycleContract.ImplementationView implementation = lifecycle.findImplementation(tenantId, modelSpecId).orElse(null);
        if (
            implementation == null ||
            !"ACTIVE".equals(implementation.status()) ||
            implementation.implementationRevision() < 1 ||
            isBlank(implementation.implementationChecksum()) ||
            isBlank(implementation.dbtUniqueId())
        ) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_INPUT_STALE",
                "The upstream implementation is unavailable",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return implementation;
    }

    private boolean reachesOwner(String tenantId, UUID ownerId, ModelSpecView candidate, Set<ModelRevisionRef> visited) {
        for (ModelRevisionRef dependency : candidate.dependsOn()) {
            if (Objects.equals(ownerId, dependency.modelSpecId())) return true;
            if (!visited.add(dependency)) continue;
            try {
                if (reachesOwner(tenantId, ownerId, modelSpecs.revision(tenantId, dependency), visited)) return true;
            } catch (RuntimeException unavailable) {
                return true;
            }
        }
        return false;
    }

    private static ValidationResult validateGenerated(GeneratedInput input) {
        return input != null && DATE_DIMENSION_GENERATOR.equals(input.generatorType())
            ? ValidationResult.ok()
            : ValidationResult.invalid("MODEL_IMPLEMENTATION_INPUT_KIND_NOT_ALLOWED");
    }

    private static boolean allows(ModelType type, InputMode mode) {
        if (type == null || mode == null) return false;
        return switch (type) {
            case DIMENSION -> true;
            case FACT -> mode == InputMode.PHYSICAL_ASSET || mode == InputMode.UPSTREAM_MODEL;
            case SUMMARY, APPLICATION -> mode == InputMode.UPSTREAM_MODEL;
        };
    }

    private static boolean allowsImplementationUpstream(ModelType ownerType, ModelSpecView upstream) {
        if (ownerType == null || !ModelSpecContract.isCanonicalModel(upstream)) return false;
        return switch (ownerType) {
            case DIMENSION -> upstream.modelType() == ModelType.DIMENSION || upstream.modelType() == ModelType.FACT;
            case FACT -> upstream.modelType() == ModelType.FACT;
            case SUMMARY -> upstream.modelType() != ModelType.APPLICATION;
            case APPLICATION -> true;
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CompatibilityProjection(boolean valid, InputMode inputMode, List<ImplementationInput> inputs, String code) {
        static CompatibilityProjection of(InputMode inputMode, List<ImplementationInput> inputs) {
            return new CompatibilityProjection(true, inputMode, List.copyOf(inputs), null);
        }

        static CompatibilityProjection invalid(String code) {
            return new CompatibilityProjection(false, null, List.of(), code);
        }
    }

    public record ValidationResult(boolean valid, String code) {
        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult invalid(String code) {
            return new ValidationResult(false, code);
        }
    }
}
