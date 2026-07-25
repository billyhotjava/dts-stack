package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry.Registration;
import com.yuzhi.dts.platform.service.catalog.CatalogExternalAssetIdentityRegistry.RegistrationResult;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/catalog/taggable-assets")
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and @metricsInternalAccess.isMetricsService(authentication)"
)
public class CatalogExternalAssetIdentityInternalResource {

    private final CatalogExternalAssetIdentityRegistry registry;

    public CatalogExternalAssetIdentityInternalResource(
        CatalogExternalAssetIdentityRegistry registry
    ) {
        this.registry = registry;
    }

    @PostMapping("/register")
    public RegistrationResult register(
        @RequestBody(required = false) RegistrationRequest request
    ) {
        try {
            if (request == null) {
                throw new IllegalArgumentException(
                    "registration request is required"
                );
            }
            boolean hasBatchIndex = request.batchIndex() != null;
            boolean hasBatchCount = request.batchCount() != null;
            if (hasBatchIndex != hasBatchCount) {
                throw new IllegalArgumentException(
                    "batch index and count must be provided together"
                );
            }
            boolean legacyUnknownBatchCount =
                request.registrationScope() != null &&
                !hasBatchIndex;
            return registry.register(
                "dts-metrics",
                request.registrationScope(),
                request.syncRunId(),
                hasBatchIndex ? request.batchIndex() : 0,
                hasBatchCount ? request.batchCount() : 1,
                legacyUnknownBatchCount,
                request.complete(),
                request.assets()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                exception.getMessage(),
                exception
            );
        }
    }

    public record RegistrationRequest(
        List<Registration> assets,
        String registrationScope,
        UUID syncRunId,
        Integer batchIndex,
        Integer batchCount,
        boolean complete
    ) {
        public RegistrationRequest(List<Registration> assets) {
            this(assets, null, null, 0, 1, false);
        }
    }
}
