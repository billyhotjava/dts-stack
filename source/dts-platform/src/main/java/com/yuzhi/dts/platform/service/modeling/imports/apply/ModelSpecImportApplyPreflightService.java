package com.yuzhi.dts.platform.service.modeling.imports.apply;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Attempt;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.CandidateResult;
import com.yuzhi.dts.platform.service.modeling.imports.reconciliation.ModelSpecImportReconciliationContract.ConflictResolution;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.Kind;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ModelSpecImportApplyException;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyContract.ResultStatus;
import com.yuzhi.dts.platform.service.modeling.imports.apply.ModelSpecImportApplyPlanContract.Candidate;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.RunStatus;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.DomainBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.ModelOwnershipSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PlanSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.SourceBindingSnapshot;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.StoredApplyPlan;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Revalidates the frozen preview against current plan, binding, ownership, and CAS facts. */
@Service
public class ModelSpecImportApplyPreflightService {

    private final ModelSpecImportPreviewRepository previewRepository;
    private final ModelSpecImportApplyPayloadCodec payloadCodec;
    private final ObjectMapper objectMapper;

    public ModelSpecImportApplyPreflightService(
        ModelSpecImportPreviewRepository previewRepository,
        ModelSpecImportApplyPayloadCodec payloadCodec,
        ObjectMapper objectMapper
    ) {
        this.previewRepository = previewRepository;
        this.payloadCodec = payloadCodec;
        this.objectMapper = objectMapper;
    }

    public PreparedApply prepare(
        String tenantId,
        StoredApplyPlan stored,
        String previewHash,
        List<String> requestedUniqueIds,
        Attempt retrySource,
        Instant now
    ) {
        return prepare(
            tenantId,
            stored,
            previewHash,
            requestedUniqueIds,
            retrySource,
            Map.of(),
            Map.of(),
            Map.of(),
            now
        );
    }

    public PreparedApply prepare(
        String tenantId,
        StoredApplyPlan stored,
        String previewHash,
        List<String> requestedUniqueIds,
        Attempt retrySource,
        Map<String, CandidateResult> retrySuccesses,
        Map<String, Set<String>> syntheticDependencies,
        Instant now
    ) {
        return prepare(
            tenantId,
            stored,
            previewHash,
            requestedUniqueIds,
            retrySource,
            retrySuccesses,
            syntheticDependencies,
            Map.of(),
            now
        );
    }

    public PreparedApply prepare(
        String tenantId,
        StoredApplyPlan stored,
        String previewHash,
        List<String> requestedUniqueIds,
        Attempt retrySource,
        Map<String, CandidateResult> retrySuccesses,
        Map<String, Set<String>> syntheticDependencies,
        Map<String, ConflictResolution> conflictResolutions,
        Instant now
    ) {
        requireStoredIntegrity(stored, previewHash, now);
        JsonNode context = tree(stored.contextSnapshotJson());
        PlanSnapshot plan = previewRepository
            .findPlan(tenantId, stored.planId())
            .orElseThrow(() -> stale("Warehouse plan no longer exists"));
        requirePlanSnapshot(plan, context.path("plan"));
        requireDomainSnapshot(tenantId, plan.id(), context.path("domains"));
        requireSourceSnapshot(tenantId, plan.id(), context.path("sources"));

        Map<String, ConflictResolution> resolutions = conflictResolutions == null ? Map.of() : Map.copyOf(conflictResolutions);
        LinkedHashMap<String, Candidate> candidates = new LinkedHashMap<>();
        stored.applyPlan().candidates().forEach(candidate -> candidates.put(candidate.dbtUniqueId(), candidate));
        candidates.replaceAll((uniqueId, candidate) -> resolveConflict(candidate, resolutions.get(uniqueId)));
        if (resolutions.keySet().stream().anyMatch(uniqueId -> !candidates.containsKey(uniqueId))) {
            throw badRequest("MODEL_IMPORT_CONFLICT_RESOLUTION_INVALID", "Conflict resolution identifies an unknown candidate");
        }
        Map<String, Set<String>> dependencies = mergeDependencies(
            dependencies(candidates),
            syntheticDependencies
        );
        List<String> selected;
        List<String> closure;
        if (retrySource == null) {
            selected = normalized(requestedUniqueIds);
            if (selected.isEmpty()) {
                throw badRequest("MODEL_IMPORT_SELECTION_REQUIRED", "selectedUniqueIds must contain at least one candidate");
            }
            closure = closure(selected, candidates, dependencies, stored.applyPlan().topology());
        } else {
            selected = retrySource
                .results()
                .stream()
                .filter(CandidateResult::retryable)
                .map(item -> item.dbtUniqueId())
                .toList();
            Set<String> unresolved = new LinkedHashSet<>();
            retrySource
                .results()
                .stream()
                .filter(item -> item.status() == ResultStatus.FAILED || item.status() == ResultStatus.BLOCKED)
                .map(item -> item.dbtUniqueId())
                .forEach(unresolved::add);
            closure = retryClosure(selected, unresolved, dependencies, stored.applyPlan().topology());
            if (selected.isEmpty() || closure.isEmpty()) {
                throw conflict("MODEL_IMPORT_RETRY_NOT_AVAILABLE", "The latest apply attempt has no unresolved candidates");
            }
        }
        Set<String> factsToValidate = new LinkedHashSet<>(closure);
        for (String uniqueId : closure) {
            factsToValidate.addAll(dependencies.getOrDefault(uniqueId, Set.of()));
        }
        if (retrySource != null && retrySuccesses != null) {
            factsToValidate.addAll(retrySuccesses.keySet());
        }
        requireCandidateFacts(
            tenantId,
            plan.id(),
            stored.applyPayloadJson(),
            candidates,
            factsToValidate,
            Set.copyOf(closure),
            retrySource,
            retrySuccesses
        );
        List<Candidate> ordered = stored
            .applyPlan()
            .topology()
            .stream()
            .filter(closure::contains)
            .map(candidates::get)
            .toList();
        return new PreparedApply(plan, stored, selected, closure, ordered, dependencies);
    }

