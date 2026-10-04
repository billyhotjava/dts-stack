package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditPayloadSanitizer;
import com.yuzhi.dts.ingestion.domain.RollbackAuditLog;
import com.yuzhi.dts.ingestion.repository.RollbackAuditLogRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackAuditService {

    private final RollbackAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public RollbackAuditService(RollbackAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public RollbackAuditLog recordCommitted(
        UUID receiptId,
        String eventHash,
        RollbackAuditReason reason,
        String operator,
        RollbackLevel level,
        String scope,
        Long taskId,
        UUID dataSourceId,
        Object requestPayload,
        Object impactPayload,
        Object resultPayload,
        String status,
        String errorMessage
    ) {
        return persist(
            receiptId,
            eventHash,
            reason,
            operator,
            level,
            scope,
            taskId,
            dataSourceId,
            requestPayload,
            impactPayload,
            resultPayload,
            status,
            errorMessage
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RollbackAuditLog recordFailure(
        UUID receiptId,
        RollbackAuditReason reason,
        String operator,
        RollbackLevel level,
        String scope,
        Long taskId,
        UUID dataSourceId,
        Object requestPayload,
        Object impactPayload,
        Object resultPayload,
        String status,
        String errorMessage
    ) {
        return persist(
            receiptId,
            null,
            reason,
            operator,
            level,
            scope,
            taskId,
            dataSourceId,
            requestPayload,
            impactPayload,
            resultPayload,
            status,
            errorMessage
        );
    }

    private RollbackAuditLog persist(
        UUID receiptId,
        String eventHash,
        RollbackAuditReason reason,
        String operator,
        RollbackLevel level,
        String scope,
        Long taskId,
        UUID dataSourceId,
        Object requestPayload,
        Object impactPayload,
        Object resultPayload,
        String status,
        String errorMessage
    ) {
        RollbackAuditLog log = new RollbackAuditLog();
        log.setOperationReceiptId(receiptId);
        log.setEventHash(eventHash);
        log.setReasonCode(reason.name());
        log.setOperator(bound(operator, 128));
        log.setLevel(level.code());
        log.setScope(bound(scope, 32));
        log.setTaskId(taskId);
        log.setDataSourceId(dataSourceId);
        log.setRequestJson(serializeSanitized(requestPayload));
        log.setImpactJson(serializeSanitized(impactPayload));
        log.setResultJson(serializeSanitized(resultPayload));
        log.setStatus(bound(status, 32));
        log.setErrorMessage(sanitizeText(errorMessage));
        return repository.saveAndFlush(log);
    }

    private String serializeSanitized(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            Map<String, Object> body;
            if (payload instanceof Map<?, ?> map) {
                body = new LinkedHashMap<>();
                map.forEach((key, value) -> body.put(String.valueOf(key), value));
            } else {
                body = objectMapper.convertValue(payload, new TypeReference<LinkedHashMap<String, Object>>() {});
            }
            return objectMapper.writeValueAsString(AuditPayloadSanitizer.sanitize(body));
        } catch (Exception ignored) {
            return "{\"_auditPayload\":\"[REDACTED_UNSERIALIZABLE]\"}";
        }
    }

    private String sanitizeText(String value) {
        if (value == null) {
            return null;
        }
        Object sanitized = AuditPayloadSanitizer.sanitize(Map.of("message", value)).get("message");
        return String.valueOf(sanitized);
    }

    private String bound(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    @Transactional(readOnly = true)
    public List<RollbackAuditLog> findByTaskId(Long taskId) {
        return repository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Transactional(readOnly = true)
    public List<RollbackAuditLog> findByDataSourceId(UUID dataSourceId) {
        return repository.findByDataSourceIdOrderByCreatedAtDesc(dataSourceId);
    }
}
