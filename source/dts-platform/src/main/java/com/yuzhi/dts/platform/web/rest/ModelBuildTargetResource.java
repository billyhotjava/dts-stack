package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.etl.DbtConfigService;
import com.yuzhi.dts.platform.service.etl.DbtConfigService.ModelBuildTargetView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only platform target metadata for the existing model build panel. */
@RestController
@RequestMapping("/api/modeling/build-target")
public class ModelBuildTargetResource {

    private final DbtConfigService config;

    public ModelBuildTargetResource(DbtConfigService config) {
        this.config = config;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<ModelBuildTargetView> get() {
        return ApiResponses.ok(config.inspectModelBuildTarget());
    }
}
