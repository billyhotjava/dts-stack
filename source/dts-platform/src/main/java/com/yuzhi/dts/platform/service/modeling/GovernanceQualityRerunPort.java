package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.QualityEvidencePort.QualityEvidence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Write boundary for creating new governance-quality runs without coupling modeling to governance entities. */
public interface GovernanceQualityRerunPort {

    Optional<RerunReceipt> findReplay(UUID candidateId, String idempotencyKey);

    RerunReceipt rerun(
        UUID candidateId,
        String idempotencyKey,
        String actorId,
        String activeDepartmentId,
        List<QualityEvidence> evidence
    );

    record QualityRunRef(
        UUID ruleId,
        UUID ruleVersionId,
        UUID bindingId,
        UUID runId,
        String status
    ) {
        public QualityRunRef {
            if (ruleId == null || ruleVersionId == null || bindingId == null || runId == null) {
                throw new IllegalArgumentException("quality run references are required");
            }
            if (status == null || status.isBlank()) throw new IllegalArgumentException("status is required");
            status = status.trim().toUpperCase(java.util.Locale.ROOT);
        }
    }

    record RerunReceipt(boolean replayed, List<QualityRunRef> runs) {
        public RerunReceipt {
            runs = List.copyOf(runs == null ? List.of() : runs);
            if (runs.isEmpty() || runs.stream().anyMatch(item -> item == null)) {
                throw new IllegalArgumentException("at least one quality run is required");
            }
        }
    }
}
