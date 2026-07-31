package com.yuzhi.dts.ingestion.security;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class ServiceDependencyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ServiceDependencyAuthenticationFilter.class);
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String USER_HEADER = "X-DTS-User";
    private static final String ROLES_HEADER = "X-DTS-Roles";

    private final IngestionProperties properties;

    public ServiceDependencyAuthenticationFilter(IngestionProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            String serviceName = resolveServiceName(request);
            if (serviceName != null) {
                String forwardedUser = normalizeHeader(request.getHeader(USER_HEADER));
                String principal = forwardedUser != null ? forwardedUser : "service:" + serviceName;
                List<SimpleGrantedAuthority> authorities = forwardedUser != null
                    ? resolveForwardedAuthorities(request.getHeader(ROLES_HEADER))
                    : List.of(new SimpleGrantedAuthority(AuthoritiesConstants.OP_ADMIN));
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    authorities
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated internal service call service={} principal={}", serviceName, principal);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveServiceName(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return null;
        }
        String declared = request.getHeader(SERVICE_HEADER);
        if (!StringUtils.hasText(declared)) {
            return null;
        }
        String canonical = declared.trim();
        String trusted = properties.getTrustedServiceName();
        boolean allowed = StringUtils.hasText(trusted)
            && Arrays.stream(trusted.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .anyMatch(name -> name.equalsIgnoreCase(canonical));
        if (!allowed) {
            log.warn("event=service_auth_denied service={} reason=service_unknown", canonical);
            return null;
        }
        String expectedToken = properties.getTrustedServiceToken();
        if (!StringUtils.hasText(expectedToken)) {
            log.warn("event=service_auth_denied service={} reason=token_not_configured", canonical);
            return null;
        }
        String suppliedToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(suppliedToken)) {
            log.warn("event=service_auth_denied service={} reason=token_missing", canonical);
            return null;
        }
        if (!MessageDigest.isEqual(
            expectedToken.getBytes(StandardCharsets.UTF_8),
            suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            log.warn("event=service_auth_denied service={} reason=token_invalid", canonical);
            return null;
        }
        return canonical;
    }

    private List<SimpleGrantedAuthority> resolveForwardedAuthorities(String rawRoles) {
        if (!StringUtils.hasText(rawRoles)) {
            return List.of();
        }
        return Arrays.stream(rawRoles.split(","))
            .map(this::normalizeHeader)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .toList();
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
