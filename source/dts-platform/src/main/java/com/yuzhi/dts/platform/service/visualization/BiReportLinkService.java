package com.yuzhi.dts.platform.service.visualization;

import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.domain.visualization.BiReportVisit;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportVisitRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkRequest;
import java.lang.reflect.Array;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class BiReportLinkService {

    private final BiReportLinkRepository repo;
    private final BiReportVisitRepository visitRepo;
    private final QueryDatasetAssetRepository queryDatasetAssetRepository;
    private final ClassificationUtils classificationUtils;

    public BiReportLinkService(
        BiReportLinkRepository repo,
        BiReportVisitRepository visitRepo,
        QueryDatasetAssetRepository queryDatasetAssetRepository,
        ClassificationUtils classificationUtils
    ) {
        this.repo = repo;
        this.visitRepo = visitRepo;
        this.queryDatasetAssetRepository = queryDatasetAssetRepository;
        this.classificationUtils = classificationUtils;
    }

    @Transactional(readOnly = true)
    public List<BiReportLinkDto> listPublished(
        String deptCode,
        String reportType,
        String keyword,
        String activeDeptHeader,
        UUID queryDatasetId,
        String bizDomain
    ) {
        String dept = trimToNull(deptCode);
        String type = trimToNull(reportType);
        String kw = trimToNull(keyword);
        String biz = trimToNull(bizDomain);
        String queryDept = resolveActiveDept(activeDeptHeader);
        String userDept = currentClaim("dept_code");
        String effectiveDept = StringUtils.hasText(queryDept) ? queryDept : userDept;
        Set<String> userRoles = currentAuthorities();

        boolean institutePrivileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        boolean superAdmin = hasGlobalManageScope();
        Instant now = Instant.now();

        // P0-10: enabled / queryDatasetId / bizDomain / reportType /
        // deptCode-CSV / expiry are pushed to SQL via findCandidatesForListing.
        // Identity-bound filters (classification clearance, role match,
        // dataset visibility, free-text keyword) remain in memory.
        List<BiReportLink> rows = repo.findCandidatesForListing(true, queryDatasetId, type, biz, dept, now);
        Map<UUID, QueryDatasetAsset> datasetCache = loadDatasetCache(rows);

        List<BiReportLinkDto> out = new ArrayList<>();
        for (BiReportLink r : rows) {
            if (r == null) continue;
            if (!classificationUtils.canAccess(r.getClassification())) continue;
            if (!matchRole(userRoles, r.getRoleCodes())) continue;
            if (!matchDept(userDept, r.getDeptCodes(), institutePrivileged)) continue;
            if (!matchKeyword(kw, r.getTitle(), r.getCode())) continue;

            QueryDatasetAsset dataset = r.getQueryDatasetId() == null ? null : datasetCache.get(r.getQueryDatasetId());
            if (!datasetVisibleInScope(dataset, effectiveDept, superAdmin)) continue;
            out.add(toDto(r, dataset != null ? trimToNull(dataset.getName()) : null));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<BiReportLinkDto> listAll(
        String deptCode,
        String reportType,
        String keyword,
        Boolean enabledOnly,
        String activeDeptHeader,
        UUID queryDatasetId,
        String bizDomain
    ) {
        String dept = trimToNull(deptCode);
        String type = trimToNull(reportType);
        String kw = trimToNull(keyword);
        String biz = trimToNull(bizDomain);
        boolean onlyEnabled = Boolean.TRUE.equals(enabledOnly);
        String activeDept = resolveActiveDept(activeDeptHeader);
        boolean superAdmin = hasGlobalManageScope();
        Instant now = Instant.now();

        // P0-10: same SQL-pushdown idea as listPublished. The admin-listing
        // form may include disabled rows when {@code enabledOnly=false}.
        List<BiReportLink> rows = repo.findCandidatesForListing(onlyEnabled, queryDatasetId, type, biz, dept, now);
        Map<UUID, QueryDatasetAsset> datasetCache = loadDatasetCache(rows);

        List<BiReportLinkDto> out = new ArrayList<>();
        for (BiReportLink r : rows) {
            if (r == null) continue;
            if (!matchKeyword(kw, r.getTitle(), r.getCode())) continue;

            QueryDatasetAsset dataset = r.getQueryDatasetId() == null ? null : datasetCache.get(r.getQueryDatasetId());
            if (!datasetVisibleInScope(dataset, activeDept, superAdmin)) continue;
            out.add(toDto(r, dataset != null ? trimToNull(dataset.getName()) : null));
        }
        out.sort(
            java.util.Comparator
                .comparing((BiReportLinkDto d) -> Optional.ofNullable(d.updatedAt()).orElse(Instant.EPOCH))
                .reversed()
        );
        return out;
    }

    public BiReportLinkDto create(BiReportLinkRequest req, String activeDeptHeader) {
        BiReportLink link = new BiReportLink();
        apply(link, req, true, activeDeptHeader);
        repo.findFirstByCodeIgnoreCase(link.getCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("code already exists");
        });
        return toDto(repo.save(link), resolveDatasetName(link.getQueryDatasetId()));
    }

    public BiReportLinkDto update(UUID id, BiReportLinkRequest req, String activeDeptHeader) {
        BiReportLink link = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not_found"));
        apply(link, req, false, activeDeptHeader);
        repo.findFirstByCodeIgnoreCase(link.getCode()).ifPresent(existing -> {
            if (existing.getId() != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("code already exists");
            }
        });
        return toDto(repo.save(link), resolveDatasetName(link.getQueryDatasetId()));
    }

    public void delete(UUID id) {
        BiReportLink link = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not_found"));
        link.setEnabled(false);
        repo.save(link);
    }

    public void purge(UUID id) {
        BiReportLink link = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not_found"));
        repo.delete(link);
    }

    // Always own its tx so a readOnly caller cannot block the visit-log write.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchVisit(UUID id, String code) {
        BiReportLink link = null;
        if (id != null) {
            link = repo.findById(id).orElse(null);
        }
        if (link == null && StringUtils.hasText(code)) {
            link = repo.findFirstByCodeIgnoreCase(code.trim()).orElse(null);
        }
        if (link == null) {
            return;
        }
        Instant now = Instant.now();
        link.setLastVisitedAt(now);
        repo.save(link);

        // Append a per-visit log row so leader-overview aggregations can
        // compute visits-in-period KPIs, top-N by count, and the domain
        // matrix (Sprint-15 F1).
        BiReportVisit visit = new BiReportVisit();
        visit.setReportId(link.getId());
        visit.setUserLogin(SecurityUtils.getCurrentUserLogin().orElse(null));
        visit.setDeptCode(SecurityUtils.getCurrentUserDept().orElse(null));
        visit.setBizDomain(link.getBizDomain());
        visit.setVisitedAt(now);
        visitRepo.save(visit);
    }

    private void apply(BiReportLink target, BiReportLinkRequest req, boolean creating, String activeDeptHeader) {
        String code = normalizeCode(req.getCode());
        String title = trimToNull(req.getTitle());
        String url = trimToNull(req.getUrl());
        String engine = trimToNull(req.getEngine());
        String classification = trimToNull(req.getClassification());
        if (!StringUtils.hasText(code) || !StringUtils.hasText(title) || !StringUtils.hasText(url) || !StringUtils.hasText(classification)) {
            throw new IllegalArgumentException("missing required fields");
        }
        target.setCode(code);
        target.setTitle(title);
        target.setUrl(url);
        target.setEngine(StringUtils.hasText(engine) ? engine.trim().toUpperCase(Locale.ROOT) : "HETU");
        target.setClassification(classification.trim().toUpperCase(Locale.ROOT));
        target.setReportType(normalizeType(req.getReportType()));
        target.setDeptCodes(joinCodes(req.getDeptCodes()));
        target.setRoleCodes(joinRoles(req.getRoleCodes()));
        target.setQueryDatasetId(resolveDatasetId(req.getQueryDatasetId(), activeDeptHeader));
        target.setQueryDatasetVersion(req.getQueryDatasetVersion());
        target.setExpiresAt(parseInstant(req.getExpiresAt()));
        target.setBizDomain(trimToNull(req.getBizDomain()));
        if (req.getEnabled() != null) {
            target.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        } else if (creating) {
            target.setEnabled(true);
        }
        if (req.getSortOrder() != null) {
            target.setSortOrder(req.getSortOrder());
        }
    }

    private BiReportLinkDto toDto(BiReportLink r, String datasetName) {
        return new BiReportLinkDto(
            r.getId(),
            r.getCode(),
            r.getTitle(),
            r.getEngine(),
            r.getReportType(),
            splitCodes(r.getDeptCodes()),
            splitCodes(r.getRoleCodes()),
            r.getClassification(),
            r.getUrl(),
            r.isEnabled(),
            r.getSortOrder(),
            r.getQueryDatasetId(),
            r.getQueryDatasetVersion(),
            datasetName,
            r.getExpiresAt(),
            r.getLastVisitedAt(),
            r.getCreatedBy(),
            r.getLastModifiedDate(),
            r.getBizDomain()
        );
    }

    private String resolveDatasetName(UUID datasetId) {
        if (datasetId == null) {
            return null;
        }
        return queryDatasetAssetRepository.findById(datasetId).map(it -> trimToNull(it.getName())).orElse(null);
    }

    private Map<UUID, QueryDatasetAsset> loadDatasetCache(List<BiReportLink> rows) {
        Set<UUID> datasetIds = new LinkedHashSet<>();
        for (BiReportLink row : rows) {
            if (row != null && row.getQueryDatasetId() != null) {
                datasetIds.add(row.getQueryDatasetId());
            }
        }
        if (datasetIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, QueryDatasetAsset> cache = new LinkedHashMap<>();
        queryDatasetAssetRepository.findAllById(datasetIds).forEach(it -> {
            if (it != null && it.getId() != null) {
                cache.put(it.getId(), it);
            }
        });
        return cache;
    }

    private UUID resolveDatasetId(String raw, String activeDeptHeader) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("queryDatasetId invalid");
        }
        QueryDatasetAsset dataset = queryDatasetAssetRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("queryDatasetId not found"));
        if (!datasetVisibleInScope(dataset, resolveActiveDept(activeDeptHeader), hasGlobalManageScope())) {
            throw new IllegalArgumentException("queryDatasetId not in active scope");
        }
        return id;
    }

    private Instant parseInstant(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("expiresAt invalid");
        }
    }

    private boolean matchKeyword(String kw, String title, String code) {
        if (!StringUtils.hasText(kw)) return true;
        String needle = kw.trim().toLowerCase(Locale.ROOT);
        return (
            (title != null && title.toLowerCase(Locale.ROOT).contains(needle)) ||
            (code != null && code.toLowerCase(Locale.ROOT).contains(needle))
        );
    }

    private boolean matchBizDomain(String bizDomainFilter, String bizDomainValue) {
        if (!StringUtils.hasText(bizDomainFilter)) return true;
        if (!StringUtils.hasText(bizDomainValue)) return false;
        return bizDomainFilter.trim().equalsIgnoreCase(bizDomainValue.trim());
    }

    private boolean matchType(String typeFilter, String reportType) {
        if (!StringUtils.hasText(typeFilter)) return true;
        if (!StringUtils.hasText(reportType)) return false;
        return reportType.trim().equalsIgnoreCase(typeFilter.trim());
    }

    private boolean matchDeptFilter(String deptFilter, String deptCodesCsv) {
        if (!StringUtils.hasText(deptFilter)) return true;
        List<String> depts = splitCodes(deptCodesCsv);
        if (depts.isEmpty()) return true;
        String needle = deptFilter.trim();
        return depts.stream().anyMatch(d -> needle.equalsIgnoreCase(d));
    }

    private boolean matchDept(String userDept, String deptCodesCsv, boolean institutePrivileged) {
        List<String> depts = splitCodes(deptCodesCsv);
        if (depts.isEmpty()) return true;
        if (institutePrivileged) return true;
        if (!StringUtils.hasText(userDept)) return false;
        String u = userDept.trim();
        return depts.stream().anyMatch(d -> u.equalsIgnoreCase(d));
    }

    private boolean matchRole(Set<String> userRoles, String roleCodesCsv) {
        List<String> required = splitCodes(roleCodesCsv);
        if (required.isEmpty()) return true;
        if (userRoles == null || userRoles.isEmpty()) return false;
        for (String role : required) {
            String normalized = normalizeRole(role);
            if (normalized != null && userRoles.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasGlobalManageScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS) || SecurityUtils.isOpAdminAccount();
    }

    private boolean datasetVisibleInScope(QueryDatasetAsset dataset, String activeDept, boolean superAdmin) {
        if (dataset == null) {
            return true;
        }
        if (superAdmin) {
            return true;
        }
        String ownerDept = trimToNull(dataset.getOwnerDept());
        if (ownerDept == null) {
            return true;
        }
        if (!StringUtils.hasText(activeDept)) {
            return false;
        }
        return DepartmentUtils.matches(ownerDept, activeDept);
    }

    private Set<String> currentAuthorities() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return Set.of();
            Set<String> set = new LinkedHashSet<>();
            for (var a : auth.getAuthorities()) {
                if (a == null) continue;
                String s = a.getAuthority();
                if (!StringUtils.hasText(s)) continue;
                set.add(normalizeRole(s));
            }
            set.remove(null);
            return set;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private String currentClaim(String name) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                return toClaimText(token.getToken().getClaims().get(name));
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                return toClaimText(principal.getAttribute(name));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String resolveActiveDept(String activeDeptHeader) {
        String header = trimToNull(activeDeptHeader);
        if (header != null) {
            return header;
        }
        return currentClaim("dept_code");
    }

    private String toClaimText(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) return element;
            }
            return null;
        }
        return raw;
    }

    private List<String> splitCodes(String csv) {
        if (!StringUtils.hasText(csv)) return List.of();
        List<String> out = new ArrayList<>();
        for (String raw : csv.split(",")) {
            if (!StringUtils.hasText(raw)) continue;
            String s = raw.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (String c : codes) {
            if (!StringUtils.hasText(c)) continue;
            String s = c.trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out.isEmpty() ? null : String.join(",", out);
    }

    private String joinRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) return null;
        List<String> out = new ArrayList<>();
        for (String r : roles) {
            String n = normalizeRole(r);
            if (n != null) out.add(n);
        }
        out = out.stream().distinct().toList();
        return out.isEmpty() ? null : String.join(",", out);
    }

    private String normalizeCode(String code) {
        if (!StringUtils.hasText(code)) return null;
        return code.trim();
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) return null;
        String upper = role.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("ROLE_")) return upper;
        return "ROLE_" + upper;
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) return null;
        return type.trim();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
