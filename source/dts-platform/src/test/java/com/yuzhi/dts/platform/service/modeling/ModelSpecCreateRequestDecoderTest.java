package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModelSpecCreateRequestDecoderTest {

    private final ObjectMapper lenientMapper = new ObjectMapper()
        .findAndRegisterModules()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final ModelSpecCreateRequestDecoder decoder = new ModelSpecCreateRequestDecoder(lenientMapper);

    @Test
    void decoderOwnsStrictUnknownFieldHandlingWithoutDependingOnGlobalMapperConfiguration() throws Exception {
        ObjectNode candidate = genericFact();
        candidate.put("objectId", "retired-object");
        candidate.put("futureGuess", true);

        ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(candidate);

        assertThat(result.command()).isNull();
        assertThat(result.issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactlyInAnyOrder(
                tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "objectId"),
                tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "futureGuess")
            );
    }

    @Test
    void decoderReturnsStableShapeIssuesForNullCollectionsAndWrongPrimitiveTypes() throws Exception {
        ObjectNode nullCollection = genericFact();
        nullCollection.putNull("fields");
        ObjectNode wrongPrimitive = genericFact();
        wrongPrimitive.put("name", 42);

        assertThat(decoder.decode(nullCollection).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(tuple("MODEL_SPEC_COLLECTION_INVALID", "fields"));
        assertThat(decoder.decode(wrongPrimitive).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(tuple("MODEL_SPEC_NAME_INVALID", "name"));
    }

    @Test
    void decoderAcceptsAFactDraftBeforeItsPhysicalSourceIsMapped() throws Exception {
        ObjectNode candidate = genericFact();
        candidate.putArray("sourceRefs");

        ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(candidate);

        assertThat(result.command()).isNotNull();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void decoderKeepsLegacyV2DimensionDefinitionAndKeyMappingCompatible() throws Exception {
        ObjectNode candidate = genericDimension();
        candidate.putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);
        candidate.putNull("description");
        ((ObjectNode) candidate.get("grain")).putArray("keys").add("missing_dimension_key");

        ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(candidate);

        assertThat(result.command()).isNotNull();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void decoderAcceptsLogicalDimensionProfileWithoutLegacyIdentityFields() throws Exception {
        ObjectNode candidate = genericDimension();
        candidate.putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);
        ObjectNode profile = candidate.putObject("dimensionProfile");
        profile.putArray("hierarchies");
        profile.putObject("scdPolicy").put("type", "TYPE1");

        ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(candidate);

        assertThat(result.issues()).isEmpty();
        assertThat(result.command()).isNotNull();
        assertThat(result.command().dimensionProfile().scdPolicy().type()).isEqualTo(ModelSpecContract.ScdType.TYPE1);
    }

    @Test
    void decoderPinsDimensionDefinitionsAndRejectsTheReferenceForEveryOtherModelType() throws Exception {
        ObjectNode dimension = genericDimension();
        dimension.putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);

        assertThat(decoder.decode(dimension).issues()).isEmpty();

        for (String modelType : Set.of("FACT", "SUMMARY", "APPLICATION")) {
            ObjectNode candidate = genericFact();
            candidate.put("modelType", modelType);
            candidate.putObject("dimensionDefinitionRef")
                .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
                .put("revision", 3);

            assertThat(decoder.decode(candidate).issues())
                .as(modelType)
                .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
                .contains(tuple("MODEL_SPEC_DIMENSION_DEFINITION_NOT_ALLOWED", "dimensionDefinitionRef"));
        }
    }

    @Test
    void decoderRejectsLegacyDimensionIdentityFieldsButKeepsLogicalDesignFields() throws Exception {
        ObjectNode legacyProfile = genericDimension();
        legacyProfile.putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);
        legacyProfile.putObject("dimensionProfile")
            .put("dimensionCode", "DIM_ORGANIZATION")
            .put("reuseScope", "PLAN")
            .putArray("hierarchies");

        assertThat(decoder.decode(legacyProfile).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(tuple("MODEL_SPEC_DIMENSION_PROFILE_INVALID", "dimensionProfile"));
    }

    @Test
    void decoderReturnsTheCommandOnlyWhenWireShapeAndSemanticsAreValid() throws Exception {
        ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(genericFact());

        assertThat(result.issues()).isEmpty();
        assertThat(result.command()).isNotNull();
        assertThat(result.valid()).isTrue();
    }

    @Test
    void decoderAcceptsTheMinimalInteractiveDraftForAllFourTableTypes() {
        for (String modelType : List.of("DIMENSION", "FACT", "SUMMARY", "APPLICATION")) {
            ObjectNode candidate = lenientMapper
                .createObjectNode()
                .put("planId", "10000000-0000-0000-0000-000000000001")
                .put("domainId", "20000000-0000-0000-0000-000000000001")
                .put("modelType", modelType)
                .put("name", modelType.toLowerCase() + "_draft")
                .put("idempotencyKey", "minimal-" + modelType.toLowerCase());
            if ("DIMENSION".equals(modelType)) {
                candidate
                    .putObject("dimensionDefinitionRef")
                    .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
                    .put("revision", 1);
            }

            ModelSpecCreateRequestDecoder.DecodeResult result = decoder.decode(candidate);

            assertThat(result.issues()).as(modelType).isEmpty();
            assertThat(result.command()).as(modelType).isNotNull();
            assertThat(result.command().implementationMode()).as(modelType).isEqualTo(ModelSpecContract.ImplementationMode.DESIGNER_GENERATED);
            assertThat(result.command().layer())
                .as(modelType)
                .isEqualTo(
                    switch (modelType) {
                        case "SUMMARY" -> ModelSpecContract.Layer.DWS;
                        case "APPLICATION" -> ModelSpecContract.Layer.ADS;
                        default -> ModelSpecContract.Layer.DWD;
                    }
                );
        }
    }

    @Test
    void decoderIsTheOnlyPublicWireValidationEntry() throws Exception {
        assertThat(
            Modifier.isPublic(ModelSpecContract.class.getDeclaredMethod("validateCreateFieldNames", Set.class).getModifiers())
        ).isFalse();
        assertThat(
            Modifier.isPublic(ModelSpecContract.class.getDeclaredMethod("validateCreateShape", Map.class).getModifiers())
        ).isFalse();
        assertThat(
            Modifier.isPublic(
                ModelSpecContract.class
                    .getDeclaredMethod("validateCreate", ModelSpecContract.CreateModelSpecCommand.class)
                    .getModifiers()
            )
        ).isFalse();
        assertThat(Modifier.isPublic(ModelSpecCreateRequestDecoder.class.getMethod("decode", JsonNode.class).getModifiers())).isTrue();
    }

    private ObjectNode genericFact() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/fixtures/modeling-v2/generic-fact.json")) {
            assertThat(stream).isNotNull();
            return (ObjectNode) lenientMapper.readTree(stream);
        }
    }

    private ObjectNode genericDimension() throws Exception {
        ObjectNode candidate = genericFact();
        candidate.put("modelType", "DIMENSION");
        candidate.put("name", "generic_dimension");
        candidate.putNull("description");
        candidate.putNull("factShape");
        candidate.putNull("timeSemantics");
        candidate.putArray("sourceRefs");
        return candidate;
    }
}
