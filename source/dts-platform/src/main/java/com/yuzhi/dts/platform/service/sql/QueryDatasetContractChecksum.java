package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/** Computes a stable semantic-contract digest independent of JSON object key order and whitespace. */
final class QueryDatasetContractChecksum {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private QueryDatasetContractChecksum() {}

    static String compute(String contractJson, String sqlText) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(contractJson);
            if (root == null) {
                throw new IllegalArgumentException("Query dataset contract JSON is required");
            }
            String canonicalJson = OBJECT_MAPPER.writeValueAsString(canonicalize(root));
            String value = canonicalJson + "\n" + (sqlText == null ? "" : sqlText);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Query dataset contract JSON is invalid", failure);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
            List<String> fieldNames = new ArrayList<>();
            node.fieldNames().forEachRemaining(fieldNames::add);
            fieldNames.sort(Comparator.naturalOrder());
            fieldNames.forEach(fieldName -> canonical.set(fieldName, canonicalize(node.get(fieldName))));
            return canonical;
        }
        if (node.isArray()) {
            ArrayNode canonical = OBJECT_MAPPER.createArrayNode();
            node.forEach(item -> canonical.add(canonicalize(item)));
            return canonical;
        }
        return node;
    }
}
