package com.yuzhi.dts.copilot.analytics.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.web.rest.errors.ApiError;
import com.yuzhi.dts.copilot.analytics.web.support.RequestContextUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

public class SecurityProblemSupport implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String ERROR_CODE_HEADER = "X-Error-Code";
    private static final String ERROR_RETRYABLE_HEADER = "X-Error-Retryable";

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        writeError(response, request, HttpStatus.UNAUTHORIZED, "SEC_UNAUTHORIZED", authException.getMessage());
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        writeError(response, request, HttpStatus.FORBIDDEN, "SEC_FORBIDDEN", accessDeniedException.getMessage());
    }

    private void writeError(
            HttpServletResponse response,
            HttpServletRequest request,
            HttpStatus status,
            String code,
            String message)
            throws IOException {
        String path = request.getRequestURI();
        String requestId = RequestContextUtils.resolveRequestId();
        String resolvedCode = (code == null || code.isBlank()) ? "SECURITY_ERROR" : code;
        ApiError payload = new ApiError(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                resolvedCode,
                false,
                (message == null || message.isBlank()) ? status.getReasonPhrase() : message,
                path,
                requestId);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(ERROR_CODE_HEADER, resolvedCode);
        response.setHeader(ERROR_RETRYABLE_HEADER, "false");
        mapper.writeValue(response.getOutputStream(), payload);
    }
}
