package com.yuzhi.dts.platform.service.modeling.imports.preview;

import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import java.util.List;
import java.util.UUID;

/** Atomic persistence and strict-audit boundary for one recoverable preview. */
public interface ModelSpecImportPreviewCommitPort {
    void commit(PersistedRun run, List<PersistedItem> items, PreviewAuditFacts auditFacts);

    enum PreviewAuditOutcome {
        SUCCESS,
        PARTIAL,
        BLOCKED,
    }

    record PreviewFailureFact(
        String itemIdentity,
        String code,
        String stage,
        String category,
        boolean retryable,
        String recoveryAction,
        String correlationId
    ) {}

    record PreviewAuditFacts(
        UUID runId,
        String tenantId,
        UUID planId,
        String actorId,
        String previewHash,
        String packageChecksum,
        PreviewSummary summary,
        PreviewAuditOutcome outcome,
        List<PreviewFailureFact> failures,
        String correlationId
    ) {
        public PreviewAuditFacts {
            failures = failures == null ? List.of() : List.copyOf(failures);
        }
    }
}
