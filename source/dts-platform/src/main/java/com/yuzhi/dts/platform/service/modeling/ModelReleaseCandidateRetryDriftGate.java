package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import java.util.List;

/**
 * Detects materialization snapshot drift after an execution snapshot has been locked.
 *
 * <p>The legacy name is retained because failed-build retry was the first consumer. The same
 * snapshot comparison now protects every later delivery gate. The candidate command service
 * remains the state owner: implementations may compare execution snapshots, but must only return
 * drift reasons and never mutate candidate state.
 */
@FunctionalInterface
public interface ModelReleaseCandidateRetryDriftGate {

    List<DriftReasonView> detect(CandidateView candidate);

    /**
     * Read-only projection variant. Persistence adapters should override this when their command
     * path uses row locks; simple test or in-memory implementations can share the same comparison.
     */
    default List<DriftReasonView> detectForRead(CandidateView candidate) {
        return detect(candidate);
    }
}
