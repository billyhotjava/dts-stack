package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.catalog.lineage.SqlTableReferenceExtractor;
import com.yuzhi.dts.platform.service.catalog.lineage.SqlTableReferenceExtractor.TableRef;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class CatalogAutoLineageService {
    private static final String RELATION_AUTO_VIEW = "AUTO_VIEW";

    private final SqlTableReferenceExtractor extractor;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDatasetLineageRepository lineageRepository;

    public CatalogAutoLineageService(
        SqlTableReferenceExtractor extractor,
        CatalogDatasetRepository datasetRepository,
        CatalogDatasetLineageRepository lineageRepository
    ) {
        this.extractor = extractor;
        this.datasetRepository = datasetRepository;
        this.lineageRepository = lineageRepository;
    }

    public AutoLineageResult syncAutoViewLineage(CatalogDataset downstream, String viewSql) {
        if (downstream == null || downstream.getId() == null) {
            return AutoLineageResult.skipped("invalid-downstream");
        }
        if (!StringUtils.hasText(viewSql)) {
            return AutoLineageResult.skipped("empty-view-sql");
        }
        UUID sourceId = downstream.getSourceId();
        String defaultSchema = downstream.getHiveDatabase();

        Set<TableRef> refs = extractor.extract(viewSql);
        if (refs.isEmpty()) {
            cleanupAutoEdges(downstream.getId(), Set.of());
            return new AutoLineageResult(0, 0, 0, "no-refs");
        }

        Set<UUID> upstreamIds = new HashSet<>();
        for (TableRef ref : refs) {
            String schema = ref.schema();
            if (!StringUtils.hasText(schema)) {
                schema = defaultSchema;
            }
            String table = ref.table();
            if (!StringUtils.hasText(table) || !StringUtils.hasText(schema)) {
                continue;
            }
            CatalogDataset upstream = resolveDataset(sourceId, schema, table);
            if (upstream == null || upstream.getId() == null) {
                continue;
            }
            if (upstream.getId().equals(downstream.getId())) {
                continue;
            }
            upstreamIds.add(upstream.getId());
        }

        int removed = cleanupAutoEdges(downstream.getId(), upstreamIds);
        int created = 0;
        int retained = 0;
        for (UUID upstreamId : upstreamIds) {
            if (upstreamId == null) {
                continue;
            }
            var existing = lineageRepository.findFirstByUpstreamDatasetIdAndDownstreamDatasetId(upstreamId, downstream.getId());
            if (existing.isPresent()) {
                retained++;
                continue;
            }
            CatalogDatasetLineage link = new CatalogDatasetLineage();
            link.setUpstreamDatasetId(upstreamId);
            link.setDownstreamDatasetId(downstream.getId());
            link.setRelationType(RELATION_AUTO_VIEW);
            link.setUpstreamAssetType("DATASET");
            link.setDownstreamAssetType("VIEW");
            link.setDirection("UPSTREAM_TO_DOWNSTREAM");
            link.setNotes("auto:view");
            lineageRepository.save(link);
            created++;
        }

        return new AutoLineageResult(created, retained, removed, "ok");
    }

    private CatalogDataset resolveDataset(UUID sourceId, String schema, String table) {
        String normalizedSchema = schema.trim();
        String normalizedTable = table.trim();
        if (sourceId != null) {
            return datasetRepository
                .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(sourceId, normalizedSchema, normalizedTable)
                .orElse(null);
        }
        return datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(normalizedSchema, normalizedTable).orElse(null);
    }

    private int cleanupAutoEdges(UUID downstreamId, Set<UUID> keepUpstreamIds) {
        if (downstreamId == null) {
            return 0;
        }
        List<CatalogDatasetLineage> existingAuto = lineageRepository.findByDownstreamDatasetIdAndRelationTypeIgnoreCase(downstreamId, RELATION_AUTO_VIEW);
        if (existingAuto.isEmpty()) {
            return 0;
        }
        int removed = 0;
        for (CatalogDatasetLineage link : existingAuto) {
            if (link == null || link.getId() == null) {
                continue;
            }
            UUID upstream = link.getUpstreamDatasetId();
            if (upstream != null && keepUpstreamIds != null && keepUpstreamIds.contains(upstream)) {
                continue;
            }
            lineageRepository.delete(link);
            removed++;
        }
        return removed;
    }

    public record AutoLineageResult(int created, int retained, int removed, String status) {
        public static AutoLineageResult skipped(String reason) {
            return new AutoLineageResult(0, 0, 0, reason);
        }
    }
}
