package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentityType;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ResolvedSource;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldRole;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelField;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecReader;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.DimensionPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.MetricPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.sql.QueryDatasetContractSnapshotAssembler;
import com.yuzhi.dts.platform.service.sql.QueryDatasetContractSnapshotAssembler.ModelMeasure;
import com.yuzhi.dts.platform.service.sql.QueryDatasetContractSnapshotAssembler.Snapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModelQueryDatasetProjectionServiceTest {

    private static final UUID MODEL_ID = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private static final UUID DATASET_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID PHYSICAL_ASSET_ID = UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID SOURCE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-08-19T02:00:00Z");

    @Test
    void projectsPublishedAdsModelIntoCanonicalPublishedQueryDataset() {
        QueryDatasetAssetRepository assets = mock(QueryDatasetAssetRepository.class);
        QueryDatasetVersionRepository versions = mock(QueryDatasetVersionRepository.class);
        CatalogDatasetRepository catalog = mock(CatalogDatasetRepository.class);
        CanonicalModelIdentityReadPort identities = mock(CanonicalModelIdentityReadPort.class);
        CatalogConsumerClassificationService classifications = mock(CatalogConsumerClassificationService.class);
        QueryDatasetContractSnapshotAssembler assembler = mock(QueryDatasetContractSnapshotAssembler.class);
        CatalogDataset physical = physical("ADS");
        ModelIdentity identity = identity();
        DerivationResult classification = classification();
        Snapshot snapshot = new Snapshot(
            QueryDatasetContractSnapshotAssembler.CONTRACT_SCHEMA,
            "r3",
            "READY",
            "{\"warehouseLayer\":\"ADS\"}",
            "a".repeat(64)
        );
        when(catalog.findById(PHYSICAL_ASSET_ID)).thenReturn(Optional.of(physical));
        when(assets.findBySourceModelSpecId(MODEL_ID)).thenReturn(Optional.empty());
        when(assets.save(any(QueryDatasetAsset.class))).thenAnswer(invocation -> {
            QueryDatasetAsset asset = invocation.getArgument(0);
            if (asset.getId() == null) asset.setId(DATASET_ID);
            return asset;
        });
        when(versions.findMaxVersionNo(DATASET_ID)).thenReturn(0);
        when(versions.findByDataset_IdOrderByVersionNoDesc(DATASET_ID)).thenReturn(List.of());
        when(identities.findById(MODEL_ID)).thenReturn(Optional.of(identity));
        when(classifications.derive(any())).thenReturn(classification);
        when(assembler.assemblePublishedModel(any(), any(), any(), any(), any(), any(), any())).thenReturn(snapshot);
        ModelQueryDatasetProjectionService service = new ModelQueryDatasetProjectionService(
            assets,
            versions,
            catalog,
            identities,
            classifications,
            assembler,
            modelReader()
        );

        var result = service.project(candidate(), payload());

        assertThat(result).isEqualTo(new ModelQueryDatasetProjectionService.ProjectionResult(true, true, DATASET_ID, 1));
        verify(assembler).assemblePublishedModel(
            any(),
            any(),
            any(),
            any(),
            any(),
            org.mockito.ArgumentMatchers.eq(List.of(new ModelMeasure("budget_amount", "预算金额", "numeric"))),
            any()
        );
        verify(versions).save(
            org.mockito.ArgumentMatchers.argThat(version ->
                "PUBLISHED".equals(version.getStatus()) &&
                "READY".equals(version.getContractSnapshotStatus()) &&
                version.getVersionNo() == 1 &&
                version.getSqlText().equals("SELECT * FROM \"public\".\"biz_ads_budget_kpi_v2\"")
            )
        );
        verify(assets, atLeastOnce()).save(
            org.mockito.ArgumentMatchers.argThat(asset ->
                MODEL_ID.equals(asset.getSourceModelSpecId()) &&
                "PUBLISHED".equals(asset.getStatus()) &&
                Integer.valueOf(1).equals(asset.getPublishedVersion()) &&
                Boolean.TRUE.equals(asset.getEnabled())
            )
        );
    }

    @Test
    void skipsPhysicalLayersOutsideDwsAndAds() {
        QueryDatasetAssetRepository assets = mock(QueryDatasetAssetRepository.class);
        QueryDatasetVersionRepository versions = mock(QueryDatasetVersionRepository.class);
        CatalogDatasetRepository catalog = mock(CatalogDatasetRepository.class);
        CanonicalModelIdentityReadPort identities = mock(CanonicalModelIdentityReadPort.class);
        CatalogConsumerClassificationService classifications = mock(CatalogConsumerClassificationService.class);
        QueryDatasetContractSnapshotAssembler assembler = mock(QueryDatasetContractSnapshotAssembler.class);
        when(catalog.findById(PHYSICAL_ASSET_ID)).thenReturn(Optional.of(physical("DWD")));
        ModelQueryDatasetProjectionService service = new ModelQueryDatasetProjectionService(
            assets,
            versions,
            catalog,
            identities,
            classifications,
            assembler,
            modelReader()
        );

        var result = service.project(candidate(), payload());

        assertThat(result).isEqualTo(new ModelQueryDatasetProjectionService.ProjectionResult(false, false, null, 0));
        verify(assets, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void republishesCurrentContractThatDoesNotExposeModelMeasures() {
        QueryDatasetAssetRepository assets = mock(QueryDatasetAssetRepository.class);
        QueryDatasetVersionRepository versions = mock(QueryDatasetVersionRepository.class);
        CatalogDatasetRepository catalog = mock(CatalogDatasetRepository.class);
        CanonicalModelIdentityReadPort identities = mock(CanonicalModelIdentityReadPort.class);
        CatalogConsumerClassificationService classifications = mock(CatalogConsumerClassificationService.class);
        QueryDatasetContractSnapshotAssembler assembler = new QueryDatasetContractSnapshotAssembler(
            new com.fasterxml.jackson.databind.ObjectMapper()
        );
        QueryDatasetAsset existing = new QueryDatasetAsset();
        existing.setId(DATASET_ID);
        QueryDatasetVersion stale = new QueryDatasetVersion();
        stale.setDataset(existing);
        stale.setVersionNo(1);
        stale.setStatus("PUBLISHED");
        stale.setContractSnapshotStatus("READY");
        stale.setSemanticContractVersion("r3");
        stale.setSqlText("SELECT * FROM \"public\".\"biz_ads_budget_kpi_v2\"");
        stale.setSemanticContractJson("{\"metrics\":[{\"code\":\"record_count\",\"expression\":\"*\"}]}");
        when(catalog.findById(PHYSICAL_ASSET_ID)).thenReturn(Optional.of(physical("DWS")));
        when(assets.findBySourceModelSpecId(MODEL_ID)).thenReturn(Optional.of(existing));
        when(assets.save(any(QueryDatasetAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versions.findByDataset_IdOrderByVersionNoDesc(DATASET_ID)).thenReturn(List.of(stale));
        when(versions.findMaxVersionNo(DATASET_ID)).thenReturn(1);
        when(identities.findById(MODEL_ID)).thenReturn(Optional.of(identity()));
        when(classifications.derive(any())).thenReturn(classification());
        ModelQueryDatasetProjectionService service = new ModelQueryDatasetProjectionService(
            assets,
            versions,
            catalog,
            identities,
            classifications,
            assembler,
            modelReader()
        );

        var result = service.project(candidate(), payloadWithoutIndicators());

        assertThat(result).isEqualTo(new ModelQueryDatasetProjectionService.ProjectionResult(true, true, DATASET_ID, 2));
        assertThat(stale.getStatus()).isEqualTo("ARCHIVED");
        verify(versions).save(
            org.mockito.ArgumentMatchers.argThat(version ->
                version.getVersionNo() == 2 &&
                "PUBLISHED".equals(version.getStatus()) &&
                version.getSemanticContractJson().contains("\"code\":\"budget_amount\"") &&
                version.getSemanticContractJson().contains("\"aggregation\":\"SUM\"")
            )
        );
    }

    private static ModelSpecReader modelReader() {
        ModelSpecReader reader = mock(ModelSpecReader.class);
        ModelSpecView model = mock(ModelSpecView.class);
        when(model.fields()).thenReturn(
            List.of(
                new ModelField("project_code", "项目编码", "varchar", false, null, FieldRole.KEY, "DATA_INTERNAL", null, false, null),
                new ModelField("budget_amount", "预算金额", "numeric", true, null, FieldRole.MEASURE, "DATA_INTERNAL", null, false, null)
            )
        );
        when(reader.revision("tenant-a", new ModelRevisionRef(MODEL_ID, 3))).thenReturn(model);
        return reader;
    }

    private static PublishPayload payloadWithoutIndicators() {
        PublishPayload base = payload();
        return new PublishPayload(
            base.tenantId(),
            base.platformDataSourceId(),
            base.modelName(),
            base.tableName(),
            base.schemaName(),
            base.label(),
            base.description(),
            base.securityLevel(),
            base.grain(),
            base.specVersion(),
            base.exposedToModeler(),
            List.of(),
            base.dimensions(),
            base.joins()
        );
    }

    private static CatalogDataset physical(String layer) {
        CatalogDataset dataset = new CatalogDataset();
        dataset.setId(PHYSICAL_ASSET_ID);
        dataset.setName("预算执行指标");
        dataset.setDescription("预算执行已发布分析模型");
        dataset.setSourceId(SOURCE_ID);
        dataset.setHiveDatabase("public");
        dataset.setHiveTable("biz_ads_budget_kpi_v2");
        dataset.setWarehouseLayer(layer);
        dataset.setClassification("DATA_INTERNAL");
        dataset.setOwner("xiezm");
        dataset.setEnabled(true);
        return dataset;
    }

    private static ModelIdentity identity() {
        return new ModelIdentity(
            ModelIdentityType.SEMANTIC_MODEL,
            MODEL_ID,
            MODEL_ID,
            null,
            "预算执行指标",
            3,
            null
        );
    }

    private static DerivationResult classification() {
        UUID snapshotId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        return new DerivationResult(
            "REPORT",
            "bi-dataset:" + DATASET_ID,
            "DATA_INTERNAL",
            snapshotId,
            1,
            List.of(new ResolvedSource("ASSET", "semantic-model:" + MODEL_ID, snapshotId, 1, "DATA_INTERNAL")),
            null,
            List.of()
        );
    }

    private static PublishPayload payload() {
        return new PublishPayload(
            "default",
            SOURCE_ID.toString(),
            "model_spec_" + MODEL_ID.toString().replace("-", ""),
            "biz_ads_budget_kpi_v2",
            "public",
            "预算执行指标",
            "预算执行已发布分析模型",
            "INTERNAL",
            "project_code",
            "r3",
            true,
            List.of(new MetricPayload("budget_amount", "预算金额", "SUM", "budget_amount", "元", null, null, null, "INTERNAL", null)),
            List.of(new DimensionPayload("project_code", "项目编码", "dimension", null)),
            List.of()
        );
    }

    private static SyncCandidate candidate() {
        ServingRef serving = new ServingRef(
            MODEL_ID,
            3,
            "b".repeat(64),
            2,
            "c".repeat(64),
            UUID.fromString("20000000-0000-0000-0000-000000000001"),
            7,
            1,
            UUID.fromString("50000000-0000-0000-0000-000000000001"),
            1,
            UUID.fromString("60000000-0000-0000-0000-000000000001"),
            "d".repeat(64),
            PHYSICAL_ASSET_ID,
            SOURCE_ID,
            "postgres",
            "biadmin",
            "public",
            "biz_ads_budget_kpi_v2",
            NOW
        );
        ModelServingProjection projection = new ModelServingProjection(
            "tenant-a",
            MODEL_ID,
            CatalogAssetType.SEMANTIC_MODEL,
            "semantic-model:" + MODEL_ID,
            null,
            serving,
            7,
            "SYNC_PENDING",
            NOW
        );
        return new SyncCandidate(projection, 0);
    }
}
