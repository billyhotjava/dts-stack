package com.yuzhi.dts.platform.web.rest.capability;

import com.yuzhi.dts.platform.config.metrics.DtsMetricsCapabilityProperties;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PlatformCapabilityResource {

    private final DtsMetricsCapabilityProperties metricsProperties;

    public PlatformCapabilityResource(DtsMetricsCapabilityProperties metricsProperties) {
        this.metricsProperties = metricsProperties;
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("enabled", true);
        metrics.put("edition", metricsProperties.getEdition());
        metrics.put("apiBasePath", metricsProperties.getApiBasePath());
        metrics.put("serviceName", metricsProperties.getServiceName());
        metrics.put("mode", "remote-service");

        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("edition", metricsProperties.getEdition());
        capabilities.put("checkedAt", Instant.now().toString());
        capabilities.put("metrics", metrics);
        return capabilities;
    }
}
