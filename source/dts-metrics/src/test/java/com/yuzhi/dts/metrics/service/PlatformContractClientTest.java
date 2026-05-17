package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class PlatformContractClientTest {

    @Test
    void internalUrlDoesNotDoublePrefixApiPath() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/api");
        PlatformContractClient client = new PlatformContractClient(properties, RestClient.builder().build());

        assertThat(client.internalUrl("/internal/asset-permission/check"))
            .isEqualTo("http://dts-platform:8081/api/internal/asset-permission/check");
        assertThat(client.internalUrl("/api/internal/asset-permission/check"))
            .isEqualTo("http://dts-platform:8081/api/internal/asset-permission/check");
    }

    @Test
    void internalUrlSupportsRootApiPath() {
        DtsMetricsProperties properties = new DtsMetricsProperties();
        properties.getPlatform().setBaseUrl("http://dts-platform:8081/");
        properties.getPlatform().setApiPath("/");
        PlatformContractClient client = new PlatformContractClient(properties, RestClient.builder().build());

        assertThat(client.internalUrl("internal/capabilities"))
            .isEqualTo("http://dts-platform:8081/internal/capabilities");
    }
}
