package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.CreateCommand;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Strict boundary decoder for the atomic dimension-definition plus complete ModelSpec command. */
@Component
public final class DimensionModelCreateRequestDecoder {

    private static final String INTERNAL_IDEMPOTENCY_PREFIX = "dm:v2:";
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of("operationId", "definitionBinding", "modelSpec");
    private static final Set<String> CREATE_BINDING_FIELDS = Set.of("mode", "definition");
    private static final Set<String> EXISTING_BINDING_FIELDS = Set.of("mode", "dimensionDefinitionRef");
    private static final int MAX_JSON_DEPTH = 16;
    private static final int MAX_JSON_NODES = 5_000;
    private static final int MAX_TEXT_LENGTH = 8_192;
    private static final int MAX_HIERARCHIES = 32;
    private static final int MAX_LEVELS_PER_HIERARCHY = 32;

    private final ObjectMapper strictObjectMapper;
    private final ModelSpecCreateRequestDecoder modelSpecCreateDecoder;
    private final ModelSpecUpdateRequestDecoder modelSpecUpdateDecoder;

    public DimensionModelCreateRequestDecoder(
        ObjectMapper objectMapper,
        ModelSpecCreateRequestDecoder modelSpecCreateDecoder,
        ModelSpecUpdateRequestDecoder modelSpecUpdateDecoder
    ) {
        this.strictObjectMapper = strictObjectMapper(objectMapper);
        this.modelSpecCreateDecoder = modelSpecCreateDecoder;
        this.modelSpecUpdateDecoder = modelSpecUpdateDecoder;
    }

    public PreparedCreate decode(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw rejected("DIMENSION_MODEL_REQUEST_OBJECT_REQUIRED", "A JSON object is required", null);
        }
        enforceJsonBudget(request);
        rejectUnknownFields(request, TOP_LEVEL_FIELDS, "DIMENSION_MODEL_FIELD_NOT_ALLOWED");

        UUID operationId = operationId(request.get("operationId"));
        ObjectNode bindingNode = object(request.get("definitionBinding"), "definitionBinding");
        ObjectNode modelNode = object(request.get("modelSpec"), "modelSpec");
        if (modelNode.has("idempotencyKey") || modelNode.has("dimensionDefinitionRef")) {
            throw rejected(
                "DIMENSION_MODEL_SUBCOMMAND_IDENTITY_FORBIDDEN",
                "Idempotency keys and dimension references are owned by the server",
                null
            );
        }

