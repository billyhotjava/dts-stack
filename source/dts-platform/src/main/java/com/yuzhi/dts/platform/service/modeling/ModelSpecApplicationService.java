package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.DomainBindingState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Canonical objectless ModelSpec command/query service. */
@Service
public class ModelSpecApplicationService {

    private final ModelSpecRepository repository;
    private final DimensionDefinitionRepository dimensionDefinitions;
    private final ModelSpecSnapshotCodec codec;
    private final CatalogDomainResolutionPort domainResolution;
    private final ModelSpecDomainWriteAccessPort domainWriteAccess;
    private final ModelSpecDomainReadAccessPort domainReadAccess;
    private final ModelSpecPlanWriteAccessPort planWriteAccess;
    private final ModelSpecSourceValidationPort sourceValidation;
    private final ModelSpecCompatibilityReader compatibilityReader;
    private final ModelSpecFeatureFlags featureFlags;
    private final Clock clock;
    private final Supplier<UUID> idGenerator;

    @Autowired
    public ModelSpecApplicationService(
        ModelSpecRepository repository,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecSnapshotCodec codec,
        CatalogDomainResolutionPort domainResolution,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecPlanWriteAccessPort planWriteAccess,
        ModelSpecSourceValidationPort sourceValidation,
        ModelSpecCompatibilityReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags
    ) {
        this(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            featureFlags,
            Clock.systemUTC(),
            UUID::randomUUID
        );
    }

    ModelSpecApplicationService(
        ModelSpecRepository repository,
        ModelSpecSnapshotCodec codec,
        CatalogDomainResolutionPort domainResolution,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecPlanWriteAccessPort planWriteAccess,
        ModelSpecSourceValidationPort sourceValidation,
        ModelSpecCompatibilityReader compatibilityReader,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this(
            repository,
            null,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            ModelSpecFeatureFlags.enabled(),
            clock,
            idGenerator
        );
    }

    ModelSpecApplicationService(
        ModelSpecRepository repository,
        ModelSpecSnapshotCodec codec,
        CatalogDomainResolutionPort domainResolution,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecPlanWriteAccessPort planWriteAccess,
        ModelSpecSourceValidationPort sourceValidation,
        ModelSpecCompatibilityReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this(
            repository,
            null,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            featureFlags,
            clock,
            idGenerator
        );
    }

    ModelSpecApplicationService(
        ModelSpecRepository repository,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecSnapshotCodec codec,
        CatalogDomainResolutionPort domainResolution,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecPlanWriteAccessPort planWriteAccess,
        ModelSpecSourceValidationPort sourceValidation,
        ModelSpecCompatibilityReader compatibilityReader,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this(
            repository,
            dimensionDefinitions,
            codec,
            domainResolution,
            domainWriteAccess,
            domainReadAccess,
            planWriteAccess,
            sourceValidation,
            compatibilityReader,
            ModelSpecFeatureFlags.enabled(),
            clock,
            idGenerator
        );
    }

