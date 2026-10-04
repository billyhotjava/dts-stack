package com.yuzhi.dts.metrics.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.MetricArtifactGenerationService;
import com.yuzhi.dts.metrics.service.MetricFormulaSqlGenerator;
import com.yuzhi.dts.metrics.service.MetricPackValidationService;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import com.yuzhi.dts.metrics.service.dto.MetricArtifactPreviewResult;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class MetricArtifactGenerationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void postgresExecutesRlsAndMaskingCandidateSql() throws Exception {
        MetricArtifactPreviewResult preview = serviceWithPolicy(allowedPlatform()).preview(
            flowerRentalManifest(),
            new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL")
        );

        assertThat(preview.valid()).isTrue();
        String postgresSql = renderForPostgres(preview.artifacts().get("dbtModelSql"));
        assertThat(postgresSql).isEqualTo(golden("golden-sql/flower-rental-postgres.sql"));

        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var statement = connection.createStatement()) {
                statement.execute("""
                    create table dwd_flower_contract_detail (
                        stat_month varchar(16),
                        dept_code varchar(32),
                        customer_phone varchar(64),
                        contract_amount numeric(18, 2)
                    )
                    """);
                statement.execute("""
                    insert into dwd_flower_contract_detail(stat_month, dept_code, customer_phone, contract_amount)
                    values
                      ('2026-05', 'D01', '13800000001', 100.00),
                      ('2026-05', 'D02', '13800000002', 250.00)
                    """);
            }

            try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(postgresSql)) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("stat_month")).isEqualTo("2026-05");
                assertThat(resultSet.getString("customer_phone")).isNull();
                assertThat(resultSet.getBigDecimal("contract_amount")).isEqualByComparingTo(new BigDecimal("100.00"));
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    @Test
    void dorisDialectCompilerAcceptsRlsAndMaskingCandidateSql() throws Exception {
        MetricArtifactPreviewResult preview = serviceWithPolicy(allowedPlatform()).preview(
            flowerRentalManifest(),
            new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "INTERNAL")
        );

        assertThat(preview.valid()).isTrue();
        String dorisSql = renderForDoris(preview.artifacts().get("dbtModelSql"));
        assertThat(dorisSql).isEqualTo(golden("golden-sql/flower-rental-doris.sql"));
        assertThat(dorisSql)
            .doesNotContain("{{")
            .doesNotContain("}}")
            .doesNotContain("::")
            .doesNotContain("nulls last");
    }

    @Test
    void unauthorizedActorGetsNoArtifactAndNoAssetNameLeak() {
        MetricArtifactPreviewResult preview = serviceWithPolicy(deniedPlatform()).preview(
            flowerRentalManifest(),
            new MetricArtifactGenerationService.PreviewActor("ptrdemo", List.of("ROLE_PTR"), "D01", "PUBLIC")
        );

        assertThat(preview.valid()).isFalse();
        assertThat(preview.artifacts()).isEmpty();
        assertThat(preview.errors()).containsExactly("platform asset permission check failed before artifact preview");
        assertThat(String.join("\n", preview.errors())).doesNotContain("dwd_flower_contract_detail");
    }

    private static MetricArtifactGenerationService serviceWithPolicy(PlatformContractClient platform) {
        return new MetricArtifactGenerationService(
            new MetricPackValidationService(),
            new MetricFormulaSqlGenerator(),
            platform
        );
    }

    private static PlatformContractClient allowedPlatform() {
        return new StubPlatformContractClient(true);
    }

    private static PlatformContractClient deniedPlatform() {
        return new StubPlatformContractClient(false);
    }

    private static String renderForPostgres(String dbtSql) {
        return stripDbtSyntax(dbtSql)
            .replace("{{ dts_mask('customer_phone') }}", "cast(null as varchar)")
            .trim();
    }

    private static String renderForDoris(String dbtSql) {
        return stripDbtSyntax(dbtSql)
            .replace("{{ dts_mask('customer_phone') }}", "cast(null as string)")
            .trim();
    }

    private static String stripDbtSyntax(String dbtSql) {
        return dbtSql
            .replace("{{ config(materialized='table', tags=['dts-metrics', 'metric-pack']) }}\n\n", "")
            .replace("-- Candidate artifact generated by dts-metrics. Review through platform/dbt release gate before publishing.\n", "")
            .replace("-- dts-platform RLS: platform-row-filter+masking\n", "")
            .replace("{{ ref('dwd_flower_contract_detail') }}", "dwd_flower_contract_detail");
    }

    private static String golden(String path) throws Exception {
        return StreamUtils.copyToString(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8).trim();
    }

    private static String flowerRentalManifest() {
        return """
            pack_id: flower-rental
            pack_name: Flower Rental Metrics
            version: 0.1.0
            industry: flower_rental
            edition_required: professional
            tenant_namespace: flowerbiz
            security:
              apply_rls: true
            source_model: dwd_flower_contract_detail
            dimensions:
              - stat_month
              - customer_phone
            metrics:
              - metric_code: contract_amount
                metric_name: Contract Amount
                term_ids:
                  - glossary.contract_amount
                formula:
                  type: aggregation
                  aggregation: sum
                  field: contract_amount
            files:
              domains: domains.yml
              dimensions: dimensions.yml
              metrics: metrics.yml
              models: models.yml
              datasets: datasets.yml
            dependencies:
              platform_assets:
                - type: DATASET
                  id: dwd_flower_contract_detail
                  asset_classification: INTERNAL
                - type: GLOSSARY_TERM
                  id: glossary.contract_amount
            """;
    }

    private static final class StubPlatformContractClient extends PlatformContractClient {

        private final boolean allowed;

        private StubPlatformContractClient(boolean allowed) {
            super(new DtsMetricsProperties(), RestClient.builder().build());
            this.allowed = allowed;
        }

        @Override
        public PermissionCheckResult checkPermission(PermissionCheckRequest request) {
            if (!allowed) {
                return PermissionCheckResult.denied("classification_denied");
            }
            return new PermissionCheckResult(
                true,
                "READ",
                "explicit_grant",
                "READ",
                request.action(),
                request.asset().type(),
                request.asset().id(),
                request.asset().key(),
                "ALLOWED",
                "explicit_grant"
            );
        }

        @Override
        public RlsPolicyResult resolveRlsPolicy(RlsPolicyRequest request) {
            return new RlsPolicyResult(
                true,
                List.of("dept_code = 'D01'"),
                List.of("customer_phone"),
                "platform-row-filter+masking"
            );
        }

        @Override
        public void recordPolicyInjection(PolicyInjectionAuditRequest request) {
            // Platform provider audit is tested in dts-platform; this IT focuses on generated SQL execution.
        }

        @Override
        public GlossaryResolveResult resolveGlossaryTerms(List<String> refs) {
            return new GlossaryResolveResult(
                refs.stream().map(ref -> new GlossaryTermContract(ref, null, ref, ref, "ACTIVE", true)).toList(),
                List.of(),
                List.of(),
                List.of()
            );
        }

        @Override
        public DomainResolveResult resolveDomains(List<String> refs) {
            return new DomainResolveResult(
                refs.stream().map(ref -> new DomainContract(ref, null, ref, ref, null)).toList(),
                List.of(),
                List.of()
            );
        }

        @Override
        public DataStandardResolveResult resolveDataStandards(List<String> refs) {
            return DataStandardResolveResult.empty();
        }
    }
}
