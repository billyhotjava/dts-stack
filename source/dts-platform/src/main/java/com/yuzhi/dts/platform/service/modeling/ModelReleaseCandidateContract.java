package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAuditView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryAction;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryEvidenceType;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.DeliveryStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Immutable persistence and UI projection contract for one revision-bound release candidate.
 *
 * <p>The header audit is the append-only milestone trail for the candidate's first delivery cycle. A rejected or
 * rolled-back scope must start a new candidate instead of clearing audit fields on this one. T03 owns any later
 * multi-attempt audit ledger and must not reinterpret this header as a reusable workflow log.
 */
public final class ModelReleaseCandidateContract {

    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");
    public static final int MAX_SCOPE_ENTRIES = 100;
    public static final int MAX_COMMAND_REASON_BYTES = 3_500;
    public static final int MAX_SELECTED_REASON_BYTES = 1_024;
    public static final int MAX_RESPONSE_SNAPSHOT_BYTES = 1_048_576;
    public static final String VERSION_CONFLICT_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_VERSION_CONFLICT";
    public static final String IDEMPOTENCY_CONFLICT_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_IDEMPOTENCY_CONFLICT";
    public static final String INVALID_TRANSITION_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_INVALID_TRANSITION";
    public static final String SCOPE_LOCKED_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_SCOPE_LOCKED";
    public static final String SCOPE_EMPTY_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_SCOPE_EMPTY";
    public static final String STALE_ERROR_CODE = "MODEL_RELEASE_CANDIDATE_STALE";

    private ModelReleaseCandidateContract() {}

    public enum CandidateOrigin {
        SINGLE_MODEL_INTENT,
        BATCH_WORKBENCH,
    }

    public record EntryView(
        UUID id,
        String tenantId,
        UUID candidateId,
        UUID planId,
        UUID modelSpecId,
        int revision,
        String checksum,
        UUID implementationId,
        ImplementationMode implementationMode,
        DeliveryStatus status,
        int sortOrder,
        String selectedReason
    ) {
        public EntryView {
            id = requiredUuid(id, "id");
            tenantId = requiredText(tenantId, "tenantId", 128);
            candidateId = requiredUuid(candidateId, "candidateId");
            planId = requiredUuid(planId, "planId");
            modelSpecId = requiredUuid(modelSpecId, "modelSpecId");
            if (revision < 1) throw new IllegalArgumentException("revision must be positive");
            checksum = requiredChecksum(checksum, "checksum");
            if (implementationMode == null) throw new IllegalArgumentException("implementationMode is required");
            if (status == null) throw new IllegalArgumentException("status is required");
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");
            selectedReason = optionalUtf8Text(selectedReason, "selectedReason", MAX_SELECTED_REASON_BYTES);
        }
    }

