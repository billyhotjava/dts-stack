package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.repository.modeling.ModelReleaseCandidateRepository;
import com.yuzhi.dts.platform.service.modeling.ModelReleaseCandidateException.Kind;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit data-module registration through the existing idempotent Catalog identity owner. */
@Service
public class ModelDataRegistrationService {
    private final ModelSpecApplicationService models;
    private final ModelReleaseCandidateRepository candidates;
    private final ModelSpecPlanWriteAccessPort access;
    private final CandidateQualityAssetRegistrationService registration;
    public ModelDataRegistrationService(ModelSpecApplicationService models, ModelReleaseCandidateRepository candidates,
        ModelSpecPlanWriteAccessPort access, CandidateQualityAssetRegistrationService registration) {
        this.models=models; this.candidates=candidates; this.access=access; this.registration=registration;
    }
    @Transactional
    public List<UUID> register(String tenant, String actor, UUID modelId, Command command) {
        var model = models.get(tenant, modelId);
        access.requireEdit(tenant, modelId, actor);
        if (!access.canMaintain(tenant, model.planId(), actor)) throw failure("MODEL_DATA_REGISTRATION_FORBIDDEN", Kind.FORBIDDEN);
        if (command == null || command.candidateId() == null || command.modelRevision() != model.revision() || !Objects.equals(command.modelChecksum(), model.checksum()))
            throw failure("MODEL_DATA_REGISTRATION_STALE", Kind.CONFLICT);
        candidates.lockPlanForCandidate(tenant, model.planId());
        var candidate = candidates.find(tenant, command.candidateId()).orElseThrow(() -> failure("MODEL_DATA_REGISTRATION_NOT_FOUND", Kind.NOT_FOUND));
        if (!candidate.planId().equals(model.planId()) || candidate.version() != command.candidateVersion() ||
            candidate.entries().stream().noneMatch(entry -> entry.modelSpecId().equals(modelId) && entry.revision() == model.revision() && entry.checksum().equals(model.checksum())))
            throw failure("MODEL_DATA_REGISTRATION_STALE", Kind.CONFLICT);
        access.requireOperation(tenant, candidate.entries().stream().map(entry -> entry.modelSpecId()).toList(), actor);
        List<UUID> registered = registration.ensureRegistered(candidate);
        // F15 K3: a manual retry from the data asset catalog completes the queued registration task as well.
        registration.recordRegistered(candidate);
        return registered;
    }
    /** F15 K3 read: the registration task of a model's build, so the catalog can show failures without a publication. */
    @Transactional(readOnly = true)
    public CandidateQualityAssetRegistrationService.RegistrationStatus status(String tenant, UUID modelId, UUID candidateId) {
        var model = models.get(tenant, modelId);
        if (candidateId == null) throw failure("MODEL_DATA_REGISTRATION_NOT_FOUND", Kind.NOT_FOUND);
        var candidate = candidates.find(tenant, candidateId).orElseThrow(() -> failure("MODEL_DATA_REGISTRATION_NOT_FOUND", Kind.NOT_FOUND));
        if (!candidate.planId().equals(model.planId()) || candidate.entries().stream().noneMatch(entry -> entry.modelSpecId().equals(modelId)))
            throw failure("MODEL_DATA_REGISTRATION_NOT_FOUND", Kind.NOT_FOUND);
        return registration.registrationStatus(candidate);
    }
    private static ModelReleaseCandidateException failure(String code, Kind kind) {
        return new ModelReleaseCandidateException(code, "数据登记条件或版本已变化，请刷新当前模型产出后重试", kind);
    }
    public record Command(UUID candidateId, int candidateVersion, int modelRevision, String modelChecksum) {}
}