    ModelSpecApplicationService(
        ModelSpecRepository repository,
        DimensionDefinitionRepository dimensionDefinitions,
        ModelSpecSnapshotCodec codec,
        CatalogDomainResolutionPort domainResolution,
        ModelSpecDomainWriteAccessPort domainWriteAccess,
        ModelSpecDomainReadAccessPort domainReadAccess,
        ModelSpecPlanWriteAccessPort planWriteAccess,
        ModelSpecSourceValidationPort sourceValidation,
        ModelSpecCompatibilityReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags,
        Clock clock,
        Supplier<UUID> idGenerator
    ) {
        this.repository = repository;
        this.dimensionDefinitions = dimensionDefinitions;
        this.codec = codec;
        this.domainResolution = domainResolution;
        this.domainWriteAccess = domainWriteAccess;
        this.domainReadAccess = domainReadAccess;
        this.planWriteAccess = planWriteAccess;
        this.sourceValidation = sourceValidation;
        this.compatibilityReader = compatibilityReader;
        this.featureFlags = featureFlags;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreateResult create(String serverTenantId, String actorId, CreateModelSpecCommand command) {
        return createInternal(serverTenantId, actorId, null, command);
    }

    /**
     * Internal import boundary that commits the deterministic ID already covered by a preview hash.
     *
     * <p>This method is intentionally not exposed by a REST resource. Normal interactive creation
     * continues to use {@link #create(String, String, CreateModelSpecCommand)} and a random ID.
     */
    @Transactional
    public CreateResult createImported(
        String serverTenantId,
        String actorId,
        UUID previewedModelSpecId,
        CreateModelSpecCommand command
    ) {
        if (previewedModelSpecId == null) {
            throw new ModelSpecException(
                "MODEL_SPEC_IMPORT_ID_REQUIRED",
                "A previewed ModelSpec id is required",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        return createInternal(serverTenantId, actorId, previewedModelSpecId, command);
    }

    private CreateResult createInternal(
        String serverTenantId,
        String actorId,
        UUID previewedModelSpecId,
        CreateModelSpecCommand command
    ) {
        requireServerContext(serverTenantId, actorId);
        requireDimensionDefinitionRef(command);
        rejectIssues(ModelSpecContract.validateCreate(command));
        String requestHash = codec.requestHash(command);
        StoredModelSpec existing = repository.findByIdempotencyKey(serverTenantId, command.idempotencyKey()).orElse(null);
        if (existing != null) {
            validateReplayAccess(serverTenantId, actorId, existing);
            if (previewedModelSpecId != null && !previewedModelSpecId.equals(existing.id())) {
                throw new ModelSpecException(
                    "MODEL_SPEC_IMPORT_ID_CONFLICT",
                    "The import idempotency key is already bound to another ModelSpec id",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            return replay(existing, requestHash);
        }

        requireCanonicalWriteEnabled();
        validateWriteContext(serverTenantId, actorId, command.planId(), command.domainId());
        validateSources(serverTenantId, actorId, command.planId(), command.sourceRefs());
        validateDimensionDefinition(serverTenantId, command.modelType(), command.dimensionDefinitionRef(), true);
        UUID modelSpecId = previewedModelSpecId == null ? idGenerator.get() : previewedModelSpecId;
        validateReferences(
            serverTenantId,
            command.planId(),
            modelSpecId,
            command.modelType(),
            command.dependsOn(),
            command.dimensionRefs()
        );
        Instant now = clock.instant();
        ModelSpecView view = codec.toCreatedView(modelSpecId, command, now);
        String responseSnapshot = codec.write(view);
        int inserted;
        try {
            inserted = repository.insertV2(serverTenantId, actorId, command, view, requestHash, responseSnapshot);
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
        if (inserted == 0) {
            StoredModelSpec concurrent = repository
                .findByIdempotencyKey(serverTenantId, command.idempotencyKey())
                .orElseThrow(() ->
                    new ModelSpecException(
                        "MODEL_SPEC_CREATE_CONCURRENCY_CONFLICT",
                        "Concurrent ModelSpec creation did not converge",
                        ModelSpecException.Kind.CONFLICT
                    )
                );
            if (previewedModelSpecId != null && !previewedModelSpecId.equals(concurrent.id())) {
                throw new ModelSpecException(
                    "MODEL_SPEC_IMPORT_ID_CONFLICT",
                    "The concurrent import winner uses another ModelSpec id",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            return replay(concurrent, requestHash);
        }
        repository.insertV2Revision(serverTenantId, actorId, view, responseSnapshot);
        return new CreateResult(view, false);
    }

    @Transactional
    public ModelSpecView update(
        String serverTenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        UpdateModelSpecCommand command
    ) {
        requireServerContext(serverTenantId, actorId);
        requireCanonicalWriteEnabled();
        if (modelSpecId == null) throw notFound(null);
        if (expected == null) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_REQUIRED",
                "A strong If-Match precondition is required",
                ModelSpecException.Kind.PRECONDITION_REQUIRED
            );
        }
        if (!modelSpecId.equals(expected.modelSpecId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_IF_MATCH_INVALID",
                "If-Match identifies a different ModelSpec",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }

        StoredModelSpec stored = repository.findCurrent(serverTenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
        if (stored.contractVersion() != ModelSpecContract.CONTRACT_VERSION) {
            throw new ModelSpecException(
                "MODEL_SPEC_LEGACY_READONLY",
                "Legacy ModelSpec rows are read-only at the canonical boundary",
                ModelSpecException.Kind.CONFLICT
            );
        }
        ModelSpecView current = compatibilityReader.read(stored);
        validateWriteContext(serverTenantId, actorId, current.planId(), current.domainId());
        requireExpected(current, expected);
        if (ModelSpecContract.hasHistoricalTypeBoundaryViolation(current)) {
            throw new ModelSpecException(
                "MODEL_SPEC_LEGACY_READONLY",
                "Historical ModelSpec rows with non-canonical type boundaries are read-only",
                ModelSpecException.Kind.CONFLICT
            );
        }
        rejectIssues(ModelSpecContract.validateUpdate(command));
        if (!Objects.equals(current.planId(), command.planId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_PLAN_IMMUTABLE",
                "ModelSpec cannot be moved to another warehouse plan",
                ModelSpecException.Kind.UNPROCESSABLE,
                List.of(fieldIssue("planId", "Warehouse plan is immutable after creation"))
            );
        }
        if (!Objects.equals(current.domainId(), command.domainId())) {
            throw new ModelSpecException(
                "MODEL_SPEC_DOMAIN_IMMUTABLE",
                "ModelSpec cannot be moved to another business category",
                ModelSpecException.Kind.UNPROCESSABLE,
                List.of(fieldIssue("domainId", "Business category is immutable after creation"))
            );
        }
        if (current.status() != ModelStatus.DRAFT) {
            throw new ModelSpecException(
                "MODEL_SPEC_STATUS_READONLY",
                "Only DRAFT ModelSpecs can be replaced",
                ModelSpecException.Kind.CONFLICT,
                Map.of("status", current.status())
            );
        }
        ModelSpecView replacement = codec.toUpdatedView(current, command, current.revision() + 1, clock.instant());
        requireDimensionDefinitionRef(replacement);
        rejectIssues(ModelSpecContract.validateView(replacement));
        validateSources(serverTenantId, actorId, command.planId(), command.sourceRefs());
        validateDimensionDefinition(serverTenantId, replacement.modelType(), replacement.dimensionDefinitionRef(), false);
        validateReferences(
            serverTenantId,
            command.planId(),
            modelSpecId,
            command.modelType(),
            command.dependsOn(),
            command.dimensionRefs()
        );
        if (replacement.checksum().equals(current.checksum())) return current;
        String snapshot = codec.write(replacement);
        int updated;
        try {
            updated = repository.compareAndSetV2(
                serverTenantId,
                actorId,
                current.revision(),
                current.checksum(),
                replacement,
                snapshot
            );
        } catch (DataIntegrityViolationException exception) {
            throw translateConstraint(exception);
        }
        if (updated == 0) {
            StoredModelSpec latest = repository.findCurrent(serverTenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
            throw revisionConflict(compatibilityReader.read(latest));
        }
        repository.insertV2Revision(serverTenantId, actorId, replacement, snapshot);
        return replacement;
    }

    @Transactional(readOnly = true)
    public ModelSpecView get(String serverTenantId, UUID modelSpecId) {
        requireTenant(serverTenantId);
        if (modelSpecId == null) throw notFound(null);
        ModelSpecView view = compatibilityReader.get(serverTenantId, modelSpecId);
        if (!canRead(view)) throw notFound(modelSpecId);
        return view;
    }

    @Transactional(readOnly = true)
    public List<ModelSpecView> list(
        String serverTenantId,
        UUID planId,
        UUID domainId,
        ModelType modelType,
        Layer layer
    ) {
        requireTenant(serverTenantId);
        return compatibilityReader
            .list(serverTenantId, planId, domainId, modelType, layer)
            .stream()
            .filter(this::canRead)
            .toList();
    }

    @Transactional(readOnly = true)
    public ModelSpecView revision(String serverTenantId, ModelSpecContract.ModelRevisionRef reference) {
        requireTenant(serverTenantId);
        ModelSpecView view = compatibilityReader.revision(serverTenantId, reference);
        if (!canRead(view)) throw notFound(reference == null ? null : reference.modelSpecId());
        return view;
    }

    @Transactional(readOnly = true)
    public DependencyGraph dependencyGraph(String serverTenantId, UUID modelSpecId) {
        ModelSpecView root = get(serverTenantId, modelSpecId);
        LinkedHashMap<String, DependencyNode> nodes = new LinkedHashMap<>();
        List<DependencyEdge> edges = new ArrayList<>();
        Set<ModelSpecContract.ModelRevisionRef> visited = new HashSet<>();
        nodes.put(
            nodeKey(root.id(), root.revision()),
            new DependencyNode(
                root.id(),
                root.revision(),
                root.revision(),
                root.name(),
                root.modelType(),
                root.status(),
                false
            )
        );
        collectDependencyGraph(serverTenantId, root, nodes, edges, visited);
        return new DependencyGraph(root.id(), List.copyOf(nodes.values()), List.copyOf(edges));
    }

    private void collectDependencyGraph(
        String tenantId,
        ModelSpecView owner,
        LinkedHashMap<String, DependencyNode> nodes,
        List<DependencyEdge> edges,
        Set<ModelSpecContract.ModelRevisionRef> visited
    ) {
        for (ModelSpecContract.ModelRevisionRef reference : owner.dependsOn()) {
            StoredModelSpec pinned = repository.findRevision(tenantId, reference.modelSpecId(), reference.revision()).orElse(null);
            StoredModelSpec current = repository.findCurrent(tenantId, reference.modelSpecId()).orElse(null);
            Integer currentRevision = current == null ? null : current.revision();
            DependencyState state = pinned == null || current == null
                ? DependencyState.UNKNOWN
                : current.revision() == reference.revision() ? DependencyState.CURRENT : DependencyState.STALE;
            ModelSpecView referenced = pinned == null ? null : compatibilityReader.read(pinned);
            boolean restricted = referenced == null || !canRead(referenced);
            nodes.putIfAbsent(
                nodeKey(reference.modelSpecId(), reference.revision()),
                new DependencyNode(
                    reference.modelSpecId(),
                    reference.revision(),
                    currentRevision,
                    restricted ? null : referenced.name(),
                    restricted ? null : referenced.modelType(),
                    restricted ? null : referenced.status(),
                    restricted
                )
            );
            edges.add(
                new DependencyEdge(
                    owner.id(),
                    reference.modelSpecId(),
                    reference.revision(),
                    currentRevision,
                    restricted ? DependencyState.UNKNOWN : state
                )
            );
            if (!restricted && visited.add(reference)) {
                collectDependencyGraph(tenantId, referenced, nodes, edges, visited);
            }
        }
    }

    private static String nodeKey(UUID modelSpecId, int revision) {
        return modelSpecId + "@" + revision;
    }

    public boolean canonicalReadEnabled() {
        return featureFlags.canonicalReadEnabled();
    }

    private void validateReferences(
        String tenantId,
        UUID planId,
        UUID modelSpecId,
        ModelType modelType,
        List<ModelSpecContract.ModelRevisionRef> dependencies,
        List<ModelSpecContract.ModelRevisionRef> dimensions
    ) {
        validateReferenceSet(tenantId, planId, dependencies, false, modelType);
        validateReferenceSet(tenantId, planId, dimensions, true, null);
        validateNoDependencyCycle(tenantId, modelSpecId, modelType, dependencies);
    }

    private void validateDimensionDefinition(
        String tenantId,
        ModelType modelType,
        ModelSpecContract.DimensionDefinitionRef reference,
        boolean creation
    ) {
        if (modelType != ModelType.DIMENSION || reference == null || dimensionDefinitions == null) return;
        StoredDimensionDefinition pinned = dimensionDefinitions
            .findRevision(tenantId, reference.dimensionDefinitionId(), reference.revision())
            .orElseThrow(() -> dimensionDefinitionNotCurrent(reference));
        if (!domainReadAccess.canRead(pinned.domainId())) throw dimensionDefinitionNotCurrent(reference);
        StoredDimensionDefinition current = (
                creation
                    ? dimensionDefinitions.findCurrentForShare(tenantId, reference.dimensionDefinitionId())
                    : dimensionDefinitions.findCurrent(tenantId, reference.dimensionDefinitionId())
            )
            .orElseThrow(() -> dimensionDefinitionNotCurrent(reference));
        if (
            current.status() != DimensionDefinitionContract.Status.CURRENT ||
            (creation && current.revision() != reference.revision())
        ) {
            throw dimensionDefinitionNotCurrent(reference);
        }
    }

    private static void requireDimensionDefinitionRef(CreateModelSpecCommand command) {
        if (command != null && command.modelType() == ModelType.DIMENSION && command.dimensionDefinitionRef() == null) {
            throw dimensionDefinitionRequired();
        }
    }

    private static void requireDimensionDefinitionRef(ModelSpecView view) {
        if (view.modelType() == ModelType.DIMENSION && view.dimensionDefinitionRef() == null) {
            throw dimensionDefinitionRequired();
        }
    }

    private static ModelSpecException dimensionDefinitionRequired() {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_REQUIRED",
            "DIMENSION models require a revision-pinned dimension definition",
            ModelSpecException.Kind.UNPROCESSABLE,
            List.of(fieldIssue("dimensionDefinitionRef", "DIMENSION models require a revision-pinned dimension definition"))
        );
    }

    private static ModelSpecException dimensionDefinitionNotCurrent(ModelSpecContract.DimensionDefinitionRef reference) {
        return new ModelSpecException(
            "DIMENSION_DEFINITION_NOT_CURRENT",
            "The pinned dimension definition is no longer current",
            ModelSpecException.Kind.UNPROCESSABLE,
            Map.of("dimensionDefinitionId", reference.dimensionDefinitionId(), "revision", reference.revision())
        );
    }

    private void validateNoDependencyCycle(
        String tenantId,
        UUID modelSpecId,
        ModelType modelType,
        List<ModelSpecContract.ModelRevisionRef> dependencies
    ) {
        if (modelType != ModelType.FACT && modelType != ModelType.SUMMARY && modelType != ModelType.APPLICATION) return;
        List<String> path = new ArrayList<>();
        path.add(modelSpecId.toString());
        Set<UUID> activeModelIds = new HashSet<>();
        activeModelIds.add(modelSpecId);
        Set<ModelSpecContract.ModelRevisionRef> visitedRevisions = new HashSet<>();
        for (ModelSpecContract.ModelRevisionRef dependency : dependencies) {
            traverseDependency(tenantId, dependency, activeModelIds, visitedRevisions, path);
        }
    }

    private void traverseDependency(
        String tenantId,
        ModelSpecContract.ModelRevisionRef reference,
        Set<UUID> activeModelIds,
        Set<ModelSpecContract.ModelRevisionRef> visitedRevisions,
        List<String> path
    ) {
        UUID referencedModelId = reference.modelSpecId();
        path.add(referencedModelId.toString());
        if (!activeModelIds.add(referencedModelId)) throw dependencyCycle(path);
        if (!visitedRevisions.add(reference)) {
            activeModelIds.remove(referencedModelId);
            path.remove(path.size() - 1);
            return;
        }

        StoredModelSpec stored = repository
            .findRevision(tenantId, referencedModelId, reference.revision())
            .orElseThrow(ModelSpecApplicationService::referenceNotFound);
        ModelSpecView referenced = compatibilityReader.read(stored);
        if (!canRead(referenced)) throw referenceNotFound();
        for (ModelSpecContract.ModelRevisionRef dependency : referenced.dependsOn()) {
            traverseDependency(tenantId, dependency, activeModelIds, visitedRevisions, path);
        }
        activeModelIds.remove(referencedModelId);
        path.remove(path.size() - 1);
    }

    private void validateSources(String tenantId, String actorId, UUID planId, List<ModelSpecContract.SourceRef> sourceRefs) {
        for (ModelSpecContract.SourceRef sourceRef : sourceRefs) {
            if (!sourceValidation.isCurrentBinding(tenantId, planId, actorId, sourceRef)) throw sourceBindingInvalid();
        }
    }

    private void validateReferenceSet(
        String tenantId,
        UUID planId,
        List<ModelSpecContract.ModelRevisionRef> references,
        boolean dimensionOnly,
        ModelType ownerType
    ) {
        for (ModelSpecContract.ModelRevisionRef reference : references) {
            StoredModelSpec stored = repository
                .findRevision(tenantId, reference.modelSpecId(), reference.revision())
                .orElseThrow(ModelSpecApplicationService::referenceNotFound);
            ModelSpecView referenced = compatibilityReader.read(stored);
            if (!canRead(referenced)) throw referenceNotFound();
            if (dimensionOnly && !ModelSpecContract.isCanonicalDimension(referenced)) {
                throw new ModelSpecException(
                    "MODEL_SPEC_DIMENSION_REF_TYPE_INVALID",
                    "Dimension references must point to canonical DIMENSION ModelSpecs at DWD",
                    ModelSpecException.Kind.UNPROCESSABLE,
                    List.of(fieldIssue("dimensionRefs", "Choose a canonical DIMENSION model at DWD"))
                );
            }
            if (
                !dimensionOnly &&
                (
                    !ModelSpecContract.isCanonicalReferenceTarget(referenced) ||
                    !ModelSpecContract.allowsUpstreamModel(ownerType, referenced.modelType(), referenced.layer())
                )
            ) {
                throw new ModelSpecException(
                    "MODEL_SPEC_UPSTREAM_LAYER_NOT_ALLOWED",
                    "Upstream ModelSpec type or layer is not allowed for the target model",
                    ModelSpecException.Kind.UNPROCESSABLE,
                    List.of(fieldIssue("dependsOn", "Choose an upstream model from an allowed lower or same warehouse layer"))
                );
            }
            if (!Objects.equals(planId, referenced.planId()) && referenced.status() != ModelStatus.PUBLISHED) {
                throw new ModelSpecException(
                    "MODEL_SPEC_CROSS_PLAN_REF_INVALID",
                    "Cross-plan references must point to published ModelSpecs",
                    ModelSpecException.Kind.UNPROCESSABLE
                );
            }
        }
    }

    private boolean canRead(ModelSpecView view) {
        if (view.contractVersion() == ModelSpecContract.CONTRACT_VERSION && !featureFlags.canonicalReadEnabled()) {
            return false;
        }
        if (view.domainId() != null) return domainReadAccess.canRead(view.domainId());
        return (
            view.contractVersion() == ModelingVNextContract.CONTRACT_VERSION &&
            view.compatibilityMode() == ModelSpecContract.CompatibilityMode.LEGACY_READONLY
        );
    }

    private CreateResult replay(StoredModelSpec stored, String requestHash) {
        if (!Objects.equals(stored.idempotencyRequestHash(), requestHash)) {
            throw new ModelSpecException(
                "MODEL_SPEC_IDEMPOTENCY_CONFLICT",
                "The idempotency key was already used for different ModelSpec content",
                ModelSpecException.Kind.CONFLICT
            );
        }
        if (stored.idempotencyResponseSnapshot() == null || stored.idempotencyResponseSnapshot().isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_IDEMPOTENCY_SNAPSHOT_MISSING",
                "The original ModelSpec create response snapshot is missing",
                ModelSpecException.Kind.CONFLICT
            );
        }
        ModelSpecView response = codec.readView(stored.idempotencyResponseSnapshot());
        String responseChecksum = codec.contentChecksum(response);
        if (
            response.contractVersion() != ModelSpecContract.CONTRACT_VERSION ||
            response.compatibilityMode() != ModelSpecContract.CompatibilityMode.CANONICAL ||
            !Objects.equals(response.id(), stored.id()) ||
            !Objects.equals(response.planId(), stored.planId()) ||
            !Objects.equals(response.domainId(), stored.domainId()) ||
            response.status() != ModelStatus.DRAFT ||
            response.revision() != 1 ||
            !Objects.equals(response.checksum(), responseChecksum) ||
            !Objects.equals(requestHash, responseChecksum)
        ) {
            throw new ModelSpecException(
                "MODEL_SPEC_IDEMPOTENCY_SNAPSHOT_INVALID",
                "The original ModelSpec create response snapshot is inconsistent",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return new CreateResult(response, true);
    }

    private void validateWriteContext(String tenantId, String actorId, UUID planId, UUID domainId) {
        PlanState plan = repository
            .lockPlan(tenantId, planId)
            .orElseThrow(() ->
                new ModelSpecException(
                    "MODEL_SPEC_PLAN_INVALID",
                    "Warehouse plan does not exist in the server tenant",
                    ModelSpecException.Kind.UNPROCESSABLE,
                    List.of(fieldIssue("planId", "Warehouse plan is unavailable"))
                )
            );
        if ("PUBLISHED".equals(plan.lifecycleStatus()) || "ARCHIVED".equals(plan.lifecycleStatus())) {
            throw new ModelSpecException(
                "MODEL_SPEC_PLAN_READONLY",
                "Warehouse plan lifecycle does not allow model edits",
                ModelSpecException.Kind.CONFLICT,
                Map.of("lifecycleStatus", plan.lifecycleStatus())
            );
        }
        if (!planWriteAccess.canMaintain(tenantId, planId, actorId)) {
            throw new ModelSpecException(
                "MODEL_SPEC_PLAN_FORBIDDEN",
                "Warehouse plan is not available for maintenance",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
        DomainBindingState binding = repository
            .lockDomainBinding(tenantId, planId, domainId)
            .orElseThrow(() -> domainNotConfirmed(domainId));
        if (!"CONFIRMED".equals(binding.confirmationStatus())) throw domainNotConfirmed(domainId);

        DomainResolution resolution = domainResolution.resolve(domainId);
        if (resolution == null || resolution.status() == null) {
            throw new ModelSpecException(
                "MODEL_SPEC_DOMAIN_RESOLUTION_FAILED",
                "Business category resolution is unavailable",
                ModelSpecException.Kind.CONFLICT
            );
        }
        switch (resolution.status()) {
            case MISSING -> throw unavailableDomain("MODEL_SPEC_DOMAIN_MISSING", "Business category does not exist");
            case ARCHIVED -> throw unavailableDomain("MODEL_SPEC_DOMAIN_ARCHIVED", "Business category is archived");
            case FORBIDDEN -> throw forbiddenDomain();
            case AVAILABLE -> {
                if (!domainWriteAccess.canMaintain(domainId)) throw forbiddenDomain();
            }
        }
    }

    private void validateReplayAccess(String tenantId, String actorId, StoredModelSpec stored) {
        boolean planVisible = planWriteAccess.canMaintain(tenantId, stored.planId(), actorId);
        boolean domainVisible = stored.domainId() == null || domainReadAccess.canRead(stored.domainId());
        if (!planVisible || !domainVisible) throw notFound(stored.id());
    }

    private void requireCanonicalWriteEnabled() {
        if (featureFlags.canonicalWriteEnabled()) return;
        throw new ModelSpecException(
            "MODEL_SPEC_CANONICAL_WRITE_DISABLED",
            "Canonical ModelSpec writes are disabled by the rollback switch",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private static void requireExpected(ModelSpecView current, ExpectedVersion expected) {
        if (current.revision() != expected.revision() || !Objects.equals(current.checksum(), expected.checksum())) {
            throw revisionConflict(current);
        }
    }

    private static ModelSpecException revisionConflict(ModelSpecView current) {
        return new ModelSpecException(
            "MODEL_SPEC_REVISION_CONFLICT",
            "ModelSpec was changed by another operation",
            ModelSpecException.Kind.CONFLICT,
            Map.of(
                "currentRevision",
                current.revision(),
                "currentChecksum",
                current.checksum(),
                "currentEtag",
                etag(current)
            )
        );
    }

    private static ModelSpecException domainNotConfirmed(UUID domainId) {
        return new ModelSpecException(
            "MODEL_SPEC_DOMAIN_NOT_CONFIRMED",
            "Business category is not confirmed in the warehouse plan",
            ModelSpecException.Kind.UNPROCESSABLE,
            List.of(fieldIssue("domainId", "Select a confirmed business category from the current plan"))
        );
    }

    private static ModelSpecException unavailableDomain(String code, String message) {
        return new ModelSpecException(
            code,
            message,
            ModelSpecException.Kind.UNPROCESSABLE,
            List.of(fieldIssue("domainId", "Business category is unavailable"))
        );
    }

    private static ModelSpecException forbiddenDomain() {
        return new ModelSpecException(
            "MODEL_SPEC_DOMAIN_FORBIDDEN",
            "Business category is not available for maintenance",
            ModelSpecException.Kind.FORBIDDEN
        );
    }

    private static ModelSpecException referenceNotFound() {
        return new ModelSpecException(
            "MODEL_SPEC_REFERENCE_NOT_FOUND",
            "Referenced ModelSpec revision is unavailable",
            ModelSpecException.Kind.UNPROCESSABLE
        );
    }

    private static ModelSpecException dependencyCycle(List<String> path) {
        return new ModelSpecException(
            "MODEL_SPEC_DEPENDENCY_CYCLE",
            "ModelSpec dependencies cannot contain a cycle",
            ModelSpecException.Kind.UNPROCESSABLE,
            Map.of("dependencyPath", List.copyOf(path))
        );
    }

    private static ModelSpecException sourceBindingInvalid() {
        return new ModelSpecException(
            "MODEL_SPEC_SOURCE_BINDING_INVALID",
            "Every source must resolve to the confirmed source version in the current warehouse plan",
            ModelSpecException.Kind.UNPROCESSABLE,
            List.of(fieldIssue("sourceRefs", "Select a confirmed source and keep its resolved version unchanged"))
        );
    }

    private static ModelSpecException translateConstraint(DataIntegrityViolationException exception) {
        String message = rootMessage(exception);
        if (message.contains("uk_model_spec_v2_plan_name")) {
            return new ModelSpecException(
                "MODEL_SPEC_NAME_CONFLICT",
                "A ModelSpec with this name already exists in the warehouse plan",
                ModelSpecException.Kind.CONFLICT
            );
        }
        if (message.contains("uk_model_spec_v2_dimension_code")) {
            return new ModelSpecException(
                "MODEL_SPEC_DIMENSION_CODE_CONFLICT",
                "A dimension with this stable code already exists in the tenant",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return new ModelSpecException(
            "MODEL_SPEC_PERSISTENCE_CONFLICT",
            "ModelSpec could not be persisted because its references or uniqueness constraints changed",
            ModelSpecException.Kind.CONFLICT
        );
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private static void rejectIssues(List<FieldIssue> issues) {
        if (issues == null || issues.isEmpty()) return;
        throw new ModelSpecException(
            "MODEL_SPEC_VALIDATION_FAILED",
            "ModelSpec request contains validation errors",
            ModelSpecException.Kind.UNPROCESSABLE,
            issues
        );
    }

    private static FieldIssue fieldIssue(String field, String message) {
        return new FieldIssue("MODEL_SPEC_FIELD_INVALID", field, ModelSpecContract.IssueSeverity.ERROR, message);
    }

    private static void requireServerContext(String tenantId, String actorId) {
        requireTenant(tenantId);
        if (actorId == null || actorId.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_ACTOR_REQUIRED",
                "Authenticated actor is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private static void requireTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ModelSpecException(
                "MODEL_SPEC_SERVER_TENANT_REQUIRED",
                "Server tenant context is required",
                ModelSpecException.Kind.FORBIDDEN
            );
        }
    }

    private static ModelSpecException notFound(UUID id) {
        return new ModelSpecException(
            "MODEL_SPEC_NOT_FOUND",
            "ModelSpec was not found",
            ModelSpecException.Kind.NOT_FOUND,
            id == null ? Map.of() : Map.of("modelSpecId", id)
        );
    }

    public static String etag(ModelSpecView view) {
        return "\"model-spec:" + view.id() + ":" + view.revision() + ":" + view.checksum() + "\"";
    }

    public record CreateResult(ModelSpecView modelSpec, boolean replayed) {}

    public record ExpectedVersion(UUID modelSpecId, int revision, String checksum) {}

    public record DependencyGraph(UUID rootModelSpecId, List<DependencyNode> nodes, List<DependencyEdge> edges) {}

    public record DependencyNode(
        UUID modelSpecId,
        int pinnedRevision,
        Integer currentRevision,
        String name,
        ModelType modelType,
        ModelStatus status,
        boolean restricted
    ) {}

    public record DependencyEdge(
        UUID fromModelSpecId,
        UUID toModelSpecId,
        int pinnedRevision,
        Integer currentRevision,
        DependencyState state
    ) {}

    public enum DependencyState {
        CURRENT,
        STALE,
        UNKNOWN,
    }
}
