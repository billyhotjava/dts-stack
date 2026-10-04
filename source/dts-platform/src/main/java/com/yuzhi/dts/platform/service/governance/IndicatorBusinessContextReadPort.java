package com.yuzhi.dts.platform.service.governance;

import java.util.Optional;
import java.util.UUID;

/** Read-only architecture-dictionary boundary used by the indicator owner. */
public interface IndicatorBusinessContextReadPort {

    Optional<DomainNode> domain(UUID id);

    Optional<BusinessProcessNode> businessProcess(UUID id);

    record DomainNode(UUID id, UUID parentId, String lifecycleStatus) {}

    record BusinessProcessNode(UUID id, UUID domainId, boolean confirmed) {}
}
