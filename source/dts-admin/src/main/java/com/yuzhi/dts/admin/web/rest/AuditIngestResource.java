package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.audit.AdminAuditOperation;
import com.yuzhi.dts.admin.service.audit.AdminAuditService;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditRecordBuilder;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditResult;
import com.yuzhi.dts.admin.service.audit.AdminAuditService.AuditTarget;
import com.yuzhi.dts.admin.service.audit.AuditOperationType;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 平台侧推送审计事件的接收端。接收到的数据会转换为统一的管理端审计事件格式。
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditIngestResource {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestResource.class);

    private final AdminAuditService auditService;

    public AuditIngestResource(AdminAuditService auditService) {
        this.auditService = Objects.requireNonNull(auditService, "auditService required");
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            Map<String, Object> detail = new LinkedHashMap<>(body);
            String sourceSystem = asText(body.get("sourceSystem"));
            String actor = resolveActor(body);
            String actorRole = resolveActorRole(body);
            String actionCode = defaultString(asText(body.get("operationCode")), asText(body.get("action")));
            if (StringUtils.isBlank(actionCode)) {
                actionCode = "PLATFORM_EVENT";
            }
            String summary = asText(body.get("summary"));
            String module = asText(body.get("module"));
            String actorName = asText(body.get("actorName"));
            Instant occurredAt = parseInstant(asText(body.get("occurredAt")));
            String resourceId = defaultString(asText(body.get("resourceId")), asText(body.get("targetRef")));
            List<String> targetIds = extractTargetIds(body, resourceId);
            String targetTable = asText(body.get("targetTable"));
            String clientIp = resolveClientIp(body, request);
            String clientAgent = resolveClientAgent(body, request);

            AuditRecordBuilder builder = auditService
                .builder()
                .actor(actor)
                .actorName(StringUtils.isNotBlank(actorName) ? actorName : null)
                .actorRole(actorRole)
                .sourceSystem(StringUtils.isNotBlank(sourceSystem) ? sourceSystem : "platform")
                .occurredAt(occurredAt)
                .clientIp(clientIp)
                .clientAgent(clientAgent)
                .details(detail);

            AdminAuditOperation.fromCode(actionCode).ifPresent(builder::fromOperation);
            if (StringUtils.isNotBlank(module)) {
                builder.module(module);
            }

            AuditOperationType opType = resolveOperationType(body, actionCode);
            builder.operationType(opType);
            builder.operationTypeText(opType.getDisplayName());

            if (!targetIds.isEmpty() || StringUtils.isNotBlank(targetTable)) {
                builder.target(new AuditTarget(targetTable, targetIds, Map.of(), Map.of()));
            }

            String resultRaw = asText(body.get("result"));
            builder.result(parseResult(resultRaw));

            if (StringUtils.isNotBlank(summary)) {
                builder.summary(summary);
            } else if (!targetIds.isEmpty()) {
                builder.summary(actionCode + "：" + String.join(",", targetIds));
            } else {
                builder.summary(actionCode);
            }

            auditService.record(builder.build());
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            log.warn("Failed to ingest audit event from platform: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private AuditOperationType resolveOperationType(Map<String, Object> body, String actionCode) {
        String explicit = firstNonBlank(
            asText(body.get("operationType")),
            asText(body.get("operation_type")),
            asText(body.get("operationTypeCode"))
        );
        if (StringUtils.isNotBlank(explicit)) {
            return mapOperationType(explicit);
        }
        if (StringUtils.isNotBlank(actionCode)) {
            return mapOperationType(actionCode);
        }
        return AuditOperationType.READ;
    }

    private AuditOperationType mapOperationType(String token) {
        if (StringUtils.isBlank(token)) {
            return AuditOperationType.READ;
        }
        String normalized = token.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return AuditOperationType.READ;
        }
        String lower = token.trim().toLowerCase(Locale.ROOT);
        if (
            matches(normalized, "CREATE", "ADD", "NEW", "INSERT", "REGISTER", "SUBMIT") ||
            containsAny(lower, "新增", "新建", "创建", "提交", "申请", "导入", "上传")
        ) {
            return AuditOperationType.CREATE;
        }
        if (matches(normalized, "DELETE", "REMOVE", "DESTROY", "DROP") || containsAny(lower, "删除", "移除", "下线", "注销")) {
            return AuditOperationType.DELETE;
        }
        if (
            matches(normalized, "READ", "GET", "LIST", "QUERY", "SEARCH", "FETCH", "VIEW", "PREVIEW", "DOWNLOAD", "EXPORT", "EXECUTE", "RUN", "TEST", "SYNC") ||
            containsAny(lower, "查看", "查询", "预览", "下载", "导出", "浏览", "列表", "检索")
        ) {
            return AuditOperationType.READ;
        }
        if (
            matches(normalized, "UPDATE", "MODIFY", "EDIT", "SAVE", "ENABLE", "DISABLE", "PUBLISH", "UNPUBLISH", "APPROVE", "REJECT", "GRANT", "REVOKE") ||
            containsAny(lower, "修改", "更新", "调整", "批准", "审批", "拒绝", "执行", "运行", "启用", "禁用", "发布", "保存", "编辑")
        ) {
            return AuditOperationType.UPDATE;
        }
        return AuditOperationType.UPDATE;
    }

    private boolean matches(String actual, String... candidates) {
        for (String candidate : candidates) {
            if (actual.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String source, String... phrases) {
        if (StringUtils.isBlank(source) || phrases == null) {
            return false;
        }
        for (String phrase : phrases) {
            if (phrase != null && source.contains(phrase)) {
                return true;
            }
        }
        return false;
    }

    private static String asText(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String defaultString(String value, String fallback) {
        return StringUtils.isNotBlank(value) ? value : fallback;
    }

    private Instant parseInstant(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private AuditResult parseResult(String value) {
        if (StringUtils.isBlank(value)) {
            return AuditResult.SUCCESS;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "success", "ok", "succeeded", "通过" -> AuditResult.SUCCESS;
            case "pending", "processing", "处理中" -> AuditResult.PENDING;
            case "fail", "failed", "error", "异常", "拒绝" -> AuditResult.FAILED;
            default -> AuditResult.SUCCESS;
        };
    }

    private List<String> extractTargetIds(Map<String, Object> body, String fallbackId) {
        List<String> ids = new ArrayList<>();
        mergeIds(ids, body.get("targetIds"));
        mergeIds(ids, body.get("targetId"));
        mergeIds(ids, body.get("resourceIds"));
        mergeIds(ids, body.get("resourceId"));
        mergeIds(ids, body.get("targetRef"));

        if (ids.isEmpty() && isMeaningfulId(fallbackId)) {
            ids.add(fallbackId.trim());
        }
        if (ids.isEmpty()) {
            mergeIds(ids, body.get("username"));
        }
        return ids.stream().map(String::trim).filter(StringUtils::isNotBlank).distinct().collect(Collectors.toList());
    }

    private void mergeIds(Collection<String> ids, Object candidate) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof Collection<?> collection) {
            for (Object item : collection) {
                mergeIds(ids, item);
            }
            return;
        }
        if (candidate.getClass().isArray()) {
            int length = Array.getLength(candidate);
            for (int i = 0; i < length; i++) {
                mergeIds(ids, Array.get(candidate, i));
            }
            return;
        }
        String text = candidate.toString();
        if (StringUtils.isBlank(text)) {
            return;
        }
        if (text.contains(",")) {
            for (String part : text.split(",")) {
                mergeIds(ids, part);
            }
            return;
        }
        if (isMeaningfulId(text)) {
            ids.add(text.trim());
        }
    }

    private boolean isMeaningfulId(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }
        String trimmed = value.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !List.of("list", "search", "sync", "pending", "-", "demo").contains(lower);
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
            asText(body.get("account"))
        );
        if (StringUtils.isNotBlank(direct) && !isAnonymous(direct)) {
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
            if (StringUtils.isNotBlank(fromPayload) && !isAnonymous(fromPayload)) {
                return fromPayload.trim();
            }
        }
        String fallback = firstNonBlank(
            asText(body.get("userId")),
            asText(body.get("resourceId")),
            asText(body.get("targetRef")),
            asText(body.get("operatorId"))
        );
        if (StringUtils.isNotBlank(fallback) && !isAnonymous(fallback)) {
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
        if (StringUtils.isNotBlank(role)) {
            return role.trim();
        }
        Object payload = body.get("payload");
        if (payload instanceof Map<?, ?> map) {
            String fromPayload = firstNonBlank(
                asText(map.get("actorRole")),
                asText(map.get("operatorRole")),
                asText(map.get("role"))
            );
            if (StringUtils.isNotBlank(fromPayload)) {
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
            if (StringUtils.isNotBlank(value) && !isAnonymous(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isAnonymous(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        String norm = value.trim().toLowerCase(Locale.ROOT);
        return "anonymous".equals(norm)
            || "anonymoususer".equals(norm)
            || "unknown".equals(norm)
            || "null".equals(norm)
            || "-".equals(norm);
    }

    private String resolveClientIp(Map<String, Object> body, HttpServletRequest request) {
        String fromBody = asText(body.get("clientIp"));
        String forwarded = request.getHeader("X-Forwarded-For");
        String realIp = request.getHeader("X-Real-IP");
        String remote = request.getRemoteAddr();
        return IpAddressUtils.resolveClientIp(fromBody, forwarded, realIp, remote);
    }

    private String resolveClientAgent(Map<String, Object> body, HttpServletRequest request) {
        String agent = asText(body.get("clientAgent"));
        if (StringUtils.isNotBlank(agent)) {
            return agent.trim();
        }
        String header = request.getHeader("User-Agent");
        return StringUtils.isNotBlank(header) ? header.trim() : null;
    }
}
