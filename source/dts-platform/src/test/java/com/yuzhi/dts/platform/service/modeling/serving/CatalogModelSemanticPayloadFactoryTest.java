package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary;
import com.yuzhi.dts.platform.service.catalog.CatalogClassificationBoundary.ClassificationFact;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Grain;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecReader;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticIndicatorReadAdapter.AtomicIndicator;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogModelSemanticPayloadFactoryTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Test
    void buildsStableSemanticPayloadFromServingRevisionAndExactAtomicBindings() {
        ModelSpecReader models = mock(ModelSpecReader.class);
        CatalogModelSemanticIndicatorReadAdapter indicators = mock(CatalogModelSemanticIndicatorReadAdapter.class);
        CatalogClassificationBoundary classifications = mock(CatalogClassificationBoundary.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(models.revision(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(model);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.revision()).thenReturn(4);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(model.name()).thenReturn("项目预算执行汇总");
        when(model.description()).thenReturn("项目预算执行汇总模型");
        when(model.grain()).thenReturn(new Grain("每个项目每月一行", List.of("project_id", "report_month")));
        when(model.fields()).thenReturn(List.of(
            new ModelField("project_id", "项目ID", "varchar", false, null, FieldRole.KEY, "DATA_INTERNAL", null, false, null),
            new ModelField("report_month", "统计月份", "date", false, null, FieldRole.TIME, "DATA_INTERNAL", null, false, null),
            new ModelField("budget_amount", "预算金额", "numeric", true, null, FieldRole.MEASURE, "DATA_INTERNAL", null, false, null)
        ));
        when(indicators.findPublishedAtomicIndicators(MODEL_ID, 4)).thenReturn(List.of(
            new AtomicIndicator(
                "PJM_BUDGET_AMOUNT",
                "预算金额",
                "项目预算金额",
                "SUM",
                "budget_amount",
                "report_month",
                "MONTH",
                "元",
                "预算,项目",
                "DATA_INTERNAL",
                "v1"
            )
        ));
        String assetKey = CatalogAssetKey.semanticModel(MODEL_ID.toString());
        when(classifications.resolve("ASSET", assetKey))
            .thenReturn(Optional.of(new ClassificationFact("ASSET", assetKey, "DATA_INTERNAL", "PROPAGATED")));

        CatalogModelSemanticPayloadFactory factory = new CatalogModelSemanticPayloadFactory(models, indicators, classifications, mock(com.yuzhi.dts.platform.service.governance.PublishedIndicatorVersionReader.class), mock(com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.class));
        var payload = factory.create(candidate());

        assertThat(payload.platformDataSourceId()).isEqualTo(SOURCE_ID.toString());
        assertThat(payload.modelName()).isEqualTo("model_spec_30000000000000000000000000000001");
        assertThat(payload.tableName()).isEqualTo("pjm_dws_budget_execution");
        assertThat(payload.schemaName()).isEqualTo("public");
        assertThat(payload.securityLevel()).isEqualTo("INTERNAL");
        assertThat(payload.grain()).isEqualTo("每个项目每月一行");
        assertThat(payload.dimensions()).extracting(CatalogModelSemanticContract.DimensionPayload::name)
            .containsExactly("project_id", "report_month");
        assertThat(payload.metrics()).singleElement().satisfies(metric -> {
            assertThat(metric.name()).isEqualTo("PJM_BUDGET_AMOUNT");
            assertThat(metric.field()).isEqualTo("budget_amount");
            assertThat(metric.aggregation()).isEqualTo("SUM");
        });
    }

    @Test
    void blocksSemanticPublishWhenStableClassificationIsMissing() {
        ModelSpecReader models = mock(ModelSpecReader.class);
        CatalogModelSemanticIndicatorReadAdapter indicators = mock(CatalogModelSemanticIndicatorReadAdapter.class);
        CatalogClassificationBoundary classifications = mock(CatalogClassificationBoundary.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(models.revision(org.mockito.ArgumentMatchers.eq("tenant-a"), org.mockito.ArgumentMatchers.any()))
            .thenReturn(model);
        when(model.id()).thenReturn(MODEL_ID);
        when(model.revision()).thenReturn(4);
        when(model.checksum()).thenReturn("a".repeat(64));
        when(classifications.resolve("ASSET", CatalogAssetKey.semanticModel(MODEL_ID.toString())))
            .thenReturn(Optional.empty());

        CatalogModelSemanticPayloadFactory factory = new CatalogModelSemanticPayloadFactory(models, indicators, classifications, mock(com.yuzhi.dts.platform.service.governance.PublishedIndicatorVersionReader.class), mock(com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.class));

        assertThatThrownBy(() -> factory.create(candidate()))
            .isInstanceOf(CatalogModelSemanticPayloadFactory.SemanticPayloadException.class)
            .hasMessage("CATALOG_MODEL_SEMANTIC_CLASSIFICATION_NOT_READY");
    }

    private static SyncCandidate candidate() {
        ServingRef serving = new ServingRef(
            MODEL_ID,
            4,
            "a".repeat(64),
            2,
            "b".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            3,
            1,
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            1,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            "c".repeat(64),
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            SOURCE_ID,
            "postgres",
            "biadmin",
            "public",
            "pjm_dws_budget_execution",
            Instant.parse("2026-08-17T00:00:00Z")
        );
        ModelServingProjection projection = new ModelServingProjection(
            "tenant-a",
            MODEL_ID,
            CatalogAssetType.SEMANTIC_MODEL,
            CatalogAssetKey.semanticModel(MODEL_ID.toString()),
            null,
            serving,
            7,
            "SYNC_PENDING",
            Instant.parse("2026-08-17T00:00:00Z")
        );
        return new SyncCandidate(projection, 0);
    }
}
