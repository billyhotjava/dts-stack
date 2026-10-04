package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditIngestFingerprintTest {

    @Test
    void canonicalHashIgnoresEnvelopeIdentityAndNestedMapOrder() {
        AuditIngestFingerprint fingerprint = new AuditIngestFingerprint(new ObjectMapper());

        Map<String, Object> first = payload("event-1", "forged-a", false);
        Map<String, Object> replay = payload("event-1", "forged-b", true);

        assertThat(fingerprint.payloadHash(first))
            .hasSize(64)
            .isEqualTo(fingerprint.payloadHash(replay));
    }

    @Test
    void businessPayloadChangeProducesDifferentHash() {
        AuditIngestFingerprint fingerprint = new AuditIngestFingerprint(new ObjectMapper());
        Map<String, Object> first = payload("event-1", "forged", false);
        Map<String, Object> changed = payload("event-1", "forged", false);
        changed.put("summary", "修改后的模型");

        assertThat(fingerprint.payloadHash(first)).isNotEqualTo(fingerprint.payloadHash(changed));
    }

    private Map<String, Object> payload(String eventId, String producer, boolean reversed) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (reversed) {
            metadata.put("correlationId", "correlation-81");
            metadata.put("tenantId", "tenant-a");
        } else {
            metadata.put("tenantId", "tenant-a");
            metadata.put("correlationId", "correlation-81");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (reversed) {
            body.put("metadata", metadata);
            body.put("summary", "创建模型");
            body.put("producer", producer);
            body.put("eventId", eventId);
        } else {
            body.put("eventId", eventId);
            body.put("producer", producer);
            body.put("summary", "创建模型");
            body.put("metadata", metadata);
        }
        return body;
    }
}
