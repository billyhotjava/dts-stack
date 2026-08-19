package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetVersionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.SyncCandidate;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort;
import com.yuzhi.dts.platform.service.catalog.CanonicalModelIdentityReadPort.ModelIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DeriveCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.SubjectRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelSemanticContract.PublishPayload;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.sql.QueryDatasetContractSnapshotAssembler;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Projects one published serving ModelSpec into the canonical governed BI query-dataset owner. */
@Service
@Transactional
public class ModelQueryDatasetProjectionService {

    private final QueryDatasetAssetRepository assetRepository;
    private final QueryDatasetVersionRepository versionRepository;
    private final CatalogDatasetRepository catalogDatasetRepository;
    private final CanonicalModelIdentityReadPort identityReadPort;
    private final CatalogConsumerClassificationService classificationService;
    private final QueryDatasetContractSnapshotAssembler contractAssembler;

    public ModelQueryDatasetProjectionService(
        QueryDatasetAssetRepository assetRepository,
        QueryDatasetVersionRepository versionRepository,
        CatalogDatasetRepository catalogDatasetRepository,
        CanonicalModelIdentityReadPort identityReadPort,
        CatalogConsumerClassificationService classificationService,
        QueryDatasetContractSnapshotAssembler contractAssembler
    ) {
        this.assetRepository = assetRepository;
        this.versionRepository = versionRepository;
        this.catalogDatasetRepository = catalogDatasetRepository;
        this.identityReadPort = identityReadPort;
        this.classificationService = classificationService;
        this.contractAssembler = contractAssembler;
    }

    public ProjectionResult project(SyncCandidate candidate, PublishPayload semantic) {
        Objects.requireNonNull(candidate, "candidate is required");
        Objects.requireNonNull(semantic, "semantic payload is required");
        var projection = Objects.requireNonNull(candidate.projection(), "serving projection is required");
        ServingRef serving = projection.servingRef();
        if (serving == null || serving.physicalAssetId() == null) {
            throw new IllegalStateException("MODEL_QUERY_DATASET_PHYSICAL_ASSET_REQUIRED");
        }
        CatalogDataset physical = catalogDatasetRepository
            .findById(serving.physicalAssetId())
            .orElseThrow(() -> new IllegalStateException("MODEL_QUERY_DATASET_PHYSICAL_ASSET_REQUIRED"));
        if (!Boolean.TRUE.equals(physical.getEnabled()) || !isPublishedBiLayer(physical.getWarehouseLayer())) {
            return new ProjectionResult(false, false, null, 0);
        }

        UUID modelSpecId = projection.modelSpecId();
        ModelIdentity identity = identityReadPort
            .findById(modelSpecId)
            .orElseThrow(() -> new IllegalStateException("MODEL_QUERY_DATASET_MODEL_IDENTITY_REQUIRED"));
        String sql = baseSql(serving);
        QueryDatasetAsset asset = assetRepository.findBySourceModelSpecId(modelSpecId).orElseGet(QueryDatasetAsset::new);
        boolean newAsset = asset.getId() == null;
        mapAsset(asset, physical, serving, modelSpecId, sql, newAsset);
        asset = assetRepository.save(asset);
        if (asset.getId() == null) {
            throw new IllegalStateException("MODEL_QUERY_DATASET_ID_REQUIRED");
        }

        List<QueryDatasetVersion> versions = versionRepository.findByDataset_IdOrderByVersionNoDesc(asset.getId());
        QueryDatasetVersion current = findCurrent(versions, semantic.specVersion(), sql);
        if (current != null) {
            publishAsset(asset, current);
            assetRepository.save(asset);
            return new ProjectionResult(true, false, asset.getId(), current.getVersionNo());
        }

        var classification = classificationService.derive(
            new DeriveCommand(
                "REPORT",
                CatalogAssetKey.biDataset(asset),
                null,
                List.of(new SubjectRef("ASSET", identity.assetKey())),
                "model-spec:" + modelSpecId + ":" + semantic.specVersion()
            )
        );
        QueryDatasetVersion version = new QueryDatasetVersion();
        version.setDataset(asset);
        version.setVersionNo(versionRepository.findMaxVersionNo(asset.getId()) + 1);
        version.setStatus("DRAFT");
        version.setSqlText(sql);
        version.setChangeSummary("由已发布模型 " + identity.modelName() + " 自动同步");
        var snapshot = contractAssembler.assemblePublishedModel(asset, version, physical, identity, semantic, classification);
        if (!"READY".equals(snapshot.status())) {
            throw new IllegalStateException("MODEL_QUERY_DATASET_CONTRACT_NOT_READY");
        }
        version.setSemanticContractSchema(snapshot.schema());
        version.setSemanticContractVersion(snapshot.version());
        version.setSemanticContractJson(snapshot.contractJson());
        version.setSemanticContractChecksum(snapshot.checksum());
        version.setContractSnapshotStatus(snapshot.status());
        version.setStatus("PUBLISHED");
        version.setPublishedAt(Instant.now());

        for (QueryDatasetVersion previous : versions) {
            if ("PUBLISHED".equalsIgnoreCase(previous.getStatus())) {
                previous.setStatus("ARCHIVED");
                versionRepository.save(previous);
            }
        }
        versionRepository.save(version);
        publishAsset(asset, version);
        assetRepository.save(asset);
        return new ProjectionResult(true, true, asset.getId(), version.getVersionNo());
    }

