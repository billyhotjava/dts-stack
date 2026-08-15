package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorReference;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovDimensionDictionaryRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorReferenceRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorVersionRepository;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorDto;
import com.yuzhi.dts.platform.service.governance.dto.IndicatorValidationResultDto;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
public class IndicatorPublishPreviewService {

    private final GovIndicatorDefinitionRepository indicatorRepository;
    private final GovIndicatorVersionRepository versionRepository;
    private final GovIndicatorReferenceRepository referenceRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final GovDimensionDictionaryRepository dimensionRepository;
    private final IndicatorService indicatorService;
    private final DbtIndicatorGenerator dbtGenerator;
    private final AccessChecker accessChecker;
    private final ObjectMapper objectMapper;

    public IndicatorPublishPreviewService(
        GovIndicatorDefinitionRepository indicatorRepository,
        GovIndicatorVersionRepository versionRepository,
        GovIndicatorReferenceRepository referenceRepository,
        CatalogDatasetRepository datasetRepository,
        GovDimensionDictionaryRepository dimensionRepository,
        IndicatorService indicatorService,
        DbtIndicatorGenerator dbtGenerator,
        AccessChecker accessChecker,
        ObjectMapper objectMapper
    ) {
        this.indicatorRepository = indicatorRepository;
        this.versionRepository = versionRepository;
        this.referenceRepository = referenceRepository;
        this.datasetRepository = datasetRepository;
        this.dimensionRepository = dimensionRepository;
        this.indicatorService = indicatorService;
        this.dbtGenerator = dbtGenerator;
        this.accessChecker = accessChecker;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> preview(UUID indicatorId, String activeDept) {
        String trustedActiveDept = indicatorService.resolveTrustedActiveDept(activeDept);
        IndicatorDto indicatorDto = indicatorService.get(indicatorId, trustedActiveDept);
        GovIndicatorDefinition indicator = indicatorRepository
            .findById(indicatorId)
            .orElseThrow(() -> new IndicatorNotFoundException("指标不存在: " + indicatorId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("indicator", indicatorDto);
        payload.put("activeDept", normalizeText(trustedActiveDept));
        payload.put("currentSignature", computeSignature(indicator));

        // Basic config checks
        List<Map<String, Object>> blockingIssues = new ArrayList<>();
        List<Map<String, Object>> warningIssues = new ArrayList<>();
        try {
            indicatorService.validateDefinitionForPublish(indicator);
        } catch (IndicatorConflictException | IndicatorRequestException error) {
            blockingIssues.add(
                issue(
                    definitionIssueCode(error),
                    "BLOCKER",
                    safeText(error.getMessage(), "指标定义不完整"),
                    "请修复指标归属、来源版本或依赖关系后重新预检"
                )
            );
        }
        boolean derived = IndicatorDefinitionSemantics.isDerivedLike(indicator);
        boolean modelBoundAtomic = IndicatorDefinitionSemantics.isModelBoundAtomic(indicator);
        String datasetIdRaw = normalizeText(indicator.getDatasetId());
        UUID datasetId = parseUuid(datasetIdRaw);
        payload.put("definitionSourceMode", modelBoundAtomic ? "MODEL_FIELD" : derived ? "INDICATOR_VERSION" : "LEGACY_DATASET_SQL");
        payload.put("datasetRequired", !derived && !modelBoundAtomic);
        payload.put("datasetIdValid", derived || modelBoundAtomic || datasetId != null);
        if (!derived && !modelBoundAtomic && datasetId == null) {
            blockingIssues.add(
                issue(
                    "IND_CFG_DATASET_ID_INVALID",
                    "BLOCKER",
                    "数据集ID格式错误或未配置",
                    "请在指标配置中绑定有效的数据集 UUID"
                )
            );
        }
        if (!modelBoundAtomic && !StringUtils.hasText(indicator.getExpressionSql())) {
            blockingIssues.add(
                issue(
                    derived ? "IND_CFG_DERIVATION_MISSING" : "IND_CFG_SQL_MISSING",
                    "BLOCKER",
                    derived ? "未配置派生表达式" : "未配置计算SQL",
                    derived ? "请先填写受控派生表达式" : "请先填写计算 SQL"
                )
            );
        }

        CatalogDataset dataset = !derived && !modelBoundAtomic && datasetId != null
            ? datasetRepository.findById(datasetId).orElse(null)
            : null;
        if (!derived && !modelBoundAtomic && datasetId != null && dataset == null) {
            blockingIssues.add(issue("IND_CFG_DATASET_NOT_FOUND", "BLOCKER", "绑定的数据集不存在", "请重新绑定有效数据集"));
        }
        boolean datasetAccessible = dataset == null || datasetAccessible(dataset, trustedActiveDept);
        if (dataset != null && !datasetAccessible) {
            blockingIssues.add(
                issue(
                    "IND_DATASET_ACCESS_DENIED",
                    "BLOCKER",
                    "当前上下文无权预检绑定数据集",
                    "请切换到有权访问该数据集的部门上下文"
                )
            );
        } else if (dataset != null) {
            Map<String, Object> datasetPayload = new LinkedHashMap<>();
            datasetPayload.put("id", dataset.getId() != null ? dataset.getId().toString() : null);
            datasetPayload.put("name", dataset.getName());
            datasetPayload.put("ownerDept", dataset.getOwnerDept());
            datasetPayload.put("classification", dataset.getClassification());
            datasetPayload.put("type", dataset.getType());
            datasetPayload.put("hiveDatabase", dataset.getHiveDatabase());
            datasetPayload.put("hiveTable", dataset.getHiveTable());
            payload.put("dataset", datasetPayload);
        }

        if (derived) {
            IndicatorDerivationValidationResult validation = indicatorService.validateDerivation(indicatorId, trustedActiveDept);
            payload.put("validation", validation);
            for (IndicatorDerivationValidationResult.Issue validationIssue : validation.issues()) {
                blockingIssues.add(
                    issue(
                        validationIssue.code(),
                        "BLOCKER",
                        validationIssue.message(),
                        "请修复派生表达式、依赖状态或粒度后重新校验"
                    )
                );
            }
        } else {
            // Atomic indicators keep the existing security guard + query-gateway validation.
            if (dataset != null && !datasetAccessible) {
                payload.put("validation", null);
            } else {
                IndicatorValidationResultDto validation = indicatorService.validateComputeRule(
                    indicatorId,
                    trustedActiveDept
                );
                payload.put("validation", validation);
                if (validation == null || !"SUCCESS".equalsIgnoreCase(validation.getStatus())) {
                    blockingIssues.add(
                        issue(
                            "IND_VALIDATION_FAILED",
                            "BLOCKER",
                            "校验未通过：" + safeText(validation != null ? validation.getMessage() : null, "UNKNOWN"),
                            "请先修复 SQL、权限或数据集问题后重新校验"
                        )
                    );
                }
            }
        }

        // Manual references
        List<GovIndicatorReference> refs = referenceRepository
            .findByIndicatorOrderByCreatedDateAsc(indicator)
            ;
        List<Map<String, Object>> references = refs.stream().map(this::toReferenceDto).toList();
        payload.put("references", references);
        List<Map<String, Object>> referenceCheck = checkReferences(refs);
        payload.put("referenceCheck", referenceCheck);
        for (Map<String, Object> check : referenceCheck) {
            String status = String.valueOf(check.get("status")).toUpperCase(Locale.ROOT);
            String refType = safeText(check.get("refType"), "UNKNOWN");
            String refTarget = safeText(check.get("refTarget"), "");
            if ("MISSING".equals(status)) {
                blockingIssues.add(
                    issue(
                        "IND_REF_MISSING",
                        "BLOCKER",
                        "引用缺失：" + refType + " -> " + refTarget,
                        "请先补齐缺失的引用对象或更新引用配置"
                    )
                );
            } else if ("UNKNOWN".equals(status)) {
                warningIssues.add(
                    issue(
                        "IND_REF_UNKNOWN",
                        "WARNING",
                        "引用校验不完整：" + refType + " -> " + refTarget,
                        "请检查引用目标格式是否符合约定"
                    )
                );
            }
        }

        if (blockingIssues.isEmpty() && !modelBoundAtomic) {
            try {
                dbtGenerator.previewSql(indicatorId);
            } catch (RuntimeException ex) {
                blockingIssues.add(
                    issue(
                        "IND_DBT_COMPILE_FAILED",
                        "BLOCKER",
                        "dbt 指标产物编译未通过",
                        "请检查指标计算配置与依赖后重新预检"
                    )
                );
            }
        }

        // Last published snapshot diff
        GovIndicatorVersion lastPublished = versionRepository
            .findFirstByIndicatorAndStatusIgnoreCaseOrderByReleasedAtDescCreatedDateDesc(indicator, "PUBLISHED")
            .orElse(null);
        if (lastPublished != null) {
            payload.put("lastPublished", toVersionDto(lastPublished));
            Map<String, Object> lastSnapshot = parseJson(lastPublished.getSnapshotJson());
            payload.put("lastPublishedSnapshot", lastSnapshot);
            payload.put("changesSinceLastPublish", diffIndicator(indicatorDto, lastSnapshot));
        } else {
            payload.put("lastPublished", null);
            payload.put("lastPublishedSnapshot", null);
            payload.put("changesSinceLastPublish", List.of());
        }

        if (lastPublished == null) {
            warningIssues.add(issue("IND_FIRST_PUBLISH", "WARNING", "尚无历史发布版本", "首次发布后可启用版本差异追踪"));
        }

        boolean ready = blockingIssues.isEmpty();
        payload.put("readyToPublish", ready);
        payload.put("blockingIssues", blockingIssues);
        payload.put("warningIssues", warningIssues);
        payload.put("publishGate", Map.of("passed", ready, "blockerCount", blockingIssues.size(), "warningCount", warningIssues.size()));
        payload.put("failureReasonCode", ready ? null : safeText(blockingIssues.get(0).get("code"), null));
        payload.put("issues", mergeIssues(blockingIssues, warningIssues));
        return payload;
    }

    private String definitionIssueCode(RuntimeException error) {
        String message = error == null ? null : normalizeText(error.getMessage());
        if (StringUtils.hasText(message)) {
            int separator = message.indexOf(':');
            String candidate = (separator > 0 ? message.substring(0, separator) : message).trim();
            if (candidate.matches("[A-Z][A-Z0-9_]*")) {
                return candidate;
            }
        }
        return "IND_DEFINITION_INVALID";
    }

    private List<Map<String, Object>> checkReferences(List<GovIndicatorReference> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> checks = new ArrayList<>();
        for (GovIndicatorReference ref : refs) {
            if (ref == null) {
                continue;
            }
            String refType = normalizeType(ref.getRefType());
            String target = normalizeText(ref.getRefTarget());
            if (!StringUtils.hasText(refType) || !StringUtils.hasText(target)) {
                checks.add(nullableMap("refType", refType, "refTarget", target, "status", "UNKNOWN", "message", "引用信息不完整"));
                continue;
            }
            if ("INDICATOR".equals(refType)) {
                checks.add(checkIndicatorRef(target));
                continue;
            }
            if ("DIMENSION".equals(refType)) {
                checks.add(checkDimensionRef(target));
                continue;
            }
            if ("DATASET".equals(refType)) {
                checks.add(checkDatasetRef(target));
                continue;
            }
            checks.add(nullableMap("refType", refType, "refTarget", target, "status", "SKIPPED"));
        }
        return checks;
    }

    private Map<String, Object> checkIndicatorRef(String target) {
        UUID id = parseUuid(target);
        if (id != null) {
            boolean exists = indicatorRepository.existsById(id);
            return nullableMap("refType", "INDICATOR", "refTarget", target, "status", exists ? "OK" : "MISSING");
        }
        boolean exists = indicatorRepository.findFirstByCodeIgnoreCase(target).isPresent();
        return nullableMap("refType", "INDICATOR", "refTarget", target, "status", exists ? "OK" : "MISSING");
    }

    private Map<String, Object> checkDimensionRef(String target) {
        UUID id = parseUuid(target);
        if (id != null) {
            boolean exists = dimensionRepository.existsById(id);
            return nullableMap("refType", "DIMENSION", "refTarget", target, "status", exists ? "OK" : "MISSING");
        }
        boolean exists = dimensionRepository.findFirstByCodeIgnoreCase(target).isPresent();
        return nullableMap("refType", "DIMENSION", "refTarget", target, "status", exists ? "OK" : "MISSING");
    }

    private Map<String, Object> checkDatasetRef(String target) {
        UUID id = parseUuid(target);
        if (id == null) {
            return nullableMap("refType", "DATASET", "refTarget", target, "status", "UNKNOWN", "message", "数据集引用应使用 UUID");
        }
        boolean exists = datasetRepository.existsById(id);
        return nullableMap("refType", "DATASET", "refTarget", target, "status", exists ? "OK" : "MISSING");
    }

    private String normalizeType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private Map<String, Object> toReferenceDto(GovIndicatorReference ref) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (ref == null) return dto;
        if (ref.getId() != null) dto.put("id", ref.getId().toString());
        dto.put("refType", ref.getRefType());
        dto.put("refTarget", ref.getRefTarget());
        dto.put("refName", ref.getRefName());
        dto.put("notes", ref.getNotes());
        dto.put("createdBy", ref.getCreatedBy());
        dto.put("createdDate", ref.getCreatedDate());
        return dto;
    }

    private Map<String, Object> toVersionDto(GovIndicatorVersion v) {
        Map<String, Object> dto = new LinkedHashMap<>();
        if (v == null) return dto;
        if (v.getId() != null) dto.put("id", v.getId().toString());
        dto.put("version", v.getVersion());
        dto.put("status", v.getStatus());
        dto.put("changeSummary", v.getChangeSummary());
        dto.put("releasedAt", v.getReleasedAt());
        dto.put("createdDate", v.getCreatedDate());
        return dto;
    }

    private List<Map<String, Object>> diffIndicator(IndicatorDto current, Map<String, Object> lastSnapshot) {
        if (current == null || lastSnapshot == null || lastSnapshot.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> diffs = new ArrayList<>();
        diffField(diffs, "name", current.getName(), safeText(lastSnapshot.get("name"), null));
        diffField(diffs, "code", current.getCode(), safeText(lastSnapshot.get("code"), null));
        diffField(diffs, "category", current.getCategory(), safeText(lastSnapshot.get("category"), null));
        diffField(diffs, "definition", current.getDefinition(), safeText(lastSnapshot.get("definition"), null));
        diffField(diffs, "datasetId", current.getDatasetId(), safeText(lastSnapshot.get("datasetId"), null));
        diffField(diffs, "expressionSql", current.getExpressionSql(), safeText(lastSnapshot.get("expressionSql"), null));
        diffField(diffs, "dataLevel", current.getDataLevel(), safeText(lastSnapshot.get("dataLevel"), null));
        diffField(diffs, "tags", current.getTags(), safeText(lastSnapshot.get("tags"), null));
        return diffs;
    }

    private void diffField(List<Map<String, Object>> diffs, String field, String current, String last) {
        String a = normalizeText(current);
        String b = normalizeText(last);
        if (Objects.equals(a, b)) {
            return;
        }
        diffs.add(nullableMap("field", field, "before", b, "after", a, "changed", true));
    }

    private Map<String, Object> parseJson(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of("raw", json);
        }
    }

    private String computeSignature(GovIndicatorDefinition entity) {
        try {
            return IndicatorValidationSignature.compute(entity, objectMapper, indicatorRepository);
        } catch (IllegalArgumentException ex) {
            throw new IndicatorConflictException("指标当前配置不合法：" + safeText(ex.getMessage(), "未知配置错误"));
        }
    }

    private boolean datasetAccessible(CatalogDataset dataset, String trustedActiveDept) {
        try {
            return accessChecker.canRead(dataset) && accessChecker.departmentAllowed(dataset, trustedActiveDept);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Map<String, Object> nullableMap(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            result.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return result;
    }

    private UUID parseUuid(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String safeText(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        if (!StringUtils.hasText(text)) {
            return fallback;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Map<String, Object> issue(String code, String severity, String message, String suggestion) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("severity", severity);
        row.put("message", message);
        row.put("suggestion", suggestion);
        return row;
    }

    private List<String> mergeIssues(List<Map<String, Object>> blockingIssues, List<Map<String, Object>> warningIssues) {
        List<String> out = new ArrayList<>();
        if (blockingIssues != null) {
            for (Map<String, Object> issue : blockingIssues) {
                out.add(formatIssue(issue));
            }
        }
        if (warningIssues != null) {
            for (Map<String, Object> issue : warningIssues) {
                out.add(formatIssue(issue));
            }
        }
        return out;
    }

    private String formatIssue(Map<String, Object> issue) {
        if (issue == null || issue.isEmpty()) {
            return "";
        }
        String code = safeText(issue.get("code"), "UNKNOWN");
        String message = safeText(issue.get("message"), "未知问题");
        return "[" + code + "] " + message;
    }
}
