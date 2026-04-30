package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionIncrementalAudit;
import com.yuzhi.dts.ingestion.domain.IngestionIncrementalState;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionIncrementalAuditRepository;
import com.yuzhi.dts.ingestion.repository.IngestionIncrementalStateRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalAuditSummaryDTO;
import com.yuzhi.dts.ingestion.service.dto.IngestionIncrementalStateDTO;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class IncrementalSyncService {

    private static final Logger LOG = LoggerFactory.getLogger(IncrementalSyncService.class);
    private static final Pattern SAFE_SQL_IDENTIFIER = Pattern.compile("^[A-Za-z0-9_.$\"]+$");
    /** Watermark values (dates, numbers) must not contain SQL meta-characters. */
    private static final Pattern SAFE_WATERMARK_VALUE = Pattern.compile("^[A-Za-z0-9_.:\\-+T /]+$");

    private final IngestionIncrementalStateRepository stateRepository;
    private final IngestionIncrementalAuditRepository auditRepository;
    private final IngestionSourceResolver sourceResolver;
    private final JdbcMetadataService jdbcMetadataService;
    private final ObjectMapper objectMapper;

    public IncrementalSyncService(
        IngestionIncrementalStateRepository stateRepository,
        IngestionIncrementalAuditRepository auditRepository,
        IngestionSourceResolver sourceResolver,
        JdbcMetadataService jdbcMetadataService,
        ObjectMapper objectMapper
    ) {
        this.stateRepository = stateRepository;
        this.auditRepository = auditRepository;
        this.sourceResolver = sourceResolver;
        this.jdbcMetadataService = jdbcMetadataService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildReaderRuntimeOverrides(
        IngestionTask task,
        IngestionSourceResolver.ResolvedSource resolvedSource
    ) {
        if (!isIncrementalTask(task)) {
            return Map.of();
        }
        if (isFileSourceType(task.getSourceType())) {
            throw new IllegalStateException("文件源不支持增量同步，请改为全量同步");
        }
        IncrementalConfig config = parseConfig(task);
        if (!StringUtils.hasText(config.column())) {
            throw new IllegalStateException("增量同步缺少增量列配置");
        }
        if (hasQuerySql(task, resolvedSource)) {
            throw new IllegalStateException("增量同步暂不支持 querySql，请改为表模式并配置 readerWhere");
        }
        List<String> sourceTables = resolveSourceTables(task, resolvedSource);
        if (sourceTables.isEmpty()) {
            return Map.of();
        }
        String baseWhere = resolveBaseWhere(task, resolvedSource);
        Map<String, String> perTableWhere = new java.util.LinkedHashMap<>();
        for (String sourceTable : sourceTables) {
            String watermark = resolveWatermark(task.getId(), sourceTable, config.initialWatermark());
            if (!StringUtils.hasText(watermark)) {
                continue;
            }
            String incrementalCondition = config.column() + " > " + formatLiteral(config.type(), watermark);
            String combinedWhere = StringUtils.hasText(baseWhere)
                ? "(" + baseWhere + ") AND (" + incrementalCondition + ")"
                : incrementalCondition;
            perTableWhere.put(sourceTable, combinedWhere);
            perTableWhere.put(sourceTable.toLowerCase(Locale.ROOT), combinedWhere);
            String stripped = stripSchema(sourceTable);
            if (StringUtils.hasText(stripped)) {
                perTableWhere.put(stripped, combinedWhere);
                perTableWhere.put(stripped.toLowerCase(Locale.ROOT), combinedWhere);
            }
        }
        if (perTableWhere.isEmpty()) {
            LOG.info("[incremental] no watermark available task={}, skip runtime where", task.getId());
            return Map.of();
        }
        LOG.info("[incremental] runtime where prepared task={} tables={}", task.getId(), sourceTables.size());
        return Map.of("_perTableWhere", perTableWhere);
    }

    public String validateBackfillWindow(IngestionTask task, String column, Instant windowStart, Instant windowEnd) {
        if (task == null || task.getId() == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (!isIncrementalTask(task)) {
            throw new IllegalStateException("按时间范围补数仅支持增量任务");
        }
        if (isFileSourceType(task.getSourceType())) {
            throw new IllegalStateException("文件源不支持按时间范围补数");
        }
        if (windowStart == null || windowEnd == null) {
            throw new IllegalArgumentException("windowStart and windowEnd are required");
        }
        if (!windowStart.isBefore(windowEnd)) {
            throw new IllegalArgumentException("windowStart must be before windowEnd");
        }
        IncrementalConfig config = parseConfig(task);
        if (!isTimeWindowBackfillType(config.type())) {
            throw new IllegalStateException("按时间范围补数仅支持时间类型增量列；主键/数值增量请使用整批重跑");
        }
        String resolvedColumn = normalize(column);
        if (!StringUtils.hasText(resolvedColumn)) {
            resolvedColumn = config.column();
        }
        if (!StringUtils.hasText(resolvedColumn)) {
            throw new IllegalStateException("补数缺少增量列配置");
        }
        if (!SAFE_SQL_IDENTIFIER.matcher(resolvedColumn).matches()) {
            throw new IllegalStateException("补数字段包含非法字符");
        }
        return resolvedColumn;
    }

    public Map<String, Object> buildBackfillReaderRuntimeOverrides(
        IngestionTask task,
        IngestionSourceResolver.ResolvedSource resolvedSource,
        String column,
        Instant windowStart,
        Instant windowEnd
    ) {
        String resolvedColumn = validateBackfillWindow(task, column, windowStart, windowEnd);
        if (hasQuerySql(task, resolvedSource)) {
            throw new IllegalStateException("按时间范围补数暂不支持 querySql，请改为表模式并配置 readerWhere");
        }
        IncrementalConfig config = parseConfig(task);
        List<String> sourceTables = resolveSourceTables(task, resolvedSource);
        if (sourceTables.isEmpty()) {
            return Map.of();
        }
        String baseWhere = resolveBaseWhere(task, resolvedSource);
        String lower = resolvedColumn + " >= " + formatLiteral(config.type(), windowStart.toString());
        String upper = resolvedColumn + " < " + formatLiteral(config.type(), windowEnd.toString());
        String backfillWhere = "(" + lower + ") AND (" + upper + ")";
        Map<String, String> perTableWhere = new java.util.LinkedHashMap<>();
        for (String sourceTable : sourceTables) {
            String combinedWhere = StringUtils.hasText(baseWhere)
                ? "(" + baseWhere + ") AND (" + backfillWhere + ")"
                : backfillWhere;
            perTableWhere.put(sourceTable, combinedWhere);
            perTableWhere.put(sourceTable.toLowerCase(Locale.ROOT), combinedWhere);
            String stripped = stripSchema(sourceTable);
            if (StringUtils.hasText(stripped)) {
                perTableWhere.put(stripped, combinedWhere);
                perTableWhere.put(stripped.toLowerCase(Locale.ROOT), combinedWhere);
            }
        }
        LOG.info(
            "[backfill] runtime where prepared task={} tables={} column={} window=[{}, {})",
            task.getId(),
            sourceTables.size(),
            resolvedColumn,
            windowStart,
            windowEnd
        );
        return Map.of("_perTableWhere", perTableWhere);
    }

    public void updateCheckpointOnSuccess(IngestionTask task, IngestionExecution execution) {
        if (!isIncrementalTask(task) || task == null || task.getSourceDataSourceId() == null) {
            return;
        }
        if (execution != null && "BACKFILL_RANGE".equalsIgnoreCase(normalize(execution.getTriggerMode()))) {
            LOG.info("[incremental] skip checkpoint update for backfill execution task={} execution={}", task.getId(), execution.getId());
            return;
        }
        if (isFileSourceType(task.getSourceType())) {
            return;
        }
        try {
            IncrementalConfig config = parseConfig(task);
            if (!StringUtils.hasText(config.column())) {
                return;
            }
            IngestionSourceResolver.ResolvedSource resolvedSource = sourceResolver.resolve(task.getSourceDataSourceId(), List.of());
            List<String> sourceTables = resolveSourceTables(task, resolvedSource);
            if (sourceTables.isEmpty()) {
                return;
            }
            int updated = 0;
            for (String sourceTable : sourceTables) {
                String watermark = querySourceMaxWatermark(task.getSourceDataSourceId(), sourceTable, config.column());
                if (!StringUtils.hasText(watermark)) {
                    continue;
                }
                IngestionIncrementalState state = stateRepository
                    .findByTaskIdAndSourceTableIgnoreCase(task.getId(), sourceTable)
                    .orElseGet(IngestionIncrementalState::new);
                String beforeWatermark = state.getLastSuccessWatermark();
                if (state.getId() == null) {
                    state.setTaskId(task.getId());
                    state.setSourceTable(sourceTable);
                    state.setCreatedAt(Instant.now());
                }
                state.setLastSuccessWatermark(watermark);
                state.setLastRunId(execution == null ? null : execution.getExecutionId());
                state.setUpdatedAt(Instant.now());
                stateRepository.save(state);
                IngestionIncrementalAudit audit = new IngestionIncrementalAudit();
                audit.setTaskId(task.getId());
                audit.setExecutionId(execution == null ? null : execution.getId());
                audit.setExecutionRunId(execution == null ? null : execution.getExecutionId());
                audit.setSourceTable(sourceTable);
                audit.setIncrementalColumn(config.column());
                audit.setBeforeWatermark(beforeWatermark);
                audit.setAfterWatermark(watermark);
                audit.setAdvanced(!java.util.Objects.equals(normalize(beforeWatermark), normalize(watermark)));
                audit.setCreatedAt(Instant.now());
                auditRepository.save(audit);
                updated++;
            }
            if (updated > 0) {
                LOG.info("[incremental] checkpoint updated task={} tables={}", task.getId(), updated);
            }
        } catch (Exception ex) {
            LOG.warn("[incremental] checkpoint update skipped task={} err={}", task == null ? null : task.getId(), ex.getMessage());
        }
    }

    public void clearCheckpointByTaskId(Long taskId) {
        if (taskId == null) {
            return;
        }
        auditRepository.deleteByTaskId(taskId);
        stateRepository.deleteByTaskId(taskId);
    }

    public List<IngestionIncrementalStateDTO> listCheckpointStates(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return stateRepository.findByTaskIdOrderByUpdatedAtDescSourceTableAsc(taskId).stream().map(state -> {
            IngestionIncrementalStateDTO dto = new IngestionIncrementalStateDTO();
            dto.setId(state.getId());
            dto.setTaskId(state.getTaskId());
            dto.setSourceTable(state.getSourceTable());
            dto.setLastSuccessWatermark(state.getLastSuccessWatermark());
            dto.setLastRunId(state.getLastRunId());
            dto.setUpdatedAt(state.getUpdatedAt());
            dto.setCreatedAt(state.getCreatedAt());
            return dto;
        }).toList();
    }

    public List<IngestionIncrementalAuditDTO> listCheckpointAudits(Long taskId, Long executionId) {
        if (taskId == null) {
            return List.of();
        }
        List<IngestionIncrementalAudit> audits = executionId == null
            ? auditRepository.findByTaskIdOrderByCreatedAtDescSourceTableAsc(taskId)
            : auditRepository.findByTaskIdAndExecutionIdOrderByCreatedAtDescSourceTableAsc(taskId, executionId);
        return audits.stream().limit(500).map(this::toAuditDto).toList();
    }

    public Page<IngestionIncrementalAuditDTO> listCheckpointAuditsPage(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        Pageable pageable,
        String tableName,
        String status
    ) {
        if (taskId == null) {
            return Page.empty();
        }
        Pageable effectivePageable = pageable;
        if (effectivePageable == null || effectivePageable.isUnpaged()) {
            effectivePageable = org.springframework.data.domain.PageRequest.of(
                0,
                100,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("sourceTable"))
            );
        }
        Specification<IngestionIncrementalAudit> spec = buildAuditSpec(taskId, executionId, executionIds, from, to, tableName, status);
        Page<IngestionIncrementalAudit> page = auditRepository.findAll(spec, effectivePageable);
        List<IngestionIncrementalAuditDTO> content = page.getContent().stream().map(this::toAuditDto).toList();
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    public IngestionIncrementalAuditSummaryDTO summarizeCheckpointAudits(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        String tableName,
        String status
    ) {
        if (taskId == null) {
            return new IngestionIncrementalAuditSummaryDTO();
        }
        Specification<IngestionIncrementalAudit> spec = buildAuditSpec(taskId, executionId, executionIds, from, to, tableName, status);
        List<IngestionIncrementalAudit> audits = auditRepository.findAll(spec, Sort.by(Sort.Order.desc("createdAt")));
        IngestionIncrementalAuditSummaryDTO summary = new IngestionIncrementalAuditSummaryDTO();
        long total = audits.size();
        long advanced = audits.stream().filter(audit -> Boolean.TRUE.equals(audit.getAdvanced())).count();
        long unchanged = Math.max(0, total - advanced);
        summary.setTotal(total);
        summary.setAdvanced(advanced);
        summary.setUnchanged(unchanged);
        summary.setAdvancedRate(total <= 0 ? 0 : (int) Math.round((advanced * 100.0d) / total));
        return summary;
    }

    private Specification<IngestionIncrementalAudit> buildAuditSpec(
        Long taskId,
        Long executionId,
        Collection<Long> executionIds,
        Instant from,
        Instant to,
        String tableName,
        String status
    ) {
        Specification<IngestionIncrementalAudit> spec = (root, query, cb) -> cb.equal(root.get("taskId"), taskId);
        if (executionId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("executionId"), executionId));
        } else if (executionIds != null && !executionIds.isEmpty()) {
            spec = spec.and((root, query, cb) -> root.get("executionId").in(executionIds));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        String normalizedTableName = normalize(tableName);
        if (StringUtils.hasText(normalizedTableName)) {
            spec =
                spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("sourceTable")), "%" + normalizedTableName.toLowerCase(Locale.ROOT) + "%")
                );
        }
        Boolean advancedFilter = parseAdvancedStatus(status);
        if (advancedFilter != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("advanced"), advancedFilter));
        }
        return spec;
    }

    private Boolean parseAdvancedStatus(String status) {
        String normalized = normalize(status);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if ("advanced".equals(lower) || "changed".equals(lower) || "advanced_only".equals(lower)) {
            return Boolean.TRUE;
        }
        if ("unchanged".equals(lower) || "stale".equals(lower) || "unchanged_only".equals(lower)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private IngestionIncrementalAuditDTO toAuditDto(IngestionIncrementalAudit audit) {
        IngestionIncrementalAuditDTO dto = new IngestionIncrementalAuditDTO();
        dto.setId(audit.getId());
        dto.setTaskId(audit.getTaskId());
        dto.setExecutionId(audit.getExecutionId());
        dto.setExecutionRunId(audit.getExecutionRunId());
        dto.setSourceTable(audit.getSourceTable());
        dto.setIncrementalColumn(audit.getIncrementalColumn());
        dto.setBeforeWatermark(audit.getBeforeWatermark());
        dto.setAfterWatermark(audit.getAfterWatermark());
        dto.setAdvanced(audit.getAdvanced());
        dto.setCreatedAt(audit.getCreatedAt());
        return dto;
    }

    private boolean isIncrementalTask(IngestionTask task) {
        return task != null && "incremental".equalsIgnoreCase(normalize(task.getSyncMode()));
    }

    private boolean hasQuerySql(IngestionTask task, IngestionSourceResolver.ResolvedSource resolvedSource) {
        Map<String, Object> sourceConfig = jsonToMap(task == null ? null : task.getSourceConfig());
        if (hasNonEmpty(sourceConfig.get("querySql"))) {
            return true;
        }
        if (resolvedSource == null || resolvedSource.readerConfig() == null) {
            return false;
        }
        return hasNonEmpty(resolvedSource.readerConfig().get("querySql"));
    }

    private boolean hasNonEmpty(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String str) {
            return StringUtils.hasText(str);
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String resolveBaseWhere(IngestionTask task, IngestionSourceResolver.ResolvedSource resolvedSource) {
        Map<String, Object> sourceConfig = jsonToMap(task == null ? null : task.getSourceConfig());
        String where = normalize(sourceConfig.get("where"));
        if (StringUtils.hasText(where)) {
            return where;
        }
        if (resolvedSource == null || resolvedSource.readerConfig() == null) {
            return null;
        }
        return normalize(resolvedSource.readerConfig().get("where"));
    }

    private String resolveWatermark(Long taskId, String sourceTable, String initialWatermark) {
        if (taskId == null || !StringUtils.hasText(sourceTable)) {
            return normalize(initialWatermark);
        }
        Optional<IngestionIncrementalState> state = stateRepository.findByTaskIdAndSourceTableIgnoreCase(taskId, sourceTable);
        if (state.isPresent() && StringUtils.hasText(state.get().getLastSuccessWatermark())) {
            return state.get().getLastSuccessWatermark().trim();
        }
        return normalize(initialWatermark);
    }

    private String querySourceMaxWatermark(java.util.UUID dataSourceId, String sourceTable, String column) throws Exception {
        if (dataSourceId == null || !StringUtils.hasText(sourceTable) || !StringUtils.hasText(column)) {
            return null;
        }
        if (!SAFE_SQL_IDENTIFIER.matcher(sourceTable).matches() || !SAFE_SQL_IDENTIFIER.matcher(column).matches()) {
            throw new IllegalStateException("增量表名或列名包含非法字符");
        }
        JdbcMetadataService.JdbcConnectionInfo jdbcInfo = sourceResolver.resolveJdbcInfo(dataSourceId);
        try (Connection connection = jdbcMetadataService.openConnection(jdbcInfo);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(" + column + ") FROM " + sourceTable)) {
            if (!rs.next()) {
                return null;
            }
            String value = rs.getString(1);
            return StringUtils.hasText(value) ? value.trim() : null;
        }
    }

    private List<String> resolveSourceTables(IngestionTask task, IngestionSourceResolver.ResolvedSource resolvedSource) {
        List<String> tables = new ArrayList<>();
        tables.addAll(extractSourceTablesFromMapping(task == null ? null : task.getTableMapping()));
        tables.addAll(extractTables(jsonToMap(task == null ? null : task.getSourceConfig())));
        if (resolvedSource != null) {
            tables.addAll(extractTables(resolvedSource.readerConfig()));
        }
        return tables.stream()
            .map(this::normalize)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private List<String> extractSourceTablesFromMapping(JsonNode mappingNode) {
        if (mappingNode == null || !mappingNode.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        for (JsonNode item : mappingNode) {
            if (item == null || item.isNull()) {
                continue;
            }
            String source = normalize(item.get("source"));
            if (StringUtils.hasText(source)) {
                tables.add(source);
            }
        }
        return List.copyOf(tables);
    }

    private List<String> extractTables(Map<String, Object> config) {
        if (config == null || config.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> tables = new LinkedHashSet<>();
        collectTables(config.get("table"), tables);
        collectTables(config.get("tables"), tables);
        Object connObj = config.get("connection");
        if (connObj instanceof Map<?, ?> map) {
            collectTables(map.get("table"), tables);
            collectTables(map.get("tables"), tables);
        } else if (connObj instanceof Iterable<?> iterable) {
            for (Object entry : iterable) {
                if (entry instanceof Map<?, ?> map) {
                    collectTables(map.get("table"), tables);
                    collectTables(map.get("tables"), tables);
                }
            }
        }
        return List.copyOf(tables);
    }

    private void collectTables(Object tableObj, LinkedHashSet<String> sink) {
        if (tableObj == null || sink == null) {
            return;
        }
        if (tableObj instanceof String str) {
            String normalized = normalize(str);
            if (StringUtils.hasText(normalized)) {
                sink.add(normalized);
            }
            return;
        }
        if (tableObj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item == null) {
                    continue;
                }
                String normalized = normalize(item);
                if (StringUtils.hasText(normalized)) {
                    sink.add(normalized);
                }
            }
        }
    }

    private String formatLiteral(String type, String value) {
        String normalizedValue = normalize(value);
        if (!StringUtils.hasText(normalizedValue)) {
            return "NULL";
        }
        if (!SAFE_WATERMARK_VALUE.matcher(normalizedValue).matches()) {
            throw new IllegalStateException("增量水位值包含非法字符: " + normalizedValue);
        }
        String normalizedType = normalize(type);
        if ("number".equalsIgnoreCase(normalizedType) || "numeric".equalsIgnoreCase(normalizedType)) {
            return normalizedValue;
        }
        return "'" + normalizedValue.replace("'", "''") + "'";
    }

    private String stripSchema(String table) {
        String normalized = normalize(table);
        if (!StringUtils.hasText(normalized)) {
            return normalized;
        }
        int idx = normalized.lastIndexOf('.');
        if (idx > -1 && idx < normalized.length() - 1) {
            return normalized.substring(idx + 1);
        }
        return normalized;
    }

    private IncrementalConfig parseConfig(IngestionTask task) {
        Map<String, Object> raw = jsonToMap(task == null ? null : task.getSyncConfig());
        String column = normalize(raw.get("incrementalColumn"));
        String type = normalize(raw.get("incrementalType"));
        String initial = normalize(raw.get("initialWatermark"));
        if (!StringUtils.hasText(type)) {
            type = "datetime";
        }
        return new IncrementalConfig(column, type, initial);
    }

    private Map<String, Object> jsonToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean isFileSourceType(String sourceType) {
        String normalized = normalize(sourceType);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return "excel".equals(lower) || "csv".equals(lower) || "excelreader".equals(lower) || "txtfilereader".equals(lower);
    }

    private boolean isTimeWindowBackfillType(String type) {
        String normalized = normalize(type);
        if (!StringUtils.hasText(normalized)) {
            return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return !Set.of("number", "numeric", "integer", "bigint", "long", "int").contains(lower);
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    private record IncrementalConfig(String column, String type, String initialWatermark) {}
}
