package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.dto.MetricPackValidationResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics/packs")
public class MetricPackResource {

    private final MetricPackValidationService validationService;

    public MetricPackResource(MetricPackValidationService validationService) {
        this.validationService = validationService;
    }

    @PostMapping(value = "/validate", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public MetricPackValidationResult validate(@RequestBody String manifestContent) {
        return validationService.validateManifest(manifestContent);
    }

    @PostMapping(value = "/import", consumes = { "application/yaml", "text/yaml", MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_JSON_VALUE })
    public MetricPackValidationResult importPack(@RequestBody String manifestContent) {
        return validationService.validateManifest(manifestContent);
    }
}
