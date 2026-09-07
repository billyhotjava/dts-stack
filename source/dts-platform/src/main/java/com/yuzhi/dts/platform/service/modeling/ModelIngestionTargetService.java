package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.CandidatePublicationEvidenceRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelLifecycleRepository;
import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves credential-free, immutable model targets; never creates or changes a physical table. */
@Service
public class ModelIngestionTargetService {
    private final ModelSpecApplicationService models;
    private final ModelLifecycleRepository implementations;
    private final ModelReleaseCandidateRepository candidates;
    private final CandidatePublicationEvidenceRepository evidence;
    private final ModelExecutionTargetCatalogResolver targets;
    private final ModelSpecPlanWriteAccessPort access;

    public ModelIngestionTargetService(ModelSpecApplicationService models, ModelLifecycleRepository implementations,
        ModelReleaseCandidateRepository candidates, CandidatePublicationEvidenceRepository evidence,
        ModelExecutionTargetCatalogResolver targets, ModelSpecPlanWriteAccessPort access) {
        this.models = models; this.implementations = implementations; this.candidates = candidates;
        this.evidence = evidence; this.targets = targets; this.access = access;
    }

    @Transactional(readOnly = true)
    public Target resolveForUser(String tenant, String actor, UUID id, String environment) {
        var model = models.get(tenant, id);
        if (!access.canMaintain(tenant, model.planId(), actor)) throw failure("MODEL_INGESTION_TARGET_FORBIDDEN", Kind.FORBIDDEN);
        return current(tenant, id, environment);
    }

    @Transactional(readOnly = true)
    public Target validateForExecution(String tenant, Target expected) {
        if (expected == null || expected.schemaVersion() != 1 || expected.modelSpecId() == null) {
            throw failure("MODEL_INGESTION_TARGET_INVALID", Kind.BAD_REQUEST);
        }
        Target actual = current(tenant, expected.modelSpecId(), expected.environment());
        if (!actual.equals(expected)) throw failure("MODEL_INGESTION_TARGET_STALE", Kind.CONFLICT);
        return actual;
    }

    private Target current(String tenant, UUID id, String environment) {
        var model = models.get(tenant, id);
        if (model.modelType() != ModelSpecContract.ModelType.SOURCE || model.layer() != ModelSpecContract.Layer.ODS) {
            throw failure("MODEL_INGESTION_TARGET_REQUIRES_ODS", Kind.UNPROCESSABLE);
        }
        var implementation = implementations.findImplementation(tenant, id)
            .orElseThrow(() -> failure("MODEL_INGESTION_TARGET_NOT_MATERIALIZED", Kind.CONFLICT));
        if (implementation.revision() != model.revision() || !Objects.equals(implementation.modelChecksum(), model.checksum())) {
            throw failure("MODEL_INGESTION_TARGET_STALE", Kind.CONFLICT);
        }
        var candidate = candidates.findLatestForModelCurrentRevision(tenant, model.planId(), id,
                model.revision(), model.checksum(), environment)
            .filter(item -> item.entries().stream().anyMatch(entry -> id.equals(entry.modelSpecId()) && entry.revision() == model.revision() &&
                Objects.equals(entry.checksum(), model.checksum()) && Objects.equals(entry.implementationId(), implementation.id())))
            .orElseThrow(() -> failure("MODEL_INGESTION_TARGET_NOT_MATERIALIZED", Kind.CONFLICT));
        var physical = evidence.requireCurrent(candidate, false).stream().filter(item -> id.equals(item.modelSpecId())).findFirst()
            .orElseThrow(() -> failure("MODEL_INGESTION_TARGET_NOT_MATERIALIZED", Kind.CONFLICT));
        if (physical.implementationRevision() != implementation.implementationRevision() ||
            !Objects.equals(physical.implementationChecksum(), implementation.implementationChecksum()) ||
            physical.relationType() == null || !"TABLE".equals(physical.relationType().name())) {
            throw failure("MODEL_INGESTION_TARGET_STALE", Kind.CONFLICT);
        }
        var target = targets.resolve(candidate);
        return new Target(1, id, model.revision(), model.checksum(), implementation.implementationRevision(), implementation.implementationChecksum(),
            environment, candidate.id(), physical.pipelineRunGroupId(), target.sourceId(), physical.databaseName(), physical.schemaName(), physical.identifier(),
            model.fields().stream().map(field -> new Column(field.name(), ModelFieldPhysicalTypeContract.requireSupported(field.dataType()).postgresType(),
                field.nullable(), field.role() == ModelSpecContract.FieldRole.KEY)).toList());
    }

    private static ModelReleaseCandidateException failure(String code, Kind kind) {
        return new ModelReleaseCandidateException(code, "模型目标不存在、版本已变化或不满足接入条件，请重新选择已物化贴源表", kind);
    }

    public record Target(int schemaVersion, UUID modelSpecId, int modelRevision, String modelChecksum, int implementationRevision,
        String implementationChecksum, String environment, UUID candidateId, UUID runGroupId, UUID dataSourceId,
        String databaseName, String schemaName, String tableName, List<Column> columns) {
        public Target { columns = List.copyOf(columns == null ? List.of() : columns); }
    }
    public record Column(String name, String dataType, boolean nullable, boolean primaryKey) {}
}
