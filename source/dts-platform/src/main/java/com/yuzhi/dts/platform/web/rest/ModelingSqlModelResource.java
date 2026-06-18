package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.analytics.SemanticContractPublishService;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelGenerationService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelContractImpact;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernanceExecuteRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernanceExecuteResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernancePreviewRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernancePreviewResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelSchemaYmlResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelStandardBindingRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelStandardBindingResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelStandardGateResult;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/modeling/sql-models")
public class ModelingSqlModelResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final Logger LOG = LoggerFactory.getLogger(ModelingSqlModelResource.class);

    private final ModelingSqlModelService sqlModelService;
    private final ModelGenerationService generationService;
    private final ModelingSqlModelRepository sqlModelRepository;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final AuditService auditService;
    private final DataStandardSecurity security;
    private final SemanticContractPublishService semanticContractPublishService;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;

    public ModelingSqlModelResource(
        ModelingSqlModelService sqlModelService,
        ModelGenerationService generationService,
        ModelingSqlModelRepository sqlModelRepository,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        CatalogDatasetRepository datasetRepository,
        InfraDataSourceRepository dataSourceRepository,
        AuditService auditService,
        DataStandardSecurity security,
        SemanticContractPublishService semanticContractPublishService,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard
    ) {
        this.sqlModelService = sqlModelService;
        this.generationService = generationService;
        this.sqlModelRepository = sqlModelRepository;
        this.odsTableMappingRepository = odsTableMappingRepository;
        this.datasetRepository = datasetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.auditService = auditService;
        this.security = security;
        this.semanticContractPublishService = semanticContractPublishService;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<SqlModelDto>> list(
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<SqlModelDto> list = sqlModelService.list(planId, keyword, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ApiResponse<SqlModelDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.get(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(dto);
    }

    @GetMapping("/{id}/columns")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listColumns(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> columns = sqlModelService.listColumns(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_COLUMNS_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(columns);
    }

    @GetMapping("/{id}/contract-impact")
    @Transactional(readOnly = true)
    public ApiResponse<SqlModelContractImpact> contractImpact(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelContractImpact impact = sqlModelService.getContractImpact(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_CONTRACT_IMPACT_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(impact);
    }

    @GetMapping("/{id}/standard-bindings")
    @Transactional(readOnly = true)
    public ApiResponse<SqlModelStandardBindingResult> listStandardBindings(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelStandardBindingResult result = sqlModelService.listStandardBindings(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_STANDARD_BINDINGS_READ", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(result);
    }

    @PutMapping("/{id}/standard-bindings")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelStandardBindingResult> saveStandardBindings(
        @PathVariable UUID id,
        @RequestBody SqlModelStandardBindingRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelStandardBindingResult result = sqlModelService.saveStandardBindings(id, request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_STANDARD_BINDINGS_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/{id}/dbt/schema-yml")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelSchemaYmlResult> generateSchemaYml(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelSchemaYmlResult result = sqlModelService.generateSchemaYml(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_SCHEMA_YML_GENERATE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/{id}/standard-gate/check")
    @Transactional(readOnly = true)
    public ApiResponse<SqlModelStandardGateResult> checkStandardGate(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelStandardGateResult result = sqlModelService.checkStandardGate(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_STANDARD_GATE_CHECK", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(result);
    }

    @PostMapping
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> create(
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.create(request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_CREATE", AuditStage.SUCCESS, dto.id().toString(), null);
        return ApiResponses.ok(dto);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> importModel(
        @RequestParam UUID planId,
        @RequestParam String name,
        @RequestParam String layer,
        @RequestParam UUID sourceDataSourceId,
        @RequestParam(required = false) String alias,
        @RequestParam(required = false) String schemaName,
        @RequestParam(required = false) String materialized,
        @RequestParam(required = false) String tags,
        @RequestParam(required = false) String description,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String ownerDept,
        @RequestParam("sql") MultipartFile sqlFile,
        @RequestParam(value = "csv", required = false) MultipartFile csvFile,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String sqlText = readText(sqlFile, "SQL");
        String csvText = csvFile != null ? readText(csvFile, "CSV") : null;
        SqlModelRequest request = new SqlModelRequest(
            planId,
            name,
            alias,
            layer,
            sourceDataSourceId,
            schemaName,
            materialized,
            tags,
            description,
            sqlText,
            enabled,
            status,
            ownerDept,
            null
        );
        SqlModelDto dto = generationService.importFromFiles(request, sqlText, csvText, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_IMPORT", AuditStage.SUCCESS, dto.id().toString(), null);
        return ApiResponses.ok(dto);
    }

    @PostMapping(value = "/batch-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingSqlModelService.BatchImportResult> batchImport(
        @RequestParam UUID planId,
        @RequestParam UUID sourceDataSourceId,
        @RequestParam("archive") MultipartFile archive,
        @RequestParam(required = false, defaultValue = "false") boolean skipExisting,
        @RequestParam(required = false, defaultValue = "false") boolean cleanOldFiles,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) throws IOException {
        if (archive.isEmpty()) {
            throw new IllegalArgumentException("请上传 ZIP 压缩包");
        }
        Path tempFile = Files.createTempFile("batch-import-", ".zip");
        try {
            archive.transferTo(tempFile);
            ModelingSqlModelService.BatchImportResult result = generationService.batchImportFromArchive(
                planId, sourceDataSourceId, skipExisting, cleanOldFiles, tempFile, activeDept
            );
            auditService.auditAction("MODELING_SQL_MODEL_BATCH_IMPORT", AuditStage.SUCCESS, "plan=" + planId + " total=" + result.total() + " imported=" + result.imported(), null);
            return ApiResponses.ok(result);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @PostMapping("/generate-from-ods")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelOdsGenerateResult> generateFromOds(
        @Valid @RequestBody SqlModelOdsGenerateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelOdsGenerateResult result = generationService.generateFromOds(request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_GENERATE", AuditStage.SUCCESS, "ods", null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/governance/preview")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelGovernancePreviewResult> previewGovernance(
        @RequestBody SqlModelGovernancePreviewRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelGovernancePreviewResult result = generationService.previewGovernance(request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_GOVERNANCE_PREVIEW_READ", AuditStage.SUCCESS, request != null && request.planId() != null ? request.planId().toString() : "all", null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/governance/execute")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelGovernanceExecuteResult> executeGovernance(
        @RequestBody SqlModelGovernanceExecuteRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelGovernanceExecuteResult result = generationService.executeGovernance(request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_GOVERNANCE_EXECUTE_DELETE", AuditStage.SUCCESS, "requested=" + result.requested() + ",deleted=" + result.deleted(), null);
        return ApiResponses.ok(result);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.update(id, request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_UPDATE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        sqlModelService.delete(id, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_DELETE", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(null);
    }

    @PostMapping("/batch-delete")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingSqlModelService.BatchDeleteResult> batchDelete(
        @RequestBody ModelingSqlModelService.BatchDeleteRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ModelingSqlModelService.BatchDeleteResult result = sqlModelService.deleteBatch(request, activeDept);
        auditService.auditAction("MODELING_SQL_MODEL_BATCH_DELETE", AuditStage.SUCCESS, "requested=" + result.requested() + ",deleted=" + result.deleted() + ",failed=" + result.failed(), null);
        return ApiResponses.ok(result);
    }

    @PostMapping("/{id}/semantic/publish")
    @Transactional
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Map<String, Object>> publishSemanticContract(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.get(id, activeDept);
        Map<String, Object> result = semanticContractPublishService.publish(dto);
        auditService.auditAction("MODELING_SQL_MODEL_SEMANTIC_PUBLISH", AuditStage.SUCCESS, id.toString(), null);
        return ApiResponses.ok(result);
    }

    /**
     * 获取可用的 ODS 源表列表（用于 SQL 编辑器 source() 函数选择器）
     * 返回格式：[{schema, table, description, sourceSnippet}]
     * sourceSnippet 示例: {{ source('public', 'ods_patent_info') }}
     */
    @GetMapping("/dbt/sources")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listDbtSources(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID sourceDataSourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<InfraOdsTableMapping> mappings = odsTableMappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        UUID defaultLakeSourceId = defaultLakeDatasetGuard.currentDefaultLakeSourceId().orElse(null);
        Map<UUID, String> sourceNameCache = new HashMap<>();
        Map<String, Boolean> activeOdsCache = new HashMap<>();
        int staleSkipped = 0;
        int sourceSkipped = 0;

        List<Map<String, Object>> result = new ArrayList<>();
        for (InfraOdsTableMapping mapping : mappings) {
            if (mapping == null) continue;
            String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema().trim() : "public";
            String table = StringUtils.hasText(mapping.getOdsTable()) ? mapping.getOdsTable().trim() : null;
            if (!StringUtils.hasText(table)) continue;
            UUID effectiveSourceId = mapping.getConnectionId() != null ? mapping.getConnectionId() : defaultLakeSourceId;
            if (sourceDataSourceId != null && !sourceDataSourceId.equals(effectiveSourceId)) {
                sourceSkipped++;
                continue;
            }
            if (!hasActiveOdsDataset(schema, table, effectiveSourceId, activeOdsCache)) {
                staleSkipped++;
                continue;
            }

            // 关键词过滤
            if (kw != null) {
                boolean match = table.toLowerCase(Locale.ROOT).contains(kw)
                    || schema.toLowerCase(Locale.ROOT).contains(kw)
                    || (StringUtils.hasText(mapping.getDescription()) && mapping.getDescription().toLowerCase(Locale.ROOT).contains(kw))
                    || (StringUtils.hasText(mapping.getSystemCode()) && mapping.getSystemCode().toLowerCase(Locale.ROOT).contains(kw));
                if (!match) continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", mapping.getId());
            row.put("schema", schema);
            row.put("table", table);
            row.put("description", mapping.getDescription());
            row.put("systemCode", mapping.getSystemCode());
            row.put("bizCode", mapping.getBizCode());
            row.put("entityCode", mapping.getEntityCode());
            row.put("sourceDataSourceId", effectiveSourceId);
            String sourceDataSourceName = resolveSourceName(effectiveSourceId, sourceNameCache);
            if (!StringUtils.hasText(sourceDataSourceName)) {
                sourceDataSourceName = StringUtils.hasText(mapping.getSystemCode()) ? mapping.getSystemCode().trim() : "未知来源";
            }
            row.put("sourceDataSourceName", sourceDataSourceName);
            row.put("sourceSnippet", "{{ source('" + schema + "', '" + table + "') }}");
            result.add(row);
        }
        List<String> preview = result
            .stream()
            .limit(5)
            .map(item -> String.valueOf(item.get("schema")) + "." + String.valueOf(item.get("table")))
            .toList();
        LOG.info(
            "[dbt-sources] sourceDataSourceId={} keyword='{}' returned={} totalMappings={} staleSkipped={} preview={}",
            sourceDataSourceId,
            StringUtils.hasText(keyword) ? keyword.trim() : "",
            result.size(),
            mappings.size(),
            staleSkipped,
            preview
        );
        if (sourceSkipped > 0) {
            LOG.debug("[dbt-sources] sourceDataSourceId={} skippedBySource={}", sourceDataSourceId, sourceSkipped);
        }
        if (sourceDataSourceId != null && result.isEmpty()) {
            LOG.warn(
                "[dbt-sources] no ODS mappings matched sourceDataSourceId={} keyword='{}'; check metadata sync/catalog_dataset source binding",
                sourceDataSourceId,
                StringUtils.hasText(keyword) ? keyword.trim() : ""
            );
        }
        auditService.auditAction("MODELING_SQL_MODEL_DBT_SOURCES_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(result);
    }

    private boolean hasActiveOdsDataset(String schema, String table, UUID sourceId, Map<String, Boolean> cache) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return false;
        }
        String normalizedSchema = schema.trim();
        String normalizedTable = table.trim();
        String sourceKey = sourceId != null ? sourceId.toString() : "*";
        String key = sourceKey + "|" + normalizedSchema.toLowerCase(Locale.ROOT) + "." + normalizedTable.toLowerCase(Locale.ROOT);
        if (cache != null && cache.containsKey(key)) {
            return Boolean.TRUE.equals(cache.get(key));
        }
        boolean exists;
        if (sourceId != null) {
            exists = datasetRepository.existsBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
                sourceId,
                normalizedSchema,
                normalizedTable,
                "ODS"
            );
            if (!exists) {
                exists = datasetRepository.existsBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndEnabledTrue(
                    sourceId,
                    normalizedSchema,
                    normalizedTable
                );
            }
        } else {
            exists = datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
                normalizedSchema,
                normalizedTable,
                "ODS"
            );
            if (!exists) {
                exists = datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndEnabledTrue(normalizedSchema, normalizedTable);
            }
        }
        if (cache != null) {
            cache.put(key, exists);
        }
        return exists;
    }

    private String resolveSourceName(UUID sourceId) {
        if (sourceId == null) {
            return null;
        }
        return dataSourceRepository.findById(sourceId).map(ds -> StringUtils.hasText(ds.getName()) ? ds.getName().trim() : null).orElse(null);
    }

    private String resolveSourceName(UUID sourceId, Map<UUID, String> cache) {
        if (sourceId == null) {
            return null;
        }
        if (cache != null && cache.containsKey(sourceId)) {
            return cache.get(sourceId);
        }
        String resolved = resolveSourceName(sourceId);
        if (cache != null) {
            cache.put(sourceId, resolved);
        }
        return resolved;
    }

    /**
     * 获取可用的 dbt 模型列表（用于 SQL 编辑器 ref() 函数选择器）
     * 返回格式：[{name, layer, description, refSnippet}]
     * refSnippet 示例: {{ ref('dwd_patent') }}
     */
    @GetMapping("/dbt/refs")
    @Transactional(readOnly = true)
    public ApiResponse<List<Map<String, Object>>> listDbtRefs(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String layer,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<SqlModelDto> models = sqlModelService.list(null, null, activeDept);
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        String layerFilter = StringUtils.hasText(layer) ? layer.trim().toUpperCase(Locale.ROOT) : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (SqlModelDto model : models) {
            if (model == null || !StringUtils.hasText(model.name())) continue;

            // 分层过滤
            if (layerFilter != null) {
                String modelLayer = StringUtils.hasText(model.layer()) ? model.layer().trim().toUpperCase(Locale.ROOT) : null;
                if (modelLayer == null || !modelLayer.equals(layerFilter)) continue;
            }

            // 关键词过滤
            if (kw != null) {
                boolean match = model.name().toLowerCase(Locale.ROOT).contains(kw)
                    || (StringUtils.hasText(model.description()) && model.description().toLowerCase(Locale.ROOT).contains(kw))
                    || (StringUtils.hasText(model.tags()) && model.tags().toLowerCase(Locale.ROOT).contains(kw));
                if (!match) continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", model.id());
            row.put("name", model.name());
            row.put("layer", model.layer());
            row.put("description", model.description());
            row.put("tags", model.tags());
            row.put("sourceSystem", model.sourceSystem());
            row.put("refSnippet", "{{ ref('" + model.name() + "') }}");
            result.add(row);
        }
        auditService.auditAction("MODELING_SQL_MODEL_DBT_REFS_READ", AuditStage.SUCCESS, "list", null);
        return ApiResponses.ok(result);
    }

    private String readText(MultipartFile file, String label) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(label + " 文件不能为空");
        }
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalArgumentException("读取 " + label + " 文件失败");
        }
    }
}