    private void requireStoredIntegrity(StoredApplyPlan stored, String previewHash, Instant now) {
        if (stored == null) {
            throw stale("Stored apply plan is unavailable or its checksum is invalid");
        }
        if (stored.expiresAt() == null || !stored.expiresAt().isAfter(now)) {
            throw stale("Model import preview has expired");
        }
        if (stored.status() != RunStatus.PREVIEWED) {
            throw conflict("MODEL_IMPORT_CONVERSION_BLOCKED", "Only a PREVIEWED run can be applied");
        }
        if (!Objects.equals(stored.previewHash(), previewHash)) {
            throw stale("Model import preview hash no longer matches");
        }
        if (!payloadCodec.isValid(stored.applyPayloadJson(), stored.applyPayloadChecksum())) {
            throw stale("Stored apply payload checksum no longer matches");
        }
    }

    private void requirePlanSnapshot(PlanSnapshot current, JsonNode expected) {
        if (
            !Objects.equals(current.id().toString(), expected.path("planId").asText()) ||
            !Objects.equals(current.tenantId(), expected.path("tenantId").asText()) ||
            !Objects.equals(current.lifecycleStatus().name(), expected.path("lifecycleStatus").asText()) ||
            current.version() != expected.path("version").asInt() ||
            current.businessScopeVersion() != expected.path("businessScopeVersion").asInt() ||
            current.sourcesVersion() != expected.path("sourcesVersion").asInt()
        ) {
            throw stale("Warehouse plan status or version changed after preview");
        }
    }

    private void requireDomainSnapshot(String tenantId, UUID planId, JsonNode expected) {
        Map<UUID, DomainBindingSnapshot> current = new HashMap<>();
        previewRepository.findDomainBindings(tenantId, planId).forEach(item -> current.put(item.domainId(), item));
        for (JsonNode node : expected) {
            UUID id = UUID.fromString(node.path("domainId").asText());
            DomainBindingSnapshot value = current.get(id);
            if (
                value == null ||
                !Objects.equals(value.confirmationStatus(), node.path("confirmationStatus").asText()) ||
                !sameInstant(value.lastValidatedAt(), node.path("lastValidatedAt").asText(null))
            ) {
                throw stale("Business category confirmation changed after preview");
            }
        }
    }

    private void requireSourceSnapshot(String tenantId, UUID planId, JsonNode expected) {
        Map<UUID, SourceBindingSnapshot> current = new HashMap<>();
        previewRepository.findSourceBindings(tenantId, planId).forEach(item -> current.put(item.id(), item));
        for (JsonNode node : expected) {
            UUID id = UUID.fromString(node.path("bindingId").asText());
            SourceBindingSnapshot value = current.get(id);
            if (
                value == null ||
                !Objects.equals(value.sourceVersion(), node.path("confirmedVersion").asText(null)) ||
                !Objects.equals(value.confirmationStatus(), node.path("confirmationStatus").asText()) ||
                !Objects.equals(value.savedResolutionStatus(), node.path("resolutionStatus").asText()) ||
                !sameInstant(value.lastValidatedAt(), node.path("lastValidatedAt").asText(null))
            ) {
                throw stale("Source binding status or version changed after preview");
            }
        }
    }

