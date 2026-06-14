package com.yuzhi.dts.metrics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.repository.InMemoryGraphDraftRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelStateRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelVersionRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricRollbackEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Proves the graph-lifecycle path reaches the same platform security depth as the metric-pack path (Sprint-35b
 * F2): source-asset permission is enforced, a real platform RLS/masking policy is injected into the candidate
 * SQL, and high-risk actions emit audit events when enabled.
 */
class MetricLifecycleSecurityParityTest {

    @Test
    void resolvedPolicyInjectsRlsWhereAndMasksDimensionInCandidateSql() {
        StubPlatformClient client = new StubPlatformClient();
        client.policy = new PlatformContractClient.RlsPolicyResult(
            true,
            List.of("dept_code = '01'"),
            List.of("stat_date"),
            "platform-rls"
        );
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties());

        Map<String, Object> result = service.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph()));
        String sql = String.valueOf(asMap(result.get("artifacts")).get("dbtModelSql"));

        assertThat(sql)
            .contains("{{ dts_mask('stat_date') }}")
            .contains("-- dts-platform RLS: platform-rls")
            .contains("where")
            .contains("(dept_code = '01')");
        assertThat(result).containsEntry("appliedPolicySource", "platform-rls");
        assertThat(asMap(result.get("artifacts"))).containsKeys("maskingMacroSql", "securityPolicyJson");
    }

    @Test
    void emptyPolicyLeavesCandidateSqlWithoutRlsOrMasking() {
        StubPlatformClient client = new StubPlatformClient(); // default empty policy
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties());

        Map<String, Object> result = service.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph()));
        String sql = String.valueOf(asMap(result.get("artifacts")).get("dbtModelSql"));

        assertThat(sql).doesNotContain("dts_mask").doesNotContain("dts-platform RLS").doesNotContain("where");
    }

    @Test
    void deniedPermissionFailsArtifactGenerationWith403() {
        StubPlatformClient client = new StubPlatformClient();
        client.permissionAllowed = false;
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties());

        assertThatThrownBy(() -> service.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
            .hasMessageContaining("asset_permission_denied");
    }

    @Test
    void maskedColumnUsedAsMetricIsRejected() {
        StubPlatformClient client = new StubPlatformClient();
        client.policy = new PlatformContractClient.RlsPolicyResult(true, List.of(), List.of("order_amount"), "platform-rls");
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties());

        assertThatThrownBy(() -> service.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
            .hasMessageContaining("graph_validation_failed");
    }

    @Test
    void highRiskActionsEmitAuditEventsWhenEnabled() {
        StubPlatformClient client = new StubPlatformClient();
        DtsMetricsProperties props = new DtsMetricsProperties();
        props.getPlatform().setAuditEventsEnabled(true);
        MetricModelLifecycleService service = service(client, props);

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        service.publish("order-summary");

        assertThat(client.auditEvents).extracting(PlatformContractClient.AuditEventRequest::action)
            .contains("metric.model.artifacts.generate", "metric.model.validate", "metric.model.publish");
        assertThat(client.auditEvents).allSatisfy(event -> assertThat(event.modelId()).isEqualTo("order-summary"));
    }

    @Test
    void auditEventsStaySilentWhenDisabled() {
        StubPlatformClient client = new StubPlatformClient();
        MetricModelLifecycleService service = service(client, new DtsMetricsProperties()); // disabled by default

        service.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        service.publish("order-summary");

        assertThat(client.auditEvents).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static MetricModelLifecycleService service(StubPlatformClient client, DtsMetricsProperties props) {
        InMemoryMetricModelStateRepository stateRepository = new InMemoryMetricModelStateRepository();
        InMemoryMetricModelVersionRepository versionRepository = new InMemoryMetricModelVersionRepository();
        InMemoryMetricRollbackEventRepository rollbackRepository = new InMemoryMetricRollbackEventRepository();
        return new MetricModelLifecycleService(
            new MetricGraphDraftService(new InMemoryGraphDraftRepository()),
            client,
            new MetricSecurityPolicyService(client),
            props,
            stateRepository,
            versionRepository,
            rollbackRepository,
            new MetricLifecyclePublishWriter(stateRepository, versionRepository)
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

        private boolean permissionAllowed = true;
        private PlatformContractClient.RlsPolicyResult policy = PlatformContractClient.RlsPolicyResult.empty();
        private final List<AuditEventRequest> auditEvents = new ArrayList<>();

        private StubPlatformClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            return permissionAllowed
                ? new PermissionCheckResult(true, "PREVIEW", "allowed", null, request.action(), null, null, null, "INTERNAL", "platform-permission")
                : PermissionCheckResult.denied("asset_permission_denied");
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            return policy;
        }

        @Override
        public Map<String, Object> validateMetricModel(MetricModelValidationRequest request) {
            return Map.of("decision", "PASS", "valid", true);
        }

        @Override
        public Map<String, Object> checkDbtReleaseGate(DbtReleaseGateRequest request) {
            return Map.of("decision", "PASS");
        }

        @Override
        public Map<String, Object> submitDbtRelease(DbtReleaseSubmitRequest request) {
            return Map.of("publishReference", "platform-release-parity", "decision", "PASS");
        }

        @Override
        public void recordAuditEvent(AuditEventRequest request) {
            auditEvents.add(request);
        }
    }
}
