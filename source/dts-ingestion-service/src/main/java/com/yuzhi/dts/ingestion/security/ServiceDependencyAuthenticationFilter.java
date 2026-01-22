package com.yuzhi.dts.ingestion.security;

import com.yuzhi.dts.ingestion.config.IngestionProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "service:" + serviceName,
                    null,
                    List.of(new SimpleGrantedAuthority(AuthoritiesConstants.OP_ADMIN))
                );
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated internal service call as {}", serviceName);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveServiceName(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return null;
        }
        String expected = properties.getTrustedServiceName();
        String declared = request.getHeader(SERVICE_HEADER);
        if (!StringUtils.hasText(declared)) {
            return null;
        }
        if (StringUtils.hasText(expected) && expected.equalsIgnoreCase(declared.trim())) {
            return expected;
        }
        return null;
    }
}
