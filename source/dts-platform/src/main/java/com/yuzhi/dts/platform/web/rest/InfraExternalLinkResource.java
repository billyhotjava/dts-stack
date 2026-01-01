package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.service.InfraExternalLink;
import com.yuzhi.dts.platform.repository.service.InfraExternalLinkRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.yuzhi.dts.common.audit.AuditStage;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/infra/external-links")
@Transactional
public class InfraExternalLinkResource {

    private static final Logger log = LoggerFactory.getLogger(InfraExternalLinkResource.class);

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final InfraExternalLinkRepository repo;
    private final AuditService auditService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public InfraExternalLinkResource(
        InfraExternalLinkRepository repo,
        AuditService auditService,
        RestTemplateBuilder builder,
        ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.auditService = auditService;
        this.restTemplate = builder.setConnectTimeout(Duration.ofSeconds(3)).setReadTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ApiResponse<List<InfraExternalLink>> list() {
        List<InfraExternalLink> list = repo
            .findAll()
            .stream()
            .sorted(Comparator.comparing(link -> String.valueOf(link.getEntryKey()).toUpperCase(Locale.ROOT)))
            .toList();
        auditService.auditAction("INFRA_EXTERNAL_LINK_VIEW", AuditStage.SUCCESS, "list", Map.of("summary", "查看外部平台入口配置列表"));
        return ApiResponses.ok(list);
    }

    @GetMapping("/{entryKey}")
    public ApiResponse<InfraExternalLink> get(@PathVariable String entryKey) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "查看外部平台入口配置");
        payload.put("entryKey", normalizedKey);
        payload.put("enabled", link != null ? link.getEnabled() : null);
        payload.put("url", link != null ? link.getUrl() : null);
        auditService.auditAction("INFRA_EXTERNAL_LINK_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
        return ApiResponses.ok(link);
    }

    @GetMapping("/{entryKey}/check")
    public ApiResponse<Map<String, Object>> check(@PathVariable String entryKey, HttpServletResponse response) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElse(null);
        String url = link != null && StringUtils.hasText(link.getUrl()) ? link.getUrl().trim() : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entryKey", normalizedKey);
        payload.put("url", url);

        if (!StringUtils.hasText(url)) {
            payload.put("reachable", false);
            payload.put("status", "NOT_CONFIGURED");
            payload.put("summary", "检查外部平台连通性：未配置URL");
            auditService.auditAction("INFRA_EXTERNAL_LINK_CHECK", AuditStage.SUCCESS, normalizedKey, payload);
            return ApiResponses.ok(payload);
        }

        if (!isHttpUrl(url)) {
            payload.put("reachable", false);
            payload.put("status", "INVALID_URL");
            payload.put("summary", "检查外部平台连通性：URL不合法");
            auditService.auditAction("INFRA_EXTERNAL_LINK_CHECK", AuditStage.SUCCESS, normalizedKey, payload);
            return ApiResponses.ok(payload);
        }

        Instant start = Instant.now();
        Integer statusCode = null;
        boolean reachable = false;
        String error = null;

