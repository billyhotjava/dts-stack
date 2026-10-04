package com.yuzhi.dts.platform.web.rest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanActorProvider.WarehousePlanActor;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * JVM-local admission control for the expensive model-package preview route.
 *
 * <p>The actor permit is acquired before reading the body; the plan permit is bound after the
 * bounded parser resolves {@code context.planId}. Actor limits prevent plan-id rotation from
 * bypassing admission, while plan limits protect a shared target from concurrent previews.
 */
@Component
public class ModelSpecImportPreviewAdmissionGate {

    static final int MAX_REQUESTS_PER_ACTOR_WINDOW = 10;
    static final int MAX_REQUESTS_PER_PLAN_WINDOW = 30;
    static final int MAX_CONCURRENT_PER_ACTOR = 2;
    static final int MAX_CONCURRENT_PER_PLAN = 4;

    private static final Duration RATE_WINDOW = Duration.ofMinutes(1);
    private static final Duration CONCURRENCY_ENTRY_TTL = Duration.ofHours(1);
    private static final long MAX_TRACKED_KEYS = 20_000;
    private static final String UNKNOWN_ACTOR = "_unknown";

    private final WarehousePlanActorProvider actorProvider;
    private final Cache<String, AtomicInteger> actorRates = rateCache();
    private final Cache<String, AtomicInteger> planRates = rateCache();
    private final Cache<String, Semaphore> actorConcurrency = concurrencyCache();
    private final Cache<String, Semaphore> planConcurrency = concurrencyCache();

    public ModelSpecImportPreviewAdmissionGate(WarehousePlanActorProvider actorProvider) {
        this.actorProvider = actorProvider;
    }

    public Admission enter() {
        WarehousePlanActor actor = actorProvider.currentActor();
        return enter(actor == null ? null : actor.ownerId());
    }

    Admission enter(String actorId) {
        String actorKey = normalizeActor(actorId);
        requireRate(actorRates, actorKey, MAX_REQUESTS_PER_ACTOR_WINDOW);
        Semaphore actorPermit = actorConcurrency.get(
            actorKey,
            ignored -> new Semaphore(MAX_CONCURRENT_PER_ACTOR, true)
        );
        if (!actorPermit.tryAcquire()) {
            throw busy();
        }
        return new ActiveAdmission(actorPermit);
    }

    private final class ActiveAdmission implements Admission {

        private final Semaphore actorPermit;
        private final AtomicBoolean closed = new AtomicBoolean();
        private Semaphore planPermit;
        private UUID admittedPlanId;

        private ActiveAdmission(Semaphore actorPermit) {
            this.actorPermit = actorPermit;
        }

        @Override
        public synchronized void admitPlan(UUID planId) {
            if (closed.get()) {
                throw new IllegalStateException("Model import preview admission is already closed");
            }
            if (planId == null) {
                return;
            }
            if (admittedPlanId != null) {
                if (admittedPlanId.equals(planId)) {
                    return;
                }
                throw new IllegalStateException("Model import preview admission is already bound to a plan");
            }
            String planKey = planId.toString();
            requireRate(planRates, planKey, MAX_REQUESTS_PER_PLAN_WINDOW);
            Semaphore candidate = planConcurrency.get(
                planKey,
                ignored -> new Semaphore(MAX_CONCURRENT_PER_PLAN, true)
            );
            if (!candidate.tryAcquire()) {
                throw busy();
            }
            admittedPlanId = planId;
            planPermit = candidate;
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            Semaphore acquiredPlanPermit = planPermit;
            if (acquiredPlanPermit != null) {
                acquiredPlanPermit.release();
            }
            actorPermit.release();
        }
    }

    public interface Admission extends AutoCloseable {
        void admitPlan(UUID planId);

        @Override
        void close();
    }

    private static void requireRate(Cache<String, AtomicInteger> counters, String key, int maximum) {
        AtomicInteger counter = counters.get(key, ignored -> new AtomicInteger());
        int count = counter.updateAndGet(current -> current >= maximum ? maximum + 1 : current + 1);
        if (count > maximum) {
            throw rateLimited();
        }
    }

    private static Cache<String, AtomicInteger> rateCache() {
        return Caffeine
            .newBuilder()
            .expireAfterWrite(RATE_WINDOW)
            .maximumSize(MAX_TRACKED_KEYS)
            .build();
    }

    private static Cache<String, Semaphore> concurrencyCache() {
        return Caffeine
            .newBuilder()
            .expireAfterAccess(CONCURRENCY_ENTRY_TTL)
            .maximumSize(MAX_TRACKED_KEYS)
            .build();
    }

    private static String normalizeActor(String actorId) {
        return actorId == null || actorId.isBlank() ? UNKNOWN_ACTOR : actorId.trim();
    }

    private static ModelSpecImportPreviewRequestParser.RequestLimitException rateLimited() {
        return new ModelSpecImportPreviewRequestParser.RequestLimitException(
            HttpStatus.TOO_MANY_REQUESTS,
            "MODEL_IMPORT_PREVIEW_RATE_LIMITED",
            "Too many model import preview requests; retry later"
        );
    }

    private static ModelSpecImportPreviewRequestParser.RequestLimitException busy() {
        return new ModelSpecImportPreviewRequestParser.RequestLimitException(
            HttpStatus.TOO_MANY_REQUESTS,
            "MODEL_IMPORT_PREVIEW_BUSY",
            "Another model import preview is already running; retry later"
        );
    }
}
