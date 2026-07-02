package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.governance.StdCodeDirectory;
import com.yuzhi.dts.platform.domain.governance.StdCodeMapping;
import com.yuzhi.dts.platform.domain.governance.StdCodeValue;
import com.yuzhi.dts.platform.domain.modeling.DataSecurityLevel;
import com.yuzhi.dts.platform.domain.modeling.MetadataStandard;
import com.yuzhi.dts.platform.domain.modeling.ModelingGlossaryTerm;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRun;
import com.yuzhi.dts.platform.domain.modeling.StandardPackageImportRunItem;
import com.yuzhi.dts.platform.repository.governance.StdCodeDirectoryRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeMappingRepository;
import com.yuzhi.dts.platform.repository.governance.StdCodeValueRepository;
import com.yuzhi.dts.platform.repository.modeling.MetadataStandardRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingGlossaryTermRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunItemRepository;
import com.yuzhi.dts.platform.repository.modeling.StandardPackageImportRunRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 标准包 apply/rollback：读取 preview 落库的 payload_json，单事务按依赖序入库
 * （术语 → 码表目录 → 码表值 → 数据元 → 映射），逐条记录 before-image 供整包回滚。
 */
@Service
@Transactional
public class StandardPackageApplyService {

    public static final String STATUS_APPLIED = "APPLIED";
    public static final String STATUS_ROLLED_BACK = "ROLLED_BACK";

    private static final String TYPE_TERM = "TERM";
    private static final String TYPE_ELEMENT = "ELEMENT";
    private static final String TYPE_CODE_DIRECTORY = "CODE_DIRECTORY";
    private static final String TYPE_CODE_VALUE = "CODE_VALUE";
    private static final String TYPE_CODE_MAPPING = "CODE_MAPPING";

    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_UPDATE = "UPDATE";

    private final StandardPackageImportRunRepository runRepository;
    private final StandardPackageImportRunItemRepository runItemRepository;
    private final ModelingGlossaryTermRepository glossaryTermRepository;
    private final MetadataStandardRepository metadataStandardRepository;
    private final MetadataStandardService metadataStandardService;
    private final StdCodeDirectoryRepository codeDirectoryRepository;
    private final StdCodeValueRepository codeValueRepository;
    private final StdCodeMappingRepository codeMappingRepository;
    private final ObjectMapper objectMapper;

    public StandardPackageApplyService(
        StandardPackageImportRunRepository runRepository,
        StandardPackageImportRunItemRepository runItemRepository,
        ModelingGlossaryTermRepository glossaryTermRepository,
        MetadataStandardRepository metadataStandardRepository,
        MetadataStandardService metadataStandardService,
        StdCodeDirectoryRepository codeDirectoryRepository,
        StdCodeValueRepository codeValueRepository,
        StdCodeMappingRepository codeMappingRepository,
        ObjectMapper objectMapper
    ) {
        this.runRepository = runRepository;
        this.runItemRepository = runItemRepository;
        this.glossaryTermRepository = glossaryTermRepository;
        this.metadataStandardRepository = metadataStandardRepository;
        this.metadataStandardService = metadataStandardService;
        this.codeDirectoryRepository = codeDirectoryRepository;
        this.codeValueRepository = codeValueRepository;
        this.codeMappingRepository = codeMappingRepository;
        this.objectMapper = objectMapper;
    }

    // ---------- apply ----------

    public Map<String, Object> apply(UUID runId, String actor) {
        StandardPackageImportRun run = runRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("导入记录不存在"));
        if (STATUS_APPLIED.equals(run.getStatus())) {
            throw new IllegalArgumentException("该导入已应用，不可重复应用");
        }
        if (!StandardPackageImportService.STATUS_PREVIEWED.equals(run.getStatus())) {
            throw new IllegalArgumentException("仅 PREVIEWED 状态的导入可应用，当前状态：" + run.getStatus());
        }
        Map<String, Object> preview = readJsonMap(run.getPreviewJson());
        if (Boolean.TRUE.equals(preview.get("blocking"))) {
            throw new IllegalArgumentException("校验报告存在错误，请修复后重新上传预检");
        }
        Map<String, Object> payload = readJsonMap(run.getPayloadJson());

