package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsBookmarkRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsTableRepository;
import com.yuzhi.dts.analytics.service.ActivityService;
import com.yuzhi.dts.analytics.service.AnalyticsAssetAccessRegistrar;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.AssetListFilterService;
import com.yuzhi.dts.analytics.service.DatasetQueryService;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.FieldValuesService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.analytics.service.RevisionService;
import com.yuzhi.dts.analytics.service.semantic.SemanticQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DashboardResourceSemanticQueryTest {

    @Test
    void saved_semantic_card_executes_through_the_semantic_query_service() throws Exception {
        AnalyticsSessionService sessions = mock(AnalyticsSessionService.class);
        AnalyticsDashboardCardRepository dashboardCards = mock(AnalyticsDashboardCardRepository.class);
        AnalyticsCardRepository cards = mock(AnalyticsCardRepository.class);
        AnalyticsConsumerClassificationService classifications = mock(AnalyticsConsumerClassificationService.class);
        QueryExecutionFacade queryExecution = mock(QueryExecutionFacade.class);
        SemanticQueryService semanticQueries = mock(SemanticQueryService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        when(sessions.resolveUser(request)).thenReturn(Optional.of(user));

        AnalyticsDashboardCard dashboardCard = new AnalyticsDashboardCard();
        dashboardCard.setId(1L);
        dashboardCard.setDashboardId(2L);
        dashboardCard.setCardId(3L);
        when(dashboardCards.findById(1L)).thenReturn(Optional.of(dashboardCard));

        AnalyticsCard card = new AnalyticsCard();
        card.setId(3L);
        card.setDatabaseId(9L);
        card.setDatasetQueryJson("""
            {
              "database": 9,
              "type": "semantic",
              "semantic_query": {
                "base": "model_spec_budget_kpi",
                "measures": ["model_spec_budget_kpi.pjm_budg_remaining"],
                "dimensions": ["model_spec_budget_kpi.snapshot_date"]
              }
            }
            """);
        when(cards.findById(3L)).thenReturn(Optional.of(card));

        DatasetQueryService.DatasetResult dataset = new DatasetQueryService.DatasetResult(
            List.of(List.of("2026-08-12", 1834.38)),
            List.of(Map.of("name", "snapshot_date"), Map.of("name", "pjm_budg_remaining")),
            List.of(Map.of("name", "snapshot_date"), Map.of("name", "pjm_budg_remaining")),
            "Asia/Shanghai"
        );
        when(semanticQueries.executeForCard(any(), any(), any()))
            .thenReturn(new SemanticQueryService.SemanticExecutionResult(
                9L,
                "SELECT snapshot_date, SUM(total_remaining) FROM biz_ads_budget_kpi_v2 GROUP BY snapshot_date",
                dataset,
                List.of("classification_filter"),
                List.of(),
                false,
                12L
            ));
        when(queryExecution.prepare(any(), any(), any(), any()))
            .thenThrow(new IllegalArgumentException("Only native and query (MBQL) queries are supported"));

        DashboardResource resource = new DashboardResource(
            sessions,
            mock(AnalyticsDashboardRepository.class),
            dashboardCards,
            mock(AnalyticsBookmarkRepository.class),
            cards,
            mock(ActivityService.class),
            mock(EntityIdGenerator.class),
            mock(PublicLinkService.class),
            mock(RevisionService.class),
            mock(AnalyticsFieldRepository.class),
            mock(AnalyticsTableRepository.class),
            mock(FieldValuesService.class),
            queryExecution,
            semanticQueries,
            mock(AssetListFilterService.class),
            classifications,
            new ObjectMapper(),
            mock(AnalyticsAssetAccessRegistrar.class)
        );

        ResponseEntity<?> response = resource.dashcardQuery(2L, 1L, 3L, new ObjectMapper().readTree("{}"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, body -> {
            assertThat(body.get("status")).isEqualTo("completed");
            assertThat(body.get("database_id")).isEqualTo(9L);
            assertThat(body.toString()).contains("2026-08-12").contains("1834.38");
        });
    }
}
