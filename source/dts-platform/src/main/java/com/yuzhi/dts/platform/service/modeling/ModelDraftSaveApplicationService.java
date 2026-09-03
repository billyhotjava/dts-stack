package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.ImplementationView;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleContract.SaveImplementationCommand;
import com.yuzhi.dts.platform.service.modeling.ModelLifecycleService.ExpectedImplementationVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.CreateResult;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService.ExpectedVersion;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.CreateModelSpecCommand;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ImplementationMode;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelRevisionRef;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.UpdateModelSpecCommand;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits a new manual-model draft and its first implementation as one database transaction. */
@Service
public class ModelDraftSaveApplicationService {

    private final ModelSpecApplicationService modelSpecs;
    private final ModelLifecycleService lifecycle;
    private final ModelSpecSnapshotCodec modelSpecCodec;

    public ModelDraftSaveApplicationService(
        ModelSpecApplicationService modelSpecs,
        ModelLifecycleService lifecycle,
        ModelSpecSnapshotCodec modelSpecCodec
    ) {
        this.modelSpecs = modelSpecs;
        this.lifecycle = lifecycle;
        this.modelSpecCodec = modelSpecCodec;
    }

    @Transactional
    public SaveResult save(
        String tenantId,
        String actorId,
        CreateModelSpecCommand create,
        UpdateModelSpecCommand modelSpec,
        SaveImplementationCommand implementation
    ) {
        requireConsistentContext(create, modelSpec);
        if (implementation == null && modelSpec.implementationMode() != ImplementationMode.DBT_MANAGED) {
            throw new ModelSpecException(
                "MODEL_IMPLEMENTATION_INPUT_REQUIRED",
                "A complete implementation is required when a manual model is first saved",
                ModelSpecException.Kind.UNPROCESSABLE
            );
        }
        if (implementation != null && modelSpec.implementationMode() == ImplementationMode.DBT_MANAGED) {
            throw inconsistent("DBT-managed models cannot be initialized with a visual implementation");
        }

        CreateResult created = modelSpecs.create(tenantId, actorId, create);
        ModelSpecView savedModel = created.replayed()
            ? replayedModel(tenantId, actorId, created.modelSpec(), modelSpec)
            : modelSpecs.update(
                tenantId,
                actorId,
                created.modelSpec().id(),
                expected(created.modelSpec()),
                modelSpec
            );
        ImplementationView savedImplementation = implementation == null
            ? null
            : lifecycle.saveImplementation(
                tenantId,
                actorId,
                savedModel.id(),
                expected(savedModel),
                new ExpectedImplementationVersion(savedModel.id(), 0, null),
                null,
                null,
                implementation
            );
        return new SaveResult(savedModel, savedImplementation, created.replayed());
    }

    private ModelSpecView replayedModel(
        String tenantId,
        String actorId,
        ModelSpecView originalSeed,
        UpdateModelSpecCommand requestedDefinition
    ) {
        ModelSpecView current = modelSpecs.get(tenantId, originalSeed.id());
        if (current.revision() == 1) {
            return modelSpecs.update(
                tenantId,
                actorId,
                current.id(),
                expected(current),
                requestedDefinition
            );
        }
        ModelSpecView completed = modelSpecs.revision(
            tenantId,
            new ModelRevisionRef(originalSeed.id(), 2)
        );
        ModelSpecView expected = modelSpecCodec.toUpdatedView(
            originalSeed,
            requestedDefinition,
            2,
            completed.updatedAt()
        );
        if (!Objects.equals(completed.checksum(), expected.checksum())) {
            throw new ModelSpecException(
                "MODEL_DRAFT_OPERATION_IDEMPOTENCY_CONFLICT",
                "The operation id is already bound to another complete model definition",
                ModelSpecException.Kind.CONFLICT
            );
        }
        return completed;
    }

    private static ExpectedVersion expected(ModelSpecView model) {
        return new ExpectedVersion(model.id(), model.revision(), model.checksum());
    }

    private static void requireConsistentContext(
        CreateModelSpecCommand create,
        UpdateModelSpecCommand modelSpec
    ) {
        if (create == null || modelSpec == null) {
            throw inconsistent("Create and full model definitions are both required");
        }
        if (
            !Objects.equals(create.planId(), modelSpec.planId()) ||
            !Objects.equals(create.domainId(), modelSpec.domainId()) ||
            create.modelType() != modelSpec.modelType() ||
            create.layer() != modelSpec.layer() ||
            !Objects.equals(create.name(), modelSpec.name()) ||
            !Objects.equals(create.warehouseLayerCode(), modelSpec.warehouseLayerCode()) ||
            !Objects.equals(create.businessProcessId(), modelSpec.businessProcessId()) ||
            !Objects.equals(create.dataMartId(), modelSpec.dataMartId()) ||
            !Objects.equals(create.subjectDomainId(), modelSpec.subjectDomainId())
        ) {
            throw inconsistent("Create and full model definitions identify different model contexts");
        }
    }

    private static ModelSpecException inconsistent(String message) {
        return new ModelSpecException(
            "MODEL_DRAFT_OPERATION_INCONSISTENT",
            message,
            ModelSpecException.Kind.UNPROCESSABLE,
            Map.of("repairAction", "RELOAD_AND_SAVE_MODEL_DRAFT")
        );
    }

    public record SaveResult(
        ModelSpecView model,
        ImplementationView implementation,
        boolean replayed
    ) {}
}
