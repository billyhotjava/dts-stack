package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityAuditEntryProxyResourceTest {

    @Test
    void listShouldReturnGatewayDataThroughPlatformApi() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.list(Map.of("page", "1"), "Bearer t")).thenReturn(Map.of("items", 1));

        SecurityAuditEntryProxyResource resource = new SecurityAuditEntryProxyResource(gateway, mock(AuditService.class), null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer t");

        ApiResponse<Object> response = resource.list(Map.of("page", "1"), request);

        assertThat(response.getData()).isEqualTo(Map.of("items", 1));
        verify(gateway).list(Map.of("page", "1"), "Bearer t");
    }

    @Test
    void exportShouldStreamGatewayCsv() throws IOException {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.export(Map.of("keyword", "alice"), "Bearer t")).thenReturn("a,b\n1,2\n".getBytes());

        SecurityAuditEntryProxyResource resource = new SecurityAuditEntryProxyResource(gateway, mock(AuditService.class), null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer t");
        MockHttpServletResponse response = new MockHttpServletResponse();

        resource.export(Map.of("keyword", "alice"), request, response);

        assertThat(response.getContentAsString()).isEqualTo("a,b\n1,2\n");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=audit-logs.csv");
        verify(gateway).export(Map.of("keyword", "alice"), "Bearer t");
    }
}
