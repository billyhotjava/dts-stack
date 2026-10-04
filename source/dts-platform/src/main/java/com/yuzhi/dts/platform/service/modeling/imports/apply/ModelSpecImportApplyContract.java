package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.MergeCheckpoint;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.RevisionPins;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable command, persistence, and response projections for model-package apply attempts. */
public final class ModelSpecImportApplyContract {

    public static final int MAX_SELECTED_UNIQUE_IDS = 200;
    public static final int MAX_UNIQUE_ID_LENGTH = 512;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 256;

    private ModelSpecImportApplyContract() {}

    public enum AttemptStatus {
        RUNNING,
        SUCCESS,
        PARTIAL,
        FAILED,
        BLOCKED,
    }

    public enum ResultStatus {
        CREATED,
        UPDATED,
        SKIPPED,
        FAILED,
        BLOCKED,
    }

    public enum BeginDisposition {
        STARTED,
        REPLAY,
        RUNNING,
    }

    public enum OperationType {
        APPLY,
        FORWARD_UNDO,
    }

    public enum Severity {
        ERROR,
        WARNING,
        INFO,
    }

    public enum Kind {
        BAD_REQUEST,
        FORBIDDEN,
        NOT_FOUND,
        GONE,
        CONFLICT,
        UNPROCESSABLE,
    }

    public record ApplyRequest(
        UUID runId,
        String previewHash,
        List<String> selectedUniqueIds,
        String idempotencyKey,
        Map<String, ConflictResolution> conflictResolutions
    ) {
        public ApplyRequest {
            Objects.requireNonNull(runId, "runId is required");
            previewHash = requestPreviewHash(previewHash);
            if (selectedUniqueIds != null && selectedUniqueIds.size() > MAX_SELECTED_UNIQUE_IDS) {
                throw new IllegalArgumentException("selectedUniqueIds cannot contain more than 200 candidates");
            }
            selectedUniqueIds = immutableStrings(selectedUniqueIds);
            if (selectedUniqueIds.stream().anyMatch(value -> value.length() > MAX_UNIQUE_ID_LENGTH)) {
                throw new IllegalArgumentException("selectedUniqueIds item cannot exceed 512 characters");
            }
            idempotencyKey = boundedRequestText(
                idempotencyKey,
                "idempotencyKey",
                MAX_IDEMPOTENCY_KEY_LENGTH
            );
            conflictResolutions = immutableConflictResolutions(conflictResolutions);
        }

        public ApplyRequest(
            UUID runId,
            String previewHash,
            List<String> selectedUniqueIds,
            String idempotencyKey
        ) {
            this(runId, previewHash, selectedUniqueIds, idempotencyKey, Map.of());
        }
    }

    public record RetryRequest(String previewHash, String idempotencyKey) {
        public RetryRequest {
            previewHash = requestPreviewHash(previewHash);
            idempotencyKey = boundedRequestText(
                idempotencyKey,
                "idempotencyKey",
                MAX_IDEMPOTENCY_KEY_LENGTH
            );
        }
    }

    /**
     * Repository command built only after the server has validated the stored preview and frozen
     * the selected dependency closure.
     */
    public record BeginCommand(
        UUID attemptId,
        UUID runId,
        UUID planId,
        UUID retrySourceAttemptId,
        String tenantId,
        String previewHash,
        List<String> selectedUniqueIds,
        List<String> selectedClosure,
        String idempotencyKey,
        String requestHash,
        String actorId,
        Instant startedAt,
        OperationType operationType,
        UUID targetAttemptId
    ) {
        public BeginCommand {
            Objects.requireNonNull(attemptId, "attemptId is required");
            Objects.requireNonNull(runId, "runId is required");
            Objects.requireNonNull(planId, "planId is required");
            tenantId = requiredText(tenantId, "tenantId");
            previewHash = requiredText(previewHash, "previewHash");
            selectedUniqueIds = immutableStrings(selectedUniqueIds);
            selectedClosure = immutableStrings(selectedClosure);
            if (selectedClosure.isEmpty()) {
                throw new IllegalArgumentException("selectedClosure is required");
            }
            if (!selectedClosure.containsAll(selectedUniqueIds)) {
                throw new IllegalArgumentException("selectedClosure must contain every selected unique id");
            }
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey");
            requestHash = requiredText(requestHash, "requestHash");
            actorId = requiredText(actorId, "actorId");
            Objects.requireNonNull(startedAt, "startedAt is required");
            operationType = operationType == null ? OperationType.APPLY : operationType;
            if (operationType == OperationType.APPLY && targetAttemptId != null) {
                throw new IllegalArgumentException("APPLY attempt cannot identify an undo target");
            }
            if (operationType == OperationType.FORWARD_UNDO && retrySourceAttemptId != null) {
                throw new IllegalArgumentException("FORWARD_UNDO attempt cannot identify an apply retry source");
            }
            if (operationType == OperationType.FORWARD_UNDO && targetAttemptId == null) {
                throw new IllegalArgumentException("FORWARD_UNDO attempt requires targetAttemptId");
            }
            if (attemptId.equals(targetAttemptId)) {
                throw new IllegalArgumentException("Attempt cannot target itself");
            }
        }

        public BeginCommand(
            UUID attemptId,
            UUID runId,
            UUID planId,
            UUID retrySourceAttemptId,
            String tenantId,
            String previewHash,
            List<String> selectedUniqueIds,
            List<String> selectedClosure,
            String idempotencyKey,
            String requestHash,
            String actorId,
            Instant startedAt
        ) {
            this(
                attemptId,
                runId,
                planId,
                retrySourceAttemptId,
                tenantId,
                previewHash,
                selectedUniqueIds,
                selectedClosure,
                idempotencyKey,
                requestHash,
                actorId,
                startedAt,
                OperationType.APPLY,
                null
            );
        }
    }

