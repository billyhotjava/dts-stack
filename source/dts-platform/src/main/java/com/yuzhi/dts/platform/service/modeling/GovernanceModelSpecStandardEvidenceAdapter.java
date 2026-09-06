package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.governance.GovernanceStandardEvidenceReadPort;
import com.yuzhi.dts.platform.service.governance.GovernanceStandardEvidenceReadPort.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import org.springframework.stereotype.Component;

/** Resolves field-standard evidence without copying professional owner records into ModelSpec. */
@Component
public class GovernanceModelSpecStandardEvidenceAdapter implements ModelSpecStandardEvidencePort {

    private final GovernanceStandardEvidenceReadPort evidence;

    public GovernanceModelSpecStandardEvidenceAdapter(GovernanceStandardEvidenceReadPort evidence) {
        this.evidence = evidence;
    }

    @Override
    public StandardEvidence evaluate(String tenantId, ModelSpecView modelSpec) {
        if (modelSpec == null) return StandardEvidence.STALE;
        for (StandardBinding binding : modelSpec.standardBindings()) {
            if (!hasDeclaredReference(binding)) continue;
            if (!hasCompleteReference(binding)) return StandardEvidence.STALE;

            EvidenceState state = currentOwnerState(binding);
            if (state == EvidenceState.UNKNOWN) return StandardEvidence.UNKNOWN;
            if (state != EvidenceState.CURRENT) return StandardEvidence.STALE;
        }
        return StandardEvidence.CURRENT;
    }

    private static boolean hasDeclaredReference(StandardBinding binding) {
        return binding != null && (
            binding.standardElementId() != null ||
            (binding.referenceCode() != null && !binding.referenceCode().isBlank()) ||
            binding.measurementUnitId() != null
        );
    }

    private EvidenceState currentOwnerState(StandardBinding binding) {
        if (binding.standardElementId() != null) {
            if (binding.standardElementVersion() == null) return EvidenceState.STALE;
            EvidenceState state = evidence.dataElement(binding.standardElementId(), binding.standardElementVersion());
            if (state != EvidenceState.CURRENT) return state;
        }
        if (binding.referenceCode() != null && !binding.referenceCode().isBlank()) {
            if (binding.referenceCodeVersion() == null) return EvidenceState.STALE;
            EvidenceState state = evidence.referenceCode(binding.referenceCode(), binding.referenceCodeVersion());
            if (state != EvidenceState.CURRENT) return state;
        }
        if (binding.measurementUnitId() != null) {
            if (binding.measurementUnitVersion() == null) return EvidenceState.STALE;
            EvidenceState state = evidence.measurementUnit(binding.measurementUnitId(), binding.measurementUnitVersion());
            if (state != EvidenceState.CURRENT) return state;
        }
        return EvidenceState.CURRENT;
    }

    private static boolean hasCompleteReference(StandardBinding binding) {
        return (
            binding.standardElementId() != null && binding.standardElementVersion() != null
        ) || (
            binding.referenceCode() != null && !binding.referenceCode().isBlank() && binding.referenceCodeVersion() != null
        ) || (
            binding.measurementUnitId() != null && binding.measurementUnitVersion() != null
        );
    }
}
