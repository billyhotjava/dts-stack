package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityScoreResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QualityScoreServiceTest {

    private static final UUID DATASET_ID = UUID.fromString("40000000-0000-0000-0000-000000000020");

    @Mock
    private GovQualityRunRepository runRepository;

    @Test
    void excludesSkippedRunsFromQualityScoresAndTrends() {
        GovQualityRun skipped = run("SKIPPED");
        when(runRepository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(eq(DATASET_ID), anyInstant()))
            .thenReturn(List.of(skipped));

        QualityScoreResult result = new QualityScoreService(runRepository).calculate(DATASET_ID, 30);

        assertThat(result.overall()).isZero();
        assertThat(result.dimensions()).isEmpty();
        assertThat(result.trend()).isEmpty();
    }

    @Test
    void keepsFailedRunsAsEffectiveZeroScoreResults() {
        GovQualityRun failed = run("FAILED");
        when(runRepository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(eq(DATASET_ID), anyInstant()))
            .thenReturn(List.of(failed));

        QualityScoreResult result = new QualityScoreService(runRepository).calculate(DATASET_ID, 30);

        assertThat(result.overall()).isZero();
        assertThat(result.dimensions()).hasSize(1);
        assertThat(result.dimensions().get(0).type()).isEqualTo("COMPLETENESS");
        assertThat(result.trend()).hasSize(1);
    }

    @Test
    void keepsLegacySuccessfulRunsAsEffectiveFullScoreResults() {
        GovQualityRun succeeded = run("SUCCESS");
        when(runRepository.findByDatasetIdAndFinishedAtAfterOrderByFinishedAtAsc(eq(DATASET_ID), anyInstant()))
            .thenReturn(List.of(succeeded));

        QualityScoreResult result = new QualityScoreService(runRepository).calculate(DATASET_ID, 30);

        assertThat(result.overall()).isEqualTo(100);
        assertThat(result.dimensions()).hasSize(1);
        assertThat(result.trend()).hasSize(1);
    }

    private GovQualityRun run(String status) {
        GovRule rule = new GovRule();
        rule.setType("COMPLETENESS");
        rule.setSeverity("MEDIUM");

        GovQualityRun run = new GovQualityRun();
        run.setRule(rule);
        run.setDatasetId(DATASET_ID);
        run.setStatus(status);
        run.setFinishedAt(Instant.now());
        return run;
    }

    private static Instant anyInstant() {
        return org.mockito.ArgumentMatchers.any(Instant.class);
    }
}
