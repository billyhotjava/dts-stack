package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AnalyticsDatabaseAliasResolver {

    private static final List<String> ARRAY_ALIAS_FIELDS = List.of(
            "logicalSourceAliases",
            "logicalDatasourceAliases",
            "dataSourceAliases",
            "datasourceAliases",
            "sourceAliases");

    private static final List<String> SINGLE_ALIAS_FIELDS = List.of(
            "logicalSourceAlias",
            "logicalDatasourceAlias",
            "dataSourceAlias",
            "datasourceAlias",
            "databaseAlias",
            "sourceAlias");

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public AnalyticsDatabaseAliasResolver(
            AnalyticsDatabaseRepository databaseRepository,
            ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    public long resolveDatabaseId(Object raw) {
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            return Math.max(number.longValue(), 0L);
        }
        if (raw instanceof JsonNode node) {
            return resolveDatabaseId(node);
        }
        return resolveTextualDatabaseReference(String.valueOf(raw));
    }

    public long resolveDatabaseId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return 0L;
        }
        if (node.canConvertToLong()) {
            return Math.max(node.asLong(), 0L);
        }
        if (node.isTextual()) {
            return resolveTextualDatabaseReference(node.asText());
        }
        return 0L;
    }

    private long resolveTextualDatabaseReference(String raw) {
        String text = trimToNull(raw);
        if (text == null) {
            return 0L;
        }
        try {
            long id = Long.parseLong(text);
            return Math.max(id, 0L);
        } catch (NumberFormatException ignored) {
            return resolveAlias(text);
        }
    }

    private long resolveAlias(String alias) {
        String normalizedAlias = normalizeAlias(alias);
        List<AnalyticsDatabase> matches = new ArrayList<>();
        for (AnalyticsDatabase database : databaseRepository.findAll()) {
            if (database.getId() == null) {
                continue;
            }
            if (hasAlias(database.getDetailsJson(), normalizedAlias)) {
                matches.add(database);
            }
        }
        return matches.stream()
                .sorted(Comparator
                        .comparingInt(AnalyticsDatabaseAliasResolver::rolePriority)
                        .thenComparing(database -> database.getId() == null ? Long.MAX_VALUE : database.getId()))
                .findFirst()
                .map(AnalyticsDatabase::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "未配置数据源别名: " + alias + "。请在治理后台数据源配置中为目标数据源添加 logicalSourceAliases。"));
    }

    private boolean hasAlias(String detailsJson, String normalizedAlias) {
        if (!StringUtils.hasText(detailsJson)) {
            return false;
        }
        try {
            JsonNode details = objectMapper.readTree(detailsJson);
            if (details == null || !details.isObject()) {
                return false;
            }
            for (String field : ARRAY_ALIAS_FIELDS) {
                JsonNode aliases = details.path(field);
                if (!aliases.isArray()) {
                    continue;
                }
                for (JsonNode alias : aliases) {
                    if (normalizedAlias.equals(normalizeAlias(alias.asText(null)))) {
                        return true;
                    }
                }
            }
            for (String field : SINGLE_ALIAS_FIELDS) {
                if (normalizedAlias.equals(normalizeAlias(details.path(field).asText(null)))) {
                    return true;
                }
            }
            JsonNode logicalDataSource = details.path("logicalDataSource");
            if (logicalDataSource.isObject()) {
                if (normalizedAlias.equals(normalizeAlias(logicalDataSource.path("alias").asText(null)))) {
                    return true;
                }
                JsonNode aliases = logicalDataSource.path("aliases");
                if (aliases.isArray()) {
                    for (JsonNode alias : aliases) {
                        if (normalizedAlias.equals(normalizeAlias(alias.asText(null)))) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static int rolePriority(AnalyticsDatabase database) {
        String role = database == null ? null : database.getDatabaseRoleValue();
        if ("BUSINESS_PRIMARY".equals(role)) {
            return 0;
        }
        if ("BUSINESS_SECONDARY".equals(role)) {
            return 10;
        }
        if ("SYSTEM_RUNTIME".equals(role)) {
            return 90;
        }
        if ("SAMPLE".equals(role)) {
            return 100;
        }
        return 50;
    }

    private static String normalizeAlias(String value) {
        String text = trimToNull(value);
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }
}
