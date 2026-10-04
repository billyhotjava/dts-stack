package com.yuzhi.dts.platform.web.rest.infra;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.infra.HiveConnectionTestResult;
import com.yuzhi.dts.platform.service.infra.JdbcConnectionTestService;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/infra/data-sources/jdbc")
public class JdbcDataSourceResource {

    private final JdbcConnectionTestService testService;

    public JdbcDataSourceResource(JdbcConnectionTestService testService) {
        this.testService = testService;
    }

    @PostMapping("/test")
    @PreAuthorize("hasAuthority('" + AuthoritiesConstants.OP_ADMIN + "')")
    public ApiResponse<HiveConnectionTestResult> test(@RequestBody JdbcConnectionTestRequest request) {
        if (request == null || !StringUtils.hasText(request.getJdbcUrl())) {
            return ApiResponses.error("请填写 JDBC 连接信息");
        }
        return ApiResponses.ok(testService.testConnection(request));
    }
}
