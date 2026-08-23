package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.repository.governance.GovQualityRunRepository;
import com.yuzhi.dts.platform.service.governance.dto.QualityRunDto;
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

    private final QualityWorkflowOrchestrator workflows;
    private final GovQualityRunRepository runRepository;

    public GovernanceQualityRerunAdapter(
        QualityWorkflowOrchestrator workflows,
        GovQualityRunRepository runRepository
    ) {
        this.workflows = workflows;
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
        var workflow = workflows.startPinnedModelQuality(
            scope
                .stream()
                .map(item -> new PinnedQualityBinding(item.ruleId(), item.ruleVersionId(), item.bindingId()))
                .toList(),
            actorId,
            activeDepartmentId,
            prefix,
            prefix
        );
        List<QualityRunDto> runs = workflow.ruleRuns();
        if (runs.size() != scope.size()) {
            throw new IllegalStateException("all pinned quality bindings must create one run in the same workflow");
        }
        return new RerunReceipt(false, runs.stream().map(GovernanceQualityRerunAdapter::toRef).toList());
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
