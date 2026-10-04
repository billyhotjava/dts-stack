package com.yuzhi.dts.platform.service.modeling.imports.reconciliation;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Stable three-way reconciliation and forward-undo contracts for dbt package imports. */
public final class ModelSpecImportReconciliationContract {

    public static final int MAX_ITEMS = 200;
    public static final int MAX_UNIQUE_ID_LENGTH = 512;
    public static final int MAX_IDEMPOTENCY_KEY_LENGTH = 256;
    private static final Pattern CHECKSUM = Pattern.compile("^[0-9a-f]{64}$");

    private ModelSpecImportReconciliationContract() {}

    public enum DriftAction {
        SKIP,
        UPDATE,
        CONFLICT,
        BLOCKED_REMAP,
    }

    public enum ConflictResolution {
        KEEP_CURRENT,
        ACCEPT_INCOMING,
        CANCEL,
    }

    public record MergeCheckpoint(
        String tenantId,
        String projectKey,
        String dbtUniqueId,
        String acceptedExternalChecksum,
        int acceptedImplementationRevision,
        String acceptedImplementationChecksum,
        int mappedModelSpecRevision,
        String mappedModelSpecEtag
    ) {
        public MergeCheckpoint {
            tenantId = requiredText(tenantId, "tenantId", 128);
            projectKey = requiredText(projectKey, "projectKey", 256);
            dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId", MAX_UNIQUE_ID_LENGTH);
            acceptedExternalChecksum = checksum(acceptedExternalChecksum, "acceptedExternalChecksum");
            if (acceptedImplementationRevision < 1) {
                throw new IllegalArgumentException("acceptedImplementationRevision must be positive");
            }
            acceptedImplementationChecksum = checksum(
                acceptedImplementationChecksum,
                "acceptedImplementationChecksum"
            );
            if (mappedModelSpecRevision < 1) {
                throw new IllegalArgumentException("mappedModelSpecRevision must be positive");
            }
            mappedModelSpecEtag = checksum(mappedModelSpecEtag, "mappedModelSpecEtag");
        }
    }

    public record TechnicalPin(int implementationRevision, String implementationChecksum) {
        public TechnicalPin {
            if (implementationRevision < 1) {
                throw new IllegalArgumentException("implementationRevision must be positive");
            }
            implementationChecksum = checksum(implementationChecksum, "implementationChecksum");
        }
    }

    public record MappingPin(int modelSpecRevision, String modelSpecEtag) {
        public MappingPin {
            if (modelSpecRevision < 1) throw new IllegalArgumentException("modelSpecRevision must be positive");
            modelSpecEtag = checksum(modelSpecEtag, "modelSpecEtag");
        }
    }

    public record DriftInput(
        MergeCheckpoint base,
        TechnicalPin currentTechnical,
        String incomingExternalChecksum,
        MappingPin currentMapping,
        JsonNode currentModelSpec,
        JsonNode incomingModelSpec,
        boolean mappingRevalidated
    ) {
        public DriftInput {
            Objects.requireNonNull(currentTechnical, "currentTechnical is required");
            incomingExternalChecksum = checksum(incomingExternalChecksum, "incomingExternalChecksum");
            Objects.requireNonNull(currentMapping, "currentMapping is required");
        }

        public DriftInput(
            MergeCheckpoint base,
            TechnicalPin currentTechnical,
            String incomingExternalChecksum,
            MappingPin currentMapping,
            JsonNode currentModelSpec,
            JsonNode incomingModelSpec
        ) {
            this(
                base,
                currentTechnical,
                incomingExternalChecksum,
                currentMapping,
                currentModelSpec,
                incomingModelSpec,
                false
            );
        }
    }

    public record DriftDecision(
        DriftAction action,
        String reasonCode,
        boolean mappingRevalidationRequired,
        JsonNode proposedModelSpec
    ) {
        public DriftDecision {
            Objects.requireNonNull(action, "action is required");
            reasonCode = requiredText(reasonCode, "reasonCode", 128);
        }
    }

    public record RenameMapping(String oldUniqueId, String newUniqueId) {
        public RenameMapping {
            oldUniqueId = requiredText(oldUniqueId, "oldUniqueId", MAX_UNIQUE_ID_LENGTH);
            newUniqueId = requiredText(newUniqueId, "newUniqueId", MAX_UNIQUE_ID_LENGTH);
            if (oldUniqueId.equals(newUniqueId)) {
                throw new IllegalArgumentException("Rename mapping must change the dbt unique id");
            }
        }
    }

