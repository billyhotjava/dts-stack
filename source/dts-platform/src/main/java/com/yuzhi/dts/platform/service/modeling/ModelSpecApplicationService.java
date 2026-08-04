package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.DomainBindingState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.PlanState;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.FieldIssue;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelStatus;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerApplicationService;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehouseLayerContract;
import com.yuzhi.dts.platform.service.modeling.warehouse.CatalogDomainResolutionPort.DomainResolution;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
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
    private final ModelSpecReader compatibilityReader;
    private final ModelSpecFeatureFlags featureFlags;
    private final AuditService auditService;
    private final WarehouseLayerApplicationService warehouseLayers;
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
        ModelSpecReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags,
        AuditService auditService,
        WarehouseLayerApplicationService warehouseLayers
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
            auditService,
            warehouseLayers,
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
        ModelSpecReader compatibilityReader,
        AuditService auditService,
        WarehouseLayerApplicationService warehouseLayers,
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
            auditService,
            warehouseLayers,
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
        ModelSpecReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags,
        AuditService auditService,
        WarehouseLayerApplicationService warehouseLayers,
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
            auditService,
            warehouseLayers,
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
        ModelSpecReader compatibilityReader,
        AuditService auditService,
        WarehouseLayerApplicationService warehouseLayers,
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
            auditService,
            warehouseLayers,
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
        ModelSpecReader compatibilityReader,
        ModelSpecFeatureFlags featureFlags,
        AuditService auditService,
        WarehouseLayerApplicationService warehouseLayers,
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
        this.auditService = auditService;
        this.warehouseLayers = warehouseLayers;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Transactional
    public CreateResult create(String serverTenantId, String actorId, CreateModelSpecCommand command) {
        CreateResult result = createInternal(serverTenantId, actorId, null, command);
        if (!result.replayed()) {
            audit("MODELING_MODEL_SPEC_CREATE", serverTenantId, actorId, result.modelSpec());
        }
        return result;
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
        CreateResult result = createInternal(serverTenantId, actorId, previewedModelSpecId, command);
        if (!result.replayed()) {
            audit("MODELING_MODEL_SPEC_IMPORT_CREATE", serverTenantId, actorId, result.modelSpec());
        }
        return result;
    }

    private CreateResult createInternal(
        String serverTenantId,
        String actorId,
        UUID previewedModelSpecId,
        CreateModelSpecCommand command
    ) {
        requireServerContext(serverTenantId, actorId);
        requireDimensionDefinitionRef(command);
        rejectIssues(
            previewedModelSpecId == null
                ? ModelSpecContract.validateInteractiveCreate(command)
                : ModelSpecContract.validateCreate(command)
        );
        command = resolveWarehouseLayerSelection(command);
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
        validateDataMartContext(serverTenantId, command.planId(), command.domainId(), command.dataMartId());
        validateSources(serverTenantId, actorId, command.planId(), command.sourceRefs());
        validateDimensionDefinition(
            serverTenantId,
            command.modelType(),
            command.dimensionDefinitionRef(),
            command.dataMartId(),
            command.fields(),
            true,
            true
        );
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
        requireUniqueDimensionVariant(serverTenantId, view, null);
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
        if (current.modelType() != command.modelType()) {
            throw new ModelSpecException(
                "MODEL_SPEC_TYPE_IMMUTABLE",
                "Use the explicit model type adjustment workflow to change a draft model type",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("currentType", current.modelType(), "requestedType", command.modelType())
            );
        }
        if (current.layer() != command.layer()) {
            throw new ModelSpecException(
                "MODEL_SPEC_LAYER_IMMUTABLE",
                "The target layer is derived from the model type and cannot be edited directly",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("currentLayer", current.layer(), "requestedLayer", command.layer())
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
        command = resolveWarehouseLayerSelection(command);
        ModelSpecView replacement = codec.toUpdatedView(current, command, current.revision() + 1, clock.instant());
        requireDimensionDefinitionRef(replacement);
        rejectIssues(ModelSpecContract.validateView(replacement));
        requireUniqueDimensionVariant(serverTenantId, replacement, current.id());
        validateDataMartContext(
            serverTenantId,
            replacement.planId(),
            replacement.domainId(),
            replacement.dataMartId()
        );
        validateSources(serverTenantId, actorId, command.planId(), command.sourceRefs());
        validateDimensionDefinition(
            serverTenantId,
            replacement.modelType(),
            replacement.dimensionDefinitionRef(),
            replacement.dataMartId(),
            replacement.fields(),
            false,
            false
        );
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
        audit("MODELING_MODEL_SPEC_UPDATE", serverTenantId, actorId, replacement);
        return replacement;
    }

    @Transactional
    public ReclassificationPreview previewReclassification(
        String serverTenantId,
        String actorId,
        UUID modelSpecId,
        ReclassificationPreviewRequest request
    ) {
        requireServerContext(serverTenantId, actorId);
        requireCanonicalWriteEnabled();
        if (modelSpecId == null) throw notFound(null);
        if (request == null || request.targetType() == null) {
            throw new ModelSpecException(
                "MODEL_RECLASSIFY_TARGET_REQUIRED",
                "Select the intended model type",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        ModelSpecView current = currentForReclassification(serverTenantId, actorId, modelSpecId);
        return previewReclassification(serverTenantId, current, request);
    }

    @Transactional
    public ModelSpecView reclassify(
        String serverTenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected,
        ReclassificationCommand command
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
        if (
            command == null ||
            command.targetType() == null ||
            command.idempotencyKey() == null ||
            command.idempotencyKey().isBlank() ||
            command.idempotencyKey().length() > 128
        ) {
            throw new ModelSpecException(
                "MODEL_RECLASSIFY_REQUEST_INVALID",
                "Target type and an idempotency key of at most 128 characters are required",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }

        ModelSpecRepository.ReclassificationReplay replay = repository
            .findReclassificationReplay(serverTenantId, modelSpecId, command.idempotencyKey())
            .orElse(null);
        if (replay != null) {
            StoredModelSpec storedReplay = repository
                .findRevision(serverTenantId, modelSpecId, replay.revision())
                .orElseThrow(() -> notFound(modelSpecId));
            validateReplayAccess(serverTenantId, actorId, storedReplay);
            ModelSpecView replayed = compatibilityReader.read(storedReplay);
            if (!Objects.equals(replayed.checksum(), replay.checksum())) {
                throw new ModelSpecException(
                    "MODEL_RECLASSIFY_REPLAY_INVALID",
                    "The stored reclassification result no longer matches its audit record",
                    ModelSpecException.Kind.CONFLICT
                );
            }
            return replayed;
        }

        ModelSpecView current = currentForReclassification(serverTenantId, actorId, modelSpecId);
        requireExpected(current, expected);
        ReclassificationPreview preview = previewReclassification(
            serverTenantId,
            current,
            new ReclassificationPreviewRequest(command.targetType(), command.dimensionDefinitionRef())
        );
        if (!preview.eligible()) {
            throw new ModelSpecException(
                "MODEL_RECLASSIFY_NOT_ALLOWED",
                "This draft already has runtime evidence or is not eligible for in-place type adjustment",
                ModelSpecException.Kind.CONFLICT,
                preview
            );
        }
        Set<String> accepted = command.acceptedClearFields() == null
            ? Set.of()
            : Set.copyOf(command.acceptedClearFields());
        List<String> missingAcceptances = preview.clearFields().stream().filter(field -> !accepted.contains(field)).toList();
        if (!missingAcceptances.isEmpty()) {
            throw new ModelSpecException(
                "MODEL_RECLASSIFY_CLEAR_FIELDS_NOT_ACCEPTED",
                "Confirm every field that will be cleared before applying the type adjustment",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("missingAcceptedClearFields", missingAcceptances)
            );
        }

        CreateModelSpecCommand projected = projectReclassification(current, command);
        ModelSpecView replacement = codec.toReclassifiedView(
            current,
            projected,
            current.revision() + 1,
            clock.instant()
        );
        requireDimensionDefinitionRef(replacement);
        validateDimensionDefinition(
            serverTenantId,
            replacement.modelType(),
            replacement.dimensionDefinitionRef(),
            replacement.dataMartId(),
            replacement.fields(),
            true,
            true
        );
        requireUniqueDimensionVariant(serverTenantId, replacement, current.id());
        String snapshot = codec.write(replacement);
        int updated = repository.compareAndSetV2(
            serverTenantId,
            actorId,
            current.revision(),
            current.checksum(),
            replacement,
            snapshot
        );
        if (updated == 0) {
            StoredModelSpec latest = repository.findCurrent(serverTenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
            throw revisionConflict(compatibilityReader.read(latest));
        }
        repository.insertV2Revision(serverTenantId, actorId, replacement, snapshot);
        repository.insertReclassificationCommand(
            serverTenantId,
            actorId,
            modelSpecId,
            command.idempotencyKey(),
            current.modelType(),
            replacement.modelType(),
            replacement.revision(),
            replacement.checksum(),
            clock.instant()
        );
        audit("MODELING_MODEL_SPEC_RECLASSIFY", serverTenantId, actorId, replacement);
        return replacement;
    }

    private ModelSpecView currentForReclassification(String tenantId, String actorId, UUID modelSpecId) {
        StoredModelSpec stored = repository.findCurrent(tenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
        ModelSpecView current = compatibilityReader.read(stored);
        validateWriteContext(tenantId, actorId, current.planId(), current.domainId());
        return current;
    }

    private ReclassificationPreview previewReclassification(
        String tenantId,
        ModelSpecView current,
        ReclassificationPreviewRequest request
    ) {
        List<String> reasons = new ArrayList<>();
        if (current.status() != ModelStatus.DRAFT) reasons.add("MODEL_RECLASSIFY_DRAFT_REQUIRED");
        if (current.compatibilityMode() != ModelSpecContract.CompatibilityMode.CANONICAL) {
            reasons.add("MODEL_RECLASSIFY_CANONICAL_REQUIRED");
        }
        if (current.modelType() == request.targetType()) reasons.add("MODEL_RECLASSIFY_TYPE_UNCHANGED");
        if (repository.hasReclassificationEvidence(tenantId, current.id())) {
            reasons.add("MODEL_RECLASSIFY_RUNTIME_EVIDENCE_EXISTS");
        }
        if (request.targetType() == ModelType.DIMENSION && request.dimensionDefinitionRef() == null) {
            reasons.add("MODEL_RECLASSIFY_DIMENSION_DEFINITION_REQUIRED");
        }
        if (request.targetType() == ModelType.DIMENSION && request.dimensionDefinitionRef() != null) {
            validateDimensionDefinition(
                tenantId,
                request.targetType(),
                request.dimensionDefinitionRef(),
                current.dataMartId(),
                current.fields(),
                true,
                false
            );
        }
        return new ReclassificationPreview(
            reasons.isEmpty(),
            current.modelType(),
            request.targetType(),
            ModelSpecContract.targetLayer(request.targetType()),
            retainedFields(current, request.targetType()),
            requiredFields(request.targetType()),
            clearFields(current, request.targetType()),
            List.copyOf(reasons),
            current.revision(),
            current.checksum()
        );
    }

    private static CreateModelSpecCommand projectReclassification(
        ModelSpecView current,
        ReclassificationCommand command
    ) {
        List<ModelSpecContract.ModelField> fields = command.targetType() == ModelType.DIMENSION
            ? current
                .fields()
                .stream()
                .map(field ->
                    field != null && (field.role() == ModelSpecContract.FieldRole.TIME || field.role() == ModelSpecContract.FieldRole.MEASURE)
                        ? new ModelSpecContract.ModelField(
                            field.name(),
                            field.displayName(),
                            field.dataType(),
                            field.nullable(),
                            field.sourceFieldRef(),
                            ModelSpecContract.FieldRole.ATTRIBUTE,
                            field.securityLevel(),
                            field.dimensionAttributeCode(),
                            field.redundant(),
                            field.redundancySourceRef()
                        )
                        : field
                )
                .toList()
            : current.fields();
        boolean directInput = command.targetType() == ModelType.DIMENSION || command.targetType() == ModelType.FACT;
        return new CreateModelSpecCommand(
            current.planId(),
            current.domainId(),
            command.targetType(),
            ModelSpecContract.targetLayer(command.targetType()),
            current.name(),
            current.description(),
            ModelSpecContract.ImplementationMode.DESIGNER_GENERATED,
            null,
            null,
            null,
            null,
            null,
            null,
            fields,
            directInput ? current.sourceRefs() : List.of(),
            List.of(),
            List.of(),
            List.of(),
            current.standardBindings(),
            null,
            null,
            command.targetType() == ModelType.DIMENSION ? command.dimensionDefinitionRef() : null,
            null,
            current.dataMartId(),
            command.targetType() == ModelType.DIMENSION ? current.variantCode() : null,
            null,
            null
        );
    }

    private static List<String> retainedFields(ModelSpecView current, ModelType targetType) {
        List<String> retained = new ArrayList<>(List.of("name", "description", "fields", "standardBindings"));
        if (current.dataMartId() != null) retained.add("dataMartId");
        if ((targetType == ModelType.DIMENSION || targetType == ModelType.FACT) && !current.sourceRefs().isEmpty()) {
            retained.add("sourceRefs");
        }
        return List.copyOf(retained);
    }

    private static List<String> requiredFields(ModelType targetType) {
        return switch (targetType) {
            case DIMENSION -> List.of("dimensionDefinitionRef", "grain", "fields.KEY", "dimensionProfile.scdPolicy");
            case FACT -> List.of("grain", "factShape", "timeSemantics", "fields.TIME");
            case SUMMARY -> List.of("grain", "dependsOn", "fields.MEASURE");
            case APPLICATION -> List.of("consumptionScenario", "dependsOn");
        };
    }

    private static List<String> clearFields(ModelSpecView current, ModelType targetType) {
        List<String> fields = new ArrayList<>();
        if (current.materialization() != null) fields.add("materialization");
        if (current.grain() != null) fields.add("grain");
        if (current.businessActivityRef() != null) fields.add("businessActivityRef");
        if (current.consumptionScenario() != null) fields.add("consumptionScenario");
        if (current.factShape() != null) fields.add("factShape");
        if (current.timeSemantics() != null) fields.add("timeSemantics");
        if (targetType != ModelType.DIMENSION && targetType != ModelType.FACT && !current.sourceRefs().isEmpty()) {
            fields.add("sourceRefs");
        }
        if (!current.dependsOn().isEmpty()) fields.add("dependsOn");
        if (!current.dimensionRefs().isEmpty()) fields.add("dimensionRefs");
        if (!current.metricRefs().isEmpty()) fields.add("metricRefs");
        if (current.generationStrategy() != null) fields.add("generationStrategy");
        if (current.dimensionProfile() != null) fields.add("dimensionProfile");
        if (targetType != ModelType.DIMENSION && current.dimensionDefinitionRef() != null) {
            fields.add("dimensionDefinitionRef");
        }
        if (targetType != ModelType.DIMENSION && current.variantCode() != null) fields.add("variantCode");
        if (current.implementationPolicy() != null) fields.add("implementationPolicy");
        if (
            targetType == ModelType.DIMENSION &&
            current
                .fields()
                .stream()
                .anyMatch(field ->
                    field != null &&
                    (field.role() == ModelSpecContract.FieldRole.TIME || field.role() == ModelSpecContract.FieldRole.MEASURE)
                )
        ) {
            fields.add("fields.roles");
        }
        return List.copyOf(fields);
    }

    @Transactional
    public void deleteDraft(
        String serverTenantId,
        String actorId,
        UUID modelSpecId,
        ExpectedVersion expected
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
        ModelSpecView current = compatibilityReader.read(stored);
        validateWriteContext(serverTenantId, actorId, current.planId(), current.domainId());
        requireExpected(current, expected);
        if (current.status() != ModelStatus.DRAFT) {
            throw new ModelSpecException(
                "MODEL_SPEC_DELETE_STATUS_INVALID",
                "Only DRAFT ModelSpecs can be deleted",
                ModelSpecException.Kind.CONFLICT,
                Map.of("status", current.status())
            );
        }
        if (repository.hasActiveModelReferences(serverTenantId, modelSpecId)) {
            throw new ModelSpecException(
                "MODEL_SPEC_DELETE_REFERENCED",
                "The draft is referenced by another active ModelSpec",
                ModelSpecException.Kind.CONFLICT
            );
        }

        ModelSpecView archived = codec.toLifecycleView(current, ModelStatus.ARCHIVED, current.revision(), clock.instant());
        int head = repository.compareAndSetLifecycle(
            serverTenantId,
            actorId,
            current.revision(),
            current.checksum(),
            ModelStatus.DRAFT,
            archived
        );
        if (head == 0) {
            StoredModelSpec latest = repository.findCurrent(serverTenantId, modelSpecId).orElseThrow(() -> notFound(modelSpecId));
            throw revisionConflict(compatibilityReader.read(latest));
        }
        int revision = repository.updateV2RevisionLifecycle(
            serverTenantId,
            actorId,
            ModelStatus.DRAFT,
            archived,
            codec.write(archived)
        );
        if (revision == 0) {
            throw new ModelSpecException(
                "MODEL_SPEC_DELETE_CONFLICT",
                "ModelSpec changed before the draft deletion was committed",
                ModelSpecException.Kind.CONFLICT
            );
        }
        audit("MODELING_MODEL_SPEC_DELETE_DRAFT", serverTenantId, actorId, archived);
    }

    private void audit(String actionCode, String tenantId, String actorId, ModelSpecView view) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        payload.put("planId", view.planId());
        payload.put("domainId", view.domainId());
        payload.put("name", view.name());
        payload.put("modelType", view.modelType().name());
        payload.put("layer", view.layer().name());
        payload.put("revision", view.revision());
        payload.put("status", view.status().name());
        auditService.auditAction(actionCode, AuditStage.SUCCESS, view.id().toString(), payload);
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
    public List<ModelSpecView> listForRelationshipGraph(String serverTenantId, UUID planId, int limit) {
        return listForRelationshipGraph(serverTenantId, planId, null, limit);
    }

    @Transactional(readOnly = true)
    public List<ModelSpecView> listForRelationshipGraph(
        String serverTenantId,
        UUID planId,
        UUID afterId,
        int limit
    ) {
        requireTenant(serverTenantId);
        if (planId == null || limit < 1 || limit > 501) {
            throw new ModelSpecException(
                "MODEL_SPEC_RELATIONSHIP_GRAPH_WINDOW_INVALID",
                "Relationship graph ModelSpec limit must be between 1 and 501",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        Set<UUID> visibleDomainIds = domainReadAccess.visibleDomainIds();
        Set<UUID> effectiveVisibleDomainIds = visibleDomainIds == null ? Set.of() : visibleDomainIds;
        List<StoredModelSpec> stored = afterId == null
            ? repository.listCurrentForRelationshipGraph(
                serverTenantId,
                planId,
                effectiveVisibleDomainIds,
                limit
            )
            : repository.listCurrentForRelationshipGraph(
                serverTenantId,
                planId,
                effectiveVisibleDomainIds,
                afterId,
                limit
            );
        return compatibilityReader.readForRelationshipGraph(stored);
    }

    @Transactional(readOnly = true)
    public List<ModelSpecView> revisionsForRelationshipGraph(
        String serverTenantId,
        Collection<ModelRevisionRef> references,
        int limit
    ) {
        requireTenant(serverTenantId);
        if (limit < 1 || limit > 500) {
            throw new ModelSpecException(
                "MODEL_SPEC_RELATIONSHIP_GRAPH_WINDOW_INVALID",
                "Relationship graph ModelSpec revision limit must be between 1 and 500",
                ModelSpecException.Kind.BAD_REQUEST
            );
        }
        if (references == null || references.isEmpty()) return List.of();
        Set<ModelRevisionRef> unique = new java.util.LinkedHashSet<>();
        int inspected = 0;
        for (ModelRevisionRef reference : references) {
            if (++inspected > limit || unique.size() >= limit) break;
            if (reference != null && reference.modelSpecId() != null && reference.revision() > 0) {
                unique.add(reference);
            }
        }
        List<ModelRevisionRef> boundedReferences = unique
            .stream()
            .sorted(
                java.util.Comparator
                    .comparing(ModelRevisionRef::modelSpecId)
                    .thenComparingInt(ModelRevisionRef::revision)
            )
            .toList();
        if (boundedReferences.isEmpty()) return List.of();
        Set<UUID> visibleDomainIds = domainReadAccess.visibleDomainIds();
        List<StoredModelSpec> stored = repository.listRevisionsForRelationshipGraph(
            serverTenantId,
            boundedReferences,
            visibleDomainIds == null ? Set.of() : visibleDomainIds,
            limit
        );
        return compatibilityReader.readForRelationshipGraph(stored);
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
        UUID dataMartId,
        List<ModelSpecContract.ModelField> fields,
        boolean requireCurrentRevision,
        boolean lockCurrent
    ) {
        if (modelType != ModelType.DIMENSION || reference == null || dimensionDefinitions == null) return;
        StoredDimensionDefinition pinned = dimensionDefinitions
            .findRevision(tenantId, reference.dimensionDefinitionId(), reference.revision())
            .orElseThrow(() -> dimensionDefinitionNotCurrent(reference));
        if (!domainReadAccess.canRead(pinned.domainId())) throw dimensionDefinitionNotCurrent(reference);
        StoredDimensionDefinition current = (
                lockCurrent
                    ? dimensionDefinitions.findCurrentForShare(tenantId, reference.dimensionDefinitionId())
                    : dimensionDefinitions.findCurrent(tenantId, reference.dimensionDefinitionId())
            )
            .orElseThrow(() -> dimensionDefinitionNotCurrent(reference));
        if (
            current.status() != DimensionDefinitionContract.Status.CURRENT ||
            (requireCurrentRevision && current.revision() != reference.revision())
        ) {
            throw dimensionDefinitionNotCurrent(reference);
        }
        if (
            pinned.scopeType() == DimensionDefinitionContract.ScopeType.DATA_MART &&
            !Objects.equals(pinned.dataMartId(), dataMartId)
        ) {
            throw new ModelSpecException(
                "MODEL_SPEC_DATA_MART_SCOPE_MISMATCH",
                "The model must use the data mart selected by its dimension definition",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of(
                    "dimensionDefinitionId",
                    reference.dimensionDefinitionId(),
                    "requiredDataMartId",
                    pinned.dataMartId()
                )
            );
        }
        Set<String> attributeCodes = pinned
            .attributes()
            .stream()
            .map(DimensionDefinitionContract.AttributeSemantic::code)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (attributeCodes.isEmpty()) return;
        for (ModelSpecContract.ModelField field : fields == null ? List.<ModelSpecContract.ModelField>of() : fields) {
            if (
                field != null &&
                field.dimensionAttributeCode() != null &&
                !attributeCodes.contains(field.dimensionAttributeCode())
            ) {
                throw new ModelSpecException(
                    "MODEL_SPEC_DIMENSION_ATTRIBUTE_UNKNOWN",
                    "A model field references an attribute outside the selected dimension definition",
                    ModelSpecException.Kind.UNPROCESSABLE,
                    Map.of(
                        "fieldName",
                        field.name(),
                        "dimensionAttributeCode",
                        field.dimensionAttributeCode(),
                        "dimensionDefinitionId",
                        reference.dimensionDefinitionId()
                    )
                );
            }
        }
    }

    private void validateDataMartContext(String tenantId, UUID planId, UUID domainId, UUID dataMartId) {
        if (dataMartId == null) return;
        if (!repository.planHasCurrentDataMart(tenantId, planId, dataMartId, domainId)) {
            throw new ModelSpecException(
                "MODEL_SPEC_DATA_MART_NOT_IN_PLAN",
                "Select a confirmed data mart from the current warehouse planning baseline",
                ModelSpecException.Kind.UNPROCESSABLE,
                Map.of("planId", planId, "domainId", domainId, "dataMartId", dataMartId)
            );
        }
    }

    private void requireUniqueDimensionVariant(String tenantId, ModelSpecView view, UUID excludingModelSpecId) {
        if (view.modelType() != ModelType.DIMENSION || view.dimensionDefinitionRef() == null) return;
        UUID existingId = repository
            .findActiveDimensionVariant(
                tenantId,
                view.planId(),
                view.dimensionDefinitionRef().dimensionDefinitionId(),
                view.dataMartId(),
                view.variantCode(),
                excludingModelSpecId
            )
            .orElse(null);
        if (existingId == null) return;
        throw new ModelSpecException(
            "MODEL_SPEC_DIMENSION_VARIANT_CONFLICT",
            "An active implementation already exists for this plan, dimension, data mart and variant",
            ModelSpecException.Kind.CONFLICT,
            Map.of(
                "existingModelSpecId",
                existingId,
                "repairRoute",
                "/modeling/models/" + existingId + "?tab=design"
            )
        );
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
        return (
            view != null &&
            view.contractVersion() == ModelSpecContract.CONTRACT_VERSION &&
            view.compatibilityMode() == ModelSpecContract.CompatibilityMode.CANONICAL &&
            view.legacyRefs() == null &&
            view.domainId() != null &&
            domainReadAccess.canRead(view.domainId())
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

    /** Reuses the canonical owner-scoped replay policy for package-local composite commands. */
    void requireReplayAccess(String tenantId, String actorId, StoredModelSpec stored) {
        requireServerContext(tenantId, actorId);
        if (stored == null) throw notFound(null);
        validateReplayAccess(tenantId, actorId, stored);
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
        if (message.contains("uk_model_spec_active_dimension_variant")) {
            return new ModelSpecException(
                "MODEL_SPEC_DIMENSION_VARIANT_CONFLICT",
                "An active implementation already exists for this plan, dimension, data mart and variant",
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

    private CreateModelSpecCommand resolveWarehouseLayerSelection(CreateModelSpecCommand command) {
        Layer canonicalLayer = ModelSpecContract.targetLayer(command.modelType());
        if (canonicalLayer == null) {
            return command;
        }
        WarehouseLayerContract.ResolvedWarehouseLayer selection = warehouseLayers.resolveSelection(
            command.warehouseLayerCode(),
            canonicalLayer
        );
        return command.withLayerSelection(canonicalLayer, selection.code());
    }

    private UpdateModelSpecCommand resolveWarehouseLayerSelection(UpdateModelSpecCommand command) {
        Layer canonicalLayer = ModelSpecContract.targetLayer(command.modelType());
        if (canonicalLayer == null) {
            return command;
        }
        WarehouseLayerContract.ResolvedWarehouseLayer selection = warehouseLayers.resolveSelection(
            command.warehouseLayerCode(),
            canonicalLayer
        );
        return command.withLayerSelection(command.layer(), selection.code());
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

    public record ReclassificationPreviewRequest(
        ModelType targetType,
        ModelSpecContract.DimensionDefinitionRef dimensionDefinitionRef
    ) {}

    public record ReclassificationCommand(
        ModelType targetType,
        ModelSpecContract.DimensionDefinitionRef dimensionDefinitionRef,
        List<String> acceptedClearFields,
        String idempotencyKey
    ) {
        public ReclassificationCommand {
            acceptedClearFields = acceptedClearFields == null ? List.of() : List.copyOf(acceptedClearFields);
            idempotencyKey = idempotencyKey == null ? null : idempotencyKey.trim();
        }
    }

    public record ReclassificationPreview(
        boolean eligible,
        ModelType fromType,
        ModelType toType,
        Layer targetLayer,
        List<String> retainedFields,
        List<String> requiredFields,
        List<String> clearFields,
        List<String> reasonCodes,
        int currentRevision,
        String checksum
    ) {
        public ReclassificationPreview {
            retainedFields = List.copyOf(retainedFields);
            requiredFields = List.copyOf(requiredFields);
            clearFields = List.copyOf(clearFields);
            reasonCodes = List.copyOf(reasonCodes);
        }
    }

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
