package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        Duration.ofMinutes(30)
    );

    private final CatalogModelServingProjectionRepository repository;
    private final CatalogModelSemanticPayloadFactory payloadFactory;
    private final AnalyticsSemanticPublishClient client;
    private final Clock clock;

    @Autowired
    public CatalogModelSemanticSyncService(
        CatalogModelServingProjectionRepository repository,
        CatalogModelSemanticPayloadFactory payloadFactory,
        AnalyticsSemanticPublishClient client
    ) {
        this(repository, payloadFactory, client, Clock.systemUTC());
    }

    CatalogModelSemanticSyncService(
        CatalogModelServingProjectionRepository repository,
        CatalogModelSemanticPayloadFactory payloadFactory,
        AnalyticsSemanticPublishClient client,
        Clock clock
    ) {
        this.repository = repository;
        this.payloadFactory = payloadFactory;
        this.client = client;
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
                Instant nextAttemptAt = nextAttempt(now, candidate.syncAttempts());
                if (repository.markSyncFailed(
                    projection.tenantId(),
                    projection.modelSpecId(),
                    projection.version(),
                    errorCode,
                    nextAttemptAt
                )) {
                    failed++;
                } else {
                    stale++;
                }
                LOG.warn(
                    "Catalog semantic sync failed modelSpecId={} attempt={} code={}",
                    projection.modelSpecId(),
                    candidate.syncAttempts() + 1,
                    errorCode
                );
            }
        }
        return new SyncResult(candidates.size(), succeeded, failed, stale);
    }

    private static Instant nextAttempt(Instant now, int previousAttempts) {
        int failureNumber = previousAttempts + 1;
        if (failureNumber >= 5) return null;
        return now.plus(RETRY_DELAYS.get(failureNumber - 1));
    }

    private static String errorCode(RuntimeException failure) {
        String message = failure.getMessage();
        if (message != null && message.matches("[A-Z0-9_]{3,160}")) return message;
        return "CATALOG_MODEL_SEMANTIC_SYNC_FAILED";
    }

    public record SyncResult(int claimed, int succeeded, int failed, int stale) {}
}
