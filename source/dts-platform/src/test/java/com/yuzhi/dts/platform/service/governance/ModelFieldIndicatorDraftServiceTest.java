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
        when(models.get("default", modelId)).thenReturn(model);
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
        when(models.get("default", modelId)).thenReturn(model);
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
}
