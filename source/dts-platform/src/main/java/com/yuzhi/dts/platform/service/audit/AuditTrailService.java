package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AuditProperties;
import com.yuzhi.dts.platform.domain.audit.AuditEvent;
import com.yuzhi.dts.platform.repository.audit.AuditEventRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.DependsOn;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@DependsOn({"entityManagerFactory", "liquibase"})
@Transactional
public class AuditTrailService {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailService.class);

    public static final class AuditEventView {
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
        public String requestUri;
        public String httpMethod;
        public String result;
        public Integer latencyMs;
        public String extraTags;
        public String payloadPreview;
    }

    public static final class PendingAuditEvent {
        public Instant occurredAt;
        public String actor;
        public String actorRole;
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
    }

    private final AuditEventRepository repository;
    private final AuditProperties properties;
    private final DtsCommonAuditClient auditClient;
    private final ObjectMapper objectMapper;

    private BlockingQueue<PendingAuditEvent> queue;
    private ScheduledExecutorService workerPool;
    private SecretKey encryptionKey;
    private SecretKey hmacKey;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> lastChainSignature = new AtomicReference<>("");

    public AuditTrailService(
        AuditEventRepository repository,
        AuditProperties properties,
        DtsCommonAuditClient auditClient,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.properties = properties;
        this.auditClient = auditClient;
        this.objectMapper = objectMapper;
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
        try {
            this.lastChainSignature.set(repository.findTopByOrderByIdDesc().map(AuditEvent::getChainSignature).orElse(""));
        } catch (Exception ex) {
            // In case Liquibase hasn't created the table yet or DB is not ready, degrade gracefully.
            log.warn("AuditTrailService could not fetch last chain signature (will retry later)", ex);
            this.lastChainSignature.set("");
        }
        running.set(true);
        workerPool.scheduleWithFixedDelay(this::drainQueue, 0, 500, TimeUnit.MILLISECONDS);
        Long existingCount = null;
        try {
            existingCount = repository.count();
        } catch (Exception ex) {
            log.warn("Failed to query existing audit event count", ex);
        }
        if (existingCount != null) {
            log.info(
                "Audit writer started with capacity {} and {} existing audit events",
                properties.getQueueCapacity(),
                existingCount
            );
        } else {
            log.info("Audit writer started with capacity {}; existing count unavailable", properties.getQueueCapacity());
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    public void record(String actor, String action, String module, String resourceType, String resourceId, String outcome, Object payload) {
        if (actor == null || actor.isBlank() || "anonymous".equalsIgnoreCase(actor)) {
            return;
        }
        PendingAuditEvent event = new PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = defaultString(actor, "anonymous");
        event.action = defaultString(action, "UNKNOWN");
        event.module = defaultString(module, "GENERAL");
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = defaultString(outcome, "SUCCESS");
        event.payload = payload;
        offer(event);
    }

    public void record(String actor, String action, String module, String resourceId, String outcome, Object payload) {
        if (actor == null || actor.isBlank() || "anonymous".equalsIgnoreCase(actor)) {
            return;
        }
        record(actor, action, module, module, resourceId, outcome, payload);
    }

    public void record(PendingAuditEvent event) {
        if (event == null || event.actor == null || event.actor.isBlank() || "anonymous".equalsIgnoreCase(event.actor)) {
            return;
        }
        if (event.occurredAt == null) {
            event.occurredAt = Instant.now();
        }
        offer(event);
    }

    public Page<AuditEvent> find(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Optional<AuditEvent> findById(Long id) {
        return repository.findById(id);
    }

    public List<AuditEvent> findAll(Sort sort) {
        return repository.findAll(sort);
    }

    public byte[] decryptPayload(AuditEvent event) {
        if (event.getPayloadCipher() == null || event.getPayloadCipher().length == 0) {
            return new byte[0];
        }
        return AuditCrypto.decrypt(event.getPayloadCipher(), encryptionKey, event.getPayloadIv());
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
        lastChainSignature.set(previousChain);
        if (properties.isForwardEnabled()) {
            entities.forEach(auditClient::enqueue);
        }
    }

    @Scheduled(cron = "0 30 2 * * *")
    public void purgeOldEvents() {
        Instant threshold = Instant.now().minus(Duration.ofDays(properties.getRetentionDays()));
        int purged = repository.deleteAllByOccurredAtBefore(threshold);
        if (purged > 0) {
            log.info("Purged {} audit events older than {}", purged, threshold);
        }
    }

    private AuditEvent buildEntity(PendingAuditEvent pending, String previousChain) throws JsonProcessingException {
        AuditEvent entity = new AuditEvent();
        entity.setOccurredAt(pending.occurredAt);
        entity.setActor(defaultString(pending.actor, "anonymous"));
        entity.setActorRole(pending.actorRole);
        entity.setSourceSystem("platform");
        entity.setEventUuid(java.util.UUID.randomUUID());
        entity.setModule(defaultString(pending.module, "GENERAL"));
        entity.setAction(defaultString(pending.action, "UNKNOWN"));
        String normalizedAction = entity.getAction() == null ? "" : entity.getAction().toUpperCase(java.util.Locale.ROOT);
        boolean security = normalizedAction.contains("AUTH_") || normalizedAction.contains("LOGIN") || normalizedAction.contains("LOGOUT") || normalizedAction.contains("ACCESS_DENIED");
        entity.setEventClass(security ? "SecurityEvent" : "AuditEvent");
        entity.setEventType(mapEventCategory(pending.resourceType, pending.module, normalizedAction));
        entity.setResourceType(pending.resourceType);
        entity.setResourceId(pending.resourceId);
        InetAddress clientIp = parseClientIp(pending.clientIp);
        if (clientIp == null && StringUtils.hasText(pending.clientIp)) {
            log.debug("Discarding invalid client IP {} for audit action {}", pending.clientIp, pending.action);
        }
        entity.setClientIp(clientIp);
        entity.setClientAgent(pending.clientAgent);
        entity.setRequestUri(pending.requestUri);
        entity.setHttpMethod(pending.httpMethod);
        entity.setResult(defaultString(pending.result, "SUCCESS"));
        entity.setLatencyMs(pending.latencyMs);
        entity.setExtraTags(normalizeExtraTags(pending.extraTags));

        // operator_* from security context (prefer full name from claims)
        String username = entity.getActor();
        entity.setOperatorId(username);
        String fullName = null;
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("name");
                if (v == null) v = token.getToken().getClaims().get("full_name");
                if (v == null) v = token.getToken().getClaims().get("fullName");
                if (v instanceof String s && !s.isBlank()) fullName = s;
            } else if (auth instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal p) {
                Object v = p.getAttribute("name");
                if (v == null) v = p.getAttribute("full_name");
                if (v == null) v = p.getAttribute("fullName");
                if (v instanceof String s && !s.isBlank()) fullName = s;
            }
        } catch (Exception ignore) {}
        entity.setOperatorName(fullName != null && !fullName.isBlank() ? fullName : username);
        java.util.List<String> roles = new java.util.ArrayList<>();
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getAuthorities() != null) {
                for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                    if (ga != null && ga.getAuthority() != null) roles.add(ga.getAuthority());
                }
            }
        } catch (Exception ignore) {}
        java.util.LinkedHashSet<String> filtered = new java.util.LinkedHashSet<>();
        for (String r : roles) {
            if (r == null || r.isBlank()) continue;
            String norm = normalizeRole(r);
            if (norm.startsWith("DEPT_DATA_") || norm.startsWith("INST_DATA_")) filtered.add(norm);
        }
        try { entity.setOperatorRoles(objectMapper.writeValueAsString(new java.util.ArrayList<>(filtered))); } catch (Exception ignored) {}
        entity.setOrgCode(null);
        entity.setOrgName(null);

        java.util.Map<String, Object> det = new java.util.LinkedHashMap<>();
        String tableKey = tableFromResource(entity.getResourceType(), entity.getModule());
        String tableLabel = mapTableLabel(tableKey);
        det.put("源表", tableLabel);
        // 仅在可解析时记录目标ID
        String rid = entity.getResourceId();
        if (rid != null) {
            String s = rid.trim();
            boolean isNumeric = s.matches("\\d+");
            boolean isUuid = s.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
            if (isNumeric || isUuid) {
                det.put("目标ID", s);
            } else {
                String extracted = extractTargetId(s);
                if (extracted != null && !extracted.isBlank()) {
                    det.put("目标ID", extracted);
                }
            }
        }
        det.put("请求ID", java.util.UUID.randomUUID().toString());

        byte[] payloadBytes = serializePayload(pending.payload);
        String payloadDigest = sha256Hex(payloadBytes); // phase 1: remove key/HMAC
        det.put("参数摘要", payloadDigest);
        try { entity.setDetails(objectMapper.writeValueAsString(det)); } catch (Exception ignored) {}
        entity.setPayloadIv(null);
        entity.setPayloadCipher(null);
        entity.setPayloadHmac(payloadDigest);
        entity.setChainSignature(sha256Hex((previousChain + "|" + payloadDigest).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        entity.setRecordSignature(simpleRecordSignature(entity, payloadDigest));
        entity.setSignatureKeyVer(null);
        entity.setSummary(buildSummary(entity));
        entity.setCreatedBy(entity.getActor());
        entity.setCreatedDate(Instant.now());
        return entity;
    }

    private String mapEventCategory(String resourceType, String module, String actionUpper) {
        String a = actionUpper == null ? "" : actionUpper;
        // 登录类
        if (a.contains("LOGIN") || a.contains("LOGOUT") || (a.contains("ACCESS") && a.contains("DENIED"))) {
            return "登录管理";
        }
        String r = resourceType == null ? null : resourceType.trim().toLowerCase(java.util.Locale.ROOT);
        String m = module == null ? null : module.trim().toLowerCase(java.util.Locale.ROOT);
        String key = r != null && !r.isBlank() ? r : (m == null ? "" : m);
        if (key.isBlank()) return "数据资产";
        // 平台侧 IAM/权限并入 数据资产
        if (key.startsWith("iam") || key.equals("iam_permission") || key.equals("iam_dataset_policy") || key.equals("iam_user_classification")) {
            return "数据资产";
        }
        if (key.equals("admin.auth") || key.equals("auth")) return "登录管理";
        if (key.equals("admin_keycloak_user") || key.equals("user") || key.equals("admin")) return "用户管理";
        if (key.startsWith("role") || key.startsWith("admin.role") || key.equals("admin_role_assignment")) return "角色管理";
        if (key.equals("portal_menu") || key.equals("menu") || key.startsWith("portal.menus") || key.startsWith("portal-menus") || key.equals("portal.navigation")) return "菜单管理";
        if (key.startsWith("org") || key.startsWith("organization") || key.equals("organization_node") || key.startsWith("admin.org")) return "部门管理";
        if (key.startsWith("modeling.standard") || key.startsWith("data_standard")) return "数据标准";
        if (key.startsWith("governance") || key.startsWith("gov_")) return "数据质量";
        if (key.startsWith("explore")) return "数据开发";
        if (key.equals("catalog_dataset_job") || key.contains("schedule") || key.contains("foundation")) return "数据开发";
        if (key.startsWith("visualization")) return "数据可视化";
        if (key.startsWith("catalog") || key.equals("catalog_table_schema") || key.equals("catalog_secure_view") || key.equals("catalog_row_filter_rule") || key.equals("catalog_access_policy")) return "数据资产";
        if (key.startsWith("svc") || key.startsWith("api")) return "数据资产";
        return "数据资产";
    }

    private String tableFromResource(String resourceType, String module) {
        if (resourceType == null || resourceType.isBlank()) {
            return module == null || module.isBlank() ? "general" : normalizeKey(module);
        }
        String r = resourceType.toLowerCase(java.util.Locale.ROOT);
        // Align key aliases to concrete table names where applicable
        if (r.equals("admin.auth") || r.equals("auth") || r.equals("admin") || r.equals("user") || r.equals("admin_keycloak_user")) {
            return "admin_keycloak_user";
        }
        if (r.equals("portal_menu") || r.equals("menu") || r.equals("portal.menus") || r.equals("portal-menus")) {
            return "portal_menu";
        }
        // Modeling (数据标准)
        if (r.equals("modeling.standard") || r.equals("standard") || r.equals("modeling.standards")) {
            return "data_standard";
        }
        if (r.equals("modeling.standard.version") || r.equals("standard.version") || r.equals("standard.versions")) {
            return "data_standard_version";
        }
        if (r.equals("modeling.standard.attachment") || r.equals("standard.attachment") || r.equals("standard.attachments")) {
            return "data_standard_attachment";
        }
        // Services / APIs
        if (r.equals("svc.api") || r.equals("svc.api.try") || r.equals("api.service")) {
            return "svc_api";
        }
        if (r.equals("svc.dataproduct") || r.equals("svc.data_product") || r.equals("data.product")) {
            return "svc_data_product";
        }
        if (r.equals("svc.dataproduct.version") || r.equals("data.product.version")) {
            return "svc_data_product_version";
        }
        if (r.equals("svc.dataproduct.dataset") || r.equals("data.product.dataset")) {
            return "svc_data_product_dataset";
        }
        // Governance
        if (r.equals("governance.rule") || r.equals("gov.rule")) {
            return "gov_rule";
        }
        if (r.equals("governance.rule.version") || r.equals("gov.rule.version")) {
            return "gov_rule_version";
        }
        if (r.equals("governance.quality.metric") || r.equals("gov.quality.metric")) {
            return "gov_quality_metric";
        }
        if (r.equals("governance.quality.run") || r.equals("gov.quality.run")) {
            return "gov_quality_run";
        }
        if (r.equals("governance.issue") || r.equals("gov.issue")) {
            return "gov_issue_ticket";
        }
        if (r.equals("governance.issue.action") || r.equals("gov.issue.action")) {
            return "gov_issue_action";
        }
        // IAM
        if (r.equals("iam.policy") || r.equals("iam.dataset.policy") || r.equals("iam.policy.dataset")) {
            return "iam_dataset_policy";
        }
        if (r.equals("iam.permission")) {
            return "iam_permission";
        }
        if (r.equals("iam.user.classification") || r.equals("iam.userclassification")) {
            return "iam_user_classification";
        }
        // Catalog
        if (r.equals("catalog.table") || r.equals("catalog.tableschema") || r.equals("catalog.table.schema")) {
            return "catalog_table_schema";
        }
        if (r.equals("catalog.secureview") || r.equals("catalog.secure_view")) {
            return "catalog_secure_view";
        }
        if (r.equals("catalog.rowfilter") || r.equals("catalog.row_filter_rule")) {
            return "catalog_row_filter_rule";
        }
        if (r.equals("catalog.accesspolicy") || r.equals("catalog.access_policy")) {
            return "catalog_access_policy";
        }
        if (r.equals("catalog.dataset.job") || r.equals("catalog.datasetjob")) {
            return "catalog_dataset_job";
        }
        if (r.equals("api") || r.equals("v1") || r.equals("v2")) {
            return "general";
        }
        return normalizeKey(r);
    }

    private String normalizeKey(String key) {
        if (key == null) return "general";
        String s = key.trim().toLowerCase(java.util.Locale.ROOT);
        s = s.replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+", "").replaceAll("_+$", "");
        return s.isBlank() ? "general" : s;
    }

    // Try to extract a meaningful ID (UUID or numeric) from a composite resourceId string
    private String extractTargetId(String resourceId) {
        if (resourceId == null) return null;
        String s = resourceId.trim();
        if (s.isEmpty()) return null;
        // 1) UUID anywhere in the string
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?i)([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})")
            .matcher(s);
        if (m.find()) {
            return m.group(1);
        }
        // 2) Query style ?id=... or &id=...
        m = java.util.regex.Pattern.compile("[?&]id=([0-9a-zA-Z-]+)").matcher(s);
        if (m.find()) {
            String v = m.group(1);
            if (v.matches("\\d+")) return v;
            if (v.matches("(?i)[0-9a-f-]{36}")) return v;
        }
        // 3) Split by common separators and inspect rightmost tokens
        String[] tokens = s.split("[^0-9A-Za-z-]+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String t = tokens[i];
            if (t == null || t.isBlank()) continue;
            if (t.matches("\\d+")) return t;
            if (t.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) return t;
        }
        return null;
    }

    private String simpleRecordSignature(AuditEvent e, String payloadHmac) {
        String base = String.join("|",
            nullToEmpty(e.getSourceSystem()), nullToEmpty(e.getActor()), nullToEmpty(e.getAction()), nullToEmpty(e.getModule()),
            nullToEmpty(e.getResourceType()), nullToEmpty(e.getResourceId()), nullToEmpty(payloadHmac), e.getOccurredAt() == null ? "" : e.getOccurredAt().toString()
        );
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(base.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) { return null; }
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

    private static String normalizeRole(String role) {
        if (role == null) return "";
        String r = role.trim().toUpperCase(java.util.Locale.ROOT);
        if (r.startsWith("ROLE_")) r = r.substring(5);
        return r;
    }

    private String buildSummary(AuditEvent e) {
        String name = e.getOperatorName() != null && !e.getOperatorName().isBlank() ? e.getOperatorName() : e.getActor();
        String actionDesc = e.getEventType() != null ? e.getEventType() : e.getAction();
        String resultText = (e.getResult() != null && e.getResult().equalsIgnoreCase("SUCCESS")) ? "成功" : (e.getResult() == null ? "" : "失败");
        String targetTable = null;
        String targetId = null;
        try {
            if (e.getDetails() != null && !e.getDetails().isBlank()) {
                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(e.getDetails());
                if (node.hasNonNull("target_table")) targetTable = node.get("target_table").asText();
                if (node.hasNonNull("target_id")) targetId = node.get("target_id").asText();
            }
        } catch (Exception ignore) {}
        String tableLabel = mapTableLabel(targetTable != null ? targetTable : e.getResourceType());
        String target = (tableLabel == null ? "" : tableLabel) + (targetId == null || targetId.isBlank() ? "" : "(ID=" + targetId + ")");
        return String.format("用户【%s】%s【%s】 %s", nullToEmpty(name), nullToEmpty(resultText), nullToEmpty(actionDesc), target);
    }

    private String mapTableLabel(String key) {
        if (key == null) return null;
        String k = key.trim().toLowerCase(java.util.Locale.ROOT);
        if (k.equals("admin_keycloak_user") || k.equals("admin") || k.equals("admin.auth") || k.equals("user")) return "用户";
        if (k.equals("portal_menu") || k.equals("menu") || k.equals("portal.menus") || k.equals("portal-menus")) return "门户菜单";
        if (k.equals("role") || k.startsWith("role_")) return "角色";
        if (k.equals("admin_role_assignment") || k.equals("role_assignment") || k.equals("role_assignments")) return "用户角色";
        if (k.equals("approval_requests") || k.equals("approval_request") || k.equals("approvals") || k.equals("approval")) return "审批请求";
        if (k.equals("organization") || k.equals("organization_node") || k.equals("org") || k.startsWith("admin.org")) return "部门";
        if (k.equals("localization") || k.contains("localization")) return "界面语言";
        if (k.equals("data_standard")) return "数据标准";
        if (k.equals("data_standard_version")) return "数据标准版本";
        if (k.equals("data_standard_attachment")) return "数据标准附件";
        if (k.equals("svc_api")) return "接口";
        if (k.equals("svc_data_product")) return "数据产品";
        if (k.equals("svc_data_product_version")) return "数据产品版本";
        if (k.equals("svc_data_product_dataset")) return "数据产品数据集";
        if (k.equals("gov_rule")) return "治理规则";
        if (k.equals("gov_rule_version")) return "治理规则版本";
        if (k.equals("gov_quality_metric")) return "质量指标";
        if (k.equals("gov_quality_run")) return "质量运行";
        if (k.equals("gov_issue_ticket")) return "问题工单";
        if (k.equals("gov_issue_action")) return "工单处理";
        if (k.equals("iam_dataset_policy")) return "数据集授权策略";
        if (k.equals("iam_permission")) return "权限";
        if (k.equals("iam_user_classification")) return "用户分级";
        if (k.equals("catalog_table_schema")) return "目录表";
        if (k.equals("catalog_secure_view")) return "安全视图";
        if (k.equals("catalog_row_filter_rule")) return "行过滤规则";
        if (k.equals("catalog_access_policy")) return "访问控制策略";
        if (k.equals("catalog_dataset_job")) return "目录作业";
        return key;
    }

    private byte[] serializePayload(Object payload) throws JsonProcessingException {
        if (payload == null) {
            return new byte[0];
        }
        if (payload instanceof byte[] bytes) {
            return bytes;
        }
        return objectMapper.writeValueAsBytes(payload);
    }

    private String resolveEncryptionKey() {
        if (!StringUtils.hasText(properties.getEncryptionKey())) {
            byte[] random = new byte[32];
            new java.security.SecureRandom().nextBytes(random);
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

    private static String defaultString(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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

    private InetAddress parseClientIp(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return InetAddress.getByName(raw.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
    }
}
