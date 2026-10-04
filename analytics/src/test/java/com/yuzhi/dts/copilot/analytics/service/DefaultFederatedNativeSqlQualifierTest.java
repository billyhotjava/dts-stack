package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
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
class DefaultFederatedNativeSqlQualifierTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldQualifyPublicDbtTablesForFederatedPostgresCatalog() {
        when(databaseRepository.findById(9L)).thenReturn(Optional.of(federatedDatabase()));
        DefaultFederatedNativeSqlQualifier qualifier =
                new DefaultFederatedNativeSqlQualifier(databaseRepository, objectMapper);

        String sql = """
                SELECT s."销售月份", SUM(s."销售金额") AS "销售金额"
                FROM public.xycyl_ads_flowerbiz_sale_summary s
                JOIN public.xycyl_dwd_flowerbiz_main m ON m.biz_id = s.biz_id
                WHERE s."销售月份" >= '2026-01'
                GROUP BY s."销售月份"
                """;

        String qualified = qualifier.qualify(9L, sql);

        assertThat(qualified)
                .contains("FROM postgres.public.xycyl_ads_flowerbiz_sale_summary s")
                .contains("JOIN postgres.public.xycyl_dwd_flowerbiz_main m");
    }

    @Test
    void shouldLeaveSqlUntouchedWhenDatabaseIsNotFederated() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setEngine("postgres");
        database.setDetailsJson("{}");
        when(databaseRepository.findById(8L)).thenReturn(Optional.of(database));
        DefaultFederatedNativeSqlQualifier qualifier =
                new DefaultFederatedNativeSqlQualifier(databaseRepository, objectMapper);

        String sql = "SELECT COUNT(*) FROM public.xycyl_ads_flowerbiz_sale_summary";

        assertThat(qualifier.qualify(8L, sql)).isEqualTo(sql);
    }

    @Test
    void shouldLeaveAlreadyQualifiedAndNonDbtRelationsUntouched() {
        when(databaseRepository.findById(9L)).thenReturn(Optional.of(federatedDatabase()));
        DefaultFederatedNativeSqlQualifier qualifier =
                new DefaultFederatedNativeSqlQualifier(databaseRepository, objectMapper);

        String sql = """
                SELECT *
                FROM postgres.public.xycyl_ads_flowerbiz_sale_summary s
                JOIN mysql.rs_cloud_flower.inventory i ON i.id = s.biz_id
                JOIN public.other_table o ON o.id = s.biz_id
                """;

        assertThat(qualifier.qualify(9L, sql)).isEqualTo(sql);
    }

    @Test
    void shouldNormalizeBareDatetimeLiteralsForFederatedTrinoNativeSql() {
        when(databaseRepository.findById(9L)).thenReturn(Optional.of(federatedDatabase()));
        DefaultFederatedNativeSqlQualifier qualifier =
                new DefaultFederatedNativeSqlQualifier(databaseRepository, objectMapper);

        String sql = """
                SELECT f.id, f.code, f.create_time
                FROM mysql.rs_cloud_flower.t_flower_biz_info f
                WHERE f.create_time >= '2026-03-01 00:00:00'
                  AND f.create_time < '2026-04-01 00:00:00'
                  AND f.code = '2026-03-01 00:00:00'
                """;

        String qualified = qualifier.qualify(9L, sql);

        assertThat(qualified)
                .contains("f.create_time >= TIMESTAMP '2026-03-01 00:00:00'")
                .contains("f.create_time < TIMESTAMP '2026-04-01 00:00:00'")
                .contains("f.code = '2026-03-01 00:00:00'");
    }

    private static AnalyticsDatabase federatedDatabase() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setEngine("trino");
        database.setDetailsJson("""
                {
                  "queryContract": {
                    "engine": "trino",
                    "catalogs": ["postgres", "mysql"],
                    "requiresQualifiedTables": true
                  }
                }
                """);
        return database;
    }
}
