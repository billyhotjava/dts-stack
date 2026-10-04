package com.yuzhi.dts.copilot.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabaseRole;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsDatabaseAliasResolverTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesThroughPositiveNumericDatabaseId() {
        AnalyticsDatabaseAliasResolver resolver =
                new AnalyticsDatabaseAliasResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveDatabaseId(8L)).isEqualTo(8L);
        assertThat(resolver.resolveDatabaseId("8")).isEqualTo(8L);
    }

    @Test
    void resolvesLogicalAliasFromDatabaseDetails() {
        AnalyticsDatabase dbtMart = database(
                8L,
                "DTS dbt模型库",
                AnalyticsDatabaseRole.BUSINESS_SECONDARY,
                """
                        {
                          "dataSourceId": 17,
                          "logicalSourceAliases": ["prs.flowerbiz.federated", "prs.flowerbiz.mart"]
                        }
                        """);
        when(databaseRepository.findAll()).thenReturn(List.of(dbtMart));

        AnalyticsDatabaseAliasResolver resolver =
                new AnalyticsDatabaseAliasResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveDatabaseId("prs.flowerbiz.federated")).isEqualTo(8L);
    }

    @Test
    void prefersBusinessPrimaryWhenAliasIsAdvertisedByMultipleWarehouses() {
        AnalyticsDatabase dbtMart = database(
                8L,
                "DTS dbt模型库",
                AnalyticsDatabaseRole.BUSINESS_SECONDARY,
                """
                        {"logicalSourceAliases": ["prs.flowerbiz.federated"]}
                        """);
        AnalyticsDatabase federated = database(
                21L,
                "联邦查询入口",
                AnalyticsDatabaseRole.BUSINESS_PRIMARY,
                """
                        {"logicalSourceAliases": ["prs.flowerbiz.federated"]}
                        """);
        when(databaseRepository.findAll()).thenReturn(List.of(dbtMart, federated));

        AnalyticsDatabaseAliasResolver resolver =
                new AnalyticsDatabaseAliasResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveDatabaseId("prs.flowerbiz.federated")).isEqualTo(21L);
    }

    @Test
    void rejectsMissingLogicalAliasWithBusinessReadableMessage() {
        when(databaseRepository.findAll()).thenReturn(List.of());

        AnalyticsDatabaseAliasResolver resolver =
                new AnalyticsDatabaseAliasResolver(databaseRepository, objectMapper);

        assertThatThrownBy(() -> resolver.resolveDatabaseId("prs.flowerbiz.federated"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未配置数据源别名")
                .hasMessageContaining("prs.flowerbiz.federated");
    }

    private static AnalyticsDatabase database(
            Long id,
            String name,
            AnalyticsDatabaseRole role,
            String detailsJson) {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(id);
        database.setName(name);
        database.setEngine("postgres");
        database.setDatabaseRole(role);
        database.setDetailsJson(detailsJson);
        return database;
    }
}
