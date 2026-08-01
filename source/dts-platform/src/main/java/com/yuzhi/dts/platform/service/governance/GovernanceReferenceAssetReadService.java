package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort.IndicatorView;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceAssetReadPort.ReferenceCodeView;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GovernanceReferenceAssetReadService implements GovernanceReferenceAssetReadPort {

    private final StdCodeDirectoryRepository referenceCodes;
    private final GovIndicatorDefinitionRepository indicators;

    public GovernanceReferenceAssetReadService(
        StdCodeDirectoryRepository referenceCodes,
        GovIndicatorDefinitionRepository indicators
    ) {
        this.referenceCodes = referenceCodes;
        this.indicators = indicators;
    }

    @Override
    public List<ReferenceCodeView> referenceCodes() {
        return referenceCodes.findAll().stream().map(GovernanceReferenceAssetReadService::view).toList();
    }

    @Override
    public Optional<ReferenceCodeView> referenceCodeByCode(String code) {
        return referenceCodes.findByCodeTypeCodeIgnoreCase(code).map(GovernanceReferenceAssetReadService::view);
    }

    @Override
    public Optional<ReferenceCodeView> referenceCodeById(String id) {
        return referenceCodes.findById(id).map(GovernanceReferenceAssetReadService::view);
    }

    @Override
    public List<IndicatorView> indicators() {
        return indicators
            .findAll()
            .stream()
            .map(GovernanceReferenceAssetReadService::view)
            .toList();
    }

    @Override
    public Optional<IndicatorView> indicatorById(java.util.UUID id) {
        return id == null ? Optional.empty() : indicators.findById(id).map(GovernanceReferenceAssetReadService::view);
    }

    @Override
    public Optional<IndicatorView> indicatorByCode(String code) {
        return code == null
            ? Optional.empty()
            : indicators.findFirstByCodeIgnoreCase(code).map(GovernanceReferenceAssetReadService::view);
    }

    private static ReferenceCodeView view(com.yuzhi.dts.platform.domain.governance.StdCodeDirectory directory) {
        return new ReferenceCodeView(
            directory.getCodeTypeId(),
            directory.getCodeTypeCode(),
            directory.getCodeTypeName(),
            directory.getBizCatalog()
        );
    }

    private static IndicatorView view(com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition indicator) {
        return new IndicatorView(
            indicator.getId(),
            indicator.getCode(),
            indicator.getName(),
            indicator.getDefinition(),
            indicator.getExpressionSql(),
            indicator.getTags()
        );
    }
}
