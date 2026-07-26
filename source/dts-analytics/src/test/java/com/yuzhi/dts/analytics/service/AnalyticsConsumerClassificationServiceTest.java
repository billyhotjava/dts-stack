package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AnalyticsConsumerClassificationServiceTest {

    private AnalyticsClassificationClient client;
    private AnalyticsConsumerClassificationService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AnalyticsClassificationClient.class);
        service = new AnalyticsConsumerClassificationService(
            client,
            Mockito.mock(AnalyticsCardRepository.class),
            Mockito.mock(AnalyticsDashboardCardRepository.class),
            Mockito.mock(AnalyticsMetricRepository.class),
            Mockito.mock(AnalyticsScreenRepository.class),
            Mockito.mock(AnalyticsTableRepository.class),
            Mockito.mock(AnalyticsDatabaseRepository.class),
            new ObjectMapper()
        );
    }

    @Test
    void low_personnel_clearance_cannot_read_confidential_card() {
        when(client.requireCurrent("CARD", "analytics-card:7"))
            .thenReturn(current("CONFIDENTIAL"));

        assertThatThrownBy(() -> service.requireCardPersonnelClearance(7L, "GENERAL"))
            .isInstanceOf(AnalyticsConsumerClassificationService.PersonnelClassificationDeniedException.class);
    }

    @Test
    void low_personnel_clearance_cannot_read_confidential_dashboard_batch_url() {
        when(client.requireCurrent("REPORT", "analytics-dashboard:9"))
            .thenReturn(new AnalyticsClassificationClient.ClassificationResult(
                "REPORT",
                "analytics-dashboard:9",
                "CONFIDENTIAL",
                "snapshot-9",
                4L,
                List.of(),
                null,
                List.of()
            ));

        assertThatThrownBy(() -> service.requireDashboardPersonnelClearance(9L, "GENERAL"))
            .isInstanceOf(AnalyticsConsumerClassificationService.PersonnelClassificationDeniedException.class);
    }

    @Test
    void unknown_card_classification_fails_closed() {
        when(client.requireCurrent("CARD", "analytics-card:7"))
            .thenReturn(current("UNKNOWN_LEVEL"));

        assertThatThrownBy(() -> service.requireCardPersonnelClearance(7L, "CORE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing or unknown");
    }

    @Test
    void matching_clearance_can_read_and_seal_export() {
        when(client.requireCurrent("CARD", "analytics-card:7"))
            .thenReturn(current("SECRET"));
        when(client.sealExport(eq("file:7"), any(), eq("dts-analytics:card-export:7")))
            .thenReturn(new AnalyticsClassificationClient.ExportSeal("snapshot-7", "file:7", "SECRET", 3L));

        AnalyticsClassificationClient.ExportSeal seal =
            service.sealCardExport(7L, "file:7", "GENERAL");

        assertThat(seal.effectiveLevel()).isEqualTo("SECRET");
        verify(client).sealExport(eq("file:7"), any(), eq("dts-analytics:card-export:7"));
    }

    @Test
    void blocked_clearance_never_creates_export_seal() {
        when(client.requireCurrent("CARD", "analytics-card:7"))
            .thenReturn(current("CONFIDENTIAL"));

        assertThatThrownBy(() -> service.sealCardExport(7L, "file:7", "GENERAL"))
            .isInstanceOf(AnalyticsConsumerClassificationService.PersonnelClassificationDeniedException.class);
        verify(client, never()).sealExport(any(), any(), any());
    }

    private AnalyticsClassificationClient.ClassificationResult current(String effectiveLevel) {
        return new AnalyticsClassificationClient.ClassificationResult(
            "CARD",
            "analytics-card:7",
            effectiveLevel,
            "snapshot-7",
            3L,
            List.of(),
            null,
            List.of()
        );
    }
}
