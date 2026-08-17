package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ServingSyncState;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetQualityStatusReader.QualityStatusSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetStatusSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.StatusAxes;
import com.yuzhi.dts.platform.service.catalog.dto.AssetRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Read-only join of the canonical asset semantics and model-serving projections. */
@Service
@ConditionalOnProperty(
    prefix = "dts.platform.catalog",
    name = "asset-status-view-enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CatalogAssetStatusViewService {

    private final CatalogAssetSemanticStore semanticStore;
    private final CatalogModelServingProjectionRepository servingRepository;
    private final CatalogAssetQualityStatusReader qualityStatusReader;
    private final Clock clock;

    @Autowired
    public CatalogAssetStatusViewService(
        CatalogAssetSemanticStore semanticStore,
        CatalogModelServingProjectionRepository servingRepository,
        CatalogAssetQualityStatusReader qualityStatusReader
    ) {
        this(semanticStore, servingRepository, qualityStatusReader, Clock.systemUTC());
    }

    CatalogAssetStatusViewService(
        CatalogAssetSemanticStore semanticStore,
        CatalogModelServingProjectionRepository servingRepository,
        CatalogAssetQualityStatusReader qualityStatusReader,
        Clock clock
    ) {
        this.semanticStore = semanticStore;
        this.servingRepository = servingRepository;
        this.qualityStatusReader = qualityStatusReader;
        this.clock = clock;
    }

    /**
     * Reads one asset page using at most three additional SQL statements: one semantics batch,
     * one serving batch and one current-quality batch. Missing projections remain explicit instead
     * of becoming a false success.
     */
    @Transactional(readOnly = true)
    public Map<AssetRef, AssetDeliveryStatus> read(List<AssetRef> assetRefs) {
        if (assetRefs == null || assetRefs.isEmpty()) {
            return Map.of();
        }
        LinkedHashSet<String> datasetKeys = new LinkedHashSet<>();
        LinkedHashSet<AssetRef> normalizedRefs = new LinkedHashSet<>();
        for (AssetRef ref : assetRefs) {
            if (ref == null || !StringUtils.hasText(ref.assetType()) || !StringUtils.hasText(ref.assetKey())) {
                continue;
            }
            AssetRef normalized = new AssetRef(ref.assetType().trim().toUpperCase(Locale.ROOT), ref.assetKey().trim());
            normalizedRefs.add(normalized);
            if (CatalogAssetType.DATASET.name().equals(normalized.assetType())) {
                datasetKeys.add(normalized.assetKey());
            }
        }
        Instant now = clock.instant();
        Map<String, AssetStatusSnapshot> semantics = datasetKeys.isEmpty()
            ? Map.of()
            : semanticStore.findStatusSnapshots(CatalogAssetType.DATASET, datasetKeys, now);
        Map<String, List<ServingSyncState>> serving = datasetKeys.isEmpty()
            ? Map.of()
            : servingRepository.findSyncStatesByAssetKeys(CatalogAssetType.DATASET, datasetKeys);
        LinkedHashSet<UUID> datasetIds = new LinkedHashSet<>();
        semantics.values().stream().map(AssetStatusSnapshot::resourceId).filter(java.util.Objects::nonNull).forEach(datasetIds::add);
        Map<UUID, QualityStatusSnapshot> quality = datasetIds.isEmpty()
            ? Map.of()
            : qualityStatusReader.readLatest(datasetIds);
        Map<AssetRef, AssetDeliveryStatus> result = new LinkedHashMap<>();
        for (AssetRef ref : normalizedRefs) {
            if (!CatalogAssetType.DATASET.name().equals(ref.assetType())) {
                result.put(ref, AssetDeliveryStatus.missing("ASSET_SEMANTICS_UNSUPPORTED_TYPE"));
                continue;
            }
            AssetStatusSnapshot semantic = semantics.get(ref.assetKey());
            result.put(
                ref,
                merge(
                    semantic,
                    serving.getOrDefault(ref.assetKey(), List.of()),
                    semantic == null || semantic.resourceId() == null ? null : quality.get(semantic.resourceId()),
                    now
                )
            );
        }
        return Map.copyOf(result);
    }

    private AssetDeliveryStatus merge(
        AssetStatusSnapshot semantic,
        List<ServingSyncState> servingStates,
        QualityStatusSnapshot quality,
        Instant now
    ) {
        List<ServingSyncState> states = servingStates == null ? List.of() : servingStates;
        List<ModelRef> modelRefs = new ArrayList<>();
        for (ServingSyncState state : states) {
            ModelServingProjection projection = state.projection();
            ServingRef active = projection.servingRef();
            PublishedRef published = projection.latestPublishedRef();
            modelRefs.add(
                new ModelRef(
                    projection.tenantId(),
                    projection.modelSpecId(),
                    active != null ? active.modelRevision() : published != null ? published.modelRevision() : null,
                    active != null ? active.candidateId() : published != null ? published.candidateId() : null,
                    active != null ? active.candidateVersion() : published != null ? published.candidateVersion() : null,
                    active != null
                )
            );
        }
        modelRefs.sort(Comparator.comparing(ref -> ref.modelSpecId() == null ? "" : ref.modelSpecId().toString()));
        ServingSync servingSync = summarizeServing(states);
        if (semantic == null) {
            return new AssetDeliveryStatus(
                null,
                "CONDITIONAL",
                List.of("ASSET_SEMANTICS_MISSING"),
                null,
                modelRefs,
                servingSync,
                "UNKNOWN"
            );
        }
        Boolean effectiveQualityGate = effectiveQualityGate(semantic.qualityGatePassed(), quality);
        var eligibility = quality == null
            ? semantic.eligibility()
            : CatalogAssetSemanticsContract.evaluateEligibility(
                semantic.statusAxes(),
                effectiveQualityGate,
                semantic.permissionGatePassed(),
                now
            );
        String decision = eligibility == null || eligibility.decision() == null
            ? "CONDITIONAL"
            : eligibility.decision().name();
        List<String> reasons = eligibility == null ? List.of("ELIGIBILITY_EVIDENCE_MISSING") : eligibility.reasonCodes();
        String qualityStatus = quality == null
            ? semantic.qualityGatePassed() == null
            ? "UNKNOWN"
            : semantic.qualityGatePassed() ? "PASSED" : "FAILED"
            : qualityStatus(quality.status());
        return new AssetDeliveryStatus(
            semantic.statusAxes(),
            decision,
            reasons,
            semantic.asOf(),
            modelRefs,
            servingSync,
            qualityStatus
        );
    }

    private Boolean effectiveQualityGate(Boolean projected, QualityStatusSnapshot quality) {
        if (quality == null) {
            return projected;
        }
        return switch (normalizedQualityRunStatus(quality.status())) {
            case "SUCCEEDED" -> Boolean.TRUE;
            case "FAILED" -> Boolean.FALSE;
            default -> null;
        };
    }

    private String qualityStatus(String status) {
        return switch (normalizedQualityRunStatus(status)) {
            case "SUCCEEDED" -> "PASSED";
            case "FAILED" -> "FAILED";
            case "QUEUED", "PENDING", "RUNNING" -> "RUNNING";
            default -> "UNKNOWN";
        };
    }

    private String normalizedQualityRunStatus(String status) {
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
    }

    private ServingSync summarizeServing(List<ServingSyncState> states) {
        if (states == null || states.isEmpty()) {
            return new ServingSync("NOT_APPLICABLE", 0, null, null, null);
        }
        ServingSyncState representative = states
            .stream()
            .max(
                Comparator.comparingInt((ServingSyncState state) -> syncRank(state.projection().syncStatus()))
                    .thenComparing(state -> state.projection().updatedAt(), Comparator.nullsFirst(Comparator.naturalOrder()))
            )
            .orElseThrow();
        int attempts = states.stream().mapToInt(ServingSyncState::syncAttempts).max().orElse(0);
        String lastError = states
            .stream()
            .map(ServingSyncState::lastSyncError)
            .filter(StringUtils::hasText)
            .findFirst()
            .orElse(null);
        Instant nextAttemptAt = states
            .stream()
            .map(ServingSyncState::nextSyncAt)
            .filter(java.util.Objects::nonNull)
            .min(Comparator.naturalOrder())
            .orElse(null);
        Instant updatedAt = states
            .stream()
            .map(state -> state.projection().updatedAt())
            .filter(java.util.Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(null);
        String status = normalizeServingStatus(representative.projection().syncStatus());
        return new ServingSync(status, attempts, lastError, nextAttemptAt, updatedAt);
    }

    private int syncRank(String value) {
        return switch (normalizeServingStatus(value)) {
            case "SYNC_FAILED" -> 4;
            case "SYNC_PENDING" -> 3;
            case "SYNCED" -> 2;
            default -> 1;
        };
    }

    private String normalizeServingStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "NOT_READY";
    }

    public record ModelRef(
        String tenantId,
        UUID modelSpecId,
        Integer modelRevision,
        UUID candidateId,
        Integer candidateVersion,
        boolean serving
    ) {}

    public record ServingSync(
        String status,
        int attempts,
        String lastError,
        Instant nextAttemptAt,
        Instant updatedAt
    ) {}

    public record AssetDeliveryStatus(
        StatusAxes statusAxes,
        String consumptionEligibility,
        List<String> eligibilityReasons,
        Instant projectionUpdatedAt,
        List<ModelRef> modelRefs,
        ServingSync servingSync,
        String qualityStatus
    ) {
        public AssetDeliveryStatus {
            eligibilityReasons = eligibilityReasons == null ? List.of() : List.copyOf(eligibilityReasons);
            modelRefs = modelRefs == null ? List.of() : List.copyOf(modelRefs);
            servingSync = servingSync == null ? new ServingSync("NOT_APPLICABLE", 0, null, null, null) : servingSync;
            qualityStatus = StringUtils.hasText(qualityStatus) ? qualityStatus : "UNKNOWN";
        }

        public static AssetDeliveryStatus missing(String reasonCode) {
            return new AssetDeliveryStatus(
                null,
                "CONDITIONAL",
                List.of(reasonCode),
                null,
                List.of(),
                new ServingSync("NOT_APPLICABLE", 0, null, null, null),
                "UNKNOWN"
            );
        }
    }
}
