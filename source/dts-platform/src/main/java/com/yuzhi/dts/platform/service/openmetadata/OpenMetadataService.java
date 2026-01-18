package com.yuzhi.dts.platform.service.openmetadata;

import com.yuzhi.dts.platform.config.OpenMetadataProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
                return OpenMetadataResult.found(fqn, found.orElseThrow(), resolveUiBaseUrl());
            }
        }
        String fallback = candidates.isEmpty() ? null : candidates.get(0);
        return OpenMetadataResult.notFound(fallback, "未找到匹配的技术资产");
    }

    public OpenMetadataResult fetchTableByFqn(String fqn) {
        if (!props.isEnabled()) {
            return OpenMetadataResult.disabled();
        }
        if (!StringUtils.hasText(fqn)) {
            return OpenMetadataResult.notFound(null, "未提供技术资产标识");
        }
        String fields = props.getTableFields();
        Optional<Map<String, Object>> found = client.getTableByFqn(fqn.trim(), fields);
        if (found.isPresent()) {
            return OpenMetadataResult.found(fqn.trim(), found.orElseThrow(), resolveUiBaseUrl());
        }
        return OpenMetadataResult.notFound(fqn.trim(), "未找到匹配的技术资产");
    }

    public OpenMetadataSummary summarize(OpenMetadataResult result) {
        if (result == null) {
            return new OpenMetadataSummary(false, false, null, null, "-", "-", "-", "-", 0, null);
        }
        Map<String, Object> entity = result.entity();
        String owner = resolveOwner(entity);
        String domain = resolveDomain(entity);
        String tags = resolveTags(entity);
        String description = resolveDescription(entity);
        int columnCount = resolveColumnCount(entity);
        UsageSummary usage = resolveUsageSummary(entity);
        return new OpenMetadataSummary(
            result.enabled(),
            result.found(),
            result.fqn(),
            result.uiBaseUrl(),
            owner,
            domain,
            tags,
            description,
            columnCount,
            usage
        );
    }

    public OpenMetadataTablePage searchTables(String keyword, int size) {
        if (!props.isEnabled()) {
            return OpenMetadataTablePage.disabled();
        }
        int limit = Math.max(1, Math.min(size, 200));
        List<Map<String, Object>> entities = new ArrayList<>();
        int total = 0;
        boolean usedSearch = StringUtils.hasText(keyword);
        if (usedSearch) {
            Optional<Map<String, Object>> response = client.searchTables(keyword.trim(), limit);
            if (response.isEmpty()) {
                return new OpenMetadataTablePage(true, List.of(), 0, keyword, true, "暂无技术资产");
            }
            SearchEnvelope envelope = parseSearchEnvelope(response.orElseThrow());
            entities = envelope.entities();
            total = envelope.total();
        } else {
            Optional<Map<String, Object>> response = client.listTables(limit, props.getTableFields());
            if (response.isEmpty()) {
                return new OpenMetadataTablePage(true, List.of(), 0, null, false, "暂无技术资产");
            }
            List<Map<String, Object>> data = parseListData(response.orElseThrow());
            entities = data;
            total = data.size();
        }
        List<OpenMetadataTableSummary> summaries = new ArrayList<>();
        for (Map<String, Object> entity : entities) {
            if (entity == null || entity.isEmpty()) {
                continue;
            }
            OpenMetadataTableSummary summary = toTableSummary(entity);
            if (summary != null) {
                summaries.add(summary);
            }
        }
        summaries.sort(Comparator.comparing(OpenMetadataTableSummary::name, Comparator.nullsLast(String::compareTo)));
        return new OpenMetadataTablePage(true, summaries, total, keyword, usedSearch, null);
    }

    public OpenMetadataLineageResult fetchLineageForDataset(CatalogDataset dataset, int upstreamDepth, int downstreamDepth) {
        OpenMetadataResult result = fetchTableForDataset(dataset);
        if (!result.enabled()) {
            return OpenMetadataLineageResult.disabled();
        }
        if (!result.found()) {
            return OpenMetadataLineageResult.notFound(result.fqn(), result.message());
        }
        String entityId = extractEntityId(result.entity());
        if (!StringUtils.hasText(entityId)) {
            return OpenMetadataLineageResult.notFound(result.fqn(), "技术资产缺少标识");
        }
        Optional<Map<String, Object>> lineage = client.getLineage(entityId, upstreamDepth, downstreamDepth);
        if (lineage.isEmpty()) {
            return OpenMetadataLineageResult.notFound(result.fqn(), "暂无血缘信息");
        }
        LineageGraph graph = buildLineageGraph(result, lineage.orElseThrow(), upstreamDepth, downstreamDepth);
        return OpenMetadataLineageResult.found(result.fqn(), graph);
    }

    public OpenMetadataQualityResult fetchQualityForDataset(CatalogDataset dataset) {
        OpenMetadataResult result = fetchTableForDataset(dataset);
        if (!result.enabled()) {
            return OpenMetadataQualityResult.disabled();
        }
        if (!result.found()) {
            return OpenMetadataQualityResult.notFound(result.fqn(), result.message());
        }
        String fqn = result.fqn();
        String entityLink = buildEntityLink(fqn);
        if (!StringUtils.hasText(entityLink)) {
            return OpenMetadataQualityResult.notFound(fqn, "技术资产缺少标识");
        }
        Optional<Map<String, Object>> response = client.getTestCases(entityLink);
        if (response.isEmpty()) {
            return OpenMetadataQualityResult.notFound(fqn, "暂无质量结果");
        }
        QualitySnapshot snapshot = buildQualitySnapshot(response.orElseThrow());
        return OpenMetadataQualityResult.found(fqn, snapshot);
    }

    public OpenMetadataQualitySummary summarizeQuality(OpenMetadataQualityResult result) {
        if (result == null) {
            return OpenMetadataQualitySummary.empty("元数据服务未启用");
        }
        if (!result.enabled()) {
            return OpenMetadataQualitySummary.empty("元数据服务未启用");
        }
        if (!result.found()) {
            return new OpenMetadataQualitySummary(
                true,
                false,
                result.fqn(),
                0,
                0,
                0,
                0,
                0,
                null,
                null,
                result.message() == null ? "暂无质量结果" : result.message()
            );
        }
        QualitySummary summary = result.snapshot() != null ? result.snapshot().summary() : null;
        int total = summary != null ? summary.total() : 0;
        int passed = summary != null ? summary.passed() : 0;
        int failed = summary != null ? summary.failed() : 0;
        int aborted = summary != null ? summary.aborted() : 0;
        int missing = summary != null ? summary.missing() : 0;
        Integer passRate = total > 0 ? Math.toIntExact(Math.round((passed * 100.0) / total)) : null;
        Instant lastRunAt = summary != null ? summary.lastRunAt() : null;
        return new OpenMetadataQualitySummary(
            true,
            true,
            result.fqn(),
            total,
            passed,
            failed,
            aborted,
            missing,
            passRate,
            lastRunAt,
            null
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

    private UsageSummary resolveUsageSummary(Map<String, Object> entity) {
        if (entity == null) return null;
        Object raw = entity.get("usageSummary");
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        UsageStats daily = toUsageStats(map.get("dailyStats"));
        UsageStats weekly = toUsageStats(map.get("weeklyStats"));
        UsageStats monthly = toUsageStats(map.get("monthlyStats"));
        String date = stringValue(map.get("date"));
        if (daily == null && weekly == null && monthly == null && date == null) {
            return null;
        }
        return new UsageSummary(daily, weekly, monthly, date);
    }

    private UsageStats toUsageStats(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return null;
        }
        Integer count = parseInteger(map.get("count"));
        Double percentile = parseDouble(map.get("percentileRank"));
        if (count == null && percentile == null) {
            return null;
        }
        return new UsageStats(count, percentile);
    }

    private OpenMetadataTableSummary toTableSummary(Map<String, Object> entity) {
        if (entity == null || entity.isEmpty()) {
            return null;
        }
        String fqn = stringValue(entity.get("fullyQualifiedName"));
        String name = pickFirstString(entity.get("displayName"), entity.get("name"), fqn);
        String id = stringValue(entity.get("id"));
        String owner = resolveOwner(entity);
        String domain = resolveDomain(entity);
        String tags = resolveTags(entity);
        String description = resolveDescription(entity);
        int columnCount = resolveColumnCount(entity);
        String service = extractName(entity.get("service"));
        String database = extractName(entity.get("database"));
        String schema = extractName(entity.get("schema"));
        return new OpenMetadataTableSummary(id, name, fqn, service, database, schema, owner, domain, tags, description, columnCount);
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

    private LineageGraph buildLineageGraph(OpenMetadataResult result, Map<String, Object> lineage, int upstreamDepth, int downstreamDepth) {
        String rootId = extractEntityId(result.entity());
        Map<String, LineageNode> nodes = new LinkedHashMap<>();
        Object rawNodes = lineage.get("nodes");
        if (rawNodes instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String id = stringValue(map.get("id"));
                    if (!StringUtils.hasText(id)) {
                        continue;
                    }
                    LineageNode node = toLineageNode(map, id);
                    nodes.put(id, node);
                }
            }
        }
        if (StringUtils.hasText(rootId) && !nodes.containsKey(rootId)) {
            nodes.put(rootId, toLineageNode(result.entity(), rootId));
        }

        Map<String, List<String>> upstreamAdj = new HashMap<>();
        Map<String, List<String>> downstreamAdj = new HashMap<>();
        Object rawEdges = lineage.get("edges");
        if (rawEdges instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String fromId = resolveEntityId(map.get("fromEntity"));
                    String toId = resolveEntityId(map.get("toEntity"));
                    if (!StringUtils.hasText(fromId) || !StringUtils.hasText(toId)) {
                        continue;
                    }
                    downstreamAdj.computeIfAbsent(fromId, key -> new ArrayList<>()).add(toId);
                    upstreamAdj.computeIfAbsent(toId, key -> new ArrayList<>()).add(fromId);
                }
            }
        }

        Set<String> upstreamIds = collectByDepth(rootId, upstreamAdj, upstreamDepth);
        Set<String> downstreamIds = collectByDepth(rootId, downstreamAdj, downstreamDepth);

        List<LineageNode> upstream = buildLineageList(upstreamIds, nodes);
        List<LineageNode> downstream = buildLineageList(downstreamIds, nodes);

        LineageNode root = StringUtils.hasText(rootId) ? nodes.get(rootId) : null;
        List<LineageLevel> upstreamLevels = buildLevels(rootId, upstreamAdj, nodes, upstreamDepth);
        List<LineageLevel> downstreamLevels = buildLevels(rootId, downstreamAdj, nodes, downstreamDepth);
        return new LineageGraph(root, upstream, downstream, upstreamDepth, downstreamDepth, upstreamLevels, downstreamLevels);
    }

    private Set<String> collectByDepth(String rootId, Map<String, List<String>> adjacency, int maxDepth) {
        Set<String> result = new HashSet<>();
        if (!StringUtils.hasText(rootId) || maxDepth <= 0) {
            return result;
        }
        Deque<DepthNode> queue = new ArrayDeque<>();
        queue.add(new DepthNode(rootId, 0));
        while (!queue.isEmpty()) {
            DepthNode node = queue.removeFirst();
            if (node.depth >= maxDepth) {
                continue;
            }
            List<String> nextList = adjacency.getOrDefault(node.id, List.of());
            for (String next : nextList) {
                if (!StringUtils.hasText(next) || !result.add(next)) {
                    continue;
                }
                queue.addLast(new DepthNode(next, node.depth + 1));
            }
        }
        return result;
    }

    private List<LineageNode> buildLineageList(Set<String> ids, Map<String, LineageNode> nodes) {
        List<LineageNode> list = new ArrayList<>();
        for (String id : ids) {
            LineageNode node = nodes.get(id);
            if (node != null) {
                list.add(node);
            }
        }
        list.sort(Comparator.comparing(LineageNode::name, Comparator.nullsLast(String::compareTo)));
        return list;
    }

    private List<LineageLevel> buildLevels(
        String rootId,
        Map<String, List<String>> adjacency,
        Map<String, LineageNode> nodes,
        int maxDepth
    ) {
        if (!StringUtils.hasText(rootId) || maxDepth <= 0) {
            return List.of();
        }
        Map<Integer, List<LineageNode>> buckets = new LinkedHashMap<>();
        Deque<DepthNode> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new DepthNode(rootId, 0));
        visited.add(rootId);
        while (!queue.isEmpty()) {
            DepthNode current = queue.removeFirst();
            if (current.depth >= maxDepth) {
                continue;
            }
            List<String> nextList = adjacency.getOrDefault(current.id, List.of());
            for (String next : nextList) {
                if (!StringUtils.hasText(next) || visited.contains(next)) {
                    continue;
                }
                int level = current.depth + 1;
                LineageNode node = nodes.get(next);
                if (node != null) {
                    buckets.computeIfAbsent(level, key -> new ArrayList<>()).add(node);
                }
                visited.add(next);
                queue.addLast(new DepthNode(next, level));
            }
        }
        List<LineageLevel> levels = new ArrayList<>();
        for (Map.Entry<Integer, List<LineageNode>> entry : buckets.entrySet()) {
            entry.getValue().sort(Comparator.comparing(LineageNode::name, Comparator.nullsLast(String::compareTo)));
            levels.add(new LineageLevel(entry.getKey(), entry.getValue()));
        }
        levels.sort(Comparator.comparingInt(LineageLevel::level));
        return levels;
    }

    private LineageNode toLineageNode(Map<?, ?> map, String id) {
        if (map == null) {
            return new LineageNode(id, null, id, null, null, null, null, null);
        }
        String fqn = stringValue(map.get("fullyQualifiedName"));
        String name = pickFirstString(map.get("displayName"), map.get("name"), fqn, id);
        String type = stringValue(map.get("entityType"));
        String service = extractName(map.get("service"));
        String database = extractName(map.get("database"));
        String schema = extractName(map.get("schema"));
        String description = stringValue(map.get("description"));
        return new LineageNode(id, fqn, name, service, database, schema, type, description);
    }

    private String extractEntityId(Map<String, Object> entity) {
        if (entity == null) {
            return null;
        }
        String id = stringValue(entity.get("id"));
        return StringUtils.hasText(id) ? id : null;
    }

    private String resolveEntityId(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof String str) {
            return str.trim();
        }
        if (raw instanceof Map<?, ?> map) {
            String id = stringValue(map.get("id"));
            if (StringUtils.hasText(id)) {
                return id;
            }
            return stringValue(map.get("entity"));
        }
        return stringValue(raw);
    }

    private String extractName(Object value) {
        if (value instanceof Map<?, ?> map) {
            return pickFirstString(map.get("displayName"), map.get("name"));
        }
        return stringValue(value);
    }

    private QualitySnapshot buildQualitySnapshot(Map<String, Object> response) {
        List<QualityTestCase> cases = new ArrayList<>();
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    QualityTestCase testCase = toQualityTestCase(map);
                    if (testCase != null) {
                        cases.add(testCase);
                    }
                }
            }
        }
        int total = cases.size();
        int passed = 0;
        int failed = 0;
        int aborted = 0;
        int missing = 0;
        Instant lastRun = null;
        for (QualityTestCase testCase : cases) {
            String status = testCase.status();
            if (status == null) {
                missing++;
            } else if ("SUCCESS".equalsIgnoreCase(status) || "PASSED".equalsIgnoreCase(status)) {
                passed++;
            } else if ("FAILED".equalsIgnoreCase(status) || "FAIL".equalsIgnoreCase(status)) {
                failed++;
            } else {
                aborted++;
            }
            Instant runAt = testCase.lastRunAt();
            if (runAt != null && (lastRun == null || runAt.isAfter(lastRun))) {
                lastRun = runAt;
            }
        }
        QualitySummary summary = new QualitySummary(total, passed, failed, aborted, missing, lastRun);
        return new QualitySnapshot(summary, cases);
    }

    private QualityTestCase toQualityTestCase(Map<?, ?> map) {
        if (map == null) {
            return null;
        }
        String id = stringValue(map.get("id"));
        String name = pickFirstString(map.get("displayName"), map.get("name"), id);
        String description = stringValue(map.get("description"));
        String owner = extractName(map.get("owner"));
        String testSuite = extractName(map.get("testSuite"));
        Map<?, ?> result = map.get("testCaseResult") instanceof Map<?, ?> r ? r : null;
        String status = result != null ? pickFirstString(result.get("testCaseStatus"), result.get("status")) : null;
        Instant lastRun = parseTimestamp(result != null ? result.get("lastRunTimestamp") : null);
        Integer passedRows = parseInteger(result != null ? result.get("passedRows") : null);
        Integer failedRows = parseInteger(result != null ? result.get("failedRows") : null);
        String resultValue = result != null ? stringValue(result.get("testResultValue")) : null;
        return new QualityTestCase(id, name, description, status, owner, testSuite, lastRun, resultValue, passedRows, failedRows);
    }

    private Integer parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Instant parseTimestamp(Object value) {
        if (value instanceof Number number) {
            long ts = number.longValue();
            if (ts <= 0) {
                return null;
            }
            return Instant.ofEpochMilli(ts);
        }
        if (value == null) {
            return null;
        }
        try {
            long ts = Long.parseLong(String.valueOf(value).trim());
            return ts > 0 ? Instant.ofEpochMilli(ts) : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildEntityLink(String fqn) {
        if (!StringUtils.hasText(fqn)) {
            return null;
        }
        return "<#E::table::" + fqn.trim() + ">";
    }

    private String pickFirstString(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private record DepthNode(String id, int depth) {}

    private record SearchEnvelope(List<Map<String, Object>> entities, int total) {}

    private SearchEnvelope parseSearchEnvelope(Map<String, Object> response) {
        if (response == null) {
            return new SearchEnvelope(List.of(), 0);
        }
        Object hits = response.get("hits");
        if (hits instanceof Map<?, ?> hitsMap) {
            int total = 0;
            Object rawTotal = hitsMap.get("total");
            if (rawTotal instanceof Map<?, ?> totalMap) {
                Object value = totalMap.get("value");
                if (value instanceof Number number) {
                    total = number.intValue();
                }
            } else if (rawTotal instanceof Number number) {
                total = number.intValue();
            }
            List<Map<String, Object>> items = new ArrayList<>();
            Object hitItems = hitsMap.get("hits");
            if (hitItems instanceof List<?> list) {
                for (Object hit : list) {
                    if (hit instanceof Map<?, ?> hitMap) {
                        Object source = hitMap.get("_source");
                        if (source instanceof Map<?, ?> srcMap) {
                            items.add(new LinkedHashMap<>((Map<String, Object>) srcMap));
                        } else if (hitMap.get("source") instanceof Map<?, ?> altMap) {
                            items.add(new LinkedHashMap<>((Map<String, Object>) altMap));
                        }
                    }
                }
            }
            return new SearchEnvelope(items, total);
        }
        List<Map<String, Object>> data = parseListData(response);
        return new SearchEnvelope(data, data.size());
    }

    private List<Map<String, Object>> parseListData(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object data = response.get("data");
        if (data instanceof List<?> list) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    items.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return items;
        }
        Object tables = response.get("tables");
        if (tables instanceof List<?> list) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    items.add(new LinkedHashMap<>((Map<String, Object>) map));
                }
            }
            return items;
        }
        return List.of();
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
        int columnCount,
        UsageSummary usage
    ) {}

    public record OpenMetadataTablePage(
        boolean enabled,
        List<OpenMetadataTableSummary> items,
        int total,
        String keyword,
        boolean searched,
        String message
    ) {
        public static OpenMetadataTablePage disabled() {
            return new OpenMetadataTablePage(false, List.of(), 0, null, false, "元数据服务未启用");
        }
    }

    public record OpenMetadataTableSummary(
        String id,
        String name,
        String fqn,
        String service,
        String database,
        String schema,
        String owner,
        String domain,
        String tags,
        String description,
        int columnCount
    ) {}

    public record OpenMetadataLineageResult(
        boolean enabled,
        boolean found,
        String fqn,
        String message,
        Instant resolvedAt,
        LineageGraph graph
    ) {
        public static OpenMetadataLineageResult disabled() {
            return new OpenMetadataLineageResult(false, false, null, "元数据服务未启用", Instant.now(), null);
        }

        public static OpenMetadataLineageResult notFound(String fqn, String message) {
            return new OpenMetadataLineageResult(true, false, fqn, message, Instant.now(), null);
        }

        public static OpenMetadataLineageResult found(String fqn, LineageGraph graph) {
            return new OpenMetadataLineageResult(true, true, fqn, null, Instant.now(), graph);
        }
    }

    public record LineageGraph(
        LineageNode root,
        List<LineageNode> upstream,
        List<LineageNode> downstream,
        int upstreamDepth,
        int downstreamDepth,
        List<LineageLevel> upstreamLevels,
        List<LineageLevel> downstreamLevels
    ) {}

    public record LineageLevel(int level, List<LineageNode> nodes) {}

    public record LineageNode(
        String id,
        String fqn,
        String name,
        String service,
        String database,
        String schema,
        String type,
        String description
    ) {}

    public record OpenMetadataQualityResult(
        boolean enabled,
        boolean found,
        String fqn,
        String message,
        Instant resolvedAt,
        QualitySnapshot snapshot
    ) {
        public static OpenMetadataQualityResult disabled() {
            return new OpenMetadataQualityResult(false, false, null, "元数据服务未启用", Instant.now(), null);
        }

        public static OpenMetadataQualityResult notFound(String fqn, String message) {
            return new OpenMetadataQualityResult(true, false, fqn, message, Instant.now(), null);
        }

        public static OpenMetadataQualityResult found(String fqn, QualitySnapshot snapshot) {
            return new OpenMetadataQualityResult(true, true, fqn, null, Instant.now(), snapshot);
        }
    }

    public record QualitySnapshot(QualitySummary summary, List<QualityTestCase> cases) {}

    public record QualitySummary(int total, int passed, int failed, int aborted, int missing, Instant lastRunAt) {}

    public record QualityTestCase(
        String id,
        String name,
        String description,
        String status,
        String owner,
        String testSuite,
        Instant lastRunAt,
        String resultValue,
        Integer passedRows,
        Integer failedRows
    ) {}

    public record OpenMetadataQualitySummary(
        boolean enabled,
        boolean found,
        String fqn,
        int total,
        int passed,
        int failed,
        int aborted,
        int missing,
        Integer passRate,
        Instant lastRunAt,
        String message
    ) {
        public static OpenMetadataQualitySummary empty(String message) {
            return new OpenMetadataQualitySummary(false, false, null, 0, 0, 0, 0, 0, null, null, message);
        }
    }

    public record UsageSummary(UsageStats daily, UsageStats weekly, UsageStats monthly, String date) {}

    public record UsageStats(Integer count, Double percentileRank) {}
}
