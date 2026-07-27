package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.security.HiveStatementExecutor;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class QualityRunServiceExecutableDefinitionTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000010");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000010");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000010");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000010");

    @Mock
    private GovRuleRepository ruleRepository;

    @Mock
    private GovRuleVersionRepository versionRepository;

    @Mock
    private GovRuleBindingRepository bindingRepository;

    @Mock
    private GovQualityRunRepository runRepository;

    @Mock
    private GovQualityMetricRepository metricRepository;

    @Mock
    private GovQualityFailingRowRepository failingRowRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private Executor taskExecutor;

    @Mock
    private HiveStatementExecutor hiveExecutor;

    @Mock
    private AuditService auditService;

    @Mock
    private IssueTicketService issueTicketService;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    private QualityRunService service;

    @BeforeEach
    void setUp() {
        service = new QualityRunService(
            ruleRepository,
            versionRepository,
            bindingRepository,
            runRepository,
            metricRepository,
            failingRowRepository,
            datasetRepository,
            taskExecutor,
            hiveExecutor,
            auditService,
            issueTicketService,
            new ObjectMapper(),
            new GovernanceProperties(),
            transactionManager,
            defaultLakeDatasetGuard
        );
    }

    @Test
    void rejectsEmptyPublishedRuleBeforeCreatingAQualityRun() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("空规则");

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        assertThatThrownBy(() -> service.trigger(request, "actor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
        verify(runRepository, never()).save(any());
    }

    @Test
    void rejectsNullSqlInLegacyPublishedRuleBeforeCreatingAQualityRun() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("空规则");

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":null}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        assertThatThrownBy(() -> service.trigger(request, "actor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
        verify(runRepository, never()).save(any());
    }

    @Test
    void keepsExecutablePublishedRulesOnTheNormalQueuedRunPath() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("有效规则");

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"sql\":\"select 1\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(UUID.fromString("50000000-0000-0000-0000-000000000010"));
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("QUEUED");
        verify(runRepository).save(any(GovQualityRun.class));
    }

    @Test
    void fallsBackToSqlWhenStatementsMapIsPresentButEmpty() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("混合定义规则");

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("PUBLISHED");
        version.setDefinition("{\"statements\":{},\"sql\":\"select 1\"}");

        GovRuleBinding binding = new GovRuleBinding();
        binding.setId(BINDING_ID);
        binding.setRuleVersion(version);
        binding.setDatasetId(DATASET_ID);
        version.setBindings(Set.of(binding));
        rule.setLatestVersion(version);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(runRepository.save(any(GovQualityRun.class))).thenAnswer(invocation -> {
            GovQualityRun run = invocation.getArgument(0);
            run.setId(UUID.fromString("50000000-0000-0000-0000-000000000011"));
            return run;
        });

        QualityRunTriggerRequest request = new QualityRunTriggerRequest();
        request.setRuleId(RULE_ID);

        List<QualityRunDto> result = service.trigger(request, "actor");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("QUEUED");
        verify(runRepository).save(any(GovQualityRun.class));
    }
}
