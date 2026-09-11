package com.yuzhi.dts.platform.service.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yuzhi.dts.platform.domain.catalog.CatalogClassificationSnapshot;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CatalogDatasetClassificationProjectionPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");

    private JdbcTemplate jdbc;
    private CatalogDatasetClassificationProjection projection;
    private final UUID sourceId = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        var dataSource = new SingleConnectionDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        jdbc = new JdbcTemplate(dataSource);
        // Deployed containers run with TZ=Asia/Shanghai, so the database session zone is not UTC.
        jdbc.execute("set time zone 'Asia/Shanghai'");
        jdbc.execute("""
            drop table if exists catalog_dataset;
            create table catalog_dataset (
                id uuid primary key, name text, source_id uuid, hive_database text, hive_table text,
                classification text, last_modified_date timestamp
            );
            """);
        projection = new CatalogDatasetClassificationProjection(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void stampsLastModifiedDateInTheUtcWallClockTheEntityReadsBack() {
        UUID datasetId = UUID.randomUUID();
        jdbc.update(
            "insert into catalog_dataset (id, name, source_id, hive_database, hive_table, classification) values (?, ?, ?, ?, ?, ?)",
            datasetId, "ods_prjdemo_project_task", sourceId, "public", "ods_prjdemo_project_task", "PUBLIC"
        );
        CatalogClassificationSnapshot snapshot = new CatalogClassificationSnapshot();
        snapshot.setSubjectType("ASSET");
        snapshot.setAssetType("DATASET");
        snapshot.setSubjectKey("source:" + sourceId + "/schema:public/table:ods_prjdemo_project_task");
        snapshot.setEffectiveLevel("INTERNAL");
        Instant before = Instant.now();

        projection.project(snapshot);

        // Same read the JPA entity performs with hibernate.jdbc.time_zone=UTC.
        Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        Timestamp stored = jdbc.queryForObject(
            "select last_modified_date from catalog_dataset where id = ?",
            (rs, row) -> rs.getTimestamp(1, utc),
            datasetId
        );
        assertThat(jdbc.queryForObject("select classification from catalog_dataset where id = ?", String.class, datasetId))
            .isEqualTo("INTERNAL");
        assertThat(Duration.between(before, stored.toInstant()).abs()).isLessThan(Duration.ofMinutes(1));
    }
}
