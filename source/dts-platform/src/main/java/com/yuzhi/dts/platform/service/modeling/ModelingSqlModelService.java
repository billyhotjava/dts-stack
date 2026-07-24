package com.yuzhi.dts.platform.service.modeling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogColumnSchema;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogDomain;
import com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema;
import com.yuzhi.dts.platform.domain.modeling.ModelingPlan;
import com.yuzhi.dts.platform.domain.modeling.ModelingSqlModel;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogDomainRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.repository.explore.QueryDatasetAssetRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingPlanRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelingSqlModelRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.repository.visualization.BiReportLinkRepository;
import com.yuzhi.dts.platform.security.DepartmentUtils;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetIdentity;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService;
import com.yuzhi.dts.platform.service.catalog.CatalogColumnSyncService.ColumnSpec;
import com.yuzhi.dts.platform.service.catalog.CodeAssetLifecycleMapper;
import com.yuzhi.dts.platform.service.catalog.CodeAssetGrantWriter;
import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.governance.DefaultLakeDatasetGuard;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.DataSourceScorer;
import com.yuzhi.dts.platform.service.security.OrganizationVisibilityService;
import jakarta.persistence.EntityNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * Core CRUD operations for SQL models: list, get, create, update, delete,
 * listColumns, and getContractImpact.
 */
