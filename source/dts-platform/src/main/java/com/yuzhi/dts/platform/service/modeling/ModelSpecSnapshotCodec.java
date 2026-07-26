package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionProfile;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Owns deterministic content hashing and immutable revision/response snapshots. */
@Component
public class ModelSpecSnapshotCodec {

    private final ObjectMapper objectMapper;
    private final ObjectWriter canonicalWriter;

    public ModelSpecSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
    }

    public String requestHash(CreateModelSpecCommand command) {
        return sha256(writeCanonical(content(command)));
    }

    public String contentChecksum(CreateModelSpecCommand command) {
        return sha256(writeCanonical(content(command)));
    }

    public String contentChecksum(UpdateModelSpecCommand command) {
        return contentChecksum(ModelSpecContract.asCreate(command));
    }

    public String contentChecksum(ModelSpecView view) {
        return sha256(writeCanonical(content(view)));
    }

    public ModelSpecView toCreatedView(UUID id, CreateModelSpecCommand command, Instant now) {
        return toView(id, command, ModelStatus.DRAFT, 1, contentChecksum(command), now, now, command.dimensionDefinitionRef());
    }

    public ModelSpecView toUpdatedView(ModelSpecView current, UpdateModelSpecCommand command, int revision, Instant now) {
        CreateModelSpecCommand updatedContent = asCreate(command, current);
        return toView(
            current.id(),
            updatedContent,
            current.status(),
            revision,
            contentChecksum(updatedContent),
            current.createdAt(),
            now,
            current.dimensionDefinitionRef()
        );
    }

    public ModelSpecView toLifecycleView(ModelSpecView current, ModelStatus status, int revision, Instant now) {
        return toView(
            current.id(),
            asCreate(current),
            status,
            revision,
            contentChecksum(current),
            current.createdAt(),
            now,
            current.dimensionDefinitionRef()
        );
    }

    private static CreateModelSpecCommand asCreate(ModelSpecView view) {
        return new CreateModelSpecCommand(
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile(),
            view.dimensionDefinitionRef(),
            null,
            view.dataMartId(),
            view.variantCode(),
            view.implementationPolicy()
        );
    }

    private static CreateModelSpecCommand asCreate(UpdateModelSpecCommand command, DimensionDefinitionRef dimensionDefinitionRef) {
        return new CreateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            dimensionDefinitionRef,
            null,
            command.dataMartId(),
            command.variantCode(),
            command.implementationPolicy()
        );
    }

    private static CreateModelSpecCommand asCreate(UpdateModelSpecCommand command, ModelSpecView current) {
        DimensionProfile updatedProfile = command.dimensionProfile();
        DimensionProfile currentProfile = current.dimensionProfile();
        if (
            currentProfile != null &&
            (currentProfile.dimensionCode() != null || currentProfile.reuseScope() != null)
        ) {
            DimensionProfile logicalProfile = updatedProfile == null ? currentProfile : updatedProfile;
            updatedProfile = new DimensionProfile(
                currentProfile.dimensionCode(),
                logicalProfile.hierarchies(),
                logicalProfile.scdPolicy(),
                currentProfile.reuseScope()
            );
        }
        UpdateModelSpecCommand effectiveCommand = new UpdateModelSpecCommand(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            updatedProfile,
            command.dataMartId() == null ? current.dataMartId() : command.dataMartId(),
            command.variantCode() == null ? current.variantCode() : command.variantCode(),
            command.implementationPolicy() == null ? current.implementationPolicy() : command.implementationPolicy()
        );
        return asCreate(effectiveCommand, current.dimensionDefinitionRef());
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ModelSpecException(
                "MODEL_SPEC_SNAPSHOT_INVALID",
                "ModelSpec snapshot cannot be serialized",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
    }

    public ModelSpecView readView(String json) {
        try {
            return objectMapper.readValue(json, ModelSpecView.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ModelSpecException(
                "MODEL_SPEC_SNAPSHOT_INVALID",
                "ModelSpec snapshot cannot be read",
                ModelSpecException.Kind.CONFLICT
            );
        }
    }

    private String writeCanonical(Object value) {
        try {
            return canonicalWriter.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ModelSpecException(
                "MODEL_SPEC_SNAPSHOT_INVALID",
                "ModelSpec content cannot be serialized for hashing",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
    }

    private static ModelSpecView toView(
        UUID id,
        CreateModelSpecCommand command,
        ModelStatus status,
        int revision,
        String checksum,
        Instant createdAt,
        Instant updatedAt,
        DimensionDefinitionRef dimensionDefinitionRef
    ) {
        return new ModelSpecView(
            ModelSpecContract.CONTRACT_VERSION,
            id,
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            dimensionDefinitionRef,
            status,
            revision,
            checksum,
            createdAt,
            updatedAt,
            CompatibilityMode.CANONICAL,
            null,
            command.dataMartId(),
            command.variantCode(),
            command.implementationPolicy()
        );
    }

    private static Object content(CreateModelSpecCommand command) {
        if (hasExtendedMetadata(command)) {
            return new ExtendedModelContent(
                command.planId(),
                command.domainId(),
                command.modelType(),
                command.layer(),
                command.name(),
                command.description(),
                command.implementationMode(),
                command.materialization(),
                command.businessActivityRef(),
                command.consumptionScenario(),
                command.grain(),
                command.factShape(),
                command.timeSemantics(),
                command.fields(),
                command.sourceRefs(),
                command.dependsOn(),
                command.dimensionRefs(),
                command.metricRefs(),
                command.standardBindings(),
                command.generationStrategy(),
                command.dimensionProfile(),
                command.dimensionDefinitionRef(),
                command.dataMartId(),
                command.variantCode(),
                command.implementationPolicy()
            );
        }
        return new ModelContent(
            command.planId(),
            command.domainId(),
            command.modelType(),
            command.layer(),
            command.name(),
            command.description(),
            command.implementationMode(),
            command.materialization(),
            command.businessActivityRef(),
            command.consumptionScenario(),
            command.grain(),
            command.factShape(),
            command.timeSemantics(),
            legacyFields(command.fields()),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy(),
            command.dimensionProfile(),
            command.dimensionDefinitionRef()
        );
    }

    private static Object content(ModelSpecView view) {
        if (hasExtendedMetadata(view)) {
            return new ExtendedModelContent(
                view.planId(),
                view.domainId(),
                view.modelType(),
                view.layer(),
                view.name(),
                view.description(),
                view.implementationMode(),
                view.materialization(),
                view.businessActivityRef(),
                view.consumptionScenario(),
                view.grain(),
                view.factShape(),
                view.timeSemantics(),
                view.fields(),
                view.sourceRefs(),
                view.dependsOn(),
                view.dimensionRefs(),
                view.metricRefs(),
                view.standardBindings(),
                view.generationStrategy(),
                view.dimensionProfile(),
                view.dimensionDefinitionRef(),
                view.dataMartId(),
                view.variantCode(),
                view.implementationPolicy()
            );
        }
        return new ModelContent(
            view.planId(),
            view.domainId(),
            view.modelType(),
            view.layer(),
            view.name(),
            view.description(),
            view.implementationMode(),
            view.materialization(),
            view.businessActivityRef(),
            view.consumptionScenario(),
            view.grain(),
            view.factShape(),
            view.timeSemantics(),
            legacyFields(view.fields()),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy(),
            view.dimensionProfile(),
            view.dimensionDefinitionRef()
        );
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static boolean hasExtendedMetadata(CreateModelSpecCommand command) {
        return (
            command.dataMartId() != null ||
            command.variantCode() != null ||
            command.implementationPolicy() != null ||
            command.fields().stream().anyMatch(ModelSpecSnapshotCodec::hasExtendedMetadata)
        );
    }

    private static boolean hasExtendedMetadata(ModelSpecView view) {
        return (
            view.dataMartId() != null ||
            view.variantCode() != null ||
            view.implementationPolicy() != null ||
            view.fields().stream().anyMatch(ModelSpecSnapshotCodec::hasExtendedMetadata)
        );
    }

    private static boolean hasExtendedMetadata(ModelSpecContract.ModelField field) {
        return (
            field != null &&
            (field.dimensionAttributeCode() != null ||
                Boolean.TRUE.equals(field.redundant()) ||
                field.redundancySourceRef() != null)
        );
    }

    private static java.util.List<LegacyModelField> legacyFields(
        java.util.List<ModelSpecContract.ModelField> fields
    ) {
        return fields
            .stream()
            .map(field ->
                field == null
                    ? null
                    : new LegacyModelField(
                        field.name(),
                        field.dataType(),
                        field.nullable(),
                        field.sourceFieldRef(),
                        field.role(),
                        field.securityLevel()
                    )
            )
            .toList();
    }

    private record ModelContent(
        UUID planId,
        UUID domainId,
        ModelSpecContract.ModelType modelType,
        ModelSpecContract.Layer layer,
        String name,
        String description,
        ModelSpecContract.ImplementationMode implementationMode,
        String materialization,
        String businessActivityRef,
        String consumptionScenario,
        ModelSpecContract.Grain grain,
        ModelSpecContract.FactShape factShape,
        ModelSpecContract.TimeSemantics timeSemantics,
        java.util.List<LegacyModelField> fields,
        java.util.List<ModelSpecContract.SourceRef> sourceRefs,
        java.util.List<ModelSpecContract.ModelRevisionRef> dependsOn,
        java.util.List<ModelSpecContract.ModelRevisionRef> dimensionRefs,
        java.util.List<ModelSpecContract.MetricRef> metricRefs,
        java.util.List<ModelSpecContract.StandardBinding> standardBindings,
        ModelSpecContract.GenerationStrategy generationStrategy,
        @JsonInclude(JsonInclude.Include.NON_NULL) ModelSpecContract.DimensionProfile dimensionProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionDefinitionRef dimensionDefinitionRef
    ) {}

    private record LegacyModelField(
        String name,
        String dataType,
        Boolean nullable,
        String sourceFieldRef,
        ModelSpecContract.FieldRole role,
        String securityLevel
    ) {}

    private record ExtendedModelContent(
        UUID planId,
        UUID domainId,
        ModelSpecContract.ModelType modelType,
        ModelSpecContract.Layer layer,
        String name,
        String description,
        ModelSpecContract.ImplementationMode implementationMode,
        String materialization,
        String businessActivityRef,
        String consumptionScenario,
        ModelSpecContract.Grain grain,
        ModelSpecContract.FactShape factShape,
        ModelSpecContract.TimeSemantics timeSemantics,
        java.util.List<ModelSpecContract.ModelField> fields,
        java.util.List<ModelSpecContract.SourceRef> sourceRefs,
        java.util.List<ModelSpecContract.ModelRevisionRef> dependsOn,
        java.util.List<ModelSpecContract.ModelRevisionRef> dimensionRefs,
        java.util.List<ModelSpecContract.MetricRef> metricRefs,
        java.util.List<ModelSpecContract.StandardBinding> standardBindings,
        ModelSpecContract.GenerationStrategy generationStrategy,
        @JsonInclude(JsonInclude.Include.NON_NULL) ModelSpecContract.DimensionProfile dimensionProfile,
        @JsonInclude(JsonInclude.Include.NON_NULL) DimensionDefinitionRef dimensionDefinitionRef,
        @JsonInclude(JsonInclude.Include.NON_NULL) UUID dataMartId,
        String variantCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) ModelSpecContract.ImplementationPolicy implementationPolicy
    ) {}
}
