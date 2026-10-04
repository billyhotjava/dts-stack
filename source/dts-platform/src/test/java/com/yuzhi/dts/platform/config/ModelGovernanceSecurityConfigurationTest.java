package com.yuzhi.dts.platform.config;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.yuzhi.dts.platform.security.session.PortalSessionBearerTokenResolver;
import com.yuzhi.dts.platform.security.session.PortalSessionCookieService;
import com.yuzhi.dts.platform.security.ModelGovernanceUserSessionVerifier;
import com.yuzhi.dts.platform.service.audit.AuditFlowManager;
import com.yuzhi.dts.platform.service.audit.AuditForwarderService;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyAdministrationService.PolicyView;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.QualityGate;
import com.yuzhi.dts.platform.service.modeling.ModelGovernancePolicyPort.StandardCoverage;
import com.yuzhi.dts.platform.web.rest.internal.ModelGovernancePolicyInternalResource;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringJUnitConfig(ModelGovernanceSecurityConfigurationTest.TestConfiguration.class)
@WebAppConfiguration
class ModelGovernanceSecurityConfigurationTest {

    private static final String PATH = ModelGovernanceSecurityConfiguration.POLICY_PATH;
    private static final String ISSUER = "https://issuer.example/realms/S10";
    private static final String CLIENT_ID = "configured-admin-console";
    private static final RSAKey SIGNING_KEY = key();

    @Autowired WebApplicationContext context;
    @Autowired ModelGovernancePolicyAdministrationService policies;
    @Autowired AuditForwarderService audit;
    @Autowired ModelGovernanceUserSessionVerifier sessions;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(policies, audit, sessions);
        when(policies.current()).thenReturn(view(QualityGate.ADVISORY, "system-migration"));
        when(policies.impact()).thenReturn(new ModelGovernancePolicyAdministrationService.ImpactView(3, 2));
        when(policies.update(eq(QualityGate.BLOCKING), anyInt(), anyString())).thenAnswer(call -> view(QualityGate.BLOCKING, call.getArgument(2)));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void administratorReadsWithoutAnyServiceCredential() throws Exception {
        String bearer = "Bearer " + token("ROLE_SYS_ADMIN", "valid");
        mvc.perform(get(PATH).header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.qualityGate").value("ADVISORY"));
        mvc.perform(get(PATH + "/impact").header("Authorization", bearer)).andExpect(status().isOk()).andExpect(jsonPath("$.unfrozenCandidates").value(3));
    }

    @Test
    void deployedAdminTokenWithTopLevelRolesAndAuthorizedPartyCanReadPolicyAndImpact() throws Exception {
        String bearer = "Bearer " + token("ROLE_SYS_ADMIN", "deployed-claims");
        mvc.perform(get(PATH).header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.qualityGate").value("ADVISORY"));
        mvc.perform(get(PATH + "/impact").header("Authorization", bearer)).andExpect(status().isOk())
            .andExpect(jsonPath("$.unfrozenCandidates").value(3));
        verify(sessions, times(2)).requireActive(argThat(jwt -> !jwt.hasClaim("aud")
            && !jwt.hasClaim("realm_access") && CLIENT_ID.equals(jwt.getClaimAsString("azp"))));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ROLE_EMPLOYEE", "ROLE_AUTH_ADMIN", "ROLE_SYSTEM_READER", "SYS_ADMIN" })
    void topLevelRolesStillRequireTheExactAdministratorRole(String role) throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token(role, "deployed-claims")))
            .andExpect(status().isForbidden());
        verifyNoInteractions(policies, sessions);
    }

    @Test
    void deployedClaimsStillRequireAnActiveAdministratorSession() throws Exception {
        doThrow(new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token"))
            .when(sessions).requireActive(any());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("ROLE_SYS_ADMIN", "deployed-claims")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(policies);
    }

    @Test
    void updateUsesTheSignedIdentityAndIgnoresAnActorSuppliedInTheBody() throws Exception {
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token("ROLE_SYS_ADMIN", "valid"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"qualityGate\":\"BLOCKING\",\"expectedRevision\":1,\"reason\":\"确认质量要求\",\"actor\":\"forged-user\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastModifiedBy").value("sysadmin"));
        verify(policies).update(QualityGate.BLOCKING, 1, "sysadmin");
        verify(audit).record(argThat(event -> "sysadmin".equals(event.actor)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ROLE_EMPLOYEE", "ROLE_OP_ADMIN", "ROLE_AUTH_ADMIN", "ROLE_AUDITOR_ADMIN", "ROLE_SERVICE_INTERNAL", "ROLE_SYSTEM_READER" })
    void otherRolesCannotReadOrWriteThePolicy(String role) throws Exception {
        String bearer = "Bearer " + token(role, "valid");
        mvc.perform(get(PATH).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"qualityGate\":\"BLOCKING\",\"expectedRevision\":1,\"reason\":\"x\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(policies);
    }

    @ParameterizedTest
    @ValueSource(strings = { "expired", "wrong-issuer", "wrong-audience", "wrong-authorized-party", "wrong-signature", "missing-expiration", "missing-user" })
    void rejectsInvalidSignedTokens(String defect) throws Exception {
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("ROLE_SYS_ADMIN", defect)))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(policies, sessions);
    }

    @Test
    void aLoggedOutOrTimedOutAdministratorCannotUseAnOtherwiseValidJwt() throws Exception {
        doThrow(new org.springframework.security.oauth2.core.OAuth2AuthenticationException("invalid_token"))
            .when(sessions).requireActive(any());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token("ROLE_SYS_ADMIN", "valid")))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(policies);
    }