    public record CandidateView(
        UUID id,
        String tenantId,
        UUID planId,
        String environment,
        DeliveryStatus status,
        int version,
        String idempotencyKey,
        String requestHash,
        DeliveryAuditView audit,
        String lastModifiedBy,
        Instant lastModifiedAt,
        List<EntryView> entries,
        CandidateOrigin origin,
        String executionTargetKey,
        String adapter,
        String profileKey,
        String targetName
    ) {
        public CandidateView {
            id = requiredUuid(id, "id");
            tenantId = requiredText(tenantId, "tenantId", 128);
            planId = requiredUuid(planId, "planId");
            environment = requiredText(environment, "environment", 64);
            if (status == null) throw new IllegalArgumentException("status is required");
            if (version < 1) throw new IllegalArgumentException("version must be positive");
            idempotencyKey = optionalText(idempotencyKey);
            if (idempotencyKey != null && idempotencyKey.length() > 128) {
                throw new IllegalArgumentException("idempotencyKey exceeds 128 characters");
            }
            requestHash = optionalText(requestHash);
            if ((idempotencyKey == null) != (requestHash == null)) {
                throw new IllegalArgumentException("idempotencyKey and requestHash must be provided together");
            }
            if (requestHash != null) requestHash = requiredChecksum(requestHash, "requestHash");
            if (audit == null) throw new IllegalArgumentException("audit is required");
            lastModifiedBy = requiredText(lastModifiedBy, "lastModifiedBy", 128);
            if (lastModifiedAt == null) throw new IllegalArgumentException("lastModifiedAt is required");
            if (lastModifiedAt.isBefore(audit.createdAt())) {
                throw new IllegalArgumentException("lastModifiedAt cannot be before createdAt");
            }
            ensureAuditOrder(audit);
            ensureAuditForStatus(status, audit);
            ensureAuditNotAfterLastModified(audit, lastModifiedAt);
            origin = origin == null ? CandidateOrigin.BATCH_WORKBENCH : origin;
            boolean hasExecutionContext =
                executionTargetKey != null ||
                adapter != null ||
                profileKey != null ||
                targetName != null;
            if (hasExecutionContext) {
                executionTargetKey = requiredText(
                    executionTargetKey,
                    "executionTargetKey",
                    256
                );
                adapter = requiredText(adapter, "adapter", 64);
                profileKey = requiredText(profileKey, "profileKey", 128);
                targetName = requiredText(targetName, "targetName", 128);
            }

            List<EntryView> candidateEntries = entries == null ? List.of() : entries;
            if (candidateEntries.stream().anyMatch(entry -> entry == null)) {
                throw new IllegalArgumentException("entries cannot contain null");
            }
            List<EntryView> stableEntries = candidateEntries
                .stream()
                .sorted(Comparator.comparingInt(EntryView::sortOrder).thenComparing(EntryView::id))
                .toList();
            Set<UUID> modelIds = new HashSet<>();
            Set<Integer> sortOrders = new HashSet<>();
            for (EntryView entry : stableEntries) {
                if (!tenantId.equals(entry.tenantId())) {
                    throw new IllegalArgumentException("entry tenantId must match candidate tenantId");
                }
                if (!id.equals(entry.candidateId())) {
                    throw new IllegalArgumentException("entry candidateId must match candidate id");
                }
                if (!planId.equals(entry.planId())) {
                    throw new IllegalArgumentException("entry planId must match candidate planId");
                }
                if (status != entry.status()) {
                    throw new IllegalArgumentException("entry status must match candidate status");
                }
                if (!modelIds.add(entry.modelSpecId())) {
                    throw new IllegalArgumentException("candidate modelSpecId must be unique");
                }
                if (!sortOrders.add(entry.sortOrder())) {
                    throw new IllegalArgumentException("candidate sortOrder must be unique");
                }
            }
            entries = List.copyOf(stableEntries);
        }

        /** Compatibility constructor for pre-Sprint-76 batch candidates and stored response snapshots. */
        public CandidateView(
            UUID id,
            String tenantId,
            UUID planId,
            String environment,
            DeliveryStatus status,
            int version,
            String idempotencyKey,
            String requestHash,
            DeliveryAuditView audit,
            String lastModifiedBy,
            Instant lastModifiedAt,
            List<EntryView> entries
        ) {
            this(
                id,
                tenantId,
                planId,
                environment,
                status,
                version,
                idempotencyKey,
                requestHash,
                audit,
                lastModifiedBy,
                lastModifiedAt,
                entries,
                CandidateOrigin.BATCH_WORKBENCH,
                null,
                null,
                null,
                null
            );
        }

        public CandidateView(
            UUID id,
            String tenantId,
            UUID planId,
            String environment,
            DeliveryStatus status,
            int version,
            String idempotencyKey,
            String requestHash,
            DeliveryAuditView audit,
            String lastModifiedBy,
            Instant lastModifiedAt,
            List<EntryView> entries,
            CandidateOrigin origin
        ) {
            this(
                id,
                tenantId,
                planId,
                environment,
                status,
                version,
                idempotencyKey,
                requestHash,
                audit,
                lastModifiedBy,
                lastModifiedAt,
                entries,
                origin,
                null,
                null,
                null,
                null
            );
        }
    }

