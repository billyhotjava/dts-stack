package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import java.util.List;
import java.util.Set;

/** Versioned, safe quality facts. No SQL, row values, JDBC messages or credentials are serialized. */
public record QualityExecutionOutcome(
    int schemaVersion, String qualityOutcome, String executionOutcome, String statisticsStatus,
    Long violationOccurrences, List<Diagnostic> diagnostics
) {
    private static final ObjectMapper JSON = new ObjectMapper();
    public QualityExecutionOutcome {
        if (schemaVersion != 1 || !Set.of("PASSED", "VIOLATION", "UNKNOWN").contains(qualityOutcome)
            || !Set.of("OK", "FAILED").contains(executionOutcome)
            || !Set.of("EXACT", "UNDEDUPLICATED", "UNAVAILABLE").contains(statisticsStatus)) {
            throw new IllegalArgumentException("无效质量结论");
        }
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
    public boolean passed() { return "PASSED".equals(qualityOutcome) && "OK".equals(executionOutcome); }
    public boolean violated() { return "VIOLATION".equals(qualityOutcome); }
    public String json() {
        try { return JSON.writeValueAsString(this); }
        catch (Exception error) { throw new IllegalStateException("质量结论保存失败", error); }
    }
    public static QualityExecutionOutcome read(GovQualityRun run) {
        String json = run.getMetricsJson();
        if (json != null && json.stripLeading().startsWith("{")) {
            try { return JSON.readValue(json, QualityExecutionOutcome.class); }
            catch (Exception ignored) { return unknown("RESULT_CONTRACT_INVALID"); }
        }
        boolean passed = "SUCCEEDED".equalsIgnoreCase(run.getStatus()) || "SUCCESS".equalsIgnoreCase(run.getStatus()) || "PASSED".equalsIgnoreCase(run.getStatus());
        boolean violated = "FAILED".equalsIgnoreCase(run.getStatus()) && !QualityRunOutcomeSemantics.isExecutionFailure(run.getStatus(), run.getErrorCategory(), run.getFailingRowCount());
        return new QualityExecutionOutcome(1, passed ? "PASSED" : violated ? "VIOLATION" : "UNKNOWN",
            passed || violated ? "OK" : "FAILED", run.getFailingRowCount() != null && run.getRowsTotal() != null ? "EXACT" : "UNAVAILABLE",
            run.getFailingRowCount() == null ? null : run.getFailingRowCount().longValue(), List.of());
    }
    public static QualityExecutionOutcome unknown(String code) {
        return new QualityExecutionOutcome(1, "UNKNOWN", "FAILED", "UNAVAILABLE", null,
            List.of(new Diagnostic("execution", code, null)));
    }
    public record Diagnostic(String statementKey, String reasonCode, String detail) {
        public Diagnostic {
            statementKey = statementKey == null ? "sql" : statementKey.substring(0, Math.min(80, statementKey.length()));
            reasonCode = reasonCode != null && reasonCode.matches("[A-Z0-9_]{1,80}") ? reasonCode : "EXECUTION_ERROR";
            // Only parser-owned identifiers may pass through. Never disclose a referenced table or raw exception.
            if (!Set.of("UNSUPPORTED_FUNCTION", "UNSUPPORTED_CAST_TYPE").contains(reasonCode)
                || detail == null || !detail.matches("[A-Za-z0-9_ .]{1,80}")) detail = null;
        }
    }
}
