package com.yuzhi.dts.platform.config;

import static org.springframework.security.config.Customizer.withDefaults;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.security.ServiceDependencyAuthenticationFilter;
import com.yuzhi.dts.platform.security.session.PortalOpaqueTokenIntrospector;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(
        HttpSecurity http,
        MvcRequestMatcher.Builder mvc,
        PortalOpaqueTokenIntrospector opaqueTokenIntrospector,
        AuditLoggingFilter auditLoggingFilter,
        ServiceDependencyAuthenticationFilter serviceDependencyAuthenticationFilter,
        PortalSessionInactivityFilter sessionInactivityFilter
    )
        throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern("/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/auth-info")).permitAll()
                    // Allow platform login/logout/refresh endpoints without prior auth
                    .requestMatchers(mvc.pattern("/api/keycloak/auth/**")).permitAll()
                    // Allow localization resources without auth (used at boot)
                    .requestMatchers(mvc.pattern("/api/keycloak/localization/**")).permitAll()
                    // Menus must be fetched under authentication so role-based filtering works
                    // Platform has no /api/admin/** endpoints; remove legacy matchers
                    .requestMatchers(mvc.pattern("/api/**")).authenticated()
                    .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/prometheus")).permitAll()
                    .requestMatchers(mvc.pattern("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(opaqueTokenIntrospector)))
            .oauth2Client(withDefaults());
        http.addFilterBefore(serviceDependencyAuthenticationFilter, AnonymousAuthenticationFilter.class);
        http.addFilterAfter(auditLoggingFilter, AnonymousAuthenticationFilter.class);
        http.addFilterAfter(sessionInactivityFilter, AuditLoggingFilter.class);
        return http.build();
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }

    @Bean
    ServiceDependencyAuthenticationFilter serviceDependencyAuthenticationFilter(DtsAdminProperties adminProperties) {
        return new ServiceDependencyAuthenticationFilter(adminProperties);
    }

}
