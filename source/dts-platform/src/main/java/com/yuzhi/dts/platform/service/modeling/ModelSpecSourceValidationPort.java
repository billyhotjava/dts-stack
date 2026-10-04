package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.modeling.ModelSpecContract.SourceRef;
import java.util.Optional;
import java.util.UUID;

/** Validates a submitted source token against the locked, live warehouse-plan source. */
public interface ModelSpecSourceValidationPort {
    boolean isCurrentBinding(String tenantId, UUID planId, String actorId, SourceRef sourceRef);

    /** Re-resolves immutable gate evidence with the persisted plan owner context, independent of the viewer. */
    boolean isCurrentBindingForGate(String tenantId, UUID planId, SourceRef sourceRef);

    /** Validates a revision-pinned implementation input without requiring a duplicate legacy ModelSpec sourceRef. */
    boolean isCurrentBindingForGate(String tenantId, UUID planId, UUID sourceBindingId, String resolvedVersion);

    /**
     * Revalidates a previously confirmed binding for a trusted background materialization.
     * This check must not depend on a request-bound SecurityContext, but still fails closed for
     * missing, disabled, stale, fenced, or version-drifted sources.
     */
    boolean isCurrentBindingForExecution(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    );

    /** Returns the server-derived executable relation only while the exact plan binding remains current. */
    Optional<SourceRef> resolveCurrentBindingForCompiler(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    );

    /** Resolves the executable relation for an already admitted, revision-pinned background run. */
    Optional<SourceRef> resolveCurrentBindingForExecutionCompiler(
        String tenantId,
        UUID planId,
        UUID sourceBindingId,
        String resolvedVersion
    );
    default java.util.List<com.yuzhi.dts.platform.service.catalog.CatalogSourceReferenceReadPort.SourceField> readFields(
        String tenantId, UUID planId, UUID bindingId, String version) { return java.util.List.of(); }
}
