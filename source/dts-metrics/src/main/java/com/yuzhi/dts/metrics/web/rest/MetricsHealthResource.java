package com.yuzhi.dts.metrics.web.rest;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metrics")
public class MetricsHealthResource {

    private final DtsMetricsProperties properties;
    private final PlatformContractClient platformContractClient;

    public MetricsHealthResource(DtsMetricsProperties properties, PlatformContractClient platformContractClient) {
        this.properties = properties;
        this.platformContractClient = platformContractClient;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
            "status",
            "UP",
            "service",
            properties.getServiceName(),
            "enabled",
            true,
            "edition",
            properties.getEdition(),
            "checkedAt",
            Instant.now().toString()
        );
    }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of(
            "service",
            properties.getServiceName(),
            "enabled",
            true,
            "edition",
            properties.getEdition(),
            "mvp",
            List.of(
                "service-shell",
                "platform-contract",
                "metric-pack-v0.1-validation",
                "metric-dsl-sql-render",
                "dws-ads-candidate-artifact-preview",
                "metric-pack-import-dry-run",
                "semantic-migration-dry-run",
                "workspace-snapshot"
            ),
            "platformContract",
            platformContractClient.describeContract()
        );
    }
}