    /** Stable conflict payload rendered directly by the candidate scope panel. */
    public record VersionConflictView(
        UUID candidateId,
        int expectedVersion,
        int currentVersion,
        DeliveryStatus currentStatus,
        String errorCode
    ) {
        public VersionConflictView {
            candidateId = requiredUuid(candidateId, "candidateId");
            if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
            if (currentVersion < 1) throw new IllegalArgumentException("currentVersion must be positive");
            if (currentVersion == expectedVersion) {
                throw new IllegalArgumentException("currentVersion must differ from expectedVersion");
            }
            if (currentStatus == null) throw new IllegalArgumentException("currentStatus is required");
            errorCode = requiredText(errorCode, "errorCode");
            if (!VERSION_CONFLICT_ERROR_CODE.equals(errorCode)) {
                throw new IllegalArgumentException("errorCode must be " + VERSION_CONFLICT_ERROR_CODE);
            }
        }

        public static VersionConflictView of(
            UUID candidateId,
            int expectedVersion,
            int currentVersion,
            DeliveryStatus currentStatus
        ) {
            return new VersionConflictView(
                candidateId,
                expectedVersion,
                currentVersion,
                currentStatus,
                VERSION_CONFLICT_ERROR_CODE
            );
        }
    }

    /** Input reference selected into a candidate without copying the ModelSpec snapshot. */
    public record ScopeEntryCommand(
        UUID modelSpecId,
        int sortOrder,
        String selectedReason
    ) {
        public ScopeEntryCommand {
            modelSpecId = requiredUuid(modelSpecId, "modelSpecId");
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder must be non-negative");
            selectedReason = optionalUtf8Text(selectedReason, "selectedReason", MAX_SELECTED_REASON_BYTES);
        }
    }

    public record CreateCandidateCommand(
        UUID planId,
        String environment,
        List<ScopeEntryCommand> entries,
        String idempotencyKey,
        String reason
    ) {
        public CreateCandidateCommand {
            planId = requiredUuid(planId, "planId");
            environment = requiredText(environment, "environment", 64);
            entries = immutableScope(entries);
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", 128);
            reason = requiredUtf8Text(reason, "reason", MAX_COMMAND_REASON_BYTES);
        }
    }

    public record ReplaceScopeCommand(
        int expectedVersion,
        List<ScopeEntryCommand> entries,
        String idempotencyKey,
        String reason
    ) {
        public ReplaceScopeCommand {
            if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
            entries = immutableScope(entries);
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", 128);
            reason = requiredUtf8Text(reason, "reason", MAX_COMMAND_REASON_BYTES);
        }
    }

    public record TransitionCommand(
        int expectedVersion,
        DeliveryStatus targetStatus,
        String idempotencyKey,
        String reason
    ) {
        public TransitionCommand {
            if (expectedVersion < 1) throw new IllegalArgumentException("expectedVersion must be positive");
            if (targetStatus == null) throw new IllegalArgumentException("targetStatus is required");
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", 128);
            reason = requiredUtf8Text(reason, "reason", MAX_COMMAND_REASON_BYTES);
        }
    }

    public enum CommandEventType {
        CREATED,
        SCOPE_REPLACED,
        STATUS_CHANGED,
        STALE_DETECTED,
    }

    /** Append-only command event and tenant-scoped idempotency receipt. */
    public record CommandEventView(
        UUID id,
        String tenantId,
        UUID candidateId,
        UUID planId,
        int candidateVersion,
        CommandEventType eventType,
        DeliveryStatus fromStatus,
        DeliveryStatus toStatus,
        String actorId,
        Instant occurredAt,
        String reason,
        String idempotencyKey,
        String requestHash,
        String responseSnapshot
    ) {
        public CommandEventView {
            id = requiredUuid(id, "id");
            tenantId = requiredText(tenantId, "tenantId", 128);
            candidateId = requiredUuid(candidateId, "candidateId");
            planId = requiredUuid(planId, "planId");
            if (candidateVersion < 1) throw new IllegalArgumentException("candidateVersion must be positive");
            if (eventType == null) throw new IllegalArgumentException("eventType is required");
            if (eventType == CommandEventType.CREATED && fromStatus != null) {
                throw new IllegalArgumentException("created event cannot have fromStatus");
            }
            if (eventType != CommandEventType.CREATED && fromStatus == null) {
                throw new IllegalArgumentException("non-created event requires fromStatus");
            }
            if (toStatus == null) throw new IllegalArgumentException("toStatus is required");
            actorId = requiredText(actorId, "actorId", 128);
            if (occurredAt == null) throw new IllegalArgumentException("occurredAt is required");
            reason = requiredUtf8Text(reason, "reason", 4_096);
            idempotencyKey = requiredText(idempotencyKey, "idempotencyKey", 128);
            requestHash = requiredChecksum(requestHash, "requestHash");
            responseSnapshot = requiredUtf8Text(
                responseSnapshot,
                "responseSnapshot",
                MAX_RESPONSE_SNAPSHOT_BYTES
            );
        }
    }

