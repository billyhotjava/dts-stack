package com.yuzhi.dts.platform.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.config.PlatformInboundServiceAuthProperties;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import jakarta.servlet.FilterChain;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class ServiceDependencyAuthenticationFilterTest {

    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String INGESTION_RUNTIME_DETAIL = "/api/infra/data-sources/11111111-1111-1111-1111-111111111111/runtime-detail";

    private PlatformInboundServiceAuthProperties props;
    private SvcTokenAuthService svcTokenAuthService;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        props = new PlatformInboundServiceAuthProperties();
        Map<String, String> trusted = new LinkedHashMap<>();
        trusted.put("dts-ingestion", "ingestion-secret");
        trusted.put("dts-analytics", "analytics-secret");
        props.setTrustedServices(trusted);
        props.setSharedSecret("shared-fallback");
        svcTokenAuthService = mock(SvcTokenAuthService.class);
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noServiceHeader_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        // no header
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void serviceHeaderWithoutToken_doesNotAuthenticate_F3StrictMode() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);
        // no token header
        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceHeaderWithWrongToken_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        when(svcTokenAuthService.authenticateService("wrong-token", "dts-ingestion")).thenReturn(null);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "wrong-token");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void serviceHeaderWithMatchingPerPairToken_authenticates() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
        assertThat(auth.getAuthorities()).extracting(Object::toString).contains(AuthoritiesConstants.SERVICE_INTERNAL);
        // Other service's secret 不能用来认证 ingestion
    }

    @Test
    void serviceHeaderWithSharedSecretFallback_authenticates() throws Exception {
        // trustedServices Map 命中但 value 为空 → fallback 到 sharedSecret
        Map<String, String> trusted = new LinkedHashMap<>();
        trusted.put("dts-ingestion", ""); // empty per-pair token
        props.setTrustedServices(trusted);

        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "shared-fallback");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
    }

    @Test
    void serviceHeaderWithDynamicSvcToken_authenticates() throws Exception {
        when(svcTokenAuthService.authenticateService("db-managed-token", "dts-ingestion"))
            .thenReturn(new TokenPrincipal("service:dts-ingestion", null, PersonnelLevel.GENERAL, null));
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "db-managed-token");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
    }

    @Test
    void unknownServiceName_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-malicious");
        req.addHeader(TOKEN_HEADER, "any-token");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }

    @Test
    void existingAuthentication_isPreserved() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        UsernamePasswordAuthenticationToken existing = new UsernamePasswordAuthenticationToken(
            "real-user",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(existing);

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }

    @Test
    void legacyHeaderOnlyMode_authenticatesWithJustHeader() throws Exception {
        props.setLegacyHeaderOnlyMode(true);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);
        // no token

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isEqualTo("service:dts-ingestion");
    }

    @Test
    void legacyHeaderOnlyMode_stillRejectsUnknownService() throws Exception {
        props.setLegacyHeaderOnlyMode(true);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-malicious");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void disabledAuthProperties_skipsInjection() throws Exception {
        props.setEnabled(false);
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI(INGESTION_RUNTIME_DETAIL);

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void matchingTokenOnDisallowedEndpoint_doesNotAuthenticate() throws Exception {
        ServiceDependencyAuthenticationFilter filter = new ServiceDependencyAuthenticationFilter(props, svcTokenAuthService);
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(SERVICE_HEADER, "dts-ingestion");
        req.addHeader(TOKEN_HEADER, "ingestion-secret");
        req.setMethod("GET");
        req.setRequestURI("/api/services/tokens");

        filter.doFilter(req, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(svcTokenAuthService, never()).authenticateService(any(), any());
    }
}
