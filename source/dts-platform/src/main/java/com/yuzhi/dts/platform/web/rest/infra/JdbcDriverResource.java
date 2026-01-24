package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.service.infra.JdbcDriverCatalogService;
import com.yuzhi.dts.platform.service.infra.dto.JdbcDriverInfo;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/jdbc")
public class JdbcDriverResource {

    private static final String INFRA_MAINTAINER_EXPRESSION =
        "hasAnyAuthority(T(com.yuzhi.dts.platform.security.AuthoritiesConstants).INFRA_MAINTAINERS)";

    private final JdbcDriverCatalogService catalogService;

    public JdbcDriverResource(JdbcDriverCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/drivers")
    @PreAuthorize(INFRA_MAINTAINER_EXPRESSION)
    public ApiResponse<List<JdbcDriverInfo>> listDrivers() {
        return ApiResponses.ok(catalogService.listDrivers());
    }
}
