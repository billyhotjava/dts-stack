package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditTrailService;
import org.springframework.beans.factory.ObjectProvider;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditLogResource {

    private static final Logger log = LoggerFactory.getLogger(AuditLogResource.class);

    private final ObjectProvider<AuditTrailService> auditServiceProvider;
    private final ObjectMapper objectMapper;

    public AuditLogResource(ObjectProvider<AuditTrailService> auditServiceProvider, ObjectMapper objectMapper) {
        this.auditServiceProvider = auditServiceProvider;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN')")
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "occurredAt,desc") String sort
    ) {
        AuditTrailService auditService = auditServiceProvider.getIfAvailable();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200), parseSort(sort));
        Page<AuditEvent> eventPage = auditService != null ? auditService.find(pageable) : Page.empty(pageable);
        List<Map<String, Object>> content = eventPage.getContent().stream().map(this::toView).toList();
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        payload.put("page", eventPage.getNumber());
        payload.put("size", eventPage.getSize());
        payload.put("totalElements", eventPage.getTotalElements());
        payload.put("totalPages", eventPage.getTotalPages());
        return ApiResponses.ok(payload);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@PathVariable Long id) {
        AuditTrailService auditService = auditServiceProvider.getIfAvailable();
        if (auditService == null) {
            return ResponseEntity.notFound().build();
        }
        return auditService
            .findById(id)
            .map(this::toView)
            .map(ApiResponses::ok)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN')")
    public ResponseEntity<byte[]> export() {
        AuditTrailService auditService = auditServiceProvider.getIfAvailable();
        List<AuditEvent> events = auditService != null ? auditService.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")) : List.of();
        StringBuilder sb = new StringBuilder();
        // Append Chinese-readable columns while keeping raw fields
        sb.append("id,timestamp,module,action,actor,result,resource,clientIp,来源系统,事件类型,结果中文,目标表,目标ID,摘要\n");
        for (AuditEvent event : events) {
            Map<String, String> details = parseDetails(event);
            String targetTable = details.getOrDefault("target_table", "");
            String targetId = details.getOrDefault("target_id", "");
            String sourceText = mapSourceSystemText(event.getSourceSystem());
            String resultText = mapResultText(event.getResult());
            String eventType = event.getEventType();
            sb
                .append(event.getId()).append(',')
                .append(event.getOccurredAt()).append(',')
                .append(escape(event.getModule())).append(',')
                .append(escape(event.getAction())).append(',')
                .append(escape(event.getActor())).append(',')
                .append(escape(event.getResult())).append(',')
                .append(escape(event.getResourceId())).append(',')
                .append(escape(event.getClientIp())).append(',')
                .append(escape(sourceText)).append(',')
                .append(escape(eventType)).append(',')
                .append(escape(resultText)).append(',')
                .append(escape(targetTable)).append(',')
                .append(escape(targetId)).append(',')
                .append(escape(event.getSummary()))
                .append('\n');
        }
        return ResponseEntity
            .ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audits.csv")
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN')")
    public ApiResponse<Map<String, Object>> purge() {
        AuditTrailService auditService = auditServiceProvider.getIfAvailable();
        long removed = auditService != null ? auditService.purgeAll() : 0L;
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        if (auditService != null) {
            auditService.record("AUDIT_PURGE", "audit", "audit", "ALL", "SUCCESS", Map.of("removed", removed, "actor", actor));
        }
        return ApiResponses.ok(Map.of("removed", removed));
    }

    private Map<String, Object> toView(AuditEvent event) {
        Map<String, Object> view = new HashMap<>();
        view.put("id", event.getId());
        view.put("occurredAt", event.getOccurredAt());
        view.put("actor", event.getActor());
        view.put("actorRole", event.getActorRole());
        view.put("module", event.getModule());
        view.put("action", event.getAction());
        view.put("resourceType", event.getResourceType());
        view.put("resourceId", event.getResourceId());
        view.put("clientIp", event.getClientIp());
        view.put("clientAgent", event.getClientAgent());
        view.put("requestUri", event.getRequestUri());
        view.put("httpMethod", event.getHttpMethod());
        view.put("result", event.getResult());
        view.put("latencyMs", event.getLatencyMs());
        view.put("extraTags", event.getExtraTags());
        view.put("payloadPreview", decodePayloadPreview(event));
        return view;
    }

    private String decodePayloadPreview(AuditEvent event) {
        AuditTrailService auditService = auditServiceProvider.getIfAvailable();
        if (auditService == null) {
            return null;
        }
        try {
            byte[] decrypted = auditService.decryptPayload(event);
            if (decrypted.length == 0) {
                return null;
            }
            Map<?, ?> map = objectMapper.readValue(decrypted, Map.class);
            return map
                .entrySet()
                .stream()
                .limit(5)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        } catch (Exception ex) {
            log.warn("Failed to decode audit payload for id {}", event.getId(), ex);
            return null;
        }
    }

    private Sort parseSort(String sortExpression) {
        if (!StringUtils.hasText(sortExpression)) {
            return Sort.by(Sort.Direction.DESC, "occurredAt");
        }
        String[] parts = sortExpression.split(",");
        if (parts.length != 2) {
            return Sort.by(Sort.Direction.DESC, "occurredAt");
        }
        Sort.Direction direction = "asc".equalsIgnoreCase(parts[1]) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, parts[0]);
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private Map<String, String> parseDetails(AuditEvent event) {
        try {
            if (event.getDetails() == null || event.getDetails().isBlank()) return Map.of();
            Map<?, ?> raw = objectMapper.readValue(event.getDetails(), Map.class);
            Map<String, String> out = new HashMap<>();
            Object t = raw.get("target_table");
            Object i = raw.get("target_id");
            if (t != null) out.put("target_table", String.valueOf(t));
            if (i != null) out.put("target_id", String.valueOf(i));
            return out;
        } catch (Exception ignore) {
            return Map.of();
        }
    }

    private String mapSourceSystemText(String source) {
        if (source == null || source.isBlank()) return "";
        String s = source.trim().toLowerCase();
        if (s.equals("admin") || s.equals("management") || s.equals("manager")) return "管理端";
        if (s.equals("platform")) return "业务端";
        return source;
    }

    private String mapResultText(String result) {
        if (result == null) return "";
        String r = result.trim().toUpperCase();
        if ("SUCCESS".equals(r)) return "成功";
        if ("FAILED".equals(r) || "FAILURE".equals(r)) return "失败";
        return result;
    }
}
