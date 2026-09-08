package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityMetric;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityMetricRepository;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;

/** Read-side quality-run authorization, filtering, batching and response sanitization. */
final class QualityRunQueryService {

    private final GovQualityRunRepository runRepository;
    private final GovQualityMetricRepository metricRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final QualityDatasetReadGuard datasetReadGuard;

    QualityRunQueryService(
        GovQualityRunRepository runRepository,
        GovQualityMetricRepository metricRepository,
        CatalogDatasetRepository datasetRepository,
        QualityDatasetReadGuard datasetReadGuard
    ) {
        this.runRepository = runRepository;
        this.metricRepository = metricRepository;
        this.datasetRepository = datasetRepository;
        this.datasetReadGuard = datasetReadGuard;
    }

    QualityRunDto getRun(UUID runId, String activeDeptHeader) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        requireReadableRun(run, activeDeptHeader);
        return toSafeDto(run, metricRepository.findByRunId(runId));
    }

    void assertRunReadable(UUID runId, String activeDeptHeader) {
        GovQualityRun run = runRepository.findById(runId).orElseThrow(EntityNotFoundException::new);
        requireReadableRun(run, activeDeptHeader);
    }

    List<QualityRunDto> recentByRule(UUID ruleId, int limit, String activeDeptHeader) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return toSafeDtos(filterReadableRuns(runRepository.findByRuleId(ruleId, pageable), activeDeptHeader));
    }

    List<QualityRunDto> recentByDataset(UUID datasetId, int limit, String activeDeptHeader) {
        datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return toSafeDtos(runRepository.findByDatasetId(datasetId, pageable));
    }

    List<QualityRunDto> recent(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.Direction.DESC, "createdDate");
        return toSafeDtos(filterReadableRuns(runRepository.findAll(pageable).getContent(), null));
    }

    List<QualityRunDto> listRuns(
        UUID ruleId,
        UUID datasetId,
        String status,
        String triggerType,
        Instant startedFrom,
        Instant startedTo,
        int limit,
        String activeDeptHeader
    ) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        int querySize = Math.max(safeLimit, 200);
        Pageable pageable = PageRequest.of(0, querySize, Sort.Direction.DESC, "createdDate");
        List<GovQualityRun> candidates;
        if (ruleId != null) {
            candidates = runRepository.findByRuleId(ruleId, pageable);
        } else if (datasetId != null) {
            datasetReadGuard.requireReadable(datasetId, activeDeptHeader);
            candidates = runRepository.findByDatasetId(datasetId, pageable);
        } else {
            candidates = runRepository.findAll(pageable).getContent();
        }
        String normalizedStatus = StringUtils.trimToNull(status);
        String normalizedTriggerType = StringUtils.trimToNull(triggerType);
        List<GovQualityRun> filteredRuns = filterReadableRuns(candidates, activeDeptHeader)
            .stream()
            .filter(run -> normalizedStatus == null || normalizedStatus.equalsIgnoreCase(StringUtils.trimToEmpty(run.getStatus())))
            .filter(run -> normalizedTriggerType == null || normalizedTriggerType.equalsIgnoreCase(StringUtils.trimToEmpty(run.getTriggerType())))
            .filter(run -> inWindow(run, startedFrom, startedTo))
            .limit(safeLimit)
            .toList();
        return toSafeDtos(filteredRuns);
    }

    List<QualityRunDto> byWorkflow(UUID workflowRunId, String activeDeptHeader) {
        if (workflowRunId == null) {
            return Collections.emptyList();
        }
        return toSafeDtos(filterReadableRuns(runRepository.findByJobIdOrderByCreatedDateAsc(workflowRunId), activeDeptHeader));
    }

    List<QualityRunDto> byWorkflowTrusted(UUID workflowRunId, DefaultLakeDatasetGuard defaultLakeDatasetGuard) {
        if (workflowRunId == null) {
            return Collections.emptyList();
        }
        List<GovQualityRun> runs = runRepository.findByJobIdOrderByCreatedDateAsc(workflowRunId);
        runs.forEach(run -> defaultLakeDatasetGuard.requireDefaultLakeDataset(run.getDatasetId()));
        return toSafeDtos(runs);
    }

    List<QualityRunDto> findDtosByIds(List<UUID> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<GovQualityRun> runs = runIds
            .stream()
            .map(runRepository::findById)
            .flatMap(Optional::stream)
            .toList();
        return toSafeDtos(runs);
    }

    private boolean inWindow(GovQualityRun run, Instant startedFrom, Instant startedTo) {
        Instant pivot = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedDate();
        if (startedFrom != null && (pivot == null || pivot.isBefore(startedFrom))) {
            return false;
        }
        return startedTo == null || pivot == null || !pivot.isAfter(startedTo);
    }

    private void requireReadableRun(GovQualityRun run, String activeDeptHeader) {
        if (run == null || run.getDatasetId() == null) {
            throw new AccessDeniedException("质量运行缺少可授权的数据集");
        }
        datasetReadGuard.requireReadable(run.getDatasetId(), activeDeptHeader);
    }

    private List<GovQualityRun> filterReadableRuns(List<GovQualityRun> runs, String activeDeptHeader) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> datasetIds = runs
            .stream()
            .map(GovQualityRun::getDatasetId)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toSet());
        if (datasetIds.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> readableIds = datasetReadGuard.readableDatasetIds(
            datasetRepository.findAllById(datasetIds),
            activeDeptHeader
        );
        return runs.stream().filter(run -> readableIds.contains(run.getDatasetId())).toList();
    }

    private List<QualityRunDto> toSafeDtos(List<GovQualityRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return Collections.emptyList();
        }
        List<UUID> runIds = runs.stream().map(GovQualityRun::getId).filter(java.util.Objects::nonNull).toList();
        Map<UUID, List<GovQualityMetric>> metricsByRunId = metricRepository
            .findByRunIdIn(runIds)
            .stream()
            .filter(metric -> metric.getRun() != null && metric.getRun().getId() != null)
            .collect(Collectors.groupingBy(metric -> metric.getRun().getId()));
        return runs
            .stream()
            .map(run -> toSafeDto(run, metricsByRunId.getOrDefault(run.getId(), Collections.emptyList())))
            .toList();
    }

    QualityRunDto toSafeDto(GovQualityRun run, List<GovQualityMetric> metrics) {
        QualityRunDto dto = GovernanceMapper.toDto(run, metrics);
        if (dto == null) {
            return null;
        }
        dto.setInputParamsJson(null);
        dto.setMetricsJson(null);
        dto.setMessage(safeRunMessage(run));
        if (dto.getMetrics() != null) {
            dto.getMetrics().forEach(metric -> metric.setDetail(safeMetricDetail(parseMetricStatus(metric.getStatus()))));
        }
        return dto;
    }

    private StatementExecutionResult.Status parseMetricStatus(String status) {
        try {
            return StatementExecutionResult.Status.valueOf(StringUtils.trimToEmpty(status).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String safeMetricDetail(StatementExecutionResult.Status status) {
        if (status == null) {
            return "质量检测状态未知";
        }
        return switch (status) {
            case FAILED -> "质量检测项执行失败";
            case SKIPPED -> "质量检测项已跳过";
            case SUCCEEDED -> "质量检测项执行成功";
        };
    }

    private String safeRunMessage(GovQualityRun run) {
        if (run == null) {
            return null;
        }
        String status = StringUtils.trimToEmpty(run.getStatus()).toUpperCase(Locale.ROOT);
        return switch (status) {
            case "QUEUED" -> "质量检测已进入队列";
            case "RUNNING" -> "正在执行质量检测";
            case "SUCCEEDED" -> "质量检测执行成功";
            case "SKIPPED" -> "质量检测已跳过";
            case "FAILED" -> switch (StringUtils.trimToEmpty(run.getErrorCategory()).toUpperCase(Locale.ROOT)) {
                case "QUALITY_VIOLATION" -> "检测已完成，存在不符合规则的数据，请查看失败样本。";
                case "DATASET_SCOPE_BLOCKED" -> "检测 SQL 未通过绑定资产校验。请检查语法及函数是否受支持，并使用 schema.table 完整表名，仅引用当前检测资产。";
                case "WRITE_BLOCKED" -> "检测 SQL 未通过只读安全校验，请使用只读查询并移除写入或结构修改语句。";
                case "SQL_SYNTAX" -> "检测 SQL 语法错误，请检查语句、表达式和数据源方言。";
                case "OBJECT_NOT_FOUND" -> "检测引用的表或字段不存在，请核对当前资产字段及 schema.table 完整表名。";
                case "PERMISSION_DENIED" -> "检测数据源访问被拒绝，请联系管理员核对绑定数据源的读取权限。";
                case "TIMEOUT" -> "检测执行超时，请检查查询范围和数据源负载后重试。";
                case "CONNECTION_ERROR" -> "检测数据源连接失败，请检查数据源连接状态后重试。";
                default -> "质量检测未能完成，暂无有效统计。请核对检测配置和数据源状态，并使用运行编号定位原因。";
            };
            default -> "质量检测状态未知";
        };
    }
}
