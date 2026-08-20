package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.common.audit.AuditStage;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository;
import com.yuzhi.dts.platform.repository.modeling.CatalogModelServingProjectionRepository.ServingSyncState;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecApplicationService;
import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.ModelSpecView;
import com.yuzhi.dts.platform.service.modeling.ModelSpecException;
import com.yuzhi.dts.platform.service.modeling.ModelSpecPlanWriteAccessPort;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.PublishedRef;
import com.yuzhi.dts.platform.service.modeling.serving.CatalogModelServingContract.ServingRef;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and controlled-retry boundary for the durable Analytics semantic delivery state. */
@Service
public class CatalogModelSemanticSyncCommandService {

    private static final int MAX_BATCH_SIZE = 64;

    private final CatalogModelServingProjectionRepository repository;
    private final ModelSpecApplicationService modelSpecs;
    private final ModelSpecPlanWriteAccessPort writeAccess;
    private final AuditService auditService;
    private final Clock clock;
    private final Supplier<UUID> correlationIds;

    @Autowired
    public CatalogModelSemanticSyncCommandService(
        CatalogModelServingProjectionRepository repository,
        ModelSpecApplicationService modelSpecs,
        ModelSpecPlanWriteAccessPort writeAccess,
        AuditService auditService
    ) {
        this(repository, modelSpecs, writeAccess, auditService, Clock.systemUTC(), UUID::randomUUID);
    }

    CatalogModelSemanticSyncCommandService(
        CatalogModelServingProjectionRepository repository,
        ModelSpecApplicationService modelSpecs,
        ModelSpecPlanWriteAccessPort writeAccess,
        AuditService auditService,
        Clock clock,
        Supplier<UUID> correlationIds
    ) {
        this.repository = repository;
        this.modelSpecs = modelSpecs;
        this.writeAccess = writeAccess;
        this.auditService = auditService;
        this.clock = clock;
        this.correlationIds = correlationIds;
    }

    @Transactional(readOnly = true)
    public ServingSyncView get(String tenantId, UUID modelSpecId) {
        modelSpecs.get(tenantId, modelSpecId);
        return repository
            .findSyncState(tenantId, modelSpecId)
            .map(CatalogModelSemanticSyncCommandService::toView)
            .orElseGet(() -> ServingSyncView.notRegistered(modelSpecId));
    }

    @Transactional(readOnly = true)
    public List<ServingSyncView> getMany(String tenantId, List<UUID> modelSpecIds) {
        LinkedHashSet<UUID> requested = modelSpecIds == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(modelSpecIds);
        if (requested.isEmpty() || requested.contains(null) || requested.size() > MAX_BATCH_SIZE) {
            throw error(
                "MODEL_SEMANTIC_SYNC_BATCH_WINDOW_INVALID",
                "Serving sync status requests must contain between 1 and " + MAX_BATCH_SIZE + " unique model ids",
                ModelSpecException.Kind.BAD_REQUEST,
                Map.of("maximum", MAX_BATCH_SIZE, "requested", requested.size())
            );
        }
        return requested.stream().map(modelSpecId -> get(tenantId, modelSpecId)).toList();
    }

    @Transactional
    public RetryResult retry(String tenantId, String actorId, UUID modelSpecId, long expectedVersion) {
        ModelSpecView model = modelSpecs.get(tenantId, modelSpecId);
        if (
            actorId == null ||
            actorId.isBlank() ||
            !writeAccess.canMaintain(tenantId, model.planId(), actorId)
        ) {
            throw error(
                "MODEL_SEMANTIC_SYNC_RETRY_FORBIDDEN",
                "The authenticated actor cannot maintain this model plan",
                ModelSpecException.Kind.FORBIDDEN,
                Map.of("modelSpecId", modelSpecId)
            );
        }

        ServingSyncState current = repository
            .findSyncState(tenantId, modelSpecId)
            .orElseThrow(() -> error(
                "MODEL_SEMANTIC_SYNC_NOT_REGISTERED",
                "The model has no published semantic delivery state",
                ModelSpecException.Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId)
            ));
        if ("SYNC_PENDING".equals(current.projection().syncStatus())) {
            return new RetryResult(toView(current), true, null);
        }
        if (!"SYNC_FAILED".equals(current.projection().syncStatus())) {
            throw error(
                "MODEL_SEMANTIC_SYNC_RETRY_NOT_ALLOWED",
                "Only a failed semantic delivery can be retried",
                ModelSpecException.Kind.CONFLICT,
                details(current)
            );
        }
        if (current.projection().servingRef() == null) {
            throw error(
                "MODEL_SEMANTIC_SYNC_SERVING_REF_REQUIRED",
                "A verified serving reference is required before retry",
                ModelSpecException.Kind.UNPROCESSABLE,
                details(current)
            );
        }
        if (current.projection().version() != expectedVersion) {
            throw error(
                "MODEL_SEMANTIC_SYNC_VERSION_CONFLICT",
                "Semantic delivery state changed before retry",
                ModelSpecException.Kind.CONFLICT,
                details(current)
            );
        }

