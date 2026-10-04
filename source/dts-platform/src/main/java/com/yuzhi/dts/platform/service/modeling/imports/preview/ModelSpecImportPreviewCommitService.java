package com.yuzhi.dts.platform.service.modeling.imports.preview;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewAuditOutcome;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewCommitPort.PreviewFailureFact;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewContract.PreviewSummary;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedItem;
import com.yuzhi.dts.platform.service.modeling.imports.preview.ModelSpecImportPreviewRepository.PersistedRun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits preview state and its compliance audit receipt in one database transaction. */
@Service
public class ModelSpecImportPreviewCommitService implements ModelSpecImportPreviewCommitPort {

    static final String PREVIEW_ACTION = "MODEL_SPEC_IMPORT_PREVIEW";

    private final ModelSpecImportPreviewRepository repository;
    private final AuditService audit;

    public ModelSpecImportPreviewCommitService(ModelSpecImportPreviewRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void commit(PersistedRun run, List<PersistedItem> items, PreviewAuditFacts facts) {
        repository.save(run, items);
        audit.auditActionStrict(
            PREVIEW_ACTION,
            auditStage(facts),
            facts == null || facts.runId() == null ? "preview" : facts.runId().toString(),
            safePayload(facts)
        );
    }

    private static AuditStage auditStage(PreviewAuditFacts facts) {
        return effectiveOutcome(facts) == PreviewAuditOutcome.SUCCESS ? AuditStage.SUCCESS : AuditStage.FAIL;
    }

    private static Map<String, Object> safePayload(PreviewAuditFacts facts) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (facts == null) return Map.of();
        put(payload, "runId", facts.runId());
        put(payload, "tenantId", facts.tenantId());
        put(payload, "planId", facts.planId());
        put(payload, "actorId", facts.actorId());
        put(payload, "correlationId", facts.correlationId());
        payload.put("outcome", effectiveOutcome(facts).name());
        put(payload, "previewHash", facts.previewHash());
        put(payload, "packageChecksum", facts.packageChecksum());
        PreviewSummary summary = facts.summary();
        if (summary != null) {
            payload.put("total", summary.total());
            payload.put("ready", summary.ready());
            payload.put("blocked", summary.blocked());
            payload.put("create", summary.create());
            payload.put("update", summary.update());
            payload.put("skip", summary.skip());
            payload.put("conflict", summary.conflict());
        }
        if (!facts.failures().isEmpty()) {
            payload.put("failures", facts.failures().stream().map(ModelSpecImportPreviewCommitService::safeFailure).toList());
        }
        return Map.copyOf(payload);
    }

    private static PreviewAuditOutcome effectiveOutcome(PreviewAuditFacts facts) {
        if (facts == null || facts.summary() == null) return PreviewAuditOutcome.BLOCKED;
        PreviewSummary summary = facts.summary();
        int unresolved = summary.blocked() + summary.conflict();
        if (unresolved == 0) return PreviewAuditOutcome.SUCCESS;
        return summary.ready() > 0 ? PreviewAuditOutcome.PARTIAL : PreviewAuditOutcome.BLOCKED;
    }

    private static Map<String, Object> safeFailure(PreviewFailureFact failure) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (failure == null) return Map.of();
        put(payload, "itemIdentity", failure.itemIdentity());
        put(payload, "code", failure.code());
        put(payload, "stage", failure.stage());
        put(payload, "category", failure.category());
        payload.put("retryable", failure.retryable());
        put(payload, "recoveryAction", failure.recoveryAction());
        put(payload, "correlationId", failure.correlationId());
        return Map.copyOf(payload);
    }

    private static void put(Map<String, Object> payload, String key, Object value) {
        if (value != null && !value.toString().isBlank()) payload.put(key, value);
    }
}
