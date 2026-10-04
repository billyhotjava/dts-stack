package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.service.infra.DataSourceSelectionService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/data-source-selections")
public class InfraDataSourceSelectionResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";
    private static final String ANALYTICS_SERVICE_EXPRESSION =
        "hasAuthority('" + com.yuzhi.dts.platform.security.AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-analytics'";

    private final DataSourceSelectionService selectionService;

    public InfraDataSourceSelectionResource(DataSourceSelectionService selectionService) {
        this.selectionService = selectionService;
    }

    @GetMapping
    @PreAuthorize("(" + INFRA_MAINTAINER_EXPRESSION + ") or (" + ANALYTICS_SERVICE_EXPRESSION + ")")
    public ApiResponse<DataSourceSelectionService.DataSourceSelectionResponse> list(
        @RequestParam(required = false, defaultValue = "MODELING_LAKEHOUSE") String capability,
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(selectionService.listSelections(capability, activeDept));
    }
}
