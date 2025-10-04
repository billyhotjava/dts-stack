package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditTrailService;
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

    private final AuditTrailService auditService;
    private final ObjectMapper objectMapper;

    public AuditLogResource(AuditTrailService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN')")
    public ApiResponse<Map<String, Object>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "occurredAt,desc") String sort
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200), parseSort(sort));
        Page<AuditEvent> eventPage = auditService.find(pageable);
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
        List<AuditEvent> events = auditService.findAll(Sort.by(Sort.Direction.DESC, "occurredAt"));
        StringBuilder sb = new StringBuilder();
        sb.append("id,timestamp,module,action,actor,result,resource,clientIp\n");
        for (AuditEvent event : events) {
            sb
                .append(event.getId()).append(',')
                .append(event.getOccurredAt()).append(',')
                .append(escape(event.getModule())).append(',')
                .append(escape(event.getAction())).append(',')
                .append(escape(event.getActor())).append(',')
                .append(escape(event.getResult())).append(',')
                .append(escape(event.getResourceId())).append(',')
                .append(escape(event.getClientIp()))
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
        long removed = auditService.purgeAll();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.record("AUDIT_PURGE", "audit", "audit", "ALL", "SUCCESS", Map.of("removed", removed, "actor", actor));
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
}
