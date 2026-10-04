package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog/datasets")
@Transactional(readOnly = true)
public class CatalogDatasetUsageResource {

    private final CatalogDatasetRepository datasetRepo;
    private final QueryExecutionRepository executionRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogDatasetUsageResource(
        CatalogDatasetRepository datasetRepo,
        QueryExecutionRepository executionRepo,
        AccessChecker accessChecker,
        AuditService audit
    ) {
        this.datasetRepo = datasetRepo;
        this.executionRepo = executionRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    @GetMapping("/{id}/usage")
    public ApiResponse<Map<String, Object>> usage(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = datasetRepo.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问"));
        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在或无权访问");
        }

        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", id.toString());
        payload.put("datasetName", dataset.getName());
        payload.put("activeDept", effDept);
        payload.put("all", usageBlock(id, null));
        payload.put("last7d", usageBlock(id, now.minus(Duration.ofDays(7))));
        payload.put("last30d", usageBlock(id, now.minus(Duration.ofDays(30))));

        audit.auditAction(
            "CATALOG_ASSET_USAGE_VIEW",
            AuditStage.SUCCESS,
            id.toString(),
            Map.of("summary", "查看数据资产使用统计", "datasetId", id.toString(), "datasetName", dataset.getName())
        );
        return ApiResponses.ok(payload);
    }

    private Map<String, Object> usageBlock(UUID datasetId, Instant since) {
        Object[] agg = executionRepo.aggregateUsage(datasetId, since);
        long total = longAt(agg, 0);
        long success = longAt(agg, 1);
        long failed = longAt(agg, 2);
        Object last = agg != null && agg.length > 3 ? agg[3] : null;

        Map<String, Object> out = new LinkedHashMap<>();
        if (since != null) {
            out.put("since", since.toString());
        }
        out.put("total", total);
        out.put("success", success);
        out.put("failed", failed);
        if (last != null) {
            out.put("lastExecutedAt", last);
        }
        return out;
    }

    private long longAt(Object[] arr, int idx) {
        if (arr == null || idx < 0 || idx >= arr.length) return 0L;
        Object v = arr[idx];
        if (v == null) return 0L;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return firstTextValue(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return firstTextValue(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String firstTextValue(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> c) {
            return c.stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object e = java.lang.reflect.Array.get(raw, i);
                if (e != null) return e;
            }
            return null;
        }
        return raw;
    }
}
