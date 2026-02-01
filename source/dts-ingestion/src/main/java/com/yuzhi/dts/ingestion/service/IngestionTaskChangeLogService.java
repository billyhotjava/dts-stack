package com.yuzhi.dts.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskChangeLog;
import com.yuzhi.dts.ingestion.repository.IngestionTaskChangeLogRepository;
import com.yuzhi.dts.ingestion.service.dto.IngestionTaskChangeLogDTO;
import com.yuzhi.dts.ingestion.service.mapper.IngestionTaskChangeLogMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class IngestionTaskChangeLogService {

    private static final Logger LOG = LoggerFactory.getLogger(IngestionTaskChangeLogService.class);

    public static final String OBJ_TYPE_INGEST_JOB = "INGEST_JOB";
    public static final String CHANGE_TASK_CREATE = "TASK_CREATE";
    public static final String CHANGE_CONN_PARAM = "CONN_PARAM";
    public static final String CHANGE_CATALOG = "CATALOG_CHANGE";
    public static final String CHANGE_SCHEDULE = "SCHEDULE_CHANGE";
    public static final String CHANGE_TASK_UPDATE = "TASK_UPDATE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_DONE = "DONE";

    private final IngestionTaskChangeLogRepository repository;
    private final IngestionTaskChangeLogMapper mapper;
    private final ObjectMapper objectMapper;

    public IngestionTaskChangeLogService(
        IngestionTaskChangeLogRepository repository,
        IngestionTaskChangeLogMapper mapper,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public Page<IngestionTaskChangeLogDTO> search(
        Long taskId,
        String objType,
        String changeType,
        String status,
        String keyword,
        Pageable pageable
    ) {
        String cleanObjType = normalize(objType);
        String cleanChangeType = normalize(changeType);
        String cleanStatus = normalize(status);
        String cleanKeyword = normalize(keyword);
        Pageable safePageable = pageable == null
            ? Pageable.unpaged()
            : org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        return repository
            .search(
                taskId,
                cleanObjType,
                cleanChangeType,
                cleanStatus,
                cleanKeyword,
                safePageable
            )
            .map(mapper::toDto);
    }

    public IngestionTaskChangeLogDTO createManualLog(IngestionTaskChangeLogDTO dto) {
        IngestionTaskChangeLog entity = new IngestionTaskChangeLog();
        entity.setTaskId(dto.getTaskId());
        entity.setTaskName(dto.getTaskName());
        entity.setObjType(StringUtils.hasText(dto.getObjType()) ? dto.getObjType() : OBJ_TYPE_INGEST_JOB);
        entity.setChangeType(dto.getChangeType());
        entity.setSummary(dto.getSummary());
        entity.setDetail(dto.getDetail());
        entity.setRiskLevel(dto.getRiskLevel());
        entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : STATUS_PENDING);
        return mapper.toDto(repository.save(entity));
    }

    public Optional<IngestionTaskChangeLogDTO> recordTaskCreate(IngestionTask task) {
        if (task == null) {
            return Optional.empty();
        }
        IngestionTaskChangeLog entity = new IngestionTaskChangeLog();
        entity.setTaskId(task.getId());
        entity.setTaskName(task.getName());
        entity.setObjType(OBJ_TYPE_INGEST_JOB);
        entity.setChangeType(CHANGE_TASK_CREATE);
        entity.setSummary("创建入湖任务");
        entity.setRiskLevel("L");
        entity.setStatus(STATUS_DONE);
        return Optional.of(mapper.toDto(repository.save(entity)));
    }

    public Optional<IngestionTaskChangeLogDTO> recordTaskUpdate(IngestionTask before, IngestionTask after) {
        ChangeSummary summary = buildChangeSummary(before, after);
        if (summary == null) {
            return Optional.empty();
        }
        IngestionTaskChangeLog entity = new IngestionTaskChangeLog();
        entity.setTaskId(after.getId());
        entity.setTaskName(after.getName());
        entity.setObjType(OBJ_TYPE_INGEST_JOB);
        entity.setChangeType(summary.changeType());
        entity.setSummary(summary.summary());
        entity.setDetail(summary.detail());
        entity.setRiskLevel(summary.riskLevel());
        entity.setStatus(STATUS_DONE);
        return Optional.of(mapper.toDto(repository.save(entity)));
    }

    private ChangeSummary buildChangeSummary(IngestionTask before, IngestionTask after) {
        if (before == null || after == null) {
            return null;
        }
        List<String> blocks = new ArrayList<>();
        List<String> fields = new ArrayList<>();

        if (!Objects.equals(before.getSourceType(), after.getSourceType())
            || !Objects.equals(before.getSourceDataSourceId(), after.getSourceDataSourceId())
            || !Objects.equals(before.getSourceConfig(), after.getSourceConfig())
            || !Objects.equals(before.getDestinationType(), after.getDestinationType())
            || !Objects.equals(before.getDestinationConfig(), after.getDestinationConfig())) {
            blocks.add("连接参数");
            fields.add("source/destination");
        }
        if (!Objects.equals(before.getTableMapping(), after.getTableMapping())
            || !Objects.equals(before.getSyncMode(), after.getSyncMode())
            || !Objects.equals(before.getSyncSchedule(), after.getSyncSchedule())) {
            blocks.add("同步范围");
            fields.add("mapping/sync");
        }
        if (!Objects.equals(before.getAirflowEnabled(), after.getAirflowEnabled())
            || !Objects.equals(before.getAirflowDagId(), after.getAirflowDagId())
            || !Objects.equals(before.getDbtModelSelector(), after.getDbtModelSelector())
            || !Objects.equals(before.getDbtDagSelector(), after.getDbtDagSelector())) {
            blocks.add("调度配置");
            fields.add("airflow");
        }
        if (!Objects.equals(before.getAddaxConfig(), after.getAddaxConfig())) {
            blocks.add("Addax 参数");
            fields.add("addaxConfig");
        }
        if (!Objects.equals(before.getName(), after.getName())
            || !Objects.equals(before.getDescription(), after.getDescription())) {
            blocks.add("基础信息");
            fields.add("basic");
        }

        if (blocks.isEmpty()) {
            return null;
        }

        String changeType = resolveChangeType(blocks);
        String summary = "更新入湖任务配置：" + String.join("、", blocks);
        String detail = toJson(Map.of("fields", fields));
        String risk = resolveRisk(changeType);
        return new ChangeSummary(changeType, summary, detail, risk);
    }

    private String resolveChangeType(List<String> blocks) {
        if (blocks.contains("连接参数")) {
            return CHANGE_CONN_PARAM;
        }
        if (blocks.contains("同步范围")) {
            return CHANGE_CATALOG;
        }
        if (blocks.contains("调度配置")) {
            return CHANGE_SCHEDULE;
        }
        return CHANGE_TASK_UPDATE;
    }

    private String resolveRisk(String changeType) {
        if (CHANGE_CONN_PARAM.equals(changeType) || CHANGE_CATALOG.equals(changeType) || CHANGE_SCHEDULE.equals(changeType)) {
            return "M";
        }
        return "L";
    }

    private String toJson(Map<String, Object> detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception ex) {
            LOG.debug("Failed to serialize change detail", ex);
            return null;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    public void deleteByTaskId(Long taskId) {
        if (taskId == null) {
            return;
        }
        repository.deleteByTaskId(taskId);
    }

    private record ChangeSummary(String changeType, String summary, String detail, String riskLevel) {}
}
