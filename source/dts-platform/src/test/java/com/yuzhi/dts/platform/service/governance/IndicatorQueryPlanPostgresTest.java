package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.service.governance.IndicatorAnalysisContract.*;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Isolated data verifies arithmetic and joins rather than mirroring generated SQL text. */
@Testcontainers
class IndicatorQueryPlanPostgresTest {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        org.testcontainers.utility.DockerImageName.parse(System.getProperty("f6.postgres.image", "postgres:16-alpine")).asCompatibleSubstituteFor("postgres"));
    private final Map<VersionRef, GovIndicatorDefinition> definitions = new LinkedHashMap<>();
    private final UUID sourceId = UUID.randomUUID();

    @Test void computesRatioOfSumsAndKeepsMissingAndZeroDenominatorsDistinct() throws Exception {
        var amount = metric("AMOUNT"); var orders = metric("ORDERS"); var ratio = ratio(amount, orders);
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE amount(region text, value numeric)");
            statement.execute("CREATE TEMP TABLE orders(region text, value numeric)");
            statement.execute("INSERT INTO amount VALUES ('east',90),('east',10),('west',900),('zero',5),('missing',7)");
            statement.execute("INSERT INTO orders VALUES ('east',1),('east',1),('west',100),('zero',0)");
            var grouped = execute(connection, ratio, List.of("region"), List.of());
            assertThat(grouped.get("east").get("metric_0").toString()).startsWith("50.");
            assertThat(grouped.get("west").get("metric_0").toString()).startsWith("9.");
            assertThat(grouped.get("zero").get("metric_0")).isNull();
            assertThat(grouped.get("zero").get("metric_0_null_reason")).isEqualTo("ZERO_DENOMINATOR");
            assertThat(grouped.get("missing").get("metric_0_null_reason")).isEqualTo("MISSING_DEPENDENCY_GROUP");
            var overall = execute(connection, ratio, List.of(), List.of(new Predicate("region", "IN", List.of("east", "west"))));
            assertThat(new java.math.BigDecimal(overall.get("all").get("metric_0").toString()))
                .isCloseTo(new java.math.BigDecimal("9.8039215686"), org.assertj.core.data.Offset.offset(new java.math.BigDecimal("0.000000001")));
            var empty = execute(connection, ratio, List.of(), List.of(new Predicate("region", "EQ", "no-data' OR 1=1 --")));
            assertThat(empty.get("all").get("metric_0")).isNull();
        }
    }

    @Test void flagsDuplicatePrecomputedRowsInsteadOfReturningAnApparentlyValidMaximum() throws Exception {
        var ref = metric("SCORES"); var definition = definitions.get(ref);
        definition.setMetricType("DERIVED"); definition.setExecutionMode("PRECOMPUTED");
        try (var connection = connection(); var statement = connection.createStatement()) {
            statement.execute("CREATE TEMP TABLE scores(region text, value numeric)");
            statement.execute("INSERT INTO scores VALUES ('east',1),('east',99)");
            assertThat(execute(connection, ref, List.of("region"), List.of()).get("east").get("metric_0_invalid")).isEqualTo(true);
        }
    }

    private Connection connection() throws SQLException { return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()); }
    private Map<String, Map<String, Object>> execute(Connection connection, VersionRef ref, List<String> dimensions, List<Predicate> filters) throws Exception {
        var plan = new IndicatorQueryPlan(new Query(List.of(ref), null, dimensions, filters, 100), definitions::get,
            definition -> new IndicatorQueryPlan.Source(sourceId, "pg_temp", definition.getCode().toLowerCase(Locale.ROOT), Set.of("region", "value")),
            new ControlledIndicatorDerivationCompiler()).build();
        try (var statement = connection.prepareStatement(plan.sql())) {
            for (int i = 0; i < plan.parameters().size(); i++) statement.setObject(i + 1, plan.parameters().get(i));
            try (var rows = statement.executeQuery()) {
                Map<String, Map<String, Object>> results = new LinkedHashMap<>();
                while (rows.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) row.put(rows.getMetaData().getColumnLabel(i), rows.getObject(i));
                    results.put(dimensions.isEmpty() ? "all" : String.valueOf(row.get("region")), row);
                }
                return results;
            }
        }
    }
    private VersionRef metric(String code) {
        var ref = new VersionRef(UUID.randomUUID(), "v1"); var value = new GovIndicatorDefinition();
        value.setId(ref.id()); value.setCode(code); value.setVersion("v1"); value.setMetricType("ATOMIC"); value.setMeasureField("value"); value.setAggregationType("SUM");
        value.setAnalysisConfig(IndicatorMapper.writeAnalysisConfig(new Config(Map.of("region", "region"), null, List.of("region"), List.of("SUM"), List.of(), List.of(), null, null, false)));
        definitions.put(ref, value); return ref;
    }
    private VersionRef ratio(VersionRef left, VersionRef right) throws Exception {
        var ref = metric("RATIO"); var value = definitions.get(ref); value.setMetricType("DERIVED"); value.setExecutionMode("FORMULA");
        value.setSourceRefs(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(
            Map.of("sourceType", "INDICATOR_VERSION", "sourceId", left.id().toString(), "sourceVersion", "v1"),
            Map.of("sourceType", "INDICATOR_VERSION", "sourceId", right.id().toString(), "sourceVersion", "v1"))));
        value.setExpressionSql("{{metric:AMOUNT}} / {{metric:ORDERS}}"); return ref;
    }
}