    /** Current canonical reference compared with the revision locked in a candidate entry. */
    public record CurrentModelReference(
        UUID modelSpecId,
        UUID planId,
        int revision,
        String checksum,
        ImplementationMode implementationMode
    ) {
        public CurrentModelReference {
            modelSpecId = requiredUuid(modelSpecId, "modelSpecId");
            planId = requiredUuid(planId, "planId");
            if (revision < 1) throw new IllegalArgumentException("revision must be positive");
            checksum = requiredChecksum(checksum, "checksum");
            if (implementationMode == null) throw new IllegalArgumentException("implementationMode is required");
        }
    }

    /** Displayable drift blocker returned with a STALE result. */
    public record DriftReasonView(
        UUID modelSpecId,
        int lockedRevision,
        String lockedChecksum,
        Integer currentRevision,
        String currentChecksum,
        String code,
        String message
    ) {
        public DriftReasonView {
            modelSpecId = requiredUuid(modelSpecId, "modelSpecId");
            if (lockedRevision < 1) throw new IllegalArgumentException("lockedRevision must be positive");
            lockedChecksum = requiredChecksum(lockedChecksum, "lockedChecksum");
            if (currentRevision != null && currentRevision < 1) {
                throw new IllegalArgumentException("currentRevision must be positive");
            }
            currentChecksum = currentChecksum == null ? null : requiredChecksum(currentChecksum, "currentChecksum");
            code = requiredText(code, "code");
            message = requiredText(message, "message");
        }
    }

    /** Stable write result; replayed responses retain the original candidate version and drift details. */
    public record CommandResult(
        CandidateView candidate,
        boolean replayed,
        List<DriftReasonView> driftReasons,
        List<DeliveryAction> allowedActions
    ) {
        public CommandResult {
            if (candidate == null) throw new IllegalArgumentException("candidate is required");
            List<DriftReasonView> reasons = driftReasons == null ? List.of() : driftReasons;
            if (reasons.stream().anyMatch(reason -> reason == null)) {
                throw new IllegalArgumentException("driftReasons cannot contain null");
            }
            driftReasons = List.copyOf(reasons);
            if (!driftReasons.isEmpty() && candidate.status() != DeliveryStatus.STALE) {
                throw new IllegalArgumentException("driftReasons require STALE candidate status");
            }
            allowedActions = List.copyOf(allowedActions == null ? List.of() : allowedActions);
            if (candidate.entries().isEmpty() && !allowedActions.isEmpty()) {
                throw new IllegalArgumentException("empty candidate cannot expose allowedActions");
            }
            if (!candidate.status().allowedActions().containsAll(allowedActions)) {
                throw new IllegalArgumentException("allowedActions must be a subset of candidate state actions");
            }
        }

        public CommandResult(CandidateView candidate, boolean replayed, List<DriftReasonView> driftReasons) {
            this(
                candidate,
                replayed,
                driftReasons,
                candidate == null || candidate.entries().isEmpty() ? List.of() : candidate.status().allowedActions()
            );
        }
    }

    /** Server-owned first-screen state; transport loading/error/forbidden remain explicit frontend states. */
    public enum WorkbenchState {
        EMPTY,
        READY,
        BLOCKED,
        STALE,
    }

    /** Evidence slots are stable from F1; later features replace UNAVAILABLE with persisted evidence summaries. */
    public enum EvidenceState {
        UNAVAILABLE,
        RUNNING,
        PASSED,
        FAILED,
        STALE,
    }

    /** UI commands are deliberately broader than lifecycle transitions because scope editing is DRAFT-only. */
    public enum WorkspaceAction {
        CREATE_CANDIDATE,
        UPDATE_SCOPE,
        REFRESH_CANDIDATE,
        START_BUILD,
        RETRY_BUILD,
        RUN_QUALITY,
        SUBMIT_REVIEW,
        CANCEL_CANDIDATE,
        APPROVE,
        REJECT,
        CREATE_REPLACEMENT_CANDIDATE,
        PUBLISH,
        RETRY_REGISTRATION,
        ROLLBACK,
    }

