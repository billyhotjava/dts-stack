package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.governance.IndicatorReferenceService.ReferenceUpsertRequest;
import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.MetricType;
import com.yuzhi.dts.platform.service.governance.IndicatorBusinessContextContract.SourceType;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CompatibilityMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.StandardBinding;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelFieldIndicatorDraftServiceTest {

    @Test
    void preservesDerivedSemanticsWhilePinningItsImplementationField() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000071");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000071");
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorReferenceService references = mock(IndicatorReferenceService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ModelField field = new ModelField("completion_rate", "完成率", "numeric", false, null, FieldRole.MEASURE, null, null, false, null);
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 2))).thenReturn(model);
        when(model.id()).thenReturn(modelId);
        when(model.name()).thenReturn("biz_ads_progress_derived_v2");
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(model.modelType()).thenReturn(ModelType.APPLICATION);
        when(model.layer()).thenReturn(Layer.ADS);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(2);
        when(model.fields()).thenReturn(List.of(field));
        when(model.standardBindings()).thenReturn(List.of());
        IndicatorDto created = new IndicatorDto();
        created.setId(indicatorId);
        when(indicators.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("institute"))).thenReturn(created);
        when(references.create(org.mockito.ArgumentMatchers.eq(indicatorId), org.mockito.ArgumentMatchers.eq("institute"), org.mockito.ArgumentMatchers.any())).thenReturn(Map.of());
        IndicatorUpsertRequest indicator = new IndicatorUpsertRequest();
        indicator.setCode("PJM_COMPLETION_RATE");
        indicator.setMetricType(MetricType.DERIVED.name());
        indicator.setIsDerived(true);
        indicator.setExpressionSql("{{metric:PJM_COMPLETED}} / nullif({{metric:PJM_TOTAL}}, 0)");
        indicator.setSourceRefs(List.of(
            new IndicatorBusinessContextContract.MetricSourceRef(SourceType.INDICATOR_VERSION, UUID.randomUUID().toString(), "v1"),
            new IndicatorBusinessContextContract.MetricSourceRef(SourceType.INDICATOR_VERSION, UUID.randomUUID().toString(), "v1")
        ));

        new ModelFieldIndicatorDraftService(models, indicators, references, new ObjectMapper()).create(
            "default",
            "institute",
            new ModelFieldIndicatorDraftService.CreateDraftRequest(indicator, modelId, 2, "completion_rate", null, null)
        );

        ArgumentCaptor<IndicatorUpsertRequest> definition = ArgumentCaptor.forClass(IndicatorUpsertRequest.class);
        verify(indicators).create(definition.capture(), org.mockito.ArgumentMatchers.eq("institute"));
        assertThat(definition.getValue().getMetricType()).isEqualTo(MetricType.DERIVED.name());
        assertThat(definition.getValue().getIsDerived()).isTrue();
        assertThat(definition.getValue().getSourceRefs()).allMatch(ref -> ref.sourceType() == SourceType.INDICATOR_VERSION);
        assertThat(definition.getValue().getTargetModelName()).isEqualTo("biz_ads_progress_derived_v2");
        assertThat(definition.getValue().getMeasureField()).isEqualTo("completion_rate");
    }

    @Test
    void createsOneDraftWithAnExactPublishedModelFieldReference() throws Exception {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000067");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000067");
        UUID unitId = UUID.fromString("70000000-0000-0000-0000-000000000067");
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorReferenceService references = mock(IndicatorReferenceService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ModelField field = new ModelField(
            "amount",
            "成交金额",
            "decimal(18,2)",
            false,
            "orders.amount",
            FieldRole.MEASURE,
            null,
            null,
            false,
            null
        );
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 3))).thenReturn(model);
        when(model.id()).thenReturn(modelId);
        when(model.name()).thenReturn("fct_orders");
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.layer()).thenReturn(Layer.DWD);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(3);
        when(model.fields()).thenReturn(List.of(field));
        when(model.standardBindings()).thenReturn(List.of(new StandardBinding("amount", null, null, null, null, unitId, 2, null)));
        IndicatorDto created = new IndicatorDto();
        created.setId(indicatorId);
        created.setName("成交金额");
        when(indicators.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("finance")))
            .thenReturn(created);
        when(references.create(
            org.mockito.ArgumentMatchers.eq(indicatorId),
            org.mockito.ArgumentMatchers.eq("finance"),
            org.mockito.ArgumentMatchers.any()
        )).thenReturn(Map.of());
        IndicatorUpsertRequest indicator = new IndicatorUpsertRequest();
        indicator.setCode("GMV");
        indicator.setName("成交金额");
        indicator.setCategory("财务业务");

        IndicatorDto result = new ModelFieldIndicatorDraftService(
            models,
            indicators,
            references,
            new ObjectMapper()
        ).create(
            "default",
            "finance",
            new ModelFieldIndicatorDraftService.CreateDraftRequest(indicator, modelId, 3, "amount", unitId, 2)
        );

        assertThat(result).isSameAs(created);
        ArgumentCaptor<IndicatorUpsertRequest> definition = ArgumentCaptor.forClass(IndicatorUpsertRequest.class);
        verify(indicators).create(definition.capture(), org.mockito.ArgumentMatchers.eq("finance"));
        assertThat(definition.getValue().getCategory()).isEqualTo("财务业务");
        assertThat(definition.getValue().getMetricType()).isEqualTo(MetricType.ATOMIC.name());
        assertThat(definition.getValue().getIsDerived()).isFalse();
        assertThat(definition.getValue().getMeasureField()).isEqualTo("amount");
        assertThat(definition.getValue().getSourceTable()).isEqualTo("fct_orders");
        assertThat(definition.getValue().getSourceLayer()).isEqualTo("DWD");
        assertThat(definition.getValue().getSourceRefs())
            .containsExactly(new IndicatorBusinessContextContract.MetricSourceRef(SourceType.SEMANTIC_MODEL_REVISION, modelId.toString(), "r3"));
        ArgumentCaptor<ReferenceUpsertRequest> reference = ArgumentCaptor.forClass(ReferenceUpsertRequest.class);
        verify(references).create(
            org.mockito.ArgumentMatchers.eq(indicatorId),
            org.mockito.ArgumentMatchers.eq("finance"),
            reference.capture()
        );
        assertThat(reference.getValue().refType()).isEqualTo("MODEL_SPEC_FIELD");
        assertThat(reference.getValue().refTarget()).isEqualTo(modelId + "@3#amount");
        assertThat(reference.getValue().refName()).isEqualTo("fct_orders.amount");
        JsonNode notes = new ObjectMapper().readTree(reference.getValue().notes());
        assertThat(notes.path("measurementUnitId").asText()).isEqualTo(unitId.toString());
        assertThat(notes.path("measurementUnitVersion").asInt()).isEqualTo(2);
    }

    @Test
    void rejectsAStaleClientUnitVersionBeforeCreatingAnIndicator() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000068");
        UUID unitId = UUID.fromString("70000000-0000-0000-0000-000000000068");
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorReferenceService references = mock(IndicatorReferenceService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 4))).thenReturn(model);
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.layer()).thenReturn(Layer.DWD);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(4);
        when(model.fields()).thenReturn(
            List.of(new ModelField("amount", "decimal(18,2)", false, "orders.amount", FieldRole.MEASURE, null))
        );
        when(model.standardBindings()).thenReturn(
            List.of(new StandardBinding("amount", null, null, null, null, unitId, 3, null))
        );

        ModelFieldIndicatorDraftService service = new ModelFieldIndicatorDraftService(
            models,
            indicators,
            references,
            new ObjectMapper()
        );
        IndicatorUpsertRequest indicator = new IndicatorUpsertRequest();
        indicator.setCode("GMV");
        indicator.setName("成交金额");

        assertThatThrownBy(() ->
            service.create(
                "default",
                "finance",
                new ModelFieldIndicatorDraftService.CreateDraftRequest(indicator, modelId, 4, "amount", unitId, 2)
            )
        )
            .isInstanceOf(IndicatorConflictException.class)
            .hasMessageContaining("计量单位版本已变化");
        verifyNoInteractions(indicators, references);
    }

    @Test
    void rebindsAnExistingDraftAndReplacesTheManagedModelFieldReference() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000069");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000069");
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorReferenceService references = mock(IndicatorReferenceService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        ModelField field = new ModelField("amount", "decimal(18,2)", false, "orders.amount", FieldRole.MEASURE, null);
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 5))).thenReturn(model);
        when(model.id()).thenReturn(modelId);
        when(model.name()).thenReturn("fct_orders");
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.layer()).thenReturn(Layer.DWD);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(5);
        when(model.fields()).thenReturn(List.of(field));
        when(model.standardBindings()).thenReturn(List.of());
        IndicatorDto current = new IndicatorDto();
        current.setId(indicatorId);
        current.setStatus("DRAFT");
        IndicatorDto saved = new IndicatorDto();
        saved.setId(indicatorId);
        when(indicators.requireMutationAccess(indicatorId, "finance")).thenReturn(current);
        when(indicators.update(org.mockito.ArgumentMatchers.eq(indicatorId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("finance")))
            .thenReturn(saved);
        IndicatorUpsertRequest indicator = new IndicatorUpsertRequest();
        indicator.setCode("GMV");
        indicator.setName("成交金额");

        IndicatorDto result = new ModelFieldIndicatorDraftService(models, indicators, references, new ObjectMapper()).rebind(
            "default",
            "finance",
            indicatorId,
            new ModelFieldIndicatorDraftService.CreateDraftRequest(indicator, modelId, 5, "amount", null, null)
        );

        assertThat(result).isSameAs(saved);
        verify(indicators).update(org.mockito.ArgumentMatchers.eq(indicatorId), org.mockito.ArgumentMatchers.same(indicator), org.mockito.ArgumentMatchers.eq("finance"));
        verify(references).replaceModelFieldReference(
            org.mockito.ArgumentMatchers.eq(indicatorId),
            org.mockito.ArgumentMatchers.eq("finance"),
            org.mockito.ArgumentMatchers.argThat(reference ->
                "MODEL_SPEC_FIELD".equals(reference.refType()) && (modelId + "@5#amount").equals(reference.refTarget())
            )
        );
    }

    @Test
    void stagesANewRevisionBeforeRebindingAPublishedIndicator() {
        UUID modelId = UUID.fromString("40000000-0000-0000-0000-000000000070");
        UUID indicatorId = UUID.fromString("60000000-0000-0000-0000-000000000070");
        ModelSpecApplicationService models = mock(ModelSpecApplicationService.class);
        IndicatorService indicators = mock(IndicatorService.class);
        IndicatorReferenceService references = mock(IndicatorReferenceService.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(models.revision("default", new ModelSpecContract.ModelRevisionRef(modelId, 6))).thenReturn(model);
        when(model.id()).thenReturn(modelId);
        when(model.name()).thenReturn("fct_orders");
        when(model.contractVersion()).thenReturn(ModelSpecContract.CONTRACT_VERSION);
        when(model.compatibilityMode()).thenReturn(CompatibilityMode.CANONICAL);
        when(model.modelType()).thenReturn(ModelType.FACT);
        when(model.layer()).thenReturn(Layer.DWD);
        when(model.status()).thenReturn(ModelStatus.PUBLISHED);
        when(model.revision()).thenReturn(6);
        when(model.fields()).thenReturn(
            List.of(new ModelField("amount", "decimal(18,2)", false, "orders.amount", FieldRole.MEASURE, null))
        );
        when(model.standardBindings()).thenReturn(List.of());
        IndicatorDto current = new IndicatorDto();
        current.setId(indicatorId);
        current.setStatus("PUBLISHED");
        IndicatorDto staged = new IndicatorDto();
        staged.setId(indicatorId);
        staged.setStatus("DRAFT");
        when(indicators.requireMutationAccess(indicatorId, "finance")).thenReturn(current);
        when(indicators.stageRevision(org.mockito.ArgumentMatchers.eq(indicatorId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("finance")))
            .thenReturn(staged);

        IndicatorDto result = new ModelFieldIndicatorDraftService(models, indicators, references, new ObjectMapper()).rebind(
            "default",
            "finance",
            indicatorId,
            new ModelFieldIndicatorDraftService.CreateDraftRequest(new IndicatorUpsertRequest(), modelId, 6, "amount", null, null)
        );

        assertThat(result).isSameAs(staged);
        verify(indicators).stageRevision(org.mockito.ArgumentMatchers.eq(indicatorId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("finance"));
    }
}
