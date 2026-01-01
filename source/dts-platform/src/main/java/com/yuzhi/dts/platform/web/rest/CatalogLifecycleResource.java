package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/catalog/lifecycle")
@Transactional(readOnly = true)
public class CatalogLifecycleResource {

    private static final String CATALOG_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;

    public CatalogLifecycleResource(CatalogDatasetRepository datasetRepo, AccessChecker accessChecker, AuditService audit) {
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
    }

    /**
     * 到期提醒（轻量）：列出 expiresAt 即将到期的数据资产。
     * - 仅做查询与审计；归档/销毁流程暂通过数据集字段 lifecycleStatus/expiresAt 手工维护。
     */
    @GetMapping("/expiring")
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> expiring(
        @RequestParam(name = "days", required = false, defaultValue = "30") int days,
        @RequestParam(name = "includeExpired", required = false, defaultValue = "true") boolean includeExpired,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        int safeDays = Math.max(0, Math.min(days, 3650));
        int safeSize = Math.max(1, Math.min(size, 200));
        int safePage = Math.max(0, page);

        String effDept = activeDept != null ? activeDept : claim("dept_code");
        if (!StringUtils.hasText(effDept)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无法识别当前用户部门信息");
        }

        Instant now = Instant.now();
        Instant deadline = now.plus(safeDays, ChronoUnit.DAYS);

        List<CatalogDataset> candidates = datasetRepo
            .findAll()
            .stream()
            .filter(ds -> ds.getExpiresAt() != null)
            .filter(ds -> includeExpired ? !ds.getExpiresAt().isAfter(deadline) : ds.getExpiresAt().isAfter(now) && !ds.getExpiresAt().isAfter(deadline))
            .filter(accessChecker::canRead)
            .filter(ds -> accessChecker.departmentAllowed(ds, effDept))
            .sorted(Comparator.comparing(CatalogDataset::getExpiresAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();

        long total = candidates.size();
        int offset = safePage * safeSize;
        int end = Math.min(candidates.size(), offset + safeSize);
        List<Map<String, Object>> content = offset >= candidates.size()
            ? List.of()
            : candidates.subList(offset, end).stream().map(this::toDto).toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("total", total);
        payload.put("page", safePage);
        payload.put("size", safeSize);
        payload.put("days", safeDays);
        payload.put("includeExpired", includeExpired);

        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "查看数据资产到期提醒列表");
        auditPayload.put("days", safeDays);
        auditPayload.put("includeExpired", includeExpired);
        auditPayload.put("page", safePage);
        auditPayload.put("size", safeSize);
        auditPayload.put("returned", content.size());
        audit.auditAction("CATALOG_LIFECYCLE_EXPIRING_LIST", AuditStage.SUCCESS, "days=" + safeDays, auditPayload);

        return ApiResponses.ok(payload);
    }

    private Map<String, Object> toDto(CatalogDataset dataset) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (dataset == null) {
            return m;
        }
        UUID id = dataset.getId();
        if (id != null) {
            m.put("id", id.toString());
        }
        m.put("name", dataset.getName());
        m.put("ownerDept", dataset.getOwnerDept());
        m.put("owner", dataset.getOwner());
        m.put("classification", dataset.getClassification());
        m.put("enabled", dataset.getEnabled());
        m.put("lifecycleStatus", dataset.getLifecycleStatus());
        m.put("retentionDays", dataset.getRetentionDays());
        m.put("expiresAt", dataset.getExpiresAt());
        if (dataset.getDomain() != null && dataset.getDomain().getId() != null) {
            m.put("domainId", dataset.getDomain().getId().toString());
            m.put("domainName", dataset.getDomain().getName());
        }
        m.put("type", dataset.getType());
        m.put("hiveDatabase", dataset.getHiveDatabase());
        m.put("hiveTable", dataset.getHiveTable());
        return m;
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

