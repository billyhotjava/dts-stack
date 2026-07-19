package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
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
        return toView(id, command, ModelStatus.DRAFT, 1, contentChecksum(command), now, now);
    }

    public ModelSpecView toUpdatedView(ModelSpecView current, UpdateModelSpecCommand command, int revision, Instant now) {
        return toView(
            current.id(),
            ModelSpecContract.asCreate(command),
            current.status(),
            revision,
            contentChecksum(command),
            current.createdAt(),
            now
        );
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
        Instant updatedAt
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
            status,
            revision,
            checksum,
            createdAt,
            updatedAt,
            CompatibilityMode.CANONICAL,
            null
        );
    }

    private static ModelContent content(CreateModelSpecCommand command) {
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
            command.fields(),
            command.sourceRefs(),
            command.dependsOn(),
            command.dimensionRefs(),
            command.metricRefs(),
            command.standardBindings(),
            command.generationStrategy()
        );
    }

    private static ModelContent content(ModelSpecView view) {
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
            view.fields(),
            view.sourceRefs(),
            view.dependsOn(),
            view.dimensionRefs(),
            view.metricRefs(),
            view.standardBindings(),
            view.generationStrategy()
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
        java.util.List<ModelSpecContract.ModelField> fields,
        java.util.List<ModelSpecContract.SourceRef> sourceRefs,
        java.util.List<ModelSpecContract.ModelRevisionRef> dependsOn,
        java.util.List<ModelSpecContract.ModelRevisionRef> dimensionRefs,
        java.util.List<ModelSpecContract.MetricRef> metricRefs,
        java.util.List<ModelSpecContract.StandardBinding> standardBindings,
        ModelSpecContract.GenerationStrategy generationStrategy
    ) {}
}
