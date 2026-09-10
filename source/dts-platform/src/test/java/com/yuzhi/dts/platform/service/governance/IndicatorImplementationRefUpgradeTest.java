package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class IndicatorImplementationRefUpgradeTest {
    private static final String ORIGINAL = "config/liquibase/changelog/20260910_01_indicator_implementation_ref.xml";
    private static final String EXPAND = "config/liquibase/changelog/20260910_02_indicator_analysis_expand.xml";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    @ParameterizedTest
    @ValueSource(strings = { "fresh", "legacy", "expanded" })
    void upgradesBothShippedChecksumsAndFreshInstallsWithoutLosingData(String variant) throws Exception {
        execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
        execute("""
            CREATE TABLE gov_indicator_definition (id uuid PRIMARY KEY, metric_type varchar(32), category varchar(32));
            CREATE TABLE gov_indicator_reference (indicator_id uuid, ref_target text, ref_type varchar(32));
            CREATE TABLE gov_indicator_run (id uuid PRIMARY KEY);
            CREATE TABLE gov_indicator_version (indicator_id uuid, version varchar(32), status varchar(32));
            INSERT INTO gov_indicator_definition VALUES ('00000000-0000-0000-0000-000000000001','ATOMIC','BUSINESS');
            INSERT INTO gov_indicator_version VALUES ('00000000-0000-0000-0000-000000000001','v1','PUBLISHED');
            INSERT INTO gov_indicator_run VALUES ('00000000-0000-0000-0000-000000000002');
            """);
        if (!"fresh".equals(variant)) {
            update("config/liquibase/indicator-ref-upgrade/" + variant + ".xml");
            assertThat(scalar("SELECT md5sum FROM databasechangelog"))
                .isEqualTo("legacy".equals(variant)
                    ? "9:88b5abfb3cb9d746cb07ee0fdd3de989"
                    : "9:701bf26bcae14951cf1cc35d6f3d6421");
        }
        update(ORIGINAL);
        update(EXPAND);
        update(ORIGINAL);
        update(EXPAND);
        assertThat(scalar("""
            SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND
            ((table_name='gov_indicator_definition' AND column_name='analysis_config') OR
             (table_name='gov_indicator_run' AND column_name IN ('query_id','source_mode','null_reason','data_as_of')))
            """)).isEqualTo("5");
        assertThat(scalar("SELECT count(*) FROM modeling_catalog_indicator_serving_projection WHERE sync_status='SYNC_PENDING'"))
            .isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM gov_indicator_run")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM gov_indicator_definition")).isEqualTo("1");
        assertThat(scalar("SELECT count(*) FROM databasechangelog")).isEqualTo("3");
    }

    private void update(String changelog) throws Exception {
        try (Connection connection = POSTGRES.createConnection("");
             ClassLoaderResourceAccessor resources = new ClassLoaderResourceAccessor()) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase(changelog, resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        }
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery(sql)) {
            assertThat(rows.next()).isTrue();
            return rows.getString(1);
        }
    }
}
