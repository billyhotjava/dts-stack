package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.repository.modeling.DataStandardRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class JpaLegacyCodeSetMigrationAdapter implements LegacyCodeSetMigrationPort {

    private final DataStandardRepository dataStandards;

    public JpaLegacyCodeSetMigrationAdapter(DataStandardRepository dataStandards) {
        this.dataStandards = dataStandards;
    }

    @Override
    public List<LegacyCodeSetCandidate> findCandidates() {
        return dataStandards
            .findAll()
            .stream()
            .filter(standard -> StringUtils.hasText(standard.getCode()))
            .filter(standard -> StringUtils.hasText(standard.getCodeSet()) && standard.getCodeSet().contains(":"))
            .map(this::candidate)
            .toList();
    }

    @Override
    @Transactional
    public boolean replaceInlineCodeSet(UUID standardId, String expectedInlineCodeSet, String codeTypeCode) {
        if (standardId == null || !StringUtils.hasText(expectedInlineCodeSet) || !StringUtils.hasText(codeTypeCode)) {
            return false;
        }
        return dataStandards.replaceCodeSetIfCurrent(standardId, expectedInlineCodeSet, codeTypeCode.trim()) == 1;
    }

    private LegacyCodeSetCandidate candidate(DataStandard standard) {
        return new LegacyCodeSetCandidate(
            standard.getId(),
            standard.getCode(),
            standard.getName(),
            standard.getDomain(),
            standard.getDataType(),
            standard.getCurrentVersion(),
            standard.getCodeSet()
        );
    }
}
