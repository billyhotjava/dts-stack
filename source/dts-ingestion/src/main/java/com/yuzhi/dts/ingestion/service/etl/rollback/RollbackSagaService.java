package com.yuzhi.dts.ingestion.service.etl.rollback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackAffectedObject;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOperation;
import com.yuzhi.dts.ingestion.domain.IngestionRollbackOutbox;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackAffectedObjectRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOperationRepository;
import com.yuzhi.dts.ingestion.repository.IngestionRollbackOutboxRepository;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RollbackSagaService {

    public static final String OUTCOME_APPLY = "APPLY";
    public static final String OUTCOME_ABORT_NO_SIDE_EFFECT = "ABORT_NO_SIDE_EFFECT";

    private final IngestionRollbackOperationRepository operationRepository;
    private final IngestionRollbackAffectedObjectRepository affectedObjectRepository;
    private final IngestionRollbackOutboxRepository outboxRepository;
    private final IngestionTaskRepository taskRepository;
    private final RollbackAuditService auditService;
    private final ObjectMapper objectMapper;

    public RollbackSagaService(
        IngestionRollbackOperationRepository operationRepository,
        IngestionRollbackAffectedObjectRepository affectedObjectRepository,
        IngestionRollbackOutboxRepository outboxRepository,
        IngestionTaskRepository taskRepository,
        RollbackAuditService auditService,
        ObjectMapper objectMapper
    ) {
        this.operationRepository = operationRepository;
        this.affectedObjectRepository = affectedObjectRepository;
        this.outboxRepository = outboxRepository;
        this.taskRepository = taskRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public BeginDecision begin(
        RollbackRequest request,
        RollbackImpact impact,
        String operator,
        List<RollbackAffectedObjectEvidence> plannedObjects
    ) {
        String idempotencyKey = request.idempotencyKey().trim();
        operationRepository.lockIdempotencyKey(idempotencyKey);
        String payloadHash = hashJson(request);
        IngestionRollbackOperation existing = operationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            requireSameCommand(existing, request, payloadHash);
            if (existing.getResultJson() != null) {
                return new BeginDecision(false, readResult(existing).withCompletion(
                    existing.getReceiptId(),
                    existing.getOutcome(),
                    existing.getCompletionEventId(),
                    completionPending(existing),
                    true
                ));
            }
            throw new RollbackCommandInProgressException(existing.getReceiptId());
        }

        Instant now = Instant.now();
        IngestionRollbackOperation operation = new IngestionRollbackOperation();
        operation.setReceiptId(request.rollbackId());
        operation.setIdempotencyKey(idempotencyKey);
        operation.setRequestHash(request.requestHash().trim().toLowerCase());
        operation.setPayloadHash(payloadHash);
        operation.setLevel(request.level());
        operation.setScope(request.scope());
        operation.setTaskId(request.taskId());
        operation.setSourceDataSourceId(request.availabilityFence().sourceDataSourceId());
        operation.setFenceSequence(request.availabilityFence().sourceSequence());
        operation.setOperator(bound(operator, 128));
        operation.setStatus("EXECUTING");
        operation.setSideEffectsApplied(false);
        operation.setRequestJson(writeJson(request));
        operation.setImpactJson(writeJson(impact));
        operation.setCreatedAt(now);
        operation.setUpdatedAt(now);
        operationRepository.saveAndFlush(operation);

        List<IngestionRollbackAffectedObject> planned = plannedObjects.stream()
            .map(evidence -> evidenceEntity(request.rollbackId(), "PLANNED", evidence, now))
            .toList();
        affectedObjectRepository.saveAllAndFlush(planned);
        return new BeginDecision(true, null);
    }

    @Transactional
    public RollbackResult complete(
        RollbackRequest request,
        RollbackImpact impact,
        String operator,
        RollbackResult physicalResult,
        List<RollbackAffectedObjectEvidence> evidence
    ) {
        IngestionRollbackOperation operation = operationRepository.findForUpdate(request.rollbackId())
            .orElseThrow(() -> new IllegalStateException("ROLLBACK_RECEIPT_NOT_FOUND: " + request.rollbackId()));
        String payloadHash = hashJson(request);
        requireSameCommand(operation, request, payloadHash);
        if (operation.getResultJson() != null) {
            return readResult(operation).withCompletion(
                operation.getReceiptId(),
                operation.getOutcome(),
                operation.getCompletionEventId(),
                completionPending(operation),
                true
            );
        }

        List<String> actions = new ArrayList<>(physicalResult.actions());
        List<String> errors = new ArrayList<>(physicalResult.errors());
        List<RollbackAffectedObjectEvidence> resultEvidence = new ArrayList<>(evidence);
        LocalStateOutcome localState = applyLocalTaskState(
            request.level(),
            physicalResult,
            actions,
            errors,
            resultEvidence
        );
        boolean success = physicalResult.success() && localState.complete();
        boolean sideEffectsApplied = physicalResult.sideEffectsApplied() || localState.applied();
        String sideEffectStatus = sideEffectsApplied ? (success ? "APPLIED" : "PARTIAL") : "NONE";
        String outcome = sideEffectsApplied ? OUTCOME_APPLY : OUTCOME_ABORT_NO_SIDE_EFFECT;
        long completionSequence = Math.addExact(operation.getFenceSequence(), 1L);
        String eventId = "rollback:" + operation.getReceiptId() + ":" + completionSequence;

        RollbackResult finalResult = new RollbackResult(
            success,
            actions,
            errors,
            physicalResult.affectedTaskIds(),
            sideEffectsApplied,
            sideEffectStatus,
            operation.getReceiptId(),
            outcome,
            eventId,
            true,
            false
        );

        Instant now = Instant.now();
        affectedObjectRepository.saveAll(
            resultEvidence.stream().map(item -> evidenceEntity(operation.getReceiptId(), "RESULT", item, now)).toList()
        );

        Map<String, Object> completionPayload = completionPayload(
            operation,
            finalResult,
            eventId,
            completionSequence,
            outcome
        );
        String payloadJson = writeJson(completionPayload);
        String eventHash = sha256(payloadJson);
        IngestionRollbackOutbox outbox = new IngestionRollbackOutbox();
        outbox.setId(UUID.randomUUID());
        outbox.setOperationReceiptId(operation.getReceiptId());
        outbox.setEventId(eventId);
        outbox.setEventHash(eventHash);
        outbox.setSourceSequence(completionSequence);
        outbox.setOutcome(outcome);
        outbox.setPayloadJson(payloadJson);
        outbox.setStatus("PENDING");
        outbox.setAttemptCount(0);
        outbox.setNextAttemptAt(now);
        outbox.setCreatedAt(now);
        outbox.setUpdatedAt(now);
        outboxRepository.save(outbox);

        operation.setCompletionSequence(completionSequence);
        operation.setCompletionEventId(eventId);
        operation.setOutcome(outcome);
        operation.setSideEffectsApplied(sideEffectsApplied);
        operation.setStatus(sideEffectsApplied ? "COMPLETION_PENDING" : "ABORT_PENDING");
        operation.setResultJson(writeJson(finalResult));
        operation.setFailureMessage(finalResult.errors().isEmpty() ? null : bound(String.join("; ", finalResult.errors()), 4096));
        operation.setUpdatedAt(now);
        operationRepository.save(operation);

        RollbackAuditReason reason = sideEffectsApplied
            ? (finalResult.success() ? RollbackAuditReason.ROLLBACK_APPLIED : RollbackAuditReason.ROLLBACK_PARTIAL)
            : RollbackAuditReason.ROLLBACK_REJECTED;
        auditService.recordCommitted(
            operation.getReceiptId(),
            eventHash,
            reason,
            operator,
            RollbackLevel.fromCode(request.level()),
            request.scope(),
            request.taskId(),
            operation.getSourceDataSourceId(),
            request,
            impact,
            finalResult,
            finalResult.status(),
            finalResult.errors().isEmpty() ? null : String.join("; ", finalResult.errors())
        );
        return finalResult;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markUnexpectedFailure(
        RollbackRequest request,
        RollbackImpact impact,
        String operator,
        RuntimeException failure
    ) {
        operationRepository.findForUpdate(request.rollbackId()).ifPresent(operation -> {
            operation.setStatus("RECONCILIATION_REQUIRED");
            operation.setFailureMessage(bound(failure.getMessage(), 4096));
            operation.setUpdatedAt(Instant.now());
            operationRepository.save(operation);
        });
        auditService.recordFailure(
            request.rollbackId(),
            RollbackAuditReason.ROLLBACK_FAILED,
            operator,
            RollbackLevel.fromCode(request.level()),
            request.scope(),
            request.taskId(),
            request.availabilityFence().sourceDataSourceId(),
            request,
            impact,
            null,
            "FAILED",
            failure.getMessage()
        );
    }

    private LocalStateOutcome applyLocalTaskState(
        int level,
        RollbackResult result,
        List<String> actions,
        List<String> errors,
        List<RollbackAffectedObjectEvidence> evidence
    ) {
        boolean successfulFullCascade = result.success() && level == RollbackLevel.FULL_CASCADE.code();
        if (!result.sideEffectsApplied() && !successfulFullCascade) {
            return LocalStateOutcome.notRequired();
        }
        List<IngestionTask> tasks = taskRepository.findAllById(result.affectedTaskIds());
        java.util.Set<Long> found = tasks.stream().map(IngestionTask::getId).collect(java.util.stream.Collectors.toSet());
        List<Long> missing = result.affectedTaskIds().stream().filter(id -> !found.contains(id)).toList();
        if (!missing.isEmpty()) {
            errors.add("ROLLBACK_TASK_STATE_MISSING: " + missing);
        }
        int originalActionCount = actions.size();
        for (IngestionTask task : tasks) {
            if (!result.success() || !missing.isEmpty()) {
                task.setStatus("rollback_reconciliation_required");
                actions.add("TASK_RECONCILIATION_REQUIRED: " + task.getId());
                evidence.add(appliedTaskState(task, "RECONCILIATION_REQUIRED"));
            } else if (level == RollbackLevel.REBUILD_SCHEMA.code()) {
                task.setSyncMode("full_refresh");
                actions.add("TASK_FULL_REFRESH_REQUIRED: " + task.getId());
                evidence.add(appliedTaskState(task, "FULL_REFRESH_REQUIRED"));
            } else if (level == RollbackLevel.FULL_CASCADE.code()) {
                task.setStatus("deleted");
                actions.add("TASK_SOFT_DELETED: " + task.getId());
                evidence.add(appliedTaskState(task, "SOFT_DELETED"));
            }
        }
        taskRepository.saveAll(tasks);
        return new LocalStateOutcome(missing.isEmpty(), actions.size() > originalActionCount);
    }

    private RollbackAffectedObjectEvidence appliedTaskState(IngestionTask task, String state) {
        return new RollbackAffectedObjectEvidence(
            "INGESTION_TASK",
            String.valueOf(task.getId()),
            "STATE_CHANGE",
            "APPLIED",
            Map.of("state", state)
        );
    }

    private record LocalStateOutcome(boolean complete, boolean applied) {
        private static LocalStateOutcome notRequired() {
            return new LocalStateOutcome(true, false);
        }
    }

    private Map<String, Object> completionPayload(
        IngestionRollbackOperation operation,
        RollbackResult result,
        String eventId,
        long sourceSequence,
        String outcome
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("receiptId", operation.getReceiptId().toString());
        payload.put("eventId", eventId);
        payload.put("sourceSequence", sourceSequence);
        payload.put("outcome", outcome);
        payload.put("reason", result.success() ? "INGESTION_ROLLBACK_COMMITTED" : "INGESTION_ROLLBACK_INCOMPLETE");
        payload.put("zeroSideEffectsConfirmed", !result.sideEffectsApplied());
        payload.put("downstreamReference", "ingestion-rollback:" + operation.getReceiptId());
        return Map.copyOf(payload);
    }

    private void requireSameCommand(
        IngestionRollbackOperation existing,
        RollbackRequest request,
        String payloadHash
    ) {
        boolean same = existing.getReceiptId().equals(request.rollbackId())
            && existing.getRequestHash().equalsIgnoreCase(request.requestHash().trim())
            && existing.getPayloadHash().equals(payloadHash);
        if (!same) {
            throw new RollbackIdempotencyConflictException(existing.getReceiptId());
        }
    }

    private IngestionRollbackAffectedObject evidenceEntity(
        UUID receiptId,
        String phase,
        RollbackAffectedObjectEvidence evidence,
        Instant recordedAt
    ) {
        String evidenceJson = writeJson(evidence.evidence());
        IngestionRollbackAffectedObject entity = new IngestionRollbackAffectedObject();
        entity.setOperationReceiptId(receiptId);
        entity.setObjectType(bound(evidence.objectType(), 48));
        entity.setObjectRef(bound(evidence.objectRef(), 1024));
        entity.setAction(bound(evidence.action(), 48));
        entity.setPhase(phase);
        entity.setStatus(bound(evidence.status(), 32));
        entity.setEvidenceJson(evidenceJson);
        entity.setEvidenceHash(sha256(evidenceJson));
        entity.setRecordedAt(recordedAt);
        return entity;
    }

    private RollbackResult readResult(IngestionRollbackOperation operation) {
        try {
            return objectMapper.readValue(operation.getResultJson(), RollbackResult.class);
        } catch (Exception ex) {
            throw new IllegalStateException("ROLLBACK_RECEIPT_RESULT_CORRUPT: " + operation.getReceiptId(), ex);
        }
    }

    private boolean completionPending(IngestionRollbackOperation operation) {
        return !List.of("COMPLETED", "ABORTED", "RESTORED").contains(operation.getStatus());
    }

    public String hashJson(Object value) {
        return sha256(writeJson(value));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("ROLLBACK_PAYLOAD_NOT_SERIALIZABLE", ex);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private String bound(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record BeginDecision(boolean execute, RollbackResult replay) {}

    public static final class RollbackIdempotencyConflictException extends RuntimeException {
        public RollbackIdempotencyConflictException(UUID receiptId) {
            super("ROLLBACK_IDEMPOTENCY_CONFLICT: " + receiptId);
        }
    }

    public static final class RollbackCommandInProgressException extends RuntimeException {
        public RollbackCommandInProgressException(UUID receiptId) {
            super("ROLLBACK_COMMAND_IN_PROGRESS: " + receiptId);
        }
    }
}
