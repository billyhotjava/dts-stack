package com.yuzhi.dts.copilot.analytics.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for the Copilot Analytics service.
 * Enforces stateless API-key-based authentication on all endpoints
 * except health, actuator, and public endpoints.
 */
@Configuration
@EnableWebSecurity
public class AnalyticsSecurityConfiguration {

    private final AnalyticsApiKeyAuthFilter analyticsApiKeyAuthFilter;

    public AnalyticsSecurityConfiguration(AnalyticsApiKeyAuthFilter analyticsApiKeyAuthFilter) {
        this.analyticsApiKeyAuthFilter = analyticsApiKeyAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/health", "/api/info").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/api/setup", "/api/setup/**").permitAll()
                .requestMatchers("/api/session", "/api/session/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(analyticsApiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
