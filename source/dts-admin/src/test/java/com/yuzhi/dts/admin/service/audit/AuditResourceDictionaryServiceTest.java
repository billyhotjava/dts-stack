package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.admin.domain.AuditResourceDictionary;
import com.yuzhi.dts.admin.repository.AuditResourceDictionaryRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AuditResourceDictionaryServiceTest {

    private AuditResourceDictionaryService service;
    private final AtomicLong ids = new AtomicLong(1L);

    @BeforeEach
    void setUp() {
        AuditResourceDictionaryRepository repository = createRepository(
            List.of(
                entry("admin_keycloak_user", "用户", "用户管理", "admin_keycloak_user,admin_keycloak_users,admin,user,users,admin.user,admin.users,keycloak.user,keycloak.users,admin.auth", 10),
                entry("organization_node", "部门", "部门管理", "organization_node,organization,org,orgs,platform.org,platform.orgs,admin.org,admin.orgs", 12),
                entry("approval_request", "审批请求", "审批管理", "admin_approval,admin_approval_item,admin_approval_request,admin.approval_requests,admin.approval_request,approval_request,approval_requests,approvals,approval", 15),
                entry("catalog_dataset", "数据资产", "数据资产", "catalog_dataset,catalog.datasets,catalog*,catalog_secure_view,catalog_row_filter_rule,catalog_access_policy,catalog_asset,数据资产", 40),
                entry("audit_log", "审计日志", "审计日志", "audit_log,audit_logs,audit_log_detail,audit_event", 60)
            )
        );
        service = new AuditResourceDictionaryService(repository, new StubJdbcTemplate());
    }

    @Test
    void resolveLabelMatchesExactAlias() {
        assertThat(service.resolveLabel("audit_logs")).contains("审计日志");
    }

    @Test
    void resolveLabelMatchesPrefixAlias() {
        assertThat(service.resolveLabel("catalog_secure_view")).contains("数据资产");
    }

    @Test
    void resolveLabelHandlesKeycloakUsers() {
        assertThat(service.resolveLabel("users")).contains("用户");
        assertThat(service.resolveCategory("keycloak.users")).contains("用户管理");
    }

    @Test
    void resolveCategoryFallsBackToEntry() {
        assertThat(service.resolveCategory("catalog_table_schema")).contains("数据资产");
    }

    @Test
    void resolveLabelMatchesOrganizationPlural() {
        assertThat(service.resolveLabel("orgs")).contains("部门");
        assertThat(service.resolveCategory("platform.orgs")).contains("部门管理");
    }

    @Test
    void resolveLabelHandlesAdminApprovalAlias() {
        assertThat(service.resolveLabel("admin_approval")).contains("审批请求");
        assertThat(service.resolveCategory("admin_approval")).contains("审批管理");
    }

    @Test
    void resolveLabelMatchesDisplayName() {
        assertThat(service.resolveLabel("数据资产")).contains("数据资产");
        assertThat(service.resolveCategory("数据资产")).contains("数据资产");
    }

    @Test
    void moduleCategoriesFollowDictionaryOrder() {
        assertThat(service.listModuleCategories()).containsExactly("用户管理", "部门管理", "审批管理", "数据资产", "审计日志");
    }

    private AuditResourceDictionary entry(String key, String display, String category, String aliases, int order) {
        AuditResourceDictionary dict = new AuditResourceDictionary();
        dict.setId(ids.getAndIncrement());
        dict.setResourceKey(key);
        dict.setDisplayName(display);
        dict.setCategory(category);
        dict.setAliases(aliases);
        dict.setEnabled(Boolean.TRUE);
        dict.setOrderValue(order);
        return dict;
    }

    private AuditResourceDictionaryRepository createRepository(List<AuditResourceDictionary> entries) {
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

    private static final class StubJdbcTemplate extends JdbcTemplate {
        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            if (requiredType == Integer.class) {
                return requiredType.cast(1);
            }
            return super.queryForObject(sql, requiredType, args);
        }
    }
}
