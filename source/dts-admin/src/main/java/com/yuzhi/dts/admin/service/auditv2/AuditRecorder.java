package com.yuzhi.dts.admin.service.auditv2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.audit.AuditEntry;
import com.yuzhi.dts.admin.domain.audit.AuditEntryDetail;
import com.yuzhi.dts.admin.domain.audit.AuditEntryTarget;
import com.yuzhi.dts.admin.repository.audit.AuditEntryRepository;
import com.yuzhi.dts.common.audit.ChangeSnapshot;
import org.springframework.beans.factory.ObjectProvider;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class AuditRecorder {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);
    private static final String DEFAULT_SOURCE_SYSTEM = "admin";
    private static final Duration QUERY_DEDUP_WINDOW = Duration.ofSeconds(2);
    private static final Duration QUERY_DEDUP_RETENTION = Duration.ofMinutes(1);
    private static final int QUERY_DEDUP_MAX_ENTRIES = 4096;

    private final AuditEntryRepository repository;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Instant> recentQueryFingerprints = new ConcurrentHashMap<>();

    public AuditRecorder(AuditEntryRepository repository, ObjectProvider<Clock> clockProvider, ObjectMapper objectMapper) {
        this.repository = repository;
        this.clock = clockProvider != null ? clockProvider.getIfAvailable(Clock::systemUTC) : Clock.systemUTC();
        this.objectMapper = objectMapper;
    }

    public AuditBuilder start(String actorId) {
        return new AuditBuilder(this, actorId);
    }

    AuditEntry persist(ResolvedAudit audit) {
        if (shouldSkipForDedup(audit)) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Skip duplicate query audit entry for actor={} button={} uri={}",
                    audit.actorId(),
                    audit.buttonCode(),
                    audit.requestUri()
                );
            }
            return null;
        }
        AuditEntry entry = new AuditEntry();
        entry.setOccurredAt(audit.occurredAt());
        entry.setSourceSystem(clamp(audit.sourceSystem(), 32, "sourceSystem"));
        entry.setActorId(clamp(audit.actorId(), 128, "actorId"));
        entry.setActorName(clamp(audit.actorName(), 128, "actorName"));
        entry.setActorRoles(audit.actorRoles());
        entry.setModuleKey(clamp(audit.moduleKey(), 64, "moduleKey"));
        entry.setModuleName(clamp(audit.moduleName(), 128, "moduleName"));
        entry.setButtonCode(clamp(audit.buttonCode(), 128, "buttonCode"));
        entry.setOperationCode(clamp(audit.operationCode(), 128, "operationCode"));
        entry.setOperationName(clamp(audit.operationName(), 256, "operationName"));
        entry.setOperationKind(clamp(audit.operationKind().code(), 32, "operationKind"));
        entry.setResult(clamp(audit.result().code(), 32, "result"));
        entry.setSummary(audit.summary());
        entry.setChangeRequestRef(clamp(audit.changeRequestRef(), 64, "changeRequestRef"));
        entry.setClientAgent(clamp(audit.clientAgent(), 256, "clientAgent"));
        entry.setRequestUri(clamp(audit.requestUri(), 512, "requestUri"));
        entry.setHttpMethod(clamp(audit.httpMethod(), 16, "httpMethod"));
        entry.setMetadata(audit.metadata());
        entry.setExtraAttributes(audit.extraAttributes());
        entry.setClientIp(audit.clientIp());

        int targetIndex = 0;
        for (TargetRecord target : audit.targets()) {
            entry.addTarget(new AuditEntryTarget(targetIndex++, target.table(), target.id(), target.label()));
        }
        int detailIndex = 0;
        for (DetailRecord detail : audit.details()) {
            entry.addDetail(new AuditEntryDetail(detailIndex++, detail.key(), detail.value()));
        }

        AuditEntry saved = repository.save(entry);
        if (log.isDebugEnabled()) {
            log.debug(
                "Recorded audit entry id={} actor={} module={} operation={} targets={}",
                saved.getId(),
                saved.getActorId(),
                saved.getModuleKey(),
                saved.getOperationCode(),
                audit.targets().size()
            );
        }
        return saved;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private InetAddress safeInet(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        try {
            return InetAddress.getByName(ip.trim());
        } catch (UnknownHostException ex) {
            if (log.isDebugEnabled()) {
                log.debug("Unable to parse client ip '{}': {}", ip, ex.getMessage());
            }
            return null;
        }
    }

    private boolean shouldSkipForDedup(ResolvedAudit audit) {
        if (audit.operationKind() != AuditOperationKind.QUERY) {
            return false;
        }
        Instant occurredAt = audit.occurredAt();
        String key = dedupKey(audit);
        AtomicBoolean duplicate = new AtomicBoolean(false);
        recentQueryFingerprints.compute(key, (k, previous) -> {
            if (previous != null && isWithinDedupWindow(previous, occurredAt)) {
                duplicate.set(true);
                return previous;
            }
            return occurredAt;
        });
        if (!duplicate.get() && recentQueryFingerprints.size() > QUERY_DEDUP_MAX_ENTRIES) {
            pruneDedupCache(occurredAt != null ? occurredAt.minus(QUERY_DEDUP_RETENTION) : null);
        }
        return duplicate.get();
    }

    private boolean isWithinDedupWindow(Instant previous, Instant current) {
        if (previous == null || current == null) {
            return false;
        }
        long diff = Math.abs(current.toEpochMilli() - previous.toEpochMilli());
        return diff <= QUERY_DEDUP_WINDOW.toMillis();
    }

    private void pruneDedupCache(Instant cutoff) {
        if (cutoff == null) {
            return;
        }
        recentQueryFingerprints.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isBefore(cutoff));
    }

    private String dedupKey(ResolvedAudit audit) {
        StringBuilder sb = new StringBuilder();
        sb.append(safeString(audit.actorId())).append('|');
        sb.append(safeString(audit.buttonCode())).append('|');
        sb.append(safeString(audit.moduleKey())).append('|');
        sb.append(safeString(audit.requestUri())).append('|');
        sb.append(safeString(audit.summary())).append('|');
        sb.append(audit.clientIp() != null ? audit.clientIp().getHostAddress() : "");
        return sb.toString();
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String clamp(String value, int maxLength, String field) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!StringUtils.hasText(trimmed)) {
            return trimmed;
        }
        if (maxLength <= 0 || trimmed.length() <= maxLength) {
            return trimmed;
        }
        String truncated = maxLength > 3 ? trimmed.substring(0, maxLength - 3) + "..." : trimmed.substring(0, maxLength);
        if (log.isDebugEnabled()) {
            log.debug("Truncated {} to {} characters for audit entry", field, maxLength);
        }
        return truncated;
    }

    private Object sanitizeDetailValue(Object value) {
        Object normalized = normalizeDetailValue(value);
        if (normalized == null) {
            return null;
        }
        if (normalized instanceof Map<?, ?> || normalized instanceof Collection<?> || normalized.getClass().isArray()) {
            return serializeStructured(normalized);
        }
        return normalized;
    }

    private Object normalizeDetailValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ChangeSnapshot snapshot) {
            return snapshot.toMap();
        }
        if (value instanceof CharSequence text) {
            String trimmed = text.toString().trim();
            return trimmed.isEmpty() ? "" : trimmed;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    normalized.put(String.valueOf(k), normalizeDetailValue(v));
                }
            });
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                normalized.add(normalizeDetailValue(item));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(value);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeDetailValue(java.lang.reflect.Array.get(value, i)));
            }
            return normalized;
        }
        return value.toString();
    }

    private Object serializeStructured(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (objectMapper != null) {
            try {
                return objectMapper.writeValueAsString(value);
            } catch (Exception ex) {
                if (log.isDebugEnabled()) {
                    log.debug("Failed to serialize structured detail value: {}", ex.getMessage());
                }
            }
        }
        return String.valueOf(value);
    }

    public static final class AuditBuilder {

        private final AuditRecorder recorder;
        private final String actorId;
        private Instant occurredAt;
        private String sourceSystem = DEFAULT_SOURCE_SYSTEM;
        private String actorName;
        private final Set<String> actorRoles = new LinkedHashSet<>();
        private String moduleKey;
        private String moduleName;
        private String buttonCode;
        private String operationCode;
        private String operationName;
        private AuditOperationKind operationKind;
        private AuditResultStatus result = AuditResultStatus.SUCCESS;
        private String summary;
        private String changeRequestRef;
        private String clientIp;
        private String clientAgent;
        private String requestUri;
        private String httpMethod;
        private final Map<String, Object> metadata = new LinkedHashMap<>();
        private final Map<String, Object> extraAttributes = new LinkedHashMap<>();
        private final List<TargetRecord> targets = new ArrayList<>();
        private final List<DetailRecord> details = new ArrayList<>();
        private boolean allowEmptyTargets;
        private boolean emitted;

        private AuditBuilder(AuditRecorder recorder, String actorId) {
            this.recorder = recorder;
            this.actorId = actorId;
        }

        public AuditBuilder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public AuditBuilder sourceSystem(String sourceSystem) {
            if (StringUtils.hasText(sourceSystem)) {
                this.sourceSystem = sourceSystem.trim();
            }
            return this;
        }

        public AuditBuilder actorName(String actorName) {
            this.actorName = StringUtils.hasText(actorName) ? actorName.trim() : null;
            return this;
        }

        public AuditBuilder actorRoles(Collection<String> roles) {
            if (roles != null) {
                roles.stream().filter(StringUtils::hasText).map(r -> r.trim().toUpperCase(Locale.ROOT)).forEach(actorRoles::add);
            }
            return this;
        }

        public AuditBuilder module(String moduleKey) {
            this.moduleKey = StringUtils.hasText(moduleKey) ? moduleKey.trim() : null;
            return this;
        }

        public AuditBuilder moduleName(String moduleName) {
            this.moduleName = StringUtils.hasText(moduleName) ? moduleName.trim() : null;
            return this;
        }

        public AuditBuilder buttonCode(String buttonCode) {
            this.buttonCode = StringUtils.hasText(buttonCode) ? buttonCode.trim() : null;
            return this;
        }

        public AuditBuilder operation(String code, String name, AuditOperationKind kind) {
            this.operationCode = StringUtils.hasText(code) ? code.trim() : null;
            this.operationName = StringUtils.hasText(name) ? name.trim() : null;
            this.operationKind = kind;
            return this;
        }

        public AuditBuilder operationKind(AuditOperationKind kind) {
            this.operationKind = kind;
            return this;
        }

        public AuditBuilder result(AuditResultStatus result) {
            if (result != null) {
                this.result = result;
            }
            return this;
        }

        public AuditBuilder summary(String summary) {
            this.summary = StringUtils.hasText(summary) ? summary.trim() : null;
            return this;
        }

        public AuditBuilder changeRequestRef(String changeRequestRef) {
            this.changeRequestRef = StringUtils.hasText(changeRequestRef) ? changeRequestRef.trim() : null;
            return this;
        }

        public AuditBuilder client(String ip, String agent) {
            this.clientIp = ip;
            this.clientAgent = agent;
            return this;
        }

        public AuditBuilder request(String uri, String method) {
            this.requestUri = StringUtils.hasText(uri) ? uri.trim() : null;
            this.httpMethod = StringUtils.hasText(method) ? method.trim().toUpperCase(Locale.ROOT) : null;
            return this;
        }

        public AuditBuilder metadata(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                metadata.put(key.trim(), value);
            }
            return this;
        }

        public AuditBuilder extraAttribute(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                extraAttributes.put(key.trim(), value);
            }
            return this;
        }

        public AuditBuilder target(String table, Object id) {
            return target(table, id, null);
        }

        public AuditBuilder target(String table, Object id, String label) {
            if (!StringUtils.hasText(table) || id == null) {
                return this;
            }
            String normalizedTable = table.trim();
            String normalizedId = String.valueOf(id).trim();
            if (normalizedId.isEmpty()) {
                return this;
            }
            String normalizedLabel = StringUtils.hasText(label) ? label.trim() : null;
            targets.add(new TargetRecord(normalizedTable, normalizedId, normalizedLabel));
            return this;
        }

        public AuditBuilder detail(String key, Object value) {
            if (StringUtils.hasText(key) && value != null) {
                details.add(new DetailRecord(key.trim(), recorder.sanitizeDetailValue(value)));
            }
            return this;
        }

        public AuditBuilder allowEmptyTargets() {
            this.allowEmptyTargets = true;
            return this;
        }

        public AuditEntry emit() {
            if (emitted) {
                throw new IllegalStateException("AuditBuilder already used");
            }
            emitted = true;
            ResolvedAudit audit = resolve();
            return recorder.persist(audit);
        }

        private ResolvedAudit resolve() {
            if (!StringUtils.hasText(actorId)) {
                throw new IllegalArgumentException("actorId is required");
            }
            if (!StringUtils.hasText(moduleKey)) {
                throw new IllegalStateException("moduleKey is required for audit entry");
            }
            if (operationKind == null) {
                throw new IllegalStateException("operationKind is required for audit entry");
            }
            if (
                !allowEmptyTargets &&
                targets.isEmpty() &&
                operationKind != AuditOperationKind.QUERY &&
                operationKind != AuditOperationKind.CLEAN
            ) {
                throw new IllegalStateException("target id is required for " + operationKind);
            }
            String effectiveOperationName = StringUtils.hasText(operationName) ? operationName : operationCode;
            if (!StringUtils.hasText(effectiveOperationName) && StringUtils.hasText(summary)) {
                effectiveOperationName = summary;
            }
            if (!StringUtils.hasText(effectiveOperationName)) {
                effectiveOperationName = operationKind.displayName();
            }
            String effectiveSummary = StringUtils.hasText(summary) ? summary : effectiveOperationName;

            List<String> roleList = actorRoles.isEmpty() ? List.of() : List.copyOf(actorRoles);

            return new ResolvedAudit(
                recorder.now(),
                StringUtils.hasText(sourceSystem) ? sourceSystem : DEFAULT_SOURCE_SYSTEM,
                actorId.trim(),
                actorName,
                roleList,
                moduleKey,
                moduleName,
                buttonCode,
                operationCode,
                effectiveOperationName,
                operationKind,
                result,
                effectiveSummary,
                changeRequestRef,
                recorder.safeInet(clientIp),
                clientAgent,
                requestUri,
                httpMethod,
                Map.copyOf(metadata),
                Map.copyOf(extraAttributes),
                List.copyOf(targets),
                List.copyOf(details)
            );
        }
    }

    record TargetRecord(String table, String id, String label) {}

    record DetailRecord(String key, Object value) {}

    record ResolvedAudit(
        Instant occurredAt,
        String sourceSystem,
        String actorId,
        String actorName,
        List<String> actorRoles,
        String moduleKey,
        String moduleName,
        String buttonCode,
        String operationCode,
        String operationName,
        AuditOperationKind operationKind,
        AuditResultStatus result,
        String summary,
        String changeRequestRef,
        InetAddress clientIp,
        String clientAgent,
        String requestUri,
        String httpMethod,
        Map<String, Object> metadata,
        Map<String, Object> extraAttributes,
        List<TargetRecord> targets,
        List<DetailRecord> details
    ) {
        ResolvedAudit {
            Objects.requireNonNull(occurredAt, "occurredAt");
            Objects.requireNonNull(sourceSystem, "sourceSystem");
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(moduleKey, "moduleKey");
            Objects.requireNonNull(operationKind, "operationKind");
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(summary, "summary");
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(extraAttributes, "extraAttributes");
            Objects.requireNonNull(targets, "targets");
            Objects.requireNonNull(details, "details");
        }
    }
}
