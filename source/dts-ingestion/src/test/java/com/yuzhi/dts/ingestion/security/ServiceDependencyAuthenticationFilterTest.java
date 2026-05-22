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

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void serviceRequestWithForwardedUserAuthenticatesAsBusinessUser() throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(new IngestionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/tasks");
        request.addHeader("X-DTS-Service", "dts-platform");
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
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(new IngestionProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/tasks");
        request.addHeader("X-DTS-Service", "dts-platform");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("service:dts-platform");
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly(AuthoritiesConstants.OP_ADMIN);
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
}
