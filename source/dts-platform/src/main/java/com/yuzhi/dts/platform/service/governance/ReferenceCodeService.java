package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeDirectoryDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeItemDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeMappingDto;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ReferenceCodeService {

    private final StdCodeDirectoryRepository directoryRepository;
    private final StdCodeValueRepository valueRepository;
    private final StdCodeMappingRepository mappingRepository;
    private final ReferenceCodeSecurity security;

    public ReferenceCodeService(
        StdCodeDirectoryRepository directoryRepository,
        StdCodeValueRepository valueRepository,
        StdCodeMappingRepository mappingRepository,
        ReferenceCodeSecurity security
    ) {
        this.directoryRepository = directoryRepository;
        this.valueRepository = valueRepository;
        this.mappingRepository = mappingRepository;
        this.security = security;
    }

    public Page<ReferenceCodeDirectoryDto> listDirectories(String keyword, Pageable pageable, String activeDept) {
        Specification<StdCodeDirectory> spec = buildSpec(keyword, activeDept);
        return directoryRepository.findAll(spec, pageable).map(this::toDtoWithCount);
    }

    public ReferenceCodeDirectoryDto getDirectory(String codeTypeId, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        return toDtoWithCount(entity);
    }

    public ReferenceCodeDirectoryDto createDirectory(ReferenceCodeDirectoryRequest request, String activeDept) {
        String codeTypeCode = normalize(request.codeTypeCode());
        String codeTypeName = normalize(request.codeTypeName());
        if (!StringUtils.hasText(codeTypeCode) || !StringUtils.hasText(codeTypeName)) {
            throw new IllegalArgumentException("码表编码和名称不能为空");
        }
        String codeTypeId = normalize(request.codeTypeId());
        if (!StringUtils.hasText(codeTypeId)) {
            codeTypeId = codeTypeCode;
        }
        if (directoryRepository.findById(codeTypeId).isPresent()) {
            throw new IllegalArgumentException("码表ID已存在");
        }
        if (directoryRepository.findByCodeTypeCodeIgnoreCase(codeTypeCode).isPresent()) {
            throw new IllegalArgumentException("码表编码已存在");
        }
        StdCodeDirectory entity = new StdCodeDirectory();
        entity.setCodeTypeId(codeTypeId);
        entity.setCodeTypeCode(codeTypeCode);
        entity.setCodeTypeName(codeTypeName);
        entity.setStdLevel(normalize(request.stdLevel()));
        entity.setBizCatalog(normalize(request.bizCatalog()));
        entity.setDataType(normalize(request.dataType()));
        entity.setStatus(request.status());
        entity.setOwnerDept(security.enforceOwnerDept(request.ownerDept(), activeDept));
        entity.setVersion(normalize(request.version()));
        directoryRepository.save(entity);
        return toDtoWithCount(entity);
    }

    public ReferenceCodeDirectoryDto updateDirectory(String codeTypeId, ReferenceCodeDirectoryRequest request, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        String newCode = normalize(request.codeTypeCode());
        if (StringUtils.hasText(newCode) && !newCode.equalsIgnoreCase(entity.getCodeTypeCode())) {
            directoryRepository.findByCodeTypeCodeIgnoreCase(newCode).ifPresent(existing -> {
                if (!Objects.equals(existing.getCodeTypeId(), entity.getCodeTypeId())) {
                    throw new IllegalArgumentException("码表编码已存在");
                }
            });
            entity.setCodeTypeCode(newCode);
        }
        String name = normalize(request.codeTypeName());
        if (StringUtils.hasText(name)) {
            entity.setCodeTypeName(name);
        }
        if (StringUtils.hasText(request.stdLevel())) entity.setStdLevel(normalize(request.stdLevel()));
        if (StringUtils.hasText(request.bizCatalog())) entity.setBizCatalog(normalize(request.bizCatalog()));
        if (StringUtils.hasText(request.dataType())) entity.setDataType(normalize(request.dataType()));
        if (request.status() != null) entity.setStatus(request.status());
        if (StringUtils.hasText(request.version())) entity.setVersion(normalize(request.version()));
        if (StringUtils.hasText(request.ownerDept())) {
            entity.setOwnerDept(security.enforceOwnerDept(request.ownerDept(), activeDept));
        }
        directoryRepository.save(entity);
        return toDtoWithCount(entity);
    }

    public void deleteDirectory(String codeTypeId, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        directoryRepository.delete(entity);
    }

    public List<ReferenceCodeItemDto> listItems(String codeTypeId, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        return valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(codeTypeId)
            .stream()
            .map(this::toItemDto)
            .toList();
    }

    public ReferenceCodeItemDto createItem(String codeTypeId, ReferenceCodeItemRequest request, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        String codeValue = normalize(request.codeValue());
        String codeName = normalize(request.codeName());
        if (!StringUtils.hasText(codeValue) || !StringUtils.hasText(codeName)) {
            throw new IllegalArgumentException("码值与名称不能为空");
        }
        if (valueRepository.existsByCodeTypeIdAndCodeValue(codeTypeId, codeValue)) {
            throw new IllegalArgumentException("码值已存在");
        }
        StdCodeValue value = new StdCodeValue();
        value.setCodeTypeId(codeTypeId);
        value.setCodeValue(codeValue);
        value.setCodeName(codeName);
        value.setDescription(normalize(request.description()));
        value.setSortNum(request.sortNum());
        value.setParentCode(normalize(request.parentCode()));
        value.setIsDefault(request.isDefault());
        valueRepository.save(value);
        return toItemDto(value);
    }

    public ReferenceCodeItemDto updateItem(String codeTypeId, Long itemId, ReferenceCodeItemRequest request, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        StdCodeValue value = valueRepository.findById(itemId)
            .orElseThrow(() -> new EntityNotFoundException("码表项不存在"));
        if (!codeTypeId.equals(value.getCodeTypeId())) {
            throw new IllegalArgumentException("码表项不属于当前码表");
        }
        String codeValue = normalize(request.codeValue());
        if (StringUtils.hasText(codeValue) && !codeValue.equalsIgnoreCase(value.getCodeValue())) {
            if (valueRepository.existsByCodeTypeIdAndCodeValue(codeTypeId, codeValue)) {
                throw new IllegalArgumentException("码值已存在");
            }
            value.setCodeValue(codeValue);
        }
        String codeName = normalize(request.codeName());
        if (StringUtils.hasText(codeName)) value.setCodeName(codeName);
        if (request.description() != null) value.setDescription(normalize(request.description()));
        if (request.sortNum() != null) value.setSortNum(request.sortNum());
        if (request.parentCode() != null) value.setParentCode(normalize(request.parentCode()));
        if (request.isDefault() != null) value.setIsDefault(request.isDefault());
        valueRepository.save(value);
        return toItemDto(value);
    }

    public void deleteItem(String codeTypeId, Long itemId, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        StdCodeValue value = valueRepository.findById(itemId)
            .orElseThrow(() -> new EntityNotFoundException("码表项不存在"));
        if (!codeTypeId.equals(value.getCodeTypeId())) {
            throw new IllegalArgumentException("码表项不属于当前码表");
        }
        valueRepository.delete(value);
    }

    public BatchImportResult batchImportItems(String codeTypeId, ReferenceCodeItemBatchRequest request, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        String raw = normalize(request.raw());
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("批量内容不能为空");
        }
        int total = 0;
        int created = 0;
        int skipped = 0;
        int invalid = 0;
        String[] pairs = raw.split(",");
        for (String pair : pairs) {
            if (!StringUtils.hasText(pair)) continue;
            total++;
            String[] parts = pair.split(":", 2);
            if (parts.length < 2) {
                invalid++;
                continue;
            }
            String codeValue = normalize(parts[0]);
            String codeName = normalize(parts[1]);
            if (!StringUtils.hasText(codeValue) || !StringUtils.hasText(codeName)) {
                invalid++;
                continue;
            }
            if (valueRepository.existsByCodeTypeIdAndCodeValue(codeTypeId, codeValue)) {
                skipped++;
                continue;
            }
            StdCodeValue value = new StdCodeValue();
            value.setCodeTypeId(codeTypeId);
            value.setCodeValue(codeValue);
            value.setCodeName(codeName);
            valueRepository.save(value);
            created++;
        }
        return new BatchImportResult(total, created, skipped, invalid);
    }

    public List<ReferenceCodeMappingDto> listMappings(String codeTypeId, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        return mappingRepository.findByCodeTypeIdOrderBySourceSysAsc(codeTypeId)
            .stream()
            .map(this::toMappingDto)
            .toList();
    }

    public ReferenceCodeMappingDto createMapping(
        String codeTypeId,
        ReferenceCodeMappingRequest request,
        String activeDept
    ) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        String sourceSys = normalize(request.sourceSys());
        String srcCode = normalize(request.srcCode());
        String stdCode = normalize(request.stdCode());
        if (!StringUtils.hasText(sourceSys) || !StringUtils.hasText(srcCode) || !StringUtils.hasText(stdCode)) {
            throw new IllegalArgumentException("映射字段不能为空");
        }
        if (!valueRepository.existsByCodeTypeIdAndCodeValue(codeTypeId, stdCode)) {
            throw new IllegalArgumentException("标准码值不存在");
        }
        if (mappingRepository.existsByCodeTypeIdAndSourceSysAndSrcCode(codeTypeId, sourceSys, srcCode)) {
            throw new IllegalArgumentException("映射已存在");
        }
        StdCodeMapping mapping = new StdCodeMapping();
        mapping.setCodeTypeId(codeTypeId);
        mapping.setSourceSys(sourceSys);
        mapping.setSrcCode(srcCode);
        mapping.setStdCode(stdCode);
        mappingRepository.save(mapping);
        return toMappingDto(mapping);
    }

    public ReferenceCodeMappingDto updateMapping(
        String codeTypeId,
        Long mapId,
        ReferenceCodeMappingRequest request,
        String activeDept
    ) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        StdCodeMapping mapping = mappingRepository.findById(mapId)
            .orElseThrow(() -> new EntityNotFoundException("映射不存在"));
        if (!codeTypeId.equals(mapping.getCodeTypeId())) {
            throw new IllegalArgumentException("映射不属于当前码表");
        }
        String sourceSys = normalize(request.sourceSys());
        String srcCode = normalize(request.srcCode());
        String stdCode = normalize(request.stdCode());
        if (StringUtils.hasText(sourceSys) && StringUtils.hasText(srcCode)) {
            boolean changed = !sourceSys.equalsIgnoreCase(mapping.getSourceSys()) || !srcCode.equalsIgnoreCase(mapping.getSrcCode());
            if (changed && mappingRepository.existsByCodeTypeIdAndSourceSysAndSrcCode(codeTypeId, sourceSys, srcCode)) {
                throw new IllegalArgumentException("映射已存在");
            }
            if (StringUtils.hasText(sourceSys)) mapping.setSourceSys(sourceSys);
            if (StringUtils.hasText(srcCode)) mapping.setSrcCode(srcCode);
        }
        if (StringUtils.hasText(stdCode)) {
            if (!valueRepository.existsByCodeTypeIdAndCodeValue(codeTypeId, stdCode)) {
                throw new IllegalArgumentException("标准码值不存在");
            }
            mapping.setStdCode(stdCode);
        }
        mappingRepository.save(mapping);
        return toMappingDto(mapping);
    }

    public void deleteMapping(String codeTypeId, Long mapId, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        StdCodeMapping mapping = mappingRepository.findById(mapId)
            .orElseThrow(() -> new EntityNotFoundException("映射不存在"));
        if (!codeTypeId.equals(mapping.getCodeTypeId())) {
            throw new IllegalArgumentException("映射不属于当前码表");
        }
        mappingRepository.delete(mapping);
    }

    private void ensureDeptAccess(StdCodeDirectory entity, String activeDept) {
        if (!security.canAccessDept(entity.getOwnerDept(), activeDept)) {
            throw new IllegalArgumentException("无权访问该部门码表");
        }
    }

    private Specification<StdCodeDirectory> buildSpec(String keyword, String activeDept) {
        String trimmed = normalize(keyword);
        String dept = security.resolveActiveDept(activeDept);
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(trimmed)) {
                String like = "%" + trimmed.toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("codeTypeCode")), like),
                    cb.like(cb.lower(root.get("codeTypeName")), like),
                    cb.like(cb.lower(root.get("bizCatalog")), like)
                ));
            }
            if (!security.hasInstituteScope() && StringUtils.hasText(dept)) {
                predicates.add(cb.equal(root.get("ownerDept"), dept));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    private ReferenceCodeDirectoryDto toDtoWithCount(StdCodeDirectory entity) {
        ReferenceCodeDirectoryDto dto = toDto(entity);
        dto.setItemCount(valueRepository.countByCodeTypeId(entity.getCodeTypeId()));
        return dto;
    }

    private ReferenceCodeDirectoryDto toDto(StdCodeDirectory entity) {
        ReferenceCodeDirectoryDto dto = new ReferenceCodeDirectoryDto();
        dto.setCodeTypeId(entity.getCodeTypeId());
        dto.setCodeTypeCode(entity.getCodeTypeCode());
        dto.setCodeTypeName(entity.getCodeTypeName());
        dto.setStdLevel(entity.getStdLevel());
        dto.setBizCatalog(entity.getBizCatalog());
        dto.setDataType(entity.getDataType());
        dto.setStatus(entity.getStatus());
        dto.setOwnerDept(entity.getOwnerDept());
        dto.setVersion(entity.getVersion());
        dto.setCreatedDate(entity.getCreatedDate());
        dto.setLastModifiedDate(entity.getLastModifiedDate());
        return dto;
    }

    private ReferenceCodeItemDto toItemDto(StdCodeValue value) {
        ReferenceCodeItemDto dto = new ReferenceCodeItemDto();
        dto.setItemId(value.getItemId());
        dto.setCodeTypeId(value.getCodeTypeId());
        dto.setCodeValue(value.getCodeValue());
        dto.setCodeName(value.getCodeName());
        dto.setDescription(value.getDescription());
        dto.setSortNum(value.getSortNum());
        dto.setParentCode(value.getParentCode());
        dto.setIsDefault(value.getIsDefault());
        return dto;
    }

    private ReferenceCodeMappingDto toMappingDto(StdCodeMapping mapping) {
        ReferenceCodeMappingDto dto = new ReferenceCodeMappingDto();
        dto.setMapId(mapping.getMapId());
        dto.setCodeTypeId(mapping.getCodeTypeId());
        dto.setSourceSys(mapping.getSourceSys());
        dto.setSrcCode(mapping.getSrcCode());
        dto.setStdCode(mapping.getStdCode());
        dto.setCreatedDate(mapping.getCreatedDate());
        dto.setLastModifiedDate(mapping.getLastModifiedDate());
        return dto;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record ReferenceCodeDirectoryRequest(
        String codeTypeId,
        String codeTypeCode,
        String codeTypeName,
        String stdLevel,
        String bizCatalog,
        String dataType,
        Integer status,
        String ownerDept,
        String version
    ) {}

    public record ReferenceCodeItemRequest(
        String codeValue,
        String codeName,
        String description,
        Integer sortNum,
        String parentCode,
        Boolean isDefault
    ) {}

    public record ReferenceCodeItemBatchRequest(String raw) {}

    public record BatchImportResult(int total, int created, int skipped, int invalid) {}

    public record ReferenceCodeMappingRequest(String sourceSys, String srcCode, String stdCode) {}
}
