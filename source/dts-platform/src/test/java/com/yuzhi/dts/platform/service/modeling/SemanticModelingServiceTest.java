package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.service.etl.DbtFileService;
import com.yuzhi.dts.platform.service.etl.DbtReleaseSubmissionService;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SemanticModelingServiceTest {

    private NamedParameterJdbcTemplate jdbc;
    private SemanticModelingService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(NamedParameterJdbcTemplate.class);
        stubQuery();
        service =
            new SemanticModelingService(
                jdbc,
                new ObjectMapper(),
                mock(DbtFileService.class),
                mock(QueryGateway.class),
                mock(QueryDatasetAssetRepository.class),
                mock(QueryDatasetVersionRepository.class),
                mock(CatalogDatasetRepository.class),
                mock(CatalogDatasetLineageRepository.class),
                mock(DbtReleaseSubmissionService.class),
                new ControlledMetricDslCompiler(new ObjectMapper()),
                new EltLayerGate()
            );
    }

    @Test
    void listBusinessObjectsOmitsDomainFilterWhenDomainIdIsNull() {
        service.listBusinessObjects(null);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains("from semantic_business_object").doesNotContain("domain_id = :domainId");
        assertThat(query.params().getValues()).doesNotContainKey("domainId");
    }

    @Test
    void listSubjectDomainsUsesCatalogGovernanceDomainsAsSource() {
        service.listSubjectDomains();

        CapturedQuery query = captureQuery();
        assertThat(query.sql())
            .contains("from catalog_domain")
            .contains("governance_domain_id")
            .doesNotContain("from semantic_subject_domain");
    }

    @Test
    void semanticMenuDiagnosticsPointsSubjectDomainsToGovernanceCenter() {
        List<Map<String, Object>> menus = service.semanticMenuDiagnostics();

        assertThat(menus)
            .anySatisfy(menu -> {
                assertThat(menu).containsEntry("key", "governance-subject-domain");
                assertThat(menu).containsEntry("path", "/governance/subjects");
            });
        assertThat(menus).noneSatisfy(menu -> assertThat(menu).containsEntry("path", "/metrics/semantic/subjects"));
    }

    @Test
    void listMetricsOmitsObjectFilterWhenObjectIdIsNull() {
        service.listMetrics(null);

        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains("from semantic_metric").doesNotContain("object_id = :objectId");
        assertThat(query.params().getValues()).doesNotContainKey("objectId");
    }

    @Test
    void listDimensionsAndArtifactsKeepUuidFiltersWhenPresent() {
        UUID objectId = UUID.randomUUID();
        UUID modelId = UUID.randomUUID();

        service.listDimensions(objectId);
        CapturedQuery dimensionsQuery = captureQuery();
        assertThat(dimensionsQuery.sql()).contains("where object_id = :objectId");
        assertThat(dimensionsQuery.params().getValue("objectId")).isEqualTo(objectId);

        service.listGeneratedArtifacts(modelId);
        CapturedQuery artifactsQuery = captureQuery();
        assertThat(artifactsQuery.sql()).contains("where model_id = :modelId");
        assertThat(artifactsQuery.params().getValue("modelId")).isEqualTo(modelId);
    }

    @Test
    void metricExpressionSupportsSkillFormulaJsonForRatioAndConditionalCount() throws Exception {
        String ratio = buildMetricExpression(
            new SemanticModelingService.MetricDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "project_share_rate",
                "项目占比",
                "ratio",
                """
                {
                  "type": "ratio",
                  "numerator": {
                    "type": "aggregation",
                    "aggregation": "count_distinct",
                    "field": "project_id"
                  },
                  "denominator": {
                    "type": "aggregation",
                    "aggregation": "count_distinct",
                    "field": "dept_id"
                  }
                }
                """,
                "percent",
                "%",
                "ACTIVE"
            )
        );
        assertThat(ratio).isEqualTo("case when count(distinct dept_id) = 0 then null else count(distinct project_id) / count(distinct dept_id) end");

        String countIf = buildMetricExpression(
            new SemanticModelingService.MetricDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "active_project_cnt",
                "在研项目数",
                "count_if",
                """
                {
                  "type": "conditional_count",
                  "field": "project_id",
                  "condition": {
                    "field": "project_status",
                    "operator": "=",
                    "value": "在研"
                  },
                  "distinct": true
                }
                """,
                "integer",
                "个",
                "ACTIVE"
            )
        );
        assertThat(countIf).isEqualTo("count(distinct case when project_status = '在研' then project_id end)");
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void stubQuery() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private CapturedQuery captureQuery() {
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc).query(sqlCaptor.capture(), paramsCaptor.capture(), any(RowMapper.class));
        clearInvocations(jdbc);
        return new CapturedQuery(sqlCaptor.getValue(), paramsCaptor.getValue());
    }

    @Test
    void newModelDefaultsGovernanceModeToControlled() throws Exception {
        // F1-T01: a new model with no explicit governanceMode defaults to CONTROLLED (strangler: opt-in 受控).
        MapSqlParameterSource params = modelParams(
            new SemanticModelingService.ModelRequest(null, "DWS", "dws_demo", null, null, null, null, null, null, null)
        );
        assertThat(params.getValue("governanceMode")).isEqualTo("CONTROLLED");
    }

    @Test
    void explicitGovernanceModeIsPreserved() throws Exception {
        // Existing/legacy models stay PERMISSIVE when the request carries it explicitly (no silent flip).
        MapSqlParameterSource params = modelParams(
            new SemanticModelingService.ModelRequest(null, "DWS", "dws_demo", null, null, null, null, null, null, "PERMISSIVE")
        );
        assertThat(params.getValue("governanceMode")).isEqualTo("PERMISSIVE");
    }

    @Test
    void listModelsSelectsGovernanceModeColumn() {
        service.listModels(null);
        CapturedQuery query = captureQuery();
        assertThat(query.sql()).contains("from semantic_model").contains("governance_mode");
    }

    private String buildMetricExpression(SemanticModelingService.MetricDto metric) throws Exception {
        Method method = SemanticModelingService.class.getDeclaredMethod("buildMetricExpression", SemanticModelingService.MetricDto.class);
        method.setAccessible(true);
        return (String) method.invoke(service, metric);
    }

    @Test
    void controlledModeDelegatesToStrictCompilerAndQuotes() throws Throwable {
        // F2-T03: CONTROLLED 模型的派生指标走严格编译器（白名单 + 方言 quote）。
        String sum = buildMetricExpression(
            new SemanticModelingService.MetricDto(
                UUID.randomUUID(), UUID.randomUUID(), "amt", "金额", "sum", "{\"field\":\"order_amount\"}", null, null, "ACTIVE"
            ),
            true
        );
        assertThat(sum).isEqualTo("sum(\"order_amount\")");
    }

    @Test
    void controlledModeRejectsRawSqlButPermissiveAllowsIt() throws Throwable {
        SemanticModelingService.MetricDto rawSql = new SemanticModelingService.MetricDto(
            UUID.randomUUID(), UUID.randomUUID(), "x", "x", "sql", "{\"expression\":\"sum(amount)\"}", null, null, "ACTIVE"
        );
        // 受控模式拒绝原始 SQL；permissive（现状）不拒绝（绞杀者并存）。
        assertThatThrownBy(() -> buildMetricExpression(rawSql, true)).isInstanceOf(IllegalArgumentException.class);
        assertThat(buildMetricExpression(rawSql, false)).isNotBlank();
    }

    @Test
    void controlledLayerGateAllowsDwsAndAds() throws Throwable {
        enforceLayerGate(model("DWS", "stat_date"));
        enforceLayerGate(model("ADS", null));
    }

    @Test
    void controlledLayerGateRejectsOdsAndDwdWithoutGrain() {
        assertThatThrownBy(() -> enforceLayerGate(model("ODS", "x")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("invalid_layer");
        assertThatThrownBy(() -> enforceLayerGate(model("DWD", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("grain_mismatch");
    }

    @Test
    void controlledLayerGateAllowsDwdWithGrain() throws Throwable {
        // 标准码强制随 SP-2 接入，当前 DWD 仅校验 grain
        enforceLayerGate(model("DWD", "stat_date"));
    }

    @Test
    void executableSqlUsesMainMappingRoleAsPrimaryTable() throws Throwable {
        UUID objectId = UUID.randomUUID();
        doReturn(
            List.of(
                new SemanticModelingService.ObjectTableMappingDto(
                    UUID.randomUUID(),
                    objectId,
                    "dim_customer",
                    "dimension",
                    "dwd_order_detail.customer_id = dim_customer.customer_id",
                    0
                ),
                new SemanticModelingService.ObjectTableMappingDto(
                    UUID.randomUUID(),
                    objectId,
                    "dwd_order_detail",
                    "main",
                    null,
                    1
                )
            )
        )
            .when(jdbc)
            .query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));

        String sql = buildExecutableModelSql(
            model("DWS", "stat_date"),
            new SemanticModelingService.BusinessObjectDto(
                objectId,
                UUID.randomUUID(),
                "order",
                "订单",
                null,
                "order_id",
                "legacy_order_main",
                "ACTIVE",
                null
            )
        );

        assertThat(sql)
            .contains("from dwd_order_detail")
            .contains("left join dim_customer on dwd_order_detail.customer_id = dim_customer.customer_id")
            .doesNotContain("from legacy_order_main");
    }

    private SemanticModelingService.ModelDto model(String type, String grain) {
        return new SemanticModelingService.ModelDto(
            UUID.randomUUID(), UUID.randomUUID(), type, "m_" + type, null, null, grain, null, null,
            "DRAFT", "DRAFT", null, null, null, null, null, "CONTROLLED"
        );
    }

    private void enforceLayerGate(SemanticModelingService.ModelDto model) throws Throwable {
        Method method = SemanticModelingService.class.getDeclaredMethod("enforceLayerGate", SemanticModelingService.ModelDto.class);
        method.setAccessible(true);
        try {
            method.invoke(service, model);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private MapSqlParameterSource modelParams(SemanticModelingService.ModelRequest request) throws Exception {
        Method method = SemanticModelingService.class.getDeclaredMethod("modelParams", UUID.class, SemanticModelingService.ModelRequest.class);
        method.setAccessible(true);
        return (MapSqlParameterSource) method.invoke(service, UUID.randomUUID(), request);
    }

    private String buildMetricExpression(SemanticModelingService.MetricDto metric, boolean controlled) throws Throwable {
        Method method = SemanticModelingService.class.getDeclaredMethod(
            "buildMetricExpression",
            SemanticModelingService.MetricDto.class,
            boolean.class
        );
        method.setAccessible(true);
        try {
            return (String) method.invoke(service, metric, controlled);
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private String buildExecutableModelSql(
        SemanticModelingService.ModelDto model,
        SemanticModelingService.BusinessObjectDto object
    ) throws Throwable {
        Method method = SemanticModelingService.class.getDeclaredMethod(
            "buildExecutableModelSql",
            SemanticModelingService.ModelDto.class,
            SemanticModelingService.BusinessObjectDto.class,
            List.class,
            List.class
        );
        method.setAccessible(true);
        try {
            return (String) method.invoke(service, model, object, List.of(), List.of());
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private record CapturedQuery(String sql, MapSqlParameterSource params) {}
}
