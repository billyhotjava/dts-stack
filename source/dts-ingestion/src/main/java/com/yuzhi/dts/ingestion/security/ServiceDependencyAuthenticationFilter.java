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
import java.util.Map;
import java.util.Set;
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
    private static final Set<String> FORWARDABLE_AUTHORITIES = Set.of(
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.USER,
        AuthoritiesConstants.INST_DATA_OWNER,
        AuthoritiesConstants.INST_LEADER,
        AuthoritiesConstants.DEPT_DATA_OWNER,
        AuthoritiesConstants.DEPT_LEADER
    );

    private final IngestionProperties properties;

    public ServiceDependencyAuthenticationFilter(IngestionProperties properties) {
        this.properties = properties;
        this.properties.validateTrustedServiceTokens();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = current != null && current.isAuthenticated() && !(current instanceof AnonymousAuthenticationToken);
        if (!authenticated) {
            InternalServiceRequestPolicy.Grant grant = resolveServiceGrant(request);
            if (grant.allowed()) {
                String forwardedUser = normalizeIdentityHeader(request.getHeader(USER_HEADER));
                if (forwardedUser != null && !grant.forwardedIdentityAllowed()) {
                    log.warn(
                        "event=service_auth_denied service={} method={} reason=forwarded_identity_not_allowed",
                        grant.serviceName(),
                        request.getMethod()
                    );
                } else {
                    Object principal = forwardedUser != null
                        ? new ForwardedUserPrincipal(forwardedUser, grant.serviceName())
                        : "service:" + grant.serviceName();
                    List<SimpleGrantedAuthority> businessAuthorities = forwardedUser != null
                        ? resolveForwardedAuthorities(request.getHeader(ROLES_HEADER))
                        : List.of(new SimpleGrantedAuthority(AuthoritiesConstants.OP_ADMIN));
                    List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>(businessAuthorities);
                    authorities.add(new SimpleGrantedAuthority(serviceAuthority(grant.serviceName())));
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                    );
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.debug(
                        "Authenticated internal service call service={} principal={}",
                        grant.serviceName(),
                        principal
                    );
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private InternalServiceRequestPolicy.Grant resolveServiceGrant(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return InternalServiceRequestPolicy.Grant.denied();
        }
        String declared = request.getHeader(SERVICE_HEADER);
        if (!StringUtils.hasText(declared)) {
            return InternalServiceRequestPolicy.Grant.denied();
        }
        String canonical = declared.trim();
        String expectedToken = configuredToken(canonical);
        if (expectedToken == null) {
            log.warn("event=service_auth_denied service={} reason=service_unknown", canonical);
            return InternalServiceRequestPolicy.Grant.denied();
        }
        if (!StringUtils.hasText(expectedToken)) {
            log.warn("event=service_auth_denied service={} reason=token_not_configured", canonical);
            return InternalServiceRequestPolicy.Grant.denied();
        }
        String suppliedToken = request.getHeader(SERVICE_TOKEN_HEADER);
        if (!StringUtils.hasText(suppliedToken)) {
            log.warn("event=service_auth_denied service={} reason=token_missing", canonical);
            return InternalServiceRequestPolicy.Grant.denied();
        }
        if (!MessageDigest.isEqual(
            expectedToken.getBytes(StandardCharsets.UTF_8),
            suppliedToken.getBytes(StandardCharsets.UTF_8)
        )) {
            log.warn("event=service_auth_denied service={} reason=token_invalid", canonical);
            return InternalServiceRequestPolicy.Grant.denied();
        }
        String contextPath = request.getContextPath();
        String requestPath = request.getRequestURI();
        if (StringUtils.hasText(contextPath) && requestPath.startsWith(contextPath)) {
            requestPath = requestPath.substring(contextPath.length());
        }
        InternalServiceRequestPolicy.Grant grant = InternalServiceRequestPolicy.authorize(
            canonical,
            request.getMethod(),
            requestPath
        );
        if (!grant.allowed()) {
            log.warn(
                "event=service_auth_denied service={} method={} reason=route_not_allowed",
                canonical,
                request.getMethod()
            );
        }
        return grant;
    }

    private String configuredToken(String canonicalServiceName) {
        Map<String, String> mappedTokens = properties.getTrustedServiceTokens();
        if (mappedTokens != null) {
            Map.Entry<String, String> mappedToken = mappedTokens.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().trim().equalsIgnoreCase(canonicalServiceName))
                .findFirst()
                .orElse(null);
            if (mappedToken != null) {
                return mappedToken.getValue();
            }
        }
        return null;
    }

    private List<SimpleGrantedAuthority> resolveForwardedAuthorities(String rawRoles) {
        if (!StringUtils.hasText(rawRoles)) {
            return List.of();
        }
        return Arrays.stream(rawRoles.split(","))
            .map(this::normalizeHeader)
            .filter(java.util.Objects::nonNull)
            .filter(FORWARDABLE_AUTHORITIES::contains)
            .distinct()
            .limit(32)
            .map(SimpleGrantedAuthority::new)
            .toList();
    }

    private String normalizeIdentityHeader(String value) {
        String normalized = normalizeHeader(value);
        if (normalized == null || normalized.length() > 128) {
            return null;
        }
        return normalized.chars().anyMatch(Character::isISOControl) ? null : normalized;
    }

    private String serviceAuthority(String serviceName) {
        return switch (serviceName) {
            case "dts-platform" -> AuthoritiesConstants.SERVICE_DTS_PLATFORM;
            case "dts-airflow" -> AuthoritiesConstants.SERVICE_DTS_AIRFLOW;
            default -> throw new IllegalArgumentException("Unsupported internal service principal");
        };
    }

    private String normalizeHeader(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
