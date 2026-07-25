package com.yuzhi.dts.platform.service.modeling.imports.checksum;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.ModelPackage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Canonical SHA-256 for model packages.
 *
 * <p>Object keys are sorted recursively, object nulls are omitted, array order is retained,
 * line endings are normalized and {@code packageChecksum} never hashes itself.
 */
public final class ModelPackageChecksum {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ModelPackageChecksum() {}

    public static ModelPackage withChecksum(ModelPackage modelPackage) {
        ModelPackage withoutChecksum = new ModelPackage(
            modelPackage.schemaVersion(),
            modelPackage.packageId(),
            null,
            modelPackage.dbt(),
            modelPackage.defaults(),
            modelPackage.sources(),
            modelPackage.technicalNodes(),
            modelPackage.models(),
            modelPackage.issues()
        );
        return new ModelPackage(
            withoutChecksum.schemaVersion(),
            withoutChecksum.packageId(),
            compute(withoutChecksum),
            withoutChecksum.dbt(),
            withoutChecksum.defaults(),
            withoutChecksum.sources(),
            withoutChecksum.technicalNodes(),
            withoutChecksum.models(),
            withoutChecksum.issues()
        );
    }

    public static String compute(ModelPackage modelPackage) {
        return compute(OBJECT_MAPPER.valueToTree(modelPackage));
    }

    public static String compute(JsonNode source) {
        JsonNode copy = source == null ? OBJECT_MAPPER.nullNode() : source.deepCopy();
        if (copy instanceof ObjectNode objectNode) {
            objectNode.remove("packageChecksum");
        }
        try {
            return sha256(OBJECT_MAPPER.writeValueAsBytes(canonicalize(copy)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("模型包无法规范化", exception);
        }
    }

    public static String sha256Text(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        return sha256(normalized.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static JsonNode canonicalize(JsonNode source) {
        if (source == null || source.isNull()) {
            return OBJECT_MAPPER.nullNode();
        }
        if (source.isObject()) {
            ObjectNode result = OBJECT_MAPPER.createObjectNode();
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            source.fields().forEachRemaining(entry -> {
                if (entry.getValue() != null && !entry.getValue().isNull()) {
                    fields.put(entry.getKey(), entry.getValue());
                }
            });
            fields.forEach((name, value) -> result.set(name, canonicalize(value)));
            return result;
        }
        if (source.isArray()) {
            ArrayNode result = OBJECT_MAPPER.createArrayNode();
            source.forEach(value -> result.add(canonicalize(value)));
            return result;
        }
        return source.deepCopy();
    }
}
