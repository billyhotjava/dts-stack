package com.yuzhi.dts.platform.service.openmetadata;

import com.yuzhi.dts.platform.config.OpenMetadataProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OpenMetadataService {

    private final OpenMetadataClient client;
    private final OpenMetadataProperties props;

    public OpenMetadataService(OpenMetadataClient client, OpenMetadataProperties props) {
        this.client = client;
        this.props = props;
    }

    public OpenMetadataResult fetchTableForDataset(CatalogDataset dataset) {
        if (!props.isEnabled()) {
            return OpenMetadataResult.disabled();
        }
        if (dataset == null) {
            return OpenMetadataResult.notFound(null, "数据集不存在");
        }

        List<String> candidates = buildCandidateFqns(dataset);
        String fields = props.getTableFields();
        for (String fqn : candidates) {
            Optional<Map<String, Object>> found = client.getTableByFqn(fqn, fields);
            if (found.isPresent()) {
                return OpenMetadataResult.found(fqn, found.get(), resolveUiBaseUrl());
            }
        }
        String fallback = candidates.isEmpty() ? null : candidates.get(0);
        return OpenMetadataResult.notFound(fallback, "未找到匹配的技术资产");
    }

    public OpenMetadataSummary summarize(OpenMetadataResult result) {
        if (result == null) {
            return new OpenMetadataSummary(false, false, null, null, "-", "-", "-", "-", 0);
        }
        Map<String, Object> entity = result.entity();
        String owner = resolveOwner(entity);
        String domain = resolveDomain(entity);
        String tags = resolveTags(entity);
        String description = resolveDescription(entity);
        int columnCount = resolveColumnCount(entity);
        return new OpenMetadataSummary(
            result.enabled(),
            result.found(),
            result.fqn(),
            result.uiBaseUrl(),
            owner,
            domain,
            tags,
            description,
            columnCount
        );
    }

    private List<String> buildCandidateFqns(CatalogDataset dataset) {
        String service = trim(props.getServiceName());
        String database = trim(dataset.getHiveDatabase());
        String table = trim(dataset.getHiveTable());
        if (!StringUtils.hasText(table)) {
            table = trim(dataset.getName());
        }
        if (!StringUtils.hasText(database)) {
            database = trim(props.getDefaultDatabase());
        }
        String schema = trim(props.getDefaultSchema());

        if (StringUtils.hasText(table) && table.contains(".") && !StringUtils.hasText(database)) {
            String[] parts = table.split("\\.");
            if (parts.length >= 2) {
                database = parts[0];
                table = parts[parts.length - 1];
                if (parts.length == 3 && !StringUtils.hasText(schema)) {
                    schema = parts[1];
                }
            }
        }

        List<String> fqns = new ArrayList<>();
        String pattern = trim(props.getTableFqnPattern());
        if (!StringUtils.hasText(pattern)) {
            pattern = "{service}.{database}.{table}";
        }
        String formatted = formatPattern(pattern, service, database, schema, table);
        if (StringUtils.hasText(formatted)) {
            fqns.add(formatted);
        }

        if (StringUtils.hasText(schema)) {
            String noSchema = formatPattern("{service}.{database}.{table}", service, database, null, table);
            if (StringUtils.hasText(noSchema) && !fqns.contains(noSchema)) {
                fqns.add(noSchema);
            }
        }

        return fqns;
    }

    private String formatPattern(String pattern, String service, String database, String schema, String table) {
        if (!StringUtils.hasText(pattern)) {
            return null;
        }
        String formatted = pattern;
        formatted = formatted.replace("{service}", safe(service));
        formatted = formatted.replace("{database}", safe(database));
        formatted = formatted.replace("{schema}", safe(schema));
        formatted = formatted.replace("{table}", safe(table));
        formatted = formatted.replace("..", ".");
        while (formatted.contains("..")) {
            formatted = formatted.replace("..", ".");
        }
        formatted = formatted.replaceAll("^\\.+", "").replaceAll("\\.+$", "");
        return StringUtils.hasText(formatted) ? formatted : null;
    }

    private String resolveUiBaseUrl() {
        if (StringUtils.hasText(props.getUiBaseUrl())) {
            return props.getUiBaseUrl().trim();
        }
        return props.getBaseUrl();
    }

    private String resolveOwner(Map<String, Object> entity) {
        if (entity == null) return "-";
        Object owner = entity.get("owner");
        if (!(owner instanceof Map)) return "-";
        Map<?, ?> map = (Map<?, ?>) owner;
        String display = stringValue(map.get("displayName"));
        if (display != null) return display;
        return fallbackDash(map.get("name"));
    }

    private String resolveDomain(Map<String, Object> entity) {
        if (entity == null) return "-";
        Object domain = entity.get("domain");
        if (!(domain instanceof Map)) return "-";
        Map<?, ?> map = (Map<?, ?>) domain;
        String display = stringValue(map.get("displayName"));
        if (display != null) return display;
        return fallbackDash(map.get("name"));
    }

    private String resolveTags(Map<String, Object> entity) {
        if (entity == null) return "-";
        Object tags = entity.get("tags");
        if (!(tags instanceof List<?> list)) return "-";
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String tagFqn = stringValue(map.get("tagFQN"));
                if (tagFqn != null) {
                    values.add(tagFqn);
                    continue;
                }
                Object tag = map.get("tag");
                if (tag instanceof Map<?, ?> tagMap) {
                    String tagName = stringValue(tagMap.get("displayName"));
                    if (tagName == null) {
                        tagName = stringValue(tagMap.get("name"));
                    }
                    if (tagName != null) {
                        values.add(tagName);
                    }
                }
            } else if (item != null) {
                String raw = String.valueOf(item).trim();
                if (!raw.isEmpty()) values.add(raw);
            }
        }
        if (values.isEmpty()) return "-";
        return String.join(", ", values);
    }

    private String resolveDescription(Map<String, Object> entity) {
        if (entity == null) return "-";
        String desc = stringValue(entity.get("description"));
        return desc != null ? desc : "-";
    }

    private int resolveColumnCount(Map<String, Object> entity) {
        if (entity == null) return 0;
        Object cols = entity.get("columns");
        if (cols instanceof List<?> list) {
            return list.size();
        }
        return 0;
    }

    private String fallbackDash(Object value) {
        if (value == null) return "-";
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? "-" : text;
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record OpenMetadataResult(
        boolean enabled,
        boolean found,
        String fqn,
        String uiBaseUrl,
        String message,
        Instant resolvedAt,
        Map<String, Object> entity
    ) {
        public static OpenMetadataResult disabled() {
            return new OpenMetadataResult(false, false, null, null, "元数据服务未启用", Instant.now(), Map.of());
        }

        public static OpenMetadataResult notFound(String fqn, String message) {
            return new OpenMetadataResult(true, false, fqn, null, message, Instant.now(), Map.of());
        }

        public static OpenMetadataResult found(String fqn, Map<String, Object> entity, String uiBaseUrl) {
            return new OpenMetadataResult(true, true, fqn, uiBaseUrl, null, Instant.now(), entity);
        }
    }

    public record OpenMetadataSummary(
        boolean enabled,
        boolean found,
        String fqn,
        String uiBaseUrl,
        String owner,
        String domain,
        String tags,
        String description,
        int columnCount
    ) {}
}
