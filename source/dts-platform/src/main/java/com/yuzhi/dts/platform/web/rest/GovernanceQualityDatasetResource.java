package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.governance.QualityDatasetCatalogService;
import com.yuzhi.dts.platform.service.governance.dto.QualityDatasetOptionDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance/quality/datasets")
public class GovernanceQualityDatasetResource {

    private final QualityDatasetCatalogService datasetCatalogService;

    public GovernanceQualityDatasetResource(QualityDatasetCatalogService datasetCatalogService) {
        this.datasetCatalogService = datasetCatalogService;
    }

    @GetMapping
    public ApiResponse<List<QualityDatasetOptionDto>> list(
        @RequestHeader(value = "X-Active-Dept", required = false) String activeDept
    ) {
        return ApiResponses.ok(datasetCatalogService.listReadableDefaultLakeDatasets(activeDept));
    }
}