    private void requireCandidateFacts(
        String tenantId,
        UUID planId,
        String payloadJson,
        Map<String, Candidate> candidates,
        Set<String> uniqueIds,
        Set<String> activeClosure,
        Attempt retrySource,
        Map<String, CandidateResult> retrySuccesses
    ) {
        String projectKey = tree(payloadJson).path("dbt").path("projectName").asText();
        Map<String, CandidateResult> retryResults = retrySource == null
            ? Map.of()
            : retrySuccesses == null || retrySuccesses.isEmpty()
                ? retrySource
                    .results()
                    .stream()
                    .filter(CandidateResult::successful)
                    .collect(java.util.stream.Collectors.toMap(CandidateResult::dbtUniqueId, item -> item))
                : retrySuccesses;
        for (String uniqueId : uniqueIds) {
            Candidate candidate = candidates.get(uniqueId);
            if (candidate == null) {
                continue;
            }
            if ("BLOCKED".equals(candidate.action()) || "CONFLICT".equals(candidate.action())) {
                throw conflict("MODEL_IMPORT_CONVERSION_BLOCKED", "A selected candidate is not applicable");
            }
            ModelOwnershipSnapshot current = previewRepository.findOwnership(tenantId, projectKey, uniqueId).orElse(null);
            if (retrySource != null && !activeClosure.contains(uniqueId)) {
                requireRetryDependencyFacts(planId, retryResults.get(uniqueId), current);
                continue;
            }
            if (candidate.expectedModelRevision() == 0) {
                if (current != null) {
                    throw stale("A selected dbt node acquired an owner after preview");
                }
                continue;
            }
            if (
                current == null ||
                !Objects.equals(current.planId(), planId) ||
                !Objects.equals(current.modelSpecId(), candidate.targetModelSpecId()) ||
                current.currentRevision() != candidate.expectedModelRevision() ||
                !Objects.equals(current.currentChecksum(), candidate.expectedModelChecksum()) ||
                !Objects.equals(current.currentModelStatus(), candidate.expectedModelStatus()) ||
                current.implementationRevision() != candidate.expectedImplementationRevision() ||
                !Objects.equals(current.currentImplementationChecksum(), candidate.expectedImplementationChecksum())
            ) {
                throw stale("Model or implementation CAS facts changed after preview");
            }
        }
    }

    private Map<String, Set<String>> mergeDependencies(
        Map<String, Set<String>> frozenDependencies,
        Map<String, Set<String>> syntheticDependencies
    ) {
        Map<String, Set<String>> result = new HashMap<>();
        frozenDependencies.forEach((uniqueId, values) -> result.put(uniqueId, new LinkedHashSet<>(values)));
        if (syntheticDependencies != null) {
            syntheticDependencies.forEach((uniqueId, values) ->
                result.computeIfAbsent(uniqueId, ignored -> new LinkedHashSet<>()).addAll(values)
            );
        }
        Map<String, Set<String>> immutable = new HashMap<>();
        result.forEach((uniqueId, values) -> immutable.put(uniqueId, Set.copyOf(values)));
        return Map.copyOf(immutable);
    }

    static Candidate resolveConflict(Candidate candidate, ConflictResolution resolution) {
        boolean conflict = "CONFLICT".equals(candidate.action());
        if (!conflict && resolution != null) {
            throw badRequest(
                "MODEL_IMPORT_CONFLICT_RESOLUTION_INVALID",
                "Conflict resolution can be supplied only for a conflicting candidate"
            );
        }
        if (!conflict || resolution == null) return candidate;
        String action = switch (resolution) {
            case KEEP_CURRENT -> "SKIP";
            case ACCEPT_INCOMING -> "UPDATE";
            case CANCEL -> "CANCEL";
        };
        boolean retainCurrent = resolution != ConflictResolution.ACCEPT_INCOMING;
        return new Candidate(
            candidate.dbtUniqueId(),
            candidate.targetModelSpecId(),
            retainCurrent ? candidate.expectedModelRevision() : candidate.targetRevision(),
            retainCurrent ? candidate.expectedImplementationRevision() : candidate.targetImplementationRevision(),
            candidate.expectedModelRevision(),
            candidate.expectedModelChecksum(),
            candidate.expectedModelStatus(),
            candidate.expectedImplementationRevision(),
            candidate.expectedImplementationChecksum(),
            retainCurrent ? candidate.expectedModelChecksum() : candidate.proposedModelSpecChecksum(),
            retainCurrent ? candidate.expectedImplementationChecksum() : candidate.proposedImplementationChecksum(),
            action,
            candidate.conversionMode(),
            candidate.modelSpecJson(),
            candidate.implementationJson(),
            candidate.artifactJson(),
            candidate.evidenceJson(),
            candidate.dependencyPinsJson(),
            candidate.sourcePinsJson(),
            candidate.incomingExternalChecksum(),
            candidate.dependencyUniqueIds()
        );
    }

