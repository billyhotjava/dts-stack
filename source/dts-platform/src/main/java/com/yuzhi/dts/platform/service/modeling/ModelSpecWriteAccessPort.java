package com.yuzhi.dts.platform.service.modeling;

import java.util.Collection;
import java.util.UUID;

/** Object editing is independent of plan maintenance, data access and release duties. */
public interface ModelSpecWriteAccessPort {
    default void requireBindingOperation(String tenant, UUID planId, UUID bindingId, String actor) {
        throw new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "没有操作这些模型的权限", ModelSpecException.Kind.FORBIDDEN);
    }
    default boolean canEdit(String tenantId, UUID modelSpecId, String actorId) { return false; }
    default boolean canReadPlan(String tenantId, UUID planId) { return false; }
    default boolean canReadPlan(UUID planId) { return false; }
    default void requireEdit(String tenantId, UUID modelSpecId, String actorId) {
        requireOperation(tenantId, java.util.List.of(modelSpecId), actorId);
    }
    default void requireOperation(String tenantId, Collection<UUID> ids, String actorId) {
        throw new ModelSpecException("MODEL_OPERATION_SCOPE_DENIED", "没有操作这些模型的权限", ModelSpecException.Kind.FORBIDDEN);
    }
    default void initializeOwner(String tenantId, UUID id, String actorId) {
        throw new IllegalStateException("Model ownership adapter is required");
    }
    default void reclassifyAccess(String tenantId, UUID id, String actorId, ModelSpecContract.ModelType from, ModelSpecContract.ModelType to) {
        throw new IllegalStateException("Model ownership adapter is required");
    }
}