        ApplyContext ctx = new ApplyContext(run.getId());

        applyTerms(listOf(payload, "terms"), ctx);
        applyCodeDirectories(listOf(payload, "codeDirectories"), ctx);
        applyCodeItems(listOf(payload, "codeItems"), ctx);
        applyElements(listOf(payload, "elements"), ctx);
        applyCodeMappings(listOf(payload, "codeMappings"), ctx);

        runItemRepository.saveAll(ctx.items);
        run.setStatus(STATUS_APPLIED);
        run.setSummary("标准包已应用：新增 " + ctx.totalCreated() + "，更新 " + ctx.totalUpdated());
        runRepository.save(run);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId().toString());
        result.put("status", run.getStatus());
        result.put("created", ctx.createdByType);
        result.put("updated", ctx.updatedByType);
        result.put("totalCreated", ctx.totalCreated());
        result.put("totalUpdated", ctx.totalUpdated());
        return result;
    }

    private void applyTerms(List<Map<String, Object>> terms, ApplyContext ctx) {
        for (Map<String, Object> term : terms) {
            String code = asString(term.get("code"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            ModelingGlossaryTerm existing = glossaryTermRepository
                .findByCodeLowerIn(Set.of(code.toLowerCase(Locale.ROOT)))
                .stream()
                .findFirst()
                .orElse(null);
            if (existing != null) {
                ctx.record(TYPE_TERM, String.valueOf(existing.getId()), ACTION_UPDATE, termImage(existing));
                copyTermFields(term, existing);
                glossaryTermRepository.save(existing);
                ctx.countUpdate(TYPE_TERM);
            } else {
                ModelingGlossaryTerm created = new ModelingGlossaryTerm();
                copyTermFields(term, created);
                ModelingGlossaryTerm saved = glossaryTermRepository.save(created);
                ctx.record(TYPE_TERM, String.valueOf(saved.getId()), ACTION_CREATE, null);
                ctx.countCreate(TYPE_TERM);
            }
        }
    }

    private void copyTermFields(Map<String, Object> source, ModelingGlossaryTerm target) {
        target.setCode(asString(source.get("code")));
        target.setName(asString(source.get("name")));
        target.setAliases(asString(source.get("aliases")));
        target.setDomain(asString(source.get("domain")));
        target.setDefinition(asString(source.get("definition")));
        target.setOwner(asString(source.get("owner")));
        target.setOwnerDept(asString(source.get("ownerDept")));
        target.setTags(asString(source.get("tags")));
        target.setVersionNotes(asString(source.get("versionNotes")));
        // 对齐 ModelingAuxResource.ensureGlossaryDefaults：术语作为简单台账，无状态工作流
        target.setStatus("ACTIVE");
        String version = asString(source.get("version"));
        target.setVersion(StringUtils.hasText(version) ? version.trim() : "v1");
    }

    private void applyCodeDirectories(List<Map<String, Object>> directories, ApplyContext ctx) {
        for (Map<String, Object> dir : directories) {
            String code = asString(dir.get("codeTypeCode"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            Optional<StdCodeDirectory> existing = codeDirectoryRepository.findByCodeTypeCodeIgnoreCase(code);
            if (existing.isPresent()) {
                StdCodeDirectory entity = existing.orElseThrow();
                ctx.record(TYPE_CODE_DIRECTORY, entity.getCodeTypeId(), ACTION_UPDATE, directoryImage(entity));
                copyDirectoryFields(dir, entity, false);
                codeDirectoryRepository.save(entity);
                ctx.directoryIds.put(code.toLowerCase(Locale.ROOT), entity.getCodeTypeId());
                ctx.countUpdate(TYPE_CODE_DIRECTORY);
            } else {
                StdCodeDirectory entity = new StdCodeDirectory();
                String codeTypeId = asString(dir.get("codeTypeId"));
                entity.setCodeTypeId(StringUtils.hasText(codeTypeId) ? codeTypeId : code);
                entity.setCodeTypeCode(code);
                copyDirectoryFields(dir, entity, true);
                codeDirectoryRepository.save(entity);
                ctx.record(TYPE_CODE_DIRECTORY, entity.getCodeTypeId(), ACTION_CREATE, null);
                ctx.directoryIds.put(code.toLowerCase(Locale.ROOT), entity.getCodeTypeId());
                ctx.countCreate(TYPE_CODE_DIRECTORY);
            }
        }
    }

    private void copyDirectoryFields(Map<String, Object> source, StdCodeDirectory target, boolean creating) {
        target.setCodeTypeName(asString(source.get("codeTypeName")));
        target.setStdLevel(asString(source.get("stdLevel")));
        target.setBizCatalog(asString(source.get("bizCatalog")));
        target.setDataType(asString(source.get("dataType")));
        Integer status = asInteger(source.get("status"));
        if (status != null || creating) {
            target.setStatus(status == null ? Integer.valueOf(1) : status);
        }
        String ownerDept = asString(source.get("ownerDept"));
        if (StringUtils.hasText(ownerDept) || creating) {
            target.setOwnerDept(ownerDept);
        }
        target.setVersion(asString(source.get("version")));
    }

    private void applyCodeItems(List<Map<String, Object>> items, ApplyContext ctx) {
        Map<String, Map<String, StdCodeValue>> existingByDirectory = new HashMap<>();
        for (Map<String, Object> item : items) {
            String typeCode = asString(item.get("codeTypeCode"));
            String codeTypeId = resolveDirectoryId(typeCode, ctx);
            if (codeTypeId == null) {
                continue;
            }
            Map<String, StdCodeValue> existingValues = existingByDirectory.computeIfAbsent(codeTypeId, key -> {
                Map<String, StdCodeValue> map = new LinkedHashMap<>();
                for (StdCodeValue value : codeValueRepository.findByCodeTypeIdOrderBySortNumAscCodeValueAsc(key)) {
                    map.put(value.getCodeValue(), value);
                }
                return map;
            });
            String codeValue = asString(item.get("codeValue"));
            StdCodeValue existing = existingValues.get(codeValue);
            if (existing != null) {
                ctx.record(TYPE_CODE_VALUE, String.valueOf(existing.getItemId()), ACTION_UPDATE, codeValueImage(existing));
                copyCodeValueFields(item, existing);
                codeValueRepository.save(existing);
                ctx.countUpdate(TYPE_CODE_VALUE);
            } else {
                StdCodeValue value = new StdCodeValue();
                value.setCodeTypeId(codeTypeId);
                value.setCodeValue(codeValue);
                copyCodeValueFields(item, value);
                StdCodeValue saved = codeValueRepository.save(value);
                existingValues.put(codeValue, saved);
                ctx.record(TYPE_CODE_VALUE, String.valueOf(saved.getItemId()), ACTION_CREATE, null);
                ctx.countCreate(TYPE_CODE_VALUE);
            }
        }
    }

    private void copyCodeValueFields(Map<String, Object> source, StdCodeValue target) {
        target.setCodeName(asString(source.get("codeName")));
        target.setDescription(asString(source.get("description")));
        target.setSortNum(asInteger(source.get("sortNum")));
        target.setParentCode(asString(source.get("parentCode")));
        target.setIsDefault(asBoolean(source.get("isDefault")));
    }

    private void applyElements(List<Map<String, Object>> elements, ApplyContext ctx) {
        for (Map<String, Object> element : elements) {
            MetadataStandardUpsertRequest req = toElementRequest(element);
            Optional<MetadataStandard> existing = metadataStandardRepository.findByFieldNameEnIgnoreCaseAndDomainIgnoreCase(
                req.getFieldNameEn(),
                req.getDomain()
            );
            if (existing.isPresent()) {
                MetadataStandard entity = existing.orElseThrow();
                ctx.record(TYPE_ELEMENT, String.valueOf(entity.getId()), ACTION_UPDATE, MetadataStandardCsvSupport.elementImage(entity));
                metadataStandardService.update(entity.getId(), req);
                ctx.countUpdate(TYPE_ELEMENT);
            } else {
                var dto = metadataStandardService.create(req);
                ctx.record(TYPE_ELEMENT, String.valueOf(dto.getId()), ACTION_CREATE, null);
                ctx.countCreate(TYPE_ELEMENT);
            }
        }
    }

    private MetadataStandardUpsertRequest toElementRequest(Map<String, Object> element) {
        MetadataStandardUpsertRequest req = new MetadataStandardUpsertRequest();
        req.setFieldNameCn(asString(element.get("fieldNameCn")));
        req.setFieldNameEn(asString(element.get("fieldNameEn")));
        req.setDataType(asString(element.get("dataType")));
        req.setDataLength(asInteger(element.get("dataLength")));
        req.setDataPrecision(asInteger(element.get("dataPrecision")));
        req.setDataScale(asInteger(element.get("dataScale")));
        req.setNullable(asBoolean(element.get("nullable")));
        req.setDomain(asString(element.get("domain")));
        req.setDescription(asString(element.get("description")));
        req.setSourceSystem(asString(element.get("sourceSystem")));
        req.setCodeSet(asString(element.get("codeSet")));
        req.setDefaultValue(asString(element.get("defaultValue")));
        req.setIsPk(asBoolean(element.get("isPk")));
        String securityLevel = asString(element.get("securityLevel"));
        req.setSecurityLevel(StringUtils.hasText(securityLevel) ? DataSecurityLevel.valueOf(securityLevel) : DataSecurityLevel.INTERNAL);
        return req;
    }

    private void applyCodeMappings(List<Map<String, Object>> mappings, ApplyContext ctx) {
        Map<String, List<StdCodeMapping>> existingByDirectory = new HashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String typeCode = asString(mapping.get("codeTypeCode"));
            String codeTypeId = resolveDirectoryId(typeCode, ctx);
            if (codeTypeId == null) {
                continue;
            }
            List<StdCodeMapping> existingList = existingByDirectory.computeIfAbsent(
                codeTypeId,
                key -> new ArrayList<>(codeMappingRepository.findByCodeTypeIdOrderBySourceSysAsc(key))
            );
            String sourceSystem = asString(mapping.get("sourceSystem"));
            String sourceCode = asString(mapping.get("sourceCode"));
            String standardCode = asString(mapping.get("standardCode"));
            StdCodeMapping existing = existingList
                .stream()
                .filter(m -> sourceSystem.equals(m.getSourceSys()) && sourceCode.equals(m.getSrcCode()))
                .findFirst()
                .orElse(null);
            if (existing != null) {
                ctx.record(TYPE_CODE_MAPPING, String.valueOf(existing.getMapId()), ACTION_UPDATE, Map.of("stdCode", existing.getStdCode()));
                existing.setStdCode(standardCode);
                codeMappingRepository.save(existing);
                ctx.countUpdate(TYPE_CODE_MAPPING);
            } else {
                StdCodeMapping entity = new StdCodeMapping();
                entity.setCodeTypeId(codeTypeId);
                entity.setSourceSys(sourceSystem);
                entity.setSrcCode(sourceCode);
                entity.setStdCode(standardCode);
                StdCodeMapping saved = codeMappingRepository.save(entity);
                existingList.add(saved);
                ctx.record(TYPE_CODE_MAPPING, String.valueOf(saved.getMapId()), ACTION_CREATE, null);
                ctx.countCreate(TYPE_CODE_MAPPING);
            }
        }
    }

    private String resolveDirectoryId(String codeTypeCode, ApplyContext ctx) {
        if (!StringUtils.hasText(codeTypeCode)) {
            return null;
        }
        String normalized = codeTypeCode.toLowerCase(Locale.ROOT);
        String cached = ctx.directoryIds.get(normalized);
        if (cached != null) {
            return cached;
        }
        return codeDirectoryRepository
            .findByCodeTypeCodeIgnoreCase(codeTypeCode)
            .map(directory -> {
                ctx.directoryIds.put(normalized, directory.getCodeTypeId());
                return directory.getCodeTypeId();
            })
            .orElse(null);
    }

    // ---------- rollback ----------

    public Map<String, Object> rollback(UUID runId, String actor) {
        StandardPackageImportRun run = runRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("导入记录不存在"));
        if (STATUS_ROLLED_BACK.equals(run.getStatus())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getId().toString());
            result.put("status", run.getStatus());
            result.put("message", "该导入已回滚");
            return result;
        }
        if (!STATUS_APPLIED.equals(run.getStatus())) {
            throw new IllegalArgumentException("仅 APPLIED 状态的导入可回滚，当前状态：" + run.getStatus());
        }

        int restored = 0;
        int deleted = 0;
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (StandardPackageImportRunItem item : runItemRepository.findByRunIdOrderBySeqDesc(runId)) {
            boolean handled = ACTION_CREATE.equals(item.getAction()) ? rollbackCreate(item) : rollbackUpdate(item);
            if (handled) {
                if (ACTION_CREATE.equals(item.getAction())) {
                    deleted++;
                } else {
                    restored++;
                }
            } else {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("entityType", item.getEntityType());
                skip.put("entityId", item.getEntityId());
                skip.put("action", item.getAction());
                skip.put("reason", "实体已不存在，跳过");
                skipped.add(skip);
            }
        }

        run.setStatus(STATUS_ROLLED_BACK);
        run.setSummary("标准包已回滚：删除 " + deleted + "，还原 " + restored + "，跳过 " + skipped.size());
        runRepository.save(run);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", run.getId().toString());
        result.put("status", run.getStatus());
        result.put("deleted", deleted);
        result.put("restored", restored);
        result.put("skipped", skipped);
        return result;
    }

    private boolean rollbackCreate(StandardPackageImportRunItem item) {
        try {
            switch (item.getEntityType()) {
                case TYPE_TERM -> {
                    UUID id = UUID.fromString(item.getEntityId());
                    if (!glossaryTermRepository.existsById(id)) return false;
                    glossaryTermRepository.deleteById(id);
                }
                case TYPE_ELEMENT -> {
                    UUID id = UUID.fromString(item.getEntityId());
                    if (!metadataStandardRepository.existsById(id)) return false;
                    metadataStandardService.delete(id);
                }
                case TYPE_CODE_DIRECTORY -> {
                    if (!codeDirectoryRepository.existsById(item.getEntityId())) return false;
                    codeDirectoryRepository.deleteById(item.getEntityId());
                }
                case TYPE_CODE_VALUE -> {
                    Long id = Long.valueOf(item.getEntityId());
                    if (!codeValueRepository.existsById(id)) return false;
                    codeValueRepository.deleteById(id);
                }
                case TYPE_CODE_MAPPING -> {
                    Long id = Long.valueOf(item.getEntityId());
                    if (!codeMappingRepository.existsById(id)) return false;
                    codeMappingRepository.deleteById(id);
                }
                default -> {
                    return false;
                }
            }
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean rollbackUpdate(StandardPackageImportRunItem item) {
        Map<String, Object> before = readJsonMap(item.getBeforeJson());
        switch (item.getEntityType()) {
            case TYPE_TERM -> {
                Optional<ModelingGlossaryTerm> entity = glossaryTermRepository.findById(UUID.fromString(item.getEntityId()));
                if (entity.isEmpty()) return false;
                ModelingGlossaryTerm term = entity.orElseThrow();
                term.setCode(asString(before.get("code")));
                term.setName(asString(before.get("name")));
                term.setAliases(asString(before.get("aliases")));
                term.setDomain(asString(before.get("domain")));
                term.setDefinition(asString(before.get("definition")));
                term.setStatus(asString(before.get("status")));
                term.setVersion(asString(before.get("version")));
                term.setVersionNotes(asString(before.get("versionNotes")));
                term.setOwner(asString(before.get("owner")));
                term.setOwnerDept(asString(before.get("ownerDept")));
                term.setTags(asString(before.get("tags")));
                glossaryTermRepository.save(term);
            }
            case TYPE_ELEMENT -> {
                Optional<MetadataStandard> entity = metadataStandardRepository.findById(UUID.fromString(item.getEntityId()));
                if (entity.isEmpty()) return false;
                MetadataStandard element = entity.orElseThrow();
                element.setFieldNameCn(asString(before.get("fieldNameCn")));
                element.setFieldNameEn(asString(before.get("fieldNameEn")));
                element.setDataType(asString(before.get("dataType")));
                element.setDataLength(asInteger(before.get("dataLength")));
                element.setDataPrecision(asInteger(before.get("dataPrecision")));
                element.setDataScale(asInteger(before.get("dataScale")));
                element.setNullable(asBoolean(before.get("nullable")));
                element.setDomain(asString(before.get("domain")));
                element.setDescription(asString(before.get("description")));
                element.setSourceSystem(asString(before.get("sourceSystem")));
                element.setCodeSet(asString(before.get("codeSet")));
                element.setDefaultValue(asString(before.get("defaultValue")));
                element.setIsPk(asBoolean(before.get("isPk")));
                String securityLevel = asString(before.get("securityLevel"));
                element.setSecurityLevel(StringUtils.hasText(securityLevel) ? DataSecurityLevel.valueOf(securityLevel) : null);
                metadataStandardRepository.save(element);
            }
            case TYPE_CODE_DIRECTORY -> {
                Optional<StdCodeDirectory> entity = codeDirectoryRepository.findById(item.getEntityId());
                if (entity.isEmpty()) return false;
                StdCodeDirectory directory = entity.orElseThrow();
                directory.setCodeTypeName(asString(before.get("codeTypeName")));
                directory.setStdLevel(asString(before.get("stdLevel")));
                directory.setBizCatalog(asString(before.get("bizCatalog")));
                directory.setDataType(asString(before.get("dataType")));
                directory.setStatus(asInteger(before.get("status")));
                directory.setOwnerDept(asString(before.get("ownerDept")));
                directory.setVersion(asString(before.get("version")));
                codeDirectoryRepository.save(directory);
            }
            case TYPE_CODE_VALUE -> {
                Optional<StdCodeValue> entity = codeValueRepository.findById(Long.valueOf(item.getEntityId()));
                if (entity.isEmpty()) return false;
                StdCodeValue value = entity.orElseThrow();
                value.setCodeName(asString(before.get("codeName")));
                value.setDescription(asString(before.get("description")));
                value.setSortNum(asInteger(before.get("sortNum")));
                value.setParentCode(asString(before.get("parentCode")));
                value.setIsDefault(asBoolean(before.get("isDefault")));
                codeValueRepository.save(value);
            }
            case TYPE_CODE_MAPPING -> {
                Optional<StdCodeMapping> entity = codeMappingRepository.findById(Long.valueOf(item.getEntityId()));
                if (entity.isEmpty()) return false;
                StdCodeMapping mapping = entity.orElseThrow();
                mapping.setStdCode(asString(before.get("stdCode")));
                codeMappingRepository.save(mapping);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ---------- runs ----------

    @Transactional(readOnly = true)
    public Map<String, Object> listRuns(int page, int size) {
        Page<StandardPackageImportRun> runs = runRepository.findAllByOrderByCreatedDateDesc(PageRequest.of(page, size));
        List<Map<String, Object>> content = runs.getContent().stream().map(this::runSummary).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("total", runs.getTotalElements());
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRun(UUID runId) {
        StandardPackageImportRun run = runRepository.findById(runId).orElseThrow(() -> new EntityNotFoundException("导入记录不存在"));
        Map<String, Object> detail = runSummary(run);
        detail.put("preview", readJsonMap(run.getPreviewJson()));
        detail.put("itemCount", runItemRepository.countByRunId(runId));
        return detail;
    }

    private Map<String, Object> runSummary(StandardPackageImportRun run) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", run.getId().toString());
        summary.put("packageName", run.getPackageName());
        summary.put("source", run.getSource());
        summary.put("status", run.getStatus());
        summary.put("summary", run.getSummary());
        summary.put("createdBy", run.getCreatedBy());
        summary.put("createdDate", run.getCreatedDate());
        return summary;
    }

    // ---------- images & helpers ----------

    private Map<String, Object> termImage(ModelingGlossaryTerm term) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("code", term.getCode());
        image.put("name", term.getName());
        image.put("aliases", term.getAliases());
        image.put("domain", term.getDomain());
        image.put("definition", term.getDefinition());
        image.put("status", term.getStatus());
        image.put("version", term.getVersion());
        image.put("versionNotes", term.getVersionNotes());
        image.put("owner", term.getOwner());
        image.put("ownerDept", term.getOwnerDept());
        image.put("tags", term.getTags());
        return image;
    }

    private Map<String, Object> directoryImage(StdCodeDirectory directory) {
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

    private Map<String, Object> codeValueImage(StdCodeValue value) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("codeName", value.getCodeName());
        image.put("description", value.getDescription());
        image.put("sortNum", value.getSortNum());
        image.put("parentCode", value.getParentCode());
        image.put("isDefault", value.getIsDefault());
        return image;
    }

    private Map<String, Object> readJsonMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalStateException("导入记录 JSON 解析失败");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text) {
            return MetadataStandardCsvSupport.parseBooleanYN(text);
        }
        return null;
    }

    /** 单次 apply 的上下文：目录 code→id 解析缓存、before-image 明细、计数。 */
    private final class ApplyContext {

        final UUID runId;
        final List<StandardPackageImportRunItem> items = new ArrayList<>();
        final Map<String, String> directoryIds = new HashMap<>();
        final Map<String, Integer> createdByType = new LinkedHashMap<>();
        final Map<String, Integer> updatedByType = new LinkedHashMap<>();
        int seq = 0;

        ApplyContext(UUID runId) {
            this.runId = runId;
        }

        void record(String entityType, String entityId, String action, Map<String, Object> beforeImage) {
            StandardPackageImportRunItem item = new StandardPackageImportRunItem();
            item.setRunId(runId);
            item.setSeq(++seq);
            item.setEntityType(entityType);
            item.setEntityId(entityId);
            item.setAction(action);
            if (beforeImage != null) {
                try {
                    item.setBeforeJson(objectMapper.writeValueAsString(beforeImage));
                } catch (Exception ex) {
                    throw new IllegalStateException("before-image 序列化失败");
                }
            }
            items.add(item);
        }

        void countCreate(String type) {
            createdByType.merge(type, 1, Integer::sum);
        }

        void countUpdate(String type) {
            updatedByType.merge(type, 1, Integer::sum);
        }

        int totalCreated() {
            return createdByType.values().stream().mapToInt(Integer::intValue).sum();
        }

        int totalUpdated() {
            return updatedByType.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
