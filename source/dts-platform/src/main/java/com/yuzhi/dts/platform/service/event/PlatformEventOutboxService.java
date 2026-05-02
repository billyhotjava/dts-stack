package com.yuzhi.dts.platform.service.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.PlatformEventProperties;
import com.yuzhi.dts.platform.domain.event.PlatformEventOutbox;
import com.yuzhi.dts.platform.repository.event.PlatformEventOutboxRepository;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventDto;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventRequest;
import com.yuzhi.dts.platform.service.event.dto.PlatformEventSummaryDto;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class PlatformEventOutboxService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PlatformEventOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final PlatformEventProperties properties;

    public PlatformEventOutboxService(
        PlatformEventOutboxRepository repository,
        ObjectMapper objectMapper,
        AuditService auditService,
        PlatformEventProperties properties
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.properties = properties;
    }

    public PlatformEventDto publish(PlatformEventRequest request) {
        String eventId = StringUtils.hasText(request.eventId()) ? request.eventId().trim() : UUID.randomUUID().toString();
        PlatformEventOutbox event = repository.findByEventId(eventId).orElseGet(PlatformEventOutbox::new);
        event.setEventId(eventId);
        event.setEventType(trimRequired(request.eventType(), "eventType"));
        event.setDomain(normalizeToken(request.domain(), "general"));
        event.setSourceApp(normalizeToken(request.sourceApp(), "dts-platform"));
        event.setAggregateType(trimToNull(request.aggregateType()));
        event.setAggregateId(trimToNull(request.aggregateId()));
        event.setAggregateName(trimToNull(request.aggregateName()));
        event.setAction(normalizeToken(request.action(), "EVENT"));
        event.setSeverity(normalizeToken(request.severity(), "INFO"));
        event.setStatus(normalizeToken(request.status(), "SUCCESS"));
        event.setOccurredAt(request.occurredAt() != null ? request.occurredAt() : Instant.now());
        event.setActor(resolveActor(request.actor()));
        event.setCorrelationId(trimToNull(request.correlationId()));
        event.setTraceId(trimToNull(request.traceId()));
        event.setAuditActionCode(trimToNull(request.auditActionCode()));
        event.setPolicyRef(trimToNull(request.policyRef()));
        event.setPayloadJson(writePayload(request.payload()));
        event.setDispatchStatus(PlatformEventOutbox.DISPATCH_PENDING);
        event.setDispatchAttempts(event.getDispatchAttempts() == null ? 0 : event.getDispatchAttempts());
        PlatformEventOutbox saved = repository.save(event);
        auditEvent(saved, request.payload());
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public Page<PlatformEventDto> list(
        String domain,
        String eventType,
        String aggregateType,
        String aggregateId,
        String status,
        String dispatchStatus,
        Pageable pageable
    ) {
        Specification<PlatformEventOutbox> spec = Specification.where(eq("domain", normalizeOptional(domain)))
            .and(eq("eventType", trimToNull(eventType)))
            .and(eq("aggregateType", trimToNull(aggregateType)))
            .and(eq("aggregateId", trimToNull(aggregateId)))
            .and(eq("status", normalizeOptional(status)))
            .and(eq("dispatchStatus", normalizeOptional(dispatchStatus)));
        return repository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public PlatformEventSummaryDto summarize() {
        var events = repository.findAll();
        Map<String, Long> byDomain = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        long pending = 0;
        long sent = 0;
        long failed = 0;
        long skipped = 0;
        for (PlatformEventOutbox event : events) {
            byDomain.merge(defaultString(event.getDomain(), "general"), 1L, Long::sum);
            byStatus.merge(defaultString(event.getStatus(), "UNKNOWN"), 1L, Long::sum);
            bySeverity.merge(defaultString(event.getSeverity(), "INFO"), 1L, Long::sum);
            String dispatch = defaultString(event.getDispatchStatus(), PlatformEventOutbox.DISPATCH_PENDING);
            if (PlatformEventOutbox.DISPATCH_PENDING.equals(dispatch)) pending++;
            if (PlatformEventOutbox.DISPATCH_SENT.equals(dispatch)) sent++;
            if (PlatformEventOutbox.DISPATCH_FAILED.equals(dispatch)) failed++;
            if (PlatformEventOutbox.DISPATCH_SKIPPED.equals(dispatch)) skipped++;
        }
        return new PlatformEventSummaryDto(
            events.size(),
            pending,
            sent,
            failed,
            skipped,
            properties.getKafka().isEnabled(),
            properties.getKafka().getTopic(),
            byDomain,
            byStatus,
            bySeverity
        );
    }

    @Transactional(readOnly = true)
    public java.util.List<PlatformEventOutbox> nextPendingForDispatch() {
        int batchSize = Math.max(1, Math.min(properties.getKafka().getBatchSize(), 500));
        return repository.findByDispatchStatusOrderByOccurredAtAsc(
            PlatformEventOutbox.DISPATCH_PENDING,
            PageRequest.of(0, batchSize)
        );
    }

    public PlatformEventOutbox markDispatched(PlatformEventOutbox event) {
        event.setDispatchStatus(PlatformEventOutbox.DISPATCH_SENT);
        event.setDispatchedAt(Instant.now());
        event.setDispatchError(null);
        event.setDispatchAttempts((event.getDispatchAttempts() == null ? 0 : event.getDispatchAttempts()) + 1);
        return repository.save(event);
    }

    public PlatformEventOutbox markDispatchFailed(PlatformEventOutbox event, Throwable error) {
        event.setDispatchStatus(PlatformEventOutbox.DISPATCH_FAILED);
        event.setDispatchAttempts((event.getDispatchAttempts() == null ? 0 : event.getDispatchAttempts()) + 1);
        event.setDispatchError(error == null ? "unknown" : String.valueOf(error.getMessage()));
        return repository.save(event);
    }

    public PlatformEventDto toDispatchDto(PlatformEventOutbox event) {
        return toDto(event);
    }

    private void auditEvent(PlatformEventOutbox event, Map<String, Object> payload) {
        String actionCode = StringUtils.hasText(event.getAuditActionCode())
            ? event.getAuditActionCode()
            : "PLATFORM_EVENT_PUBLISH";
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "发布平台事件");
        auditPayload.put("eventId", event.getEventId());
        auditPayload.put("eventType", event.getEventType());
        auditPayload.put("domain", event.getDomain());
        auditPayload.put("sourceApp", event.getSourceApp());
        auditPayload.put("aggregateType", event.getAggregateType());
        auditPayload.put("aggregateId", event.getAggregateId());
        auditPayload.put("status", event.getStatus());
        auditPayload.put("severity", event.getSeverity());
        auditPayload.put("dispatchStatus", event.getDispatchStatus());
        if (payload != null && !payload.isEmpty()) {
            auditPayload.put("payloadKeys", payload.keySet());
        }
        auditService.auditAction(actionCode, AuditStage.SUCCESS, event.getEventId(), auditPayload);
    }

    private PlatformEventDto toDto(PlatformEventOutbox event) {
        return new PlatformEventDto(
            event.getId(),
            event.getEventId(),
            event.getEventType(),
            event.getDomain(),
            event.getSourceApp(),
            event.getAggregateType(),
            event.getAggregateId(),
            event.getAggregateName(),
            event.getAction(),
            event.getSeverity(),
            event.getStatus(),
            event.getOccurredAt(),
            event.getActor(),
            event.getCorrelationId(),
            event.getTraceId(),
            event.getAuditActionCode(),
            event.getPolicyRef(),
            readPayload(event.getPayloadJson()),
            event.getDispatchStatus(),
            event.getDispatchAttempts(),
            event.getDispatchedAt(),
            event.getDispatchError(),
            event.getCreatedBy(),
            event.getCreatedDate()
        );
    }

    private Map<String, Object> readPayload(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ignored) {
            return Map.of("raw", json);
        }
    }

    private String writePayload(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"serializationError\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }

    private String resolveActor(String actor) {
        if (StringUtils.hasText(actor)) {
            return actor.trim();
        }
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    private String trimRequired(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeOptional(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeToken(String value, String fallback) {
        String resolved = StringUtils.hasText(value) ? value.trim() : fallback;
        return resolved.toUpperCase(Locale.ROOT);
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private Specification<PlatformEventOutbox> eq(String field, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get(field), value);
    }
}
