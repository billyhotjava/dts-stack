package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateOrigin;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/** Shared admission rule; callers hold the canonical plan lock before reserving scope. */
public final class ModelCandidateScopePolicy {
    private ModelCandidateScopePolicy() {}

    public static boolean isActive(CandidateView candidate) {
        return !Set.of(DeliveryStatus.REJECTED, DeliveryStatus.ROLLED_BACK, DeliveryStatus.STALE,
            DeliveryStatus.CANCELLED, DeliveryStatus.PUBLISHED).contains(candidate.status());
    }

    public static boolean conflicts(CandidateView existing, CandidateOrigin requestedOrigin,
        String environment, Collection<UUID> modelIds) {
        if (!isActive(existing)) return false;
        if (existing.origin() != CandidateOrigin.SCHEMA_ONLY_INTENT && requestedOrigin != CandidateOrigin.SCHEMA_ONLY_INTENT) {
            return true;
        }
        return existing.environment().equals(environment) && existing.entries().stream()
            .anyMatch(entry -> modelIds.contains(entry.modelSpecId()));
    }
}
