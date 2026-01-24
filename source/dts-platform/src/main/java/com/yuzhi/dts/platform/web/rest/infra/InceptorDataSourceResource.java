package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.infra.HiveConnectionService;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.JdbcDriverCatalogService;
import com.yuzhi.dts.platform.service.infra.dto.JdbcDriverInfo;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/data-sources/inceptor")
public class InceptorDataSourceResource {

    private final HiveConnectionService connectionService;
    private final JdbcDriverCatalogService driverCatalogService;

    public InceptorDataSourceResource(HiveConnectionService connectionService, JdbcDriverCatalogService driverCatalogService) {
        this.connectionService = connectionService;
        this.driverCatalogService = driverCatalogService;
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<HiveConnectionTestResult> test(@RequestBody HiveConnectionTestRequest request) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ApiResponses.error("请填写 JDBC 连接信息");
        }
        HiveConnectionTestResult result = connectionService.testConnection(request);
        return ApiResponses.ok(result);
    }

    @GetMapping("/drivers")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<List<JdbcDriverInfo>> drivers() {
        return ApiResponses.ok(driverCatalogService.listDrivers());
    }
}
