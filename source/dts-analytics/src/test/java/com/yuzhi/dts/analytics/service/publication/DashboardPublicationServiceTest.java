package com.yuzhi.dts.analytics.service.publication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsCard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboardCard;
import com.yuzhi.dts.analytics.domain.AnalyticsRevision;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsRevisionRepository;
import com.yuzhi.dts.analytics.service.EntityIdGenerator;
import com.yuzhi.dts.analytics.service.publication.AnalysisPublicationService.PublicationCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardPublicationServiceTest {

    private AnalyticsDashboardRepository dashboards;
    private AnalyticsDashboardCardRepository components;
    private AnalyticsCardRepository cards;
    private AnalyticsRevisionRepository revisions;
    private EntityIdGenerator entityIdGenerator;
    private PublicationEntityLock entityLock;
    private ReportRegistrationOutboxService outbox;
    private DashboardPublicationService service;
    private AnalyticsDashboard dashboard;
    private AnalyticsUser actor;

    @BeforeEach
    void setUp() {
        dashboards = mock(AnalyticsDashboardRepository.class);
        components = mock(AnalyticsDashboardCardRepository.class);
        cards = mock(AnalyticsCardRepository.class);
        revisions = mock(AnalyticsRevisionRepository.class);
        entityIdGenerator = mock(EntityIdGenerator.class);
        entityLock = mock(PublicationEntityLock.class);
        outbox = mock(ReportRegistrationOutboxService.class);
        service = new DashboardPublicationService(
            dashboards, components, cards, revisions, entityIdGenerator, entityLock, outbox, new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-08-17T06:00:00Z"), ZoneOffset.UTC)
        );
        dashboard = new AnalyticsDashboard();
        dashboard.setId(22L);
        dashboard.setEntityId("dashboard-22");
        dashboard.setName("项目驾驶舱");
        dashboard.setCreatorId(7L);
        dashboard.setLifecycleStatus("DRAFT");
        dashboard.setRegistrationStatus("NOT_REGISTERED");
        dashboard.setParametersJson("[]");
        actor = new AnalyticsUser();
        actor.setId(7L);
        actor.setActive(true);
        when(dashboards.findById(22L)).thenReturn(Optional.of(dashboard));
        when(entityLock.dashboard(22L)).thenReturn(dashboard);
        when(dashboards.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(entityIdGenerator.newEntityId()).thenReturn("dashboard-draft-23");
    }

    @Test
    void validationEnforcesTheFiftyComponentFitnessFunction() {
        List<AnalyticsDashboardCard> tooMany = new ArrayList<>();
        for (int index = 0; index < 51; index++) {
            AnalyticsDashboardCard component = new AnalyticsDashboardCard();
            component.setId((long) index + 1);
            component.setDashboardId(22L);
            component.setCardId((long) index + 100);
            tooMany.add(component);
        }
        when(components.findAllByDashboardIdOrderByIdAsc(22L)).thenReturn(tooMany);

        DashboardPublicationService.ValidationResult result = service.validate(
            22L, actor, new PublicationCommand(List.of("D1"), List.of(), "DATA_INTERNAL", null)
        );

        assertThat(result.blockers()).extracting(AnalysisPublicationService.PublicationIssue::code)
            .contains("DASHBOARD_COMPONENT_LIMIT_EXCEEDED");
    }

    @Test
    void publishPinsTheAnalysisRevisionAndQueuesRegistration() {
        AnalyticsDashboardCard component = new AnalyticsDashboardCard();
        component.setId(1L);
        component.setDashboardId(22L);
        component.setCardId(11L);
        component.setParameterMappingsJson("[]");
        when(components.findAllByDashboardIdOrderByIdAsc(22L)).thenReturn(List.of(component));

        AnalyticsCard analysis = new AnalyticsCard();
        analysis.setId(11L);
        analysis.setCardType("analysis");
        analysis.setLifecycleStatus("PUBLISHED");
        analysis.setPublishedRevisionId(91L);
        when(cards.findAllById(any())).thenReturn(List.of(analysis));

        AnalyticsRevision analysisRevision = new AnalyticsRevision();
        analysisRevision.setId(91L);
        analysisRevision.setVersionNo(1);
        analysisRevision.setStatus("PUBLISHED");
        analysisRevision.setContractChecksum("analysis-checksum");
        analysisRevision.setDependencySnapshotJson("{\"datasetId\":\"a98ff6ee-58df-4ef1-a6e5-c9344d87ae09\",\"datasetVersion\":1,\"classification\":\"DATA_INTERNAL\"}");
        when(revisions.findAllById(any())).thenReturn(List.of(analysisRevision));
        when(revisions.findCurrentPublished("dashboard", 22L)).thenReturn(Optional.empty());
        when(revisions.findMaxVersionNo("dashboard", 22L)).thenReturn(0);
        when(revisions.save(any())).thenAnswer(invocation -> {
            AnalyticsRevision revision = invocation.getArgument(0);
            revision.setId(101L);
            return revision;
        });

        DashboardPublicationService.PublicationResult result = service.publish(
            22L, actor, new PublicationCommand(List.of("D1"), List.of("ROLE_ANALYST"), "DATA_INTERNAL", null)
        );

        assertThat(result.registrationStatus()).isEqualTo("PENDING_REGISTRATION");
        assertThat(result.dependencySnapshot().toString()).contains("analysisRevisionId=91");
        assertThat(dashboard.getPublishedRevisionId()).isEqualTo(101L);
        verify(outbox).enqueueDashboard(any(Long.class), any(Long.class), any(String.class), any(ReportRegistrationCommand.class));
    }

    @Test
    void createsAnEditableDraftFromTheSelectedPublishedVersion() {
        AnalyticsRevision published = new AnalyticsRevision();
        published.setId(101L);
        published.setModel("dashboard");
        published.setModelId(22L);
        published.setVersionNo(3);
        published.setStatus("PUBLISHED");
        published.setObjectJson("""
            {"name":"项目驾驶舱","description":"已发布版本","collectionId":9,"parameters":[],
             "components":[{"analysisId":11,"row":1,"col":2,"sizeX":6,"sizeY":4,
             "parameterMappings":[],"visualizationSettings":{}}]}
            """);
        when(revisions.findById(101L)).thenReturn(Optional.of(published));
        when(dashboards.save(any())).thenAnswer(invocation -> {
            AnalyticsDashboard value = invocation.getArgument(0);
            value.setId(23L);
            return value;
        });
        when(components.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        DashboardPublicationService.DraftResult result = service.createDraftFromVersion(22L, 101L, actor);

        assertThat(result.dashboard().getId()).isEqualTo(23L);
        assertThat(result.dashboard().getLifecycleStatus()).isEqualTo("DRAFT");
        assertThat(result.dashboard().getName()).isEqualTo("项目驾驶舱（草稿）");
        assertThat(result.dashcards()).extracting(AnalyticsDashboardCard::getCardId).containsExactly(11L);
        verify(components).saveAll(any());
    }
}
