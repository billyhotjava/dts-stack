package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.checksum.ModelPackageChecksum;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.PackageModel;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SourceNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.TechnicalNode;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

/** Builds the deliberately narrow, durable payload consumed by the apply flow. */
@Component
public class ModelSpecImportApplyPayloadCodec {

    private static final Set<String> EXECUTION_SETTINGS = Set.of(
        "materialized",
        "schema",
        "alias",
        "database",
        "tags",
        "enabled",
        "incremental_strategy",
        "unique_key",
        "partition_by",
        "cluster_by"
    );
    private static final Set<String> FORBIDDEN_FIELD_NAMES = Set.of(
        "rawsql",
        "rawsqlchecksum",
        "compiledsql",
        "compiledsqlchecksum",
        "config",
        "hooks",
        "prehook",
        "posthook",
        "password",
        "secret",
        "token",
        "credential",
        "connection",
        "apikey",
        "privatekey",
        "accesskey",
        "clientsecret",
        "dsn",
        "jdbcurl"
    );

    private final ObjectMapper objectMapper;

    public ModelSpecImportApplyPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SanitizedPayload sanitize(ModelPackage modelPackage) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("schemaVersion", ModelSpecImportApplyPlanContract.APPLY_PAYLOAD_SCHEMA_VERSION);
        root.put("packageId", modelPackage.packageId());
        root.put("packageChecksum", modelPackage.packageChecksum());
        root.set("dbt", objectMapper.valueToTree(modelPackage.dbt()));
        root.set("defaults", objectMapper.valueToTree(modelPackage.defaults()));

