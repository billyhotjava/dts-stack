package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDatasetLineage;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetLineageRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
        String notes
    ) {}

    @GetMapping
    public ApiResponse<Map<String, Object>> getLineage(
        @RequestParam UUID datasetId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }

        List<CatalogDatasetLineage> links = lineageRepo.findByEitherSide(datasetId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("upstreams", links.stream().filter(l -> datasetId.equals(l.getDownstreamDatasetId())).map(l -> toEdgeDto(l, effDept)).filter(Objects::nonNull).toList());
        payload.put("downstreams", links.stream().filter(l -> datasetId.equals(l.getUpstreamDatasetId())).map(l -> toEdgeDto(l, effDept)).filter(Objects::nonNull).toList());

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
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset root = datasetRepo.findById(datasetId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "dataset not found"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(root) || !accessChecker.departmentAllowed(root, effDept)) {
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }

        int safeDepth = Math.max(1, Math.min(depth, 10));
        String dir = org.springframework.util.StringUtils.hasText(direction) ? direction.trim().toUpperCase(java.util.Locale.ROOT) : "BOTH";
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
                    if (edge == null) {
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

        List<Map<String, Object>> edgeDtos = edges.stream().map(edge -> toEdgeDto(edge, effDept)).filter(Objects::nonNull).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("direction", dir);
        payload.put("depth", safeDepth);
        payload.put("nodeCount", nodes.size());
        payload.put("edgeCount", edgeDtos.size());
        payload.put(
            "nodes",
            nodes
                .values()
                .stream()
                .map(ds -> Map.of("id", ds.getId().toString(), "name", ds.getName(), "db", ds.getHiveDatabase(), "table", ds.getHiveTable(), "type", ds.getType()))
                .toList()
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

        CatalogDatasetLineage link = lineageRepo
            .findFirstByUpstreamDatasetIdAndDownstreamDatasetId(body.upstreamDatasetId, body.downstreamDatasetId)
            .orElse(null);
        if (link != null) {
            if (StringUtils.hasText(body.relationType)) {
                link.setRelationType(body.relationType.trim());
            }
            if (StringUtils.hasText(body.notes)) {
                link.setNotes(body.notes.trim());
            }
            CatalogDatasetLineage saved = lineageRepo.save(link);
            audit.auditAction("CATALOG_LINEAGE_EDIT", AuditStage.SUCCESS, saved.getId().toString(), Map.of("summary", "更新血缘关系"));
            return ApiResponses.ok(Map.of("id", saved.getId().toString(), "updated", true));
        }

        CatalogDatasetLineage link = new CatalogDatasetLineage();
        link.setUpstreamDatasetId(body.upstreamDatasetId);
        link.setDownstreamDatasetId(body.downstreamDatasetId);
        String relationType = trimToNull(body.relationType);
        link.setRelationType(relationType != null ? relationType : "MANUAL");
        link.setNotes(trimToNull(body.notes));
        CatalogDatasetLineage saved = lineageRepo.save(link);
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
        return relationType.trim().toUpperCase(java.util.Locale.ROOT).startsWith("AUTO_");
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
