package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.DriftReasonView;
import java.util.List;

/**
 * Detects materialization snapshot drift before a failed release build is retried.
 *
 * <p>The candidate command service remains the state owner. Implementations may lock and compare
 * execution snapshots, but must only return drift reasons; they must not mutate candidate state.
 */
@FunctionalInterface
public interface ModelReleaseCandidateRetryDriftGate {

    List<DriftReasonView> detect(CandidateView candidate);
}
