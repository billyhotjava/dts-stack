package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FederatedQueryGuardrail {

    private static final String IDENTIFIER = "(?:[A-Za-z_][A-Za-z0-9_$]*|\"(?:[^\"]|\"\")*\"|`[^`]*`)";
    private static final Pattern RELATION_PATTERN = Pattern.compile(
            "(?i)\\b(from|join)\\s+(?!\\()(" + IDENTIFIER + "(?:\\s*\\.\\s*" + IDENTIFIER + "){0,3})");
    private static final Pattern CTE_ALIAS_PATTERN = Pattern.compile(
            "(?i)(?:\\bwith\\b|,)\\s*(" + IDENTIFIER + ")\\s+as\\s*\\(");
    private static final Pattern JOIN_PATTERN = Pattern.compile("(?i)\\bjoin\\b");
    private static final Pattern WHERE_OR_LIMIT_PATTERN = Pattern.compile("(?i)\\b(where|limit)\\b");

    private final AnalyticsDatabaseRepository databaseRepository;
    private final ObjectMapper objectMapper;

    public FederatedQueryGuardrail(AnalyticsDatabaseRepository databaseRepository, ObjectMapper objectMapper) {
        this.databaseRepository = databaseRepository;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(long databaseId, String queryType, String sql) {
        Optional<AnalyticsDatabase> database = databaseRepository.findById(databaseId);
        if (database.isEmpty()) {
            return ValidationResult.notFederated();
        }

        JsonNode details = parseDetails(database.get().getDetailsJson());
        if (!isFederatedDatabase(database.get(), details)) {
            return ValidationResult.notFederated();
        }

        if (!"native".equalsIgnoreCase(queryType)) {
            throw new IllegalArgumentException("联邦查询入口仅支持 native SQL");
        }
        if (!StringUtils.hasText(sql)) {
            throw new IllegalArgumentException("联邦查询 SQL 不能为空");
        }

        Set<String> cteAliases = extractCteAliases(sql);
        List<String> allowedCatalogs = configuredCatalogs(details);
        List<String> relations = new ArrayList<>();
        Set<String> catalogs = new TreeSet<>();

        Matcher matcher = RELATION_PATTERN.matcher(sql);
        while (matcher.find()) {
            List<String> parts = splitIdentifierPath(matcher.group(2));
            if (parts.isEmpty()) {
                continue;
            }
            if (parts.size() < 3) {
                String alias = normalizeIdentifier(parts.get(0));
                if (!cteAliases.contains(alias)) {
                    throw new IllegalArgumentException("联邦查询表名必须使用 catalog.schema.table: " + matcher.group(2));
                }
                continue;
            }

            String catalog = normalizeIdentifier(parts.get(0));
            if (!allowedCatalogs.isEmpty() && !allowedCatalogs.contains(catalog)) {
                throw new IllegalArgumentException("联邦查询 catalog 未授权: " + catalog);
            }
            catalogs.add(catalog);
            relations.add(String.join(".", parts.stream().map(FederatedQueryGuardrail::normalizeIdentifier).toList()));
        }

        if (relations.isEmpty()) {
            throw new IllegalArgumentException("联邦查询必须至少引用一个 catalog.schema.table");
        }

        boolean hasJoin = JOIN_PATTERN.matcher(sql).find();
        if (hasJoin && catalogs.size() >= 2 && !WHERE_OR_LIMIT_PATTERN.matcher(sql).find()) {
            throw new IllegalArgumentException("跨库 JOIN 必须包含 WHERE 或 LIMIT");
        }

        return new ValidationResult(true, "trino", List.copyOf(catalogs), List.copyOf(relations));
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

    private static List<String> configuredCatalogs(JsonNode details) {
        if (details == null || !details.isObject()) {
            return List.of();
        }
        JsonNode catalogs = details.path("queryContract").path("catalogs");
        if (!catalogs.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode catalog : catalogs) {
            String normalized = normalizeIdentifier(catalog.asText(null));
            if (StringUtils.hasText(normalized) && !out.contains(normalized)) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static Set<String> extractCteAliases(String sql) {
        Set<String> aliases = new LinkedHashSet<>();
        Matcher matcher = CTE_ALIAS_PATTERN.matcher(sql);
        while (matcher.find()) {
            aliases.add(normalizeIdentifier(matcher.group(1)));
        }
        return aliases;
    }

    private static List<String> splitIdentifierPath(String raw) {
        List<String> parts = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return parts;
        }
        StringBuilder current = new StringBuilder();
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '"' && !inBacktick) {
                inDoubleQuote = !inDoubleQuote;
                current.append(ch);
                continue;
            }
            if (ch == '`' && !inDoubleQuote) {
                inBacktick = !inBacktick;
                current.append(ch);
                continue;
            }
            if (ch == '.' && !inDoubleQuote && !inBacktick) {
                addPathPart(parts, current);
                current.setLength(0);
                continue;
            }
            if (!Character.isWhitespace(ch) || inDoubleQuote || inBacktick) {
                current.append(ch);
            }
        }
        addPathPart(parts, current);
        return parts;
    }

    private static void addPathPart(List<String> parts, StringBuilder current) {
        String normalized = normalizeIdentifier(current.toString());
        if (StringUtils.hasText(normalized)) {
            parts.add(normalized);
        }
    }

    private static String normalizeIdentifier(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String text = value.trim();
        if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("`") && text.endsWith("`"))) {
            text = text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }

    public record ValidationResult(boolean federated, String engine, List<String> catalogs, List<String> relations) {

        static ValidationResult notFederated() {
            return new ValidationResult(false, null, List.of(), List.of());
        }

        public Map<String, Object> toResponseMetadata() {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("federated", federated);
            if (engine != null) {
                metadata.put("engine", engine);
            }
            metadata.put("catalogs", catalogs);
            metadata.put("relations", relations);
            return metadata;
        }
    }
}
