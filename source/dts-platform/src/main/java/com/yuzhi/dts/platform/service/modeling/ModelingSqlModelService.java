package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Comparator;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class ModelingSqlModelService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelingSqlModelService.class);
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String BIADMIN_NAME = "数仓 (biadmin)";

    private final ModelingSqlModelRepository repo;
    private final ModelingPlanRepository planRepo;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final DataStandardSecurity security;
    private final DbtConfigService dbtConfigService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ModelingSqlModelService(
        ModelingSqlModelRepository repo,
        ModelingPlanRepository planRepo,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        InfraDataSourceRepository dataSourceRepository,
        OrganizationVisibilityService organizationVisibilityService,
        DataStandardSecurity security,
        DbtConfigService dbtConfigService,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        CatalogColumnSyncService columnSyncService,
        AuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.planRepo = planRepo;
        this.odsTableMappingRepository = odsTableMappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.organizationVisibilityService = organizationVisibilityService;
        this.security = security;
        this.dbtConfigService = dbtConfigService;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.columnSyncService = columnSyncService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<SqlModelDto> list(UUID planId, String keyword, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        List<ModelingSqlModel> models = planId == null ? repo.findAll() : repo.findByPlanId(planId);
        String kw = trimToNull(keyword);
        List<SqlModelDto> result = new ArrayList<>();
        for (ModelingSqlModel model : models) {
            if (!isOwnerDeptVisible(model != null ? model.getOwnerDept() : null, activeDept, instituteScope)) {
                continue;
            }
            if (kw != null && !matchKeyword(model, kw)) {
                continue;
            }
            result.add(toDto(model));
        }
        result.sort((a, b) -> String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));
        return result;
    }

    public SqlModelDto get(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该模型");
        }
        return toDto(model);
    }

    public List<Map<String, Object>> listColumns(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该模型");
        }

        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        String tableName = resolveModelTable(model);
        String schemaName = resolveModelSchema(model, view);
        if (StringUtils.hasText(tableName) && StringUtils.hasText(schemaName)) {
            CatalogDataset dataset = datasetRepository
                .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schemaName, tableName)
                .orElse(null);
            if (dataset != null) {
                CatalogTableSchema table = tableRepository
                    .findFirstByDatasetAndNameIgnoreCase(dataset, tableName)
                    .orElse(null);
                if (table != null) {
                    List<CatalogColumnSchema> columns = columnRepository.findByTable(table);
                    return columns
                        .stream()
                        .map(this::toColumnPayload)
                        .toList();
                }
            }
        }

        String projectDir = view != null && view.config() != null ? view.config().projectDir() : null;
        List<ColumnSpec> specs = resolveCsvSpecs(model, projectDir);
        if (specs.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> payload = new ArrayList<>();
        for (ColumnSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", spec.name());
            item.put("dataType", spec.dataType());
            item.put("comment", spec.comment());
            item.put("status", CatalogColumnSyncService.STATUS_DRAFT);
            payload.add(item);
        }
        return payload;
    }

    public SqlModelDto create(SqlModelRequest request, String activeDeptHeader) {
        ensureWorkspaceWritable();
        ModelingSqlModel model = new ModelingSqlModel();
        apply(model, request, activeDeptHeader, true);
        ModelingSqlModel saved = repo.save(model);
        writeModelFile(saved);
        syncDraftColumns(saved);
        return toDto(saved);
    }

    public SqlModelDto importFromFiles(SqlModelRequest request, String sqlText, String csvText, String activeDeptHeader) {
        if (!StringUtils.hasText(sqlText)) {
            throw new IllegalArgumentException("SQL 内容不能为空");
        }
        SqlModelRequest normalized = new SqlModelRequest(
            request.planId(),
            request.name(),
            request.alias(),
            request.layer(),
            request.sourceDataSourceId(),
            request.schemaName(),
            request.materialized(),
            request.tags(),
            request.description(),
            sqlText,
            request.enabled(),
            request.status(),
            request.ownerDept()
        );
        SqlModelDto dto = create(normalized, activeDeptHeader);
        if (StringUtils.hasText(csvText)) {
            writeCsvSidecar(dto.modelPath(), csvText);
            ModelingSqlModel saved = repo.findById(dto.id()).orElse(null);
            if (saved != null) {
                syncDraftColumns(saved);
            }
        }
        return dto;
    }

    public SqlModelOdsGenerateResult generateFromOds(SqlModelOdsGenerateRequest request, String activeDeptHeader) {
        if (request == null || request.planId() == null) {
            throw new IllegalArgumentException("请选择项目空间");
        }
        if (request.mappingIds() == null || request.mappingIds().isEmpty()) {
            throw new IllegalArgumentException("请至少选择一个 ODS 表");
        }

        boolean createDwd = request.createDwd() == null || request.createDwd();
        boolean createDws = request.createDws() == null || request.createDws();
        boolean createAds = request.createAds() == null || request.createAds();
        if (!createDwd && !createDws && !createAds) {
            throw new IllegalArgumentException("请至少选择一个分层（DWD/DWS/ADS）");
        }

        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ensureWorkspaceWritable();
        resolvePlan(request.planId(), activeDeptHeader);
        UUID preferredSourceId = resolveUsableSourceId(request.sourceDataSourceId(), activeDeptHeader, "request");

        UUID fallbackSourceId = resolveFallbackSourceId(activeDeptHeader);

        Set<UUID> selectedIds = new LinkedHashSet<>(request.mappingIds());
        List<InfraOdsTableMapping> mappings = odsTableMappingRepository.findAllById(selectedIds)
            .stream()
            .filter(mapping -> mapping != null && Boolean.TRUE.equals(mapping.getEnabled()) && isOwnerDeptVisible(mapping.getOwnerDept(), activeDept, instituteScope))
            .sorted(
                Comparator.comparing((InfraOdsTableMapping m) -> defaultText(trimToNull(m.getOdsSchema()), "public"), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(m -> defaultText(trimToNull(m.getOdsTable()), ""))
            )
            .toList();

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("未找到可用的 ODS 表映射");
        }

        int created = 0;
        int updated = 0;
        List<String> createdModels = new ArrayList<>();
        List<String> updatedModels = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        boolean overwrite = request.overwriteExisting() == null || request.overwriteExisting();

        for (InfraOdsTableMapping mapping : mappings) {
            String odsSchema = defaultText(trimToNull(mapping.getOdsSchema()), "public");
            String odsTable = trimToNull(mapping.getOdsTable());
            if (!StringUtils.hasText(odsTable)) {
                skipped.add("mapping:" + mapping.getId() + " (ODS 表名为空)");
                continue;
            }
            UUID modelSourceId = preferredSourceId;
            if (modelSourceId == null) {
                modelSourceId = resolveUsableSourceId(mapping.getConnectionId(), activeDeptHeader, "mapping");
            }
            if (modelSourceId == null) {
                modelSourceId = resolveOdsDatasetSourceId(odsSchema, odsTable, activeDeptHeader);
            }
            if (modelSourceId == null) {
                modelSourceId = fallbackSourceId;
            }
            if (modelSourceId == null) {
                skipped.add(odsSchema + "." + odsTable + " (未找到可用来源数据源)");
                continue;
            }

            String entityToken = resolveEntityToken(mapping, odsTable);
            if (!StringUtils.hasText(entityToken)) {
                skipped.add(odsSchema + "." + odsTable + " (无法推断模型名)");
                continue;
            }

            String extraTags = mergeTags(trimToNull(request.tags()), trimToNull(mapping.getSystemCode()));
            String descriptionBase = trimToNull(mapping.getDescription());

            GenerateLayerResult dwdResult = null;
            if (createDwd) {
                dwdResult = upsertGeneratedModel(
                    request.planId(),
                    modelSourceId,
                    "dwd_" + entityToken,
                    "DWD",
                    request.schemaName(),
                    request.materialized(),
                    extraTags,
                    buildLayerDescription("DWD", odsSchema, odsTable, descriptionBase),
                    request.enabled(),
                    request.status(),
                    request.ownerDept(),
                    """
                    select
                      *
                    from {{ source('%s', '%s') }}
                    """.formatted(odsSchema, odsTable),
                    activeDeptHeader,
                    overwrite
                );
                created += dwdResult.created() ? 1 : 0;
                updated += dwdResult.updated() ? 1 : 0;
                if (dwdResult.created()) createdModels.add(dwdResult.modelName());
                if (dwdResult.updated()) updatedModels.add(dwdResult.modelName());
                if (dwdResult.skippedReason() != null) skipped.add(dwdResult.skippedReason());
            }

            GenerateLayerResult dwsResult = null;
            if (createDws) {
                String dwdRef = dwdResult != null ? dwdResult.modelName() : "dwd_" + entityToken;
                dwsResult = upsertGeneratedModel(
                    request.planId(),
                    modelSourceId,
                    "dws_" + entityToken,
                    "DWS",
                    request.schemaName(),
                    request.materialized(),
                    extraTags,
                    buildLayerDescription("DWS", odsSchema, odsTable, descriptionBase),
                    request.enabled(),
                    request.status(),
                    request.ownerDept(),
                    """
                    select
                      *
                    from {{ ref('%s') }}
                    """.formatted(dwdRef),
                    activeDeptHeader,
                    overwrite
                );
                created += dwsResult.created() ? 1 : 0;
                updated += dwsResult.updated() ? 1 : 0;
                if (dwsResult.created()) createdModels.add(dwsResult.modelName());
                if (dwsResult.updated()) updatedModels.add(dwsResult.modelName());
                if (dwsResult.skippedReason() != null) skipped.add(dwsResult.skippedReason());
            }

            if (createAds) {
                String dwsRef = dwsResult != null ? dwsResult.modelName() : "dws_" + entityToken;
                GenerateLayerResult adsResult = upsertGeneratedModel(
                    request.planId(),
                    modelSourceId,
                    "ads_" + entityToken,
                    "ADS",
                    request.schemaName(),
                    request.materialized(),
                    extraTags,
                    buildLayerDescription("ADS", odsSchema, odsTable, descriptionBase),
                    request.enabled(),
                    request.status(),
                    request.ownerDept(),
                    """
                    select
                      *
                    from {{ ref('%s') }}
                    """.formatted(dwsRef),
                    activeDeptHeader,
                    overwrite
                );
                created += adsResult.created() ? 1 : 0;
                updated += adsResult.updated() ? 1 : 0;
                if (adsResult.created()) createdModels.add(adsResult.modelName());
                if (adsResult.updated()) updatedModels.add(adsResult.modelName());
                if (adsResult.skippedReason() != null) skipped.add(adsResult.skippedReason());
            }
        }

        return new SqlModelOdsGenerateResult(
            mappings.size(),
            created,
            updated,
            createdModels,
            updatedModels,
            skipped
        );
    }

    public SqlModelDto update(UUID id, SqlModelRequest request, String activeDeptHeader) {
        ensureWorkspaceWritable();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        String oldPath = model.getModelPath();
        apply(model, request, activeDeptHeader, false);
        ModelingSqlModel saved = repo.save(model);
        writeModelFile(saved);
        deleteFileIfChanged(oldPath, saved.getModelPath());
        syncDraftColumns(saved);
        return toDto(saved);
    }

    private void syncDraftColumns(ModelingSqlModel model) {
        if (model == null) {
            return;
        }
        try {
            DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
            String projectDir = view != null && view.config() != null ? view.config().projectDir() : null;
            List<ColumnSpec> specs = resolveCsvSpecs(model, projectDir);
            if (specs.isEmpty()) {
                return;
            }
            String tableName = resolveModelTable(model);
            String schemaName = resolveModelSchema(model, view);
            if (!StringUtils.hasText(tableName) || !StringUtils.hasText(schemaName)) {
                return;
            }
            CatalogDataset dataset = datasetRepository
                .findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schemaName, tableName)
                .orElse(null);
            if (dataset == null) {
                dataset = new CatalogDataset();
            }
            dataset.setName(defaultText(dataset.getName(), tableName));
            dataset.setHiveDatabase(defaultText(dataset.getHiveDatabase(), schemaName));
            dataset.setHiveTable(defaultText(dataset.getHiveTable(), tableName));
            if (!StringUtils.hasText(dataset.getWarehouseLayer()) && StringUtils.hasText(model.getLayer())) {
                dataset.setWarehouseLayer(normalizeLayer(model.getLayer()));
            }
            if (!StringUtils.hasText(dataset.getType())) {
                dataset.setType(resolveDatasetType(view));
            }
            if (!StringUtils.hasText(dataset.getDescription()) && StringUtils.hasText(model.getDescription())) {
                dataset.setDescription(model.getDescription());
            }
            if (!StringUtils.hasText(dataset.getOwnerDept()) && StringUtils.hasText(model.getOwnerDept())) {
                dataset.setOwnerDept(model.getOwnerDept());
            }
            CatalogDataset savedDataset = datasetRepository.save(dataset);

            CatalogTableSchema table = tableRepository
                .findFirstByDatasetAndNameIgnoreCase(savedDataset, tableName)
                .orElse(null);
            if (table == null) {
                table = new CatalogTableSchema();
                table.setDataset(savedDataset);
            }
            table.setName(tableName);
            CatalogTableSchema savedTable = tableRepository.save(table);

            int updated = columnSyncService.upsertColumns(savedTable, specs, CatalogColumnSyncService.STATUS_DRAFT);
            auditService.auditAction(
                "MODELING_COLUMN_SYNC",
                AuditStage.SUCCESS,
                savedDataset.getId() != null ? savedDataset.getId().toString() : "modeling",
                Map.of(
                    "summary",
                    "模型字段草稿同步",
                    "modelId",
                    model.getId() != null ? model.getId().toString() : null,
                    "schema",
                    schemaName,
                    "table",
                    tableName,
                    "columnCount",
                    updated
                )
            );
        } catch (Exception ex) {
            LOG.warn("[dbt-model] failed to sync draft columns: {}", ex.getMessage());
            auditService.auditAction(
                "MODELING_COLUMN_SYNC",
                AuditStage.FAIL,
                model.getId() != null ? model.getId().toString() : "modeling",
                Map.of("summary", "模型字段草稿同步失败", "error", ex.getMessage())
            );
        }
    }

    private List<ColumnSpec> resolveCsvSpecs(ModelingSqlModel model, String projectDir) {
        if (model == null || !StringUtils.hasText(model.getModelPath()) || !StringUtils.hasText(projectDir)) {
            return List.of();
        }
        Path projectPath = Path.of(projectDir).normalize();
        Path sqlPath = projectPath.resolve(model.getModelPath()).normalize();
        if (!sqlPath.startsWith(projectPath)) {
            return List.of();
        }
        String baseName = sqlPath.getFileName() != null ? sqlPath.getFileName().toString() : null;
        if (!StringUtils.hasText(baseName)) {
            return List.of();
        }
        String csvName = baseName.endsWith(".sql") ? baseName.substring(0, baseName.length() - 4) + ".csv" : baseName + ".csv";
        Path csvPath = sqlPath.resolveSibling(csvName);
        return columnSyncService.parseCsv(csvPath);
    }

    private String resolveModelSchema(ModelingSqlModel model, DbtConfigService.DbtConfigView view) {
        if (model != null && StringUtils.hasText(model.getSchemaName())) {
            return model.getSchemaName().trim();
        }
        if (view != null && view.config() != null && StringUtils.hasText(view.config().schema())) {
            return view.config().schema().trim();
        }
        return "public";
    }

    private String resolveModelTable(ModelingSqlModel model) {
        if (model == null) return null;
        if (StringUtils.hasText(model.getAlias())) {
            return model.getAlias().trim();
        }
        return StringUtils.hasText(model.getName()) ? model.getName().trim() : null;
    }

    private String resolveDatasetType(DbtConfigService.DbtConfigView view) {
        if (view != null && view.target() != null && StringUtils.hasText(view.target().type())) {
            return view.target().type().toUpperCase(Locale.ROOT);
        }
        return null;
    }

    public void delete(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权删除该模型");
        }
        repo.delete(model);
        deleteFileIfChanged(model.getModelPath(), null);
    }

    private void apply(ModelingSqlModel model, SqlModelRequest request, String activeDeptHeader, boolean isCreate) {
        if (request == null) {
            throw new IllegalArgumentException("模型配置不能为空");
        }
        String name = trimToNull(request.name());
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        if (!MODEL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("模型名称仅允许字母/数字/下划线，且必须以字母开头");
        }
        String sql = request.sql();
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("SQL 内容不能为空");
        }
        UUID sourceId = request.sourceDataSourceId();
        // sourceDataSourceId 可选，支持 Excel 等无数据库连接的场景

        ModelingPlan plan = resolvePlan(request.planId(), activeDeptHeader);
        InfraDataSource source = sourceId != null ? resolveSource(sourceId, activeDeptHeader) : null;

        String activeDept = security.resolveActiveDept(activeDeptHeader);
        if (isCreate) {
            String ownerDept = trimToNull(request.ownerDept());
            if (ownerDept == null) {
                ownerDept = trimToNull(activeDept);
            }
            if (ownerDept == null && plan != null) {
                ownerDept = trimToNull(plan.getOwnerDept());
            }
            model.setOwnerDept(ownerDept);
        } else if (StringUtils.hasText(request.ownerDept())) {
            model.setOwnerDept(trimToNull(request.ownerDept()));
        }

        String sourceKey = source != null ? resolveSourceKey(source) : null;
        String sourceTag = StringUtils.hasText(sourceKey) ? sanitizeTag(sourceKey, slugify(sourceKey)) : null;
        String mergedTags = mergeTags(request.tags(), sourceTag);
        String dagSelector = StringUtils.hasText(sourceTag) ? "tab:" + sourceTag : null;
        String layer = trimToNull(request.layer());
        if (layer == null) {
            layer = inferLayer(name);
        }
        layer = normalizeLayer(layer);
        if (!StringUtils.hasText(layer)) {
            throw new IllegalArgumentException("请选择模型分层");
        }

        model.setPlanId(plan != null ? plan.getId() : null);
        model.setName(name);
        model.setAlias(trimToNull(request.alias()));
        model.setLayer(layer);
        model.setSourceDataSourceId(sourceId);
        model.setSchemaName(trimToNull(request.schemaName()));
        model.setMaterialized(defaultText(trimToNull(request.materialized()), "table"));
        model.setTags(mergedTags);
        model.setDagSelector(dagSelector);
        model.setDescription(trimToNull(request.description()));
        model.setSqlText(sql);
        model.setEnabled(request.enabled() == null ? Boolean.TRUE : request.enabled());
        model.setStatus(defaultText(trimToNull(request.status()), "DRAFT"));
        model.setModelPath(resolveModelPath(model, plan, name));
    }

    private ModelingPlan resolvePlan(UUID planId, String activeDeptHeader) {
        if (planId == null) {
            return null;
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingPlan plan = planRepo.findById(planId).orElseThrow(() -> new EntityNotFoundException("项目空间不存在"));
        if (!isOwnerDeptVisible(plan.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该项目空间");
        }
        return plan;
    }

    private InfraDataSource resolveSource(UUID sourceId, String activeDeptHeader) {
        InfraDataSource source = dataSourceRepository.findById(sourceId).orElseThrow(() -> new EntityNotFoundException("来源数据源不存在"));
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        if (!isOwnerDeptVisible(source.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该数据源");
        }
        return source;
    }

    private UUID resolveUsableSourceId(UUID sourceId, String activeDeptHeader, String origin) {
        if (sourceId == null) {
            return null;
        }
        try {
            resolveSource(sourceId, activeDeptHeader);
            return sourceId;
        } catch (RuntimeException ex) {
            LOG.warn("[generate-from-ods] {} sourceDataSourceId {} not usable: {}", origin, sourceId, ex.getMessage());
            return null;
        }
    }

    private UUID resolveOdsDatasetSourceId(String schema, String table, String activeDeptHeader) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return null;
        }
        List<CatalogDataset> datasets = datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema.trim(), table.trim());
        if (datasets == null || datasets.isEmpty()) {
            return null;
        }
        for (CatalogDataset dataset : datasets) {
            if (dataset == null || dataset.getSourceId() == null) {
                continue;
            }
            UUID usable = resolveUsableSourceId(dataset.getSourceId(), activeDeptHeader, "dataset");
            if (usable != null) {
                return usable;
            }
        }
        return null;
    }

    private UUID resolveFallbackSourceId(String activeDeptHeader) {
        List<InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (candidates == null || candidates.isEmpty()) {
            candidates = dataSourceRepository.findAll();
        }
        UUID bestId = null;
        int bestScore = Integer.MIN_VALUE;
        for (InfraDataSource source : candidates) {
            if (source == null || source.getId() == null) {
                continue;
            }
            UUID usable = resolveUsableSourceId(source.getId(), activeDeptHeader, "fallback");
            if (usable == null) {
                continue;
            }
            int score = scoreSource(source);
            if (score > bestScore) {
                bestScore = score;
                bestId = usable;
            }
        }
        return bestId;
    }

    private int scoreSource(InfraDataSource source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = 0;
        String name = trimToEmpty(source.getName()).toLowerCase(Locale.ROOT);
        String type = trimToEmpty(source.getType()).toLowerCase(Locale.ROOT);
        String jdbcUrl = trimToEmpty(source.getJdbcUrl()).toLowerCase(Locale.ROOT);
        String status = trimToEmpty(source.getStatus()).toLowerCase(Locale.ROOT);
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

    private SqlModelDto toDto(ModelingSqlModel model) {
        if (model == null) return null;
        ModelingPlan plan = model.getPlanId() == null ? null : planRepo.findById(model.getPlanId()).orElse(null);
        InfraDataSource source = model.getSourceDataSourceId() == null
            ? null
            : dataSourceRepository.findById(model.getSourceDataSourceId()).orElse(null);
        String sourceKey = source != null ? resolveSourceKey(source) : null;
        String sourceTag = sanitizeTag(sourceKey, slugify(sourceKey));
        return new SqlModelDto(
            model.getId(),
            model.getPlanId(),
            plan != null ? plan.getName() : null,
            model.getName(),
            model.getAlias(),
            model.getLayer(),
            model.getSourceDataSourceId(),
            source != null ? source.getName() : null,
            sourceKey,
            "tab:" + sourceTag,
            model.getTags(),
            model.getMaterialized(),
            model.getSchemaName(),
            model.getDescription(),
            model.getSqlText(),
            model.getEnabled(),
            model.getModelPath(),
            model.getOwnerDept(),
            model.getStatus(),
            model.getCreatedDate(),
            model.getLastModifiedDate()
        );
    }

    private String resolveModelPath(ModelingSqlModel model, ModelingPlan plan, String name) {
        String planSlug = plan == null ? null : slugify(plan.getName());
        if (!StringUtils.hasText(planSlug)) {
            planSlug = "default";
        }
        String fileName = slugify(name);
        if (!StringUtils.hasText(fileName)) {
            fileName = "model";
        }
        String layerDir = resolveLayerDir(model != null ? model.getLayer() : null);
        return "models/" + layerDir + "/" + planSlug + "/" + fileName + ".sql";
    }

    private void ensureWorkspaceWritable() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.workspaceStatus() == null || !view.workspaceStatus().ok()) {
            String message = view != null && view.workspaceStatus() != null
                ? view.workspaceStatus().message()
                : "dbt 工作区未配置";
            throw new IllegalArgumentException(message);
        }
    }

    private Map<String, Object> toColumnPayload(CatalogColumnSchema column) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (column == null) {
            return item;
        }
        item.put("name", column.getName());
        item.put("dataType", column.getDataType());
        item.put("comment", column.getComment());
        item.put("status", column.getStatus());
        return item;
    }

    private void writeModelFile(ModelingSqlModel model) {
        if (model == null) return;
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            LOG.warn("[dbt-model] projectDir 未配置，跳过写入 {}", model.getName());
            return;
        }
        Path projectDir = Path.of(view.config().projectDir()).normalize();
        Path targetPath = projectDir.resolve(model.getModelPath()).normalize();
        if (!targetPath.startsWith(projectDir)) {
            LOG.warn("[dbt-model] invalid target path {} for model {}", targetPath, model.getName());
            return;
        }
        try {
            Files.createDirectories(targetPath.getParent());
            String sourceTag = resolveSourceTag(model.getSourceDataSourceId());
            String content = buildSqlContent(model, sourceTag);
            Files.writeString(targetPath, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            LOG.warn("[dbt-model] failed to write {}: {}", targetPath, ex.getMessage());
        }
    }

    private void writeCsvSidecar(String modelPath, String csvText) {
        if (!StringUtils.hasText(modelPath) || !StringUtils.hasText(csvText)) {
            return;
        }
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            LOG.warn("[dbt-model] projectDir 未配置，跳过 CSV 写入 {}", modelPath);
            return;
        }
        Path projectDir = Path.of(view.config().projectDir()).normalize();
        Path sqlPath = projectDir.resolve(modelPath).normalize();
        if (!sqlPath.startsWith(projectDir)) {
            LOG.warn("[dbt-model] invalid target path {} for csv", sqlPath);
            return;
        }
        String baseName = sqlPath.getFileName().toString();
        String csvName = baseName.endsWith(".sql") ? baseName.substring(0, baseName.length() - 4) + ".csv" : baseName + ".csv";
        Path csvPath = sqlPath.resolveSibling(csvName);
        try {
            Files.createDirectories(csvPath.getParent());
            Files.writeString(csvPath, csvText.trim() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            LOG.warn("[dbt-model] failed to write csv {}: {}", csvPath, ex.getMessage());
        }
    }

    private void deleteFileIfChanged(String oldPath, String newPath) {
        if (!StringUtils.hasText(oldPath)) {
            return;
        }
        if (StringUtils.hasText(newPath) && oldPath.equals(newPath)) {
            return;
        }
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            return;
        }
        Path projectDir = Path.of(view.config().projectDir()).normalize();
        Path targetPath = projectDir.resolve(oldPath).normalize();
        if (!targetPath.startsWith(projectDir)) {
            return;
        }
        try {
            Files.deleteIfExists(targetPath);
        } catch (IOException ex) {
            LOG.warn("[dbt-model] failed to delete {}: {}", targetPath, ex.getMessage());
        }
    }

    private String buildSqlContent(ModelingSqlModel model, String sourceTag) {
        String body = trimToEmpty(model.getSqlText());
        // 如果用户的 SQL 已经包含 {{ config，不再自动添加
        if (body.contains("{{ config") || body.contains("{{config")) {
            return body + "\n";
        }
        String configLine = buildConfigLine(model, sourceTag);
        if (!StringUtils.hasText(configLine)) {
            return body + "\n";
        }
        return configLine + "\n\n" + body + "\n";
    }

    private String buildConfigLine(ModelingSqlModel model, String sourceTag) {
        List<String> configs = new ArrayList<>();
        if (StringUtils.hasText(model.getMaterialized())) {
            configs.add("materialized='" + model.getMaterialized() + "'");
        }
        if (StringUtils.hasText(model.getAlias())) {
            configs.add("alias='" + model.getAlias() + "'");
        }
        // 不自动添加 schema 参数，让 dbt 使用 profiles.yml 里配置的默认 schema
        // 避免生成 public_public 这样的拼接 schema 名
        String layerTag = sanitizeTag(normalizeLayer(model.getLayer()), null);
        List<String> tags = splitTags(model.getTags(), sourceTag, layerTag);
        if (!tags.isEmpty()) {
            String rendered = tags.stream().map(tag -> "'" + tag + "'").reduce((a, b) -> a + ", " + b).orElse("");
            configs.add("tags=[" + rendered + "]");
        }
        if (configs.isEmpty()) {
            return "";
        }
        return "{{ config(" + String.join(", ", configs) + ") }}";
    }

    private List<String> splitTags(String rawTags, String sourceTag, String layerTag) {
        Set<String> tags = new LinkedHashSet<>();
        if (StringUtils.hasText(rawTags)) {
            String[] parts = rawTags.split("[,;\\s]+");
            for (String part : parts) {
                String tag = sanitizeTag(part, null);
                if (StringUtils.hasText(tag)) {
                    tags.add(tag);
                }
            }
        }
        if (StringUtils.hasText(sourceTag)) {
            tags.add(sanitizeTag(sourceTag, null));
        }
        if (StringUtils.hasText(layerTag)) {
            tags.add(sanitizeTag(layerTag, null));
        }
        return new ArrayList<>(tags);
    }

    private String resolveSourceTag(UUID sourceId) {
        if (sourceId == null) return null;
        InfraDataSource source = dataSourceRepository.findById(sourceId).orElse(null);
        if (source == null) return null;
        return sanitizeTag(resolveSourceKey(source), slugify(source.getName()));
    }

    private boolean matchKeyword(ModelingSqlModel model, String keyword) {
        if (model == null || !StringUtils.hasText(keyword)) return false;
        String kw = keyword.toLowerCase(Locale.ROOT);
        return contains(model.getName(), kw) || contains(model.getDescription(), kw) || contains(model.getTags(), kw);
    }

    private boolean contains(String value, String keyword) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(keyword)) return false;
        return value.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = trimToNull(ownerDept);
        if (trimmedOwner == null) return true;
        if (instituteScope) return true;
        if (organizationVisibilityService.isRoot(trimmedOwner)) return true;
        if (!StringUtils.hasText(activeDept)) return false;
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    private String resolveSourceKey(InfraDataSource source) {
        if (source == null) return null;
        Map<String, Object> props = parseProps(source.getProps());
        String key = firstText(props, "sourceSystem", "sourceName", "system", "app", "appCode", "name");
        if (StringUtils.hasText(key)) {
            return key;
        }
        return source.getName();
    }

    private Map<String, Object> parseProps(String raw) {
        if (!StringUtils.hasText(raw)) return Map.of();
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return objectMapper.readValue(trimmed, new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        return Map.of();
    }

    private String firstText(Map<String, Object> props, String... keys) {
        if (props == null) return null;
        for (String key : keys) {
            Object value = props.get(key);
            if (value != null) {
                String text = value.toString().trim();
                if (!text.isEmpty()) return text;
            }
        }
        return null;
    }

    private String slugify(String value) {
        if (!StringUtils.hasText(value)) return "model";
        String slug = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(slug) ? slug : "model";
    }

    private String sanitizeTag(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String tag = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
        tag = tag.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(tag) ? tag : fallback;
    }

    private String mergeTags(String rawTags, String sourceTag) {
        List<String> tags = splitTags(rawTags, sourceTag, null);
        if (tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    private String inferLayer(String name) {
        if (!StringUtils.hasText(name)) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ods_")) return "ODS";
        if (normalized.startsWith("dwd_")) return "DWD";
        if (normalized.startsWith("dws_")) return "DWS";
        if (normalized.startsWith("ads_")) return "ADS";
        return null;
    }

    private String normalizeLayer(String layer) {
        if (!StringUtils.hasText(layer)) {
            return null;
        }
        String normalized = layer.trim().toUpperCase(Locale.ROOT);
        if ("ODS".equals(normalized) || "DWD".equals(normalized) || "DWS".equals(normalized) || "ADS".equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private String resolveLayerDir(String layer) {
        String normalized = normalizeLayer(layer);
        if ("ODS".equals(normalized)) {
            return "ods";
        }
        if ("DWD".equals(normalized)) {
            return "dwd";
        }
        if ("DWS".equals(normalized)) {
            return "dws";
        }
        if ("ADS".equals(normalized)) {
            return "ads";
        }
        return "dwh";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String resolveEntityToken(InfraOdsTableMapping mapping, String odsTable) {
        String candidate = trimToNull(mapping != null ? mapping.getEntityCode() : null);
        if (!StringUtils.hasText(candidate)) {
            candidate = trimToNull(odsTable);
        }
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        String normalized = slugify(candidate);
        if (normalized.startsWith("ods_")) {
            normalized = normalized.substring(4);
        }
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String buildLayerDescription(String layer, String odsSchema, String odsTable, String baseDescription) {
        String layerText = defaultText(layer, "DWD");
        String sourceText = odsSchema + "." + odsTable;
        if (StringUtils.hasText(baseDescription)) {
            return layerText + " 自动生成模型，来源 " + sourceText + "；" + baseDescription;
        }
        return layerText + " 自动生成模型，来源 " + sourceText;
    }

    private GenerateLayerResult upsertGeneratedModel(
        UUID planId,
        UUID sourceDataSourceId,
        String modelName,
        String layer,
        String schemaName,
        String materialized,
        String tags,
        String description,
        Boolean enabled,
        String status,
        String ownerDept,
        String sql,
        String activeDeptHeader,
        boolean overwrite
    ) {
        ModelingSqlModel existing = repo.findFirstByPlanIdAndNameIgnoreCase(planId, modelName).orElse(null);
        if (existing != null && !overwrite) {
            return new GenerateLayerResult(modelName, false, false, modelName + " (已存在，未覆盖)");
        }
        SqlModelRequest payload = new SqlModelRequest(
            planId,
            modelName,
            null,
            layer,
            sourceDataSourceId,
            trimToNull(schemaName),
            defaultText(trimToNull(materialized), "table"),
            tags,
            description,
            sql,
            enabled,
            trimToNull(status),
            trimToNull(ownerDept)
        );
        if (existing == null) {
            create(payload, activeDeptHeader);
            return new GenerateLayerResult(modelName, true, false, null);
        }
        update(existing.getId(), payload, activeDeptHeader);
        return new GenerateLayerResult(modelName, false, true, null);
    }

    public record SqlModelOdsGenerateRequest(
        UUID planId,
        UUID sourceDataSourceId,
        List<UUID> mappingIds,
        String schemaName,
        String materialized,
        String tags,
        String ownerDept,
        Boolean enabled,
        String status,
        Boolean createDwd,
        Boolean createDws,
        Boolean createAds,
        Boolean overwriteExisting
    ) {}

    public record SqlModelOdsGenerateResult(
        int mappingsTotal,
        int modelsCreated,
        int modelsUpdated,
        List<String> createdModels,
        List<String> updatedModels,
        List<String> skipped
    ) {}

    private record GenerateLayerResult(
        String modelName,
        boolean created,
        boolean updated,
        String skippedReason
    ) {}

    public record SqlModelRequest(
        UUID planId,
        String name,
        String alias,
        String layer,
        UUID sourceDataSourceId,
        String schemaName,
        String materialized,
        String tags,
        String description,
        String sql,
        Boolean enabled,
        String status,
        String ownerDept
    ) {}

    public record SqlModelDto(
        UUID id,
        UUID planId,
        String planName,
        String name,
        String alias,
        String layer,
        UUID sourceDataSourceId,
        String sourceDataSourceName,
        String sourceSystem,
        String dagSelector,
        String tags,
        String materialized,
        String schemaName,
        String description,
        String sql,
        Boolean enabled,
        String modelPath,
        String ownerDept,
        String status,
        Instant createdDate,
        Instant lastModifiedDate
    ) {}
}
