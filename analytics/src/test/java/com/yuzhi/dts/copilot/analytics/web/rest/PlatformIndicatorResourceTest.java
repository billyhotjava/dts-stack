package com.yuzhi.dts.copilot.analytics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsUser;
import com.yuzhi.dts.copilot.analytics.service.AnalyticsSessionService;
import com.yuzhi.dts.copilot.analytics.service.PlatformIndicatorClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class PlatformIndicatorResourceTest {

    @Mock
    private AnalyticsSessionService sessionService;

    @Mock
    private PlatformIndicatorClient indicatorClient;

    @Test
    void returnsIndicatorCatalogForAuthenticatedUsers() {
        PlatformIndicatorResource resource = new PlatformIndicatorResource(sessionService, indicatorClient);
        when(sessionService.resolveUser(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(user()));
        when(indicatorClient.listIndicators()).thenReturn(new PlatformIndicatorClient.CatalogResponse(
                List.of(new PlatformIndicatorClient.IndicatorItem(
                        "cash-in",
                        "cash_in",
                        "回款金额",
                        "财务",
                        "按月统计已确认回款。",
                        "sum(received_amount)",
                        "已发布",
                        "v2",
                        List.of("project"),
                        "month",
                        "finance",
                        "L2")),
                null,
                false,
                null));

        ResponseEntity<?> response = resource.indicators(new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(PlatformIndicatorClient.CatalogResponse.class);
        PlatformIndicatorClient.CatalogResponse body = (PlatformIndicatorClient.CatalogResponse) response.getBody();
        assertThat(body.items()).extracting(PlatformIndicatorClient.IndicatorItem::name)
                .containsExactly("回款金额");
    }

    @Test
    void returnsDetailValueForAuthenticatedUsers() {
        PlatformIndicatorResource resource = new PlatformIndicatorResource(sessionService, indicatorClient);
        when(sessionService.resolveUser(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(user()));
        when(indicatorClient.getDetail("cash-in", 30)).thenReturn(new PlatformIndicatorClient.ValueResponse(
                "cash-in",
                "detail",
                List.of(new PlatformIndicatorClient.DatasetColumn("month", "月份", "type/Text")),
                List.of(List.of("2026-05", 100)),
                "month",
                List.of("project"),
                false,
                null));

        ResponseEntity<?> response = resource.detail("cash-in", 30, new MockHttpServletRequest());

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        PlatformIndicatorClient.ValueResponse body = (PlatformIndicatorClient.ValueResponse) response.getBody();
        assertThat(body.indicatorId()).isEqualTo("cash-in");
        assertThat(body.rows()).containsExactly(List.of("2026-05", 100));
    }

    private static AnalyticsUser user() {
        AnalyticsUser user = new AnalyticsUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("secret");
        user.setActive(true);
        user.setSuperuser(true);
        return user;
    }
}
