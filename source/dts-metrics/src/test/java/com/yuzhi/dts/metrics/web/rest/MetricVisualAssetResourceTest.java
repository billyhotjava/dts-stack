package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class MetricVisualAssetResourceTest {

    @Test
    void defaultQueryReadsOnlyDwsAndAdsFromPlatformContract() {
        CapturingPlatformClient platformClient = new CapturingPlatformClient();
        MetricVisualAssetResource resource = new MetricVisualAssetResource(platformClient);

        Map<String, Object> result = resource.listVisualAssets(null, null, 0, 20, false);

        assertThat(platformClient.layers).containsExactly("DWS", "ADS");
        assertThat((List<?>) result.get("data")).hasSize(2);
        assertThat(asMap(result.get("meta"))).containsEntry("layers", List.of("DWS", "ADS"));
    }

    @Test
    void dwdRequiresExplicitDrilldownMode() {
        MetricVisualAssetResource resource = new MetricVisualAssetResource(new CapturingPlatformClient());

        assertThatThrownBy(() -> resource.listVisualAssets("DWD", null, 0, 20, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void odsAndStgAreRejectedAsLineageOnlyLayers() {
        MetricVisualAssetResource resource = new MetricVisualAssetResource(new CapturingPlatformClient());

        assertThatThrownBy(() -> resource.listVisualAssets("ODS", null, 0, 20, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void platformFailureDoesNotFallbackToStaticAssets() {
        CapturingPlatformClient platformClient = new CapturingPlatformClient();
        platformClient.fail = true;
        MetricVisualAssetResource resource = new MetricVisualAssetResource(platformClient);

        assertThatThrownBy(() -> resource.listVisualAssets(null, null, 0, 20, false))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static final class CapturingPlatformClient extends PlatformContractClient {

        private final List<String> layers = new ArrayList<>();
        private boolean fail;

        private CapturingPlatformClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public Map<String, Object> listCatalogAssets(String warehouseLayer, String keyword, int page, int size) {
            if (fail) {
                throw new PlatformContractException("platform unavailable");
            }
            layers.add(warehouseLayer);
            return Map.of(
                "data",
                Map.of(
                    "content",
                    List.of(
                        Map.of(
                            "id",
                            warehouseLayer.toLowerCase() + "-001",
                            "displayName",
                            warehouseLayer + " Order Summary",
                            "fqn",
                            "warehouse." + warehouseLayer.toLowerCase() + ".order_summary",
                            "warehouseLayer",
                            warehouseLayer,
                            "governanceStatus",
                            "ACTIVE"
                        )
                    ),
                    "total",
                    1
                )
            );
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }
}
