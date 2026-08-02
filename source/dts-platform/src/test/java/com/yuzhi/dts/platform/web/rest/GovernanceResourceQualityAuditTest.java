package com.yuzhi.dts.platform.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityFailingRow;
import com.yuzhi.dts.platform.domain.governance.GovQualityTemplate;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityTemplateRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.QualityAuditRecorder;
import com.yuzhi.dts.platform.service.governance.QualityDatasetReadGuard;
import com.yuzhi.dts.platform.service.governance.QualityReportExportService;
import com.yuzhi.dts.platform.service.governance.QualityRuleService;
import com.yuzhi.dts.platform.service.governance.QualityRunService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRuleDto;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.SqlRepairService;
import com.yuzhi.dts.platform.service.governance.SqlTemplateRenderer;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class GovernanceResourceQualityAuditTest {

    @Mock
    private AuditService auditService;

    @Mock
    private QualityAuditRecorder qualityAuditRecorder;

    @Mock
    private GovQualityTemplateRepository templateRepository;

    @Mock
    private SqlTemplateRenderer sqlTemplateRenderer;

    @Mock
    private GovQualityFailingRowRepository failingRowRepository;

    @Mock
    private QualityReportExportService qualityReportExportService;

    @Mock
    private SqlRepairService sqlRepairService;

    @Mock
    private QualityRunService qualityRunService;

    @Mock
    private QualityRuleService qualityRuleService;

    @Mock
    private QualityDatasetReadGuard qualityDatasetReadGuard;

    @Mock
    private GovRuleRepository ruleRepository;

    @Mock
    private GovRuleBindingRepository ruleBindingRepository;

    @Spy
    private GovernanceProperties governanceProperties = new GovernanceProperties();

    @InjectMocks
    private GovernanceResource resource;

    @BeforeEach
    void setUpSecurity() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", "n/a"));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditsTemplateCreateSuccessAndFailure() {
        UUID templateId = UUID.fromString("10000000-0000-0000-0000-000000000031");
        GovQualityTemplate template = new GovQualityTemplate();
        template.setCode("NULL_CHECK");
        template.setName("空值检查");
        when(templateRepository.save(template)).thenAnswer(invocation -> {
            template.setId(templateId);
            return template;
        });

        assertThat(resource.createTemplate(template).getData().getId()).isEqualTo(templateId);
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_TEMPLATE_CREATE"),
            eq(AuditStage.SUCCESS),
            eq(templateId.toString()),
            any()
        );

        GovQualityTemplate failingTemplate = new GovQualityTemplate();
        failingTemplate.setCode("RANGE_CHECK");
        failingTemplate.setName("范围检查");
        when(templateRepository.save(failingTemplate)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> resource.createTemplate(failingTemplate))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("database unavailable");
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_TEMPLATE_CREATE"),
            eq("RANGE_CHECK"),
            any()
        );
    }

    @Test
    void hidesTheUnsupportedCrossAssetTemplate() {
        GovQualityTemplate supported = new GovQualityTemplate();
        supported.setCode("NULL_CHECK");
        supported.setEnabled(Boolean.TRUE);
        supported.setDialect("INCEPTOR");
        GovQualityTemplate crossAsset = new GovQualityTemplate();
        crossAsset.setCode("CROSS_TABLE_REF");
        when(templateRepository.findAll()).thenReturn(List.of(supported, crossAsset));

        assertThat(resource.listTemplates().getData()).containsExactly(supported);
    }

    @Test
    void failingRowReadAuditsOnlyRunDatasetAndCountsWithoutActualValues() {
        UUID runId = UUID.fromString("11000000-0000-0000-0000-000000000031");
        UUID datasetId = UUID.fromString("12000000-0000-0000-0000-000000000031");
        QualityRunDto run = new QualityRunDto();
        run.setId(runId);
        run.setDatasetId(datasetId);
        GovQualityFailingRow row = new GovQualityFailingRow();
        row.setId(UUID.fromString("13000000-0000-0000-0000-000000000031"));
        row.setRowId("customer-42");
        row.setColumnName("secret_token");
        row.setActualValue("top-secret-value");
        when(qualityRunService.getRun(runId, "D01")).thenReturn(run);
        when(failingRowRepository.findByRunId(eq(runId), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(row)));

        assertThat(resource.listFailingRows(runId, 0, 20, null, "D01").getData().get("content").toString())
            .contains("top-secret-value");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_FAILING_ROW_VIEW"),
            eq(AuditStage.SUCCESS),
            eq(runId.toString()),
            payload.capture()
        );
        assertThat(String.valueOf(payload.getValue()))
            .contains(runId.toString())
            .contains(datasetId.toString())
            .contains("returnedCount=1")
            .doesNotContain("customer-42")
            .doesNotContain("secret_token")
            .doesNotContain("top-secret-value");
    }

    @Test
    void failingRowAuthorizationDenialWritesFailureAudit() {
        UUID runId = UUID.fromString("14000000-0000-0000-0000-000000000031");
        org.springframework.security.access.AccessDeniedException denied =
            new org.springframework.security.access.AccessDeniedException("denied");
        when(qualityRunService.getRun(runId, "D01")).thenThrow(denied);

        assertThatThrownBy(() -> resource.listFailingRows(runId, 0, 20, null, "D01")).isSameAs(denied);

        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_FAILING_ROW_VIEW"),
            eq(runId.toString()),
            any()
        );
    }

    @Test
    void auditsQualityReportExport() throws Exception {
        UUID datasetId = UUID.fromString("20000000-0000-0000-0000-000000000031");
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);
        when(qualityReportExportService.exportExcel(datasetId, 30, "D01")).thenReturn(new byte[] { 1, 2, 3 });

        resource.exportQualityReport(datasetId, 30, "D01", response);

        verify(qualityAuditRecorder).recordAttempt(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(AuditStage.BEGIN),
            eq(datasetId.toString()),
            any()
        );
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(AuditStage.SUCCESS),
            eq(datasetId.toString()),
            any()
        );
    }

    @Test
    void reportBeginAuditFailurePreventsAnyResponseDelivery() throws Exception {
        UUID datasetId = UUID.fromString("20000000-0000-0000-0000-000000000033");
        HttpServletResponse response = mock(HttpServletResponse.class);
        IllegalStateException auditFailure = new IllegalStateException("strict audit unavailable");
        org.mockito.Mockito.doThrow(auditFailure).when(qualityAuditRecorder).recordAttempt(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(AuditStage.BEGIN),
            eq(datasetId.toString()),
            any()
        );

        assertThatThrownBy(() -> resource.exportQualityReport(datasetId, 30, "D01", response))
            .isSameAs(auditFailure);

        org.mockito.Mockito.verifyNoInteractions(qualityReportExportService);
        verify(response, org.mockito.Mockito.never()).getOutputStream();
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(datasetId.toString()),
            any()
        );
    }

    @Test
    void reportSuccessAuditFailureStillLeavesDurableBeginEvidence() throws Exception {
        UUID datasetId = UUID.fromString("20000000-0000-0000-0000-000000000034");
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);
        when(qualityReportExportService.exportExcel(datasetId, 30, "D01")).thenReturn(new byte[] { 1, 2, 3 });
        IllegalStateException successAuditFailure = new IllegalStateException("success audit commit failed");
        org.mockito.Mockito.doThrow(successAuditFailure).when(qualityAuditRecorder).recordAction(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(AuditStage.SUCCESS),
            eq(datasetId.toString()),
            any()
        );

        assertThatThrownBy(() -> resource.exportQualityReport(datasetId, 30, "D01", response))
            .isSameAs(successAuditFailure);

        verify(qualityAuditRecorder).recordAttempt(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(AuditStage.BEGIN),
            eq(datasetId.toString()),
            any()
        );
        verify(output).write(any(byte[].class));
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_REPORT_EXPORT"),
            eq(datasetId.toString()),
            any()
        );
    }

    @Test
    void reportStreamFailureWritesOnlyFailureAudit() throws Exception {
        UUID datasetId = UUID.fromString("20000000-0000-0000-0000-000000000032");
        HttpServletResponse response = mock(HttpServletResponse.class);
        ServletOutputStream output = mock(ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(output);
        when(qualityReportExportService.exportExcel(datasetId, 30, "D01")).thenReturn(new byte[] { 1, 2, 3 });
        org.mockito.Mockito.doThrow(new IOException("client disconnected"))
            .when(output)
            .write(any(byte[].class));

        assertThatThrownBy(() -> resource.exportQualityReport(datasetId, 30, "D01", response))
            .isInstanceOf(IOException.class)
            .hasMessage("client disconnected");

        verify(qualityAuditRecorder, org.mockito.Mockito.never()).recordAction(
            eq("GOV_QUALITY_REPORT_EXPORT"), eq(AuditStage.SUCCESS), eq(datasetId.toString()), any()
        );
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_REPORT_EXPORT"), eq(datasetId.toString()), any()
        );
    }

    @Test
    void cleansingAndSqlRepairFailClosedAfterRunAuthorization() {
        UUID runId = UUID.fromString("30000000-0000-0000-0000-000000000031");
        UUID functionId = UUID.fromString("40000000-0000-0000-0000-000000000031");
        assertThatThrownBy(() -> resource.executeQualityCleansing(
            Map.of("runId", runId.toString(), "functionId", functionId.toString()),
            "D01"
        ))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("质量清洗执行暂未开放");
        verify(qualityRunService).assertRunReadable(runId, "D01");
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_CLEANSING_EXECUTE"), eq(runId.toString()), any()
        );

        String sql = "update ods_budget_v2 set project_name = trim(project_name)";
        when(sqlRepairService.execute(sql, runId)).thenThrow(new IllegalArgumentException("repair rejected"));

        assertThatThrownBy(() -> resource.executeSqlRepair(Map.of("sql", sql, "runId", runId.toString()), "D01"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("repair rejected");
        verify(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_SQL_REPAIR_EXECUTE"),
            eq(runId.toString()),
            any()
        );
    }

    @Test
    void sqlRepairFailureAuditIsSanitizedAndCannotMaskBusinessFailure() {
        UUID runId = UUID.fromString("50000000-0000-0000-0000-000000000031");
        String sensitiveSql = "update ods_secret set token = 'top-secret-token'";
        IllegalArgumentException businessFailure = new IllegalArgumentException(
            "driver rejected SQL " + sensitiveSql + " password=p@ssw0rd"
        );
        when(sqlRepairService.execute(sensitiveSql, runId)).thenThrow(businessFailure);
        doAnswer(invocation -> {
            Object payload = invocation.getArgument(2);
            assertThat(String.valueOf(payload))
                .doesNotContain(sensitiveSql)
                .doesNotContain("top-secret-token")
                .doesNotContain("p@ssw0rd")
                .contains("errorType")
                .contains("errorCategory");
            throw new IllegalStateException("audit unavailable");
        }).when(qualityAuditRecorder).recordFailureAction(
            eq("GOV_QUALITY_SQL_REPAIR_EXECUTE"), eq(runId.toString()), any()
        );

        assertThatThrownBy(() -> resource.executeSqlRepair(
            Map.of("sql", sensitiveSql, "runId", runId.toString()),
            "D01"
        ))
            .isSameAs(businessFailure);
    }

    @Test
    void autoTriggerUsesAuthorizedDatasetDepartmentAndServerActorWithoutCleansing() {
        UUID datasetId = UUID.fromString("60000000-0000-0000-0000-000000000031");
        UUID ruleId = UUID.fromString("70000000-0000-0000-0000-000000000031");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setHiveTable("ods_budget_v2");
        GovRule rule = new GovRule();
        rule.setId(ruleId);
        rule.setName("预算完整性检查");
        GovRule hiddenRule = new GovRule();
        hiddenRule.setId(UUID.fromString("71000000-0000-0000-0000-000000000031"));
        hiddenRule.setName("其他部门敏感规则");
        GovRule unboundRule = new GovRule();
        unboundRule.setId(UUID.fromString("72000000-0000-0000-0000-000000000031"));
        unboundRule.setName("当前资产未绑定规则");
        QualityRuleDto visibleRule = new QualityRuleDto();
        visibleRule.setId(ruleId);
        QualityRuleDto visibleUnboundRule = new QualityRuleDto();
        visibleUnboundRule.setId(unboundRule.getId());
        GovRuleVersion publishedVersion = new GovRuleVersion();
        publishedVersion.setRule(rule);
        publishedVersion.setStatus("PUBLISHED");
        GovRuleBinding binding = new GovRuleBinding();
        binding.setDatasetId(datasetId);
        binding.setRuleVersion(publishedVersion);
        when(qualityDatasetReadGuard.requireReadable(datasetId, "D01")).thenReturn(dataset);
        when(qualityRuleService.listAll("D01")).thenReturn(List.of(visibleRule, visibleUnboundRule));
        when(ruleBindingRepository.findByDatasetIdAndRuleVersionStatus(datasetId, "PUBLISHED"))
            .thenReturn(List.of(binding));
        when(ruleRepository.findByAutoTriggerTrueAndEnabledTrue()).thenReturn(List.of(rule, hiddenRule, unboundRule));
        when(qualityRunService.triggerAuthorizedIndependent(any(), eq("alice"), eq("D01"))).thenReturn(List.of());

        Map<String, Object> response = resource.autoTrigger(
            Map.of(
                "tableName", "ods_budget_v2",
                "datasetId", datasetId.toString(),
                "triggerRef", "mallory"
            ),
            "D01"
        ).getData();

        ArgumentCaptor<com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest> request =
            ArgumentCaptor.forClass(com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest.class);
        verify(qualityDatasetReadGuard).requireReadable(datasetId, "D01");
        verify(qualityRunService).triggerAuthorizedIndependent(request.capture(), eq("alice"), eq("D01"));
        assertThat(request.getValue().getDatasetId()).isEqualTo(datasetId);
        assertThat(request.getValue().getTriggerType()).isEqualTo("AUTO");
        assertThat(response.get("cleanse").toString()).contains("auto-cleanse-unavailable");
        assertThat(response.toString())
            .doesNotContain(hiddenRule.getId().toString())
            .doesNotContain(hiddenRule.getName())
            .doesNotContain(unboundRule.getId().toString())
            .doesNotContain(unboundRule.getName());
        verify(qualityAuditRecorder).recordAction(
            eq("GOV_AUTO_TRIGGER"),
            eq(AuditStage.SUCCESS),
            eq("ods_budget_v2"),
            any()
        );
    }

    @Test
    void autoTriggerStrictAuditFailurePropagatesAndWritesIndependentFailureEvidence() {
        UUID datasetId = UUID.fromString("73000000-0000-0000-0000-000000000031");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setHiveTable("ods_budget_v2");
        when(qualityDatasetReadGuard.requireReadable(datasetId, "D01")).thenReturn(dataset);
        when(qualityRuleService.listAll("D01")).thenReturn(List.of());
        when(ruleBindingRepository.findByDatasetIdAndRuleVersionStatus(datasetId, "PUBLISHED"))
            .thenReturn(List.of());
        IllegalStateException auditFailure = new IllegalStateException("strict audit unavailable");
        doAnswer(invocation -> {
            throw auditFailure;
        }).when(qualityAuditRecorder).recordAction(
            eq("GOV_AUTO_TRIGGER"), eq(AuditStage.SUCCESS), eq("ods_budget_v2"), any()
        );

        assertThatThrownBy(() -> resource.autoTrigger(
            Map.of("tableName", "ods_budget_v2", "datasetId", datasetId.toString()),
            "D01"
        )).isSameAs(auditFailure);

        verify(qualityAuditRecorder).recordFailureAction(eq("GOV_AUTO_TRIGGER"), eq("ods_budget_v2"), any());
    }

    @Test
    void autoTriggerUsesAnOuterAuditTransactionWhileEachRuleRunsIndependently() throws Exception {
        Transactional transaction = GovernanceResource.class
            .getMethod("autoTrigger", Map.class, String.class)
            .getAnnotation(Transactional.class);

        assertThat(transaction).isNotNull();
        assertThat(transaction.propagation()).isEqualTo(Propagation.REQUIRED);
        Transactional independent = QualityRunService.class
            .getMethod(
                "triggerAuthorizedIndependent",
                com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest.class,
                String.class,
                String.class
            )
            .getAnnotation(Transactional.class);
        assertThat(independent).isNotNull();
        assertThat(independent.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    @Test
    void serviceAuthorityWithoutTheIngestionPrincipalCannotSelectTrustedTrigger() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken(
                    "service:rogue",
                    "n/a",
                    AuthoritiesConstants.SERVICE_INTERNAL
                )
            );
        com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest request =
            new com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest();
        when(qualityRunService.trigger(request, "service:rogue", "D01")).thenReturn(List.of());

        resource.triggerQualityRun(request, "D01");

        verify(qualityRunService).trigger(request, "service:rogue", "D01");
        verify(qualityRunService, org.mockito.Mockito.never()).trigger(request, "service:rogue");
    }

    @Test
    void exactIngestionServicePrincipalCanSelectTrustedTrigger() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken(
                    "service:dts-ingestion",
                    "n/a",
                    AuthoritiesConstants.SERVICE_INTERNAL
                )
            );
        com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest request =
            new com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest();
        when(qualityRunService.triggerTrustedIngestion(request)).thenReturn(List.of());

        resource.triggerQualityRun(request, "D01");

        verify(qualityRunService).triggerTrustedIngestion(request);
        verify(qualityRunService, org.mockito.Mockito.never()).trigger(request, "service:dts-ingestion", "D01");
        verify(qualityAuditRecorder).recordMachine(
            eq("ingestion"),
            org.mockito.ArgumentMatchers.startsWith("quality-ingestion-trigger:"),
            any(Instant.class),
            eq("GOV_RULE_EXECUTE"),
            eq(AuditStage.SUCCESS),
            eq("trigger"),
            any()
        );
    }

    @Test
    void sameNamedHumanPrincipalCannotSelectTrustedIngestionTrigger() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new TestingAuthenticationToken(
                    "service:dts-ingestion",
                    "n/a",
                    AuthoritiesConstants.ADMIN
                )
            );
        com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest request =
            new com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest();
        when(qualityRunService.trigger(request, "service:dts-ingestion", "D01")).thenReturn(List.of());

        resource.triggerQualityRun(request, "D01");

        verify(qualityRunService).trigger(request, "service:dts-ingestion", "D01");
        verify(qualityRunService, org.mockito.Mockito.never()).triggerTrustedIngestion(request);
        verify(qualityAuditRecorder, org.mockito.Mockito.never()).recordMachine(
            eq("ingestion"),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
    }

    @Test
    void autoTriggerRejectsExplicitCleansingBeforeAnySideEffect() {
        UUID datasetId = UUID.fromString("80000000-0000-0000-0000-000000000031");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setHiveTable("ods_budget_v2");
        when(qualityDatasetReadGuard.requireReadable(datasetId, "D01")).thenReturn(dataset);

        assertThatThrownBy(() -> resource.autoTrigger(
            Map.of(
                "tableName", "ods_budget_v2",
                "datasetId", datasetId.toString(),
                "autoCleanse", true
            ),
            "D01"
        ))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessage("质量自动清洗暂未开放");

        org.mockito.Mockito.verifyNoInteractions(qualityRunService);
    }

    @Test
    void autoTriggerRejectsATableOutsideTheAuthorizedDatasetBeforeAnySideEffect() {
        UUID datasetId = UUID.fromString("90000000-0000-0000-0000-000000000031");
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(datasetId);
        dataset.setHiveTable("ods_authorized");
        when(qualityDatasetReadGuard.requireReadable(datasetId, "D01")).thenReturn(dataset);

        assertThatThrownBy(() -> resource.autoTrigger(
            Map.of("tableName", "ods_other_department", "datasetId", datasetId.toString()),
            "D01"
        ))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessage("请求表与授权数据集不匹配");

        org.mockito.Mockito.verifyNoInteractions(qualityRunService);
    }
}