        ArrayNode sources = root.putArray("sources");
        safe(modelPackage.sources()).stream().sorted(Comparator.comparing(SourceNode::dbtUniqueId)).forEach(source -> {
            ObjectNode node = sources.addObject();
            node.put("dbtUniqueId", source.dbtUniqueId());
            node.put("name", source.name());
            node.put("resourcePath", source.resourcePath());
            node.set("columns", objectMapper.valueToTree(safe(source.columns())));
        });
        ArrayNode technicalNodes = root.putArray("technicalNodes");
        safe(modelPackage.technicalNodes()).stream().sorted(Comparator.comparing(TechnicalNode::dbtUniqueId)).forEach(node ->
            technicalNodes.add(sanitizeTechnicalNode(node))
        );
        ArrayNode models = root.putArray("models");
        safe(modelPackage.models()).stream().sorted(Comparator.comparing(PackageModel::dbtUniqueId)).forEach(model ->
            models.add(sanitizeModel(model))
        );
        String checksum = checksum(root);
        return new SanitizedPayload(canonicalJson(root), checksum);
    }

    public String checksum(JsonNode payload) {
        return ModelPackageChecksum.sha256(bytes(canonicalize(payload)));
    }

    public boolean isValid(String payloadJson, String checksum) {
        if (payloadJson == null || checksum == null) {
            return false;
        }
        try {
            JsonNode payload = objectMapper.readTree(payloadJson);
            return payload != null && payload.isObject() && isSanitized(payload) && checksum.equals(checksum(payload));
        } catch (JsonProcessingException exception) {
            return false;
        }
    }

    public boolean isSanitized(JsonNode payload) {
        return payload != null &&
            payload.isObject() &&
            ModelSpecImportApplyPlanContract.APPLY_PAYLOAD_SCHEMA_VERSION.equals(payload.path("schemaVersion").asText()) &&
            !hasForbiddenFields(payload);
    }

    private ObjectNode sanitizeTechnicalNode(TechnicalNode node) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("dbtUniqueId", node.dbtUniqueId());
        result.put("name", node.name());
        result.put("resourceType", node.resourceType());
        result.put("resourcePath", node.resourcePath());
        result.set("sql", effectiveSql(node.sql() == null ? null : node.sql().effectiveSql(), node.sql() == null ? null : node.sql().effectiveSqlChecksum(), node.sql() == null ? null : node.sql().effectiveSource()));
        ObjectNode settings = safeSettings(node.config());
        result.set("executionSettings", settings);
        result.put("configChecksum", checksum(settings));
        ObjectNode schema = objectMapper.createObjectNode();
        schema.putArray("columns");
        schema.putArray("tests");
        result.put("schemaChecksum", checksum(schema));
        result.put("dependencyChecksum", checksum(objectMapper.valueToTree(safe(node.dependencies()))));
        result.set("dependencies", objectMapper.valueToTree(safe(node.dependencies())));
        result.set("tags", objectMapper.valueToTree(safe(node.tags())));
        result.set("conversion", objectMapper.valueToTree(node.conversion()));
        return result;
    }

    private ObjectNode sanitizeModel(PackageModel model) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("dbtUniqueId", model.dbtUniqueId());
        result.put("name", model.name());
        result.put("description", model.description());
        result.put("resourcePath", model.resourcePath());
        result.put("materialization", model.materialization());
        result.set("sql", effectiveSql(model.sql() == null ? null : model.sql().effectiveSql(), model.sql() == null ? null : model.sql().effectiveSqlChecksum(), model.sql() == null ? null : model.sql().effectiveSource()));
        ObjectNode settings = safeSettings(model.config());
        result.set("executionSettings", settings);
        result.put("configChecksum", checksum(settings));
        result.set("tags", objectMapper.valueToTree(safe(model.tags())));
        result.set("columns", objectMapper.valueToTree(safe(model.columns())));
        result.set("tests", objectMapper.valueToTree(safe(model.tests())));
        ObjectNode schema = objectMapper.createObjectNode();
        schema.set("columns", objectMapper.valueToTree(safe(model.columns())));
        schema.set("tests", objectMapper.valueToTree(safe(model.tests())));
        result.put("schemaChecksum", checksum(schema));
        result.set("dependencies", objectMapper.valueToTree(safe(model.dependencies())));
        result.put("dependencyChecksum", checksum(objectMapper.valueToTree(safe(model.dependencies()))));
        result.set("semantics", objectMapper.valueToTree(model.semantics()));
        result.set("conversion", objectMapper.valueToTree(model.conversion()));
        return result;
    }

    private ObjectNode effectiveSql(String value, String checksum, String source) {
        ObjectNode sql = objectMapper.createObjectNode();
        sql.put("effectiveSql", value);
        sql.put("effectiveSqlChecksum", checksum);
        sql.put("effectiveSource", source);
        return sql;
    }

    private ObjectNode safeSettings(Map<String, Object> config) {
        ObjectNode result = objectMapper.createObjectNode();
        if (config == null) {
            return result;
        }
        new TreeMap<>(config).forEach((key, value) -> {
            if (EXECUTION_SETTINGS.contains(key) && !hasForbiddenFields(objectMapper.valueToTree(value))) {
                result.set(key, objectMapper.valueToTree(value));
            }
        });
        return result;
    }

    private boolean hasForbiddenFields(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (FORBIDDEN_FIELD_NAMES.contains(normalize(field.getKey())) || hasForbiddenFields(field.getValue())) {
                    return true;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) {
                if (hasForbiddenFields(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String canonicalJson(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(canonicalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Model import apply payload cannot be serialized", exception);
        }
    }

    private byte[] bytes(JsonNode value) {
        try {
            return objectMapper.writeValueAsBytes(canonicalize(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Model import apply payload cannot be canonicalized", exception);
        }
    }

    private JsonNode canonicalize(JsonNode source) {
        if (source == null || source.isNull()) {
            return objectMapper.nullNode();
        }
        if (source.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            source.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    fields.put(entry.getKey(), entry.getValue());
                }
            });
            fields.forEach((key, value) -> result.set(key, canonicalize(value)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            source.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return source.deepCopy();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    public record SanitizedPayload(String json, String checksum) {}
}
