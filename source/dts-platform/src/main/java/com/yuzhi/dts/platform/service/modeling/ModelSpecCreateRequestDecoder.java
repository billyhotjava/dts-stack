package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The single production entry for decoding and validating canonical ModelSpec create requests. */
@Component
public final class ModelSpecCreateRequestDecoder {

    private static final TypeReference<Map<String, Object>> RAW_REQUEST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;

    public ModelSpecCreateRequestDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictReader = objectMapper
            .readerFor(ModelSpecContract.CreateModelSpecCommand.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public DecodeResult decode(JsonNode request) {
        if (request == null || !request.isObject()) {
            return rejected(requestIssue("ModelSpec create request must be a JSON object"));
        }

        ObjectNode normalized = ((ObjectNode) request).deepCopy();
        applyCreateDefaults(normalized);
        Map<String, Object> raw;
        try {
            raw = objectMapper.convertValue(normalized, RAW_REQUEST_TYPE);
        } catch (IllegalArgumentException exception) {
            return rejected(requestIssue("ModelSpec create request cannot be decoded"));
        }

        List<ModelSpecContract.FieldIssue> shapeIssues = ModelSpecContract.validateCreateShape(raw);
        if (!shapeIssues.isEmpty()) return new DecodeResult(null, shapeIssues);

        ModelSpecContract.CreateModelSpecCommand command;
        try (JsonParser parser = normalized.traverse(objectMapper)) {
            command = strictReader.readValue(parser);
        } catch (IOException | IllegalArgumentException exception) {
            return rejected(requestIssue("ModelSpec create request cannot be decoded"));
        }

        List<ModelSpecContract.FieldIssue> semanticIssues = ModelSpecContract.validateInteractiveCreate(command);
        return semanticIssues.isEmpty() ? new DecodeResult(command, List.of()) : new DecodeResult(null, semanticIssues);
    }

    private static void applyCreateDefaults(ObjectNode request) {
        if (!request.has("implementationMode")) {
            request.put("implementationMode", ModelSpecContract.ImplementationMode.DESIGNER_GENERATED.name());
        }
        if (request.has("layer") || !request.hasNonNull("modelType") || !request.get("modelType").isTextual()) return;
        try {
            ModelSpecContract.ModelType modelType = ModelSpecContract.ModelType.valueOf(request.get("modelType").textValue());
            request.put("layer", ModelSpecContract.targetLayer(modelType).name());
        } catch (IllegalArgumentException ignored) {
            // Shape validation below owns the stable invalid-model-type response.
        }
    }

    private static DecodeResult rejected(ModelSpecContract.FieldIssue issue) {
        return new DecodeResult(null, List.of(issue));
    }

    private static ModelSpecContract.FieldIssue requestIssue(String message) {
        return new ModelSpecContract.FieldIssue(
            "MODEL_SPEC_REQUEST_INVALID",
            "$",
            ModelSpecContract.IssueSeverity.ERROR,
            message
        );
    }

    public record DecodeResult(
        ModelSpecContract.CreateModelSpecCommand command,
        List<ModelSpecContract.FieldIssue> issues
    ) {
        public DecodeResult {
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (!issues.isEmpty()) command = null;
        }

        public boolean valid() {
            return command != null && issues.isEmpty();
        }
    }
}
