package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuditIngestFingerprint {

    private final ObjectMapper objectMapper;

    public AuditIngestFingerprint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String eventId(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("eventId");
        if (raw == null || !StringUtils.hasText(String.valueOf(raw))) {
            return null;
        }
        String eventId = String.valueOf(raw).trim();
        if (eventId.length() > 128) {
            throw new IllegalArgumentException("eventId exceeds 128 characters");
        }
        return eventId;
    }

    public String payloadHash(Map<String, Object> body) {
        Map<String, Object> canonicalPayload = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
        canonicalPayload.remove("eventId");
        canonicalPayload.remove("producer");
        canonicalPayload.remove("sourceSystem");
        try {
            String json = objectMapper
                .writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsString(canonicalPayload);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("audit payload cannot be canonicalized", ex);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
