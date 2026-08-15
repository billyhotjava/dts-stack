package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorRunRepository;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationPolicy;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IndicatorCalculationServiceTest {

    @Test
    void calculatesLatestMaterializedPeriodFromThePinnedModelField() {
        UUID indicatorId = UUID.fromString("61000000-0000-0000-0000-000000000001");
        UUID modelId = UUID.fromString("41000000-0000-0000-0000-000000000001");
        GovIndicatorDefinitionRepository indicators = mock(GovIndicatorDefinitionRepository.class);
        GovIndicatorReferenceRepository references = mock(GovIndicatorReferenceRepository.class);
        GovIndicatorRunRepository runs = mock(GovIndicatorRunRepository.class);
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        QueryGateway queries = mock(QueryGateway.class);
        GovIndicatorDefinition indicator = indicator(indicatorId, "PJM_PROJECT_TOTAL");
        indicator.setAggregationType("SUM");
        GovIndicatorReference reference = new GovIndicatorReference();
        reference.setRefType("MODEL_SPEC_FIELD");
        reference.setRefTarget(modelId + "@2#project_total_cnt");
        ModelSpecView model = mock(ModelSpecView.class);
        when(indicators.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(references.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of(reference));
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 2))).thenReturn(model);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(2);
        when(model.name()).thenReturn("biz_ads_progress_kpi_v2");
        when(model.implementationPolicy()).thenReturn(new ImplementationPolicy("biz_ads_progress_kpi_v2", null, null, List.of()));
        when(model.grain()).thenReturn(new Grain("month", List.of("plan_year", "plan_month")));
        when(model.fields()).thenReturn(List.of(
            new ModelField("project_total_cnt", "项目总数", "bigint", false, null, FieldRole.MEASURE, null, null, false, null)
        ));
        when(queries.execute(anyString())).thenReturn(Map.of(
            "rows", List.of(Map.of("metric_value", new BigDecimal("3"), "rows_processed", 1L))
        ));
        when(runs.findByIndicatorIdOrderByRunAtDesc(indicatorId)).thenReturn(List.of());
        when(runs.save(any(GovIndicatorRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IndicatorCalculationService.CalculationBatch batch = service(indicators, references, runs, models, queries)
            .calculate(List.of(indicatorId));

        assertThat(batch.successCount()).isEqualTo(1);
        assertThat(batch.items().get(0).value()).isEqualByComparingTo("3");
        assertThat(batch.items().get(0).sourceMode()).isEqualTo("MODEL_FIELD");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(queries).execute(sql.capture());
        assertThat(sql.getValue()).contains("latest_period", "project_total_cnt", "plan_year", "plan_month");
    }

    @Test
    void recordsOneFailedRunWhenThePhysicalImplementationIsMissing() {
        UUID indicatorId = UUID.fromString("61000000-0000-0000-0000-000000000002");
        GovIndicatorDefinitionRepository indicators = mock(GovIndicatorDefinitionRepository.class);
        GovIndicatorReferenceRepository references = mock(GovIndicatorReferenceRepository.class);
        GovIndicatorRunRepository runs = mock(GovIndicatorRunRepository.class);
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        QueryGateway queries = mock(QueryGateway.class);
        GovIndicatorDefinition indicator = indicator(indicatorId, "PJM_UNBOUND");
        when(indicators.findById(indicatorId)).thenReturn(Optional.of(indicator));
        when(references.findByIndicatorOrderByCreatedDateAsc(indicator)).thenReturn(List.of());
        when(runs.save(any(GovIndicatorRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IndicatorCalculationService.CalculationBatch batch = service(indicators, references, runs, models, queries)
            .calculate(List.of(indicatorId));

        assertThat(batch.failedCount()).isEqualTo(1);
        assertThat(batch.items().get(0).errorMessage()).contains("固定模型字段实现");
        ArgumentCaptor<GovIndicatorRun> run = ArgumentCaptor.forClass(GovIndicatorRun.class);
        verify(runs).save(run.capture());
        assertThat(run.getValue().getStatus()).isEqualTo("FAILED");
    }

    private static IndicatorCalculationService service(
        GovIndicatorDefinitionRepository indicators,
        GovIndicatorReferenceRepository references,
        GovIndicatorRunRepository runs,
        ModelSpecApplicationService models,
        QueryGateway queries
    ) {
        return new IndicatorCalculationService(
            indicators,
            references,
            runs,
            models,
            queries,
            new ControlledIndicatorDerivationCompiler(),
            new ObjectMapper(),
            "default"
        );
    }

    private static GovIndicatorDefinition indicator(UUID id, String code) {
        GovIndicatorDefinition indicator = new GovIndicatorDefinition();
        indicator.setId(id);
        indicator.setCode(code);
        indicator.setName(code);
        indicator.setStatus("PUBLISHED");
        indicator.setMetricType("ATOMIC");
        indicator.setMeasureField("project_total_cnt");
        return indicator;
    }
}
