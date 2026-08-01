package com.yuzhi.dts.ingestion.web.rest;

import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.repository.IngestionExecutionRepository;
import com.yuzhi.dts.ingestion.service.IngestionTaskService;
import com.yuzhi.dts.ingestion.service.dto.IngestionExecutionDTO;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/api-ingestion")
public class InternalApiIngestionResource {

    private static final String INTERNAL_SERVICE_EXPRESSION =
        "hasAuthority(T(com.yuzhi.dts.ingestion.security.AuthoritiesConstants).OP_ADMIN)";

    private final IngestionTaskService taskService;
    private final IngestionExecutionRepository executionRepository;

    public InternalApiIngestionResource(IngestionTaskService taskService, IngestionExecutionRepository executionRepository) {
        this.taskService = taskService;
        this.executionRepository = executionRepository;
    }

    @PostMapping("/executions")
    @PreAuthorize(INTERNAL_SERVICE_EXPRESSION)
    public ResponseEntity<Map<String, Object>> startExecution(@RequestBody Map<String, Object> request) {
        Long taskId = requiredLong(request, "taskId");
        Instant windowStart = optionalInstant(request, "backfillWindowStart", "backfill_window_start");
        Instant windowEnd = optionalInstant(request, "backfillWindowEnd", "backfill_window_end");
        String configChecksum = text(request, "configChecksum", "config_checksum");
        String airflowDagId = text(request, "airflowDagId", "airflow_dag_id");
        String airflowRunId = text(request, "airflowRunId", "airflow_run_id");
        boolean exactRevisionRequested = request != null && (
            request.containsKey("revisionId")
                || request.containsKey("revision_id")
                || StringUtils.hasText(configChecksum)
                || StringUtils.hasText(airflowDagId)
                || StringUtils.hasText(airflowRunId)
        );
        IngestionExecutionDTO submitted = exactRevisionRequested
            ? taskService.executeInternalApiForRevision(
                taskId,
                text(request, "batchId", "batch_id"),
                text(request, "mode", "triggerMode", "trigger_mode"),
                windowStart,
                windowEnd,
                text(request, "backfillCursorColumn", "backfill_column", "backfillColumn"),
                requiredLong(request, "revisionId", "revision_id"),
                requiredText(request, "configChecksum", "config_checksum"),
                requiredText(request, "airflowDagId", "airflow_dag_id"),
                requiredText(request, "airflowRunId", "airflow_run_id")
            )
            : taskService.executeInternalApi(
                taskId,
                text(request, "batchId", "batch_id"),
                text(request, "mode", "triggerMode", "trigger_mode"),
                windowStart,
                windowEnd,
                text(request, "backfillCursorColumn", "backfill_column", "backfillColumn")
            );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toResponse(submitted));
    }

    @PostMapping("/scheduled-executions")
    @PreAuthorize(INTERNAL_SERVICE_EXPRESSION)
    public ResponseEntity<Map<String, Object>> registerScheduledExecution(@RequestBody Map<String, Object> request) {
        IngestionExecutionDTO execution = taskService.registerScheduledExecution(
            requiredLong(request, "taskId", "task_id"),
            requiredLong(request, "revisionId", "revision_id"),
            requiredText(request, "configChecksum", "config_checksum"),
            requiredText(request, "airflowDagId", "airflow_dag_id"),
            requiredText(request, "airflowRunId", "airflow_run_id")
        );
        return ResponseEntity.ok(toResponse(execution));
    }

    @GetMapping("/executions/{executionId}")
    @PreAuthorize(INTERNAL_SERVICE_EXPRESSION)
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable String executionId) {
        IngestionExecution execution = findExecution(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Execution not found: " + executionId));
        return ResponseEntity.ok(toResponse(execution));
    }

    private Optional<IngestionExecution> findExecution(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        String normalized = token.trim();
        try {
            Optional<IngestionExecution> byId = executionRepository.findById(Long.parseLong(normalized));
            if (byId.isPresent()) {
                return byId;
            }
        } catch (NumberFormatException ignored) {
            // Fall through to execution_id lookup for API runner ids.
        }
        return executionRepository.findByExecutionId(normalized);
    }

    private Map<String, Object> toResponse(IngestionExecutionDTO execution) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", execution.getId());
        body.put("taskId", execution.getTaskId());
        body.put("executionId", execution.getExecutionId());
        body.put("batchId", execution.getBatchId());
        body.put("status", execution.getStatus());
        body.put("triggerMode", execution.getTriggerMode());
        body.put("backfillWindowStart", execution.getBackfillWindowStart());
        body.put("backfillWindowEnd", execution.getBackfillWindowEnd());
        body.put("backfillColumn", execution.getBackfillColumn());
        body.put("rowsRead", execution.getRowsRead());
        body.put("rowsWritten", execution.getRowsWritten());
        body.put("errorMessage", execution.getErrorMessage());
        body.put("failureCategory", execution.getFailureCategory());
        body.put("failureAdvice", execution.getFailureAdvice());
        return body;
    }

    private Map<String, Object> toResponse(IngestionExecution execution) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", execution.getId());
        body.put("taskId", execution.getTask() == null ? null : execution.getTask().getId());
        body.put("taskName", execution.getTask() == null ? null : execution.getTask().getName());
        body.put("executionId", execution.getExecutionId());
        body.put("batchId", execution.getBatchId());
        body.put("status", execution.getStatus());
        body.put("startTime", execution.getStartTime());
        body.put("endTime", execution.getEndTime());
        body.put("rowsRead", execution.getRowsRead());
        body.put("rowsWritten", execution.getRowsWritten());
        body.put("errorMessage", execution.getErrorMessage());
        body.put("failureCategory", execution.getFailureCategory());
        body.put("failureAdvice", execution.getFailureAdvice());
        body.put("triggerMode", execution.getTriggerMode());
        body.put("backfillWindowStart", execution.getBackfillWindowStart());
        body.put("backfillWindowEnd", execution.getBackfillWindowEnd());
        body.put("backfillColumn", execution.getBackfillColumn());
        return body;
    }

    private Long requiredLong(Map<String, Object> request, String... fields) {
        Object value = null;
        String matchedField = fields == null || fields.length == 0 ? "value" : fields[0];
        if (request != null && fields != null) {
            for (String field : fields) {
                if (request.containsKey(field)) {
                    value = request.get(field);
                    matchedField = field;
                    break;
                }
            }
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null && StringUtils.hasText(value.toString())) {
            try {
                return Long.parseLong(value.toString().trim());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, matchedField + " must be a number", ex);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, matchedField + " is required");
    }

    private String requiredText(Map<String, Object> request, String... fields) {
        String value = text(request, fields);
        if (StringUtils.hasText(value)) {
            return value;
        }
        String field = fields == null || fields.length == 0 ? "value" : fields[0];
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
    }

    private Instant optionalInstant(Map<String, Object> request, String... keys) {
        String value = text(request, keys);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid instant: " + value, ex);
        }
    }

    private String text(Map<String, Object> request, String... keys) {
        if (request == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Object value = request.get(key);
            if (value != null && StringUtils.hasText(value.toString())) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
