package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.*;
import com.yuzhi.dts.platform.repository.catalog.*;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import jakarta.validation.Valid;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static com.yuzhi.dts.platform.web.rest.catalog.CatalogResourceHelper.CATALOG_MAINTAINER_EXPRESSION;

@RestController
@RequestMapping("/api/catalog")
public class CatalogMaskingResource {

    private final CatalogDatasetRepository datasetRepo;
    private final CatalogMaskingRuleRepository maskingRepo;
    private final CatalogClassificationMappingRepository mappingRepo;
    private final AuditService audit;
    private final AccessChecker accessChecker;
    private final CatalogResourceHelper helper;

    public CatalogMaskingResource(
        CatalogDatasetRepository datasetRepo,
        CatalogMaskingRuleRepository maskingRepo,
        CatalogClassificationMappingRepository mappingRepo,
        AuditService audit,
        AccessChecker accessChecker,
        CatalogResourceHelper helper
    ) {
        this.datasetRepo = datasetRepo;
        this.maskingRepo = maskingRepo;
        this.mappingRepo = mappingRepo;
        this.audit = audit;
        this.accessChecker = accessChecker;
        this.helper = helper;
    }

    @GetMapping("/masking-rules")
    @Transactional(readOnly = true)
    public ApiResponse<List<CatalogMaskingRule>> listMaskingRules() {
        List<CatalogMaskingRule> list = maskingRepo.findAll();
        audit.audit("READ", "catalog.masking", "list");
        return ApiResponses.ok(list);
    }

    @PostMapping("/masking-rules")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogMaskingRule> createMasking(@Valid @RequestBody CatalogMaskingRule rule) {
        CatalogMaskingRule saved = maskingRepo.save(rule);
        audit.audit("CREATE", "catalog.masking", saved.getId().toString());
        return ApiResponses.ok(saved);
    }

    @PutMapping("/masking-rules/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<CatalogMaskingRule> updateMasking(@PathVariable UUID id, @Valid @RequestBody CatalogMaskingRule patch) {
        CatalogMaskingRule existing = maskingRepo.findById(id).orElseThrow();
        existing.setColumn(patch.getColumn());
        existing.setFunction(patch.getFunction());
        existing.setArgs(patch.getArgs());
        CatalogMaskingRule saved = maskingRepo.save(existing);
        audit.audit("UPDATE", "catalog.masking", id.toString());
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/masking-rules/{id}")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Boolean> deleteMasking(@PathVariable UUID id) {
        maskingRepo.deleteById(id);
        audit.audit("DELETE", "catalog.masking", id.toString());
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PostMapping("/masking-rules/preview")
    @Transactional
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        String function = Objects.toString(body.get("function"), "");
        String value = Objects.toString(body.get("value"), "");
        String result = switch (function) {
            case "hash" -> Integer.toHexString(Objects.hashCode(value));
            case "mask_email" -> value.replaceAll("(^.).*(@.*$)", "$1***$2");
            case "mask_phone" -> value.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
            default -> value;
        };
        Map<String, Object> resp = Map.of("input", value, "function", function, "output", result);
        audit.audit("EXECUTE", "catalog.masking.preview", function);
        return ApiResponses.ok(resp);
    }

    @GetMapping("/classification-mapping")
    @Transactional(readOnly = true)
    public ApiResponse<List<CatalogClassificationMapping>> getMapping() {
        List<CatalogClassificationMapping> list = mappingRepo.findAll();
        audit.audit("READ", "catalog.classificationMapping", "list");
        return ApiResponses.ok(list);
    }

