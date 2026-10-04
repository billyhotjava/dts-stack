package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract;
import com.yuzhi.dts.platform.service.governance.IndicatorCalculationService;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.web.rest.ApiResponse;
import com.yuzhi.dts.platform.web.rest.ApiResponses;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/indicators")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and authentication.name == 'service:dts-analytics'")
public class IndicatorAnalysisPlanResource {
    private final IndicatorCalculationService calculations;
    public IndicatorAnalysisPlanResource(IndicatorCalculationService calculations) { this.calculations = calculations; }
    public record Request(IndicatorAnalysisContract.Query query, IndicatorCalculationService.ConsumerActor actor) {}
    @PostMapping("/plan")
    public ApiResponse<IndicatorCalculationService.ConsumerPlan> plan(@RequestBody Request request) {
        return ApiResponses.ok(calculations.planForConsumer(request.query(), request.actor()));
    }
}
