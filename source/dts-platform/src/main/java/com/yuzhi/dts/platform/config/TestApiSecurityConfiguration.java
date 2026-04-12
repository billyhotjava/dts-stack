package com.yuzhi.dts.platform.config;

import com.yuzhi.dts.platform.web.filter.TestApiAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import tech.jhipster.config.JHipsterConstants;

/**
 * Separate SecurityFilterChain dedicated to /test/** helper endpoints.
 *
 * <p>Double-gated — only active when BOTH:
 * <ul>
 *   <li>{@code app.test-api.enabled=true}</li>
 *   <li>prod profile is NOT active</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(name = "app.test-api.enabled", havingValue = "true")
@Profile("!" + JHipsterConstants.SPRING_PROFILE_PRODUCTION)
@EnableConfigurationProperties(TestApiProperties.class)
public class TestApiSecurityConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain testApiFilterChain(HttpSecurity http, TestApiProperties props) throws Exception {
        http
            .securityMatcher("/test/**")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> {})
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
            .addFilterBefore(new TestApiAuthFilter(props.getToken()), AnonymousAuthenticationFilter.class);
        return http.build();
    }
}
