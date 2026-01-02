package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.DtsAdminProperties;
import com.yuzhi.dts.platform.service.audit.AuditService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/security/audit-logs")
@Transactional(readOnly = true)
@PreAuthorize("hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INSTITUTE_PRIVILEGED_ROLES)")
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
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看审计日志");
        payload.put("filters", params != null ? new LinkedHashMap<>(params) : Map.of());
        auditService.auditAction("SECURITY_AUDIT_LOG_LIST", AuditStage.SUCCESS, "audit-logs", payload);
        return ApiResponses.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<Object> get(
        @PathVariable String id,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        URI uri = buildAdminUri("/audit-logs/" + id, Map.of());
        Object data = unwrapAdminData(exchange(uri, authorization));
        auditService.auditAction(
            "SECURITY_AUDIT_LOG_VIEW",
            AuditStage.SUCCESS,
            id,
            Map.of("summary", "查看审计日志详情")
        );
        return ApiResponses.ok(data);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public void export(
        @RequestParam Map<String, String> params,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        HttpServletResponse response
    ) throws IOException {
        URI uri = buildAdminUri("/audit-logs/export", params);
        ResponseEntity<byte[]> resp = exchangeBytes(uri, authorization);
        if (resp == null || !resp.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "审计日志导出失败");
        }
        auditService.auditAction(
            "SECURITY_AUDIT_LOG_EXPORT",
            AuditStage.SUCCESS,
            "audit-logs.export",
            Map.of("summary", "导出审计日志", "filters", params != null ? new LinkedHashMap<>(params) : Map.of())
        );
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit-logs.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(resp.getBody() != null ? resp.getBody() : new byte[0]);
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
        headers.setAccept(java.util.List.of(new MediaType("text", "csv", StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
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
                    HttpStatus.BAD_GATEWAY,
                    String.valueOf(body.getOrDefault("message", "审计服务返回错误"))
                );
            }
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
