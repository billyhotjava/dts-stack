package com.yuzhi.dts.analytics.web.filter;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public class AnalyticsAuthenticationFilter extends OncePerRequestFilter {

    private static final String PLATFORM_SERVICE = "dts-platform";
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";
    private static final String SEMANTIC_PUBLISH_PATH = "/api/semantic/publish";
    private static final String INTERNAL_SCREENS_PATH = "/api/internal/screens";

    private final AnalyticsSessionService sessionService;
    private final String platformServiceToken;

    public AnalyticsAuthenticationFilter(AnalyticsSessionService sessionService) {
        this(sessionService, null);
    }

    public AnalyticsAuthenticationFilter(AnalyticsSessionService sessionService, String platformServiceToken) {
        this.sessionService = sessionService;
        this.platformServiceToken = trimToNull(platformServiceToken);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            if (isTrustedPlatformSemanticPublish(request)) {
                UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                    PLATFORM_SERVICE,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ANALYTICS_SERVICE"))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                sessionService
                    .resolveUser(request)
                    .filter(AnalyticsUser::isActive)
                    .ifPresent(actor -> {
                        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                            actor,
                            null,
                            authorities(actor, request.getHeader("X-DTS-Roles"))
                        );
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    });
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean isTrustedPlatformSemanticPublish(HttpServletRequest request) {
        boolean semanticPublish = request != null &&
            "POST".equalsIgnoreCase(request.getMethod()) &&
            SEMANTIC_PUBLISH_PATH.equals(request.getRequestURI());
        boolean internalScreenRead = request != null &&
            "GET".equalsIgnoreCase(request.getMethod()) &&
            INTERNAL_SCREENS_PATH.equals(request.getRequestURI());
        if (
            request == null ||
            platformServiceToken == null ||
            (!semanticPublish && !internalScreenRead) ||
            !PLATFORM_SERVICE.equals(request.getHeader(SERVICE_HEADER))
        ) {
            return false;
        }
        String suppliedToken = trimToNull(request.getHeader(SERVICE_TOKEN_HEADER));
        return suppliedToken != null && MessageDigest.isEqual(bytes(platformServiceToken), bytes(suppliedToken));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private List<SimpleGrantedAuthority> authorities(AnalyticsUser actor, String rolesHeader) {
        Set<String> names = new LinkedHashSet<>();
        if (rolesHeader != null) {
            for (String role : rolesHeader.split(",")) {
                String value = role.trim();
                if (!value.isEmpty()) names.add(value.startsWith("ROLE_") ? value : "ROLE_" + value);
            }
        }
        if (actor.isSuperuser()) names.add("ROLE_ANALYTICS_ADMIN");
        if (names.isEmpty()) names.add("ROLE_ANALYTICS_USER");
        return names.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
