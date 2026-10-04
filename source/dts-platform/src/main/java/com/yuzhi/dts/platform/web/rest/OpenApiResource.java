package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.services.SvcApiQueryService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public data query APIs (token-auth, JSON/XML) for technical agreement 5.4.
 * Note: This endpoint is intentionally outside /api/** so it can be exposed with dedicated token auth.
 */
@RestController
@RequestMapping("/openapi")
@Transactional
public class OpenApiResource {

    private final SvcTokenAuthService tokenAuthService;
    private final SvcApiQueryService apiQueryService;
    private final AuditService auditService;

    public OpenApiResource(SvcTokenAuthService tokenAuthService, SvcApiQueryService apiQueryService, AuditService auditService) {
        this.tokenAuthService = tokenAuthService;
        this.apiQueryService = apiQueryService;
        this.auditService = auditService;
    }

    @PostMapping("/{code}")
    public ResponseEntity<?> query(
        @PathVariable("code") String code,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(value = "X-Access-Token", required = false) String accessToken,
        @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
        HttpServletRequest request
    ) {
        String apiCode = normalizeCode(code);
        String token = resolveToken(authorization, accessToken, request);
        TokenPrincipal principal = tokenAuthService.authenticate(token);
        if (principal == null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "非法令牌调用API服务");
            payload.put("username", "anonymous");
            payload.put("apiCode", apiCode);
            auditService.auditAction("SERVICE_API_EXECUTE", AuditStage.FAIL, apiCode, payload);
            return ResponseEntity.status(401).body(ApiResponses.error("无效令牌或令牌已过期"));
        }

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "调用API服务");
        auditPayload.put("username", principal.username());
        auditPayload.put("apiCode", apiCode);
        auditPayload.put("deptCode", principal.deptCode());
        auditPayload.put("personnelLevel", principal.personnelLevel().name());

        try {
            SvcApiQueryService.QueryResult result = apiQueryService.query(apiCode, body != null ? body : Map.of(), principal);
            apiQueryService.recordSuccess(result.apiId(), result.maskedColumns());
            auditPayload.put("maskedColumns", result.maskedColumns());
            auditService.auditAction("SERVICE_API_EXECUTE", AuditStage.SUCCESS, apiCode, auditPayload);
            return buildResponse(accept, result.payload());
        } catch (RuntimeException ex) {
            auditPayload.put("error", ex.getMessage());
            auditService.auditAction("SERVICE_API_EXECUTE", AuditStage.FAIL, apiCode, auditPayload);
            return ResponseEntity.badRequest().body(ApiResponses.error(ex.getMessage()));
        }
    }

    @PostMapping("/{code}/batch")
    public ResponseEntity<?> batch(
        @PathVariable("code") String code,
        @RequestBody(required = false) List<Map<String, Object>> batch,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(value = "X-Access-Token", required = false) String accessToken,
        HttpServletRequest request
    ) {
        String apiCode = normalizeCode(code);
        String token = resolveToken(authorization, accessToken, request);
        TokenPrincipal principal = tokenAuthService.authenticate(token);
        if (principal == null) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("summary", "非法令牌批量调用API服务");
            payload.put("username", "anonymous");
            payload.put("apiCode", apiCode);
            auditService.auditAction("SERVICE_API_BATCH_EXECUTE", AuditStage.FAIL, apiCode, payload);
            return ResponseEntity.status(401).body(ApiResponses.error("无效令牌或令牌已过期"));
        }
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "批量调用API服务");
        auditPayload.put("username", principal.username());
        auditPayload.put("apiCode", apiCode);
        auditPayload.put("deptCode", principal.deptCode());
        auditPayload.put("personnelLevel", principal.personnelLevel().name());
        auditPayload.put("batchCount", batch != null ? batch.size() : 0);

        try {
            Map<String, Object> payload = apiQueryService.batchQuery(apiCode, batch, principal);
            auditService.auditAction("SERVICE_API_BATCH_EXECUTE", AuditStage.SUCCESS, apiCode, auditPayload);
            return ResponseEntity.ok(ApiResponses.ok(payload));
        } catch (RuntimeException ex) {
            auditPayload.put("error", ex.getMessage());
            auditService.auditAction("SERVICE_API_BATCH_EXECUTE", AuditStage.FAIL, apiCode, auditPayload);
            return ResponseEntity.badRequest().body(ApiResponses.error(ex.getMessage()));
        }
    }

    @GetMapping("/{code}/health")
    public ResponseEntity<?> health(@PathVariable("code") String code) {
        // Intentionally does not check token; used for connectivity check by callers.
        return ResponseEntity.ok(Map.of("status", "OK", "apiCode", normalizeCode(code)));
    }

    private ResponseEntity<?> buildResponse(String acceptHeader, Map<String, Object> payload) {
        if (StringUtils.hasText(acceptHeader) && acceptHeader.toLowerCase().contains("application/xml")) {
            String xml = toSimpleXml(payload);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(xml);
        }
        return ResponseEntity.ok(ApiResponses.ok(payload));
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    private String resolveToken(String authorization, String accessToken, HttpServletRequest request) {
        if (StringUtils.hasText(accessToken)) {
            return accessToken.trim();
        }
        if (StringUtils.hasText(authorization)) {
            String trimmed = authorization.trim();
            if (trimmed.toLowerCase().startsWith("bearer ")) {
                return trimmed.substring("bearer ".length()).trim();
            }
            return trimmed;
        }
        if (request != null) {
            String q = request.getParameter("token");
            if (StringUtils.hasText(q)) {
                return q.trim();
            }
        }
        return null;
    }

    private String toSimpleXml(Map<String, Object> payload) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<response>");
        if (payload != null) {
            sb.append("<code>").append(escapeXml(String.valueOf(payload.getOrDefault("code", "")))).append("</code>");
            sb.append("<name>").append(escapeXml(String.valueOf(payload.getOrDefault("name", "")))).append("</name>");
            Object rows = payload.get("rows");
            if (rows instanceof List<?> list) {
                sb.append("<rows>");
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> row)) continue;
                    sb.append("<row>");
                    for (Map.Entry<?, ?> e : row.entrySet()) {
                        if (e.getKey() == null) continue;
                        String k = String.valueOf(e.getKey());
                        sb.append("<col name=\"").append(escapeXml(k)).append("\">");
                        sb.append(escapeXml(e.getValue() != null ? String.valueOf(e.getValue()) : ""));
                        sb.append("</col>");
                    }
                    sb.append("</row>");
                }
                sb.append("</rows>");
            }
        }
        sb.append("</response>");
        return sb.toString();
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}

