package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ModelSpecImportPreviewRequestParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ModelSpecImportPreviewRequestParser parser = new ModelSpecImportPreviewRequestParser(objectMapper);

    @Test
    void rejectsOversizedContentLengthBeforeReadingOrBindingJson() {
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(new byte[0]), ModelSpecImportPreviewRequestParser.MAX_REQUEST_BYTES + 1L))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error ->
                assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE)
            );
    }

    @Test
    void acceptsBoundedPreviewEnvelopeAtTheRouteBoundary() {
        String json = """
            {
              \"package\": {},
              \"inspectionProof\": \"proof-v1\",
              \"context\": {\"planId\": \"%s\", \"domainMappings\": {}, \"sourceMappings\": {}},
              \"selectedUniqueIds\": [],
              \"semanticOverrides\": [{
                \"modelUniqueId\": \"model.demo.fact_budget\",
                \"businessName\": \"预算事实\",
                \"fieldRoles\": {\"budget_id\": \"KEY\"}
              }]
            }
            """.formatted(UUID.randomUUID());

        var parsed = parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), json.getBytes(StandardCharsets.UTF_8).length);

        assertThat(parsed.context().planId()).isNotNull();
        assertThat(parsed.inspectionProof()).isEqualTo("proof-v1");
        assertThat(parsed.selectedUniqueIds()).isEmpty();
        assertThat(parsed.semanticOverrides()).singleElement().satisfies(override -> {
            assertThat(override.modelUniqueId()).isEqualTo("model.demo.fact_budget");
            assertThat(override.businessName()).isEqualTo("预算事实");
        });
    }

    @Test
    void rejectsTechnicalFieldsOutsideTheSemanticOverrideAllowlist() {
        String json = """
            {
              "package": {},
              "inspectionProof": "proof-v1",
              "context": {"planId": "%s"},
              "selectedUniqueIds": [],
              "semanticOverrides": [{
                "modelUniqueId": "model.demo.fact_budget",
                "sql": "select secret from source"
              }]
            }
            """.formatted(UUID.randomUUID());

        assertInvalidRequest(json);
    }

    @Test
    void rejectsUnknownSemanticOverrideFieldsEvenWhenTheProductionMapperIsLenient() {
        ObjectMapper productionMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ModelSpecImportPreviewRequestParser strictParser = new ModelSpecImportPreviewRequestParser(
            productionMapper
        );
        String json = """
            {
              "package": {},
              "inspectionProof": "proof-v1",
              "context": {"planId": "%s"},
              "selectedUniqueIds": [],
              "semanticOverrides": [{
                "modelUniqueId": "model.demo.fact_budget",
                "compiledSql": "select credential_body from forbidden_source"
              }]
            }
            """.formatted(UUID.randomUUID());
        byte[] body = json.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> strictParser.parse(new ByteArrayInputStream(body), body.length))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error -> {
                ModelSpecImportPreviewRequestParser.RequestLimitException requestError =
                    (ModelSpecImportPreviewRequestParser.RequestLimitException) error;
                assertThat(requestError.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(requestError.code()).isEqualTo("MODEL_IMPORT_REQUEST_INVALID");
                assertThat(requestError.getMessage()).doesNotContain("credential_body", "forbidden_source");
            });
    }

    @Test
    void rejectsApplyBodyThatExceeds64KiBEvenWithoutContentLength() {
        byte[] body = new byte[ModelSpecImportPreviewRequestParser.MAX_APPLY_REQUEST_BYTES + 1];

        assertThatThrownBy(() -> parser.parseApply(new ByteArrayInputStream(body), -1))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error -> {
                ModelSpecImportPreviewRequestParser.RequestLimitException requestError =
                    (ModelSpecImportPreviewRequestParser.RequestLimitException) error;
                assertThat(requestError.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
                assertThat(requestError.code()).isEqualTo("MODEL_IMPORT_REQUEST_TOO_LARGE");
            });
    }

    @Test
    void parsesBoundedApplyAndRejectsMalformedCommandFields() {
        String valid = """
            {
              "runId": "%s",
              "previewHash": "%s",
              "selectedUniqueIds": ["model.pjm.fact"],
              "idempotencyKey": "apply-key"
            }
            """.formatted(UUID.randomUUID(), "a".repeat(64));
        byte[] validBody = valid.getBytes(StandardCharsets.UTF_8);

        assertThat(parser.parseApply(new ByteArrayInputStream(validBody), validBody.length).selectedUniqueIds())
            .containsExactly("model.pjm.fact");
        byte[] invalidBody = valid.replace("a".repeat(64), "not-a-hash").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parseApply(new ByteArrayInputStream(invalidBody), invalidBody.length))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error ->
                assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).code()).isEqualTo(
                    "MODEL_IMPORT_REQUEST_INVALID"
                )
            );
    }

    @Test
    void rejectsPlainReadTreeIoFailureAsAnInvalidPreviewRequest() throws Exception {
        ObjectMapper mapper = org.mockito.Mockito.spy(new ObjectMapper());
        org.mockito.Mockito.doReturn(mapper).when(mapper).copy();
        org.mockito.Mockito.doThrow(new IOException("simulated read failure")).when(mapper).readTree(org.mockito.ArgumentMatchers.any(byte[].class));
        ModelSpecImportPreviewRequestParser parser = new ModelSpecImportPreviewRequestParser(mapper);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)), 2))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error -> {
                ModelSpecImportPreviewRequestParser.RequestLimitException requestError =
                    (ModelSpecImportPreviewRequestParser.RequestLimitException) error;
                assertThat(requestError.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(requestError.code()).isEqualTo("MODEL_IMPORT_REQUEST_INVALID");
            });
    }

    @Test
    void rejectsARequestWithTooManySelectedIdsBeforePreviewService() {
        String ids = java.util.stream.IntStream
            .rangeClosed(0, ModelSpecImportPreviewRequestParser.MAX_SELECTED_UNIQUE_IDS)
            .mapToObj(index -> "\"model.project.n" + index + "\"")
            .collect(java.util.stream.Collectors.joining(","));
        String json = "{\"package\":{},\"context\":{\"planId\":\"" + UUID.randomUUID() + "\"},\"selectedUniqueIds\":[" + ids + "]}";

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), json.length()))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error ->
                assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).status()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    void rejectsStreamingTokenBudgetBeforeTreeBinding() {
        int items = (ModelSpecImportPreviewRequestParser.MAX_JSON_TOKENS / 4) + 10;
        StringBuilder padding = new StringBuilder(items * 8);
        for (int index = 0; index < items; index++) {
            if (index > 0) {
                padding.append(',');
            }
            padding.append("{\"x\":0}");
        }

        assertInvalidRequest("{\"padding\":[" + padding + "]}");
    }

    @Test
    void rejectsStreamingFieldAndAggregateCollectionBudgetsBeforeTreeBinding() {
        StringBuilder fields = new StringBuilder(ModelSpecImportPreviewRequestParser.MAX_JSON_FIELDS * 10);
        for (int index = 0; index <= ModelSpecImportPreviewRequestParser.MAX_JSON_FIELDS; index++) {
            if (index > 0) {
                fields.append(',');
            }
            fields.append("\"f").append(index).append("\":0");
        }
        assertInvalidRequest("{" + fields + "}");

        StringBuilder items = new StringBuilder(ModelSpecImportPreviewRequestParser.MAX_JSON_COLLECTION_ITEMS * 2);
        for (int index = 0; index <= ModelSpecImportPreviewRequestParser.MAX_JSON_COLLECTION_ITEMS; index++) {
            if (index > 0) {
                items.append(',');
            }
            items.append('0');
        }
        assertInvalidRequest("{\"padding\":[" + items + "]}");
    }

    @Test
    void rejectsOverlongFieldNamesBeforeTreeBinding() {
        String json = "{\"" + "x".repeat(ModelSpecImportPreviewRequestParser.MAX_JSON_FIELD_NAME_BYTES + 1) + "\":0}";

        assertInvalidRequest(json);
    }

    @Test
    void rejectsAggregateColumnsAcrossOtherwiseBoundedModels() throws Exception {
        ObjectNode request = boundedEnvelope();
        ArrayNode models = ((ObjectNode) request.path("package")).putArray("models");
        int remaining = ModelSpecImportPreviewRequestParser.MAX_TOTAL_COLUMNS + 1;
        while (remaining > 0) {
            ArrayNode columns = models.addObject().putArray("columns");
            int count = Math.min(remaining, ModelSpecImportPreviewRequestParser.MAX_COLUMNS_PER_NODE);
            for (int index = 0; index < count; index++) {
                columns.addObject();
            }
            remaining -= count;
        }

        byte[] json = objectMapper.writeValueAsBytes(request);

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(json), json.length))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error ->
                assertThat(((ModelSpecImportPreviewRequestParser.RequestLimitException) error).code()).isEqualTo(
                        "MODEL_IMPORT_REQUEST_INVALID"
                    )
            );
    }

    private ObjectNode boundedEnvelope() {
        ObjectNode request = objectMapper.createObjectNode();
        request.putObject("package");
        request.putObject("context").put("planId", UUID.randomUUID().toString());
        request.putArray("selectedUniqueIds");
        return request;
    }

    private void assertInvalidRequest(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(body), body.length))
            .isInstanceOf(ModelSpecImportPreviewRequestParser.RequestLimitException.class)
            .satisfies(error -> {
                ModelSpecImportPreviewRequestParser.RequestLimitException requestError =
                    (ModelSpecImportPreviewRequestParser.RequestLimitException) error;
                assertThat(requestError.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(requestError.code()).isEqualTo("MODEL_IMPORT_REQUEST_INVALID");
            });
    }
}
