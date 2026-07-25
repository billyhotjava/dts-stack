package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.PlatformContractClient.TaggableAssetRegistration;
import com.yuzhi.dts.metrics.service.PlatformContractClient.TaggableAssetsRegisterRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class MetricTaggableAssetRegistrarTest {

    @Test
    void registersExactMetricPackAndMetricIdentities() {
        CapturingPlatformClient client = new CapturingPlatformClient();
        MetricTaggableAssetRegistrar registrar =
            new MetricTaggableAssetRegistrar(client, true);

        Map<String, Object> response = registrar.register(
            "Order Summary",
            Map.of("version", "v7"),
            Map.of(
                "tenant_namespace",
                "Acme North",
                "ownerDept",
                "FIN-OPS",
                "measures",
                List.of(
                    "Gross Amount",
                    Map.of("id", "Order Count"),
                    "gross_amount"
                ),
                "derived_metrics",
                List.of(
                    Map.of("id", "Average Amount"),
                    "Conversion Rate"
                )
            )
        );

        assertThat(response).containsEntry("registered", 5);
        assertThat(client.request).isNotNull();
        assertThat(client.request.registrationScope())
            .isEqualTo(
                "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary"
            );
        assertThat(client.request.syncRunId()).isNotNull();
        assertThat(client.request.complete()).isTrue();
        assertThat(client.request.assets())
            .containsExactly(
                new TaggableAssetRegistration(
                    "METRIC_PACK",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/version:v7",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/version:v7",
                    "FIN-OPS"
                ),
                new TaggableAssetRegistration(
                    "METRIC",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:gross_amount",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:gross_amount",
                    "FIN-OPS"
                ),
                new TaggableAssetRegistration(
                    "METRIC",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:order_count",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:order_count",
                    "FIN-OPS"
                ),
                new TaggableAssetRegistration(
                    "METRIC",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:average_amount",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:average_amount",
                    "FIN-OPS"
                ),
                new TaggableAssetRegistration(
                    "METRIC",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:conversion_rate",
                    "tenant:acme_north/env:prod/dialect:generic/metric-pack:order_summary/metric:conversion_rate",
                    "FIN-OPS"
                )
            );
    }

    @Test
    void isolatesCanonicalAndPermissionIdentitiesAcrossTenants() {
        CapturingPlatformClient client = new CapturingPlatformClient();
        MetricTaggableAssetRegistrar registrar =
            new MetricTaggableAssetRegistrar(client, true);
        Map<String, Object> published = Map.of("version", "v1");

        registrar.register(
            "orders",
            published,
            Map.of(
                "tenantNamespace",
                "tenant-a",
                "packId",
                "orders",
                "measures",
                List.of("revenue")
            )
        );
        registrar.register(
            "orders",
            published,
            Map.of(
                "tenantNamespace",
                "tenant-b",
                "packId",
                "orders",
                "measures",
                List.of("revenue")
            )
        );

        List<TaggableAssetRegistration> tenantA = client.requests
            .get(0)
            .assets();
        List<TaggableAssetRegistration> tenantB = client.requests
            .get(1)
            .assets();
        assertThat(tenantA)
            .extracting(TaggableAssetRegistration::canonicalAssetKey)
            .allMatch(key -> key.startsWith("tenant:tenant-a/"))
            .doesNotContainAnyElementsOf(
                tenantB
                    .stream()
                    .map(TaggableAssetRegistration::canonicalAssetKey)
                    .toList()
            );
        assertThat(tenantA)
            .extracting(TaggableAssetRegistration::remoteAssetId)
            .allMatch(id -> id.startsWith("tenant:tenant-a/"))
            .doesNotContainAnyElementsOf(
                tenantB
                    .stream()
                    .map(TaggableAssetRegistration::remoteAssetId)
                    .toList()
            );
    }

    @Test
    void disabledRegistrationIsAnIdempotentNoOp() {
        CapturingPlatformClient client = new CapturingPlatformClient();
        MetricTaggableAssetRegistrar registrar =
            new MetricTaggableAssetRegistrar(client, false);

        assertThat(registrar.register("model", Map.of(), Map.of()))
            .isEmpty();
        assertThat(client.requests).isEmpty();
    }

    @Test
    void chunksLargeMetricPacksAtThePlatformContractLimit() {
        CapturingPlatformClient client = new CapturingPlatformClient();
        MetricTaggableAssetRegistrar registrar =
            new MetricTaggableAssetRegistrar(client, true);
        List<String> measures = IntStream.range(0, 500)
            .mapToObj(index -> "metric_" + index)
            .toList();

        Map<String, Object> response = registrar.register(
            "large-pack",
            Map.of("version", "v1"),
            Map.of("measures", measures)
        );

        assertThat(response).containsEntry("registered", 501);
        assertThat(client.requests)
            .extracting(request -> request.assets().size())
            .containsExactly(500, 1);
        assertThat(client.requests)
            .extracting(TaggableAssetsRegisterRequest::complete)
            .containsExactly(false, true);
        assertThat(client.requests)
            .extracting(TaggableAssetsRegisterRequest::syncRunId)
            .containsOnly(client.requests.getFirst().syncRunId());
    }

    private static final class CapturingPlatformClient
        extends PlatformContractClient {

        private TaggableAssetsRegisterRequest request;
        private final List<TaggableAssetsRegisterRequest> requests =
            new ArrayList<>();

        private CapturingPlatformClient() {
            super(
                new DtsMetricsProperties(),
                RestClient.builder().build()
            );
        }

        @Override
        public Map<String, Object> registerTaggableAssets(
            TaggableAssetsRegisterRequest request
        ) {
            this.request = request;
            this.requests.add(request);
            return Map.of("registered", request.assets().size());
        }
    }
}
