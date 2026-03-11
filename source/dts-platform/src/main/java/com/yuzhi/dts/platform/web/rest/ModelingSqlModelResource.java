package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelContractImpact;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
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
@Transactional
public class ModelingSqlModelResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String BIADMIN_NAME = "数仓 (biadmin)";
    private static final Logger LOG = LoggerFactory.getLogger(ModelingSqlModelResource.class);

    private final ModelingSqlModelService sqlModelService;
    private final ModelingSqlModelRepository sqlModelRepository;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final AuditService auditService;
    private final DataStandardSecurity security;

    public ModelingSqlModelResource(
        ModelingSqlModelService sqlModelService,
        ModelingSqlModelRepository sqlModelRepository,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        CatalogDatasetRepository datasetRepository,
        InfraDataSourceRepository dataSourceRepository,
        AuditService auditService,
        DataStandardSecurity security
    ) {
        this.sqlModelService = sqlModelService;
        this.sqlModelRepository = sqlModelRepository;
        this.odsTableMappingRepository = odsTableMappingRepository;
        this.datasetRepository = datasetRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.auditService = auditService;
        this.security = security;
    }

    @GetMapping
    public ApiResponse<List<SqlModelDto>> list(
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) String keyword,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<SqlModelDto> list = sqlModelService.list(planId, keyword, activeDept);
        auditService.audit("READ", "modeling.sql-model", "list");
        return ApiResponses.ok(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<SqlModelDto> get(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.get(id, activeDept);
        auditService.audit("READ", "modeling.sql-model", id.toString());
        return ApiResponses.ok(dto);
    }

    @GetMapping("/{id}/columns")
    public ApiResponse<List<Map<String, Object>>> listColumns(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<Map<String, Object>> columns = sqlModelService.listColumns(id, activeDept);
        auditService.audit("READ", "modeling.sql-model.columns", id.toString());
        return ApiResponses.ok(columns);
    }

    @GetMapping("/{id}/contract-impact")
    public ApiResponse<SqlModelContractImpact> contractImpact(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelContractImpact impact = sqlModelService.getContractImpact(id, activeDept);
        auditService.audit("READ", "modeling.sql-model.contract-impact", id.toString());
        return ApiResponses.ok(impact);
    }

    @PostMapping
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> create(
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.create(request, activeDept);
        auditService.audit("CREATE", "modeling.sql-model", dto.id().toString());
        return ApiResponses.ok(dto);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
        SqlModelDto dto = sqlModelService.importFromFiles(request, sqlText, csvText, activeDept);
        auditService.audit("IMPORT", "modeling.sql-model", dto.id().toString());
        return ApiResponses.ok(dto);
    }

    @PostMapping(value = "/batch-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<ModelingSqlModelService.BatchImportResult> batchImport(
        @RequestParam UUID planId,
        @RequestParam UUID sourceDataSourceId,
        @RequestParam("archive") MultipartFile archive,
        @RequestParam(required = false, defaultValue = "false") boolean skipExisting,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) throws IOException {
        Path tempFile = Files.createTempFile("batch-import-", ".zip");
        try {
            archive.transferTo(tempFile);
            ModelingSqlModelService.BatchImportResult result = sqlModelService.batchImportFromArchive(
                planId, sourceDataSourceId, skipExisting, tempFile, activeDept
            );
            auditService.audit("BATCH_IMPORT", "modeling.sql-model",
                "plan=" + planId + " total=" + result.total() + " imported=" + result.imported());
            return ApiResponses.ok(result);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @PostMapping("/generate-from-ods")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelOdsGenerateResult> generateFromOds(
        @Valid @RequestBody SqlModelOdsGenerateRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelOdsGenerateResult result = sqlModelService.generateFromOds(request, activeDept);
        auditService.audit("GENERATE", "modeling.sql-model", "ods");
        return ApiResponses.ok(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<SqlModelDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody SqlModelRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        SqlModelDto dto = sqlModelService.update(id, request, activeDept);
        auditService.audit("UPDATE", "modeling.sql-model", id.toString());
        return ApiResponses.ok(dto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(MODELING_MAINTAINER_EXPRESSION)
    public ApiResponse<Void> delete(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        sqlModelService.delete(id, activeDept);
        auditService.audit("DELETE", "modeling.sql-model", id.toString());
        return ApiResponses.ok(null);
    }

    /**
     * 获取可用的 ODS 源表列表（用于 SQL 编辑器 source() 函数选择器）
     * 返回格式：[{schema, table, description, sourceSnippet}]
     * sourceSnippet 示例: {{ source('public', 'ods_patent_info') }}
     */
    @GetMapping("/dbt/sources")
    public ApiResponse<List<Map<String, Object>>> listDbtSources(
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) UUID sourceDataSourceId,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<InfraOdsTableMapping> mappings = odsTableMappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;
        UUID fallbackSourceId = resolveFallbackSourceId();
        Map<UUID, String> sourceNameCache = new HashMap<>();
        Map<String, Boolean> activeOdsCache = new HashMap<>();
        int staleSkipped = 0;

        List<Map<String, Object>> result = new ArrayList<>();
        for (InfraOdsTableMapping mapping : mappings) {
            if (mapping == null) continue;
            String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema().trim() : "public";
            String table = StringUtils.hasText(mapping.getOdsTable()) ? mapping.getOdsTable().trim() : null;
            if (!StringUtils.hasText(table)) continue;
            UUID resolvedSourceId = resolveExistingSourceId(resolveDatasetSourceId(schema, table));
            UUID mappingSourceId = resolveExistingSourceId(mapping.getConnectionId());
            List<UUID> sourceCandidates = new ArrayList<>(3);
            if (resolvedSourceId != null) {
                sourceCandidates.add(resolvedSourceId);
            }
            if (mappingSourceId != null && !mappingSourceId.equals(resolvedSourceId)) {
                sourceCandidates.add(mappingSourceId);
            }
            if (sourceCandidates.isEmpty() && fallbackSourceId != null) {
                sourceCandidates.add(fallbackSourceId);
            }
            UUID effectiveSourceId = pickEffectiveSourceId(sourceCandidates, sourceDataSourceId);
            if (sourceDataSourceId != null && effectiveSourceId == null) {
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
        if (sourceDataSourceId != null && result.isEmpty()) {
            LOG.warn(
                "[dbt-sources] no ODS mappings matched sourceDataSourceId={} keyword='{}'; check metadata sync/catalog_dataset source binding",
                sourceDataSourceId,
                StringUtils.hasText(keyword) ? keyword.trim() : ""
            );
        }
        auditService.audit("READ", "modeling.sql-model.dbt-sources", "list");
        return ApiResponses.ok(result);
    }

    private UUID pickEffectiveSourceId(List<UUID> sourceCandidates, UUID requestedSourceId) {
        if (sourceCandidates == null || sourceCandidates.isEmpty()) {
            return null;
        }
        if (requestedSourceId != null) {
            for (UUID candidate : sourceCandidates) {
                if (requestedSourceId.equals(candidate)) {
                    return candidate;
                }
            }
            return null;
        }
        return sourceCandidates.get(0);
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

    private UUID resolveDatasetSourceId(String schema, String table) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return null;
        }
        List<CatalogDataset> datasets = datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema.trim(), table.trim());
        if (datasets == null || datasets.isEmpty()) {
            return null;
        }
        for (CatalogDataset dataset : datasets) {
            if (dataset == null) continue;
            if (dataset.getEnabled() != null && !dataset.getEnabled().booleanValue()) continue;
            if (dataset.getSourceId() != null) {
                return dataset.getSourceId();
            }
        }
        return null;
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

    private UUID resolveExistingSourceId(UUID sourceId) {
        if (sourceId == null) {
            return null;
        }
        return dataSourceRepository.existsById(sourceId) ? sourceId : null;
    }

    private UUID resolveFallbackSourceId() {
        List<com.yuzhi.dts.platform.domain.service.InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (candidates == null || candidates.isEmpty()) {
            candidates = dataSourceRepository.findAll();
        }
        UUID bestId = null;
        int bestScore = Integer.MIN_VALUE;
        for (com.yuzhi.dts.platform.domain.service.InfraDataSource source : candidates) {
            if (source == null || source.getId() == null) continue;
            int score = scoreSource(source);
            if (score > bestScore) {
                bestScore = score;
                bestId = source.getId();
            }
        }
        return bestId;
    }

    private int scoreSource(com.yuzhi.dts.platform.domain.service.InfraDataSource source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String name = source.getName() == null ? "" : source.getName().trim().toLowerCase(Locale.ROOT);
        String type = source.getType() == null ? "" : source.getType().trim().toLowerCase(Locale.ROOT);
        String jdbcUrl = source.getJdbcUrl() == null ? "" : source.getJdbcUrl().trim().toLowerCase(Locale.ROOT);
        String status = source.getStatus() == null ? "" : source.getStatus().trim().toLowerCase(Locale.ROOT);
        if (BIADMIN_NAME.equalsIgnoreCase(source.getName())) {
            score += 100;
        }
        if (name.contains("biadmin")) {
            score += 80;
        }
        if (jdbcUrl.contains("/biadmin")) {
            score += 70;
        }
        if ("postgres".equals(type) || "postgresql".equals(type)) {
            score += 50;
        } else if (StringUtils.hasText(type)) {
            score += 20;
        }
        if ("active".equals(status)) {
            score += 5;
        }
        if (StringUtils.hasText(jdbcUrl)) {
            score += 5;
        }
        return score;
    }

    /**
     * 获取可用的 dbt 模型列表（用于 SQL 编辑器 ref() 函数选择器）
     * 返回格式：[{name, layer, description, refSnippet}]
     * refSnippet 示例: {{ ref('dwd_patent') }}
     */
    @GetMapping("/dbt/refs")
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
        auditService.audit("READ", "modeling.sql-model.dbt-refs", "list");
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
