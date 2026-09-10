package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import java.util.*;
import org.junit.jupiter.api.Test;

class F8QualitySemanticsTest {
    @Test void effectiveStatementsCannotBeHiddenBySql() {
        var statements = QualityRuleStatements.resolve(Map.of("sql", "SELECT * FROM public.safe", "statements", Map.of("bad", "DELETE FROM public.safe")));
        assertThat(statements).containsOnlyKeys("bad");
        assertThat(statements.get("bad")).startsWith("DELETE");
    }
    @Test void invalidStatementsCannotFallBackToSql() {
        assertThatThrownBy(() -> QualityRuleStatements.resolve(Map.of("sql", "SELECT * FROM public.safe", "statements", Map.of("bad", ""))))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void unknownOrUndeduplicatedCountsNeverBecomePerfectScores() {
        assertThat(QualityRunOutcomeSemantics.passRate("FAILED", "QUALITY_VIOLATION", 10, null)).isNull();
        assertThat(QualityRunOutcomeSemantics.passRate("SUCCEEDED", null, null, null)).isNull();
        assertThat(QualityRunOutcomeSemantics.passRate("FAILED", "CONNECTION_ERROR", 10, 0)).isNull();
        assertThat(QualityRunOutcomeSemantics.passRate("FAILED", "QUALITY_VIOLATION", 10, 2)).isEqualTo(80);
    }
    @Test void reloadPreservesViolationAndSafeFunctionDiagnostic() {
        var outcome = new QualityExecutionOutcome(1, "VIOLATION", "FAILED", "UNAVAILABLE", 2L,
            List.of(new QualityExecutionOutcome.Diagnostic("check", "UNSUPPORTED_FUNCTION", "pg_sleep")));
        var run = new GovQualityRun(); run.setStatus("FAILED"); run.setMetricsJson(outcome.json());
        assertThat(QualityExecutionOutcome.read(run)).isEqualTo(outcome);
        assertThat(QualityExecutionOutcome.read(run).violated()).isTrue();
        assertThat(QualityExecutionOutcome.read(run).passed()).isFalse();
    }
    @Test void diagnosticNeverLeaksTableOrJdbcSecrets() {
        var diagnostic = new QualityExecutionOutcome.Diagnostic("check", "OUT_OF_SCOPE_TABLE", "secret_schema.other_table password=secret");
        assertThat(diagnostic.detail()).isNull();
        assertThat(new QualityExecutionOutcome.Diagnostic("check", "UNSUPPORTED_FUNCTION", "pg_sleep(); password=secret").detail()).isNull();
    }
    @Test void draftPreviewUsesInputWithoutPublishedRuleOrPersistentRun() {
        UUID datasetId = UUID.randomUUID(); String sql = "SELECT project_code FROM public.projects";
        var access = mock(QualityDatasetReadGuard.class);
        var executor = mock(QualityDatasetStatementExecutor.class);
        var statements = QualityRuleStatements.resolve(Map.of("sql", sql));
        String checksum = QualityRuleStatements.checksum(statements);
        when(executor.validate(datasetId, statements)).thenReturn(new QualityDatasetStatementExecutor.Validation(true, checksum, List.of(), "count"));
        when(executor.execute(any(), eq(statements))).thenReturn(new QualityDatasetStatementExecutor.Execution(List.of(), 2, 0,
            new QualityExecutionOutcome(1, "PASSED", "OK", "EXACT", 0L, List.of())));
        var result = new QualityRulePreflightService(access, executor).preview(new QualityRulePreflightService.Request(datasetId, Map.of("sql", sql)), "dept");
        assertThat(result.checksum()).isEqualTo(checksum);
        verify(access).requireReadable(datasetId, "dept");
        verify(executor).execute(argThat(run -> run.getId() == null && run.getRule() == null && datasetId.equals(run.getDatasetId())), eq(statements));
    }
}
