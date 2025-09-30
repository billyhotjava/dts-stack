package com.yuzhi.dts.common.service;

import com.yuzhi.dts.common.domain.AuditEvent;
import com.yuzhi.dts.common.repository.AuditEventRepository;
import com.yuzhi.dts.common.service.dto.AuditEventDTO;
import com.yuzhi.dts.common.service.util.TargetRefValidator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuditEventService {

    private static final Logger LOG = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditEventRepository repository;
    private final Counter writeSuccess;
    private final Counter writeFail;
    private final Timer writeLatency;

    public AuditEventService(AuditEventRepository repository, MeterRegistry registry) {
        this.repository = repository;
        this.writeSuccess = Counter.builder("audit_events_write_total").tag("status", "success").register(registry);
        this.writeFail = Counter.builder("audit_events_write_total").tag("status", "fail").register(registry);
        this.writeLatency = Timer.builder("audit_events_write_latency_ms").publishPercentiles(0.5, 0.95, 0.99).register(registry);
    }

    @Retryable(include = { DataIntegrityViolationException.class }, maxAttempts = 3, backoff = @Backoff(delay = 200))
    public AuditEventDTO create(AuditEventDTO dto, String idempotencyKey) {
        return writeLatency.record(() -> doCreate(dto, idempotencyKey));
    }

    private AuditEventDTO doCreate(AuditEventDTO dto, String idempotencyKey) {
        String normalizedRef = TargetRefValidator.normalize(dto.getTargetRef());

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            AuditEvent existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing != null) {
                return toDto(existing);
            }
        }

        AuditEvent e = new AuditEvent();
        e.setActor(dto.getActor());
        e.setAction(dto.getAction());
        e.setTargetRef(normalizedRef);
        e.setTargetKind(dto.getTargetKind());
        e.setCreatedAt(Optional.ofNullable(dto.getCreatedAt()).orElse(Instant.now()));
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            e.setIdempotencyKey(idempotencyKey);
        }
        try {
            AuditEvent saved = repository.saveAndFlush(e);
            writeSuccess.increment();
            return toDto(saved);
        } catch (DataIntegrityViolationException ex) {
            writeFail.increment();
            LOG.warn("[AUDIT_PERSIST_FAIL] integrity violation for idempotencyKey={}", idempotencyKey, ex);
            // if unique constraint hit by concurrent request, load existing
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                AuditEvent existing = repository.findByIdempotencyKey(idempotencyKey);
                if (existing != null) {
                    return toDto(existing);
                }
            }
            throw ex;
        } catch (RuntimeException ex) {
            writeFail.increment();
            LOG.error("[AUDIT_PERSIST_FAIL] error saving audit event", ex);
            throw ex;
        }
    }

    public AuditEventDTO toDto(AuditEvent e) {
        AuditEventDTO dto = new AuditEventDTO();
        dto.setId(e.getId());
        dto.setActor(e.getActor());
        dto.setAction(e.getAction());
        dto.setTargetRef(e.getTargetRef());
        dto.setTargetKind(e.getTargetKind());
        dto.setCreatedAt(e.getCreatedAt());
        return dto;
    }
}

