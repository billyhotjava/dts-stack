package com.yuzhi.dts.admin.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.service.audit.AuditV2Service;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PlatformPolicyException;
import com.yuzhi.dts.admin.service.infra.PlatformGovernancePolicyClient.PolicyView;
import com.yuzhi.dts.admin.web.rest.AdminModelGovernancePolicyResource.UpdateRequest;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class AdminModelGovernancePolicyResourceTest {

    private static final PolicyView ADVISORY = new PolicyView("ADVISORY", "KEY_AND_MEASURE", 1, "system-migration", Instant.parse("2026-09-24T08:00:00Z"), true);
    private static final PolicyView BLOCKING = new PolicyView("BLOCKING", "KEY_AND_MEASURE", 2, "sysadmin", Instant.parse("2026-09-24T09:00:00Z"), false);

    private final PlatformGovernancePolicyClient platform = mock(PlatformGovernancePolicyClient.class);
    private final AuditV2Service audit = mock(AuditV2Service.class);
    private final AdminModelGovernancePolicyResource resource = new AdminModelGovernancePolicyResource(platform, audit);

    @BeforeEach
    void loginAsSystemAdministrator() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("sysadmin", "n/a", AuthorityUtils.createAuthorityList(AuthoritiesConstants.SYS_ADMIN))
        );
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void onlySystemAdministratorsReachThePolicy() {
        PreAuthorize guard = AdminModelGovernancePolicyResource.class.getAnnotation(PreAuthorize.class);
        assertThat(guard.value()).isEqualTo("hasAuthority('" + AuthoritiesConstants.SYS_ADMIN + "')");
    }

    @Test
    void rejectsIncompleteRequestsWithoutCallingThePlatform() {
        assertThat(AdminModelGovernancePolicyResource.validate(new UpdateRequest("STRICT", 1, "x"))).isNotNull();
        assertThat(AdminModelGovernancePolicyResource.validate(new UpdateRequest("BLOCKING", null, "x"))).isNotNull();
        assertThat(AdminModelGovernancePolicyResource.validate(new UpdateRequest("BLOCKING", 1, "  "))).isNotNull();
        assertThat(AdminModelGovernancePolicyResource.validate(new UpdateRequest("BLOCKING", 1, "x".repeat(201)))).isNotNull();

        var response = resource.update(new UpdateRequest("BLOCKING", 1, ""), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(platform, never()).update(anyString(), anyInt(), anyString());
    }

    @Test
    void switchesWithTheLoggedInActorAndAuditsBeforeAndAfter() {
        when(platform.current()).thenReturn(ADVISORY);
        when(platform.update("BLOCKING", 1, "上线前收紧质量要求")).thenReturn(BLOCKING);

        var response = resource.update(new UpdateRequest("BLOCKING", 1, " 上线前收紧质量要求 "), new MockHttpServletRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData()).isEqualTo(BLOCKING);
        ArgumentCaptor<AuditActionRequest> recorded = ArgumentCaptor.forClass(AuditActionRequest.class);
        verify(audit).record(recorded.capture());
        assertThat(recorded.getValue().actorId()).isEqualTo("sysadmin");
        assertThat(recorded.getValue().buttonCode()).isEqualTo("ADMIN_MODEL_GOVERNANCE_POLICY_UPDATE");
    }

    @Test
    void platformFailureIsAuditedAndSurfacedUnchanged() {
        when(platform.current()).thenReturn(ADVISORY);
        when(platform.update(anyString(), anyInt(), anyString()))
            .thenThrow(new PlatformPolicyException(HttpStatus.CONFLICT, "GOVERNANCE_POLICY_REVISION_CONFLICT", "策略已被他人修改，请刷新后重试"));

        assertThatThrownBy(() -> resource.update(new UpdateRequest("BLOCKING", 1, "收紧"), new MockHttpServletRequest()))
            .isInstanceOf(PlatformPolicyException.class);
        verify(audit).record(any(AuditActionRequest.class));

        var mapped = resource.platformFailure(new PlatformPolicyException(HttpStatus.CONFLICT, "GOVERNANCE_POLICY_REVISION_CONFLICT", "策略已被他人修改，请刷新后重试"));
        assertThat(mapped.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
