package com.yuzhi.dts.admin.web.filter;

import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator.Decision;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Transport guard for the internal audit ingest endpoint.
 *
 * <p>Authentication happens before the request body is read. Authenticated bodies are then
 * bounded and buffered once, so MVC/Jackson never receives an unbounded stream.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuditIngestPreAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTHENTICATION_ATTRIBUTE = AuditIngestPreAuthenticationFilter.class.getName() + ".decision";
    private static final String AUDIT_INGEST_PATH = "/api/audit-events";

    private final AdminInboundServiceAuthenticator authenticator;
    private final AuditIngestProperties properties;
    private final ConcurrentMap<String, TokenBucket> producerBuckets = new ConcurrentHashMap<>();

    public AuditIngestPreAuthenticationFilter(
        AdminInboundServiceAuthenticator authenticator,
        AuditIngestProperties properties
    ) {
        this.authenticator = authenticator;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request == null || !AUDIT_INGEST_PATH.equals(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        Decision decision = authenticator.authenticate(request);
        if (!decision.accepted()) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            return;
        }
        if (!allow(decision.serviceName())) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }

        int maxBodyBytes = properties.getMaxBodyBytes();
        long declaredLength = request.getContentLengthLong();
        if (declaredLength > maxBodyBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }
        byte[] body = request.getInputStream().readNBytes(maxBodyBytes + 1);
        if (body.length > maxBodyBytes) {
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            return;
        }

        CachedBodyRequest wrapped = new CachedBodyRequest(request, body);
        wrapped.setAttribute(AUTHENTICATION_ATTRIBUTE, decision);
        filterChain.doFilter(wrapped, response);
    }

    private boolean allow(String producer) {
        int rate = properties.getRateLimitPerSecond();
        int burst = properties.getRateLimitBurst();
        return producerBuckets
            .computeIfAbsent(producer, ignored -> new TokenBucket(burst, System.nanoTime()))
            .tryAcquire(rate, burst, System.nanoTime());
    }

    private static final class TokenBucket {

        private double tokens;
        private long lastRefillNanos;

        private TokenBucket(int capacity, long nowNanos) {
            this.tokens = capacity;
            this.lastRefillNanos = nowNanos;
        }

        private synchronized boolean tryAcquire(int ratePerSecond, int capacity, long nowNanos) {
            long elapsed = Math.max(0, nowNanos - lastRefillNanos);
            tokens = Math.min(capacity, tokens + ((double) elapsed / 1_000_000_000d) * ratePerSecond);
            lastRefillNanos = nowNanos;
            if (tokens < 1d) {
                return false;
            }
            tokens -= 1d;
            return true;
        }
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
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
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] target, int offset, int length) {
                    return input.read(target, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
