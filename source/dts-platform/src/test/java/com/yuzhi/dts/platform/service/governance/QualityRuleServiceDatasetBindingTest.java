package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleBindingRequest;
import com.yuzhi.dts.platform.service.governance.request.QualityRuleUpsertRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class QualityRuleServiceDatasetBindingTest {

    private static final UUID RULE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID BINDING_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");

    @Mock
    private GovRuleRepository ruleRepository;

    @Mock
    private GovRuleVersionRepository versionRepository;

    @Mock
    private GovRuleBindingRepository bindingRepository;

    @Mock
    private CatalogDatasetRepository datasetRepository;

    @Mock
    private DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    @Mock
    private QualityAuditRecorder qualityAuditRecorder;

    @Mock
    private GovernanceProperties properties;

    @Mock
    private AccessChecker accessChecker;

    @Mock
    private OrganizationVisibilityService organizationVisibilityService;

    @Mock
    private QualityEffectiveDepartmentResolver departmentResolver;

    @Mock
    private QualityDatasetReadGuard datasetReadGuard;

    private QualityRuleService service;

    @BeforeEach
    void setUp() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken("actor", "n/a", AuthoritiesConstants.ADMIN)
            );
        service = new QualityRuleService(
            ruleRepository,
            versionRepository,
            bindingRepository,
            datasetRepository,
            defaultLakeDatasetGuard,
            qualityAuditRecorder,
            new ObjectMapper().findAndRegisterModules(),
            properties,
            accessChecker,
            organizationVisibilityService,
            departmentResolver,
            datasetReadGuard,
            mock(QualityRulePreflightService.class)
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsVersionBindingFromTheRuleDatasetWhenBindingsAreOmitted() {
        AtomicReference<GovRule> savedRule = new AtomicReference<>();
        when(ruleRepository.findByCode(any())).thenReturn(Optional.empty());
        when(ruleRepository.save(any(GovRule.class))).thenAnswer(invocation -> {
            GovRule rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            savedRule.set(rule);
            return rule;
        });
        when(ruleRepository.findById(RULE_ID)).thenAnswer(invocation -> Optional.of(savedRule.get()));
        when(versionRepository.save(any(GovRuleVersion.class))).thenAnswer(invocation -> {
            GovRuleVersion version = invocation.getArgument(0);
            version.setId(VERSION_ID);
            return version;
        });
        when(versionRepository.findByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(List.of());
        when(bindingRepository.save(any(GovRuleBinding.class))).thenAnswer(invocation -> {
            GovRuleBinding binding = invocation.getArgument(0);
            binding.setId(BINDING_ID);
            return binding;
        });

        QualityRuleUpsertRequest request = new QualityRuleUpsertRequest();
        request.setName("订单完整性检查");
        request.setType("COMPLETENESS");
        request.setSeverity("MEDIUM");
        request.setExecutor("hive");
        request.setEnabled(true);
        request.setPublishNow(true);
        request.setDatasetId(DATASET_ID);
        request.setDefinition(Map.of("sql", "select 1"));

        service.createRule(request, "actor", "dept-a");

        ArgumentCaptor<GovRuleBinding> binding = ArgumentCaptor.forClass(GovRuleBinding.class);
        verify(bindingRepository).save(binding.capture());
        assertThat(binding.getValue().getDatasetId()).isEqualTo(DATASET_ID);
        assertThat(binding.getValue().getRuleVersion().getId()).isEqualTo(VERSION_ID);
        verify(datasetReadGuard).requireReadable(DATASET_ID, "dept-a");
    }

    @Test
    void rejectsABindingThatDoesNotMatchTheRuleDataset() {
        UUID anotherDatasetId = UUID.fromString("40000000-0000-0000-0000-000000000002");
        QualityRuleUpsertRequest request = executableRequest(DATASET_ID);
        QualityRuleBindingRequest binding = new QualityRuleBindingRequest();
        binding.setDatasetId(anotherDatasetId);
        binding.setScopeType("DATASET");
        request.setBindings(List.of(binding));

        assertThatThrownBy(() -> service.createRule(request, "actor", "dept-a"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("绑定数据资产必须与规则数据资产一致");
    }

    @Test
    void rejectsCreatingARuleForAnUnreadableDatasetBeforePersistence() {
        QualityRuleUpsertRequest request = executableRequest(DATASET_ID);
        doThrow(new AccessDeniedException("denied"))
            .when(datasetReadGuard)
            .requireReadable(DATASET_ID, "dept-a");

        assertThatThrownBy(() -> service.createRule(request, "actor", "dept-a"))
            .isInstanceOf(AccessDeniedException.class);
        verify(ruleRepository, org.mockito.Mockito.never()).save(any(GovRule.class));
    }

    @Test
    void rejectsPublishingAVersionWithoutExecutableStatements() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("空规则");

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("DRAFT");
        version.setDefinition("{}");

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(versionRepository.findByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(List.of(version));

        assertThatThrownBy(() ->
            service.changeRuleVersionStatus(RULE_ID, 1, "PUBLISHED", null, "actor", null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
    }

    @Test
    void rejectsCreatingAPublishedRuleWithoutExecutableStatements() {
        when(ruleRepository.findByCode(any())).thenReturn(Optional.empty());

        QualityRuleUpsertRequest request = new QualityRuleUpsertRequest();
        request.setName("空规则");
        request.setExecutor("hive");
        request.setPublishNow(true);
        request.setDatasetId(DATASET_ID);
        request.setDefinition(Map.of());

        assertThatThrownBy(() -> service.createRule(request, "actor"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("未配置可执行检测语句");
    }

    @Test
    void rejectsCreatingAPublishedRuleWithoutADatasetBinding() {
        QualityRuleUpsertRequest request = executableRequest(null);

        assertThatThrownBy(() -> service.createRule(request, "actor", "dept-a"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("已发布质量规则必须绑定数据资产");
    }

    @Test
    void rejectsUpdateWhenTheEditorVersionIsStaleBeforeWritingAnotherVersion() {
        GovRule rule = existingRule();
        GovRuleVersion latest = latestVersion(4);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        QualityRuleUpsertRequest request = executableRequest(DATASET_ID);
        request.setExpectedVersion(3);

        when(ruleRepository.findByIdForUpdate(RULE_ID)).thenReturn(Optional.of(rule));
        when(versionRepository.findFirstByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(Optional.of(latest));
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(departmentResolver.resolve("dept-a")).thenReturn("dept-a");

        assertThatThrownBy(() -> service.updateRule(RULE_ID, request, "actor", "dept-a"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("QUALITY_RULE_VERSION_CONFLICT");
        verify(versionRepository, org.mockito.Mockito.never()).save(any(GovRuleVersion.class));
    }

    @Test
    void rejectsRebindingAnExistingRuleEvenWithTheCurrentVersion() {
        UUID otherDataset = UUID.fromString("40000000-0000-0000-0000-000000000099");
        GovRule rule = existingRule();
        GovRuleVersion latest = latestVersion(4);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        QualityRuleUpsertRequest request = executableRequest(otherDataset);
        request.setExpectedVersion(4);

        when(ruleRepository.findByIdForUpdate(RULE_ID)).thenReturn(Optional.of(rule));
        when(versionRepository.findFirstByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(Optional.of(latest));
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);
        when(departmentResolver.resolve("dept-a")).thenReturn("dept-a");

        assertThatThrownBy(() -> service.updateRule(RULE_ID, request, "actor", "dept-a"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("QUALITY_RULE_DATASET_REBIND_FORBIDDEN");
        verify(datasetReadGuard, org.mockito.Mockito.never()).requireReadable(otherDataset, "dept-a");
    }

    @Test
    void rejectsPublishingADraftWhoseVersionHasNoBinding() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("待发布规则");
        rule.setDatasetId(DATASET_ID);

        GovRuleVersion version = new GovRuleVersion();
        version.setId(VERSION_ID);
        version.setRule(rule);
        version.setVersion(1);
        version.setStatus("DRAFT");
        version.setDefinition("{\"sql\":\"select id from public.orders\"}");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);

        when(ruleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
        when(versionRepository.findByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(List.of(version));
        when(bindingRepository.findByRuleVersionId(VERSION_ID)).thenReturn(List.of());
        when(datasetRepository.findById(DATASET_ID)).thenReturn(Optional.of(dataset));
        when(accessChecker.canRead(dataset)).thenReturn(true);

        assertThatThrownBy(() ->
            service.changeRuleVersionStatus(RULE_ID, 1, "PUBLISHED", null, "actor", "dept-a")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("发布质量规则前必须绑定数据资产");
    }

    @Test
    void strictSuccessAuditFailurePropagatesToRollbackTheRuleTransaction() {
        AtomicReference<GovRule> savedRule = new AtomicReference<>();
        when(ruleRepository.findByCode(any())).thenReturn(Optional.empty());
        when(ruleRepository.save(any(GovRule.class))).thenAnswer(invocation -> {
            GovRule rule = invocation.getArgument(0);
            rule.setId(RULE_ID);
            savedRule.set(rule);
            return rule;
        });
        when(ruleRepository.findById(RULE_ID)).thenAnswer(invocation -> Optional.of(savedRule.get()));
        when(versionRepository.save(any(GovRuleVersion.class))).thenAnswer(invocation -> {
            GovRuleVersion version = invocation.getArgument(0);
            version.setId(VERSION_ID);
            return version;
        });
        when(versionRepository.findByRuleIdOrderByVersionDesc(RULE_ID)).thenReturn(List.of());
        IllegalStateException auditFailure = new IllegalStateException("strict audit unavailable");
        doThrow(auditFailure).when(qualityAuditRecorder).recordAction(
            eq("GOV_RULE_MANAGE"), eq(AuditStage.SUCCESS), eq(RULE_ID.toString()), any()
        );

        assertThatThrownBy(() -> service.createRule(executableRequest(DATASET_ID), "actor")).isSameAs(auditFailure);
        assertThat(QualityRuleService.class.getAnnotation(Transactional.class)).isNotNull();
        verify(qualityAuditRecorder).recordFailureAction(eq("GOV_RULE_MANAGE"), eq("UNASSIGNED"), any());
    }

    @Test
    void failureAuditFailureNeverMasksTheOriginalRuleException() {
        when(ruleRepository.findByCode(any())).thenReturn(Optional.empty());
        IllegalStateException businessFailure = new IllegalStateException("rule repository unavailable");
        when(ruleRepository.save(any(GovRule.class))).thenThrow(businessFailure);
        doThrow(new IllegalStateException("failure audit unavailable"))
            .when(qualityAuditRecorder)
            .recordFailureAction(eq("GOV_RULE_MANAGE"), eq("UNASSIGNED"), any());

        assertThatThrownBy(() -> service.createRule(executableRequest(DATASET_ID), "actor")).isSameAs(businessFailure);
    }

    private QualityRuleUpsertRequest executableRequest(UUID datasetId) {
        QualityRuleUpsertRequest request = new QualityRuleUpsertRequest();
        request.setName("订单完整性检查");
        request.setType("COMPLETENESS");
        request.setSeverity("MEDIUM");
        request.setExecutor("hive");
        request.setEnabled(true);
        request.setPublishNow(true);
        request.setDatasetId(datasetId);
        request.setDefinition(Map.of("sql", "select 1"));
        return request;
    }

    private static GovRule existingRule() {
        GovRule rule = new GovRule();
        rule.setId(RULE_ID);
        rule.setName("订单完整性检查");
        rule.setDatasetId(DATASET_ID);
        return rule;
    }

    private static GovRuleVersion latestVersion(int version) {
        GovRuleVersion latest = new GovRuleVersion();
        latest.setId(VERSION_ID);
        latest.setVersion(version);
        return latest;
    }
}
