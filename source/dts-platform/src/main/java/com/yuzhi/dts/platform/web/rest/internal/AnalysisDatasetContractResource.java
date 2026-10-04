package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.sql.PublishedQueryDatasetService;
import com.yuzhi.dts.platform.service.sql.dto.AnalysisDatasetRuntimeContract;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/analysis-datasets")
@PreAuthorize(
    "hasAuthority('" +
    AuthoritiesConstants.SERVICE_INTERNAL +
    "') and authentication.name == 'service:dts-analytics'"
)
public class AnalysisDatasetContractResource {

    private final PublishedQueryDatasetService publishedQueryDatasetService;

    public AnalysisDatasetContractResource(PublishedQueryDatasetService publishedQueryDatasetService) {
        this.publishedQueryDatasetService = publishedQueryDatasetService;
    }

    @GetMapping("/{datasetId}/versions/{version}")
    public ApiResponse<AnalysisDatasetRuntimeContract> runtimeContract(
        @PathVariable UUID datasetId,
        @PathVariable int version
    ) {
        return ApiResponses.ok(publishedQueryDatasetService.runtimeContract(datasetId, version));
    }
}
