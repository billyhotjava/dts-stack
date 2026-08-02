package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualityRunOutcomeSemanticsTest {

    @Test
    void executionErrorDominatesQualityViolationRegardlessOfResultOrder() {
        StatementExecutionResult violation = new StatementExecutionResult(
            "nulls",
            "select ...",
            StatementExecutionResult.Status.FAILED,
            "发现 3 条不符合规则的数据"
        );
        StatementExecutionResult sqlFailure = new StatementExecutionResult(
            "range",
            "select ...",
            StatementExecutionResult.Status.FAILED,
            "permission denied",
            "PERMISSION_DENIED"
        );

        assertThat(QualityRunOutcomeSemantics.dominantFailureCategory(List.of(violation, sqlFailure)))
            .isEqualTo("PERMISSION_DENIED");
        assertThat(QualityRunOutcomeSemantics.dominantFailureCategory(List.of(sqlFailure, violation)))
            .isEqualTo("PERMISSION_DENIED");
        assertThat(QualityRunOutcomeSemantics.dominantFailureCategory(List.of(violation)))
            .isEqualTo(QualityRunOutcomeSemantics.QUALITY_VIOLATION);
    }
}
