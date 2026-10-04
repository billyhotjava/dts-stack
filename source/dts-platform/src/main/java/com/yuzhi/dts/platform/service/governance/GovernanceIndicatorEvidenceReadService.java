package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceIndicatorEvidenceReadPort.EvidenceState;
import com.yuzhi.dts.platform.service.governance.GovernanceIndicatorEvidenceReadPort.IndicatorEvidence;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GovernanceIndicatorEvidenceReadService implements GovernanceIndicatorEvidenceReadPort {

    private static final Pattern INDICATOR_VERSION = Pattern.compile("(?i)^v?([1-9][0-9]*)$");

    private final GovIndicatorDefinitionRepository indicators;

    public GovernanceIndicatorEvidenceReadService(GovIndicatorDefinitionRepository indicators) {
        this.indicators = indicators;
    }

    @Override
    public IndicatorEvidence read(UUID indicatorId) {
        if (indicatorId == null) return new IndicatorEvidence(EvidenceState.STALE, null);
        try {
            GovIndicatorDefinition indicator = indicators.findById(indicatorId).orElse(null);
            Integer version = indicator == null ? null : parseVersion(indicator.getVersion());
            if (indicator == null || !"PUBLISHED".equalsIgnoreCase(indicator.getStatus()) || version == null) {
                return new IndicatorEvidence(EvidenceState.STALE, null);
            }
            return new IndicatorEvidence(EvidenceState.CURRENT, version);
        } catch (DataAccessException unavailable) {
            return new IndicatorEvidence(EvidenceState.UNKNOWN, null);
        }
    }

    private static Integer parseVersion(String value) {
        if (value == null) return null;
        Matcher matcher = INDICATOR_VERSION.matcher(value.trim());
        if (!matcher.matches()) return null;
        try {
            return Integer.valueOf(matcher.group(1));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }
}
