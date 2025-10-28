package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.domain.AuditResourceDictionary;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import com.yuzhi.dts.admin.repository.AuditResourceDictionaryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationMappingEngineTest {

    private final JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
    private OperationMappingEngine engine;
    private final AtomicLong idSequence = new AtomicLong(1L);
    private AuditOperationMappingRepository repository;
    private AuditResourceDictionaryService dictionaryService;

    @BeforeEach
    void setUp() {
        repository = createRepository(List.of());
        dictionaryService =
            new AuditResourceDictionaryService(
                createDictionaryRepository(
                    List.of(
                        dictionaryEntry(
                            "admin_keycloak_user",
                            "用户",
                            "用户管理",
                            "admin_keycloak_user,admin_keycloak_users,admin,user,users,admin.user,admin.users,keycloak.user,keycloak.users,admin.auth",
                            10
                        ),
                        dictionaryEntry(
                            "organization_node",
                            "部门",
                            "部门管理",
                            "organization_node,organization,org,orgs,platform.org,platform.orgs,admin.org,admin.orgs",
                            12
                        ),
                        dictionaryEntry(
                            "approval_request",
                            "审批请求",
                            "审批管理",
                            "admin_approval,admin_approval_item,admin_approval_request,admin.approval_requests,admin.approval_request,approval_request,approval_requests,approvals,approval",
                            15
                        ),
                        dictionaryEntry(
                            "catalog_dataset",
                            "数据资产",
                            "数据资产",
                            "catalog_dataset,catalog.datasets,catalog*,catalog_secure_view,catalog_row_filter_rule,catalog_access_policy,catalog_asset,数据资产",
                            40
                        ),
                        dictionaryEntry(
                            "audit_log",
                            "审计日志",
                            "审计日志",
                            "audit_log,audit_logs,audit_log_detail,audit_event",
                            60
                        )
                    )
                ),
                jdbcTemplate
            );
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
    }

    @Test
    void resolveWithFallbackReturnsRuleMatchWhenMappingExists() {
        AuditOperationMapping mapping = createMapping(
            "/api/users/{id}",
            "GET",
            "用户管理",
            AuditOperationType.READ.getCode(),
            "查看用户{person}",
            "admin_keycloak_user"
        );
        mapping.setParamExtractors("{\"person\":\"path.id\"}");
        repository = createRepository(List.of(mapping));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);

        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/users/alice");
        event.setHttpMethod("GET");
        event.setOccurredAt(Instant.now());

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);

        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isTrue();
        assertThat(op.ruleId).isEqualTo(mapping.getId());
        assertThat(op.operationType).isEqualTo(AuditOperationType.READ);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.READ.getDisplayName());
        assertThat(op.description).isEqualTo("查看用户alice");
        assertThat(op.sourceTable).isEqualTo("admin_keycloak_user");
    }

    @Test
    void resolveWithFallbackUsesFallbackWhenNoRuleMatches() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/assets/123");
        event.setHttpMethod("DELETE");
        event.setAction("ASSET_DELETE");
        event.setSummary("删除数据资产【订单表】");
        event.setResourceType("catalog_asset");
        event.setResourceId("123");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isFalse();
        assertThat(op.operationType).isEqualTo(AuditOperationType.DELETE);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.DELETE.getDisplayName());
        assertThat(op.description).contains("数据资产");
        assertThat(op.description).contains("【123】");
        assertThat(op.sourceTable).isEqualTo("catalog_asset");
    }

    @Test
    void portalMenuDisableRuleMapsToUpdateOperation() {
        AuditOperationMapping mapping = createMapping(
            "/api/admin/portal/menus/{id}",
            "DELETE",
            "菜单管理",
            AuditOperationType.UPDATE.getCode(),
            "禁用门户菜单：{targetRef|未知}",
            "门户菜单"
        );
        mapping.setParamExtractors("{\"targetRef\":\"path.id\"}");
        repository = createRepository(List.of(mapping));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/admin/portal/menus/42");
        event.setHttpMethod("DELETE");
        event.setSourceSystem("admin");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isTrue();
        assertThat(op.operationType).isEqualTo(AuditOperationType.UPDATE);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.UPDATE.getDisplayName());
        assertThat(op.description).isEqualTo("禁用门户菜单：42");
    }

    @Test
    void resolveWithFallbackUsesDictionaryCategoryForApprovals() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/approval-requests/1601");
        event.setHttpMethod("POST");
        event.setAction("APPROVAL_EXECUTE");
        event.setSummary("执行了操作：/api/approval-requests/1601");
        event.setResourceType("admin_approval");
        event.setDetails("{\"源表\":\"admin_approval\",\"目标ID\":\"1601\"}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isFalse();
        assertThat(op.moduleName).isEqualTo("审批管理");
        assertThat(op.sourceTable).isEqualTo("admin_approval");
    }

    @Test
    void resolveWithFallbackCategorizesOrganizationEvents() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/platform/orgs");
        event.setHttpMethod("POST");
        event.setSummary("执行了操作：/api/platform/orgs");
        event.setResourceType("organization");
        event.setDetails("{\"源表\":\"orgs\",\"目标ID\":\"89155ebc-c3ed-4f30-8cae-859136ef53be\"}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isFalse();
        assertThat(op.moduleName).isEqualTo("部门管理");
        assertThat(op.sourceTable).isEqualTo("orgs");
    }

    @Test
    void resolveWithFallbackHonorsDisplayNameAliases() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/catalog/datasets/cecae4d8-3e93-4186-9b65-6d6c22096135");
        event.setHttpMethod("POST");
        event.setSummary("执行了操作：/api/catalog/datasets/cecae4d8-3e93-4186-9b65-6d6c22096135");
        event.setResourceType("catalog_dataset");
        event.setDetails("{\"源表\":\"catalog_dataset\",\"目标ID\":\"cecae4d8-3e93-4186-9b65-6d6c22096135\"}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isFalse();
        assertThat(op.moduleName).isEqualTo("数据资产");
        assertThat(op.sourceTable).isEqualTo("catalog_dataset");
    }

    @Test
    void resolveWithFallbackMapsKeycloakUsers() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/keycloak/users/22ddf810-6d11-456f-b808-2d068b29810b/roles");
        event.setHttpMethod("POST");
        event.setSummary("执行了操作：/api/keycloak/users/22ddf810-6d11-456f-b808-2d068b29810b/roles");
        event.setResourceType("users");
        event.setDetails("{\"源表\":\"users\",\"目标ID\":\"b345b069-93df-4c84-8640-88ddbf9a4793\"}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isFalse();
        assertThat(op.moduleName).isEqualTo("用户管理");
        assertThat(op.sourceTable).isEqualTo("users");
    }

    @Test
    void describeRulesExposesLoadedRuleMetadata() {
        AuditOperationMapping mapping = createMapping(
            "/api/approvals",
            "POST",
            "审批中心",
            AuditOperationType.EXECUTE.getCode(),
            "执行审批{event.actor}",
            "admin_approval"
        );
        repository = createRepository(List.of(mapping));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        List<OperationMappingEngine.RuleSummary> summaries = engine.describeRules();
        assertThat(summaries).hasSize(1);
        OperationMappingEngine.RuleSummary summary = summaries.get(0);
        assertThat(summary.getId()).isEqualTo(mapping.getId());
        assertThat(summary.getModuleName()).isEqualTo("审批中心");
        assertThat(summary.getOperationType()).isEqualTo(AuditOperationType.EXECUTE.getCode());
        assertThat(summary.getOperationTypeLabel()).isEqualTo(AuditOperationType.EXECUTE.getDisplayName());
        assertThat(summary.getDescriptionTemplate()).contains("审批");
        assertThat(summary.getSourceTableTemplate()).isEqualTo("admin_approval");
    }

    @Test
    void approvalRequestDetailRuleOverridesCatchAll() {
        AuditOperationMapping specific = createMapping(
            "/api/approval-requests/{id}",
            "GET",
            "审批管理",
            AuditOperationType.READ.getCode(),
            "查询审批请求：{rid|未知}",
            "admin_approval"
        );
        specific.setParamExtractors("{\"rid\":\"path.id\"}");
        specific.setOrderValue(12);

        AuditOperationMapping catchAll = createMapping(
            "/**",
            "ALL",
            "未知模块",
            AuditOperationType.EXECUTE.getCode(),
            "执行了操作：{event.requestUri|未知}",
            "general"
        );
        catchAll.setOrderValue(10000);

        repository = createRepository(List.of(specific, catchAll));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/approval-requests/1601");
        event.setHttpMethod("GET");
        event.setSourceSystem("admin");
        event.setDetails("{}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isTrue();
        assertThat(op.moduleName).isEqualTo("审批管理");
        assertThat(op.operationType).isEqualTo(AuditOperationType.READ);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.READ.getDisplayName());
        assertThat(op.description).isEqualTo("查询审批请求：1601");
    }

    @Test
    void datasetPreviewRuleUsesDatasetName() {
        AuditOperationMapping datasetRule = createMapping(
            "/api/datasets/{id}/preview",
            "GET",
            "数据资产",
            AuditOperationType.READ.getCode(),
            "预览数据资产：{datasetName|未知}（ID：{datasetId|未知}）",
            "catalog_dataset"
        );
        datasetRule.setParamExtractors("{\"datasetId\":\"path.id\",\"datasetName\":\"details.datasetName\"}");
        datasetRule.setOrderValue(30);

        AuditOperationMapping catchAll = createMapping(
            "/**",
            "ALL",
            "未知模块",
            AuditOperationType.EXECUTE.getCode(),
            "执行了操作：{event.requestUri|未知}",
            "general"
        );
        catchAll.setOrderValue(10000);

        repository = createRepository(List.of(datasetRule, catchAll));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/datasets/db486a4a-fb64-4218-907b-b582a764e66e/preview");
        event.setHttpMethod("GET");
        event.setSourceSystem("platform");
        event.setDetails("{\"datasetName\":\"客户订单\",\"源表\":\"catalog_dataset\",\"源表描述\":\"数据资产\"}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isTrue();
        assertThat(op.moduleName).isEqualTo("数据资产");
        assertThat(op.operationType).isEqualTo(AuditOperationType.READ);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.READ.getDisplayName());
        assertThat(op.description).contains("客户订单");
        assertThat(op.description).contains("db486a4a-fb64-4218-907b-b582a764e66e");
    }

    @Test
    void specificKeycloakApprovalRuleBeatsCatchAll() {
        AuditOperationMapping specific = createMapping(
            "/api/keycloak/approvals/{id}/{action}",
            "POST",
            "审批管理",
            AuditOperationType.UPDATE.getCode(),
            "处理审批：{rid|未知}",
            "admin_approval"
        );
        specific.setParamExtractors("{\"rid\":\"path.id\"}");
        specific.setOrderValue(10);

        AuditOperationMapping catchAll = createMapping(
            "/**",
            "ALL",
            "未知模块",
            AuditOperationType.EXECUTE.getCode(),
            "执行了操作：{event.requestUri|未知}",
            "general"
        );
        catchAll.setOrderValue(10000);

        repository = createRepository(List.of(specific, catchAll));
        engine = new OperationMappingEngine(repository, jdbcTemplate, dictionaryService);
        engine.reload();

        AuditEvent event = new AuditEvent();
        event.setRequestUri("/api/keycloak/approvals/5901/approve");
        event.setHttpMethod("POST");
        event.setSourceSystem("admin");
        event.setDetails("{}");

        Optional<OperationMappingEngine.ResolvedOperation> resolved = engine.resolveWithFallback(event);
        assertThat(resolved).isPresent();
        OperationMappingEngine.ResolvedOperation op = resolved.orElseThrow();
        assertThat(op.ruleMatched).isTrue();
        assertThat(op.moduleName).isEqualTo("审批管理");
        assertThat(op.operationType).isEqualTo(AuditOperationType.UPDATE);
        assertThat(op.operationTypeLabel).isEqualTo(AuditOperationType.UPDATE.getDisplayName());
        assertThat(op.description).contains("处理审批");
    }

    private AuditOperationMappingRepository createRepository(List<AuditOperationMapping> mappings) {
        return (AuditOperationMappingRepository) Proxy.newProxyInstance(
            AuditOperationMappingRepository.class.getClassLoader(),
            new Class[] { AuditOperationMappingRepository.class },
            (proxy, method, args) -> {
                if ("findAllByEnabledTrueOrderByOrderValueAscIdAsc".equals(method.getName())) {
                    return mappings;
                }
                throw new UnsupportedOperationException("Method " + method.getName() + " not supported in tests.");
            }
        );
    }

    private AuditResourceDictionaryRepository createDictionaryRepository(List<AuditResourceDictionary> entries) {
        return (AuditResourceDictionaryRepository) Proxy.newProxyInstance(
            AuditResourceDictionaryRepository.class.getClassLoader(),
            new Class[] { AuditResourceDictionaryRepository.class },
            (proxy, method, args) -> {
                if ("findAllByEnabledTrueOrderByOrderValueAscResourceKeyAsc".equals(method.getName())) {
                    return entries;
                }
                throw new UnsupportedOperationException("Method " + method.getName() + " not supported in tests.");
            }
        );
    }

    private AuditResourceDictionary dictionaryEntry(String key, String displayName, String category, String aliases, int order) {
        AuditResourceDictionary dict = new AuditResourceDictionary();
        dict.setResourceKey(key);
        dict.setDisplayName(displayName);
        dict.setCategory(category);
        dict.setAliases(aliases);
        dict.setEnabled(Boolean.TRUE);
        dict.setOrderValue(order);
        return dict;
    }

    private static final class StubJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Integer.class) {
                return requiredType.cast(Integer.valueOf(1));
            }
            return super.queryForObject(sql, requiredType, args);
        }
    }

    private AuditOperationMapping createMapping(
        String url,
        String method,
        String moduleName,
        String operationTypeCode,
        String descriptionTemplate,
        String sourceTableTemplate
    ) {
        AuditOperationMapping mapping = new AuditOperationMapping();
        mapping.setId(idSequence.getAndIncrement());
        mapping.setUrlPattern(url);
        mapping.setHttpMethod(method);
        mapping.setModuleName(moduleName);
        mapping.setOperationType(operationTypeCode);
        mapping.setDescriptionTemplate(descriptionTemplate);
        mapping.setSourceTableTemplate(sourceTableTemplate);
        mapping.setEnabled(Boolean.TRUE);
        mapping.setOrderValue(100);
        return mapping;
    }
}