        UpdateModelSpecCommand modelSpec = decodeCompleteModel(modelNode);
        DefinitionBinding definitionBinding = decodeBinding(operationId, bindingNode, modelSpec);
        return new PreparedCreate(operationId, definitionBinding, modelSpec);
    }

    public CreateModelSpecCommand resolveModelCreateCommand(
        PreparedCreate prepared,
        DimensionDefinitionRef dimensionDefinitionRef
    ) {
        if (prepared == null || dimensionDefinitionRef == null) {
            throw rejected(
                "DIMENSION_MODEL_INTERNAL_REFERENCE_REQUIRED",
                "A prepared request and confirmed definition reference are required",
                null
            );
        }
        UpdateModelSpecCommand model = prepared.modelSpec();
        ObjectNode seed = strictObjectMapper.createObjectNode();
        seed.put("planId", model.planId().toString());
        seed.put("domainId", model.domainId().toString());
        seed.put("modelType", ModelType.DIMENSION.name());
        seed.put("name", model.name());
        putNullable(seed, "description", model.description());
        putNullable(seed, "dataMartId", model.dataMartId());
        putNullable(seed, "variantCode", model.variantCode());
        seed.put("idempotencyKey", modelIdempotencyKey(prepared.operationId()));
        ObjectNode reference = seed.putObject("dimensionDefinitionRef");
        reference.put("dimensionDefinitionId", dimensionDefinitionRef.dimensionDefinitionId().toString());
        reference.put("revision", dimensionDefinitionRef.revision());

        ModelSpecCreateRequestDecoder.DecodeResult decoded = modelSpecCreateDecoder.decode(seed);
        if (!decoded.valid()) {
            throw rejected(
                "DIMENSION_MODEL_MODEL_SPEC_INVALID",
                "ModelSpec seed contains invalid fields",
                decoded.issues()
            );
        }
        validateDefinitionContext(prepared.definitionBinding().definition(), decoded.command());
        return decoded.command();
    }

    public UUID parseOperationId(String value) {
        if (value == null) {
            throw rejected("DIMENSION_MODEL_OPERATION_ID_INVALID", "operationId must be a canonical UUID", null);
        }
        return operationId(strictObjectMapper.getNodeFactory().textNode(value));
    }

    static String definitionIdempotencyKey(UUID operationId) {
        return INTERNAL_IDEMPOTENCY_PREFIX + "dimension:" + operationId;
    }

    static String modelIdempotencyKey(UUID operationId) {
        return INTERNAL_IDEMPOTENCY_PREFIX + "model:" + operationId;
    }

    public static boolean isInternalIdempotencyKey(String idempotencyKey) {
        return idempotencyKey != null && idempotencyKey.startsWith(INTERNAL_IDEMPOTENCY_PREFIX);
    }

    private DefinitionBinding decodeBinding(
        UUID operationId,
        ObjectNode binding,
        UpdateModelSpecCommand modelSpec
    ) {
        JsonNode modeNode = binding.get("mode");
        if (modeNode == null || !modeNode.isTextual()) {
            throw rejected("DIMENSION_MODEL_BINDING_MODE_INVALID", "definitionBinding.mode is required", null);
        }
        BindingMode mode;
        try {
            mode = BindingMode.valueOf(modeNode.textValue());
        } catch (IllegalArgumentException exception) {
            throw rejected("DIMENSION_MODEL_BINDING_MODE_INVALID", "definitionBinding.mode is unsupported", null);
        }
        if (mode == BindingMode.CREATE) {
            rejectUnknownFields(binding, CREATE_BINDING_FIELDS, "DIMENSION_MODEL_BINDING_FIELD_NOT_ALLOWED");
            ObjectNode definitionNode = object(binding.get("definition"), "definitionBinding.definition");
            rejectNonIntegralHierarchyOrders(definitionNode);
            enforceHierarchyBudget(definitionNode);
            ObjectNode normalized = definitionNode.deepCopy();
            if (normalized.has("idempotencyKey")) {
                throw rejected(
                    "DIMENSION_MODEL_SUBCOMMAND_IDENTITY_FORBIDDEN",
                    "Definition idempotency is owned by the server",
                    null
                );
            }
            normalized.put("idempotencyKey", definitionIdempotencyKey(operationId));
            CreateCommand definition = decodeDefinition(normalized);
            validateDefinitionContext(definition, modelSpec);
            return new DefinitionBinding(mode, definition, null);
        }

        rejectUnknownFields(binding, EXISTING_BINDING_FIELDS, "DIMENSION_MODEL_BINDING_FIELD_NOT_ALLOWED");
        ObjectNode referenceNode = object(
            binding.get("dimensionDefinitionRef"),
            "definitionBinding.dimensionDefinitionRef"
        );
        DimensionDefinitionRef reference;
        try {
            reference = strictObjectMapper.treeToValue(referenceNode, DimensionDefinitionRef.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw rejected(
                "DIMENSION_MODEL_DEFINITION_REFERENCE_INVALID",
                "Existing dimension reference cannot be decoded",
                null
            );
        }
        if (reference.dimensionDefinitionId() == null || reference.revision() < 1) {
            throw rejected(
                "DIMENSION_MODEL_DEFINITION_REFERENCE_INVALID",
                "Existing dimension reference must contain an id and positive revision",
                null
            );
        }
        return new DefinitionBinding(mode, null, reference);
    }

    private UpdateModelSpecCommand decodeCompleteModel(ObjectNode modelNode) {
        ModelSpecUpdateRequestDecoder.DecodeResult decoded = modelSpecUpdateDecoder.decode(modelNode);
        if (!decoded.valid()) {
            throw rejected(
                "DIMENSION_MODEL_MODEL_SPEC_INVALID",
                "Complete ModelSpec contains invalid fields",
                decoded.issues()
            );
        }
        UpdateModelSpecCommand command = decoded.command();
        if (
            command.modelType() != ModelType.DIMENSION ||
            command.layer() != Layer.DWD ||
            command.implementationMode() != ImplementationMode.DESIGNER_GENERATED
        ) {
            throw rejected(
                "DIMENSION_MODEL_FIXED_CONTEXT_REQUIRED",
                "Dimension models must use DIMENSION, DWD and DESIGNER_GENERATED",
                null
            );
        }
        return command;
    }

    private CreateCommand decodeDefinition(ObjectNode normalized) {
        CreateCommand command;
        try {
            command = strictObjectMapper.treeToValue(normalized, CreateCommand.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw rejected("DIMENSION_MODEL_DEFINITION_INVALID", "Dimension definition cannot be decoded", null);
        }
        List<FieldIssue> issues = DimensionDefinitionContract.validateCreate(command);
        if (!issues.isEmpty()) {
            throw rejected(
                "DIMENSION_MODEL_DEFINITION_INVALID",
                "Dimension definition contains invalid fields",
                issues
            );
        }
        return command;
    }

    private static void validateDefinitionContext(CreateCommand definition, CreateModelSpecCommand modelSpec) {
        if (definition == null) return;
        if (!definition.domainId().equals(modelSpec.domainId())) {
            throw rejected(
                "DIMENSION_MODEL_DOMAIN_MISMATCH",
                "Dimension definition and ModelSpec must belong to the same business category",
                null
            );
        }
        if (!java.util.Objects.equals(definition.dataMartId(), modelSpec.dataMartId())) {
            throw rejected(
                "DIMENSION_MODEL_DATA_MART_MISMATCH",
                "Dimension definition and ModelSpec must use the same data mart context",
                null
            );
        }
    }

    private static void validateDefinitionContext(CreateCommand definition, UpdateModelSpecCommand modelSpec) {
        if (definition == null) return;
        if (!definition.domainId().equals(modelSpec.domainId())) {
            throw rejected(
                "DIMENSION_MODEL_DOMAIN_MISMATCH",
                "Dimension definition and ModelSpec must belong to the same business category",
                null
            );
        }
        if (!java.util.Objects.equals(definition.dataMartId(), modelSpec.dataMartId())) {
            throw rejected(
                "DIMENSION_MODEL_DATA_MART_MISMATCH",
                "Dimension definition and ModelSpec must use the same data mart context",
                null
            );
        }
    }

    private UUID operationId(JsonNode value) {
        if (value == null || !value.isTextual()) {
            throw rejected("DIMENSION_MODEL_OPERATION_ID_INVALID", "operationId must be a canonical UUID", null);
        }
        String text = value.textValue().trim();
        try {
            UUID parsed = UUID.fromString(text);
            if (!parsed.toString().equals(text)) throw new IllegalArgumentException("non-canonical UUID");
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw rejected("DIMENSION_MODEL_OPERATION_ID_INVALID", "operationId must be a canonical UUID", null);
        }
    }

    private static void rejectUnknownFields(JsonNode node, Set<String> allowed, String code) {
        List<String> rejectedFields = new ArrayList<>();
        node.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) rejectedFields.add(field);
        });
        if (!rejectedFields.isEmpty()) {
            throw rejected(code, "Field is not allowed at the dimension-model boundary", List.copyOf(rejectedFields));
        }
    }

    private static ObjectNode object(JsonNode value, String field) {
        if (value == null || !value.isObject()) {
            throw rejected(
                "DIMENSION_MODEL_FIELD_OBJECT_REQUIRED",
                field + " must be a JSON object",
                List.of(field)
            );
        }
        return (ObjectNode) value;
    }

    private static void enforceHierarchyBudget(JsonNode definition) {
        JsonNode hierarchies = definition.get("hierarchies");
        if (hierarchies == null) return;
        if (!hierarchies.isArray() || hierarchies.size() > MAX_HIERARCHIES) {
            throw rejected(
                "DIMENSION_MODEL_REQUEST_LIMIT_EXCEEDED",
                "Dimension hierarchy count exceeds the fixed request budget",
                null
            );
        }
        for (JsonNode hierarchy : hierarchies) {
            JsonNode levels = hierarchy.get("levels");
            if (levels != null && (!levels.isArray() || levels.size() > MAX_LEVELS_PER_HIERARCHY)) {
                throw rejected(
                    "DIMENSION_MODEL_REQUEST_LIMIT_EXCEEDED",
                    "Dimension hierarchy level count exceeds the fixed request budget",
                    null
                );
            }
        }
    }

    private static void enforceJsonBudget(JsonNode root) {
        ArrayDeque<NodeDepth> pending = new ArrayDeque<>();
        pending.add(new NodeDepth(root, 1));
        int nodes = 0;
        while (!pending.isEmpty()) {
            NodeDepth item = pending.removeFirst();
            nodes++;
            if (nodes > MAX_JSON_NODES || item.depth() > MAX_JSON_DEPTH) {
                throw rejected(
                    "DIMENSION_MODEL_REQUEST_LIMIT_EXCEEDED",
                    "Dimension model request exceeds the fixed JSON budget",
                    null
                );
            }
            if (item.node().isTextual() && item.node().textValue().length() > MAX_TEXT_LENGTH) {
                throw rejected(
                    "DIMENSION_MODEL_REQUEST_LIMIT_EXCEEDED",
                    "Dimension model text exceeds the fixed request budget",
                    null
                );
            }
            item.node().elements().forEachRemaining(child -> pending.addLast(new NodeDepth(child, item.depth() + 1)));
        }
    }

    private static void rejectNonIntegralHierarchyOrders(JsonNode body) {
        JsonNode hierarchies = body.get("hierarchies");
        if (hierarchies == null || !hierarchies.isArray()) return;
        List<String> invalidPaths = new ArrayList<>();
        for (int hierarchyIndex = 0; hierarchyIndex < hierarchies.size(); hierarchyIndex++) {
            JsonNode levels = hierarchies.get(hierarchyIndex).get("levels");
            if (levels == null || !levels.isArray()) continue;
            for (int levelIndex = 0; levelIndex < levels.size(); levelIndex++) {
                JsonNode order = levels.get(levelIndex).get("order");
                if (order != null && (!order.isIntegralNumber() || !order.canConvertToInt())) {
                    invalidPaths.add("hierarchies[" + hierarchyIndex + "].levels[" + levelIndex + "].order");
                }
            }
        }
        if (!invalidPaths.isEmpty()) {
            throw rejected(
                "DIMENSION_MODEL_DEFINITION_INVALID",
                "Hierarchy level order must be an integer",
                invalidPaths
            );
        }
    }

    private static void putNullable(ObjectNode target, String field, Object value) {
        if (value == null) return;
        if (value instanceof UUID uuid) target.put(field, uuid.toString());
        else target.put(field, value.toString());
    }

    private static ObjectMapper strictObjectMapper(ObjectMapper objectMapper) {
        ObjectMapper strict = objectMapper
            .copy()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS
            )
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
        strict
            .coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strict
            .coercionConfigFor(LogicalType.Integer)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        return strict;
    }

    private static ModelSpecException rejected(String code, String message, Object details) {
        return new ModelSpecException(code, message, ModelSpecException.Kind.UNPROCESSABLE, details);
    }

    public enum BindingMode {
        CREATE,
        EXISTING,
    }

    public record DefinitionBinding(
        BindingMode mode,
        CreateCommand definition,
        DimensionDefinitionRef dimensionDefinitionRef
    ) {}

    public record PreparedCreate(
        UUID operationId,
        DefinitionBinding definitionBinding,
        UpdateModelSpecCommand modelSpec
    ) {}

    private record NodeDepth(JsonNode node, int depth) {}
}
