package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsDataPortalDirectory;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.DataPortalService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class DataPortalResourceTest {

    @Test
    void letsAuthenticatedViewersReadButNotEditPortalMenus() {
        AnalyticsSessionService sessions = mock(AnalyticsSessionService.class);
        DataPortalService service = mock(DataPortalService.class);
        DataPortalResource resource = new DataPortalResource(sessions, service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        AnalyticsUser viewer = user(8L, false);
        when(sessions.resolveUser(request)).thenReturn(Optional.of(viewer));
        when(service.snapshot()).thenReturn(new DataPortalService.Snapshot(List.of(), List.of()));

        var read = resource.get(request);
        var write = resource.createDirectory(new DataPortalResource.DirectoryRequest("一月", null), request);

        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) read.getBody()).get("can_write")).isEqualTo(false);
        assertThat(write.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(service, never()).createDirectory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void letsDataOwnersCreatePortalMenus() {
        AnalyticsSessionService sessions = mock(AnalyticsSessionService.class);
        DataPortalService service = mock(DataPortalService.class);
        DataPortalResource resource = new DataPortalResource(sessions, service);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-DTS-Roles", "ROLE_INST_DATA_OWNER");
        AnalyticsUser editor = user(7L, false);
        AnalyticsDataPortalDirectory created = new AnalyticsDataPortalDirectory();
        created.setId(1L);
        created.setName("一月");
        created.setSortOrder(10);
        when(sessions.resolveUser(request)).thenReturn(Optional.of(editor));
        when(service.createDirectory("一月", null, 7L)).thenReturn(created);

        var response = resource.createDirectory(new DataPortalResource.DirectoryRequest("一月", null), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(service).createDirectory("一月", null, 7L);
    }

    private static AnalyticsUser user(Long id, boolean superuser) {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(id);
        user.setSuperuser(superuser);
        return user;
    }
}
