package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DefaultFederatedNativeSqlQualifier implements FederatedNativeSqlQualifier {

    private static final Pattern PUBLIC_DBT_RELATION_PATTERN = Pattern.compile(
            "(?i)\\b(from|join)\\s+(public\\s*\\.\\s*xycyl_[A-Za-z0-9_$]*)\\b");
    private static final Pattern TEMPORAL_FIELD_BARE_LITERAL_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z0-9_$])"
                    + "((?:[A-Za-z_][A-Za-z0-9_$]*\\.)?"
                    + "(?:[A-Za-z_][A-Za-z0-9_$]*(?:_time|_date|_at|time|date)|\"[^\"]*(?:时间|日期|月份|time|date)[^\"]*\"))"
                    + "\\s*(>=|<=|<>|!=|=|>|<)\\s*"
                    + "'((?:19|20)\\d{2}-\\d{2}-\\d{2}(?:\\s+\\d{2}:\\d{2}:\\d{2})?)'");

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public DefaultFederatedNativeSqlQualifier(
            AnalyticsDatabaseRepository databaseRepository, ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String qualify(long databaseId, String sql) {
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        Optional<AnalyticsDatabase> database = databaseRepository.findById(databaseId);
        if (database.isEmpty()) {
            return sql;
        }
        JsonNode details = parseDetails(database.get().getDetailsJson());
        if (!isFederatedDatabase(database.get(), details)) {
            return sql;
        }

        String qualifiedSql = sql;
        if (catalogAllowed(details, "postgres")) {
            qualifiedSql = qualifyPostgresPublicDbtTables(qualifiedSql);
        }
        if (isTrinoDatabase(database.get(), details)) {
            qualifiedSql = normalizeBareTemporalLiterals(qualifiedSql);
        }
        return qualifiedSql;
    }

    private String qualifyPostgresPublicDbtTables(String sql) {
        Matcher matcher = PUBLIC_DBT_RELATION_PATTERN.matcher(sql);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String relation = matcher.group(2).replaceAll("\\s*\\.\\s*", ".");
            matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group(1) + " postgres." + relation));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private String normalizeBareTemporalLiterals(String sql) {
        Matcher matcher = TEMPORAL_FIELD_BARE_LITERAL_PATTERN.matcher(sql);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + " " + matcher.group(2) + " TIMESTAMP '" + matcher.group(3) + "'";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private boolean isFederatedDatabase(AnalyticsDatabase database, JsonNode details) {
        String engine = database.getEngine() == null ? "" : database.getEngine().trim().toLowerCase(Locale.ROOT);
        if ("trino".equals(engine)) {
            return true;
        }
        if (details == null || !details.isObject()) {
            return false;
        }
        if (details.path("federatedQuery").asBoolean(false)) {
            return true;
        }
        return "trino".equalsIgnoreCase(details.path("queryContract").path("engine").asText(""));
    }

    private boolean isTrinoDatabase(AnalyticsDatabase database, JsonNode details) {
        String engine = database.getEngine() == null ? "" : database.getEngine().trim().toLowerCase(Locale.ROOT);
        if ("trino".equals(engine)) {
            return true;
        }
        return details != null
                && details.isObject()
                && "trino".equalsIgnoreCase(details.path("queryContract").path("engine").asText(""));
    }

    private JsonNode parseDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean catalogAllowed(JsonNode details, String catalogName) {
        if (details == null || !details.isObject()) {
            return false;
        }
        JsonNode catalogs = details.path("queryContract").path("catalogs");
        if (!catalogs.isArray()) {
            return false;
        }
        for (JsonNode catalog : catalogs) {
            if (catalogName.equalsIgnoreCase(catalog.asText(""))) {
                return true;
            }
        }
        return false;
    }
}
