package com.yuzhi.dts.ingestion.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private static final String AIRFLOW_TOKEN = "airflow-to-ingestion-test-token-01";

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
        assertThat(authentication.getPrincipal()).isInstanceOf(ForwardedUserPrincipal.class);
        assertThat(((ForwardedUserPrincipal) authentication.getPrincipal()).sourceService()).isEqualTo("dts-platform");
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly("ROLE_INST_DATA_OWNER", AuthoritiesConstants.SERVICE_DTS_PLATFORM);
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
        assertThat(authentication.getPrincipal()).isInstanceOf(String.class);
        assertThat(authentication.getAuthorities())
            .extracting(GrantedAuthority::getAuthority)
            .containsExactly(AuthoritiesConstants.OP_ADMIN, AuthoritiesConstants.SERVICE_DTS_PLATFORM);
    }

    @Test
    void unknownServiceHeaderMustNotCreateAnAuthenticatedPrincipal() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
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
        assertNotAuthenticated(properties, TRUSTED_TOKEN);
    }

    @Test
    void missingTokenHeaderMustFailClosedEvenForConfiguredService() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        assertNotAuthenticated(properties, null);
    }

    @Test
    void wrongTokenMustNotTrustForwardedAdminRole() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        assertNotAuthenticated(properties, "wrong-token");
    }

    @Test
    void configuredServiceWithoutTokenMustNotAuthenticateServicePrincipal() throws ServletException, IOException {
        IngestionProperties properties = trustedProperties();
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-Service", "dts-platform");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void pairwiseTokenMappingMustBindEachCredentialToOneProducer() throws ServletException, IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.setTrustedServiceTokens(java.util.Map.of(
            "dts-platform", TRUSTED_TOKEN,
            "dts-airflow", AIRFLOW_TOKEN
        ));

        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest airflow = new MockHttpServletRequest("POST", "/internal/api-ingestion/executions");
        airflow.addHeader("X-DTS-Service", "dts-airflow");
        airflow.addHeader("X-DTS-Service-Token", AIRFLOW_TOKEN);
        filter.doFilter(airflow, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("service:dts-airflow");

        SecurityContextHolder.clearContext();
        MockHttpServletRequest confusedDeputy = new MockHttpServletRequest("POST", "/internal/api-ingestion/executions");
        confusedDeputy.addHeader("X-DTS-Service", "dts-airflow");
        confusedDeputy.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);
        filter.doFilter(confusedDeputy, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void airflowCredentialMustNotAuthorizeRollbackOrForwardedRoles() throws ServletException, IOException {
        IngestionProperties properties = new IngestionProperties();
        properties.setTrustedServiceTokens(java.util.Map.of("dts-airflow", AIRFLOW_TOKEN));
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ingestion/rollback/execute");
        request.addHeader("X-DTS-Service", "dts-airflow");
        request.addHeader("X-DTS-Service-Token", AIRFLOW_TOKEN);
        request.addHeader("X-DTS-User", "forged-admin");
        request.addHeader("X-DTS-Roles", "ROLE_ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void platformCredentialMustNotAuthorizeAirflowInternalRoute() throws ServletException, IOException {
        ServiceDependencyAuthenticationFilter filter = trustedFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/api-ingestion/executions");
        request.addHeader("X-DTS-Service", "dts-platform");
        request.addHeader("X-DTS-Service-Token", TRUSTED_TOKEN);

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void configuredTokensMustBeLongPairwiseAndLimitedToSupportedServices() {
        IngestionProperties shortToken = new IngestionProperties();
        shortToken.setTrustedServiceTokens(java.util.Map.of("dts-platform", "too-short"));
        assertThatThrownBy(() -> new ServiceDependencyAuthenticationFilter(shortToken))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("at least 32");

        IngestionProperties duplicate = new IngestionProperties();
        duplicate.setTrustedServiceTokens(java.util.Map.of(
            "dts-platform", TRUSTED_TOKEN,
            "dts-airflow", TRUSTED_TOKEN
        ));
        assertThatThrownBy(() -> new ServiceDependencyAuthenticationFilter(duplicate))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("pairwise unique");

        IngestionProperties unknown = new IngestionProperties();
        unknown.setTrustedServiceTokens(java.util.Map.of("custom-client", TRUSTED_TOKEN));
        assertThatThrownBy(() -> new ServiceDependencyAuthenticationFilter(unknown))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported");
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
        properties.setTrustedServiceTokens(java.util.Map.of("dts-platform", TRUSTED_TOKEN));
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
