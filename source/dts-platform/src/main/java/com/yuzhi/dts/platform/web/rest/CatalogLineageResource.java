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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

        Optional<CatalogDatasetLineage> existing = lineageRepo.findFirstByUpstreamDatasetIdAndDownstreamDatasetId(body.upstreamDatasetId, body.downstreamDatasetId);
        if (existing.isPresent()) {
            CatalogDatasetLineage link = existing.get();
            if (StringUtils.hasText(body.relationType)) {
                link.setRelationType(body.relationType.trim());
            }
            if (StringUtils.hasText(body.notes)) {
                link.setNotes(body.notes.trim());
            }
            CatalogDatasetLineage saved = lineageRepo.save(link);
            audit.audit("UPDATE", "catalog.lineage", saved.getId().toString());
            return ApiResponses.ok(Map.of("id", saved.getId().toString(), "updated", true));
        }

        CatalogDatasetLineage link = new CatalogDatasetLineage();
        link.setUpstreamDatasetId(body.upstreamDatasetId);
        link.setDownstreamDatasetId(body.downstreamDatasetId);
        link.setRelationType(trimToNull(body.relationType));
        link.setNotes(trimToNull(body.notes));
        CatalogDatasetLineage saved = lineageRepo.save(link);
        audit.audit("CREATE", "catalog.lineage", saved.getId().toString());
        return ApiResponses.ok(Map.of("id", saved.getId().toString(), "created", true));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> delete(@PathVariable UUID id) {
        lineageRepo.deleteById(id);
        audit.audit("DELETE", "catalog.lineage", id.toString());
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
