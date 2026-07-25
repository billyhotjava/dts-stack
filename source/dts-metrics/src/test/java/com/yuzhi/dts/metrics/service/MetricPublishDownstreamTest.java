package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.repository.InMemoryGraphDraftRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelStateRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelVersionRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricRollbackEventRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proves the publish loop is closed (Sprint-35b F3): a published version is registered with the platform BI
 * Dataset + lineage, a registration failure blocks rather than silently reports a clean publish, and a
 * publish retry is idempotent.
 */
class MetricPublishDownstreamTest {

    @Test
    void publishRegistersBiDatasetAndLineageWhenEnabled() {
        StubPlatformClient client = new StubPlatformClient();
        DtsMetricsProperties props = registerEnabled();
        MetricModelLifecycleService service = service(client, props);

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> result = service.publish("order-summary");

        assertThat(result)
            .containsEntry("status", "PUBLISHED")
            .containsEntry("biDatasetReference", "bi-dataset-001")
            .containsEntry("lineageReference", "lineage-001");
        assertThat(client.biRegisterCount).isEqualTo(1);
        assertThat(client.lineageRegisterCount).isEqualTo(1);
    }

    @Test
    void publishRegistersMetricIdentitiesFromInlineGraphWhenEnabled() {
        StubPlatformClient client = new StubPlatformClient();
        MetricModelLifecycleService service = service(
            client,
            new DtsMetricsProperties(),
            true
        );

        service.validateModel(
            "order-summary",
            Map.of("graph", readyDwsGraph())
        );
        Map<String, Object> result = service.publish("order-summary");

        assertThat(result)
            .containsEntry("status", "PUBLISHED")
            .containsEntry("taggableAssetRegistrationCount", 4)
            .doesNotContainKeys(
                "biDatasetReference",
                "lineageReference"
            );
        assertThat(client.taggableRegisterCount).isEqualTo(1);
        assertThat(client.taggableRequest.assets())
            .extracting(
                PlatformContractClient.TaggableAssetRegistration::canonicalAssetKey
            )
            .containsExactly(
                "tenant:default/env:prod/dialect:generic/metric-pack:order-summary/version:v1",
                "metric:order-summary/order_amount",
                "metric:order-summary/order_count",
                "metric:order-summary/avg_order_amount"
            );
    }

    @Test
    void taggableRegistrationFailureBlocksAndRetriesCommittedVersion() {
        StubPlatformClient client = new StubPlatformClient();
        client.failTaggableRegister = true;
        MetricModelLifecycleService service = service(
            client,
            new DtsMetricsProperties(),
            true
        );

        service.validateModel(
            "order-summary",
            Map.of("graph", readyDwsGraph())
        );

        assertThatThrownBy(() -> service.publish("order-summary"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error ->
                assertThat(
                    ((ResponseStatusException) error).getStatusCode()
                ).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
            )
            .hasMessageContaining("platform_contract_unavailable");
        assertThat(service.versionHistory("order-summary"))
            .containsEntry("status", "PUBLISH_BLOCKED");

        client.failTaggableRegister = false;
        Map<String, Object> retry = service.publish("order-summary");

        assertThat(retry)
            .containsEntry("status", "PUBLISHED")
            .containsEntry("version", "v1")
            .containsEntry("taggableAssetRegistrationCount", 4)
            .doesNotContainKey("publishBlockReason");
        assertThat(client.submitCount).isEqualTo(1);
        assertThat(client.taggableRegisterCount).isEqualTo(2);
    }

    @Test
    void registrationFailureMarksModelPublishBlocked() {
        StubPlatformClient client = new StubPlatformClient();
        client.failBiRegister = true;
        MetricModelLifecycleService service = service(client, registerEnabled());

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));

        assertThatThrownBy(() -> service.publish("order-summary"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE))
            .hasMessageContaining("platform_contract_unavailable");
        assertThat(service.versionHistory("order-summary")).containsEntry("status", "PUBLISH_BLOCKED");

        client.failBiRegister = false;
        Map<String, Object> retry = service.publish("order-summary");

