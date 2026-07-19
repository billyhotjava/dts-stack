package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredUnit;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/** Resolves field-standard evidence without copying professional owner records into ModelSpec. */
@Component
public class GovernanceModelSpecStandardEvidenceAdapter implements ModelSpecStandardEvidencePort {

    private static final Pattern NUMERIC_VERSION = Pattern.compile("(?i)^v?(\\d+)$");

    private final MetadataStandardRepository dataElements;
    private final StdCodeDirectoryRepository referenceCodes;
    private final MeasurementUnitRepository measurementUnits;

    public GovernanceModelSpecStandardEvidenceAdapter(
        MetadataStandardRepository dataElements,
        StdCodeDirectoryRepository referenceCodes,
        MeasurementUnitRepository measurementUnits
    ) {
        this.dataElements = dataElements;
        this.referenceCodes = referenceCodes;
        this.measurementUnits = measurementUnits;
    }

    @Override
    public StandardEvidence evaluate(String tenantId, ModelSpecView modelSpec) {
        if (modelSpec == null || modelSpec.fields().isEmpty()) return StandardEvidence.STALE;
        Map<String, StandardBinding> bindings = new HashMap<>();
        for (StandardBinding binding : modelSpec.standardBindings()) {
            if (binding != null && binding.fieldName() != null) bindings.put(binding.fieldName(), binding);
        }
        try {
            for (ModelField field : modelSpec.fields()) {
                if (field == null) return StandardEvidence.STALE;
                StandardBinding binding = bindings.get(field.name());
                if (binding == null || !hasCompleteReference(binding)) return StandardEvidence.STALE;

                if (binding.standardElementId() != null) {
                    if (binding.standardElementVersion() == null) return StandardEvidence.STALE;
                    MetadataStandard current = dataElements.findById(binding.standardElementId()).orElse(null);
                    if (current == null || !binding.standardElementVersion().equals(current.getVersion())) {
                        return StandardEvidence.STALE;
                    }
                }

                if (binding.referenceCode() != null && !binding.referenceCode().isBlank()) {
                    if (binding.referenceCodeVersion() == null) return StandardEvidence.STALE;
                    StdCodeDirectory current = referenceCodes.findById(binding.referenceCode()).orElse(null);
                    Integer ownerVersion = current != null ? numericVersion(current.getVersion()) : null;
                    if (
                        current == null ||
                        !Integer.valueOf(1).equals(current.getStatus()) ||
                        !binding.referenceCodeVersion().equals(ownerVersion)
                    ) {
                        return StandardEvidence.STALE;
                    }
                }

                if (binding.measurementUnitId() != null) {
                    if (binding.measurementUnitVersion() == null) return StandardEvidence.STALE;
                    StoredUnit current = measurementUnits.findCurrent(binding.measurementUnitId()).orElse(null);
                    if (
                        current == null ||
                        current.status() != MeasurementUnitStatus.ACTIVE ||
                        current.version() != binding.measurementUnitVersion()
                    ) return StandardEvidence.STALE;
                }
            }
        } catch (DataAccessException unavailable) {
            return StandardEvidence.UNKNOWN;
        }
        return StandardEvidence.CURRENT;
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

    private static Integer numericVersion(String value) {
        if (value == null) return null;
        Matcher matcher = NUMERIC_VERSION.matcher(value.trim());
        if (!matcher.matches()) return null;
        try {
            int parsed = Integer.parseInt(matcher.group(1));
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
