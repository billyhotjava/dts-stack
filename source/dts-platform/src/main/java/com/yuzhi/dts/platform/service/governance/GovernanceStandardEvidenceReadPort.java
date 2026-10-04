package com.yuzhi.dts.platform.service.governance;

import java.util.UUID;

/** Read-only professional-owner contract consumed by model release gates. */
public interface GovernanceStandardEvidenceReadPort {

    EvidenceState dataElement(UUID elementId, int expectedVersion);

    EvidenceState referenceCode(String codeTypeId, int expectedVersion);

    EvidenceState measurementUnit(UUID unitId, int expectedVersion);

    enum EvidenceState {
        CURRENT,
        STALE,
        UNKNOWN,
    }
}
