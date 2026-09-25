package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyRevisionConflictException;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyView;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.StandardCoverage;
import com.yuzhi.dts.platform.web.rest.internal.ModelGovernancePolicyInternalResource.UpdateRequest;
import java.lang.reflect.Method;
import java.time.Instant;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.Authentication;

class ModelGovernancePolicyInternalResourceTest {

    private static final PolicyView ADVISORY = view(QualityGate.ADVISORY, 1, "system-migration", true);
    private static final PolicyView BLOCKING = view(QualityGate.BLOCKING, 2, "sysadmin", false);

    private final ModelGovernancePolicyAdministrationService policies = mock(ModelGovernancePolicyAdministrationService.class);
    private final ModelGovernancePolicyInternalResource resource = new ModelGovernancePolicyInternalResource(policies);

    @ParameterizedTest
    @CsvSource({
        "service:dts-admin, EMPLOYEE, false",
        "service:dts-airflow, SERVICE, false",
        "service:dts-admin, SERVICE, false",
        "sysadmin, SYS_ADMIN, true",
        "operator, OP_ADMIN, false",
        "authadmin, AUTH_ADMIN, false",
    })
    void onlyTheSystemAdministratorMayChangeThePolicy(String principal, String authorityKind, boolean granted) throws Exception {
        String authority = "SERVICE".equals(authorityKind) ? AuthoritiesConstants.SERVICE_INTERNAL : "ROLE_" + authorityKind;
        Method method = ModelGovernancePolicyInternalResource.class.getMethod("update", UpdateRequest.class, Authentication.class);
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(resource);
        when(invocation.getArguments()).thenReturn(new Object[] { null, null });

        var result = new PreAuthorizeAuthorizationManager().authorize(
            () -> new UsernamePasswordAuthenticationToken(principal, "n/a", AuthorityUtils.createAuthorityList(authority)),
            invocation
        );

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isEqualTo(granted);
    }

    @Test
    void switchesOnlyWithAnExpectedRevision() {
        assertThatThrownBy(() -> resource.update(new UpdateRequest(QualityGate.BLOCKING, null, "上线前收紧"), admin()))
            .isInstanceOf(IllegalArgumentException.class);
        verify(policies, never()).update(QualityGate.BLOCKING, 1, "sysadmin");
    }

    @Test
    void returnsThePolicyAfterAManualSwitch() {
        when(policies.current()).thenReturn(ADVISORY);
        when(policies.update(QualityGate.BLOCKING, 1, "sysadmin")).thenReturn(BLOCKING);

        PolicyView after = resource.update(new UpdateRequest(QualityGate.BLOCKING, 1, "上线前收紧"), admin());

        assertThat(after).isEqualTo(BLOCKING);
    }

    @Test
    void mapsARevisionConflictToHttp409() {
        var response = resource.conflict(new PolicyRevisionConflictException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).containsEntry("code", "GOVERNANCE_POLICY_REVISION_CONFLICT");
    }

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken("sysadmin", "n/a", AuthorityUtils.createAuthorityList(AuthoritiesConstants.SYS_ADMIN));
    }

    private static PolicyView view(QualityGate gate, int revision, String actor, boolean systemDefault) {
        return new PolicyView(gate, StandardCoverage.KEY_AND_MEASURE, revision, actor, Instant.parse("2026-09-24T08:00:00Z"), systemDefault);
    }
}