    @PutMapping("/classification-mapping")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<List<CatalogClassificationMapping>> replaceMapping(
        @RequestBody List<CatalogClassificationMapping> items
    ) {
        CatalogResourceHelper.MappingValidationResult validation = validateClassificationMappingInternal(items);
        if (!validation.conflicts().isEmpty()) {
            String msg = validation
                .conflicts()
                .stream()
                .map(it -> Objects.toString(it.get("message"), "映射冲突"))
                .findFirst()
                .orElse("分类映射存在冲突");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, msg + "（可先执行分类映射冲突预检）");
        }
        mappingRepo.deleteAll();
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(validation.normalizedItems());
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "更新分类映射");
        auditPayload.put("savedCount", saved.size());
        auditPayload.put("warningCount", validation.warnings().size());
        auditPayload.put("warnings", validation.warnings());
        audit.auditAction("CATALOG_CLASSIFICATION_MAPPING_REPLACE", AuditStage.SUCCESS, "replace:" + saved.size(), auditPayload);
        return ApiResponses.ok(saved);
    }

    @PostMapping("/classification-mapping/import")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> importMapping(@RequestBody List<CatalogClassificationMapping> items) {
        List<CatalogClassificationMapping> saved = mappingRepo.saveAll(items);
        audit.audit("CREATE", "catalog.classificationMapping", "import:" + saved.size());
        return ApiResponses.ok(Map.of("imported", saved.size()));
    }

    @GetMapping("/classification-mapping/export")
    @Transactional(readOnly = true)
    public ApiResponse<List<CatalogClassificationMapping>> exportMapping() {
        List<CatalogClassificationMapping> list = mappingRepo.findAll();
        audit.audit("READ", "catalog.classificationMapping", "export");
        return ApiResponses.ok(list);
    }

    @PostMapping("/classification-mapping/validate")
    @Transactional
    @PreAuthorize(CATALOG_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> validateClassificationMapping(
        @RequestBody(required = false) List<CatalogClassificationMapping> items
    ) {
        CatalogResourceHelper.MappingValidationResult validation = validateClassificationMappingInternal(items);
        Map<String, Object> payload = toMappingValidationPayload(validation);
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", "分类映射冲突预检");
        auditPayload.put("conflictCount", validation.conflicts().size());
        auditPayload.put("warningCount", validation.warnings().size());
        audit.auditAction("CATALOG_CLASSIFICATION_MAPPING_VALIDATE", AuditStage.SUCCESS, "classification-mapping", auditPayload);
        return ApiResponses.ok(payload);
    }

    @GetMapping("/classification-masking/linkage")
    @Transactional(readOnly = true)
    public ApiResponse<Map<String, Object>> classificationMaskingLinkage(
        @RequestParam(name = "datasetId", required = false) UUID datasetId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        if (datasetId == null && !SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅维护角色可查看联动总览");
        }
        Map<String, Object> payload = datasetId != null ? buildDatasetLinkage(datasetId, activeDept) : buildLinkageSummary();
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", datasetId != null ? "查看资产密级与脱敏策略联动" : "查看密级与脱敏联动总览");
        if (datasetId != null) {
            auditPayload.put("datasetId", datasetId.toString());
        }
        audit.auditAction("CATALOG_CLASSIFICATION_MASKING_LINKAGE_VIEW", AuditStage.SUCCESS, datasetId != null ? datasetId.toString() : "summary", auditPayload);
        return ApiResponses.ok(payload);
    }

    private CatalogResourceHelper.MappingValidationResult validateClassificationMappingInternal(List<CatalogClassificationMapping> items) {
        List<CatalogClassificationMapping> normalized = new ArrayList<>();
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> warnings = new ArrayList<>();
        Map<String, String> seenKeys = new LinkedHashMap<>();
        List<CatalogClassificationMapping> safeItems = items != null ? items : List.of();
        for (int i = 0; i < safeItems.size(); i++) {
            CatalogClassificationMapping item = safeItems.get(i);
            String source = helper.trimToNull(item != null ? item.getSource() : null);
            String sourceLevel = helper.trimToNull(item != null ? item.getSourceLevel() : null);
            String platformLevel = helper.normalizeClassificationString(item != null ? item.getPlatformLevel() : null);
            int rowNo = i + 1;

            if (source == null || sourceLevel == null || platformLevel == null) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "INVALID_ROW");
                conflict.put("message", "来源系统、来源级别、平台级别不能为空，且平台级别必须合法。");
                conflict.put("suggestion", "请补全字段并将平台级别修正为 " + supportedClassificationText() + "。");
                conflicts.add(conflict);
                continue;
            }
            if (!CatalogResourceHelper.SUPPORTED_CLASSIFICATION_LEVELS.contains(platformLevel)) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "INVALID_PLATFORM_LEVEL");
                conflict.put("message", "平台级别不合法：" + platformLevel);
                conflict.put("suggestion", "平台级别仅支持 " + supportedClassificationText() + "。");
                conflicts.add(conflict);
                continue;
            }

            String key = source.toUpperCase(Locale.ROOT) + "::" + sourceLevel.toUpperCase(Locale.ROOT);
            if (seenKeys.containsKey(key)) {
                Map<String, Object> conflict = new LinkedHashMap<>();
                conflict.put("row", rowNo);
                conflict.put("code", "DUPLICATE_MAPPING");
                conflict.put("message", "存在重复映射键：" + source + " / " + sourceLevel);
                conflict.put("suggestion", "请保留唯一映射并删除重复项。");
                conflicts.add(conflict);
                continue;
            }
            seenKeys.put(key, platformLevel);

            CatalogClassificationMapping normalizedItem = new CatalogClassificationMapping();
            normalizedItem.setSource(source);
            normalizedItem.setSourceLevel(sourceLevel);
            normalizedItem.setPlatformLevel(platformLevel);
            normalized.add(normalizedItem);
        }

        List<CatalogDataset> datasets = datasetRepo.findAll(PageRequest.of(0, 10_000, Sort.by("id"))).getContent();
        Map<UUID, Integer> datasetRuleCount = new HashMap<>();
        if (!datasets.isEmpty()) {
            for (CatalogMaskingRule rule : maskingRepo.findByDatasetIn(datasets)) {
                UUID datasetId = rule != null && rule.getDataset() != null ? rule.getDataset().getId() : null;
                if (datasetId != null) {
                    datasetRuleCount.merge(datasetId, 1, Integer::sum);
                }
            }
        }
        Map<String, Integer> classificationDatasetCount = new HashMap<>();
        for (CatalogDataset dataset : datasets) {
            String level = helper.normalizeClassificationString(dataset.getClassification());
            if (level != null) {
                classificationDatasetCount.merge(level, 1, Integer::sum);
            }
            if (!helper.requiresMasking(level) || datasetRuleCount.getOrDefault(dataset.getId(), 0) > 0) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("code", "MASKING_GAP");
            warning.put("datasetId", dataset.getId() != null ? dataset.getId().toString() : null);
            warning.put("datasetName", dataset.getName());
            warning.put("classification", level);
            warning.put("message", "高密级数据集缺少脱敏规则：" + dataset.getName());
            warning.put("suggestion", "请在\u201c脱敏规则\u201d中为该数据集至少配置 1 条规则。");
            warnings.add(warning);
        }

        Set<String> mappedLevels = normalized.stream().map(CatalogClassificationMapping::getPlatformLevel).filter(Objects::nonNull).collect(Collectors.toSet());
        for (String level : mappedLevels) {
            if (classificationDatasetCount.getOrDefault(level, 0) > 0) {
                continue;
            }
            Map<String, Object> warning = new LinkedHashMap<>();
            warning.put("code", "UNUSED_PLATFORM_LEVEL");
            warning.put("classification", level);
            warning.put("message", "平台级别 " + level + " 当前没有对应数据集。");
            warning.put("suggestion", "可保留为预留映射，或移除以减少维护成本。");
            warnings.add(warning);
        }
        return new CatalogResourceHelper.MappingValidationResult(normalized, conflicts, warnings);
    }

    private static String supportedClassificationText() {
        return String.join("/", CatalogResourceHelper.CLASSIFICATION_LEVEL_ORDER);
    }

    private Map<String, Object> toMappingValidationPayload(CatalogResourceHelper.MappingValidationResult validation) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("valid", validation.conflicts().isEmpty());
        payload.put("normalizedCount", validation.normalizedItems().size());
        payload.put("conflicts", validation.conflicts());
        payload.put("warnings", validation.warnings());
        return payload;
    }

    private Map<String, Object> buildDatasetLinkage(UUID datasetId, String activeDept) {
        CatalogDataset dataset = datasetRepo
            .findById(datasetId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据集不存在"));
        String effDept = activeDept != null ? activeDept : helper.claim("dept_code");
        if (!accessChecker.canRead(dataset) || !accessChecker.departmentAllowed(dataset, effDept)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该数据集");
        }
        String classification = helper.normalizeClassificationString(dataset.getClassification());
        List<CatalogMaskingRule> rules = maskingRepo.findByDataset(dataset);
        List<Map<String, Object>> effectiveRules = rules.stream().map(helper::toMaskingRuleDto).toList();
        List<Map<String, Object>> mappingMatches = (classification != null ? mappingRepo.findByPlatformLevelIgnoreCase(classification) : Collections.<CatalogClassificationMapping>emptyList())
            .stream()
            .filter(item -> Objects.equals(helper.normalizeClassificationString(item.getPlatformLevel()), classification))
            .map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId() != null ? item.getId().toString() : null);
                row.put("source", helper.trimToNull(item.getSource()));
                row.put("sourceLevel", helper.trimToNull(item.getSourceLevel()));
                row.put("platformLevel", helper.normalizeClassificationString(item.getPlatformLevel()));
                return row;
            })
            .toList();
        boolean requiresMasking = helper.requiresMasking(classification);
        boolean conflict = requiresMasking && rules.isEmpty();
        List<String> suggestions = new ArrayList<>();
        if (conflict) {
            suggestions.add("当前密级要求至少 1 条脱敏规则，请在\u201c数据治理中心 / 分级分类 -> 脱敏规则\u201d中补齐。");
        }
        if (mappingMatches.isEmpty()) {
            suggestions.add("当前密级在分类映射中无对应关系，请在\u201c分类映射\u201d中新增来源级别映射。");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("datasetId", datasetId.toString());
        payload.put("datasetName", dataset.getName());
        payload.put("classification", classification);
        payload.put("requiresMasking", requiresMasking);
        payload.put("maskingRuleCount", rules.size());
        payload.put("effectiveRules", effectiveRules);
        payload.put("mappingMatches", mappingMatches);
        payload.put("conflict", conflict);
        payload.put("suggestions", suggestions);
        return payload;
    }

    private Map<String, Object> buildLinkageSummary() {
        List<CatalogDataset> datasets = datasetRepo.findAll(PageRequest.of(0, 10_000, Sort.by("id"))).getContent();
        List<CatalogMaskingRule> rules = datasets.isEmpty() ? Collections.emptyList() : maskingRepo.findByDatasetIn(datasets);
        List<CatalogClassificationMapping> mappings = mappingRepo.findAll();
        Map<UUID, Integer> datasetRuleCount = new HashMap<>();
        for (CatalogMaskingRule rule : rules) {
            UUID datasetId = rule != null && rule.getDataset() != null ? rule.getDataset().getId() : null;
            if (datasetId != null) {
                datasetRuleCount.merge(datasetId, 1, Integer::sum);
            }
        }
        Map<String, Integer> mappingCountByLevel = new HashMap<>();
        for (CatalogClassificationMapping mapping : mappings) {
            String level = helper.normalizeClassificationString(mapping.getPlatformLevel());
            if (level != null) {
                mappingCountByLevel.merge(level, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> conflictsList = new ArrayList<>();
        for (String level : CatalogResourceHelper.CLASSIFICATION_LEVEL_ORDER) {
            int datasetCount = 0;
            int datasetWithRules = 0;
            int maskingRuleCount = 0;
            List<String> noRuleDatasets = new ArrayList<>();
            for (CatalogDataset dataset : datasets) {
                if (!Objects.equals(helper.normalizeClassificationString(dataset.getClassification()), level)) {
                    continue;
                }
                datasetCount += 1;
                int ruleCount = datasetRuleCount.getOrDefault(dataset.getId(), 0);
                maskingRuleCount += ruleCount;
                if (ruleCount > 0) {
                    datasetWithRules += 1;
                } else if (helper.requiresMasking(level)) {
                    noRuleDatasets.add(dataset.getName());
                }
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("classification", level);
            row.put("requiresMasking", helper.requiresMasking(level));
            row.put("datasetCount", datasetCount);
            row.put("datasetWithRules", datasetWithRules);
            row.put("maskingRuleCount", maskingRuleCount);
            row.put("mappingCount", mappingCountByLevel.getOrDefault(level, 0));
            rows.add(row);

            if (!noRuleDatasets.isEmpty()) {
                Map<String, Object> conflictEntry = new LinkedHashMap<>();
                conflictEntry.put("type", "MASKING_GAP");
                conflictEntry.put("classification", level);
                conflictEntry.put("count", noRuleDatasets.size());
                conflictEntry.put("datasets", noRuleDatasets.stream().limit(10).toList());
                conflictEntry.put("suggestion", "为该密级数据集补充脱敏规则，至少覆盖核心敏感字段。");
                conflictsList.add(conflictEntry);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rows", rows);
        payload.put("conflicts", conflictsList);
        payload.put("datasetTotal", datasets.size());
        payload.put("maskingRuleTotal", rules.size());
        payload.put("mappingTotal", mappings.size());
        return payload;
    }
}
