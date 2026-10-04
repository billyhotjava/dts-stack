package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FederatedQueryGuardrailTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsQualifiedCrossCatalogJoinWithLimitAndReturnsSourceMetadata() {
        AnalyticsDatabase database = federatedDatabase();
        when(databaseRepository.findById(18L)).thenReturn(Optional.of(database));
        FederatedQueryGuardrail guardrail = new FederatedQueryGuardrail(databaseRepository, objectMapper);

        FederatedQueryGuardrail.ValidationResult result = guardrail.validate(
                18L,
                "native",
                """
                SELECT pg.pg_rows, my.mysql_rows
                FROM (
                    SELECT count(*) AS pg_rows
                    FROM postgres.public.xycyl_dwd_flowerbiz_main
                ) pg
                JOIN (
                    SELECT count(*) AS mysql_rows
                    FROM mysql.rs_cloud_flower.t_flower_biz_info
                ) my ON true
                LIMIT 1
                """);

        assertThat(result.federated()).isTrue();
        assertThat(result.catalogs()).containsExactly("mysql", "postgres");
        assertThat(result.relations())
                .containsExactly(
                        "postgres.public.xycyl_dwd_flowerbiz_main",
                        "mysql.rs_cloud_flower.t_flower_biz_info");
        assertThat(result.toResponseMetadata())
                .containsEntry("engine", "trino")
                .containsEntry("catalogs", result.catalogs())
                .containsEntry("federated", true);
    }

    @Test
    void rejectsFederatedJoinWhenTablesAreNotCatalogQualified() {
        AnalyticsDatabase database = federatedDatabase();
        when(databaseRepository.findById(18L)).thenReturn(Optional.of(database));
        FederatedQueryGuardrail guardrail = new FederatedQueryGuardrail(databaseRepository, objectMapper);

        assertThatThrownBy(() -> guardrail.validate(
                        18L,
                        "native",
                        "SELECT * FROM xycyl_dwd_flowerbiz_main p JOIN t_flower_biz_info m ON true LIMIT 1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalog.schema.table");
    }

    @Test
    void rejectsCrossCatalogJoinWithoutWhereOrLimit() {
        AnalyticsDatabase database = federatedDatabase();
        when(databaseRepository.findById(18L)).thenReturn(Optional.of(database));
        FederatedQueryGuardrail guardrail = new FederatedQueryGuardrail(databaseRepository, objectMapper);

        assertThatThrownBy(() -> guardrail.validate(
                        18L,
                        "native",
                        """
                        SELECT *
                        FROM postgres.public.xycyl_dwd_flowerbiz_main p
                        JOIN mysql.rs_cloud_flower.t_flower_biz_info m ON p.biz_id = m.id
                        """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WHERE 或 LIMIT");
    }

    @Test
    void acceptsQualifiedSingleCatalogJoinForBusinessObjectQueries() {
        AnalyticsDatabase database = federatedDatabase();
        when(databaseRepository.findById(18L)).thenReturn(Optional.of(database));
        FederatedQueryGuardrail guardrail = new FederatedQueryGuardrail(databaseRepository, objectMapper);

        FederatedQueryGuardrail.ValidationResult result = guardrail.validate(
                18L,
                "native",
                """
                SELECT a.good_name, COUNT(*) AS row_count
                FROM mysql.rs_cloud_flower.t_purchase_price_item a
                LEFT JOIN mysql.rs_cloud_flower.t_purchase_info b ON a.purchase_info_id = b.id
                WHERE a.purchase_time >= TIMESTAMP '2026-01-01 00:00:00'
                GROUP BY a.good_name
                LIMIT 100
                """);

        assertThat(result.federated()).isTrue();
        assertThat(result.catalogs()).containsExactly("mysql");
        assertThat(result.relations())
                .containsExactly(
                        "mysql.rs_cloud_flower.t_purchase_price_item",
                        "mysql.rs_cloud_flower.t_purchase_info");
    }

    @Test
    void skipsNonFederatedDatabase() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setEngine("postgres");
        database.setDetailsJson("{}");
        when(databaseRepository.findById(7L)).thenReturn(Optional.of(database));
        FederatedQueryGuardrail guardrail = new FederatedQueryGuardrail(databaseRepository, objectMapper);

        FederatedQueryGuardrail.ValidationResult result =
                guardrail.validate(7L, "native", "SELECT * FROM public.xycyl_dwd_flowerbiz_main");

        assertThat(result.federated()).isFalse();
        assertThat(result.catalogs()).isEmpty();
    }

    private static AnalyticsDatabase federatedDatabase() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setEngine("trino");
        database.setDetailsJson("""
                {
                  "federatedQuery": true,
                  "queryContract": {
                    "engine": "trino",
                    "catalogs": ["postgres", "mysql"],
                    "requiresQualifiedTables": true,
                    "joinRequiresWhereOrLimit": true
                  }
                }
                """);
        return database;
    }
}
