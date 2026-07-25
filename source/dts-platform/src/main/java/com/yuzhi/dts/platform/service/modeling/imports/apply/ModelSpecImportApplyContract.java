package com.yuzhi.dts.platform.service.modeling.imports.apply;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Stable command, persistence, and response projections for model-package apply attempts. */
public final class ModelSpecImportApplyContract {

    public static final int MAX_SELECTED_UNIQUE_IDS = 200;
    public static final int MAX_UNIQUE_ID_LENGTH = 512;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 256;

    private ModelSpecImportApplyContract() {}

    public enum AttemptStatus {
        RUNNING,
        SUCCEEDED,
        PARTIAL,
        FAILED,
    }

    public enum ResultStatus {
        CREATED,
        UPDATED,
        SKIPPED,
        REPLAYED,
        FAILED,
        BLOCKED,
    }

    public enum BeginDisposition {
        STARTED,
        REPLAY,
        RUNNING,
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
        String idempotencyKey
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
        Instant startedAt
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
        String fieldPath,
        String modelUniqueId,
        String message,
        String recoveryAction
    ) {}

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
        Instant recordedAt
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
        }

        public boolean successful() {
            return switch (status) {
                case CREATED, UPDATED, SKIPPED, REPLAYED -> true;
                case FAILED, BLOCKED -> false;
            };
        }
    }

    public record ApplySummary(
        int total,
        int created,
        int updated,
        int skipped,
        int replayed,
        int failed,
        int blocked
    ) {
        public static final ApplySummary EMPTY = new ApplySummary(0, 0, 0, 0, 0, 0, 0);

        public ApplySummary {
            if (total < 0 || created < 0 || updated < 0 || skipped < 0 || replayed < 0 || failed < 0 || blocked < 0) {
                throw new IllegalArgumentException("Apply summary counts cannot be negative");
            }
            if (total != created + updated + skipped + replayed + failed + blocked) {
                throw new IllegalArgumentException("Apply summary total must equal its status counts");
            }
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
        List<CandidateResult> results
    ) {
        public Attempt {
            selectedUniqueIds = immutableStrings(selectedUniqueIds);
            selectedClosure = immutableStrings(selectedClosure);
            summary = summary == null ? ApplySummary.EMPTY : summary;
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record ApplyResponse(
        UUID attemptId,
        UUID runId,
        BeginDisposition disposition,
        AttemptStatus status,
        ApplySummary summary,
        List<CandidateResult> items
    ) {
        public ApplyResponse {
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

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
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
