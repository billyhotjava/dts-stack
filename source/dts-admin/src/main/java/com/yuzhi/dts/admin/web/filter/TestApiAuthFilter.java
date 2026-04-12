package com.yuzhi.dts.admin.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates X-Test-Token header for /test/** endpoints. Configured via
 * {@code app.test-api.token}. Only registered when the test-api is enabled
 * and the prod profile is not active (see TestApiSecurityConfiguration).
 */
public class TestApiAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Test-Token";
    private final String expected;

    public TestApiAuthFilter(String expected) {
        this.expected = expected == null ? "" : expected;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
        String supplied = req.getHeader(HEADER);
        if (expected.isBlank() || supplied == null || !constantTimeEquals(expected, supplied)) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"error\":\"invalid or missing X-Test-Token\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
