package com.yuzhi.dts.platform.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ResultStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Caps the complete dimension-model JSON envelope before Jackson materializes its tree. */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 11)
public final class DimensionModelBodyLimitFilter extends OncePerRequestFilter {

    public static final long MAX_REQUEST_BYTES = 1024L * 1024L;
    private static final String CREATE_PATH = "/api/modeling/model-specs/dimension";

    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final long maxRequestBytes;

    @Autowired
    public DimensionModelBodyLimitFilter(ObjectMapper objectMapper, AuditService auditService) {
        this(objectMapper, auditService, MAX_REQUEST_BYTES);
    }

    DimensionModelBodyLimitFilter(
        ObjectMapper objectMapper,
        AuditService auditService,
        long maxRequestBytes
    ) {
        if (maxRequestBytes < 1 || maxRequestBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxRequestBytes must be between 1 and Integer.MAX_VALUE - 1");
        }
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        return !CREATE_PATH.equals(path);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxRequestBytes) {
            reject(response, declaredLength);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxRequestBytes + 1));
        if (body.length > maxRequestBytes) {
            reject(response, body.length);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response, long observedBytes) throws IOException {
        auditService.auditActionStrict(
            "MODELING_DIMENSION_MODEL_CREATE",
            AuditStage.FAIL,
            "dimension-model-request",
            Map.of(
                "errorCode",
                "DIMENSION_MODEL_REQUEST_TOO_LARGE",
                "observedBytes",
                Math.max(0L, observedBytes),
                "maxBytes",
                maxRequestBytes
            )
        );
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiResponse<>(
                ResultStatus.ERROR.getCode(),
                "Dimension model request exceeds the fixed wire envelope limit",
                "DIMENSION_MODEL_REQUEST_TOO_LARGE",
                Map.of("maxBytes", maxRequestBytes)
            )
        );
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    if (readListener == null) throw new IllegalArgumentException("readListener is required");
                    try {
                        if (isFinished()) readListener.onAllDataRead();
                        else readListener.onDataAvailable();
                    } catch (IOException exception) {
                        readListener.onError(exception);
                    }
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