    private static void mapAsset(
        QueryDatasetAsset asset,
        CatalogDataset physical,
        ServingRef serving,
        UUID modelSpecId,
        String sql,
        boolean newAsset
    ) {
        asset.setSourceModelSpecId(modelSpecId);
        asset.setName(firstText(physical.getName(), serving.identifier()));
        asset.setDescription(physical.getDescription());
        asset.setSourceDatasourceId(firstNonNull(physical.getSourceId(), serving.sourceId()));
        asset.setSourceDatasourceName(firstText(serving.databaseName(), serving.adapter()));
        asset.setOwnerDept(trimToNull(physical.getOwnerDept()));
        asset.setRefreshStrategy("MODEL_PUBLISH");
        asset.setSqlText(sql);
        asset.setParametersJson(null);
        asset.setEnabled(Boolean.TRUE);
        if (newAsset && StringUtils.hasText(physical.getOwner())) {
            asset.setCreatedBy(physical.getOwner().trim());
        }
    }

    private static QueryDatasetVersion findCurrent(
        List<QueryDatasetVersion> versions,
        String contractVersion,
        String sql
    ) {
        if (versions == null) return null;
        return versions
            .stream()
            .filter(version -> "PUBLISHED".equalsIgnoreCase(version.getStatus()))
            .filter(version -> "READY".equalsIgnoreCase(version.getContractSnapshotStatus()))
            .filter(version -> Objects.equals(trimToNull(version.getSemanticContractVersion()), trimToNull(contractVersion)))
            .filter(version -> Objects.equals(version.getSqlText(), sql))
            .findFirst()
            .orElse(null);
    }

    private static void publishAsset(QueryDatasetAsset asset, QueryDatasetVersion version) {
        asset.setPublishedVersion(version.getVersionNo());
        asset.setStatus("PUBLISHED");
        asset.setSqlText(version.getSqlText());
        asset.setLatestExecutionId(null);
        asset.setLatestResultSetId(null);
        asset.setEnabled(Boolean.TRUE);
    }

    private static String baseSql(ServingRef serving) {
        if (!StringUtils.hasText(serving.schemaName()) || !StringUtils.hasText(serving.identifier())) {
            throw new IllegalStateException("MODEL_QUERY_DATASET_PHYSICAL_TARGET_REQUIRED");
        }
        return "SELECT * FROM " + quote(serving.schemaName()) + "." + quote(serving.identifier());
    }

    private static String quote(String identifier) {
        return "\"" + identifier.trim().replace("\"", "\"\"") + "\"";
    }

    private static boolean isPublishedBiLayer(String value) {
        String layer = trimToNull(value);
        if (layer == null) return false;
        String normalized = layer.toUpperCase(Locale.ROOT);
        return "DWS".equals(normalized) || "ADS".equals(normalized);
    }

    private static UUID firstNonNull(UUID first, UUID second) {
        return first != null ? first : second;
    }

    private static String firstText(String first, String second) {
        String value = trimToNull(first);
        return value != null ? value : trimToNull(second);
    }

    private static String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ProjectionResult(boolean projected, boolean changed, UUID datasetId, int version) {}
}
