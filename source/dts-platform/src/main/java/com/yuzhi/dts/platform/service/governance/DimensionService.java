package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.repository.governance.GovDimensionDictionaryRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.DimensionDto;
import com.yuzhi.dts.platform.service.governance.request.DimensionUpsertRequest;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class DimensionService {

    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String STATUS_DEPRECATED = "DEPRECATED";

    private final GovDimensionDictionaryRepository repository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;

    public DimensionService(
        GovDimensionDictionaryRepository repository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.repository = repository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    @Transactional(readOnly = true)
    public Page<DimensionDto> list(String keyword, String status, Pageable pageable, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        List<GovDimensionDictionary> all = repository.findAll();
        List<GovDimensionDictionary> filtered = new ArrayList<>();
        for (GovDimensionDictionary dim : all) {
            if (dim == null) continue;
            if (!keywordMatches(dim, keyword)) continue;
            if (!statusMatches(dim, status)) continue;
            if (!deptAllowed(dim, trustedActiveDept)) continue;
            if (!levelAllowed(dim)) continue;
            filtered.add(dim);
        }
        filtered.sort(
            Comparator.comparing(GovDimensionDictionary::getLastModifiedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
        );

        int total = filtered.size();
        if (pageable == null) {
            List<DimensionDto> content = filtered.stream().map(IndicatorMapper::toDto).toList();
            return new PageImpl<>(content, Pageable.unpaged(), total);
        }
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<DimensionDto> content = filtered.subList(start, end).stream().map(IndicatorMapper::toDto).toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public DimensionDto get(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, trustedActiveDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for dimension");
        }
        return IndicatorMapper.toDto(entity);
    }

    public DimensionDto create(DimensionUpsertRequest request, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = new GovDimensionDictionary();
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, null, trustedActiveDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public DimensionDto update(UUID id, DimensionUpsertRequest request, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = repository.findById(id).orElseThrow();
        requireMutationAccess(entity, trustedActiveDept);
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, trustedActiveDept);
        requireMutationAccess(entity, trustedActiveDept);
        validateUpsert(entity, id, trustedActiveDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public DimensionDto publish(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = repository.findById(id).orElseThrow();
        requireMutationAccess(entity, trustedActiveDept);
        entity.setStatus(STATUS_PUBLISHED);
        applyDefaults(entity, trustedActiveDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public DimensionDto archive(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = repository.findById(id).orElseThrow();
        requireMutationAccess(entity, trustedActiveDept);
        entity.setStatus(STATUS_ARCHIVED);
        applyDefaults(entity, trustedActiveDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public void delete(UUID id, String activeDept) {
        String trustedActiveDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary entity = repository.findById(id).orElseThrow();
        requireMutationAccess(entity, trustedActiveDept);
        repository.delete(entity);
    }

    private String resolveTrustedActiveDept(String requestedActiveDept) {
        String requested = StringUtils.hasText(requestedActiveDept) ? requestedActiveDept.trim() : null;
        String claimed = SecurityUtils.getCurrentUserDept().filter(StringUtils::hasText).map(String::trim).orElse(null);
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return requested != null ? requested : claimed;
        }
        if (requested != null && !DepartmentUtils.matches(requested, claimed)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        return claimed;
    }

    private void requireMutationAccess(GovDimensionDictionary entity, String activeDept) {
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return;
        }
        if (
            SecurityUtils.isAuthenticated() &&
            (!StringUtils.hasText(activeDept) ||
                isGlobalOrRoot(entity != null ? entity.getOwnerDept() : null) ||
                !DepartmentUtils.matches(entity != null ? entity.getOwnerDept() : null, activeDept))
        ) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for dimension mutation");
        }
    }

    private void applyDefaults(GovDimensionDictionary entity, String activeDept) {
        if (entity == null) return;
        if (!StringUtils.hasText(entity.getOwner()) && SecurityUtils.getCurrentUserDisplayName().isPresent()) {
            entity.setOwner(SecurityUtils.getCurrentUserDisplayName().orElse(null));
        }
        if (!StringUtils.hasText(entity.getOwnerDept()) && StringUtils.hasText(activeDept)) {
            entity.setOwnerDept(activeDept.trim());
        }
        entity.setStatus(normalizeStatus(entity.getStatus(), STATUS_DRAFT));
        entity.setDataLevel(normalizeDataLevel(entity.getDataLevel()));
    }

    private String normalizeDataLevel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return DataLevel.DATA_INTERNAL.name();
        }
        DataLevel normalized = DataLevel.normalize(raw);
        if (normalized != null) return normalized.name();
        String upper = raw.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("DATA_")) return upper;
        return DataLevel.DATA_INTERNAL.name();
    }

    private boolean keywordMatches(GovDimensionDictionary entity, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), kw) || contains(entity.getCode(), kw);
    }

    private boolean statusMatches(GovDimensionDictionary entity, String status) {
        if (!StringUtils.hasText(status)) return true;
        String expected = normalizeStatus(status, "");
        String actual = normalizeStatus(entity.getStatus(), "");
        return expected.equals(actual);
    }

    private String normalizeStatus(String status, String defaultValue) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase(Locale.ROOT) : defaultValue;
        if (!StringUtils.hasText(normalized)) {
            return defaultValue;
        }
        if (STATUS_DEPRECATED.equals(normalized)) {
            return STATUS_ARCHIVED;
        }
        return normalized;
    }

    private boolean contains(String value, String keywordLower) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keywordLower)) return false;
        return value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private boolean deptAllowed(GovDimensionDictionary entity, String activeDept) {
        if (entity == null) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        if (!StringUtils.hasText(activeDept)) {
            return isGlobalOrRoot(entity.getOwnerDept());
        }
        if (isGlobalOrRoot(entity.getOwnerDept())) {
            return true;
        }
        return DepartmentUtils.matches(entity.getOwnerDept(), activeDept);
    }

    private boolean isGlobalOrRoot(String ownerDept) {
        if (!StringUtils.hasText(ownerDept)) {
            return true;
        }
        try {
            return organizationVisibilityService.isRoot(ownerDept);
        } catch (Exception ignored) {
            return "ROOT".equalsIgnoreCase(ownerDept.trim());
        }
    }

    private boolean levelAllowed(GovDimensionDictionary entity) {
        if (entity == null) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        DataLevel resource = DataLevel.normalize(entity.getDataLevel());
        if (resource == null) {
            resource = DataLevel.DATA_INTERNAL;
        }
        int maxRank = accessChecker.resolveHighestDataLevel().rank();
        return resource.rank() <= maxRank;
    }

    private void validateUpsert(GovDimensionDictionary entity, UUID existingId, String activeDept) {
        if (entity == null) throw new IllegalArgumentException("Invalid dimension payload");
        if (!StringUtils.hasText(entity.getCode())) {
            throw new IllegalArgumentException("维度编码不能为空");
        }
        if (!StringUtils.hasText(entity.getName())) {
            throw new IllegalArgumentException("维度名称不能为空");
        }
        if (!SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            if (StringUtils.hasText(activeDept) && StringUtils.hasText(entity.getOwnerDept())) {
                if (!isGlobalOrRoot(entity.getOwnerDept()) && !DepartmentUtils.matches(entity.getOwnerDept(), activeDept)) {
                    throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
                }
            }
        }
        repository
            .findFirstByCodeIgnoreCase(entity.getCode().trim())
            .ifPresent(existing -> {
                if (existingId == null || existing.getId() == null || !existing.getId().equals(existingId)) {
                    throw new IllegalArgumentException("维度编码已存在");
                }
            });
    }
}
