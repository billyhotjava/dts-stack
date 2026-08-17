package com.yuzhi.dts.analytics.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.analytics.repository.AnalyticsDatabaseRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsDatabaseBindingResolver {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsDatabaseBindingResolver(
        AnalyticsDatabaseRepository databaseRepository,
        ObjectMapper objectMapper
    ) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    public long requireDatabaseId(UUID platformDataSourceId) {
        if (platformDataSourceId == null) {
            throw new AnalysisConflictException("ANALYSIS_DATASOURCE_REQUIRED", "published dataset has no source datasource");
        }
        return databaseRepository
            .findAll()
            .stream()
            .filter(database -> platformDataSourceId.equals(platformDataSourceId(database)))
            .map(AnalyticsDatabase::getId)
            .filter(id -> id != null && id > 0)
            .findFirst()
            .orElseThrow(() ->
                new AnalysisConflictException(
                    "ANALYSIS_DATASOURCE_NOT_REGISTERED",
                    "published dataset datasource is not registered in analytics runtime"
                )
            );
    }

    private UUID platformDataSourceId(AnalyticsDatabase database) {
        try {
            JsonNode details = objectMapper.readTree(database.getDetailsJson());
            String value = text(details, "platformDataSourceId", "platform_data_source_id", "platformDataSourceID");
            if (value == null && details != null && details.path("platform").isObject()) {
                value = text(details.path("platform"), "dataSourceId", "datasourceId", "id");
            }
            return value == null ? null : UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            String value = node.path(name).asText(null);
            if (value != null && !value.isBlank()) return value.trim();
        }
        return null;
    }
}
