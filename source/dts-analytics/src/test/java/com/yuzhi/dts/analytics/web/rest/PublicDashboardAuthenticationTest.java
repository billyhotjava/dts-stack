package com.yuzhi.dts.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDashboard;
import com.yuzhi.dts.analytics.domain.AnalyticsPublicLink;
import com.yuzhi.dts.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.analytics.repository.AnalyticsCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsDashboardCardRepository;
import com.yuzhi.dts.analytics.repository.AnalyticsPublicLinkRepository;
import com.yuzhi.dts.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.analytics.service.PublicLinkService;
import com.yuzhi.dts.analytics.service.AnalyticsConsumerClassificationService;
import com.yuzhi.dts.analytics.service.ScreenAuditService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;

class PublicDashboardAuthenticationTest {
    private final AnalyticsSessionService sessions = mock(AnalyticsSessionService.class);
    private final AnalyticsPublicLinkRepository links = mock(AnalyticsPublicLinkRepository.class);
    private final AnalyticsDashboardRepository dashboards = mock(AnalyticsDashboardRepository.class);
    private final AnalyticsDashboardCardRepository dashcards = mock(AnalyticsDashboardCardRepository.class);
    private final PublicLinkService permission = new PublicLinkService(links,
            mock(AnalyticsConsumerClassificationService.class), mock(ScreenAuditService.class));
    private final PublicResource resource = new PublicResource(sessions, permission,
            mock(AnalyticsCardRepository.class), dashboards, dashcards, null, null, null, null, null,
            null, null, null, new ObjectMapper(), null);
    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    void setUp() {
        AnalyticsPublicLink link = new AnalyticsPublicLink();
        link.setModel(PublicLinkService.MODEL_DASHBOARD);
        link.setModelId(7L);
        link.setPublicUuid("share-id");
        link.setDept("1152");
        link.setClassification("SECRET");
        when(links.findByPublicUuid("share-id")).thenReturn(Optional.of(link));
        request.addHeader("X-DTS-Dept-Code", "1152");
        request.addHeader("X-DTS-Personnel-Level", "GENERAL");
    }

    private ResponseEntity<?> call(int endpoint) {
        return switch (endpoint) {
            case 0 -> resource.dashboard("share-id", request);
            case 1 -> resource.dashboardDashcardQuery("share-id", 1L, 2L, null, request);
            default -> resource.pivotDashboardDashcardQuery("share-id", 1L, 2L, null, request);
        };
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void anonymousRequestsRequireLoginBeforeReadingLinkOrData(int endpoint) {
        when(sessions.resolveUser(request)).thenReturn(Optional.empty());
        assertThat(call(endpoint).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(links, dashboards, dashcards);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void authenticatedWrongDepartmentIsStillForbidden(int endpoint) {
        when(sessions.resolveUser(request)).thenReturn(Optional.of(new AnalyticsUser()));
        request.removeHeader("X-DTS-Dept-Code");
        request.addHeader("X-DTS-Dept-Code", "other");
        assertThat(call(endpoint).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(dashboards, dashcards);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void authenticatedInsufficientClearanceIsStillForbidden(int endpoint) {
        when(sessions.resolveUser(request)).thenReturn(Optional.of(new AnalyticsUser()));
        request.addHeader("X-DTS-Classification", "PUBLIC");
        assertThat(call(endpoint).getStatusCode().value()).isEqualTo(403);
        verifyNoInteractions(dashboards, dashcards);
    }

    @org.junit.jupiter.api.Test
    void authenticatedMatchingDepartmentAndClearanceCanReadDashboard() {
        when(sessions.resolveUser(request)).thenReturn(Optional.of(new AnalyticsUser()));
        AnalyticsDashboard dashboard = new AnalyticsDashboard();
        dashboard.setId(7L);
        when(dashboards.findById(7L)).thenReturn(Optional.of(dashboard));
        when(dashcards.findAllByDashboardIdOrderByIdAsc(7L)).thenReturn(List.of());
        assertThat(resource.dashboard("share-id", request).getStatusCode().value()).isEqualTo(200);
    }
}
