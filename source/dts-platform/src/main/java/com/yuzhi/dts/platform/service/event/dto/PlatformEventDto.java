package com.yuzhi.dts.platform.service.event.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record PlatformEventDto(
    UUID id,
    String eventId,
    String eventType,
    String domain,
    String sourceApp,
    String aggregateType,
    String aggregateId,
    String aggregateName,
    String action,
    String severity,
    String status,
    Instant occurredAt,
    String actor,
    String correlationId,
    String traceId,
    String auditActionCode,
    String policyRef,
    Map<String, Object> payload,
    String dispatchStatus,
    Integer dispatchAttempts,
    Instant dispatchedAt,
    String dispatchError,
    String createdBy,
    Instant createdDate
) {}
