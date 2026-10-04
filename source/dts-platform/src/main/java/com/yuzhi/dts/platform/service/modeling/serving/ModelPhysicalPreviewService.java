package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetKey;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ModelServingProjection;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.AccessContext;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.ColumnPolicy;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.MaskingSummary;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewRequest;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PhysicalPreviewView;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PolicyDecision;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewMode;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewColumn;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.PreviewScope;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.QueryResult;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import com.yuzhi.dts.platform.service.security.MaskingFunctions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fail-closed orchestration for evidence-bound, masked physical sample rows. */
@Service
public class ModelPhysicalPreviewService {

    private static final Set<String> SUCCEEDED_CANDIDATE_STATES = Set.of(
        "BUILT", "QUALITY_RUNNING", "QUALITY_PASSED", "REVIEW_PENDING",
        "APPROVED", "PUBLISHING", "PARTIAL", "PUBLISHED"
    );
    private static final Set<Integer> SAMPLE_LIMITS = Set.of(20, 50, 100, 500);

    private final ModelSpecApplicationService modelSpecs;
    private final CatalogModelServingProjectionRepository repository;
    private final PhysicalPreviewAccessPort access;
    private final PhysicalPreviewPolicyPort policies;
    private final PhysicalPreviewQueryPort queries;
    private final AuditService audit;
    private final Clock clock;
    private final Supplier<String> correlations;

    @Autowired
    public ModelPhysicalPreviewService(
        ModelSpecApplicationService modelSpecs,
        CatalogModelServingProjectionRepository repository,
        PhysicalPreviewAccessPort access,
        PhysicalPreviewPolicyPort policies,
        PhysicalPreviewQueryPort queries,
        AuditService audit
    ) {
        this(modelSpecs, repository, access, policies, queries, audit, Clock.systemUTC(), () -> UUID.randomUUID().toString());
    }

    public ModelPhysicalPreviewService(
        ModelSpecApplicationService modelSpecs,
        CatalogModelServingProjectionRepository repository,
        PhysicalPreviewAccessPort access,
        PhysicalPreviewPolicyPort policies,
        PhysicalPreviewQueryPort queries,
        AuditService audit,
        Clock clock,
        Supplier<String> correlations
    ) {
        this.modelSpecs = modelSpecs;
        this.repository = repository;
        this.access = access;
        this.policies = policies;
        this.queries = queries;
        this.audit = audit;
        this.clock = clock;
        this.correlations = correlations;
    }

    @Transactional
    public PhysicalPreviewView preview(String tenantId, String actorId, PhysicalPreviewRequest request) {
        String correlationId = correlations.get();
        int returned = 0;
        try {
            requireRequest(request, correlationId);
            ModelSpecView model;
            try {
                model = modelSpecs.get(tenantId, request.modelSpecId());
            } catch (RuntimeException inaccessible) {
                throw error("PHYSICAL_PREVIEW_ACCESS_DENIED", HttpStatus.FORBIDDEN, correlationId);
            }
            ModelServingProjection projection = repository
                .findProjectionForPreview(tenantId, model.id())
                .orElse(null);
            if (
                model.revision() != request.modelRevision() &&
                request.mode() == PreviewMode.SAMPLE &&
                !currentServingRevision(projection, request)
            ) {
                throw error("PHYSICAL_PREVIEW_HISTORICAL_ROWS_NOT_REPRODUCIBLE", HttpStatus.CONFLICT, correlationId);
            }
            RelationEvidence evidence = repository
                .findRelationEvidenceForPreview(tenantId, request.relationEvidenceId())
                .orElseThrow(() -> error("PHYSICAL_PREVIEW_NOT_MATERIALIZED", HttpStatus.CONFLICT, correlationId));
            requirePins(tenantId, request, evidence, correlationId);
            if (request.scope() == PreviewScope.SERVING) {
                requireServing(projection, request, evidence, correlationId);
            } else {
                requireCurrentCandidateEvidence(tenantId, request, correlationId);
                if (!SUCCEEDED_CANDIDATE_STATES.contains(evidence.candidateStatus())) {
                    throw error("PHYSICAL_PREVIEW_CANDIDATE_NOT_SUCCEEDED", HttpStatus.CONFLICT, correlationId);
                }
            }
            if (
                !evidence.verified() ||
                !evidence.relationExists() ||
                !"COMPLETED".equals(evidence.dispatchStatus()) ||
                !"BUILT".equals(evidence.pipelineStatus())
            ) {
                throw error("PHYSICAL_PREVIEW_NOT_MATERIALIZED", HttpStatus.CONFLICT, correlationId);
            }
            requireIdentifiers(evidence, correlationId);
            AccessContext accessContext = access.authorize(tenantId, actorId, model, request);
            PolicyDecision policy = policies.resolve(model, evidence, accessContext, actorId);
            requirePolicy(policy, evidence, correlationId);
            List<String> selectedColumns = evidence
                .columns()
                .stream()
                .map(column -> column.name())
                .filter(column -> policy.columns().get(column).policy() != ColumnPolicy.DENY)
                .toList();
            QueryResult rows;
            if (request.mode() == PreviewMode.STRUCTURE || selectedColumns.isEmpty()) {
                rows = new QueryResult(List.of(), queries.verifyStructure(evidence), false);
            } else {
                rows = queries.query(evidence, selectedColumns, request.limit());
            }
            PhysicalPreviewView view = applyPolicy(request, evidence, policy, rows, correlationId);
            returned = view.returnedRows();
            auditStrict(actorId, tenantId, request, view.maskingSummary(), returned, "SUCCESS", null, correlationId);
            return view;
        } catch (PhysicalPreviewException failure) {
            PhysicalPreviewException safe = failure.correlationId() == null
                ? error(failure.errorCode(), failure.status(), correlationId)
                : failure;
            auditFailureStrict(actorId, tenantId, request, returned, safe.errorCode(), correlationId);
            throw safe;
        } catch (RuntimeException failure) {
            PhysicalPreviewException safe = error("PHYSICAL_PREVIEW_MASKING_UNAVAILABLE", HttpStatus.LOCKED, correlationId);
            auditFailureStrict(actorId, tenantId, request, 0, safe.errorCode(), correlationId);
            throw safe;
        }
    }

