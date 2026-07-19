package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.service.modeling.dto.MetadataStandardDto;
import jakarta.persistence.EntityNotFoundException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MetadataStandardService {

    private final MetadataStandardRepository repository;
    private final ModelingAssetReferenceService referenceService;

    public MetadataStandardService(MetadataStandardRepository repository, ModelingAssetReferenceService referenceService) {
        this.repository = repository;
        this.referenceService = referenceService;
    }

    @Transactional(readOnly = true)
    public Page<MetadataStandardDto> list(MetadataStandardFilter filter, Pageable pageable) {
        MetadataStandardFilter effective = filter != null ? filter : new MetadataStandardFilter();
        Specification<MetadataStandard> spec = buildSpecification(effective);
        return repository.findAll(spec, pageable).map(MetadataStandardMapper::toDto);
    }

    @Transactional(readOnly = true)
    public MetadataStandardDto get(UUID id) {
        MetadataStandard entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("元数据标准不存在"));
        return MetadataStandardMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> references(UUID id) {
        MetadataStandard entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("元数据标准不存在"));
        return referenceService.metadataStandardReferences(entity);
    }

    public MetadataStandardDto create(MetadataStandardUpsertRequest request) {
        MetadataStandard entity = new MetadataStandard();
        applyUpsert(entity, request);
        entity = repository.save(entity);
        return MetadataStandardMapper.toDto(entity);
    }

    public MetadataStandardDto update(UUID id, MetadataStandardUpsertRequest request) {
        MetadataStandard entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("元数据标准不存在"));
        applyUpsert(entity, request);
        entity.setVersion(Objects.requireNonNullElse(entity.getVersion(), 0) + 1);
        entity = repository.save(entity);
        return MetadataStandardMapper.toDto(entity);
    }

    public void delete(UUID id) {
        MetadataStandard entity = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("元数据标准不存在"));
        Map<String, Object> references = referenceService.metadataStandardReferences(entity);
        int impact = referenceService.countReferences(references);
        if (impact > 0) {
            String summary = referenceService.summarizeReferences(references, 5);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "存在引用依赖，无法删除（影响对象 " + impact + " 个）" + (summary.isBlank() ? "" : "：" + summary)
            );
        }
        repository.delete(entity);
    }

    private Specification<MetadataStandard> buildSpecification(MetadataStandardFilter filter) {
        Specification<MetadataStandard> spec = Specification.where(null);
        if (StringUtils.hasText(filter.getDomain())) {
            String domain = filter.getDomain().trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("domain"), domain));
        }
        if (StringUtils.hasText(filter.getSourceSystem())) {
            String sourceSystem = filter.getSourceSystem().trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("sourceSystem"), sourceSystem));
        }
        if (StringUtils.hasText(filter.getDataType())) {
            String dataType = filter.getDataType().trim().toUpperCase(Locale.ROOT);
            spec = spec.and((root, query, cb) -> cb.equal(cb.upper(root.get("dataType")), dataType));
        }
        if (StringUtils.hasText(filter.getKeyword())) {
            String like = "%" + filter.getKeyword().trim().toLowerCase(Locale.ROOT) + "%";
            spec =
                spec.and(
                    (root, query, cb) ->
                        cb.or(
                            cb.like(cb.lower(root.get("fieldNameCn")), like),
                            cb.like(cb.lower(root.get("fieldNameEn")), like),
                            cb.like(cb.lower(root.get("description")), like)
                        )
                );
        }
        return spec;
    }

    private void applyUpsert(MetadataStandard entity, MetadataStandardUpsertRequest request) {
        entity.setFieldNameCn(trimToNull(request.getFieldNameCn()));
        entity.setFieldNameEn(trimToNull(request.getFieldNameEn()));
        entity.setDataType(trimToNull(request.getDataType()));
        entity.setDataLength(request.getDataLength());
        entity.setDataPrecision(request.getDataPrecision());
        entity.setDataScale(request.getDataScale());
        entity.setNullable(request.getNullable() != null ? request.getNullable() : Boolean.TRUE);
        entity.setDomain(trimToNull(request.getDomain()));
        entity.setDescription(trimToNull(request.getDescription()));
        entity.setSourceSystem(trimToNull(request.getSourceSystem()));
        entity.setCodeSet(trimToNull(request.getCodeSet()));
        entity.setDefaultValue(trimToNull(request.getDefaultValue()));
        entity.setIsPk(request.getIsPk());
        DataSecurityLevel level = request.getSecurityLevel();
        entity.setSecurityLevel(Objects.requireNonNullElse(level, DataSecurityLevel.INTERNAL));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
