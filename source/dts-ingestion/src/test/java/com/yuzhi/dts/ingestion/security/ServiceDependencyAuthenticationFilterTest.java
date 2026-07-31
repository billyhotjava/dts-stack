package com.yuzhi.dts.ingestion.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceDependencyAuthenticationFilterTest {

    private static final String TRUSTED_TOKEN = "platform-to-ingestion-test-token";

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceRequestWithForwardedUserAuthenticatesAsBusinessUser() throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = trustedFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/tasks");
        request.addHeader("X-DTS-Service", "dts-platform");
        request.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);
        request.addHeader("X-DTS-User", "xiezm");
        request.addHeader("X-DTS-Roles", "ROLE_INST_DATA_OWNER,ROLE_EMPLOYEE");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("xiezm");
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_INST_DATA_OWNER", "ROLE_EMPLOYEE");
    }

    @Test
    void serviceRequestWithoutForwardedUserFallsBackToServicePrincipal() throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = trustedFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/tasks");
        request.addHeader("X-DTS-Service", "dts-platform");
        request.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("service:dts-platform");
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly(AuthoritiesConstants.OP_ADMIN);
    }

    @Test
    void unknownServiceHeaderMustNotCreateAnAuthenticatedPrincipal() throws ServletException, IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.setTrustedServiceName("dts-platform,dts-admin");
        properties.setTrustedServiceToken(TRUSTED_TOKEN);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-Service", "attacker-controlled-service");
        request.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);
        request.addHeader("X-DTS-User", "attacker");
        request.addHeader("X-DTS-Roles", "ROLE_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingServiceHeaderMustNotTrustForwardedUserOrRoles() throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = trustedFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-User", "attacker");
        request.addHeader("X-DTS-Roles", "ROLE_ADMIN");
        request.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void missingTokenConfigurationMustFailClosed() throws ServletException, IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.setTrustedServiceToken(null);
        assertNotAuthenticated(properties, TRUSTED_TOKEN);
    }

    @Test
    void missingTokenHeaderMustFailClosedEvenForTrustedServiceName() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        assertNotAuthenticated(properties, null);
    }

    @Test
    void wrongTokenMustNotTrustForwardedAdminRole() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        assertNotAuthenticated(properties, "wrong-token");
    }

    @Test
    void trustedServiceNameAloneMustNotAuthenticateServicePrincipal() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-Service", "dts-platform");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void platformMaintainerRolesAreAcceptedByIngestionPolicy() {
        assertThat(AuthoritiesConstants.INFRA_MAINTAINERS)
            .contains(
                AuthoritiesConstants.INST_DATA_OWNER,
                AuthoritiesConstants.DEPT_DATA_OWNER,
                AuthoritiesConstants.INST_LEADER,
                AuthoritiesConstants.DEPT_LEADER
            );
    }

    private ServiceDependencyAuthenticationFilter trustedFilter() {
        return new ServiceDependencyAuthenticationFilter(trustedProperties());
    }

    private IngestionProperties trustedProperties() {
        IngestionProperties properties = new IngestionProperties();
        properties.setTrustedServiceName("dts-platform");
        properties.setTrustedServiceToken(TRUSTED_TOKEN);
        return properties;
    }

    private void assertNotAuthenticated(IngestionProperties properties, String token) throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-Service", "dts-platform");
        if (token != null) {
            request.addHeader("X-DTS-Service-Token", token);
        }
        request.addHeader("X-DTS-User", "attacker");
        request.addHeader("X-DTS-Roles", "ROLE_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
