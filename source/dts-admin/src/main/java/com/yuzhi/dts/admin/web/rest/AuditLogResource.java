package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.security.AuthoritiesConstants;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditEventView;
import com.yuzhi.dts.admin.web.rest.api.ApiResponse;
import com.yuzhi.dts.admin.web.rest.errors.BadRequestAlertException;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasAnyAuthority('" + AuthoritiesConstants.SYS_ADMIN + "','" + AuthoritiesConstants.AUTH_ADMIN + "','" + AuthoritiesConstants.AUDITOR_ADMIN + "')")
public class AuditLogResource {

    private static final Logger log = LoggerFactory.getLogger(AuditLogResource.class);

    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    public AuditLogResource(AdminAuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @RequestParam(value = "sort", defaultValue = "occurredAt,desc") String sort,
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "resource", required = false) String resource,
        @RequestParam(value = "requestUri", required = false) String requestUri,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "clientIp", required = false) String clientIp
    ) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(size, 200), parseSort(sort));
        Instant fromDate = parseInstant(from, "from");
        Instant toDate = parseInstant(to, "to");
        Page<AuditEvent> pageResult = auditService.search(
            actor,
            module,
            action,
            result,
            resourceType,
            resource,
            requestUri,
            fromDate,
            toDate,
            clientIp,
            pageable
        );
        List<AuditEventView> views = pageResult.getContent().stream().map(this::toView).toList();
        Page<AuditEventView> viewPage = new PageImpl<>(views, pageable, pageResult.getTotalElements());
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", viewPage.getContent());
        payload.put("page", viewPage.getNumber());
        payload.put("size", viewPage.getSize());
        payload.put("totalElements", viewPage.getTotalElements());
        payload.put("totalPages", viewPage.getTotalPages());
        return ResponseEntity.ok(ApiResponse.ok(payload));
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    public void export(
        @RequestParam(value = "actor", required = false) String actor,
        @RequestParam(value = "module", required = false) String module,
        @RequestParam(value = "action", required = false) String action,
        @RequestParam(value = "result", required = false) String result,
        @RequestParam(value = "resourceType", required = false) String resourceType,
        @RequestParam(value = "resource", required = false) String resource,
        @RequestParam(value = "requestUri", required = false) String requestUri,
        @RequestParam(value = "from", required = false) String from,
        @RequestParam(value = "to", required = false) String to,
        @RequestParam(value = "clientIp", required = false) String clientIp,
        HttpServletResponse response
    ) throws IOException {
        Instant fromDate = parseInstant(from, "from");
        Instant toDate = parseInstant(to, "to");
        List<AuditEvent> events = auditService.findAllForExport(
            actor,
            module,
            action,
            result,
            resourceType,
            resource,
            requestUri,
            fromDate,
            toDate,
            clientIp
        );
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audits.csv");
        response.setContentType("text/csv;charset=UTF-8");
        response.getOutputStream().write(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AuditEventDetailView>> detail(@PathVariable Long id) {
        AuditEvent event = auditService
            .findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "审计日志不存在"));
        AuditEventDetailView view = toDetailView(event);
        return ResponseEntity.ok(ApiResponse.ok(view));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> purge() {
        long removed = auditService.purgeAll();
        String actor = SecurityUtils.getCurrentUserLogin().orElse("anonymous");
        auditService.record(actor, "AUDIT_PURGE", "AUDIT", "ALL", "SUCCESS", Map.of("removed", removed));
        return ResponseEntity.ok(ApiResponse.ok(Map.of("removed", removed)));
    }

    private AuditEventView toView(AuditEvent event) {
        AuditEventView view = new AuditEventView();
        view.id = event.getId();
        view.occurredAt = event.getOccurredAt();
        view.actor = event.getActor();
        view.actorRole = event.getActorRole();
        view.module = event.getModule();
        view.action = event.getAction();
        view.resourceType = event.getResourceType();
        view.resourceId = event.getResourceId();
        view.clientIp = event.getClientIp();
        view.clientAgent = event.getClientAgent();
        view.requestUri = event.getRequestUri();
        view.httpMethod = event.getHttpMethod();
        view.result = event.getResult();
        view.latencyMs = event.getLatencyMs();
        view.extraTags = event.getExtraTags();
        view.payloadPreview = decodePayloadPreview(event);
        return view;
    }

    private String decodePayloadPreview(AuditEvent event) {
        Object payload = decodePayload(event);
        if (payload == null) {
            return null;
        }
        if (payload instanceof Map<?, ?> map) {
            return map
                .entrySet()
                .stream()
                .limit(5)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("; "));
        }
        if (payload instanceof Collection<?> collection) {
            return collection
                .stream()
                .limit(3)
                .map(Object::toString)
                .collect(Collectors.joining(", "));
        }
        String text = payload.toString();
        return text.length() > 160 ? text.substring(0, 157) + "..." : text;
    }

    private AuditEventDetailView toDetailView(AuditEvent event) {
        AuditEventView base = toView(event);
        AuditEventDetailView detail = new AuditEventDetailView();
        detail.id = base.id;
        detail.occurredAt = base.occurredAt;
        detail.actor = base.actor;
        detail.actorRole = base.actorRole;
        detail.module = base.module;
        detail.action = base.action;
        detail.resourceType = base.resourceType;
        detail.resourceId = base.resourceId;
        detail.clientIp = base.clientIp;
        detail.clientAgent = base.clientAgent;
        detail.requestUri = base.requestUri;
        detail.httpMethod = base.httpMethod;
        detail.result = base.result;
        detail.latencyMs = base.latencyMs;
        detail.extraTags = base.extraTags;
        detail.payloadPreview = base.payloadPreview;
        detail.payload = decodePayload(event);
        return detail;
    }

    private Object decodePayload(AuditEvent event) {
        try {
            byte[] decrypted = auditService.decryptPayload(event);
            if (decrypted.length == 0) {
                return null;
            }
            try {
                return objectMapper.readValue(decrypted, Object.class);
            } catch (Exception parseEx) {
                String fallback = new String(decrypted, StandardCharsets.UTF_8);
                return fallback;
            }
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

    private Instant parseInstant(String value, String parameter) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new BadRequestAlertException("Invalid timestamp for parameter " + parameter, "audit", "invalidTimestamp");
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static final class AuditEventDetailView extends AuditEventView {
        public Object payload;
    }
}
