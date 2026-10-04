package com.yuzhi.dts.copilot.analytics.web.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsScreenAuditLog;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsScreenAuditLogRepository;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.ScreenComplianceService;
import com.yuzhi.dts.copilot.analytics.web.support.MetabaseAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/screen-compliance")
@Transactional
public class ScreenComplianceResource {

    private final AnalyticsSessionService sessionService;
    private final AnalyticsScreenAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final ScreenComplianceService screenComplianceService;

    public ScreenComplianceResource(
            AnalyticsSessionService sessionService,
            AnalyticsScreenAuditLogRepository auditLogRepository,
            ObjectMapper objectMapper,
            ScreenComplianceService screenComplianceService) {
        this.sessionService = sessionService;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
        this.screenComplianceService = screenComplianceService;
    }

    @GetMapping(path = "/policy", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPolicy(HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        return ResponseEntity.ok(screenComplianceService.currentPolicy());
    }

    @PutMapping(path = "/policy", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePolicy(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }

        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("invalid policy payload");
        }

        return ResponseEntity.ok(screenComplianceService.updatePolicy(body, String.valueOf(user.get().getId())));
    }

    @GetMapping(path = "/policy/history", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<?> policyHistory(
            @RequestParam(value = "limit", required = false, defaultValue = "50") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        return ResponseEntity.ok(screenComplianceService.history(limit));
    }

    @PostMapping(path = "/policy/rollback", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> rollbackPolicy(@RequestBody(required = false) JsonNode body, HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }
        if (!user.get().isSuperuser()) {
            return ResponseEntity.status(403).contentType(MediaType.TEXT_PLAIN).body("Forbidden");
        }
        if (body == null || !body.isObject()) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("invalid rollback payload");
        }
        int version = body.path("version").asInt(0);
        if (version <= 0) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body("version must be positive");
        }
        try {
            return ResponseEntity.ok(screenComplianceService.rollbackToVersion(version, String.valueOf(user.get().getId())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).contentType(MediaType.TEXT_PLAIN).body(e.getMessage());
        }
    }

    @GetMapping(path = "/report", produces = MediaType.APPLICATION_JSON_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<?> report(
            @RequestParam(value = "screenId", required = false) Long screenId,
            @RequestParam(value = "days", required = false, defaultValue = "30") int days,
            @RequestParam(value = "limit", required = false, defaultValue = "200") int limit,
            HttpServletRequest request) {
        Optional<AnalyticsUser> user = MetabaseAuth.currentUser(sessionService, request);
        if (user.isEmpty()) {
            return ResponseEntity.status(401).contentType(MediaType.TEXT_PLAIN).body("Unauthorized");
        }

        int safeDays = Math.max(1, Math.min(3650, days));
        int safeLimit = Math.max(1, Math.min(1000, limit));
        Instant cutoff = Instant.now().minus(safeDays, ChronoUnit.DAYS);

        List<AnalyticsScreenAuditLog> rows = auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .filter(item -> item.getCreatedAt() != null && !item.getCreatedAt().isBefore(cutoff))
                .filter(item -> screenId == null || screenId.equals(item.getScreenId()))
                .limit(safeLimit)
                .toList();

        Map<String, Integer> actionCount = new LinkedHashMap<>();
        Map<String, Integer> actorCount = new LinkedHashMap<>();

        ArrayNode rowNodes = objectMapper.createArrayNode();
        for (AnalyticsScreenAuditLog row : rows) {
            String action = row.getAction() == null ? "unknown" : row.getAction();
            actionCount.put(action, actionCount.getOrDefault(action, 0) + 1);
            String actor = row.getActorId() == null ? "anonymous" : String.valueOf(row.getActorId());
            actorCount.put(actor, actorCount.getOrDefault(actor, 0) + 1);

            ObjectNode item = objectMapper.createObjectNode();
            item.putPOJO("id", row.getId());
            item.putPOJO("screenId", row.getScreenId());
            item.putPOJO("actorId", row.getActorId());
            item.put("action", action);
            item.put("requestId", row.getRequestId());
            item.putPOJO("createdAt", row.getCreatedAt());
            rowNodes.add(item);
        }

        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("total", rows.size());
        summary.set("actionCount", objectMapper.valueToTree(actionCount));
        summary.set("actorCount", objectMapper.valueToTree(actorCount));

        ObjectNode result = objectMapper.createObjectNode();
        result.putPOJO("generatedAt", Instant.now());
        result.put("scope", screenId == null ? "all" : "screen");
        if (screenId != null) {
            result.putPOJO("screenId", screenId);
        }
        result.put("days", safeDays);
        result.put("limit", safeLimit);
        result.set("policy", screenComplianceService.currentPolicy());
        result.set("summary", summary);
        result.set("rows", rowNodes);
        return ResponseEntity.ok(result);
    }
}
