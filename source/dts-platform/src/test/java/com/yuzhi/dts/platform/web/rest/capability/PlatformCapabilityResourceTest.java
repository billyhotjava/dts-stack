package com.yuzhi.dts.platform.web.rest.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.config.metrics.DtsMetricsCapabilityProperties;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlatformCapabilityResourceTest {

    @Test
    void internalCapabilitiesExposeCatalogPermissionAndPublishContracts() {
        DtsMetricsCapabilityProperties properties = new DtsMetricsCapabilityProperties();
        properties.setEdition("professional");
        PlatformCapabilityResource resource = new PlatformCapabilityResource(properties);

        Map<String, Object> capabilities = resource.internalCapabilities();

        assertThat(capabilities).containsKeys("goldenPath", "catalog", "permissions", "classification", "dbtPublish");
        assertThat(map(capabilities.get("goldenPath"))).containsEntry("contractVersion", "2026-05-sprint31");
        assertThat(map(capabilities.get("catalog"))).containsEntry("contractVersion", "2026-05-sprint31a");
        assertThat(map(capabilities.get("catalog")))
            .containsEntry("migrationDryRunEndpoint", "/api/catalog/assets-v2/migration/dry-run");
        assertThat(map(capabilities.get("catalog")).get("readEndpoints").toString())
            .contains("/api/catalog/assets-v2/resolution-failures");
        assertThat(map(capabilities.get("permissions"))).containsEntry("source", "platform-asset-grant");
        assertThat(map(capabilities.get("dbtPublish"))).containsEntry("mode", "platform-gated");
        assertThat(map(capabilities.get("dbtPublish"))).containsEntry("validationGateway", "/api/internal/metrics/model-validation");
        assertThat(map(capabilities.get("metrics"))).containsEntry("serviceBoundary", "optional-value-added-service");
        assertThat(map(capabilities.get("metrics"))).containsEntry("partnerDelivery", "metric-pack");
        assertThat(map(capabilities.get("metrics")).get("requiredPlatformContracts").toString())
            .contains("/api/internal/domains/resolve")
            .contains("/api/internal/data-standards/resolve")
            .contains("/api/internal/v1/asset-permission/policy")
            .contains("/api/internal/metrics/model-validation");
        assertThat(map(capabilities.get("permissions")).get("endpoints").toString())
            .contains("/api/internal/v1/asset-permission/policy")
            .contains("/api/internal/asset-permission/policy");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
