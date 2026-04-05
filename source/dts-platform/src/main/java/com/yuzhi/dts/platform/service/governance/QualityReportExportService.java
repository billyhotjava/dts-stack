package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult.DimensionScore;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult.TrendPoint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class QualityReportExportService {

    private static final Logger log = LoggerFactory.getLogger(QualityReportExportService.class);

    private static final Map<String, String> DIMENSION_LABELS = Map.of(
        "COMPLETENESS", "完整性",
        "CONSISTENCY", "一致性",
        "ACCURACY", "准确性",
        "UNIQUENESS", "唯一性",
        "TIMELINESS", "及时性"
    );

    private final QualityScoreService qualityScoreService;
    private final GovRuleRepository ruleRepository;
    private final GovQualityRunRepository runRepository;

    public QualityReportExportService(
        QualityScoreService qualityScoreService,
        GovRuleRepository ruleRepository,
        GovQualityRunRepository runRepository
    ) {
        this.qualityScoreService = qualityScoreService;
        this.ruleRepository = ruleRepository;
        this.runRepository = runRepository;
    }

    public byte[] exportExcel(UUID datasetId, int periodDays) throws IOException {
        QualityScoreResult scoreResult = qualityScoreService.calculate(datasetId, periodDays);
        List<GovRule> rules = ruleRepository.findByDatasetId(datasetId);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            buildScoreSheet(workbook, scoreResult);
            buildRuleDetailSheet(workbook, rules, datasetId);
            buildTrendSheet(workbook, scoreResult);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    private void buildScoreSheet(XSSFWorkbook workbook, QualityScoreResult scoreResult) {
        Sheet sheet = workbook.createSheet("评分概览");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("维度");
        header.createCell(1).setCellValue("得分");
        header.createCell(2).setCellValue("较上期变化");

        int rowIdx = 1;

        // Overall row
        Row overallRow = sheet.createRow(rowIdx++);
        overallRow.createCell(0).setCellValue("综合");
        overallRow.createCell(1).setCellValue(scoreResult.overall());
        overallRow.createCell(2).setCellValue(formatDelta(scoreResult.overallDelta()));

        // Dimension rows
        for (DimensionScore dim : scoreResult.dimensions()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(DIMENSION_LABELS.getOrDefault(dim.type(), dim.type()));
            row.createCell(1).setCellValue(dim.score());
            row.createCell(2).setCellValue(formatDelta(dim.delta()));
        }
    }

    private void buildRuleDetailSheet(XSSFWorkbook workbook, List<GovRule> rules, UUID datasetId) {
        Sheet sheet = workbook.createSheet("规则明细");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("规则名称");
        header.createCell(1).setCellValue("类型");
        header.createCell(2).setCellValue("严重性");
        header.createCell(3).setCellValue("最近状态");
        header.createCell(4).setCellValue("通过率");
        header.createCell(5).setCellValue("失败行数");

        // Get latest run for each rule
        Map<UUID, GovQualityRun> latestRunByRule = findLatestRunPerRule(datasetId);

        int rowIdx = 1;
        for (GovRule rule : rules) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(rule.getName() != null ? rule.getName() : "");
            row.createCell(1).setCellValue(
                DIMENSION_LABELS.getOrDefault(
                    rule.getType() != null ? rule.getType().toUpperCase(java.util.Locale.ROOT) : "",
                    rule.getType() != null ? rule.getType() : ""
                )
            );
            row.createCell(2).setCellValue(rule.getSeverity() != null ? rule.getSeverity() : "");

            GovQualityRun latestRun = latestRunByRule.get(rule.getId());
            if (latestRun != null) {
                String status = latestRun.getStatus();
                row.createCell(3).setCellValue(status != null ? status : "");
                // passRate: SUCCESS=100%, FAILED with failingRowCount info available
                if ("SUCCESS".equalsIgnoreCase(status)) {
                    row.createCell(4).setCellValue("100%");
                    row.createCell(5).setCellValue(0);
                } else {
                    row.createCell(4).setCellValue("-");
                    row.createCell(5).setCellValue(0);
                }
            } else {
                row.createCell(3).setCellValue("-");
                row.createCell(4).setCellValue("-");
                row.createCell(5).setCellValue("-");
            }
        }
    }

    private void buildTrendSheet(XSSFWorkbook workbook, QualityScoreResult scoreResult) {
        Sheet sheet = workbook.createSheet("趋势数据");

        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("日期");
        header.createCell(1).setCellValue("综合得分");

        int rowIdx = 1;
        for (TrendPoint point : scoreResult.trend()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(point.date());
            row.createCell(1).setCellValue(point.overall());
        }
    }

    private Map<UUID, GovQualityRun> findLatestRunPerRule(UUID datasetId) {
        List<GovQualityRun> runs = runRepository.findByDatasetId(datasetId, PageRequest.of(0, 5000));
        return runs.stream()
            .filter(r -> r.getRule() != null)
            .collect(Collectors.toMap(
                r -> r.getRule().getId(),
                r -> r,
                (a, b) -> {
                    // Keep the one with the latest createdDate
                    if (a.getCreatedDate() == null) return b;
                    if (b.getCreatedDate() == null) return a;
                    return a.getCreatedDate().isAfter(b.getCreatedDate()) ? a : b;
                }
            ));
    }

    private String formatDelta(Integer delta) {
        if (delta == null) return "-";
        if (delta > 0) return "+" + delta;
        if (delta < 0) return String.valueOf(delta);
        return "0";
    }
}
