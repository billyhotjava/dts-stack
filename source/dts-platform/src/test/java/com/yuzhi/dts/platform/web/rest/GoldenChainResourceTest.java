package com.yuzhi.dts.platform.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.domain.goldenchain.GoldenChainSourceKind;
import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainQueryService;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageStatus;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainDetailResponse;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainStageResponse;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainSummaryResponse;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = GoldenChainResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc(addFilters = false)
class GoldenChainResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoldenChainQueryService queryService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void listChainsReturnsSingleWorkbenchPayload() throws Exception {
        when(queryService.listChains())
            .thenReturn(List.of(new GoldenChainSummaryResponse(
                "jdbc-orders-daily",
                "订单 JDBC 黄金链路",
                GoldenChainSourceKind.JDBC,
                GoldenChainStage.CONSUMABLE,
                "消费资产可用",
                GoldenChainStageStatus.READY,
                "数据负责人"
            )));

        mockMvc.perform(get("/api/golden-chains"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(200))
            .andExpect(jsonPath("$.data[0].chainKey").value("jdbc-orders-daily"))
            .andExpect(jsonPath("$.data[0].currentStageLabel").value("消费资产可用"));
    }

    @Test
    void detailReturnsOrderedStagesAndBusinessNextAction() throws Exception {
        GoldenChainDetailResponse detail = new GoldenChainDetailResponse(
            "api-sprint-38-orders",
            "Sprint-38 API 入湖样例链路",
            GoldenChainSourceKind.API,
            GoldenChainStage.INGESTION_READY,
            "入湖任务就绪",
            GoldenChainStageStatus.BLOCKED,
            "接口数据负责人",
            List.of(
                new GoldenChainStageResponse(
                    GoldenChainStage.INGESTION_READY,
                    "入湖任务就绪",
                    GoldenChainStageStatus.BLOCKED,
                    "接口数据负责人",
                    null,
                    "API 入湖执行器返回分页游标异常",
                    "检查入湖任务、调度实例和目标表写入"
                )
            )
        );
        when(queryService.findDetailByChainKey("api-sprint-38-orders")).thenReturn(Optional.of(detail));

        mockMvc.perform(get("/api/golden-chains/api-sprint-38-orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.chainKey").value("api-sprint-38-orders"))
            .andExpect(jsonPath("$.data.stages[0].failureReason").value("API 入湖执行器返回分页游标异常"))
            .andExpect(jsonPath("$.data.stages[0].nextAction").value("检查入湖任务、调度实例和目标表写入"));
    }

    @Test
    void missingOrUnauthorizedChainFailsClosedAsNotFound() throws Exception {
        when(queryService.findDetailByChainKey("hidden-chain")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/golden-chains/hidden-chain")).andExpect(status().isNotFound());
    }
}
