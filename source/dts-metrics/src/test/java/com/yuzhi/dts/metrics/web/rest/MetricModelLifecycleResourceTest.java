package com.yuzhi.dts.metrics.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.domain.repository.InMemoryGraphDraftRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelStateRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricModelVersionRepository;
import com.yuzhi.dts.metrics.domain.repository.InMemoryMetricRollbackEventRepository;
import com.yuzhi.dts.metrics.service.MetricDownstreamRegistrar;
import com.yuzhi.dts.metrics.service.MetricGraphDraftService;
import com.yuzhi.dts.metrics.service.MetricLifecyclePublishWriter;
import com.yuzhi.dts.metrics.service.MetricModelLifecycleService;
import com.yuzhi.dts.metrics.service.MetricSecurityPolicyService;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import com.yuzhi.dts.metrics.service.dto.MetricContractErrorCode;
import com.yuzhi.dts.metrics.service.dto.MetricLifecycleStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class MetricModelLifecycleResourceTest {

    @Test
    void lifecycleStatusAndErrorCodesAreCentralizedContracts() {
        assertThat(MetricLifecycleStatus.PUBLISHED.code()).isEqualTo("PUBLISHED");
        assertThat(MetricLifecycleStatus.ROLLED_BACK.code()).isEqualTo("ROLLED_BACK");
        assertThat(MetricContractErrorCode.GRAPH_VALIDATION_FAILED.code()).isEqualTo("graph_validation_failed");
        assertThat(MetricContractErrorCode.STANDARD_CODE_REQUIRED.code()).isEqualTo("standard_code_required");
        assertThat(MetricContractErrorCode.DBT_VALIDATION_REQUIRED.code()).isEqualTo("dbt_validation_required");
    }

    @Test
    void artifactGenerationRejectsBlockedGraph() {
        MetricModelResource resource = resource(new CapturingPlatformClient());

        assertThatThrownBy(() -> resource.generateArtifacts("blocked-model", Map.of("graph", blockedOdsGraph())))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
            .hasMessageContaining("graph_validation_failed");
    }

    @Test
    void artifactGenerationCreatesSafeCandidateArtifactsForReadyDwsGraph() {
        MetricModelResource resource = resource(new CapturingPlatformClient());

        Map<String, Object> result = resource.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> artifacts = asMap(result.get("artifacts"));

        assertThat(result).containsEntry("status", "ARTIFACT_GENERATED");
        assertThat(result).containsKeys("artifactRef", "appliedPolicySource", "appliedPredicateHash");
        assertThat(artifacts).containsKeys("dbtModelSql", "schemaYml", "exposureYml", "metricDoc", "lineageHint", "securitySnapshot");
        assertThat(String.valueOf(artifacts.get("dbtModelSql")))
            .contains("from {{ ref('dws_order_day') }}")
            .contains("nullif(sum(\"order_count\"), 0)")
            .doesNotContain(";")
            .doesNotContain("select *");
        assertThat(asMap(artifacts.get("lineageHint"))).containsEntry("targetLayer", "DWS");
        assertThat(asMap(artifacts.get("securitySnapshot")))
            .containsEntry("policySource", result.get("appliedPolicySource"))
            .containsEntry("predicateHash", result.get("appliedPredicateHash"))
            .containsEntry("targetLayer", "DWS");
        assertThat(String.valueOf(artifacts.get("securitySnapshot"))).doesNotContain("secret").doesNotContain("/var/lib");
    }

    @Test
    void generatedDwsModelSqlMatchesGoldenCandidateSql() throws IOException {
        MetricModelResource resource = resource(new CapturingPlatformClient());

        Map<String, Object> result = resource.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> artifacts = asMap(result.get("artifacts"));

        assertThat(normalize(String.valueOf(artifacts.get("dbtModelSql")))).isEqualTo(golden("golden-sql/dws-order-summary-model.sql"));
    }

    @Test
    void generatedDorisCandidateSqlUsesDialectIdentifierQuoting() {
        MetricModelResource resource = resource(new CapturingPlatformClient());

        Map<String, Object> result = resource.generateArtifacts("order-summary", Map.of("graph", readyDwsGraphWithDialect("doris")));
        Map<String, Object> artifacts = asMap(result.get("artifacts"));

        assertThat(String.valueOf(artifacts.get("dbtModelSql")))
            .contains("`stat_date`")
            .contains("sum(`order_amount`) as `order_amount`")
            .contains("nullif(sum(`order_count`), 0)");
    }

    @Test
    void validationCallsPlatformModelValidationGateway() {
        CapturingPlatformClient platformClient = new CapturingPlatformClient();
        MetricModelResource resource = resource(platformClient);

        Map<String, Object> result = resource.validateModel("order-summary", Map.of("graph", readyDwsGraph()));

        assertThat(result).containsEntry("status", "DBT_VALIDATED");
        assertThat(platformClient.validationRequest).isNotNull();
        assertThat(platformClient.validationRequest.modelId()).isEqualTo("order-summary");
        assertThat(platformClient.validationRequest.artifactRef()).startsWith("candidate://dts-metrics/");
        assertThat(platformClient.validationRequest.artifacts()).containsKeys("dbtModelSql", "schemaYml");
    }

    @Test
    void publishDryRunRequiresDbtValidatedState() {
        MetricModelResource resource = resource(new CapturingPlatformClient());
        resource.generateArtifacts("order-summary", Map.of("graph", readyDwsGraph()));

        assertThatThrownBy(() -> resource.publishDryRun("order-summary"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
            .hasMessageContaining("dbt_validation_required");
    }

    @Test
    void publishReturnsPlatformReferenceWithoutLeakingArtifactsOrPlatformPayload() {
        CapturingPlatformClient platformClient = new CapturingPlatformClient();
        MetricModelResource resource = resource(platformClient);

        resource.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> result = resource.publish("order-summary");

        assertThat(result)
            .containsEntry("status", "PUBLISHED")
            .containsEntry("platformPublishReference", "platform-release-001")
            .containsEntry("releaseDecision", "PASS")
            .containsEntry("version", "v1")
            .containsEntry("rollbackAvailable", false);
        assertThat(result).doesNotContainKeys("artifacts", "release", "secret", "dbtProjectPath");
        assertThat(platformClient.submitRequest).isNotNull();
        assertThat(platformClient.submitRequest.dryRun()).isFalse();
    }

    @Test
    void rollbackRestoresPreviousPublishedVersionAndExposesVersionHistory() {
        CapturingPlatformClient platformClient = new CapturingPlatformClient();
        MetricModelResource resource = resource(platformClient);

        resource.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        resource.publish("order-summary");
        resource.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        Map<String, Object> secondPublish = resource.publish("order-summary");

        Map<String, Object> rollback = resource.rollback("order-summary", Map.of("reason", "bad downstream validation"));
        Map<String, Object> history = resource.versionHistory("order-summary");

        assertThat(secondPublish)
            .containsEntry("version", "v2")
            .containsEntry("previousVersion", "v1")
            .containsEntry("rollbackAvailable", true);
        assertThat(rollback)
            .containsEntry("status", "ROLLED_BACK")
            .containsEntry("activeVersion", "v1")
            .containsEntry("rollbackFromVersion", "v2")
            .containsEntry("rollbackToVersion", "v1")
            .containsEntry("reason", "bad downstream validation");
        assertThat(rollback).doesNotContainKeys("artifacts", "release", "secret", "dbtProjectPath");
        assertThat(history)
            .containsEntry("status", "ROLLED_BACK")
            .containsEntry("activeVersion", "v1")
            .containsEntry("rollbackAvailable", true);
        assertThat(asList(history.get("versions"))).hasSize(2);
        assertThat(asList(history.get("rollbackEvents"))).hasSize(1);
    }

    @Test
    void rollbackRequiresAPreviousPublishedVersion() {
        MetricModelResource resource = resource(new CapturingPlatformClient());

        resource.validateModel("order-summary", Map.of("graph", readyDwsGraph()));
        resource.publish("order-summary");

        assertThatThrownBy(() -> resource.rollback("order-summary", Map.of()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY))
            .hasMessageContaining("rollback_target_required");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static String golden(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return normalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static String normalize(String value) {
        return value.replace("\r\n", "\n").trim();
    }

    private static MetricModelResource resource(CapturingPlatformClient platformClient) {
        // Share the same in-memory state/version repo instances between the service and the publish writer
        // so reads and writes hit the same backing store (mirrors prod where Spring injects one bean each).
        InMemoryMetricModelStateRepository stateRepository = new InMemoryMetricModelStateRepository();
        InMemoryMetricModelVersionRepository versionRepository = new InMemoryMetricModelVersionRepository();
        InMemoryMetricRollbackEventRepository rollbackRepository = new InMemoryMetricRollbackEventRepository();
        MetricLifecyclePublishWriter publishWriter = new MetricLifecyclePublishWriter(stateRepository, versionRepository);
        return new MetricModelResource(
            new MetricModelLifecycleService(
                new MetricGraphDraftService(new InMemoryGraphDraftRepository()),
                platformClient,
                new MetricSecurityPolicyService(platformClient),
                new DtsMetricsProperties(),
                stateRepository,
                versionRepository,
                rollbackRepository,
                publishWriter,
                new MetricDownstreamRegistrar(platformClient, new DtsMetricsProperties(), stateRepository)
            )
        );
    }

    private static Map<String, Object> readyDwsGraph() {
        return readyDwsGraphWithDialect("");
    }

    private static Map<String, Object> readyDwsGraphWithDialect(String dialect) {
        return Map.of(
            "base",
            "dws_order_day",
            "dialect",
            dialect,
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

    private static Map<String, Object> blockedOdsGraph() {
        return Map.of(
            "base",
            "ods_order_snapshot",
            "measures",
            List.of("order_amount"),
            "dimensions",
            List.of("stat_date"),
            "nodes",
            List.of(Map.of("id", "ods_order_snapshot", "role", "BASE", "warehouseLayer", "ODS"))
        );
    }

    private static final class CapturingPlatformClient extends PlatformContractClient {

        private MetricModelValidationRequest validationRequest;
        private DbtReleaseGateRequest releaseGateRequest;
        private DbtReleaseSubmitRequest submitRequest;

        private CapturingPlatformClient() {
            super(new DtsMetricsProperties(), RestClient.builder().build());
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            // Lifecycle now enforces source-asset permission (F2-T02); the platform grants PREVIEW here.
            return new PermissionCheckResult(true, "PREVIEW", "allowed", null, request.action(), null, null, null, "INTERNAL", "platform-permission");
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            // Empty policy -> no row filter / no masking, so candidate SQL stays byte-identical to the golden file.
            return RlsPolicyResult.empty();
        }

        @Override
        public Map<String, Object> validateMetricModel(MetricModelValidationRequest request) {
            this.validationRequest = request;
            return Map.of("decision", "PASS", "valid", true);
        }

        @Override
        public Map<String, Object> checkDbtReleaseGate(DbtReleaseGateRequest request) {
            this.releaseGateRequest = request;
            return Map.of("decision", "PASS", "strictMode", true);
        }

        @Override
        public Map<String, Object> submitDbtRelease(DbtReleaseSubmitRequest request) {
            this.submitRequest = request;
            return Map.of(
                "publishReference",
                "platform-release-001",
                "decision",
                "PASS",
                "secret",
                "should-not-leak",
                "dbtProjectPath",
                "/var/lib/dts/dbt"
            );
        }
    }
}
