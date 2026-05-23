package com.yuzhi.dts.platform.web.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.audit.AuditForwarderService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditLoggingFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldRecordAuthenticatedReadFallbackForPageListOperations() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/governance/issues");
        request.setQueryString("page=0&size=20");
        request.addParameter("page", "0");
        request.addParameter("size", "20");
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.11");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        AuditForwarderService.PendingAuditEvent event = captor.getValue();
        assertThat(event.actor).isEqualTo("opadmin");
        assertThat(event.httpMethod).isEqualTo("GET");
        assertThat(event.action).contains("查看");
        assertThat(event.clientIp).isEqualTo("223.86.189.127");
    }

    @Test
    void shouldRecordPriorityExportEvenWhenEndpointIsOtherwiseSupplementary() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        AuditForwarderService.PendingAuditEvent event = perform(filter, forwarder, "GET", "/api/catalog/classification-mapping/export");

        assertThat(event.action).isEqualTo("导出密级映射");
        assertThat(event.operationType).isEqualTo("EXPORT");
        assertThat(event.resourceType).isEqualTo("classification-mapping");
    }

    @Test
    void shouldClassifyPriorityWriteOperationsWithChineseActionTypes() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        AuditForwarderService.PendingAuditEvent sync = perform(filter, forwarder, "POST", "/api/catalog/sync");
        assertThat(sync.action).isEqualTo("同步同步任务");
        assertThat(sync.operationType).isEqualTo("REFRESH");
        reset(forwarder);

        AuditForwarderService.PendingAuditEvent approve = perform(filter, forwarder, "POST", "/api/catalog/access/tasks/7/approve");
        assertThat(approve.action).isEqualTo("审批数据集访问审批");
        assertThat(approve.operationType).isEqualTo("APPROVE");
        assertThat(approve.resourceId).isEqualTo("7");
        reset(forwarder);

        AuditForwarderService.PendingAuditEvent publish = perform(filter, forwarder, "POST", "/api/governance/indicators/42/publish");
        assertThat(publish.action).isEqualTo("发布指标");
        assertThat(publish.operationType).isEqualTo("PUBLISH");
        assertThat(publish.resourceId).isEqualTo("42");
    }

    @Test
    void shouldKeepSpecificExplorePreviewSemanticBeforeGenericQueryHint() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/explore/query/preview");
        request.setContentType("application/json");
        request.setContent("{\"datasetId\":\"ds-1\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> req.getInputStream().readAllBytes());

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        assertThat(captor.getValue().action).isEqualTo("预览数据集");
        assertThat(captor.getValue().resourceId).isEqualTo("ds-1");
    }

    @Test
    void shouldKeepSupplementaryReadsOutOfFallbackAudit() throws Exception {
        AuditForwarderService forwarder = mock(AuditForwarderService.class);
        AuditLoggingFilter filter = new AuditLoggingFilter(mockProvider(forwarder), mock(AuditFlowManager.class), false);
        authenticate("opadmin");

        for (String uri : List.of(
            "/api/directory/users",
            "/api/forward-auth",
            "/api/menu/tree",
            "/api/catalog/domains/123/asset-stats",
            "/api/catalog/domain/asset-stats",
            "/api/workbench/options"
        )) {
            reset(forwarder);
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.setQueryString("keyword=test1");
            request.addParameter("keyword", "test1");

            filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

            verify(forwarder, never()).record(org.mockito.ArgumentMatchers.any(AuditForwarderService.PendingAuditEvent.class));
        }
    }

    private AuditForwarderService.PendingAuditEvent perform(
        AuditLoggingFilter filter,
        AuditForwarderService forwarder,
        String method,
        String uri
    ) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.addHeader("X-Forwarded-For", "223.86.189.127, 172.19.0.11");
        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {});

        ArgumentCaptor<AuditForwarderService.PendingAuditEvent> captor = ArgumentCaptor.forClass(
            AuditForwarderService.PendingAuditEvent.class
        );
        verify(forwarder).record(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<AuditForwarderService> mockProvider(AuditForwarderService forwarder) {
        ObjectProvider<AuditForwarderService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(forwarder);
        return provider;
    }

    private void authenticate(String username) {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    username,
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))
                )
            );
    }
}
