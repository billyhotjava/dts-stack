package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Claims due serving projections and converges them into dts-analytics without a distributed transaction. */
@Service
public class CatalogModelSemanticSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogModelSemanticSyncService.class);
    private static final int BATCH_SIZE = 50;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final List<Duration> RETRY_DELAYS = List.of(
        Duration.ofMinutes(1),
        Duration.ofMinutes(5),
        Duration.ofMinutes(15),
        Duration.ofMinutes(30),
        Duration.ofMinutes(60)
    );
    private static final Set<String> PERMANENT_ERROR_CODES = Set.of(
        "CATALOG_MODEL_SEMANTIC_SERVING_REF_REQUIRED",
        "CATALOG_MODEL_SEMANTIC_PHYSICAL_TARGET_REQUIRED",
        "CATALOG_MODEL_SEMANTIC_REVISION_MISMATCH",
        "CATALOG_MODEL_SEMANTIC_ASSET_IDENTITY_MISMATCH",
        "CATALOG_MODEL_SEMANTIC_ATOMIC_INDICATOR_INVALID",
        "CATALOG_MODEL_SEMANTIC_METRIC_FIELD_NOT_FOUND",
        "CATALOG_MODEL_SEMANTIC_TIME_FIELD_NOT_FOUND",
        "CATALOG_MODEL_SEMANTIC_AGGREGATION_UNSUPPORTED",
        "CATALOG_MODEL_SEMANTIC_CLASSIFICATION_INVALID",
        "ANALYTICS_SEMANTIC_PUBLISH_NOT_CONFIGURED"
    );

    private final CatalogModelServingProjectionRepository repository;
    private final CatalogModelSemanticPayloadFactory payloadFactory;
    private final AnalyticsSemanticPublishClient client;
    private final AuditService auditService;
    private final Clock clock;

    @Autowired
    public CatalogModelSemanticSyncService(
        CatalogModelServingProjectionRepository repository,
        CatalogModelSemanticPayloadFactory payloadFactory,
        AnalyticsSemanticPublishClient client,
        AuditService auditService
    ) {
        this(repository, payloadFactory, client, auditService, Clock.systemUTC());
    }

    CatalogModelSemanticSyncService(
        CatalogModelServingProjectionRepository repository,
        CatalogModelSemanticPayloadFactory payloadFactory,
        AnalyticsSemanticPublishClient client,
        Clock clock
    ) {
        this(repository, payloadFactory, client, null, clock);
    }

    CatalogModelSemanticSyncService(
        CatalogModelServingProjectionRepository repository,
        CatalogModelSemanticPayloadFactory payloadFactory,
        AnalyticsSemanticPublishClient client,
        AuditService auditService,
        Clock clock
    ) {
        this.repository = repository;
        this.payloadFactory = payloadFactory;
        this.client = client;
        this.auditService = auditService;
        this.clock = clock;
    }

    public SyncResult synchronizeOnce() {
        Instant now = clock.instant();
        List<SyncCandidate> candidates = repository.claimSyncCandidates(BATCH_SIZE, now, LEASE);
        int succeeded = 0;
        int failed = 0;
        int stale = 0;
        for (SyncCandidate candidate : candidates) {
            var projection = candidate.projection();
            String correlationId = UUID.randomUUID().toString();
            try {
                client.publish(payloadFactory.create(candidate));
                if (repository.markSyncSucceeded(
                    projection.tenantId(),
                    projection.modelSpecId(),
                    projection.version()
                )) {
                    succeeded++;
                } else {
                    stale++;
                }
            } catch (RuntimeException failure) {
                String errorCode = errorCode(failure);
                Instant nextAttemptAt = nextAttempt(now, candidate.syncAttempts(), errorCode);
                if (repository.markSyncFailed(
                    projection.tenantId(),
                    projection.modelSpecId(),
                    projection.version(),
                    errorCode,
                    nextAttemptAt
                )) {
                    failed++;
                    if (nextAttemptAt == null) {
                        recordTerminalFailure(candidate, errorCode, now, correlationId);
                    }
                } else {
                    stale++;
                }
                LOG.warn(
                    "Catalog semantic sync failed correlationId={} tenantId={} assetKey={} modelSpecId={} projectionVersion={} attempt={} errorCode={} nextAttemptAt={}",
                    correlationId,
                    projection.tenantId(),
                    projection.catalogAssetKey(),
                    projection.modelSpecId(),
                    projection.version(),
                    candidate.syncAttempts() + 1,
                    errorCode,
                    nextAttemptAt
                );
            }
        }
        return new SyncResult(candidates.size(), succeeded, failed, stale);
    }

    private void recordTerminalFailure(
        SyncCandidate candidate,
        String errorCode,
        Instant occurredAt,
        String correlationId
    ) {
        if (auditService == null) return;
        var projection = candidate.projection();
        int attempt = candidate.syncAttempts() + 1;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("correlationId", correlationId);
        payload.put("tenantId", projection.tenantId());
        payload.put("assetKey", projection.catalogAssetKey());
        payload.put("modelSpecId", projection.modelSpecId());
        payload.put("projectionVersion", projection.version());
        payload.put("attempt", attempt);
        payload.put("errorCode", errorCode);
        payload.put("terminalReason", isPermanent(errorCode) ? "PERMANENT_FAILURE" : "RETRY_EXHAUSTED");
        try {
            auditService.auditActionAs(
                "scheduler",
                "model-semantic-sync:" + projection.modelSpecId() + ":v" + projection.version() + ":a" + attempt,
                occurredAt,
                "MODELING_SEMANTIC_SYNC_TERMINAL_FAILURE",
                AuditStage.FAIL,
                projection.modelSpecId().toString(),
                payload
            );
        } catch (RuntimeException auditFailure) {
            LOG.error(
                "Catalog semantic sync terminal audit failed correlationId={} tenantId={} assetKey={} modelSpecId={} projectionVersion={} attempt={} errorCode={}",
                correlationId,
                projection.tenantId(),
                projection.catalogAssetKey(),
                projection.modelSpecId(),
                projection.version(),
                attempt,
                errorCode,
                auditFailure
            );
        }
    }

    private static Instant nextAttempt(Instant now, int previousAttempts, String errorCode) {
        if (isPermanent(errorCode)) return null;
        int failureNumber = previousAttempts + 1;
        if (failureNumber > RETRY_DELAYS.size()) return null;
        return now.plus(RETRY_DELAYS.get(failureNumber - 1));
    }

    private static boolean isPermanent(String errorCode) {
        if (PERMANENT_ERROR_CODES.contains(errorCode)) return true;
        if (errorCode != null && errorCode.startsWith("ANALYTICS_SEMANTIC_PUBLISH_HTTP_")) {
            try {
                int status = Integer.parseInt(errorCode.substring("ANALYTICS_SEMANTIC_PUBLISH_HTTP_".length()));
                return status >= 400 && status < 500 && status != 408 && status != 429;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    private static String errorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && message.matches("[A-Z0-9_]{3,160}")) return message;
        return "CATALOG_MODEL_SEMANTIC_SYNC_FAILED";
    }

    public record SyncResult(int claimed, int succeeded, int failed, int stale) {}
}
