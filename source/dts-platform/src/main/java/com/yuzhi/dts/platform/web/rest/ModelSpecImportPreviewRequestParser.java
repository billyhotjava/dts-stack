package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ApplyRequest;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.RetryRequest;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Route-local, bounded decoder for model-package preview requests. */
@Component
public class ModelSpecImportPreviewRequestParser {

    public static final int MAX_REQUEST_BYTES = 16 * 1024 * 1024;
    public static final int MAX_APPLY_REQUEST_BYTES = 64 * 1024;
    public static final int MAX_APPLY_SELECTED_UNIQUE_IDS = 200;
    public static final int MAX_APPLY_UNIQUE_ID_LENGTH = 512;
    public static final int MAX_APPLY_IDEMPOTENCY_KEY_LENGTH = 256;
    public static final int MAX_JSON_DEPTH = 64;
    public static final int MAX_STRING_BYTES = 128 * 1024;
    public static final int MAX_SELECTED_UNIQUE_IDS = 200;
    public static final int MAX_MAPPING_ENTRIES = 200;
    public static final int MAX_MODEL_NODES = 200;
    public static final int MAX_TECHNICAL_NODES = 500;
    public static final int MAX_SOURCE_NODES = 200;
    public static final int MAX_COLUMNS_PER_NODE = 500;
    public static final int MAX_DEPENDENCIES_PER_NODE = 500;
    public static final int MAX_PACKAGE_ISSUES = 20;
    public static final int MAX_ISSUE_TEXT_BYTES = 2 * 1024;
    public static final int MAX_JSON_TOKENS = 100_000;
    public static final int MAX_JSON_FIELDS = 40_000;
    public static final int MAX_JSON_COLLECTION_ITEMS = 50_000;
    public static final int MAX_JSON_FIELD_NAME_BYTES = 512;
    public static final int MAX_TOTAL_COLUMNS = 10_000;
    public static final int MAX_TOTAL_DEPENDENCIES = 20_000;
    public static final int MAX_NESTED_COLLECTION = 500;
    public static final int MAX_OBJECT_FIELDS = 500;
    public static final int MAX_TAGS_PER_NODE = 100;
    public static final int MAX_TESTS_PER_COLUMN = 50;
    public static final int MAX_CONFIG_ENTRIES = 200;

    private final ObjectMapper mapper;

