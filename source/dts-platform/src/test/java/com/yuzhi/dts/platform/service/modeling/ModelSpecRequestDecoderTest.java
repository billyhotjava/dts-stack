package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import org.junit.jupiter.api.Test;

class ModelSpecRequestDecoderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ModelSpecCreateRequestDecoder createDecoder = new ModelSpecCreateRequestDecoder(objectMapper);
    private final ModelSpecUpdateRequestDecoder updateDecoder = new ModelSpecUpdateRequestDecoder(objectMapper);

    @Test
    void decodesStrictCreateAndUpdateContractsIndependently() throws Exception {
        CreateModelSpecCommand create = createDecoder.decode(objectMapper.readTree(minimalCreateJson())).command();
        ObjectNode updateJson = (ObjectNode) objectMapper.readTree(validCreateJson());
        updateJson.remove("idempotencyKey");
        UpdateModelSpecCommand update = updateDecoder.decode(updateJson).command();

        assertThat(create.idempotencyKey()).isEqualTo("create-1");
        assertThat(update.name()).isEqualTo("customer_detail");
    }

    @Test
    void rejectsServerManagedAndCreateOnlyFieldsBeforeTypedDeserialization() throws Exception {
        ObjectNode legacyCreate = (ObjectNode) objectMapper.readTree(minimalCreateJson());
        legacyCreate.put("objectId", "legacy");
        assertThat(createDecoder.decode(legacyCreate).issues())
            .extracting(ModelSpecContract.FieldIssue::code)
            .containsExactly("MODEL_SPEC_FIELD_NOT_ALLOWED");

        assertThat(updateDecoder.decode(objectMapper.readTree(validCreateJson())).issues())
            .extracting(ModelSpecContract.FieldIssue::code)
            .containsExactly("MODEL_SPEC_FIELD_NOT_ALLOWED");
    }

    @Test
    void rejectsDimensionDefinitionReferenceOnUpdateBecauseTheCreationPinIsImmutable() throws Exception {
        ObjectNode update = (ObjectNode) objectMapper.readTree(validCreateJson());
        update.remove("idempotencyKey");
        update.putObject("dimensionDefinitionRef")
            .put("dimensionDefinitionId", "60000000-0000-0000-0000-000000000001")
            .put("revision", 3);

        assertThat(updateDecoder.decode(update).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(tuple("MODEL_SPEC_FIELD_NOT_ALLOWED", "dimensionDefinitionRef"));
    }

    @Test
    void redirectsLegacyImplementationPolicyWritesToDataImplementation() throws Exception {
        ObjectNode update = (ObjectNode) objectMapper.readTree(validCreateJson());
        update.remove("idempotencyKey");
        update.putObject("implementationPolicy")
            .put("physicalName", "dwd_customer_detail")
            .put("loadStrategy", "FULL")
            .putArray("partitionFields");

        assertThat(updateDecoder.decode(update).issues())
            .extracting(ModelSpecContract.FieldIssue::code, ModelSpecContract.FieldIssue::field)
            .containsExactly(tuple("MODEL_SPEC_IMPLEMENTATION_POLICY_MOVED", "implementationPolicy"));
    }

    private static String validCreateJson() {
        return """
            {"planId":"10000000-0000-0000-0000-000000000001",
             "domainId":"20000000-0000-0000-0000-000000000001",
             "modelType":"FACT","layer":"DWD","name":"customer_detail",
             "implementationMode":"DESIGNER_GENERATED",
             "grain":{"statement":"one row per customer event","keys":["customer_id"]},
             "sourceRefs":[{"kind":"TABLE","ref":"ods.customer","layer":"ODS","role":"PRIMARY","sortOrder":0,
                            "sourceBindingId":"50000000-0000-0000-0000-000000000001","resolvedVersion":"v1"}],
             "idempotencyKey":"create-1"}
            """;
    }

    private static String minimalCreateJson() {
        return """
            {"planId":"10000000-0000-0000-0000-000000000001",
             "domainId":"20000000-0000-0000-0000-000000000001",
             "modelType":"FACT","name":"customer_detail",
             "idempotencyKey":"create-1"}
            """;
    }
}
