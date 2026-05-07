package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.request.IssueTicketUpsertRequest;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.security.HiveStatementExecutor;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class QualityRunService {

    private static final Logger log = LoggerFactory.getLogger(QualityRunService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private static final String SAFE_IDENTIFIER = "^[a-zA-Z_][a-zA-Z0-9_.]*$";

    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final GovQualityRunRepository runRepository;
    private final GovQualityMetricRepository metricRepository;
    private final GovQualityFailingRowRepository failingRowRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final Executor taskExecutor;
    private final HiveStatementExecutor hiveExecutor;
    private final AuditService auditService;
    private final IssueTicketService issueTicketService;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;
    private final TransactionTemplate runTransactionTemplate;

    public QualityRunService(
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        GovRuleBindingRepository bindingRepository,
        GovQualityRunRepository runRepository,
        GovQualityMetricRepository metricRepository,
        GovQualityFailingRowRepository failingRowRepository,
        CatalogDatasetRepository datasetRepository,
        @Qualifier("taskExecutor") Executor taskExecutor,
        HiveStatementExecutor hiveExecutor,
        AuditService auditService,
        IssueTicketService issueTicketService,
        ObjectMapper objectMapper,
        GovernanceProperties properties,
        PlatformTransactionManager transactionManager
    ) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.bindingRepository = bindingRepository;
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.failingRowRepository = failingRowRepository;
        this.datasetRepository = datasetRepository;
        this.taskExecutor = taskExecutor;
        this.hiveExecutor = hiveExecutor;
        this.auditService = auditService;
        this.issueTicketService = issueTicketService;
        this.objectMapper = objectMapper;
        this.properties = properties;
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.runTransactionTemplate = template;
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor) {
        if (!properties.getQuality().isEnabled()) {
            throw new IllegalStateException("质量检测功能已禁用");
        }
        boolean dryRun = request != null && Boolean.TRUE.equals(request.getDryRun());
        GovRule rule = resolveRule(request.getRuleId());
        GovRuleVersion version = resolveVersion(rule);
        List<GovRuleBinding> bindings = resolveBindings(version, request.getBindingId(), request.getDatasetId());
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("该规则尚未绑定数据集");
        }

        Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();
        List<QualityRunDto> runs = new ArrayList<>();
        List<UUID> runIds = new ArrayList<>();
        for (GovRuleBinding binding : bindings) {
            GovQualityRun run = new GovQualityRun();
            run.setRule(rule);
            run.setRuleVersion(version);
            run.setBinding(binding);
            run.setDatasetId(binding.getDatasetId());
            run.setTriggerType(dryRun ? "DRY_RUN" : StringUtils.defaultIfBlank(request.getTriggerType(), "MANUAL"));
            run.setTriggerRef(actor);
            run.setStatus("QUEUED");
            run.setSeverity(rule.getSeverity());
            run.setDataLevel(rule.getDataLevel());
            run.setScheduledAt(Instant.now());
            run.setInputParamsJson(writeJson(params));
            runRepository.save(run);

            runIds.add(run.getId());
            runs.add(GovernanceMapper.toDto(run, Collections.emptyList()));
        }

        if (!runIds.isEmpty()) {
            List<UUID> dispatchIds = List.copyOf(runIds);
            if (dryRun) {
                dispatchIds.forEach(id -> doExecuteRun(id, params));
            } else {
                if (TransactionSynchronizationManager.isSynchronizationActive()) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dispatchIds.forEach(id ->
                                taskExecutor.execute(() ->
                                    runTransactionTemplate.executeWithoutResult(status -> doExecuteRun(id, params))
                                )
                            );
                        }
                    });
                } else {
                    dispatchIds.forEach(id ->
                        taskExecutor.execute(() ->
                            runTransactionTemplate.executeWithoutResult(status -> doExecuteRun(id, params))
                        )
                    );
                }
            }
        }
        if (dryRun) {
            return dispatchIdsToDtos(runIds);
        }
        return runs;
    }

    @Transactional(readOnly = true)
    public QualityRunDto getRun(UUID runId) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        List<GovQualityMetric> metrics = metricRepository.findByRunId(runId);
        return GovernanceMapper.toDto(run, metrics);
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByRule(UUID ruleId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return runRepository
            .findByRuleId(ruleId, pageable)
            .stream()
            .map(run -> GovernanceMapper.toDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recentByDataset(UUID datasetId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return runRepository
            .findByDatasetId(datasetId, pageable)
            .stream()
            .map(run -> GovernanceMapper.toDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> recent(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return runRepository
            .findAll(pageable)
            .getContent()
            .stream()
            .map(run -> GovernanceMapper.toDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QualityRunDto> listRuns(UUID ruleId, UUID datasetId, String status, String triggerType, Instant startedFrom, Instant startedTo, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int querySize = Math.max(safeLimit, 200);
        Pageable pageable = PageRequest.of(0, querySize, Sort.Direction.DESC, "createdDate");
        List<GovQualityRun> candidates;
        if (ruleId != null) {
            candidates = runRepository.findByRuleId(ruleId, pageable);
        } else if (datasetId != null) {
            candidates = runRepository.findByDatasetId(datasetId, pageable);
        } else {
            candidates = runRepository.findAll(pageable).getContent();
        }
        String normalizedStatus = StringUtils.trimToNull(status);
        String normalizedTriggerType = StringUtils.trimToNull(triggerType);
        return candidates
            .stream()
            .filter(run -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(StringUtils.trimToEmpty(run.getStatus())))
            .filter(run -> normalizedTriggerType == null || normalizedTriggerType.equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType())))
            .filter(run -> {
                Instant pivot = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedDate();
                if (startedFrom != null && (pivot == null || pivot.isBefore(startedFrom))) {
                    return false;
                }
                return startedTo == null || pivot == null || !pivot.isAfter(startedTo);
            })
            .limit(safeLimit)
            .map(run -> GovernanceMapper.toDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    private void doExecuteRun(UUID runId, Map<String, Object> params) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        Instant start = Instant.now();
        run.setStatus("RUNNING");
        run.setStartedAt(start);
        run.setMessage("正在执行质量检测");
        runRepository.save(run);

        try {
            // Count total rows in target table before executing checks
            countRowsTotal(run);

            Map<String, String> statements = resolveStatements(run.getRuleVersion());
            if (statements.isEmpty()) {
                run.setStatus("SKIPPED");
                run.setFinishedAt(Instant.now());
                run.setMessage("未配置检测语句");
                run.setErrorCategory(null);
                runRepository.save(run);
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("message", run.getMessage());
                auditService.recordAs(
                    resolveRunActor(run),
                    "SKIP",
                    "governance.quality.run",
                    "governance.quality.run",
                    runId.toString(),
                    "SUCCESS",
                    payload,
                    buildRunAuditTags(run)
                );
                return;
            }

            Map<String, String> rendered = renderParams(statements, params);
            List<StatementExecutionResult> results = hiveExecutor.execute(rendered, run.getRule() != null ? run.getRule().getOwner() : null);
            persistMetrics(run, results);
            // Update failing row count from the failing_row table
            long failCount = failingRowRepository.countByRunId(run.getId());
            run.setFailingRowCount((int) failCount);
            StatementExecutionResult.Status aggregate = aggregateStatus(results);
            run.setStatus(mapStatus(aggregate));
            run.setMessage(summaryMessage(results));
            run.setErrorCategory(resolveErrorCategory(results));
            run.setFinishedAt(Instant.now());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            run.setMetricsJson(writeMetrics(results));
            runRepository.save(run);
            if (aggregate == StatementExecutionResult.Status.FAILED) {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("message", run.getMessage());
                payload.put("results", results);
                auditService.recordAs(
                    resolveRunActor(run),
                    "EXECUTE",
                    "governance.quality.run",
                    "governance.quality.run",
                    runId.toString(),
                    "FAILED",
                    payload,
                    buildRunAuditTags(run)
                );
                if (!isDryRun(run)) {
                    createIssueForFailedRun(run, results, resolveRunActor(run));
                }
            } else {
                Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
                payload.put("status", run.getStatus());
                payload.put("message", run.getMessage());
                auditService.recordAs(
                    resolveRunActor(run),
                    "EXECUTE",
                    "governance.quality.run",
                    "governance.quality.run",
                    runId.toString(),
                    "SUCCESS",
                    payload,
                    buildRunAuditTags(run)
                );
            }
        } catch (Exception ex) {
            log.error("Quality run failed: {}", ex.getMessage(), ex);
            run.setStatus("FAILED");
            run.setFinishedAt(Instant.now());
            run.setMessage(ex.getMessage());
            run.setErrorCategory(resolveErrorCategory(ex.getMessage()));
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            runRepository.save(run);
            Map<String, Object> payload = buildRunAuditPayload(run, "运行质量规则：" + resolveRunRuleName(run));
            payload.put("status", run.getStatus());
            payload.put("message", run.getMessage());
            payload.put("error", ex.getMessage());
            auditService.recordAs(
                resolveRunActor(run),
                "EXECUTE",
                "governance.quality.run",
                "governance.quality.run",
                runId.toString(),
                "FAILED",
                payload,
                buildRunAuditTags(run)
            );
            if (!isDryRun(run)) {
                createIssueForFailedRun(run, null, resolveRunActor(run));
            }
        }
    }

    private boolean isDryRun(GovQualityRun run) {
        return run != null && "DRY_RUN".equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType()));
    }

    private List<QualityRunDto> dispatchIdsToDtos(List<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Collections.emptyList();
        }
        return runIds
            .stream()
            .map(runRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(run -> GovernanceMapper.toDto(run, metricRepository.findByRunId(run.getId())))
            .collect(Collectors.toList());
    }

    private void createIssueForFailedRun(GovQualityRun run, List<StatementExecutionResult> results, String actor) {
        if (run == null || run.getId() == null) {
            return;
        }
        try {
            IssueTicketUpsertRequest req = new IssueTicketUpsertRequest();
            String ruleName = resolveRunRuleName(run);
            req.setTitle("质量检测失败：" + ruleName);
            StringBuilder summary = new StringBuilder();
            summary.append("规则：").append(ruleName);
            if (run.getDatasetId() != null) {
                summary.append("\n数据集：").append(run.getDatasetId());
            }
            if (StringUtils.isNotBlank(run.getMessage())) {
                summary.append("\n原因：").append(run.getMessage());
            }
            if (results != null && !results.isEmpty()) {
                long failed = results.stream().filter(r -> r != null && r.status() == StatementExecutionResult.Status.FAILED).count();
                summary.append("\n失败项数：").append(failed);
            }
            req.setSummary(summary.toString());
            req.setSeverity(run.getSeverity());
            req.setDataLevel(run.getDataLevel());
            req.setDatasetId(run.getDatasetId());
            req.setOwner(run.getRule() != null ? run.getRule().getOwner() : null);
            req.setTags(List.of(
                "QUALITY_RUN",
                "trigger=" + String.valueOf(run.getTriggerType()),
                "datasetId=" + String.valueOf(run.getDatasetId())
            ));
            String effectiveActor = StringUtils.isNotBlank(actor) ? actor : "system";
            issueTicketService.createOrTouch("QUALITY_RUN", run.getId(), req, effectiveActor, "系统自动生成：质量检测失败");
        } catch (Exception ex) {
            log.debug("Failed to create issue ticket for run {}: {}", run.getId(), ex.getMessage());
        }
    }

    private String resolveRuleName(GovRule rule) {
        if (rule == null) {
            return "未知规则";
        }
        if (StringUtils.isNotBlank(rule.getName())) {
            return rule.getName();
        }
        if (StringUtils.isNotBlank(rule.getCode())) {
            return rule.getCode();
        }
        return rule.getId() != null ? rule.getId().toString() : "未知规则";
    }

    private String resolveRunRuleName(GovQualityRun run) {
        if (run == null) {
            return "未知规则";
        }
        GovRule rule = run.getRule();
        if (rule != null) {
            return resolveRuleName(rule);
        }
        GovRuleVersion version = run.getRuleVersion();
        if (version != null && version.getRule() != null) {
            return resolveRuleName(version.getRule());
        }
        return run.getId() != null ? run.getId().toString() : "未知规则";
    }

    private String resolveRunActor(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        if (StringUtils.isNotBlank(run.getCreatedBy())) {
            return run.getCreatedBy();
        }
        if (StringUtils.isNotBlank(run.getLastModifiedBy())) {
            return run.getLastModifiedBy();
        }
        return null;
    }

    private Map<String, Object> buildRunAuditPayload(GovQualityRun run, String summary) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("summary", summary);
        if (run != null) {
            if (run.getId() != null) {
                payload.put("runId", run.getId().toString());
            }
            GovRule rule = run.getRule();
            if (rule != null && rule.getId() != null) {
                payload.put("ruleId", rule.getId().toString());
                payload.put("ruleName", resolveRuleName(rule));
            } else if (run.getRuleVersion() != null && run.getRuleVersion().getRule() != null) {
                GovRule vrule = run.getRuleVersion().getRule();
                if (vrule.getId() != null) {
                    payload.put("ruleId", vrule.getId().toString());
                }
                payload.put("ruleName", resolveRuleName(vrule));
            }
            payload.putIfAbsent("ruleName", resolveRunRuleName(run));
        }
        return payload;
    }

    private Map<String, Object> buildRunAuditTags(GovQualityRun run) {
        if (run == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> tags = new LinkedHashMap<>();
        if (run.getId() != null) {
            tags.put("qualityRunId", run.getId().toString());
        }
        GovRule rule = run.getRule();
        if (rule != null && rule.getId() != null) {
            tags.put("qualityRuleId", rule.getId().toString());
        }
        GovRuleVersion version = run.getRuleVersion();
        if (version != null && version.getId() != null) {
            tags.put("qualityRuleVersionId", version.getId().toString());
        }
        if (run.getDatasetId() != null) {
            tags.put("datasetId", run.getDatasetId().toString());
        }
        if (StringUtils.isNotBlank(run.getSeverity())) {
            tags.put("severity", run.getSeverity());
        }
        if (StringUtils.isNotBlank(run.getTriggerType())) {
            tags.put("triggerType", run.getTriggerType());
        }
        if (StringUtils.isNotBlank(run.getStatus())) {
            tags.put("status", run.getStatus());
        }
        return tags.isEmpty() ? Collections.emptyMap() : tags;
    }

    private void countRowsTotal(GovQualityRun run) {
        String tableName = resolveTableName(run);
        if (tableName == null || !tableName.matches(SAFE_IDENTIFIER)) {
            return;
        }
        try {
            String countSql = "SELECT count(*) FROM " + tableName;
            List<StatementExecutionResult> countResults = hiveExecutor.execute(
                Map.of("__count__", countSql),
                run.getRule() != null ? run.getRule().getOwner() : null
            );
            if (!countResults.isEmpty()) {
                StatementExecutionResult first = countResults.getFirst();
                if (first.status() == StatementExecutionResult.Status.SUCCEEDED && first.message() != null) {
                    try {
                        String msg = first.message().trim();
                        // The message may contain the count result; try to parse it
                        run.setRowsTotal(Integer.parseInt(msg));
                    } catch (NumberFormatException ignored) {
                        // Count result not parsable from message
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to count rows for {}: {}", tableName, e.getMessage());
        }
    }

    private String resolveTableName(GovQualityRun run) {
        if (run.getDatasetId() == null) {
            return null;
        }
        Optional<CatalogDataset> datasetOpt = datasetRepository.findById(run.getDatasetId());
        if (datasetOpt.isEmpty()) {
            return null;
        }
        CatalogDataset dataset = datasetOpt.orElseThrow();
        String db = dataset.getHiveDatabase();
        String table = dataset.getHiveTable();
        if (StringUtils.isBlank(table)) {
            return null;
        }
        if (StringUtils.isNotBlank(db)) {
            return db + "." + table;
        }
        return table;
    }

    private GovRule resolveRule(UUID ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("缺少规则ID");
        }
        return ruleRepository.findById(ruleId).orElseThrow(EntityNotFoundException::new);
    }

    private GovRuleVersion resolveVersion(GovRule rule) {
        GovRuleVersion version = rule.getLatestVersion();
        if (version != null && !"PUBLISHED".equalsIgnoreCase(StringUtils.trimToEmpty(version.getStatus()))) {
            version = null;
        }
        if (version == null) {
            version = versionRepository.findFirstByRuleIdAndStatusOrderByVersionDesc(rule.getId(), "PUBLISHED").orElse(null);
        }
        if (version == null) {
            throw new IllegalStateException("规则尚无可执行的已发布版本");
        }
        return version;
    }

    private List<GovRuleBinding> resolveBindings(GovRuleVersion version, UUID bindingId, UUID datasetId) {
        if (bindingId != null) {
            Optional<GovRuleBinding> binding = bindingRepository.findById(bindingId);
            GovRuleBinding entity = binding.orElseThrow(() -> new IllegalArgumentException("未找到绑定"));
            if (entity.getRuleVersion() == null || !entity.getRuleVersion().getId().equals(version.getId())) {
                throw new IllegalArgumentException("绑定与规则版本不匹配");
            }
            if (datasetId != null && entity.getDatasetId() != null && !datasetId.equals(entity.getDatasetId())) {
                throw new IllegalArgumentException("绑定与数据集不匹配");
            }
            return List.of(entity);
        }
        List<GovRuleBinding> bindings = new ArrayList<>(version.getBindings());
        if (datasetId == null) {
            return bindings;
        }
        return bindings.stream().filter(binding -> datasetId.equals(binding.getDatasetId())).collect(Collectors.toList());
    }

    private Map<String, String> resolveStatements(GovRuleVersion version) {
        if (version.getDefinition() == null) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(version.getDefinition(), MAP_TYPE);
            if (raw.containsKey("statements")) {
                Object statements = raw.get("statements");
                if (statements instanceof Map<?, ?> map) {
                    Map<String, String> resolved = new LinkedHashMap<>();
                    map.forEach((key, value) -> {
                        if (key != null && value != null) {
                            resolved.put(String.valueOf(key), String.valueOf(value));
                        }
                    });
                    return resolved;
                }
            }
            if (raw.containsKey("sql")) {
                String sql = String.valueOf(raw.get("sql"));
                return Map.of("sql", sql);
            }
        } catch (Exception ex) {
            log.warn("Failed to parse rule definition: {}", ex.getMessage());
        }
        return Collections.emptyMap();
    }

    private Map<String, String> renderParams(Map<String, String> statements, Map<String, Object> params) {
        if (params.isEmpty()) {
            return statements;
        }
        Map<String, String> rendered = new LinkedHashMap<>();
        statements.forEach((key, value) -> rendered.put(key, applyParams(value, params)));
        return rendered;
    }

    private String applyParams(String sql, Map<String, Object> params) {
        String rendered = sql;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = ":" + entry.getKey();
            if (rendered.contains(placeholder) && entry.getValue() != null) {
                rendered = rendered.replace(placeholder, quote(entry.getValue().toString()));
            }
        }
        return rendered;
    }

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private void persistMetrics(GovQualityRun run, List<StatementExecutionResult> results) {
        metricRepository.findByRunId(run.getId()).forEach(metricRepository::delete);
        for (StatementExecutionResult result : results) {
            GovQualityMetric metric = new GovQualityMetric();
            metric.setRun(run);
            metric.setMetricKey(result.key());
            metric.setDetail(result.message());
            metric.setStatus(result.status().name());
            // Compute metric_value: pass rate based on rowsTotal and failingRowCount
            Integer rowsTotal = run.getRowsTotal();
            Integer failingRows = run.getFailingRowCount();
            if (rowsTotal != null && rowsTotal > 0) {
                int failing = failingRows != null ? failingRows : 0;
                BigDecimal passRate = BigDecimal.valueOf(rowsTotal - failing)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(rowsTotal), 6, RoundingMode.HALF_UP);
                metric.setMetricValue(passRate);
            } else if (result.status() == StatementExecutionResult.Status.SUCCEEDED) {
                metric.setMetricValue(BigDecimal.valueOf(100));
            } else if (result.status() == StatementExecutionResult.Status.FAILED) {
                metric.setMetricValue(BigDecimal.ZERO);
            }
            // Set threshold_value from rule severity config if available
            GovRule rule = run.getRule();
            if (rule != null) {
                String severity = rule.getSeverity();
                if (StringUtils.isNotBlank(severity)) {
                    BigDecimal threshold = switch (severity.toUpperCase(Locale.ROOT)) {
                        case "CRITICAL" -> BigDecimal.valueOf(99);
                        case "HIGH" -> BigDecimal.valueOf(95);
                        case "MEDIUM" -> BigDecimal.valueOf(90);
                        case "LOW" -> BigDecimal.valueOf(80);
                        default -> null;
                    };
                    metric.setThresholdValue(threshold);
                }
            }
            metricRepository.save(metric);
        }
    }

    private StatementExecutionResult.Status aggregateStatus(List<StatementExecutionResult> results) {
        boolean hasFailure = results.stream().anyMatch(res -> res.status() == StatementExecutionResult.Status.FAILED);
        if (hasFailure) {
            return StatementExecutionResult.Status.FAILED;
        }
        boolean allSkipped = results.stream().allMatch(res -> res.status() == StatementExecutionResult.Status.SKIPPED);
        if (allSkipped) {
            return StatementExecutionResult.Status.SKIPPED;
        }
        return StatementExecutionResult.Status.SUCCEEDED;
    }

    private String mapStatus(StatementExecutionResult.Status status) {
        return switch (status) {
            case FAILED -> "FAILED";
            case SKIPPED -> "SKIPPED";
            default -> "SUCCEEDED";
        };
    }

    private String resolveErrorCategory(List<StatementExecutionResult> results) {
        if (results == null || results.isEmpty()) {
            return null;
        }
        StatementExecutionResult failed = results
            .stream()
            .filter(item -> item != null && item.status() == StatementExecutionResult.Status.FAILED)
            .findFirst()
            .orElse(null);
        if (failed == null) {
            return null;
        }
        if (StringUtils.isNotBlank(failed.errorCode())) {
            return normalizeErrorCode(failed.errorCode());
        }
        return resolveErrorCategory(failed.message());
    }

    private String resolveErrorCategory(String rawMessage) {
        String message = StringUtils.trimToEmpty(rawMessage).toLowerCase(Locale.ROOT);
        if (message.isEmpty()) {
            return "UNKNOWN";
        }
        if (message.contains("permission denied") || message.contains("access denied") || message.contains("not authorized")) {
            return "PERMISSION_DENIED";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "TIMEOUT";
        }
        if (message.contains("syntax error") || message.contains("parse exception") || message.contains("parser")) {
            return "SQL_SYNTAX";
        }
        if (
            message.contains("does not exist") ||
            message.contains("not found") ||
            message.contains("unknown table") ||
            message.contains("unknown column")
        ) {
            return "OBJECT_NOT_FOUND";
        }
        if (message.contains("connection refused") || message.contains("connection reset") || message.contains("connection closed")) {
            return "CONNECTION_ERROR";
        }
        return "EXECUTION_ERROR";
    }

    private String normalizeErrorCode(String errorCode) {
        String normalized = StringUtils.trimToEmpty(errorCode).toUpperCase(Locale.ROOT).replace('-', '_');
        return normalized.isEmpty() ? "UNKNOWN" : normalized;
    }

    private String summaryMessage(List<StatementExecutionResult> results) {
        long failed = results.stream().filter(res -> res.status() == StatementExecutionResult.Status.FAILED).count();
        long skipped = results.stream().filter(res -> res.status() == StatementExecutionResult.Status.SKIPPED).count();
        if (failed > 0) {
            return "存在" + failed + "个检测失败";
        }
        if (skipped == results.size()) {
            return "Hive 执行未开启，已跳过";
        }
        return "执行成功";
    }

    private String writeMetrics(List<StatementExecutionResult> results) {
        return writeJson(results);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
