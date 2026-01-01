package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/security/audit-logs")
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.OP_ADMIN + "','" + AuthoritiesConstants.ADMIN + "')")
public class SecurityAuditLogProxyResource {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogProxyResource.class);

    private final RestTemplate restTemplate;
    private final DtsAdminProperties adminProperties;
    private final AuditService auditService;

    public SecurityAuditLogProxyResource(RestTemplateBuilder builder, DtsAdminProperties adminProperties, AuditService auditService) {
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(5)).setReadTimeout(Duration.ofSeconds(15)).build();
        this.adminProperties = adminProperties;
        this.auditService = auditService;
    }

    @GetMapping
    public ApiResponse<Object> list(
        @RequestParam Map<String, String> params,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        URI uri = buildAdminUri("/audit-logs", params);
        Object data = unwrapAdminData(exchange(uri, authorization));
        auditService.recordAuxiliary("READ", "security.auditLogs", "security.auditLogs", "LIST", Map.of("summary", "查看日志审计列表"));
        return ApiResponses.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> get(
        @PathVariable String id,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        URI uri = buildAdminUri("/audit-logs/" + id, Map.of());
        Object data = unwrapAdminData(exchange(uri, authorization));
        auditService.recordAuxiliary("READ", "security.auditLogs", "security.auditLogs", id, Map.of("summary", "查看日志审计详情"));
        return ApiResponses.ok(data);
    }

    private ResponseEntity<Map> exchange(URI uri, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(authorization)) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
    }

    private Object unwrapAdminData(ResponseEntity<Map> response) {
        Map body = response != null ? response.getBody() : null;
        if (body == null) {
            return null;
        }
        Object data = body.get("data");
        return data != null ? data : body;
    }

    private URI buildAdminUri(String path, Map<String, String> params) {
        if (adminProperties == null || !adminProperties.isEnabled()) {
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
