package com.yuzhi.dts.platform.service.modeling;

import java.util.UUID;

/** Authorization port for maintaining a catalog domain from the modeling boundary. */
public interface ModelSpecDomainWriteAccessPort {
    boolean canMaintain(UUID domainId);
}
