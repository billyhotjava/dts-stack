package com.yuzhi.dts.analytics.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.yuzhi.dts.analytics.config.AnalyticsOutboundPlatformProperties;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AnalyticsAssetAccessRegistrarTest {

    @Test
    void registersSavedAssetUsingTrustedPlatformIdentityAndDepartment() {
        PlatformPermissionClient client = org.mockito.Mockito.mock(PlatformPermissionClient.class);
        AnalyticsOutboundPlatformProperties properties = new AnalyticsOutboundPlatformProperties();
        AnalyticsAssetAccessRegistrar registrar = new AnalyticsAssetAccessRegistrar(client, properties);
        AnalyticsUser user = user("xiezm");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-Dept-Code", "D1");

        registrar.register("CARD", 42L, user, request);

        verify(client).registerAssetAccess("CARD", "42", "D1", "xiezm");
    }

    @Test
    void disabledPlatformOrLocalOnlyUserDoesNotCreateRemoteOwnership() {
        PlatformPermissionClient client = org.mockito.Mockito.mock(PlatformPermissionClient.class);
        AnalyticsOutboundPlatformProperties disabled = new AnalyticsOutboundPlatformProperties();
        disabled.setEnabled(false);
        AnalyticsAssetAccessRegistrar disabledRegistrar = new AnalyticsAssetAccessRegistrar(client, disabled);

        disabledRegistrar.register("CARD", 42L, user("xiezm"), new MockHttpServletRequest());
        new AnalyticsAssetAccessRegistrar(client, new AnalyticsOutboundPlatformProperties())
            .register("CARD", 42L, user(null), new MockHttpServletRequest());

        verify(client, never()).registerAssetAccess(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private static AnalyticsUser user(String platformUsername) {
        AnalyticsUser user = new AnalyticsUser();
        user.setPlatformUsername(platformUsername);
        return user;
    }
}
