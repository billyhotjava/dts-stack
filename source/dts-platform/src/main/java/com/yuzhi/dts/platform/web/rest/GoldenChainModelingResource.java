package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtAssetSnapshot;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationInventoryService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainDbtMigrationReport;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseDecision;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseGateService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainModelReleaseRequest;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceCandidate;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceContractService;
import com.yuzhi.dts.platform.service.goldenchain.modeling.GoldenChainOdsDbtSourceRequest;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/golden-chains/modeling")
@Transactional(readOnly = true)
public class GoldenChainModelingResource {

    private final GoldenChainOdsDbtSourceContractService odsDbtSourceContractService;
    private final GoldenChainModelReleaseGateService modelReleaseGateService;
    private final GoldenChainDbtMigrationInventoryService dbtMigrationInventoryService;

    public GoldenChainModelingResource(
        GoldenChainOdsDbtSourceContractService odsDbtSourceContractService,
        GoldenChainModelReleaseGateService modelReleaseGateService,
        GoldenChainDbtMigrationInventoryService dbtMigrationInventoryService
    ) {
        this.odsDbtSourceContractService = odsDbtSourceContractService;
        this.modelReleaseGateService = modelReleaseGateService;
        this.dbtMigrationInventoryService = dbtMigrationInventoryService;
    }

    @PostMapping("/ods-dbt-source-candidates")
    public ApiResponse<GoldenChainOdsDbtSourceCandidate> buildOdsDbtSourceCandidate(
        @RequestBody GoldenChainOdsDbtSourceRequest request
    ) {
        return ApiResponses.ok(odsDbtSourceContractService.buildCandidate(request));
    }

    @PostMapping("/model-release-decisions")
    public ApiResponse<GoldenChainModelReleaseDecision> evaluateModelRelease(@RequestBody GoldenChainModelReleaseRequest request) {
        return ApiResponses.ok(modelReleaseGateService.evaluate(request));
    }

    @PostMapping("/dbt-migration-inventory")
    public ApiResponse<GoldenChainDbtMigrationReport> buildDbtMigrationInventory(@RequestBody List<GoldenChainDbtAssetSnapshot> snapshots) {
        return ApiResponses.ok(dbtMigrationInventoryService.inventory(snapshots));
    }
}
