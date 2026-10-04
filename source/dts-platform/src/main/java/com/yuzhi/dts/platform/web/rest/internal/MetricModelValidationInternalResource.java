package com.yuzhi.dts.platform.web.rest.internal;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.service.metrics.MetricModelValidationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/metrics")
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.SERVICE_INTERNAL + "') and @metricsInternalAccess.isMetricsService(authentication)")
public class MetricModelValidationInternalResource {

    private final MetricModelValidationService validationService;

    public MetricModelValidationInternalResource(MetricModelValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping("/model-validation")
    public MetricModelValidationService.ValidationResult validateModel(
        @RequestBody(required = false) MetricModelValidationService.MetricModelValidationRequest request
    ) {
        return validationService.validate(request);
    }
}
