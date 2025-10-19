package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.PendingAuditEvent;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public AuditIngestResource(AdminAuditService auditService) {
        this.auditService = auditService;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            PendingAuditEvent event = new PendingAuditEvent();
            String source = asText(body.get("sourceSystem"));
            event.sourceSystem = StringUtils.hasText(source) ? source : "platform";
            Object occurredAt = body.get("occurredAt");
            if (occurredAt instanceof String s && !s.isBlank()) {
                try { event.occurredAt = Instant.parse(s.trim()); } catch (Exception ignore) { event.occurredAt = Instant.now(); }
            } else {
                event.occurredAt = Instant.now();
            }
            event.actor = resolveActor(body);
            event.actorRole = resolveActorRole(body);
            event.action = defaultString(asText(body.get("action")), "UNKNOWN");
            event.module = defaultString(asText(body.get("module")), "GENERAL");
            String resourceType = asText(body.get("resourceType"));
            if (!StringUtils.hasText(resourceType)) {
                resourceType = asText(body.get("targetKind"));
            }
            event.resourceType = resourceType;
            String resourceId = asText(body.get("resourceId"));
            if (!StringUtils.hasText(resourceId)) {
                resourceId = asText(body.get("targetRef"));
            }
            event.resourceId = resourceId;
            String result = asText(body.get("result"));
            event.result = StringUtils.hasText(result) ? result : "SUCCESS";
            String httpMethod = asText(body.get("httpMethod"));
            if (StringUtils.hasText(httpMethod)) {
                event.httpMethod = httpMethod.trim().toUpperCase(Locale.ROOT);
            }
            String requestUriBody = asText(body.get("requestUri"));
            if (StringUtils.hasText(requestUriBody)) {
                event.requestUri = requestUriBody.trim();
            }
            Object latency = body.get("latencyMs");
            Integer latencyMs = parseLatency(latency);
            if (latencyMs != null) {
                event.latencyMs = latencyMs;
            }
            // Client IP: prefer body-provided clientIp (origin browser IP) then forwarded headers
            String bodyIp = asText(body.get("clientIp"));
            String forwarded = request.getHeader("X-Forwarded-For");
            String xfip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : null;
            String realIp = request.getHeader("X-Real-IP");
            String remote = request.getRemoteAddr();
            event.clientIp = IpAddressUtils.resolveClientIp(bodyIp, xfip, realIp, remote);
            String agent = asText(body.get("clientAgent"));
            event.clientAgent = StringUtils.hasText(agent) ? agent : request.getHeader("User-Agent");
            event.extraTags = asText(body.get("extraTags"));
            Object payload = body.get("payload");
            event.payload = payload != null ? payload : body;
            auditService.record(event);
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            log.warn("Failed to ingest audit event from platform: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private static String asText(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String resolveActor(Map<String, Object> body) {
        String direct = firstNonBlank(
            asText(body.get("actor")),
            asText(body.get("username")),
            asText(body.get("user")),
            asText(body.get("operator")),
            asText(body.get("principal")),
            asText(body.get("operatorId")),
            asText(body.get("operatorCode")),
            asText(body.get("subject")),
            asText(body.get("account")),
            asText(body.get("resourceId")),
            asText(body.get("targetRef"))
        );
        if (StringUtils.hasText(direct) && !isAnonymous(direct)) {
            return direct.trim();
        }
        Object payload = body.get("payload");
        if (payload instanceof Map<?, ?> map) {
            String fromPayload = firstNonBlank(
                asText(map.get("actor")),
                asText(map.get("username")),
                asText(map.get("operator")),
                asText(map.get("user")),
                asText(map.get("principal")),
                asText(map.get("resourceId")),
                asText(map.get("targetRef"))
            );
            if (StringUtils.hasText(fromPayload) && !isAnonymous(fromPayload)) {
                return fromPayload.trim();
            }
        }
        String fallback = firstNonBlank(
            asText(body.get("userId")),
            asText(body.get("resourceId")),
            asText(body.get("targetRef"))
        );
        if (StringUtils.hasText(fallback) && !isAnonymous(fallback)) {
            return fallback.trim();
        }
        return "unknown";
    }

    private String resolveActorRole(Map<String, Object> body) {
        String role = firstNonBlank(
            asText(body.get("actorRole")),
            asText(body.get("operatorRole")),
            asText(body.get("role"))
        );
        if (StringUtils.hasText(role)) {
            return role.trim();
        }
        Object payload = body.get("payload");
        if (payload instanceof Map<?, ?> map) {
            String fromPayload = firstNonBlank(
                asText(map.get("actorRole")),
                asText(map.get("operatorRole")),
                asText(map.get("role"))
            );
            if (StringUtils.hasText(fromPayload)) {
                return fromPayload.trim();
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value) && !isAnonymous(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isAnonymous(String value) {
        if (!StringUtils.hasText(value)) {
            return true;
        }
        String norm = value.trim().toLowerCase(Locale.ROOT);
        return "anonymous".equals(norm)
            || "anonymoususer".equals(norm)
            || "unknown".equals(norm)
            || "null".equals(norm)
            || "-".equals(norm);
    }

    private Integer parseLatency(Object latency) {
        if (latency == null) {
            return null;
        }
        if (latency instanceof Number number) {
            return number.intValue();
        }
        if (latency instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }
}
