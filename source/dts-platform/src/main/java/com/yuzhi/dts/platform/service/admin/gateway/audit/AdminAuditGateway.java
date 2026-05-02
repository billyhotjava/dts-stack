package com.yuzhi.dts.platform.service.admin.gateway.audit;

import com.yuzhi.dts.platform.config.PlatformOutboundAdminProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class AdminAuditGateway {

    private final RestTemplate restTemplate;
    private final PlatformOutboundAdminProperties adminProperties;

    public AdminAuditGateway(RestTemplateBuilder builder, PlatformOutboundAdminProperties adminProperties) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.adminProperties = adminProperties;
    }

    public boolean isEnabled() {
        return adminProperties != null && adminProperties.isEnabled();
    }

    public Object list(Map<String, String> params, String authorization) {
        return unwrapAdminData(exchange(buildAdminUri("/audit-entries", params), authorization));
    }

    public Object get(String id, String authorization) {
        return unwrapAdminData(exchange(buildAdminUri("/audit-entries/" + id, Map.of()), authorization));
    }

    public byte[] export(Map<String, String> params, String authorization) {
        ResponseEntity<byte[]> response = exchangeBytes(buildAdminUri("/audit-entries/export", params), authorization);
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, "审计日志导出失败");
        }
        return response.getBody() != null ? response.getBody() : new byte[0];
    }

    public boolean recordEvent(Map<String, Object> body) {
        if (!isEnabled() || body == null) {
            return false;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(adminProperties.getServiceToken())) {
                String raw = adminProperties.getServiceToken().trim();
                headers.set(HttpHeaders.AUTHORIZATION, raw.startsWith("Bearer ") ? raw : "Bearer " + raw);
            }
            if (StringUtils.hasText(adminProperties.getServiceName())) {
                headers.set("X-DTS-Service", adminProperties.getServiceName().trim());
            }
            ResponseEntity<Void> response = restTemplate.postForEntity(
                buildAdminUri("/audit-events", Map.of()),
                new HttpEntity<>(body, headers),
                Void.class
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (RestClientException ex) {
            return false;
        }
    }

    private ResponseEntity<Map> exchange(URI uri, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
    }

    private ResponseEntity<byte[]> exchangeBytes(URI uri, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(new MediaType("text", "csv", StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
    }

    private Object unwrapAdminData(ResponseEntity<Map> response) {
        Map body = response != null ? response.getBody() : null;
        if (body == null) {
            return null;
        }
        Object status = body.get("status");
        if (status != null) {
            String text = String.valueOf(status).trim().toUpperCase();
            if (!text.isEmpty() && !"SUCCESS".equals(text) && !"OK".equals(text)) {
                throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY,
                    String.valueOf(body.getOrDefault("message", "审计服务返回错误"))
                );
            }
        }
        Object data = body.get("data");
        return data != null ? data : body;
    }

    private URI buildAdminUri(String path, Map<String, String> params) {
        if (!isEnabled()) {
            throw new IllegalStateException("dts-admin 服务调用已禁用");
        }
        String base = StringUtils.hasText(adminProperties.getBaseUrl()) ? adminProperties.getBaseUrl().trim() : "http://dts-admin:8081";
        String normalizedBase = base.replaceAll("/+$", "");
        String apiPath = StringUtils.hasText(adminProperties.getApiPath()) ? adminProperties.getApiPath().trim() : "/api";
        String normalizedApiPath = "/" + apiPath.replaceAll("^/+", "").replaceAll("/+$", "");
        String normalizedRelPath = "/" + String.valueOf(path).replaceAll("^/+", "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(normalizedBase + normalizedApiPath + normalizedRelPath);
        if (params != null && !params.isEmpty()) {
            params.forEach((k, v) -> {
                if (k != null && v != null) {
                    builder.queryParam(k, v);
                }
            });
        }
        return builder.build(true).toUri();
    }
}
