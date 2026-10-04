package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionExecution;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackAffectedObject;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOutbox;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackAffectedObjectRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackRecoveryService {

    private final IngestionRollbackOperationRepository operationRepository;
    private final IngestionRollbackAffectedObjectRepository affectedObjectRepository;
    private final IngestionRollbackOutboxRepository outboxRepository;
    private final RollbackAuditService auditService;
    private final RollbackSagaService sagaService;
    private final ObjectMapper objectMapper;

    public RollbackRecoveryService(
        IngestionRollbackOperationRepository operationRepository,
        IngestionRollbackAffectedObjectRepository affectedObjectRepository,
        IngestionRollbackOutboxRepository outboxRepository,
        RollbackAuditService auditService,
        RollbackSagaService sagaService,
        ObjectMapper objectMapper
    ) {
        this.operationRepository = operationRepository;
        this.affectedObjectRepository = affectedObjectRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.sagaService = sagaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordSuccessfulRevalidation(IngestionTask task, IngestionExecution execution) {
        if (
            task == null ||
            task.getId() == null ||
            execution == null ||
            task.getSourceDataSourceId() == null ||
            !"success".equalsIgnoreCase(execution.getStatus()) ||
            execution.getEndTime() == null ||
            (execution.getId() == null && (execution.getExecutionId() == null || execution.getExecutionId().isBlank())) ||
            (execution.getTask() != null && !java.util.Objects.equals(execution.getTask().getId(), task.getId()))
        ) {
            return;
        }
        UUID sourceId = task.getSourceDataSourceId();
        operationRepository.lockIdempotencyKey("rollback-restore:" + sourceId);
        IngestionRollbackOperation operation = operationRepository
            .findFirstBySourceDataSourceIdAndStatusInOrderByCreatedAtDesc(sourceId, List.of("COMPLETED"))
            .orElse(null);
        if (operation == null || operation.getCompletionSequence() == null) {
            return;
        }
        if ("task".equals(operation.getScope()) && !java.util.Objects.equals(operation.getTaskId(), task.getId())) {
            return;
        }
        List<IngestionRollbackAffectedObject> loadedEvidence = affectedObjectRepository
            .findByOperationReceiptIdOrderByIdAsc(operation.getReceiptId());
        List<IngestionRollbackAffectedObject> existingEvidence = loadedEvidence == null ? List.of() : loadedEvidence;
        Set<Long> requiredTaskIds = requiredTaskIds(existingEvidence);
        if (task.getId() == null || requiredTaskIds.isEmpty() || !requiredTaskIds.contains(task.getId())) {
            return;
        }
        Set<RecoveryTable> requiredTables = requiredTables(existingEvidence);
        if (requiredTables == null) {
            return;
        }
        Instant landedAt = execution.getEndTime();
        if (!landedAt.isAfter(operation.getUpdatedAt())) {
            return;
        }

        String executionRef = execution.getExecutionId() == null
            ? "ingestion-execution:" + execution.getId()
            : "ingestion-execution:" + execution.getExecutionId();
        Instant now = Instant.now();
        List<IngestionRollbackAffectedObject> newEvidence = recoveryEvidence(
            operation,
            task,
            executionRef,
            landedAt,
            requiredTables,
            existingEvidence,
            now
        );
        if (!newEvidence.isEmpty()) {
            affectedObjectRepository.saveAll(newEvidence);
        }

        Set<Long> revalidatedTaskIds = revalidatedTaskIds(existingEvidence);
        revalidatedTaskIds.add(task.getId());
        Set<RecoveryTable> revalidatedTables = revalidatedTables(existingEvidence);
        requiredTables.stream()
            .filter(table -> table.taskId().equals(task.getId()))
            .forEach(revalidatedTables::add);
        Set<Long> pendingTaskIds = new HashSet<>(requiredTaskIds);
        pendingTaskIds.removeAll(revalidatedTaskIds);
        Set<RecoveryTable> pendingTables = new HashSet<>(requiredTables);
        pendingTables.removeAll(revalidatedTables);
        if (!pendingTaskIds.isEmpty() || !pendingTables.isEmpty()) {
            if (!newEvidence.isEmpty()) {
                Map<String, Object> progress = new LinkedHashMap<>();
                progress.put("execution", executionRef);
                progress.put("taskId", task.getId());
                progress.put("pendingTaskIds", pendingTaskIds.stream().sorted().toList());
                progress.put("pendingTables", pendingTables.stream().map(RecoveryTable::table).sorted().toList());
                auditService.recordCommitted(
                    operation.getReceiptId(),
                    sagaService.hashJson(progress),
                    RollbackAuditReason.SOURCE_REVALIDATION_PROGRESS,
                    "service:dts-ingestion",
                    RollbackLevel.fromCode(operation.getLevel()),
                    operation.getScope(),
                    operation.getTaskId(),
                    sourceId,
                    Map.of("execution", executionRef),
                    Map.of("requiredTaskCount", requiredTaskIds.size(), "requiredTableCount", requiredTables.size()),
                    progress,
                    "PENDING",
                    null
                );
            }
            return;
        }

        long restoreSequence = Math.addExact(operation.getCompletionSequence(), 1L);
        String eventId = "rollback:" + operation.getReceiptId() + ":restore:" + restoreSequence;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptId", operation.getReceiptId().toString());
        payload.put("eventId", eventId);
        payload.put("sourceSequence", restoreSequence);
        payload.put("outcome", "RESTORE");
        payload.put("reason", "INGESTION_SOURCE_REVALIDATED");
        payload.put("zeroSideEffectsConfirmed", false);
        payload.put("downstreamReference", executionRef);
        String payloadJson = writeJson(payload);
        String eventHash = sagaService.hashJson(payload);

        IngestionRollbackOutbox outbox = new IngestionRollbackOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setOperationReceiptId(operation.getReceiptId());
        outbox.setEventId(eventId);
        outbox.setEventHash(eventHash);
        outbox.setSourceSequence(restoreSequence);
        outbox.setOutcome("RESTORE");
        outbox.setPayloadJson(payloadJson);
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxRepository.save(outbox);

        operation.setCompletionSequence(restoreSequence);
        operation.setStatus("RESTORE_PENDING");
        operation.setUpdatedAt(now);
        operationRepository.save(operation);
        auditService.recordCommitted(
            operation.getReceiptId(),
            eventHash,
            RollbackAuditReason.SOURCE_AVAILABILITY_RESTORE,
            "service:dts-ingestion",
            RollbackLevel.fromCode(operation.getLevel()),
            operation.getScope(),
            operation.getTaskId(),
            sourceId,
            Map.of("execution", executionRef),
            Map.of("previousAvailability", "UNAVAILABLE"),
            Map.of("completionEventId", eventId, "outcome", "RESTORE"),
            "SUCCESS",
            null
        );
    }

    private List<IngestionRollbackAffectedObject> recoveryEvidence(
        IngestionRollbackOperation operation,
        IngestionTask task,
        String executionRef,
        Instant landedAt,
        Set<RecoveryTable> requiredTables,
        List<IngestionRollbackAffectedObject> existing,
        Instant now
    ) {
        List<IngestionRollbackAffectedObject> evidence = new ArrayList<>();
        if (!revalidatedTaskIds(existing).contains(task.getId())) {
            evidence.add(
                evidence(
                    operation,
                    "INGESTION_TASK",
                    String.valueOf(task.getId()),
                    executionRef,
                    landedAt,
                    task.getId(),
                    now
                )
            );
        }
        Set<RecoveryTable> revalidated = revalidatedTables(existing);
        requiredTables.stream()
            .filter(table -> table.taskId().equals(task.getId()))
            .filter(table -> !revalidated.contains(table))
            .map(
                table -> evidence(
                    operation,
                    "TABLE",
                    table.table(),
                    executionRef,
                    landedAt,
                    task.getId(),
                    now
                )
            )
            .forEach(evidence::add);
        return List.copyOf(evidence);
    }

    private IngestionRollbackAffectedObject evidence(
        IngestionRollbackOperation operation,
        String objectType,
        String objectRef,
        String executionRef,
        Instant landedAt,
        Long taskId,
        Instant now
    ) {
        Map<String, Object> evidencePayload = Map.of(
            "executionId", executionRef,
            "landedAt", landedAt.toString(),
            "sourceDataSourceId", operation.getSourceDataSourceId().toString(),
            "taskId", taskId,
            "objectType", objectType,
            "objectRef", objectRef
        );
        IngestionRollbackAffectedObject evidence = new IngestionRollbackAffectedObject();
        evidence.setOperationReceiptId(operation.getReceiptId());
        evidence.setObjectType(objectType);
        evidence.setObjectRef(objectRef);
        evidence.setAction("SOURCE_REVALIDATION");
        evidence.setPhase("RESULT");
        evidence.setStatus("REVALIDATED");
        evidence.setEvidenceJson(writeJson(evidencePayload));
        evidence.setEvidenceHash(sagaService.hashJson(evidencePayload));
        evidence.setRecordedAt(now);
        return evidence;
    }

    private Set<Long> requiredTaskIds(List<IngestionRollbackAffectedObject> evidence) {
        Set<Long> taskIds = new HashSet<>();
        evidence.stream()
            .filter(item -> "PLANNED".equals(item.getPhase()))
            .filter(item -> "INGESTION_TASK".equals(item.getObjectType()))
            .forEach(item -> parsePositiveLong(item.getObjectRef()).ifPresent(taskIds::add));
        return taskIds;
    }

    private Set<Long> revalidatedTaskIds(List<IngestionRollbackAffectedObject> evidence) {
        Set<Long> taskIds = new HashSet<>();
        evidence.stream()
            .filter(this::isRevalidationEvidence)
            .filter(item -> "INGESTION_TASK".equals(item.getObjectType()))
            .forEach(item -> parsePositiveLong(item.getObjectRef()).ifPresent(taskIds::add));
        return taskIds;
    }

    /** Returns null when a planned table lacks its immutable owning-task evidence. */
    private Set<RecoveryTable> requiredTables(List<IngestionRollbackAffectedObject> evidence) {
        Set<RecoveryTable> tables = new HashSet<>();
        for (IngestionRollbackAffectedObject item : evidence) {
            if (!"PLANNED".equals(item.getPhase()) || !"TABLE".equals(item.getObjectType())) {
                continue;
            }
            Long taskId = evidenceTaskId(item);
            if (taskId == null) {
                return null;
            }
            tables.add(new RecoveryTable(taskId, item.getObjectRef()));
        }
        return tables;
    }

    private Set<RecoveryTable> revalidatedTables(List<IngestionRollbackAffectedObject> evidence) {
        Set<RecoveryTable> tables = new HashSet<>();
        evidence.stream()
            .filter(this::isRevalidationEvidence)
            .filter(item -> "TABLE".equals(item.getObjectType()))
            .forEach(item -> {
                Long taskId = evidenceTaskId(item);
                if (taskId != null) {
                    tables.add(new RecoveryTable(taskId, item.getObjectRef()));
                }
            });
        return tables;
    }

    private boolean isRevalidationEvidence(IngestionRollbackAffectedObject item) {
        return "RESULT".equals(item.getPhase()) &&
            "SOURCE_REVALIDATION".equals(item.getAction()) &&
            "REVALIDATED".equals(item.getStatus());
    }

    private Long evidenceTaskId(IngestionRollbackAffectedObject item) {
        if (item.getEvidenceJson() == null) {
            return null;
        }
        try {
            var value = objectMapper.readTree(item.getEvidenceJson()).path("taskId");
            return value.canConvertToLong() && value.asLong() > 0 ? value.asLong() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private java.util.Optional<Long> parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? java.util.Optional.of(parsed) : java.util.Optional.empty();
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private record RecoveryTable(Long taskId, String table) {}

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("ROLLBACK_RESTORE_PAYLOAD_NOT_SERIALIZABLE", ex);
        }
    }
}
