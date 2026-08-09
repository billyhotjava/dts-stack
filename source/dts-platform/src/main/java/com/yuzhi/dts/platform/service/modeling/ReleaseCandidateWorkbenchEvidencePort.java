package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.CandidateView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.EntryEvidenceView;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateContract.ModelMaterializationStatusView;
import java.util.List;
import java.util.UUID;

/** Read-only port for persisted per-model build and physical-relation evidence. */
public interface ReleaseCandidateWorkbenchEvidencePort {

    List<EntryEvidenceView> findCurrent(CandidateView candidate);

    List<ModelMaterializationStatusView> findLatest(String tenantId, UUID planId, List<UUID> modelSpecIds);
}
