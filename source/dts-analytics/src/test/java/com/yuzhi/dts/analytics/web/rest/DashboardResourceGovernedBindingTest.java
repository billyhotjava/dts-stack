package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
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
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.FieldValuesService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.QueryExecutionFacade;
import com.yuzhi.dts.analytics.service.RevisionService;
import com.yuzhi.dts.analytics.service.analysis.AnalysisQueryGateway;
import com.yuzhi.dts.analytics.service.publication.DashboardPublicationService;
import com.yuzhi.dts.analytics.service.semantic.SemanticQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DashboardResourceGovernedBindingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void save_rejects_a_new_binding_to_a_legacy_question_before_writing() throws Exception {
        Fixture fixture = fixture();
        AnalyticsCard legacy = card(2L, "question", "DRAFT", null);
        when(fixture.cards.findAllById(any())).thenReturn(List.of(legacy));

        ResponseEntity<?> response = fixture.resource.save(body(2L, null, 2L), fixture.request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, value ->
            assertThat(value.get("code")).isEqualTo("DASHBOARD_ANALYSIS_REQUIRED")
        );
        verify(fixture.dashboardCards, never()).save(any());
    }

    @Test
    void save_accepts_a_new_binding_to_a_published_governed_analysis() throws Exception {
        Fixture fixture = fixture();
        when(fixture.cards.findAllById(any())).thenReturn(List.of(card(3L, "analysis", "PUBLISHED", 9L)));

        ResponseEntity<?> response = fixture.resource.save(body(2L, null, 3L), fixture.request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        verify(fixture.cards).findAllById(any());
        verify(fixture.dashboardCards).save(any(AnalyticsDashboardCard.class));
    }

    @Test
    void save_keeps_an_unchanged_legacy_binding_available_for_explicit_repair() throws Exception {
        Fixture fixture = fixture();
        AnalyticsDashboardCard existing = new AnalyticsDashboardCard();
        existing.setId(10L);
        existing.setDashboardId(2L);
        existing.setCardId(2L);
        when(fixture.dashboardCards.findAllByDashboardIdOrderByIdAsc(2L)).thenReturn(List.of(existing));
        when(fixture.dashboardCards.findById(10L)).thenReturn(Optional.of(existing));

        ResponseEntity<?> response = fixture.resource.save(body(2L, 10L, 2L), fixture.request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void dashboard_detail_exposes_governance_metadata_for_component_repair() {
        Fixture fixture = fixture();
        AnalyticsDashboardCard binding = new AnalyticsDashboardCard();
        binding.setId(10L);
        binding.setDashboardId(2L);
        binding.setCardId(3L);
        when(fixture.dashboardCards.findAllByDashboardIdOrderByIdAsc(2L)).thenReturn(List.of(binding));
        when(fixture.cards.findById(3L)).thenReturn(Optional.of(card(3L, "analysis", "PUBLISHED", 9L)));

        ResponseEntity<?> response = fixture.resource.get(2L, fixture.request);

        assertThat(response.getBody()).isInstanceOfSatisfying(Map.class, body -> {
            List<?> ordered = (List<?>) body.get("ordered_cards");
            assertThat(ordered).hasSize(1);
            Map<?, ?> component = (Map<?, ?>) ordered.getFirst();
            Map<?, ?> nestedCard = (Map<?, ?>) component.get("card");
            assertThat(nestedCard.get("type")).isEqualTo("analysis");
            assertThat(nestedCard.get("lifecycle_status")).isEqualTo("PUBLISHED");
            assertThat(nestedCard.get("published_revision_id")).isEqualTo(9L);
        });
    }

    private Fixture fixture() {
        AnalyticsSessionService sessions = mock(AnalyticsSessionService.class);
        AnalyticsDashboardRepository dashboards = mock(AnalyticsDashboardRepository.class);
        AnalyticsDashboardCardRepository dashboardCards = mock(AnalyticsDashboardCardRepository.class);
        AnalyticsCardRepository cards = mock(AnalyticsCardRepository.class);
        AnalyticsBookmarkRepository bookmarks = mock(AnalyticsBookmarkRepository.class);
        HttpServletRequest request = mock(HttpServletRequest.class);

        AnalyticsUser user = new AnalyticsUser();
        user.setId(7L);
        when(sessions.resolveUser(request)).thenReturn(Optional.of(user));

        AnalyticsDashboard dashboard = new AnalyticsDashboard();
        dashboard.setId(2L);
        dashboard.setCreatorId(7L);
        dashboard.setName("项目经营看板");
        dashboard.setLifecycleStatus("DRAFT");
        dashboard.setRegistrationStatus("NOT_REGISTERED");
        dashboard.setParametersJson("[]");
        when(dashboards.findById(2L)).thenReturn(Optional.of(dashboard));
        when(dashboards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(dashboardCards.findAllByDashboardIdOrderByIdAsc(2L)).thenReturn(List.of());
        when(bookmarks.findByUserIdAndModelAndModelId(7L, "dashboard", 2L)).thenReturn(Optional.empty());

        DashboardResource resource = new DashboardResource(
            sessions,
            dashboards,
            dashboardCards,
            bookmarks,
            cards,
            mock(ActivityService.class),
            mock(EntityIdGenerator.class),
            mock(PublicLinkService.class),
            mock(RevisionService.class),
            mock(AnalyticsFieldRepository.class),
            mock(AnalyticsTableRepository.class),
            mock(FieldValuesService.class),
            mock(QueryExecutionFacade.class),
            mock(SemanticQueryService.class),
            mock(AssetListFilterService.class),
            mock(AnalyticsConsumerClassificationService.class),
            objectMapper,
            mock(AnalyticsAssetAccessRegistrar.class),
            mock(AnalysisQueryGateway.class),
            mock(DashboardPublicationService.class)
        );
        return new Fixture(resource, dashboards, dashboardCards, cards, request);
    }

    private JsonNode body(long dashboardId, Long dashcardId, long cardId) throws Exception {
        String id = dashcardId == null ? "" : "\"id\":" + dashcardId + ",";
        return objectMapper.readTree("""
            {
              "dashboard": {"id": %d, "name": "项目经营看板", "parameters": []},
              "dashcards": [{%s "card_id": %d, "row": 0, "col": 0, "size_x": 6, "size_y": 4}]
            }
            """.formatted(dashboardId, id, cardId));
    }

    private AnalyticsCard card(long id, String type, String lifecycle, Long revisionId) {
        AnalyticsCard card = new AnalyticsCard();
        card.setId(id);
        card.setName("分析 " + id);
        card.setCardType(type);
        card.setLifecycleStatus(lifecycle);
        card.setPublishedRevisionId(revisionId);
        return card;
    }

    private record Fixture(
        DashboardResource resource,
        AnalyticsDashboardRepository dashboards,
        AnalyticsDashboardCardRepository dashboardCards,
        AnalyticsCardRepository cards,
        HttpServletRequest request
    ) {}
}
