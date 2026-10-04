package com.yuzhi.dts.platform.service.admin.gateway.support;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class AdminGatewayTransport {

    private static final Logger LOG = LoggerFactory.getLogger(AdminGatewayTransport.class);

    private final RestTemplate restTemplate;
    private final RestTemplate currentDirectoryTemplate;
    private final PlatformOutboundAdminProperties properties;
    private final AdminGatewayHeaders gatewayHeaders;

    @Autowired
    public AdminGatewayTransport(RestTemplateBuilder builder, PlatformOutboundAdminProperties properties) {
        this(builder, properties, new AdminGatewayHeaders(properties));
    }

    public AdminGatewayTransport(RestTemplateBuilder builder, PlatformOutboundAdminProperties properties, AdminGatewayHeaders gatewayHeaders) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(10)).build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
        factory.setReadTimeout(Duration.ofSeconds(2));
        this.currentDirectoryTemplate = builder.requestFactory(() -> factory).build();
        this.properties = properties;
        this.gatewayHeaders = gatewayHeaders;
    }

    public URI buildUri(AdminGatewayTarget target, String suffix) {
        String baseUrl = StringUtils.hasText(properties.getBaseUrl()) ? properties.getBaseUrl().trim() : "http://dts-admin:8081";
        String basePath = target == AdminGatewayTarget.ADMIN_API ? properties.getAdminApiPath() : properties.getApiPath();
        String normalizedBase = baseUrl.replaceAll("/+$", "");
        String normalizedPath = normalizePath(basePath);
        String normalizedSuffix = normalizeSuffix(suffix);
        return URI.create(normalizedBase + normalizedPath + normalizedSuffix);
    }

    public <T> T exchangeRaw(
        AdminGatewayTarget target,
        HttpMethod method,
        String suffix,
        Object payload,
        ParameterizedTypeReference<T> responseType,
        AdminGatewayRequestOptions options
    ) {
        URI uri = buildUri(target, suffix);
        try {
            HttpHeaders headers = gatewayHeaders.createJsonHeaders(resolveOptions(options), payload != null);
            HttpEntity<?> entity = payload != null ? new HttpEntity<>(payload, headers) : new HttpEntity<>(headers);
            ResponseEntity<T> response = restTemplate.exchange(uri, method, entity, responseType);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            throw upstreamFailure(uri, ex);
        } catch (Exception ex) {
            throw new AdminGatewayException("Admin gateway call failed: " + uri, null, uri.toString(), ex);
        }
    }

    public <T> T exchangeEnvelopeData(
        AdminGatewayTarget target,
        HttpMethod method,
        String suffix,
        Object payload,
        ParameterizedTypeReference<AdminGatewayEnvelope<T>> responseType,
        AdminGatewayRequestOptions options
    ) {
        URI uri = buildUri(target, suffix);
        try {
            HttpHeaders headers = gatewayHeaders.createJsonHeaders(resolveOptions(options), payload != null);
            HttpEntity<?> entity = payload != null ? new HttpEntity<>(payload, headers) : new HttpEntity<>(headers);
            ResponseEntity<AdminGatewayEnvelope<T>> response = restTemplate.exchange(uri, method, entity, responseType);
            AdminGatewayEnvelope<T> envelope = response.getBody();
            if (envelope == null) {
                throw new AdminGatewayException("Admin gateway returned empty response", response.getStatusCode().value(), uri.toString());
            }
            if (!envelope.isSuccess()) {
                throw new AdminGatewayException(
                    StringUtils.hasText(envelope.message()) ? envelope.message() : "Admin gateway returned error envelope",
                    response.getStatusCode().value(),
                    uri.toString()
                );
            }
            return envelope.data();
        } catch (HttpStatusCodeException ex) {
            throw upstreamFailure(uri, ex);
        } catch (AdminGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AdminGatewayException("Admin gateway call failed: " + uri, null, uri.toString(), ex);
        }
    }

    /** Current authorization facts: one HTTP request, two second deadline, no fallback or retry. */
    public <T> T currentDirectory(String suffix, ParameterizedTypeReference<AdminGatewayEnvelope<T>> type) {
        URI uri = buildUri(AdminGatewayTarget.API, suffix);
        try {
            var headers = gatewayHeaders.createJsonHeaders(AdminGatewayRequestOptions.defaults(), false);
            var response = currentDirectoryTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type);
            var envelope = response.getBody();
            if (envelope == null || !envelope.isSuccess() || envelope.data() == null) {
                throw new AdminGatewayException("Current directory response is incomplete", 503, uri.toString());
            }
            return envelope.data();
        } catch (HttpStatusCodeException ex) {
            throw upstreamFailure(uri, ex);
        } catch (AdminGatewayException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AdminGatewayException("Current directory unavailable", 503, uri.toString(), ex);
        }
    }

    private AdminGatewayRequestOptions resolveOptions(AdminGatewayRequestOptions options) {
        return options != null ? options : AdminGatewayRequestOptions.defaults();
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "";
        }
        String normalized = path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        return normalized.replaceAll("/+$", "");
    }

    private String normalizeSuffix(String suffix) {
        if (!StringUtils.hasText(suffix)) {
            return "";
        }
        String normalized = suffix.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private AdminGatewayException upstreamFailure(URI uri, HttpStatusCodeException ex) {
        String body = ex.getResponseBodyAsString();
        String message = StringUtils.hasText(body) ? trim(body, 256) : ex.getMessage();
        LOG.debug("Admin gateway upstream failure status={} uri={} body={}", ex.getStatusCode().value(), uri, message);
        return new AdminGatewayException(message, ex.getStatusCode().value(), uri.toString(), ex);
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }
}
