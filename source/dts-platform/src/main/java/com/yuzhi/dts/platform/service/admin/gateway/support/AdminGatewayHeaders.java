package com.yuzhi.dts.platform.service.admin.gateway.support;

import com.yuzhi.dts.common.net.ClientIpTrace;
import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AdminGatewayHeaders {

    private static final Logger log = LoggerFactory.getLogger(AdminGatewayHeaders.class);

    private final PlatformOutboundAdminProperties properties;

    public AdminGatewayHeaders(PlatformOutboundAdminProperties properties) {
        this.properties = properties;
    }

    public HttpHeaders createJsonHeaders(AdminGatewayRequestOptions options, boolean hasBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (hasBody) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (options != null && options.includeServiceAuthorization() && StringUtils.hasText(properties.getServiceToken())) {
            headers.set("X-DTS-Service-Token", stripBearerPrefix(properties.getServiceToken()));
        }
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set("X-DTS-Service", properties.getServiceName().trim());
        }
        if (options != null && options.auditSilent()) {
            headers.set("X-Audit-Silent", "true");
        }
        if (options != null && options.forwardAuthorization()) {
            String inbound = currentRequestHeader(HttpHeaders.AUTHORIZATION);
            if (StringUtils.hasText(inbound)) {
                headers.set(HttpHeaders.AUTHORIZATION, inbound.trim());
            }
        }
        propagateForwardedHeaders(headers);
        if (options != null && options.extraHeaders() != null) {
            options.extraHeaders().forEach((name, value) -> {
                if (StringUtils.hasText(name) && value != null) {
                    headers.set(name, value);
                }
            });
        }
        return headers;
    }

    void propagateForwardedHeaders(HttpHeaders headers) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return;
        }
        String forwardedStd = request.getHeader("Forwarded");
        String forwarded = request.getHeader("X-Forwarded-For");
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        ClientIpTrace trace = ClientIpTrace.from(request::getHeader, remote);
        String resolved = trace.resolved();
        trace.logInfo(log, "platform-admin-gateway-inbound", request.getMethod(), request.getRequestURI());

        if (StringUtils.hasText(forwarded)) {
            headers.set("X-Forwarded-For", forwarded.trim());
        } else if (StringUtils.hasText(resolved)) {
            headers.set("X-Forwarded-For", resolved.trim());
        } else if (StringUtils.hasText(remote)) {
            headers.set("X-Forwarded-For", remote.trim());
        }
        if (StringUtils.hasText(realIp)) {
            headers.set("X-Real-IP", realIp.trim());
        } else if (StringUtils.hasText(resolved)) {
            headers.set("X-Real-IP", resolved.trim());
        }
        if (StringUtils.hasText(forwardedStd)) {
            headers.set("Forwarded", forwardedStd.trim());
        } else if (StringUtils.hasText(resolved)) {
            headers.set("Forwarded", "for=\"" + resolved.trim() + "\"");
        }
        if (log.isInfoEnabled()) {
            log.info(
                "[platform-admin-gateway-forwarded] method={} uri={} outboundForwarded='{}' outboundXff='{}' outboundReal='{}'",
                nullSafe(request.getMethod()),
                nullSafe(request.getRequestURI()),
                nullSafe(headers.getFirst("Forwarded")),
                nullSafe(headers.getFirst("X-Forwarded-For")),
                nullSafe(headers.getFirst("X-Real-IP"))
            );
        }
    }

    private HttpServletRequest currentRequest() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs != null ? attrs.getRequest() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String currentRequestHeader(String name) {
        HttpServletRequest request = currentRequest();
        return request != null ? request.getHeader(name) : null;
    }

    private String stripBearerPrefix(String value) {
        String raw = value == null ? "" : value.trim();
        return raw.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length()) ? raw.substring("Bearer ".length()).trim() : raw;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
