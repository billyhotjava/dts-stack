package com.yuzhi.dts.platform.web.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftContract;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftCorrelation;
import com.yuzhi.dts.platform.service.modeling.dbtdraft.DbtImplementationDraftRejectionAudit;
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
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Buffers and caps dbt-draft JSON before Spring/Jackson can deserialize the request. */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER + 10)
public final class DbtImplementationDraftBodyLimitFilter extends OncePerRequestFilter {

    private static final long JSON_WORST_CASE_ESCAPE_FACTOR = 6L;
    private static final long JSON_ENVELOPE_OVERHEAD_BYTES = 2L * 1024L * 1024L;
    public static final long MAX_REQUEST_BYTES =
        (DbtImplementationDraftContract.MAX_TOTAL_BYTES * JSON_WORST_CASE_ESCAPE_FACTOR) +
        JSON_ENVELOPE_OVERHEAD_BYTES;
    private static final Pattern ROUTE = Pattern.compile(
        "^/api/modeling/model-specs/[^/]+/(?:" +
        "dbt-drafts(?:/[^/]+/(?:files|validate|commit))?" +
        "|authoring-drafts(?:/[^/]+(?:/(?:validate|commit))?)?" +
        ")$"
    );

    private final ObjectMapper objectMapper;
    private final DbtImplementationDraftRejectionAudit rejectionAudit;
    private final long maxRequestBytes;

    @Autowired
    public DbtImplementationDraftBodyLimitFilter(
        ObjectMapper objectMapper,
        DbtImplementationDraftRejectionAudit rejectionAudit
    ) {
        this(objectMapper, rejectionAudit, MAX_REQUEST_BYTES);
    }

    DbtImplementationDraftBodyLimitFilter(
        ObjectMapper objectMapper,
        DbtImplementationDraftRejectionAudit rejectionAudit,
        long maxRequestBytes
    ) {
        if (maxRequestBytes < 1 || maxRequestBytes >= Integer.MAX_VALUE) {
            throw new IllegalArgumentException("maxRequestBytes must be between 1 and Integer.MAX_VALUE - 1");
        }
        this.objectMapper = objectMapper;
        this.rejectionAudit = rejectionAudit;
        this.maxRequestBytes = maxRequestBytes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String method = request.getMethod();
        if (!("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) return true;
        String path = request.getServletPath();
        if (path == null || path.isBlank()) path = request.getRequestURI();
        return path == null || !ROUTE.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String correlationId = DbtImplementationDraftCorrelation.install(request, response);
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxRequestBytes) {
            rejectionAudit.recordBodyTooLarge(request, correlationId, declaredLength);
            reject(response, correlationId);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(Math.toIntExact(maxRequestBytes + 1));
        if (body.length > maxRequestBytes) {
            rejectionAudit.recordBodyTooLarge(request, correlationId, body.length);
            reject(response, correlationId);
            return;
        }
        filterChain.doFilter(new CachedBodyRequest(request, body), response);
    }

    private void reject(HttpServletResponse response, String correlationId) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getOutputStream(),
            new ApiResponse<>(
                ResultStatus.ERROR.getCode(),
                "Advanced dbt draft request body exceeds the fixed wire envelope limit",
                "DBT_DRAFT_REQUEST_TOO_LARGE",
                Map.of("correlationId", correlationId)
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
                public void setReadListener(ReadListener listener) {
                    if (listener == null) throw new IllegalArgumentException("listener is required");
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
