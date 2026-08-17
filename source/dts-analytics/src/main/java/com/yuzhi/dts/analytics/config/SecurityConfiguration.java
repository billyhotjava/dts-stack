package com.yuzhi.dts.analytics.config;

import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.web.filter.AnalyticsAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        AnalyticsAuthenticationFilter analyticsAuthenticationFilter,
        @Value("${analytics.public-sharing.enabled:false}") boolean publicSharingEnabled
    ) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityProblemSupport())
                        .accessDeniedHandler(securityProblemSupport()))
                .authorizeHttpRequests(auth -> {
                    auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/init.html",
                                "/api/health",
                                "/api/info",
                                "/api/session/properties",
                                "/api/session/**",
                                "/auth/oidc/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/app/**",
                                "/webjars/**")
                        .permitAll()
                        ;
                    if (publicSharingEnabled) {
                        auth.requestMatchers("/api/public/**", "/api/embed/**").permitAll();
                    } else {
                        auth.requestMatchers("/api/public/**", "/api/embed/**").denyAll();
                    }
                    auth.anyRequest().authenticated();
                })
                .addFilterBefore(analyticsAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AnalyticsAuthenticationFilter analyticsAuthenticationFilter(AnalyticsSessionService sessionService) {
        return new AnalyticsAuthenticationFilter(sessionService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityProblemSupport securityProblemSupport() {
        return new SecurityProblemSupport();
    }
}
