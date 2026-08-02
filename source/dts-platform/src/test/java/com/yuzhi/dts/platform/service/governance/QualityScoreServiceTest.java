package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualityScoreServiceTest {

    private static final UUID DATASET_ID = UUID.fromString("50000000-0000-0000-0000-000000000093");

    @Test
    void failedExecutionWithCountedRowsScoresZeroInsteadOfOneHundred() {
        GovQualityRunRepository repository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun failed = run("FAILED", 20, 0);
        failed.setErrorCategory("SQL_EXECUTION_FAILED");
        when(repository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(eq(DATASET_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(failed));

        var result = new QualityScoreService(repository, readGuard).calculate(DATASET_ID, 30, "dept-a");

        assertThat(result.overall()).isZero();
        assertThat(result.dimensions()).singleElement().satisfies(dimension -> assertThat(dimension.score()).isZero());
    }

    @Test
    void dataQualityFailureStillUsesObservedFailingRows() {
        GovQualityRunRepository repository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        GovQualityRun failed = run("FAILED", 20, 5);
        failed.setErrorCategory(QualityRunOutcomeSemantics.QUALITY_VIOLATION);
        when(repository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(eq(DATASET_ID), org.mockito.ArgumentMatchers.any()))
            .thenReturn(List.of(failed));

        var result = new QualityScoreService(repository, readGuard).calculate(DATASET_ID, 30, "dept-a");

        assertThat(result.overall()).isEqualTo(75);
    }

    @Test
    void rejectsUnboundedScoringPeriodsBeforeReadingTheDataset() {
        GovQualityRunRepository repository = mock(GovQualityRunRepository.class);
        QualityDatasetReadGuard readGuard = mock(QualityDatasetReadGuard.class);
        QualityScoreService service = new QualityScoreService(repository, readGuard);

        for (int periodDays : List.of(-1, 0, 366)) {
            assertThatThrownBy(() -> service.calculate(DATASET_ID, periodDays, "dept-a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("统计周期必须在 1 到 365 天之间");
        }
        org.mockito.Mockito.verifyNoInteractions(readGuard, repository);
    }

    private static GovQualityRun run(String status, int rowsTotal, int failingRows) {
        GovRule rule = new GovRule();
        rule.setType("COMPLETENESS");
        rule.setSeverity("MEDIUM");
        GovQualityRun run = new GovQualityRun();
        run.setRule(rule);
        run.setStatus(status);
        run.setRowsTotal(rowsTotal);
        run.setFailingRowCount(failingRows);
        run.setFinishedAt(Instant.now());
        return run;
    }
}
