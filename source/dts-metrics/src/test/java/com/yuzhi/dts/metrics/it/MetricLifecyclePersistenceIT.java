package com.yuzhi.dts.metrics.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.metrics.config.DtsMetricsProperties;
import com.yuzhi.dts.metrics.service.MetricGraphDraftService;
import com.yuzhi.dts.metrics.service.MetricModelLifecycleService;
import com.yuzhi.dts.metrics.service.PlatformContractClient;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * F1-T05: durable persistence proof for the metric-model lifecycle.
 *
 * <p>Boots the real JPA + Liquibase stack against a Testcontainers Postgres (the changelog builds the four
 * tables, proving the DDL). Proves the in-memory {@code ConcurrentHashMap}s are truly replaced by durable
 * storage:
 *
 * <ol>
 *   <li>a graph draft survives a simulated context restart (cleared persistence context → reloaded from DB);
 *   <li>version history survives a simulated restart;
 *   <li>two concurrent publishes on one model → exactly one wins, the other gets 409
 *       {@code metric_version_conflict} (unique constraint + {@code @Version} optimistic lock);
 *   <li>a rollback event row persists and is replayed in version history.
 * </ol>
 *
 * <p>The platform is stubbed via a {@code @Primary} {@link PlatformContractClient} bean (same approach as
 * {@code MetricArtifactGenerationIT.StubPlatformContractClient}) so no real HTTP call is made.
 * {@code @Testcontainers(disabledWithoutDocker = true)} skips (rather than fails) where Docker is absent.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class MetricLifecyclePersistenceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        // Hikari auto-commit=false is fine; the suite uses TransactionTemplate for explicit boundaries.
    }

    @Autowired
    private MetricGraphDraftService graphDraftService;

    @Autowired
    private MetricModelLifecycleService lifecycleService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void graphDraftSurvivesContextRestart() throws Exception {
        Map<String, Object> created = graphDraftService.createDraft(readyDwsGraph());
        String draftId = String.valueOf(created.get("id"));

        // Simulate a restart: nothing is held in memory; prove the row is physically in Postgres.
        assertThat(rowCountById("graph_draft", draftId)).isEqualTo(1L);
        clearPersistenceContext();

        Map<String, Object> reloaded = graphDraftService.getDraft(draftId);
        assertThat(reloaded).containsEntry("id", draftId).containsEntry("status", "GRAPH_READY");
        assertThat(reloaded).containsKey("graph");
    }

    @Test
    void versionHistorySurvivesContextRestart() throws Exception {
        String modelId = "im-version-history";
        lifecycleService.validateModel(modelId, Map.of("graph", readyDwsGraph()));
        lifecycleService.publish(modelId);
        lifecycleService.validateModel(modelId, Map.of("graph", readyDwsGraph()));
        Map<String, Object> secondPublish = lifecycleService.publish(modelId);

        assertThat(secondPublish).containsEntry("version", "v2").containsEntry("previousVersion", "v1");
        assertThat(versionRowCount(modelId)).isEqualTo(2L);

        // Simulate a restart and reload purely from the database.
        clearPersistenceContext();

        Map<String, Object> history = lifecycleService.versionHistory(modelId);
        assertThat(history).containsEntry("status", "PUBLISHED").containsEntry("activeVersion", "v2");
        assertThat(asList(history.get("versions"))).hasSize(2);
        assertThat(asList(history.get("versions")).get(0)).containsEntry("version", "v1");
        assertThat(asList(history.get("versions")).get(1)).containsEntry("version", "v2");
    }

    @Test
    void concurrentPublishOnOneModelLetsExactlyOneWinAndConflictsTheOther() throws Exception {
        String modelId = "im-concurrent-publish";
        lifecycleService.validateModel(modelId, Map.of("graph", readyDwsGraph()));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Outcome> publishTask = () -> {
                try {
                    lifecycleService.publish(modelId);
                    return Outcome.WON;
                } catch (ResponseStatusException e) {
                    return e.getStatusCode().value() == 409 && String.valueOf(e.getReason()).contains("metric_version_conflict")
                        ? Outcome.CONFLICT
                        : Outcome.OTHER_ERROR;
                } catch (RuntimeException e) {
                    // Some optimistic-lock paths surface as a wrapped runtime exception; treat any
                    // version-conflict signal as the expected loser.
                    return String.valueOf(e.getMessage()).contains("metric_version_conflict") ? Outcome.CONFLICT : Outcome.OTHER_ERROR;
                }
            };

            Future<Outcome> first = pool.submit(publishTask);
            Future<Outcome> second = pool.submit(publishTask);
            List<Outcome> outcomes = List.of(first.get(), second.get());

            long winners = outcomes.stream().filter(o -> o == Outcome.WON).count();
            long conflicts = outcomes.stream().filter(o -> o == Outcome.CONFLICT).count();
            assertThat(winners).as("exactly one publish wins").isEqualTo(1L);
            assertThat(conflicts).as("the other publish gets 409 metric_version_conflict").isEqualTo(1L);
        } finally {
            pool.shutdownNow();
        }

        // Exactly one v1 row exists for this model despite two concurrent attempts.
        assertThat(versionRowCount(modelId)).isEqualTo(1L);
    }

    @Test
    void rollbackEventPersists() throws Exception {
        String modelId = "im-rollback";
        lifecycleService.validateModel(modelId, Map.of("graph", readyDwsGraph()));
        lifecycleService.publish(modelId);
        lifecycleService.validateModel(modelId, Map.of("graph", readyDwsGraph()));
        lifecycleService.publish(modelId);

        Map<String, Object> rollback = lifecycleService.rollback(modelId, Map.of("reason", "downstream regression"));
        assertThat(rollback)
            .containsEntry("status", "ROLLED_BACK")
            .containsEntry("rollbackFromVersion", "v2")
            .containsEntry("rollbackToVersion", "v1")
            .containsEntry("version", "rollback-1");

        assertThat(rollbackEventRowCount(modelId)).isEqualTo(1L);

        // Reload from the DB and confirm the event is replayed in history.
        clearPersistenceContext();
        Map<String, Object> history = lifecycleService.versionHistory(modelId);
        assertThat(history).containsEntry("status", "ROLLED_BACK").containsEntry("activeVersion", "v1");
        assertThat(asList(history.get("rollbackEvents"))).hasSize(1);
        assertThat(asList(history.get("rollbackEvents")).get(0))
            .containsEntry("version", "rollback-1")
            .containsEntry("reason", "downstream regression");
    }

    private void clearPersistenceContext() {
        transactionTemplate.executeWithoutResult(status -> entityManager.clear());
    }

    private long versionRowCount(String modelId) throws Exception {
        return rowCountForModel("metric_model_version", modelId);
    }

    private long rollbackEventRowCount(String modelId) throws Exception {
        return rowCountForModel("metric_rollback_event", modelId);
    }

    /**
     * Count rows directly in Postgres (bypassing JPA caches) to prove durability. All IT methods share one
     * Spring context + database, so counts are scoped by {@code model_id} to stay order-independent.
     */
    private long rowCountForModel(String table, String modelId) throws Exception {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("select count(*) from " + table + " where model_id = ?")) {
                statement.setString(1, modelId);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        }
    }

    private long rowCountById(String table, String id) throws Exception {
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("select count(*) from " + table + " where id = ?")) {
                statement.setString(1, id);
                try (var resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return resultSet.getLong(1);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asList(Object value) {
        return (List<Map<String, Object>>) value;
    }

    private static Map<String, Object> readyDwsGraph() {
        return Map.of(
            "base",
            "dws_order_day",
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

    private enum Outcome {
        WON,
        CONFLICT,
        OTHER_ERROR
    }

    /**
     * Stubs the platform contract so validate/publish pass without a live dts-platform. {@code @Primary}
     * overrides the real {@code @Component} bean. Mirrors {@code MetricArtifactGenerationIT}'s stub.
     */
    @TestConfiguration
    static class StubPlatformConfig {

        @Bean
        @Primary
        PlatformContractClient stubPlatformContractClient() {
            return new StubPlatformContractClient();
        }
    }

    private static final class StubPlatformContractClient extends PlatformContractClient {

        private StubPlatformContractClient() {
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
        public Map<String, Object> checkDbtReleaseGate(DbtReleaseGateRequest request) {
            return Map.of("decision", "PASS", "strictMode", true);
        }

        @Override
        public Map<String, Object> submitDbtRelease(DbtReleaseSubmitRequest request) {
            return Map.of("publishReference", "platform-release-it", "decision", "PASS");
        }
    }
}
