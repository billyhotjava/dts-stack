package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.config.AuditProperties;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.repository.AuditEventRepository;
import com.yuzhi.dts.admin.security.SecurityUtils;
import com.yuzhi.dts.common.audit.AuditStage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
@Transactional
public class AdminAuditService {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditService.class);

    public static class AuditEventView {
        private String eventId;
        private long id;
        private Instant occurredAt;
        private String actor;
        private String actorRole;
        private String actorName;
        private String module;
        private String moduleLabel;
        private String action;
        private String resourceType;
        private String resourceId;
        private String clientIp;
        private String clientAgent;
        private String httpMethod;
        private String result;
        private String resultText;
        private String extraTags;
        private String payloadPreview;
        private String sourceSystem;
        private String sourceSystemText;
        private String eventClass;
        private String eventType;
        private String summary;
        private String operationCode;
        private String operationGroup;
        private String operationName;
        private String operationTypeCode;
        private String operationTypeText;
        private String operationContent;
        private Boolean operationRuleHit;
        private Long operationRuleId;
        private String operationModule;
        private String operationSourceTable;
        private String operatorId;
        private String operatorName;
        private List<String> operatorRoles = List.of();
        private String orgCode;
        private String orgName;
        private String departmentName;
        private String requestId;
        private String targetTable;
        private String targetTableLabel;
        private List<String> targetIds = List.of();
        private Map<String, Object> targetLabels = Map.of();
        private Map<String, Object> targetSnapshot = Map.of();
        private String targetId;
        private String targetRef;
        private String changeRequestRef;
        private String approvalSummary;
        private Map<String, Object> details = Map.of();

        public String eventId() { return eventId; }
        public long id() { return id; }
        public Instant occurredAt() { return occurredAt; }
        public String actor() { return actor; }
        public String actorRole() { return actorRole; }
        public String actorName() { return actorName; }
        public String module() { return module; }
        public String moduleLabel() { return moduleLabel; }
        public String action() { return action; }
        public String resourceType() { return resourceType; }
        public String resourceId() { return resourceId; }
        public String clientIp() { return clientIp; }
        public String clientAgent() { return clientAgent; }
        public String httpMethod() { return httpMethod; }
        public String result() { return result; }
        public String resultText() { return resultText; }
        public String extraTags() { return extraTags; }
        public String payloadPreview() { return payloadPreview; }
        public String sourceSystem() { return sourceSystem; }
        public String sourceSystemText() { return sourceSystemText; }
        public String eventClass() { return eventClass; }
        public String eventType() { return eventType; }
        public String summary() { return summary; }
        public String operationCode() { return operationCode; }
        public String operationGroup() { return operationGroup; }
        public String operationName() { return operationName; }
        public String operationType() { return operationTypeCode; }
        public String operationTypeText() { return operationTypeText; }
        public String operationContent() { return operationContent; }
        public Boolean operationRuleHit() { return operationRuleHit; }
        public Long operationRuleId() { return operationRuleId; }
        public String operationModule() { return operationModule; }
        public String operationSourceTable() { return operationSourceTable; }
        public String operatorId() { return operatorId; }
        public String operatorName() { return operatorName; }
        public List<String> operatorRoles() { return operatorRoles; }
        public String orgCode() { return orgCode; }
        public String orgName() { return orgName; }
        public String departmentName() { return departmentName; }
        public String requestId() { return requestId; }
        public String targetTable() { return targetTable; }
        public String targetTableLabel() { return targetTableLabel; }
        public List<String> targetIds() { return targetIds; }
        public Map<String, Object> targetLabels() { return targetLabels; }
        public Map<String, Object> targetSnapshot() { return targetSnapshot; }
        public String targetId() { return targetId; }
        public String targetRef() { return targetRef; }
        public String changeRequestRef() { return changeRequestRef; }
        public String approvalSummary() { return approvalSummary; }
        public Map<String, Object> details() { return details; }
    }

    public record ModuleOption(String code, String label) {}

    private String resolveActorRole(String actor) {
        if (!StringUtils.hasText(actor)) {
            return null;
        }
        String uname = actor.trim().toLowerCase(java.util.Locale.ROOT);
        if (uname.equals("sysadmin")) {
            return "ROLE_SYS_ADMIN";
        }
        if (uname.equals("authadmin")) {
            return "ROLE_AUTH_ADMIN";
        }
        if (uname.equals("auditadmin") || uname.equals("securityadmin") || uname.equals("security_auditor")) {
            return "ROLE_SECURITY_AUDITOR";
        }
        if (uname.equals("opadmin")) {
            return "ROLE_OP_ADMIN";
        }
        try {
            return userRepo
                .findByUsernameIgnoreCase(actor)
                .flatMap(user -> user.getRealmRoles().stream().map(SecurityUtils::normalizeRole).filter(r -> r.startsWith("ROLE_")).findFirst())
                .orElse(null);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to resolve role for actor {}: {}", actor, ex.toString());
            }
            return null;
        }
    }

    private String translateActorRole(String role, String actor) {
        String uname = actor == null ? null : actor.trim().toLowerCase(java.util.Locale.ROOT);
        if (!StringUtils.hasText(role)) {
            if ("sysadmin".equals(uname)) return "系统管理员";
            if ("authadmin".equals(uname)) return "授权管理员";
            if ("auditadmin".equals(uname)) return "安全审计员";
            if ("opadmin".equals(uname)) return "运维管理员";
            return null;
        }
        String normalized = SecurityUtils.normalizeRole(role);
        return switch (normalized) {
            case "ROLE_SYS_ADMIN", "SYS_ADMIN" -> "系统管理员";
            case "ROLE_AUTH_ADMIN", "AUTH_ADMIN" -> "授权管理员";
            case "ROLE_SECURITY_AUDITOR", "ROLE_AUDITOR_ADMIN", "ROLE_AUDIT_ADMIN", "SECURITY_AUDITOR", "AUDITOR_ADMIN", "AUDIT_ADMIN", "AUDITADMIN" -> "安全审计员";
            case "ROLE_OP_ADMIN", "OP_ADMIN" -> "运维管理员";
            default -> null;
        };
    }

    public static final class PendingAuditEvent {
        public Instant occurredAt;
        public String actor;
        public String actorRole;
        public String actorName;
        public String sourceSystem; // admin|platform（缺省admin）
        public String module;
        public String action;
        public String moduleLabel;
        public String operationCode;
        public String operationName;
        public String operationType;
        public String operationTypeText;
        public String operationContent;
        public String operationGroup;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public Object payload;
        public Map<String, Object> details;
        public AuditTarget target;
        public String summary;
        public String extraTags;
        // captured entity info (propagated from request thread by aspect)
        public String capturedTable;
        public String capturedId;
        public int retryCount;
        public Instant firstFailureAt;
        public String lastError;
    }

    public enum AuditResult {
        SUCCESS("SUCCESS", "成功"),
        FAILED("FAILED", "失败"),
        PENDING("PENDING", "处理中");

        private final String code;
        private final String display;

        AuditResult(String code, String display) {
            this.code = code;
            this.display = display;
        }

        public String code() {
            return code;
        }

        public String display() {
            return display;
        }

        public static AuditResult from(String value) {
            if (!StringUtils.hasText(value)) {
                return SUCCESS;
            }
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (AuditResult candidate : values()) {
                if (candidate.code.equals(normalized)) {
                    return candidate;
                }
            }
            return switch (normalized) {
                case "OK", "SUCCEEDED", "APPROVED", "通过" -> SUCCESS;
                case "FAIL", "FAILED", "ERROR", "异常", "驳回", "拒绝" -> FAILED;
                case "PROCESSING", "PROCESS", "PENDING", "处理中", "排队" -> PENDING;
                default -> SUCCESS;
            };
        }
    }

    public static final class AuditTarget {
        private final String table;
        private final List<String> ids;
        private final Map<String, String> labels;
        private final Map<String, Object> snapshot;

        public AuditTarget(String table, Collection<String> ids, Map<String, String> labels, Map<String, Object> snapshot) {
            this.table = StringUtils.hasText(table) ? table.trim() : null;
            this.ids = normalizeIds(ids);
            this.labels = labels == null ? Map.of() : Map.copyOf(labels);
            this.snapshot = snapshot == null ? Map.of() : Map.copyOf(snapshot);
        }

        public static AuditTarget of(String table, Collection<String> ids) {
            return new AuditTarget(table, ids, Map.of(), Map.of());
        }

        public String table() {
            return table;
        }

        public List<String> ids() {
            return ids;
        }

        public Map<String, String> labels() {
            return labels;
        }

        public Map<String, Object> snapshot() {
            return snapshot;
        }

        private List<String> normalizeIds(Collection<String> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
            for (String id : ids) {
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                normalized.add(id.trim());
            }
            return List.copyOf(normalized);
        }
    }

    public static final class AuditSearchCriteria {
        private final String actor;
        private final String module;
        private final String operationType;
        private final String actionCode;
        private final String operationGroup;
        private final String sourceSystem;
        private final String result;
        private final String targetTable;
        private final String targetId;
        private final String clientIp;
        private final String keyword;
        private final Instant from;
        private final Instant to;
        private final List<String> allowedActors;
        private final List<String> excludedActors;
        private final boolean includeDetails;

        public AuditSearchCriteria(
            String actor,
            String module,
            String operationType,
            String actionCode,
            String operationGroup,
            String sourceSystem,
            String result,
            String targetTable,
            String targetId,
            String clientIp,
            String keyword,
            Instant from,
            Instant to,
            Collection<String> allowedActors,
            Collection<String> excludedActors,
            boolean includeDetails
        ) {
            this.actor = sanitize(actor);
            this.module = sanitize(module);
            this.operationType = sanitize(operationType);
            this.actionCode = sanitize(actionCode);
            this.operationGroup = sanitize(operationGroup);
            this.sourceSystem = sanitize(sourceSystem);
            this.result = sanitize(result);
            this.targetTable = sanitize(targetTable);
            this.targetId = sanitize(targetId);
            this.clientIp = sanitize(clientIp);
            this.keyword = sanitize(keyword);
            this.from = from;
            this.to = to;
            this.allowedActors = sanitizeActorList(allowedActors);
            this.excludedActors = sanitizeActorList(excludedActors);
            this.includeDetails = includeDetails;
        }

        public String actor() { return actor; }
        public String module() { return module; }
        public String operationType() { return operationType; }
        public String actionCode() { return actionCode; }
        public String operationGroup() { return operationGroup; }
        public String sourceSystem() { return sourceSystem; }
        public String result() { return result; }
        public String targetTable() { return targetTable; }
        public String targetId() { return targetId; }
        public String clientIp() { return clientIp; }
        public String keyword() { return keyword; }
        public Instant from() { return from; }
        public Instant to() { return to; }
        public List<String> allowedActors() { return allowedActors; }
        public List<String> excludedActors() { return excludedActors; }
        public boolean includeDetails() { return includeDetails; }

        private static String sanitize(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            return value.trim();
        }

        private static List<String> sanitizeActorList(Collection<String> actors) {
            if (actors == null || actors.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String actor : actors) {
                if (!StringUtils.hasText(actor)) {
                    continue;
                }
                normalized.add(actor.trim().toLowerCase(Locale.ROOT));
            }
            return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
        }
    }

    public AuditRecordBuilder builder() {
        return new AuditRecordBuilder();
    }

    public Page<AuditEventView> search(AuditSearchCriteria criteria, Pageable pageable) {
        AuditSearchCriteria effective = criteria != null
            ? criteria
            : new AuditSearchCriteria(null, null, null, null, null, null, null, null, null, null, null, null, null, List.of(), List.of(), true);

        Page<AuditEvent> page = search(
            effective.actor(),
            effective.module(),
            effective.actionCode(),
            effective.sourceSystem(),
            null,
            effective.result(),
            effective.targetTable(),
            effective.targetId(),
            null,
            effective.from(),
            effective.to(),
            effective.clientIp(),
            effective.operationGroup(),
            pageable
        );

        List<AuditEvent> filtered = page.getContent();
        boolean mutated = false;
        if (!effective.allowedActors().isEmpty()) {
            Set<String> allowed = new HashSet<>(effective.allowedActors());
            filtered = filtered
                .stream()
                .filter(event -> allowed.contains(Optional.ofNullable(event.getActor()).orElse("" ).trim().toLowerCase(Locale.ROOT)))
                .toList();
            mutated = true;
        }
        if (!effective.excludedActors().isEmpty()) {
            Set<String> excluded = new HashSet<>(effective.excludedActors());
            filtered = filtered
                .stream()
                .filter(event -> !excluded.contains(Optional.ofNullable(event.getActor()).orElse("" ).trim().toLowerCase(Locale.ROOT)))
                .toList();
            mutated = true;
        }
        Page<AuditEvent> effectivePage = mutated
            ? new PageImpl<>(filtered, pageable, filtered.size())
            : page;
        boolean includeDetails = effective.includeDetails();
        return effectivePage.map(event -> toView(event, includeDetails));
    }

    public Optional<AuditEventView> fetchById(Long id, boolean includeDetails) {
        if (id == null) {
            return Optional.empty();
        }
        return repository.findById(id).map(event -> toView(event, includeDetails));
    }

    public final class AuditRecordBuilder {
        private final PendingAuditEvent event = new PendingAuditEvent();
        private final Map<String, Object> detailMap = new LinkedHashMap<>();

        public AuditRecordBuilder actor(String actor) {
            event.actor = actor;
            return this;
        }

        public AuditRecordBuilder actorName(String actorName) {
            event.actorName = actorName;
            return this;
        }

        public AuditRecordBuilder actorRole(String actorRole) {
            event.actorRole = actorRole;
            return this;
        }

        public AuditRecordBuilder sourceSystem(String sourceSystem) {
            event.sourceSystem = sourceSystem;
            return this;
        }

        public AuditRecordBuilder occurredAt(Instant occurredAt) {
            event.occurredAt = occurredAt;
            return this;
        }

        public AuditRecordBuilder module(String module) {
            event.module = module;
            return this;
        }

        public AuditRecordBuilder moduleLabel(String moduleLabel) {
            event.moduleLabel = moduleLabel;
            return this;
        }

        public AuditRecordBuilder action(String action) {
            event.action = action;
            return this;
        }

        public AuditRecordBuilder summary(String summary) {
            event.summary = summary;
            return this;
        }

        public AuditRecordBuilder operationName(String operationName) {
            event.operationName = operationName;
            return this;
        }

        public AuditRecordBuilder operationCode(String operationCode) {
            event.operationCode = operationCode;
            return this;
        }

        public AuditRecordBuilder operationGroup(String operationGroup) {
            event.operationGroup = operationGroup;
            return this;
        }

        public AuditRecordBuilder operationType(AuditOperationType type) {
            if (type != null && type != AuditOperationType.UNKNOWN) {
                event.operationType = type.getCode();
                if (!StringUtils.hasText(event.operationTypeText)) {
                    event.operationTypeText = type.getDisplayName();
                }
            }
            return this;
        }

        public AuditRecordBuilder operationTypeText(String text) {
            event.operationTypeText = text;
            return this;
        }

        public AuditRecordBuilder operationContent(String content) {
            event.operationContent = content;
            return this;
        }

        public AuditRecordBuilder details(Map<String, ?> details) {
            this.detailMap.clear();
            if (details != null) {
                details.forEach((k, v) -> {
                    if (k != null) {
                        this.detailMap.put(String.valueOf(k), v);
                    }
                });
            }
            return this;
        }

        public AuditRecordBuilder detail(String key, Object value) {
            if (StringUtils.hasText(key)) {
                this.detailMap.put(key, value);
            }
            return this;
        }

        public AuditRecordBuilder clientIp(String clientIp) {
            event.clientIp = clientIp;
            return this;
        }

        public AuditRecordBuilder clientAgent(String clientAgent) {
            event.clientAgent = clientAgent;
            return this;
        }

        public AuditRecordBuilder requestUri(String requestUri) {
            event.requestUri = requestUri;
            return this;
        }

        public AuditRecordBuilder httpMethod(String httpMethod) {
            event.httpMethod = httpMethod;
            return this;
        }

        public AuditRecordBuilder target(AuditTarget target) {
            event.target = target;
            return this;
        }

        public AuditRecordBuilder resource(String resourceType, String resourceId) {
            event.resourceType = resourceType;
            event.resourceId = resourceId;
            return this;
        }

        public AuditRecordBuilder result(AuditResult result) {
            AuditResult effective = result == null ? AuditResult.SUCCESS : result;
            event.result = effective.code();
            return this;
        }

        public AuditRecordBuilder fromOperation(AdminAuditOperation operation) {
            if (operation == null) {
                return this;
            }
            event.operationCode = operation.code();
            event.module = operation.moduleKey();
            event.moduleLabel = operation.moduleLabel();
            if (!StringUtils.hasText(event.operationType)) {
                event.operationType = operation.type().getCode();
            }
            if (!StringUtils.hasText(event.operationTypeText)) {
                event.operationTypeText = operation.type().getDisplayName();
            }
            if (!StringUtils.hasText(event.action)) {
                event.action = operation.defaultName();
            }
            if (!StringUtils.hasText(event.operationName)) {
                event.operationName = operation.defaultName();
            }
            if (!StringUtils.hasText(event.resourceType)) {
                event.resourceType = operation.targetTable();
            }
            return this;
        }

        public PendingAuditEvent build() {
            PendingAuditEvent built = new PendingAuditEvent();
            built.actor = defaultString(event.actor, SecurityUtils.getCurrentUserLogin().orElse("anonymous"));
            built.actorRole = event.actorRole;
            built.actorName = event.actorName;
            built.sourceSystem = defaultString(event.sourceSystem, "admin");
            built.occurredAt = event.occurredAt != null ? event.occurredAt : Instant.now();
            built.module = defaultString(event.module, "general");
            built.moduleLabel = event.moduleLabel;
            built.action = defaultString(event.action, "UNKNOWN");
            built.operationCode = event.operationCode;
            built.operationGroup = event.operationGroup;
            built.operationName = StringUtils.hasText(event.operationName) ? event.operationName : built.action;
            AuditOperationType resolvedType = StringUtils.hasText(event.operationType)
                ? AuditOperationType.from(event.operationType)
                : AuditOperationType.from(event.action);
            built.operationType = resolvedType.getCode();
            built.operationTypeText = StringUtils.hasText(event.operationTypeText) ? event.operationTypeText : resolvedType.getDisplayName();
            built.operationContent = event.operationContent;
            built.resourceType = event.resourceType;
            built.resourceId = event.resourceId;
            built.clientIp = event.clientIp;
            built.clientAgent = event.clientAgent;
            built.requestUri = event.requestUri;
            built.httpMethod = event.httpMethod;
            built.result = defaultString(event.result, AuditResult.SUCCESS.code());
            built.summary = event.summary;
            built.target = event.target;
            if (!detailMap.isEmpty()) {
                built.details = Map.copyOf(detailMap);
                built.payload = new LinkedHashMap<>(detailMap);
            }
            built.extraTags = event.extraTags;
            return built;
        }
    }

    private final AuditEventRepository repository;
    private final AuditProperties properties;
    private final DtsCommonAuditClient auditClient;
    private final ObjectMapper objectMapper;
    private final com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository userRepo;
    private final com.yuzhi.dts.admin.repository.OrganizationRepository organizationRepository;
    private final AuditResourceDictionaryService resourceDictionary;
    private final OperationMappingEngine operationMappingEngine;

    private BlockingQueue<PendingAuditEvent> queue;
    private ScheduledExecutorService workerPool;
    private SecretKey encryptionKey;
    private SecretKey hmacKey;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> lastChainSignature = new AtomicReference<>("");

    public AdminAuditService(
        AuditEventRepository repository,
        AuditProperties properties,
        DtsCommonAuditClient auditClient,
        ObjectMapper objectMapper,
        com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository userRepo,
        com.yuzhi.dts.admin.repository.OrganizationRepository organizationRepository,
        AuditResourceDictionaryService resourceDictionary,
        OperationMappingEngine operationMappingEngine
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditClient = auditClient;
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
        this.organizationRepository = organizationRepository;
        this.resourceDictionary = resourceDictionary;
        this.operationMappingEngine = operationMappingEngine;
    }

    @PostConstruct
    public void start() {
        if (!properties.isEnabled()) {
            log.warn("Auditing is disabled via configuration");
            return;
        }
        this.queue = new LinkedBlockingQueue<>(properties.getQueueCapacity());
        ThreadFactory factory = runnable -> {
            Thread t = new Thread(runnable, "audit-writer");
            t.setDaemon(true);
            return t;
        };
        this.workerPool = Executors.newScheduledThreadPool(1, factory);
        this.encryptionKey = AuditCrypto.buildKey(resolveEncryptionKey());
        this.hmacKey = AuditCrypto.buildMacKey(resolveHmacKey());
        boolean historyAvailable = true;
        String chainSeed = "";
        try {
            chainSeed = repository.findTopByOrderByIdDesc().map(AuditEvent::getChainSignature).orElse("");
        } catch (InvalidDataAccessResourceUsageException ex) {
            historyAvailable = false;
            log.info(
                "Audit event table not yet available; delaying chain seed until Liquibase completes ({})",
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
            );
        } catch (Exception ex) {
            historyAvailable = false;
            log.warn("Failed to load existing audit chain signature; starting fresh", ex);
        }
        this.lastChainSignature.set(chainSeed);
        running.set(true);
        workerPool.scheduleWithFixedDelay(this::drainQueue, 0, 500, TimeUnit.MILLISECONDS);
        Long existingCount = null;
        if (historyAvailable) {
            try {
                existingCount = repository.count();
            } catch (Exception ex) {
                log.warn("Failed to query existing audit event count", ex);
            }
        }
        if (existingCount != null) {
            log.info(
                "Audit writer started with capacity {} and {} existing audit events",
                properties.getQueueCapacity(),
                existingCount
            );
        } else {
            log.info(
                "Audit writer started with capacity {}; existing audit history unavailable yet",
                properties.getQueueCapacity()
            );
        }
    }

    public List<ModuleOption> listModuleOptions() {
        List<String> distinct = repository.findDistinctModules();
        LinkedHashMap<String, ModuleOption> options = new LinkedHashMap<>();
        if (distinct != null) {
            for (String raw : distinct) {
                String normalized = normalize(raw);
                if (!StringUtils.hasText(normalized) || options.containsKey(normalized)) {
                    continue;
                }
                options.put(normalized, new ModuleOption(normalized, resolveModuleLabel(normalized)));
            }
        }
        if (options.isEmpty()) {
            options.put("general", new ModuleOption("general", resolveModuleLabel("general")));
        }
        return List.copyOf(options.values());
    }

    private String resolveModuleLabel(String code) {
        if (!StringUtils.hasText(code)) {
            return "通用";
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        Optional<String> category = resourceDictionary.resolveCategory(normalized);
        if (category.isPresent()) {
            return category.orElseThrow();
        }
        Optional<String> label = resourceDictionary.resolveLabel(normalized);
        if (label.isPresent()) {
            return label.orElseThrow();
        }
        return switch (normalized) {
            case "admin" -> "用户管理";
            case "approval" -> "审批管理";
            case "catalog" -> "数据资产";
            case "platform" -> "平台管理";
            case "governance" -> "治理中心";
            case "explore" -> "探索分析";
            case "modeling" -> "模型管理";
            case "visualization" -> "可视化";
            case "general" -> "通用";
            default -> normalized;
        };
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    public void recordAction(String actionCode, AuditStage stage, String resourceId, Object payload) {
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.markDomainAudit();
        recordAction(SecurityUtils.getCurrentUserLogin().orElse("anonymous"), actionCode, stage, resourceId, payload);
    }

    public void recordAction(String actor, String actionCode, AuditStage stage, String resourceId, Object payload) {
        if (!StringUtils.hasText(actor) || "anonymous".equalsIgnoreCase(actor) || "anonymoususer".equalsIgnoreCase(actor)) {
            return;
        }
        String normalizedCode = normalizeActionCode(actionCode);
        AuditStage effectiveStage = stage == null ? AuditStage.SUCCESS : stage;
        ActionEnvelope envelope = deriveActionEnvelope(normalizedCode);
        Map<String, Object> extraTags = new HashMap<>();
        if (StringUtils.hasText(normalizedCode)) {
            extraTags.put("actionCode", normalizedCode);
        }
        extraTags.put("stage", switch (effectiveStage) {
            case BEGIN -> "处理中";
            case SUCCESS -> "成功";
            case FAIL -> "失败";
        });
        if (StringUtils.hasText(envelope.moduleKey())) {
            extraTags.put("moduleKey", envelope.moduleKey());
        }
        if (StringUtils.hasText(envelope.resourceKey())) {
            extraTags.put("entryKey", envelope.resourceKey());
        }

        record(
            actor,
            envelope.actionDisplay(),
            envelope.moduleKey(),
            envelope.resourceKey(),
            resourceId,
            outcomeFromStage(effectiveStage),
            payload,
            extraTags
        );
    }

    private String outcomeFromStage(AuditStage stage) {
        AuditStage effective = stage == null ? AuditStage.SUCCESS : stage;
        return switch (effective) {
            case BEGIN -> "PENDING";
            case SUCCESS -> "SUCCESS";
            case FAIL -> "FAILED";
        };
    }

    private record ActionEnvelope(String moduleKey, String resourceKey, String actionDisplay) {}

    private String normalizeActionCode(String actionCode) {
        if (!StringUtils.hasText(actionCode)) {
            return null;
        }
        return actionCode.trim().toUpperCase(Locale.ROOT);
    }

    private ActionEnvelope deriveActionEnvelope(String normalizedCode) {
        String moduleKey = "general";
        String resourceKey = "general";
        String display = StringUtils.hasText(normalizedCode) ? normalizedCode : "UNKNOWN_ACTION";
        if (!StringUtils.hasText(normalizedCode)) {
            return new ActionEnvelope(moduleKey, resourceKey, display);
        }
        String[] tokens = normalizedCode.split("_");
        if (tokens.length > 0) {
            moduleKey = mapNamespaceToModule(tokens[0]);
            resourceKey = buildResourceKey(moduleKey, tokens);
        }
        return new ActionEnvelope(moduleKey, resourceKey, display);
    }

    private String mapNamespaceToModule(String namespaceToken) {
        if (!StringUtils.hasText(namespaceToken)) {
            return "general";
        }
        String ns = namespaceToken.toLowerCase(Locale.ROOT);
        // Known namespaces collapse to well-defined buckets; otherwise keep lowercase token
        return switch (ns) {
            case "admin", "catalog", "governance", "modeling", "explore", "visualization", "platform" -> ns;
            default -> ns;
        };
    }

    private String buildResourceKey(String moduleKey, String[] tokens) {
        if (tokens.length <= 1) {
            return moduleKey;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(moduleKey);
        builder.append('.');
        if (tokens.length == 2) {
            builder.append(tokens[1].toLowerCase(Locale.ROOT));
            return builder.toString();
        }
        for (int i = 1; i < tokens.length - 1; i++) {
            if (i > 1) {
                builder.append('-');
            }
            builder.append(tokens[i].toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }

    public void record(String actor, String action, String module, String resourceType, String resourceId, String outcome, Object payload) {
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.markDomainAudit();
        record(actor, action, module, resourceType, resourceId, outcome, payload, null);
    }

    public void record(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String outcome,
        Object payload,
        Map<String, Object> extraTags
    ) {
        if (!StringUtils.hasText(actor) || "anonymous".equalsIgnoreCase(actor) || "anonymoususer".equalsIgnoreCase(actor)) {
            return;
        }
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = defaultString(actor, "anonymous");
        event.actorRole = SecurityUtils.getCurrentUserPrimaryAuthority();
        if (!StringUtils.hasText(event.actorRole)) {
            event.actorRole = resolveActorRole(actor);
        }
        event.action = defaultString(action, "UNKNOWN");
        event.module = defaultString(module, "GENERAL");
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = defaultString(outcome, "SUCCESS");
        event.payload = payload;
        event.extraTags = serializeTags(extraTags);
        AuditOperationType opType = AuditOperationType.from(action);
        event.operationType = opType.getCode();
        event.operationTypeText = opType.getDisplayName();
        if (payload instanceof Map<?, ?> mapPayload) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            mapPayload.forEach((k, v) -> {
                if (k != null) {
                    normalized.put(String.valueOf(k), v);
                }
            });
            if (!normalized.isEmpty()) {
                event.details = normalized;
            }
        }
        enrichWithRequestContext(event);
        // capture last entity from current thread (aspect)
        try {
            AuditEntityContext.Payload cap = AuditEntityContext.get();
            if (cap != null) {
                event.capturedTable = cap.tableName;
                event.capturedId = cap.entityId;
            }
        } catch (Exception ignore) {
        } finally {
            try { AuditEntityContext.clear(); } catch (Exception ignore) {}
        }
        logEnqueuedEvent(event);
        offer(event);
    }

    public void record(String actor, String action, String module, String resourceId, String outcome, Object payload) {
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.markDomainAudit();
        record(actor, action, module, module, resourceId, outcome, payload, null);
    }

    public void record(PendingAuditEvent event) {
        if (event == null) return;
        if (!StringUtils.hasText(event.actor) || "anonymous".equalsIgnoreCase(event.actor) || "anonymoususer".equalsIgnoreCase(event.actor)) {
            return;
        }
        if (event.occurredAt == null) {
            event.occurredAt = Instant.now();
        }
        if (!StringUtils.hasText(event.actorRole)) {
            event.actorRole = SecurityUtils.getCurrentUserPrimaryAuthority();
        }
        if (!StringUtils.hasText(event.actorRole)) {
            event.actorRole = resolveActorRole(event.actor);
        }
        if (!StringUtils.hasText(event.operationType)) {
            AuditOperationType inferred = AuditOperationType.from(event.action);
            event.operationType = inferred.getCode();
            if (!StringUtils.hasText(event.operationTypeText)) {
                event.operationTypeText = inferred.getDisplayName();
            }
        }
        if (event.details == null && event.payload instanceof Map<?, ?> payloadMap) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            payloadMap.forEach((k, v) -> {
                if (k != null) {
                    normalized.put(String.valueOf(k), v);
                }
            });
            if (!normalized.isEmpty()) {
                event.details = normalized;
            }
        }
        // If not prefilled, attempt to capture entity snapshot at record time
        if (!StringUtils.hasText(event.capturedId)) {
            try {
                AuditEntityContext.Payload cap = AuditEntityContext.get();
                if (cap != null) {
                    event.capturedTable = cap.tableName;
                    event.capturedId = cap.entityId;
                }
            } catch (Exception ignore) {
            } finally {
                try { AuditEntityContext.clear(); } catch (Exception ignore) {}
            }
        }
        enrichWithRequestContext(event);
        logEnqueuedEvent(event);
        com.yuzhi.dts.admin.service.audit.AuditRequestContext.markDomainAudit();
        offer(event);
    }

    private void offer(PendingAuditEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!queue.offer(event)) {
            log.warn("Audit queue is full, falling back to synchronous write");
            try {
                persist(List.of(event));
            } catch (Exception ex) {
                log.error("Failed to persist audit event synchronously; event will be requeued", ex);
                markFailure(event, ex);
                requeue(List.of(event));
            }
        }
    }

    private void drainQueue() {
        if (!running.get()) {
            return;
        }
        List<PendingAuditEvent> batch = new ArrayList<>();
        queue.drainTo(batch, 256);
        if (batch.isEmpty()) {
            return;
        }
        try {
            persist(batch);
        } catch (Exception ex) {
            handlePersistenceFailure(batch, ex);
        }
    }

    private void handlePersistenceFailure(List<PendingAuditEvent> batch, Exception failure) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        markFailure(batch, failure);
        if (batch.size() == 1) {
            log.error(
                "Failed to persist audit event (action={}, actor={}); scheduling retry",
                batch.get(0) != null ? batch.get(0).action : "unknown",
                batch.get(0) != null ? batch.get(0).actor : "unknown",
                failure
            );
            requeue(batch);
            return;
        }
        log.error(
            "Failed to persist audit batch (size={}); attempting itemized recovery",
            batch.size(),
            failure
        );
        List<PendingAuditEvent> retryable = new ArrayList<>();
        for (PendingAuditEvent event : batch) {
            if (event == null) {
                continue;
            }
            try {
                persist(List.of(event));
            } catch (Exception singleFailure) {
                markFailure(event, singleFailure);
                retryable.add(event);
            }
        }
        if (!retryable.isEmpty()) {
            requeue(retryable);
        }
    }

    private void persist(List<PendingAuditEvent> batch) {
        List<AuditEvent> entities = new ArrayList<>(batch.size());
        String previousChain = lastChainSignature.get();
        for (PendingAuditEvent pending : batch) {
            try {
                AuditEvent entity = buildEntity(pending, previousChain);
                entities.add(entity);
                previousChain = entity.getChainSignature();
            } catch (Exception ex) {
                log.error("Failed to prepare audit event {}", pending.action, ex);
            }
        }
        if (entities.isEmpty()) {
            return;
        }
        repository.saveAll(entities);
        // Clear captured entity context after flush of this batch
        try { com.yuzhi.dts.admin.service.audit.AuditEntityContext.clear(); } catch (Exception ignore) {}
        lastChainSignature.set(previousChain);
        if (log.isDebugEnabled()) {
            AuditEvent last = entities.get(entities.size() - 1);
            log.debug(
                "Persisted {} audit events (module={}, action={}, result={}, queueSize={})",
                entities.size(),
                last.getModule(),
                last.getAction(),
                last.getResult(),
                queue != null ? queue.size() : -1
            );
        }
        if (properties.isForwardEnabled()) {
            for (AuditEvent entity : entities) {
                try {
                    auditClient.enqueue(entity);
                } catch (Exception ex) {
                    log.error("Failed to forward audit event id={} action={}", entity.getId(), entity.getAction(), ex);
                }
            }
        }
    }

    private void requeue(List<PendingAuditEvent> batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (queue == null) {
            log.error("Audit queue unavailable; dropping {} events", batch.size());
            return;
        }
        for (PendingAuditEvent event : batch) {
            if (event == null) {
                continue;
            }
            incrementRetry(event);
            if (event.retryCount > maxRetryAttempts()) {
                dropEvent(event);
                continue;
            }
            enqueueRetry(event);
        }
    }

    private void incrementRetry(PendingAuditEvent event) {
        if (event == null) {
            return;
        }
        event.retryCount = event.retryCount + 1;
        if (event.firstFailureAt == null) {
            event.firstFailureAt = Instant.now();
        }
    }

    private int maxRetryAttempts() {
        int configured = properties.getMaxRetryAttempts();
        return configured <= 0 ? 1 : configured;
    }

    private void enqueueRetry(PendingAuditEvent event) {
        if (queue == null) {
            dropEvent(event);
            return;
        }
        long delay = Math.max(0L, properties.getRetryBackoffMs());
        if (workerPool != null && delay > 0L) {
            long sanitizedDelay = Math.min(delay, TimeUnit.MINUTES.toMillis(1));
            try {
                workerPool.schedule(() -> retryOffer(event), sanitizedDelay, TimeUnit.MILLISECONDS);
                return;
            } catch (Exception ex) {
                log.warn(
                    "Failed to schedule delayed audit retry for action={} actor={}: {}",
                    event.action,
                    event.actor,
                    ex.toString()
                );
            }
        }
        retryOffer(event);
    }

    private void retryOffer(PendingAuditEvent event) {
        if (queue == null) {
            dropEvent(event);
            return;
        }
        boolean offered = queue.offer(event);
        if (!offered) {
            log.warn(
                "Audit queue still full when retrying action={} actor={}, attempt={}",
                event.action,
                event.actor,
                event.retryCount
            );
            incrementRetry(event);
            if (event.retryCount > maxRetryAttempts()) {
                dropEvent(event);
            } else {
                enqueueRetry(event);
            }
        }
    }

    private void dropEvent(PendingAuditEvent event) {
        if (event == null) {
            return;
        }
        log.error(
            "Dropping audit event after {} attempts (actor={}, action={}, module={}, firstFailureAt={}, lastError={}, payloadPreview={})",
            event.retryCount,
            event.actor,
            event.action,
            event.module,
            event.firstFailureAt,
            event.lastError,
            previewPayload(event.payload)
        );
    }

    private String previewPayload(Object payload) {
        if (payload == null) {
            return "null";
        }
        String text = String.valueOf(payload);
        if (text.length() > 500) {
            return text.substring(0, 497) + "...";
        }
        return text;
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
            if (map == null || map.isEmpty()) {
                return Map.of();
            }
            return Collections.unmodifiableMap(new LinkedHashMap<>(map));
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to parse JSON map: {}", ex.toString());
            }
            return Map.of();
        }
    }

    private List<String> parseJsonStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> raw = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String item : raw) {
                if (StringUtils.hasText(item)) {
                    normalized.add(item.trim());
                }
            }
            return normalized.isEmpty() ? List.of() : List.copyOf(normalized);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to parse JSON list: {}", ex.toString());
            }
            return List.of();
        }
    }

    private String asText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String localizeStatus(String statusText) {
        if (!StringUtils.hasText(statusText)) {
            return statusText;
        }
        String normalized = statusText.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "APPROVED", "SUCCESS", "APPLIED", "COMPLETED" -> "通过";
            case "REJECTED", "DENIED" -> "驳回";
            case "DELAYED", "PROCESS", "PROCESSING" -> "延后";
            case "FAILED", "FAILURE", "ERROR" -> "失败";
            case "PENDING", "WAITING", "SUBMITTED", "APPROVAL_PENDING", "BEGIN" -> "待处理";
            case "CANCELLED", "CANCELED" -> "撤回";
            default -> statusText;
        };
    }

    private void markFailure(List<PendingAuditEvent> events, Throwable failure) {
        if (events == null) {
            return;
        }
        for (PendingAuditEvent event : events) {
            markFailure(event, failure);
        }
    }

    private void markFailure(PendingAuditEvent event, Throwable failure) {
        if (event == null || failure == null) {
            return;
        }
        if (event.firstFailureAt == null) {
            event.firstFailureAt = Instant.now();
        }
        event.lastError = summarizeException(failure);
    }

    private String summarizeException(Throwable failure) {
        if (failure == null) {
            return "unknown";
        }
        Throwable root = failure;
        int guard = 0;
        while (root.getCause() != null && guard++ < 8) {
            root = root.getCause();
        }
        String type = root.getClass().getSimpleName();
        String message = root.getMessage();
        if ((message == null || message.isBlank()) && root != failure) {
            message = failure.getMessage();
        }
        if (message != null && message.length() > 280) {
            message = message.substring(0, 277) + "...";
        }
        if (message == null || message.isBlank()) {
            return type;
        }
        return type + ": " + message;
    }

    private AuditEvent buildEntity(PendingAuditEvent pending, String previousChain) throws JsonProcessingException {
        AuditEvent entity = new AuditEvent();
        entity.setOccurredAt(pending.occurredAt);
        entity.setActor(defaultString(pending.actor, "anonymous"));
        entity.setActorRole(pending.actorRole);
        if (StringUtils.hasText(pending.actorName)) {
            entity.setActorName(pending.actorName);
        }
        // source_system：默认admin，可由调用方覆盖（平台转发时置为platform）
        String src = pending.sourceSystem;
        if (src == null || src.isBlank()) src = "admin";
        entity.setSourceSystem(src);

        String baseModule = defaultString(pending.module, "GENERAL");
        String baseAction = defaultString(pending.action, "UNKNOWN");

        OperationMappingEngine.ResolvedOperation ruleResolution = null;
        // Rule Engine Integration
        try {
            AuditEvent forEngine = new AuditEvent();
            forEngine.setRequestUri(pending.requestUri);
            forEngine.setHttpMethod(pending.httpMethod);
            forEngine.setSourceSystem(entity.getSourceSystem());
            forEngine.setResult(pending.result);
            forEngine.setModule(pending.module);
            forEngine.setResourceType(pending.resourceType);
            forEngine.setAction(pending.action);

            ruleResolution = operationMappingEngine.resolveWithFallback(forEngine).orElse(null);
            if (ruleResolution != null) {
                entity.setOperationGroup(ruleResolution.operationGroup);
                if (ruleResolution.operationType != null) {
                    entity.setOperationType(ruleResolution.operationType.getCode());
                }
                if (StringUtils.hasText(ruleResolution.moduleName)) {
                    entity.setModule(ruleResolution.moduleName);
                }
                if (StringUtils.hasText(ruleResolution.description)) {
                    entity.setSummary(ruleResolution.description);
                }
                if (StringUtils.hasText(ruleResolution.description) && !StringUtils.hasText(entity.getAction())) {
                    entity.setAction(ruleResolution.description);
                }
            }
        } catch (Exception ex) {
            log.error("Error during audit rule engine resolution", ex);
        }

        if (StringUtils.hasText(pending.moduleLabel) && !StringUtils.hasText(entity.getModuleLabel())) {
            entity.setModuleLabel(pending.moduleLabel);
        }
        if (StringUtils.hasText(pending.operationGroup) && !StringUtils.hasText(entity.getOperationGroup())) {
            entity.setOperationGroup(pending.operationGroup);
        }
        if (StringUtils.hasText(pending.operationCode) && !StringUtils.hasText(entity.getOperationCode())) {
            entity.setOperationCode(pending.operationCode);
        }
        if (StringUtils.hasText(pending.operationName) && !StringUtils.hasText(entity.getOperationName())) {
            entity.setOperationName(pending.operationName);
        }
        if (StringUtils.hasText(pending.operationType)) {
            entity.setOperationType(pending.operationType);
        }
        if (StringUtils.hasText(pending.operationTypeText)) {
            entity.setOperationTypeText(pending.operationTypeText);
        }
        if (StringUtils.hasText(pending.summary) && !StringUtils.hasText(entity.getSummary())) {
            entity.setSummary(pending.summary);
        }

        if (!StringUtils.hasText(entity.getModule())) {
            entity.setModule(baseModule);
        }
        if (!StringUtils.hasText(entity.getAction())) {
            entity.setAction(baseAction);
        }

        entity.setEventUuid(java.util.UUID.randomUUID());
        // event_class/event_type 简化映射：安全相关 action -> SecurityEvent；其余 -> AuditEvent
        String normalizedAction = entity.getAction() == null ? "" : entity.getAction().toUpperCase(java.util.Locale.ROOT);
        boolean security = normalizedAction.contains("AUTH_") || normalizedAction.contains("LOGIN") || normalizedAction.contains("LOGOUT") || normalizedAction.contains("ACCESS_DENIED");
        if (!StringUtils.hasText(entity.getEventClass())) {
            entity.setEventClass(security ? "SecurityEvent" : "AuditEvent");
        }
        AuditOperationType operationType = AuditOperationType.from(entity.getOperationType());
        AuditEntityContext.Payload contextPayload = AuditEntityContext.get();
        String ruleSourceRaw = ruleResolution != null ? ruleResolution.sourceTable : null;
        String contextTableRaw = contextPayload.tableName;
        String explicitTableRaw = pending.capturedTable;
        String resourceTypeRaw = pending.resourceType;
        String moduleRaw = pending.module;

        if (pending.target != null) {
            AuditTarget target = pending.target;
            if (StringUtils.hasText(target.table())) {
                entity.setTargetTable(target.table());
                if (!StringUtils.hasText(entity.getResourceType())) {
                    entity.setResourceType(target.table());
                }
            }
            if (!target.ids().isEmpty()) {
                entity.setTargetIds(objectMapper.writeValueAsString(target.ids()));
                entity.setTargetIdText(String.join(",", target.ids()));
                if (!StringUtils.hasText(entity.getResourceId())) {
                    entity.setResourceId(target.ids().get(0));
                }
            }
            if (!target.labels().isEmpty()) {
                entity.setTargetLabels(objectMapper.writeValueAsString(target.labels()));
            }
            if (!target.snapshot().isEmpty()) {
                entity.setTargetSnapshot(objectMapper.writeValueAsString(target.snapshot()));
            }
        }
        resourceTypeRaw = entity.getResourceType();

        String ruleTable = sanitizeTableName(ruleSourceRaw);
        String contextTable = sanitizeTableName(contextTableRaw);
        String explicitTable = sanitizeTableName(explicitTableRaw);
        String fallbackTable = sanitizeTableName(resourceTypeRaw);
        String moduleTable = sanitizeTableName(moduleRaw);
        String contextId = sanitizeId(contextPayload.entityId);
        String explicitId = sanitizeId(pending.capturedId);
        String fallbackId = sanitizeId(pending.resourceId);

        String resolvedId = firstNonBlank(contextId, explicitId, fallbackId);
        String dictionaryFromRule = dictionaryCanonicalTable(ruleSourceRaw, resourceTypeRaw, contextTableRaw, moduleRaw);
        String dictionaryFromResource = dictionaryCanonicalTable(resourceTypeRaw, ruleSourceRaw, contextTableRaw, moduleRaw);
        String dictionaryFromContext = dictionaryCanonicalTable(contextTableRaw, resourceTypeRaw, ruleSourceRaw, moduleRaw);
        String dictionaryFromModule = dictionaryCanonicalTable(moduleRaw, resourceTypeRaw, ruleSourceRaw);
        String resolvedTable = selectTable(
            resolvedId,
            ruleTable,
            contextTable,
            explicitTable,
            dictionaryFromRule,
            dictionaryFromResource,
            dictionaryFromContext,
            dictionaryFromModule,
            fallbackTable,
            moduleTable
        );
        if (operationType.requiresTarget() && (!StringUtils.hasText(resolvedTable) || !StringUtils.hasText(resolvedId))) {
            log.warn(
                "Missing precise audit target for module={} action={} type={} uri={}",
                pending.module,
                pending.action,
                operationType.getCode(),
                pending.requestUri
            );
        }
        entity.setResourceType(StringUtils.hasText(resolvedTable) ? resolvedTable : null);
        entity.setResourceId(StringUtils.hasText(resolvedId) ? resolvedId : null);
        entity.setClientIp(parseClientIp(pending.clientIp));
        entity.setClientAgent(pending.clientAgent);
        entity.setRequestUri(pending.requestUri);
        entity.setHttpMethod(pending.httpMethod);
        entity.setResult(defaultString(pending.result, "SUCCESS"));
        entity.setLatencyMs(pending.latencyMs);
        entity.setExtraTags(normalizeExtraTags(pending.extraTags));

        // operator_* 与组织信息
        String username = entity.getActor();
        entity.setOperatorId(username);
        var snapshot = (username == null) ? java.util.Optional.<com.yuzhi.dts.admin.domain.AdminKeycloakUser>empty() : userRepo.findByUsernameIgnoreCase(username);
        entity.setOperatorName(snapshot.map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::getFullName).orElse(username));
        java.util.List<String> roles = SecurityUtils.getCurrentUserAuthorities();
        java.util.LinkedHashSet<String> filtered = new java.util.LinkedHashSet<>();
        for (String r : roles) {
            if (r == null || r.isBlank()) continue;
            String norm = SecurityUtils.normalizeRole(r);
            if (norm.startsWith("DEPT_DATA_") || norm.startsWith("INST_DATA_")) filtered.add(norm);
        }
        try { entity.setOperatorRoles(objectMapper.writeValueAsString(new java.util.ArrayList<>(filtered))); } catch (Exception ignored) {}
        // org_code 从用户属性（dept_code）推断：本地快照缺省无该字段，尝试从 group path 末级名作为占位
        String orgCode = null;
        if (snapshot.isPresent()) {
            // Prefer Optional.orElse* chain to avoid Optional.get
            java.util.List<String> gps = snapshot
                .map(com.yuzhi.dts.admin.domain.AdminKeycloakUser::getGroupPaths)
                .orElse(null);
            if (gps != null && !gps.isEmpty()) {
                String p = gps.get(0);
                if (p != null && !p.isBlank()) {
                    String[] seg = p.split("/");
                    orgCode = seg.length > 0 ? seg[seg.length - 1] : p; // 占位：用末级组名作为 org_code 占位
                }
            }
        }
        entity.setOrgCode(orgCode);
        // 通过 org_code（dept_code）查组织库，获取中文组织名称
        // 口径：dept_code 等同于 admin 组织的 ID（字符串），因此可按 Long 解析后查找
        String orgName = null;
        if (orgCode != null && !orgCode.isBlank()) {
            try {
                Long orgId = Long.parseLong(orgCode.trim());
                var orgOpt = organizationRepository.findById(orgId);
                // Prefer Optional.orElse* instead of get()
                orgName = orgOpt.map(com.yuzhi.dts.admin.domain.OrganizationNode::getName).orElse(null);
            } catch (NumberFormatException ignore) {
                // 非数字的 org_code，保留为空；前端可能从路径叶子展示
            } catch (Exception ex) {
                log.debug("Failed to resolve org name by org_code {}: {}", orgCode, ex.toString());
            }
        }
        entity.setOrgName(orgName);

        // 详情：中文键
        java.util.Map<String, Object> det = new java.util.LinkedHashMap<>();
        if (pending.details != null && !pending.details.isEmpty()) {
            pending.details.forEach((k, v) -> {
                if (k != null) {
                    det.put(String.valueOf(k), v);
                }
            });
        }
        if (StringUtils.hasText(resolvedTable)) {
            det.put("源表", resolvedTable);
            String tableLabel = mapTableLabel(resolvedTable);
            if (StringUtils.hasText(tableLabel) && !Objects.equals(tableLabel, resolvedTable)) {
                det.put("源表描述", tableLabel);
            }
        }
        if (StringUtils.hasText(resolvedId)) {
            det.put("目标ID", resolvedId);
        }
        if (operationType != AuditOperationType.UNKNOWN) {
            det.put("操作类型", operationType.getDisplayName());
        }
        if (pending.payload instanceof java.util.Map<?, ?> payloadMap) {
            String targetRef = firstNonBlank(
                coerceToString(payloadMap.get("target_ref")),
                coerceToString(payloadMap.get("targetRef")),
                coerceToString(payloadMap.get("username")),
                coerceToString(payloadMap.get("userName")),
                coerceToString(payloadMap.get("fullName")),
                coerceToString(payloadMap.get("name"))
            );
            String datasetName = coerceToString(payloadMap.get("datasetName"));
            if (!StringUtils.hasText(targetRef)) {
                targetRef = datasetName;
            }
            if (!StringUtils.hasText(targetRef)) {
                targetRef = coerceToString(pending.resourceId);
            }
            if (StringUtils.hasText(targetRef) && !det.containsKey("目标引用")) {
                det.put("目标引用", targetRef);
            }
            if (StringUtils.hasText(datasetName)) {
                det.put("datasetName", datasetName);
            }
            String datasetDomain = coerceToString(payloadMap.get("datasetDomain"));
            if (StringUtils.hasText(datasetDomain)) {
                det.put("datasetDomain", datasetDomain);
            }
            String datasetClassification = coerceToString(payloadMap.get("datasetClassification"));
            if (StringUtils.hasText(datasetClassification)) {
                det.put("datasetClassification", datasetClassification);
            }
            String datasetOwner = coerceToString(payloadMap.get("datasetOwner"));
            if (StringUtils.hasText(datasetOwner)) {
                det.put("datasetOwner", datasetOwner);
            }
            String datasetOwnerDept = coerceToString(payloadMap.get("datasetOwnerDept"));
            if (StringUtils.hasText(datasetOwnerDept)) {
                det.put("datasetOwnerDept", datasetOwnerDept);
            }
            String changeRequestRef = coerceToString(payloadMap.get("changeRequestRef"));
            if (!StringUtils.hasText(changeRequestRef)) {
                String changeRequestId = coerceToString(payloadMap.get("changeRequestId"));
                if (StringUtils.hasText(changeRequestId)) {
                    String trimmed = changeRequestId.trim();
                    if (trimmed.toUpperCase(Locale.ROOT).startsWith("CR-")) {
                        changeRequestRef = trimmed;
                    } else {
                        changeRequestRef = "CR-" + trimmed;
                    }
                }
            }
            if (StringUtils.hasText(changeRequestRef)) {
                det.put("审批单号", changeRequestRef);
                det.put("目标引用", changeRequestRef);
            }
            String approvalSummary = firstNonBlank(
                coerceToString(payloadMap.get("approvalSummary")),
                coerceToString(payloadMap.get("summary")),
                coerceToString(payloadMap.get("reason"))
            );
            if (StringUtils.hasText(approvalSummary)) {
                String trimmedSummary = approvalSummary.trim();
                if (StringUtils.hasText(trimmedSummary)) {
                    det.put("审批内容", trimmedSummary);
                }
            }
        }
        // Expose HTTP status code for downstream rule matching (e.g., login success vs failure).
        if (pending.payload instanceof java.util.Map<?, ?> payloadMap) {
            Object statusObj = payloadMap.get("status");
            if (statusObj == null) statusObj = payloadMap.get("httpStatus");
            if (statusObj == null) statusObj = payloadMap.get("http_status");
            if (statusObj != null) {
                String statusText = String.valueOf(statusObj).trim();
                if (!statusText.isEmpty()) {
                    det.put("状态码", localizeStatus(statusText));
                }
            }
        }

        String actorRoleDisplay = translateActorRole(entity.getActorRole(), entity.getActor());
        if (actorRoleDisplay != null) {
            det.put("操作者角色", actorRoleDisplay);
        }

        det.put("请求ID", extractRequestId());
        if ((!det.containsKey("目标ID")) || det.get("目标ID") == null || String.valueOf(det.get("目标ID")).isBlank()) {
            java.util.UUID uuid = entity.getEventUuid();
            det.put("目标ID", uuid != null ? uuid.toString() : java.util.UUID.randomUUID().toString());
        }
        // 参数摘要（哈希）
        // param_digest 写入后以中文键暴露为“参数摘要”

        byte[] payloadBytes = pending.payload == null ? new byte[0] : objectMapper.writeValueAsBytes(pending.payload);
        String payloadDigest = sha256Hex(payloadBytes); // first phase: no key/HMAC, just SHA-256
        String chainSignature = sha256Hex((previousChain + "|" + payloadDigest).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        det.put("参数摘要", payloadDigest);

        // Add before/after states from AOP context
        try {
            if (contextPayload.before != null) {
                det.put("before", objectMapper.convertValue(contextPayload.before, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
            if (contextPayload.after != null) {
                det.put("after", objectMapper.convertValue(contextPayload.after, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
            }
        } catch (Exception e) {
            log.warn("Failed to serialize before/after state for audit", e);
        }

        try { entity.setDetails(objectMapper.writeValueAsString(det)); } catch (Exception ignored) {}

        entity.setPayloadIv(null);
        entity.setPayloadCipher(null);
        entity.setPayloadHmac(payloadDigest);
        entity.setChainSignature(chainSignature);
        entity.setRecordSignature(simpleRecordSignature(entity, payloadDigest));
        entity.setSignatureKeyVer(null);
        // Localize operatorName for built-in admins to improve readability
        if (entity.getActor() != null) {
            String a = entity.getActor().trim().toLowerCase(java.util.Locale.ROOT);
            if (a.equals("sysadmin")) entity.setOperatorName("系统管理员");
            else if (a.equals("authadmin")) entity.setOperatorName("授权管理员");
            else if (a.equals("auditadmin")) entity.setOperatorName("安全审计员");
        }
        if (!StringUtils.hasText(entity.getSummary())) {
            entity.setSummary(buildSummary(entity));
        }
        if (!StringUtils.hasText(entity.getEventType())) {
            String categoryResource = StringUtils.hasText(entity.getResourceType()) ? entity.getResourceType() : pending.resourceType;
            String categoryModule = StringUtils.hasText(entity.getModule()) ? entity.getModule() : pending.module;
            entity.setEventType(mapEventCategory(categoryResource, categoryModule, normalizedAction));
        }
        entity.setCreatedBy(entity.getActor());
        entity.setCreatedDate(Instant.now());
        return entity;
    }

    private String mapEventCategory(String resourceType, String module, String actionUpper) {
        String a = actionUpper == null ? "" : actionUpper;
        if (a.contains("LOGIN") || a.contains("LOGOUT") || (a.contains("ACCESS") && a.contains("DENIED"))) {
            return "登录管理";
        }
        Optional<String> category = resourceDictionary.resolveCategory(resourceType);
        if (category.isPresent()) {
            return category.orElseThrow();
        }
        Optional<String> moduleCategory = resourceDictionary.resolveCategory(module);
        if (moduleCategory.isPresent()) {
            return moduleCategory.orElseThrow();
        }
        String r = resourceType == null ? null : resourceType.trim().toLowerCase(java.util.Locale.ROOT);
        String m = module == null ? null : module.trim().toLowerCase(java.util.Locale.ROOT);
        String key = r != null && !r.isBlank() ? r : (m == null ? "" : m);
        if (key.isBlank()) return "数据资产";
        if (key.startsWith("iam") || key.equals("iam_permission") || key.equals("iam_dataset_policy") || key.equals("iam_user_classification")) {
            return "角色管理";
        }
        if (key.equals("admin.auth") || key.equals("auth")) return "登录管理";
        if (key.equals("admin_keycloak_user") || key.equals("user") || key.equals("admin") || key.startsWith("admin.user") || key.startsWith("admin.users")) return "用户管理";
        if (key.startsWith("role") || key.startsWith("admin.role") || key.equals("admin_role_assignment")) return "角色管理";
        if (key.equals("portal_menu") || key.equals("menu") || key.startsWith("portal.menus") || key.startsWith("portal-menus") || key.equals("portal.navigation")) return "菜单管理";
        if (key.startsWith("org") || key.startsWith("organization") || key.equals("organization_node") || key.startsWith("admin.org")) return "部门管理";
        if (key.startsWith("modeling.standard") || key.startsWith("data_standard")) return "数据标准";
        if (key.startsWith("governance") || key.startsWith("gov_")) return "数据质量";
        if (key.startsWith("explore")) return "数据开发";
        if (key.startsWith("visualization")) return "数据可视化";
        if (key.equals("catalog_dataset_job") || key.contains("schedule")) return "数据开发";
        if (key.startsWith("catalog") || key.equals("catalog_table_schema") || key.equals("catalog_secure_view") || key.equals("catalog_row_filter_rule") || key.equals("catalog_access_policy")) return "数据资产";
        if (key.startsWith("svc") || key.startsWith("api")) return "数据资产";
        return "数据资产";
    }

    private String sanitizeTableName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized.isBlank() ? null : normalized;
    }

    private String sanitizeId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String extractRequestId() {
        return java.util.UUID.randomUUID().toString();
    }

    private String simpleRecordSignature(AuditEvent e, String payloadHmac) {
        String base = String.join("|",
            nullToEmpty(e.getSourceSystem()),
            nullToEmpty(e.getActor()),
            nullToEmpty(e.getAction()),
            nullToEmpty(e.getModule()),
            nullToEmpty(e.getResourceType()),
            nullToEmpty(e.getResourceId()),
            nullToEmpty(payloadHmac),
            e.getOccurredAt() == null ? "" : e.getOccurredAt().toString()
        );
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(data);
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private String buildSummary(AuditEvent e) {
        String name = e.getOperatorName() != null && !e.getOperatorName().isBlank() ? e.getOperatorName() : e.getActor();
        String actionDesc = e.getEventType() != null ? e.getEventType() : e.getAction();
        String resultText = (e.getResult() != null && e.getResult().equalsIgnoreCase("SUCCESS")) ? "成功" : (e.getResult() == null ? "" : "失败");
        // 从详情提取中文信息，避免泄露主键；优先 "目标引用"，不显示 ID
        String targetTable = null;
        String targetId = null;
        String targetRef = null;
        try {
            if (e.getDetails() != null && !e.getDetails().isBlank()) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getDetails());
                if (node.hasNonNull("源表")) targetTable = node.get("源表").asText();
                if (node.hasNonNull("目标引用")) targetRef = node.get("目标引用").asText();
                if (targetTable == null && node.hasNonNull("target_table")) targetTable = node.get("target_table").asText();
                if (targetRef == null && node.hasNonNull("target_ref")) targetRef = node.get("target_ref").asText();
                if (node.hasNonNull("目标ID")) targetId = node.get("目标ID").asText();
                if (targetId == null && node.hasNonNull("target_id")) targetId = node.get("target_id").asText();
            }
        } catch (Exception ignore) {}
        String method = e.getHttpMethod() == null ? "" : e.getHttpMethod().trim().toUpperCase(java.util.Locale.ROOT);
        boolean isListQuery = "GET".equals(method) && (targetId == null || targetId.isBlank());
        String tableLabel = mapTableLabel(targetTable != null ? targetTable : e.getResourceType());
        String targetText = "";
        if (tableLabel != null && hasChinese(tableLabel)) {
            if (isListQuery) {
                targetText = tableLabel + "列表";
            } else if (targetRef != null && hasChinese(targetRef)) {
                targetText = tableLabel + "（" + targetRef + "）";
            } else {
                targetText = tableLabel;
            }
        } else if (targetRef != null && hasChinese(targetRef)) {
            targetText = targetRef; // 仅展示中文引用
        }
        String suffix = targetText.isBlank() ? "" : (" " + targetText);
        return String.format("用户【%s】%s【%s】%s", nullToEmpty(name), nullToEmpty(resultText), nullToEmpty(actionDesc), suffix);
    }

    private boolean hasChinese(String s) {
        if (s == null || s.isBlank()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u4e00' && c <= '\u9fa5') return true;
        }
        return false;
    }

    private String mapTableLabel(String key) {
        if (!StringUtils.hasText(key)) {
            return "通用";
        }
        String trimmed = key.trim();
        return resourceDictionary.resolveLabel(trimmed).orElse(trimmed);
    }

    public Page<AuditEvent> search(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.search(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            effectivePageable
        );
    }

    public Page<AuditEvent> searchAllowedActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        List<String> allowedActors,
        Pageable pageable
    ) {
        List<String> normalizedAllowed = normalizeActorList(allowedActors);
        if (normalizedAllowed.isEmpty()) {
            return Page.empty(pageable == null ? Pageable.unpaged() : pageable);
        }
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchAllowedActors(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            normalizedAllowed,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchAllowedRoles(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> roles,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchAllowedRoles(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            roles,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchExcludeActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        List<String> excludedActors,
        Pageable pageable
    ) {
        List<String> normalizedExcluded = normalizeActorList(excludedActors);
        if (normalizedExcluded.isEmpty()) {
            return search(actor, module, action, sourceSystem, eventType, result, resourceType, resource, requestUri, from, to, clientIp, operationGroup, pageable);
        }
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchExcludeActors(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            normalizedExcluded,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchAllowedRolesExcludeActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> roles,
        java.util.List<String> excludedActors,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchAllowedRolesExcludeActors(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            roles,
            excludedActors,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchExcludeRole(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        String excludedRole,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchExcludeRole(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            excludedRole,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchExcludeRoles(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> excludedRoles,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchExcludeRoles(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            excludedRoles,
            effectivePageable
        );
    }

    public Page<AuditEvent> searchExcludeRolesExcludeActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> excludedRoles,
        java.util.List<String> excludedActors,
        Pageable pageable
    ) {
        Pageable effectivePageable = pageable;
        if (pageable != null && pageable.getSort().isSorted()) {
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }
        return repository.searchExcludeRolesExcludeActors(
            likePattern(actor),
            likePattern(module),
            likePattern(action),
            likePattern(sourceSystem),
            likePattern(eventType),
            likePattern(result),
            likePattern(resourceType),
            likePattern(resource),
            likePattern(requestUri),
            from,
            to,
            likePattern(clientIp),
            likePattern(operationGroup),
            excludedRoles,
            excludedActors,
            effectivePageable
        );
    }

    public List<AuditEventView> list(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp
    ) {
        return search(actor, module, action, sourceSystem, eventType, result, resourceType, resource, requestUri, from, to, clientIp, null, Pageable.unpaged())
            .getContent()
            .stream()
            .map(this::toView)
            .toList();
    }

    public Optional<AuditEvent> findById(Long id) {
        return repository.findById(id);
    }

    public List<AuditEvent> findAllForExport(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup
    ) {
        return repository
            .search(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                Pageable.unpaged()
            )
            .getContent();
    }

    public List<AuditEvent> findAllForExportAllowedRoles(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> roles
    ) {
        return repository
            .searchAllowedRoles(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                roles,
                Pageable.unpaged()
            )
            .getContent();
    }

    public List<AuditEvent> findAllForExportAllowedRolesExcludeActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> roles,
        java.util.List<String> excludedActors
    ) {
        return repository
            .searchAllowedRolesExcludeActors(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                roles,
                excludedActors,
                Pageable.unpaged()
            )
            .getContent();
    }

    public List<AuditEvent> findAllForExportExcludeRole(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        String excludedRole
    ) {
        Page<AuditEvent> page = repository
            .searchExcludeRole(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                excludedRole,
                Pageable.unpaged()
            );
        return page.getContent();
    }

    public List<AuditEvent> findAllForExportExcludeRoles(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> excludedRoles
    ) {
        Page<AuditEvent> page = repository
            .searchExcludeRoles(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                excludedRoles,
                Pageable.unpaged()
            );
        return page.getContent();
    }

    public List<AuditEvent> findAllForExportExcludeRolesExcludeActors(
        String actor,
        String module,
        String action,
        String sourceSystem,
        String eventType,
        String result,
        String resourceType,
        String resource,
        String requestUri,
        Instant from,
        Instant to,
        String clientIp,
        String operationGroup,
        java.util.List<String> excludedRoles,
        java.util.List<String> excludedActors
    ) {
        Page<AuditEvent> page = repository
            .searchExcludeRolesExcludeActors(
                likePattern(actor),
                likePattern(module),
                likePattern(action),
                likePattern(sourceSystem),
                likePattern(eventType),
                likePattern(result),
                likePattern(resourceType),
                likePattern(resource),
                likePattern(requestUri),
                from,
                to,
                likePattern(clientIp),
                likePattern(operationGroup),
                excludedRoles,
                excludedActors,
                Pageable.unpaged()
            );
        return page.getContent();
    }

    public long purgeAll() {
        long removed = repository.count();
        if (removed == 0) {
            if (queue != null) {
                queue.clear();
            }
            lastChainSignature.set("");
            return 0;
        }
        repository.deleteAllInBatch();
        repository.flush();
        if (queue != null) {
            queue.clear();
        }
        lastChainSignature.set("");
        return removed;
    }

    public byte[] decryptPayload(AuditEvent event) {
        if (event.getPayloadCipher() == null || event.getPayloadCipher().length == 0) {
            return new byte[0];
        }
        try {
            return AuditCrypto.decrypt(event.getPayloadCipher(), encryptionKey, event.getPayloadIv());
        } catch (Exception ex) {
            // Backward compatibility: if legacy rows were stored with a different key/format,
            // do not break listing; return empty to omit preview/details silently.
            log.debug("decryptPayload failed for id {}: {}", event.getId(), ex.toString());
            return new byte[0];
        }
    }

    private AuditEventView assembleAuditView(AuditEvent event) {
        AuditEventView view = new AuditEventView();
        view.id = event.getId() == null ? 0L : event.getId();
        view.eventId = event.getEventUuid() != null ? event.getEventUuid().toString() : null;
        view.occurredAt = event.getOccurredAt();
        view.actor = event.getActor();
        view.actorRole = event.getActorRole();
        view.actorName = event.getActorName();
        view.module = event.getModule();
        view.moduleLabel = StringUtils.hasText(event.getModuleLabel())
            ? event.getModuleLabel()
            : resourceDictionary.resolveLabel(event.getModule()).orElse(event.getModule());
        view.action = event.getAction();
        view.resourceType = event.getResourceType();
        view.resourceId = event.getResourceId();
        view.clientIp = event.getClientIp() == null ? null : event.getClientIp().getHostAddress();
        view.clientAgent = event.getClientAgent();
        view.httpMethod = event.getHttpMethod();
        view.result = event.getResult();
        view.resultText = localizeStatus(event.getResult());
        view.extraTags = event.getExtraTags();
        view.sourceSystem = event.getSourceSystem();
        view.sourceSystemText = "platform".equalsIgnoreCase(view.sourceSystem) ? "业务端审计" : "管理端审计";
        view.eventClass = event.getEventClass();
        view.eventType = event.getEventType();
        view.operationCode = event.getOperationCode();
        view.operationGroup = event.getOperationGroup();
        view.operationName = StringUtils.hasText(event.getOperationName()) ? event.getOperationName() : event.getAction();
        AuditOperationType operationType = AuditOperationType.from(event.getOperationType());
        view.operationTypeCode = operationType.getCode();
        view.operationTypeText = StringUtils.hasText(event.getOperationTypeText()) ? event.getOperationTypeText() : operationType.getDisplayName();
        view.operationContent = view.operationName;
        view.summary = event.getSummary();
        if (StringUtils.hasText(view.summary)) {
            view.operationContent = view.summary;
        }
        view.operationModule = event.getModule();
        view.operationSourceTable = event.getResourceType();
        view.operatorId = event.getOperatorId();
        view.operatorName = event.getOperatorName();
        view.operatorRoles = parseJsonStringList(event.getOperatorRoles());
        view.orgCode = event.getOrgCode();
        view.orgName = event.getOrgName();
        view.departmentName = event.getDepartmentName();
        view.targetTable = StringUtils.hasText(event.getTargetTable()) ? event.getTargetTable() : event.getResourceType();
        String targetTableLabel = view.targetTable == null
            ? null
            : resourceDictionary.resolveLabel(view.targetTable).orElse(view.targetTable);
        view.targetTableLabel = targetTableLabel;
        view.targetIds = parseJsonStringList(event.getTargetIds());
        view.targetLabels = parseJsonMap(event.getTargetLabels());
        view.targetSnapshot = parseJsonMap(event.getTargetSnapshot());
        view.targetId = StringUtils.hasText(event.getResourceId())
            ? event.getResourceId()
            : (view.targetIds.isEmpty() ? null : view.targetIds.get(0));
        Map<String, Object> details = parseJsonMap(event.getDetails());
        view.details = details;
        view.requestId = firstNonBlank(
            asText(details.get("请求ID")),
            asText(details.get("requestId")),
            asText(details.get("request_id"))
        );
        view.changeRequestRef = firstNonBlank(
            asText(details.get("审批单号")),
            asText(details.get("changeRequestRef")),
            asText(details.get("change_request_ref"))
        );
        view.approvalSummary = firstNonBlank(
            asText(details.get("审批内容")),
            asText(details.get("approvalSummary"))
        );
        if (StringUtils.hasText(view.approvalSummary)) {
            view.operationContent = view.approvalSummary;
        }
        view.targetRef = firstNonBlank(
            event.getTargetIdText(),
            asText(details.get("目标引用")),
            view.changeRequestRef,
            view.targetId
        );
        if (view.targetRef == null && !view.targetIds.isEmpty()) {
            view.targetRef = view.targetIds.get(0);
        }
        return view;
    }

    public AuditEventView toView(AuditEvent event) {
        return toView(event, true);
    }

    public AuditEventView toView(AuditEvent event, boolean includeDetails) {
        AuditEventView view = assembleAuditView(event);
        if (!includeDetails) {
            view.details = Map.of();
            view.targetSnapshot = Map.of();
            view.targetLabels = Map.of();
        }
        return view;
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldEvents() {
        Instant threshold = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
        int purged = repository.deleteAllByOccurredAtBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} audit events older than {}", purged, threshold);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> normalizeActorList(List<String> actors) {
        if (actors == null || actors.isEmpty()) {
            return List.of();
        }
        java.util.LinkedHashSet<String> normalized = new java.util.LinkedHashSet<>();
        for (String actor : actors) {
            if (!StringUtils.hasText(actor)) {
                continue;
            }
            normalized.add(actor.trim().toLowerCase(Locale.ROOT));
        }
        return List.copyOf(normalized);
    }

    private String likePattern(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "%";
        }
        return "%" + escapeLike(normalized) + "%";
    }

    private String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }

    private void logEnqueuedEvent(PendingAuditEvent event) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
            "Queue audit event actor={} module={} action={} result={} resource={} queueSize={}",
            defaultString(event.actor, "anonymous"),
            defaultString(event.module, "GENERAL"),
            defaultString(event.action, "UNKNOWN"),
            defaultString(event.result, "SUCCESS"),
            event.resourceId,
            queue != null ? queue.size() : 0
        );
    }

    private void enrichWithRequestContext(PendingAuditEvent event) {
        if (event == null) {
            return;
        }
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (!(attrs instanceof ServletRequestAttributes servletAttributes)) {
                return;
            }
            HttpServletRequest request = servletAttributes.getRequest();
            if (request == null) {
                return;
            }
            if (!StringUtils.hasText(event.requestUri)) {
                String uri = request.getRequestURI();
                if (StringUtils.hasText(uri)) {
                    event.requestUri = uri;
                }
            }
            if (!StringUtils.hasText(event.httpMethod)) {
                String method = request.getMethod();
                if (StringUtils.hasText(method)) {
                    event.httpMethod = method.toUpperCase(java.util.Locale.ROOT);
                }
            }
            if (!StringUtils.hasText(event.clientIp)) {
                String clientIp = extractClientIp(request);
                if (StringUtils.hasText(clientIp)) {
                    event.clientIp = clientIp;
                }
            }
            if (!StringUtils.hasText(event.clientAgent)) {
                String agent = request.getHeader("User-Agent");
                if (StringUtils.hasText(agent)) {
                    event.clientAgent = agent;
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) {
                log.debug("Failed to enrich audit event with request context: {}", ex.toString());
            }
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            for (String part : forwarded.split(",")) {
                String sanitized = sanitizeIpCandidate(part);
                if (sanitized != null) {
                    candidates.add(sanitized);
                }
            }
        }
        String realIp = sanitizeIpCandidate(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            candidates.add(realIp);
        }
        String remote = sanitizeIpCandidate(request.getRemoteAddr());
        if (remote != null) {
            candidates.add(remote);
        }
        if (candidates.isEmpty()) {
            return null;
        }
        for (String candidate : candidates) {
            if (isPublicAddress(candidate)) {
                return normalizeLoopback(candidate);
            }
        }
        for (String candidate : candidates) {
            if (!isLoopbackOrUnspecified(candidate)) {
                return normalizeLoopback(candidate);
            }
        }
        return normalizeLoopback(candidates.get(0));
    }

    private String sanitizeIpCandidate(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeLoopback(String ip) {
        if (!StringUtils.hasText(ip)) {
            return null;
        }
        String value = ip.trim();
        if ("::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) {
            return "127.0.0.1";
        }
        if (value.startsWith("::ffff:")) {
            return value.substring(7);
        }
        return value;
    }

    private boolean isLoopbackOrUnspecified(String ip) {
        InetAddress addr = tryParseInet(ip);
        if (addr == null) {
            return false;
        }
        return addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress();
    }

    private boolean isPublicAddress(String ip) {
        InetAddress addr = tryParseInet(ip);
        if (addr == null) {
            return false;
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress() || addr.isLinkLocalAddress()) {
            return false;
        }
        if (addr.isSiteLocalAddress()) {
            return false;
        }
        if (addr instanceof Inet6Address inet6) {
            String lower = ip.toLowerCase(Locale.ROOT);
            if (lower.startsWith("fc") || lower.startsWith("fd") || lower.startsWith("fe80")) {
                return false;
            }
            if (inet6.isIPv4CompatibleAddress()) {
                return isPublicAddress(ipv4FromIpv6(inet6));
            }
        }
        return true;
    }

    private String ipv4FromIpv6(Inet6Address inet6) {
        byte[] addr = inet6.getAddress();
        return (addr[12] & 0xFF) + "." + (addr[13] & 0xFF) + "." + (addr[14] & 0xFF) + "." + (addr[15] & 0xFF);
    }

    private String serializeTags(Map<String, Object> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize audit extra tags", ex);
            return null;
        }
    }

    private InetAddress parseClientIp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String candidate = raw.trim();
        if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }

        InetAddress parsed = tryParseInet(candidate);
        if (parsed != null) {
            if (parsed.isLoopbackAddress() || parsed.isAnyLocalAddress()) {
                return null;
            }
            return parsed;
        }

        if (candidate.startsWith("[") && candidate.contains("]")) {
            String inside = candidate.substring(1, candidate.indexOf(']'));
            parsed = tryParseInet(inside);
            if (parsed != null) {
                if (parsed.isLoopbackAddress() || parsed.isAnyLocalAddress()) {
                    return null;
                }
                return parsed;
            }
        }

        int lastColon = candidate.lastIndexOf(':');
        if (lastColon > 0 && candidate.indexOf(':') == lastColon && candidate.contains(".")) {
            String withoutPort = candidate.substring(0, lastColon);
            parsed = tryParseInet(withoutPort);
            if (parsed != null) {
                if (parsed.isLoopbackAddress() || parsed.isAnyLocalAddress()) {
                    return null;
                }
                return parsed;
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Discarding invalid client IP string '{}'", raw);
        }
        return null;
    }

    private InetAddress tryParseInet(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private String defaultString(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String resolveEncryptionKey() {
        if (!StringUtils.hasText(properties.getEncryptionKey())) {
            byte[] random = new byte[32];
            java.security.SecureRandom randomSource = new java.security.SecureRandom();
            randomSource.nextBytes(random);
            String generated = java.util.Base64.getEncoder().encodeToString(random);
            log.warn("No auditing.encryption-key configured; generated ephemeral key for this runtime session");
            properties.setEncryptionKey(generated);
        }
        return properties.getEncryptionKey();
    }

    private String resolveHmacKey() {
        if (StringUtils.hasText(properties.getHmacKey())) {
            return properties.getHmacKey();
        }
        return properties.getEncryptionKey();
    }

    private String normalizeExtraTags(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            return objectMapper.readTree(trimmed).toString();
        } catch (JsonProcessingException ex) {
            if (log.isDebugEnabled()) {
                log.debug("extraTags payload is not JSON, storing as string", ex);
            }
            try {
                return objectMapper.writeValueAsString(trimmed);
            } catch (JsonProcessingException secondary) {
                log.warn("Failed to serialize extraTags payload as JSON string, discarding", secondary);
                return null;
            }
        }
    }

    private static String coerceToString(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString();
        return text == null ? null : text.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String selectTable(String resolvedId, String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        for (String candidate : candidates) {
            if (!StringUtils.hasText(candidate)) {
                continue;
            }
            if (isLikelyIdentifier(candidate, resolvedId)) {
                continue;
            }
            return candidate;
        }
        return null;
    }

    private boolean isLikelyIdentifier(String candidate, String resolvedId) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String trimmed = candidate.trim();
        if (StringUtils.hasText(resolvedId) && trimmed.equalsIgnoreCase(resolvedId.trim())) {
            return true;
        }
        if (trimmed.startsWith("/api/")) {
            return true;
        }
        if (trimmed.matches("\\d{1,18}")) {
            return true;
        }
        if (trimmed.matches("[0-9a-f]{32}")) {
            return true;
        }
        if (trimmed.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            return true;
        }
        return false;
    }

    private String dictionaryCanonicalTable(String raw, String... hints) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        boolean adminHint = false;
        java.util.List<String> probes = new java.util.ArrayList<>();
        probes.add(raw);
        String replaced = raw.replace('.', '_');
        if (!replaced.equals(raw)) {
            probes.add(replaced);
        }
        String sanitizedRaw = sanitizeTableName(raw);
        if (StringUtils.hasText(sanitizedRaw)) {
            probes.add(sanitizedRaw);
            if (sanitizedRaw.startsWith("admin")) {
                adminHint = true;
            }
        }
        if (hints != null) {
            for (String hint : hints) {
                if (!StringUtils.hasText(hint)) {
                    continue;
                }
                String sanitized = sanitizeTableName(hint);
                if (StringUtils.hasText(sanitized)) {
                    if (!probes.contains(sanitized)) {
                        probes.add(sanitized);
                    }
                    if (sanitized.startsWith("admin")) {
                        adminHint = true;
                    }
                }
            }
        }
        for (String probe : probes) {
            if (!StringUtils.hasText(probe)) {
                continue;
            }
            java.util.Optional<AuditResourceDictionaryService.DictionaryEntry> entryOpt = resourceDictionary.findEntry(probe);
            if (entryOpt.isEmpty()) {
                continue;
            }
            AuditResourceDictionaryService.DictionaryEntry entry = entryOpt.orElseThrow();
            String key = sanitizeTableName(entry.key());
            if (!StringUtils.hasText(key)) {
                continue;
            }
            if (adminHint && !key.startsWith("admin_")) {
                String adminKey = sanitizeTableName("admin_" + key);
                if (StringUtils.hasText(adminKey)) {
                    return adminKey;
                }
            }
            return key;
        }
        return null;
    }
}
