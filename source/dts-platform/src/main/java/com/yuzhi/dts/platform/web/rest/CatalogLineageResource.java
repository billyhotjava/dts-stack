package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.validation.Valid;
import java.time.Instant;
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

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogDatasetLineageRepository lineageRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogLineageResource(
        CatalogDatasetRepository datasetRepo,
        CatalogDatasetLineageRepository lineageRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.lineageRepo = lineageRepo;
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
            .findByEitherSide(datasetId)
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
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset root = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(root) || !accessChecker.departmentAllowed(root, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }

        Set<String> layerFilters = parseLayerFilters(layers);
        Instant changedSince = resolveChangedSince(changedWithinHours);
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
                List<CatalogDatasetLineage> direct = lineageRepo.findByEitherSide(current);
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
        List<Map<String, Object>> edgeDtos = edges
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
        payload.put("nodeCount", filteredNodes.size());
        payload.put("edgeCount", edgeDtos.size());
        payload.put("impactStats", Map.of("layerNodeCounts", layerStats, "relationTypeCounts", relationStats, "changedNodeCount", changedNodeCount));
        payload.put(
            "nodes",
            filteredNodes.values().stream().map(ds -> {
                Map<String, Object> dto = new LinkedHashMap<>();
                dto.put("id", ds.getId() != null ? ds.getId().toString() : null);
                dto.put("name", ds.getName());
                dto.put("db", ds.getHiveDatabase());
                dto.put("table", ds.getHiveTable());
                dto.put("type", ds.getType());
                dto.put("layer", ds.getWarehouseLayer());
                dto.put("ownerDept", ds.getOwnerDept());
                dto.put("owner", ds.getOwner());
                dto.put("sourceId", ds.getSourceId() != null ? ds.getSourceId().toString() : null);
                dto.put("lastModifiedAt", ds.getLastModifiedDate());
                dto.put("snapshotTime", ds.getSnapshotTime());
                return dto;
            }).toList()
        );
        payload.put("edges", edgeDtos);
        audit.auditAction(
            "CATALOG_LINEAGE_IMPACT_VIEW",
            AuditStage.SUCCESS,
            datasetId.toString(),
            Map.of("summary", "查看影响分析", "direction", dir, "depth", safeDepth)
        );
        return ApiResponses.ok(payload);
    }

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

        CatalogDatasetLineage existingLink = lineageRepo
            .findFirstByUpstreamDatasetIdAndDownstreamDatasetId(body.upstreamDatasetId, body.downstreamDatasetId)
            .orElse(null);
        if (existingLink != null) {
            if (StringUtils.hasText(body.relationType)) {
                existingLink.setRelationType(body.relationType.trim());
            }
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
        String relationType = trimToNull(body.relationType);
        createdLink.setRelationType(relationType != null ? relationType : "MANUAL");
        createdLink.setNotes(trimToNull(body.notes));
        createdLink.setUpstreamAssetType(trimToNull(body.upstreamAssetType));
        createdLink.setDownstreamAssetType(trimToNull(body.downstreamAssetType));
        createdLink.setDirection(normalizeDirection(body.direction));
        createdLink.setProjectName(trimToNull(body.projectName));
        CatalogDatasetLineage saved = lineageRepo.save(createdLink);
        audit.auditAction("CATALOG_LINEAGE_EDIT", AuditStage.SUCCESS, saved.getId().toString(), Map.of("summary", "新增血缘关系"));
        return ApiResponses.ok(Map.of("id", saved.getId().toString(), "created", true));
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
        lineageRepo.delete(link);
        audit.auditAction("CATALOG_LINEAGE_DELETE", AuditStage.SUCCESS, id.toString(), Map.of("summary", force ? "强制删除血缘关系" : "删除血缘关系"));
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
        dto.put("lastModifiedAt", link.getLastModifiedDate());
        return dto;
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
