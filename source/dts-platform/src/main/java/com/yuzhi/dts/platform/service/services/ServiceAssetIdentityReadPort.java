package com.yuzhi.dts.platform.service.services;

import java.util.Optional;
import java.util.UUID;

/** Service-domain identity projection consumed by the catalog boundary. */
public interface ServiceAssetIdentityReadPort {

    Optional<ApiAsset> findApiById(UUID id);

    Optional<ApiAsset> findApiByCode(String code);

    record ApiAsset(UUID id, String code) {}
}
