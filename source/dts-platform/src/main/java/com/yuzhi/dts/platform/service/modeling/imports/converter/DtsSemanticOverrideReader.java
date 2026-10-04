package com.yuzhi.dts.platform.service.modeling.imports.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.contract.ModelPackageContract.SemanticMetadata;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Reads an explicit JSON mapping from dbt unique_id to DTS business semantics. */
public final class DtsSemanticOverrideReader {

    private final ObjectMapper objectMapper;

    public DtsSemanticOverrideReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, SemanticMetadata> read(Path path) throws IOException {
        if (path == null) {
            return Map.of();
        }
        JsonNode root = objectMapper.readTree(path.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("SEMANTIC_OVERRIDES_INVALID: overrides 必须是 unique_id 到语义对象的映射");
        }
        Map<String, SemanticMetadata> result = new TreeMap<>();
        root.fields().forEachRemaining(entry -> {
            SemanticMetadata semantics = DbtModelPackageConverter.semanticFrom(entry.getValue(), path.toString());
            if (semantics == null) {
                throw new IllegalArgumentException("SEMANTIC_OVERRIDES_INVALID: " + entry.getKey());
            }
            result.put(entry.getKey(), semantics);
        });
        return Map.copyOf(result);
    }
}
