package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.admin.gateway.audit.AdminAuditGateway;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.DelegatingServletOutputStream;
import org.springframework.mock.web.MockHttpServletResponse;

class SecurityAuditLogProxyResourceTest {

    @Test
    void listShouldReturnGatewayDataThroughPlatformApi() {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.list(Map.of("page", "1"), "Bearer t")).thenReturn(Map.of("items", 1));

        SecurityAuditLogProxyResource resource = new SecurityAuditLogProxyResource(gateway, mock(AuditService.class));

        ApiResponse<Object> response = resource.list(Map.of("page", "1"), "Bearer t");

        assertThat(response.getData()).isEqualTo(Map.of("items", 1));
        verify(gateway).list(Map.of("page", "1"), "Bearer t");
    }

    @Test
    void exportShouldStreamGatewayCsv() throws IOException {
        AdminAuditGateway gateway = mock(AdminAuditGateway.class);
        when(gateway.export(Map.of("keyword", "alice"), "Bearer t")).thenReturn("a,b\n1,2\n".getBytes());

        SecurityAuditLogProxyResource resource = new SecurityAuditLogProxyResource(gateway, mock(AuditService.class));
        MockHttpServletResponse response = new MockHttpServletResponse();

        resource.export(Map.of("keyword", "alice"), "Bearer t", response);

        assertThat(response.getContentAsString()).isEqualTo("a,b\n1,2\n");
        assertThat(response.getHeader("Content-Disposition")).isEqualTo("attachment; filename=audit-logs.csv");
        verify(gateway).export(Map.of("keyword", "alice"), "Bearer t");
    }
}
