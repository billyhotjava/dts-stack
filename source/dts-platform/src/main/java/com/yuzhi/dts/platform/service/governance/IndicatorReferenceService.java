package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class IndicatorReferenceService {

    private final GovIndicatorDefinitionRepository indicatorRepository;
    private final GovIndicatorReferenceRepository referenceRepository;
    private final IndicatorService indicatorService;

    public IndicatorReferenceService(
        GovIndicatorDefinitionRepository indicatorRepository,
        GovIndicatorReferenceRepository referenceRepository,
        IndicatorService indicatorService
    ) {
        this.indicatorRepository = indicatorRepository;
        this.referenceRepository = referenceRepository;
        this.indicatorService = indicatorService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID indicatorId, String activeDept) {
        // Permission check (department + level).
        indicatorService.get(indicatorId, activeDept);
        GovIndicatorDefinition indicator = indicatorRepository
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        return referenceRepository
            .findByIndicatorOrderByCreatedDateAsc(indicator)
            .stream()
            .map(this::toDto)
            .toList();
    }

    public Map<String, Object> create(UUID indicatorId, String activeDept, ReferenceUpsertRequest request) {
        if (request == null) {
            throw new IndicatorRequestException("Invalid payload");
        }
        // Permission check for edit: reuse update rules in IndicatorService (maintainer endpoints are protected in controller).
        IndicatorDto dto = indicatorService.requireMutationAccess(indicatorId, activeDept);
        GovIndicatorDefinition indicator = indicatorRepository
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));

        String refType = normalizeRefType(request.refType());
        String refTarget = normalizeText(request.refTarget());
        if (!StringUtils.hasText(refType) || !StringUtils.hasText(refTarget)) {
            throw new IndicatorRequestException("refType/refTarget 不能为空");
        }
        rejectManagedIndicatorReference(refType);

        GovIndicatorReference entity = referenceRepository
            .findFirstByIndicatorAndRefTypeIgnoreCaseAndRefTargetIgnoreCase(indicator, refType, refTarget)
            .orElseGet(GovIndicatorReference::new);
        entity.setIndicator(indicator);
        entity.setRefType(refType);
        entity.setRefTarget(refTarget);
        entity.setRefName(normalizeText(request.refName()));
        entity.setNotes(normalizeText(request.notes()));
        GovIndicatorReference saved = referenceRepository.save(entity);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("indicatorId", indicatorId.toString());
        out.put("indicatorName", dto != null ? dto.getName() : null);
        out.put("reference", toDto(saved));
        return out;
    }

    public Map<String, Object> update(UUID indicatorId, UUID referenceId, String activeDept, ReferenceUpsertRequest request) {
        if (referenceId == null) {
            throw new IndicatorRequestException("referenceId required");
        }
        if (request == null) {
            throw new IndicatorRequestException("Invalid payload");
        }
        indicatorService.requireMutationAccess(indicatorId, activeDept);
        GovIndicatorDefinition indicator = indicatorRepository
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));

        GovIndicatorReference entity = referenceRepository
            .findById(referenceId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标引用不存在: " + referenceId));
        if (entity.getIndicator() == null || entity.getIndicator().getId() == null || !entity.getIndicator().getId().equals(indicator.getId())) {
            throw new IndicatorRequestException("reference does not belong to indicator");
        }

        String refType = normalizeRefType(request.refType());
        String refTarget = normalizeText(request.refTarget());
        rejectManagedIndicatorReference(entity.getRefType());
        rejectManagedIndicatorReference(refType);
        if (StringUtils.hasText(refType)) {
            entity.setRefType(refType);
        }
        if (StringUtils.hasText(refTarget)) {
            entity.setRefTarget(refTarget);
        }
        entity.setRefName(normalizeText(request.refName()));
        entity.setNotes(normalizeText(request.notes()));
        GovIndicatorReference saved = referenceRepository.save(entity);

        return Map.of("reference", toDto(saved));
    }

    public void delete(UUID indicatorId, UUID referenceId, String activeDept) {
        indicatorService.requireMutationAccess(indicatorId, activeDept);
        GovIndicatorReference entity = referenceRepository
            .findById(referenceId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标引用不存在: " + referenceId));
        if (entity.getIndicator() == null || entity.getIndicator().getId() == null || !entity.getIndicator().getId().equals(indicatorId)) {
            throw new IndicatorRequestException("reference does not belong to indicator");
        }
        rejectManagedIndicatorReference(entity.getRefType());
        referenceRepository.delete(entity);
    }

    public Map<String, Object> replaceModelFieldReference(
        UUID indicatorId,
        String activeDept,
        ReferenceUpsertRequest request
    ) {
        if (
            request == null ||
            !"MODEL_SPEC_FIELD".equalsIgnoreCase(normalizeRefType(request.refType())) ||
            !StringUtils.hasText(normalizeText(request.refTarget()))
        ) {
            throw new IndicatorRequestException("MODEL_SPEC_FIELD 引用不能为空");
        }
        IndicatorDto dto = indicatorService.requireMutationAccess(indicatorId, activeDept);
        GovIndicatorDefinition indicator = indicatorRepository
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));
        List<GovIndicatorReference> managed = referenceRepository
            .findByIndicatorOrderByCreatedDateAsc(indicator)
            .stream()
            .filter(reference -> "MODEL_SPEC_FIELD".equalsIgnoreCase(reference.getRefType()))
            .toList();
        GovIndicatorReference target = managed.isEmpty() ? new GovIndicatorReference() : managed.get(0);
        managed.stream().skip(1).forEach(referenceRepository::delete);
        target.setIndicator(indicator);
        target.setRefType("MODEL_SPEC_FIELD");
        target.setRefTarget(normalizeText(request.refTarget()));
        target.setRefName(normalizeText(request.refName()));
        target.setNotes(normalizeText(request.notes()));
        GovIndicatorReference saved = referenceRepository.save(target);
        return Map.of(
            "indicatorId",
            indicatorId.toString(),
            "indicatorName",
            dto != null && dto.getName() != null ? dto.getName() : "",
            "reference",
            toDto(saved)
        );
    }

    private void rejectManagedIndicatorReference(String refType) {
        if ("INDICATOR".equalsIgnoreCase(normalizeRefType(refType))) {
            throw new IndicatorConflictException("指标依赖引用由 dependencyIndicators 自动维护，不能手工修改");
        }
    }

    private Map<String, Object> toDto(GovIndicatorReference entity) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (entity == null) return dto;
        if (entity.getId() != null) dto.put("id", entity.getId().toString());
        dto.put("refType", entity.getRefType());
        dto.put("refTarget", entity.getRefTarget());
        dto.put("refName", entity.getRefName());
        dto.put("notes", entity.getNotes());
        dto.put("createdBy", entity.getCreatedBy());
        dto.put("createdDate", entity.getCreatedDate());
        return dto;
    }

    private String normalizeRefType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ReferenceUpsertRequest(String refType, String refTarget, String refName, String notes) {}
}
