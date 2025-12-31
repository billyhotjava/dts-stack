package com.yuzhi.dts.analytics.web.ui;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

public class MetabaseSpaFallbackFilter extends OncePerRequestFilter {

    private final MetabaseUiTemplateRenderer renderer;

    public MetabaseSpaFallbackFilter(MetabaseUiTemplateRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (shouldServeIndex(request)) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.getWriter().write(renderer.renderIndex(request));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldServeIndex(HttpServletRequest request) {
        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            return false;
        }

        String path = request.getRequestURI();
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.startsWith("/api/") || path.equals("/api")) {
            return false;
        }
        if (path.startsWith("/actuator/") || path.equals("/actuator")) {
            return false;
        }
        if (path.startsWith("/app/") || path.equals("/app")) {
            return false;
        }
        if (path.startsWith("/webjars/") || path.equals("/webjars")) {
            return false;
        }
        if (path.equals("/favicon.ico")) {
            return false;
        }
        int lastSlash = path.lastIndexOf('/');
        String lastSegment = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (lastSegment.contains(".")) {
            return false;
        }

        String accept = request.getHeader("Accept");
        if (accept == null) {
            return true;
        }
        String lower = accept.toLowerCase(Locale.ROOT);
        return lower.contains("text/html") || lower.contains("*/*");
    }
}
