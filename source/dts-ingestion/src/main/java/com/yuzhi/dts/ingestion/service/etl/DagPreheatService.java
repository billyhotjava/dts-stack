package com.yuzhi.dts.ingestion.service.etl;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Asynchronously "preheats" Airflow DAGs after task create/update so that the
 * scheduler has already registered the DAG by the time the user clicks execute.
 * <p>
 * This is a separate component (not inlined in {@link com.yuzhi.dts.ingestion.service.IngestionTaskService})
 * so that Spring's {@code @Async} proxy works correctly — self-invocation within the
 * same bean bypasses the proxy and would run synchronously.
 */
@Component
public class DagPreheatService {

    private static final Logger LOG = LoggerFactory.getLogger(DagPreheatService.class);

    /** Max number of polling attempts during preheat. */
    private static final int PREHEAT_MAX_ATTEMPTS = 12;
    /** Seconds between each polling attempt. */
    private static final int PREHEAT_POLL_SECONDS = 10;

    private final AirflowClient airflowClient;

    public DagPreheatService(AirflowClient airflowClient) {
        this.airflowClient = airflowClient;
    }

    /**
     * Best-effort background poll to warm up the Airflow scheduler for the given DAG.
     * Runs asynchronously — caller does not wait for result.
     * No exceptions propagate; failures are logged and silently ignored.
     *
     * @param dagId the Airflow DAG identifier to preheat
     */
    @Async("ingestionTaskExecutor")
    public CompletableFuture<Boolean> preheatDag(String dagId) {
        if (!StringUtils.hasText(dagId)) {
            return CompletableFuture.completedFuture(false);
        }
        LOG.info("[dag-preheat] starting preheat for dagId={}", dagId);
        try {
            // Quick check — maybe the scheduler already picked it up
            if (airflowClient.isDagRegistered(dagId)) {
                LOG.info("[dag-preheat] dagId={} already registered, preheat complete", dagId);
                return CompletableFuture.completedFuture(true);
            }

            // Poll a few times waiting for the scheduler to register the DAG
            for (int attempt = 1; attempt <= PREHEAT_MAX_ATTEMPTS; attempt++) {
                try {
                    Thread.sleep(PREHEAT_POLL_SECONDS * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.debug("[dag-preheat] interrupted while waiting for dagId={}", dagId);
                    return CompletableFuture.completedFuture(false);
                }
                if (airflowClient.isDagRegistered(dagId)) {
                    LOG.info("[dag-preheat] dagId={} registered after {} poll(s), preheat complete", dagId, attempt);
                    return CompletableFuture.completedFuture(true);
                }
                LOG.debug("[dag-preheat] dagId={} not yet registered, attempt {}/{}", dagId, attempt, PREHEAT_MAX_ATTEMPTS);
            }

            LOG.info("[dag-preheat] dagId={} not registered after {} attempts — will be picked up at execution time",
                dagId, PREHEAT_MAX_ATTEMPTS);
            return CompletableFuture.completedFuture(false);
        } catch (Exception ex) {
            LOG.warn("[dag-preheat] preheat failed for dagId={}: {}", dagId, ex.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }
}
