package com.yuzhi.dts.platform.service.infra;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import com.yuzhi.dts.platform.service.catalog.lineage.IngestionLineageWriter;
import com.yuzhi.dts.platform.service.etl.DbtSourceService;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsColumnPlanDto;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationApplyResult;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationPreviewResponse;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsGenerationRequest;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsPrecheckResponse;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsPrecheckRuleResult;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsSyncTaskDraftResponse;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsSourceColumnRequest;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsSourceTableRequest;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsTablePlanDto;
import com.yuzhi.dts.platform.service.infra.dto.OdsGenerationDtos.OdsTechnicalColumnDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class OdsGenerationService {

    private static final String DEFAULT_ODS_SCHEMA = "ods";
    private static final String DEFAULT_BIZ_CODE = "default";
    private static final Set<String> TYPE_REVIEW_TOKENS = Set.of("number", "decimal", "numeric", "money", "float", "double", "real");

    private static final List<OdsTechnicalColumnDto> BASE_TECHNICAL_COLUMNS = List.of(
        new OdsTechnicalColumnDto("ods_id", "varchar(64)", "ODS 行标识"),
        new OdsTechnicalColumnDto("source_system", "varchar(128)", "来源系统"),
        new OdsTechnicalColumnDto("source_table", "varchar(256)", "来源表"),
        new OdsTechnicalColumnDto("source_pk", "varchar(512)", "来源主键值"),
        new OdsTechnicalColumnDto("extract_time", "timestamp", "抽取时间"),
        new OdsTechnicalColumnDto("batch_id", "varchar(128)", "接入批次"),
        new OdsTechnicalColumnDto("is_deleted", "boolean", "源端删除标记"),
        new OdsTechnicalColumnDto("created_at", "timestamp", "ODS 创建时间"),
        new OdsTechnicalColumnDto("updated_at", "timestamp", "ODS 更新时间")
    );

    private static final OdsTechnicalColumnDto RAW_JSON_COLUMN = new OdsTechnicalColumnDto("raw_json", "text", "源记录原始 JSON");

    private final InfraOdsTableMappingRepository mappingRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final DbtSourceService dbtSourceService;
    private final IngestionLineageWriter lineageWriter;

    public OdsGenerationService(
        InfraOdsTableMappingRepository mappingRepository,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSyncService columnSyncService,
        DbtSourceService dbtSourceService,
        IngestionLineageWriter lineageWriter
    ) {
        this.mappingRepository = mappingRepository;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnSyncService = columnSyncService;
        this.dbtSourceService = dbtSourceService;
        this.lineageWriter = lineageWriter;
    }

    public OdsGenerationPreviewResponse preview(InfraDataSource source, OdsGenerationRequest request) {
        List<OdsTablePlanDto> plans = buildPlans(source, request);
        String yaml = buildDbtSourceYaml(plans);
        List<String> warnings = plans.stream().flatMap(plan -> plan.warnings().stream()).distinct().toList();
        return new OdsGenerationPreviewResponse(
            source.getId(),
            source.getName(),
            resolveOdsSchema(request),
            plans,
            yaml,
            warnings
        );
    }

    @Transactional
    public OdsGenerationApplyResult apply(InfraDataSource source, OdsGenerationRequest request) {
        List<OdsTablePlanDto> plans = buildPlans(source, request);
        int mappingsUpserted = 0;
        int columnsUpserted = 0;
        int lineageCreated = 0;
        int lineageUpdated = 0;
        int lineageSkipped = 0;
        for (OdsTablePlanDto plan : plans) {
            InfraOdsTableMapping mapping = upsertMapping(source, plan);
            CatalogDataset dataset = ensureOdsDataset(source, mapping, plan);
            mapping.setDatasetId(dataset.getId());
            mapping = mappingRepository.save(mapping);
            CatalogTableSchema table = ensureCatalogTable(dataset, plan);
            columnsUpserted += columnSyncService.upsertColumns(table, toColumnSpecs(plan), CatalogColumnSyncService.STATUS_DRAFT);
            IngestionLineageWriter.LineageWriteResult lineage = lineageWriter.writeAddaxLineage(mapping, IngestionLineageWriter.LineageObservation.declared());
            lineageCreated += lineage.created();
            lineageUpdated += lineage.updated();
            lineageSkipped += lineage.skipped();
            mappingsUpserted++;
        }
        DbtSourceService.DbtSourceRefreshResult dbt = dbtSourceService.refreshOdsSources();
        List<String> warnings = plans.stream().flatMap(plan -> plan.warnings().stream()).distinct().toList();
        return new OdsGenerationApplyResult(
            source.getId(),
            source.getName(),
            mappingsUpserted,
            columnsUpserted,
            lineageCreated,
            lineageUpdated,
            lineageSkipped,
            dbt.message(),
            plans,
            warnings
        );
    }

    public OdsSyncTaskDraftResponse buildSyncTaskDraft(InfraDataSource source, OdsGenerationRequest request) {
        List<OdsTablePlanDto> plans = buildPlans(source, request);
        OdsPrecheckResponse precheck = precheck(source, request, plans);
        if ("FAIL".equalsIgnoreCase(precheck.status())) {
            String firstFailure = precheck.rules().stream()
                .filter(rule -> "FAIL".equalsIgnoreCase(rule.status()))
                .map(OdsPrecheckRuleResult::message)
                .findFirst()
                .orElse("同步任务预检失败");
            throw new IllegalArgumentException(firstFailure);
        }
        String taskName = buildSyncTaskName(source, plans);
        Map<String, Object> payload = buildSyncTaskPayload(source, request, plans, taskName);
        List<String> warnings = plans.stream().flatMap(plan -> plan.warnings().stream()).distinct().toList();
        return new OdsSyncTaskDraftResponse(source.getId(), source.getName(), taskName, payload, plans, warnings);
    }

    public OdsPrecheckResponse precheck(InfraDataSource source, OdsGenerationRequest request) {
        return precheck(source, request, buildPlans(source, request));
    }

    private OdsPrecheckResponse precheck(InfraDataSource source, OdsGenerationRequest request, List<OdsTablePlanDto> plans) {
        List<OdsPrecheckRuleResult> rules = new ArrayList<>();
        addRule(
            rules,
            "DATASOURCE_JDBC",
            "ERROR",
            StringUtils.hasText(source.getJdbcUrl()),
            source.getName(),
            "数据源具备 JDBC 连接信息",
            "请补充 JDBC URL 后重新测试连接"
        );
        addRule(
            rules,
            "DATASOURCE_VERIFIED",
            "WARN",
            source.getLastVerifiedAt() != null,
            source.getName(),
            "数据源已完成连接测试",
            "建议先执行连接测试，确认凭据和网络仍可用"
        );
        addRule(
            rules,
            "SOURCE_TABLE_SELECTED",
            "ERROR",
            plans != null && !plans.isEmpty(),
            source.getName(),
            "已选择至少一张源表",
            "请先通过 Schema Discover 选择源表"
        );

        Set<String> targetNames = new LinkedHashSet<>();
        Set<String> duplicateTargets = new LinkedHashSet<>();
        List<String> planWarnings = new ArrayList<>();
        for (OdsTablePlanDto plan : plans) {
            String sourceName = physicalName(plan.sourceSchema(), plan.sourceTable());
            String targetName = physicalName(plan.odsSchema(), plan.odsTable());
            String targetKey = targetName.toLowerCase(Locale.ROOT);
            if (!targetNames.add(targetKey)) {
                duplicateTargets.add(targetName);
            }
            boolean hasColumns = plan.columns() != null && !plan.columns().isEmpty();
            addRule(
                rules,
                "SOURCE_COLUMNS",
                "ERROR",
                hasColumns,
                sourceName,
                "源表字段已读取",
                "请重新执行 Schema Discover，并确认 includeColumns=true"
            );
            boolean hasPrimaryKey = plan.primaryKeys() != null && !plan.primaryKeys().isEmpty();
            addRule(
                rules,
                "PRIMARY_KEY_CANDIDATE",
                "WARN",
                hasPrimaryKey,
                sourceName,
                "已识别主键或业务唯一键候选",
                "建议维护主键/业务键，提升幂等写入、审计追踪和删除识别能力"
            );
            InfraOdsTableMapping existing = mappingRepository
                .findFirstByOdsSchemaIgnoreCaseAndOdsTableIgnoreCase(plan.odsSchema(), plan.odsTable())
                .orElse(null);
            boolean targetAvailable = existing == null || isSameSourceMapping(existing, source, plan);
            addRule(
                rules,
                "ODS_TARGET_OWNERSHIP",
                "ERROR",
                targetAvailable,
                targetName,
                "ODS 目标表未被其他接入映射占用",
                "请调整 system/biz/entity 命名，或先确认既有 ODS 映射归属"
            );
            if (plan.warnings() != null) {
                planWarnings.addAll(plan.warnings());
            }
        }
        addRule(
            rules,
            "ODS_TARGET_DUPLICATE",
            "ERROR",
            duplicateTargets.isEmpty(),
            duplicateTargets.isEmpty() ? "ods" : String.join(", ", duplicateTargets),
            "本次生成的 ODS 目标表名不重复",
            "请为单表设置实体编码，或调整系统/业务编码避免多个源表落到同一 ODS 表"
        );

        String syncMode = firstNonBlank(request == null ? null : request.syncMode(), "full_refresh");
        if ("incremental".equalsIgnoreCase(syncMode)) {
            boolean incrementalOk = true;
            String incrementalMessage = "已识别多表共同增量字段";
            String incrementalSuggestion = "增量任务会使用共同候选字段推进 watermark";
            try {
                String incrementalColumn = resolveIncrementalColumn(plans, syncMode);
                incrementalMessage = "已识别增量字段：" + incrementalColumn;
            } catch (IllegalArgumentException ex) {
                incrementalOk = false;
                incrementalMessage = ex.getMessage();
                incrementalSuggestion = "请改为全量模式，或为所有表选择同名时间戳增量字段";
            }
            addRule(rules, "INCREMENTAL_WATERMARK", "ERROR", incrementalOk, syncMode, incrementalMessage, incrementalSuggestion);
        } else {
            addRule(
                rules,
                "SYNC_MODE",
                "INFO",
                true,
                syncMode,
                "同步模式为全量覆盖",
                "可在增量字段明确后切换为时间戳增量"
            );
        }

        for (String warning : planWarnings.stream().filter(StringUtils::hasText).distinct().toList()) {
            rules.add(new OdsPrecheckRuleResult("TYPE_REVIEW", "WARN", "WARN", "columns", warning, "请在字段 override 能力补齐后确认精度映射"));
        }

        int failed = (int) rules.stream().filter(rule -> "FAIL".equalsIgnoreCase(rule.status())).count();
        int warned = (int) rules.stream().filter(rule -> "WARN".equalsIgnoreCase(rule.status())).count();
        int passed = (int) rules.stream().filter(rule -> "PASS".equalsIgnoreCase(rule.status())).count();
        String status = failed > 0 ? "FAIL" : warned > 0 ? "WARN" : "PASS";
        List<String> warnings = rules.stream()
            .filter(rule -> "WARN".equalsIgnoreCase(rule.status()))
            .map(OdsPrecheckRuleResult::message)
            .distinct()
            .toList();
        return new OdsPrecheckResponse(
            source.getId(),
            source.getName(),
            status,
            rules.size(),
            passed,
            warned,
            failed,
            rules,
            plans,
            warnings
        );
    }

    private List<OdsTablePlanDto> buildPlans(InfraDataSource source, OdsGenerationRequest request) {
        if (source == null || source.getId() == null) {
            throw new IllegalArgumentException("数据源无效");
        }
        if (request == null || request.tables() == null || request.tables().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一张源表");
        }
        String odsSchema = resolveOdsSchema(request);
        String systemCode = safeCode(firstNonBlank(request.systemCode(), source.getConnectorKey(), source.getType(), source.getName()), "src");
        String bizCode = safeCode(request.bizCode(), DEFAULT_BIZ_CODE);
        boolean includeTechnical = request.includeTechnicalColumns() == null || Boolean.TRUE.equals(request.includeTechnicalColumns());
        boolean includeRawJson = request.includeRawJson() == null || Boolean.TRUE.equals(request.includeRawJson());
        List<OdsTablePlanDto> plans = new ArrayList<>();
        for (OdsSourceTableRequest table : request.tables()) {
            if (table == null || !StringUtils.hasText(table.name())) {
                continue;
            }
            plans.add(buildPlan(source, table, odsSchema, systemCode, bizCode, includeTechnical, includeRawJson, request));
        }
        if (plans.isEmpty()) {
            throw new IllegalArgumentException("未解析到有效源表");
        }
        return plans;
    }

    private OdsTablePlanDto buildPlan(
        InfraDataSource source,
        OdsSourceTableRequest table,
        String odsSchema,
        String defaultSystemCode,
        String defaultBizCode,
        boolean includeTechnical,
        boolean includeRawJson,
        OdsGenerationRequest request
    ) {
        String sourceTable = trim(table.name());
        String sourceSchema = trim(table.schema());
        String entityCode = safeCode(
            firstNonBlank(request.tables() != null && request.tables().size() == 1 ? request.entityCode() : null, sourceTable),
            "entity"
        );
        String odsTable = buildOdsTableName(defaultSystemCode, defaultBizCode, entityCode);
        List<String> primaryKeys = normalizeList(table.primaryKeys());
        List<String> incrementalCandidates = normalizeList(table.incrementalCandidates());
        List<String> warnings = new ArrayList<>();
        List<OdsTechnicalColumnDto> technicalColumns = includeTechnical ? technicalColumns(includeRawJson) : List.of();
        Set<String> usedNames = new LinkedHashSet<>();
        for (OdsTechnicalColumnDto column : technicalColumns) {
            usedNames.add(column.name().toLowerCase(Locale.ROOT));
        }
        List<OdsColumnPlanDto> columns = new ArrayList<>();
        for (OdsSourceColumnRequest sourceColumn : table.columns() == null ? List.<OdsSourceColumnRequest>of() : table.columns()) {
            if (sourceColumn == null || !StringUtils.hasText(sourceColumn.name())) {
                continue;
            }
            String targetName = uniqueColumnName(
                safeColumn(firstNonBlank(sourceColumn.targetName(), sourceColumn.name())),
                usedNames
            );
            String sourceType = firstNonBlank(sourceColumn.dataType(), sourceColumn.nativeType(), "text");
            String odsType = firstNonBlank(sourceColumn.targetDataType(), mapOdsType(sourceType));
            boolean review = needsTypeReview(sourceType);
            if (review) {
                warnings.add("字段 " + sourceColumn.name() + " 的数值精度需要人工确认");
            }
            columns.add(
                new OdsColumnPlanDto(
                    sourceColumn.name().trim(),
                    targetName,
                    sourceType,
                    odsType,
                    trim(sourceColumn.comment()),
                    sourceColumn.nullable() == null ? Boolean.TRUE : sourceColumn.nullable(),
                    Boolean.TRUE.equals(sourceColumn.primaryKey()) || containsIgnoreCase(primaryKeys, sourceColumn.name()),
                    Boolean.TRUE.equals(sourceColumn.indexed()),
                    Boolean.TRUE.equals(sourceColumn.incrementalCandidate()) || containsIgnoreCase(incrementalCandidates, sourceColumn.name()),
                    review
                )
            );
        }
        if (columns.isEmpty()) {
            warnings.add("未读取源表字段，ODS 字段需在任务向导中补充");
        }
        return new OdsTablePlanDto(
            sourceSchema,
            sourceTable,
            odsSchema,
            odsTable,
            defaultSystemCode,
            defaultBizCode,
            entityCode,
            primaryKeys,
            incrementalCandidates,
            columns,
            technicalColumns,
            buildCreateTableSql(odsSchema, odsTable, columns, technicalColumns),
            buildTableSourceYaml(odsSchema, odsTable, table, columns, technicalColumns),
            buildAddaxDraft(source, table, odsSchema, odsTable, columns, request),
            buildAirflowDraft(source, table, odsSchema, odsTable),
            warnings
        );
    }

    private InfraOdsTableMapping upsertMapping(InfraDataSource source, OdsTablePlanDto plan) {
        String namespace = plan.sourceSchema() == null ? "" : plan.sourceSchema();
        InfraOdsTableMapping mapping = mappingRepository
            .findFirstByConnectionIdAndStreamNameIgnoreCaseAndStreamNamespaceIgnoreCase(source.getId(), plan.sourceTable(), namespace)
            .orElseGet(InfraOdsTableMapping::new);
        mapping.setConnectionId(source.getId());
        mapping.setStreamName(plan.sourceTable());
        mapping.setStreamNamespace(namespace);
        mapping.setSystemCode(plan.systemCode());
        mapping.setBizCode(plan.bizCode());
        mapping.setEntityCode(plan.entityCode());
        mapping.setOdsSchema(plan.odsSchema());
        mapping.setOdsTable(plan.odsTable());
        mapping.setOwnerDept(source.getOwnerDept());
        mapping.setEnabled(Boolean.TRUE);
        mapping.setDescription(buildDescription(plan));
        return mappingRepository.save(mapping);
    }

    private CatalogDataset ensureOdsDataset(InfraDataSource source, InfraOdsTableMapping mapping, OdsTablePlanDto plan) {
        CatalogDataset dataset = mapping.getDatasetId() == null
            ? null
            : datasetRepository.findById(mapping.getDatasetId()).orElse(null);
        if (dataset == null) {
            dataset = datasetRepository
                .findFirstBySourceIdAndHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(source.getId(), plan.odsSchema(), plan.odsTable())
                .orElseGet(CatalogDataset::new);
        }
        dataset.setName(truncate(plan.odsTable(), 128));
        dataset.setType("DATASET");
        dataset.setSourceId(source.getId());
        dataset.setHiveDatabase(plan.odsSchema());
        dataset.setHiveTable(plan.odsTable());
        dataset.setWarehouseLayer("ODS");
        dataset.setOwnerDept(source.getOwnerDept());
        dataset.setEnabled(Boolean.TRUE);
        dataset.setSnapshotTime(Instant.now());
        if (!StringUtils.hasText(dataset.getClassification())) {
            dataset.setClassification(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
        }
        if (!StringUtils.hasText(dataset.getDescription())) {
            dataset.setDescription(truncate("ODS table generated from " + physicalName(plan.sourceSchema(), plan.sourceTable()), 2048));
        }
        return datasetRepository.save(dataset);
    }

    private CatalogTableSchema ensureCatalogTable(CatalogDataset dataset, OdsTablePlanDto plan) {
        CatalogTableSchema table = tableRepository
            .findFirstByDatasetAndNameIgnoreCase(dataset, plan.odsTable())
            .orElseGet(CatalogTableSchema::new);
        table.setDataset(dataset);
        table.setName(plan.odsTable());
        if (!StringUtils.hasText(table.getClassification())) {
            table.setClassification(dataset.getClassification());
        }
        if (!StringUtils.hasText(table.getOwner())) {
            table.setOwner(dataset.getOwner());
        }
        return tableRepository.save(table);
    }

    private List<ColumnSpec> toColumnSpecs(OdsTablePlanDto plan) {
        List<ColumnSpec> specs = new ArrayList<>();
        for (OdsColumnPlanDto column : plan.columns()) {
            specs.add(new ColumnSpec(column.targetName(), column.odsType(), column.nullable(), column.comment(), null, null, null, null));
        }
        for (OdsTechnicalColumnDto column : plan.technicalColumns()) {
            specs.add(new ColumnSpec(column.name(), column.dataType(), Boolean.TRUE, column.comment(), "technical", null, null, null));
        }
        return specs;
    }

    private List<OdsTechnicalColumnDto> technicalColumns(boolean includeRawJson) {
        if (!includeRawJson) {
            return BASE_TECHNICAL_COLUMNS;
        }
        List<OdsTechnicalColumnDto> result = new ArrayList<>(BASE_TECHNICAL_COLUMNS);
        result.add(RAW_JSON_COLUMN);
        return result;
    }

    private String buildCreateTableSql(
        String schema,
        String table,
        List<OdsColumnPlanDto> columns,
        List<OdsTechnicalColumnDto> technicalColumns
    ) {
        List<String> lines = new ArrayList<>();
        for (OdsColumnPlanDto column : columns) {
            lines.add("  " + quote(column.targetName()) + " " + column.odsType());
        }
        for (OdsTechnicalColumnDto column : technicalColumns) {
            lines.add("  " + quote(column.name()) + " " + column.dataType());
        }
        return "CREATE TABLE IF NOT EXISTS " + quote(schema) + "." + quote(table) + " (\n" +
            String.join(",\n", lines) +
            "\n);";
    }

    private String buildDbtSourceYaml(List<OdsTablePlanDto> plans) {
        if (plans.isEmpty()) {
            return "version: 2\nsources: []\n";
        }
        Map<String, List<OdsTablePlanDto>> grouped = new LinkedHashMap<>();
        for (OdsTablePlanDto plan : plans) {
            grouped.computeIfAbsent(plan.odsSchema(), ignored -> new ArrayList<>()).add(plan);
        }
        StringBuilder sb = new StringBuilder("version: 2\n\nsources:\n");
        for (Map.Entry<String, List<OdsTablePlanDto>> entry : grouped.entrySet()) {
            sb.append("  - name: ").append(entry.getKey()).append("\n");
            sb.append("    schema: ").append(entry.getKey()).append("\n");
            sb.append("    tables:\n");
            for (OdsTablePlanDto plan : entry.getValue()) {
                appendTableYaml(sb, plan, 6);
            }
        }
        return sb.toString();
    }

    private String buildTableSourceYaml(
        String odsSchema,
        String odsTable,
        OdsSourceTableRequest table,
        List<OdsColumnPlanDto> columns,
        List<OdsTechnicalColumnDto> technicalColumns
    ) {
        OdsTablePlanDto plan = new OdsTablePlanDto(
            trim(table.schema()),
            trim(table.name()),
            odsSchema,
            odsTable,
            "",
            "",
            "",
            normalizeList(table.primaryKeys()),
            normalizeList(table.incrementalCandidates()),
            columns,
            technicalColumns,
            "",
            "",
            Map.of(),
            Map.of(),
            List.of()
        );
        StringBuilder sb = new StringBuilder();
        appendTableYaml(sb, plan, 0);
        return sb.toString();
    }

    private void appendTableYaml(StringBuilder sb, OdsTablePlanDto plan, int indent) {
        String p = " ".repeat(Math.max(0, indent));
        sb.append(p).append("- name: ").append(plan.odsTable()).append("\n");
        sb.append(p).append("  description: \"ODS generated from ").append(escapeYaml(physicalName(plan.sourceSchema(), plan.sourceTable()))).append("\"\n");
        sb.append(p).append("  meta:\n");
        sb.append(p).append("    source_schema: \"").append(escapeYaml(plan.sourceSchema())).append("\"\n");
        sb.append(p).append("    source_table: \"").append(escapeYaml(plan.sourceTable())).append("\"\n");
        sb.append(p).append("    system: \"").append(escapeYaml(plan.systemCode())).append("\"\n");
        sb.append(p).append("    biz: \"").append(escapeYaml(plan.bizCode())).append("\"\n");
        sb.append(p).append("    entity: \"").append(escapeYaml(plan.entityCode())).append("\"\n");
        sb.append(p).append("  columns:\n");
        for (OdsColumnPlanDto column : plan.columns()) {
            sb.append(p).append("    - name: ").append(column.targetName()).append("\n");
            sb.append(p).append("      data_type: \"").append(escapeYaml(column.odsType())).append("\"\n");
            if (StringUtils.hasText(column.comment())) {
                sb.append(p).append("      description: \"").append(escapeYaml(column.comment())).append("\"\n");
            }
        }
        for (OdsTechnicalColumnDto column : plan.technicalColumns()) {
            sb.append(p).append("    - name: ").append(column.name()).append("\n");
            sb.append(p).append("      data_type: \"").append(escapeYaml(column.dataType())).append("\"\n");
            sb.append(p).append("      description: \"").append(escapeYaml(column.comment())).append("\"\n");
            sb.append(p).append("      meta:\n");
            sb.append(p).append("        dts_technical: true\n");
        }
    }

    private Map<String, Object> buildAddaxDraft(
        InfraDataSource source,
        OdsSourceTableRequest table,
        String odsSchema,
        String odsTable,
        List<OdsColumnPlanDto> columns,
        OdsGenerationRequest request
    ) {
        Map<String, Object> connection = new LinkedHashMap<>();
        if (StringUtils.hasText(source.getJdbcUrl())) {
            connection.put("jdbcUrl", source.getJdbcUrl());
        }
        connection.put("table", List.of(physicalName(table.schema(), table.name())));
        Map<String, Object> reader = new LinkedHashMap<>();
        reader.put("name", resolveReaderType(source));
        reader.put("parameter", Map.of("connection", List.of(connection)));
        Map<String, Object> writer = new LinkedHashMap<>();
        writer.put("name", "postgresqlwriter");
        writer.put("parameter", Map.of("table", physicalName(odsSchema, odsTable), "column", columns.stream().map(OdsColumnPlanDto::targetName).toList()));
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("syncMode", firstNonBlank(request.syncMode(), "full_refresh"));
        draft.put("reader", reader);
        draft.put("writer", writer);
        draft.put("requiresConfirmation", true);
        return draft;
    }

    private Map<String, Object> buildAirflowDraft(InfraDataSource source, OdsSourceTableRequest table, String odsSchema, String odsTable) {
        String dagId = "dts_ingest_" + safeCode(source.getName(), "source") + "_" + safeCode(odsTable, "ods");
        Map<String, Object> draft = new LinkedHashMap<>();
        draft.put("dagId", dagId);
        draft.put("tasks", List.of("generate_addax_job", "ensure_ods_table", "run_addax", "refresh_dbt_sources", "write_lineage"));
        draft.put("source", physicalName(table.schema(), table.name()));
        draft.put("target", physicalName(odsSchema, odsTable));
        return draft;
    }

    private String resolveReaderType(InfraDataSource source) {
        String type = source == null ? null : source.getType();
        String normalized = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (normalized.contains("mysql")) return "mysqlreader";
        if (normalized.contains("oracle")) return "oraclereader";
        if (normalized.contains("sqlserver") || normalized.contains("mssql")) return "sqlserverreader";
        if (normalized.contains("postgres") || normalized.contains("dm") || normalized.contains("kingbase") || normalized.contains("gbase")) {
            return "postgresqlreader";
        }
        return "rdbmsreader";
    }

    private String buildDescription(OdsTablePlanDto plan) {
        return "DTS Connector Center: " + physicalName(plan.sourceSchema(), plan.sourceTable()) + " -> " + physicalName(plan.odsSchema(), plan.odsTable());
    }

    private Map<String, Object> buildSyncTaskPayload(
        InfraDataSource source,
        OdsGenerationRequest request,
        List<OdsTablePlanDto> plans,
        String taskName
    ) {
        List<String> sourceTables = plans.stream().map(plan -> physicalName(plan.sourceSchema(), plan.sourceTable())).toList();
        List<String> targetTables = plans.stream().map(plan -> physicalName(plan.odsSchema(), plan.odsTable())).toList();
        String readerType = resolveReaderType(source);
        String syncMode = firstNonBlank(request == null ? null : request.syncMode(), "full_refresh");

        Map<String, Object> sourceConfig = new LinkedHashMap<>();
        sourceConfig.put("readerType", readerType);
        sourceConfig.put("table", sourceTables);

        Map<String, Object> sourceSpec = new LinkedHashMap<>();
        sourceSpec.put("dataSourceId", source.getId());
        sourceSpec.put("type", readerType);
        sourceSpec.put("config", sourceConfig);

        Map<String, Object> writerConnection = new LinkedHashMap<>();
        writerConnection.put("table", targetTables);

        Map<String, Object> writerConfig = new LinkedHashMap<>();
        writerConfig.put("connection", List.of(writerConnection));
        writerConfig.put("table", targetTables);
        writerConfig.put("autoCreateTables", Boolean.TRUE);
        writerConfig.put("column", List.of("*"));

        Map<String, Object> destinationSpec = new LinkedHashMap<>();
        destinationSpec.put("usePlatformDefault", Boolean.TRUE);
        destinationSpec.put("config", writerConfig);

        Map<String, Object> syncSpec = new LinkedHashMap<>();
        syncSpec.put("mode", syncMode);
        String incrementalColumn = resolveIncrementalColumn(plans, syncMode);
        if (StringUtils.hasText(incrementalColumn)) {
            syncSpec.put("incrementalColumn", incrementalColumn);
            syncSpec.put("incrementalType", "timestamp");
        }
        Map<String, Object> schedule = new LinkedHashMap<>();
        schedule.put("type", "manual");
        syncSpec.put("schedule", schedule);

        Map<String, Object> streamsSpec = new LinkedHashMap<>();
        streamsSpec.put("selection", "manual");
        streamsSpec.put("include", sourceTables);
        streamsSpec.put("schema", firstNonBlank(plans.get(0).sourceSchema(), ""));

        Map<String, Object> airflowSpec = new LinkedHashMap<>();
        airflowSpec.put("enabled", Boolean.TRUE);
        airflowSpec.put("dagId", "dts_ingest_" + safeCode(taskName, "ods"));
        airflowSpec.put("scheduleType", "manual");

        Map<String, Object> dbtSpec = new LinkedHashMap<>();
        dbtSpec.put("modelSelector", "source:" + plans.get(0).odsSchema());

        Map<String, Object> lineageSpec = new LinkedHashMap<>();
        lineageSpec.put("enabled", Boolean.TRUE);
        lineageSpec.put("domain", "connector-center");
        lineageSpec.put("tags", List.of("ods", "connector-center"));

        Map<String, Object> schemaChanges = new LinkedHashMap<>();
        schemaChanges.put("mode", "detect");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("draft", Boolean.FALSE);
        payload.put("name", taskName);
        payload.put("description", "DTS Connector Center generated task: " + source.getName() + " -> " + plans.get(0).odsSchema());
        payload.put("source", sourceSpec);
        payload.put("destination", destinationSpec);
        payload.put("sync", syncSpec);
        payload.put("streams", streamsSpec);
        payload.put("airflow", airflowSpec);
        payload.put("dbt", dbtSpec);
        payload.put("lineage", lineageSpec);
        payload.put("schemaChanges", schemaChanges);
        payload.put("runNow", Boolean.FALSE);
        return payload;
    }

    private String buildSyncTaskName(InfraDataSource source, List<OdsTablePlanDto> plans) {
        String sourceCode = safeCode(firstNonBlank(source.getConnectorKey(), source.getType(), source.getName()), "source");
        String entity = plans == null || plans.isEmpty() ? "ods" : safeCode(plans.get(0).entityCode(), "ods");
        String suffix = plans != null && plans.size() > 1 ? "_plus_" + (plans.size() - 1) : "";
        return truncate("ingest_" + sourceCode + "_to_ods_" + entity + suffix, 128);
    }

    private String resolveIncrementalColumn(List<OdsTablePlanDto> plans, String syncMode) {
        if (!"incremental".equalsIgnoreCase(syncMode)) {
            return null;
        }
        Map<String, String> commonCandidates = null;
        for (OdsTablePlanDto plan : plans) {
            if (plan == null || plan.incrementalCandidates() == null || plan.incrementalCandidates().isEmpty()) {
                throw new IllegalArgumentException("增量同步需要每张源表都有增量字段候选");
            }
            Map<String, String> candidates = new LinkedHashMap<>();
            for (String candidate : plan.incrementalCandidates()) {
                if (StringUtils.hasText(candidate)) {
                    candidates.putIfAbsent(candidate.trim().toLowerCase(Locale.ROOT), candidate.trim());
                }
            }
            if (commonCandidates == null) {
                commonCandidates = new LinkedHashMap<>(candidates);
            } else {
                commonCandidates.keySet().retainAll(candidates.keySet());
            }
        }
        if (commonCandidates == null || commonCandidates.isEmpty()) {
            throw new IllegalArgumentException("多表增量同步需要存在相同的增量字段候选");
        }
        for (String preferred : List.of("updated_at", "update_time", "modified_at", "modify_time", "last_updated_at")) {
            String value = commonCandidates.get(preferred);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return commonCandidates.values().iterator().next();
    }

    private void addRule(
        List<OdsPrecheckRuleResult> rules,
        String code,
        String level,
        boolean passed,
        String target,
        String message,
        String suggestion
    ) {
        String normalizedLevel = firstNonBlank(level, "ERROR").toUpperCase(Locale.ROOT);
        String status;
        if (passed) {
            status = "PASS";
        } else if ("ERROR".equals(normalizedLevel)) {
            status = "FAIL";
        } else if ("INFO".equals(normalizedLevel)) {
            status = "PASS";
        } else {
            status = "WARN";
        }
        rules.add(new OdsPrecheckRuleResult(code, normalizedLevel, status, target, message, suggestion));
    }

    private boolean isSameSourceMapping(InfraOdsTableMapping existing, InfraDataSource source, OdsTablePlanDto plan) {
        if (existing == null) {
            return true;
        }
        if (source == null || plan == null || source.getId() == null) {
            return false;
        }
        String namespace = plan.sourceSchema() == null ? "" : plan.sourceSchema();
        return source.getId().equals(existing.getConnectionId()) &&
            equalsIgnoreCase(plan.sourceTable(), existing.getStreamName()) &&
            equalsIgnoreCase(namespace, existing.getStreamNamespace());
    }

    private boolean equalsIgnoreCase(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equalsIgnoreCase(b);
    }

    private String resolveOdsSchema(OdsGenerationRequest request) {
        return safeColumn(firstNonBlank(request == null ? null : request.odsSchema(), DEFAULT_ODS_SCHEMA));
    }

    private String buildOdsTableName(String systemCode, String bizCode, String entityCode) {
        String explicitEntity = safeCode(entityCode, "entity");
        boolean hasBiz = StringUtils.hasText(bizCode) && !DEFAULT_BIZ_CODE.equalsIgnoreCase(bizCode);
        String name = "ods_" + systemCode + (hasBiz ? "_" + bizCode : "") + "_" + explicitEntity;
        return truncate(safeColumn(name), 128);
    }

    private String mapOdsType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
        if (normalized.contains("bigint") || normalized.contains("int8") || normalized.contains("long")) return "bigint";
        if (normalized.contains("smallint") || normalized.contains("tinyint") || normalized.contains("int2")) return "integer";
        if (normalized.contains("int") || normalized.contains("serial")) return "integer";
        if (normalized.contains("decimal") || normalized.contains("numeric") || normalized.contains("number") || normalized.contains("money")) return "numeric";
        if (normalized.contains("double") || normalized.contains("float") || normalized.contains("real")) return "double precision";
        if (normalized.contains("bool") || normalized.contains("bit")) return "boolean";
        if (normalized.equals("date") || normalized.contains(" date")) return "date";
        if (normalized.contains("timestamp") || normalized.contains("datetime")) return "timestamp";
        if (normalized.contains("time")) return "time";
        if (normalized.contains("json")) return "jsonb";
        if (normalized.contains("blob") || normalized.contains("binary") || normalized.contains("bytea")) return "bytea";
        return "text";
    }

    private boolean needsTypeReview(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
        return TYPE_REVIEW_TOKENS.stream().anyMatch(normalized::contains);
    }

    private String uniqueColumnName(String base, Set<String> usedNames) {
        String safe = safeColumn(base);
        String candidate = safe;
        int i = 2;
        while (usedNames.contains(candidate.toLowerCase(Locale.ROOT))) {
            candidate = safe + "_" + i;
            i++;
        }
        usedNames.add(candidate.toLowerCase(Locale.ROOT));
        return candidate;
    }

    private String safeColumn(String value) {
        String text = trim(value);
        if (!StringUtils.hasText(text)) {
            return "field";
        }
        String safe = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
        if (!StringUtils.hasText(safe)) {
            safe = "field";
        }
        if (Character.isDigit(safe.charAt(0))) {
            safe = "c_" + safe;
        }
        return safe;
    }

    private String safeCode(String value, String fallback) {
        String safe = safeColumn(value);
        return StringUtils.hasText(safe) ? safe : fallback;
    }

    private String quote(String identifier) {
        return "\"" + String.valueOf(identifier).replace("\"", "\"\"") + "\"";
    }

    private String physicalName(String schema, String table) {
        return StringUtils.hasText(schema) ? schema.trim() + "." + table : table;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private List<String> normalizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private boolean containsIgnoreCase(List<String> values, String needle) {
        if (values == null || !StringUtils.hasText(needle)) {
            return false;
        }
        for (String value : values) {
            if (needle.trim().equalsIgnoreCase(trim(value))) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    private String escapeYaml(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
