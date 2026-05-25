package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditEntryActionRecorderTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditLogQueryUsesStandardForwardedHeaderBeforeRemoteAddr() {
        AuditV2Service auditV2Service = mock(AuditV2Service.class);
        AuditEntryActionRecorder recorder = new AuditEntryActionRecorder(auditV2Service);
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "auditadmin",
                    "n/a",
                    List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
                )
            );
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/audit-entries");
        request.addHeader("Forwarded", "for=172.168.0.1;proto=https;host=biadmin.example.com");
        request.setRemoteAddr("172.18.0.4");

        recorder.record(ButtonCodes.AUDIT_LOG_QUERY, null, Pageable.unpaged(), 3, 3, request);

        ArgumentCaptor<AuditActionRequest> captor = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(auditV2Service).record(captor.capture());
        assertThat(captor.getValue().clientIp()).isEqualTo("172.168.0.1");
    }
}
