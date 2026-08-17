package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
import com.yuzhi.dts.platform.service.governance.request.QualityRunTriggerRequest;
import com.yuzhi.dts.platform.service.modeling.GovernanceQualityRerunPort;
import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.stereotype.Component;

/** Governance-side adapter that creates new runs for the exact rule bindings pinned by modeling evidence. */
@Component
public class GovernanceQualityRerunAdapter implements GovernanceQualityRerunPort {

    private static final int MAX_RUNS = 100;

    private final QualityRunService qualityRuns;
    private final GovQualityRunRepository runRepository;

    public GovernanceQualityRerunAdapter(QualityRunService qualityRuns, GovQualityRunRepository runRepository) {
        this.qualityRuns = qualityRuns;
        this.runRepository = runRepository;
    }

    @Override
    public Optional<RerunReceipt> findReplay(UUID candidateId, String idempotencyKey) {
        String prefix = triggerPrefix(candidateId, idempotencyKey);
        List<QualityRunRef> runs = runRepository
            .findByTriggerRefStartingWithOrderByCreatedDateAsc(prefix)
            .stream()
            .map(GovernanceQualityRerunAdapter::toRef)
            .toList();
        return runs.isEmpty() ? Optional.empty() : Optional.of(new RerunReceipt(true, runs));
    }

    @Override
    public RerunReceipt rerun(
        UUID candidateId,
        String idempotencyKey,
        String actorId,
        String activeDepartmentId,
        List<QualityEvidence> evidence
    ) {
        List<QualityEvidence> scope = evidence == null
            ? List.of()
            : evidence
                .stream()
                .sorted(
                    Comparator.comparing(QualityEvidence::assetKey)
                        .thenComparing(item -> item.ruleVersionId().toString())
                        .thenComparing(item -> item.bindingId().toString())
                )
                .toList();
        if (scope.isEmpty() || scope.size() > MAX_RUNS) {
            throw new IllegalArgumentException("governance quality rerun must contain between 1 and 100 bindings");
        }
        String prefix = triggerPrefix(candidateId, idempotencyKey);
        java.util.ArrayList<QualityRunRef> created = new java.util.ArrayList<>(scope.size());
        for (int index = 0; index < scope.size(); index++) {
            QualityEvidence item = scope.get(index);
            QualityRunTriggerRequest request = new QualityRunTriggerRequest();
            request.setRuleId(item.ruleId());
            request.setBindingId(item.bindingId());
            request.setTriggerType("MODEL_RELEASE");
            List<QualityRunDto> runs = qualityRuns.triggerPinnedAuthorized(
                request,
                item.ruleVersionId(),
                actorId,
                activeDepartmentId,
                prefix + index
            );
            if (runs.size() != 1) {
                throw new IllegalStateException("a pinned quality binding must create exactly one run");
            }
            created.add(toRef(runs.getFirst()));
        }
        return new RerunReceipt(false, created);
    }

    private static String triggerPrefix(UUID candidateId, String idempotencyKey) {
        if (candidateId == null) throw new IllegalArgumentException("candidateId is required");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey is required");
        }
        String digest = DigestUtils.sha256Hex(idempotencyKey.trim().getBytes(StandardCharsets.UTF_8));
        return "mcq:" + candidateId + ":" + digest + ":";
    }

    private static QualityRunRef toRef(GovQualityRun run) {
        return new QualityRunRef(
            run.getRule().getId(),
            run.getRuleVersion().getId(),
            run.getBinding().getId(),
            run.getId(),
            run.getStatus()
        );
    }

    private static QualityRunRef toRef(QualityRunDto run) {
        return new QualityRunRef(
            run.getRuleId(),
            run.getRuleVersionId(),
            run.getBindingId(),
            run.getId(),
            run.getStatus()
        );
    }
}
