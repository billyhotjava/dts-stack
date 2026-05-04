package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.domain.catalog.CatalogLineageJob;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogLineageJobRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import com.yuzhi.dts.platform.service.etl.OdsTableMappingSyncService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog/lineage")
@Transactional
public class CatalogLineageResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final String INGESTION_SERVICE_EXPRESSION =
        "hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-ingestion'";

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDatasetLineageRepository lineageRepo;
    private final CatalogColumnLineageRepository columnLineageRepo;
    private final CatalogLineageJobRepository lineageJobRepo;
    private final InfraOdsTableMappingRepository odsMappingRepo;
    private final InfraDataSourceRepository dataSourceRepo;
    private final IngestionLineageWriter ingestionLineageWriter;
    private final OdsTableMappingSyncService odsTableMappingSyncService;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogLineageResource(
        CatalogDatasetRepository datasetRepo,
        CatalogDatasetLineageRepository lineageRepo,
        CatalogColumnLineageRepository columnLineageRepo,
        CatalogLineageJobRepository lineageJobRepo,
        InfraOdsTableMappingRepository odsMappingRepo,
        InfraDataSourceRepository dataSourceRepo,
        IngestionLineageWriter ingestionLineageWriter,
        OdsTableMappingSyncService odsTableMappingSyncService,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.lineageRepo = lineageRepo;
        this.columnLineageRepo = columnLineageRepo;
        this.lineageJobRepo = lineageJobRepo;
        this.odsMappingRepo = odsMappingRepo;
        this.dataSourceRepo = dataSourceRepo;
        this.ingestionLineageWriter = ingestionLineageWriter;
        this.odsTableMappingSyncService = odsTableMappingSyncService;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    public record LineageCreateRequest(
        UUID upstreamDatasetId,
        UUID downstreamDatasetId,
        String relationType,
        String notes,
        String upstreamAssetType,
        String downstreamAssetType,
        String direction,
        String projectName
    ) {}

    @GetMapping
    public ApiResponse<Map<String, Object>> getLineage(
        @RequestParam UUID datasetId,
        @RequestParam(name = "projectName", required = false) String projectName,
        @RequestParam(name = "layers", required = false) String layers,
        @RequestParam(name = "changedWithinHours", required = false) Integer changedWithinHours,
        @RequestParam(name = "sourceId", required = false) UUID sourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }

        Set<String> layerFilters = parseLayerFilters(layers);
        Instant changedSince = resolveChangedSince(changedWithinHours);
        List<CatalogDatasetLineage> links = lineageRepo
            .findByEitherSideAt(datasetId, Instant.now())
            .stream()
            .filter(link -> matchProject(link, projectName) && matchLineageFilters(link, datasetId, layerFilters, sourceId, changedSince, effDept))
            .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("projectName", trimToNull(projectName));
        payload.put("layers", layerFilters);
        payload.put("changedWithinHours", normalizeChangedWindow(changedWithinHours));
        payload.put("sourceId", sourceId != null ? sourceId.toString() : null);
        payload.put(
            "upstreams",
            links.stream().filter(l -> datasetId.equals(l.getDownstreamDatasetId())).map(l -> toEdgeDto(l, effDept)).filter(Objects::nonNull).toList()
        );
        payload.put(
            "downstreams",
            links.stream().filter(l -> datasetId.equals(l.getUpstreamDatasetId())).map(l -> toEdgeDto(l, effDept)).filter(Objects::nonNull).toList()
        );

        audit.auditAction("CATALOG_LINEAGE_VIEW", AuditStage.SUCCESS, datasetId.toString(), Map.of("summary", "查看血缘关系"));
        return ApiResponses.ok(payload);
    }

    /**
     * 影响分析（多跳血缘）：返回指定深度内的节点与边。
     * - depth=1 等价于只查看一跳关系
     */
    @GetMapping("/impact")
    public ApiResponse<Map<String, Object>> impact(
        @RequestParam UUID datasetId,
        @RequestParam(name = "direction", required = false, defaultValue = "BOTH") String direction,
        @RequestParam(name = "depth", required = false, defaultValue = "3") int depth,
        @RequestParam(name = "projectName", required = false) String projectName,
        @RequestParam(name = "layers", required = false) String layers,
        @RequestParam(name = "changedWithinHours", required = false) Integer changedWithinHours,
        @RequestParam(name = "sourceId", required = false) UUID sourceId,
        @RequestParam(name = "withJobs", required = false, defaultValue = "false") boolean withJobs,
        @RequestParam(name = "withColumns", required = false, defaultValue = "false") boolean withColumns,
        @RequestParam(name = "at", required = false) String at,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset root = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(root) || !accessChecker.departmentAllowed(root, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }

        Set<String> layerFilters = parseLayerFilters(layers);
        Instant changedSince = resolveChangedSince(changedWithinHours);
        Instant snapshotAt = resolveSnapshotAt(at);
        int safeDepth = Math.max(1, Math.min(depth, 10));
        String dir = StringUtils.hasText(direction) ? direction.trim().toUpperCase(Locale.ROOT) : "BOTH";
        boolean upstreamEnabled = "UPSTREAM".equals(dir) || "BOTH".equals(dir);
        boolean downstreamEnabled = "DOWNSTREAM".equals(dir) || "BOTH".equals(dir);

        Set<UUID> visited = new LinkedHashSet<>();
        Set<CatalogDatasetLineage> edges = new LinkedHashSet<>();
        Set<UUID> frontier = new LinkedHashSet<>();
        visited.add(datasetId);
        frontier.add(datasetId);

        for (int level = 0; level < safeDepth; level++) {
            if (frontier.isEmpty()) {
                break;
            }
            Set<UUID> next = new LinkedHashSet<>();
            for (UUID current : frontier) {
                List<CatalogDatasetLineage> direct = lineageRepo.findByEitherSideAt(current, snapshotAt);
                for (CatalogDatasetLineage edge : direct) {
                    if (edge == null || !matchProject(edge, projectName)) {
                        continue;
                    }
                    UUID up = edge.getUpstreamDatasetId();
                    UUID down = edge.getDownstreamDatasetId();
                    boolean include = false;
                    if (upstreamEnabled && current.equals(down)) {
                        include = true;
                    }
                    if (downstreamEnabled && current.equals(up)) {
                        include = true;
                    }
                    if (!include) {
                        continue;
                    }
                    edges.add(edge);
                    if (up != null && !visited.contains(up)) {
                        visited.add(up);
                        next.add(up);
                    }
                    if (down != null && !visited.contains(down)) {
                        visited.add(down);
                        next.add(down);
                    }
                }
            }
            frontier = next;
        }

        Map<UUID, CatalogDataset> nodes = new LinkedHashMap<>();
        for (UUID id : visited) {
            datasetRepo.findById(id).ifPresent(ds -> {
                if (accessChecker.canRead(ds) && accessChecker.departmentAllowed(ds, effDept)) {
                    nodes.putIfAbsent(id, ds);
                }
            });
        }

        Map<UUID, CatalogDataset> filteredNodes = nodes
            .entrySet()
            .stream()
            .filter(entry -> {
                if (datasetId.equals(entry.getKey())) {
                    return true;
                }
                CatalogDataset ds = entry.getValue();
                return (
                    matchLayer(ds, layerFilters) &&
                    matchSource(ds, sourceId) &&
                    matchChanged(ds != null ? ds.getLastModifiedDate() : null, changedSince)
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        if (!filteredNodes.containsKey(datasetId)) {
            filteredNodes.put(datasetId, root);
        }

        Set<UUID> filteredIds = filteredNodes.keySet();
        List<Map<String, Object>> datasetEdges = edges
            .stream()
            .filter(edge -> {
                if (edge == null) {
                    return false;
                }
                UUID up = edge.getUpstreamDatasetId();
                UUID down = edge.getDownstreamDatasetId();
                if (up == null || down == null || !filteredIds.contains(up) || !filteredIds.contains(down)) {
                    return false;
                }
                CatalogDataset upNode = filteredNodes.get(up);
                CatalogDataset downNode = filteredNodes.get(down);
                if (!matchChanged(edge.getLastModifiedDate(), changedSince)) {
                    return matchChanged(upNode != null ? upNode.getLastModifiedDate() : null, changedSince) ||
                    matchChanged(downNode != null ? downNode.getLastModifiedDate() : null, changedSince);
                }
                return true;
            })
            .map(edge -> toEdgeDto(edge, effDept))
            .filter(Objects::nonNull)
            .toList();

        List<Map<String, Object>> columnLineages = withColumns ? loadColumnLineageDtos(datasetEdges) : List.of();
        List<Map<String, Object>> nodeDtos = filteredNodes.values().stream().map(this::toDatasetNodeDto).toList();
        List<Map<String, Object>> edgeDtos = datasetEdges;
        if (withJobs) {
            GraphExpansion expansion = expandWithVirtualJobs(
                nodeDtos,
                datasetEdges,
                filteredNodes,
                upstreamEnabled,
                sourceId,
                effDept
            );
            nodeDtos = expansion.nodes();
            edgeDtos = expansion.edges();
        } else {
            edgeDtos = datasetEdges.stream().map(edge -> {
                Map<String, Object> dto = new LinkedHashMap<>(edge);
                dto.put("kind", "DATASET_TO_DATASET");
                dto.put("fromId", dto.get("upstreamDatasetId"));
                dto.put("toId", dto.get("downstreamDatasetId"));
                return dto;
            }).toList();
        }

        Map<String, Long> layerStats = filteredNodes
            .values()
            .stream()
            .collect(
                Collectors.groupingBy(
                    ds -> normalizeLayer(ds != null ? ds.getWarehouseLayer() : null),
                    LinkedHashMap::new,
                    Collectors.counting()
                )
            );
        Map<String, Long> relationStats = edgeDtos
            .stream()
            .collect(
                Collectors.groupingBy(
                    edge -> StringUtils.hasText((String) edge.get("relationType")) ? ((String) edge.get("relationType")).toUpperCase(Locale.ROOT) : "UNKNOWN",
                    LinkedHashMap::new,
                    Collectors.counting()
                )
            );
        long changedNodeCount = filteredNodes
            .values()
            .stream()
            .filter(ds -> matchChanged(ds != null ? ds.getLastModifiedDate() : null, changedSince))
            .count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("direction", dir);
        payload.put("depth", safeDepth);
        payload.put("projectName", trimToNull(projectName));
        payload.put("layers", layerFilters);
        payload.put("changedWithinHours", normalizeChangedWindow(changedWithinHours));
        payload.put("sourceId", sourceId != null ? sourceId.toString() : null);
        payload.put("withJobs", withJobs);
        payload.put("withColumns", withColumns);
        payload.put("snapshotAt", snapshotAt.toString());
        payload.put("timeTravel", StringUtils.hasText(at));
        payload.put("nodeCount", nodeDtos.size());
        payload.put("edgeCount", edgeDtos.size());
        payload.put(
            "impactStats",
            Map.of(
                "layerNodeCounts",
                layerStats,
                "relationTypeCounts",
                relationStats,
                "verificationStatusCounts",
                countByString(edgeDtos, "verificationStatus"),
                "kindNodeCounts",
                countByString(nodeDtos, "kind"),
                "changedNodeCount",
                changedNodeCount,
                "columnLineageCount",
                columnLineages.size()
            )
        );
        payload.put("nodes", nodeDtos);
        payload.put("edges", edgeDtos);
        payload.put("columnLineages", columnLineages);
        audit.auditAction(
            "CATALOG_LINEAGE_IMPACT_VIEW",
            AuditStage.SUCCESS,
            datasetId.toString(),
            Map.of("summary", "查看影响分析", "direction", dir, "depth", safeDepth, "snapshotAt", snapshotAt.toString())
        );
        return ApiResponses.ok(payload);
    }

    @GetMapping("/diff")
    public ApiResponse<Map<String, Object>> diff(
        @RequestParam UUID datasetId,
        @RequestParam String from,
        @RequestParam String to,
        @RequestParam(name = "direction", required = false, defaultValue = "BOTH") String direction,
        @RequestParam(name = "depth", required = false, defaultValue = "3") int depth,
        @RequestParam(name = "projectName", required = false) String projectName,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset root = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(root) || !accessChecker.departmentAllowed(root, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        Instant fromAt = resolveSnapshotAt(from);
        Instant toAt = resolveSnapshotAt(to);
        if (fromAt.isAfter(toAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before or equal to to");
        }
        String dir = StringUtils.hasText(direction) ? direction.trim().toUpperCase(Locale.ROOT) : "BOTH";
        int safeDepth = Math.max(1, Math.min(depth, 10));
        Map<String, Map<String, Object>> left = edgeMapAt(datasetId, dir, safeDepth, projectName, fromAt, effDept);
        Map<String, Map<String, Object>> right = edgeMapAt(datasetId, dir, safeDepth, projectName, toAt, effDept);

        List<Map<String, Object>> added = right
            .entrySet()
            .stream()
            .filter(entry -> !left.containsKey(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
        List<Map<String, Object>> removed = left
            .entrySet()
            .stream()
            .filter(entry -> !right.containsKey(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();
        List<Map<String, Object>> unchanged = right
            .entrySet()
            .stream()
            .filter(entry -> left.containsKey(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("from", fromAt.toString());
        payload.put("to", toAt.toString());
        payload.put("direction", dir);
        payload.put("depth", safeDepth);
        payload.put("projectName", trimToNull(projectName));
        payload.put("addedCount", added.size());
        payload.put("removedCount", removed.size());
        payload.put("unchangedCount", unchanged.size());
        payload.put("addedEdges", added);
        payload.put("removedEdges", removed);
        payload.put("unchangedEdges", unchanged);
        audit.auditAction(
            "CATALOG_LINEAGE_DIFF_VIEW",
            AuditStage.SUCCESS,
            datasetId.toString(),
            Map.of("summary", "查看血缘时间差异", "from", fromAt.toString(), "to", toAt.toString(), "direction", dir, "depth", safeDepth)
        );
        return ApiResponses.ok(payload);
    }

    private Map<String, Map<String, Object>> edgeMapAt(
        UUID datasetId,
        String dir,
        int safeDepth,
        String projectName,
        Instant snapshotAt,
        String effDept
    ) {
        boolean upstreamEnabled = "UPSTREAM".equals(dir) || "BOTH".equals(dir);
        boolean downstreamEnabled = "DOWNSTREAM".equals(dir) || "BOTH".equals(dir);
        Set<UUID> visited = new LinkedHashSet<>();
        Set<CatalogDatasetLineage> edges = new LinkedHashSet<>();
        Set<UUID> frontier = new LinkedHashSet<>();
        visited.add(datasetId);
        frontier.add(datasetId);
        for (int level = 0; level < safeDepth; level++) {
            if (frontier.isEmpty()) {
                break;
            }
            Set<UUID> next = new LinkedHashSet<>();
            for (UUID current : frontier) {
                for (CatalogDatasetLineage edge : lineageRepo.findByEitherSideAt(current, snapshotAt)) {
                    if (edge == null || !matchProject(edge, projectName)) {
                        continue;
                    }
                    UUID up = edge.getUpstreamDatasetId();
                    UUID down = edge.getDownstreamDatasetId();
                    boolean include = false;
                    if (upstreamEnabled && current.equals(down)) {
                        include = true;
                    }
                    if (downstreamEnabled && current.equals(up)) {
                        include = true;
                    }
                    if (!include) {
                        continue;
                    }
                    edges.add(edge);
                    if (up != null && !visited.contains(up)) {
                        visited.add(up);
                        next.add(up);
                    }
                    if (down != null && !visited.contains(down)) {
                        visited.add(down);
                        next.add(down);
                    }
                }
            }
            frontier = next;
        }
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (CatalogDatasetLineage edge : edges) {
            Map<String, Object> dto = toEdgeDto(edge, effDept);
            if (dto == null) {
                continue;
            }
            result.put(edgeKey(dto), dto);
        }
        return result;
    }

    private List<Map<String, Object>> loadColumnLineageDtos(List<Map<String, Object>> datasetEdges) {
        if (datasetEdges == null || datasetEdges.isEmpty()) {
            return List.of();
        }
        Set<UUID> edgeIds = datasetEdges
            .stream()
            .map(edge -> parseUuid(edge != null ? stringValue(edge.get("id")) : null))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (edgeIds.isEmpty()) {
            return List.of();
        }
        return columnLineageRepo
            .findByDatasetLineageIdIn(edgeIds)
            .stream()
            .map(this::toColumnLineageDto)
            .toList();
    }

    private Map<String, Object> toColumnLineageDto(CatalogColumnLineage lineage) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", lineage.getId() != null ? lineage.getId().toString() : null);
        dto.put("datasetLineageId", lineage.getDatasetLineageId() != null ? lineage.getDatasetLineageId().toString() : null);
        dto.put("upstreamDatasetId", lineage.getUpstreamDatasetId() != null ? lineage.getUpstreamDatasetId().toString() : null);
        dto.put("downstreamDatasetId", lineage.getDownstreamDatasetId() != null ? lineage.getDownstreamDatasetId().toString() : null);
        dto.put("upstreamColumnId", lineage.getUpstreamColumnId() != null ? lineage.getUpstreamColumnId().toString() : null);
        dto.put("downstreamColumnId", lineage.getDownstreamColumnId() != null ? lineage.getDownstreamColumnId().toString() : null);
        dto.put("upstreamColumn", lineage.getUpstreamColumn());
        dto.put("downstreamColumn", lineage.getDownstreamColumn());
        dto.put("relationType", lineage.getRelationType());
        dto.put("lineageType", lineage.getLineageType());
        dto.put("expression", lineage.getExpression());
        dto.put("confidence", lineage.getConfidence());
        dto.put("projectName", lineage.getProjectName());
        dto.put("lineageJobId", lineage.getLineageJobId() != null ? lineage.getLineageJobId().toString() : null);
        dto.put("lastObservedAt", lineage.getLastObservedAt());
        dto.put("lastModifiedAt", lineage.getLastModifiedDate());
        return dto;
    }

    private GraphExpansion expandWithVirtualJobs(
        List<Map<String, Object>> datasetNodes,
        List<Map<String, Object>> datasetEdges,
        Map<UUID, CatalogDataset> datasets,
        boolean includeUpstreamIngestion,
        UUID sourceId,
        String effDept
    ) {
        Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
        List<Map<String, Object>> edges = new ArrayList<>();
        for (Map<String, Object> node : datasetNodes) {
            String id = stringValue(node.get("id"));
            if (StringUtils.hasText(id)) {
                nodes.put(id, node);
            }
        }
        Map<UUID, CatalogLineageJob> jobsById = loadLineageJobs(datasetEdges);
        for (Map<String, Object> edge : datasetEdges) {
            String upstreamId = stringValue(edge.get("upstreamDatasetId"));
            String downstreamId = stringValue(edge.get("downstreamDatasetId"));
            if (!StringUtils.hasText(upstreamId) || !StringUtils.hasText(downstreamId)) {
                continue;
            }
            String relationType = stringValue(edge.get("relationType"));
            CatalogLineageJob persistedJob = resolveLineageJob(edge, jobsById);
            String jobId;
            if (persistedJob != null) {
                jobId = "job:" + persistedJob.getId();
                nodes.putIfAbsent(jobId, buildPersistedJobNode(jobId, persistedJob));
            } else {
                String jobType = jobTypeForRelation(relationType);
                jobId = "job:" + normalizedRelation(relationType) + ":" + downstreamId;
                nodes.putIfAbsent(jobId, buildJobNode(jobId, edge, jobType, relationType));
            }
            edges.add(buildVirtualEdge(edge, upstreamId, jobId, "DATASET_TO_JOB"));
            edges.add(buildVirtualEdge(edge, jobId, downstreamId, "JOB_TO_DATASET"));
        }
        if (includeUpstreamIngestion) {
            addOdsIngestionNodes(nodes, edges, datasets, sourceId, effDept);
        }
        return new GraphExpansion(List.copyOf(nodes.values()), edges);
    }

    private Map<UUID, CatalogLineageJob> loadLineageJobs(List<Map<String, Object>> datasetEdges) {
        if (datasetEdges == null || datasetEdges.isEmpty()) {
            return Map.of();
        }
        Set<UUID> ids = datasetEdges
            .stream()
            .map(edge -> parseUuid(edge != null ? stringValue(edge.get("lineageJobId")) : null))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return lineageJobRepo
            .findByIdIn(ids)
            .stream()
            .filter(job -> job.getId() != null)
            .collect(Collectors.toMap(CatalogLineageJob::getId, job -> job, (a, b) -> a, LinkedHashMap::new));
    }

    private CatalogLineageJob resolveLineageJob(Map<String, Object> edge, Map<UUID, CatalogLineageJob> jobsById) {
        UUID jobId = parseUuid(edge != null ? stringValue(edge.get("lineageJobId")) : null);
        return jobId != null && jobsById != null ? jobsById.get(jobId) : null;
    }

    private void addOdsIngestionNodes(
        Map<String, Map<String, Object>> nodes,
        List<Map<String, Object>> edges,
        Map<UUID, CatalogDataset> datasets,
        UUID sourceId,
        String effDept
    ) {
        if (datasets == null || datasets.isEmpty()) {
            return;
        }
        List<InfraOdsTableMapping> mappings = odsMappingRepo.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        Set<String> addaxCoveredTargets = edges
            .stream()
            .filter(edge -> "ADDAX".equalsIgnoreCase(stringValue(edge.get("relationType"))))
            .filter(edge -> "JOB_TO_DATASET".equalsIgnoreCase(stringValue(edge.get("kind"))))
            .map(edge -> stringValue(edge.get("toId")))
            .filter(StringUtils::hasText)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, List<InfraOdsTableMapping>> mappingsByOds = new LinkedHashMap<>();
        for (InfraOdsTableMapping mapping : mappings) {
            if (mapping == null || mapping.getEnabled() == null || !mapping.getEnabled()) {
                continue;
            }
            if (sourceId != null && !sourceId.equals(mapping.getConnectionId())) {
                continue;
            }
            String key = tableKey(mapping.getOdsSchema(), mapping.getOdsTable());
            mappingsByOds.computeIfAbsent(key, ignored -> new ArrayList<>()).add(mapping);
        }
        Map<UUID, InfraDataSource> dataSources = new LinkedHashMap<>();
        for (CatalogDataset dataset : datasets.values()) {
            if (dataset == null || !StringUtils.hasText(dataset.getHiveTable())) {
                continue;
            }
            String layer = normalizeLayer(dataset.getWarehouseLayer());
            if (!"ODS".equals(layer)) {
                continue;
            }
            List<InfraOdsTableMapping> matched = mappingsByOds.get(tableKey(dataset.getHiveDatabase(), dataset.getHiveTable()));
            if (matched == null || matched.isEmpty()) {
                continue;
            }
            String datasetId = dataset.getId() != null ? dataset.getId().toString() : null;
            if (!StringUtils.hasText(datasetId)) {
                continue;
            }
            if (addaxCoveredTargets.contains(datasetId)) {
                continue;
            }
            for (InfraOdsTableMapping mapping : matched) {
                InfraDataSource source = null;
                if (mapping.getConnectionId() != null) {
                    source = dataSources.computeIfAbsent(mapping.getConnectionId(), id -> dataSourceRepo.findById(id).orElse(null));
                }
                if (source != null && !departmentAllowed(source.getOwnerDept(), effDept)) {
                    continue;
                }
                String sourceNodeId = "source:" + (mapping.getConnectionId() != null ? mapping.getConnectionId() : "unknown") + ":" +
                    safeNodePart(mapping.getStreamNamespace()) + ":" + safeNodePart(mapping.getStreamName());
                CatalogLineageJob persistedJob = findAddaxLineageJob(mapping);
                String jobNodeId = persistedJob != null ? "job:" + persistedJob.getId() : "job:ADDAX:" + mapping.getId();
                Map<String, Object> jobEdgeBase = persistedJob != null ? jobEdgeBase(persistedJob) : Map.of();
                nodes.putIfAbsent(sourceNodeId, buildSourceNode(sourceNodeId, mapping, source));
                nodes.putIfAbsent(jobNodeId, persistedJob != null ? buildPersistedJobNode(jobNodeId, persistedJob) : buildAddaxJobNode(jobNodeId, mapping, source));
                edges.add(buildSyntheticEdge(
                    "edge:" + sourceNodeId + ":" + jobNodeId,
                    sourceNodeId,
                    jobNodeId,
                    "SOURCE_TO_JOB",
                    "ADDAX",
                    sourceLabel(mapping, source),
                    mappingLabel(mapping),
                    jobEdgeBase
                ));
                edges.add(buildSyntheticEdge(
                    "edge:" + jobNodeId + ":" + datasetId,
                    jobNodeId,
                    datasetId,
                    "JOB_TO_DATASET",
                    "ADDAX",
                    mappingLabel(mapping),
                    dataset.getName(),
                    jobEdgeBase
                ));
            }
        }
    }

    private Map<String, Object> toDatasetNodeDto(CatalogDataset ds) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("kind", "dataset");
        dto.put("id", ds.getId() != null ? ds.getId().toString() : null);
        dto.put("name", ds.getName());
        dto.put("db", ds.getHiveDatabase());
        dto.put("table", ds.getHiveTable());
        dto.put("type", ds.getType());
        dto.put("assetType", resolveDatasetAssetType(ds));
        dto.put("layer", ds.getWarehouseLayer());
        dto.put("ownerDept", ds.getOwnerDept());
        dto.put("owner", ds.getOwner());
        dto.put("sourceId", ds.getSourceId() != null ? ds.getSourceId().toString() : null);
        dto.put("lastModifiedAt", ds.getLastModifiedDate());
        dto.put("snapshotTime", ds.getSnapshotTime());
        return dto;
    }

    private Map<String, Object> buildJobNode(String jobId, Map<String, Object> edge, String jobType, String relationType) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "job");
        node.put("id", jobId);
        node.put("name", defaultString(edge.get("downstreamName"), relationType));
        node.put("jobType", jobType);
        node.put("relationType", relationType);
        node.put("projectName", edge.get("projectName"));
        node.put("layer", "JOB");
        node.put("lastModifiedAt", edge.get("lastModifiedAt"));
        return node;
    }

    private Map<String, Object> buildPersistedJobNode(String nodeId, CatalogLineageJob job) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "job");
        node.put("id", nodeId);
        node.put("lineageJobId", job.getId() != null ? job.getId().toString() : null);
        node.put("jobKey", job.getJobKey());
        node.put("name", job.getName());
        node.put("jobType", job.getJobType());
        node.put("engine", job.getEngine());
        node.put("relationType", job.getRelationType());
        node.put("projectName", job.getProjectName());
        node.put("layer", "JOB");
        node.put("sourceId", job.getSourceId() != null ? job.getSourceId().toString() : null);
        node.put("ownerDept", job.getOwnerDept());
        node.put("status", job.getStatus());
        node.put("lastExecutionId", job.getLastExecutionId());
        node.put("lastExecutionStatus", job.getLastExecutionStatus());
        node.put("lastObservedAt", job.getLastObservedAt());
        node.put("lastVerifiedAt", job.getLastVerifiedAt());
        node.put("lastModifiedAt", job.getLastModifiedDate());
        return node;
    }

    private Map<String, Object> buildSourceNode(String nodeId, InfraOdsTableMapping mapping, InfraDataSource source) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "source");
        node.put("id", nodeId);
        node.put("name", sourceLabel(mapping, source));
        node.put("db", mapping.getStreamNamespace());
        node.put("table", mapping.getStreamName());
        node.put("type", source != null ? source.getType() : null);
        node.put("assetType", "EXTERNAL_TABLE");
        node.put("layer", "SOURCE");
        node.put("ownerDept", source != null ? source.getOwnerDept() : mapping.getOwnerDept());
        node.put("owner", mapping.getOwner());
        node.put("sourceId", mapping.getConnectionId() != null ? mapping.getConnectionId().toString() : null);
        node.put("lastModifiedAt", mapping.getLastModifiedDate());
        return node;
    }

    private Map<String, Object> buildAddaxJobNode(String nodeId, InfraOdsTableMapping mapping, InfraDataSource source) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("kind", "job");
        node.put("id", nodeId);
        node.put("name", mappingLabel(mapping));
        node.put("jobType", "ADDAX_TASK");
        node.put("relationType", "ADDAX");
        node.put("layer", "JOB");
        node.put("sourceId", mapping.getConnectionId() != null ? mapping.getConnectionId().toString() : null);
        node.put("sourceName", source != null ? source.getName() : null);
        node.put("lastModifiedAt", mapping.getLastModifiedDate());
        return node;
    }

    private Map<String, Object> buildVirtualEdge(Map<String, Object> base, String fromId, String toId, String kind) {
        String relationType = stringValue(base.get("relationType"));
        String edgeId = stringValue(base.get("id")) + ":" + kind;
        return buildSyntheticEdge(
            edgeId,
            fromId,
            toId,
            kind,
            relationType,
            defaultString(base.get("upstreamName"), fromId),
            defaultString(base.get("downstreamName"), toId),
            base
        );
    }

    private Map<String, Object> buildSyntheticEdge(
        String id,
        String fromId,
        String toId,
        String kind,
        String relationType,
        String upstreamName,
        String downstreamName
    ) {
        return buildSyntheticEdge(id, fromId, toId, kind, relationType, upstreamName, downstreamName, Map.of());
    }

    private Map<String, Object> buildSyntheticEdge(
        String id,
        String fromId,
        String toId,
        String kind,
        String relationType,
        String upstreamName,
        String downstreamName,
        Map<String, Object> base
    ) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("kind", kind);
        edge.put("fromId", fromId);
        edge.put("toId", toId);
        edge.put("relationType", relationType);
        edge.put("lineageJobId", base.get("lineageJobId"));
        edge.put("upstreamDatasetId", fromId);
        edge.put("downstreamDatasetId", toId);
        edge.put("upstreamName", upstreamName);
        edge.put("downstreamName", downstreamName);
        edge.put("projectName", base.get("projectName"));
        edge.put("notes", base.get("notes"));
        edge.put("verificationStatus", edgeVerificationStatus(base.get("verificationStatus")));
        edge.put("lastExecutionId", base.get("lastExecutionId"));
        edge.put("lastExecutionStatus", base.get("lastExecutionStatus"));
        edge.put("lastObservedAt", base.get("lastObservedAt"));
        edge.put("lastVerifiedAt", base.get("lastVerifiedAt"));
        edge.put("lastModifiedAt", base.get("lastModifiedAt"));
        return edge;
    }

    private Map<String, Long> countByString(List<Map<String, Object>> rows, String key) {
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows
            .stream()
            .collect(
                Collectors.groupingBy(
                    row -> {
                        String value = row == null ? null : stringValue(row.get(key));
                        return StringUtils.hasText(value) ? value.toLowerCase(Locale.ROOT) : "unknown";
                    },
                    LinkedHashMap::new,
                    Collectors.counting()
                )
            );
    }

    private String edgeVerificationStatus(Object value) {
        String status = stringValue(value);
        return StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : "DECLARED";
    }

    private String jobVerificationStatus(CatalogLineageJob job) {
        if (job == null) {
            return "DECLARED";
        }
        String executionStatus = job.getLastExecutionStatus();
        if ("success".equalsIgnoreCase(executionStatus)) {
            return "VERIFIED";
        }
        if (StringUtils.hasText(executionStatus)) {
            return "KNOWN_UNVERIFIED";
        }
        String status = job.getStatus();
        if ("verified".equalsIgnoreCase(status)) {
            return "VERIFIED";
        }
        if ("known_unverified".equalsIgnoreCase(status) || "failed".equalsIgnoreCase(status) || "running".equalsIgnoreCase(status)) {
            return "KNOWN_UNVERIFIED";
        }
        return "DECLARED";
    }

    private String jobTypeForRelation(String relationType) {
        String relation = normalizedRelation(relationType);
        if ("DBT".equals(relation) || "DBT_MODEL".equals(relation)) {
            return "DBT_MODEL";
        }
        if ("ADDAX".equals(relation)) {
            return "ADDAX_TASK";
        }
        if ("AIRFLOW".equals(relation)) {
            return "AIRFLOW_DAG";
        }
        if ("AUTO_VIEW".equals(relation)) {
            return "VIEW_DEFINITION";
        }
        return "LINEAGE_JOB";
    }

    private String normalizedRelation(String relationType) {
        if (!StringUtils.hasText(relationType)) {
            return "UNKNOWN";
        }
        return relationType.trim().toUpperCase(Locale.ROOT);
    }

    private String resolveDatasetAssetType(CatalogDataset dataset) {
        if (dataset == null) {
            return "DATASET";
        }
        String type = stringValue(dataset.getType());
        if (StringUtils.hasText(type) && type.toUpperCase(Locale.ROOT).contains("VIEW")) {
            return "VIEW";
        }
        String layer = normalizeLayer(dataset.getWarehouseLayer());
        if ("SOURCE".equals(layer)) {
            return "EXTERNAL_TABLE";
        }
        return "DATASET";
    }

    private boolean departmentAllowed(String ownerDept, String effDept) {
        if (!StringUtils.hasText(effDept) || !StringUtils.hasText(ownerDept)) {
            return true;
        }
        return ownerDept.trim().equalsIgnoreCase(effDept.trim());
    }

    private String tableKey(String schema, String table) {
        String normalizedSchema = StringUtils.hasText(schema) ? schema.trim().toLowerCase(Locale.ROOT) : "";
        String normalizedTable = StringUtils.hasText(table) ? table.trim().toLowerCase(Locale.ROOT) : "";
        return normalizedSchema + "." + normalizedTable;
    }

    private String sourceLabel(InfraOdsTableMapping mapping, InfraDataSource source) {
        String table = mapping == null ? null : mapping.getStreamName();
        String namespace = mapping == null ? null : mapping.getStreamNamespace();
        String sourceName = source != null ? source.getName() : null;
        String physical = StringUtils.hasText(namespace) ? namespace.trim() + "." + defaultString(table, "unknown") : defaultString(table, "unknown");
        return StringUtils.hasText(sourceName) ? sourceName.trim() + "/" + physical : physical;
    }

    private String mappingLabel(InfraOdsTableMapping mapping) {
        if (mapping == null) {
            return "Addax task";
        }
        String description = trimToNull(mapping.getDescription());
        if (description != null) {
            int taskEnd = description.indexOf("] ");
            String withoutTask = taskEnd >= 0 ? description.substring(taskEnd + 2) : description;
            int colon = withoutTask.indexOf(": ");
            if (colon > 0) {
                return withoutTask.substring(0, colon);
            }
        }
        String source = StringUtils.hasText(mapping.getStreamName()) ? mapping.getStreamName() : "source";
        String target = StringUtils.hasText(mapping.getOdsTable()) ? mapping.getOdsTable() : "ods";
        return source + " -> " + target;
    }

    private CatalogLineageJob findAddaxLineageJob(InfraOdsTableMapping mapping) {
        String jobKey = addaxJobKey(mapping);
        return StringUtils.hasText(jobKey) ? lineageJobRepo.findByJobKey(jobKey).orElse(null) : null;
    }

    private Map<String, Object> jobEdgeBase(CatalogLineageJob job) {
        if (job == null) {
            return Map.of();
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lineageJobId", job.getId() != null ? job.getId().toString() : null);
        data.put("projectName", job.getProjectName());
        data.put("verificationStatus", jobVerificationStatus(job));
        data.put("lastExecutionId", job.getLastExecutionId());
        data.put("lastExecutionStatus", job.getLastExecutionStatus());
        data.put("lastObservedAt", job.getLastObservedAt());
        data.put("lastVerifiedAt", job.getLastVerifiedAt());
        data.put("lastModifiedAt", job.getLastModifiedDate());
        return data;
    }

    private String addaxJobKey(InfraOdsTableMapping mapping) {
        if (mapping == null) {
            return null;
        }
        if (mapping.getId() != null) {
            return "ADDAX:" + mapping.getId();
        }
        return truncate(
            "ADDAX:" +
            safeNodePart(mapping.getConnectionId() != null ? mapping.getConnectionId().toString() : "unknown") + ":" +
            safeNodePart(tableKey(mapping.getStreamNamespace(), mapping.getStreamName())) + "->" +
            safeNodePart(tableKey(mapping.getOdsSchema(), mapping.getOdsTable())),
            256
        );
    }

    private String safeNodePart(String value) {
        if (!StringUtils.hasText(value)) {
            return "_";
        }
        return value.trim().replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength));
    }

    private String defaultString(Object value, String fallback) {
        String text = stringValue(value);
        return StringUtils.hasText(text) ? text : fallback;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Map<String, Object> payloadMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, item) -> {
                if (key != null) {
                    result.put(String.valueOf(key), item);
                }
            });
            return result;
        }
        return Map.of();
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private record GraphExpansion(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {}

    @PostMapping
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> create(@Valid @RequestBody LineageCreateRequest body) {
        if (body == null || body.upstreamDatasetId == null || body.downstreamDatasetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upstreamDatasetId/downstreamDatasetId required");
        }
        if (body.upstreamDatasetId.equals(body.downstreamDatasetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "upstream and downstream cannot be the same");
        }
        // Ensure datasets exist (permission is handled on read; maintenance is role-based).
        datasetRepo.findById(body.upstreamDatasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "upstream dataset not found"));
        datasetRepo.findById(body.downstreamDatasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "downstream dataset not found"));

        String relationType = trimToNull(body.relationType);
        String effectiveRelationType = relationType != null ? relationType : "MANUAL";
        CatalogDatasetLineage existingLink = lineageRepo
            .findFirstByUpstreamDatasetIdAndDownstreamDatasetIdAndRelationTypeIgnoreCaseAndValidToIsNull(
                body.upstreamDatasetId,
                body.downstreamDatasetId,
                effectiveRelationType
            )
            .orElse(null);
        if (existingLink != null) {
            existingLink.setRelationType(effectiveRelationType);
            if (StringUtils.hasText(body.notes)) {
                existingLink.setNotes(body.notes.trim());
            }
            if (StringUtils.hasText(body.upstreamAssetType)) {
                existingLink.setUpstreamAssetType(body.upstreamAssetType.trim());
            }
            if (StringUtils.hasText(body.downstreamAssetType)) {
                existingLink.setDownstreamAssetType(body.downstreamAssetType.trim());
            }
            existingLink.setDirection(normalizeDirection(body.direction));
            if (StringUtils.hasText(body.projectName)) {
                existingLink.setProjectName(body.projectName.trim());
            }
            CatalogDatasetLineage saved = lineageRepo.save(existingLink);
            audit.auditAction("CATALOG_LINEAGE_EDIT", AuditStage.SUCCESS, saved.getId().toString(), Map.of("summary", "更新血缘关系"));
            return ApiResponses.ok(Map.of("id", saved.getId().toString(), "updated", true));
        }

        CatalogDatasetLineage createdLink = new CatalogDatasetLineage();
        createdLink.setUpstreamDatasetId(body.upstreamDatasetId);
        createdLink.setDownstreamDatasetId(body.downstreamDatasetId);
        createdLink.setRelationType(effectiveRelationType);
        createdLink.setNotes(trimToNull(body.notes));
        createdLink.setUpstreamAssetType(trimToNull(body.upstreamAssetType));
        createdLink.setDownstreamAssetType(trimToNull(body.downstreamAssetType));
        createdLink.setDirection(normalizeDirection(body.direction));
        createdLink.setProjectName(trimToNull(body.projectName));
        createdLink.setVerificationStatus("DECLARED");
        createdLink.setValidFrom(Instant.now());
        CatalogDatasetLineage saved = lineageRepo.save(createdLink);
        audit.auditAction("CATALOG_LINEAGE_EDIT", AuditStage.SUCCESS, saved.getId().toString(), Map.of("summary", "新增血缘关系"));
        return ApiResponses.ok(Map.of("id", saved.getId().toString(), "created", true));
    }

    @PostMapping("/sync-addax")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<IngestionLineageWriter.LineageWriteResult> syncAddaxLineage() {
        IngestionLineageWriter.LineageWriteResult result = ingestionLineageWriter.syncEnabledOdsMappings();
        audit.auditAction(
            "LINEAGE_INGEST_WRITE",
            AuditStage.SUCCESS,
            "ADDAX",
            Map.of("summary", "手动同步 Addax 入湖血缘", "created", result.created(), "updated", result.updated(), "skipped", result.skipped())
        );
        return ApiResponses.ok(result);
    }

    @PostMapping("/ingestion-executions")
    @PreAuthorize("(" + CATALOG_MAINTAINER_EXPRESSION + ") or (" + INGESTION_SERVICE_EXPRESSION + ")")
    public ApiResponse<OdsTableMappingSyncService.SyncResult> syncIngestionExecutionLineage(@RequestBody Map<String, Object> payload) {
        Map<String, Object> execution = payloadMap(payload == null ? null : payload.get("execution"));
        String status = stringValue(execution.get("status"));
        String executionId = defaultString(execution.get("id"), defaultString(execution.get("executionId"), "unknown"));
        String batchId = stringValue(execution.get("batchId"));
        Instant observedAt = parseInstant(defaultString(execution.get("endTime"), stringValue(execution.get("startTime"))));
        IngestionLineageWriter.LineageObservation observation = IngestionLineageWriter.LineageObservation.fromExecution(
            status,
            executionId,
            batchId,
            observedAt
        );
        OdsTableMappingSyncService.SyncResult result = odsTableMappingSyncService.syncFromIngestionPayload(payload, observation);
        audit.auditAction(
            "LINEAGE_INGEST_EXECUTION_SYNC",
            AuditStage.SUCCESS,
            executionId,
            Map.of(
                "summary",
                "入湖执行后回写 Addax 血缘状态",
                "executionId",
                executionId,
                "status",
                defaultString(status, "unknown"),
                "verificationStatus",
                observation.verificationStatus(),
                "tables",
                result.tables(),
                "synced",
                result.synced()
            )
        );
        return ApiResponses.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable UUID id, @RequestParam(name = "force", required = false, defaultValue = "false") boolean force) {
        CatalogDatasetLineage link = lineageRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "lineage not found"));
        if (!force && isAutoRelationType(link.getRelationType())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "AUTO_* lineage is generated by sync and will be rebuilt; use force=true only when you really want to remove it"
            );
        }
        link.setValidTo(Instant.now());
        lineageRepo.save(link);
        audit.auditAction("CATALOG_LINEAGE_DELETE", AuditStage.SUCCESS, id.toString(), Map.of("summary", force ? "关闭血缘关系" : "关闭血缘关系"));
        return ApiResponses.ok(Boolean.TRUE);
    }

    private Map<String, Object> toEdgeDto(CatalogDatasetLineage link, String effDept) {
        if (link == null || link.getId() == null) return null;
        UUID upstreamId = link.getUpstreamDatasetId();
        UUID downstreamId = link.getDownstreamDatasetId();
        CatalogDataset upstream = upstreamId != null ? datasetRepo.findById(upstreamId).orElse(null) : null;
        CatalogDataset downstream = downstreamId != null ? datasetRepo.findById(downstreamId).orElse(null) : null;

        // Avoid leaking names for inaccessible datasets.
        if (upstream != null && (!accessChecker.canRead(upstream) || !accessChecker.departmentAllowed(upstream, effDept))) {
            upstream = null;
        }
        if (downstream != null && (!accessChecker.canRead(downstream) || !accessChecker.departmentAllowed(downstream, effDept))) {
            downstream = null;
        }

        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", link.getId().toString());
        dto.put("relationType", link.getRelationType());
        dto.put("notes", link.getNotes());
        dto.put("upstreamDatasetId", upstreamId != null ? upstreamId.toString() : null);
        dto.put("downstreamDatasetId", downstreamId != null ? downstreamId.toString() : null);
        dto.put("upstreamName", upstream != null ? upstream.getName() : null);
        dto.put("downstreamName", downstream != null ? downstream.getName() : null);
        dto.put("upstreamLayer", upstream != null ? upstream.getWarehouseLayer() : null);
        dto.put("downstreamLayer", downstream != null ? downstream.getWarehouseLayer() : null);
        dto.put("upstreamOwner", upstream != null ? upstream.getOwner() : null);
        dto.put("downstreamOwner", downstream != null ? downstream.getOwner() : null);
        dto.put("upstreamSourceId", upstream != null && upstream.getSourceId() != null ? upstream.getSourceId().toString() : null);
        dto.put("downstreamSourceId", downstream != null && downstream.getSourceId() != null ? downstream.getSourceId().toString() : null);
        dto.put("upstreamLastModifiedAt", upstream != null ? upstream.getLastModifiedDate() : null);
        dto.put("downstreamLastModifiedAt", downstream != null ? downstream.getLastModifiedDate() : null);
        dto.put("upstreamAssetType", link.getUpstreamAssetType());
        dto.put("downstreamAssetType", link.getDownstreamAssetType());
        dto.put("direction", link.getDirection());
        dto.put("projectName", link.getProjectName());
        dto.put("lineageJobId", link.getLineageJobId() != null ? link.getLineageJobId().toString() : null);
        dto.put("verificationStatus", edgeVerificationStatus(link.getVerificationStatus()));
        dto.put("lastExecutionId", link.getLastExecutionId());
        dto.put("lastExecutionStatus", link.getLastExecutionStatus());
        dto.put("lastObservedAt", link.getLastObservedAt());
        dto.put("lastVerifiedAt", link.getLastVerifiedAt());
        dto.put("validFrom", link.getValidFrom());
        dto.put("validTo", link.getValidTo());
        dto.put("lastModifiedAt", link.getLastModifiedDate());
        return dto;
    }

    private String edgeKey(Map<String, Object> edge) {
        return (
            defaultString(edge.get("upstreamDatasetId"), "_") + "|" +
            defaultString(edge.get("downstreamDatasetId"), "_") + "|" +
            defaultString(edge.get("relationType"), "UNKNOWN").toUpperCase(Locale.ROOT)
        );
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isAutoRelationType(String relationType) {
        if (!StringUtils.hasText(relationType)) {
            return false;
        }
        return relationType.trim().toUpperCase(Locale.ROOT).startsWith("AUTO_");
    }

    private String normalizeDirection(String direction) {
        String text = trimToNull(direction);
        if (text == null) {
            return "UPSTREAM_TO_DOWNSTREAM";
        }
        return text.toUpperCase(Locale.ROOT);
    }

    private boolean matchProject(CatalogDatasetLineage link, String projectName) {
        String filter = trimToNull(projectName);
        if (filter == null) {
            return true;
        }
        String edgeProject = trimToNull(link != null ? link.getProjectName() : null);
        if (edgeProject == null) {
            return false;
        }
        return edgeProject.equalsIgnoreCase(filter);
    }

    private Set<String> parseLayerFilters(String layers) {
        String raw = trimToNull(layers);
        if (raw == null) {
            return Set.of();
        }
        return java.util.Arrays
            .stream(raw.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Instant resolveChangedSince(Integer changedWithinHours) {
        int hours = normalizeChangedWindow(changedWithinHours);
        if (hours <= 0) {
            return null;
        }
        return Instant.now().minusSeconds((long) hours * 3600L);
    }

    private Instant resolveSnapshotAt(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return Instant.now();
        }
        try {
            return Instant.parse(text);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ISO-8601 instant: " + text);
        }
    }

    private int normalizeChangedWindow(Integer changedWithinHours) {
        if (changedWithinHours == null || changedWithinHours <= 0) {
            return 0;
        }
        return Math.min(changedWithinHours, 24 * 365);
    }

    private boolean matchChanged(Instant value, Instant changedSince) {
        if (changedSince == null) {
            return true;
        }
        return value != null && !value.isBefore(changedSince);
    }

    private boolean matchLayer(CatalogDataset dataset, Set<String> layers) {
        if (layers == null || layers.isEmpty()) {
            return true;
        }
        String layer = normalizeLayer(dataset != null ? dataset.getWarehouseLayer() : null);
        return layers.contains(layer);
    }

    private String normalizeLayer(String layer) {
        if (!StringUtils.hasText(layer)) {
            return "UNKNOWN";
        }
        return layer.trim().toUpperCase(Locale.ROOT);
    }

    private boolean matchSource(CatalogDataset dataset, UUID sourceId) {
        if (sourceId == null) {
            return true;
        }
        if (dataset == null || dataset.getSourceId() == null) {
            return false;
        }
        return sourceId.equals(dataset.getSourceId());
    }

    private boolean matchLineageFilters(
        CatalogDatasetLineage link,
        UUID rootId,
        Set<String> layerFilters,
        UUID sourceId,
        Instant changedSince,
        String effDept
    ) {
        if (link == null) {
            return false;
        }
        CatalogDataset upstream = link.getUpstreamDatasetId() != null ? datasetRepo.findById(link.getUpstreamDatasetId()).orElse(null) : null;
        CatalogDataset downstream = link.getDownstreamDatasetId() != null ? datasetRepo.findById(link.getDownstreamDatasetId()).orElse(null) : null;
        if (upstream != null && (!accessChecker.canRead(upstream) || !accessChecker.departmentAllowed(upstream, effDept))) {
            upstream = null;
        }
        if (downstream != null && (!accessChecker.canRead(downstream) || !accessChecker.departmentAllowed(downstream, effDept))) {
            downstream = null;
        }
        CatalogDataset peer = rootId.equals(link.getDownstreamDatasetId()) ? upstream : downstream;
        if (peer == null) {
            return false;
        }
        if (!matchLayer(peer, layerFilters) || !matchSource(peer, sourceId)) {
            return false;
        }
        if (matchChanged(link.getLastModifiedDate(), changedSince)) {
            return true;
        }
        return matchChanged(peer.getLastModifiedDate(), changedSince);
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        if (text == null) return null;
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(raw);
            if (length > 0) {
                Object first = java.lang.reflect.Array.get(raw, 0);
                if (first != null) return first;
            }
        }
        return raw;
    }
}
