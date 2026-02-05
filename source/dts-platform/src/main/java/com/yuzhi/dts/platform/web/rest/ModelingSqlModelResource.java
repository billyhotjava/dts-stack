package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DataStandardSecurity;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

@RestController
@RequestMapping("/api/modeling/sql-models")
@Transactional
public class ModelingSqlModelResource {

    private static final String MODELING_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).CATALOG_MAINTAINERS)";

    private final ModelingSqlModelService sqlModelService;
    private final ModelingSqlModelRepository sqlModelRepository;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final AuditService auditService;
    private final DataStandardSecurity security;

    public ModelingSqlModelResource(
        ModelingSqlModelService sqlModelService,
        ModelingSqlModelRepository sqlModelRepository,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        AuditService auditService,
        DataStandardSecurity security
    ) {
        this.sqlModelService = sqlModelService;
        this.sqlModelRepository = sqlModelRepository;
        this.odsTableMappingRepository = odsTableMappingRepository;
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
            ownerDept
        );
        SqlModelDto dto = sqlModelService.importFromFiles(request, sqlText, csvText, activeDept);
        auditService.audit("IMPORT", "modeling.sql-model", dto.id().toString());
        return ApiResponses.ok(dto);
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
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<InfraOdsTableMapping> mappings = odsTableMappingRepository.findByEnabledTrueOrderByOdsSchemaAscOdsTableAsc();
        String kw = StringUtils.hasText(keyword) ? keyword.trim().toLowerCase(Locale.ROOT) : null;

        List<Map<String, Object>> result = new ArrayList<>();
        for (InfraOdsTableMapping mapping : mappings) {
            if (mapping == null) continue;
            String schema = StringUtils.hasText(mapping.getOdsSchema()) ? mapping.getOdsSchema().trim() : "public";
            String table = StringUtils.hasText(mapping.getOdsTable()) ? mapping.getOdsTable().trim() : null;
            if (!StringUtils.hasText(table)) continue;

            // 关键词过滤
            if (kw != null) {
                boolean match = table.toLowerCase(Locale.ROOT).contains(kw)
                    || schema.toLowerCase(Locale.ROOT).contains(kw)
                    || (StringUtils.hasText(mapping.getDescription()) && mapping.getDescription().toLowerCase(Locale.ROOT).contains(kw))
                    || (StringUtils.hasText(mapping.getSystemCode()) && mapping.getSystemCode().toLowerCase(Locale.ROOT).contains(kw));
                if (!match) continue;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("schema", schema);
            row.put("table", table);
            row.put("description", mapping.getDescription());
            row.put("systemCode", mapping.getSystemCode());
            row.put("bizCode", mapping.getBizCode());
            row.put("entityCode", mapping.getEntityCode());
            row.put("sourceSnippet", "{{ source('" + schema + "', '" + table + "') }}");
            result.add(row);
        }
        auditService.audit("READ", "modeling.sql-model.dbt-sources", "list");
        return ApiResponses.ok(result);
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
