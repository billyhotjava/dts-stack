package com.yuzhi.dts.copilot.analytics.web.filter;

import com.yuzhi.dts.copilot.analytics.config.Constants;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(Constants.LOG_CATEGORY_REQUESTS);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long start = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            String requestId = RequestContextUtils.resolveRequestId();
            log.info(
                    "{} {} -> {} ({} ms) [requestId={}]",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration,
                    requestId == null ? "unknown" : requestId);
        }
    }
}
