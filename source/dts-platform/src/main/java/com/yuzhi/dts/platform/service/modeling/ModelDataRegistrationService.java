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
        if (!access.canMaintain(tenant, model.planId(), actor)) throw failure("MODEL_DATA_REGISTRATION_FORBIDDEN", Kind.FORBIDDEN);
        if (command == null || command.candidateId() == null || command.modelRevision() != model.revision() || !Objects.equals(command.modelChecksum(), model.checksum()))
            throw failure("MODEL_DATA_REGISTRATION_STALE", Kind.CONFLICT);
        candidates.lockPlanForCandidate(tenant, model.planId());
        var candidate = candidates.find(tenant, command.candidateId()).orElseThrow(() -> failure("MODEL_DATA_REGISTRATION_NOT_FOUND", Kind.NOT_FOUND));
        if (!candidate.planId().equals(model.planId()) || candidate.version() != command.candidateVersion() ||
            candidate.entries().stream().noneMatch(entry -> entry.modelSpecId().equals(modelId) && entry.revision() == model.revision() && entry.checksum().equals(model.checksum())))
            throw failure("MODEL_DATA_REGISTRATION_STALE", Kind.CONFLICT);
        return registration.ensureRegistered(candidate);
    }
    private static ModelReleaseCandidateException failure(String code, Kind kind) {
        return new ModelReleaseCandidateException(code, "数据登记条件或版本已变化，请刷新当前模型产出后重试", kind);
    }
    public record Command(UUID candidateId, int candidateVersion, int modelRevision, String modelChecksum) {}
}
