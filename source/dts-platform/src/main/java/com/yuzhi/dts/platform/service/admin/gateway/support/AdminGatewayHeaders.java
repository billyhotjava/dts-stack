package com.yuzhi.dts.platform.service.admin.gateway.support;

import com.yuzhi.dts.common.net.IpAddressUtils;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class AdminGatewayHeaders {

    private final DtsAdminProperties properties;

    public AdminGatewayHeaders(DtsAdminProperties properties) {
        this.properties = properties;
    }

    public HttpHeaders createJsonHeaders(AdminGatewayRequestOptions options, boolean hasBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (hasBody) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        if (options != null && options.includeServiceAuthorization() && StringUtils.hasText(properties.getServiceToken())) {
            String raw = properties.getServiceToken().trim();
            headers.set(HttpHeaders.AUTHORIZATION, raw.startsWith("Bearer ") ? raw : "Bearer " + raw);
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
        String resolved = IpAddressUtils.resolveClientIp(forwarded, realIp, remote);

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
}
