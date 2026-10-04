package com.yuzhi.dts.admin.web.rest;

import com.yuzhi.dts.admin.service.audit.AuditActionRequest;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator;
import com.yuzhi.dts.admin.security.AdminInboundServiceAuthenticator.Decision;
import com.yuzhi.dts.admin.service.audit.AuditIngestIdempotencyService;
import com.yuzhi.dts.admin.service.audit.AuditIngestIdempotencyService.IngestResult;
import com.yuzhi.dts.admin.service.audit.AuditOperationKind;
import com.yuzhi.dts.admin.service.audit.AuditResultStatus;
import com.yuzhi.dts.admin.service.audit.ButtonCodes;
import com.yuzhi.dts.admin.config.AuditIngestProperties;
import com.yuzhi.dts.admin.domain.AdminKeycloakUser;
import com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository;
import com.yuzhi.dts.admin.web.filter.AuditIngestPreAuthenticationFilter;
import com.yuzhi.dts.common.audit.AuditPayloadSanitizer;
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
import java.util.function.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.transaction.TransactionException;
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

    private final AuditIngestIdempotencyService idempotencyService;
    private final AdminInboundServiceAuthenticator authenticator;
    private final AdminKeycloakUserRepository userRepository;
    private final List<IpAddressMatcher> containerMatchers;

    public AuditIngestResource(
        AuditIngestIdempotencyService idempotencyService,
        AdminInboundServiceAuthenticator authenticator,
        AdminKeycloakUserRepository userRepository,
        AuditIngestProperties properties
    ) {
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "auditIngestIdempotencyService required");
        this.authenticator = Objects.requireNonNull(authenticator, "adminInboundServiceAuthenticator required");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository required");
        this.containerMatchers = buildContainerMatchers(properties);
    }

    private static List<IpAddressMatcher> buildContainerMatchers(AuditIngestProperties properties) {
        List<IpAddressMatcher> matchers = new ArrayList<>();
        List<String> cidrs = properties != null ? properties.getContainerCidrs() : List.of();
        if (cidrs != null) {
            for (String cidr : cidrs) {
                if (StringUtils.isBlank(cidr)) {
                    continue;
                }
                try {
                    matchers.add(new IpAddressMatcher(cidr.trim()));
                } catch (IllegalArgumentException ex) {
                    log.warn("Ignoring invalid auditing.ingest.container-cidrs entry '{}': {}", cidr, ex.getMessage());
                }
            }
        }
        return List.copyOf(matchers);
    }

    /**
     * Whether {@code ip} is a container/proxy hop rather than a real client. Loopback is always
     * a hop; the remaining ranges come from {@link AuditIngestProperties#getContainerCidrs()} so
     * sites with a non-standard bridge network (e.g. {@code 172.168.0.0/16}) can be configured
     * without a code change.
     */
    private boolean isContainerAddress(String ip) {
        if (StringUtils.isBlank(ip)) {
            return true;
        }
        String normalized = ip.trim();
        if (normalized.equals("127.0.0.1") || normalized.equals("::1") || normalized.equals("0:0:0:0:0:0:0:1")) {
            return true;
        }
        for (IpAddressMatcher matcher : containerMatchers) {
            try {
                if (matcher.matches(normalized)) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
                // non-IP literal (kept verbatim as audit evidence) — treat as non-container
            }
        }
        return false;
    }

    @PostMapping
    public ResponseEntity<?> ingest(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        Object preAuthenticated = request == null
            ? null
            : request.getAttribute(AuditIngestPreAuthenticationFilter.AUTHENTICATION_ATTRIBUTE);
        Decision decision = preAuthenticated instanceof Decision verified
            ? verified
            : authenticator.authenticate(request);
        if (!decision.accepted()) {
            log.warn(
                "Rejected audit ingest from service={} reason={} ip={}",
                decision.serviceName(),
                decision.reason(),
                request != null ? request.getRemoteAddr() : null
            );
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            Map<String, Object> authenticatedBody = body == null ? new LinkedHashMap<>() : new LinkedHashMap<>(body);
            authenticatedBody.put("sourceSystem", sourceSystemForProducer(decision.serviceName()));
            authenticatedBody.remove("producer");
            Map<String, Object> sanitizedBody = AuditPayloadSanitizer.sanitize(authenticatedBody);
            AuditPayload payload = AuditPayload.from(
                sanitizedBody,
                request,
                decision.serviceName(),
                userRepository,
                this::isContainerAddress
            );
            AuditActionRequest.Builder builder = AuditActionRequest
                .builder(payload.actor(), payload.buttonCode())
                .occurredAt(payload.occurredAt())
                .sourceSystem(payload.sourceSystem())
                .actorName(payload.actorName())
                .actorRoles(payload.actorRoles())
                .summary(payload.summary())
                .result(payload.result())
                .changeRequestRef(payload.changeRequestRef())
                .client(payload.clientIp(), payload.clientAgent())
                .request(payload.requestUri(), payload.httpMethod())
                .metadata("sourceSystem", payload.sourceSystem())
                .metadata("moduleKeyRaw", payload.moduleKeyRaw());

            if (payload.actor().startsWith("_system:")) {
                builder.allowSystemActor();
            }

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
            } else if (
                payload.operationKind() == AuditOperationKind.QUERY ||
                payload.operationKind() == AuditOperationKind.CLEAN
            ) {
                builder.allowEmptyTargets();
            }

            if (!payload.details().isEmpty()) {
                builder.detail("payload", payload.details());
            }

            Object eventId = sanitizedBody.get("eventId");
            if (eventId != null && StringUtils.isNotBlank(String.valueOf(eventId))) {
                builder.metadata("ingestEventId", String.valueOf(eventId).trim());
            }

            IngestResult result = idempotencyService.record(decision.serviceName(), sanitizedBody, builder.build());
            return switch (result.status()) {
                case RECORDED -> ResponseEntity.status(HttpStatus.CREATED).body(responseBody(result));
                case DUPLICATE -> ResponseEntity.ok(responseBody(result));
                case IDEMPOTENCY_CONFLICT -> ResponseEntity.status(HttpStatus.CONFLICT).body(responseBody(result));
            };
        } catch (NonUserActorException ex) {
            log.warn(
                "Rejected audit ingest reason=NON_USER_ACTOR producer={} eventId={}",
                decision.serviceName(),
                safeEventId(body)
            );
            return ResponseEntity.unprocessableEntity().body(Map.of("status", "REJECTED_NON_USER_ACTOR"));
        } catch (DataAccessException | TransactionException ex) {
            log.error(
                "Audit ingest temporarily unavailable producer={} eventId={} cause={}",
                decision.serviceName(),
                safeEventId(body),
                ex.getClass().getSimpleName()
            );
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "TEMPORARILY_UNAVAILABLE"));
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            log.warn(
                "Rejected invalid audit ingest producer={} eventId={} cause={}",
                decision.serviceName(),
                safeEventId(body),
                ex.getClass().getSimpleName()
            );
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (Exception ex) {
            log.error(
                "Audit ingest failed producer={} eventId={} cause={}",
                decision.serviceName(),
                safeEventId(body),
                ex.getClass().getSimpleName(),
                ex
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("status", "INTERNAL_ERROR"));
        }
    }

    private String safeEventId(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("eventId");
        if (raw == null || StringUtils.isBlank(String.valueOf(raw))) {
            return "missing";
        }
        Object sanitized = AuditPayloadSanitizer.sanitize(Map.of("eventId", raw)).get("eventId");
        String normalized = String.valueOf(sanitized).replaceAll("[\\p{Cntrl}]", "?").trim();
        return normalized.length() <= 128 ? normalized : normalized.substring(0, 128);
    }

    private Map<String, String> responseBody(IngestResult result) {
        return Map.of("status", result.status().name(), "eventId", result.eventId());
    }

    private String sourceSystemForProducer(String producer) {
        if (StringUtils.isBlank(producer)) {
            throw new IllegalArgumentException("authenticated audit producer is required");
        }
        String normalized = producer.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("dts-") ? normalized.substring("dts-".length()) : normalized;
    }

    private static final class NonUserActorException extends RuntimeException {
        private NonUserActorException(String message) {
            super(message);
        }
    }

    private record TargetRecord(String table, Object id, String label) {}

    private record ActorResolution(String username, String displayName) {}

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
        static AuditPayload from(
            Map<String, Object> body,
            HttpServletRequest request,
            String authenticatedProducer,
            AdminKeycloakUserRepository userRepository,
            Predicate<String> isContainerAddress
        ) {
            Map<String, Object> sanitizedBody = body == null ? Map.of() : new LinkedHashMap<>(body);
            String sourceSystem = text(sanitizedBody.get("sourceSystem"), "platform");
            String moduleKey = firstNonBlank(
                text(sanitizedBody.get("moduleKey")),
                text(sanitizedBody.get("module")),
                sourceSystem
            );
            String moduleName = firstNonBlank(text(sanitizedBody.get("moduleName")), moduleKey);

            ActorResolution actorResolution = normalizeActor(
                sanitizedBody,
                sourceSystem,
                authenticatedProducer,
                userRepository
            );
            String actor = actorResolution.username();
            String actorName = firstNonBlank(
                actorResolution.displayName(),
                sanitizeHumanLabel(text(sanitizedBody.get("actorName"))),
                actor
            );
            List<String> actorRoles = extractStringList(
                sanitizedBody.get("actorRoles"),
                sanitizedBody.get("operatorRoles"),
                sanitizedBody.get("roles")
            );

            String operationCodeCandidate = text(sanitizedBody.get("operationCode"));
            String actionCandidate = text(sanitizedBody.get("action"));
            String buttonCode = firstNonBlank(
                text(sanitizedBody.get("buttonCode")),
                operationCodeCandidate,
                actionCandidate,
                ButtonCodes.PLATFORM_GENERIC_EVENT
            );
            String operationCode = firstNonBlank(
                operationCodeCandidate,
                actionCandidate,
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
            String clientIp = resolveClientIp(sanitizedBody, request, isContainerAddress);
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
            copy.remove("eventId");
            copy.remove("producer");
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

        private static ActorResolution normalizeActor(
            Map<String, Object> body,
            String sourceSystem,
            String authenticatedProducer,
            AdminKeycloakUserRepository userRepository
        ) {
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
                if (text.isEmpty() || isAnonymous(text) || isMachineActor(text)) {
                    continue;
                }
                ActorResolution resolved = resolveExistingUser(text, userRepository);
                if (resolved != null) {
                    return resolved;
                }
            }
            ActorResolution machineActor = resolveTrustedMachineActor(candidates, authenticatedProducer);
            if (machineActor != null) {
                return machineActor;
            }
            String origin = sourceSystem != null && !sourceSystem.isBlank() ? sourceSystem.trim() : "unknown";
            throw new NonUserActorException("source=" + origin + " candidates=" + candidates);
        }

        private static ActorResolution resolveTrustedMachineActor(
            List<Object> candidates,
            String authenticatedProducer
        ) {
            if (!"dts-platform".equalsIgnoreCase(StringUtils.trimToEmpty(authenticatedProducer))) {
                return null;
            }
            for (Object candidate : candidates) {
                if (candidate == null) continue;
                String normalized = candidate.toString().trim().toLowerCase(Locale.ROOT);
                if (
                    normalized.equals("_system:airflow") ||
                    normalized.equals("_system:scheduler") ||
                    normalized.equals("_system:ingestion")
                ) {
                    return new ActorResolution(normalized, normalized);
                }
            }
            return null;
        }

        private static ActorResolution resolveExistingUser(String candidate, AdminKeycloakUserRepository userRepository) {
            if (userRepository == null || StringUtils.isBlank(candidate)) {
                return null;
            }
            String trimmed = candidate.trim();
            return userRepository
                .findByUsernameIgnoreCase(trimmed)
                .or(() -> userRepository.findByEmailIgnoreCase(trimmed))
                .map(AuditPayload::toActorResolution)
                .orElse(null);
        }

        private static ActorResolution toActorResolution(AdminKeycloakUser user) {
            if (user == null || StringUtils.isBlank(user.getUsername())) {
                return null;
            }
            return new ActorResolution(user.getUsername().trim(), firstNonBlank(user.getFullName(), user.getUsername()));
        }

        private static String sanitizeHumanLabel(String value) {
            if (StringUtils.isBlank(value) || isMachineActor(value) || isAnonymous(value)) {
                return null;
            }
            return value.trim();
        }

        private static boolean isAnonymous(String value) {
            if (value == null) {
                return true;
            }
            String norm = value.trim().toLowerCase(Locale.ROOT);
            return norm.isEmpty() || norm.equals("anonymous") || norm.equals("anonymoususer") || norm.equals("unknown");
        }

        private static boolean isMachineActor(String value) {
            if (value == null) {
                return true;
            }
            String norm = value.trim().toLowerCase(Locale.ROOT);
            if (norm.isEmpty()) {
                return true;
            }
            if (
                norm.startsWith("service:") ||
                norm.startsWith("_system:") ||
                norm.equals("system") ||
                norm.equals("liquibase") ||
                norm.contains("liquibase") ||
                norm.startsWith("dts-") ||
                norm.equals("postgresql") ||
                norm.equals("success") ||
                norm.equals("failed") ||
                norm.equals("execute")
            ) {
                return true;
            }
            return norm.matches("\\d+");
        }

        private static String resolveClientIp(
            Map<String, Object> body,
            HttpServletRequest request,
            Predicate<String> isContainerAddress
        ) {
            String fromBody = text(body.get("clientIp"), text(body.get("ip")));
            String forwardedCombined = request != null ? request.getHeader("Forwarded") : null;
            String forwarded = request != null ? request.getHeader("X-Forwarded-For") : null;
            String realIp = request != null ? request.getHeader("X-Real-IP") : null;
            String remote = request != null ? request.getRemoteAddr() : null;
            return firstNonContainerClientIp(isContainerAddress, forwardedCombined, forwarded, realIp, fromBody, remote);
        }

        private static String firstNonContainerClientIp(Predicate<String> isContainerAddress, String... candidates) {
            if (candidates == null) {
                return null;
            }
            String firstResolved = null;
            for (String candidate : candidates) {
                String resolved = IpAddressUtils.resolveClientIp(candidate);
                if (StringUtils.isBlank(resolved)) {
                    continue;
                }
                if (firstResolved == null) {
                    firstResolved = resolved;
                }
                if (isContainerAddress == null || !isContainerAddress.test(resolved)) {
                    return resolved;
                }
            }
            return firstResolved;
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

            if (normalized.startsWith("LOGIN") || containsAny(lower, "登录", "登入")) {
                return AuditOperationKind.LOGIN;
            }
            if (normalized.startsWith("LOGOUT") || containsAny(lower, "登出", "退出登录", "注销登录")) {
                return AuditOperationKind.LOGOUT;
            }
            if (normalized.startsWith("DOWNLOAD") || containsAny(lower, "下载", "download")) {
                return AuditOperationKind.DOWNLOAD;
            }
            if (normalized.startsWith("UPLOAD") || containsAny(lower, "上传", "upload")) {
                return AuditOperationKind.UPLOAD;
            }
            if (normalized.startsWith("EXPORT") || containsAny(lower, "导出", "export")) {
                return AuditOperationKind.EXPORT;
            }
            if (normalized.startsWith("IMPORT") || containsAny(lower, "导入", "import")) {
                return AuditOperationKind.IMPORT;
            }
            if (normalized.startsWith("GRANT") || containsAny(lower, "授权", "共享", "grant")) {
                return AuditOperationKind.GRANT;
            }
            if (normalized.startsWith("REVOKE") || containsAny(lower, "撤销授权", "取消授权", "收回", "回收", "revoke")) {
                return AuditOperationKind.REVOKE;
            }
            if (normalized.startsWith("ENABLE") || containsAny(lower, "启用", "开启", "激活", "enable")) {
                return AuditOperationKind.ENABLE;
            }
            if (normalized.startsWith("DISABLE") || containsAny(lower, "禁用", "停用", "关闭", "失效", "disable")) {
                return AuditOperationKind.DISABLE;
            }
            if (normalized.startsWith("APPROVE") || containsAny(lower, "批准", "审批通过")) {
                return AuditOperationKind.APPROVE;
            }
            if (normalized.startsWith("REJECT") || containsAny(lower, "拒绝", "驳回")) {
                return AuditOperationKind.REJECT;
            }
            if (
                normalized.startsWith("EXECUTE") ||
                normalized.startsWith("RUN") ||
                containsAny(lower, "执行", "运行", "run", "apply")
            ) {
                return AuditOperationKind.EXECUTE;
            }
            if (normalized.startsWith("ARCHIVE") || containsAny(lower, "归档", "archive")) {
                return AuditOperationKind.ARCHIVE;
            }
            if (normalized.startsWith("PUBLISH") || containsAny(lower, "发布", "publish")) {
                return AuditOperationKind.PUBLISH;
            }
            if (
                normalized.startsWith("CREATE") ||
                normalized.startsWith("ADD") ||
                normalized.startsWith("NEW") ||
                containsAny(lower, "新增", "新建", "创建", "提交", "申请")
            ) {
                return AuditOperationKind.CREATE;
            }
            if (
                normalized.startsWith("CLEAN") ||
                normalized.startsWith("PURGE") ||
                containsAny(lower, "清理", "清除", "清空", "清扫", "purge", "cleanup")
            ) {
                return AuditOperationKind.CLEAN;
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
                normalized.startsWith("WRITE") ||
                normalized.startsWith("MODIFY") ||
                normalized.startsWith("EDIT") ||
                normalized.startsWith("SAVE") ||
                containsAny(lower, "修改", "更新", "调整", "保存", "编辑", "配置", "写入")
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
                containsAny(lower, "查看", "查询", "预览", "浏览", "列表", "检索")
            ) {
                return AuditOperationKind.QUERY;
            }
            return AuditOperationKind.OTHER;
        }

        private static AuditResultStatus resolveResult(String raw) {
            if (StringUtils.isBlank(raw)) {
                // Absent result → success is the conventional default the API contract has used since v1.
                return AuditResultStatus.SUCCESS;
            }
            String normalized = raw.trim().toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "SUCCESS", "SUCCEEDED", "OK", "PASS", "通过" -> AuditResultStatus.SUCCESS;
                case "FAIL", "FAILED", "ERROR", "ERR", "DENY", "DENIED", "拒绝", "异常" -> AuditResultStatus.FAILED;
                case "PENDING", "PROCESSING", "IN_PROGRESS", "处理中" -> AuditResultStatus.PENDING;
                default -> {
                    log.warn("Unrecognised audit result '{}' — recorded as UNKNOWN to avoid silent success", raw);
                    yield AuditResultStatus.UNKNOWN;
                }
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