    public ModelSpecImportPreviewRequestParser(ObjectMapper objectMapper) {
        this.mapper = objectMapper.copy();
        this.mapper.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder()
                    .maxNestingDepth(MAX_JSON_DEPTH)
                    .maxStringLength(MAX_STRING_BYTES)
                    .build()
            );
    }

    public PreviewRequest parse(HttpServletRequest request) {
        if (request == null) {
            throw invalid("Request is required");
        }
        try {
            return parse(request.getInputStream(), request.getContentLengthLong());
        } catch (IOException exception) {
            throw invalid("Request body cannot be read");
        }
    }

    public ApplyRequest parseApply(HttpServletRequest request) {
        return parseSmallRequest(request, ApplyRequest.class, "Apply");
    }

    public RetryRequest parseRetry(HttpServletRequest request) {
        return parseSmallRequest(request, RetryRequest.class, "Retry");
    }

    PreviewRequest parse(InputStream input, long contentLength) {
        if (contentLength > MAX_REQUEST_BYTES) {
            throw previewTooLarge();
        }
        byte[] body = readBounded(input, MAX_REQUEST_BYTES, previewTooLarge());
        try {
            enforceStreamingBudget(body);
            JsonNode root = mapper.readTree(body);
            enforceLimits(root);
            return mapper.treeToValue(root, PreviewRequest.class);
        } catch (RequestLimitException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw invalid("Preview request must be a valid JSON object");
        } catch (IOException exception) {
            throw invalid("Preview request must be a valid JSON object");
        }
    }

    ApplyRequest parseApply(InputStream input, long contentLength) {
        return parseSmallRequest(input, contentLength, ApplyRequest.class, "Apply");
    }

    RetryRequest parseRetry(InputStream input, long contentLength) {
        return parseSmallRequest(input, contentLength, RetryRequest.class, "Retry");
    }

    private <T> T parseSmallRequest(HttpServletRequest request, Class<T> type, String requestName) {
        if (request == null) {
            throw invalid(requestName + " request is required");
        }
        try {
            return parseSmallRequest(request.getInputStream(), request.getContentLengthLong(), type, requestName);
        } catch (IOException exception) {
            throw invalid(requestName + " request body cannot be read");
        }
    }

    private <T> T parseSmallRequest(InputStream input, long contentLength, Class<T> type, String requestName) {
        if (contentLength > MAX_APPLY_REQUEST_BYTES) {
            throw applyTooLarge(requestName);
        }
        byte[] body = readBounded(input, MAX_APPLY_REQUEST_BYTES, applyTooLarge(requestName));
        try {
            JsonNode root = mapper.readTree(body);
            enforceApplyRequestLimits(root, type == ApplyRequest.class, requestName);
            return mapper.treeToValue(root, type);
        } catch (RequestLimitException exception) {
            throw exception;
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid(requestName + " request must be a valid JSON object");
        }
    }

    private void enforceStreamingBudget(byte[] body) throws IOException {
        int tokens = 0;
        int fields = 0;
        int collectionItems = 0;
        Deque<JsonToken> containers = new ArrayDeque<>();
        try (JsonParser parser = mapper.getFactory().createParser(body)) {
            JsonToken token;
            while ((token = parser.nextToken()) != null) {
                if (++tokens > MAX_JSON_TOKENS) {
                    throw invalid("JSON token count exceeds the allowed limit");
                }
                if (token == JsonToken.END_ARRAY || token == JsonToken.END_OBJECT) {
                    if (!containers.isEmpty()) {
                        containers.pop();
                    }
                    continue;
                }
                if (!containers.isEmpty() && containers.peek() == JsonToken.START_ARRAY) {
                    if (++collectionItems > MAX_JSON_COLLECTION_ITEMS) {
                        throw invalid("aggregate JSON collection items exceed the allowed limit");
                    }
                }
                if (token == JsonToken.FIELD_NAME) {
                    if (++fields > MAX_JSON_FIELDS) {
                        throw invalid("JSON field count exceeds the allowed limit");
                    }
                    String fieldName = parser.currentName();
                    if (
                        fieldName != null &&
                        fieldName.getBytes(StandardCharsets.UTF_8).length > MAX_JSON_FIELD_NAME_BYTES
                    ) {
                        throw invalid("JSON field name exceeds the allowed limit");
                    }
                }
                if (token == JsonToken.START_ARRAY || token == JsonToken.START_OBJECT) {
                    containers.push(token);
                }
            }
        }
    }

    private byte[] readBounded(InputStream input, int maximumBytes, RequestLimitException tooLarge) {
        if (input == null) {
            throw invalid("Request body is required");
        }
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = stream.read(buffer)) >= 0) {
                total += read;
                if (total > maximumBytes) {
                    throw tooLarge;
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (RequestLimitException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalid("Request body cannot be read");
        }
    }

    private void enforceApplyRequestLimits(JsonNode root, boolean apply, String requestName) {
        if (root == null || !root.isObject()) {
            throw invalid(requestName + " request must be a JSON object");
        }
        requireHexHash(root.path("previewHash"));
        requireBoundedText(root.path("idempotencyKey"), "idempotencyKey", MAX_APPLY_IDEMPOTENCY_KEY_LENGTH);
        if (!apply) {
            return;
        }
        JsonNode runId = root.path("runId");
        if (!runId.isTextual() || runId.asText().isBlank()) {
            throw invalid("runId is required");
        }
        JsonNode selected = root.path("selectedUniqueIds");
        if (
            !selected.isArray() ||
            selected.isEmpty() ||
            selected.size() > MAX_APPLY_SELECTED_UNIQUE_IDS
        ) {
            throw invalid("selectedUniqueIds must contain between 1 and 200 candidates");
        }
        selected.forEach(value ->
            requireBoundedText(value, "selectedUniqueIds item", MAX_APPLY_UNIQUE_ID_LENGTH)
        );
    }

    private void requireHexHash(JsonNode value) {
        if (!value.isTextual() || !value.asText().matches("(?i)[0-9a-f]{64}")) {
            throw invalid("previewHash must be a 64-character hexadecimal checksum");
        }
    }

    private void requireBoundedText(JsonNode value, String name, int maximumLength) {
        if (
            !value.isTextual() ||
            value.asText().isBlank() ||
            value.asText().length() > maximumLength
        ) {
            throw invalid(name + " exceeds the allowed limit");
        }
    }

    private void enforceLimits(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw invalid("Preview request must be a JSON object");
        }
        validateTree(root, 1);
        JsonNode selected = root.path("selectedUniqueIds");
        if (!selected.isMissingNode() && (!selected.isArray() || selected.size() > MAX_SELECTED_UNIQUE_IDS)) {
            throw invalid("selectedUniqueIds exceeds the allowed limit");
        }
        JsonNode context = root.path("context");
        if (context.isObject()) {
            validateMap(context.path("domainMappings"), "domainMappings", MAX_MAPPING_ENTRIES);
            validateMap(context.path("sourceMappings"), "sourceMappings", MAX_MAPPING_ENTRIES);
        }
        JsonNode modelPackage = root.path("package");
        if (modelPackage.isObject()) {
            validatePackage(modelPackage, new PackageBudget());
        }
    }

    private void validatePackage(JsonNode modelPackage, PackageBudget budget) {
        validateArray(modelPackage.path("models"), "package.models", MAX_MODEL_NODES, node -> validateModelNode(node, budget));
        validateArray(
            modelPackage.path("technicalNodes"),
            "package.technicalNodes",
            MAX_TECHNICAL_NODES,
            node -> validateTechnicalNode(node, budget)
        );
        validateArray(modelPackage.path("sources"), "package.sources", MAX_SOURCE_NODES, node -> validateSourceNode(node, budget));
        JsonNode issues = modelPackage.path("issues");
        if (!issues.isMissingNode()) {
            validateArray(issues, "package.issues", MAX_PACKAGE_ISSUES, this::validateIssue);
        }
    }

    private void validateModelNode(JsonNode node, PackageBudget budget) {
        validateColumns(node.path("columns"), "package.models.columns", budget);
        validateDependencies(node.path("dependencies"), "package.models.dependencies", budget);
        validateArray(node.path("tests"), "package.models.tests", MAX_NESTED_COLLECTION, ignored -> {});
        validateArray(node.path("tags"), "package.models.tags", MAX_TAGS_PER_NODE, ignored -> {});
        validateMap(node.path("config"), "package.models.config", MAX_CONFIG_ENTRIES);
        validateConversion(node.path("conversion"), "package.models.conversion");
        validateSemantics(node.path("semantics"));
    }

    private void validateTechnicalNode(JsonNode node, PackageBudget budget) {
        validateDependencies(node.path("dependencies"), "package.technicalNodes.dependencies", budget);
        validateArray(node.path("tags"), "package.technicalNodes.tags", MAX_TAGS_PER_NODE, ignored -> {});
        validateMap(node.path("config"), "package.technicalNodes.config", MAX_CONFIG_ENTRIES);
        validateConversion(node.path("conversion"), "package.technicalNodes.conversion");
    }

    private void validateSourceNode(JsonNode node, PackageBudget budget) {
        validateColumns(node.path("columns"), "package.sources.columns", budget);
    }

    private void validateColumns(JsonNode columns, String name, PackageBudget budget) {
        validateArray(columns, name, MAX_COLUMNS_PER_NODE, column -> {
            budget.addColumns(1);
            validateArray(column.path("tests"), name + ".tests", MAX_TESTS_PER_COLUMN, ignored -> {});
        });
    }

    private void validateDependencies(JsonNode dependencies, String name, PackageBudget budget) {
        validateArray(dependencies, name, MAX_DEPENDENCIES_PER_NODE, ignored -> budget.addDependencies(1));
    }

    private void validateConversion(JsonNode conversion, String name) {
        if (conversion.isMissingNode() || conversion.isNull()) {
            return;
        }
        if (!conversion.isObject()) {
            throw invalid(name + " must be an object");
        }
        validateArray(conversion.path("reasonCodes"), name + ".reasonCodes", MAX_NESTED_COLLECTION, ignored -> {});
    }

    private void validateSemantics(JsonNode semantics) {
        if (semantics.isMissingNode() || semantics.isNull()) {
            return;
        }
        if (!semantics.isObject()) {
            throw invalid("package.models.semantics must be an object");
        }
        validateArray(semantics.path("sourceRefs"), "package.models.semantics.sourceRefs", MAX_NESTED_COLLECTION, ignored -> {});
        validateArray(
            semantics.path("consumptionScenarios"),
            "package.models.semantics.consumptionScenarios",
            MAX_NESTED_COLLECTION,
            ignored -> {}
        );
        validateMap(semantics.path("fieldRoles"), "package.models.semantics.fieldRoles", MAX_COLUMNS_PER_NODE);
        validateArray(semantics.path("grain").path("keys"), "package.models.semantics.grain.keys", MAX_NESTED_COLLECTION, ignored -> {});
        validateArray(
            semantics.path("timeSemantics").path("fields"),
            "package.models.semantics.timeSemantics.fields",
            MAX_NESTED_COLLECTION,
            ignored -> {}
        );
    }

    private void validateIssue(JsonNode issue) {
        if (!issue.isObject()) {
            return;
        }
        validateIssueText(issue.path("code"));
        validateIssueText(issue.path("severity"));
        validateIssueText(issue.path("fieldPath"));
        validateIssueText(issue.path("modelUniqueId"));
        validateIssueText(issue.path("message"));
        validateIssueText(issue.path("recoveryAction"));
    }

    private void validateIssueText(JsonNode value) {
        if (value.isTextual() && value.asText().getBytes(StandardCharsets.UTF_8).length > MAX_ISSUE_TEXT_BYTES) {
            throw invalid("package issue text exceeds the allowed limit");
        }
    }

    private void validateMap(JsonNode value, String name, int maximum) {
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isObject() || value.size() > maximum) {
            throw invalid(name + " exceeds the allowed limit");
        }
    }

    private void validateArray(JsonNode value, String name, int maximum, java.util.function.Consumer<JsonNode> itemValidator) {
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (!value.isArray() || value.size() > maximum) {
            throw invalid(name + " exceeds the allowed limit");
        }
        value.forEach(itemValidator);
    }

    private void validateTree(JsonNode node, int depth) {
        if (depth > MAX_JSON_DEPTH) {
            throw invalid("JSON nesting depth exceeds the allowed limit");
        }
        if (node.isTextual() && node.asText().getBytes(StandardCharsets.UTF_8).length > MAX_STRING_BYTES) {
            throw invalid("JSON string exceeds the allowed limit");
        }
        if (node.isObject()) {
            if (node.size() > MAX_OBJECT_FIELDS) {
                throw invalid("JSON object field count exceeds the allowed limit");
            }
            Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) {
                validateTree(values.next(), depth + 1);
            }
        } else if (node.isArray()) {
            if (node.size() > MAX_NESTED_COLLECTION) {
                throw invalid("JSON array size exceeds the allowed limit");
            }
            for (JsonNode value : node) {
                validateTree(value, depth + 1);
            }
        }
    }

    private static RequestLimitException previewTooLarge() {
        return new RequestLimitException(HttpStatus.PAYLOAD_TOO_LARGE, "MODEL_IMPORT_REQUEST_TOO_LARGE", "Preview request exceeds 16 MiB");
    }

    private static RequestLimitException applyTooLarge(String requestName) {
        return new RequestLimitException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "MODEL_IMPORT_REQUEST_TOO_LARGE",
            requestName + " request exceeds 64 KiB"
        );
    }

    private static RequestLimitException invalid(String message) {
        return new RequestLimitException(HttpStatus.BAD_REQUEST, "MODEL_IMPORT_REQUEST_INVALID", message);
    }

    private static final class PackageBudget {

        private int columns;
        private int dependencies;

        private void addColumns(int count) {
            columns += count;
            if (columns > MAX_TOTAL_COLUMNS) {
                throw invalid("aggregate package columns exceed the allowed limit");
            }
        }

        private void addDependencies(int count) {
            dependencies += count;
            if (dependencies > MAX_TOTAL_DEPENDENCIES) {
                throw invalid("aggregate package dependencies exceed the allowed limit");
            }
        }
    }

    public static final class RequestLimitException extends RuntimeException {

        private static final long serialVersionUID = 1L;
        private final HttpStatus status;
        private final String code;

        RequestLimitException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public HttpStatus status() {
            return status;
        }

        public String code() {
            return code;
        }
    }
}
