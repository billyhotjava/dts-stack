package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository;
import com.yuzhi.dts.platform.repository.modeling.DimensionDefinitionRepository.StoredDimensionDefinition;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelSpecRepository.StoredModelSpec;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.Status;
import com.yuzhi.dts.platform.service.modeling.DimensionDefinitionContract.View;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.BindingMode;
import com.yuzhi.dts.platform.service.modeling.DimensionModelCreateRequestDecoder.PreparedCreate;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.DimensionDefinitionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.Layer;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomic orchestration and recovery boundary for a complete dimension ModelSpec operation. */
@Service
public class DimensionModelApplicationService {

    private final DimensionDefinitionApplicationService dimensionDefinitions;
    private final ModelSpecApplicationService modelSpecs;
    private final DimensionDefinitionRepository dimensionDefinitionRepository;
    private final ModelSpecRepository modelSpecRepository;
    private final DimensionModelCreateRequestDecoder decoder;
    private final ModelSpecSnapshotCodec modelSpecCodec;
    private final AuditService auditService;

    public DimensionModelApplicationService(
        DimensionDefinitionApplicationService dimensionDefinitions,
        ModelSpecApplicationService modelSpecs,
        DimensionDefinitionRepository dimensionDefinitionRepository,
        ModelSpecRepository modelSpecRepository,
        DimensionModelCreateRequestDecoder decoder,
        ModelSpecSnapshotCodec modelSpecCodec,
        AuditService auditService
    ) {
        this.dimensionDefinitions = dimensionDefinitions;
        this.modelSpecs = modelSpecs;
        this.dimensionDefinitionRepository = dimensionDefinitionRepository;
        this.modelSpecRepository = modelSpecRepository;
        this.decoder = decoder;
        this.modelSpecCodec = modelSpecCodec;
        this.auditService = auditService;
    }

    @Transactional
    public OperationResult create(String tenantId, String actorId, PreparedCreate prepared) {
        StoredModelSpec storedModelOperation = modelSpecRepository
            .findByIdempotencyKey(tenantId, DimensionModelCreateRequestDecoder.modelIdempotencyKey(prepared.operationId()))
            .orElse(null);
        StoredDimensionDefinition storedDefinitionOperation = dimensionDefinitionRepository
            .findByIdempotencyKey(
                tenantId,
                DimensionModelCreateRequestDecoder.definitionIdempotencyKey(prepared.operationId())
            )
            .orElse(null);
        boolean modelOperationAlreadyExists = storedModelOperation != null;
        validateRecordedBinding(
            tenantId,
            actorId,
            prepared,
            storedModelOperation,
            storedDefinitionOperation
        );
        BoundDefinition bound = bindDefinition(
            tenantId,
            actorId,
            prepared,
            modelOperationAlreadyExists
        );
        ModelSpecApplicationService.CreateResult seedCreate = modelSpecs.create(
            tenantId,
            actorId,
            decoder.resolveModelCreateCommand(prepared, bound.reference())
        );
        if (
            prepared.definitionBinding().mode() == BindingMode.CREATE &&
            bound.replayed() != seedCreate.replayed()
        ) {
            throw inconsistent(prepared.operationId(), "definition and model replay state did not converge");
        }

        ModelSpecView operationModel = seedCreate.replayed()
            ? replayCompletedModel(tenantId, prepared, bound.definition(), seedCreate.modelSpec())
            : completeFirstModel(tenantId, actorId, prepared, bound.definition(), seedCreate.modelSpec());
        ModelSpecView currentModel = modelSpecs.get(tenantId, operationModel.id());
        return new OperationResult(
            prepared.operationId(),
            prepared.definitionBinding().mode(),
            bound.definition(),
            operationModel,
            currentModel,
            seedCreate.replayed()
        );
    }

