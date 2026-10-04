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
class CopilotChatDataSourceResolverTest {

    @Mock
    private AnalyticsDatabaseRepository databaseRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void resolvesLinkedAiDatasourceIdFromAnalyticsDatabase() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setDetailsJson("{\"dataSourceId\":91}");
        when(databaseRepository.findById(7L)).thenReturn(Optional.of(database));

        CopilotChatDataSourceResolver resolver =
                new CopilotChatDataSourceResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveSelectedDatasourceId("7")).isEqualTo(91L);
    }

    @Test
    void resolvesDedicatedCopilotDatasourceIdWithoutOverridingJdbcDetails() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(8L);
        database.setDetailsJson("""
                {
                  "jdbc-url": "jdbc:postgresql://${DTS_DBT_DB_HOST:host.docker.internal}:5432/biadmin",
                  "copilotDataSourceId": 92
                }
                """);
        when(databaseRepository.findById(8L)).thenReturn(Optional.of(database));

        CopilotChatDataSourceResolver resolver =
                new CopilotChatDataSourceResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveSelectedDatasourceId("8")).isEqualTo(92L);
    }

    @Test
    void rejectsAnalyticsDatabaseWithoutLinkedAiDatasource() {
        AnalyticsDatabase database = new AnalyticsDatabase();
        database.setId(7L);
        database.setDetailsJson("{\"platformDataSourceId\":\"11be00c6-8656-4f25-9438-918c6c4f2388\"}");
        when(databaseRepository.findById(7L)).thenReturn(Optional.of(database));

        CopilotChatDataSourceResolver resolver =
                new CopilotChatDataSourceResolver(databaseRepository, objectMapper);

        assertThatThrownBy(() -> resolver.resolveSelectedDatasourceId("7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not have a linked copilot datasource");
    }

    @Test
    void passesThroughRawDatasourceIdWhenAnalyticsDatabaseIsAbsent() {
        when(databaseRepository.findById(91L)).thenReturn(Optional.empty());

        CopilotChatDataSourceResolver resolver =
                new CopilotChatDataSourceResolver(databaseRepository, objectMapper);

        assertThat(resolver.resolveSelectedDatasourceId("91")).isEqualTo(91L);
    }
}
