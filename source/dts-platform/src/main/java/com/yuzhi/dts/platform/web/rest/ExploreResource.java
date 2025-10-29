package com.yuzhi.dts.platform.web.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.explore.ExecEnums;
import com.yuzhi.dts.platform.domain.explore.ExploreSavedQuery;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.domain.explore.ResultSet.StorageFormat;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.explore.ExploreSavedQueryRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.explore.dto.CreateSavedQueryRequest;
import com.yuzhi.dts.platform.service.explore.dto.UpdateSavedQueryRequest;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.AccessChecker;
import com.yuzhi.dts.platform.service.security.DatasetSqlBuilder;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import com.yuzhi.dts.platform.service.security.SecurityGuardException;
import com.yuzhi.dts.platform.service.security.SecuritySqlRewriter;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.validation.Valid;
import java.lang.reflect.Array;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/explore")
@Transactional
@Validated
public class ExploreResource {

    private static final int HISTORY_LIMIT = 50;
    private static final int PREVIEW_ROW_LIMIT = 50;

    private static final Logger LOG = LoggerFactory.getLogger(ExploreResource.class);

    private final ExploreSavedQueryRepository savedRepo;
    private final QueryExecutionRepository executionRepo;
    private final ResultSetRepository resultSetRepo;
    private final CatalogDatasetRepository datasetRepo;
    private final AccessChecker accessChecker;
    private final AuditService audit;
    private final ObjectMapper objectMapper;
    private final QueryGateway queryGateway;
    private final DatasetSqlBuilder datasetSqlBuilder;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final SecuritySqlRewriter securitySqlRewriter;

    public ExploreResource(
        ExploreSavedQueryRepository savedRepo,
        QueryExecutionRepository executionRepo,
        ResultSetRepository resultSetRepo,
        CatalogDatasetRepository datasetRepo,
        AccessChecker accessChecker,
        AuditService audit,
        ObjectMapper objectMapper,
        QueryGateway queryGateway,
        DatasetSqlBuilder datasetSqlBuilder,
        DatasetSecurityMetadataResolver metadataResolver,
        SecuritySqlRewriter securitySqlRewriter
    ) {
        this.savedRepo = savedRepo;
        this.executionRepo = executionRepo;
        this.resultSetRepo = resultSetRepo;
        this.datasetRepo = datasetRepo;
        this.accessChecker = accessChecker;
        this.audit = audit;
        this.objectMapper = objectMapper;
        this.queryGateway = queryGateway;
        this.datasetSqlBuilder = datasetSqlBuilder;
        this.metadataResolver = metadataResolver;
        this.securitySqlRewriter = securitySqlRewriter;
    }