    private static void requireRequest(PhysicalPreviewRequest request, String correlationId) {
        if (request == null) {
            throw error("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH", HttpStatus.CONFLICT, correlationId);
        }
        if (request.mode() == PreviewMode.SAMPLE && !SAMPLE_LIMITS.contains(request.limit())) {
            throw error("PHYSICAL_PREVIEW_LIMIT_EXCEEDED", HttpStatus.BAD_REQUEST, correlationId);
        }
        if (
            request.modelSpecId() == null || request.modelRevision() < 1 ||
            request.modelChecksum() == null || !request.modelChecksum().matches("^[0-9a-f]{64}$") ||
            request.implementationRevision() < 1 || request.implementationChecksum() == null ||
            !request.implementationChecksum().matches("^[0-9a-f]{64}$") ||
            request.scope() == null || request.mode() == null ||
            request.candidateId() == null || request.candidateVersion() < 1 ||
            request.attempt() < 1 || request.pipelineRunId() == null ||
            request.observationAttempt() < 1 || request.relationEvidenceId() == null ||
            request.evidenceChecksum() == null || !request.evidenceChecksum().matches("^[0-9a-f]{64}$")
        ) {
            throw error("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH", HttpStatus.CONFLICT, correlationId);
        }
    }

    private static void requirePins(
        String tenantId,
        PhysicalPreviewRequest request,
        RelationEvidence evidence,
        String correlationId
    ) {
        if (
            !Objects.equals(tenantId, evidence.tenantId()) ||
            !Objects.equals(request.relationEvidenceId(), evidence.relationEvidenceId()) ||
            !Objects.equals(request.modelSpecId(), evidence.modelSpecId()) ||
            request.modelRevision() != evidence.modelRevision() ||
            !Objects.equals(request.modelChecksum(), evidence.modelChecksum()) ||
            request.implementationRevision() != evidence.implementationRevision() ||
            !Objects.equals(request.implementationChecksum(), evidence.implementationChecksum()) ||
            !Objects.equals(request.candidateId(), evidence.candidateId()) ||
            request.candidateVersion() != evidence.candidateVersion() ||
            request.attempt() != evidence.attempt() ||
            !Objects.equals(request.pipelineRunId(), evidence.pipelineRunId()) ||
            request.observationAttempt() != evidence.observationAttempt() ||
            !Objects.equals(request.evidenceChecksum(), evidence.evidenceChecksum())
        ) {
            throw error("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH", HttpStatus.CONFLICT, correlationId);
        }
    }

