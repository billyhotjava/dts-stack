package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogClassificationPropagationService {

    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository datasetLineageRepository;
    private final CatalogColumnLineageRepository columnLineageRepository;
    private final CatalogClassificationService classificationService;

    public CatalogClassificationPropagationService(
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository datasetLineageRepository,
        CatalogColumnLineageRepository columnLineageRepository,
        CatalogClassificationService classificationService
    ) {
        this.datasetRepository = datasetRepository;
        this.datasetLineageRepository = datasetLineageRepository;
        this.columnLineageRepository = columnLineageRepository;
        this.classificationService = classificationService;
    }

    @Transactional
    public PropagationResult recompute(UUID downstreamDatasetId, String triggerRef) {
        CatalogDataset downstream = datasetRepository
            .findById(downstreamDatasetId)
            .orElseThrow(() ->
                new CatalogClassificationException(
                    "CLASSIFICATION_TARGET_NOT_FOUND",
                    "Downstream dataset does not exist: " + downstreamDatasetId
                )
            );
        List<CatalogDatasetLineage> incoming = currentIncoming(downstreamDatasetId);
        if (incoming.isEmpty()) {
            throw new CatalogClassificationException(
                "PENDING_LINEAGE",
                "No current upstream lineage is available for classification propagation"
            );
        }
        if (containsCycle(downstreamDatasetId, new HashSet<>(), new HashSet<>())) {
            throw new CatalogClassificationException(
                "CLASSIFICATION_LINEAGE_CYCLE",
                "A lineage cycle blocks classification publication; the last known highest level is preserved"
            );
        }

        Map<UUID, CatalogDataset> upstreamDatasets = new LinkedHashMap<>();
        Map<UUID, String> upstreamAssetLevels = new LinkedHashMap<>();
        for (CatalogDatasetLineage edge : incoming) {
            CatalogDataset upstream = datasetRepository.findById(edge.getUpstreamDatasetId()).orElse(null);
            if (upstream == null) {
                continue;
            }
            upstreamDatasets.put(upstream.getId(), upstream);
            classificationService
                .resolve("ASSET", CatalogAssetKey.dataset(upstream))
                .ifPresent(snapshot -> upstreamAssetLevels.put(upstream.getId(), snapshot.getEffectiveLevel()));
        }

        List<UUID> incomingIds = incoming
            .stream()
            .map(CatalogDatasetLineage::getId)
            .filter(java.util.Objects::nonNull)
            .toList();
        List<CatalogColumnLineage> columnEdges = incomingIds.isEmpty()
            ? List.of()
            : columnLineageRepository.findByDatasetLineageIdIn(incomingIds);
        Map<String, List<ColumnEvidence>> byDownstreamColumn = new LinkedHashMap<>();
        for (CatalogColumnLineage edge : columnEdges) {
            CatalogDataset upstream = upstreamDatasets.get(edge.getUpstreamDatasetId());
            if (upstream == null) {
                continue;
            }
            String upstreamKey = columnKey(CatalogAssetKey.dataset(upstream), edge.getUpstreamColumn());
            classificationService
                .resolve("COLUMN", upstreamKey)
                .ifPresent(snapshot ->
                    byDownstreamColumn
                        .computeIfAbsent(edge.getDownstreamColumn(), ignored -> new ArrayList<>())
                        .add(new ColumnEvidence(snapshot.getEffectiveLevel(), upstreamKey, snapshot.getRecordVersion()))
                );
        }

        int affectedFields = 0;
        List<String> derivedFieldLevels = new ArrayList<>();
        String downstreamAssetKey = CatalogAssetKey.dataset(downstream);
        for (Map.Entry<String, List<ColumnEvidence>> entry : byDownstreamColumn.entrySet()) {
            List<String> levels = entry.getValue().stream().map(ColumnEvidence::level).toList();
            String effective = SecurityLevelCatalog.maxDataCode(levels);
            if (effective == null) {
                continue;
            }
            String evidence = entry.getValue().toString();
            String downstreamColumnKey = columnKey(downstreamAssetKey, entry.getKey());
            if (classificationService.resolve("COLUMN", downstreamColumnKey).isPresent()) {
                classificationService.inherit(
                    new CatalogClassificationService.InheritCommand(
                        "COLUMN",
                        downstreamColumnKey,
                        levels,
                        triggerRef,
                        "{\"upstreams\":\"" + escape(evidence) + "\"}"
                    )
                );
            } else {
                classificationService.seal(
                    new CatalogClassificationService.SealCommand(
                        "COLUMN",
                        downstreamColumnKey,
                        "DATASET",
                        null,
                        null,
                        null,
                        levels,
                        "UPSTREAM_INHERITANCE",
                        triggerRef,
                        sha256(downstreamAssetKey + ":" + entry.getKey() + ":" + evidence),
                        "{\"upstreams\":\"" + escape(evidence) + "\"}"
                    )
                );
            }
            derivedFieldLevels.add(effective);
            affectedFields++;
        }

        List<String> upstreamLevels = new ArrayList<>(upstreamAssetLevels.values());
        boolean usedAssetFallback = affectedFields == 0;
        List<String> assetInputs = new ArrayList<>(
            new java.util.LinkedHashSet<>(usedAssetFallback ? upstreamLevels : derivedFieldLevels)
        );
        if (!usedAssetFallback) {
            for (String upstreamLevel : upstreamLevels) {
                if (!assetInputs.contains(upstreamLevel)) {
                    assetInputs.add(upstreamLevel);
                }
            }
        }
        String resultingLevel = SecurityLevelCatalog.maxDataCode(assetInputs);
        if (resultingLevel == null) {
            throw new CatalogClassificationException(
                "PENDING_CLASSIFICATION",
                "Upstream lineage exists but no sealed upstream classification can be resolved"
            );
        }

        CatalogClassificationSnapshot downstreamSnapshot = classificationService
            .resolve("ASSET", downstreamAssetKey)
            .map(snapshot ->
                classificationService.inherit(
                    new CatalogClassificationService.InheritCommand(
                        "ASSET",
                        downstreamAssetKey,
                        assetInputs,
                        triggerRef,
                        "{\"assetFallback\":" + usedAssetFallback + ",\"upstreamCount\":" + upstreamLevels.size() + "}"
                    )
                )
            )
            .orElseGet(() ->
                classificationService.sealOrRaise(
                    new CatalogClassificationService.SealCommand(
                        "ASSET",
                        downstreamAssetKey,
                        "DATASET",
                        null,
                        null,
                        null,
                        assetInputs,
                        "UPSTREAM_INHERITANCE",
                        triggerRef,
                        sha256(downstreamAssetKey + ":" + String.join(",", assetInputs)),
                        "{\"assetFallback\":" + usedAssetFallback + ",\"upstreamCount\":" + upstreamLevels.size() + "}"
                    )
                )
            );
        return new PropagationResult(
            downstreamDatasetId,
            downstreamAssetKey,
            downstreamSnapshot.getEffectiveLevel(),
            affectedFields,
            upstreamLevels.size(),
            usedAssetFallback,
            List.of()
        );
    }

    public ImpactExplanation explainImpact(UUID datasetId) {
        List<CatalogDatasetLineage> upstream = currentIncoming(datasetId);
        List<CatalogDatasetLineage> downstream = datasetLineageRepository
            .findByUpstreamDatasetId(datasetId)
            .stream()
            .filter(this::isCurrent)
            .toList();
        return new ImpactExplanation(
            datasetId,
            upstream.stream().map(CatalogDatasetLineage::getUpstreamDatasetId).distinct().toList(),
            downstream.stream().map(CatalogDatasetLineage::getDownstreamDatasetId).distinct().toList(),
            containsCycle(datasetId, new HashSet<>(), new HashSet<>()),
            upstream.isEmpty() ? List.of("PENDING_LINEAGE") : List.of()
        );
    }

    private List<CatalogDatasetLineage> currentIncoming(UUID datasetId) {
        return datasetLineageRepository
            .findByDownstreamDatasetId(datasetId)
            .stream()
            .filter(this::isCurrent)
            .toList();
    }

    private boolean isCurrent(CatalogDatasetLineage edge) {
        return edge.getValidTo() == null;
    }

    private boolean containsCycle(UUID current, Set<UUID> visiting, Set<UUID> visited) {
        if (!visiting.add(current)) {
            return true;
        }
        if (visited.contains(current)) {
            visiting.remove(current);
            return false;
        }
        for (CatalogDatasetLineage edge : currentIncoming(current)) {
            if (containsCycle(edge.getUpstreamDatasetId(), visiting, visited)) {
                return true;
            }
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }

    private String columnKey(String datasetKey, String column) {
        String normalized = String.valueOf(column)
            .trim()
            .toLowerCase(java.util.Locale.ROOT)
            .replaceAll("[^a-z0-9_.:-]+", "_");
        return datasetKey + "/column:" + normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to calculate classification lineage checksum", ex);
        }
    }

    private String escape(String value) {
        return String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ColumnEvidence(String level, String subjectKey, Long snapshotVersion) {}

    public record PropagationResult(
        UUID datasetId,
        String assetKey,
        String effectiveLevel,
        int affectedFields,
        int upstreamSubjects,
        boolean assetFallback,
        List<String> blockers
    ) {}

    public record ImpactExplanation(
        UUID datasetId,
        List<UUID> upstreamDatasetIds,
        List<UUID> downstreamDatasetIds,
        boolean cycleDetected,
        List<String> blockers
    ) {}
}
