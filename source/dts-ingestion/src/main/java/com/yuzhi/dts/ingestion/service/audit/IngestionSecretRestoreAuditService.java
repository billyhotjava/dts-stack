package com.yuzhi.dts.ingestion.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditPayloadSanitizer;
import com.yuzhi.dts.ingestion.service.audit.IngestionSecretRestoreAuditOutboxRepository.EnqueueCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Builds classified, redacted audit facts for the secret compatibility endpoint only. */
@Service
public class IngestionSecretRestoreAuditService {

    public static final String CLASSIFICATION = "SENSITIVE_CONFIGURATION_RECOVERY";
    private static final String ACTION = "INGESTION_SECRET_COMPATIBILITY_RESTORE";
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    private final IngestionSecretRestoreAuditOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final String tenantId;

    public IngestionSecretRestoreAuditService(
        IngestionSecretRestoreAuditOutboxRepository outbox,
        ObjectMapper objectMapper,
        @Value("${auditing.tenant-id}") String tenantId
    ) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.tenantId = requireIdentity(tenantId, "tenantId");
    }

    public UUID recordAttempt(
        String actor,
        String batchId,
        boolean dryRun,
        Long taskId,
        Long revisionId,
        String configChecksum
    ) {
        return record(actor, batchId, dryRun, "ATTEMPT", taskId, revisionId, configChecksum, "PENDING", null);
    }

    public UUID recordResult(
        String actor,
        String batchId,
        boolean dryRun,
        Long taskId,
        Long revisionId,
        String configChecksum,
        String outcome,
        String errorCode
    ) {
        return record(actor, batchId, dryRun, "RESULT", taskId, revisionId, configChecksum, outcome, errorCode);
    }

    private UUID record(
        String actor,
        String batchId,
        boolean dryRun,
        String eventType,
        Long taskId,
        Long revisionId,
        String configChecksum,
        String outcome,
        String errorCode
    ) {
        String safeActor = requireHumanActor(actor);
        String safeBatchId = requireCode(batchId, "batchId");
        String safeOutcome = requireCode(outcome, "outcome").toUpperCase(Locale.ROOT);
        String safeErrorCode = StringUtils.hasText(errorCode) ? requireCode(errorCode, "errorCode") : null;
        String safeChecksum = normalizeChecksum(configChecksum);
        UUID receiptId = UUID.randomUUID();
        String eventId = receiptId.toString();
        Instant occurredAt = Instant.now();

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("tenantId", tenantId);
        attributes.put("classification", CLASSIFICATION);
        attributes.put("eventType", eventType);
        attributes.put("dryRun", dryRun);
        attributes.put("batchId", safeBatchId);
        attributes.put("taskId", taskId);
        attributes.put("revisionId", revisionId);
        attributes.put("configChecksum", safeChecksum);
        attributes.put("outcome", safeOutcome);
        attributes.put("errorCode", safeErrorCode);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("eventId", eventId);
        body.put("producer", "dts-ingestion");
        body.put("sourceSystem", "ingestion");
        body.put("occurredAt", occurredAt.toString());
        body.put("actor", safeActor);
        body.put("module", "data-integration");
        body.put("moduleKey", "data-integration");
        body.put("moduleName", "数据集成");
        body.put("buttonCode", ACTION);
        body.put("operationCode", ACTION);
        body.put("operationName", dryRun ? "凭据兼容恢复预检" : "凭据兼容恢复");
        body.put("summary", eventType.equals("ATTEMPT") ? "提交凭据兼容恢复操作" : "凭据兼容恢复操作结果");
        body.put("operationType", "UPDATE");
        body.put("resourceType", "INGESTION_TASK_SECRET");
        body.put("targetTable", "ingestion_task");
        if (taskId != null) {
            body.put("resourceId", taskId.toString());
            body.put("targetIds", List.of(taskId.toString()));
        }
        body.put("result", centralResult(eventType, safeOutcome));
        body.put("requestUri", dryRun
            ? "/api/ingestion/operations/secret-compatibility/restore/dry-run"
            : "/api/ingestion/operations/secret-compatibility/restore");
        body.put("httpMethod", "POST");
        body.put("attributes", attributes);

        Map<String, Object> sanitized = AuditPayloadSanitizer.sanitize(body);
        String payloadJson = serialize(sanitized);
        String payloadHash = sha256(payloadJson);
        return outbox.enqueue(
            new EnqueueCommand(
                receiptId,
                eventId,
                tenantId,
                safeActor,
                eventType,
                CLASSIFICATION,
                safeBatchId,
                taskId,
                revisionId,
                safeChecksum,
                safeOutcome,
                safeErrorCode,
                payloadHash,
                payloadJson
            )
        );
    }

    private String centralResult(String eventType, String outcome) {
        if ("ATTEMPT".equals(eventType)) {
            return "PENDING";
        }
        return switch (outcome) {
            case "READY", "RESTORED", "ALREADY_RESTORED", "NOT_REQUIRED" -> "SUCCESS";
            default -> "FAIL";
        };
    }

    private String requireHumanActor(String actor) {
        String value = requireIdentity(actor, "actor");
        String normalized = value.toLowerCase(Locale.ROOT);
        if (
            normalized.equals("anonymous") ||
            normalized.equals("system") ||
            normalized.equals("scheduler") ||
            normalized.startsWith("service:") ||
            normalized.startsWith("service-account-") ||
            normalized.startsWith("_system:")
        ) {
            throw new IllegalStateException("AUTHENTICATED_HUMAN_AUDIT_ACTOR_REQUIRED");
        }
        return value;
    }

    private String requireIdentity(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(field + " is required for secret restore audit");
        }
        String normalized = value.trim();
        if (normalized.length() > 128 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException(field + " is invalid for secret restore audit");
        }
        return normalized;
    }

    private String requireCode(String value, String field) {
        if (!StringUtils.hasText(value) || !SAFE_CODE.matcher(value.trim()).matches()) {
            throw new IllegalStateException(field + " is invalid for secret restore audit");
        }
        return value.trim();
    }

    private String normalizeChecksum(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String checksum = value.trim();
        if (checksum.length() > 64 || checksum.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("configChecksum is invalid for secret restore audit");
        }
        return checksum;
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("INGESTION_SECRET_RESTORE_AUDIT_SERIALIZATION_FAILED", ex);
        }
    }

    private String sha256(String payload) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