    private static void requireServing(
        ModelServingProjection projection,
        PhysicalPreviewRequest request,
        RelationEvidence evidence,
        String correlationId
    ) {
        ServingRef serving = projection == null ? null : projection.servingRef();
        if (
            serving == null ||
            !Objects.equals(serving.modelSpecId(), request.modelSpecId()) ||
            serving.modelRevision() != request.modelRevision() ||
            !Objects.equals(serving.modelChecksum(), request.modelChecksum()) ||
            serving.implementationRevision() != request.implementationRevision() ||
            !Objects.equals(serving.implementationChecksum(), request.implementationChecksum()) ||
            !Objects.equals(serving.candidateId(), request.candidateId()) ||
            serving.candidateVersion() != request.candidateVersion() ||
            serving.attempt() != request.attempt() ||
            !Objects.equals(serving.pipelineRunId(), request.pipelineRunId()) ||
            serving.observationAttempt() != request.observationAttempt() ||
            !Objects.equals(serving.relationEvidenceId(), request.relationEvidenceId()) ||
            !Objects.equals(serving.evidenceChecksum(), request.evidenceChecksum()) ||
            !Objects.equals(serving.schemaName(), evidence.schemaName()) ||
            !Objects.equals(serving.identifier(), evidence.identifier())
        ) {
            throw error("PHYSICAL_PREVIEW_EVIDENCE_MISMATCH", HttpStatus.CONFLICT, correlationId);
        }
    }

    private void requireCurrentCandidateEvidence(
        String tenantId,
        PhysicalPreviewRequest request,
        String correlationId
    ) {
        RelationEvidence current = repository
            .findLatestSuccessfulCandidateEvidence(
                tenantId,
                request.modelSpecId(),
                request.modelRevision(),
                request.modelChecksum(),
                request.implementationRevision(),
                request.implementationChecksum()
            )
            .orElseThrow(() -> error("PHYSICAL_PREVIEW_NOT_MATERIALIZED", HttpStatus.CONFLICT, correlationId));
        requirePins(tenantId, request, current, correlationId);
    }

    private static void requireIdentifiers(RelationEvidence evidence, String correlationId) {
        boolean invalid = !identifier(evidence.schemaName()) || !identifier(evidence.identifier()) ||
            evidence.columns().stream().anyMatch(column -> !identifier(column.name()));
        if (invalid) {
            throw error("PHYSICAL_PREVIEW_IDENTIFIER_INVALID", HttpStatus.CONFLICT, correlationId);
        }
    }

    private static boolean identifier(String value) {
        return value != null && PhysicalRelationInspector.IDENTIFIER.matcher(value).matches();
    }

    private static void requirePolicy(PolicyDecision policy, RelationEvidence evidence, String correlationId) {
        if (policy == null || policy.classification() == null || policy.classification().isBlank()) {
            throw error("PHYSICAL_PREVIEW_CLASSIFICATION_UNRESOLVED", HttpStatus.LOCKED, correlationId);
        }
        for (var column : evidence.columns()) {
            ColumnDecision decision = policy.columns().get(column.name());
            if (decision == null || decision.policy() == null || decision.policy() == ColumnPolicy.UNKNOWN) {
                throw error("PHYSICAL_PREVIEW_MASKING_UNAVAILABLE", HttpStatus.LOCKED, correlationId);
            }
            if (decision.policy() == ColumnPolicy.MASK && (decision.maskingStrategy() == null || decision.maskingStrategy().isBlank())) {
                throw error("PHYSICAL_PREVIEW_MASKING_UNAVAILABLE", HttpStatus.LOCKED, correlationId);
            }
        }
    }