    public record BeginResult(BeginDisposition disposition, Attempt attempt, String ownerToken) {
        public BeginResult {
            Objects.requireNonNull(disposition, "disposition is required");
            Objects.requireNonNull(attempt, "attempt is required");
            if (disposition == BeginDisposition.STARTED) {
                ownerToken = requiredText(ownerToken, "ownerToken");
            } else {
                ownerToken = null;
            }
        }

        public BeginResult(BeginDisposition disposition, Attempt attempt) {
            this(
                disposition,
                attempt,
                disposition == BeginDisposition.STARTED ? UUID.randomUUID().toString() : null
            );
        }
    }

    public record ApplyIssue(
        String code,
        Severity severity,
        String stage,
        String category,
        boolean retryable,
        String fieldPath,
        String modelUniqueId,
        String dependencyUniqueId,
        String message,
        String recoveryAction,
        String correlationId
    ) {
        private static final Set<String> STAGES = Set.of(
            "INSPECT",
            "MAPPING",
            "PREVIEW",
            "APPLY",
            "RETRY",
            "UNDO"
        );
        private static final Set<String> CATEGORIES = Set.of(
            "VALIDATION",
            "SECURITY",
            "DEPENDENCY",
            "CONFLICT",
            "PERMISSION",
            "STALE",
            "PERSISTENCE",
            "INTERNAL"
        );
        private static final Set<String> RECOVERY_ACTIONS = Set.of(
            "REUPLOAD",
            "COMPLETE_MAPPING",
            "INCLUDE_DEPENDENCY",
            "RESOLVE_CONFLICT",
            "REAUTHORIZE",
            "REFRESH_PREVIEW",
            "RETRY",
            "OPEN_MODEL",
            "CONTACT_ADMIN",
            "NONE"
        );

        public ApplyIssue {
            code = requiredText(code, "code");
            Objects.requireNonNull(severity, "severity is required");
            stage = requiredEnum(stage, STAGES, "stage");
            category = requiredEnum(category, CATEGORIES, "category");
            fieldPath = optionalText(fieldPath);
            modelUniqueId = requiredText(modelUniqueId, "modelUniqueId");
            dependencyUniqueId = optionalText(dependencyUniqueId);
            if ("DEPENDENCY".equals(category) && dependencyUniqueId == null) {
                throw new IllegalArgumentException("dependencyUniqueId is required for DEPENDENCY issues");
            }
            message = requiredText(message, "message");
            recoveryAction = requiredEnum(recoveryAction, RECOVERY_ACTIONS, "recoveryAction");
            correlationId = requiredText(correlationId, "correlationId");
        }

        private static String requiredEnum(String value, Set<String> allowed, String field) {
            String normalized = requiredText(value, field);
            if (!allowed.contains(normalized)) {
                throw new IllegalArgumentException(field + " must be one of " + allowed);
            }
            return normalized;
        }

        private static String optionalText(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** Candidate outcome persisted after its independent transaction has committed or rolled back. */
    public record CandidateResult(
        UUID resultId,
        int sequence,
        String dbtUniqueId,
        String candidateIdempotencyKey,
        String candidateRequestHash,
        ResultStatus status,
        UUID modelSpecId,
        Integer revision,
        String modelChecksum,
        Integer implementationRevision,
        String implementationChecksum,
        int artifactCount,
        List<ApplyIssue> issues,
        Instant recordedAt,
        String appliedAction,
        String projectKey,
        MergeCheckpoint mergeCheckpoint,
        RevisionPins preAttemptPins,
        List<String> dependencyUniqueIds
    ) {
        public CandidateResult {
            Objects.requireNonNull(resultId, "resultId is required");
            if (sequence < 0) {
                throw new IllegalArgumentException("sequence cannot be negative");
            }
            dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId");
            candidateIdempotencyKey = requiredText(candidateIdempotencyKey, "candidateIdempotencyKey");
            candidateRequestHash = requiredText(candidateRequestHash, "candidateRequestHash");
            Objects.requireNonNull(status, "status is required");
            if (artifactCount < 0) {
                throw new IllegalArgumentException("artifactCount cannot be negative");
            }
            issues = issues == null ? List.of() : List.copyOf(issues);
            if (issues.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("issues cannot contain null");
            }
            Objects.requireNonNull(recordedAt, "recordedAt is required");
            appliedAction = optionalText(appliedAction);
            projectKey = optionalText(projectKey);
            dependencyUniqueIds = immutableStrings(dependencyUniqueIds);
            if (mergeCheckpoint != null) {
                if (!Objects.equals(mergeCheckpoint.dbtUniqueId(), dbtUniqueId)) {
                    throw new IllegalArgumentException("Merge checkpoint identity must match candidate result");
                }
                if (!Objects.equals(mergeCheckpoint.projectKey(), projectKey)) {
                    throw new IllegalArgumentException("Merge checkpoint project must match candidate result");
                }
            }
        }

        public CandidateResult(
            UUID resultId,
            int sequence,
            String dbtUniqueId,
            String candidateIdempotencyKey,
            String candidateRequestHash,
            ResultStatus status,
            UUID modelSpecId,
            Integer revision,
            String modelChecksum,
            Integer implementationRevision,
            String implementationChecksum,
            int artifactCount,
            List<ApplyIssue> issues,
            Instant recordedAt
        ) {
            this(
                resultId,
                sequence,
                dbtUniqueId,
                candidateIdempotencyKey,
                candidateRequestHash,
                status,
                modelSpecId,
                revision,
                modelChecksum,
                implementationRevision,
                implementationChecksum,
                artifactCount,
                issues,
                recordedAt,
                null,
                null,
                null,
                null,
                List.of()
            );
        }

        public boolean successful() {
            return switch (status) {
                case CREATED, UPDATED, SKIPPED -> true;
                case FAILED, BLOCKED -> false;
            };
        }

        /** Retry eligibility is a persisted diagnostic fact, never inferred from status alone. */
        public boolean retryable() {
            return !successful() && issues.stream().anyMatch(ApplyIssue::retryable);
        }
    }

    public record ApplySummary(
        int selected,
        int pending,
        int succeeded,
        int created,
        int updated,
        int skipped,
        int failed,
        int blocked
    ) {
        public static final ApplySummary EMPTY = new ApplySummary(0, 0, 0, 0, 0, 0, 0, 0);

        public ApplySummary {
            if (
                selected < 0 ||
                pending < 0 ||
                succeeded < 0 ||
                created < 0 ||
                updated < 0 ||
                skipped < 0 ||
                failed < 0 ||
                blocked < 0
            ) {
                throw new IllegalArgumentException("Apply summary counts cannot be negative");
            }
            if (succeeded != created + updated) {
                throw new IllegalArgumentException("Apply summary succeeded must equal created plus updated");
            }
            if (selected != pending + created + updated + skipped + failed + blocked) {
                throw new IllegalArgumentException("Apply summary selected must equal pending plus terminal status counts");
            }
        }

        public static ApplySummary running(int selected) {
            return new ApplySummary(selected, selected, 0, 0, 0, 0, 0, 0);
        }

        public int handled() {
            return succeeded + skipped;
        }

        public int unresolved() {
            return failed + blocked;
        }
    }

    public record Attempt(
        UUID id,
        UUID runId,
        UUID planId,
        UUID retrySourceAttemptId,
        String tenantId,
        int attemptNo,
        String previewHash,
        List<String> selectedUniqueIds,
        List<String> selectedClosure,
        String idempotencyKey,
        String requestHash,
        AttemptStatus status,
        ApplySummary summary,
        String actorId,
        Instant startedAt,
        Instant completedAt,
        List<CandidateResult> results,
        OperationType operationType,
        UUID targetAttemptId
    ) {
        public Attempt {
            selectedUniqueIds = immutableStrings(selectedUniqueIds);
            selectedClosure = immutableStrings(selectedClosure);
            summary = summary == null ? ApplySummary.EMPTY : summary;
            results = results == null ? List.of() : List.copyOf(results);
            operationType = operationType == null ? OperationType.APPLY : operationType;
            if (operationType == OperationType.APPLY && targetAttemptId != null) {
                throw new IllegalArgumentException("APPLY attempt cannot identify an undo target");
            }
            if (operationType == OperationType.FORWARD_UNDO && (targetAttemptId == null || retrySourceAttemptId != null)) {
                throw new IllegalArgumentException("FORWARD_UNDO attempt requires only an undo target");
            }
            if (id != null && id.equals(targetAttemptId)) {
                throw new IllegalArgumentException("Attempt cannot target itself");
            }
        }

        public Attempt(
            UUID id,
            UUID runId,
            UUID planId,
            UUID retrySourceAttemptId,
            String tenantId,
            int attemptNo,
            String previewHash,
            List<String> selectedUniqueIds,
            List<String> selectedClosure,
            String idempotencyKey,
            String requestHash,
            AttemptStatus status,
            ApplySummary summary,
            String actorId,
            Instant startedAt,
            Instant completedAt,
            List<CandidateResult> results
        ) {
            this(
                id,
                runId,
                planId,
                retrySourceAttemptId,
                tenantId,
                attemptNo,
                previewHash,
                selectedUniqueIds,
                selectedClosure,
                idempotencyKey,
                requestHash,
                status,
                summary,
                actorId,
                startedAt,
                completedAt,
                results,
                OperationType.APPLY,
                null
            );
        }
    }

    public record ApplyResponse(
        UUID attemptId,
        UUID runId,
        BeginDisposition disposition,
        AttemptStatus status,
        ApplySummary summary,
        List<CandidateResult> items,
        CanonicalRunResult overallRun
    ) {
        public ApplyResponse {
            items = items == null ? List.of() : List.copyOf(items);
            overallRun = overallRun == null
                ? new CanonicalRunResult(attemptId, status, summary, items)
                : overallRun;
        }

        public ApplyResponse(
            UUID attemptId,
            UUID runId,
            BeginDisposition disposition,
            AttemptStatus status,
            ApplySummary summary,
            List<CandidateResult> items
        ) {
            this(attemptId, runId, disposition, status, summary, items, null);
        }
    }

    /** Canonical projection across the root apply and every retry attempt in its existing ledger chain. */
    public record CanonicalRunResult(
        UUID rootAttemptId,
        AttemptStatus status,
        ApplySummary summary,
        List<CandidateResult> items
    ) {
        public CanonicalRunResult {
            Objects.requireNonNull(rootAttemptId, "rootAttemptId is required");
            Objects.requireNonNull(status, "status is required");
            summary = summary == null ? ApplySummary.EMPTY : summary;
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public static final class ModelSpecImportApplyException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final String code;
        private final Kind kind;
        private final transient Object details;

        public ModelSpecImportApplyException(String code, String message, Kind kind, Object details) {
            super(message);
            this.code = requiredText(code, "code");
            this.kind = Objects.requireNonNull(kind, "kind is required");
            this.details = details;
        }

        public String code() {
            return code;
        }

        public Kind kind() {
            return kind;
        }

        public Object details() {
            return details;
        }
    }

    private static List<String> immutableStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = values.stream().map(value -> requiredText(value, "list value")).distinct().toList();
        return List.copyOf(result);
    }

    private static Map<String, ConflictResolution> immutableConflictResolutions(
        Map<String, ConflictResolution> values
    ) {
        if (values == null) return Map.of();
        if (values.size() > MAX_SELECTED_UNIQUE_IDS) {
            throw new IllegalArgumentException("conflictResolutions cannot contain more than 200 candidates");
        }
        LinkedHashMap<String, ConflictResolution> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String uniqueId = requiredText(key, "conflictResolutions key");
            if (uniqueId.length() > MAX_UNIQUE_ID_LENGTH) {
                throw new IllegalArgumentException("conflictResolutions key cannot exceed 512 characters");
            }
            result.put(uniqueId, Objects.requireNonNull(value, "conflictResolutions value is required"));
        });
        return Map.copyOf(result);
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requestPreviewHash(String value) {
        String previewHash = requiredText(value, "previewHash");
        if (!previewHash.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("previewHash must be a 64-character hexadecimal checksum");
        }
        return previewHash;
    }

    private static String boundedRequestText(String value, String name, int maximumLength) {
        String text = requiredText(value, name);
        if (text.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds the allowed limit");
        }
        return text;
    }
}
