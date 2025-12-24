package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.request.IndicatorUpsertRequest;
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
public class IndicatorService {

    private final GovIndicatorDefinitionRepository repository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;

    public IndicatorService(
        GovIndicatorDefinitionRepository repository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.repository = repository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    @Transactional(readOnly = true)
    public Page<IndicatorDto> list(String keyword, String status, Pageable pageable, String activeDept) {
        List<GovIndicatorDefinition> all = repository.findAll();
        List<GovIndicatorDefinition> filtered = new ArrayList<>();
        for (GovIndicatorDefinition indicator : all) {
            if (indicator == null) continue;
            if (!keywordMatches(indicator, keyword)) continue;
            if (!statusMatches(indicator, status)) continue;
            if (!deptAllowed(indicator, activeDept)) continue;
            if (!levelAllowed(indicator)) continue;
            filtered.add(indicator);
        }
        filtered.sort(
            Comparator.comparing(GovIndicatorDefinition::getLastModifiedDate, Comparator.nullsLast(Comparator.naturalOrder())).reversed()
        );

        int total = filtered.size();
        if (pageable == null) {
            List<IndicatorDto> content = filtered.stream().map(IndicatorMapper::toDto).toList();
            return new PageImpl<>(content, Pageable.unpaged(), total);
        }
        int start = (int) Math.min(pageable.getOffset(), total);
        int end = Math.min(start + pageable.getPageSize(), total);
        List<IndicatorDto> content = filtered.subList(start, end).stream().map(IndicatorMapper::toDto).toList();
        return new PageImpl<>(content, pageable, total);
    }

    @Transactional(readOnly = true)
    public IndicatorDto get(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept) || !levelAllowed(entity)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for indicator");
        }
        return IndicatorMapper.toDto(entity);
    }

    public IndicatorDto create(IndicatorUpsertRequest request, String activeDept) {
        GovIndicatorDefinition entity = new GovIndicatorDefinition();
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, activeDept);
        validateUpsert(entity, null, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto update(UUID id, IndicatorUpsertRequest request, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        IndicatorMapper.apply(entity, request);
        applyDefaults(entity, activeDept);
        validateUpsert(entity, id, activeDept);
        GovIndicatorDefinition saved = repository.save(entity);
        return IndicatorMapper.toDto(saved);
    }

    public IndicatorDto publish(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        entity.setStatus("PUBLISHED");
        applyDefaults(entity, activeDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public IndicatorDto archive(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        entity.setStatus("DEPRECATED");
        applyDefaults(entity, activeDept);
        return IndicatorMapper.toDto(repository.save(entity));
    }

    public void delete(UUID id, String activeDept) {
        GovIndicatorDefinition entity = repository.findById(id).orElseThrow();
        if (!deptAllowed(entity, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        repository.delete(entity);
    }

    private void applyDefaults(GovIndicatorDefinition entity, String activeDept) {
        if (entity == null) return;
        if (!StringUtils.hasText(entity.getOwner()) && SecurityUtils.getCurrentUserDisplayName().isPresent()) {
            entity.setOwner(SecurityUtils.getCurrentUserDisplayName().orElse(null));
        }
        if (!StringUtils.hasText(entity.getOwnerDept()) && StringUtils.hasText(activeDept)) {
            entity.setOwnerDept(activeDept.trim());
        }
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("DRAFT");
        }
        if (!StringUtils.hasText(entity.getVersion())) {
            entity.setVersion("v1");
        }
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

    private boolean keywordMatches(GovIndicatorDefinition entity, String keyword) {
        if (!StringUtils.hasText(keyword)) return true;
        String kw = keyword.trim().toLowerCase(Locale.ROOT);
        return contains(entity.getName(), kw) || contains(entity.getCode(), kw) || contains(entity.getCategory(), kw);
    }

    private boolean statusMatches(GovIndicatorDefinition entity, String status) {
        if (!StringUtils.hasText(status)) return true;
        String expected = status.trim().toUpperCase(Locale.ROOT);
        String actual = entity.getStatus() == null ? "" : entity.getStatus().trim().toUpperCase(Locale.ROOT);
        return expected.equals(actual);
    }

    private boolean contains(String value, String keywordLower) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keywordLower)) return false;
        return value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private boolean deptAllowed(GovIndicatorDefinition entity, String activeDept) {
        if (entity == null) return false;
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return true;
        }
        if (!StringUtils.hasText(activeDept)) {
            // No context selected: only expose global/root-owned indicators.
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

    private boolean levelAllowed(GovIndicatorDefinition entity) {
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

    private void validateUpsert(GovIndicatorDefinition entity, UUID existingId, String activeDept) {
        if (entity == null) throw new IllegalArgumentException("Invalid indicator payload");
        if (!StringUtils.hasText(entity.getCode())) {
            throw new IllegalArgumentException("指标编码不能为空");
        }
        if (!StringUtils.hasText(entity.getName())) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        if (StringUtils.hasText(entity.getDatasetId())) {
            try {
                UUID.fromString(entity.getDatasetId().trim());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("数据集ID格式错误");
            }
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
                    throw new IllegalArgumentException("指标编码已存在");
                }
            });
    }
}
