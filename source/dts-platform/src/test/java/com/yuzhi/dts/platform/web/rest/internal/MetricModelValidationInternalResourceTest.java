package com.yuzhi.dts.platform.web.rest.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.etl.DbtReleaseGateService;
import com.yuzhi.dts.platform.service.metrics.MetricModelValidationService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricModelValidationInternalResourceTest {

    @Test
    void validateModelReturnsPassWithoutLeakingBuildEvidencePath() {
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        when(releaseGateService.evaluate("dws_order_summary", null, null, true))
            .thenReturn(
                new DbtReleaseGateService.DbtReleaseGateResult(
                    "dws_order_summary",
                    true,
                    null,
                    null,
                    "PASS",
                    false,
                    false,
                    List.of(),
                    List.of(),
                    new DbtReleaseGateService.BuildEvidence("inv-1", "dbt build --select dws_order_summary", "SUCCESS", "2026-06-07T00:00:00Z", "/host/dbt/run_results.json")
                )
            );
        MetricModelValidationInternalResource resource = resource(releaseGateService);

        MetricModelValidationService.ValidationResult result = resource.validateModel(request("select 1 as metric_ready"));

        assertThat(result.valid()).isTrue();
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(result.status()).isEqualTo("DBT_VALIDATED");
        assertThat(result.releaseGate().toString()).contains("inv-1").doesNotContain("/host/dbt/run_results.json");
    }

    @Test
    void validateModelBlocksUnsafeSqlBeforeReleaseGate() {
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        MetricModelValidationInternalResource resource = resource(releaseGateService);

        MetricModelValidationService.ValidationResult result = resource.validateModel(request("drop table dws_order_summary"));

        assertThat(result.valid()).isFalse();
        assertThat(result.decision()).isEqualTo("BLOCK");
        assertThat(result.diagnostics()).anySatisfy(item -> assertThat(item.code()).isEqualTo("artifact_sql_unsafe"));
        verify(releaseGateService, never()).evaluate("dws_order_summary", null, null, true);
    }

    @Test
    void validateModelRequiresSecuritySnapshotBeforeReleaseGate() {
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        MetricModelValidationInternalResource resource = resource(releaseGateService);

        MetricModelValidationService.ValidationResult result = resource.validateModel(requestWithoutSecuritySnapshot());

        assertThat(result.valid()).isFalse();
        assertThat(result.decision()).isEqualTo("BLOCK");
        assertThat(result.diagnostics()).anySatisfy(item -> assertThat(item.code()).isEqualTo("artifact_required"));
        verify(releaseGateService, never()).evaluate("dws_order_summary", null, null, true);
    }

    @Test
    void validateModelBlocksSecurityHashMismatchBeforeReleaseGate() {
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        MetricModelValidationInternalResource resource = resource(releaseGateService);

        MetricModelValidationService.ValidationResult result = resource.validateModel(requestWithSecurityHash("sha256:other"));

        assertThat(result.valid()).isFalse();
        assertThat(result.decision()).isEqualTo("BLOCK");
        assertThat(result.diagnostics()).anySatisfy(item -> assertThat(item.code()).isEqualTo("security_hash_mismatch"));
        verify(releaseGateService, never()).evaluate("dws_order_summary", null, null, true);
    }

    @Test
    void validateModelMapsReleaseGateBlockToDbtValidationFailure() {
        DbtReleaseGateService releaseGateService = mock(DbtReleaseGateService.class);
        when(releaseGateService.evaluate("dws_order_summary", null, null, true))
            .thenReturn(
                new DbtReleaseGateService.DbtReleaseGateResult(
                    "dws_order_summary",
                    true,
                    null,
                    null,
                    "BLOCK",
                    true,
                    false,
                    List.of("未发现可用构建记录"),
                    List.of(),
                    null
                )
            );
        MetricModelValidationInternalResource resource = resource(releaseGateService);

        MetricModelValidationService.ValidationResult result = resource.validateModel(request("select 1 as metric_ready"));

        assertThat(result.valid()).isFalse();
        assertThat(result.status()).isEqualTo("DBT_VALIDATION_FAILED");
        assertThat(result.diagnostics()).anySatisfy(item -> assertThat(item.code()).isEqualTo("dbt_validation_failed"));
        assertThat(result.releaseGate()).containsEntry("decision", "BLOCK").containsEntry("blocking", true);
    }

    private static MetricModelValidationInternalResource resource(DbtReleaseGateService releaseGateService) {
        return new MetricModelValidationInternalResource(new MetricModelValidationService(releaseGateService));
    }

    private static MetricModelValidationService.MetricModelValidationRequest request(String sql) {
        return requestWithSecurityHash(sql, "sha256:abc123");
    }

    private static MetricModelValidationService.MetricModelValidationRequest requestWithSecurityHash(String predicateHash) {
        return requestWithSecurityHash("select 1 as metric_ready", predicateHash);
    }

    private static MetricModelValidationService.MetricModelValidationRequest requestWithSecurityHash(String sql, String predicateHash) {
        return new MetricModelValidationService.MetricModelValidationRequest(
            "order-summary",
            "dws_order_summary",
            "candidate://dts-metrics/dws_order_summary",
            Map.of("base", "dws_order_day"),
            artifacts(sql, Map.of("policySource", "platform-policy-required", "predicateHash", predicateHash)),
            "platform-policy-required",
            "sha256:abc123"
        );
    }

    private static MetricModelValidationService.MetricModelValidationRequest requestWithoutSecuritySnapshot() {
        return new MetricModelValidationService.MetricModelValidationRequest(
            "order-summary",
            "dws_order_summary",
            "candidate://dts-metrics/dws_order_summary",
            Map.of("base", "dws_order_day"),
            artifacts("select 1 as metric_ready", null),
            "platform-policy-required",
            "sha256:abc123"
        );
    }

    private static Map<String, Object> artifacts(String sql, Map<String, String> securitySnapshot) {
        Map<String, Object> artifacts = new java.util.LinkedHashMap<>();
        artifacts.put("dbtModelSql", sql);
        artifacts.put("schemaYml", "version: 2");
        artifacts.put("lineageHint", Map.of("upstreamAsset", "dws_order_day"));
        if (securitySnapshot != null) {
            artifacts.put("securitySnapshot", securitySnapshot);
        }
        return artifacts;
    }
}
