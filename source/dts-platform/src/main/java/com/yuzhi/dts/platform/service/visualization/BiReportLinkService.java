package com.yuzhi.dts.platform.service.visualization;

import com.yuzhi.dts.platform.domain.visualization.BiReportLink;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkDto;
import com.yuzhi.dts.platform.service.visualization.dto.BiReportLinkRequest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class BiReportLinkService {

    private final BiReportLinkRepository repo;
    private final ClassificationUtils classificationUtils;

    public BiReportLinkService(BiReportLinkRepository repo, ClassificationUtils classificationUtils) {
        this.repo = repo;
        this.classificationUtils = classificationUtils;
    }

    @Transactional(readOnly = true)
    public List<BiReportLinkDto> listPublished(String deptCode, String reportType, String keyword) {
        List<BiReportLink> all = repo.findByEnabledTrueOrderBySortOrderAscLastModifiedDateDesc();

        String dept = trimToNull(deptCode);
        String type = trimToNull(reportType);
        String kw = trimToNull(keyword);
        String userDept = currentClaim("dept_code");
        Set<String> userRoles = currentAuthorities();

        boolean institutePrivileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);

        List<BiReportLinkDto> out = new ArrayList<>();
        for (BiReportLink r : all) {
            if (r == null) continue;
            if (!classificationUtils.canAccess(r.getClassification())) continue;

            if (!matchRole(userRoles, r.getRoleCodes())) continue;
            if (!matchDept(userDept, r.getDeptCodes(), institutePrivileged)) continue;
            if (!matchDeptFilter(dept, r.getDeptCodes())) continue;
            if (!matchType(type, r.getReportType())) continue;
            if (!matchKeyword(kw, r.getTitle(), r.getCode())) continue;

            out.add(toDto(r));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<BiReportLinkDto> listAll(String deptCode, String reportType, String keyword, Boolean enabledOnly) {
        String dept = trimToNull(deptCode);
        String type = trimToNull(reportType);
        String kw = trimToNull(keyword);
        boolean onlyEnabled = Boolean.TRUE.equals(enabledOnly);

        List<BiReportLinkDto> out = new ArrayList<>();
        for (BiReportLink r : repo.findAll()) {
            if (r == null) continue;
            if (onlyEnabled && !r.isEnabled()) continue;
            if (!matchDeptFilter(dept, r.getDeptCodes())) continue;
            if (!matchType(type, r.getReportType())) continue;
            if (!matchKeyword(kw, r.getTitle(), r.getCode())) continue;
            out.add(toDto(r));
        }
        out.sort(
            java.util.Comparator
                .comparing((BiReportLinkDto d) -> Optional.ofNullable(d.updatedAt()).orElse(Instant.EPOCH))
                .reversed()
        );
        return out;
    }

    public BiReportLinkDto create(BiReportLinkRequest req) {
        BiReportLink link = new BiReportLink();
        apply(link, req, true);
        // Best-effort unique enforcement before DB constraint
        repo.findFirstByCodeIgnoreCase(link.getCode()).ifPresent(existing -> {
            throw new IllegalArgumentException("code already exists");
        });
        return toDto(repo.save(link));
    }

    public BiReportLinkDto update(UUID id, BiReportLinkRequest req) {
        BiReportLink link = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("not_found"));
        apply(link, req, false);
        repo.findFirstByCodeIgnoreCase(link.getCode()).ifPresent(existing -> {
            if (existing.getId() != null && !existing.getId().equals(id)) {
                throw new IllegalArgumentException("code already exists");
            }
        });
        return toDto(repo.save(link));
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

    private void apply(BiReportLink target, BiReportLinkRequest req, boolean creating) {
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
        if (req.getEnabled() != null) {
            target.setEnabled(Boolean.TRUE.equals(req.getEnabled()));
        } else if (creating) {
            target.setEnabled(true);
        }
        if (req.getSortOrder() != null) {
            target.setSortOrder(req.getSortOrder());
        }
    }

    private BiReportLinkDto toDto(BiReportLink r) {
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
            r.getCreatedBy(),
            r.getLastModifiedDate()
        );
    }

    private boolean matchKeyword(String kw, String title, String code) {
        if (!StringUtils.hasText(kw)) return true;
        String needle = kw.trim().toLowerCase(Locale.ROOT);
        return (
            (title != null && title.toLowerCase(Locale.ROOT).contains(needle)) ||
            (code != null && code.toLowerCase(Locale.ROOT).contains(needle))
        );
    }

    private boolean matchType(String typeFilter, String reportType) {
        if (!StringUtils.hasText(typeFilter)) return true;
        if (!StringUtils.hasText(reportType)) return false;
        return reportType.trim().equalsIgnoreCase(typeFilter.trim());
    }

    private boolean matchDeptFilter(String deptFilter, String deptCodesCsv) {
        if (!StringUtils.hasText(deptFilter)) return true;
        // When filtering by dept, include global reports (no dept codes) as well.
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

    private Set<String> currentAuthorities() {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication();
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
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = java.lang.reflect.Array.get(raw, i);
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
