package com.yuzhi.dts.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.domain.AnalyticsScreen;
import com.yuzhi.dts.analytics.domain.AnalyticsSemanticModel;
import com.yuzhi.dts.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsMetricRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsScreenRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsSemanticModelRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;

class AnalyticsConsumerClassificationServiceTest {

    private AnalyticsClassificationClient client;
    private AnalyticsScreenRepository screenRepository;
    private AnalyticsSemanticModelRepository semanticModelRepository;
    private AnalyticsTableRepository tableRepository;
    private AnalyticsDatabaseRepository databaseRepository;
    private ObjectMapper objectMapper;
    private AnalyticsConsumerClassificationService service;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(AnalyticsClassificationClient.class);
        screenRepository = Mockito.mock(AnalyticsScreenRepository.class);
        semanticModelRepository = Mockito.mock(AnalyticsSemanticModelRepository.class);
        tableRepository = Mockito.mock(AnalyticsTableRepository.class);
        databaseRepository = Mockito.mock(AnalyticsDatabaseRepository.class);
        objectMapper = new ObjectMapper();
        service = new AnalyticsConsumerClassificationService(
            client,
            Mockito.mock(AnalyticsCardRepository.class),
            Mockito.mock(AnalyticsDashboardCardRepository.class),
            Mockito.mock(AnalyticsMetricRepository.class),
            screenRepository,
            tableRepository,
            databaseRepository,
            semanticModelRepository,
            objectMapper
        );
    }

    @Test
    void semantic_card_inherits_the_physical_table_classification_instead_of_database_fallback() {
        AnalyticsCard card = new AnalyticsCard();
        card.setId(42L);
        card.setDatabaseId(1L);
        card.setDatasetQueryJson("""
            {
              "database": 1,
              "type": "semantic",
              "semantic_query": {
                "base": "model_spec_budget_kpi",
                "joins": [],
                "measures": ["model_spec_budget_kpi.pjm_budg_remaining"],
                "dimensions": ["model_spec_budget_kpi.snapshot_date"]
              }
            }
            """);

        AnalyticsSemanticModel model = new AnalyticsSemanticModel();
        model.setModelName("model_spec_budget_kpi");
        model.setTableId(15L);
        when(semanticModelRepository.findByModelNameIgnoreCase("model_spec_budget_kpi"))
            .thenReturn(Optional.of(model));

        AnalyticsTable table = new AnalyticsTable();
        table.setId(15L);
        table.setDatabaseId(1L);
        table.setSchemaName("public");
        table.setName("biz_ads_budget_kpi_v2");
        when(tableRepository.findById(15L)).thenReturn(Optional.of(table));

        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(1L);
        database.setDetailsJson("{\"platformDataSourceId\":\"a0000000-0000-0000-0000-000000000001\"}");
        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));

        service.deriveCard(card);

        verify(client).derive(
            eq("CARD"),
            eq("analytics-card:42"),
            isNull(),
            eq(List.of(new AnalyticsClassificationClient.SubjectRef(
                "ASSET",
                "source:a0000000-0000-0000-0000-000000000001/schema:public/table:biz_ads_budget_kpi_v2"
            ))),
            eq("dts-analytics:card:42")
        );
    }

    @Test
    void governed_analysis_card_inherits_its_published_query_dataset_classification() {
        UUID queryDatasetId = UUID.fromString("f306fd06-e1bb-4344-9eb5-f0620bb8f52c");
        AnalyticsCard card = new AnalyticsCard();
        card.setId(43L);
        card.setDatabaseId(1L);
        card.setQueryDatasetId(queryDatasetId);
        card.setDatasetQueryJson("{\"version\":\"dts.analysis/v1\"}");

        service.deriveCard(card);

        verify(client).derive(
            eq("CARD"),
            eq("analytics-card:43"),
            isNull(),
            eq(List.of(new AnalyticsClassificationClient.SubjectRef(
                "ASSET",
                "bi-dataset:" + queryDatasetId
            ))),
            eq("dts-analytics:card:43")
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
    void pending_upstream_classification_can_be_saved_as_governance_blocked_draft() throws Exception {
        AnalyticsScreen screen = databaseScreen(17L);
        AnalyticsClassificationClient.ClassificationContractException pending =
            pendingSourceClassification();
        when(client.derive(
            eq("SCREEN"),
            eq("screen:17"),
            eq("SECRET"),
            eq(List.of(new AnalyticsClassificationClient.SubjectRef(
                "ASSET",
                "data-source:a0000000-0000-0000-0000-000000000001"
            ))),
            eq("dts-analytics:screen:17")
        )).thenThrow(pending);

        service.prepareScreenDraft(screen);

        assertThat(screen.getClassification()).isEqualTo("SECRET");
        assertThat(screen.getClassificationSnapshotId()).isNull();
        assertThat(screen.getClassificationSnapshotVersion()).isNull();
        JsonNode evidence = objectMapper.readTree(screen.getClassificationEvidenceJson());
        assertThat(evidence.path("status").asText()).isEqualTo("BLOCKED_UPSTREAM");
        assertThat(evidence.path("blockers").get(0).asText())
            .isEqualTo("CONSUMER_CLASSIFICATION_SOURCE_MISSING");
        assertThat(service.hasUnresolvedScreenSources(screen)).isTrue();
        verify(screenRepository).save(screen);

        when(screenRepository.findById(17L)).thenReturn(Optional.of(screen));
        assertThatThrownBy(() -> service.requireCurrentScreen(17L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("governance-blocked");
        verify(client, never()).requireCurrent("SCREEN", "screen:17");

        assertThatThrownBy(() -> service.deriveScreen(screen)).isSameAs(pending);
    }

    @Test
    void unrelated_platform_failure_still_blocks_draft_save() {
        AnalyticsScreen screen = databaseScreen(18L);
        AnalyticsClassificationClient.ClassificationContractException failure =
            new AnalyticsClassificationClient.ClassificationContractException(
                "Platform classification contract call failed"
            );
        when(client.derive(
            eq("SCREEN"),
            eq("screen:18"),
            eq("SECRET"),
            any(),
            eq("dts-analytics:screen:18")
        )).thenThrow(failure);

        assertThatThrownBy(() -> service.prepareScreenDraft(screen)).isSameAs(failure);
        verify(screenRepository, never()).save(screen);
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

    private AnalyticsScreen databaseScreen(long id) {
        AnalyticsScreen screen = new AnalyticsScreen();
        screen.setId(id);
        screen.setClassification("SECRET");
        screen.setManualClassificationFloor("SECRET");
        screen.setComponentsJson("""
            [
              {
                "id": "project-total",
                "dataSource": {
                  "type": "sql",
                  "sqlConfig": {"databaseId": 1}
                }
              }
            ]
            """);
        screen.setPagesJson("[]");

        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(1L);
        database.setDetailsJson(
            "{\"platformDataSourceId\":\"a0000000-0000-0000-0000-000000000001\"}"
        );
        when(databaseRepository.findById(1L)).thenReturn(Optional.of(database));
        return screen;
    }

    private AnalyticsClassificationClient.ClassificationContractException pendingSourceClassification() {
        String body = """
            {
              "title": "Internal Server Error",
              "status": 500,
              "detail": "Consumer source classification is missing or pending: ASSET/data-source:a0000000-0000-0000-0000-000000000001",
              "message": "error.http.500"
            }
            """;
        HttpServerErrorException remote = HttpServerErrorException.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            HttpHeaders.EMPTY,
            body.getBytes(StandardCharsets.UTF_8),
            StandardCharsets.UTF_8
        );
        return new AnalyticsClassificationClient.ClassificationContractException(
            "Platform classification contract call failed",
            remote
        );
    }
}
