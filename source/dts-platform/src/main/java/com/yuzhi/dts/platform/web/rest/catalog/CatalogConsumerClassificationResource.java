package com.yuzhi.dts.platform.web.rest.catalog;

import com.yuzhi.dts.platform.security.SecurityUtils;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.AccessBindingView;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.BindAccessCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DerivationResult;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.DeriveCommand;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.ExportSeal;
import com.yuzhi.dts.platform.service.catalog.CatalogConsumerClassificationService.SubjectRef;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/classifications/consumers")
public class CatalogConsumerClassificationResource {

    private static final String WRITE =
        "hasAnyAuthority('ROLE_ADMIN','ROLE_OP_ADMIN','ROLE_GOV_ADMIN','ROLE_DATA_STEWARD','ROLE_INFRA_ADMIN','ROLE_INTERNAL_SERVICE')";

    private final CatalogConsumerClassificationService service;

    public CatalogConsumerClassificationResource(CatalogConsumerClassificationService service) {
        this.service = service;
    }

    @PostMapping("/derive")
    @PreAuthorize(WRITE)
    public ApiResponse<DerivationResult> derive(@RequestBody DeriveCommand command) {
        return ApiResponses.ok(service.derive(command));
    }

    @GetMapping("/explain")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DerivationResult> explain(
        @RequestParam String consumerType,
        @RequestParam String consumerKey
    ) {
        return ApiResponses.ok(service.explain(consumerType, consumerKey));
    }

    @GetMapping("/guard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<DerivationResult> guardConsumer(
        @RequestParam String consumerType,
        @RequestParam String consumerKey
    ) {
        return ApiResponses.ok(service.requireCurrentConsumer(consumerType, consumerKey));
    }

    @PostMapping("/access-bindings")
    @PreAuthorize(WRITE)
    public ApiResponse<AccessBindingView> bind(@RequestBody BindAccessCommand command) {
        return ApiResponses.ok(service.bindAccess(command, actor()));
    }

    @GetMapping("/access-bindings/guard")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<AccessBindingView> guard(
        @RequestParam String bindingType,
        @RequestParam String bindingKey
    ) {
        return ApiResponses.ok(service.requireCurrentAccessBinding(bindingType, bindingKey));
    }

    @PostMapping("/exports/seal")
    @PreAuthorize(WRITE)
    public ApiResponse<ExportSeal> sealExport(@RequestBody ExportSealRequest request) {
        return ApiResponses.ok(
            service.sealExport(
                request.fileSubjectKey(),
                request.upstreams(),
                request.originRef()
            )
        );
    }

    private String actor() {
        return SecurityUtils.getCurrentUserLogin().orElse("system");
    }

    public record ExportSealRequest(
        String fileSubjectKey,
        List<SubjectRef> upstreams,
        String originRef
    ) {}
}
