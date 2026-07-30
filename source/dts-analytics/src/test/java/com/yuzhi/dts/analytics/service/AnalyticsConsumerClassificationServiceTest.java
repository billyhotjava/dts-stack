package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
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
    private AnalyticsScreenRepository screenRepository;
    private ObjectMapper objectMapper;
    private AnalyticsConsumerClassificationService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AnalyticsClassificationClient.class);
        screenRepository = Mockito.mock(AnalyticsScreenRepository.class);
        objectMapper = new ObjectMapper();
        service = new AnalyticsConsumerClassificationService(
            client,
            Mockito.mock(AnalyticsCardRepository.class),
            Mockito.mock(AnalyticsDashboardCardRepository.class),
            Mockito.mock(AnalyticsMetricRepository.class),
            screenRepository,
            Mockito.mock(AnalyticsTableRepository.class),
            Mockito.mock(AnalyticsDatabaseRepository.class),
            objectMapper
        );
    }

    @Test
    void unresolved_api_source_can_be_saved_as_governance_blocked_draft() throws Exception {
        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setId(16L);
        screen.setClassification("CONFIDENTIAL");
        screen.setManualClassificationFloor("CONFIDENTIAL");
        screen.setComponentsJson("[]");
        screen.setPagesJson("""
            [
              {
                "id": "overview",
                "components": [
                  {
                    "id": "project-overview",
                    "dataSource": {
                      "type": "api",
                      "sourceType": "api",
                      "apiConfig": {
                        "url": "/bi/api/project-cockpit/screen/overview",
                        "method": "GET"
                      }
                    }
                  }
                ]
              }
            ]
            """);

        service.prepareScreenDraft(screen);

        assertThat(screen.getClassification()).isEqualTo("CONFIDENTIAL");
        assertThat(screen.getClassificationSnapshotId()).isNull();
        assertThat(screen.getClassificationSnapshotVersion()).isNull();
        JsonNode evidence = objectMapper.readTree(screen.getClassificationEvidenceJson());
        assertThat(evidence.path("status").asText()).isEqualTo("BLOCKED_UNRESOLVED");
        assertThat(evidence.path("unresolvedSources").size()).isEqualTo(1);
        assertThat(evidence.path("unresolvedSources").get(0).asText())
            .contains("/bi/api/project-cockpit/screen/overview");
    }

    @Test
    void unresolved_api_source_remains_blocked_for_strict_derivation() {
        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setId(16L);
        screen.setClassification("CONFIDENTIAL");
        screen.setManualClassificationFloor("CONFIDENTIAL");
        screen.setComponentsJson("[]");
        screen.setPagesJson("""
            [
              {
                "id": "overview",
                "components": [
                  {
                    "id": "project-overview",
                    "dataSource": {
                      "type": "api",
                      "apiConfig": {
                        "url": "/bi/api/project-cockpit/screen/overview"
                      }
                    }
                  }
                ]
              }
            ]
            """);

        assertThatThrownBy(() -> service.deriveScreen(screen))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without classification identity")
            .hasMessageContaining("/bi/api/project-cockpit/screen/overview");
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
