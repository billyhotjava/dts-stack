package com.yuzhi.dts.platform.service.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable governance-owned projections used by cross-domain reference queries. */
public interface GovernanceReferenceAssetReadPort {

    List<ReferenceCodeView> referenceCodes();

    Optional<ReferenceCodeView> referenceCodeByCode(String code);

    Optional<ReferenceCodeView> referenceCodeById(String id);

    List<IndicatorView> indicators();

    Optional<IndicatorView> indicatorById(UUID id);

    Optional<IndicatorView> indicatorByCode(String code);

    record ReferenceCodeView(String id, String code, String name, String businessCatalog) {}

    record IndicatorView(
        UUID id,
        String code,
        String name,
        String definition,
        String expressionSql,
        String tags
    ) {}
}
