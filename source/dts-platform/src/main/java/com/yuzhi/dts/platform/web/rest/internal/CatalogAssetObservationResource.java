package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ObservationCommand;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Service-to-service observation boundary used by scanners, ingestion, dbt and materialization adapters. */
@RestController
@RequestMapping("/api/internal/catalog/asset-observations")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "')")
public class CatalogAssetObservationResource {

    private static final int MAX_BATCH_SIZE = 500;
    private final CatalogAssetRegistrationService registrationService;

    public CatalogAssetObservationResource(CatalogAssetRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public List<CatalogAssetRegistrationService.ObservationResult> observe(@RequestBody List<ObservationCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > MAX_BATCH_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ASSET_OBSERVATION_BATCH_SIZE_INVALID");
        }
        return commands.stream().map(registrationService::observe).toList();
    }
}
