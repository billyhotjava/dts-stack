package com.yuzhi.dts.admin.config;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;

import com.yuzhi.dts.admin.security.*;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.security.oauth2.AudienceValidator;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import com.yuzhi.dts.admin.web.filter.AuditLoggingFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}")
    private String issuerUri;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc, AuditLoggingFilter auditLoggingFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern("/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/auth-info")).permitAll()
                    .requestMatchers(mvc.pattern("/api/keycloak/auth/**")).permitAll()
                    // Portal menu endpoints are needed by the platform for initial navigation
                    .requestMatchers(mvc.pattern("/api/menu"))
                        .permitAll()
                    .requestMatchers(mvc.pattern("/api/menu/**"))
                        .permitAll()
                    // Localization endpoints are required by the login/UI bootstrap without auth
                    .requestMatchers(mvc.pattern("/api/keycloak/localization/**")).permitAll()
                    // Admin service is governance-only: restrict all API endpoints to the triad roles
                    .requestMatchers(mvc.pattern("/api/admin/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
                    .requestMatchers(mvc.pattern("/api/keycloak/approvals/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN)
                    .requestMatchers(mvc.pattern("/api/keycloak/**"))
                        .hasAuthority(AuthoritiesConstants.SYS_ADMIN)
                    .requestMatchers(mvc.pattern("/admin/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
                    .requestMatchers(mvc.pattern("/api/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
                    .requestMatchers(mvc.pattern("/v3/api-docs/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/prometheus")).permitAll()
                    .requestMatchers(mvc.pattern("/management/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())))
            .oauth2Client(withDefaults());
        http.addFilterAfter(auditLoggingFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }

    Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(
            new Converter<Jwt, Collection<GrantedAuthority>>() {
                @Override
                public Collection<GrantedAuthority> convert(Jwt jwt) {
                    return SecurityUtils.extractAuthorityFromClaims(jwt.getClaims());
                }
            }
        );
        jwtAuthenticationConverter.setPrincipalClaimName(PREFERRED_USERNAME);
        return jwtAuthenticationConverter;
    }

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = JwtDecoders.fromOidcIssuerLocation(issuerUri);

        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(jHipsterProperties.getSecurity().getOauth2().getAudience());
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);

        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }
}
