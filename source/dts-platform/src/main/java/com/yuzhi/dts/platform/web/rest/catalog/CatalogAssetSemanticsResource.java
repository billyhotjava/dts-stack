package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetRegistrationService;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.AssetSemanticsView;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.ReconciliationReceipt;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticStore.StatsSnapshot;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetSemanticsContract.ObservationCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogAssetType;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Compatible extension of the assets-v2 API; existing list and contract payloads remain unchanged. */
@RestController
@RequestMapping("/api/catalog/assets-v2")
public class CatalogAssetSemanticsResource {

    private static final String GLOBAL_WRITE_EXPRESSION =
        "hasAnyAuthority('" +
        AuthoritiesConstants.ADMIN +
        "','" +
        AuthoritiesConstants.OP_ADMIN +
        "','" +
        AuthoritiesConstants.INST_DATA_OWNER +
        "')";

    private final CatalogAssetRegistrationService registrationService;

    public CatalogAssetSemanticsResource(CatalogAssetRegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @GetMapping("/semantics")
    public ApiResponse<AssetSemanticsView> semantics(
        @RequestParam(name = "assetType", defaultValue = "DATASET") CatalogAssetType assetType,
        @RequestParam(name = "assetKey") String assetKey
    ) {
        AssetSemanticsView view = registrationService
            .find(assetType, assetKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "ASSET_SEMANTICS_NOT_FOUND"));
        return ApiResponses.ok(view);
    }

    @GetMapping("/stats-projection")
    public ApiResponse<StatsSnapshot> stats(@RequestParam(name = "domainId", required = false) UUID domainId) {
        return ApiResponses.ok(registrationService.stats(domainId));
    }

    @PostMapping("/observations")
    @PreAuthorize(GLOBAL_WRITE_EXPRESSION)
    public ApiResponse<CatalogAssetRegistrationService.ObservationResult> observe(@RequestBody ObservationCommand command) {
        return ApiResponses.ok(registrationService.observe(command));
    }

    @PostMapping("/stats-projection/reconcile")
    @PreAuthorize(GLOBAL_WRITE_EXPRESSION)
    public ApiResponse<ReconciliationReceipt> reconcile() {
        return ApiResponses.ok(registrationService.reconcile());
    }
}