    public record EvidenceSummaryView(
        DeliveryEvidenceType type,
        EvidenceState state,
        String code,
        String message
    ) {
        public EvidenceSummaryView {
            if (type == null) throw new IllegalArgumentException("type is required");
            if (state == null) throw new IllegalArgumentException("state is required");
            code = optionalText(code);
            message = optionalText(message);
            if ((code == null) != (message == null)) {
                throw new IllegalArgumentException("evidence code and message must be provided together");
            }
        }
    }

    public record BlockerView(String code, String message) {
        public BlockerView {
            code = requiredText(code, "code");
            message = requiredText(message, "message");
        }
    }

    /** One aggregate response is sufficient to render the plan delivery workbench first screen. */
    public record WorkbenchView(
        UUID planId,
        WorkbenchState state,
        CandidateView candidate,
        List<EvidenceSummaryView> evidence,
        BlockerView primaryBlocker,
        List<WorkspaceAction> allowedActions,
        String etag
    ) {
        public WorkbenchView {
            planId = requiredUuid(planId, "planId");
            if (state == null) throw new IllegalArgumentException("state is required");
            if (candidate != null && !planId.equals(candidate.planId())) {
                throw new IllegalArgumentException("candidate planId must match workbench planId");
            }
            List<EvidenceSummaryView> evidenceItems = evidence == null ? List.of() : evidence;
            if (
                evidenceItems.size() != DeliveryEvidenceType.values().length ||
                evidenceItems.stream().anyMatch(item -> item == null) ||
                evidenceItems.stream().map(EvidenceSummaryView::type).distinct().count() != DeliveryEvidenceType.values().length
            ) {
                throw new IllegalArgumentException("evidence must contain every delivery evidence type exactly once");
            }
            evidence = List.copyOf(evidenceItems);
            List<WorkspaceAction> actions = allowedActions == null ? List.of() : allowedActions;
            if (actions.stream().anyMatch(action -> action == null) || actions.stream().distinct().count() != actions.size()) {
                throw new IllegalArgumentException("allowedActions must be unique and non-null");
            }
            allowedActions = List.copyOf(actions);
            if (state == WorkbenchState.READY && (candidate == null || candidate.entries().isEmpty())) {
                throw new IllegalArgumentException("READY requires a candidate with scope entries");
            }
            if (state == WorkbenchState.STALE && (candidate == null || candidate.status() != DeliveryStatus.STALE)) {
                throw new IllegalArgumentException("STALE requires a stale candidate");
            }
            if (state == WorkbenchState.EMPTY && candidate != null && !candidate.entries().isEmpty()) {
                throw new IllegalArgumentException("EMPTY candidate must have no scope entries");
            }
            if ((state == WorkbenchState.READY) != (primaryBlocker == null)) {
                throw new IllegalArgumentException("only READY may omit primaryBlocker");
            }
            if (candidate == null) {
                if (etag != null) throw new IllegalArgumentException("empty workbench cannot have an etag");
            } else {
                String expectedEtag = "\"release-candidate:" + candidate.id() + ":" + candidate.version() + "\"";
                if (!expectedEtag.equals(etag)) {
                    throw new IllegalArgumentException("etag must match candidate id and version");
                }
            }
        }
    }

    private static void ensureAuditOrder(DeliveryAuditView audit) {
        if (audit.submittedAt() != null && audit.submittedAt().isBefore(audit.createdAt())) {
            throw new IllegalArgumentException("submittedAt cannot be before createdAt");
        }
        if (audit.approvedAt() != null && (audit.submittedAt() == null || audit.approvedAt().isBefore(audit.submittedAt()))) {
            throw new IllegalArgumentException("approvedAt cannot be before submittedAt");
        }
        if (audit.publishedAt() != null && (audit.approvedAt() == null || audit.publishedAt().isBefore(audit.approvedAt()))) {
            throw new IllegalArgumentException("publishedAt cannot be before approvedAt");
        }
        if (
            audit.submittedBy() != null &&
            (audit.submittedBy().equals(audit.approvedBy()) || audit.submittedBy().equals(audit.publishedBy()))
        ) {
            throw new IllegalArgumentException("submitter must be separated from approver and publisher");
        }
    }

