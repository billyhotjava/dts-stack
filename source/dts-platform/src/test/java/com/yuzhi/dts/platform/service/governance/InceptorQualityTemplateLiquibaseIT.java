package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class InceptorQualityTemplateLiquibaseIT {

    private static final List<String> CHANGELOGS = List.of(
        "config/liquibase/changelog/20260404_01_gov_quality_template.xml",
        "config/liquibase/changelog/20260404_02_gov_quality_template_seed.xml",
        "config/liquibase/changelog/20260801_13_inceptor_quality_templates.xml"
    );
    private static final List<String> EXPOSED_CODES = List.of(
        "NOT_NULL",
        "ENUM_CHECK",
        "DATE_FORMAT",
        "NUMERIC_RANGE",
        "UNIQUE_CHECK",
        "REGEX_MATCH"
    );

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.4")
        .withDatabaseName("inceptor_quality_template_it")
        .withUsername("quality_template_test")
        .withPassword("quality_template_test");

    @Test
    void everyExposedBuiltinRendersAsBoundTableInceptorSql() throws Exception {
        for (String changelog : CHANGELOGS) {
            apply(changelog);
        }
        List<TemplateRow> templates = loadEnabledBuiltins();

        assertThat(templates).extracting(TemplateRow::code).containsExactlyInAnyOrderElementsOf(EXPOSED_CODES);
        SqlTemplateRenderer renderer = new SqlTemplateRenderer();
        ObjectMapper mapper = new ObjectMapper();
        for (TemplateRow template : templates) {
            List<Map<String, Object>> schema = mapper.readValue(
                template.paramSchema(),
                new TypeReference<List<Map<String, Object>>>() {}
            );
            String rendered = renderer.render(template.sql(), parameters(template.code()), schema);

            assertThat(rendered)
                .doesNotContainIgnoringCase(" AS text")
                .doesNotContain("!~")
                .doesNotContainIgnoringCase(" AS numeric");
            if ("DATE_FORMAT".equals(template.code())) {
                assertThat(rendered).contains("RLIKE '^\\d{4}-\\d{2}-\\d{2}$'");
            }
            assertThat(QualitySqlScopeValidator.referencesOnlyBoundTable(
                rendered,
                "default",
                "ods_budget",
                "INCEPTOR"
            )).as(template.code()).isTrue();
        }
    }

    @Test
    void rollbackRestoresTheOriginalPostgresqlTemplateState() throws Exception {
        for (String changelog : CHANGELOGS) {
            apply(changelog);
        }

        rollback("config/liquibase/changelog/20260801_13_inceptor_quality_templates.xml");

        Map<String, RollbackTemplateRow> templates = loadRollbackTemplates();
        assertThat(templates).hasSize(10);
        assertThat(templates.values()).allSatisfy(template -> {
            assertThat(template.dialect()).isEqualTo("POSTGRESQL");
            assertThat(template.enabled()).isTrue();
        });
        assertThat(templates.get("NOT_NULL").sql()).contains(" AS text");
        assertThat(templates.get("DATE_FORMAT").sql()).contains("!~");
        assertThat(templates.get("UNIQUE_CHECK").paramSchema()).contains("\"columns\"");
        assertThat(templates.get("UNIQUE_CHECK").paramSchema()).doesNotContain("\"column\"");
    }

    private static Map<String, Object> parameters(String code) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("table", "default.ods_budget");
        values.put("column", "amount");
        values.put("allowed_values", "10,20");
        if (!"DATE_FORMAT".equals(code)) {
            values.put("pattern", "^[0-9]+$");
        }
        values.put("min", 0);
        values.put("max", 100);
        values.put("metric", "null_rate");
        values.put("threshold", 10);
        return values;
    }

    private static List<TemplateRow> loadEnabledBuiltins() throws SQLException {
        String sql = "select code, param_schema::text, sql_template from gov_quality_template " +
            "where builtin=true and enabled=true and dialect='INCEPTOR' order by code";
        List<TemplateRow> rows = new ArrayList<>();
        try (
            Connection connection = connection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                rows.add(new TemplateRow(resultSet.getString(1), resultSet.getString(2), resultSet.getString(3)));
            }
        }
        return rows;
    }

    private static void apply(String changelog) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.update(new Contexts(), new LabelExpression());
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private static void rollback(String changelog) throws Exception {
        try (
            ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor();
            Connection connection = connection()
        ) {
            Database database = null;
            try {
                database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
                try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                    liquibase.rollback(1, new Contexts(), new LabelExpression());
                }
            } finally {
                if (database != null && !database.getConnection().isClosed()) {
                    database.close();
                }
            }
        }
    }

    private static Map<String, RollbackTemplateRow> loadRollbackTemplates() throws SQLException {
        String sql = "select code, param_schema::text, sql_template, dialect, enabled " +
            "from gov_quality_template where builtin=true order by code";
        Map<String, RollbackTemplateRow> rows = new LinkedHashMap<>();
        try (
            Connection connection = connection();
            PreparedStatement statement = connection.prepareStatement(sql);
            ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                rows.put(
                    resultSet.getString(1),
                    new RollbackTemplateRow(
                        resultSet.getString(2),
                        resultSet.getString(3),
                        resultSet.getString(4),
                        resultSet.getBoolean(5)
                    )
                );
            }
        }
        return rows;
    }

    private static Connection connection() throws SQLException {
        Connection connection = POSTGRES.createConnection("");
        connection.setAutoCommit(true);
        return connection;
    }

    private record TemplateRow(String code, String paramSchema, String sql) {}

    private record RollbackTemplateRow(String paramSchema, String sql, String dialect, boolean enabled) {}
}
