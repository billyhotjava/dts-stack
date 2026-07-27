package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ModelSpecV2FixtureTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ModelSpecCreateRequestDecoder decoder = new ModelSpecCreateRequestDecoder(objectMapper);

    @Test
    void internalSchemaAndJavaCommandExposeTheSameCreateFields() throws Exception {
        JsonNode schema = resourceJson("/config/modeling/model-spec-v2.schema.json");
        ObjectNode fixture = (ObjectNode) resourceJson("/fixtures/modeling-v2/generic-fact.json");
        Set<String> schemaFields = names(schema.path("$defs").path("createModelSpecCommand").path("properties"));
        Set<String> fixtureFields = names(fixture);
        Set<String> javaFields = Arrays.stream(ModelSpecContract.CreateModelSpecCommand.class.getRecordComponents())
            .map(component -> component.getName())
            .collect(Collectors.toSet());

        assertThat(schema.path("$defs").path("createModelSpecCommand").path("additionalProperties").asBoolean()).isFalse();
        assertThat(
            java.util.stream.StreamSupport.stream(
                schema
                    .path("$defs")
                    .path("modelSpecView")
                    .path("properties")
                    .path("contractVersion")
                    .path("enum")
                    .spliterator(),
                false
            ).map(JsonNode::asInt)
        ).containsExactly(1, 2);
        assertThat(schemaFields).containsExactlyInAnyOrderElementsOf(javaFields);
        assertThat(javaFields).containsAll(fixtureFields);
        assertThat(validateInternalCreate(fixture)).isEmpty();
    }

    @Test
    void rootSchemaAndProductionDecoderExposeOnlyTheMinimalInteractiveCreateRequest() throws Exception {
        JsonNode schema = resourceJson("/config/modeling/model-spec-v2.schema.json");
        JsonNode interactive = schema.path("$defs").path("interactiveCreateModelSpecRequest");
        ObjectNode request = objectMapper
            .createObjectNode()
            .put("planId", "10000000-0000-0000-0000-000000000001")
            .put("domainId", "20000000-0000-0000-0000-000000000001")
            .put("modelType", "FACT")
            .put("name", "finance_project")
            .put("idempotencyKey", "finance-project-create");

        assertThat(schema.path("$ref").asText()).isEqualTo("#/$defs/interactiveCreateModelSpecRequest");
        assertThat(names(interactive.path("properties")))
            .containsExactlyInAnyOrderElementsOf(ModelSpecContract.INTERACTIVE_CREATE_FIELDS);
        assertThat(decoder.decode(request).issues()).isEmpty();
    }

    @Test
    void strictDecoderRejectsRetiredAndUnknownFieldsAsStableIssues() throws Exception {
        ObjectNode fixture = objectMapper
            .createObjectNode()
            .put("planId", "10000000-0000-0000-0000-000000000001")
            .put("domainId", "20000000-0000-0000-0000-000000000001")
            .put("modelType", "FACT")
            .put("name", "finance_project")
            .put("idempotencyKey", "finance-project-create")
            .put("objectId", "retired-object");

        assertThat(decoder.decode(fixture).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(org.assertj.core.groups.Tuple.tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "objectId"));
    }

    @Test
    void schemaAndJavaExposeTheSameRequiredFieldIssueCodes() throws Exception {
        JsonNode schema = resourceJson("/config/modeling/model-spec-v2.schema.json");
        JsonNode createProperties = schema.path("$defs").path("createModelSpecCommand").path("properties");

        ModelSpecContract.REQUIRED_FIELD_CODES.forEach((field, code) -> {
            assertThat(createProperties.path(field).path("x-dts-requiredIssueCode").asText()).as(field).isEqualTo(code);
        });
    }

    @Test
    void createSchemaRequiresOnlyCoreScalarsAndKeepsCollectionsStructurallyOptional() throws Exception {
        JsonNode schema = resourceJson("/config/modeling/model-spec-v2.schema.json");
        JsonNode create = schema.path("$defs").path("createModelSpecCommand");
        Set<String> required = java.util.stream.StreamSupport.stream(create.path("required").spliterator(), false)
            .map(JsonNode::asText)
            .collect(Collectors.toSet());

        assertThat(required)
            .containsExactlyInAnyOrder("planId", "domainId", "modelType", "layer", "name", "implementationMode", "idempotencyKey");
        assertThat(required).doesNotContain(ModelSpecContract.COLLECTION_FIELDS.toArray(String[]::new));
        assertThat(schema.path("$defs").path("grain").path("properties").path("keys").path("minItems").asInt()).isEqualTo(1);
        assertThat(schema.path("$defs").path("timeSemantics").path("properties").path("fields").path("minItems").asInt())
            .isEqualTo(1);
    }

    @Test
    void schemaKeepsLegacyV2DimensionDefinitionAndGrainKeysCompatible() throws Exception {
        JsonNode schema = resourceJson("/config/modeling/model-spec-v2.schema.json");
        JsonNode dimensionBoundary = java.util.stream.StreamSupport.stream(
            schema.path("$defs").path("modelTypeSaveBoundaries").path("allOf").spliterator(),
            false
        )
            .filter(boundary ->
                "DIMENSION".equals(
                    boundary.path("if").path("properties").path("modelType").path("const").asText()
                )
            )
            .findFirst()
            .orElseThrow();
        Set<String> required = java.util.stream.StreamSupport.stream(
            dimensionBoundary.path("then").path("required").spliterator(),
            false
        )
            .map(JsonNode::asText)
            .collect(Collectors.toSet());
        assertThat(required).doesNotContain("description");
        assertThat(schema.path("$defs").path("grain").path("properties").path("keys").path("uniqueItems").asBoolean())
            .isFalse();
    }

    @Test
    void schemaContainsNoDuplicateJsonObjectKeys() throws Exception {
        ObjectMapper duplicateDetectingMapper = new ObjectMapper(
            JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build()
        );
        try (InputStream stream = getClass().getResourceAsStream("/config/modeling/model-spec-v2.schema.json")) {
            assertThat(stream).isNotNull();
            assertThat(duplicateDetectingMapper.readTree(stream)).isNotNull();
        }
    }

    @Test
    void sharedValidationCasesKeepStructuralAndSemanticIssueCodesStable() throws Exception {
        ObjectNode base = (ObjectNode) resourceJson("/fixtures/modeling-v2/generic-fact.json");
        JsonNode cases = resourceJson("/fixtures/modeling-v2/validation-cases.json");

        for (JsonNode testCase : cases) {
            assertThat(testCase.path("schemaValid").isBoolean()).as(testCase.path("name").asText()).isTrue();
            ObjectNode candidate = testCase.has("request") ? (ObjectNode) testCase.path("request").deepCopy() : base.deepCopy();
            testCase.path("deleteFields").forEach(field -> candidate.remove(field.asText()));
            testCase.path("overrides").fields().forEachRemaining(entry -> candidate.set(entry.getKey(), entry.getValue()));
            List<ModelSpecContract.FieldIssue> issues = validateInternalCreate(candidate);

            assertThat(issues)
                .as(testCase.path("name").asText())
                .extracting(ModelSpecContract.FieldIssue::code)
                .containsExactlyInAnyOrderElementsOf(
                    java.util.stream.StreamSupport.stream(testCase.path("expectedIssueCodes").spliterator(), false)
                        .map(JsonNode::asText)
                        .toList()
                );
        }
    }

    private List<ModelSpecContract.FieldIssue> validateInternalCreate(ObjectNode candidate) {
        Map<String, Object> raw = objectMapper.convertValue(candidate, new TypeReference<>() {});
        List<ModelSpecContract.FieldIssue> shapeIssues = ModelSpecContract.validateCreateShape(raw);
        if (!shapeIssues.isEmpty()) return shapeIssues;
        try {
            ModelSpecContract.CreateModelSpecCommand command = objectMapper.treeToValue(
                candidate,
                ModelSpecContract.CreateModelSpecCommand.class
            );
            return ModelSpecContract.validateCreate(command);
        } catch (Exception exception) {
            return List.of(
                new ModelSpecContract.FieldIssue(
                    "MODEL_SPEC_REQUEST_INVALID",
                    "$",
                    ModelSpecContract.IssueSeverity.ERROR,
                    "Internal fixture cannot be decoded"
                )
            );
        }
    }

    private JsonNode resourceJson(String path) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as(path).isNotNull();
            return objectMapper.readTree(stream);
        }
    }

    private static Set<String> names(JsonNode object) {
        return java.util.stream.StreamSupport.stream(
            java.util.Spliterators.spliteratorUnknownSize(object.fieldNames(), java.util.Spliterator.ORDERED),
            false
        ).collect(Collectors.toSet());
    }
}