    private static void ensureAuditForStatus(DeliveryStatus status, DeliveryAuditView audit) {
        if (status == DeliveryStatus.STALE) return;
        boolean submittedRequired = switch (status) {
            case REVIEW_PENDING, REJECTED, APPROVED, PUBLISHING, PARTIAL, PUBLISHED, ROLLED_BACK -> true;
            default -> false;
        };
        boolean approvedRequired = switch (status) {
            case APPROVED, PUBLISHING, PARTIAL, PUBLISHED, ROLLED_BACK -> true;
            default -> false;
        };
        boolean publishedRequired = switch (status) {
            case PARTIAL, PUBLISHED, ROLLED_BACK -> true;
            default -> false;
        };
        if (submittedRequired && audit.submittedAt() == null) {
            throw new IllegalArgumentException("status requires submitted audit");
        }
        if (approvedRequired && audit.approvedAt() == null) {
            throw new IllegalArgumentException("status requires approved audit");
        }
        if (publishedRequired && audit.publishedAt() == null) {
            throw new IllegalArgumentException("status requires published audit");
        }
    }

    private static void ensureAuditNotAfterLastModified(DeliveryAuditView audit, Instant lastModifiedAt) {
        if (isAfter(audit.submittedAt(), lastModifiedAt)) {
            throw new IllegalArgumentException("submittedAt cannot be after lastModifiedAt");
        }
        if (isAfter(audit.approvedAt(), lastModifiedAt)) {
            throw new IllegalArgumentException("approvedAt cannot be after lastModifiedAt");
        }
        if (isAfter(audit.publishedAt(), lastModifiedAt)) {
            throw new IllegalArgumentException("publishedAt cannot be after lastModifiedAt");
        }
    }

    private static boolean isAfter(Instant value, Instant maximum) {
        return value != null && value.isAfter(maximum);
    }

    private static UUID requiredUuid(UUID value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String requiredText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.trim();
    }

    private static String requiredText(String value, String name, int maximumLength) {
        String normalized = requiredText(value, name);
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " exceeds " + maximumLength + " characters");
        }
        return normalized;
    }

    private static String requiredUtf8Text(String value, String name, int maximumBytes) {
        String normalized = requiredText(value, name);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw new IllegalArgumentException(name + " exceeds " + maximumBytes + " UTF-8 bytes");
        }
        return normalized;
    }

    private static String requiredChecksum(String value, String name) {
        String normalized = requiredText(value, name);
        if (!CHECKSUM.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be 64 lowercase hexadecimal characters");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String optionalUtf8Text(String value, String name, int maximumBytes) {
        String normalized = optionalText(value);
        if (
            normalized != null &&
            normalized.getBytes(StandardCharsets.UTF_8).length > maximumBytes
        ) {
            throw new IllegalArgumentException(name + " exceeds " + maximumBytes + " UTF-8 bytes");
        }
        return normalized;
    }

    private static List<ScopeEntryCommand> immutableScope(List<ScopeEntryCommand> entries) {
        List<ScopeEntryCommand> source = entries == null ? List.of() : entries;
        if (source.size() > MAX_SCOPE_ENTRIES) {
            throw new IllegalArgumentException(
                "entries exceeds maximum of " + MAX_SCOPE_ENTRIES
            );
        }
        if (source.stream().anyMatch(entry -> entry == null)) {
            throw new IllegalArgumentException("entries cannot contain null");
        }
        List<ScopeEntryCommand> result = List.copyOf(source);
        Set<UUID> modelIds = new HashSet<>();
        Set<Integer> sortOrders = new HashSet<>();
        for (ScopeEntryCommand entry : result) {
            if (!modelIds.add(entry.modelSpecId())) {
                throw new IllegalArgumentException("candidate modelSpecId must be unique");
            }
            if (!sortOrders.add(entry.sortOrder())) {
                throw new IllegalArgumentException("candidate sortOrder must be unique");
            }
        }
        return result
            .stream()
            .sorted(Comparator.comparingInt(ScopeEntryCommand::sortOrder).thenComparing(ScopeEntryCommand::modelSpecId))
            .toList();
    }
}
