package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset;
import com.yuzhi.dts.platform.domain.infra.InfraOdsTableMapping;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.infra.InfraOdsTableMappingRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.modeling.ModelFileService.ColumnProfile;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.BatchImportDetail;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.BatchImportResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelDto;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernanceExecuteItem;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernanceExecuteRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernanceExecuteResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernancePreviewItem;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernancePreviewRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelGovernancePreviewResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateRequest;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelOdsGenerateResult;
import com.yuzhi.dts.platform.service.modeling.ModelingSqlModelService.SqlModelRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Handles ODS-to-DWD/DWS/ADS generation, governance preview/execute,
 * batch import from ZIP archives, and workspace sync operations.
 */
@Service
@Transactional
public class ModelGenerationService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelGenerationService.class);
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    private static final Pattern DBT_TAGS_CONFIG_PATTERN = Pattern.compile("tags\\s*=\\s*\\[(.*?)]", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DBT_TAG_LITERAL_PATTERN = Pattern.compile("['\\\"]([^'\\\"]+)['\\\"]");

    private final ModelingSqlModelService coreService;
    private final ModelFileService fileService;
    private final ModelingSqlModelRepository repo;
    private final ModelingPlanRepository planRepo;
    private final InfraOdsTableMappingRepository odsTableMappingRepository;
    private final InfraDataSourceRepository dataSourceRepository;
    private final AdminInfraClient adminInfraClient;
    private final DataStandardSecurity security;
    private final DbtConfigService dbtConfigService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final QueryDatasetAssetRepository queryDatasetAssetRepository;
    private final BiReportLinkRepository biReportLinkRepository;

    public ModelGenerationService(
        ModelingSqlModelService coreService,
        ModelFileService fileService,
        ModelingSqlModelRepository repo,
        ModelingPlanRepository planRepo,
        InfraOdsTableMappingRepository odsTableMappingRepository,
        InfraDataSourceRepository dataSourceRepository,
        AdminInfraClient adminInfraClient,
        DataStandardSecurity security,
        DbtConfigService dbtConfigService,
        CatalogDatasetRepository datasetRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        QueryDatasetAssetRepository queryDatasetAssetRepository,
        BiReportLinkRepository biReportLinkRepository
    ) {
        this.coreService = coreService;
        this.fileService = fileService;
        this.repo = repo;
        this.planRepo = planRepo;
        this.odsTableMappingRepository = odsTableMappingRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.adminInfraClient = adminInfraClient;
        this.security = security;
        this.dbtConfigService = dbtConfigService;
        this.datasetRepository = datasetRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.queryDatasetAssetRepository = queryDatasetAssetRepository;
        this.biReportLinkRepository = biReportLinkRepository;
    }

    // ── ODS Generation ─────────────────────────────────────────────────

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
        coreService.ensureWorkspaceWritable();
        coreService.resolvePlan(request.planId(), activeDeptHeader);
        UUID preferredSourceId = resolveUsableSourceId(request.sourceDataSourceId(), activeDeptHeader, "request");

        UUID fallbackSourceId = resolveFallbackSourceId(activeDeptHeader);

        Set<UUID> selectedIds = new LinkedHashSet<>(request.mappingIds());
        List<InfraOdsTableMapping> mappings = odsTableMappingRepository.findAllById(selectedIds)
            .stream()
            .filter(mapping -> mapping != null && Boolean.TRUE.equals(mapping.getEnabled()) && coreService.isOwnerDeptVisible(mapping.getOwnerDept(), activeDept, instituteScope))
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
        int qualityTemplatesGenerated = 0;
        List<String> qualitySkipped = new ArrayList<>();
        boolean overwrite = request.overwriteExisting() == null || request.overwriteExisting();

        for (InfraOdsTableMapping mapping : mappings) {
            String odsSchema = defaultText(trimToNull(mapping.getOdsSchema()), "public");
            String odsTable = trimToNull(mapping.getOdsTable());
            if (!isValidIdentifier(odsSchema) || (odsTable != null && !isValidIdentifier(odsTable))) {
                skipped.add(odsSchema + "." + odsTable + " (标识符包含非法字符)");
                continue;
            }
            if (!StringUtils.hasText(odsTable)) {
                skipped.add("mapping:" + mapping.getId() + " (ODS 表名为空)");
                continue;
            }
            if (!hasActiveOdsDataset(odsSchema, odsTable)) {
                skipped.add(odsSchema + "." + odsTable + " (ODS 映射已过期，请先重新采集)");
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

            String extraTags = ModelingSqlModelService.mergeTags(trimToNull(request.tags()), trimToNull(mapping.getSystemCode()));
            String descriptionBase = trimToNull(mapping.getDescription());
            List<ColumnProfile> sourceColumns = resolveSourceColumnsForQuality(odsSchema, odsTable);
            try {
                GenerateLayerResult dwdResult = null;
                if (createDwd) {
                    dwdResult = upsertGeneratedModel(
                        request.planId(), modelSourceId, "dwd_" + entityToken, "DWD",
                        request.schemaName(), request.materialized(), extraTags,
                        buildLayerDescription("DWD", odsSchema, odsTable, descriptionBase),
                        request.enabled(), request.status(), request.ownerDept(),
                        """
                        select
                          *
                        from {{ source('%s', '%s') }}
                        """.formatted(odsSchema, odsTable),
                        activeDeptHeader, overwrite, sourceColumns
                    );
                    created += dwdResult.created() ? 1 : 0;
                    updated += dwdResult.updated() ? 1 : 0;
                    if (dwdResult.created()) createdModels.add(dwdResult.modelName());
                    if (dwdResult.updated()) updatedModels.add(dwdResult.modelName());
                    if (dwdResult.skippedReason() != null) skipped.add(dwdResult.skippedReason());
                    if (dwdResult.qualityTemplateGenerated()) qualityTemplatesGenerated++;
                    if (dwdResult.qualityWarning() != null) qualitySkipped.add(dwdResult.qualityWarning());
                }

                GenerateLayerResult dwsResult = null;
                if (createDws) {
                    String dwdRef = dwdResult != null ? dwdResult.modelName() : "dwd_" + entityToken;
                    dwsResult = upsertGeneratedModel(
                        request.planId(), modelSourceId, "dws_" + entityToken, "DWS",
                        request.schemaName(), request.materialized(), extraTags,
                        buildLayerDescription("DWS", odsSchema, odsTable, descriptionBase),
                        request.enabled(), request.status(), request.ownerDept(),
                        """
                        select
                          *
                        from {{ ref('%s') }}
                        """.formatted(dwdRef),
                        activeDeptHeader, overwrite, sourceColumns
                    );
                    created += dwsResult.created() ? 1 : 0;
                    updated += dwsResult.updated() ? 1 : 0;
                    if (dwsResult.created()) createdModels.add(dwsResult.modelName());
                    if (dwsResult.updated()) updatedModels.add(dwsResult.modelName());
                    if (dwsResult.skippedReason() != null) skipped.add(dwsResult.skippedReason());
                    if (dwsResult.qualityTemplateGenerated()) qualityTemplatesGenerated++;
                    if (dwsResult.qualityWarning() != null) qualitySkipped.add(dwsResult.qualityWarning());
                }

                if (createAds) {
                    String dwsRef = dwsResult != null ? dwsResult.modelName() : "dws_" + entityToken;
                    GenerateLayerResult adsResult = upsertGeneratedModel(
                        request.planId(), modelSourceId, "ads_" + entityToken, "ADS",
                        request.schemaName(), request.materialized(), extraTags,
                        buildLayerDescription("ADS", odsSchema, odsTable, descriptionBase),
                        request.enabled(), request.status(), request.ownerDept(),
                        """
                        select
                          *
                        from {{ ref('%s') }}
                        """.formatted(dwsRef),
                        activeDeptHeader, overwrite, sourceColumns
                    );
                    created += adsResult.created() ? 1 : 0;
                    updated += adsResult.updated() ? 1 : 0;
                    if (adsResult.created()) createdModels.add(adsResult.modelName());
                    if (adsResult.updated()) updatedModels.add(adsResult.modelName());
                    if (adsResult.skippedReason() != null) skipped.add(adsResult.skippedReason());
                    if (adsResult.qualityTemplateGenerated()) qualityTemplatesGenerated++;
                    if (adsResult.qualityWarning() != null) qualitySkipped.add(adsResult.qualityWarning());
                }
            } catch (RuntimeException ex) {
                String reason = trimToNull(ex.getMessage());
                if (reason == null) {
                    reason = ex.getClass().getSimpleName();
                }
                skipped.add(odsSchema + "." + odsTable + " (生成失败: " + reason + ")");
                LOG.warn("[generate-from-ods] failed for {}.{}: {}", odsSchema, odsTable, reason);
            }
        }

        return new SqlModelOdsGenerateResult(
            mappings.size(), created, updated, createdModels, updatedModels, skipped, qualityTemplatesGenerated, qualitySkipped
        );
    }

    // ── Governance ─────────────────────────────────────────────────────

    public SqlModelGovernancePreviewResult previewGovernance(SqlModelGovernancePreviewRequest request, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        List<ModelingSqlModel> scopedModels = (request != null && request.planId() != null ? repo.findByPlanId(request.planId()) : repo.findAll())
            .stream()
            .filter(model -> coreService.isOwnerDeptVisible(model != null ? model.getOwnerDept() : null, activeDept, instituteScope))
            .filter(model -> governanceScopeMatches(model, request))
            .toList();

        Map<UUID, GovernancePreviewAccumulator> accumulators = new LinkedHashMap<>();
        Set<String> ruleKeys = normalizeGovernanceRules(request != null ? request.ruleKeys() : null);

        if (ruleKeys.contains("duplicate-model")) {
            Map<String, List<ModelingSqlModel>> groups = new LinkedHashMap<>();
            for (ModelingSqlModel model : scopedModels) {
                groups.computeIfAbsent(coreService.dedupeModelKey(model), unused -> new ArrayList<>()).add(model);
            }
            for (List<ModelingSqlModel> group : groups.values()) {
                if (group.size() < 2) {
                    continue;
                }
                ModelingSqlModel keeper = null;
                for (ModelingSqlModel candidate : group) {
                    if (coreService.shouldReplaceDuplicateModel(candidate, keeper)) {
                        keeper = candidate;
                    }
                }
                for (ModelingSqlModel candidate : group) {
                    if (keeper != null && keeper.getId() != null && keeper.getId().equals(candidate.getId())) {
                        continue;
                    }
                    governanceAccumulator(accumulators, candidate).addRuleHit("duplicate-model");
                }
            }
        }

        if (ruleKeys.contains("sql-keyword")) {
            List<String> sqlKeywords = normalizeSqlKeywords(request != null ? request.sqlKeywords() : null);
            if (!sqlKeywords.isEmpty()) {
                for (ModelingSqlModel model : scopedModels) {
                    if (sqlKeywords.stream().anyMatch(keyword -> governanceSqlContains(model, keyword))) {
                        governanceAccumulator(accumulators, model).addRuleHit("sql-keyword");
                    }
                }
            }
        }

        if (ruleKeys.contains("name-pattern") && StringUtils.hasText(request != null ? request.namePattern() : null)) {
            String namePattern = trimToNull(request.namePattern());
            for (ModelingSqlModel model : scopedModels) {
                if (containsIgnoreCase(model != null ? model.getName() : null, namePattern)) {
                    governanceAccumulator(accumulators, model).addRuleHit("name-pattern");
                }
            }
        }

        if (ruleKeys.contains("path-pattern") && StringUtils.hasText(request != null ? request.modelPathPattern() : null)) {
            String pathPattern = trimToNull(request.modelPathPattern());
            for (ModelingSqlModel model : scopedModels) {
                if (containsIgnoreCase(model != null ? model.getModelPath() : null, pathPattern)) {
                    governanceAccumulator(accumulators, model).addRuleHit("path-pattern");
                }
            }
        }

        if (ruleKeys.contains("tag-match") && StringUtils.hasText(request != null ? request.tag() : null)) {
            String tag = trimToNull(request.tag());
            for (ModelingSqlModel model : scopedModels) {
                if (containsIgnoreCase(model != null ? model.getTags() : null, tag)) {
                    governanceAccumulator(accumulators, model).addRuleHit("tag-match");
                }
            }
        }

        if (ruleKeys.contains("preset:project-management-legacy-program")) {
            for (ModelingSqlModel model : scopedModels) {
                String tags = normalizeLower(model != null ? model.getTags() : null);
                if (!tags.contains("project-management")) {
                    continue;
                }
                if (governanceSqlContains(model, "program_id") || governanceSqlContains(model, "program_name")) {
                    governanceAccumulator(accumulators, model).addRuleHit("preset:project-management-legacy-program");
                }
            }
        }

        List<SqlModelGovernancePreviewItem> items = accumulators
            .values()
            .stream()
            .map(accumulator -> toGovernancePreviewItem(accumulator.model(), accumulator.ruleHits()))
            .sorted(Comparator.comparing(item -> defaultText(item.name(), ""), String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new SqlModelGovernancePreviewResult(items.size(), items);
    }

    public SqlModelGovernanceExecuteResult executeGovernance(SqlModelGovernanceExecuteRequest request, String activeDeptHeader) {
        if (request == null || request.modelIds() == null || request.modelIds().isEmpty()) {
            throw new IllegalArgumentException("请选择待治理模型");
        }
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();

        List<ModelingSqlModel> selectedModels = request.modelIds()
            .stream()
            .distinct()
            .map(id -> repo.findById(id).orElse(null))
            .filter(model -> model != null)
            .filter(model -> coreService.isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope))
            .toList();

        if (selectedModels.isEmpty()) {
            return new SqlModelGovernanceExecuteResult(request.modelIds().size(), 0, request.modelIds().size(), List.of());
        }

        boolean deleteFiles = request.deleteFiles() == null || request.deleteFiles();
        Map<String, Set<UUID>> removingIdsByPath = new LinkedHashMap<>();
        if (deleteFiles) {
            for (ModelingSqlModel model : selectedModels) {
                if (!StringUtils.hasText(model.getModelPath()) || model.getId() == null) {
                    continue;
                }
                removingIdsByPath.computeIfAbsent(model.getModelPath(), unused -> new LinkedHashSet<>()).add(model.getId());
            }
        }

        List<SqlModelGovernanceExecuteItem> items = new ArrayList<>();
        for (ModelingSqlModel model : selectedModels) {
            try {
                repo.delete(model);
                items.add(new SqlModelGovernanceExecuteItem(model.getId(), model.getName(), "DELETED", "模型记录已删除"));
            } catch (RuntimeException ex) {
                items.add(
                    new SqlModelGovernanceExecuteItem(
                        model.getId(),
                        model.getName(),
                        "FAILED",
                        defaultText(trimToNull(ex.getMessage()), "删除模型失败")
                    )
                );
            }
        }

        if (deleteFiles) {
            for (Map.Entry<String, Set<UUID>> entry : removingIdsByPath.entrySet()) {
                if (!coreService.canDeleteModelPathAfterRemoving(entry.getKey(), entry.getValue())) {
                    continue;
                }
                coreService.scheduleFileDeletionAfterCommit(entry.getKey());
            }
        }

        int deleted = (int) items.stream().filter(item -> "DELETED".equals(item.result())).count();
        int skippedCount = request.modelIds().size() - deleted;
        return new SqlModelGovernanceExecuteResult(request.modelIds().size(), deleted, skippedCount, items);
    }

    // ── Workspace sync ─────────────────────────────────────────────────

    public void syncWorkspaceModels(String activeDeptHeader) {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.config() == null || !StringUtils.hasText(view.config().projectDir())) {
            return;
        }
        UUID sourceDataSourceId = resolveFallbackSourceId(activeDeptHeader);
        if (sourceDataSourceId == null) {
            return;
        }
        Path projectDir = Path.of(view.config().projectDir()).normalize();
        Path modelsDir = projectDir.resolve("models").normalize();
        if (!modelsDir.startsWith(projectDir) || !Files.isDirectory(modelsDir)) {
            return;
        }
        Map<String, ModelingSqlModel> existingByName = new LinkedHashMap<>();
        for (ModelingSqlModel existing : repo.findAll()) {
            if (existing == null || !StringUtils.hasText(existing.getName())) {
                continue;
            }
            existingByName.putIfAbsent(existing.getName().trim().toLowerCase(Locale.ROOT), existing);
        }
        try (var walk = Files.walk(modelsDir, FileVisitOption.FOLLOW_LINKS)) {
            walk
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                .sorted(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)))
                .forEach(path -> upsertDiscoveredWorkspaceModel(projectDir, path, view.config(), sourceDataSourceId, existingByName));
        } catch (IOException ex) {
            LOG.warn("[dbt-model] failed to discover workspace models: {}", ex.getMessage());
        }
    }

    // ── File import (single + batch) ───────────────────────────────────

    public SqlModelDto importFromFiles(SqlModelRequest request, String sqlText, String csvText, String activeDeptHeader) {
        if (!StringUtils.hasText(sqlText)) {
            throw new IllegalArgumentException("SQL 内容不能为空");
        }
        SqlModelRequest normalized = new SqlModelRequest(
            request.planId(), request.name(), request.alias(), request.layer(),
            request.sourceDataSourceId(), request.schemaName(), request.materialized(),
            request.tags(), request.description(), sqlText, request.enabled(),
            request.status(), request.ownerDept(), request.semanticContract()
        );
        SqlModelDto dto = coreService.create(normalized, activeDeptHeader);
        if (StringUtils.hasText(csvText)) {
            fileService.writeCsvSidecar(dto.modelPath(), csvText);
            ModelingSqlModel saved = repo.findById(dto.id()).orElse(null);
            if (saved != null) {
                coreService.syncDraftColumns(saved);
            }
        }
        return dto;
    }

    SqlModelDto upsertImportFromFiles(SqlModelRequest request, String sqlText, String csvText, String activeDeptHeader) {
        if (!StringUtils.hasText(sqlText)) {
            throw new IllegalArgumentException("SQL 内容不能为空");
        }
        SqlModelRequest normalized = new SqlModelRequest(
            request.planId(), request.name(), request.alias(), request.layer(),
            request.sourceDataSourceId(), request.schemaName(), request.materialized(),
            request.tags(), request.description(), sqlText, request.enabled(),
            request.status(), request.ownerDept(), request.semanticContract()
        );
        ModelingSqlModel existing = repo
            .findFirstByPlanIdAndNameIgnoreCase(normalized.planId(), defaultText(normalized.name(), ""))
            .orElse(null);
        SqlModelDto dto = existing == null ? coreService.create(normalized, activeDeptHeader) : coreService.update(existing.getId(), normalized, activeDeptHeader);
        if (StringUtils.hasText(csvText)) {
            fileService.writeCsvSidecar(dto.modelPath(), csvText);
            ModelingSqlModel saved = repo.findById(dto.id()).orElse(null);
            if (saved != null) {
                coreService.syncDraftColumns(saved);
            }
        }
        return dto;
    }

    public BatchImportResult batchImportFromArchive(
        UUID planId,
        UUID defaultSourceDataSourceId,
        boolean skipExisting,
        Path archivePath,
        String activeDept
    ) {
        coreService.ensureWorkspaceWritable();
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("batch-import-unzip-");
            fileService.unzip(archivePath, tempDir);

            Path tsvFile = null;
            try (var stream = Files.walk(tempDir)) {
                tsvFile = stream
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".tsv"))
                    .findFirst()
                    .orElse(null);
            }
            if (tsvFile == null) {
                throw new IllegalArgumentException("ZIP 压缩包中未找到 .tsv 清单文件");
            }

            Path tsvDir = tsvFile.getParent();
            List<String> lines = Files.readAllLines(tsvFile, StandardCharsets.UTF_8);
            List<BatchImportDetail> details = new ArrayList<>();
            int imported = 0;
            int skipped = 0;
            int failed = 0;

            for (String line : lines) {
                if (!StringUtils.hasText(line)) continue;
                String[] cols = line.split("\t", -1);
                if (cols.length == 0 || !StringUtils.hasText(cols[0])) continue;
                if ("name".equalsIgnoreCase(cols[0].trim())) continue;

                String name = cols[0].trim();
                String layer = cols.length > 1 ? cols[1].trim() : "";
                String sqlPath = cols.length > 2 ? cols[2].trim() : "";
                String sourceDataSourceIdStr = cols.length > 3 ? cols[3].trim() : "";
                String alias = cols.length > 4 ? cols[4].trim() : "";
                String schemaName = cols.length > 5 ? cols[5].trim() : "";
                String materialized = cols.length > 6 ? cols[6].trim() : "";
                String tags = cols.length > 7 ? cols[7].trim() : "";
                String status = cols.length > 8 ? cols[8].trim() : "";
                String enabledStr = cols.length > 9 ? cols[9].trim() : "";
                String ownerDept = cols.length > 10 ? cols[10].trim() : "";
                String description = cols.length > 11 ? cols[11].trim() : "";
                String csvPath = cols.length > 12 ? cols[12].trim() : "";

                try {
                    String validationError = validateBatchImportEntry(name, layer, sqlPath, materialized);
                    if (validationError != null) {
                        details.add(new BatchImportDetail(name, layer, "validation_failed", validationError));
                        failed++;
                        continue;
                    }

                    if (skipExisting && repo.findFirstByPlanIdAndNameIgnoreCase(planId, name).isPresent()) {
                        details.add(new BatchImportDetail(name, layer, "skipped", "同名模型已存在"));
                        skipped++;
                        continue;
                    }

                    Path sqlFile = fileService.resolveSidecarFile(tsvDir, tempDir, sqlPath, name, ".sql");
                    if (sqlFile == null || !Files.isRegularFile(sqlFile)) {
                        details.add(new BatchImportDetail(name, layer, "validation_failed", "找不到 SQL 文件: " + sqlPath));
                        failed++;
                        continue;
                    }
                    String sqlText = Files.readString(sqlFile, StandardCharsets.UTF_8);

                    String csvText = null;
                    if (StringUtils.hasText(csvPath)) {
                        Path csvFile = fileService.resolveSidecarFile(tsvDir, tempDir, csvPath, name, ".csv");
                        if (csvFile != null && Files.isRegularFile(csvFile)) {
                            csvText = Files.readString(csvFile, StandardCharsets.UTF_8);
                        }
                    }

                    UUID sourceId = defaultSourceDataSourceId;
                    if (StringUtils.hasText(sourceDataSourceIdStr)) {
                        try {
                            sourceId = UUID.fromString(sourceDataSourceIdStr);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    sourceId = resolveBatchImportSourceId(sourceId, activeDept);
                    if (sourceId == null) {
                        details.add(new BatchImportDetail(name, layer, "validation_failed", "来源数据源不存在，且未找到可用默认数据源"));
                        failed++;
                        continue;
                    }

                    Boolean enabled = null;
                    if (StringUtils.hasText(enabledStr)) {
                        enabled = "true".equalsIgnoreCase(enabledStr) || "1".equals(enabledStr);
                    }

                    SqlModelRequest batchRequest = new SqlModelRequest(
                        planId, name,
                        StringUtils.hasText(alias) ? alias : null,
                        layer, sourceId,
                        StringUtils.hasText(schemaName) ? schemaName : null,
                        StringUtils.hasText(materialized) ? materialized : null,
                        StringUtils.hasText(tags) ? tags : null,
                        StringUtils.hasText(description) ? description : null,
                        sqlText, enabled,
                        StringUtils.hasText(status) ? status : null,
                        StringUtils.hasText(ownerDept) ? ownerDept : null,
                        null
                    );
                    upsertImportFromFiles(batchRequest, sqlText, csvText, activeDept);
                    details.add(new BatchImportDetail(name, layer, "imported", null));
                    imported++;
                } catch (IllegalArgumentException ex) {
                    LOG.warn("[batch-import] validation failed for model '{}': {}", name, ex.getMessage());
                    details.add(new BatchImportDetail(name, layer, "validation_failed", ex.getMessage()));
                    failed++;
                } catch (Exception ex) {
                    LOG.warn("[batch-import] failed to import model '{}': {}", name, ex.getMessage(), ex);
                    details.add(new BatchImportDetail(name, layer, "write_failed", ex.getMessage()));
                    failed++;
                }
            }

            if (imported > 0) {
                fileService.copyWorkspaceCompanionFiles(tempDir);
            }

            int total = imported + skipped + failed;
            return new BatchImportResult(total, imported, skipped, failed, details);
        } catch (IOException ex) {
            throw new RuntimeException("批量导入失败: " + ex.getMessage(), ex);
        } finally {
            if (tempDir != null) {
                try {
                    fileService.deleteRecursively(tempDir);
                } catch (IOException ignored) {
                    LOG.warn("[batch-import] failed to clean up temp dir: {}", tempDir);
                }
            }
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private GenerateLayerResult upsertGeneratedModel(
        UUID planId, UUID sourceDataSourceId, String modelName, String layer,
        String schemaName, String materialized, String tags, String description,
        Boolean enabled, String status, String ownerDept, String sql,
        String activeDeptHeader, boolean overwrite, List<ColumnProfile> sourceColumns
    ) {
        if (sourceDataSourceId == null) {
            return new GenerateLayerResult(modelName, false, false, modelName + " (来源数据源不可用: 来源数据源不存在)", false, null);
        }
        ModelingSqlModel existing = repo.findFirstByPlanIdAndNameIgnoreCase(planId, modelName).orElse(null);
        if (existing != null && !overwrite) {
            return new GenerateLayerResult(modelName, false, false, modelName + " (已存在，未覆盖)", false, null);
        }
        SqlModelRequest payload = new SqlModelRequest(
            planId, modelName, null, layer, sourceDataSourceId,
            trimToNull(schemaName), defaultText(trimToNull(materialized), "table"),
            tags, description, sql, enabled, trimToNull(status), trimToNull(ownerDept), null
        );
        SqlModelDto dto;
        boolean created;
        boolean updated;
        if (existing == null) {
            dto = coreService.create(payload, activeDeptHeader);
            created = true;
            updated = false;
        } else {
            dto = coreService.update(existing.getId(), payload, activeDeptHeader);
            created = false;
            updated = true;
        }
        String qualityWarning = fileService.writeAutoQualityTemplate(dto, sourceColumns, planId);
        boolean qualityGenerated = qualityWarning == null;
        if (qualityWarning != null) {
            LOG.warn("[generate-from-ods] quality template skipped for {}: {}", modelName, qualityWarning);
        }
        return new GenerateLayerResult(modelName, created, updated, null, qualityGenerated, qualityWarning);
    }

    private List<ColumnProfile> resolveSourceColumnsForQuality(String schema, String table) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) {
            return List.of();
        }
        CatalogDataset dataset = datasetRepository.findFirstByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema.trim(), table.trim()).orElse(null);
        if (dataset == null) {
            return List.of();
        }
        CatalogTableSchema catalogTable = tableRepository.findFirstByDatasetAndNameIgnoreCase(dataset, table.trim()).orElse(null);
        if (catalogTable == null) {
            List<CatalogTableSchema> tables = tableRepository.findByDataset(dataset);
            if (tables != null && !tables.isEmpty()) {
                catalogTable = tables.get(0);
            }
        }
        if (catalogTable == null) {
            return List.of();
        }
        List<CatalogColumnSchema> columns = columnRepository.findByTable(catalogTable);
        if (columns == null || columns.isEmpty()) {
            return List.of();
        }
        List<ColumnProfile> profiles = new ArrayList<>();
        for (CatalogColumnSchema column : columns) {
            if (column == null || !StringUtils.hasText(column.getName())) {
                continue;
            }
            profiles.add(
                new ColumnProfile(
                    column.getName().trim(),
                    trimToNull(column.getDataType()),
                    column.getNullable(),
                    trimToNull(column.getComment()),
                    column.getStandardId() != null ? column.getStandardId().toString() : null,
                    trimToNull(column.getStandardRule())
                )
            );
        }
        profiles.sort(Comparator.comparing(ColumnProfile::name, String.CASE_INSENSITIVE_ORDER));
        return profiles;
    }

    private void upsertDiscoveredWorkspaceModel(
        Path projectDir, Path sqlFile, DbtConfigService.DbtWorkspaceConfig config,
        UUID sourceDataSourceId, Map<String, ModelingSqlModel> existingByName
    ) {
        try {
            String fileName = sqlFile.getFileName() == null ? null : sqlFile.getFileName().toString();
            if (!StringUtils.hasText(fileName) || !fileName.toLowerCase(Locale.ROOT).endsWith(".sql")) {
                return;
            }
            String modelName = fileName.substring(0, fileName.length() - 4);
            if (!MODEL_NAME_PATTERN.matcher(modelName).matches()) {
                return;
            }
            String relativePath = projectDir.relativize(sqlFile).toString().replace('\\', '/');
            String sqlText = Files.readString(sqlFile, StandardCharsets.UTF_8);
            String layer = inferLayerFromPath(relativePath, modelName);
            String discoveredTags = parseDbtTags(sqlText);
            String discoveredDagSelector = ModelingSqlModelService.resolveDagSelectorValue(discoveredTags, null);
            String key = modelName.toLowerCase(Locale.ROOT);
            ModelingSqlModel existing = existingByName.get(key);
            boolean changed = false;
            if (existing == null) {
                existing = new ModelingSqlModel();
                existing.setName(modelName);
                existing.setEnabled(Boolean.TRUE);
                existing.setStatus("DRAFT");
                existing.setSourceDataSourceId(sourceDataSourceId);
                existing.setSchemaName(trimToNull(config != null ? config.schema() : null));
                existing.setMaterialized("table");
                existing.setDescription("从 dbt 工作区自动发现");
                changed = true;
            }
            if (!relativePath.equals(trimToNull(existing.getModelPath()))) {
                existing.setModelPath(relativePath);
                changed = true;
            }
            if (!sqlText.equals(defaultText(existing.getSqlText(), ""))) {
                existing.setSqlText(sqlText);
                changed = true;
            }
            if (!StringUtils.hasText(existing.getLayer()) && StringUtils.hasText(layer)) {
                existing.setLayer(layer);
                changed = true;
            }
            if (StringUtils.hasText(discoveredTags) && !discoveredTags.equals(trimToNull(existing.getTags()))) {
                existing.setTags(discoveredTags);
                changed = true;
            }
            if (StringUtils.hasText(discoveredDagSelector) && !discoveredDagSelector.equals(trimToNull(existing.getDagSelector()))) {
                existing.setDagSelector(discoveredDagSelector);
                changed = true;
            }
            if (existing.getSourceDataSourceId() == null) {
                existing.setSourceDataSourceId(sourceDataSourceId);
                changed = true;
            }
            if (!StringUtils.hasText(existing.getMaterialized())) {
                existing.setMaterialized("table");
                changed = true;
            }
            if (changed) {
                ModelingSqlModel saved = repo.save(existing);
                existingByName.put(key, saved);
            }
        } catch (IOException ex) {
            LOG.warn("[dbt-model] failed to load sql file {}: {}", sqlFile, ex.getMessage());
        }
    }

    private String inferLayerFromPath(String relativePath, String modelName) {
        if (StringUtils.hasText(relativePath)) {
            String normalized = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
            if (normalized.contains("/ods/")) return "ODS";
            if (normalized.contains("/dwd/")) return "DWD";
            if (normalized.contains("/dws/")) return "DWS";
            if (normalized.contains("/ads/")) return "ADS";
        }
        return ModelingSqlModelService.inferLayer(modelName);
    }

    private String parseDbtTags(String sqlText) {
        if (!StringUtils.hasText(sqlText)) {
            return null;
        }
        Set<String> tags = new LinkedHashSet<>();
        Matcher configMatcher = DBT_TAGS_CONFIG_PATTERN.matcher(sqlText);
        while (configMatcher.find()) {
            Matcher literalMatcher = DBT_TAG_LITERAL_PATTERN.matcher(configMatcher.group(1));
            while (literalMatcher.find()) {
                String tag = ModelingSqlModelService.sanitizeTag(literalMatcher.group(1), null);
                if (StringUtils.hasText(tag)) {
                    tags.add(tag);
                }
            }
        }
        return tags.isEmpty() ? null : String.join(",", tags);
    }

    private SqlModelGovernancePreviewItem toGovernancePreviewItem(ModelingSqlModel model, Set<String> ruleHits) {
        ModelingPlan plan = model != null && model.getPlanId() != null ? planRepo.findById(model.getPlanId()).orElse(null) : null;
        GovernanceImpact impact = collectGovernanceImpact(model);
        boolean fileDeleteSafe = fileService.canDeleteModelPathAfterRemoving(model != null ? model.getModelPath() : null, model != null && model.getId() != null ? Set.of(model.getId()) : Set.of());
        String suggestedAction = fileDeleteSafe ? "DELETE_WITH_FILE" : "DELETE_RECORD_ONLY";
        return new SqlModelGovernancePreviewItem(
            model != null ? model.getId() : null,
            model != null ? model.getPlanId() : null,
            plan != null ? plan.getName() : null,
            model != null ? model.getName() : null,
            model != null ? model.getLayer() : null,
            model != null ? model.getStatus() : null,
            model != null ? model.getModelPath() : null,
            List.copyOf(ruleHits),
            impact.downstreamRefCount(),
            impact.datasetBindingCount(),
            impact.reportBindingCount(),
            fileDeleteSafe,
            suggestedAction
        );
    }

    private GovernanceImpact collectGovernanceImpact(ModelingSqlModel model) {
        if (model == null) {
            return new GovernanceImpact(0, 0, 0);
        }
        int downstreamRefCount = countGovernanceDownstreamRefs(model);
        Set<UUID> impactedDatasetIds = new LinkedHashSet<>();
        if (queryDatasetAssetRepository != null) {
            List<QueryDatasetAsset> allDatasets = queryDatasetAssetRepository.findByEnabledTrueOrderByLastModifiedDateDesc();
            for (QueryDatasetAsset dataset : allDatasets) {
                if (dataset == null || dataset.getId() == null) {
                    continue;
                }
                if (referencesModel(dataset.getSqlText(), model.getName(), model.getAlias())) {
                    impactedDatasetIds.add(dataset.getId());
                }
            }
        }
        int reportBindingCount = 0;
        if (biReportLinkRepository != null && !impactedDatasetIds.isEmpty()) {
            reportBindingCount = (int) biReportLinkRepository
                .findAll()
                .stream()
                .filter(report -> report != null && report.getQueryDatasetId() != null)
                .filter(report -> impactedDatasetIds.contains(report.getQueryDatasetId()))
                .count();
        }
        return new GovernanceImpact(downstreamRefCount, impactedDatasetIds.size(), reportBindingCount);
    }

    private int countGovernanceDownstreamRefs(ModelingSqlModel model) {
        if (model == null || model.getId() == null || !StringUtils.hasText(model.getName())) {
            return 0;
        }
        return (int) repo
            .findAll()
            .stream()
            .filter(candidate -> candidate != null && candidate.getId() != null && !candidate.getId().equals(model.getId()))
            .filter(candidate -> referencesModel(candidate.getSqlText(), model.getName(), model.getAlias()))
            .count();
    }

    private boolean referencesModel(String sqlText, String modelName, String modelAlias) {
        String sql = trimToEmpty(sqlText).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(sql)) {
            return false;
        }
        String name = trimToNull(modelName);
        String alias = trimToNull(modelAlias);
        if (StringUtils.hasText(name)) {
            String needle = name.toLowerCase(Locale.ROOT);
            if (sql.contains("ref('" + needle + "')") || sql.contains("ref(\"" + needle + "\")") || sql.contains(" " + needle + " ") || sql.contains("." + needle)) {
                return true;
            }
        }
        if (StringUtils.hasText(alias)) {
            String needle = alias.toLowerCase(Locale.ROOT);
            if (sql.contains("ref('" + needle + "')") || sql.contains("ref(\"" + needle + "\")") || sql.contains(" " + needle + " ") || sql.contains("." + needle)) {
                return true;
            }
        }
        return false;
    }

    private boolean governanceScopeMatches(ModelingSqlModel model, SqlModelGovernancePreviewRequest request) {
        if (model == null) return false;
        if (request == null) return true;
        if (StringUtils.hasText(request.layer()) && !normalizeUpper(model.getLayer()).equals(normalizeUpper(request.layer()))) return false;
        if (StringUtils.hasText(request.tag()) && !containsIgnoreCase(model.getTags(), request.tag())) return false;
        return true;
    }

    private Set<String> normalizeGovernanceRules(List<String> rules) {
        Set<String> normalized = new LinkedHashSet<>();
        if (rules == null) return normalized;
        for (String rule : rules) {
            String value = trimToNull(rule);
            if (value != null) normalized.add(value);
        }
        return normalized;
    }

    private List<String> normalizeSqlKeywords(List<String> sqlKeywords) {
        if (sqlKeywords == null || sqlKeywords.isEmpty()) return List.of();
        return sqlKeywords.stream().map(this::trimToNull).filter(StringUtils::hasText).toList();
    }

    private boolean governanceSqlContains(ModelingSqlModel model, String keyword) {
        String sql = normalizeLower(model != null ? model.getSqlText() : null);
        String normalizedKeyword = normalizeLower(keyword);
        return StringUtils.hasText(sql) && StringUtils.hasText(normalizedKeyword) && sql.contains(normalizedKeyword);
    }

    private GovernancePreviewAccumulator governanceAccumulator(Map<UUID, GovernancePreviewAccumulator> accumulators, ModelingSqlModel model) {
        if (model == null || model.getId() == null) throw new IllegalArgumentException("模型不存在");
        return accumulators.computeIfAbsent(model.getId(), unused -> new GovernancePreviewAccumulator(model));
    }

    private boolean hasActiveOdsDataset(String schema, String table) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) return false;
        boolean exists = datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndWarehouseLayerIgnoreCaseAndEnabledTrue(
            schema.trim(), table.trim(), "ODS"
        );
        if (!exists) {
            exists = datasetRepository.existsByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCaseAndEnabledTrue(schema.trim(), table.trim());
        }
        return exists;
    }

    private String resolveEntityToken(InfraOdsTableMapping mapping, String odsTable) {
        String candidate = trimToNull(mapping != null ? mapping.getEntityCode() : null);
        if (!StringUtils.hasText(candidate)) candidate = trimToNull(odsTable);
        if (!StringUtils.hasText(candidate)) return null;
        String normalized = ModelingSqlModelService.slugify(candidate);
        if (normalized.startsWith("ods_")) normalized = normalized.substring(4);
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

    private UUID resolveUsableSourceId(UUID sourceId, String activeDeptHeader, String origin) {
        if (sourceId == null) return null;
        try {
            coreService.resolveSource(sourceId, activeDeptHeader);
            return sourceId;
        } catch (RuntimeException ex) {
            LOG.warn("[generate-from-ods] {} sourceDataSourceId {} not usable: {}", origin, sourceId, ex.getMessage());
            return null;
        }
    }

    private UUID resolveOdsDatasetSourceId(String schema, String table, String activeDeptHeader) {
        if (!StringUtils.hasText(schema) || !StringUtils.hasText(table)) return null;
        List<CatalogDataset> datasets = datasetRepository.findByHiveDatabaseIgnoreCaseAndHiveTableIgnoreCase(schema.trim(), table.trim());
        if (datasets == null || datasets.isEmpty()) return null;
        for (CatalogDataset dataset : datasets) {
            if (dataset == null || dataset.getSourceId() == null) continue;
            UUID usable = resolveUsableSourceId(dataset.getSourceId(), activeDeptHeader, "dataset");
            if (usable != null) return usable;
        }
        return null;
    }

    private UUID resolveFallbackSourceId(String activeDeptHeader) {
        List<InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase("ACTIVE");
        if (candidates == null || candidates.isEmpty()) {
            candidates = dataSourceRepository.findAll();
        }
        List<InfraDataSource> ranked = new ArrayList<>();
        if (candidates != null) ranked.addAll(candidates);
        adminInfraClient.fetchDefaultDataLake().map(ModelingSqlModelService::toVirtualSource).ifPresent(ranked::add);
        UUID bestId = null;
        int bestScore = Integer.MIN_VALUE;
        for (InfraDataSource source : ranked) {
            if (source == null || source.getId() == null) continue;
            UUID usable = resolveUsableSourceId(source.getId(), activeDeptHeader, "fallback");
            if (usable == null) continue;
            int score = ModelingSqlModelService.scoreSource(source);
            if (score > bestScore) {
                bestScore = score;
                bestId = usable;
            }
        }
        return bestId;
    }

    private UUID resolveBatchImportSourceId(UUID requestedSourceId, String activeDeptHeader) {
        UUID usable = resolveUsableSourceId(requestedSourceId, activeDeptHeader, "batch-import");
        if (usable != null) return usable;
        UUID fallback = resolveFallbackSourceId(activeDeptHeader);
        if (fallback != null && requestedSourceId != null) {
            LOG.info("[batch-import] fallback sourceDataSourceId {} -> {}", requestedSourceId, fallback);
        }
        return fallback;
    }

    private String validateBatchImportEntry(String name, String layer, String sqlPath, String materialized) {
        if (!StringUtils.hasText(name)) return "模型名称不能为空";
        if (!MODEL_NAME_PATTERN.matcher(name).matches()) return "模型名称仅允许字母/数字/下划线，且必须以字母开头";
        if (!StringUtils.hasText(layer)) return "模型分层不能为空";
        if (!StringUtils.hasText(ModelingSqlModelService.normalizeLayer(trimToNull(layer)))) return "非法模型分层: " + layer;
        if (!StringUtils.hasText(sqlPath)) return "SQL 文件路径不能为空";
        if (StringUtils.hasText(materialized)) {
            String normalized = materialized.trim().toLowerCase(Locale.ROOT);
            if (!Set.of("table", "view", "incremental").contains(normalized)) return "非法物化方式: " + materialized;
        }
        return null;
    }

    private static boolean isValidIdentifier(String value) {
        return value != null && value.matches("^[A-Za-z_][A-Za-z0-9_.\\-]*$");
    }

    // ── String utilities ───────────────────────────────────────────────

    private boolean containsIgnoreCase(String text, String keyword) {
        return normalizeLower(text).contains(normalizeLower(keyword));
    }

    private String normalizeLower(String value) {
        return defaultText(trimToNull(value), "").toLowerCase(Locale.ROOT);
    }

    private String normalizeUpper(String value) {
        return defaultText(trimToNull(value), "").toUpperCase(Locale.ROOT);
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

    // ── Inner types ────────────────────────────────────────────────────

    private record GenerateLayerResult(
        String modelName,
        boolean created,
        boolean updated,
        String skippedReason,
        boolean qualityTemplateGenerated,
        String qualityWarning
    ) {}

    private static final class GovernancePreviewAccumulator {
        private final ModelingSqlModel model;
        private final Set<String> ruleHits = new LinkedHashSet<>();

        private GovernancePreviewAccumulator(ModelingSqlModel model) {
            this.model = model;
        }

        private ModelingSqlModel model() {
            return model;
        }

        private Set<String> ruleHits() {
            return ruleHits;
        }

        private void addRuleHit(String ruleHit) {
            if (StringUtils.hasText(ruleHit)) {
                ruleHits.add(ruleHit);
            }
        }
    }

    private record GovernanceImpact(int downstreamRefCount, int datasetBindingCount, int reportBindingCount) {}
}
