package com.yuzhi.dts.analytics.service.audit;

import com.yuzhi.dts.analytics.config.DtsAdminProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

/**
 * Builds HTTP headers for outbound calls to the dts-admin audit ingest endpoint.
 * <p>
 * Centralized so that any future change to the wire authentication (e.g. mTLS,
 * signed JWT) only has to be applied here.
 */
public final class AdminAuditHttpHeadersFactory {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String SERVICE_HEADER = "X-DTS-Service";
    private static final String SERVICE_TOKEN_HEADER = "X-DTS-Service-Token";

    private AdminAuditHttpHeadersFactory() {}

    public static HttpHeaders build(DtsAdminProperties properties) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties == null) {
            return headers;
        }
        String token = properties.getServiceToken();
        if (StringUtils.hasText(token)) {
            headers.set(SERVICE_TOKEN_HEADER, stripBearerPrefix(token));
        }
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set(SERVICE_HEADER, properties.getServiceName().trim());
        }
        return headers;
    }

    private static String stripBearerPrefix(String token) {
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return trimmed.substring(BEARER_PREFIX.length()).trim();
        }
        return trimmed;
    }
}
