package com.yuzhi.dts.platform.service.event.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.Map;

public record PlatformEventRequest(
    String eventId,
    @NotBlank String eventType,
    @NotBlank String domain,
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
    Map<String, Object> payload
) {}