        try {
            URI uri = URI.create(url);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "*/*");
            headers.set(HttpHeaders.RANGE, "bytes=0-0");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<byte[]> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, byte[].class);
            statusCode = resp.getStatusCode().value();
            reachable = statusCode >= 200 && statusCode < 400;
            if (!reachable && (statusCode == 401 || statusCode == 403)) {
                // reachable but requires auth
                reachable = true;
            }
        } catch (IllegalArgumentException ex) {
            error = "URL不合法：" + ex.getMessage();
        } catch (RestClientException ex) {
            error = ex.getMessage();
            log.debug("External link check failed: key={}, url={}, error={}", normalizedKey, url, error);
        }

        long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
        payload.put("reachable", reachable);
        payload.put("durationMs", durationMs);
        if (statusCode != null) {
            payload.put("httpStatus", statusCode);
        }
        if (error != null) {
            payload.put("error", error);
        }
        payload.put("status", reachable ? "OK" : "FAILED");
        payload.put("summary", reachable ? "检查外部平台连通性：可访问" : "检查外部平台连通性：不可访问");
        auditService.auditAction("INFRA_EXTERNAL_LINK_CHECK", AuditStage.SUCCESS, normalizedKey, payload);

        // no-cache
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponses.ok(payload);
    }

    @GetMapping("/{entryKey}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable String entryKey, HttpServletResponse response) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElse(null);
        String url = link != null && StringUtils.hasText(link.getUrl()) ? link.getUrl().trim() : null;
        boolean enabled = link != null && Boolean.TRUE.equals(link.getEnabled());
        boolean statusEnabled = link != null && Boolean.TRUE.equals(link.getStatusApiEnabled());
        String statusApiUrl = link != null && StringUtils.hasText(link.getStatusApiUrl()) ? link.getStatusApiUrl().trim() : null;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("entryKey", normalizedKey);
        payload.put("url", url);
        payload.put("enabled", enabled);
        payload.put("statusApiEnabled", statusEnabled);
        payload.put("statusApiUrl", statusApiUrl);

        if (!enabled) {
            payload.put("reachable", false);
            payload.put("status", "DISABLED");
            payload.put("summary", "查看外部平台状态：入口未启用");
            auditService.auditAction("INFRA_EXTERNAL_LINK_STATUS_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return ApiResponses.ok(payload);
        }

        if (!statusEnabled || !StringUtils.hasText(statusApiUrl)) {
            payload.put("reachable", false);
            payload.put("status", "NOT_CONFIGURED");
            payload.put("summary", "查看外部平台状态：未配置状态接口");
            auditService.auditAction("INFRA_EXTERNAL_LINK_STATUS_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return ApiResponses.ok(payload);
        }

        if (!isHttpUrl(statusApiUrl)) {
            payload.put("reachable", false);
            payload.put("status", "INVALID_URL");
            payload.put("summary", "查看外部平台状态：状态接口URL不合法");
            auditService.auditAction("INFRA_EXTERNAL_LINK_STATUS_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return ApiResponses.ok(payload);
        }

        if (StringUtils.hasText(url) && isHttpUrl(url) && !isSameHost(url, statusApiUrl)) {
            payload.put("reachable", false);
            payload.put("status", "HOST_MISMATCH");
            payload.put("error", "状态接口URL必须与入口URL同域（host一致）");
            payload.put("summary", "查看外部平台状态：状态接口URL不被允许");
            auditService.auditAction("INFRA_EXTERNAL_LINK_STATUS_VIEW", AuditStage.SUCCESS, normalizedKey, payload);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
            return ApiResponses.ok(payload);
        }

        Instant start = Instant.now();
        Integer statusCode = null;
        boolean reachable = false;
        String error = null;
        JsonNode data = null;

        try {
            URI uri = URI.create(statusApiUrl);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/json, */*");
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> resp = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            statusCode = resp.getStatusCode().value();
            reachable = statusCode >= 200 && statusCode < 400;
            if (!reachable && (statusCode == 401 || statusCode == 403)) {
                reachable = true;
            }

            String body = resp.getBody();
            if (StringUtils.hasText(body)) {
                if (body.length() > 200_000) {
                    throw new IllegalStateException("状态接口返回内容过大（>200KB），请改用更精简的状态接口");
                }
                data = objectMapper.readTree(body);
            }
        } catch (IllegalArgumentException ex) {
            error = "URL不合法：" + ex.getMessage();
        } catch (RestClientException ex) {
            error = ex.getMessage();
            log.debug("External link status fetch failed: key={}, url={}, error={}", normalizedKey, statusApiUrl, error);
        } catch (Exception ex) {
            error = ex.getMessage();
            log.debug("External link status parse failed: key={}, url={}, error={}", normalizedKey, statusApiUrl, error);
        }

        long durationMs = java.time.Duration.between(start, Instant.now()).toMillis();
        payload.put("reachable", reachable);
        payload.put("durationMs", durationMs);
        if (statusCode != null) {
            payload.put("httpStatus", statusCode);
        }
        if (error != null) {
            payload.put("error", error);
        }
        if (data != null) {
            payload.put("data", data);
        }
        payload.put("status", reachable ? "OK" : "FAILED");
        payload.put("summary", reachable ? "查看外部平台状态：已获取" : "查看外部平台状态：获取失败");
        auditService.auditAction("INFRA_EXTERNAL_LINK_STATUS_VIEW", AuditStage.SUCCESS, normalizedKey, payload);

        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return ApiResponses.ok(payload);
    }

    @PutMapping("/{entryKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<InfraExternalLink> upsert(@PathVariable String entryKey, @Valid @RequestBody InfraExternalLink patch) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        if (normalizedKey.isEmpty()) {
            throw new IllegalArgumentException("entryKey 不能为空");
        }
        InfraExternalLink link = repo.findByEntryKeyIgnoreCase(normalizedKey).orElseGet(InfraExternalLink::new);
        link.setEntryKey(normalizedKey);
        link.setName(StringUtils.hasText(patch.getName()) ? patch.getName().trim() : null);
        link.setUrl(StringUtils.hasText(patch.getUrl()) ? patch.getUrl().trim() : null);
        link.setDescription(StringUtils.hasText(patch.getDescription()) ? patch.getDescription().trim() : null);
        link.setEnabled(patch.getEnabled() != null ? patch.getEnabled() : Boolean.TRUE);
        link.setStatusApiEnabled(patch.getStatusApiEnabled() != null ? patch.getStatusApiEnabled() : Boolean.FALSE);
        link.setStatusApiUrl(StringUtils.hasText(patch.getStatusApiUrl()) ? patch.getStatusApiUrl().trim() : null);
        InfraExternalLink saved = repo.save(link);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", "更新外部平台入口配置");
        payload.put("entryKey", normalizedKey);
        payload.put("enabled", saved.getEnabled());
        payload.put("url", saved.getUrl());
        payload.put("statusApiEnabled", saved.getStatusApiEnabled());
        payload.put("statusApiUrl", saved.getStatusApiUrl());
        auditService.auditAction("INFRA_EXTERNAL_LINK_EDIT", AuditStage.SUCCESS, normalizedKey, payload);
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/{entryKey}")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable String entryKey) {
        String normalizedKey = StringUtils.hasText(entryKey) ? entryKey.trim() : "";
        if (!normalizedKey.isEmpty()) {
            repo.findByEntryKeyIgnoreCase(normalizedKey).ifPresent(repo::delete);
            auditService.auditAction(
                "INFRA_EXTERNAL_LINK_DELETE",
                AuditStage.SUCCESS,
                normalizedKey,
                Map.of("summary", "删除外部平台入口配置", "entryKey", normalizedKey)
            );
        }
        return ApiResponses.ok(Boolean.TRUE);
    }

    private boolean isHttpUrl(String url) {
        if (!StringUtils.hasText(url)) return false;
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private boolean isSameHost(String urlA, String urlB) {
        try {
            URI a = URI.create(urlA);
            URI b = URI.create(urlB);
            String hostA = a.getHost();
            String hostB = b.getHost();
            if (!StringUtils.hasText(hostA) || !StringUtils.hasText(hostB)) return false;
            return hostA.equalsIgnoreCase(hostB);
        } catch (Exception ex) {
            return false;
        }
    }
}
