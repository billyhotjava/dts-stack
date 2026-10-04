package com.yuzhi.dts.platform.config;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ModelGovernanceUserSessionVerifier;
import com.yuzhi.dts.platform.security.oauth2.AudienceValidator;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.util.StringUtils;
import tech.jhipster.config.JHipsterProperties;

/** Only the governance policy endpoints accept the administrator's forwarded Keycloak JWT. */
@Configuration
public class ModelGovernanceSecurityConfiguration {

    static final String POLICY_PATH = "/api/internal/modeling/governance-policy";

    @Bean
    @Order(2)
    SecurityFilterChain modelGovernanceFilterChain(
        HttpSecurity http,
        @Qualifier("modelGovernanceJwtDecoder") JwtDecoder decoder,
        AuditLoggingFilter auditLoggingFilter,
        ModelGovernanceUserSessionVerifier sessions
    ) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName("preferred_username");
        // DTS maps roles at the top level; also accept Keycloak's standard realm role mapping.
        // Both layouts require the exact system administrator role, followed by admin session validation.
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object roles = jwt.getClaims().get("roles");
            Object realm = jwt.getClaims().get("realm_access");
            Object realmRoles = realm instanceof Map<?, ?> values ? values.get("roles") : null;
            if ((roles instanceof Collection<?> roleValues && roleValues.contains(AuthoritiesConstants.SYS_ADMIN))
                || (realmRoles instanceof Collection<?> realmValues && realmValues.contains(AuthoritiesConstants.SYS_ADMIN))) {
                sessions.requireActive(jwt);
                return List.of(new SimpleGrantedAuthority(AuthoritiesConstants.SYS_ADMIN));
            }
            return List.of();
        });
        return http
            .securityMatcher(POLICY_PATH, POLICY_PATH + "/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, POLICY_PATH, POLICY_PATH + "/impact").hasAuthority(AuthoritiesConstants.SYS_ADMIN)
                .requestMatchers(HttpMethod.PUT, POLICY_PATH).hasAuthority(AuthoritiesConstants.SYS_ADMIN)
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                // Do not inherit the portal's globally registered cookie-only resolver.
                .bearerTokenResolver(new DefaultBearerTokenResolver())
                .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(converter)))
            .addFilterAfter(auditLoggingFilter, AnonymousAuthenticationFilter.class)
            .build();
    }

    @Bean
    JwtDecoder modelGovernanceJwtDecoder(
        @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}") String issuer,
        JHipsterProperties properties,
        @Value("${spring.security.oauth2.client.registration.oidc.client-id}") String clientId
    ) {
        NimbusJwtDecoder decoder = JwtDecoders.fromOidcIssuerLocation(issuer);
        decoder.setJwtValidator(tokenValidator(issuer, properties.getSecurity().getOauth2().getAudience(), clientId));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, List<String> audience, String clientId) {
        // Existing DTS access tokens can omit aud and identify their authorized client through azp.
        List<String> allowedAudience = new ArrayList<>(audience);
        if (StringUtils.hasText(clientId)) allowedAudience.add(clientId.trim());
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer),
            new AudienceValidator(allowedAudience),
            jwt -> jwt.getExpiresAt() != null && StringUtils.hasText(jwt.getSubject())
                && jwt.getClaims().get("preferred_username") instanceof String username && StringUtils.hasText(username)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "User identity or expiration is missing", null))
        );
    }
}
