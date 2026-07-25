package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Canonical implementation checksum shared by package preview and apply.
 *
 * <p>The idempotency key is deliberately excluded, matching implementation persistence.
 */
@Component
public class ModelImplementationChecksumCodec {

    private final ObjectWriter canonicalWriter;

    public ModelImplementationChecksumCodec(ObjectMapper objectMapper) {
        this.canonicalWriter = objectMapper
            .copy()
            .setSerializationInclusion(JsonInclude.Include.ALWAYS)
            .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .disable(SerializationFeature.INDENT_OUTPUT)
            .writer();
    }

    public String contentChecksum(SaveImplementationCommand command) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("inputMode", command.inputMode());
        content.put("inputs", command.inputs());
        content.put("fieldMappings", command.fieldMappings());
        content.put("settings", command.settings());
        content.put("ownership", command.ownership());
        content.put("materialization", command.materialization());
        try {
            return sha256(canonicalWriter.writeValueAsBytes(content));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Implementation checksum payload cannot be serialized", exception);
        }
    }

    public String artifactChecksum(String effectiveSql) {
        return effectiveSql == null ? null : sha256(effectiveSql.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
