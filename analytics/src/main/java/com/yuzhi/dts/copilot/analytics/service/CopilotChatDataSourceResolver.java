package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.io.IOException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CopilotChatDataSourceResolver {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public CopilotChatDataSourceResolver(
            AnalyticsDatabaseRepository databaseRepository,
            ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    public Long resolveSelectedDatasourceId(String rawDatasourceId) {
        if (!StringUtils.hasText(rawDatasourceId)) {
            return null;
        }

        Long parsedId = parseLong(rawDatasourceId);
        if (parsedId == null) {
            return null;
        }

        Optional<AnalyticsDatabase> database = databaseRepository.findById(parsedId);
        if (database.isEmpty()) {
            return parsedId;
        }

        Long linkedDatasourceId = extractDataSourceId(database.get().getDetailsJson());
        if (linkedDatasourceId != null) {
            return linkedDatasourceId;
        }

        throw new IllegalArgumentException(
                "Selected database %d does not have a linked copilot datasource".formatted(parsedId));
    }

    private Long extractDataSourceId(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode details = objectMapper.readTree(detailsJson);
        JsonNode value = firstPresent(details, "dataSourceId", "datasourceId", "copilotDataSourceId", "copilot_datasource_id");
        if (value == null || value.isNull()) {
            return null;
        }
            if (value.canConvertToLong()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                return parseLong(value.asText());
            }
            return null;
        } catch (IOException e) {
            throw new IllegalArgumentException("Selected database details are invalid JSON", e);
        }
    }

    private JsonNode firstPresent(JsonNode node, String... fieldNames) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private Long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
