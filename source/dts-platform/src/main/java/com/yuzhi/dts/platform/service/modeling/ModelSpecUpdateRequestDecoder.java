package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** The single production entry for decoding and validating canonical ModelSpec update requests. */
@Component
public final class ModelSpecUpdateRequestDecoder {

    private static final TypeReference<Map<String, Object>> RAW_REQUEST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final ObjectReader strictReader;

    public ModelSpecUpdateRequestDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.strictReader = objectMapper
            .readerFor(ModelSpecContract.UpdateModelSpecCommand.class)
            .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public DecodeResult decode(JsonNode request) {
        return decode(request, false);
    }

    /** Decodes a complete logical definition while deferring only the derived-model upstream requirement. */
    public DecodeResult decodeDefinition(JsonNode request) {
        return decode(request, true);
    }

    private DecodeResult decode(JsonNode request, boolean definitionOnly) {
        if (request == null || !request.isObject()) return rejected(requestIssue("ModelSpec update request must be a JSON object"));
        if (request.has("implementationPolicy")) {
            return rejected(
                new ModelSpecContract.FieldIssue(
                    "MODEL_SPEC_IMPLEMENTATION_POLICY_MOVED",
                    "implementationPolicy",
                    ModelSpecContract.IssueSeverity.ERROR,
                    "Physical target, load, partition and retention settings are maintained in data implementation"
                )
            );
        }

        Map<String, Object> raw;
        try {
            raw = objectMapper.convertValue(request, RAW_REQUEST_TYPE);
        } catch (IllegalArgumentException exception) {
            return rejected(requestIssue("ModelSpec update request cannot be decoded"));
        }

        List<ModelSpecContract.FieldIssue> shapeIssues = ModelSpecContract.validateUpdateShape(raw);
        if (!shapeIssues.isEmpty()) return new DecodeResult(null, shapeIssues);

        ModelSpecContract.UpdateModelSpecCommand command;
        try (JsonParser parser = request.traverse(objectMapper)) {
            command = strictReader.readValue(parser);
        } catch (IOException | IllegalArgumentException exception) {
            return rejected(requestIssue("ModelSpec update request cannot be decoded"));
        }

        List<ModelSpecContract.FieldIssue> semanticIssues = definitionOnly
            ? ModelSpecContract.validateDefinitionUpdate(command)
            : ModelSpecContract.validateUpdate(command);
        return semanticIssues.isEmpty() ? new DecodeResult(command, List.of()) : new DecodeResult(null, semanticIssues);
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
        ModelSpecContract.UpdateModelSpecCommand command,
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