    private static PhysicalPreviewView applyPolicy(
        PhysicalPreviewRequest request,
        RelationEvidence evidence,
        PolicyDecision policy,
        QueryResult result,
        String correlationId
    ) {
        List<PreviewColumn> columns = new ArrayList<>();
        int allowed = 0;
        int masked = 0;
        int denied = 0;
        for (var column : evidence.columns()) {
            ColumnDecision decision = policy.columns().get(column.name());
            if (decision.policy() == ColumnPolicy.DENY) {
                denied++;
                continue;
            }
            if (decision.policy() == ColumnPolicy.MASK) masked++; else allowed++;
            columns.add(
                new PreviewColumn(
                    column.ordinalPosition(),
                    column.name(),
                    column.dataType(),
                    column.nullable(),
                    decision.policy()
                )
            );
        }
        List<Map<String, Object>> maskedRows = new ArrayList<>();
        for (Map<String, Object> source : result.rows()) {
            Map<String, Object> target = new LinkedHashMap<>();
            for (PreviewColumn column : columns) {
                ColumnDecision decision = policy.columns().get(column.name());
                Object value = source.get(column.name());
                target.put(
                    column.name(),
                    decision.policy() == ColumnPolicy.MASK
                        ? MaskingFunctions.apply(value, decision.maskingStrategy())
                        : value
                );
            }
            maskedRows.add(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(target)));
        }
        MaskingSummary summary = new MaskingSummary(allowed, masked, denied);
        return new PhysicalPreviewView(
            request.modelSpecId(), request.modelRevision(), request.implementationRevision(), request.mode(), request.scope(),
            request.candidateId(), request.candidateVersion(), request.attempt(), request.pipelineRunId(),
            request.observationAttempt(), request.relationEvidenceId(), request.evidenceChecksum(),
            CatalogAssetType.SEMANTIC_MODEL, CatalogAssetKey.semanticModel(request.modelSpecId().toString()),
            evidence.schemaName() + "." + evidence.identifier(), evidence.observedAt(), result.queriedAt(),
            List.copyOf(columns), List.copyOf(maskedRows), summary, maskedRows.size(), result.truncated(),
            "CURRENT", correlationId,
            request.scope() == PreviewScope.CANDIDATE ? "候选结果，非正式资产" : null
        );
    }

    private void auditStrict(
        String actorId,
        String tenantId,
        PhysicalPreviewRequest request,
        MaskingSummary summary,
        int returnedRows,
        String result,
        String errorCode,
        String correlationId
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("actor", actorId);
        payload.put("tenantId", tenantId);
        UUID modelSpecId = request == null ? null : request.modelSpecId();
        payload.put("catalogAssetKey", modelSpecId == null ? null : CatalogAssetKey.semanticModel(modelSpecId.toString()));
        if (request != null) {
            payload.put("modelRevision", request.modelRevision());
            payload.put("modelChecksum", request.modelChecksum());
            payload.put("implementationRevision", request.implementationRevision());
            payload.put("implementationChecksum", request.implementationChecksum());
            payload.put("candidateId", request.candidateId());
            payload.put("candidateVersion", request.candidateVersion());
            payload.put("attempt", request.attempt());
            payload.put("pipelineRunId", request.pipelineRunId());
            payload.put("observationAttempt", request.observationAttempt());
            payload.put("relationEvidenceId", request.relationEvidenceId());
            payload.put("evidenceChecksum", request.evidenceChecksum());
            payload.put("previewMode", request.mode() == null ? null : request.mode().name());
            payload.put("requestedRows", request.mode() == PreviewMode.SAMPLE ? request.limit() : 0);
        } else {
            payload.put("previewMode", null);
            payload.put("requestedRows", 0);
        }
        payload.put("returnedRows", returnedRows);
        payload.put("maskedColumnCount", summary == null ? 0 : summary.maskedColumnCount());
        payload.put("deniedColumnCount", summary == null ? 0 : summary.deniedColumnCount());
        payload.put("sampleValueCount", 0);
        payload.put("result", result);
        payload.put("errorCode", errorCode);
        payload.put("correlationId", correlationId);
        payload.put("occurredAt", clock.instant());
        audit.auditActionStrict(
            "MODELING_PHYSICAL_PREVIEW",
            "SUCCESS".equals(result) ? AuditStage.SUCCESS : AuditStage.FAIL,
            modelSpecId == null ? "UNKNOWN" : modelSpecId.toString(),
            payload
        );
    }

    private void auditFailureStrict(
        String actorId,
        String tenantId,
        PhysicalPreviewRequest request,
        int returnedRows,
        String errorCode,
        String correlationId
    ) {
        auditStrict(actorId, tenantId, request, null, returnedRows, "FAILED", errorCode, correlationId);
    }

    private static PhysicalPreviewException error(String code, HttpStatus status, String correlationId) {
        return new PhysicalPreviewException(code, status, correlationId);
    }

    private static boolean currentServingRevision(
        ModelServingProjection projection,
        PhysicalPreviewRequest request
    ) {
        ServingRef serving = projection == null ? null : projection.servingRef();
        return request.scope() == PreviewScope.SERVING &&
            serving != null &&
            serving.modelRevision() == request.modelRevision() &&
            Objects.equals(serving.modelChecksum(), request.modelChecksum()) &&
            serving.implementationRevision() == request.implementationRevision() &&
            Objects.equals(serving.implementationChecksum(), request.implementationChecksum());
    }
}
