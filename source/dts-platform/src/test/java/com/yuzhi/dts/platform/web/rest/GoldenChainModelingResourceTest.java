package com.yuzhi.dts.platform.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yuzhi.dts.platform.security.session.PortalSessionInactivityFilter;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStage;
import com.yuzhi.dts.platform.service.goldenchain.GoldenChainStageSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtAssetSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationInventoryService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationItem;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationReport;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationRisk;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationStatus;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelLayer;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseDecision;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseGateService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsColumnSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceCandidate;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceContractService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceRequest;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainReleaseEnvironment;
import com.yuzhi.dts.platform.web.filter.AuditLoggingFilter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = GoldenChainModelingResource.class,
    excludeAutoConfiguration = OAuth2ClientAutoConfiguration.class,
    properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration"
    }
)
@AutoConfigureMockMvc(addFilters = false)
class GoldenChainModelingResourceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GoldenChainOdsDbtSourceContractService odsDbtSourceContractService;

    @MockBean
    private GoldenChainModelReleaseGateService modelReleaseGateService;

    @MockBean
    private GoldenChainDbtMigrationInventoryService dbtMigrationInventoryService;

    @MockBean
    private PortalSessionInactivityFilter portalSessionInactivityFilter;

    @MockBean
    private AuditLoggingFilter auditLoggingFilter;

    @Test
    void buildOdsDbtSourceCandidateReturnsBusinessReadyYamlAndStage() throws Exception {
        when(odsDbtSourceContractService.buildCandidate(any(GoldenChainOdsDbtSourceRequest.class)))
            .thenReturn(new GoldenChainOdsDbtSourceCandidate(
                true,
                "erp",
                "ods",
                "ods_orders",
                "sales-ops",
                "daily",
                List.of(new GoldenChainOdsColumnSnapshot("order_id", "bigint", false, "订单ID")),
                "version: 2\nsources:\n  - name: erp\n",
                List.of(),
                GoldenChainStageSnapshot.ready(GoldenChainStage.MODEL_READY, "sales-ops", "dbt-source://erp/ods_orders")
            ));

        mockMvc
            .perform(post("/api/golden-chains/modeling/ods-dbt-source-candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceName": "erp",
                      "schemaName": "ods",
                      "tableName": "ods_orders",
                      "owner": "sales-ops",
                      "refreshCadence": "daily",
                      "columns": [{"name": "order_id", "dataType": "bigint", "nullable": false, "description": "订单ID"}]
                    }
                    """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishable").value(true))
            .andExpect(jsonPath("$.data.sourceYaml").value(org.hamcrest.Matchers.containsString("version: 2")))
            .andExpect(jsonPath("$.data.stageSnapshot.stage").value("MODEL_READY"))
            .andExpect(jsonPath("$.data.stageSnapshot.evidenceRef").value("dbt-source://erp/ods_orders"));
    }

    @Test
    void evaluateModelReleaseGateReturnsPublishDecisionAndWarnings() throws Exception {
        when(modelReleaseGateService.evaluate(any()))
            .thenReturn(new GoldenChainModelReleaseDecision(
                true,
                "ads_sales_summary",
                GoldenChainModelLayer.ADS,
                GoldenChainReleaseEnvironment.DEV,
                List.of(),
                List.of("dbt test 未通过或缺少证据"),
                GoldenChainStageSnapshot.ready(GoldenChainStage.RELEASE_READY, "sales-ops", "model-release://dev/ads_sales_summary")
            ));

        mockMvc
            .perform(post("/api/golden-chains/modeling/model-release-decisions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "modelName": "ads_sales_summary",
                      "layer": "ADS",
                      "owner": "sales-ops",
                      "environment": "DEV",
                      "dbtEvidence": {
                        "compileEvidenceRef": "dbt://compile",
                        "compilePassed": true,
                        "testEvidenceRef": "dbt://test",
                        "testPassed": false,
                        "buildEvidenceRef": "dbt://build",
                        "buildPassed": true
                      },
                      "governanceSnapshot": {
                        "evidenceRef": "governance://snapshot/1",
                        "schemaContractPassed": true,
                        "qualityPassed": true,
                        "lineageReady": true,
                        "classificationReady": true
                      },
                      "semanticContract": {
                        "primaryKey": null,
                        "standardCodesMapped": true,
                        "grain": "order_day",
                        "grainConsistent": true,
                        "permissionConsumable": true
                      }
                    }
                    """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishable").value(true))
            .andExpect(jsonPath("$.data.environment").value("DEV"))
            .andExpect(jsonPath("$.data.warnings[0]").value(org.hamcrest.Matchers.containsString("dbt test")))
            .andExpect(jsonPath("$.data.stageSnapshot.stage").value("RELEASE_READY"));
    }

    @Test
    void buildDbtMigrationInventoryReturnsRiskSummary() throws Exception {
        when(dbtMigrationInventoryService.inventory(any()))
            .thenReturn(new GoldenChainDbtMigrationReport(
                1,
                1,
                0,
                0,
                List.of(new GoldenChainDbtMigrationItem(
                    "pm",
                    "biz_dwd_project_node_enriched",
                    List.of(GoldenChainDbtMigrationStatus.NEED_SOURCE, GoldenChainDbtMigrationStatus.NEED_CONFIRMATION),
                    GoldenChainDbtMigrationRisk.HIGH,
                    "补齐 dbt source；人工确认迁移优先级"
                ))
            ));

        mockMvc
            .perform(post("/api/golden-chains/modeling/dbt-migration-inventory")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [
                      {
                        "packageName": "pm",
                        "modelName": "biz_dwd_project_node_enriched",
                        "sourceRegistered": false,
                        "catalogAssetRegistered": true,
                        "lineageReady": true,
                        "runtimeGraphReady": true,
                        "goldenChainManaged": false
                      }
                    ]
                    """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.totalAssets").value(1))
            .andExpect(jsonPath("$.data.highRiskAssets").value(1))
            .andExpect(jsonPath("$.data.items[0].riskLevel").value("HIGH"))
            .andExpect(jsonPath("$.data.items[0].nextAction").value(org.hamcrest.Matchers.containsString("dbt source")));
    }
}
