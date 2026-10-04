package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.BindingMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DimensionModelCreateRequestDecoderTest {

    private static final UUID DEFINITION_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID OPERATION_ID = UUID.fromString("50000000-0000-0000-0000-000000000001");

    private ObjectMapper objectMapper;
    private DimensionModelCreateRequestDecoder decoder;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        decoder = new DimensionModelCreateRequestDecoder(
            objectMapper,
            new ModelSpecCreateRequestDecoder(objectMapper),
            new ModelSpecUpdateRequestDecoder(objectMapper)
        );
    }

    @Test
    void decodesACompleteCreateBindingAndDerivesServerOwnedV2Identity() throws Exception {
        DimensionModelCreateRequestDecoder.PreparedCreate prepared = decoder.decode(
            objectMapper.readTree(validCreateRequest())
        );

        assertThat(prepared.operationId()).isEqualTo(OPERATION_ID);
        assertThat(prepared.definitionBinding().mode()).isEqualTo(BindingMode.CREATE);
        assertThat(prepared.definitionBinding().definition().idempotencyKey())
            .isEqualTo("dm:v2:dimension:" + OPERATION_ID);
        assertThat(prepared.modelSpec().modelType()).isEqualTo(ModelType.DIMENSION);
        assertThat(prepared.modelSpec().layer()).isEqualTo(Layer.DWD);
        assertThat(prepared.modelSpec().implementationMode()).isEqualTo(ImplementationMode.DESIGNER_GENERATED);

        var seed = decoder.resolveModelCreateCommand(
            prepared,
            new DimensionDefinitionRef(DEFINITION_ID, 2)
        );
        assertThat(seed.idempotencyKey()).isEqualTo("dm:v2:model:" + OPERATION_ID);
        assertThat(seed.dimensionDefinitionRef()).isEqualTo(new DimensionDefinitionRef(DEFINITION_ID, 2));
        assertThat(seed.domainId()).isEqualTo(prepared.definitionBinding().definition().domainId());
    }

    @Test
    void decodesAnExistingRevisionBindingWithoutCreatingParallelDimensionIdentity() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(validCreateRequest());
        request.set(
            "definitionBinding",
            objectMapper
                .createObjectNode()
                .put("mode", "EXISTING")
                .set(
                    "dimensionDefinitionRef",
                    objectMapper
                        .createObjectNode()
                        .put("dimensionDefinitionId", DEFINITION_ID.toString())
                        .put("revision", 4)
                )
        );

        var prepared = decoder.decode(request);

        assertThat(prepared.definitionBinding().mode()).isEqualTo(BindingMode.EXISTING);
        assertThat(prepared.definitionBinding().definition()).isNull();
        assertThat(prepared.definitionBinding().dimensionDefinitionRef())
            .isEqualTo(new DimensionDefinitionRef(DEFINITION_ID, 4));
    }

    @Test
    void rejectsUnknownFieldsAndClientOwnedSubcommandIdentity() throws Exception {
        ObjectNode unknown = (ObjectNode) objectMapper.readTree(validCreateRequest());
        unknown.put("parallelLedger", "forbidden");
        assertRejected(unknown, "DIMENSION_MODEL_FIELD_NOT_ALLOWED");

        ObjectNode clientDefinitionKey = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) clientDefinitionKey.at("/definitionBinding/definition"))
            .put("idempotencyKey", "client-key");
        assertRejected(clientDefinitionKey, "DIMENSION_MODEL_SUBCOMMAND_IDENTITY_FORBIDDEN");

        ObjectNode clientModelKey = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) clientModelKey.get("modelSpec")).put("idempotencyKey", "client-key");
        assertRejected(clientModelKey, "DIMENSION_MODEL_SUBCOMMAND_IDENTITY_FORBIDDEN");

        ObjectNode clientRef = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) clientRef.get("modelSpec"))
            .putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", DEFINITION_ID.toString())
            .put("revision", 1);
        assertRejected(clientRef, "DIMENSION_MODEL_SUBCOMMAND_IDENTITY_FORBIDDEN");
    }

    @Test
    void rejectsNonCanonicalOperationIdsWrongFixedContextAndCrossDomainComposition() throws Exception {
        ObjectNode upperCaseOperation = (ObjectNode) objectMapper.readTree(validCreateRequest());
        upperCaseOperation.put("operationId", "50000000-0000-0000-0000-00000000000A");
        assertRejected(upperCaseOperation, "DIMENSION_MODEL_OPERATION_ID_INVALID");

        ObjectNode wrongContext = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) wrongContext.get("modelSpec")).put("modelType", "FACT");
        assertRejected(wrongContext, "DIMENSION_MODEL_FIXED_CONTEXT_REQUIRED");

        ObjectNode mismatch = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) mismatch.get("modelSpec"))
            .put("domainId", "20000000-0000-0000-0000-000000000002");
        assertRejected(mismatch, "DIMENSION_MODEL_DOMAIN_MISMATCH");
    }

    @Test
    void rejectsRequestsThatExceedTheFixedJsonTextBudget() throws Exception {
        ObjectNode request = (ObjectNode) objectMapper.readTree(validCreateRequest());
        ((ObjectNode) request.get("modelSpec")).put("description", "x".repeat(8_193));

        assertRejected(request, "DIMENSION_MODEL_REQUEST_LIMIT_EXCEEDED");
    }

    private void assertRejected(JsonNode request, String code) {
        assertThatThrownBy(() -> decoder.decode(request))
            .isInstanceOf(ModelSpecException.class)
            .extracting(error -> ((ModelSpecException) error).code())
            .isEqualTo(code);
    }

    static String validCreateRequest() {
        return """
            {
              "operationId":"50000000-0000-0000-0000-000000000001",
              "definitionBinding":{
                "mode":"CREATE",
                "definition":{
                  "domainId":"20000000-0000-0000-0000-000000000001",
                  "name":"Customer",
                  "definition":"Customer dimension",
                  "ownerId":"alice",
                  "reuseScope":"DOMAIN",
                  "scopeType":"DOMAIN",
                  "attributes":[{
                    "code":"CUSTOMER_CODE",
                    "name":"Customer code",
                    "definition":"Customer code",
                    "primaryKey":true,
                    "order":1
                  }]
                }
              },
              "modelSpec":{
                "planId":"10000000-0000-0000-0000-000000000001",
                "domainId":"20000000-0000-0000-0000-000000000001",
                "modelType":"DIMENSION",
                "layer":"DWD",
                "name":"dim_customer",
                "description":"Customer dimension model",
                "implementationMode":"DESIGNER_GENERATED",
                "grain":{"statement":"one row per customer","keys":["customer_code"]},
                "fields":[{
                  "name":"customer_code",
                  "displayName":"Customer code",
                  "dataType":"varchar(64)",
                  "nullable":false,
                  "role":"KEY",
                  "dimensionAttributeCode":"CUSTOMER_CODE"
                }],
                "variantCode":"DEFAULT"
              }
            }
            """;
    }
}
