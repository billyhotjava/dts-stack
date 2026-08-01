package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository;
import com.yuzhi.dts.platform.repository.governance.MeasurementUnitRepository.StoredUnit;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceStandardEvidenceReadPort.EvidenceState;
import com.yuzhi.dts.platform.service.governance.MeasurementUnitContract.MeasurementUnitStatus;
import com.yuzhi.dts.platform.service.modeling.GovernedStandardReadPort;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns reads of governance standard records and exposes only release-gate evidence. */
@Service
@Transactional(readOnly = true)
public class GovernanceStandardEvidenceReadService implements GovernanceStandardEvidenceReadPort {

    private static final Pattern NUMERIC_VERSION = Pattern.compile("(?i)^v?(\\d+)$");

    private final GovernedStandardReadPort governedStandards;
    private final StdCodeDirectoryRepository referenceCodes;
    private final MeasurementUnitRepository measurementUnits;

    public GovernanceStandardEvidenceReadService(
        GovernedStandardReadPort governedStandards,
        StdCodeDirectoryRepository referenceCodes,
        MeasurementUnitRepository measurementUnits
    ) {
        this.governedStandards = governedStandards;
        this.referenceCodes = referenceCodes;
        this.measurementUnits = measurementUnits;
    }

    @Override
    public EvidenceState dataElement(UUID elementId, int expectedVersion) {
        if (elementId == null || expectedVersion <= 0) return EvidenceState.STALE;
        try {
            Integer currentVersion = governedStandards.findDataElementVersion(elementId).orElse(null);
            return Integer.valueOf(expectedVersion).equals(currentVersion)
                ? EvidenceState.CURRENT
                : EvidenceState.STALE;
        } catch (DataAccessException unavailable) {
            return EvidenceState.UNKNOWN;
        }
    }

    @Override
    public EvidenceState referenceCode(String codeTypeId, int expectedVersion) {
        if (codeTypeId == null || codeTypeId.isBlank() || expectedVersion <= 0) return EvidenceState.STALE;
        try {
            StdCodeDirectory current = referenceCodes.findById(codeTypeId).orElse(null);
            Integer ownerVersion = current == null ? null : numericVersion(current.getVersion());
            return current != null &&
                    Integer.valueOf(1).equals(current.getStatus()) &&
                    Integer.valueOf(expectedVersion).equals(ownerVersion)
                ? EvidenceState.CURRENT
                : EvidenceState.STALE;
        } catch (DataAccessException unavailable) {
            return EvidenceState.UNKNOWN;
        }
    }

    @Override
    public EvidenceState measurementUnit(UUID unitId, int expectedVersion) {
        if (unitId == null || expectedVersion <= 0) return EvidenceState.STALE;
        try {
            StoredUnit current = measurementUnits.findCurrent(unitId).orElse(null);
            return current != null &&
                    current.status() == MeasurementUnitStatus.ACTIVE &&
                    current.version() == expectedVersion
                ? EvidenceState.CURRENT
                : EvidenceState.STALE;
        } catch (DataAccessException unavailable) {
            return EvidenceState.UNKNOWN;
        }
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
