package com.yuzhi.dts.platform.service.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditActionCatalog;
import com.yuzhi.dts.common.audit.AuditActionDefinition;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private static final boolean AUDIT_CONTEXT_PRESENT;
    private static final Class<?> AUDIT_CONTEXT_CLASS;

    static {
        boolean present;
        Class<?> ctxClass = null;
        try {
            ctxClass = Class.forName("com.yuzhi.dts.platform.service.audit.AuditRequestContext");
            present = true;
        } catch (ClassNotFoundException | NoClassDefFoundError ex) {
            present = false;
        }
        AUDIT_CONTEXT_PRESENT = present;
        AUDIT_CONTEXT_CLASS = ctxClass;
    }

    private static final Map<String, LegacyActionMapping> LEGACY_ACTIONS = new java.util.HashMap<>();
    private static final Set<String> ACTOR_HINT_KEYS = Set.of(
        "username",
        "user",
        "operator",
        "operatorName",
        "operatorId",
        "account",
        "principal",
        "actor",
        "login",
        "owner",
        "requester"
    );

    static {
        // API catalog actions
        registerLegacy("api.test", "EXECUTE", legacyMapping("SERVICE_API_REGISTER", "测试 API 服务", "测试 API 服务失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("api.publish", "PUBLISH", legacyMapping("SERVICE_API_PUBLISH", "发布 API 服务", "发布 API 服务失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("api.execute", "EXECUTE", legacyMapping("SERVICE_API_REGISTER", "执行 API 服务", "执行 API 服务失败", null, "EXECUTE", false, AuditStage.SUCCESS));

        // Catalog classification mappings
        registerLegacy("catalog.classificationMapping", "READ", legacyMapping("CATALOG_ASSET_VIEW", "查看分类映射", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("catalog.classificationMapping", "CREATE", legacyMapping("CATALOG_ASSET_EDIT", "新增分类映射", "新增分类映射失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("catalog.classificationMapping", "UPDATE", legacyMapping("CATALOG_ASSET_EDIT", "更新分类映射", "更新分类映射失败", null, "UPDATE", false, AuditStage.SUCCESS));

        // Catalog dataset grants
        registerLegacy("catalog.dataset.grant", "READ", legacyMapping("CATALOG_ASSET_VIEW", "查看数据集授权", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("catalog.dataset.grant", "CREATE", legacyMapping("CATALOG_ASSET_EDIT", "新增数据集授权", "新增数据集授权失败", null, "GRANT", false, AuditStage.SUCCESS));
        registerLegacy("catalog.dataset.grant", "DELETE", legacyMapping("CATALOG_ASSET_EDIT", "删除数据集授权", "删除数据集授权失败", null, "REVOKE", false, AuditStage.SUCCESS));

        // Catalog dataset import
        registerLegacy("catalog.dataset.import", "CREATE", legacyMapping("CATALOG_ASSET_EDIT", "导入数据资产", "导入数据资产失败", null, "IMPORT", false, AuditStage.SUCCESS));

        // Catalog domains
        registerLegacy("catalog.domain", "READ", legacyMapping("CATALOG_ASSET_VIEW", "查看数据域", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("catalog.domain", "CREATE", legacyMapping("CATALOG_ASSET_EDIT", "新增数据域", "新增数据域失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("catalog.domain", "UPDATE", legacyMapping("CATALOG_ASSET_EDIT", "更新数据域", "更新数据域失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("catalog.domain", "DELETE", legacyMapping("CATALOG_ASSET_EDIT", "删除数据域", "删除数据域失败", null, "DELETE", false, AuditStage.SUCCESS));
        registerLegacy("catalog.domain.move", "UPDATE", legacyMapping("CATALOG_ASSET_EDIT", "调整数据域顺序", "调整数据域顺序失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("catalog.domain.tree", "READ", legacyMapping("CATALOG_ASSET_VIEW", "查看数据域树", null, null, "READ", true, AuditStage.SUCCESS));

        // Catalog table metadata
        registerLegacyCrud("catalog.table", "数据表");
        registerLegacy("catalog.table.import", "CREATE", legacyMapping("CATALOG_ASSET_EDIT", "导入数据表", "导入数据表失败", null, "IMPORT", false, AuditStage.SUCCESS));
        registerLegacyCrud("catalog.column", "数据字段");
        registerLegacyCrud("catalog.rowFilter", "数据过滤规则");
        registerLegacyCrud("catalog.masking", "脱敏规则");
        registerLegacy("catalog.masking.preview", "EXECUTE", legacyMapping("CATALOG_ASSET_VIEW", "预览脱敏规则效果", "预览脱敏规则效果失败", null, "READ", true, AuditStage.SUCCESS));

        // Catalog domain list / classification mapping read already handled
        registerLegacy("catalog.classificationMapping", "READ", legacyMapping("CATALOG_ASSET_VIEW", "查看分类映射", null, null, "READ", true, AuditStage.SUCCESS));

        // Catalog dataset grants already handled above

        // Dashboard
        registerLegacy("dashboard.list", "READ", legacyMapping("VIS_DASHBOARD_VIEW", "查看仪表盘列表", null, null, "READ", true, AuditStage.SUCCESS));

        // ETL / job
        registerLegacy("etl.job", "SUBMIT", legacyMapping("FOUNDATION_SCHEDULE_DEPLOY", "提交数据集成任务", "提交数据集成任务失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("etl.run.status", "READ", legacyMapping("FOUNDATION_SCHEDULE_REGISTER", "查看任务运行状态", null, null, "READ", true, AuditStage.SUCCESS));

        // Explore workbench
        registerLegacy("explore.execute", "EXECUTE", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行数据查询", "执行数据查询失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("explore.execute", "DENY", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行数据查询", "执行数据查询被拒绝", null, "EXECUTE", false, AuditStage.FAIL));
        registerLegacy("explore.execute", "ERROR", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行数据查询", "执行数据查询失败", null, "EXECUTE", false, AuditStage.FAIL));
        registerLegacy("explore.explain", "READ", legacyMapping("EXPLORE_WORKBENCH_QUERY", "查看查询执行计划", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("explore.resultPreview", "READ", legacyMapping("EXPLORE_RESULTSET_VIEW", "预览查询结果", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("explore.resultPreview", "DENY", legacyMapping("EXPLORE_RESULTSET_VIEW", "预览查询结果", "预览查询结果被拒绝", null, "READ", true, AuditStage.FAIL));
        registerLegacy("explore.resultSet", "DELETE", legacyMapping("EXPLORE_RESULTSET_PURGE", "删除查询结果集", "删除查询结果集失败", null, "DELETE", false, AuditStage.SUCCESS));
        registerLegacy("explore.resultSet", "DENY", legacyMapping("EXPLORE_RESULTSET_PURGE", "删除查询结果集", "删除查询结果集被拒绝", null, "DELETE", false, AuditStage.FAIL));
        registerLegacy("explore.resultSet.cleanup", "DELETE", legacyMapping("EXPLORE_RESULTSET_PURGE", "清理查询结果集", "清理查询结果集失败", null, "DELETE", false, AuditStage.SUCCESS));
        registerLegacy("explore.saveResult", "EXPORT", legacyMapping("EXPLORE_RESULTSET_EXPORT", "保存查询结果集", "保存查询结果集失败", null, "EXPORT", false, AuditStage.SUCCESS));
        registerLegacy("explore.saveResult", "DENY", legacyMapping("EXPLORE_RESULTSET_EXPORT", "保存查询结果集", "保存查询结果集被拒绝", null, "EXPORT", false, AuditStage.FAIL));

        // Governance compliance
        registerLegacy(
            "governance.compliance",
            "LIST",
            legacyMapping("GOV_COMPLIANCE_RUN", "刷新合规批次列表", "刷新合规批次列表失败", null, "READ", true, AuditStage.SUCCESS)
        );
        registerLegacy(
            "governance.compliance.batch",
            "CREATE",
            legacyMapping("GOV_COMPLIANCE_PLAN", "新建合规批次", "新建合规批次失败", null, "CREATE", false, AuditStage.SUCCESS)
        );
        registerLegacy(
            "governance.compliance.batch",
            "READ",
            legacyMapping("GOV_COMPLIANCE_REVIEW", "查看合规批次", "查看合规批次失败", null, "READ", true, AuditStage.SUCCESS)
        );
        registerLegacy(
            "governance.compliance.batch",
            "DELETE",
            legacyMapping("GOV_COMPLIANCE_PLAN", "删除合规批次", "删除合规批次失败", null, "DELETE", false, AuditStage.SUCCESS)
        );
        registerLegacy(
            "governance.compliance.item",
            "UPDATE",
            legacyMapping("GOV_COMPLIANCE_REVIEW", "登记合规检查结果", "登记合规检查结果失败", null, "UPDATE", false, AuditStage.SUCCESS)
        );
        registerLegacy(
            "governance.compliance.qualityRun",
            "READ",
            legacyMapping("GOV_COMPLIANCE_REVIEW", "查看质量运行详情", "查看质量运行详情失败", null, "READ", true, AuditStage.SUCCESS)
        );

        // IAM classification
        registerLegacy("iam.classification", "READ", legacyMapping("IAM_CLASSIFICATION_VIEW", "查看密级映射", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("iam.classification", "CREATE", legacyMapping("IAM_CLASSIFICATION_SYNC", "新增密级映射", "新增密级映射失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.classification", "UPDATE", legacyMapping("IAM_CLASSIFICATION_SYNC", "更新密级映射", "更新密级映射失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.classification", "DELETE", legacyMapping("IAM_CLASSIFICATION_SYNC", "删除密级映射", "删除密级映射失败", null, "DELETE", false, AuditStage.SUCCESS));

        // IAM permissions
        registerLegacy("iam.permission", "READ", legacyMapping("IAM_AUTH_GRANT", "查看权限策略", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("iam.permission", "CREATE", legacyMapping("IAM_AUTH_GRANT", "新增权限策略", "新增权限策略失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.permission", "UPDATE", legacyMapping("IAM_AUTH_GRANT", "更新权限策略", "更新权限策略失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.permission", "DELETE", legacyMapping("IAM_AUTH_REVOKE", "删除权限策略", "删除权限策略失败", null, "DELETE", false, AuditStage.SUCCESS));

        // IAM requests
        registerLegacy("iam.request", "READ", legacyMapping("IAM_REQUEST_SUBMIT", "查看权限申请", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("iam.request", "CREATE", legacyMapping("IAM_REQUEST_SUBMIT", "提交权限申请", "提交权限申请失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.request.approve", "UPDATE", legacyMapping("IAM_REQUEST_APPROVE", "审批权限申请", "审批权限申请失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.request.reject", "UPDATE", legacyMapping("IAM_REQUEST_REJECT", "驳回权限申请", "驳回权限申请失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("iam.simulate", "EXECUTE", legacyMapping("IAM_SIMULATION_RUN", "执行策略模拟", "执行策略模拟失败", null, "EXECUTE", false, AuditStage.SUCCESS));

        // Infra data sources
        registerLegacy("infra.dataSource", "CREATE", legacyMapping("FOUNDATION_DATASOURCE_REGISTER", "新增数据源", "新增数据源失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource", "UPDATE", legacyMapping("FOUNDATION_DATASOURCE_REGISTER", "更新数据源", "更新数据源失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource", "DELETE", legacyMapping("FOUNDATION_DATASOURCE_DISABLE", "删除数据源", "删除数据源失败", null, "DELETE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource", "TEST", legacyMapping("FOUNDATION_DATASOURCE_TEST", "测试数据源连接", "测试数据源连接失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource.inceptor", "PUBLISH", legacyMapping("FOUNDATION_DATASOURCE_REGISTER", "发布 Inceptor 数据源", "发布 Inceptor 数据源失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource.inceptor", "REFRESH", legacyMapping("FOUNDATION_DATASOURCE_TEST", "刷新 Inceptor 数据源", "刷新 Inceptor 数据源失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataSource.postgres", "PUBLISH", legacyMapping("FOUNDATION_DATASOURCE_REGISTER", "发布 Postgres 数据源", "发布 Postgres 数据源失败", null, "UPDATE", false, AuditStage.SUCCESS));

        // Infra data storage
        registerLegacy("infra.dataStorage", "CREATE", legacyMapping("FOUNDATION_STORAGE_REGISTER", "新增数据存储", "新增数据存储失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataStorage", "UPDATE", legacyMapping("FOUNDATION_STORAGE_UPDATE", "更新数据存储", "更新数据存储失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.dataStorage", "DELETE", legacyMapping("FOUNDATION_STORAGE_UPDATE", "删除数据存储", "删除数据存储失败", null, "DELETE", false, AuditStage.SUCCESS));

        // Infra schedule
        registerLegacy("infra.schedule", "READ", legacyMapping("FOUNDATION_SCHEDULE_REGISTER", "查看调度任务", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("infra.schedule", "CREATE", legacyMapping("FOUNDATION_SCHEDULE_REGISTER", "创建调度任务", "创建调度任务失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.schedule", "UPDATE", legacyMapping("FOUNDATION_SCHEDULE_DEPLOY", "更新调度任务", "更新调度任务失败", null, "UPDATE", false, AuditStage.SUCCESS));
        registerLegacy("infra.schedule", "DELETE", legacyMapping("FOUNDATION_SCHEDULE_DISABLE", "删除调度任务", "删除调度任务失败", null, "DELETE", false, AuditStage.SUCCESS));

        // Modeling settings
        registerLegacy("modeling.standard.settings", "READ", legacyMapping("MODELING_STANDARD_VIEW", "查看数据标准设置", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("modeling.standard.settings", "UPDATE", legacyMapping("MODELING_STANDARD_EDIT", "更新数据标准设置", "更新数据标准设置失败", null, "UPDATE", false, AuditStage.SUCCESS));

        // SQL query fallback
        registerLegacy("sql.query", "EXECUTE", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行 SQL 查询", "执行 SQL 查询失败", null, "EXECUTE", false, AuditStage.SUCCESS));
        registerLegacy("sql.query", "DENY", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行 SQL 查询", "执行 SQL 查询被拒绝", null, "EXECUTE", false, AuditStage.FAIL));
        registerLegacy("sql.query", "ERROR", legacyMapping("EXPLORE_WORKBENCH_QUERY", "执行 SQL 查询", "执行 SQL 查询失败", null, "EXECUTE", false, AuditStage.FAIL));
        registerLegacy("sql.query", "CANCEL", legacyMapping("EXPLORE_WORKBENCH_QUERY", "停止 SQL 查询", "停止 SQL 查询失败", null, "CANCEL", false, AuditStage.SUCCESS));

        // Service tokens
        registerLegacy("svc.token", "READ", legacyMapping("SERVICE_TOKEN_ISSUE", "查看访问令牌", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("svc.token", "CREATE", legacyMapping("SERVICE_TOKEN_ISSUE", "发放访问令牌", "发放访问令牌失败", null, "CREATE", false, AuditStage.SUCCESS));
        registerLegacy("svc.token", "DELETE", legacyMapping("SERVICE_TOKEN_REVOKE", "吊销访问令牌", "吊销访问令牌失败", null, "DELETE", false, AuditStage.SUCCESS));

        // Visualization dashboards
        registerLegacy("vis.dashboards", "READ", legacyMapping("VIS_DASHBOARD_VIEW", "查看仪表盘", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("vis.cockpit", "READ", legacyMapping("VIS_COCKPIT_VIEW", "查看驾驶舱", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("vis.finance", "READ", legacyMapping("VIS_FINANCE_VIEW", "查看财务看板", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("vis.hr", "READ", legacyMapping("VIS_HR_VIEW", "查看人力看板", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("vis.projects", "READ", legacyMapping("VIS_PROJECT_VIEW", "查看项目看板", null, null, "READ", true, AuditStage.SUCCESS));
        registerLegacy("vis.supply", "READ", legacyMapping("VIS_SUPPLYCHAIN_VIEW", "查看供应链看板", null, null, "READ", true, AuditStage.SUCCESS));

    }

    private final ObjectProvider<AuditTrailService> auditTrailServiceProvider;
    private final AuditActionCatalog actionCatalog;
    private final PortalSessionRegistry portalSessionRegistry;
    private final ObjectMapper objectMapper;

    public AuditService(
        ObjectProvider<AuditTrailService> auditTrailServiceProvider,
        AuditActionCatalog actionCatalog,
        PortalSessionRegistry portalSessionRegistry,
        ObjectMapper objectMapper
    ) {
        this.auditTrailServiceProvider = auditTrailServiceProvider;
        this.actionCatalog = actionCatalog;
        this.portalSessionRegistry = portalSessionRegistry;
        this.objectMapper = objectMapper;
    }

    public void auditAction(String actionCode, AuditStage stage, String resourceId, Object payload) {
        if (!StringUtils.hasText(actionCode)) {
            log.warn("auditAction invoked without action code; falling back to legacy audit");
            audit(actionCode, "general", resourceId);
            return;
        }
        AuditStage effectiveStage = stage == null ? AuditStage.SUCCESS : stage;
        AuditActionDefinition definition = actionCatalog
            .findByCode(actionCode)
            .orElseGet(() -> {
                log.warn("Unknown audit action code {}, using fallback metadata", actionCode);
                return new AuditActionDefinition(
                    actionCode.trim().toUpperCase(),
                    actionCode,
                    "general",
                    "General",
                    "general",
                    "通用动作",
                    false,
                    null
                );
            });
        if (!definition.isStageSupported(effectiveStage)) {
            log.debug(
                "Audit action {} does not declare stage {}; proceeding for backward compatibility",
                definition.getCode(),
                effectiveStage
            );
        }

        String module = definition.getModuleKey();
        String actionDisplay = definition.getDisplay();
        String resourceType = definition.getEntryKey();
        String result = switch (effectiveStage) {
            case BEGIN -> "PENDING";
            case SUCCESS -> "SUCCESS";
            case FAIL -> "FAILED";
        };

        Map<String, Object> tags = new HashMap<>();
        tags.put("actionCode", definition.getCode());
        tags.put("stage", effectiveStage.name());
        tags.put("moduleKey", definition.getModuleKey());
        tags.put("moduleTitle", definition.getModuleTitle());
        tags.put("entryKey", definition.getEntryKey());
        tags.put("entryTitle", definition.getEntryTitle());
        tags.put("supportsFlow", definition.isSupportsFlow());

        record(actionDisplay, module, resourceType, resourceId, result, payload, tags);
    }

    public void audit(String action, String targetKind, String targetRef) {
        record(action, targetKind, targetKind, targetRef, "SUCCESS", null, null);
    }

    public void auditFailure(String action, String targetKind, String targetRef, Object payload) {
        record(action, targetKind, targetKind, targetRef, "FAILED", payload, null);
    }

    public void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        record(action, module, resourceType, resourceId, result, payload, null);
    }

    public void record(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAudit(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action,
            module,
            resourceType,
            resourceId,
            result,
            payload,
            extraTags
        );
    }

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        Object payload
    ) {
        recordAuxiliary(action, module, resourceType, resourceId, "SUCCESS", payload, null);
    }

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload
    ) {
        recordAuxiliary(action, module, resourceType, resourceId, result, payload, null);
    }

    public void recordAuxiliary(
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAuditInternal(
            SecurityUtils.getCurrentUserLogin().orElse("anonymous"),
            action,
            module,
            resourceType,
            resourceId,
            result,
            payload,
            extraTags,
            true
        );
    }

    // Explicit-actor variant used for events occurring before SecurityContext is populated (e.g., login)
    public void recordAs(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAuditInternal(actor, action, module, resourceType, resourceId, result, payload, extraTags, false);
    }

    private void submitAudit(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags
    ) {
        submitAuditInternal(actor, action, module, resourceType, resourceId, result, payload, extraTags, false);
    }

    private void submitAuditInternal(
        String actor,
        String action,
        String module,
        String resourceType,
        String resourceId,
        String result,
        Object payload,
        Map<String, Object> extraTags,
        boolean auxiliary
    ) {
        Map<String, Object> payloadMap = toPayloadMap(payload);
        String safeActor = resolveActor(actor, payloadMap);
        if (safeActor == null) {
            if (log.isDebugEnabled()) {
                log.debug(
                    "Skip audit record without resolved actor action={} module={} resourceId={}",
                    action,
                    module,
                    resourceId
                );
            }
            return;
        }
        Map<String, Object> effectiveExtraTags = extraTags != null ? new java.util.LinkedHashMap<>(extraTags) : null;
        LegacyActionMapping legacyMapping = null;
        AuditActionDefinition legacyDefinition = null;
        String overrideAction = null;
        String overrideOperationType = null;
        boolean disableDefaultResourceFallback = auxiliary;
        AuditStage stage = resolveStageFromResult(result, null);

        if (!auxiliary && (effectiveExtraTags == null || !effectiveExtraTags.containsKey("actionCode"))) {
            legacyMapping = LEGACY_ACTIONS.get(legacyKey(module, action));
            if (legacyMapping != null) {
                stage = resolveStageFromResult(result, legacyMapping.defaultStage());
                legacyDefinition = actionCatalog.findByCode(legacyMapping.actionCode()).orElse(null);
                overrideAction = legacyMapping.summaryForStage(stage);
                overrideOperationType = legacyMapping.operationType();
                if (legacyMapping.allowEmptyTargets()) {
                    disableDefaultResourceFallback = true;
                }
                if (legacyDefinition != null) {
                    module = legacyDefinition.getModuleKey();
                    if (StringUtils.hasText(legacyDefinition.getEntryKey())) {
                        resourceType = legacyDefinition.getEntryKey();
                    }
                    effectiveExtraTags = new java.util.LinkedHashMap<>();
                    effectiveExtraTags.put("actionCode", legacyDefinition.getCode());
                    effectiveExtraTags.put("moduleKey", legacyDefinition.getModuleKey());
                    effectiveExtraTags.put("moduleTitle", legacyDefinition.getModuleTitle());
                    effectiveExtraTags.put("entryKey", legacyDefinition.getEntryKey());
                    effectiveExtraTags.put("entryTitle", legacyDefinition.getEntryTitle());
                    effectiveExtraTags.put("supportsFlow", legacyDefinition.isSupportsFlow());
                    effectiveExtraTags.put("stage", stage.name());
                }
                if (StringUtils.hasText(overrideAction) && !payloadMap.containsKey("summary")) {
                    payloadMap.put("summary", overrideAction);
                }
                if (StringUtils.hasText(overrideOperationType) && !payloadMap.containsKey("operationType")) {
                    payloadMap.put("operationType", overrideOperationType);
                }
            }
        }

        result = normalizeResultForStage(stage, result);
        String logAction = StringUtils.hasText(overrideAction) ? overrideAction : action;
        log.info(
            "AUDIT actor={} action={} module={} resourceType={} resourceId={} result={}",
            safeActor,
            logAction,
            module,
            resourceType,
            resourceId,
            result
        );
        AuditTrailService.PendingAuditEvent event = new AuditTrailService.PendingAuditEvent();
        event.occurredAt = Instant.now();
        event.actor = safeActor;
        event.actorRole = resolvePrimaryAuthority();
        event.module = StringUtils.hasText(module) ? module : "general";
        event.action = StringUtils.hasText(overrideAction)
            ? overrideAction
            : (StringUtils.hasText(action) ? action : "READ");
        event.resourceType = resourceType;
        event.resourceId = resourceId;
        event.result = StringUtils.hasText(result) ? result : "SUCCESS";

        if (!payloadMap.isEmpty()) {
            event.payload = payloadMap;
        } else {
            event.payload = payload;
        }
        String actorDisplayName = resolveActorName(payloadMap, safeActor);
        if (StringUtils.hasText(actorDisplayName)) {
            payloadMap.putIfAbsent("actorName", actorDisplayName);
            event.actorName = actorDisplayName;
        }
        if (effectiveExtraTags != null && !effectiveExtraTags.isEmpty()) {
            event.extraTags = serializeTags(effectiveExtraTags);
        }

        String summary = extractText(payloadMap, "summary");
        String targetName = extractText(payloadMap, "targetName");
        if (StringUtils.hasText(targetName)) {
            event.resourceName = targetName;
        } else {
            String resourceName = extractText(payloadMap, "resourceName");
            if (StringUtils.hasText(resourceName)) {
                event.resourceName = resourceName;
            }
        }
        if (!StringUtils.hasText(summary)) {
            if (StringUtils.hasText(event.action) && StringUtils.hasText(event.resourceName)) {
                summary = event.action + "：" + event.resourceName;
            } else if (StringUtils.hasText(event.action)) {
                summary = event.action;
            }
        }
        event.summary = summary;
        if (StringUtils.hasText(overrideOperationType)) {
            event.operationType = overrideOperationType;
        } else {
            event.operationType = deriveOperationType(event.action, payloadMap);
        }

        Map<String, Object> attributes = extractNestedAttributes(payloadMap);
        if (!attributes.isEmpty()) {
            event.attributes = attributes;
        }
        if (effectiveExtraTags != null && !effectiveExtraTags.isEmpty()) {
            event.metadata = new java.util.LinkedHashMap<>(effectiveExtraTags);
        }
        event.auxiliary = auxiliary;
        if (disableDefaultResourceFallback) {
            event.disableDefaultResourceFallback = true;
        }

        // Best-effort populate client/network fields from current request
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                var req = attrs.getRequest();
                event.clientIp = resolveClientIp(req);
                event.clientAgent = req.getHeader("User-Agent");
                event.requestUri = req.getRequestURI();
                event.httpMethod = req.getMethod();
            }
        } catch (Exception ignore) {}
        markDomainAuditSafe();
        AuditTrailService svc = auditTrailServiceProvider.getIfAvailable();
        if (svc != null) {
            svc.record(event);
        } else if (log.isDebugEnabled()) {
            log.debug("AuditTrailService not available; skipping audit record action={} module={} resourceId={}", action, module, resourceId);
        }
    }

    private String resolveActor(String actor, Map<String, Object> payloadMap) {
        String primary = sanitizeActorString(actor);
        if (primary != null) {
            return primary;
        }
        String fromPayload = extractActorFromPayload(payloadMap);
        if (fromPayload != null) {
            return fromPayload;
        }
        return sanitizeActorString(SecurityUtils.getCurrentUserLogin().orElse(null));
    }

    private String extractActorFromPayload(Map<String, Object> payloadMap) {
        if (payloadMap == null || payloadMap.isEmpty()) {
            return null;
        }
        return extractActorFromPayloadInternal(
            payloadMap,
            Collections.newSetFromMap(new IdentityHashMap<>())
        );
    }

    private String extractActorFromPayloadInternal(Object source, Set<Object> visited) {
        if (source == null) {
            return null;
        }
        if (source instanceof String s) {
            return sanitizeActorString(s);
        }
        if (source instanceof Map<?, ?> map) {
            if (!visited.add(map)) {
                return null;
            }
            for (String key : ACTOR_HINT_KEYS) {
                if (map.containsKey(key)) {
                    String candidate = extractActorFromPayloadInternal(map.get(key), visited);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
            for (Object value : map.values()) {
                String candidate = extractActorFromPayloadInternal(value, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (source instanceof Collection<?> collection) {
            if (!visited.add(collection)) {
                return null;
            }
            for (Object value : collection) {
                String candidate = extractActorFromPayloadInternal(value, visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (source.getClass().isArray()) {
            int length = Array.getLength(source);
            for (int i = 0; i < length; i++) {
                String candidate = extractActorFromPayloadInternal(Array.get(source, i), visited);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        return sanitizeActorString(source.toString());
    }

    private String sanitizeActorString(String candidate) {
        if (candidate == null) {
            return null;
        }
        String text = candidate.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.startsWith("Bearer ")) {
            text = text.substring(7).trim();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.equals("anonymous") || lower.equals("anonymoususer") || lower.equals("unknown")) {
            return null;
        }
        return text;
    }

    private Map<String, Object> toPayloadMap(Object payload) {
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return new java.util.LinkedHashMap<>();
    }

    private Map<String, Object> extractNestedAttributes(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        Object attributes = payload.get("attributes");
        if (attributes instanceof Map<?, ?> map) {
            Map<String, Object> copy = new java.util.LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    copy.put(String.valueOf(k), v);
                }
            });
            return copy;
        }
        return java.util.Collections.emptyMap();
    }

    private String extractText(Map<String, Object> map, String key) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String deriveOperationType(String action, Map<String, Object> payload) {
        String direct = extractText(payload, "operationType");
        if (!StringUtils.hasText(direct)) {
            direct = extractText(payload, "operation_type");
        }
        if (StringUtils.hasText(direct)) {
            return canonicalOperationType(direct);
        }
        if (StringUtils.hasText(action)) {
            return canonicalOperationType(action);
        }
        String summary = extractText(payload, "summary");
        if (StringUtils.hasText(summary)) {
            return canonicalOperationType(summary);
        }
        return "READ";
    }

    private String canonicalOperationType(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return "READ";
        }
        String trimmed = candidate.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (upper.contains("LOGIN") || containsAny(lower, "登录", "登入")) {
            return "LOGIN";
        }
        if (upper.contains("LOGOUT") || containsAny(lower, "登出", "退出登录", "注销登录")) {
            return "LOGOUT";
        }
        if (upper.contains("DOWNLOAD") || containsAny(lower, "下载", "download")) {
            return "DOWNLOAD";
        }
        if (upper.contains("UPLOAD") || containsAny(lower, "上传", "upload")) {
            return "UPLOAD";
        }
        if (upper.contains("EXPORT") || containsAny(lower, "导出", "export")) {
            return "EXPORT";
        }
        if (upper.contains("IMPORT") || containsAny(lower, "导入", "import")) {
            return "IMPORT";
        }
        if (upper.contains("GRANT") || containsAny(lower, "授权", "共享", "grant")) {
            return "GRANT";
        }
        if (upper.contains("REVOKE") || containsAny(lower, "撤销授权", "取消授权", "收回", "回收", "revoke")) {
            return "REVOKE";
        }
        if (upper.contains("ENABLE") || containsAny(lower, "启用", "开启", "激活", "enable")) {
            return "ENABLE";
        }
        if (upper.contains("DISABLE") || containsAny(lower, "禁用", "停用", "关闭", "失效", "disable")) {
            return "DISABLE";
        }
        if (upper.contains("ARCHIVE") || containsAny(lower, "归档", "封存", "archive")) {
            return "ARCHIVE";
        }
        if (upper.contains("APPROVE") || containsAny(lower, "批准", "审批通过")) {
            return "APPROVE";
        }
        if (upper.contains("REJECT") || containsAny(lower, "拒绝", "驳回")) {
            return "REJECT";
        }
        if (
            upper.contains("EXECUTE") ||
            upper.contains("RUN") ||
            containsAny(lower, "执行", "运行", "run", "apply")
        ) {
            return "EXECUTE";
        }
        if (upper.contains("REFRESH") || containsAny(lower, "刷新", "refresh")) {
            return "REFRESH";
        }
        if (upper.contains("TEST") || containsAny(lower, "测试", "校验", "验证", "test")) {
            return "TEST";
        }
        if (
            upper.contains("CREATE") ||
            upper.contains("ADD") ||
            upper.contains("NEW") ||
            containsAny(lower, "新增", "新建", "创建", "提交", "申请")
        ) {
            return "CREATE";
        }
        if (upper.contains("DELETE") || containsAny(lower, "删除", "移除", "下线", "注销")) {
            return "DELETE";
        }
        if (
            upper.contains("UPDATE") ||
            upper.contains("MODIFY") ||
            upper.contains("EDIT") ||
            upper.contains("SAVE") ||
            containsAny(lower, "修改", "更新", "调整", "保存", "编辑", "配置")
        ) {
            return "UPDATE";
        }
        if (
            upper.contains("READ") ||
            upper.contains("QUERY") ||
            upper.contains("GET") ||
            containsAny(lower, "查看", "查询", "预览", "浏览", "列表", "检索")
        ) {
            return "READ";
        }
        return "READ";
    }

    private AuditStage resolveStageFromResult(String result, AuditStage defaultStage) {
        String normalized = result == null ? "" : result.trim().toUpperCase(Locale.ROOT);
        if ("FAILED".equals(normalized) || "FAIL".equals(normalized) || "ERROR".equals(normalized) || "DENY".equals(normalized) || "DENIED".equals(normalized)) {
            return AuditStage.FAIL;
        }
        if ("PENDING".equals(normalized) || "BEGIN".equals(normalized)) {
            return AuditStage.BEGIN;
        }
        if (defaultStage != null) {
            return defaultStage;
        }
        return AuditStage.SUCCESS;
    }

    private String normalizeResultForStage(AuditStage stage, String original) {
        String normalized = original == null ? "" : original.trim().toUpperCase(Locale.ROOT);
        return switch (stage) {
            case FAIL -> "FAILED";
            case BEGIN -> "PENDING";
            case SUCCESS -> StringUtils.hasText(normalized) ? normalized : "SUCCESS";
        };
    }

    private static String legacyKey(String module, String action) {
        String normalizedModule = StringUtils.hasText(module) ? module.trim() : "";
        String normalizedAction = StringUtils.hasText(action) ? action.trim() : "";
        return normalizedModule + ":" + normalizedAction;
    }

    private static void registerLegacy(String module, String action, LegacyActionMapping mapping) {
        if (mapping == null) {
            return;
        }
        LEGACY_ACTIONS.put(legacyKey(module, action), mapping);
    }

    private static LegacyActionMapping legacyMapping(
        String actionCode,
        String successSummary,
        String failureSummary,
        String pendingSummary,
        String operationType,
        boolean allowEmptyTargets,
        AuditStage defaultStage
    ) {
        return new LegacyActionMapping(
            actionCode,
            successSummary,
            failureSummary,
            pendingSummary,
            operationType,
            allowEmptyTargets,
            defaultStage
        );
    }

    private static void registerLegacyCrud(String module, String label) {
        registerLegacy(
            module,
            "READ",
            legacyMapping("CATALOG_ASSET_VIEW", "查看" + label, null, null, "READ", true, AuditStage.SUCCESS)
        );
        registerLegacy(
            module,
            "CREATE",
            legacyMapping("CATALOG_ASSET_EDIT", "新增" + label, "新增" + label + "失败", null, "CREATE", false, AuditStage.SUCCESS)
        );
        registerLegacy(
            module,
            "UPDATE",
            legacyMapping("CATALOG_ASSET_EDIT", "更新" + label, "更新" + label + "失败", null, "UPDATE", false, AuditStage.SUCCESS)
        );
        registerLegacy(
            module,
            "DELETE",
            legacyMapping("CATALOG_ASSET_EDIT", "删除" + label, "删除" + label + "失败", null, "DELETE", false, AuditStage.SUCCESS)
        );
    }

    private boolean containsAny(String source, String... needles) {
        if (!StringUtils.hasText(source) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && source.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String resolveActorName(Map<String, Object> payload, String actorId) {
        String fromPayload = extractText(payload, "actorName");
        if (StringUtils.hasText(fromPayload)) {
            return fromPayload;
        }
        String fromSecurity = SecurityUtils.getCurrentUserDisplayName().orElse(null);
        if (StringUtils.hasText(fromSecurity)) {
            return fromSecurity;
        }
        if (StringUtils.hasText(actorId)) {
            return portalSessionRegistry.resolveDisplayName(actorId).orElse(null);
        }
        return null;
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] parts = forwarded.split(",");
            for (String part : parts) {
                String candidate = sanitizeIpCandidate(part);
                if (candidate != null) {
                    candidates.add(candidate);
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

    private InetAddress tryParseInet(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return InetAddress.getByName(raw.trim());
        } catch (UnknownHostException ignored) {
            return null;
        }
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

    private void markDomainAuditSafe() {
        if (!AUDIT_CONTEXT_PRESENT) {
            return;
        }
        try {
            if (AUDIT_CONTEXT_CLASS != null) {
                AUDIT_CONTEXT_CLASS.getMethod("markDomainAudit").invoke(null);
            }
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            // tolerate missing context helper at runtime
            if (log.isDebugEnabled()) {
                log.debug("AuditRequestContext not available: {}", ex.getMessage());
            }
        }
    }

    private String resolvePrimaryAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Optional<? extends GrantedAuthority> authority = authentication.getAuthorities().stream().findFirst();
        return authority.map(GrantedAuthority::getAuthority).orElse(null);
    }

    private static final class LegacyActionMapping {
        private final String actionCode;
        private final String successSummary;
        private final String failureSummary;
        private final String pendingSummary;
        private final String operationType;
        private final boolean allowEmptyTargets;
        private final AuditStage defaultStage;

        LegacyActionMapping(
            String actionCode,
            String successSummary,
            String failureSummary,
            String pendingSummary,
            String operationType,
            boolean allowEmptyTargets,
            AuditStage defaultStage
        ) {
            this.actionCode = actionCode;
            this.successSummary = successSummary;
            this.failureSummary = failureSummary;
            this.pendingSummary = pendingSummary;
            this.operationType = operationType;
            this.allowEmptyTargets = allowEmptyTargets;
            this.defaultStage = defaultStage;
        }

        String actionCode() {
            return actionCode;
        }

        boolean allowEmptyTargets() {
            return allowEmptyTargets;
        }

        AuditStage defaultStage() {
            return defaultStage;
        }

        String operationType() {
            return operationType;
        }

        String summaryForStage(AuditStage stage) {
            if (stage == AuditStage.FAIL) {
                if (StringUtils.hasText(failureSummary)) {
                    return failureSummary;
                }
                if (StringUtils.hasText(successSummary) && !successSummary.endsWith("失败")) {
                    return successSummary + "失败";
                }
            }
            if (stage == AuditStage.BEGIN && StringUtils.hasText(pendingSummary)) {
                return pendingSummary;
            }
            return successSummary;
        }
    }
}
