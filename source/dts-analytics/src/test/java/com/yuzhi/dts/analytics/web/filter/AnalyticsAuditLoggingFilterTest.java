package com.yuzhi.dts.analytics.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisRequestContext;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService;
import com.yuzhi.dts.analytics.service.audit.AnalyticsAuditForwarderService.AnalyticsAuditEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AnalyticsAuditLoggingFilterTest {

    @Test
    void shouldNotDuplicateAnAuditAlreadyRecordedByTheGovernedQueryGateway() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/analysis/42/query");
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(user()));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
            req.setAttribute(AnalysisRequestContext.SPECIALIZED_AUDIT_RECORDED_ATTRIBUTE, Boolean.TRUE)
        );

        verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AnalyticsAuditEvent.class));
    }

    @Test
    void shouldRetainGenericAuditWhenQueryValidationFailsBeforeTheGatewayRuns() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);

        AnalyticsAuditEvent event = perform(filter, sessionService, forwarder, "POST", "/api/analysis/preview");

        assertThat(event.actionCode()).isEqualTo("ANALYTICS_ANALYSIS_READ");
    }

    @Test
    void shouldRecordAuthenticatedAnalyticsPageReadWithHumanActorAndRealIp() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screen");
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.15");
        request.addHeader("User-Agent", "JUnit");
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(user()));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        ArgumentCaptor<AnalyticsAuditEvent> captor = ArgumentCaptor.forClass(AnalyticsAuditEvent.class);
        verify(forwarder).record(captor.capture());
        AnalyticsAuditEvent event = captor.getValue();
        assertThat(event.actor()).isEqualTo("opadmin");
        assertThat(event.actorName()).isEqualTo("Ops Admin");
        assertThat(event.actionCode()).isEqualTo("SCREEN_VIEW");
        assertThat(event.action()).isEqualTo("查看大屏");
        assertThat(event.operationType()).isEqualTo("READ");
        assertThat(event.clientIp()).isEqualTo("223.86.189.127");
    }

    @Test
    void shouldRecordForwardedOnlyInternalClientIp() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/screen");
        request.setRemoteAddr("172.19.0.14");
        request.addHeader("Forwarded", "for=\"192.168.8.66\";proto=https;host=bi.example.com");
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(user()));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        ArgumentCaptor<AnalyticsAuditEvent> captor = ArgumentCaptor.forClass(AnalyticsAuditEvent.class);
        verify(forwarder).record(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("192.168.8.66");
    }

    @Test
    void shouldSkipAnalyticsAuditWhenNoHumanUserIsResolved() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/dataset");
        when(sessionService.resolveUser(request)).thenReturn(Optional.empty());

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AnalyticsAuditEvent.class));
    }

    @Test
    void shouldClassifyAnalyticsPriorityOperations() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);

        AnalyticsAuditEvent save = perform(filter, sessionService, forwarder, "POST", "/api/dashboard/save");
        assertThat(save.actionCode()).isEqualTo("VIS_DASHBOARD_EDIT");
        assertThat(save.action()).isEqualTo("保存仪表板");
        assertThat(save.operationType()).isEqualTo("UPDATE");
        reset(forwarder);

        AnalyticsAuditEvent sync = perform(filter, sessionService, forwarder, "POST", "/api/database/1/sync_schema");
        assertThat(sync.actionCode()).isEqualTo("ANALYTICS_DATABASE_REFRESH");
        assertThat(sync.action()).isEqualTo("同步数据源");
        assertThat(sync.operationType()).isEqualTo("REFRESH");
        reset(forwarder);

        AnalyticsAuditEvent export = perform(filter, sessionService, forwarder, "GET", "/api/report-factory/runs/8/export");
        assertThat(export.actionCode()).isEqualTo("ANALYTICS_REPORT_FACTORY_EXPORT");
        assertThat(export.action()).isEqualTo("导出报告工厂");
        assertThat(export.operationType()).isEqualTo("EXPORT");
        reset(forwarder);

        AnalyticsAuditEvent revoke = perform(filter, sessionService, forwarder, "DELETE", "/api/screen/42/public_link");
        assertThat(revoke.actionCode()).isEqualTo("SCREEN_PUBLIC_LINK_DISABLE");
        assertThat(revoke.action()).isEqualTo("撤销授权大屏");
        assertThat(revoke.operationType()).isEqualTo("REVOKE");
    }

    @Test
    void shouldClassifyPluralScreenManagementRoutesAsScreenAudit() throws Exception {
        AnalyticsSessionService sessionService = mock(AnalyticsSessionService.class);
        AnalyticsAuditForwarderService forwarder = mock(AnalyticsAuditForwarderService.class);
        AnalyticsAuditLoggingFilter filter = new AnalyticsAuditLoggingFilter(sessionService, forwarder);

        AnalyticsAuditEvent list = perform(filter, sessionService, forwarder, "GET", "/api/screens");
        assertThat(list.module()).isEqualTo("analytics.screen");
        assertThat(list.resourceType()).isEqualTo("SCREEN");
        assertThat(list.actionCode()).isEqualTo("SCREEN_VIEW");
        assertThat(list.action()).isEqualTo("查看大屏");
        assertThat(list.operationType()).isEqualTo("READ");
        reset(forwarder);

        AnalyticsAuditEvent update = perform(filter, sessionService, forwarder, "PUT", "/api/screens/42");
        assertThat(update.module()).isEqualTo("analytics.screen");
        assertThat(update.resourceType()).isEqualTo("SCREEN");
        assertThat(update.resourceId()).isEqualTo("42");
        assertThat(update.actionCode()).isEqualTo("SCREEN_UPDATE");
        assertThat(update.action()).isEqualTo("修改大屏");
        assertThat(update.operationType()).isEqualTo("UPDATE");
        reset(forwarder);

        AnalyticsAuditEvent grant = perform(filter, sessionService, forwarder, "PUT", "/api/screens/42/grants");
        assertThat(grant.actionCode()).isEqualTo("SCREEN_ACL_GRANT");
        assertThat(grant.action()).isEqualTo("授权大屏");
        assertThat(grant.operationType()).isEqualTo("GRANT");
    }

    private AnalyticsAuditEvent perform(
        AnalyticsAuditLoggingFilter filter,
        AnalyticsSessionService sessionService,
        AnalyticsAuditForwarderService forwarder,
        String method,
        String uri
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.15");
        when(sessionService.resolveUser(request)).thenReturn(Optional.of(user()));

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        ArgumentCaptor<AnalyticsAuditEvent> captor = ArgumentCaptor.forClass(AnalyticsAuditEvent.class);
        verify(forwarder).record(captor.capture());
        return captor.getValue();
    }

    private AnalyticsUser user() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        user.setEmail("opadmin@platform.local");
        user.setPlatformUsername("opadmin");
        user.setFirstName("Ops");
        user.setLastName("Admin");
        return user;
    }
}