@Service
public class ModelingSqlModelService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelingSqlModelService.class);
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");
    static final Pattern NON_SAFE = Pattern.compile("[^a-z0-9_]+");
    private static final String STATUS_ACTIVE = "ACTIVE";

    private static final String SOURCE_ADMIN_DATA_LAKE = "admin-data-lake";

    private final ModelingSqlModelRepository repo;
    private final ModelingPlanRepository planRepo;
    private final InfraDataSourceRepository dataSourceRepository;
    private final AdminInfraClient adminInfraClient;
    private final OrganizationVisibilityService organizationVisibilityService;
    private final DataStandardSecurity security;
    private final DbtConfigService dbtConfigService;
    private final CatalogDatasetRepository datasetRepository;
    private final CatalogDomainRepository catalogDomainRepository;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;
    private final QueryDatasetAssetRepository queryDatasetAssetRepository;
    private final BiReportLinkRepository biReportLinkRepository;
    private final CatalogColumnSyncService columnSyncService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final ModelFileService fileService;
    private final Executor taskExecutor;
    private final TransactionTemplate batchDeleteTransactionTemplate;
    private final DefaultLakeDatasetGuard defaultLakeDatasetGuard;
    private final CodeAssetGrantWriter codeAssetGrantWriter;

    public ModelingSqlModelService(
        ModelingSqlModelRepository repo,
        ModelingPlanRepository planRepo,
        InfraDataSourceRepository dataSourceRepository,
        AdminInfraClient adminInfraClient,
        OrganizationVisibilityService organizationVisibilityService,
        DataStandardSecurity security,
        DbtConfigService dbtConfigService,
        CatalogDatasetRepository datasetRepository,
        CatalogDomainRepository catalogDomainRepository,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository,
        QueryDatasetAssetRepository queryDatasetAssetRepository,
        BiReportLinkRepository biReportLinkRepository,
        CatalogColumnSyncService columnSyncService,
        AuditService auditService,
        ObjectMapper objectMapper,
        ModelFileService fileService,
        @Qualifier("taskExecutor") Executor taskExecutor,
        PlatformTransactionManager transactionManager,
        DefaultLakeDatasetGuard defaultLakeDatasetGuard,
        CodeAssetGrantWriter codeAssetGrantWriter
    ) {
        this.repo = repo;
        this.planRepo = planRepo;
        this.dataSourceRepository = dataSourceRepository;
        this.adminInfraClient = adminInfraClient;
        this.organizationVisibilityService = organizationVisibilityService;
        this.security = security;
        this.dbtConfigService = dbtConfigService;
        this.datasetRepository = datasetRepository;
        this.catalogDomainRepository = catalogDomainRepository;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
        this.queryDatasetAssetRepository = queryDatasetAssetRepository;
        this.biReportLinkRepository = biReportLinkRepository;
        this.columnSyncService = columnSyncService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.fileService = fileService;
        this.taskExecutor = taskExecutor;
        this.defaultLakeDatasetGuard = defaultLakeDatasetGuard;
        this.codeAssetGrantWriter = codeAssetGrantWriter;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchDeleteTransactionTemplate = template;
    }

    // ── Public CRUD operations ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<SqlModelDto> list(UUID planId, String keyword, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        List<ModelingSqlModel> models = planId == null ? repo.findAll() : repo.findByPlanId(planId);
        String kw = trimToNull(keyword);
        List<ModelingSqlModel> visibleModels = new ArrayList<>();
        for (ModelingSqlModel model : models) {
            if (!isOwnerDeptVisible(model != null ? model.getOwnerDept() : null, activeDept, instituteScope)) {
                continue;
            }
            if (kw != null && !matchKeyword(model, kw)) {
                continue;
            }
            visibleModels.add(model);
        }
        List<SqlModelDto> result = new ArrayList<>(toDtoBatch(visibleModels));
        result.sort((a, b) -> String.valueOf(a.name()).compareToIgnoreCase(String.valueOf(b.name())));
        return result;
    }

    @Transactional(readOnly = true)
    public SqlModelDto get(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该模型");
        }
        return toDto(model);
    }

    @Transactional(readOnly = true)
    public SqlModelContractImpact getContractImpact(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该模型");
        }
        ContractMeta meta = extractContractMeta(model.getSemanticContract());
        int fieldCount = listColumns(id, activeDeptHeader).size();
        List<com.yuzhi.dts.platform.domain.explore.QueryDatasetAsset> allDatasets = queryDatasetAssetRepository.findByEnabledTrueOrderByLastModifiedDateDesc();
        List<QueryDatasetImpactItem> impactedDatasets = new ArrayList<>();
        Set<UUID> impactedDatasetIds = new LinkedHashSet<>();
        for (var dataset : allDatasets) {
            if (dataset == null || dataset.getId() == null) {
                continue;
            }
            if (!referencesModel(dataset.getSqlText(), model.getName(), model.getAlias())) {
                continue;
            }
            impactedDatasetIds.add(dataset.getId());
            impactedDatasets.add(
                new QueryDatasetImpactItem(
                    dataset.getId(),
                    trimToNull(dataset.getName()),
                    trimToNull(dataset.getStatus()),
                    dataset.getPublishedVersion()
                )
            );
        }
        impactedDatasets.sort(
            java.util.Comparator.comparing(
                item -> defaultText(trimToNull(item.name()), item.id() != null ? item.id().toString() : ""),
                String.CASE_INSENSITIVE_ORDER
            )
        );

        List<ReportImpactItem> impactedReports = biReportLinkRepository
            .findAll()
            .stream()
            .filter(report -> report != null && report.getId() != null && report.getQueryDatasetId() != null)
            .filter(report -> impactedDatasetIds.contains(report.getQueryDatasetId()))
            .map(
                report ->
                    new ReportImpactItem(
                        report.getId(),
                        trimToNull(report.getTitle()),
                        trimToNull(report.getCode()),
                        report.getQueryDatasetId(),
                        report.isEnabled()
                    )
            )
            .sorted(
                java.util.Comparator.comparing(
                    item -> defaultText(trimToNull(item.title()), item.id() != null ? item.id().toString() : ""),
                    String.CASE_INSENSITIVE_ORDER
                )
            )
            .toList();

        return new SqlModelContractImpact(
            model.getId(),
            model.getName(),
            model.getContractVersion(),
            model.getContractUpdatedAt(),
            meta.metricCount(),
            meta.dimensionCount(),
            fieldCount,
            impactedDatasets.size(),
            impactedReports.size(),
            impactedDatasets,
            impactedReports
        );
    }

    @Transactional(readOnly = true)
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

    @Transactional(readOnly = true)
    public SqlModelStandardBindingResult listStandardBindings(UUID id, String activeDeptHeader) {
        ModelingSqlModel model = loadVisibleModel(id, activeDeptHeader, "当前账号无权访问该模型");
        return buildStandardBindingResult(model, readStandardBindings(model), activeDeptHeader);
    }

    @Transactional
    public SqlModelStandardBindingResult saveStandardBindings(
        UUID id,
        SqlModelStandardBindingRequest request,
        String activeDeptHeader
    ) {
        ModelingSqlModel model = loadVisibleModel(id, activeDeptHeader, "当前账号无权维护该模型");
        List<SqlModelStandardBinding> bindings = request == null || request.bindings() == null
            ? List.of()
            : request
                .bindings()
                .stream()
                .map(this::normalizeStandardBinding)
                .filter(binding -> StringUtils.hasText(binding.columnName()))
                .toList();
        writeStandardBindings(model, bindings);
        ModelingSqlModel saved = repo.save(model);
        return buildStandardBindingResult(saved, bindings, activeDeptHeader);
    }

    @Transactional
    public SqlModelSchemaYmlResult generateSchemaYml(UUID id, String activeDeptHeader) {
        ModelingSqlModel model = loadVisibleModel(id, activeDeptHeader, "当前账号无权访问该模型");
        SqlModelStandardBindingResult bindingResult = buildStandardBindingResult(model, readStandardBindings(model), activeDeptHeader);
        String schemaYml = buildSchemaYml(model, bindingResult.bindings());
        fileService.writeSchemaYmlFile(model.getModelPath(), schemaYml);
        return new SqlModelSchemaYmlResult(
            model.getId(),
            model.getName(),
            resolveSchemaYmlPath(model),
            schemaYml
        );
    }

    @Transactional(readOnly = true)
    public SqlModelStandardGateResult checkStandardGate(UUID id, String activeDeptHeader) {
        ModelingSqlModel model = loadVisibleModel(id, activeDeptHeader, "当前账号无权访问该模型");
        SqlModelStandardBindingResult result = buildStandardBindingResult(model, readStandardBindings(model), activeDeptHeader);
        List<String> blockers = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (result.totalColumns() == 0) {
            warnings.add("当前模型尚未识别字段，请先同步字段或生成字段草稿");
        }
        if (result.missingColumns() > 0) {
            blockers.add("存在 " + result.missingColumns() + " 个字段未绑定数据元标准");
        }
        long missingCodeSet = result
            .bindings()
            .stream()
            .filter(binding -> StringUtils.hasText(binding.codeSet()))
            .filter(binding -> !StringUtils.hasText(binding.standardCode()))
            .count();
        if (missingCodeSet > 0) {
            blockers.add("存在 " + missingCodeSet + " 个码表字段缺少标准编码");
        }
        long drifted = result.bindings().stream().filter(binding -> "drift".equalsIgnoreCase(trimToNull(binding.status()))).count();
        if (drifted > 0) {
            blockers.add("存在 " + drifted + " 个字段标准版本漂移");
        }
        if (result.mappedColumns() == 0 && result.totalColumns() > 0) {
            warnings.add("字段已识别，但尚未形成标准绑定");
        }
        return new SqlModelStandardGateResult(
            model.getId(),
            model.getName(),
            !blockers.isEmpty(),
            blockers,
            warnings,
            result.totalColumns(),
            result.mappedColumns(),
            result.missingColumns()
        );
    }

    @Transactional
    public SqlModelDto create(SqlModelRequest request, String activeDeptHeader) {
        ensureWorkspaceWritable();
        ModelingSqlModel model = new ModelingSqlModel();
        apply(model, request, activeDeptHeader, true);
        ModelingSqlModel saved = repo.save(model);
        syncCodeAssetGrant(saved);
        fileService.writeModelFile(saved);
        syncDraftColumns(saved);
        return toDto(saved);
    }

    @Transactional
    public SqlModelDto update(UUID id, SqlModelRequest request, String activeDeptHeader) {
        ensureWorkspaceWritable();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (model.getModelSpecId() != null && repo.hasCurrentLifecycleEvidence(model.getModelSpecId())) {
            throw new IllegalArgumentException("当前高级实现已有构建或审批证据，请先返回数据实现阶段创建新的 implementation revision");
        }
        String oldPath = model.getModelPath();
        apply(model, request, activeDeptHeader, false);
        ModelingSqlModel saved = repo.save(model);
        syncCodeAssetGrant(saved);
        fileService.writeModelFile(saved);
        fileService.deleteFileIfChanged(oldPath, saved.getModelPath());
        syncDraftColumns(saved);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id, String activeDeptHeader) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权删除该模型");
        }
        if (model.getModelSpecId() != null) {
            throw new IllegalArgumentException("高级实现工作区不能通过普通删除移除，请使用 ModelSpec 退役流程");
        }
        boolean deleteFile = canDeleteModelPathAfterRemoving(model.getModelPath(), model.getId() == null ? Set.of() : Set.of(model.getId()));
        repo.delete(model);
        if (deleteFile) {
            scheduleFileDeletionAfterCommit(model.getModelPath());
        }
    }

    @Transactional
    public BatchDeleteResult deleteBatch(BatchDeleteRequest request, String activeDeptHeader) {
        List<UUID> requestedIds = request == null || request.modelIds() == null
            ? List.of()
            : request.modelIds().stream().filter(Objects::nonNull).distinct().toList();
        List<BatchDeleteFailure> failures = new ArrayList<>();
        int deleted = 0;
        for (UUID id : requestedIds) {
            try {
                batchDeleteTransactionTemplate.executeWithoutResult(status -> delete(id, activeDeptHeader));
                deleted++;
            } catch (Exception ex) {
                failures.add(new BatchDeleteFailure(id, trimToNull(ex.getMessage()) != null ? ex.getMessage() : "删除失败"));
            }
        }
        return new BatchDeleteResult(requestedIds.size(), deleted, failures.size(), failures);
    }

    public record BatchDeleteRequest(List<UUID> modelIds) {}

    public record BatchDeleteFailure(UUID modelId, String message) {}

    public record BatchDeleteResult(int requested, int deleted, int failed, List<BatchDeleteFailure> failures) {}

    // ── Package-visible methods used by ModelGenerationService ──────────

    void ensureWorkspaceWritable() {
        DbtConfigService.DbtConfigView view = dbtConfigService.loadConfig();
        if (view == null || view.workspaceStatus() == null || !view.workspaceStatus().ok()) {
            String message = view != null && view.workspaceStatus() != null
                ? view.workspaceStatus().message()
                : "dbt 工作区未配置";
            throw new IllegalArgumentException(message);
        }
    }

    ModelingPlan resolvePlan(UUID planId, String activeDeptHeader) {
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

    InfraDataSource resolveSource(UUID sourceId, String activeDeptHeader) {
        InfraDataSource source = resolveSourceEntity(sourceId).orElseThrow(() -> new EntityNotFoundException("来源数据源不存在"));
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        if (!isOwnerDeptVisible(source.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException("当前账号无权访问该数据源");
        }
        return source;
    }

    boolean isOwnerDeptVisible(String ownerDept, String activeDept, boolean instituteScope) {
        String trimmedOwner = trimToNull(ownerDept);
        if (trimmedOwner == null) return true;
        if (instituteScope) return true;
        if (organizationVisibilityService.isRoot(trimmedOwner)) return true;
        if (!StringUtils.hasText(activeDept)) return false;
        return DepartmentUtils.matches(trimmedOwner, activeDept);
    }

    boolean canDeleteModelPathAfterRemoving(String modelPath, Set<UUID> removingIds) {
        return fileService.canDeleteModelPathAfterRemoving(modelPath, removingIds);
    }

    String dedupeModelKey(ModelingSqlModel model) {
        if (model == null) {
            return UUID.randomUUID().toString();
        }
        return defaultText(model.getPlanId() != null ? model.getPlanId().toString() : null, "")
            + "::"
            + defaultText(trimToNull(model.getName()), model.getId() != null ? model.getId().toString() : "").toLowerCase(Locale.ROOT);
    }

    boolean shouldReplaceDuplicateModel(ModelingSqlModel candidate, ModelingSqlModel existing) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        int byModified = compareInstants(candidate.getLastModifiedDate(), existing.getLastModifiedDate());
        if (byModified != 0) {
            return byModified > 0;
        }
        int byCreated = compareInstants(candidate.getCreatedDate(), existing.getCreatedDate());
        if (byCreated != 0) {
            return byCreated > 0;
        }
        int byStatus = Integer.compare(modelStatusRank(candidate.getStatus()), modelStatusRank(existing.getStatus()));
        if (byStatus != 0) {
            return byStatus > 0;
        }
        return defaultText(candidate.getId() != null ? candidate.getId().toString() : null, "").compareTo(
            defaultText(existing.getId() != null ? existing.getId().toString() : null, "")
        ) > 0;
    }

    void syncDraftColumns(ModelingSqlModel model) {
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
            Optional<CatalogDomain> planDomain = resolvePlanDomain(model);
            if (dataset.getDomain() == null && planDomain.isPresent()) {
                dataset.setDomain(planDomain.orElseThrow());
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
                    "summary", "模型字段草稿同步",
                    "modelId", model.getId() != null ? model.getId().toString() : null,
                    "schema", schemaName,
                    "table", tableName,
                    "columnCount", updated
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

    // ── Static/package-visible utility methods ─────────────────────────

    static String slugify(String value) {
        if (!StringUtils.hasText(value)) return "model";
        String slug = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("_");
        slug = slug.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(slug) ? slug : "model";
    }

    static String sanitizeTag(String value, String fallback) {
        if (!StringUtils.hasText(value)) return fallback;
        String tag = NON_SAFE.matcher(value.trim().toLowerCase(Locale.ROOT)).replaceAll("-");
        tag = tag.replaceAll("^-+", "").replaceAll("-+$", "");
        return StringUtils.hasText(tag) ? tag : fallback;
    }

    static String mergeTags(String rawTags, String sourceTag) {
        List<String> tags = splitTags(rawTags, sourceTag, null);
        if (tags.isEmpty()) return null;
        return String.join(",", tags);
    }

    static String resolveDagSelectorValue(String rawTags, String fallbackTag) {
        List<String> tags = splitTags(rawTags, fallbackTag, null);
        if (tags.isEmpty()) {
            return null;
        }
        return "tag:" + tags.get(0);
    }

    static List<String> splitTags(String rawTags, String sourceTag, String layerTag) {
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

    static String normalizeLayer(String layer) {
        if (!StringUtils.hasText(layer)) {
            return null;
        }
        String normalized = layer.trim().toUpperCase(Locale.ROOT);
        if (
            "ODS".equals(normalized)
            || "STG".equals(normalized)
            || "DWD".equals(normalized)
            || "DWS".equals(normalized)
            || "ADS".equals(normalized)
        ) {
            return normalized;
        }
        return null;
    }

    static String inferLayer(String name) {
        if (!StringUtils.hasText(name)) return null;
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ods_")) return "ODS";
        if (normalized.startsWith("stg_")) return "STG";
        if (normalized.startsWith("dwd_")) return "DWD";
        if (normalized.startsWith("dws_")) return "DWS";
        if (normalized.startsWith("ads_")) return "ADS";
        return null;
    }

    static String resolveSourceKey(InfraDataSource source) {
        if (source == null) return null;
        Map<String, Object> props = parsePropsStatic(source.getProps());
        String key = firstTextStatic(props, "sourceSystem", "sourceName", "system", "app", "appCode", "name");
        if (StringUtils.hasText(key)) {
            return key;
        }
        return source.getName();
    }

    static InfraDataSource toVirtualSource(AdminInfraClient.AdminDataLakeConfig lake) {
        InfraDataSource source = new InfraDataSource();
        source.setId(lake.getId());
        source.setName(StringUtils.hasText(lake.getName()) ? lake.getName() : "默认数据湖");
        source.setType(StringUtils.hasText(lake.getType()) ? lake.getType() : "DATA_LAKE");
        source.setJdbcUrl(lake.getJdbcUrl());
        source.setUsername(lake.getUsername());
        source.setDescription(StringUtils.hasText(lake.getDescription()) ? lake.getDescription() : "由管理员配置的默认数据湖");
        source.setOwnerDept(null);
        source.setStatus(StringUtils.hasText(lake.getStatus()) ? lake.getStatus() : STATUS_ACTIVE);
        source.setProps(buildVirtualSourcePropsStatic(lake));
        return source;
    }

    static int scoreSource(InfraDataSource source) {
        if (source == null) {
            return Integer.MIN_VALUE;
        }
        int score = DataSourceScorer.scoreDataSource(source.getName(), source.getJdbcUrl(), source.getType(), source.getStatus());
        // Context-specific bonuses: admin-data-lake prop and "defaulted" flag
        Map<String, Object> props = parsePropsStatic(source.getProps());
        if (SOURCE_ADMIN_DATA_LAKE.equalsIgnoreCase(trimToEmpty(firstTextStatic(props, "source", "sourceSystem")))) {
            score += 120;
        }
        if (Boolean.parseBoolean(String.valueOf(props.get("defaulted")))) {
            score += 40;
        }
        return score;
    }

    // ── Private helpers ────────────────────────────────────────────────

    private int compareInstants(Instant left, Instant right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        return left.compareTo(right);
    }

    private int modelStatusRank(String status) {
        String normalized = defaultText(trimToNull(status), "").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PUBLISHED" -> 3;
            case "ACTIVE" -> 2;
            case "DRAFT" -> 1;
            default -> 0;
        };
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

    private ModelingSqlModel loadVisibleModel(UUID id, String activeDeptHeader, String forbiddenMessage) {
        String activeDept = security.resolveActiveDept(activeDeptHeader);
        boolean instituteScope = security.hasInstituteScope();
        ModelingSqlModel model = repo.findById(id).orElseThrow(() -> new EntityNotFoundException("模型不存在"));
        if (!isOwnerDeptVisible(model.getOwnerDept(), activeDept, instituteScope)) {
            throw new IllegalArgumentException(forbiddenMessage);
        }
        return model;
    }

    private SqlModelStandardBindingResult buildStandardBindingResult(
        ModelingSqlModel model,
        List<SqlModelStandardBinding> persistedBindings,
        String activeDeptHeader
    ) {
        List<SqlModelStandardBinding> saved = persistedBindings == null
            ? List.of()
            : persistedBindings.stream().map(this::normalizeStandardBinding).toList();
        List<Map<String, Object>> columns = model != null && model.getId() != null ? listColumns(model.getId(), activeDeptHeader) : List.of();
        if (columns.isEmpty()) {
            int mapped = (int) saved.stream().filter(binding -> StringUtils.hasText(binding.standardCode())).count();
            return new SqlModelStandardBindingResult(
                model != null ? model.getId() : null,
                model != null ? model.getName() : null,
                saved.size(),
                mapped,
                Math.max(saved.size() - mapped, 0),
                saved
            );
        }

        Map<String, SqlModelStandardBinding> byColumn = new LinkedHashMap<>();
        for (SqlModelStandardBinding binding : saved) {
            String key = normalizeColumnKey(binding.columnName());
            if (StringUtils.hasText(key)) {
                byColumn.put(key, binding);
            }
        }
        List<SqlModelStandardBinding> merged = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String columnName = objectText(column.get("name"));
            if (!StringUtils.hasText(columnName)) {
                continue;
            }
            SqlModelStandardBinding binding = byColumn.get(normalizeColumnKey(columnName));
            if (binding == null) {
                merged.add(
                    new SqlModelStandardBinding(
                        columnName,
                        null,
                        null,
                        null,
                        null,
                        objectText(column.get("dataType")),
                        null,
                        null,
                        null,
                        null,
                        "missing",
                        "未绑定数据元标准"
                    )
                );
            } else {
                merged.add(
                    new SqlModelStandardBinding(
                        binding.columnName(),
                        binding.standardId(),
                        binding.standardCode(),
                        binding.standardName(),
                        binding.standardVersion(),
                        defaultText(binding.dataType(), objectText(column.get("dataType"))),
                        binding.nullable(),
                        binding.codeSet(),
                        binding.securityLevel(),
                        binding.bindingSource(),
                        binding.status(),
                        binding.driftReason()
                    )
                );
            }
        }
        int mapped = (int) merged.stream().filter(binding -> StringUtils.hasText(binding.standardCode())).count();
        return new SqlModelStandardBindingResult(
            model != null ? model.getId() : null,
            model != null ? model.getName() : null,
            merged.size(),
            mapped,
            Math.max(merged.size() - mapped, 0),
            merged
        );
    }

    private List<SqlModelStandardBinding> readStandardBindings(ModelingSqlModel model) {
        Map<String, Object> root = readSemanticContractObject(model);
        Object dts = root.get("dts");
        if (!(dts instanceof Map<?, ?> dtsMap)) {
            return List.of();
        }
        Object rawBindings = dtsMap.get("standardBindings");
        if (!(rawBindings instanceof List<?>)) {
            return List.of();
        }
        try {
            List<SqlModelStandardBinding> bindings = objectMapper.convertValue(rawBindings, new TypeReference<>() {});
            return bindings.stream().map(this::normalizeStandardBinding).toList();
        } catch (IllegalArgumentException ex) {
            LOG.warn("[sql-model] failed to parse standard bindings for model {}: {}", model != null ? model.getId() : null, ex.getMessage());
            return List.of();
        }
    }

    private void writeStandardBindings(ModelingSqlModel model, List<SqlModelStandardBinding> bindings) {
        Map<String, Object> root = readSemanticContractObject(model);
        Map<String, Object> dts;
        Object existingDts = root.get("dts");
        if (existingDts instanceof Map<?, ?> map) {
            dts = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    dts.put(entry.getKey().toString(), entry.getValue());
                }
            }
        } else {
            dts = new LinkedHashMap<>();
        }
        List<Map<String, Object>> payload = bindings
            .stream()
            .map(binding -> objectMapper.convertValue(binding, new TypeReference<Map<String, Object>>() {}))
            .toList();
        dts.put("standardBindings", payload);
        root.put("dts", dts);
        try {
            applySemanticContract(model, objectMapper.writeValueAsString(root));
        } catch (Exception ex) {
            throw new IllegalArgumentException("字段标准绑定无法写入语义契约");
        }
    }

    private Map<String, Object> readSemanticContractObject(ModelingSqlModel model) {
        String raw = model != null ? trimToNull(model.getSemanticContract()) : null;
        if (!StringUtils.hasText(raw)) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        result.put(entry.getKey().toString(), entry.getValue());
                    }
                }
                return result;
            }
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("legacyContract", parsed);
            return wrapper;
        } catch (Exception ex) {
            throw new IllegalArgumentException("语义契约无法解析，不能写入字段标准绑定");
        }
    }

    private SqlModelStandardBinding normalizeStandardBinding(SqlModelStandardBinding raw) {
        if (raw == null) {
            return new SqlModelStandardBinding(null, null, null, null, null, null, null, null, null, null, "missing", null);
        }
        String columnName = trimToNull(raw.columnName());
        String standardCode = trimToNull(raw.standardCode());
        String status = trimToNull(raw.status());
        if (!StringUtils.hasText(status)) {
            status = StringUtils.hasText(standardCode) ? "active" : "missing";
        }
        String bindingSource = trimToNull(raw.bindingSource());
        if (!StringUtils.hasText(bindingSource) && StringUtils.hasText(standardCode)) {
            bindingSource = "manual";
        }
        return new SqlModelStandardBinding(
            columnName,
            raw.standardId(),
            standardCode,
            trimToNull(raw.standardName()),
            trimToNull(raw.standardVersion()),
            trimToNull(raw.dataType()),
            raw.nullable(),
            normalizeUpperOrNull(raw.codeSet()),
            normalizeUpperOrNull(raw.securityLevel()),
            bindingSource,
            status,
            trimToNull(raw.driftReason())
        );
    }

    private String buildSchemaYml(ModelingSqlModel model, List<SqlModelStandardBinding> bindings) {
        StringBuilder yml = new StringBuilder();
        yml.append("version: 2\n");
        yml.append("models:\n");
        yml.append("  - name: ").append(yamlScalar(defaultText(trimToNull(model.getName()), "model"))).append("\n");
        if (StringUtils.hasText(model.getDescription())) {
            yml.append("    description: ").append(yamlScalar(model.getDescription())).append("\n");
        }
        List<SqlModelStandardBinding> effective = bindings == null ? List.of() : bindings;
        if (effective.isEmpty()) {
            yml.append("    columns: []\n");
            return yml.toString();
        }
        yml.append("    columns:\n");
        for (SqlModelStandardBinding binding : effective) {
            if (!StringUtils.hasText(binding.columnName())) {
                continue;
            }
            yml.append("      - name: ").append(yamlScalar(binding.columnName())).append("\n");
            if (StringUtils.hasText(binding.standardName())) {
                yml.append("        description: ").append(yamlScalar(binding.standardName())).append("\n");
            }
            if (StringUtils.hasText(binding.dataType())) {
                yml.append("        data_type: ").append(yamlScalar(binding.dataType())).append("\n");
            }
            List<String> tests = buildSchemaYmlTests(binding);
            if (!tests.isEmpty()) {
                yml.append("        tests:\n");
                for (String test : tests) {
                    yml.append(test);
                }
            }
            yml.append("        meta:\n");
            yml.append("          dts:\n");
            appendYamlMeta(yml, "standardCode", binding.standardCode());
            appendYamlMeta(yml, "standardVersion", binding.standardVersion());
            appendYamlMeta(yml, "codeSet", binding.codeSet());
            appendYamlMeta(yml, "securityLevel", binding.securityLevel());
            appendYamlMeta(yml, "bindingSource", binding.bindingSource());
            appendYamlMeta(yml, "status", binding.status());
        }
        return yml.toString();
    }

    private List<String> buildSchemaYmlTests(SqlModelStandardBinding binding) {
        List<String> tests = new ArrayList<>();
        if (Boolean.FALSE.equals(binding.nullable())) {
            tests.add("          - not_null\n");
        }
        if (StringUtils.hasText(binding.codeSet())) {
            tests.add("          - relationships:\n");
            tests.add("              to: ref('" + seedName(binding.codeSet()) + "')\n");
            tests.add("              field: code\n");
        }
        return tests;
    }

    private void appendYamlMeta(StringBuilder yml, String key, String value) {
        if (StringUtils.hasText(value)) {
            yml.append("            ").append(key).append(": ").append(yamlScalar(value)).append("\n");
        }
    }

    private String resolveSchemaYmlPath(ModelingSqlModel model) {
        String modelPath = model != null ? trimToNull(model.getModelPath()) : null;
        if (!StringUtils.hasText(modelPath)) {
            return "models/schema.yml";
        }
        int lastSlash = modelPath.lastIndexOf('/');
        return lastSlash >= 0 ? modelPath.substring(0, lastSlash + 1) + "schema.yml" : "schema.yml";
    }

    private String seedName(String codeSet) {
        return "seed_" + slugify(codeSet);
    }

    private String yamlScalar(String value) {
        String text = trimToNull(value);
        if (!StringUtils.hasText(text)) {
            return "\"\"";
        }
        if (text.matches("^[A-Za-z0-9_./:-]+$")) {
            return text;
        }
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String objectText(Object value) {
        if (value == null) {
            return null;
        }
        return trimToNull(value.toString());
    }

    private String normalizeColumnKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private String normalizeUpperOrNull(String value) {
        String text = trimToNull(value);
        return StringUtils.hasText(text) ? text.toUpperCase(Locale.ROOT) : null;
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

    private Optional<CatalogDomain> resolvePlanDomain(ModelingSqlModel model) {
        if (model == null || model.getPlanId() == null || catalogDomainRepository == null) {
            return Optional.empty();
        }
        return planRepo
            .findById(model.getPlanId())
            .map(ModelingPlan::getDomainId)
            .filter(Objects::nonNull)
            .flatMap(catalogDomainRepository::findById);
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
        if (sourceId == null) {
            sourceId = resolveFallbackSourceId(activeDeptHeader);
        }
        if (sourceId == null) {
            if (defaultLakeDatasetGuard != null) {
                throw new IllegalArgumentException("未识别默认数据湖数据源，请先在管理端配置默认数据湖并确认平台已映射本地数据源");
            }
            throw new IllegalArgumentException("来源数据源不存在，请先配置可用数据源");
        }

        ModelingPlan plan = resolvePlan(request.planId(), activeDeptHeader);
        InfraDataSource source = resolveSource(sourceId, activeDeptHeader);
        assertUniqueModelName(plan != null ? plan.getId() : null, name, isCreate ? null : model.getId());

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
        String dagSelector = resolveDagSelectorValue(mergedTags, sourceTag);
        String layer = trimToNull(request.layer());
        if (layer == null) {
            layer = inferLayer(name);
        }
        layer = normalizeLayer(layer);
        if (!StringUtils.hasText(layer)) {
            throw new IllegalArgumentException("请选择模型分层");
        }

        model.setPlanId(plan != null ? plan.getId() : null);
        if (isCreate) {
            model.setModelSpecId(request.modelSpecId());
        } else if (request.modelSpecId() != null && !Objects.equals(model.getModelSpecId(), request.modelSpecId())) {
            throw new IllegalArgumentException("已绑定的 ModelSpec 不允许通过普通编辑切换");
        }
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
        if (isCreate || request.semanticContract() != null) {
            applySemanticContract(model, request.semanticContract());
        }
    }

    private void assertUniqueModelName(UUID planId, String modelName, UUID currentModelId) {
        String normalizedName = trimToNull(modelName);
        if (planId == null || normalizedName == null) {
            return;
        }
        repo.findFirstByPlanIdAndNameIgnoreCase(planId, normalizedName)
            .filter(existing -> currentModelId == null || !Objects.equals(existing.getId(), currentModelId))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("同一项目空间下模型名已存在: " + normalizedName);
            });
    }

    private void syncCodeAssetGrant(ModelingSqlModel model) {
        if (codeAssetGrantWriter == null || model == null || model.getId() == null) {
            return;
        }
        String naturalKey = StringUtils.hasText(model.getName()) ? model.getName().trim() : model.getId().toString();
        CatalogAssetIdentity identity = new CatalogAssetIdentity(
            CatalogAssetType.MODELING_SQL_MODEL,
            CatalogAssetKey.codeAsset(CatalogAssetType.MODELING_SQL_MODEL, "default", naturalKey),
            model.getId().toString(),
            "modeling-sql-model:" + naturalKey
        );
        codeAssetGrantWriter.upsertCodeAsset(
            identity,
            model.getOwnerDept(),
            "dts-platform",
            null,
            CodeAssetLifecycleMapper.fromModelingSqlModelStatus(model.getStatus(), model.getEnabled())
        );
    }

    private Optional<InfraDataSource> resolveSourceEntity(UUID sourceId) {
        if (sourceId == null) {
            return Optional.empty();
        }
        InfraDataSource local = dataSourceRepository.findById(sourceId).orElse(null);
        if (local != null) {
            return Optional.of(local);
        }
        return adminInfraClient.fetchDefaultDataLake().filter(lake -> sourceId.equals(lake.getId())).map(ModelingSqlModelService::toVirtualSource);
    }

    private static String buildVirtualSourcePropsStatic(AdminInfraClient.AdminDataLakeConfig lake) {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("source", SOURCE_ADMIN_DATA_LAKE);
        props.put("sourceSystem", SOURCE_ADMIN_DATA_LAKE);
        props.put("defaulted", Boolean.TRUE.equals(lake.getDefaulted()));
        if (lake.getJdbcProperties() != null && !lake.getJdbcProperties().isEmpty()) {
            props.putAll(lake.getJdbcProperties());
        }
        try {
            return new ObjectMapper().writeValueAsString(props);
        } catch (Exception ex) {
            LOG.warn("[dbt-model] failed to serialize admin data lake props: {}", ex.getMessage());
            return "{\"source\":\"" + SOURCE_ADMIN_DATA_LAKE + "\",\"sourceSystem\":\"" + SOURCE_ADMIN_DATA_LAKE + "\"}";
        }
    }

    private UUID resolveFallbackSourceId(String activeDeptHeader) {
        if (defaultLakeDatasetGuard != null) {
            return defaultLakeDatasetGuard.currentDefaultLakeSourceId().orElse(null);
        }
        List<InfraDataSource> candidates = dataSourceRepository.findByStatusIgnoreCase(STATUS_ACTIVE);
        if (candidates == null || candidates.isEmpty()) {
            candidates = dataSourceRepository.findAll();
        }
        List<InfraDataSource> ranked = new ArrayList<>();
        if (candidates != null) {
            ranked.addAll(candidates);
        }
        UUID bestId = null;
        int bestScore = Integer.MIN_VALUE;
        for (InfraDataSource source : ranked) {
            if (source == null || source.getId() == null) {
                continue;
            }
            UUID usable = resolveUsableSourceId(source.getId(), activeDeptHeader);
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

    private UUID resolveUsableSourceId(UUID sourceId, String activeDeptHeader) {
        if (sourceId == null) return null;
        try {
            resolveSource(sourceId, activeDeptHeader);
            return sourceId;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void applySemanticContract(ModelingSqlModel model, String rawSemanticContract) {
        String normalized = normalizeSemanticContract(rawSemanticContract);
        if (!StringUtils.hasText(normalized)) {
            model.setSemanticContract(null);
            model.setContractVersion(null);
            model.setContractUpdatedAt(null);
            return;
        }
        boolean changed = !normalized.equals(trimToNull(model.getSemanticContract()));
        model.setSemanticContract(normalized);
        if (changed || !StringUtils.hasText(model.getContractVersion())) {
            model.setContractVersion(computeContractVersion(normalized));
            model.setContractUpdatedAt(Instant.now());
        }
    }

    private String normalizeSemanticContract(String rawSemanticContract) {
        String text = trimToNull(rawSemanticContract);
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return objectMapper
                .copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(parsed);
        } catch (Exception ex) {
            throw new IllegalArgumentException("语义契约必须是合法 JSON 对象或数组");
        }
    }

    private String computeContractVersion(String normalizedContract) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedContract.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hash);
            return "sc-" + hex.substring(0, 16);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算契约版本", ex);
        }
    }

    private ContractMeta extractContractMeta(String semanticContract) {
        String normalized = trimToNull(semanticContract);
        if (!StringUtils.hasText(normalized)) {
            return new ContractMeta(0, 0);
        }
        try {
            Object parsed = objectMapper.readValue(normalized, Object.class);
            if (!(parsed instanceof Map<?, ?> map)) {
                return new ContractMeta(0, 0);
            }
            int metricCount = countContractNode(map.get("metrics"));
            int dimensionCount = countContractNode(map.get("dimensions"));
            return new ContractMeta(metricCount, dimensionCount);
        } catch (Exception ignored) {
            return new ContractMeta(0, 0);
        }
    }

    private int countContractNode(Object value) {
        if (value == null) return 0;
        if (value instanceof List<?> list) return list.size();
        if (value instanceof Map<?, ?> map) return map.size();
        return 0;
    }

    private boolean referencesModel(String sqlText, String modelName, String modelAlias) {
        String sql = trimToEmpty(sqlText).toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(sql)) return false;
        String name = trimToNull(modelName);
        String alias = trimToNull(modelAlias);
        if (StringUtils.hasText(name)) {
            String needle = name.toLowerCase(Locale.ROOT);
            if (sql.contains("ref('" + needle + "')") || sql.contains("ref(\"" + needle + "\")") || sql.contains(" " + needle + " ") || sql.contains("." + needle)) return true;
        }
        if (StringUtils.hasText(alias)) {
            String needle = alias.toLowerCase(Locale.ROOT);
            if (sql.contains("ref('" + needle + "')") || sql.contains("ref(\"" + needle + "\")") || sql.contains(" " + needle + " ") || sql.contains("." + needle)) return true;
        }
        return false;
    }

    private List<SqlModelDto> toDtoBatch(Collection<ModelingSqlModel> models) {
        if (models.isEmpty()) return List.of();
        Set<UUID> planIds = models.stream().map(ModelingSqlModel::getPlanId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ModelingPlan> planMap = planIds.isEmpty() ? Map.of()
                : planRepo.findAllById(planIds).stream().collect(Collectors.toMap(ModelingPlan::getId, p -> p, (a, b) -> a));
        Set<UUID> sourceIds = models.stream().map(ModelingSqlModel::getSourceDataSourceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, InfraDataSource> sourceMap = sourceIds.isEmpty() ? Map.of()
                : sourceIds.stream().map(this::resolveSourceEntity).filter(Optional::isPresent).map(Optional::get)
                    .collect(Collectors.toMap(InfraDataSource::getId, s -> s, (a, b) -> a));
        return models.stream().map(model -> toDtoWithCache(model, planMap, sourceMap)).toList();
    }

    private SqlModelDto toDtoWithCache(ModelingSqlModel model, Map<UUID, ModelingPlan> planMap, Map<UUID, InfraDataSource> sourceMap) {
        if (model == null) return null;
        ModelingPlan plan = model.getPlanId() == null ? null : planMap.get(model.getPlanId());
        InfraDataSource source = model.getSourceDataSourceId() == null ? null : sourceMap.get(model.getSourceDataSourceId());
        String sourceKey = source != null ? resolveSourceKey(source) : null;
        String sourceTag = sanitizeTag(sourceKey, slugify(sourceKey));
        String dagSelector = defaultText(trimToNull(model.getDagSelector()), resolveDagSelectorValue(model.getTags(), sourceTag));
        ContractMeta meta = extractContractMeta(model.getSemanticContract());
        return new SqlModelDto(
            model.getId(), model.getPlanId(), model.getModelSpecId(), plan != null ? plan.getName() : null,
            model.getName(), model.getAlias(), model.getLayer(), model.getSourceDataSourceId(),
            source != null ? source.getName() : null, sourceKey, dagSelector, model.getTags(),
            model.getMaterialized(), model.getSchemaName(), model.getDescription(), model.getSqlText(),
            model.getEnabled(), model.getModelPath(), model.getOwnerDept(), model.getStatus(),
            model.getSemanticContract(), model.getContractVersion(), model.getContractUpdatedAt(),
            meta.metricCount(), meta.dimensionCount(), model.getCreatedDate(), model.getLastModifiedDate()
        );
    }

    private SqlModelDto toDto(ModelingSqlModel model) {
        if (model == null) return null;
        ModelingPlan plan = model.getPlanId() == null ? null : planRepo.findById(model.getPlanId()).orElse(null);
        InfraDataSource source = model.getSourceDataSourceId() == null ? null : resolveSourceEntity(model.getSourceDataSourceId()).orElse(null);
        String sourceKey = source != null ? resolveSourceKey(source) : null;
        String sourceTag = sanitizeTag(sourceKey, slugify(sourceKey));
        String dagSelector = defaultText(trimToNull(model.getDagSelector()), resolveDagSelectorValue(model.getTags(), sourceTag));
        ContractMeta meta = extractContractMeta(model.getSemanticContract());
        return new SqlModelDto(
            model.getId(), model.getPlanId(), model.getModelSpecId(), plan != null ? plan.getName() : null,
            model.getName(), model.getAlias(), model.getLayer(), model.getSourceDataSourceId(),
            source != null ? source.getName() : null, sourceKey, dagSelector, model.getTags(),
            model.getMaterialized(), model.getSchemaName(), model.getDescription(), model.getSqlText(),
            model.getEnabled(), model.getModelPath(), model.getOwnerDept(), model.getStatus(),
            model.getSemanticContract(), model.getContractVersion(), model.getContractUpdatedAt(),
            meta.metricCount(), meta.dimensionCount(), model.getCreatedDate(), model.getLastModifiedDate()
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

    private Map<String, Object> toColumnPayload(CatalogColumnSchema column) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (column == null) return item;
        item.put("name", column.getName());
        item.put("dataType", column.getDataType());
        item.put("comment", column.getComment());
        item.put("status", column.getStatus());
        return item;
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

    private static Map<String, Object> parsePropsStatic(String raw) {
        if (!StringUtils.hasText(raw)) return Map.of();
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) {
            try {
                return new ObjectMapper().readValue(trimmed, new TypeReference<>() {});
            } catch (Exception ignored) {}
        }
        return Map.of();
    }

    private static String firstTextStatic(Map<String, Object> props, String... keys) {
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

    private String resolveLayerDir(String layer) {
        String normalized = normalizeLayer(layer);
        if ("ODS".equals(normalized)) return "ods";
        if ("STG".equals(normalized)) return "stg";
        if ("DWD".equals(normalized)) return "dwd";
        if ("DWS".equals(normalized)) return "dws";
        if ("ADS".equals(normalized)) return "ads";
        return "dwh";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    void scheduleFileDeletionAfterCommit(String modelPath) {
        String normalizedPath = trimToNull(modelPath);
        if (normalizedPath == null) {
            return;
        }
        Runnable cleanup = () -> {
            try {
                fileService.deleteFileIfChanged(normalizedPath, null);
            } catch (RuntimeException ex) {
                LOG.warn("[sql-model] failed to cleanup deleted model file {}: {}", normalizedPath, ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitFileCleanup(normalizedPath, cleanup);
                }
            });
            return;
        }
        submitFileCleanup(normalizedPath, cleanup);
    }

    private void submitFileCleanup(String modelPath, Runnable cleanup) {
        try {
            taskExecutor.execute(cleanup);
        } catch (RuntimeException ex) {
            LOG.warn("[sql-model] failed to submit cleanup for deleted model file {}: {}", modelPath, ex.getMessage());
        }
    }

    // ── Public DTO records ─────────────────────────────────────────────

    private record ContractMeta(int metricCount, int dimensionCount) {}

    public record SqlModelRequest(
        UUID planId,
        UUID modelSpecId,
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
        String ownerDept,
        String semanticContract
    ) {
        public SqlModelRequest(
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
            String ownerDept,
            String semanticContract
        ) {
            this(
                planId,
                null,
                name,
                alias,
                layer,
                sourceDataSourceId,
                schemaName,
                materialized,
                tags,
                description,
                sql,
                enabled,
                status,
                ownerDept,
                semanticContract
            );
        }
    }

    public record SqlModelDto(
        UUID id,
        UUID planId,
        UUID modelSpecId,
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
        String semanticContract,
        String contractVersion,
        Instant contractUpdatedAt,
        int metricCount,
        int dimensionCount,
        Instant createdDate,
        Instant lastModifiedDate
    ) {}

    public record SqlModelContractImpact(
        UUID modelId,
        String modelName,
        String contractVersion,
        Instant contractUpdatedAt,
        int metricCount,
        int dimensionCount,
        int fieldCount,
        int impactedDatasetCount,
        int impactedReportCount,
        List<QueryDatasetImpactItem> impactedDatasets,
        List<ReportImpactItem> impactedReports
    ) {}

    public record QueryDatasetImpactItem(
        UUID id,
        String name,
        String status,
        Integer publishedVersion
    ) {}

    public record ReportImpactItem(
        UUID id,
        String title,
        String code,
        UUID queryDatasetId,
        boolean enabled
    ) {}

    public record SqlModelStandardBinding(
        String columnName,
        UUID standardId,
        String standardCode,
        String standardName,
        String standardVersion,
        String dataType,
        Boolean nullable,
        String codeSet,
        String securityLevel,
        String bindingSource,
        String status,
        String driftReason
    ) {}

    public record SqlModelStandardBindingRequest(List<SqlModelStandardBinding> bindings) {}

    public record SqlModelStandardBindingResult(
        UUID modelId,
        String modelName,
        int totalColumns,
        int mappedColumns,
        int missingColumns,
        List<SqlModelStandardBinding> bindings
    ) {}

    public record SqlModelSchemaYmlResult(UUID modelId, String modelName, String path, String schemaYml) {}

    public record SqlModelStandardGateResult(
        UUID modelId,
        String modelName,
        boolean blocking,
        List<String> blockers,
        List<String> warnings,
        int totalColumns,
        int mappedColumns,
        int missingColumns
    ) {}

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
        List<String> skipped,
        int qualityTemplatesGenerated,
        List<String> qualitySkipped
    ) {}

    public record BatchImportResult(
        int total,
        int imported,
        int skipped,
        int failed,
        List<BatchImportDetail> details
    ) {}

    public record BatchImportDetail(
        String name,
        String layer,
        String status,
        String message
    ) {}

    public record SqlModelGovernancePreviewRequest(
        UUID planId,
        List<String> ruleKeys,
        List<String> sqlKeywords,
        String namePattern,
        String modelPathPattern,
        String tag,
        String layer
    ) {}

    public record SqlModelGovernancePreviewResult(int total, List<SqlModelGovernancePreviewItem> items) {}

    public record SqlModelGovernancePreviewItem(
        UUID modelId,
        UUID planId,
        String planName,
        String name,
        String layer,
        String status,
        String modelPath,
        List<String> ruleHits,
        int downstreamRefCount,
        int datasetBindingCount,
        int reportBindingCount,
        boolean fileDeleteSafe,
        String suggestedAction
    ) {}

    public record SqlModelGovernanceExecuteRequest(List<UUID> modelIds, Boolean deleteFiles) {}

    public record SqlModelGovernanceExecuteResult(
        int requested,
        int deleted,
        int skipped,
        int failed,
        List<SqlModelGovernanceExecuteItem> items
    ) {}

    public record SqlModelGovernanceExecuteItem(UUID modelId, String name, String result, String message) {}
}
