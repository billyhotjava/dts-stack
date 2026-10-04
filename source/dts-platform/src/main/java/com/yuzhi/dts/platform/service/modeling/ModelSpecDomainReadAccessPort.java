package com.yuzhi.dts.platform.service.modeling;

import java.util.Set;
import java.util.UUID;

/** Authorization port for reading a catalog domain from the modeling boundary. */
public interface ModelSpecDomainReadAccessPort {
    boolean canRead(UUID domainId);

    Set<UUID> visibleDomainIds();
}
