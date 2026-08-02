package com.yuzhi.dts.platform.web.filter;

import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Installs one server-generated correlation id before any model-import request can be rejected. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public final class ModelSpecImportCorrelationFilter extends OncePerRequestFilter {

    private static final Set<String> IMPORT_PATHS = Set.of(
        "/api/modeling/model-spec-imports/dbt/archive/inspect",
        "/api/modeling/model-spec-imports/dbt/preview",
        "/api/modeling/model-spec-imports/dbt/apply"
    );
    private static final Pattern RETRY_PATH = Pattern.compile("^/api/modeling/model-spec-imports/[^/]+/retry$");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) return true;
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        return path == null || (!IMPORT_PATHS.contains(path) && !RETRY_PATH.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        DbtImplementationDraftCorrelation.install(request, response);
        filterChain.doFilter(request, response);
    }
}
