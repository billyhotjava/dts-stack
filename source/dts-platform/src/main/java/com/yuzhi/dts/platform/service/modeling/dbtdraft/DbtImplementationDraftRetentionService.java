package com.yuzhi.dts.platform.service.modeling.dbtdraft;

import com.yuzhi.dts.platform.repository.modeling.DbtImplementationDraftRepository;
import com.yuzhi.dts.platform.service.modeling.imports.converter.AdvancedDbtDraftStaticValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bounded retention for expired draft rows and plaintext temp workspaces orphaned by process loss. */
@Service
public class DbtImplementationDraftRetentionService {

    private static final String AUDIT_PURGE = "MODELING_DBT_DRAFT_PURGE";

    private final DbtImplementationDraftRepository repository;
    private final AdvancedDbtDraftStaticValidator validator;
    private final DbtImplementationDraftAuditRecorder audit;
    private final Clock clock;
    private final int batchSize;
    private final int maxBatches;
    private final Duration orphanAge;

    @Autowired
    public DbtImplementationDraftRetentionService(
        DbtImplementationDraftRepository repository,
        AdvancedDbtDraftStaticValidator validator,
        DbtImplementationDraftAuditRecorder audit,
        @Value("${dts.platform.modeling.dbt-draft-retention-batch-size:500}") int batchSize,
        @Value("${dts.platform.modeling.dbt-draft-retention-max-batches:20}") int maxBatches,
        @Value("${dts.platform.modeling.dbt-draft-orphan-hours:24}") long orphanHours
    ) {
        this(repository, validator, audit, Clock.systemUTC(), batchSize, maxBatches, Duration.ofHours(orphanHours));
    }

    DbtImplementationDraftRetentionService(
        DbtImplementationDraftRepository repository,
        AdvancedDbtDraftStaticValidator validator,
        DbtImplementationDraftAuditRecorder audit,
        Clock clock,
        int batchSize,
        int maxBatches,
        Duration orphanAge
    ) {
        if (batchSize < 1 || batchSize > 10_000) throw new IllegalArgumentException("batchSize must be 1..10000");
        if (maxBatches < 1 || maxBatches > 100) throw new IllegalArgumentException("maxBatches must be 1..100");
        if (orphanAge == null || orphanAge.isNegative() || orphanAge.isZero()) {
            throw new IllegalArgumentException("orphanAge must be positive");
        }
        this.repository = repository;
        this.validator = validator;
        this.audit = audit;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
        this.orphanAge = orphanAge;
    }

    @Scheduled(fixedDelayString = "${dts.platform.modeling.dbt-draft-retention-interval-ms:3600000}")
    @Transactional
    public PurgeResult purgeExpired() {
        Instant now = clock.instant();
        String correlationId = UUID.randomUUID().toString();
        String eventIdentity = "dbt-draft-purge:" + correlationId;
        try {
            int workspaceCount = validator.purgeOrphanedWorkspaces(now.minus(orphanAge), batchSize);
            int draftCount = 0;
            for (int batch = 0; batch < maxBatches; batch++) {
                int purged = repository.purgeExpiredBatch(now, batchSize);
                draftCount += purged;
                if (purged < batchSize) break;
            }
            Map<String, Object> payload = purgePayload(correlationId, now, draftCount, workspaceCount, "SUCCESS", null);
            audit.recordMachineSuccess(AUDIT_PURGE, eventIdentity, now, "dbt-drafts", payload);
            return new PurgeResult(draftCount, workspaceCount, correlationId);
        } catch (RuntimeException failure) {
            Map<String, Object> payload = purgePayload(
                correlationId,
                now,
                0,
                0,
                "FAILED",
                "DBT_DRAFT_PURGE_FAILED"
            );
            audit.recordMachineFailure(AUDIT_PURGE, eventIdentity + ":failed", now, "dbt-drafts", payload);
            throw failure;
        }
    }

    private static Map<String, Object> purgePayload(
        String correlationId,
        Instant cutoff,
        int draftCount,
        int workspaceCount,
        String result,
        String errorCode
    ) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("cutoff", cutoff.toString());
        payload.put("draftCount", draftCount);
        payload.put("workspaceCount", workspaceCount);
        payload.put("result", result);
        if (errorCode != null) payload.put("errorCode", errorCode);
        return Map.copyOf(payload);
    }

    public record PurgeResult(int draftCount, int workspaceCount, String correlationId) {}
}
