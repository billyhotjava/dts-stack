package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.GovReferenceImportRun;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.repository.governance.GovReferenceImportRunRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.service.modeling.ModelingAssetReferenceService;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeDirectoryDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeItemDto;
import com.yuzhi.dts.platform.service.governance.dto.ReferenceCodeMappingDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
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
public class ReferenceCodeService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final StdCodeDirectoryRepository directoryRepository;
    private final StdCodeValueRepository valueRepository;
    private final StdCodeMappingRepository mappingRepository;
    private final GovReferenceImportRunRepository importRunRepository;
    private final ReferenceCodeSecurity security;
    private final ObjectMapper objectMapper;
    private final ModelingAssetReferenceService referenceService;
    private final ConcurrentHashMap<String, ReentrantLock> importLocks = new ConcurrentHashMap<>();

    public ReferenceCodeService(
        StdCodeDirectoryRepository directoryRepository,
        StdCodeValueRepository valueRepository,
        StdCodeMappingRepository mappingRepository,
        GovReferenceImportRunRepository importRunRepository,
        ReferenceCodeSecurity security,
        ObjectMapper objectMapper,
        ModelingAssetReferenceService referenceService
    ) {
        this.directoryRepository = directoryRepository;
        this.valueRepository = valueRepository;
        this.mappingRepository = mappingRepository;
        this.importRunRepository = importRunRepository;
        this.security = security;
        this.objectMapper = objectMapper;
        this.referenceService = referenceService;
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

    /**
     * Bounded relationship-graph owner lookup. This intentionally avoids the item-count mapper,
     * which would issue one count query per directory.
     */
    @Transactional(readOnly = true)
    public List<RelationshipGraphReferenceCode> listForRelationshipGraph(
        Collection<String> codeTypeIds,
        String activeDept,
        int limit
    ) {
        if (codeTypeIds == null || codeTypeIds.isEmpty() || limit < 1) return List.of();
        int boundedLimit = Math.min(limit, 500);
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        int inspected = 0;
        for (String codeTypeId : codeTypeIds) {
            if (++inspected > boundedLimit || unique.size() >= boundedLimit) break;
            String normalized = normalize(codeTypeId);
            if (normalized != null) unique.add(normalized);
        }
        List<String> boundedIds = unique.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        if (boundedIds.isEmpty()) return List.of();
        return directoryRepository
            .findAllById(boundedIds)
            .stream()
            .filter(Objects::nonNull)
            .filter(directory -> security.canAccessDept(directory.getOwnerDept(), activeDept))
            .filter(directory -> Integer.valueOf(1).equals(directory.getStatus()))
            .sorted(
                Comparator
                    .comparing(ReferenceCodeService::referenceCodeLabel, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(StdCodeDirectory::getCodeTypeId)
            )
            .limit(boundedLimit)
            .map(directory ->
                new RelationshipGraphReferenceCode(
                    directory.getCodeTypeId(),
                    directory.getCodeTypeCode(),
                    directory.getCodeTypeName(),
                    directory.getStatus(),
                    directory.getVersion()
                )
            )
            .toList();
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
        Map<String, Object> references = referenceService.referenceCodeReferences(
            entity.getCodeTypeId(),
            entity.getCodeTypeCode(),
            entity.getCodeTypeName()
        );
        int impact = referenceService.countReferences(references);
        if (impact > 0) {
            String summary = referenceService.summarizeReferences(references, 5);
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "存在引用依赖，无法删除（影响对象 " + impact + " 个）" + (summary.isBlank() ? "" : "：" + summary)
            );
        }
        directoryRepository.delete(entity);
    }

    public Map<String, Object> references(String codeTypeId, String activeDept) {
        StdCodeDirectory entity = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(entity, activeDept);
        return referenceService.referenceCodeReferences(
            entity.getCodeTypeId(),
            entity.getCodeTypeCode(),
            entity.getCodeTypeName()
        );
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

    public Map<String, Object> previewStructuredImport(
        String codeTypeId,
        ReferenceCodeStructuredImportRequest request,
        String activeDept,
        String actor
    ) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        List<ReferenceCodeStructuredRow> rows = normalizeRows(request.rows());
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("导入内容不能为空");
        }

        Map<String, StdCodeValue> existingMap = new LinkedHashMap<>();
        for (StdCodeValue value : valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(codeTypeId)) {
            existingMap.put(normalize(value.getCodeValue()), value);
        }

        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        int createCount = 0;
        int updateCount = 0;
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ReferenceCodeStructuredRow row = rows.get(i);
            String codeValue = normalize(row.codeValue());
            String codeName = normalize(row.codeName());
            if (!StringUtils.hasText(codeValue) || !StringUtils.hasText(codeName)) {
                errors.add(Map.of("line", i + 1, "reason", "codeValue/codeName 不能为空"));
                continue;
            }
            seen.put(codeValue, seen.getOrDefault(codeValue, 0) + 1);
            StdCodeValue existing = existingMap.get(codeValue);
            if (existing == null) {
                createCount++;
                continue;
            }
            boolean changed = !Objects.equals(normalize(existing.getCodeName()), codeName) ||
                !Objects.equals(normalize(existing.getDescription()), normalize(row.description())) ||
                !Objects.equals(existing.getSortNum(), row.sortNum()) ||
                !Objects.equals(normalize(existing.getParentCode()), normalize(row.parentCode())) ||
                !Objects.equals(Boolean.TRUE.equals(existing.getIsDefault()), Boolean.TRUE.equals(row.isDefault()));
            if (changed) {
                updateCount++;
                conflicts.add(
                    Map.of(
                        "codeValue",
                        codeValue,
                        "currentName",
                        String.valueOf(existing.getCodeName()),
                        "incomingName",
                        String.valueOf(codeName),
                        "reason",
                        "已存在且字段存在差异"
                    )
                );
            }
        }
        for (Map.Entry<String, Integer> entry : seen.entrySet()) {
            if (entry.getValue() > 1) {
                errors.add(Map.of("codeValue", entry.getKey(), "reason", "导入文件内重复"));
            }
        }

        String conflictPolicy = normalizeConflictPolicy(request.conflictPolicy());
        boolean strictMode = "STRICT".equals(conflictPolicy);
        Map<String, Object> preview = new LinkedHashMap<>();
        preview.put("codeTypeId", codeTypeId);
        preview.put("total", rows.size());
        preview.put("valid", rows.size() - errors.size());
        preview.put("createCount", createCount);
        preview.put("updateCount", updateCount);
        preview.put("conflictCount", conflicts.size());
        preview.put("errorCount", errors.size());
        preview.put("conflictPolicy", conflictPolicy);
        preview.put("strictMode", strictMode);
        preview.put("conflicts", conflicts);
        preview.put("errors", errors);

        GovReferenceImportRun run = new GovReferenceImportRun();
        run.setCodeTypeId(codeTypeId);
        run.setImportMode("STRUCTURED");
        run.setConflictPolicy(conflictPolicy);
        run.setStatus("PREVIEWED");
        run.setSummary("结构化导入预检");
        run.setPreviewJson(writeJson(preview));
        run.setBeforeSnapshotJson(writeJson(snapshotRows(valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(codeTypeId))));
        run.setCreatedBy(StringUtils.hasText(actor) ? actor : "system");
        importRunRepository.save(run);
        preview.put("runId", run.getId().toString());
        return preview;
    }

    public Map<String, Object> applyStructuredImport(
        String codeTypeId,
        ReferenceCodeStructuredImportApplyRequest request,
        String activeDept,
        String actor
    ) {
        ReentrantLock lock = importLocks.computeIfAbsent(codeTypeId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "[GOV_REFERENCE_IMPORT_BUSY] 该码表正在执行导入任务，请稍后重试");
        }
        try {
            StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
                .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
            ensureDeptAccess(directory, activeDept);
            List<ReferenceCodeStructuredRow> rows = normalizeRows(request.rows());
            if (rows.isEmpty()) {
                throw new IllegalArgumentException("导入内容不能为空");
            }
            String conflictPolicy = normalizeConflictPolicy(request.conflictPolicy());

            Map<String, Object> preview = previewStructuredImport(
                codeTypeId,
                new ReferenceCodeStructuredImportRequest(conflictPolicy, rows),
                activeDept,
                actor
            );
            int errorCount = ((Number) preview.getOrDefault("errorCount", 0)).intValue();
            int conflictCount = ((Number) preview.getOrDefault("conflictCount", 0)).intValue();
            if (errorCount > 0) {
                throw new IllegalArgumentException("导入存在非法记录，请先修复");
            }
            if ("STRICT".equals(conflictPolicy) && conflictCount > 0) {
                throw new IllegalArgumentException("STRICT 模式下不允许存在冲突记录");
            }

            List<StdCodeValue> before = valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(codeTypeId);
            Map<String, StdCodeValue> existing = new LinkedHashMap<>();
            for (StdCodeValue value : before) {
                existing.put(normalize(value.getCodeValue()), value);
            }

            int created = 0;
            int updated = 0;
            int skipped = 0;
            for (ReferenceCodeStructuredRow row : rows) {
                String codeValue = normalize(row.codeValue());
                String codeName = normalize(row.codeName());
                if (!StringUtils.hasText(codeValue) || !StringUtils.hasText(codeName)) {
                    continue;
                }
                StdCodeValue current = existing.get(codeValue);
                if (current == null) {
                    StdCodeValue value = new StdCodeValue();
                    value.setCodeTypeId(codeTypeId);
                    value.setCodeValue(codeValue);
                    value.setCodeName(codeName);
                    value.setDescription(normalize(row.description()));
                    value.setSortNum(row.sortNum());
                    value.setParentCode(normalize(row.parentCode()));
                    value.setIsDefault(row.isDefault());
                    valueRepository.save(value);
                    existing.put(codeValue, value);
                    created++;
                    continue;
                }
                if ("SKIP".equals(conflictPolicy)) {
                    skipped++;
                    continue;
                }
                current.setCodeName(codeName);
                current.setDescription(normalize(row.description()));
                current.setSortNum(row.sortNum());
                current.setParentCode(normalize(row.parentCode()));
                current.setIsDefault(row.isDefault());
                valueRepository.save(current);
                updated++;
            }

            List<StdCodeValue> after = valueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(codeTypeId);
            GovReferenceImportRun run = new GovReferenceImportRun();
            run.setCodeTypeId(codeTypeId);
            run.setImportMode("STRUCTURED");
            run.setConflictPolicy(conflictPolicy);
            run.setStatus("APPLIED");
            run.setSummary("结构化导入执行完成");
            run.setPreviewJson(writeJson(preview));
            run.setBeforeSnapshotJson(writeJson(snapshotRows(before)));
            run.setAfterSnapshotJson(writeJson(snapshotRows(after)));
            run.setCreatedBy(StringUtils.hasText(actor) ? actor : "system");
            importRunRepository.save(run);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getId().toString());
            result.put("created", created);
            result.put("updated", updated);
            result.put("skipped", skipped);
            result.put("totalAfter", after.size());
            result.put("conflictPolicy", conflictPolicy);
            return result;
        } finally {
            lock.unlock();
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listStructuredImportRuns(String codeTypeId, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        return importRunRepository
            .findByCodeTypeIdOrderByCreatedDateDesc(codeTypeId)
            .stream()
            .map(this::toImportRunSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> importOpsOverview(int hours, String activeDept) {
        long startedAt = System.nanoTime();
        int safeHours = Math.max(1, Math.min(hours <= 0 ? 168 : hours, 24 * 30));
        Instant since = Instant.now().minusSeconds(safeHours * 3600L);
        List<String> codeTypeIds = directoryRepository
            .findAll(buildSpec(null, activeDept))
            .stream()
            .map(StdCodeDirectory::getCodeTypeId)
            .filter(StringUtils::hasText)
            .toList();
        if (codeTypeIds.isEmpty()) {
            return Map.of(
                "windowHours",
                safeHours,
                "directoryCount",
                0,
                "totalRuns",
                0,
                "appliedRuns",
                0,
                "rolledBackRuns",
                0,
                "previewRuns",
                0,
                "conflictTotal",
                0,
                "errorTotal",
                0,
                "failureTop",
                List.of(),
                "queryCostMs",
                elapsedMs(startedAt)
            );
        }
        List<GovReferenceImportRun> runs = importRunRepository.findByCodeTypeIdInAndCreatedDateGreaterThanEqualOrderByCreatedDateDesc(codeTypeIds, since);
        int applied = 0;
        int rolledBack = 0;
        int preview = 0;
        int conflictTotal = 0;
        int errorTotal = 0;
        Map<String, Integer> failureTop = new LinkedHashMap<>();
        for (GovReferenceImportRun run : runs) {
            String status = normalize(run.getStatus());
            if ("APPLIED".equalsIgnoreCase(status)) {
                applied++;
            } else if ("ROLLED_BACK".equalsIgnoreCase(status)) {
                rolledBack++;
            } else if ("PREVIEWED".equalsIgnoreCase(status)) {
                preview++;
            }
            Map<String, Object> previewMap = parseMap(run.getPreviewJson());
            int conflicts = numberAsInt(previewMap.get("conflictCount"));
            int errors = numberAsInt(previewMap.get("errorCount"));
            conflictTotal += conflicts;
            errorTotal += errors;
            if (errors > 0) {
                failureTop.merge("preview_error_rows", errors, Integer::sum);
            }
            if ("STRICT".equalsIgnoreCase(run.getConflictPolicy()) && conflicts > 0) {
                failureTop.merge("strict_conflicts", conflicts, Integer::sum);
            }
        }
        List<Map<String, Object>> failureRows = failureTop
            .entrySet()
            .stream()
            .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
            .limit(5)
            .map(entry -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("category", entry.getKey());
                row.put("count", entry.getValue());
                return row;
            })
            .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("windowHours", safeHours);
        payload.put("directoryCount", codeTypeIds.size());
        payload.put("totalRuns", runs.size());
        payload.put("appliedRuns", applied);
        payload.put("rolledBackRuns", rolledBack);
        payload.put("previewRuns", preview);
        payload.put("conflictTotal", conflictTotal);
        payload.put("errorTotal", errorTotal);
        payload.put("failureTop", failureRows);
        payload.put("queryCostMs", elapsedMs(startedAt));
        return payload;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getStructuredImportRunDetail(String codeTypeId, UUID runId, String activeDept) {
        StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
            .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
        ensureDeptAccess(directory, activeDept);
        GovReferenceImportRun run = importRunRepository.findById(runId)
            .orElseThrow(() -> new EntityNotFoundException("导入批次不存在"));
        if (!codeTypeId.equals(run.getCodeTypeId())) {
            throw new IllegalArgumentException("导入批次不属于当前码表");
        }
        List<Map<String, Object>> beforeRows = parseRows(run.getBeforeSnapshotJson());
        List<Map<String, Object>> afterRows = parseRows(run.getAfterSnapshotJson());
        List<Map<String, Object>> diffRows = buildImportDiff(beforeRows, afterRows);
        Map<String, Object> payload = new LinkedHashMap<>(toImportRunSummary(run));
        payload.put("preview", parseMap(run.getPreviewJson()));
        payload.put("beforeCount", beforeRows.size());
        payload.put("afterCount", afterRows.size());
        payload.put("diffCount", diffRows.size());
        payload.put("diffRows", diffRows);
        payload.put("beforeSample", beforeRows.stream().limit(20).toList());
        payload.put("afterSample", afterRows.stream().limit(20).toList());
        return payload;
    }

    public Map<String, Object> rollbackStructuredImport(String codeTypeId, UUID runId, String activeDept) {
        ReentrantLock lock = importLocks.computeIfAbsent(codeTypeId, key -> new ReentrantLock());
        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "[GOV_REFERENCE_IMPORT_BUSY] 该码表正在执行导入任务，请稍后重试");
        }
        try {
            StdCodeDirectory directory = directoryRepository.findById(codeTypeId)
                .orElseThrow(() -> new EntityNotFoundException("码表不存在"));
            ensureDeptAccess(directory, activeDept);
            GovReferenceImportRun run = importRunRepository.findById(runId)
                .orElseThrow(() -> new EntityNotFoundException("导入批次不存在"));
            if (!codeTypeId.equals(run.getCodeTypeId())) {
                throw new IllegalArgumentException("导入批次不属于当前码表");
            }
            if ("ROLLED_BACK".equalsIgnoreCase(run.getStatus())) {
                return Map.of(
                    "runId",
                    runId.toString(),
                    "restoredCount",
                    valueRepository.countByCodeTypeId(codeTypeId),
                    "status",
                    "ROLLED_BACK",
                    "idempotent",
                    true
                );
            }
            List<Map<String, Object>> beforeRows = parseRows(run.getBeforeSnapshotJson());
            valueRepository.deleteByCodeTypeId(codeTypeId);
            for (Map<String, Object> row : beforeRows) {
                StdCodeValue value = new StdCodeValue();
                value.setCodeTypeId(codeTypeId);
                value.setCodeValue(normalize(String.valueOf(row.get("codeValue"))));
                value.setCodeName(normalize(String.valueOf(row.get("codeName"))));
                value.setDescription(normalize((String) row.get("description")));
                value.setSortNum(row.get("sortNum") == null ? null : Integer.valueOf(String.valueOf(row.get("sortNum"))));
                value.setParentCode(normalize((String) row.get("parentCode")));
                value.setIsDefault(row.get("isDefault") == null ? null : Boolean.valueOf(String.valueOf(row.get("isDefault"))));
                valueRepository.save(value);
            }
            run.setStatus("ROLLED_BACK");
            run.setSummary("已回滚到导入前快照");
            importRunRepository.save(run);

            return Map.of("runId", runId.toString(), "restoredCount", beforeRows.size(), "status", "ROLLED_BACK");
        } finally {
            lock.unlock();
        }
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

    private static String referenceCodeLabel(StdCodeDirectory directory) {
        if (StringUtils.hasText(directory.getCodeTypeName())) return directory.getCodeTypeName().trim();
        if (StringUtils.hasText(directory.getCodeTypeCode())) return directory.getCodeTypeCode().trim();
        return directory.getCodeTypeId() == null ? "" : directory.getCodeTypeId();
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

    private String normalizeConflictPolicy(String conflictPolicy) {
        String value = normalize(conflictPolicy);
        if (!StringUtils.hasText(value)) return "MERGE";
        String upper = value.toUpperCase(Locale.ROOT);
        if (!List.of("STRICT", "MERGE", "SKIP").contains(upper)) {
            throw new IllegalArgumentException("冲突策略仅支持 STRICT/MERGE/SKIP");
        }
        return upper;
    }

    private List<ReferenceCodeStructuredRow> normalizeRows(List<ReferenceCodeStructuredRow> rows) {
        if (rows == null) return List.of();
        return rows
            .stream()
            .filter(Objects::nonNull)
            .map(row ->
                new ReferenceCodeStructuredRow(
                    normalize(row.codeValue()),
                    normalize(row.codeName()),
                    normalize(row.description()),
                    row.sortNum(),
                    normalize(row.parentCode()),
                    row.isDefault()
                )
            )
            .sorted(Comparator.comparing(row -> String.valueOf(row.codeValue())))
            .toList();
    }

    private List<Map<String, Object>> snapshotRows(List<StdCodeValue> values) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (StdCodeValue value : values) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("codeValue", value.getCodeValue());
            row.put("codeName", value.getCodeName());
            row.put("description", value.getDescription());
            row.put("sortNum", value.getSortNum());
            row.put("parentCode", value.getParentCode());
            row.put("isDefault", value.getIsDefault());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> parseRows(String json) {
        try {
            if (!StringUtils.hasText(json)) return List.of();
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("导入快照数据解析失败");
        }
    }

    private Map<String, Object> parseMap(String json) {
        try {
            if (!StringUtils.hasText(json)) return Map.of();
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private Map<String, Object> toImportRunSummary(GovReferenceImportRun run) {
        Map<String, Object> preview = parseMap(run.getPreviewJson());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("runId", run.getId() != null ? run.getId().toString() : null);
        row.put("codeTypeId", run.getCodeTypeId());
        row.put("importMode", run.getImportMode());
        row.put("conflictPolicy", run.getConflictPolicy());
        row.put("status", run.getStatus());
        row.put("summary", run.getSummary());
        row.put("createdBy", run.getCreatedBy());
        row.put("createdDate", run.getCreatedDate());
        row.put("previewTotal", numberAsInt(preview.get("total")));
        row.put("previewValid", numberAsInt(preview.get("valid")));
        row.put("createCount", numberAsInt(preview.get("createCount")));
        row.put("updateCount", numberAsInt(preview.get("updateCount")));
        row.put("conflictCount", numberAsInt(preview.get("conflictCount")));
        row.put("errorCount", numberAsInt(preview.get("errorCount")));
        row.put("rollbackable", "APPLIED".equalsIgnoreCase(run.getStatus()));
        return row;
    }

    private Integer numberAsInt(Object raw) {
        if (raw == null) return 0;
        if (raw instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private List<Map<String, Object>> buildImportDiff(
        List<Map<String, Object>> beforeRows,
        List<Map<String, Object>> afterRows
    ) {
        Map<String, Map<String, Object>> beforeMap = new LinkedHashMap<>();
        for (Map<String, Object> row : beforeRows) {
            String key = normalize(row == null ? null : String.valueOf(row.get("codeValue")));
            if (StringUtils.hasText(key)) {
                beforeMap.put(key, row);
            }
        }
        Map<String, Map<String, Object>> afterMap = new LinkedHashMap<>();
        for (Map<String, Object> row : afterRows) {
            String key = normalize(row == null ? null : String.valueOf(row.get("codeValue")));
            if (StringUtils.hasText(key)) {
                afterMap.put(key, row);
            }
        }
        List<Map<String, Object>> diff = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : afterMap.entrySet()) {
            String codeValue = entry.getKey();
            Map<String, Object> after = entry.getValue();
            Map<String, Object> before = beforeMap.get(codeValue);
            if (before == null) {
                diff.add(buildDiffRow("ADDED", codeValue, null, after));
                continue;
            }
            if (!Objects.equals(writeJson(before), writeJson(after))) {
                diff.add(buildDiffRow("UPDATED", codeValue, before, after));
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : beforeMap.entrySet()) {
            String codeValue = entry.getKey();
            if (!afterMap.containsKey(codeValue)) {
                diff.add(buildDiffRow("REMOVED", codeValue, entry.getValue(), null));
            }
        }
        return diff;
    }

    private Map<String, Object> buildDiffRow(
        String changeType,
        String codeValue,
        Map<String, Object> before,
        Map<String, Object> after
    ) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("changeType", changeType);
        row.put("codeValue", codeValue);
        row.put("before", before);
        row.put("after", after);
        return row;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("导入快照序列化失败");
        }
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

    public record RelationshipGraphReferenceCode(
        String codeTypeId,
        String codeTypeCode,
        String codeTypeName,
        Integer status,
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

    public record ReferenceCodeStructuredRow(
        String codeValue,
        String codeName,
        String description,
        Integer sortNum,
        String parentCode,
        Boolean isDefault
    ) {}

    public record ReferenceCodeStructuredImportRequest(String conflictPolicy, List<ReferenceCodeStructuredRow> rows) {}

    public record ReferenceCodeStructuredImportApplyRequest(String conflictPolicy, List<ReferenceCodeStructuredRow> rows) {}
}
