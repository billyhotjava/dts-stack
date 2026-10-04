package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.service.goldenchain.GoldenChainQueryService;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainDetailResponse;
import com.yuzhi.dts.platform.service.goldenchain.dto.GoldenChainSummaryResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/golden-chains")
@Transactional(readOnly = true)
public class GoldenChainResource {

    private final GoldenChainQueryService queryService;

    public GoldenChainResource(GoldenChainQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<List<GoldenChainSummaryResponse>> list() {
        return ApiResponses.ok(queryService.listChains());
    }

    @GetMapping("/{chainKey}")
    public ApiResponse<GoldenChainDetailResponse> detail(@PathVariable String chainKey) {
        GoldenChainDetailResponse detail = queryService
            .findDetailByChainKey(chainKey)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "链路不存在或无权访问"));
        return ApiResponses.ok(detail);
    }
}
