package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceCodePackagePort.Action;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceCodePackagePort.Change;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceCodePackagePort.DirectoryChanges;
import com.yuzhi.dts.platform.service.governance.GovernanceReferenceCodePackagePort.EntityType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class GovernanceReferenceCodePackageService implements GovernanceReferenceCodePackagePort {

    private final StdCodeDirectoryRepository directories;
    private final StdCodeValueRepository values;
    private final StdCodeMappingRepository mappings;

    public GovernanceReferenceCodePackageService(
        StdCodeDirectoryRepository directories,
        StdCodeValueRepository values,
        StdCodeMappingRepository mappings
    ) {
        this.directories = directories;
        this.values = values;
        this.mappings = mappings;
    }

    @Override
    public DirectoryChanges applyDirectories(List<Map<String, Object>> input) {
        List<Change> changes = new ArrayList<>();
        Map<String, String> directoryIds = new HashMap<>();
        for (Map<String, Object> item : safe(input)) {
            String code = text(item.get("codeTypeCode"));
            if (!StringUtils.hasText(code)) continue;
            StdCodeDirectory existing = directories.findByCodeTypeCodeIgnoreCase(code).orElse(null);
            if (existing != null) {
                changes.add(new Change(EntityType.CODE_DIRECTORY, existing.getCodeTypeId(), Action.UPDATE, image(existing)));
                copy(item, existing, false);
                directories.save(existing);
                directoryIds.put(code.toLowerCase(Locale.ROOT), existing.getCodeTypeId());
            } else {
                StdCodeDirectory created = new StdCodeDirectory();
                String requestedId = text(item.get("codeTypeId"));
                created.setCodeTypeId(StringUtils.hasText(requestedId) ? requestedId : code);
                created.setCodeTypeCode(code);
                copy(item, created, true);
                directories.save(created);
                changes.add(new Change(EntityType.CODE_DIRECTORY, created.getCodeTypeId(), Action.CREATE, null));
                directoryIds.put(code.toLowerCase(Locale.ROOT), created.getCodeTypeId());
            }
        }
        return new DirectoryChanges(changes, directoryIds);
    }

    @Override
    public List<Change> applyValues(List<Map<String, Object>> input, Map<String, String> directoryIdsByCode) {
        List<Change> changes = new ArrayList<>();
        Map<String, String> directoryIds = mutableDirectoryIds(directoryIdsByCode);
        Map<String, Map<String, StdCodeValue>> existingByDirectory = new HashMap<>();
        for (Map<String, Object> item : safe(input)) {
            String directoryId = resolveDirectoryId(text(item.get("codeTypeCode")), directoryIds);
            if (directoryId == null) continue;
            Map<String, StdCodeValue> existingValues = existingByDirectory.computeIfAbsent(directoryId, key -> {
                Map<String, StdCodeValue> indexed = new LinkedHashMap<>();
                for (StdCodeValue value : values.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(key)) {
                    indexed.put(value.getCodeValue(), value);
                }
                return indexed;
            });
            String codeValue = text(item.get("codeValue"));
            StdCodeValue existing = existingValues.get(codeValue);
            if (existing != null) {
                changes.add(new Change(EntityType.CODE_VALUE, String.valueOf(existing.getItemId()), Action.UPDATE, image(existing)));
                copy(item, existing);
                values.save(existing);
            } else {
                StdCodeValue created = new StdCodeValue();
                created.setCodeTypeId(directoryId);
                created.setCodeValue(codeValue);
                copy(item, created);
                StdCodeValue saved = values.save(created);
                existingValues.put(codeValue, saved);
                changes.add(new Change(EntityType.CODE_VALUE, String.valueOf(saved.getItemId()), Action.CREATE, null));
            }
        }
        return List.copyOf(changes);
    }

    @Override
    public List<Change> applyMappings(List<Map<String, Object>> input, Map<String, String> directoryIdsByCode) {
        List<Change> changes = new ArrayList<>();
        Map<String, String> directoryIds = mutableDirectoryIds(directoryIdsByCode);
        Map<String, List<StdCodeMapping>> existingByDirectory = new HashMap<>();
        for (Map<String, Object> item : safe(input)) {
            String directoryId = resolveDirectoryId(text(item.get("codeTypeCode")), directoryIds);
            if (directoryId == null) continue;
            List<StdCodeMapping> existingMappings = existingByDirectory.computeIfAbsent(
                directoryId,
                key -> new ArrayList<>(mappings.findByCodeTypeIdOrderBySourceSysAsc(key))
            );
            String sourceSystem = text(item.get("sourceSystem"));
            String sourceCode = text(item.get("sourceCode"));
            String standardCode = text(item.get("standardCode"));
            StdCodeMapping existing = existingMappings
                .stream()
                .filter(mapping -> sourceSystem.equals(mapping.getSourceSys()) && sourceCode.equals(mapping.getSrcCode()))
                .findFirst()
                .orElse(null);
            if (existing != null) {
                changes.add(
                    new Change(
                        EntityType.CODE_MAPPING,
                        String.valueOf(existing.getMapId()),
                        Action.UPDATE,
                        Map.of("stdCode", existing.getStdCode())
                    )
                );
                existing.setStdCode(standardCode);
                mappings.save(existing);
            } else {
                StdCodeMapping created = new StdCodeMapping();
                created.setCodeTypeId(directoryId);
                created.setSourceSys(sourceSystem);
                created.setSrcCode(sourceCode);
                created.setStdCode(standardCode);
                StdCodeMapping saved = mappings.save(created);
                existingMappings.add(saved);
                changes.add(new Change(EntityType.CODE_MAPPING, String.valueOf(saved.getMapId()), Action.CREATE, null));
            }
        }
        return List.copyOf(changes);
    }

    @Override
    public boolean rollbackCreate(EntityType entityType, String entityId) {
        try {
            return switch (entityType) {
                case CODE_DIRECTORY -> deleteDirectory(entityId);
                case CODE_VALUE -> deleteValue(Long.valueOf(entityId));
                case CODE_MAPPING -> deleteMapping(Long.valueOf(entityId));
            };
        } catch (NumberFormatException invalidId) {
            return false;
        }
    }

    @Override
    public boolean rollbackUpdate(EntityType entityType, String entityId, Map<String, Object> beforeImage) {
        try {
            return switch (entityType) {
                case CODE_DIRECTORY -> restoreDirectory(entityId, beforeImage);
                case CODE_VALUE -> restoreValue(Long.valueOf(entityId), beforeImage);
                case CODE_MAPPING -> restoreMapping(Long.valueOf(entityId), beforeImage);
            };
        } catch (NumberFormatException invalidId) {
            return false;
        }
    }

    private boolean deleteDirectory(String id) {
        if (!directories.existsById(id)) return false;
        directories.deleteById(id);
        return true;
    }

    private boolean deleteValue(Long id) {
        if (!values.existsById(id)) return false;
        values.deleteById(id);
        return true;
    }

    private boolean deleteMapping(Long id) {
        if (!mappings.existsById(id)) return false;
        mappings.deleteById(id);
        return true;
    }

    private boolean restoreDirectory(String id, Map<String, Object> before) {
        StdCodeDirectory directory = directories.findById(id).orElse(null);
        if (directory == null) return false;
        directory.setCodeTypeName(text(before.get("codeTypeName")));
        directory.setStdLevel(text(before.get("stdLevel")));
        directory.setBizCatalog(text(before.get("bizCatalog")));
        directory.setDataType(text(before.get("dataType")));
        directory.setStatus(integer(before.get("status")));
        directory.setOwnerDept(text(before.get("ownerDept")));
        directory.setVersion(text(before.get("version")));
        directories.save(directory);
        return true;
    }

    private boolean restoreValue(Long id, Map<String, Object> before) {
        StdCodeValue value = values.findById(id).orElse(null);
        if (value == null) return false;
        value.setCodeName(text(before.get("codeName")));
        value.setDescription(text(before.get("description")));
        value.setSortNum(integer(before.get("sortNum")));
        value.setParentCode(text(before.get("parentCode")));
        value.setIsDefault(bool(before.get("isDefault")));
        values.save(value);
        return true;
    }

    private boolean restoreMapping(Long id, Map<String, Object> before) {
        StdCodeMapping mapping = mappings.findById(id).orElse(null);
        if (mapping == null) return false;
        mapping.setStdCode(text(before.get("stdCode")));
        mappings.save(mapping);
        return true;
    }

    private String resolveDirectoryId(String code, Map<String, String> cache) {
        if (!StringUtils.hasText(code)) return null;
        String normalized = code.toLowerCase(Locale.ROOT);
        if (cache.containsKey(normalized)) return cache.get(normalized);
        String resolved = directories.findByCodeTypeCodeIgnoreCase(code).map(StdCodeDirectory::getCodeTypeId).orElse(null);
        cache.put(normalized, resolved);
        return resolved;
    }

    private static void copy(Map<String, Object> source, StdCodeDirectory target, boolean creating) {
        target.setCodeTypeName(text(source.get("codeTypeName")));
        target.setStdLevel(text(source.get("stdLevel")));
        target.setBizCatalog(text(source.get("bizCatalog")));
        target.setDataType(text(source.get("dataType")));
        Integer status = integer(source.get("status"));
        if (status != null || creating) target.setStatus(status == null ? Integer.valueOf(1) : status);
        String ownerDept = text(source.get("ownerDept"));
        if (StringUtils.hasText(ownerDept) || creating) target.setOwnerDept(ownerDept);
        target.setVersion(text(source.get("version")));
    }

    private static void copy(Map<String, Object> source, StdCodeValue target) {
        target.setCodeName(text(source.get("codeName")));
        target.setDescription(text(source.get("description")));
        target.setSortNum(integer(source.get("sortNum")));
        target.setParentCode(text(source.get("parentCode")));
        target.setIsDefault(bool(source.get("isDefault")));
    }

    private static Map<String, Object> image(StdCodeDirectory directory) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("codeTypeName", directory.getCodeTypeName());
        image.put("stdLevel", directory.getStdLevel());
        image.put("bizCatalog", directory.getBizCatalog());
        image.put("dataType", directory.getDataType());
        image.put("status", directory.getStatus());
        image.put("ownerDept", directory.getOwnerDept());
        image.put("version", directory.getVersion());
        return image;
    }

    private static Map<String, Object> image(StdCodeValue value) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("codeName", value.getCodeName());
        image.put("description", value.getDescription());
        image.put("sortNum", value.getSortNum());
        image.put("parentCode", value.getParentCode());
        image.put("isDefault", value.getIsDefault());
        return image;
    }

    private static List<Map<String, Object>> safe(List<Map<String, Object>> input) {
        return input == null ? List.of() : input;
    }

    private static Map<String, String> mutableDirectoryIds(Map<String, String> input) {
        return input == null ? new HashMap<>() : new HashMap<>(input);
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException invalid) {
                return null;
            }
        }
        return null;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean bool) return bool;
        if (!(value instanceof String text) || !StringUtils.hasText(text)) return null;
        return switch (text.trim().toUpperCase(Locale.ROOT)) {
            case "Y", "YES", "TRUE" -> Boolean.TRUE;
            case "N", "NO", "FALSE" -> Boolean.FALSE;
            default -> null;
        };
    }
}
