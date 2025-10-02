package com.yuzhi.dts.platform.service.governance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.governance.GovRule;
import com.yuzhi.dts.platform.domain.governance.GovRuleBinding;
import com.yuzhi.dts.platform.domain.governance.GovRuleVersion;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleBindingRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleRepository;
import com.yuzhi.dts.platform.repository.governance.GovRuleVersionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.security.HiveStatementExecutor;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QualityRunService {

    private static final Logger log = LoggerFactory.getLogger(QualityRunService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final GovRuleRepository ruleRepository;
    private final GovRuleVersionRepository versionRepository;
    private final GovRuleBindingRepository bindingRepository;
    private final GovQualityRunRepository runRepository;
    private final GovQualityMetricRepository metricRepository;
    private final Executor taskExecutor;
    private final HiveStatementExecutor hiveExecutor;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final GovernanceProperties properties;

    public QualityRunService(
        GovRuleRepository ruleRepository,
        GovRuleVersionRepository versionRepository,
        GovRuleBindingRepository bindingRepository,
        GovQualityRunRepository runRepository,
        GovQualityMetricRepository metricRepository,
        @Qualifier("taskExecutor") Executor taskExecutor,
        HiveStatementExecutor hiveExecutor,
        AuditService auditService,
        ObjectMapper objectMapper,
        GovernanceProperties properties
    ) {
        this.ruleRepository = ruleRepository;
        this.versionRepository = versionRepository;
        this.bindingRepository = bindingRepository;
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.taskExecutor = taskExecutor;
        this.hiveExecutor = hiveExecutor;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional
    public List<QualityRunDto> trigger(QualityRunTriggerRequest request, String actor) {
        if (!properties.getQuality().isEnabled()) {
            throw new IllegalStateException("质量检测功能已禁用");
        }
        GovRule rule = resolveRule(request.getRuleId());
        GovRuleVersion version = resolveVersion(rule);
        List<GovRuleBinding> bindings = resolveBindings(version, request.getBindingId());
        if (bindings.isEmpty()) {
            throw new IllegalArgumentException("该规则尚未绑定数据集");
        }

        Map<String, Object> params = request.getParameters() != null ? request.getParameters() : Collections.emptyMap();
        List<QualityRunDto> runs = new ArrayList<>();
        for (GovRuleBinding binding : bindings) {
            GovQualityRun run = new GovQualityRun();
            run.setRule(rule);
            run.setRuleVersion(version);
            run.setBinding(binding);
            run.setDatasetId(binding.getDatasetId());
            run.setTriggerType(StringUtils.defaultIfBlank(request.getTriggerType(), "MANUAL"));
            run.setTriggerRef(actor);
            run.setStatus("QUEUED");
            run.setSeverity(rule.getSeverity());
            run.setDataLevel(rule.getDataLevel());
            run.setScheduledAt(Instant.now());
            runRepository.save(run);

            taskExecutor.execute(() -> executeRun(run.getId(), params));
            runs.add(GovernanceMapper.toDto(run, Collections.emptyList()));
        }
        auditService.audit("EXECUTE", "governance.quality.run",
            rule.getId() + ":" + runs.stream().map(dto -> dto.getId().toString()).collect(Collectors.joining(",")));
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

    @Transactional
    protected void executeRun(UUID runId, Map<String, Object> params) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        Instant start = Instant.now();
        run.setStatus("RUNNING");
        run.setStartedAt(start);
        run.setMessage("正在执行质量检测");
        runRepository.save(run);

        try {
            Map<String, String> statements = resolveStatements(run.getRuleVersion());
            if (statements.isEmpty()) {
                run.setStatus("SKIPPED");
                run.setFinishedAt(Instant.now());
                run.setMessage("未配置检测语句");
                runRepository.save(run);
                auditService.audit("SKIP", "governance.quality.run", runId.toString());
                return;
            }

            Map<String, String> rendered = renderParams(statements, params);
            List<StatementExecutionResult> results = hiveExecutor.execute(rendered, run.getRule() != null ? run.getRule().getOwner() : null);
            persistMetrics(run, results);
            StatementExecutionResult.Status aggregate = aggregateStatus(results);
            run.setStatus(mapStatus(aggregate));
            run.setMessage(summaryMessage(results));
            run.setFinishedAt(Instant.now());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            run.setMetricsJson(writeMetrics(results));
            runRepository.save(run);
            if (aggregate == StatementExecutionResult.Status.FAILED) {
                auditService.auditFailure("EXECUTE", "governance.quality.run", runId.toString(), results);
            } else {
                auditService.audit("EXECUTE", "governance.quality.run", runId.toString());
            }
        } catch (Exception ex) {
            log.error("Quality run failed: {}", ex.getMessage(), ex);
            run.setStatus("FAILED");
            run.setFinishedAt(Instant.now());
            run.setMessage(ex.getMessage());
            run.setDurationMs(java.time.Duration.between(start, run.getFinishedAt()).toMillis());
            runRepository.save(run);
            auditService.auditFailure("EXECUTE", "governance.quality.run", runId.toString(), ex.getMessage());
        }
    }

    private GovRule resolveRule(UUID ruleId) {
        if (ruleId == null) {
            throw new IllegalArgumentException("缺少规则ID");
        }
        return ruleRepository.findById(ruleId).orElseThrow(EntityNotFoundException::new);
    }

    private GovRuleVersion resolveVersion(GovRule rule) {
        GovRuleVersion version = rule.getLatestVersion();
        if (version == null) {
            version = versionRepository.findFirstByRuleIdOrderByVersionDesc(rule.getId()).orElse(null);
        }
        if (version == null) {
            throw new IllegalStateException("规则尚未发布版本");
        }
        return version;
    }

    private List<GovRuleBinding> resolveBindings(GovRuleVersion version, UUID bindingId) {
        if (bindingId != null) {
            Optional<GovRuleBinding> binding = bindingRepository.findById(bindingId);
            GovRuleBinding entity = binding.orElseThrow(() -> new IllegalArgumentException("未找到绑定"));
            if (entity.getRuleVersion() == null || !entity.getRuleVersion().getId().equals(version.getId())) {
                throw new IllegalArgumentException("绑定与规则版本不匹配");
            }
            return List.of(entity);
        }
        return new ArrayList<>(version.getBindings());
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
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception ex) {
            return null;
        }
    }
}
