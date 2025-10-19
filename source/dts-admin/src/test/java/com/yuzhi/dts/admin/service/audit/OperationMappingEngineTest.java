package com.yuzhi.dts.admin.service.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.admin.domain.AuditEvent;
import com.yuzhi.dts.admin.domain.AuditOperationMapping;
import com.yuzhi.dts.admin.repository.AuditOperationMappingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationMappingEngineTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate = new StubJdbcTemplate();
    private OperationMappingEngine engine;
    private final AtomicLong idSequence = new AtomicLong(1L);
    private AuditOperationMappingRepository repository;

    @BeforeEach
    void setUp() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, objectMapper, jdbcTemplate);
    }

    @Test
    void resolveWithFallbackReturnsRuleMatchWhenMappingExists() {
        AuditOperationMapping mapping = createMapping("/api/users/{id}", "GET", "用户管理", "查询", "查看用户{person}", "用户");
        mapping.setParamExtractors("{\"person\":\"path.id\"}");
        repository = createRepository(List.of(mapping));
        engine = new OperationMappingEngine(repository, objectMapper, jdbcTemplate);

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
        assertThat(op.actionType).isEqualTo("查询");
        assertThat(op.description).isEqualTo("查看用户alice");
        assertThat(op.sourceTable).isEqualTo("用户");
    }

    @Test
    void resolveWithFallbackUsesFallbackWhenNoRuleMatches() {
        repository = createRepository(List.of());
        engine = new OperationMappingEngine(repository, objectMapper, jdbcTemplate);
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
        assertThat(op.actionType).isEqualTo("删除");
        assertThat(op.description).contains("数据资产");
        assertThat(op.description).contains("【123】");
        assertThat(op.sourceTable).isEqualTo("数据资产");
    }

    @Test
    void describeRulesExposesLoadedRuleMetadata() {
        AuditOperationMapping mapping = createMapping("/api/approvals", "POST", "审批中心", "执行", "执行审批{event.actor}", "审批请求");
        repository = createRepository(List.of(mapping));
        engine = new OperationMappingEngine(repository, objectMapper, jdbcTemplate);
        engine.reload();

        List<OperationMappingEngine.RuleSummary> summaries = engine.describeRules();
        assertThat(summaries).hasSize(1);
        OperationMappingEngine.RuleSummary summary = summaries.get(0);
        assertThat(summary.getId()).isEqualTo(mapping.getId());
        assertThat(summary.getModuleName()).isEqualTo("审批中心");
        assertThat(summary.getActionType()).isEqualTo("执行");
        assertThat(summary.getDescriptionTemplate()).contains("审批");
        assertThat(summary.getSourceTableTemplate()).isEqualTo("审批请求");
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
        String actionType,
        String descriptionTemplate,
        String sourceTableTemplate
    ) {
        AuditOperationMapping mapping = new AuditOperationMapping();
        mapping.setId(idSequence.getAndIncrement());
        mapping.setUrlPattern(url);
        mapping.setHttpMethod(method);
        mapping.setModuleName(moduleName);
        mapping.setActionType(actionType);
        mapping.setDescriptionTemplate(descriptionTemplate);
        mapping.setSourceTableTemplate(sourceTableTemplate);
        mapping.setEnabled(Boolean.TRUE);
        mapping.setOrderValue(100);
        return mapping;
    }
}
