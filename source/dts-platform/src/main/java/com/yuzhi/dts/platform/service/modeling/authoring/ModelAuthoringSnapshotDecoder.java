package com.yuzhi.dts.platform.service.modeling.authoring;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.FieldMapping;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.GeneratedInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.InputMode;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.PhysicalAssetInput;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.UpstreamModelInput;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.IssueSeverity;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecUpdateRequestDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Strict decoder for the versioned logical plus structured-implementation authoring snapshot. */
public final class ModelAuthoringSnapshotDecoder {

    private static final TypeReference<List<FieldMapping>> FIELD_MAPPINGS = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> SETTINGS = new TypeReference<>() {};
    private final ObjectMapper objectMapper;
    private final ModelSpecUpdateRequestDecoder modelDecoder;

    public ModelAuthoringSnapshotDecoder(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.modelDecoder = new ModelSpecUpdateRequestDecoder(objectMapper);
    }

    public DecodeResult decode(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject()) return rejected("Model authoring snapshot must be a JSON object");
        boolean versioned = snapshot.has("modelSpec") || snapshot.has("schemaVersion") || snapshot.has("visualImplementation");
        JsonNode modelNode = versioned ? snapshot.path("modelSpec") : snapshot;
        var model = modelDecoder.decode(modelNode);
        if (!model.valid()) return new DecodeResult(null, null, model.issues());
        if (!versioned || !snapshot.hasNonNull("visualImplementation")) {
            return new DecodeResult(model.command(), null, List.of());
        }
        if (snapshot.path("schemaVersion").asInt(0) != 1) return rejected("Unsupported model authoring snapshot version");
        try {
            return new DecodeResult(model.command(), visual(snapshot.path("visualImplementation")), List.of());
        } catch (RuntimeException failure) {
            return new DecodeResult(
                null,
                null,
                List.of(
                    new FieldIssue(
                        "MODEL_AUTHORING_VISUAL_IMPLEMENTATION_INVALID",
                        "visualImplementation",
                        IssueSeverity.ERROR,
                        "Structured implementation snapshot is invalid"
                    )
                )
            );
        }
    }

    private VisualImplementationSnapshot visual(JsonNode value) {
        if (!value.isObject()) throw new IllegalArgumentException("visualImplementation must be an object");
        String projectKey = text(value, "projectKey");
        String dbtUniqueId = text(value, "dbtUniqueId");
        InputMode inputMode = InputMode.valueOf(text(value, "inputMode").toUpperCase());
        JsonNode inputNodes = value.path("inputs");
        if (!inputNodes.isArray() || inputNodes.isEmpty()) throw new IllegalArgumentException("inputs are required");
        List<ImplementationInput> inputs = new ArrayList<>();
        inputNodes.forEach(input -> inputs.add(input(inputMode, input)));
        List<FieldMapping> mappings = value.has("fieldMappings")
            ? objectMapper.convertValue(value.path("fieldMappings"), FIELD_MAPPINGS)
            : List.of();
        Map<String, Object> settings = value.has("settings")
            ? objectMapper.convertValue(value.path("settings"), SETTINGS)
            : Map.of();
        ImplementationMode ownership = ImplementationMode.valueOf(text(value, "ownership").toUpperCase());
        SaveImplementationCommand command = new SaveImplementationCommand(
            inputMode,
            inputs,
            mappings,
            settings,
            ownership,
            text(value, "materialization"),
            text(value, "idempotencyKey")
        );
        return new VisualImplementationSnapshot(projectKey, dbtUniqueId, command);
    }

    private ImplementationInput input(InputMode mode, JsonNode value) {
        if (value == null || !value.isObject()) throw new IllegalArgumentException("implementation input is invalid");
        return switch (mode) {
            case PHYSICAL_ASSET -> new PhysicalAssetInput(
                UUID.fromString(text(value, "sourceBindingId")),
                text(value, "resolvedVersion")
            );
            case UPSTREAM_MODEL -> new UpstreamModelInput(
                UUID.fromString(text(value, "modelSpecId")),
                positive(value, "revision"),
                text(value, "checksum"),
                value.path("implementationRevision").asInt(0),
                nullableText(value, "implementationChecksum"),
                nullableText(value, "dbtUniqueId")
            );
            case GENERATED -> new GeneratedInput(
                text(value, "generatorType"),
                value.has("config")
                    ? objectMapper.convertValue(value.path("config"), SETTINGS)
                    : Map.of()
            );
        };
    }

    private static int positive(JsonNode value, String field) {
        int result = value.path(field).asInt(0);
        if (result < 1) throw new IllegalArgumentException(field + " must be positive");
        return result;
    }

    private static String text(JsonNode value, String field) {
        String result = nullableText(value, field);
        if (result == null) throw new IllegalArgumentException(field + " is required");
        return result;
    }

    private static String nullableText(JsonNode value, String field) {
        if (!value.hasNonNull(field) || !value.path(field).isTextual()) return null;
        String result = value.path(field).textValue().trim();
        return result.isEmpty() ? null : result;
    }

    private static DecodeResult rejected(String message) {
        return new DecodeResult(
            null,
            null,
            List.of(new FieldIssue("MODEL_AUTHORING_SNAPSHOT_INVALID", "$", IssueSeverity.ERROR, message))
        );
    }

    public record VisualImplementationSnapshot(
        String projectKey,
        String dbtUniqueId,
        SaveImplementationCommand command
    ) {}

    public record DecodeResult(
        UpdateModelSpecCommand modelSpec,
        VisualImplementationSnapshot visualImplementation,
        List<FieldIssue> issues
    ) {
        public DecodeResult {
            issues = List.copyOf(issues == null ? List.of() : issues);
            if (!issues.isEmpty()) {
                modelSpec = null;
                visualImplementation = null;
            }
        }

        public boolean valid() {
            return modelSpec != null && issues.isEmpty();
        }
    }
}
