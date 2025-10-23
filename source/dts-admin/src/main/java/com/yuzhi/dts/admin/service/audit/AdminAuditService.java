package com.yuzhi.dts.admin.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        public String eventId;
        public long id;
        public Instant occurredAt;
        public String actor;
        public String actorRole;
        public String module;
        public String action;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String httpMethod;
        public String result;
        public String resultText; // SUCCESS/FAILED -> 成功/失败（只读显示）
        public String extraTags;
        public String payloadPreview;
        // extended view fields
        public String sourceSystem;
        public String sourceSystemText; // admin/platform -> 管理端/业务端（只读显示）
        public String eventClass;
        public String eventType;
        public String summary;
        public String operatorId;
        public String operatorName;
        public String operatorRoles; // JSON array string
        public String orgCode;
        public String orgName;
        public String departmentName; // 别名（用于前端显示“部门”）
        // extracted from details for convenience
        public String requestId;
        public String targetTable;
        public String targetTableLabel; // 表中文名（只读显示）
        public String targetId;
        public String targetRef;
        // derived presentation fields (新口径)
        public String operationType;     // 查询/新增/修改/删除/登录/登出/部分更新
        public String operationContent;  // 如：修改了用户/查询了用户列表
        public String logTypeText;       // 安全审计/操作审计
        // rule engine metadata
        public Boolean operationRuleHit;
        public Long operationRuleId;
        public String operationModule;
        public String operationSourceTable;
        public String operationEventClass;
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
        public String sourceSystem; // admin|platform（缺省admin）
        public String module;
        public String action;
        public String resourceType;
        public String resourceId;
        public String clientIp;
        public String clientAgent;
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public Object payload;
        public String extraTags;
        // captured entity info (propagated from request thread by aspect)
        public String capturedTable;
        public String capturedId;
    }

    private final AuditEventRepository repository;
    private final AuditProperties properties;
    private final DtsCommonAuditClient auditClient;
    private final ObjectMapper objectMapper;
    private final com.yuzhi.dts.admin.repository.AdminKeycloakUserRepository userRepo;
    private final com.yuzhi.dts.admin.repository.OrganizationRepository organizationRepository;
    private final AuditResourceDictionaryService resourceDictionary;

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
        AuditResourceDictionaryService resourceDictionary
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditClient = auditClient;
        this.objectMapper = objectMapper;
        this.userRepo = userRepo;
        this.organizationRepository = organizationRepository;
        this.resourceDictionary = resourceDictionary;
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
        extraTags.put("stage", effectiveStage.name());
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
        enrichWithRequestContext(event);
        // capture last entity from current thread (aspect)
        try {
            var cap = com.yuzhi.dts.admin.service.audit.AuditEntityContext.getLast();
            if (cap != null) {
                event.capturedTable = cap.tableName;
                event.capturedId = cap.id;
            }
        } catch (Exception ignore) {}
        try { com.yuzhi.dts.admin.service.audit.AuditEntityContext.clear(); } catch (Exception ignore) {}
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
        // If not prefilled, attempt to capture entity snapshot at record time
        if (!StringUtils.hasText(event.capturedId)) {
            try {
                var cap = com.yuzhi.dts.admin.service.audit.AuditEntityContext.getLast();
                if (cap != null) {
                    event.capturedTable = cap.tableName;
                    event.capturedId = cap.id;
                }
            } catch (Exception ignore) {}
            try { com.yuzhi.dts.admin.service.audit.AuditEntityContext.clear(); } catch (Exception ignore) {}
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
            persist(List.of(event));
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
        persist(batch);
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
            entities.forEach(auditClient::enqueue);
        }
    }

    private AuditEvent buildEntity(PendingAuditEvent pending, String previousChain) throws JsonProcessingException {
        AuditEvent entity = new AuditEvent();
        entity.setOccurredAt(pending.occurredAt);
        entity.setActor(defaultString(pending.actor, "anonymous"));
        entity.setActorRole(pending.actorRole);
        // source_system：默认admin，可由调用方覆盖（平台转发时置为platform）
        String src = pending.sourceSystem;
        if (src == null || src.isBlank()) src = "admin";
        entity.setSourceSystem(src);
        entity.setEventUuid(java.util.UUID.randomUUID());
        entity.setModule(defaultString(pending.module, "GENERAL"));
        entity.setAction(defaultString(pending.action, "UNKNOWN"));
        // event_class/event_type 简化映射：安全相关 action -> SecurityEvent；其余 -> AuditEvent
        String normalizedAction = entity.getAction() == null ? "" : entity.getAction().toUpperCase(java.util.Locale.ROOT);
        boolean security = normalizedAction.contains("AUTH_") || normalizedAction.contains("LOGIN") || normalizedAction.contains("LOGOUT") || normalizedAction.contains("ACCESS_DENIED");
        entity.setEventClass(security ? "SecurityEvent" : "AuditEvent");
        entity.setEventType(mapEventCategory(pending.resourceType, pending.module, normalizedAction));
        entity.setResourceType(pending.resourceType);
        entity.setResourceId(pending.resourceId);
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
        String tableKey = tableFromResource(entity.getResourceType(), entity.getModule());
        String tableLabel = mapTableLabel(tableKey);
        det.put("源表", tableKey);
        if (StringUtils.hasText(tableLabel) && !Objects.equals(tableLabel, tableKey)) {
            det.put("源表描述", tableLabel);
        }
        if (pending.payload instanceof java.util.Map<?, ?> payloadMap) {
            String targetRef = firstNonBlank(
                coerceToString(payloadMap.get("target_ref")),
                coerceToString(payloadMap.get("targetRef"))
            );
            String datasetName = coerceToString(payloadMap.get("datasetName"));
            if (!StringUtils.hasText(targetRef)) {
                targetRef = datasetName;
            }
            if (StringUtils.hasText(targetRef) && !det.containsKey("目标引用")) {
                det.put("目标引用", targetRef);
            }
            if (StringUtils.hasText(targetRef)) {
                det.put("target_ref", targetRef);
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
        }
        // Only record PK 目标ID（同时兼容旧键 target_id 以便历史/平台复用）。
        // 对 admin_keycloak_user（用户）：
        // - 数字 -> 本地主键
        // - UUID -> 当作 KeycloakId，转换为本地主键
        // - 用户名 -> 解析为本地主键
        String rid = entity.getResourceId();
        if (rid != null) {
            String s = rid.trim();
            boolean isNumeric = s.matches("\\d+");
            boolean isUuid = s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
            String tbl = tableKey;
            if ("admin_keycloak_user".equals(tbl) || "user".equalsIgnoreCase(entity.getResourceType()) || "admin.auth".equalsIgnoreCase(entity.getResourceType())) {
                if (isNumeric) {
                    det.put("目标ID", s);
                    det.put("target_id", s);
                } else if (isUuid) {
                    try {
                        var byKc = userRepo.findByKeycloakId(s);
                        byKc.ifPresent(u -> { det.put("目标ID", String.valueOf(u.getId())); det.put("target_id", String.valueOf(u.getId())); });
                    } catch (Exception ex) {
                        if (log.isDebugEnabled()) log.debug("Failed to map keycloakId to local id {}: {}", s, ex.toString());
                    }
                } else {
                    try {
                        var userOpt = userRepo.findByUsernameIgnoreCase(s);
                        userOpt.ifPresent(u -> { det.put("目标ID", String.valueOf(u.getId())); det.put("target_id", String.valueOf(u.getId())); });
                    } catch (Exception ex) {
                        if (log.isDebugEnabled()) log.debug("Failed to resolve user id for username {}: {}", s, ex.toString());
                    }
                }
            } else {
                if (isNumeric) {
                    det.put("目标ID", s);
                } else if (isUuid) {
                    det.put("目标ID", s);
                }
            }
        }
        // If still missing and resource is admin_keycloak_user, fallback to actor username lookup
        if ((!det.containsKey("目标ID")) || det.get("目标ID") == null) {
            if ("admin_keycloak_user".equals(tableKey)) {
                String actorUser = entity.getActor();
                if (actorUser != null && !actorUser.isBlank()) {
                    try {
                        var userOpt = userRepo.findByUsernameIgnoreCase(actorUser.trim());
                        userOpt.ifPresent(u -> det.put("目标ID", String.valueOf(u.getId())));
                    } catch (Exception ignore) {}
                }
            }
        }
        // Fallback to captured last entity if target_id not resolved above
        if (!det.containsKey("目标ID") || det.get("目标ID") == null || String.valueOf(det.get("目标ID")).isBlank()) {
            var cap = com.yuzhi.dts.admin.service.audit.AuditEntityContext.getLast();
            if (cap != null && cap.id != null && !cap.id.isBlank()) {
                det.put("目标ID", cap.id);
            }
        }
        // Fallback to captured last entity if target_id not resolved above
        if ((!det.containsKey("目标ID")) || det.get("目标ID") == null || String.valueOf(det.get("目标ID")).isBlank()) {
            if (pending.capturedId != null && !pending.capturedId.isBlank()) {
                det.put("目标ID", pending.capturedId);
            }
        }
        // 目标引用（人类可读名称）：当前支持 用户 -> 姓名
        try {
            Object tidObj = det.get("目标ID");
            if (tidObj == null) tidObj = det.get("target_id");
            String tid = tidObj == null ? null : String.valueOf(tidObj);
            if (tid != null && !tid.isBlank()) {
                if ("admin_keycloak_user".equals(tableKey) || "user".equalsIgnoreCase(entity.getResourceType()) || "admin.auth".equalsIgnoreCase(entity.getResourceType())) {
                    try {
                        Long uid = Long.parseLong(tid);
                        userRepo.findById(uid).ifPresent(u -> det.put("目标引用", u.getFullName() != null && !u.getFullName().isBlank() ? u.getFullName() : u.getUsername()));
                    } catch (NumberFormatException ignore) {
                        // 非数字：可能是 username 或 UUID，前面已尽可能转为本地ID；此处不再重复解析
                    }
                }
            }
        } catch (Exception ex) {
            if (log.isDebugEnabled()) log.debug("Failed to build human-readable target_ref: {}", ex.toString());
        }

        // Expose HTTP status code for downstream rule matching (e.g., login success vs failure).
        if (pending.payload instanceof java.util.Map<?, ?> payloadMap) {
            Object statusObj = payloadMap.get("status");
            if (statusObj == null) statusObj = payloadMap.get("httpStatus");
            if (statusObj == null) statusObj = payloadMap.get("http_status");
            if (statusObj != null) {
                String statusText = String.valueOf(statusObj).trim();
                if (!statusText.isEmpty()) {
                    det.put("status_code", statusText);
                    det.put("状态码", statusText);
                }
            }
        }

        String actorRoleDisplay = translateActorRole(entity.getActorRole(), entity.getActor());
        if (actorRoleDisplay != null) {
            det.put("操作者角色", actorRoleDisplay);
        }

        det.put("请求ID", extractRequestId());
        // Absolute fallback: if target_id still missing, use event UUID to guarantee non-empty value
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
        entity.setSummary(buildSummary(entity));
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

    private String tableFromResource(String resourceType, String module) {
        if (resourceType == null || resourceType.isBlank()) {
            return module == null || module.isBlank() ? "general" : normalizeTableKey(module);
        }
        String r = resourceType.toLowerCase(java.util.Locale.ROOT);
        // Normalize common admin entry keys to concrete table names
        if (r.equals("user") || r.equals("admin_keycloak_user") || r.equals("admin.users") || r.equals("admin.user")) {
            return "admin_keycloak_user";
        }
        if (r.equals("role") || r.equals("role_assignment") || r.equals("admin_role_assignment") || r.equals("admin.roles") || r.equals("admin.role")) {
            return "admin_role_assignment";
        }
        if (r.equals("portal_menu") || r.equals("menu") || r.equals("admin.portal-menus") || r.equals("admin.menus") || r.equals("portal.navigation")) {
            return "portal_menu";
        }
        if (r.equals("org") || r.equals("organization") || r.equals("organization_node") || r.equals("admin.orgs") || r.equals("admin.org")) {
            return "organization_node";
        }
        if (r.equals("admin.auth") || r.equals("auth") || r.equals("admin")) {
            // Auth-related events anchor to user table to avoid non-existent 'admin' table
            return "admin_keycloak_user";
        }
        if (r.equals("api") || r.equals("v1") || r.equals("v2")) {
            return "general";
        }
        return normalizeTableKey(r);
    }

    private String normalizeTableKey(String key) {
        if (key == null) return "general";
        // convert dotted or kebab keys to snake-like token
        String s = key.trim().toLowerCase(java.util.Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s.isBlank() ? "general" : s;
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
        List<String> excludedActors,
        Pageable pageable
    ) {
        List<String> normalizedExcluded = normalizeActorList(excludedActors);
        if (normalizedExcluded.isEmpty()) {
            return search(actor, module, action, sourceSystem, eventType, result, resourceType, resource, requestUri, from, to, clientIp, pageable);
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
        return search(actor, module, action, sourceSystem, eventType, result, resourceType, resource, requestUri, from, to, clientIp, Pageable.unpaged())
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
        String clientIp
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

    public AuditEventView toView(AuditEvent event) {
        AuditEventView view = new AuditEventView();
        view.id = event.getId();
        view.occurredAt = event.getOccurredAt();
        view.actor = event.getActor();
        view.module = event.getModule();
        view.action = event.getAction();
        view.resourceType = event.getResourceType();
        view.resourceId = event.getResourceId();
        view.clientIp = event.getClientIp();
        view.clientAgent = event.getClientAgent();
        view.httpMethod = event.getHttpMethod();
        view.result = event.getResult();
        view.extraTags = event.getExtraTags();
        view.actorRole = event.getActorRole();
        if (StringUtils.hasText(event.getSourceSystem())) {
            view.sourceSystem = event.getSourceSystem();
            view.sourceSystemText = "platform".equalsIgnoreCase(event.getSourceSystem()) ? "业务管理" : "系统管理";
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
}
