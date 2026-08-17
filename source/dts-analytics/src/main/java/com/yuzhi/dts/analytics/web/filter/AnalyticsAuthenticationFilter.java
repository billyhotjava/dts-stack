package com.yuzhi.dts.analytics.web.filter;

import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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

    private final AnalyticsSessionService sessionService;

    public AnalyticsAuthenticationFilter(AnalyticsSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
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
        filterChain.doFilter(request, response);
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
