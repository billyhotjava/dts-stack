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

    private AdminAuditHttpHeadersFactory() {}

    public static HttpHeaders build(DtsAdminProperties properties) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties == null) {
            return headers;
        }
        String token = properties.getServiceToken();
        if (StringUtils.hasText(token)) {
            String trimmed = token.trim();
            String value = trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                ? trimmed
                : BEARER_PREFIX + trimmed;
            headers.set(HttpHeaders.AUTHORIZATION, value);
        }
        if (StringUtils.hasText(properties.getServiceName())) {
            headers.set(SERVICE_HEADER, properties.getServiceName().trim());
        }
        return headers;
    }
}
