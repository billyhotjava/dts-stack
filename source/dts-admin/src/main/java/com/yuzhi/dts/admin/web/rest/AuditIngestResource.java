package com.yuzhi.dts.admin.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.PendingAuditEvent;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives forwarded audit events from platform and persists them into admin store
 * so that all logs can be viewed centrally on the admin side.
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditIngestResource {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestResource.class);

    private final AdminAuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${dts.common.audit.ingest-token:${DTS_COMMON_AUDIT_TOKEN:}}")
    private String ingestToken;

    public AuditIngestResource(AdminAuditService auditService, ObjectMapper objectMapper) {
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        // Simple shared-secret validation (header Authorization: Bearer <token> or X-Audit-Token)
        if (!validToken(request)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            PendingAuditEvent event = new PendingAuditEvent();
            event.sourceSystem = "platform";
            Object occurredAt = body.get("occurredAt");
            if (occurredAt instanceof String s && !s.isBlank()) {
                try { event.occurredAt = Instant.parse(s.trim()); } catch (Exception ignore) { event.occurredAt = Instant.now(); }
            } else {
                event.occurredAt = Instant.now();
            }
            event.actor = asText(body.get("actor"));
            if (event.actor == null || event.actor.isBlank() || "anonymous".equalsIgnoreCase(event.actor) || "anonymoususer".equalsIgnoreCase(event.actor)) {
                // Closed system: ignore anonymous/anonymousUser forwarded events to keep store clean
                return ResponseEntity.accepted().build();
            }
            event.action = asText(body.get("action"));
            event.module = asText(body.get("module"));
            event.resourceType = asText(body.get("targetKind"));
            event.resourceId = asText(body.get("targetRef"));
            event.result = asText(body.get("result"));
            event.httpMethod = asText(body.get("httpMethod"));
            event.requestUri = asText(body.get("requestUri"));
            // Client IP: prefer body-provided clientIp (origin browser IP) then forwarded headers
            String bodyIp = asText(body.get("clientIp"));
            String forwarded = request.getHeader("X-Forwarded-For");
            String xfip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : null;
            String realIp = request.getHeader("X-Real-IP");
            String remote = request.getRemoteAddr();
            event.clientIp = IpAddressUtils.resolveClientIp(bodyIp, xfip, realIp, remote);
            event.clientAgent = request.getHeader("User-Agent");
            // Keep raw payload (if client sends extra fields)
            try { event.payload = objectMapper.writeValueAsBytes(body); } catch (Exception ignore) {}
            auditService.record(event);
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            log.warn("Failed to ingest audit event from platform: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private boolean validToken(HttpServletRequest request) {
        // Closed-network default: if no token configured, accept
        if (!StringUtils.hasText(ingestToken)) {
            return true;
        }
        String auth = request.getHeader("Authorization");
        if (StringUtils.hasText(auth) && auth.startsWith("Bearer ")) {
            String token = auth.substring(7).trim();
            if (ingestToken.equals(token)) return true;
        }
        String header = request.getHeader("X-Audit-Token");
        return StringUtils.hasText(header) && ingestToken.equals(header.trim());
    }

    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

}
