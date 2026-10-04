package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import com.yuzhi.dts.platform.security.policy.AssetAction;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class QualityReportExportServiceTest {

    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000093");

    @Test
    void exportsCurrentSucceededStatusAndActualFailureCounts() throws Exception {
        QualityScoreService scoreService = mock(QualityScoreService.class);
        GovRuleRepository ruleRepository = mock(GovRuleRepository.class);
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        GovRule succeededRule = rule("成功规则", "10000000-0000-0000-0000-000000000093");
        GovRule failedRule = rule("失败规则", "10000000-0000-0000-0000-000000000094");
        GovQualityRun succeeded = run(succeededRule, "SUCCEEDED", 0, 0);
        GovQualityRun failed = run(failedRule, "FAILED", 20, 5);

        when(scoreService.calculate(DATASET_ID, 30, "dept-a"))
            .thenReturn(new QualityScoreResult(88, null, List.of(), List.of()));
        when(readGuard.requireReadable(DATASET_ID, "dept-a")).thenReturn(dataset);
        when(accessChecker.canPerform(dataset, AssetAction.EXPORT)).thenReturn(true);
        when(ruleRepository.findByDatasetId(DATASET_ID)).thenReturn(List.of(succeededRule, failedRule));
        when(runRepository.findByDatasetId(eq(DATASET_ID), any(Pageable.class))).thenReturn(List.of(succeeded, failed));

        QualityReportExportService service = new QualityReportExportService(
            scoreService,
            ruleRepository,
            runRepository,
            readGuard,
            accessChecker
        );

        byte[] bytes = service.exportExcel(DATASET_ID, 30, "dept-a");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("规则明细");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("SUCCEEDED");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("-");
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isZero();
            assertThat(sheet.getRow(2).getCell(4).getStringCellValue()).isEqualTo("75.00%");
            assertThat(sheet.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(5.0D);
        }
    }

    @Test
    void executionFailureWithCountedRowsNeverExportsAsFullyPassed() throws Exception {
        QualityScoreService scoreService = mock(QualityScoreService.class);
        GovRuleRepository ruleRepository = mock(GovRuleRepository.class);
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        GovRule failedRule = rule("SQL 执行失败规则", "10000000-0000-0000-0000-000000000095");
        GovQualityRun failed = run(failedRule, "FAILED", 20, 0);
        failed.setErrorCategory("SQL_EXECUTION_FAILED");

        when(scoreService.calculate(DATASET_ID, 30, "dept-a"))
            .thenReturn(new QualityScoreResult(0, null, List.of(), List.of()));
        when(readGuard.requireReadable(DATASET_ID, "dept-a")).thenReturn(dataset);
        when(accessChecker.canPerform(dataset, AssetAction.EXPORT)).thenReturn(true);
        when(ruleRepository.findByDatasetId(DATASET_ID)).thenReturn(List.of(failedRule));
        when(runRepository.findByDatasetId(eq(DATASET_ID), any(Pageable.class))).thenReturn(List.of(failed));

        QualityReportExportService service = new QualityReportExportService(
            scoreService,
            ruleRepository,
            runRepository,
            readGuard,
            accessChecker
        );

        byte[] bytes = service.exportExcel(DATASET_ID, 30, "dept-a");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("规则明细");
            assertThat(workbook.getSheet("评分概览").getRow(1).getCell(1).getStringCellValue()).isEqualTo("暂无有效评分");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("FAILED");
            assertThat(sheet.getRow(1).getCell(4).getStringCellValue()).isEqualTo("-");
        }
    }

    @Test
    void deniesReadableDatasetWhenExportActionIsNotAllowed() {
        QualityScoreService scoreService = mock(QualityScoreService.class);
        GovRuleRepository ruleRepository = mock(GovRuleRepository.class);
        GovQualityRunRepository runRepository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        AccessChecker accessChecker = mock(AccessChecker.class);
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(DATASET_ID);
        when(readGuard.requireReadable(DATASET_ID, "dept-a")).thenReturn(dataset);
        when(accessChecker.canPerform(dataset, AssetAction.EXPORT)).thenReturn(false);
        QualityReportExportService service = new QualityReportExportService(
            scoreService,
            ruleRepository,
            runRepository,
            readGuard,
            accessChecker
        );

        assertThatThrownBy(() -> service.exportExcel(DATASET_ID, 30, "dept-a"))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
            .hasMessage("asset_action_not_allowed:EXPORT");
    }

    private static GovRule rule(String name, String id) {
        GovRule rule = new GovRule();
        rule.setId(UUID.fromString(id));
        rule.setName(name);
        rule.setType("COMPLETENESS");
        return rule;
    }

    private static GovQualityRun run(GovRule rule, String status, int rowsTotal, int failingRows) {
        GovQualityRun run = new GovQualityRun();
        run.setRule(rule);
        run.setStatus(status);
        run.setRowsTotal(rowsTotal);
        run.setFailingRowCount(failingRows);
        run.setCreatedDate(Instant.now());
        return run;
    }
}
