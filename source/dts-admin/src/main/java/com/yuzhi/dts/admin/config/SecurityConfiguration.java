package com.yuzhi.dts.admin.config;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.PREFERRED_USERNAME;

import com.yuzhi.dts.common.security.AuthEndpointPaths;
import com.yuzhi.dts.admin.security.*;
import com.yuzhi.dts.admin.security.oauth2.AudienceValidator;
import com.yuzhi.dts.admin.web.filter.SessionInactivityFilter;
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
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}")
    private String issuerUri;

    // 由 .env 的 APP_API_DOCS_PUBLIC 控制：true 时 Swagger UI 与 /v3/api-docs 匿名可访问；
    // 生产环境务必保持 false，此时保留原三员角色限制。
    @Value("${app.api-docs.public:false}")
    private boolean apiDocsPublic;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        MvcRequestMatcher.Builder mvc,
        SessionInactivityFilter sessionInactivityFilter
    ) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern("/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/auth-info")).permitAll()
                    .requestMatchers(mvc.pattern(AuthEndpointPaths.KEYCLOAK_AUTH_API_PATTERN)).permitAll()
                    // Platform-friendly endpoints (service-to-service without triad token)
                    .requestMatchers(mvc.pattern("/api/platform/**")).permitAll()
                    .requestMatchers(mvc.pattern("/api/keycloak/platform/**")).permitAll()
                    // Explicitly allow platform-consumed admin endpoints (orgs under /api/admin/platform/**)
                    .requestMatchers(mvc.pattern("/api/admin/platform/**")).permitAll()
                    // Portal menu endpoints are needed by the platform for initial navigation
                    .requestMatchers(mvc.pattern("/api/menu"))
                        .permitAll()
                    .requestMatchers(mvc.pattern("/api/menu/**"))
                        .permitAll()
                    // Localization endpoints are required by the login/UI bootstrap without auth
                    .requestMatchers(mvc.pattern(AuthEndpointPaths.KEYCLOAK_LOCALIZATION_API_PATTERN)).permitAll()
                    // Ingest endpoint for sibling-service audit pushes. The controller (AuditIngestResource)
                    // validates a shared service token via AuditIngestAuthenticator; permitAll here only
                    // means "skip the OAuth2 resource server filter" because callers are services, not Keycloak users.
                    .requestMatchers(mvc.pattern("/api/audit-events")).permitAll()
                    // MDM 回调/对接无需认证，由网关自身校验签名/令牌
                    .requestMatchers(mvc.pattern("/api/mdm/**")).permitAll()
                    // Admin service is governance-only: restrict all API endpoints to the triad roles
                    .requestMatchers(mvc.pattern("/api/admin/**"))
                        .hasAnyAuthority(
                            AuthoritiesConstants.SYS_ADMIN,
                            AuthoritiesConstants.AUTH_ADMIN,
                            AuthoritiesConstants.AUDITOR_ADMIN,
                            // Backward-compatible aliases observed in legacy realms/tokens
                            "ROLE_AUDITOR_ADMIN",
                            "ROLE_AUDIT_ADMIN",
                            "ROLE_AUDITADMIN"
                        )
                    .requestMatchers(mvc.pattern("/api/keycloak/approvals/**"))
                        .hasAnyAuthority(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.AUTH_ADMIN)
                    .requestMatchers(mvc.pattern("/api/keycloak/**"))
                        .hasAuthority(AuthoritiesConstants.SYS_ADMIN)
                    .requestMatchers(mvc.pattern("/admin/**"))
                        .hasAnyAuthority(
                            AuthoritiesConstants.SYS_ADMIN,
                            AuthoritiesConstants.AUTH_ADMIN,
                            AuthoritiesConstants.AUDITOR_ADMIN,
                            "ROLE_AUDITOR_ADMIN",
                            "ROLE_AUDIT_ADMIN",
                            "ROLE_AUDITADMIN"
                        )
                    .requestMatchers(mvc.pattern("/api/**"))
                        .hasAnyAuthority(
                            AuthoritiesConstants.SYS_ADMIN,
                            AuthoritiesConstants.AUTH_ADMIN,
                            AuthoritiesConstants.AUDITOR_ADMIN,
                            "ROLE_AUDITOR_ADMIN",
                            "ROLE_AUDIT_ADMIN",
                            "ROLE_AUDITADMIN"
                        )
                    // Swagger UI + OpenAPI spec: APP_API_DOCS_PUBLIC=true 时匿名可访问（dev/test），
                    // 否则仍受三员角色限制（生产默认）。
                    .requestMatchers(
                        mvc.pattern("/v3/api-docs"),
                        mvc.pattern("/v3/api-docs/**"),
                        mvc.pattern("/v3/api-docs.yaml"),
                        mvc.pattern("/swagger-ui.html"),
                        mvc.pattern("/swagger-ui/**"),
                        mvc.pattern("/webjars/**")
                    ).access((authentication, context) -> {
                        if (apiDocsPublic) {
                            return new org.springframework.security.authorization.AuthorizationDecision(true);
                        }
                        var auth = authentication.get();
                        boolean allowed = auth != null && auth.isAuthenticated() && auth.getAuthorities().stream()
                            .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                            .anyMatch(a ->
                                AuthoritiesConstants.SYS_ADMIN.equals(a)
                                || AuthoritiesConstants.AUTH_ADMIN.equals(a)
                                || AuthoritiesConstants.AUDITOR_ADMIN.equals(a)
                                || "ROLE_AUDITOR_ADMIN".equals(a)
                                || "ROLE_AUDIT_ADMIN".equals(a)
                                || "ROLE_AUDITADMIN".equals(a));
                        return new org.springframework.security.authorization.AuthorizationDecision(allowed);
                    })
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/prometheus")).permitAll()
                    .requestMatchers(mvc.pattern("/management/**"))
                        .hasAnyAuthority(
                            AuthoritiesConstants.SYS_ADMIN,
                            AuthoritiesConstants.AUTH_ADMIN,
                            AuthoritiesConstants.AUDITOR_ADMIN,
                            "ROLE_AUDITOR_ADMIN",
                            "ROLE_AUDIT_ADMIN",
                            "ROLE_AUDITADMIN"
                        )
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter())))
            .oauth2Client(withDefaults());
        http.addFilterAfter(sessionInactivityFilter, AnonymousAuthenticationFilter.class);
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
