package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ClaimedAudit;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway.AuditSubmissionResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AuditOutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AuditOutboxDispatcher.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final PlatformAuditOutboxRepository outbox;
    private final AdminAuditGateway gateway;
    private final ObjectMapper objectMapper;
    private final AuditProperties properties;
    private final Clock clock;

    @Autowired
    public AuditOutboxDispatcher(
        PlatformAuditOutboxRepository outbox,
        AdminAuditGateway gateway,
        ObjectMapper objectMapper,
        AuditProperties properties
    ) {
        this(outbox, gateway, objectMapper, properties, Clock.systemUTC());
    }

    AuditOutboxDispatcher(
        PlatformAuditOutboxRepository outbox,
        AdminAuditGateway gateway,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this(outbox, gateway, objectMapper, new AuditProperties(), clock);
    }

    private AuditOutboxDispatcher(
        PlatformAuditOutboxRepository outbox,
        AdminAuditGateway gateway,
        ObjectMapper objectMapper,
        AuditProperties properties,
        Clock clock
    ) {
        this.outbox = outbox;
        this.gateway = gateway;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${auditing.outbox.dispatch-delay-ms:2000}")
    public void dispatchPendingEvents() {
        if (!properties.isEnabled() || !gateway.isEnabled()) {
            return;
        }
        int batchSize = Math.max(1, Math.min(properties.getOutbox().getBatchSize(), 500));
        long cycleBudgetNanos = Duration
            .ofSeconds(Math.max(1, properties.getOutbox().getMaxDispatchCycleSeconds()))
            .toNanos();
        long deadline = System.nanoTime() + cycleBudgetNanos;
        for (int index = 0; index < batchSize; index++) {
            if (System.nanoTime() >= deadline) {
                return;
            }
            try {
                if (dispatchNext().isEmpty()) {
                    return;
                }
            } catch (RuntimeException failure) {
                log.error("Audit outbox dispatch failed before delivery could be classified", failure);
                return;
            }
        }
    }

    public Optional<DispatchResult> dispatchNext() {
        Instant now = clock.instant();
        Duration staleClaimTtl = Duration.ofSeconds(Math.max(1, properties.getOutbox().getStaleClaimSeconds()));
        Optional<ClaimedAudit> candidate = outbox.claimNext(now, staleClaimTtl);
        if (candidate.isEmpty()) {
            return Optional.empty();
        }

        ClaimedAudit claimed = candidate.orElseThrow();
        Map<String, Object> body;
        try {
            body = objectMapper.readValue(claimed.bodyJson(), MAP_TYPE);
        } catch (Exception malformed) {
            outbox.markDead(claimed.id(), claimed.attempts(), "AUDIT_PAYLOAD_MALFORMED", now);
            return Optional.of(new DispatchResult(claimed.eventId(), "DEAD", claimed.attempts()));
        }
        if (!hasValidIntegrity(claimed, body)) {
            outbox.markDead(claimed.id(), claimed.attempts(), "AUDIT_PAYLOAD_INTEGRITY_FAILED", now);
            return Optional.of(new DispatchResult(claimed.eventId(), "DEAD", claimed.attempts()));
        }

        AuditSubmissionResult submission = gateway.submitEvent(body);
        return Optional.of(classify(claimed, submission, now));
    }

    @Scheduled(cron = "${auditing.outbox.retention-cron:0 20 2 * * *}", zone = "UTC")
    public void purgeDeliveredEvents() {
        if (!properties.isEnabled()) {
            return;
        }
        int retentionDays = Math.max(1, properties.getOutbox().getRetentionDays());
        int batchSize = Math.max(1, Math.min(properties.getOutbox().getRetentionBatchSize(), 50_000));
        Instant cutoff = clock.instant().minus(retentionDays, ChronoUnit.DAYS);
        int totalPurged = 0;
        for (int batch = 0; batch < 20; batch++) {
            int purged = outbox.purgeSentBefore(cutoff, batchSize);
            totalPurged += purged;
            if (purged < batchSize) {
                break;
            }
        }
        if (totalPurged > 0) {
            log.info("Purged {} delivered audit outbox rows", totalPurged);
        }
        long dead = outbox.countDead();
        if (dead > 0) {
            log.warn("Audit outbox contains {} dead-letter event(s); operator review and controlled replay are required", dead);
        }
    }

    private DispatchResult classify(ClaimedAudit claimed, AuditSubmissionResult submission, Instant now) {
        if (
            submission.outcome() == AuditSubmissionResult.Outcome.RECORDED ||
            submission.outcome() == AuditSubmissionResult.Outcome.DUPLICATE
        ) {
            outbox.markSent(claimed.id(), claimed.attempts(), now);
            return new DispatchResult(claimed.eventId(), "SENT", claimed.attempts());
        }
        if (submission.outcome() == AuditSubmissionResult.Outcome.IDEMPOTENCY_CONFLICT) {
            outbox.markDead(claimed.id(), claimed.attempts(), "AUDIT_IDEMPOTENCY_CONFLICT", now);
            return new DispatchResult(claimed.eventId(), "DEAD", claimed.attempts());
        }
        if (submission.outcome() == AuditSubmissionResult.Outcome.PERMANENT_FAILURE) {
            outbox.markDead(claimed.id(), claimed.attempts(), "AUDIT_DELIVERY_PERMANENT", now);
            return new DispatchResult(claimed.eventId(), "DEAD", claimed.attempts());
        }

        int maxAttempts = Math.max(1, properties.getOutbox().getMaxAttempts());
        if (claimed.attempts() >= maxAttempts) {
            outbox.markDead(claimed.id(), claimed.attempts(), "AUDIT_DELIVERY_RETRIES_EXHAUSTED", now);
            return new DispatchResult(claimed.eventId(), "DEAD", claimed.attempts());
        }
        Duration delay = submission.retryAfter() != null
            ? submission.retryAfter()
            : retryDelay(claimed.attempts());
        outbox.markRetry(
            claimed.id(),
            claimed.attempts(),
            "AUDIT_DELIVERY_RETRYABLE",
            now.plus(delay),
            now
        );
        return new DispatchResult(claimed.eventId(), "RETRY", claimed.attempts());
    }

    private boolean hasValidIntegrity(ClaimedAudit claimed, Map<String, Object> body) {
        if (claimed == null || body == null) {
            return false;
        }
        Object bodyEventId = body.get("eventId");
        Object bodyProducer = body.get("producer");
        return claimed.eventId().equals(String.valueOf(bodyEventId)) &&
            claimed.producer().equals(String.valueOf(bodyProducer)) &&
            claimed.payloadHash().equals(sha256(claimed.bodyJson()));
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    private Duration retryDelay(int attempts) {
        long initial = Math.max(1, properties.getOutbox().getInitialRetrySeconds());
        long maximum = Math.max(initial, properties.getOutbox().getMaxRetrySeconds());
        int exponent = Math.max(0, Math.min(attempts - 1, 30));
        long multiplier = 1L << exponent;
        long ceiling = initial > maximum / multiplier ? maximum : Math.min(maximum, initial * multiplier);
        long floor = Math.max(1, ceiling / 2);
        long jittered = floor == ceiling ? ceiling : ThreadLocalRandom.current().nextLong(floor, ceiling + 1);
        return Duration.ofSeconds(jittered);
    }

    public record DispatchResult(String eventId, String status, int attempts) {}
}