    @Test
    void cookiesAndOldServiceTokensCannotAuthorizePolicyAdministration() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).header("X-DTS-Service", "dts-admin").header("X-DTS-Service-Token", "legacy-secret"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get(PATH).cookie(new Cookie("portal_session", "opaque-portal-session"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(policies);
    }

    @Test
    void rejectsUnexpectedMethodsAndInvalidUpdateReasons() throws Exception {
        String bearer = "Bearer " + token("ROLE_SYS_ADMIN", "valid");
        mvc.perform(delete(PATH).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get(PATH + "/unknown").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(put(PATH).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
            .content("{\"qualityGate\":\"BLOCKING\",\"expectedRevision\":1,\"reason\":\" \"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(policies);
    }

    @Test
    void unrelatedPlatformApisKeepCookieAuthentication() throws Exception {
        mvc.perform(get("/api/other").header("Authorization", "Bearer " + token("ROLE_SYS_ADMIN", "valid")))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/other").cookie(new Cookie("portal_session", "opaque-portal-session")))
            .andExpect(status().isOk());
    }

    private static PolicyView view(QualityGate gate, String actor) {
        return new PolicyView(gate, StandardCoverage.KEY_AND_MEASURE, 1, actor, Instant.now(), false);
    }

    private static RSAKey key() {
        try { return new RSAKeyGenerator(2048).generate(); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static String token(String role, String defect) throws Exception {
        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
            .issuer("wrong-issuer".equals(defect) ? "https://untrusted.example" : ISSUER)
            .subject("user-id").issueTime(Date.from(now.minusSeconds(600)));
        if ("deployed-claims".equals(defect) || "wrong-authorized-party".equals(defect)) {
            claims.claim("roles", List.of(role))
                .claim("azp", "wrong-authorized-party".equals(defect) ? "untrusted-app" : CLIENT_ID);
        } else {
            claims.audience("wrong-audience".equals(defect) ? "other-app" : "account")
                .claim("realm_access", Map.of("roles", List.of(role)));
        }
        if (!"missing-expiration".equals(defect)) {
            claims.expirationTime(Date.from("expired".equals(defect) ? now.minusSeconds(300) : now.plusSeconds(300)));
        }
        if (!"missing-user".equals(defect)) claims.claim("preferred_username", "sysadmin");
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner("wrong-signature".equals(defect) ? key() : SIGNING_KEY));
        return jwt.serialize();
    }

    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity
    @EnableWebMvc
    static class TestConfiguration implements WebMvcConfigurer {
        @Bean ModelGovernancePolicyAdministrationService policies() { return mock(ModelGovernancePolicyAdministrationService.class); }
        @Bean ModelGovernancePolicyInternalResource resource(ModelGovernancePolicyAdministrationService policies) {
            return new ModelGovernancePolicyInternalResource(policies);
        }
        @Bean ProbeResource probe() { return new ProbeResource(); }
        @Bean AuditForwarderService audit() { return mock(AuditForwarderService.class); }
        @Bean ModelGovernanceUserSessionVerifier sessions() { return mock(ModelGovernanceUserSessionVerifier.class); }
        // Match production: the portal resolver is a global bean, not just an inline DSL setting.
        @Bean PortalSessionBearerTokenResolver portalSessionBearerTokenResolver() {
            var cookies = new PortalSessionCookieService("browser_id", "portal_session", "/", false, "Lax", "test-signing-secret");
            return new PortalSessionBearerTokenResolver(cookies);
        }

        @Bean @Order(2)
        SecurityFilterChain governance(HttpSecurity http, AuditForwarderService audit, ModelGovernanceUserSessionVerifier sessions) throws Exception {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(SIGNING_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(ModelGovernanceSecurityConfiguration.tokenValidator(ISSUER, List.of("account"), CLIENT_ID));
            var provider = new StaticListableBeanFactory(Map.of("audit", audit)).getBeanProvider(AuditForwarderService.class);
            var filter = new AuditLoggingFilter(provider, mock(AuditFlowManager.class), false);
            return new ModelGovernanceSecurityConfiguration().modelGovernanceFilterChain(http, decoder, filter, sessions);
        }

        @Bean @Order(3)
        SecurityFilterChain portal(HttpSecurity http, PortalSessionBearerTokenResolver cookies) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(cookies)
                    .opaqueToken(opaque -> opaque.introspector(token -> new DefaultOAuth2AuthenticatedPrincipal(
                        "employee", Map.of("sub", "employee"), AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE")))))
                .build();
        }

        @Override
        public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
            // Match Spring Boot's default handling of unknown JSON fields (including the former actor field).
            converters.stream().filter(MappingJackson2HttpMessageConverter.class::isInstance)
                .map(MappingJackson2HttpMessageConverter.class::cast)
                .forEach(converter -> converter.getObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        }
    }

    @RestController
    static class ProbeResource {
        @GetMapping("/api/other") String read() { return "ok"; }
    }
}