        assertThat(retry)
            .containsEntry("status", "PUBLISHED")
            .containsEntry("version", "v1");
        assertThat(client.submitCount).isEqualTo(1);
        assertThat(client.biRegisterCount).isEqualTo(2);
    }

    @Test
    void registrationIsSkippedWhenDisabled() {
        StubPlatformClient client = new StubPlatformClient();
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties()); // disabled by default

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> result = service.publish("order-summary");

        assertThat(result).containsEntry("status", "PUBLISHED").doesNotContainKeys("biDatasetReference", "lineageReference");
        assertThat(client.biRegisterCount).isZero();
        assertThat(client.lineageRegisterCount).isZero();
        assertThat(client.taggableRegisterCount).isZero();
    }

    @Test
    void republishingAPublishedModelIsIdempotent() {
        StubPlatformClient client = new StubPlatformClient();
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties());

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> first = service.publish("order-summary");
        Map<String, Object> retry = service.publish("order-summary");

        assertThat(retry).containsEntry("version", first.get("version")).containsEntry("status", "PUBLISHED");
        assertThat(client.submitCount).isEqualTo(1); // the retry did not submit a duplicate release
    }

    private static DtsMetricsProperties registerEnabled() {
        DtsMetricsProperties props = new DtsMetricsProperties();
        props.getPlatform().setBiLineageRegisterEnabled(true);
        return props;
    }

    private static MetricModelLifecycleService service(StubPlatformClient client, DtsMetricsProperties props) {
        return service(client, props, false);
    }

    private static MetricModelLifecycleService service(
        StubPlatformClient client,
        DtsMetricsProperties props,
        boolean taggableAssetRegistrationEnabled
    ) {
        InMemoryMetricModelStateRepository stateRepository = new InMemoryMetricModelStateRepository();
        InMemoryMetricModelVersionRepository versionRepository = new InMemoryMetricModelVersionRepository();
        InMemoryMetricRollbackEventRepository rollbackRepository = new InMemoryMetricRollbackEventRepository();
        return new MetricModelLifecycleService(
            new MetricGraphDraftService(new InMemoryGraphDraftRepository()),
            client,
            new MetricSecurityPolicyService(client),
            new MetricCandidateArtifactBuilder(new MetricSecurityPolicyService(client)),
            props,
            stateRepository,
            versionRepository,
            rollbackRepository,
            new MetricLifecyclePublishWriter(stateRepository, versionRepository),
            new MetricDownstreamRegistrar(
                client,
                props,
                stateRepository,
                new MetricTaggableAssetRegistrar(
                    client,
                    taggableAssetRegistrationEnabled
                )
            )
        );
    }

    private static Map<String, Object> readyDwsGraph() {
        return Map.of(
            "base",
            "dws_order_day",
            "dialect",
            "",
            "measures",
            List.of("order_amount", "order_count"),
            "dimensions",
            List.of("stat_date"),
            "derived_metrics",
            List.of(Map.of("id", "avg_order_amount", "expression", "ratio(order_amount, order_count)")),
            "nodes",
            List.of(Map.of("id", "dws_order_day", "role", "BASE", "warehouseLayer", "DWS", "grain", List.of("stat_date")))
        );
    }

    private static final class StubPlatformClient extends PlatformContractClient {

        private boolean failBiRegister = false;
        private boolean failTaggableRegister = false;
        private int submitCount = 0;
        private int biRegisterCount = 0;
        private int lineageRegisterCount = 0;
        private int taggableRegisterCount = 0;
        private TaggableAssetsRegisterRequest taggableRequest;

        private StubPlatformClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            return new PermissionCheckResult(true, "PREVIEW", "allowed", null, request.action(), null, null, null, "INTERNAL", "platform-permission");
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            return RlsPolicyResult.empty();
        }

        @Override
        public Map<String, Object> validateMetricModel(MetricModelValidationRequest request) {
            return Map.of("decision", "PASS", "valid", true);
        }

        @Override
        public Map<String, Object> submitDbtRelease(DbtReleaseSubmitRequest request) {
            submitCount++;
            return Map.of("publishReference", "platform-release-f3", "decision", "PASS");
        }

        @Override
        public Map<String, Object> registerBiDataset(BiDatasetRegisterRequest request) {
            biRegisterCount++;
            if (failBiRegister) {
                throw new PlatformContractException("bi register down");
            }
            return Map.of("datasetReference", "bi-dataset-001");
        }

        @Override
        public Map<String, Object> registerLineage(LineageRegisterRequest request) {
            lineageRegisterCount++;
            return Map.of("lineageReference", "lineage-001");
        }

        @Override
        public Map<String, Object> registerTaggableAssets(
            TaggableAssetsRegisterRequest request
        ) {
            taggableRegisterCount++;
            if (failTaggableRegister) {
                throw new PlatformContractException(
                    "taggable register down"
                );
            }
            taggableRequest = request;
            return Map.of("registered", request.assets().size());
        }
    }
}
