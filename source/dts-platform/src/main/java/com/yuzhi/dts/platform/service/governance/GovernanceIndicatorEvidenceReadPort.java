package com.yuzhi.dts.platform.service.governance;

import java.util.UUID;

/** Published indicator owner evidence consumed by WarehousePlan projections. */
public interface GovernanceIndicatorEvidenceReadPort {

    IndicatorEvidence read(UUID indicatorId);

    enum EvidenceState {
        CURRENT,
        STALE,
        UNKNOWN,
    }

    record IndicatorEvidence(EvidenceState state, Integer version) {
        public IndicatorEvidence {
            if (state == null) throw new IllegalArgumentException("state is required");
            if (state == EvidenceState.CURRENT && (version == null || version <= 0)) {
                throw new IllegalArgumentException("current indicator evidence requires a positive version");
            }
            if (state != EvidenceState.CURRENT && version != null) {
                throw new IllegalArgumentException("non-current indicator evidence cannot expose a version");
            }
        }
    }
}
