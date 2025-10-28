package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.auditv2.AuditActionRequest;
import com.yuzhi.dts.admin.service.auditv2.AuditOperationKind;
import com.yuzhi.dts.admin.service.auditv2.AuditResultStatus;
import com.yuzhi.dts.admin.service.auditv2.AuditV2Service;
import com.yuzhi.dts.admin.service.auditv2.ButtonCodes;
import com.yuzhi.dts.common.net.IpAddressUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Array;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
 * 平台侧推送审计事件的接入端，统一写入新的 audit_entry 表。
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditIngestResource {

    private static final Logger log = LoggerFactory.getLogger(AuditIngestResource.class);

    private final AuditV2Service auditV2Service;

    public AuditIngestResource(AuditV2Service auditV2Service) {
        this.auditV2Service = Objects.requireNonNull(auditV2Service, "auditV2Service required");
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            AuditPayload payload = AuditPayload.from(body, request);
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(payload.actor(), payload.buttonCode())
                .occurredAt(payload.occurredAt())
                .actorName(payload.actorName())
                .actorRoles(payload.actorRoles())
                .summary(payload.summary())
                .result(payload.result())
                .changeRequestRef(payload.changeRequestRef())
                .client(payload.clientIp(), payload.clientAgent())
                .request(payload.requestUri(), payload.httpMethod())
                .metadata("sourceSystem", payload.sourceSystem())
                .metadata("moduleKeyRaw", payload.moduleKeyRaw());

            if (StringUtils.isNotBlank(payload.moduleKey())) {
                builder.moduleOverride(payload.moduleKey(), payload.moduleName());
            }

            builder.operationOverride(
                payload.operationCode(),
                payload.operationName(),
                payload.operationKind()
            );

            if (!payload.metadata().isEmpty()) {
                payload
                    .metadata()
                    .forEach((key, value) -> builder.metadata(key, value));
            }
            if (!payload.attributes().isEmpty()) {
                payload
                    .attributes()
                    .forEach((key, value) -> builder.attribute(key, value));
            }

            if (!payload.targets().isEmpty()) {
                payload
                    .targets()
                    .forEach(target -> builder.target(target.table(), target.id(), target.label()));
            } else if (payload.operationKind() == AuditOperationKind.QUERY) {
                builder.allowEmptyTargets();
            }

            if (!payload.details().isEmpty()) {
                builder.detail("payload", payload.details());
            }

            auditV2Service.record(builder.build());
            return ResponseEntity.accepted().build();
        } catch (Exception ex) {
            log.warn("Failed to ingest audit event from platform: {}", ex.getMessage(), ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private record TargetRecord(String table, Object id, String label) {}

    private record AuditPayload(
        String actor,
        String actorName,
        List<String> actorRoles,
        String buttonCode,
        String moduleKey,
        String moduleKeyRaw,
        String moduleName,
        String operationCode,
        String operationName,
        AuditOperationKind operationKind,
        AuditResultStatus result,
        String summary,
        String changeRequestRef,
        String requestUri,
        String httpMethod,
        String clientIp,
        String clientAgent,
        String sourceSystem,
        Instant occurredAt,
        Map<String, Object> metadata,
        Map<String, Object> attributes,
        List<TargetRecord> targets,
        Map<String, Object> details
    ) {
        static AuditPayload from(Map<String, Object> body, HttpServletRequest request) {
            Map<String, Object> sanitizedBody = body == null ? Map.of() : new LinkedHashMap<>(body);
            String sourceSystem = text(sanitizedBody.get("sourceSystem"), "platform");
            String moduleKey = firstNonBlank(
                text(sanitizedBody.get("moduleKey")),
                text(sanitizedBody.get("module")),
                sourceSystem
            );
            String moduleName = firstNonBlank(text(sanitizedBody.get("moduleName")), moduleKey);

            String actor = normalizeActor(sanitizedBody);
            String actorName = text(sanitizedBody.get("actorName"), actor);
            List<String> actorRoles = extractStringList(
                sanitizedBody.get("actorRoles"),
                sanitizedBody.get("operatorRoles"),
                sanitizedBody.get("roles")
            );

            String buttonCode = text(sanitizedBody.get("buttonCode"), ButtonCodes.PLATFORM_GENERIC_EVENT);
            String operationCode = firstNonBlank(
                text(sanitizedBody.get("operationCode")),
                text(sanitizedBody.get("action")),
                buttonCode
            );
            String operationName = firstNonBlank(
                text(sanitizedBody.get("operationName")),
                text(sanitizedBody.get("summary")),
                operationCode
            );
            AuditOperationKind operationKind = resolveKind(
                firstNonBlank(
                    text(sanitizedBody.get("operationType")),
                    text(sanitizedBody.get("operationTypeCode")),
                    text(sanitizedBody.get("operation_type")),
                    text(sanitizedBody.get("httpMethod")),
                    text(sanitizedBody.get("method"))
                ),
                operationCode
            );

            AuditResultStatus result = resolveResult(text(sanitizedBody.get("result")));
            String summary = firstNonBlank(text(sanitizedBody.get("summary")), operationName);
            String changeRequestRef = text(sanitizedBody.get("changeRequestRef"), text(sanitizedBody.get("requestId")));

            String requestUri = firstNonBlank(
                text(sanitizedBody.get("requestUri")),
                text(sanitizedBody.get("uri")),
                text(sanitizedBody.get("path"))
            );
            String httpMethod = firstNonBlank(
                text(sanitizedBody.get("httpMethod")),
                text(sanitizedBody.get("method")),
                "POST"
            );
            String clientIp = resolveClientIp(sanitizedBody, request);
            String clientAgent = resolveClientAgent(sanitizedBody, request);

            Instant occurredAt = parseInstant(text(sanitizedBody.get("occurredAt")));

            Map<String, Object> metadata = new LinkedHashMap<>();
            Map<String, Object> attributes = new LinkedHashMap<>();
            extractMap(sanitizedBody.get("metadata")).forEach((k, v) -> metadata.put(String.valueOf(k), v));
            extractMap(sanitizedBody.get("attributes")).forEach((k, v) -> attributes.put(String.valueOf(k), v));

            List<TargetRecord> targets = resolveTargets(sanitizedBody);
            Map<String, Object> details = sanitizeDetails(sanitizedBody);

            return new AuditPayload(
                actor,
                actorName,
                actorRoles,
                buttonCode,
                moduleKey.toLowerCase(Locale.ROOT),
                moduleKey,
                moduleName,
                operationCode,
                operationName,
                operationKind,
                result,
                summary,
                changeRequestRef,
                requestUri,
                httpMethod,
                clientIp,
                clientAgent,
                sourceSystem,
                occurredAt,
                metadata,
                attributes,
                targets,
                details
            );
        }

        private static List<TargetRecord> resolveTargets(Map<String, Object> body) {
            String targetTable = text(body.get("targetTable"), text(body.get("resourceType")));
            List<Object> rawIds = collectValues(
                body.get("targetIds"),
                body.get("resourceIds"),
                body.get("resourceId"),
                body.get("targetId"),
                body.get("targetRef")
            );
            if (rawIds.isEmpty()) {
                Object payload = body.get("payload");
                if (payload instanceof Map<?, ?> map) {
                    rawIds.addAll(collectValues(
                        map.get("targetIds"),
                        map.get("resourceId"),
                        map.get("targetId")
                    ));
                }
            }
            List<TargetRecord> targets = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Object raw : rawIds) {
                if (raw == null) {
                    continue;
                }
                String id = raw.toString().trim();
                if (id.isEmpty() || !seen.add(id)) {
                    continue;
                }
                targets.add(new TargetRecord(targetTable, id, null));
            }
            return targets;
        }

        private static Map<String, Object> sanitizeDetails(Map<String, Object> body) {
            Map<String, Object> copy = new LinkedHashMap<>(body);
            copy.remove("targetIds");
            copy.remove("targetId");
            copy.remove("resourceIds");
            copy.remove("resourceId");
            copy.remove("targetRef");
            copy.remove("actorRoles");
            copy.remove("role");
            copy.remove("actorRole");
            copy.remove("operatorRoles");
            copy.remove("requestUri");
            copy.remove("httpMethod");
            copy.remove("method");
            copy.remove("clientIp");
            copy.remove("clientAgent");
            copy.remove("occurredAt");
            copy.remove("module");
            copy.remove("moduleKey");
            copy.remove("moduleName");
            copy.remove("operationType");
            copy.remove("operationTypeCode");
            copy.remove("operation_type");
            copy.remove("buttonCode");
            copy.remove("sourceSystem");
            copy.remove("metadata");
            copy.remove("attributes");
            return copy;
        }

        private static Map<String, Object> extractMap(Object value) {
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                map.forEach((k, v) -> result.put(String.valueOf(k), v));
                return result;
            }
            return Map.of();
        }

        private static List<Object> collectValues(Object... sources) {
            List<Object> values = new ArrayList<>();
            if (sources == null) {
                return values;
            }
            for (Object source : sources) {
                merge(values, source);
            }
            return values;
        }

        private static void merge(Collection<Object> values, Object source) {
            if (source == null) {
                return;
            }
            if (source instanceof Collection<?> collection) {
                for (Object item : collection) {
                    merge(values, item);
                }
                return;
            }
            if (source.getClass().isArray()) {
                int length = Array.getLength(source);
                for (int i = 0; i < length; i++) {
                    merge(values, Array.get(source, i));
                }
                return;
            }
            String text = source.toString();
            if (StringUtils.isBlank(text)) {
                return;
            }
            if (text.contains(",")) {
                for (String part : text.split(",")) {
                    merge(values, part.trim());
                }
                return;
            }
            values.add(text.trim());
        }

        private static List<String> extractStringList(Object... sources) {
            List<Object> raw = collectValues(sources);
            List<String> normalized = new ArrayList<>();
            for (Object value : raw) {
                if (value == null) {
                    continue;
                }
                String text = value.toString().trim();
                if (!text.isEmpty()) {
                    normalized.add(text.toUpperCase(Locale.ROOT));
                }
            }
            return List.copyOf(normalized);
        }

        private static String normalizeActor(Map<String, Object> body) {
            List<Object> candidates = collectValues(
                body.get("actor"),
                body.get("username"),
                body.get("user"),
                body.get("operator"),
                body.get("principal"),
                body.get("account")
            );
            Object payload = body.get("payload");
            if (payload instanceof Map<?, ?> map) {
                candidates.addAll(collectValues(
                    map.get("actor"),
                    map.get("username"),
                    map.get("operator"),
                    map.get("user"),
                    map.get("principal")
                ));
            }
            for (Object candidate : candidates) {
                if (candidate == null) continue;
                String text = candidate.toString().trim();
                if (!text.isEmpty() && !isAnonymous(text)) {
                    return text;
                }
            }
            return "platform";
        }

        private static boolean isAnonymous(String value) {
            if (value == null) {
                return true;
            }
            String norm = value.trim().toLowerCase(Locale.ROOT);
            return norm.isEmpty() || norm.equals("anonymous") || norm.equals("anonymoususer") || norm.equals("unknown");
        }

        private static String resolveClientIp(Map<String, Object> body, HttpServletRequest request) {
            String fromBody = text(body.get("clientIp"), text(body.get("ip")));
            String forwarded = request != null ? request.getHeader("X-Forwarded-For") : null;
            String realIp = request != null ? request.getHeader("X-Real-IP") : null;
            String remote = request != null ? request.getRemoteAddr() : null;
            return IpAddressUtils.resolveClientIp(fromBody, forwarded, realIp, remote);
        }

        private static String resolveClientAgent(Map<String, Object> body, HttpServletRequest request) {
            String agent = text(body.get("clientAgent"), text(body.get("userAgent")));
            if (StringUtils.isNotBlank(agent)) {
                return agent.trim();
            }
            if (request == null) {
                return null;
            }
            String header = request.getHeader("User-Agent");
            return StringUtils.isNotBlank(header) ? header.trim() : null;
        }

        private static Instant parseInstant(String raw) {
            if (StringUtils.isBlank(raw)) {
                return Instant.now();
            }
            try {
                return Instant.parse(raw.trim());
            } catch (DateTimeParseException ex) {
                return Instant.now();
            }
        }

        private static AuditOperationKind resolveKind(String explicit, String fallback) {
            if (StringUtils.isNotBlank(explicit)) {
                return mapOperationKind(explicit);
            }
            if (StringUtils.isNotBlank(fallback)) {
                return mapOperationKind(fallback);
            }
            return AuditOperationKind.OTHER;
        }

        private static AuditOperationKind mapOperationKind(String token) {
            if (StringUtils.isBlank(token)) {
                return AuditOperationKind.OTHER;
            }
            String normalized = token.trim().toUpperCase(Locale.ROOT);
            String lower = token.trim().toLowerCase(Locale.ROOT);
            if (
                normalized.startsWith("CREATE") ||
                normalized.startsWith("ADD") ||
                normalized.startsWith("NEW") ||
                containsAny(lower, "新增", "新建", "创建", "提交", "申请", "导入", "上传")
            ) {
                return AuditOperationKind.CREATE;
            }
            if (
                normalized.startsWith("DELETE") ||
                normalized.startsWith("REMOVE") ||
                containsAny(lower, "删除", "移除", "下线", "注销")
            ) {
                return AuditOperationKind.DELETE;
            }
            if (
                normalized.startsWith("UPDATE") ||
                normalized.startsWith("MODIFY") ||
                normalized.startsWith("EDIT") ||
                normalized.startsWith("SAVE") ||
                normalized.startsWith("ENABLE") ||
                normalized.startsWith("DISABLE") ||
                normalized.startsWith("APPROVE") ||
                normalized.startsWith("REJECT") ||
                containsAny(lower, "修改", "批准", "审批", "拒绝", "执行", "启用", "禁用", "发布")
            ) {
                return AuditOperationKind.UPDATE;
            }
            if (
                normalized.startsWith("READ") ||
                normalized.startsWith("GET") ||
                normalized.startsWith("LIST") ||
                normalized.startsWith("QUERY") ||
                normalized.startsWith("SEARCH") ||
                normalized.startsWith("VIEW") ||
                normalized.startsWith("DOWNLOAD") ||
                normalized.startsWith("EXPORT") ||
                containsAny(lower, "查看", "查询", "预览", "下载", "导出", "浏览", "列表")
            ) {
                return AuditOperationKind.QUERY;
            }
            if (normalized.startsWith("LOGIN")) {
                return AuditOperationKind.LOGIN;
            }
            if (normalized.startsWith("LOGOUT")) {
                return AuditOperationKind.LOGOUT;
            }
            return AuditOperationKind.OTHER;
        }

        private static AuditResultStatus resolveResult(String raw) {
            if (StringUtils.isBlank(raw)) {
                return AuditResultStatus.SUCCESS;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "SUCCESS", "SUCCEEDED", "OK", "PASS", "通过" -> AuditResultStatus.SUCCESS;
                case "FAIL", "FAILED", "ERROR", "ERR", "拒绝", "异常" -> AuditResultStatus.FAILED;
                case "PENDING", "PROCESSING", "IN_PROGRESS", "处理中" -> AuditResultStatus.PENDING;
                default -> AuditResultStatus.SUCCESS;
            };
        }

        private static boolean containsAny(String text, String... tokens) {
            if (text == null || tokens == null) {
                return false;
            }
            for (String token : tokens) {
                if (token != null && text.contains(token)) {
                    return true;
                }
            }
            return false;
        }

        private static String firstNonBlank(String... values) {
            if (values == null) {
                return null;
            }
            for (String value : values) {
                if (StringUtils.isNotBlank(value)) {
                    return value.trim();
                }
            }
            return null;
        }

        private static String text(Object value) {
            return value == null ? null : value.toString();
        }

        private static String text(Object value, String fallback) {
            String converted = text(value);
            return StringUtils.isNotBlank(converted) ? converted.trim() : fallback;
        }
    }
}
