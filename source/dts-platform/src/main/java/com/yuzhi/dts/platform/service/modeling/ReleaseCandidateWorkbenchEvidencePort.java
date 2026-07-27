package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import java.util.List;

/** Read-only port for persisted per-model build and physical-relation evidence. */
public interface ReleaseCandidateWorkbenchEvidencePort {

    List<EntryEvidenceView> findCurrent(CandidateView candidate);
}
