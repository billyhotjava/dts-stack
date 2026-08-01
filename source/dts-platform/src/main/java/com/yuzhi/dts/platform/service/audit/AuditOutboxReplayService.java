package com.yuzhi.dts.platform.service.audit;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ReplayCommand;
import com.yuzhi.dts.platform.repository.audit.PlatformAuditOutboxRepository.ReplayTarget;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditOutboxReplayService {

    private static final String REPLAY_ACTION = "AUDIT_OUTBOX_REPLAY_REQUESTED";
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final PlatformAuditOutboxRepository repository;
    private final AuditService auditService;
    private final AuditTenantResolver tenantResolver;
    private final Clock clock;

    @Autowired
    public AuditOutboxReplayService(
        PlatformAuditOutboxRepository repository,
        AuditService auditService,
        AuditTenantResolver tenantResolver
    ) {
        this(repository, auditService, tenantResolver, Clock.systemUTC());
    }

    AuditOutboxReplayService(
        PlatformAuditOutboxRepository repository,
        AuditService auditService,
        AuditTenantResolver tenantResolver,
        Clock clock
    ) {
        this.repository = repository;
        this.auditService = auditService;
        this.tenantResolver = tenantResolver;
        this.clock = clock;
    }

    @Transactional
    public ReplayView replay(UUID outboxId, String expectedPayloadHash, AuditOutboxReplayReason reasonCode) {
        if (outboxId == null) throw AuditOutboxReplayException.invalidRequest("outboxId is required");
        String hash = normalizeHash(expectedPayloadHash);
        if (reasonCode == null) throw AuditOutboxReplayException.invalidRequest("reasonCode is required");
        String tenantId = tenantResolver.currentTenantId();
        ReplayTarget target = repository
            .findReplayTarget(tenantId, outboxId)
            .orElseThrow(AuditOutboxReplayException::notFound);
        if (!"DEAD".equals(target.status())) {
            throw AuditOutboxReplayException.conflict(
                "AUDIT_OUTBOX_NOT_DEAD",
                "Only DEAD audit outbox rows can be replayed"
            );
        }
        if (!hash.equals(target.payloadHash())) {
            throw AuditOutboxReplayException.conflict(
                "AUDIT_OUTBOX_PAYLOAD_CHANGED",
                "Audit outbox payload hash no longer matches"
            );
        }
        if (target.bodyJson() == null || !target.payloadHash().equals(sha256(target.bodyJson()))) {
            throw AuditOutboxReplayException.conflict(
                "AUDIT_OUTBOX_PAYLOAD_CHANGED",
                "Audit outbox body no longer matches its payload hash"
            );
        }

        Instant now = clock.instant();
        int updated = repository.replayDead(new ReplayCommand(tenantId, outboxId, hash, target.bodyJson(), now, now));
        if (updated != 1) {
            throw AuditOutboxReplayException.conflict(
                "AUDIT_OUTBOX_REPLAY_CONFLICT",
                "Audit outbox row changed before replay"
            );
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("outboxId", outboxId.toString());
        auditPayload.put("eventId", target.eventId());
        auditPayload.put("producer", target.producer());
        auditPayload.put("attemptCount", target.attemptCount());
        auditPayload.put("reasonCode", reasonCode.name());
        auditService.auditActionStrict(REPLAY_ACTION, AuditStage.SUCCESS, outboxId.toString(), auditPayload);
        return new ReplayView(outboxId, "PENDING", target.attemptCount(), now);
    }

    private String normalizeHash(String value) {
        if (value == null || !SHA256.matcher(value.trim()).matches()) {
            throw AuditOutboxReplayException.invalidRequest("expectedPayloadHash must be a lowercase SHA-256 value");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public record ReplayView(UUID outboxId, String status, int attemptCount, Instant nextAttemptAt) {}
}
