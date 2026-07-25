package com.yuzhi.dts.platform.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.converter.SafeZipExtractor;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects oversized or chunked model-archive uploads before Spring resolves multipart parts.
 *
 * <p>The one MiB allowance covers multipart boundaries around the 32 MiB archive. Nginx applies
 * the same raw-request limit; this filter also protects direct backend access in development.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public final class ModelArchiveUploadLimitFilter extends OncePerRequestFilter {

    static final String ARCHIVE_INSPECT_PATH = "/api/modeling/model-spec-imports/dbt/archive/inspect";
    static final long MAX_REQUEST_BYTES = SafeZipExtractor.MAX_ARCHIVE_BYTES + (1024L * 1024);

    private final ObjectMapper objectMapper;

    public ModelArchiveUploadLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !ARCHIVE_INSPECT_PATH.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength < 0) {
            reject(
                response,
                HttpStatus.LENGTH_REQUIRED,
                "MODEL_IMPORT_ARCHIVE_LENGTH_REQUIRED",
                "dbt ZIP 上传必须提供 Content-Length"
            );
            return;
        }
        if (contentLength > MAX_REQUEST_BYTES) {
            reject(
                response,
                HttpStatus.PAYLOAD_TOO_LARGE,
                "MODEL_IMPORT_ARCHIVE_TOO_LARGE",
                "dbt ZIP 请求体超过允许大小"
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiResponse<>(ResultStatus.ERROR.getCode(), message, code, null)
        );
    }
}
