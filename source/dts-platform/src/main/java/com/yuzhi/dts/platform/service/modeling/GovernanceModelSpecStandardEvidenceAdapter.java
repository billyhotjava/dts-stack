package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.governance.GovernanceStandardEvidenceReadPort;
import com.yuzhi.dts.platform.service.governance.GovernanceStandardEvidenceReadPort.EvidenceState;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import java.util.HashMap;
import java.util.Map;
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
        if (modelSpec == null || modelSpec.fields().isEmpty()) return StandardEvidence.STALE;
        Map<String, StandardBinding> bindings = new HashMap<>();
        for (StandardBinding binding : modelSpec.standardBindings()) {
            if (binding != null && binding.fieldName() != null) bindings.put(binding.fieldName(), binding);
        }
        for (ModelField field : modelSpec.fields()) {
            if (field == null) return StandardEvidence.STALE;
            StandardBinding binding = bindings.get(field.name());
            if (binding == null || !hasCompleteReference(binding)) return StandardEvidence.STALE;

            EvidenceState state = currentOwnerState(binding);
            if (state == EvidenceState.UNKNOWN) return StandardEvidence.UNKNOWN;
            if (state != EvidenceState.CURRENT) return StandardEvidence.STALE;
        }
        return StandardEvidence.CURRENT;
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