        if (!repository.requestSyncRetry(tenantId, modelSpecId, expectedVersion)) {
            Optional<ServingSyncState> concurrent = repository.findSyncState(tenantId, modelSpecId);
            if (concurrent.isPresent() && "SYNC_PENDING".equals(concurrent.orElseThrow().projection().syncStatus())) {
                return new RetryResult(toView(concurrent.orElseThrow()), true, null);
            }
            throw error(
                "MODEL_SEMANTIC_SYNC_VERSION_CONFLICT",
                "Semantic delivery state changed before retry",
                ModelSpecException.Kind.CONFLICT,
                concurrent.map(CatalogModelSemanticSyncCommandService::details).orElse(Map.of("modelSpecId", modelSpecId))
            );
        }

        ServingSyncState updated = repository
            .findSyncState(tenantId, modelSpecId)
            .orElseThrow(() -> error(
                "MODEL_SEMANTIC_SYNC_NOT_REGISTERED",
                "Semantic delivery state disappeared after retry",
                ModelSpecException.Kind.CONFLICT,
                Map.of("modelSpecId", modelSpecId)
            ));
        UUID correlationId = correlationIds.get();
        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("actor", actorId);
        audit.put("tenantId", tenantId);
        audit.put("planId", model.planId());
        audit.put("assetKey", current.projection().catalogAssetKey());
        audit.put("previousStatus", current.projection().syncStatus());
        audit.put("previousAttempts", current.syncAttempts());
        audit.put("previousErrorCode", current.lastSyncError());
        audit.put("expectedVersion", expectedVersion);
        audit.put("resultVersion", updated.projection().version());
        audit.put("correlationId", correlationId);
        audit.put("occurredAt", clock.instant());
        auditService.auditActionStrict(
            "MODELING_SEMANTIC_SYNC_RETRY",
            AuditStage.SUCCESS,
            modelSpecId.toString(),
            audit
        );
        return new RetryResult(toView(updated), false, correlationId);
    }

    private static ServingSyncView toView(ServingSyncState state) {
        var projection = state.projection();
        return new ServingSyncView(
            projection.modelSpecId(),
            projection.catalogAssetKey(),
            projection.syncStatus(),
            state.syncAttempts(),
            state.lastSyncError(),
            state.nextSyncAt(),
            projection.updatedAt(),
            projection.version(),
            projection.servingRef() != null,
            projection.latestPublishedRef(),
            projection.servingRef()
        );
    }

    private static Map<String, Object> details(ServingSyncState state) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("modelSpecId", state.projection().modelSpecId());
        details.put("syncStatus", state.projection().syncStatus());
        details.put("syncAttempts", state.syncAttempts());
        details.put("lastSyncError", state.lastSyncError());
        details.put("nextSyncAt", state.nextSyncAt());
        details.put("currentVersion", state.projection().version());
        return details;
    }

    private static ModelSpecException error(
        String code,
        String message,
        ModelSpecException.Kind kind,
        Object details
    ) {
        return new ModelSpecException(code, message, kind, details);
    }

    public record ServingSyncView(
        UUID modelSpecId,
        String catalogAssetKey,
        String syncStatus,
        int syncAttempts,
        String lastSyncError,
        Instant nextSyncAt,
        Instant updatedAt,
        long version,
        boolean servingReady,
        PublishedRef latestPublishedRef,
        ServingRef servingRef
    ) {
        static ServingSyncView notRegistered(UUID modelSpecId) {
            return new ServingSyncView(
                modelSpecId,
                null,
                "NOT_REGISTERED",
                0,
                null,
                null,
                null,
                0,
                false,
                null,
                null
            );
        }
    }

    public record RetryResult(ServingSyncView status, boolean replayed, UUID correlationId) {}
}