    private void validateRecordedBinding(
        String tenantId,
        String actorId,
        PreparedCreate prepared,
        StoredModelSpec storedModel,
        StoredDimensionDefinition storedDefinition
    ) {
        if (storedModel == null) {
            if (storedDefinition != null) {
                requireVisibleDefinition(tenantId, storedDefinition.id(), prepared.operationId());
                throw inconsistent(prepared.operationId(), "definition exists without a model");
            }
            return;
        }
        requireReplayAccess(tenantId, actorId, storedModel, prepared.operationId());
        requireVisibleModel(tenantId, storedModel.id(), prepared.operationId());
        BindingMode recordedMode = storedDefinition == null ? BindingMode.EXISTING : BindingMode.CREATE;
        if (recordedMode != prepared.definitionBinding().mode()) {
            throw idempotencyConflict(
                prepared.operationId(),
                "the operation id is already bound to another dimension binding mode"
            );
        }
    }

    private void requireReplayAccess(
        String tenantId,
        String actorId,
        StoredModelSpec storedModel,
        UUID operationId
    ) {
        try {
            modelSpecs.requireReplayAccess(tenantId, actorId, storedModel);
        } catch (ModelSpecException exception) {
            if (exception.kind() == ModelSpecException.Kind.NOT_FOUND) throw operationNotFound(operationId);
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public OperationResult recover(String tenantId, String actorId, UUID operationId) {
        String modelKey = DimensionModelCreateRequestDecoder.modelIdempotencyKey(operationId);
        String definitionKey = DimensionModelCreateRequestDecoder.definitionIdempotencyKey(operationId);
        StoredModelSpec storedModel = modelSpecRepository.findByIdempotencyKey(tenantId, modelKey).orElse(null);
        StoredDimensionDefinition storedDefinition = dimensionDefinitionRepository
            .findByIdempotencyKey(tenantId, definitionKey)
            .orElse(null);
        if (storedModel == null) {
            if (storedDefinition != null) {
                requireVisibleDefinition(tenantId, storedDefinition.id(), operationId);
                throw inconsistent(operationId, "definition exists without a model");
            }
            throw operationNotFound(operationId);
        }
        requireReplayAccess(tenantId, actorId, storedModel, operationId);
        ModelSpecView currentModel = requireVisibleModel(tenantId, storedModel.id(), operationId);
        ModelSpecView operationModel = fixedModelRevision(tenantId, storedModel.id(), operationId);
        DimensionDefinitionRef reference = operationModel.dimensionDefinitionRef();
        if (reference == null) throw inconsistent(operationId, "the completed model has no dimension reference");
        BindingMode bindingMode = storedDefinition == null ? BindingMode.EXISTING : BindingMode.CREATE;
        if (storedDefinition != null) {
            requireVisibleDefinition(tenantId, storedDefinition.id(), operationId);
            if (
                !storedDefinition.id().equals(reference.dimensionDefinitionId()) ||
                reference.revision() != 2
            ) {
                throw inconsistent(operationId, "the created definition does not match the completed model");
            }
        }
        View definition = fixedDefinitionRevision(tenantId, reference, operationId);
        validateBoundContext(operationId, definition, operationModel);
        return new OperationResult(
            operationId,
            bindingMode,
            definition,
            operationModel,
            currentModel,
            true
        );
    }

    private ModelSpecView requireVisibleModel(String tenantId, UUID modelSpecId, UUID operationId) {
        try {
            return modelSpecs.get(tenantId, modelSpecId);
        } catch (ModelSpecException exception) {
            if (exception.kind() == ModelSpecException.Kind.NOT_FOUND) throw operationNotFound(operationId);
            throw exception;
        }
    }

    private void requireVisibleDefinition(String tenantId, UUID definitionId, UUID operationId) {
        try {
            dimensionDefinitions.get(tenantId, definitionId);
        } catch (ModelSpecException exception) {
            if (
                exception.kind() == ModelSpecException.Kind.NOT_FOUND ||
                exception.kind() == ModelSpecException.Kind.FORBIDDEN
            ) {
                throw operationNotFound(operationId);
            }
            throw exception;
        }
    }

    private ModelSpecView fixedModelRevision(String tenantId, UUID modelSpecId, UUID operationId) {
        try {
            return modelSpecs.revision(tenantId, new ModelRevisionRef(modelSpecId, 2));
        } catch (ModelSpecException exception) {
            if (exception.kind() == ModelSpecException.Kind.NOT_FOUND) {
                throw inconsistent(operationId, "the fixed completion revision is missing");
            }
            throw exception;
        }
    }

    private View fixedDefinitionRevision(
        String tenantId,
        DimensionDefinitionRef reference,
        UUID operationId
    ) {
        try {
            return dimensionDefinitions.revision(
                tenantId,
                reference.dimensionDefinitionId(),
                reference.revision()
            );
        } catch (ModelSpecException exception) {
            if (exception.kind() == ModelSpecException.Kind.FORBIDDEN) throw operationNotFound(operationId);
            if (exception.kind() == ModelSpecException.Kind.NOT_FOUND) {
                throw inconsistent(operationId, "the fixed dimension revision is missing");
            }
            throw exception;
        }
    }

    private BoundDefinition bindDefinition(
        String tenantId,
        String actorId,
        PreparedCreate prepared,
        boolean modelOperationAlreadyExists
    ) {
        if (prepared.definitionBinding().mode() == BindingMode.EXISTING) {
            DimensionDefinitionRef reference = prepared.definitionBinding().dimensionDefinitionRef();
            View fixed = dimensionDefinitions.revision(
                tenantId,
                reference.dimensionDefinitionId(),
                reference.revision()
            );
            if (!modelOperationAlreadyExists) {
                View current = dimensionDefinitions.get(tenantId, reference.dimensionDefinitionId());
                if (
                    current.status() != Status.CURRENT ||
                    current.revision() != reference.revision() ||
                    !Objects.equals(current.checksum(), fixed.checksum())
                ) {
                    throw new ModelSpecException(
                        "DIMENSION_MODEL_EXISTING_DEFINITION_NOT_CURRENT",
                        "The selected dimension definition is no longer current",
                        ModelSpecException.Kind.CONFLICT,
                        Map.of("dimensionDefinitionId", reference.dimensionDefinitionId())
                    );
                }
            }
            validateBoundContext(prepared.operationId(), fixed, prepared.modelSpec().domainId(), prepared.modelSpec().dataMartId());
            return new BoundDefinition(fixed, reference, modelOperationAlreadyExists);
        }

        DimensionDefinitionApplicationService.CreateResult definitionCreate = dimensionDefinitions.create(
            tenantId,
            actorId,
            prepared.definitionBinding().definition()
        );
        View definition = definitionCreate.replayed()
            ? dimensionDefinitions.revision(tenantId, definitionCreate.dimensionDefinition().id(), 2)
            : dimensionDefinitions.confirm(
                tenantId,
                actorId,
                definitionCreate.dimensionDefinition().id(),
                new ExpectedVersion(
                    definitionCreate.dimensionDefinition().id(),
                    definitionCreate.dimensionDefinition().revision(),
                    definitionCreate.dimensionDefinition().checksum()
                )
            );
        if (definition.status() != Status.CURRENT || definition.revision() != 2) {
            throw inconsistent(prepared.operationId(), "the created dimension did not converge to current revision 2");
        }
        validateBoundContext(prepared.operationId(), definition, prepared.modelSpec().domainId(), prepared.modelSpec().dataMartId());
        return new BoundDefinition(
            definition,
            new DimensionDefinitionRef(definition.id(), definition.revision()),
            definitionCreate.replayed()
        );
    }

    private ModelSpecView completeFirstModel(
        String tenantId,
        String actorId,
        PreparedCreate prepared,
        View definition,
        ModelSpecView seed
    ) {
        if (seed.revision() != 1) throw inconsistent(prepared.operationId(), "the ModelSpec seed is not revision 1");
        ModelSpecView completed = modelSpecs.update(
            tenantId,
            actorId,
            seed.id(),
            new ModelSpecApplicationService.ExpectedVersion(seed.id(), seed.revision(), seed.checksum()),
            prepared.modelSpec()
        );
        if (completed.revision() != 2) {
            throw inconsistent(prepared.operationId(), "the complete logical design did not produce revision 2");
        }
        validateBoundContext(prepared.operationId(), definition, completed);
        auditStrict(tenantId, actorId, prepared.operationId(), definition, completed);
        return completed;
    }

    private ModelSpecView replayCompletedModel(
        String tenantId,
        PreparedCreate prepared,
        View definition,
        ModelSpecView seed
    ) {
        ModelSpecView completed = fixedModelRevision(tenantId, seed.id(), prepared.operationId());
        ModelSpecView expected = modelSpecCodec.toUpdatedView(seed, prepared.modelSpec(), 2, completed.updatedAt());
        if (!Objects.equals(completed.checksum(), expected.checksum())) {
            throw idempotencyConflict(
                prepared.operationId(),
                "the operation id is already bound to another complete logical design"
            );
        }
        validateBoundContext(prepared.operationId(), definition, completed);
        return completed;
    }

    private static void validateBoundContext(UUID operationId, View definition, ModelSpecView modelSpec) {
        validateBoundContext(operationId, definition, modelSpec.domainId(), modelSpec.dataMartId());
        DimensionDefinitionRef reference = modelSpec.dimensionDefinitionRef();
        if (
            modelSpec.modelType() != ModelType.DIMENSION ||
            modelSpec.layer() != Layer.DWD ||
            reference == null ||
            !reference.dimensionDefinitionId().equals(definition.id()) ||
            reference.revision() != definition.revision()
        ) {
            throw inconsistent(operationId, "the completed model does not match the fixed dimension reference");
        }
    }

    private static void validateBoundContext(
        UUID operationId,
        View definition,
        UUID modelDomainId,
        UUID modelDataMartId
    ) {
        if (
            !Objects.equals(definition.domainId(), modelDomainId) ||
            !Objects.equals(definition.dataMartId(), modelDataMartId)
        ) {
            throw inconsistent(operationId, "the definition and model business context do not match");
        }
    }

    private void auditStrict(
        String tenantId,
        String actorId,
        UUID operationId,
        View definition,
        ModelSpecView modelSpec
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operationId", operationId);
        payload.put("tenantId", tenantId);
        payload.put("actor", actorId);
        payload.put("planId", modelSpec.planId());
        payload.put("domainId", modelSpec.domainId());
        payload.put("dimensionDefinitionId", definition.id());
        payload.put("dimensionDefinitionRevision", definition.revision());
        payload.put("modelSpecId", modelSpec.id());
        payload.put("modelSpecRevision", modelSpec.revision());
        auditService.auditActionStrict(
            "MODELING_DIMENSION_MODEL_CREATE",
            AuditStage.SUCCESS,
            modelSpec.id().toString(),
            payload
        );
    }

    private static ModelSpecException operationNotFound(UUID operationId) {
        return new ModelSpecException(
            "DIMENSION_MODEL_OPERATION_NOT_FOUND",
            "The dimension model operation does not exist",
            ModelSpecException.Kind.NOT_FOUND,
            Map.of("operationId", operationId)
        );
    }

    private static ModelSpecException inconsistent(UUID operationId, String reason) {
        return new ModelSpecException(
            "DIMENSION_MODEL_OPERATION_INCONSISTENT",
            "The dimension model operation is incomplete or inconsistent",
            ModelSpecException.Kind.CONFLICT,
            Map.of("operationId", operationId, "reason", reason)
        );
    }

    private static ModelSpecException idempotencyConflict(UUID operationId, String reason) {
        return new ModelSpecException(
            "DIMENSION_MODEL_IDEMPOTENCY_CONFLICT",
            "The dimension model operation conflicts with a prior request",
            ModelSpecException.Kind.CONFLICT,
            Map.of("operationId", operationId, "reason", reason)
        );
    }

    public record OperationResult(
        UUID operationId,
        BindingMode bindingMode,
        View dimensionDefinitionRevision,
        ModelSpecView modelSpecRevision,
        ModelSpecView currentModelSpec,
        boolean replayed
    ) {}

    private record BoundDefinition(
        View definition,
        DimensionDefinitionRef reference,
        boolean replayed
    ) {}
}
