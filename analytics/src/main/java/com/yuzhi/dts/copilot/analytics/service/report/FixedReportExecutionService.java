package com.yuzhi.dts.copilot.analytics.service.report;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsReportTemplate;
import com.yuzhi.dts.copilot.analytics.service.report.ReportExecutionPlanService.ReportExecutionPlan;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FixedReportExecutionService {

    Optional<ExecutionResult> execute(
            AnalyticsReportTemplate template, Map<String, Object> parameters, ReportExecutionPlan plan) throws SQLException;

    record PreviewColumn(String key, String label, String baseType) {}

    record ExecutionResult(
            Long databaseId,
            String databaseName,
            List<PreviewColumn> columns,
            List<Map<String, Object>> rows,
            int rowCount,
            boolean truncated) {}
}
