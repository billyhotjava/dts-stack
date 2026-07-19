package com.yuzhi.dts.platform.service.modeling.warehouse;

import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceLocator;
import com.yuzhi.dts.platform.service.modeling.warehouse.WarehousePlanContract.SourceType;

/** Resolves a warehouse-plan source against the subsystem that owns the referenced asset. */
public interface SourceReferenceResolver {

    ResolvedSource resolve(SourceType sourceType, SourceLocator locator, AccessContext accessContext);

    enum ResolutionStatus {
        AVAILABLE,
        MISSING,
        FORBIDDEN,
        PROVIDER_ERROR,
    }

    record AccessContext(String tenantId, String actorId, String actorDepartmentId) {}

    record ResolvedSource(ResolutionStatus status, String displayName, String resolvedVersion) {
        public static ResolvedSource available(String displayName, String resolvedVersion) {
            return new ResolvedSource(ResolutionStatus.AVAILABLE, displayName, resolvedVersion);
        }

        public static ResolvedSource missing() {
            return new ResolvedSource(ResolutionStatus.MISSING, null, null);
        }

        public static ResolvedSource forbidden() {
            return new ResolvedSource(ResolutionStatus.FORBIDDEN, null, null);
        }

        public static ResolvedSource providerError() {
            return new ResolvedSource(ResolutionStatus.PROVIDER_ERROR, null, null);
        }
    }
}