    private void requireRetryDependencyFacts(UUID planId, CandidateResult appliedDependency, ModelOwnershipSnapshot current) {
        if (
            appliedDependency == null ||
            !appliedDependency.successful() ||
            current == null ||
            !Objects.equals(current.planId(), planId) ||
            !Objects.equals(current.modelSpecId(), appliedDependency.modelSpecId()) ||
            !Objects.equals(current.currentRevision(), appliedDependency.revision()) ||
            !Objects.equals(current.currentChecksum(), appliedDependency.modelChecksum()) ||
            !Objects.equals(current.implementationRevision(), appliedDependency.implementationRevision()) ||
            !Objects.equals(current.currentImplementationChecksum(), appliedDependency.implementationChecksum())
        ) {
            throw stale("A previously applied dependency changed before retry");
        }
    }

    private Map<String, Set<String>> dependencies(Map<String, Candidate> candidates) {
        Map<String, Set<String>> result = new HashMap<>();
        candidates.forEach((uniqueId, candidate) -> {
            TreeSet<String> values = new TreeSet<>();
            for (JsonNode node : tree(candidate.dependencyPinsJson()).path("canonicalDependencies")) {
                String value = node.isTextual() ? node.asText() : node.path("dbtUniqueId").asText(null);
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
            result.put(uniqueId, Set.copyOf(values));
        });
        return Map.copyOf(result);
    }

    private List<String> closure(
        List<String> selected,
        Map<String, Candidate> candidates,
        Map<String, Set<String>> dependencies,
        List<String> topology
    ) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String uniqueId : selected) {
            if (!candidates.containsKey(uniqueId)) {
                throw conflict("MODEL_IMPORT_DEPENDENCY_MISSING", "Selected candidate is not present in the frozen preview");
            }
            collect(uniqueId, candidates, dependencies, result);
        }
        return topology.stream().filter(result::contains).toList();
    }

    private void collect(
        String uniqueId,
        Map<String, Candidate> candidates,
        Map<String, Set<String>> dependencies,
        Set<String> result
    ) {
        if (!result.add(uniqueId)) {
            return;
        }
        for (String dependency : dependencies.getOrDefault(uniqueId, Set.of())) {
            if (candidates.containsKey(dependency)) {
                collect(dependency, candidates, dependencies, result);
            }
        }
    }

    private static List<String> retryClosure(
        List<String> selected,
        Set<String> unresolved,
        Map<String, Set<String>> dependencies,
        List<String> topology
    ) {
        LinkedHashSet<String> reachable = new LinkedHashSet<>(selected);
        boolean changed;
        do {
            changed = false;
            for (String uniqueId : topology) {
                if (
                    unresolved.contains(uniqueId) &&
                    !reachable.contains(uniqueId) &&
                    dependencies.getOrDefault(uniqueId, Set.of()).stream().anyMatch(reachable::contains)
                ) {
                    changed = reachable.add(uniqueId) || changed;
                }
            }
        } while (changed);
        return topology.stream().filter(reachable::contains).toList();
    }

    private JsonNode tree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw stale("Stored model import preview JSON is invalid");
        }
    }

    private static List<String> normalized(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private static boolean sameInstant(Instant actual, String expected) {
        return expected == null ? actual == null : actual != null && actual.equals(Instant.parse(expected));
    }

    private static ModelSpecImportApplyException badRequest(String code, String message) {
        return new ModelSpecImportApplyException(code, message, Kind.BAD_REQUEST, null);
    }

    private static ModelSpecImportApplyException stale(String message) {
        return new ModelSpecImportApplyException("MODEL_IMPORT_PREVIEW_STALE", message, Kind.CONFLICT, null);
    }

    private static ModelSpecImportApplyException conflict(String code, String message) {
        return new ModelSpecImportApplyException(code, message, Kind.CONFLICT, null);
    }

    public record PreparedApply(
        PlanSnapshot plan,
        StoredApplyPlan stored,
        List<String> selectedUniqueIds,
        List<String> selectedClosure,
        List<Candidate> candidates,
        Map<String, Set<String>> dependencies
    ) {
        public PreparedApply {
            selectedUniqueIds = List.copyOf(selectedUniqueIds);
            selectedClosure = List.copyOf(selectedClosure);
            candidates = List.copyOf(candidates);
            dependencies = Map.copyOf(dependencies);
        }
    }
}
