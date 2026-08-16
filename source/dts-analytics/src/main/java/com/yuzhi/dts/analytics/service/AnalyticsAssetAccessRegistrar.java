package com.yuzhi.dts.analytics.service;

import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.web.support.PlatformContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsAssetAccessRegistrar {

    private final PlatformPermissionClient platformClient;
    private final AnalyticsOutboundPlatformProperties platformProperties;

    public AnalyticsAssetAccessRegistrar(
        PlatformPermissionClient platformClient,
        AnalyticsOutboundPlatformProperties platformProperties
    ) {
        this.platformClient = platformClient;
        this.platformProperties = platformProperties;
    }

    public void register(String assetType, Long assetId, AnalyticsUser creator, HttpServletRequest request) {
        String username = creator == null ? null : trimToNull(creator.getPlatformUsername());
        if (!platformProperties.isEnabled() || username == null || assetId == null) {
            return;
        }
        platformClient.registerAssetAccess(
            assetType,
            String.valueOf(assetId),
            PlatformContext.from(request).dept(),
            username
        );
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