    public record RevisionPins(
        int modelRevision,
        String modelChecksum,
        int implementationRevision,
        String implementationChecksum
    ) {
        public RevisionPins {
            if (modelRevision < 1) throw new IllegalArgumentException("modelRevision must be positive");
            modelChecksum = checksum(modelChecksum, "modelChecksum");
            if (implementationRevision < 1) {
                throw new IllegalArgumentException("implementationRevision must be positive");
            }
            implementationChecksum = checksum(implementationChecksum, "implementationChecksum");
        }
    }

    /** Immutable target-attempt fact read from the existing apply result ledger. */
    public record UndoTargetItem(
        UUID modelSpecId,
        String dbtUniqueId,
        String appliedAction,
        List<String> dependencies,
        RevisionPins postPins,
        RevisionPins prePins
    ) {
        public UndoTargetItem {
            Objects.requireNonNull(modelSpecId, "modelSpecId is required");
            dbtUniqueId = requiredText(dbtUniqueId, "dbtUniqueId", MAX_UNIQUE_ID_LENGTH);
            appliedAction = requiredText(appliedAction, "appliedAction", 16);
            if (!List.of("CREATE", "UPDATE", "SKIP").contains(appliedAction)) {
                throw new IllegalArgumentException("appliedAction is invalid");
            }
            dependencies = immutableUniqueIds(dependencies);
            Objects.requireNonNull(postPins, "postPins is required");
            if ("UPDATE".equals(appliedAction) && prePins == null) {
                throw new IllegalArgumentException("UPDATE undo target requires prePins");
            }
        }
    }

    public record ForwardUndoRequest(
        UUID targetAttemptId,
        List<String> selectedItemIds,
        Map<String, RevisionPins> expectedCurrentRevisions,
        String idempotencyKey
    ) {
        public ForwardUndoRequest {
            Objects.requireNonNull(targetAttemptId, "targetAttemptId is required");
            selectedItemIds = immutableUniqueIds(selectedItemIds);
            expectedCurrentRevisions = immutablePins(expectedCurrentRevisions);
            idempotencyKey = requiredText(
                idempotencyKey,
                "idempotencyKey",
                MAX_IDEMPOTENCY_KEY_LENGTH
            );
        }
    }

    public record ForwardUndoPlan(List<String> selectedUniqueIds, List<UndoTargetItem> reverseClosure) {
        public ForwardUndoPlan {
            selectedUniqueIds = List.copyOf(selectedUniqueIds);
            reverseClosure = List.copyOf(reverseClosure);
            if (reverseClosure.isEmpty()) throw new IllegalArgumentException("reverseClosure is required");
        }
    }

    private static Map<String, RevisionPins> immutablePins(Map<String, RevisionPins> values) {
        if (values == null) return Map.of();
        if (values.size() > MAX_ITEMS) throw new IllegalArgumentException("expectedCurrentRevisions exceeds 200 items");
        LinkedHashMap<String, RevisionPins> result = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String uniqueId = requiredText(key, "expectedCurrentRevisions key", MAX_UNIQUE_ID_LENGTH);
            result.put(uniqueId, Objects.requireNonNull(value, "expectedCurrentRevisions value is required"));
        });
        return Map.copyOf(result);
    }

    private static List<String> immutableUniqueIds(List<String> values) {
        if (values == null) return List.of();
        if (values.size() > MAX_ITEMS) throw new IllegalArgumentException("item list exceeds 200 items");
        return values
            .stream()
            .map(value -> requiredText(value, "item identity", MAX_UNIQUE_ID_LENGTH))
            .distinct()
            .toList();
    }

    private static String checksum(String value, String name) {
        String normalized = requiredText(value, name, 64).toLowerCase();
        if (!CHECKSUM.matcher(normalized).matches()) {
            throw new IllegalArgumentException(name + " must be a SHA-256 checksum");
        }
        return normalized;
    }

    private static String requiredText(String value, String name, int maximumLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        String normalized = value.trim();
        if (normalized.length() > maximumLength) throw new IllegalArgumentException(name + " exceeds the allowed limit");
        return normalized;
    }
}
