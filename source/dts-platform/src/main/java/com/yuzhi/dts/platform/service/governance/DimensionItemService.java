package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovDimensionDictionary;
import com.yuzhi.dts.platform.domain.governance.GovDimensionItem;
import com.yuzhi.dts.platform.repository.governance.GovDimensionDictionaryRepository;
import com.yuzhi.dts.platform.repository.governance.GovDimensionItemRepository;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class DimensionItemService {

    private final GovDimensionDictionaryRepository dimensionRepository;
    private final GovDimensionItemRepository itemRepository;
    private final AccessChecker accessChecker;
    private final OrganizationVisibilityService organizationVisibilityService;

    public DimensionItemService(
        GovDimensionDictionaryRepository dimensionRepository,
        GovDimensionItemRepository itemRepository,
        AccessChecker accessChecker,
        OrganizationVisibilityService organizationVisibilityService
    ) {
        this.dimensionRepository = dimensionRepository;
        this.itemRepository = itemRepository;
        this.accessChecker = accessChecker;
        this.organizationVisibilityService = organizationVisibilityService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID dimensionId, String keyword, String activeDept) {
        activeDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary dimension = dimensionRepository.findById(dimensionId).orElseThrow();
        ensureCanReadDimension(dimension, activeDept);

        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        return itemRepository
            .findByDimensionOrderBySortOrderAscCreatedDateAsc(dimension)
            .stream()
            .filter(it -> kw == null || contains(it.getCode(), kw) || contains(it.getName(), kw))
            .map(this::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> tree(UUID dimensionId, String activeDept) {
        activeDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary dimension = dimensionRepository.findById(dimensionId).orElseThrow();
        ensureCanReadDimension(dimension, activeDept);

        List<GovDimensionItem> all = itemRepository.findByDimensionOrderBySortOrderAscCreatedDateAsc(dimension);
        Map<UUID, Map<String, Object>> nodeMap = new LinkedHashMap<>();
        for (GovDimensionItem item : all) {
            Map<String, Object> dto = toDto(item);
            dto.put("children", new ArrayList<>());
            nodeMap.put(item.getId(), dto);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (GovDimensionItem item : all) {
            UUID id = item.getId();
            if (id == null) continue;
            GovDimensionItem parent = item.getParent();
            UUID parentId = parent != null ? parent.getId() : null;
            if (parentId != null && nodeMap.containsKey(parentId)) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) nodeMap.get(parentId).get("children");
                children.add(nodeMap.get(id));
            } else {
                roots.add(nodeMap.get(id));
            }
        }
        sortTree(roots);
        return roots;
    }

    public Map<String, Object> create(UUID dimensionId, String activeDept, DimensionItemUpsertRequest request) {
        activeDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary dimension = dimensionRepository.findById(dimensionId).orElseThrow();
        ensureCanManageDimension(dimension, activeDept);
        validateUpsert(dimension, null, request);

        GovDimensionItem entity = new GovDimensionItem();
        entity.setDimension(dimension);
        apply(entity, dimension, request);
        entity.setStatus(StringUtils.hasText(entity.getStatus()) ? entity.getStatus() : "ACTIVE");
        GovDimensionItem saved = itemRepository.save(entity);
        return toDto(saved);
    }

    public Map<String, Object> update(UUID dimensionId, UUID itemId, String activeDept, DimensionItemUpsertRequest request) {
        activeDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary dimension = dimensionRepository.findById(dimensionId).orElseThrow();
        ensureCanManageDimension(dimension, activeDept);
        GovDimensionItem entity = itemRepository.findById(itemId).orElseThrow();
        if (entity.getDimension() == null || entity.getDimension().getId() == null || !entity.getDimension().getId().equals(dimension.getId())) {
            throw new IllegalArgumentException("item does not belong to dimension");
        }
        validateUpsert(dimension, entity.getId(), request);
        apply(entity, dimension, request);
        GovDimensionItem saved = itemRepository.save(entity);
        return toDto(saved);
    }

    public void delete(UUID dimensionId, UUID itemId, String activeDept) {
        activeDept = resolveTrustedActiveDept(activeDept);
        GovDimensionDictionary dimension = dimensionRepository.findById(dimensionId).orElseThrow();
        ensureCanManageDimension(dimension, activeDept);
        GovDimensionItem entity = itemRepository.findById(itemId).orElseThrow();
        if (entity.getDimension() == null || entity.getDimension().getId() == null || !entity.getDimension().getId().equals(dimension.getId())) {
            throw new IllegalArgumentException("item does not belong to dimension");
        }
        // Block delete if has children (force flag can be added later)
        boolean hasChild = itemRepository
            .findByDimensionOrderBySortOrderAscCreatedDateAsc(dimension)
            .stream()
            .anyMatch(it -> it.getParent() != null && Objects.equals(it.getParent().getId(), entity.getId()));
        if (hasChild) {
            throw new IllegalStateException("存在子节点，无法删除");
        }
        itemRepository.delete(entity);
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

    private void apply(GovDimensionItem entity, GovDimensionDictionary dimension, DimensionItemUpsertRequest request) {
        entity.setCode(normalizeRequired(request.code(), "字典项编码不能为空"));
        entity.setName(normalizeRequired(request.name(), "字典项名称不能为空"));
        entity.setSortOrder(request.sortOrder());
        entity.setStatus(normalizeStatus(request.status()));
        entity.setAttributesJson(normalizeText(request.attributesJson()));
        entity.setNotes(normalizeText(request.notes()));

        UUID parentId = request.parentId();
        if (parentId == null) {
            entity.setParent(null);
        } else {
            GovDimensionItem parent = itemRepository.findById(parentId).orElseThrow(() -> new IllegalArgumentException("父节点不存在"));
            if (parent.getDimension() == null || parent.getDimension().getId() == null || !parent.getDimension().getId().equals(dimension.getId())) {
                throw new IllegalArgumentException("父节点不属于当前维度");
            }
            if (entity.getId() != null && entity.getId().equals(parentId)) {
                throw new IllegalArgumentException("父节点不能是自身");
            }
            entity.setParent(parent);
        }
    }

    private void validateUpsert(GovDimensionDictionary dimension, UUID existingId, DimensionItemUpsertRequest request) {
        if (dimension == null || dimension.getId() == null) throw new IllegalArgumentException("Invalid dimension");
        if (request == null) throw new IllegalArgumentException("Invalid payload");
        String code = normalizeRequired(request.code(), "字典项编码不能为空");
        itemRepository.findFirstByDimensionAndCodeIgnoreCase(dimension, code).ifPresent(existing -> {
            if (existingId == null || existing.getId() == null || !existing.getId().equals(existingId)) {
                throw new IllegalArgumentException("字典项编码已存在");
            }
        });
    }

    private void ensureCanReadDimension(GovDimensionDictionary dimension, String activeDept) {
        if (dimension == null) {
            throw new IllegalArgumentException("dimension not found");
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return;
        }
        if (!deptAllowed(dimension, activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
        if (!levelAllowed(dimension)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied for dimension");
        }
    }

    private void ensureCanManageDimension(GovDimensionDictionary dimension, String activeDept) {
        ensureCanReadDimension(dimension, activeDept);
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)) {
            return;
        }
        // For department roles, require activeDept to match
        if (!StringUtils.hasText(activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Missing active department context");
        }
        if (!DepartmentUtils.matches(dimension.getOwnerDept(), activeDept)) {
            throw new org.springframework.security.access.AccessDeniedException("Invalid department context");
        }
    }

    private boolean deptAllowed(GovDimensionDictionary dim, String activeDept) {
        if (dim == null) return false;
        if (!StringUtils.hasText(activeDept)) {
            return isGlobalOrRoot(dim.getOwnerDept());
        }
        if (isGlobalOrRoot(dim.getOwnerDept())) {
            return true;
        }
        return DepartmentUtils.matches(dim.getOwnerDept(), activeDept);
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

    private boolean levelAllowed(GovDimensionDictionary dim) {
        DataLevel resource = DataLevel.normalize(dim.getDataLevel());
        if (resource == null) resource = DataLevel.DATA_INTERNAL;
        int maxRank = accessChecker.resolveHighestDataLevel().rank();
        return resource.rank() <= maxRank;
    }

    private Map<String, Object> toDto(GovDimensionItem item) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (item == null) return dto;
        if (item.getId() != null) dto.put("id", item.getId().toString());
        dto.put("code", item.getCode());
        dto.put("name", item.getName());
        dto.put("status", item.getStatus());
        dto.put("sortOrder", item.getSortOrder());
        dto.put("attributesJson", item.getAttributesJson());
        dto.put("notes", item.getNotes());
        dto.put("parentId", item.getParent() != null && item.getParent().getId() != null ? item.getParent().getId().toString() : null);
        dto.put("createdBy", item.getCreatedBy());
        dto.put("createdDate", item.getCreatedDate());
        return dto;
    }

    private void sortTree(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        nodes.sort(Comparator.comparingInt(m -> asInt(m.get("sortOrder"), 0)));
        for (Map<String, Object> node : nodes) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            if (children != null && !children.isEmpty()) {
                sortTree(children);
            }
        }
    }

    private int asInt(Object value, int fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private boolean contains(String value, String keywordLower) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keywordLower)) return false;
        return value.toLowerCase(Locale.ROOT).contains(keywordLower);
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value, String message) {
        String out = normalizeText(value);
        if (!StringUtils.hasText(out)) {
            throw new IllegalArgumentException(message);
        }
        return out;
    }

    private String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return "ACTIVE";
        }
        String trimmed = value.trim().toUpperCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return "ACTIVE";
        }
        if (!"ACTIVE".equals(trimmed) && !"INACTIVE".equals(trimmed)) {
            return "ACTIVE";
        }
        return trimmed;
    }

    public record DimensionItemUpsertRequest(UUID parentId, String code, String name, String status, Integer sortOrder, String attributesJson, String notes) {}
}