    @PostMapping("/query/preview")
    public ApiResponse<Map<String, Object>> preview(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        String datasetLabel = datasetName(dataset, body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            recordAudit(
                "DENY",
                "explore.preview",
                Objects.toString(body.get("datasetId"), "unknown"),
                "预览数据集被拒绝",
                datasetLabel,
                "FAILED"
            );
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        if (dataset != null) {
            String effDept = resolveActiveDeptContext(activeDept);
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                recordAudit(
                    "DENY",
                    "explore.preview",
                    datasetIdentifier(dataset, body.get("datasetId")),
                    "预览数据集被拒绝：" + safeLabel(datasetLabel),
                    datasetLabel,
                    "FAILED"
                );
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        try {
            Map<String, Object> payload = generateResult(dataset, extractSql(body), false);
            recordAudit(
                "EXECUTE",
                "explore.preview",
                datasetIdentifier(dataset, body.get("datasetId")),
                "预览数据集：" + safeLabel(datasetLabel),
                datasetLabel
            );
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Explore preview denied: {}", ex.getMessage());
            recordAudit(
                "DENY",
                "explore.preview",
                datasetIdentifier(dataset, body.get("datasetId")),
                "预览数据集被拒绝：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Explore preview failed: {}", ex.getMessage());
            recordAudit(
                "ERROR",
                "explore.preview",
                datasetIdentifier(dataset, body.get("datasetId")),
                "预览数据集失败：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Explore preview unexpected failure", ex);
            recordAudit(
                "ERROR",
                "explore.preview",
                datasetIdentifier(dataset, body.get("datasetId")),
                "预览数据集失败：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error("查询预览失败: " + ex.getMessage());
        }
    }

    @PostMapping("/execute")
    public ApiResponse<Map<String, Object>> execute(
        @RequestBody Map<String, Object> body,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        CatalogDataset dataset = resolveDataset(body.get("datasetId"));
        String datasetLabel = datasetName(dataset, body.get("datasetId"));
        if (body.get("datasetId") != null && dataset == null) {
            recordAudit(
                "DENY",
                "explore.execute",
                Objects.toString(body.get("datasetId"), "unknown"),
                "执行数据查询被拒绝",
                datasetLabel,
                "FAILED"
            );
            return ApiResponses.error(com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RESOURCE_NOT_VISIBLE, "Access denied for dataset");
        }
        if (dataset != null) {
            String effDept = resolveActiveDeptContext(activeDept);
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                recordAudit(
                    "DENY",
                    "explore.execute",
                    datasetIdentifier(dataset, body.get("datasetId")),
                    "执行数据查询被拒绝：" + safeLabel(datasetLabel),
                    datasetLabel,
                    "FAILED"
                );
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        try {
            Map<String, Object> payload = generateResult(dataset, extractSql(body), true);
            recordAudit(
                "EXECUTE",
                "explore.execute",
                datasetIdentifier(dataset, body.get("datasetId")),
                "执行数据查询：" + safeLabel(datasetLabel),
                datasetLabel
            );
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Explore execute denied: {}", ex.getMessage());
            recordAudit(
                "DENY",
                "explore.execute",
                datasetIdentifier(dataset, body.get("datasetId")),
                "执行数据查询被拒绝：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Explore execute failed: {}", ex.getMessage());
            recordAudit(
                "ERROR",
                "explore.execute",
                datasetIdentifier(dataset, body.get("datasetId")),
                "执行数据查询失败：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Explore execute unexpected failure", ex);
            recordAudit(
                "ERROR",
                "explore.execute",
                datasetIdentifier(dataset, body.get("datasetId")),
                "执行数据查询失败：" + safeLabel(datasetLabel),
                datasetLabel,
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error("查询执行失败: " + ex.getMessage());
        }
    }

    @PostMapping("/explain")
    public ApiResponse<Map<String, Object>> explain(@RequestBody Map<String, Object> body) {
        String sql = extractSql(body);
        List<String> steps = buildExplainSteps(sql);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("effectiveSql", sql.isBlank() ? "SELECT 1" : sql);
        resp.put("steps", steps);
        double cost = ThreadLocalRandom.current().nextDouble(1.5, 15.0);
        resp.put("estimatedCost", String.format(Locale.ROOT, "%.2f", cost));
        recordAudit(
            "READ",
            "explore.explain",
            null,
            "查看查询执行计划",
            null,
            "SUCCESS",
            Map.of("stepCount", steps.size())
        );
        return ApiResponses.ok(resp);
    }

    @PostMapping("/save-result/{executionId}")
    public ApiResponse<Map<String, Object>> saveResult(
        @PathVariable UUID executionId,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        return resultSetRepo
            .findById(executionId)
            .map(record -> {
                int ttlDays = 7;
                if (body != null && body.get("ttlDays") != null) {
                    try {
                        ttlDays = Math.max(1, Integer.parseInt(String.valueOf(body.get("ttlDays"))));
                    } catch (NumberFormatException ignored) {}
                }
                String requestedName = body != null ? trimToLength(asText(body.get("name")), 128) : null;
                if (requestedName != null && !requestedName.isBlank()) {
                    record.setName(requestedName);
                } else if (record.getName() == null || record.getName().isBlank()) {
                    record.setName("临时结果集");
                }
                Instant now = Instant.now();
                record.setTtlDays(ttlDays);
                record.setExpiresAt(now.plus(ttlDays, ChronoUnit.DAYS));
                record.setStorageUri("oss://datalake/explore/" + executionId + ".parquet");
                resultSetRepo.save(record);
                recordAudit(
                    "UPDATE",
                    "explore.saveResult",
                    executionId.toString(),
                    "保存查询结果集：" + safeLabel(record.getName()),
                    record.getName()
                );
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("id", executionId);
                resp.put("name", record.getName());
                resp.put("expiresAt", Optional.ofNullable(record.getExpiresAt()).map(Instant::toString).orElse(null));
                resp.put("storageUri", record.getStorageUri());
                return ApiResponses.ok(resp);
            })
            .orElseGet(() -> ApiResponses.error("Result set not found"));
    }

    @GetMapping("/query-executions")
    public ApiResponse<List<Map<String, Object>>> listExecutions(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<QueryExecution> executions = executionRepo
            .findAll(PageRequest.of(0, HISTORY_LIMIT, Sort.by(Sort.Direction.DESC, "startedAt", "createdDate")))
            .getContent();

        Set<UUID> datasetIds = executions
            .stream()
            .map(QueryExecution::getDatasetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String effDept = resolveActiveDeptContext(activeDept);
        Map<UUID, CatalogDataset> datasets = datasetRepo
            .findAllById(datasetIds)
            .stream()
            .filter(ds -> datasetWithinScope(ds, effDept))
            .collect(Collectors.toMap(CatalogDataset::getId, it -> it));

        List<Map<String, Object>> list = executions
            .stream()
            .filter(exec -> exec.getDatasetId() == null || datasets.containsKey(exec.getDatasetId()))
            .map(exec -> toExecutionMap(exec, datasets.get(exec.getDatasetId())))
            .collect(Collectors.toList());
        recordAudit(
            "READ",
            "explore.execution",
            null,
            "查看查询任务历史",
            null,
            "SUCCESS",
            Map.of("count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @GetMapping("/result-sets")
    public ApiResponse<List<Map<String, Object>>> listResultSets(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        String currentUser = SecurityUtils
            .getCurrentUserLogin()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .orElse(null);
        if (!StringUtils.hasText(currentUser)) {
            return ApiResponses.ok(List.of());
        }
        List<ResultSet> records = resultSetRepo.findByCreatedByOrderByCreatedDateDesc(currentUser);

        List<ResultSet> filtered = records
            .stream()
            .filter(rs -> rs.getTtlDays() != null)
            .collect(Collectors.toList());
        Map<UUID, QueryExecution> execByResult = new LinkedHashMap<>();
        Set<UUID> datasetIds = new LinkedHashSet<>();
        for (ResultSet rs : filtered) {
            List<QueryExecution> executions = executionRepo.findByResultSetId(rs.getId());
            QueryExecution exec = executions.isEmpty() ? null : executions.get(0);
            if (exec != null) {
                execByResult.put(rs.getId(), exec);
                if (exec.getDatasetId() != null) {
                    datasetIds.add(exec.getDatasetId());
                }
            }
        }

        String effDept = resolveActiveDeptContext(activeDept);
        Map<UUID, CatalogDataset> datasets = datasetRepo
            .findAllById(datasetIds)
            .stream()
            .filter(ds -> datasetWithinScope(ds, effDept))
            .collect(Collectors.toMap(CatalogDataset::getId, it -> it));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ResultSet rs : filtered) {
            QueryExecution exec = execByResult.get(rs.getId());
            CatalogDataset dataset = exec != null ? datasets.get(exec.getDatasetId()) : null;
            if (exec != null && exec.getDatasetId() != null && dataset == null) {
                // Underlying dataset no longer visible to current user
                continue;
            }
            list.add(toResultSetMap(rs, exec, dataset));
        }
        recordAudit(
            "READ",
            "explore.resultSet",
            null,
            "查看查询结果集列表",
            null,
            "SUCCESS",
            Map.of("count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @GetMapping("/result-preview/{id}")
    public ApiResponse<Map<String, Object>> previewResultSet(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return resultSetRepo
            .findById(id)
            .map(record -> {
                assertResultSetAccess(record);
                QueryExecution execution = executionRepo
                    .findByResultSetId(record.getId())
                    .stream()
                    .findFirst()
                    .orElse(null);
                if (execution == null) {
                    recordAudit(
                        "WARN",
                        "explore.result.sql",
                        id.toString(),
                        "查询结果缺少执行记录",
                        null,
                        "FAILED",
                        Map.of("resultSetId", id.toString())
                    );
                    return ApiResponses.<Map<String, Object>>error("未找到结果集关联的查询记录");
                }
                CatalogDataset dataset = execution.getDatasetId() != null
                    ? datasetRepo.findById(execution.getDatasetId()).orElse(null)
                    : null;
                String datasetLabel = datasetName(dataset, execution.getDatasetId());
                String effDept = resolveActiveDeptContext(activeDept);
                if (dataset != null && !datasetWithinScope(dataset, effDept)) {
                    recordAudit(
                        "DENY",
                        "explore.result.sql",
                        id.toString(),
                        "无权查看查询结果：" + safeLabel(datasetLabel),
                        datasetLabel,
                        "FAILED"
                    );
                    return ApiResponses.<Map<String, Object>>error(
                        com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                        "Access denied for result set dataset"
                    );
                }
                Map<String, Object> resp = new LinkedHashMap<>();
                resp.put("sqlText", execution.getSqlText());
                resp.put("datasetId", execution.getDatasetId());
                resp.put("datasetName", dataset != null ? dataset.getName() : null);
                resp.put("rowCount", record.getRowCount());
                resp.put("engine", execution.getEngine() != null ? execution.getEngine().name() : null);
                resp.put("finishedAt", execution.getFinishedAt());
                resp.put("limitApplied", execution.getLimitApplied());
                recordAudit(
                    "READ",
                    "explore.result.sql",
                    id.toString(),
                    "查看查询语句：" + safeLabel(datasetLabel),
                    datasetLabel
                );
                return ApiResponses.ok(resp);
            })
            .orElseGet(() -> ApiResponses.<Map<String, Object>>error("Result set not found"));
    }

    @DeleteMapping("/result-sets/{id}")
    public ApiResponse<Boolean> deleteResultSet(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return resultSetRepo
            .findById(id)
            .map(rs -> {
                assertResultSetAccess(rs);
                String effDept = resolveActiveDeptContext(activeDept);
                List<QueryExecution> executions = executionRepo.findByResultSetId(id);
                CatalogDataset dataset = executions
                    .stream()
                    .map(QueryExecution::getDatasetId)
                    .filter(Objects::nonNull)
                    .map(datasetRepo::findById)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst()
                    .orElse(null);
                if (dataset != null && !datasetWithinScope(dataset, effDept)) {
                    String datasetLabel = datasetName(dataset, id);
                    recordAudit(
                        "DENY",
                        "explore.result.delete",
                        id.toString(),
                        "删除查询结果集被拒绝：" + safeLabel(datasetLabel),
                        datasetLabel,
                        "FAILED"
                    );
                    return ApiResponses.<Boolean>error(
                        com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                        "Access denied for result set dataset"
                    );
                }
                resultSetRepo.deleteById(id);
                executionRepo.clearResultSetReferences(id);
                String resultLabel = rs.getName();
                String summary = "删除查询结果集：" + safeLabel(resultLabel != null ? resultLabel : dataset != null ? dataset.getName() : null);
                recordAudit(
                    "DELETE",
                    "explore.result.delete",
                    id.toString(),
                    summary,
                    resultLabel
                );
                return ApiResponses.ok(Boolean.TRUE);
            })
            .orElseGet(() -> ApiResponses.ok(Boolean.TRUE));
    }

    @PostMapping("/result-sets/cleanup")
    public ApiResponse<Map<String, Object>> cleanupResultSets() {
        if (!canManageAllResultSets()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅运维管理员可以批量清理结果集");
        }
        Instant now = Instant.now();
        List<ResultSet> expired = resultSetRepo
            .findByExpiresAtBefore(now)
            .stream()
            .collect(Collectors.toList());
        expired.forEach(rs -> {
            resultSetRepo.delete(rs);
            executionRepo.clearResultSetReferences(rs.getId());
        });
        String summary = expired.isEmpty()
            ? "清理过期查询结果集：无过期记录"
            : "清理过期查询结果集：删除 " + expired.size() + " 条结果集";
        Map<String, Object> auditPayload = new LinkedHashMap<>();
        auditPayload.put("summary", summary);
        auditPayload.put("operationName", summary);
        auditPayload.put("operationType", "CLEAN");
        auditPayload.put("operationTypeCode", "CLEAN");
        auditPayload.put("operationCode", "EXPLORE_RESULTSET_PURGE");
        SecurityUtils
            .getCurrentUserLogin()
            .ifPresent(login -> {
                auditPayload.putIfAbsent("actor", login);
                auditPayload.putIfAbsent("username", login);
            });
        SecurityUtils
            .getCurrentUserDisplayName()
            .ifPresent(name -> auditPayload.putIfAbsent("actorName", name));
        auditPayload.put("deleted", expired.size());
        audit.auditAction("EXPLORE_RESULTSET_PURGE", AuditStage.SUCCESS, null, auditPayload);
        return ApiResponses.ok(Map.of("deleted", expired.size()));
    }

    @GetMapping("/saved-queries")
    public ApiResponse<List<Map<String, Object>>> listSaved(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        List<ExploreSavedQuery> saved = savedRepo.findAll();
        Set<UUID> datasetIds = saved
            .stream()
            .map(ExploreSavedQuery::getDatasetId)
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        String effDept = resolveActiveDeptContext(activeDept);
        Map<UUID, CatalogDataset> datasets = datasetRepo
            .findAllById(datasetIds)
            .stream()
            .filter(ds -> datasetWithinScope(ds, effDept))
            .collect(Collectors.toMap(CatalogDataset::getId, it -> it));
        var list = saved
            .stream()
            .filter(it -> it.getDatasetId() == null || datasets.containsKey(it.getDatasetId()))
            .map(
                it -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", it.getId());
                    m.put("name", it.getName());
                    m.put("datasetId", it.getDatasetId());
                    m.put("sqlText", it.getSqlText());
                    return m;
                }
            )
            .collect(Collectors.toList());
        recordAudit(
            "READ",
            "explore.savedQuery",
            null,
            "查看保存查询列表",
            null,
            "SUCCESS",
            Map.of("count", list.size())
        );
        return ApiResponses.ok(list);
    }

    @PostMapping("/saved-queries")
    public ApiResponse<ExploreSavedQuery> createSaved(
        @Valid @RequestBody CreateSavedQueryRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        UUID datasetId = null;
        String datasetLabel = null;
        if (StringUtils.hasText(request.getDatasetId())) {
            var datasetCheck = validateDatasetAssociation(request.getDatasetId().trim(), activeDept);
            if (!datasetCheck.success()) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery",
                    datasetCheck.auditHint(),
                    "新建保存查询失败：数据集不可用",
                    request.getName(),
                    "FAILED"
                );
                return ApiResponses.error(datasetCheck.message());
            }
            datasetId = datasetCheck.dataset() != null ? datasetCheck.dataset().getId() : null;
            datasetLabel = datasetCheck.dataset() != null ? datasetCheck.dataset().getName() : null;
        }

        ExploreSavedQuery entity = new ExploreSavedQuery();
        entity.setName(trimToLength(request.getName(), 128));
        entity.setSqlText(trimToLength(request.getSqlText(), 4096));
        entity.setDatasetId(datasetId);

        ExploreSavedQuery saved = savedRepo.save(entity);
        recordAudit(
            "CREATE",
            "explore.savedQuery",
            saved.getId().toString(),
            "新建保存查询：" + safeLabel(saved.getName()),
            saved.getName(),
            "SUCCESS",
            datasetLabel != null ? Map.of("dataset", datasetLabel) : null
        );
        return ApiResponses.ok(saved);
    }

    @DeleteMapping("/saved-queries/{id}")
    public ApiResponse<Boolean> deleteSaved(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ExploreSavedQuery existing = savedRepo.findById(id).orElse(null);
        if (existing != null && existing.getDatasetId() != null) {
            CatalogDataset dataset = datasetRepo.findById(existing.getDatasetId()).orElse(null);
            if (!datasetWithinScope(dataset, resolveActiveDeptContext(activeDept))) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery",
                    id.toString(),
                    "删除保存查询被拒绝：" + safeLabel(existing.getName()),
                    existing.getName(),
                    "FAILED"
                );
                return ApiResponses.error(
                    com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                    "Access denied for dataset"
                );
            }
        }
        savedRepo.deleteById(id);
        recordAudit(
            "DELETE",
            "explore.savedQuery",
            id.toString(),
            "删除保存查询：" + safeLabel(existing != null ? existing.getName() : null),
            existing != null ? existing.getName() : null
        );
        return ApiResponses.ok(Boolean.TRUE);
    }

    @PutMapping("/saved-queries/{id}")
    public ApiResponse<ExploreSavedQuery> updateSaved(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateSavedQueryRequest request,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        ExploreSavedQuery existing = savedRepo.findById(id).orElse(null);
        if (existing == null) {
            return ApiResponses.error("Saved query not found");
        }
        if (existing.getDatasetId() != null) {
            CatalogDataset original = datasetRepo.findById(existing.getDatasetId()).orElse(null);
            if (!datasetWithinScope(original, resolveActiveDeptContext(activeDept))) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery",
                    id.toString(),
                    "更新保存查询被拒绝：" + safeLabel(existing.getName()),
                    existing.getName(),
                    "FAILED"
                );
                return ApiResponses.error(
                    com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                    "Access denied for dataset"
                );
            }
        }

        boolean mutated = false;
        if (request.getName() != null) {
            String name = trimToLength(request.getName(), 128);
            if (!StringUtils.hasText(name)) {
                return ApiResponses.error("name cannot be blank");
            }
            existing.setName(name);
            mutated = true;
        }
        if (request.getSqlText() != null) {
            String sqlText = trimToLength(request.getSqlText(), 4096);
            if (!StringUtils.hasText(sqlText)) {
                return ApiResponses.error("sqlText cannot be blank");
            }
            existing.setSqlText(sqlText);
            mutated = true;
        }
        if (request.isDatasetIdPresent()) {
            String datasetIdRaw = request.getDatasetId();
            if (!StringUtils.hasText(datasetIdRaw)) {
                existing.setDatasetId(null);
                mutated = true;
            } else {
                var datasetCheck = validateDatasetAssociation(datasetIdRaw.trim(), activeDept);
                if (!datasetCheck.success()) {
                    recordAudit(
                        "DENY",
                        "explore.savedQuery",
                        datasetCheck.auditHint(),
                        "更新保存查询失败：数据集不可用",
                        existing.getName(),
                        "FAILED"
                    );
                    return ApiResponses.error(datasetCheck.message());
                }
                existing.setDatasetId(datasetCheck.dataset() != null ? datasetCheck.dataset().getId() : null);
                mutated = true;
            }
        }

        if (!mutated) {
            return ApiResponses.error("No fields to update");
        }

        ExploreSavedQuery saved = savedRepo.save(existing);
        recordAudit(
            "UPDATE",
            "explore.savedQuery",
            id.toString(),
            "更新保存查询：" + safeLabel(saved.getName()),
            saved.getName()
        );
        return ApiResponses.ok(saved);
    }

    @GetMapping("/saved-queries/{id}")
    public ApiResponse<ExploreSavedQuery> getSaved(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var q = savedRepo.findById(id).orElse(null);
        if (q != null && q.getDatasetId() != null) {
            CatalogDataset dataset = datasetRepo.findById(q.getDatasetId()).orElse(null);
            if (!datasetWithinScope(dataset, resolveActiveDeptContext(activeDept))) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery",
                    id.toString(),
                    "查看保存查询被拒绝：" + safeLabel(q.getName()),
                    q.getName(),
                    "FAILED"
                );
                return ApiResponses.error(
                    com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT,
                    "Access denied for dataset"
                );
            }
        }
        if (q != null) {
            recordAudit(
                "READ",
                "explore.savedQuery",
                id.toString(),
                "查看保存查询：" + safeLabel(q.getName()),
                q.getName()
            );
        }
        return ApiResponses.ok(q);
    }

    @PostMapping("/saved-queries/{id}/run")
    public ApiResponse<Map<String, Object>> runSaved(
        @PathVariable UUID id,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        var q = savedRepo.findById(id).orElseThrow();
        CatalogDataset dataset = null;
        if (q.getDatasetId() != null) {
            dataset = resolveDataset(q.getDatasetId().toString());
            if (dataset == null) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery.run",
                    id.toString(),
                    "执行保存查询被拒绝：数据集不可用",
                    q.getName(),
                    "FAILED"
                );
                return ApiResponses.error("Access denied for dataset");
            }
            // Enforce scope gate similar to preview/execute
            String effDept = resolveActiveDeptContext(activeDept);
            boolean read = accessChecker.canRead(dataset);
            boolean deptOk = accessChecker.departmentAllowed(dataset, effDept);
            if (!read || !deptOk) {
                recordAudit(
                    "DENY",
                    "explore.savedQuery.run",
                    id.toString(),
                    "执行保存查询被拒绝：" + safeLabel(q.getName()),
                    q.getName(),
                    "FAILED"
                );
                String code = !deptOk
                    ? com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.INVALID_CONTEXT
                    : com.yuzhi.dts.platform.security.policy.PolicyErrorCodes.RBAC_DENY;
                return ApiResponses.error(code, "Access denied for dataset");
            }
        }
        String datasetLabel = datasetName(dataset, q.getDatasetId());
        try {
            Map<String, Object> payload = generateResult(
                dataset,
                Optional.ofNullable(q.getSqlText()).orElse(""),
                true
            );
            recordAudit(
                "EXECUTE",
                "explore.savedQuery.run",
                id.toString(),
                "执行保存查询：" + safeLabel(q.getName()),
                q.getName(),
                "SUCCESS",
                datasetLabel != null ? Map.of("dataset", datasetLabel) : null
            );
            return ApiResponses.ok(payload);
        } catch (SecurityGuardException ex) {
            LOG.warn("Saved query run denied: {}", ex.getMessage());
            recordAudit(
                "DENY",
                "explore.savedQuery.run",
                id.toString(),
                "执行保存查询被拒绝：" + safeLabel(q.getName()),
                q.getName(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (IllegalStateException ex) {
            LOG.warn("Saved query run failed: {}", ex.getMessage());
            recordAudit(
                "ERROR",
                "explore.savedQuery.run",
                id.toString(),
                "执行保存查询失败：" + safeLabel(q.getName()),
                q.getName(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error(ex.getMessage());
        } catch (Exception ex) {
            LOG.error("Saved query run unexpected failure", ex);
            recordAudit(
                "ERROR",
                "explore.savedQuery.run",
                id.toString(),
                "执行保存查询失败：" + safeLabel(q.getName()),
                q.getName(),
                "FAILED",
                Map.of("error", ex.getMessage())
            );
            return ApiResponses.error("查询执行失败: " + ex.getMessage());
        }
    }

    private DatasetAssociationResult validateDatasetAssociation(String datasetIdRaw, String activeDeptHeader) {
        if (!StringUtils.hasText(datasetIdRaw)) {
            return DatasetAssociationResult.success(null);
        }
        UUID datasetId;
        try {
            datasetId = UUID.fromString(datasetIdRaw);
        } catch (IllegalArgumentException ex) {
            return DatasetAssociationResult.failure("datasetId must be a valid UUID", "invalid:" + datasetIdRaw);
        }
        CatalogDataset dataset = datasetRepo.findById(datasetId).orElse(null);
        if (dataset == null) {
            return DatasetAssociationResult.failure("指定的数据集不存在", "missing:" + datasetId);
        }
        String effDept = resolveActiveDeptContext(activeDeptHeader);
        if (!datasetWithinScope(dataset, effDept)) {
            return DatasetAssociationResult.failure("Access denied for dataset", datasetId.toString());
        }
        return DatasetAssociationResult.success(dataset);
    }

    private CatalogDataset resolveDataset(Object datasetIdRaw) {
        if (datasetIdRaw == null) {
            return null;
        }
        try {
            UUID datasetId = UUID.fromString(String.valueOf(datasetIdRaw));
            CatalogDataset dataset = datasetRepo.findById(datasetId).orElse(null);
            if (dataset == null) {
                return null;
            }
            return accessChecker.canRead(dataset) ? dataset : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> generateResult(CatalogDataset dataset, String sqlText, boolean persist) {
        String effectiveSql = prepareSql(sqlText, dataset);
        effectiveSql = securitySqlRewriter.guard(effectiveSql, dataset);
        Map<String, Object> queryResult = queryGateway.execute(effectiveSql);

        List<String> headers = extractHeaders(queryResult);
        List<Map<String, Object>> rows = extractRows(queryResult.get("rows"));
        if (headers.isEmpty() && !rows.isEmpty()) {
            headers = new ArrayList<>(rows.get(0).keySet());
        }
        if (headers.isEmpty()) {
            headers = buildHeaders(dataset);
        }

        applyDataLevelRowFilter(dataset, headers, rows);

        Map<String, Object> masking = buildMasking(headers);
        long connectMillis = numberOrDefault(queryResult.get("connectMillis"), -1L);
        long queryMillis = numberOrDefault(queryResult.get("queryMillis"), -1L);
        long durationMs = numberOrDefault(queryResult.get("durationMs"), -1L);
        if (durationMs < 0) {
            if (connectMillis >= 0 && queryMillis >= 0) {
                durationMs = connectMillis + queryMillis;
            } else if (queryMillis >= 0) {
                durationMs = queryMillis;
            } else if (connectMillis >= 0) {
                durationMs = connectMillis;
            } else {
                durationMs = rows.isEmpty() ? 0 : 200;
            }
        }

        long rowCount = Math.min(numberOrDefault(queryResult.get("rowCount"), rows.size()), rows.size());

        Map<String, Object> payload = new LinkedHashMap<>(queryResult);
        payload.put("effectiveSql", effectiveSql);
        payload.put("headers", headers);
        payload.put("rows", cloneRows(rows));
        payload.put("masking", masking);
        payload.put("rowCount", rowCount);
        payload.put("durationMs", durationMs);
        if (connectMillis >= 0) {
            payload.put("connectMillis", connectMillis);
        }
        if (queryMillis >= 0) {
            payload.put("queryMillis", queryMillis);
        }

        if (persist) {
            UUID executionId = persistExecution(dataset, effectiveSql, headers, rows, masking, durationMs, rowCount);
            payload.put("executionId", executionId.toString());
        }
        return payload;
    }

    private String prepareSql(String sqlText, CatalogDataset dataset) {
        String candidate = sqlText != null ? sqlText.trim() : "";
        if (!candidate.isEmpty()) {
            return candidate;
        }
        if (dataset != null) {
            return datasetSqlBuilder.buildSampleQuery(dataset, 100);
        }
        return "SELECT 1";
    }

    @SuppressWarnings("unchecked")
    private List<String> extractHeaders(Map<String, Object> queryResult) {
        if (queryResult == null) {
            return new ArrayList<>();
        }
        Object headersObj = queryResult.get("headers");
        if (headersObj instanceof List<?> list) {
            List<String> headers = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item != null) {
                    headers.add(String.valueOf(item));
                }
            }
            headers.removeIf(String::isBlank);
            if (!headers.isEmpty()) {
                return headers;
            }
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Object rowsObj) {
        if (!(rowsObj instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> rows = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                    if (entry.getKey() != null) {
                        row.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private void applyDataLevelRowFilter(CatalogDataset dataset, List<String> headers, List<Map<String, Object>> rows) {
        if (dataset == null || rows == null || rows.isEmpty()) {
            return;
        }
        Optional<String> columnOpt = metadataResolver.findDataLevelColumn(dataset);
        if (columnOpt.isEmpty()) {
            return;
        }
        List<DataLevel> allowedLevelsList = accessChecker.resolveAllowedDataLevels();
        if (allowedLevelsList == null || allowedLevelsList.isEmpty()) {
            rows.clear();
            return;
        }
        Set<DataLevel> allowedLevels = allowedLevelsList.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        if (allowedLevels.isEmpty()) {
            rows.clear();
            return;
        }
        Set<String> allowedTokens = allowedLevels
            .stream()
            .flatMap(level -> level.tokens().stream())
            .map(token -> token.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        String dataLevelColumn = columnOpt.orElseThrow();
        int headerIndex = resolveHeaderIndex(headers, dataLevelColumn);

        rows.removeIf(row -> !isRowLevelAllowed(row, headers, headerIndex, dataLevelColumn, allowedLevels, allowedTokens));
    }

    private int resolveHeaderIndex(List<String> headers, String columnName) {
        if (headers == null || columnName == null) {
            return -1;
        }
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (header != null && header.trim().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isRowLevelAllowed(
        Map<String, Object> row,
        List<String> headers,
        int headerIndex,
        String columnName,
        Set<DataLevel> allowedLevels,
        Set<String> allowedTokens
    ) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        Object rawValue = lookupValue(row, columnName);
        if (rawValue == null && headerIndex >= 0 && headers != null && headerIndex < headers.size()) {
            rawValue = lookupValue(row, headers.get(headerIndex));
        }
        if (rawValue == null) {
            return false;
        }
        String text = rawValue.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        DataLevel level = DataLevel.normalize(text);
        if (level != null) {
            return allowedLevels.contains(level);
        }
        for (String token : expandValueVariants(text)) {
            if (allowedTokens.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Object lookupValue(Map<String, Object> row, String key) {
        if (key == null) {
            return null;
        }
        if (row.containsKey(key)) {
            return row.get(key);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Set<String> expandValueVariants(String raw) {
        Set<String> variants = new LinkedHashSet<>();
        if (raw == null) {
            return variants;
        }
        String base = raw.trim();
        if (base.isEmpty()) {
            return variants;
        }
        String upper = base.toUpperCase(Locale.ROOT);
        variants.add(upper);
        variants.add(upper.replace('-', '_'));
        variants.add(upper.replace('_', '-'));
        variants.add(upper.replace(' ', '_'));
        variants.add(upper.replace(' ', '-'));
        variants.add(upper.replace('-', ' '));
        variants.add(upper.replace('_', ' '));
        return variants;
    }

    private long numberOrDefault(Object value, long defaultValue) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private UUID persistExecution(
        CatalogDataset dataset,
        String sqlText,
        List<String> headers,
        List<Map<String, Object>> rows,
        Map<String, Object> masking,
        long durationMs,
        long rowCount
    ) {
        ResultSet resultSet = new ResultSet();
        String defaultName = null;
        if (dataset != null && dataset.getName() != null && !dataset.getName().isBlank()) {
            defaultName = dataset.getName();
        } else if (sqlText != null && !sqlText.isBlank()) {
            defaultName = sqlText.replaceAll("\\s+", " ");
        }
        resultSet.setName(trimToLength(defaultName != null ? defaultName : "临时结果集", 128));
        resultSet.setStorageUri("memory://result/" + UUID.randomUUID());
        resultSet.setStorageFormat(StorageFormat.PARQUET);
        resultSet.setColumns(String.join(",", headers));
        resultSet.setRowCount(rowCount);
        resultSet.setChunkCount(1);
        resultSet.setPreviewColumns(writePreview(rows, masking));
        ResultSet savedResult = resultSetRepo.save(resultSet);

        Instant finished = Instant.now();
        Instant started = durationMs > 0 ? finished.minus(durationMs, ChronoUnit.MILLIS) : finished;

        QueryExecution execution = new QueryExecution();
        execution.setEngine(ExecEnums.ExecEngine.TRINO);
        execution.setSqlText(sqlText == null || sqlText.isBlank() ? "SELECT 1" : sqlText);
        execution.setStatus(ExecEnums.ExecStatus.SUCCESS);
        execution.setStartedAt(started);
        execution.setFinishedAt(finished);
        execution.setRowCount(rowCount);
        execution.setElapsedMs(durationMs);
        execution.setResultSetId(savedResult.getId());
        if (dataset != null) {
            execution.setDatasetId(dataset.getId());
            execution.setConnection(Optional.ofNullable(dataset.getTrinoCatalog()).orElse("default"));
            execution.setDatasource(Optional.ofNullable(dataset.getHiveTable()).orElse(dataset.getName()));
        } else {
            execution.setConnection("default");
            execution.setDatasource("adhoc");
        }
        executionRepo.save(execution);
        return savedResult.getId();
    }

    private Map<String, Object> toExecutionMap(QueryExecution execution, CatalogDataset dataset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", execution.getId() != null ? execution.getId().toString() : null);
        map.put("status", execution.getStatus() != null ? execution.getStatus().name() : ExecEnums.ExecStatus.SUCCESS.name());
        map.put("startedAt", Optional.ofNullable(execution.getStartedAt()).map(Instant::toString).orElse(null));
        map.put("finishedAt", Optional.ofNullable(execution.getFinishedAt()).map(Instant::toString).orElse(null));
        map.put("rowCount", execution.getRowCount());
        map.put("durationMs", execution.getElapsedMs());
        map.put("sqlText", execution.getSqlText());
        if (dataset != null) {
            map.put("datasetName", dataset.getName());
            map.put("classification", translateClassification(dataset.getClassification()));
        } else {
            map.put("datasetName", Optional.ofNullable(execution.getDatasource()).orElse("临时查询"));
            map.put("classification", "内部");
        }
        map.put("executionId", Optional.ofNullable(execution.getResultSetId()).map(UUID::toString).orElse(null));
        return map;
    }

    private Map<String, Object> toResultSetMap(ResultSet record, QueryExecution execution, CatalogDataset dataset) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId().toString());
        String name = record.getName();
        if (name == null || name.isBlank()) {
            if (dataset != null && dataset.getName() != null && !dataset.getName().isBlank()) {
                name = dataset.getName();
            } else if (execution != null && execution.getDatasource() != null && !execution.getDatasource().isBlank()) {
                name = execution.getDatasource();
            } else {
                name = "临时结果集";
            }
        }
        map.put("name", name);
        map.put("columns", parseColumns(record.getColumns()));
        map.put("rowCount", record.getRowCount());
        map.put("createdAt", Optional.ofNullable(record.getCreatedDate()).map(Instant::toString).orElse(null));
        map.put("expiresAt", Optional.ofNullable(record.getExpiresAt()).map(Instant::toString).orElse(null));
        map.put("storageUri", record.getStorageUri());
        if (dataset != null) {
            map.put("datasetName", dataset.getName());
            map.put("classification", translateClassification(dataset.getClassification()));
        } else if (execution != null) {
            map.put("datasetName", Optional.ofNullable(execution.getDatasource()).orElse("临时查询"));
            map.put("classification", "内部");
        } else {
            map.put("datasetName", "临时查询");
            map.put("classification", "内部");
        }
        return map;
    }

    private boolean canManageAllResultSets() {
        return SecurityUtils.isOpAdminAccount() ||
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.DATA_MAINTAINER_ROLES);
    }

    private boolean isResultSetOwner(ResultSet rs) {
        if (rs == null || rs.getCreatedBy() == null) {
            return false;
        }
        String owner = rs.getCreatedBy().trim();
        if (!StringUtils.hasText(owner)) {
            return false;
        }
        String currentUser = SecurityUtils
            .getCurrentUserLogin()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .orElse(null);
        return currentUser != null && owner.equalsIgnoreCase(currentUser);
    }

    private void assertResultSetAccess(ResultSet rs) {
        if (isResultSetOwner(rs)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前结果集仅创建人可访问");
    }

    private List<String> buildHeaders(CatalogDataset dataset) {
        List<String> headers = new ArrayList<>();
        if (dataset != null && dataset.getHiveTable() != null) {
            String base = dataset.getHiveTable().replaceAll("[^a-zA-Z0-9]", "_");
            headers.add(base + "_id");
            headers.add(base + "_name");
            headers.add("updated_at");
            headers.add("sensitivity_level");
            headers.add("metric_value");
        } else {
            for (int i = 1; i <= 5; i++) {
                headers.add("col_" + i);
            }
        }
        return headers;
    }

    private List<Map<String, Object>> buildRows(List<String> headers) {
        List<Map<String, Object>> rows = new ArrayList<>();
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String header : headers) {
                String lower = header.toLowerCase(Locale.ROOT);
                if (lower.contains("id")) {
                    row.put(header, "ID-" + tlr.nextInt(1000, 9999));
                } else if (lower.contains("name")) {
                    row.put(header, "样本" + (char) ('A' + tlr.nextInt(0, 26)));
                } else if (lower.contains("time") || lower.contains("date")) {
                    row.put(header, Instant.now().minus(tlr.nextInt(1, 48), ChronoUnit.HOURS).toString());
                } else if (lower.contains("level")) {
                    row.put(header, tlr.nextBoolean() ? "内部" : "秘密");
                } else {
                    row.put(header, tlr.nextInt(1, 10000));
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> cloneRows(List<Map<String, Object>> source) {
        return cloneRows(source, source.size());
    }

    private List<Map<String, Object>> cloneRows(List<Map<String, Object>> source, int limit) {
        List<Map<String, Object>> copy = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, source.size()); i++) {
            copy.add(new LinkedHashMap<>(source.get(i)));
        }
        return copy;
    }

    private List<Map<String, Object>> generateFallbackRows(List<String> headers, int limit) {
        return cloneRows(buildRows(headers), limit);
    }

    private boolean datasetWithinScope(CatalogDataset dataset, String activeDept) {
        if (dataset == null) {
            return true;
        }
        if (!accessChecker.canRead(dataset)) {
            return false;
        }
        return accessChecker.departmentAllowed(dataset, activeDept);
    }

    private String resolveActiveDeptContext(String activeDeptHeader) {
        if (StringUtils.hasText(activeDeptHeader)) {
            return activeDeptHeader.trim();
        }
        String candidate = claim("dept_code");
        if (!StringUtils.hasText(candidate)) {
            candidate = claim("deptCode");
        }
        if (!StringUtils.hasText(candidate)) {
            candidate = claim("department");
        }
        return StringUtils.hasText(candidate) ? candidate.trim() : null;
    }

    private Map<String, Object> buildMasking(List<String> headers) {
        List<String> masked = headers
            .stream()
            .filter(h -> {
                String lower = h.toLowerCase(Locale.ROOT);
                return lower.contains("name") || lower.contains("id") || lower.contains("phone");
            })
            .collect(Collectors.toList());
        return Map.of("maskedColumns", masked);
    }

    private List<String> parseColumns(String columnsRaw) {
        if (columnsRaw == null || columnsRaw.isBlank()) {
            return List.of();
        }
        String[] parts = columnsRaw.split(",");
        List<String> headers = new ArrayList<>(parts.length);
        for (String part : parts) {
            headers.add(part.trim());
        }
        return headers;
    }

    private String translateClassification(String classification) {
        if (classification == null) {
            return "内部";
        }
        return switch (classification.toUpperCase(Locale.ROOT)) {
            case "PUBLIC" -> "公开";
            case "INTERNAL", "GENERAL" -> "内部";
            case "SECRET" -> "秘密";
            case "TOP_SECRET", "CONFIDENTIAL" -> "机密";
            default -> classification;
        };
    }

    private String datasetIdentifier(CatalogDataset dataset, Object raw) {
        if (dataset != null && dataset.getId() != null) {
            return dataset.getId().toString();
        }
        return Objects.toString(raw, "adhoc");
    }

    private String datasetName(CatalogDataset dataset, Object raw) {
        if (dataset != null && dataset.getName() != null && !dataset.getName().isBlank()) {
            return dataset.getName();
        }
        if (raw != null) {
            String text = String.valueOf(raw).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return "未命名数据集";
    }

    private String safeLabel(String label) {
        if (label == null) {
            return "未命名资源";
        }
        String trimmed = label.trim();
        return trimmed.isEmpty() ? "未命名资源" : trimmed;
    }

    private String claim(String name) {
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get(name);
                return stringifyClaim(v);
            }
            if (auth != null && auth.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
                Object v = principal.getAttribute(name);
                return stringifyClaim(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String stringifyClaim(Object raw) {
        Object flattened = flattenClaim(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flattenClaim(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    private record DatasetAssociationResult(boolean success, CatalogDataset dataset, String message, String auditHint) {

        static DatasetAssociationResult success(CatalogDataset dataset) {
            return new DatasetAssociationResult(true, dataset, null, dataset != null && dataset.getId() != null ? dataset.getId().toString() : "");
        }

        static DatasetAssociationResult failure(String message, String auditHint) {
            return new DatasetAssociationResult(false, null, message, auditHint);
        }
    }

    private String asText(Object value) {
        return value != null ? String.valueOf(value).trim() : null;
    }

    private String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }

    private String extractSql(Map<String, Object> body) {
        Object sql = body.get("sqlText");
        if (sql == null) {
            sql = body.get("sql");
        }
        return sql != null ? String.valueOf(sql) : "";
    }

    private List<String> buildExplainSteps(String sql) {
        String normalized = sql == null ? "" : sql.trim();
        if (normalized.isEmpty()) {
            normalized = "SELECT 1";
        }
        List<String> steps = new ArrayList<>();
        steps.add("Parse SQL");
        if (normalized.toUpperCase(Locale.ROOT).contains("JOIN")) {
            steps.add("Resolve join strategy (Broadcast Hash Join)");
        }
        if (normalized.toUpperCase(Locale.ROOT).contains("GROUP")) {
            steps.add("Aggregate on distributed nodes");
        }
        steps.add("Scan Hive table with predicate pushdown");
        steps.add("Collect results and apply masking");
        return steps;
    }

    private String writePreview(List<Map<String, Object>> rows, Map<String, Object> masking) {
        PreviewPayload payload = new PreviewPayload();
        payload.setRows(cloneRows(rows, Math.min(PREVIEW_ROW_LIMIT, rows.size())));
        payload.setMasking(masking);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private PreviewPayload readPreview(String json) {
        if (json == null || json.isBlank()) {
            return new PreviewPayload();
        }
        try {
            return objectMapper.readValue(json, PreviewPayload.class);
        } catch (Exception e) {
            return new PreviewPayload();
        }
    }

    private void recordAudit(String action, String module, String resourceId, String summary, String targetName) {
        recordAudit(action, module, resourceId, summary, targetName, "SUCCESS", null);
    }

    private void recordAudit(String action, String module, String resourceId, String summary, String targetName, String result) {
        recordAudit(action, module, resourceId, summary, targetName, result, null);
    }

    private void recordAudit(
        String action,
        String module,
        String resourceId,
        String summary,
        String targetName,
        String result,
        Map<String, Object> extra
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (summary != null && !summary.isBlank()) {
            payload.put("summary", summary);
        }
        if (resourceId != null && !resourceId.isBlank()) {
            payload.put("targetId", resourceId);
        }
        if (targetName != null && !targetName.isBlank()) {
            payload.put("targetName", targetName);
        }
        if (extra != null && !extra.isEmpty()) {
            payload.putAll(extra);
        }
        audit.record(action, module, module, resourceId, result, payload);
    }

    private static final class PreviewPayload {
        private List<Map<String, Object>> rows;
        private Map<String, Object> masking;

        public List<Map<String, Object>> getRows() {
            return rows;
        }

        public void setRows(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        public Map<String, Object> getMasking() {
            return masking;
        }

        public void setMasking(Map<String, Object> masking) {
            this.masking = masking;
        }

        public boolean hasRows() {
            return rows != null && !rows.isEmpty();
        }
    }
}
