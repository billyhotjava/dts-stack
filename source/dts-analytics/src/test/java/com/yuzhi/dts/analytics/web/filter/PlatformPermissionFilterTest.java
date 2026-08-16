package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.service.PlatformPermissionClient;
import com.yuzhi.dts.analytics.service.PlatformPermissionClient.PermissionResult;
import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PlatformPermissionFilterTest {

    @Test
    void mapsReadUpdateDeleteAndPublicLinkOperationsToPlatformActions() throws Exception {
        assertAction("GET", "/api/card/42", "READ");
        assertAction("POST", "/api/card/42/query", "READ");
        assertAction("PUT", "/api/card/42", "EDIT");
        assertAction("POST", "/api/dashboard/7/cards", "EDIT");
        assertAction("DELETE", "/api/dashboard/7", "MANAGE");
        assertAction("POST", "/api/dashboard/7/public_link", "MANAGE");
    }

    @Test
    void favoriteChangesOnlyRequireReadAccessToTheDashboard() throws Exception {
        assertAction("POST", "/api/dashboard/7/favorite", "READ");
        assertAction("DELETE", "/api/dashboard/7/favorite", "READ");
    }

    private void assertAction(String method, String path, String expectedAction) throws Exception {
        PlatformPermissionClient permissionClient = mock(PlatformPermissionClient.class);
        when(permissionClient.authorize(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(new PermissionResult(true, "MANAGE", "allowed"));
        PlatformPermissionFilter filter = new PlatformPermissionFilter(permissionClient);

        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("X-DTS-User", "dept-owner");
        request.addHeader("X-DTS-Roles", "ROLE_DEPT_DATA_OWNER");
        request.addHeader("X-DTS-Dept-Code", "D1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean continued = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> continued.set(true);

        filter.doFilter(request, response, chain);

        assertThat(continued).isTrue();
        verify(permissionClient).authorize(
            "dept-owner",
            "ROLE_DEPT_DATA_OWNER",
            "D1",
            path.startsWith("/api/card/") ? "CARD" : "DASHBOARD",
            path.startsWith("/api/card/") ? "42" : "7",
            expectedAction
        );
    }
}
