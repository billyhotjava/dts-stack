package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.etl.DbtRuntimeProfileLeaseService;
import java.lang.reflect.Method;
import java.util.UUID;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.authorization.method.PreAuthorizeAuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

class DbtRuntimeProfileLeaseInternalAuthorizationTest {

    private static final UUID LEASE_ID =
        UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void rejectsServiceAuthorityWithTheWrongPrincipal() throws Exception {
        AuthorizationResult result = authorize(
            authentication(
                "service:dts-ingestion",
                AuthoritiesConstants.SERVICE_INTERNAL
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }

    @Test
    void rejectsAirflowPrincipalWithoutTheServiceAuthority()
        throws Exception {
        AuthorizationResult result = authorize(
            authentication(
                "service:dts-airflow",
                AuthoritiesConstants.EMPLOYEE
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isFalse();
    }

    @Test
    void acceptsOnlyTheAirflowServicePrincipalAndAuthority()
        throws Exception {
        AuthorizationResult result = authorize(
            authentication(
                "service:dts-airflow",
                AuthoritiesConstants.SERVICE_INTERNAL
            )
        );

        assertThat(result).isNotNull();
        assertThat(result.isGranted()).isTrue();
    }

    private static AuthorizationResult authorize(
        Authentication authentication
    ) throws Exception {
        DbtRuntimeProfileLeaseInternalResource resource =
            new DbtRuntimeProfileLeaseInternalResource(
                mock(DbtRuntimeProfileLeaseService.class)
            );
        Method method =
            DbtRuntimeProfileLeaseInternalResource.class.getMethod(
                    "release",
                    UUID.class
                );
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getMethod()).thenReturn(method);
        when(invocation.getThis()).thenReturn(resource);
        when(invocation.getArguments())
            .thenReturn(new Object[] { LEASE_ID });

        return new PreAuthorizeAuthorizationManager()
            .authorize(() -> authentication, invocation);
    }

    private static Authentication authentication(
        String principal,
        String authority
    ) {
        return new UsernamePasswordAuthenticationToken(
            principal,
            "n/a",
            AuthorityUtils.createAuthorityList(authority)
        );
    }
}
