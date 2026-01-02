package com.yuzhi.dts.platform.service.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.service.SvcApi;
import com.yuzhi.dts.platform.repository.catalog.CatalogDatasetRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiMetricHourlyRepository;
import com.yuzhi.dts.platform.repository.service.SvcApiRepository;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.DataLevelSqlHelper;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import com.yuzhi.dts.platform.service.query.QueryGateway;
import com.yuzhi.dts.platform.service.security.CatalogMaskingService;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import com.yuzhi.dts.platform.service.security.DatasetSqlBuilder;
import com.yuzhi.dts.platform.service.services.SvcTokenAuthService.TokenPrincipal;
import com.yuzhi.dts.platform.service.services.dto.ApiFieldDto;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SvcApiQueryService {

    public record QueryResult(UUID apiId, Map<String, Object> payload, int maskedColumns, String effectiveSql) {}

    private static final int LIMIT_DEFAULT = 100;
    private static final int LIMIT_MAX = 5000;
    private static final int BATCH_MAX = 50;
    private static final int FILTER_VALUES_MAX = 50;

    private final SvcApiRepository apiRepository;
    private final CatalogDatasetRepository datasetRepository;
    private final DatasetSecurityMetadataResolver metadataResolver;
    private final DatasetSqlBuilder datasetSqlBuilder;
    private final QueryGateway queryGateway;
    private final CatalogMaskingService maskingService;
    private final SvcApiMetricService metricService;
    private final SvcApiMetricHourlyRepository metricRepository;
    private final SvcApiRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public SvcApiQueryService(
        SvcApiRepository apiRepository,
        CatalogDatasetRepository datasetRepository,
        DatasetSecurityMetadataResolver metadataResolver,
        DatasetSqlBuilder datasetSqlBuilder,
        QueryGateway queryGateway,
        CatalogMaskingService maskingService,
        SvcApiMetricService metricService,
        SvcApiMetricHourlyRepository metricRepository,
        SvcApiRateLimiter rateLimiter,
        ObjectMapper objectMapper
    ) {
        this.apiRepository = apiRepository;
        this.datasetRepository = datasetRepository;
        this.metadataResolver = metadataResolver;
        this.datasetSqlBuilder = datasetSqlBuilder;
        this.queryGateway = queryGateway;
        this.maskingService = maskingService;
        this.metricService = metricService;
        this.metricRepository = metricRepository;
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QueryResult query(String apiCode, Map<String, Object> request, TokenPrincipal principal) {
        SvcApi api = apiRepository
            .findFirstByCodeIgnoreCase(apiCode)
            .orElseThrow(() -> new EntityNotFoundException("API not found"));
        if (!"PUBLISHED".equalsIgnoreCase(StringUtils.trimAllWhitespace(api.getStatus()))) {
            throw new IllegalStateException("API未发布或已下线");
        }
        if (api.getDatasetId() == null) {
            throw new IllegalStateException("API未绑定数据集");
        }
        CatalogDataset dataset = datasetRepository.findById(api.getDatasetId()).orElseThrow(() -> new IllegalStateException("数据集不存在"));
        enforceQuota(api, principal);

        List<ApiFieldDto> fields = parseFields(api.getResponseSchemaJson());
        if (fields.isEmpty()) {
            throw new IllegalStateException("API未配置返回字段");
        }
        Set<String> allowedColumns = new LinkedHashSet<>();
        for (ApiFieldDto f : fields) {
            if (f != null && StringUtils.hasText(f.name())) {
                allowedColumns.add(f.name().trim());
            }
        }

        Map<String, Object> params = extractParams(request);
        int limit = extractInt(request, "limit", extractInt(request, "rowLimit", LIMIT_DEFAULT));
        int offset = extractInt(request, "offset", 0);
        int safeLimit = Math.max(1, Math.min(limit, LIMIT_MAX));
        int safeOffset = Math.max(0, offset);

        String effectiveSql = buildSelectSql(api, dataset, principal, fields, allowedColumns, params, safeLimit, safeOffset, request);
        Map<String, Object> result = queryGateway.execute(effectiveSql);

        List<String> headers = asStringList(result.get("headers"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = result.get("rows") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();

        CatalogMaskingService.Context maskingCtx = maskingService.resolveContext(dataset, headers);
        maskingService.applyMasking(maskingCtx, headers, rows);
        int maskedColumns = maskingCtx != null && maskingCtx.maskedColumns() != null ? maskingCtx.maskedColumns().size() : 0;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", api.getCode());
        payload.put("name", api.getName());
        payload.put("datasetId", api.getDatasetId() != null ? api.getDatasetId().toString() : null);
        payload.put("datasetName", api.getDatasetName());
        payload.put("headers", headers);
        payload.put("rows", rows);
        payload.put("rowCount", result.get("rowCount"));
        payload.put("maskedColumns", maskingCtx != null ? maskingCtx.maskedColumns() : List.of());
        payload.put("effectiveSql", effectiveSql);
        return new QueryResult(api.getId(), payload, maskedColumns, effectiveSql);
    }

    @Transactional
    public Map<String, Object> batchQuery(String apiCode, List<Map<String, Object>> batch, TokenPrincipal principal) {
        if (batch == null) {
            batch = List.of();
        }
        if (batch.size() > BATCH_MAX) {
            throw new IllegalArgumentException("批量查询条数超过限制：" + BATCH_MAX);
        }
        List<Map<String, Object>> results = new ArrayList<>();
        int maskedSum = 0;
        for (int i = 0; i < batch.size(); i++) {
            Map<String, Object> one = batch.get(i);
            QueryResult qr = query(apiCode, one, principal);
            maskedSum += qr.maskedColumns();
            results.add(Map.of("index", i, "data", qr.payload()));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", results.size());
        payload.put("maskedColumns", maskedSum);
        payload.put("results", results);
        return payload;
    }

    @Transactional
    public void recordSuccess(UUID apiId, int maskedColumns) {
        metricService.recordCall(apiId, maskedColumns, false);
    }

    @Transactional
    public void recordDenied(UUID apiId) {
        metricService.recordCall(apiId, 0, true);
    }

    private void enforceQuota(SvcApi api, TokenPrincipal principal) {
        UUID apiId = api.getId();
        int qpsLimit = api.getQpsLimit() != null ? api.getQpsLimit() : 0;
        if (!rateLimiter.allow(apiId, qpsLimit)) {
            recordDenied(apiId);
            throw new IllegalStateException("请求过于频繁，请稍后再试");
        }

        int dailyLimit = api.getDailyLimit() != null ? api.getDailyLimit() : 0;
        if (dailyLimit > 0) {
            Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
            long calls = metricRepository.sumCallsSince(apiId, dayStart);
            if (calls >= dailyLimit) {
                recordDenied(apiId);
                throw new IllegalStateException("已达到当日调用上限");
            }
        }

        // Enforce dept context for row-level filtering (required by current policy).
        if (principal == null || !StringUtils.hasText(principal.deptCode())) {
            recordDenied(apiId);
            throw new IllegalStateException("访问令牌未绑定部门信息，无法执行查询");
        }
    }

    private String buildSelectSql(
        SvcApi api,
        CatalogDataset dataset,
        TokenPrincipal principal,
        List<ApiFieldDto> fields,
        Set<String> allowedColumns,
        Map<String, Object> params,
        int limit,
        int offset,
        Map<String, Object> request
    ) {
        String alias = "t";
        String from = qualifyDatasetTable(dataset);

        List<String> projections = new ArrayList<>();
        for (ApiFieldDto f : fields) {
            String name = f != null ? StringUtils.trimToNull(f.name()) : null;
            if (!StringUtils.hasText(name)) continue;
            ensureSafeIdentifier(name);
            projections.add(alias + "." + datasetSqlBuilder.quoteColumn(dataset, name));
        }
        if (projections.isEmpty()) {
            throw new IllegalStateException("API未配置可用的返回字段");
        }

        List<String> predicates = new ArrayList<>();

        // Data level predicate
        DatasetSecurityMetadataResolver.ResolvedColumn dataLevelInfo = metadataResolver
            .findDataLevelColumnInfo(dataset)
            .orElseThrow(() -> new IllegalStateException("数据集缺少数据密级字段，无法对外提供查询服务"));
        String dataLevelCol = dataLevelInfo.name();
        String dataExpr = alias + "." + datasetSqlBuilder.quoteColumn(dataset, dataLevelCol);
        PersonnelLevel personnel = principal != null ? principal.personnelLevel() : PersonnelLevel.GENERAL;
        List<DataLevel> allowedLevels = personnel.allowedDataLevels();
        String dataPredicate = DataLevelSqlHelper.buildPredicate(dataExpr, allowedLevels, dataLevelInfo.numeric());
        if (!StringUtils.hasText(dataPredicate)) {
            throw new IllegalStateException("无法生成数据密级过滤条件");
        }
        predicates.add(dataPredicate);

        // Department predicate
        String deptPredicate = datasetSqlBuilder
            .resolveDeptPredicate(dataset, alias, principal.deptCode())
            .orElseThrow(() -> new IllegalStateException("数据集缺少部门字段或部门上下文无效，无法对外提供查询服务"));
        predicates.add(deptPredicate);

        // User filters
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = StringUtils.trimToNull(entry.getKey());
            if (!StringUtils.hasText(key)) continue;
            if (!allowedColumns.contains(key)) {
                continue;
            }
            ensureSafeIdentifier(key);
            String expr = alias + "." + datasetSqlBuilder.quoteColumn(dataset, key);
            String predicate = buildParamPredicate(expr, entry.getValue(), dataset);
            if (StringUtils.hasText(predicate)) {
                predicates.add(predicate);
            }
        }

        StringBuilder sql = new StringBuilder("SELECT ").append(String.join(", ", projections)).append(" FROM ").append(from).append(" ").append(alias);
        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }

        appendSorting(sql, dataset, allowedColumns, request, alias);

        if (offset > 0) {
            sql.append(" OFFSET ").append(offset);
        }
        sql.append(" LIMIT ").append(limit);
        return sql.toString();
    }

    private void appendSorting(StringBuilder sql, CatalogDataset dataset, Set<String> allowedColumns, Map<String, Object> request, String alias) {
        Object sorted = request != null ? request.get("sorted") : null;
        if (!(sorted instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<String> orders = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) continue;
            Object field = m.get("field");
            if (field == null) field = m.get("fieldName");
            if (field == null) field = m.get("column");
            String name = field != null ? StringUtils.trimToNull(String.valueOf(field)) : null;
            if (!StringUtils.hasText(name) || !allowedColumns.contains(name)) continue;
            ensureSafeIdentifier(name);
            String dir = m.get("direction") != null ? String.valueOf(m.get("direction")) : (m.get("order") != null ? String.valueOf(m.get("order")) : "ASC");
            String upper = dir.trim().toUpperCase(Locale.ROOT);
            String normalized = "DESC".equals(upper) ? "DESC" : "ASC";
            orders.add(alias + "." + datasetSqlBuilder.quoteColumn(dataset, name) + " " + normalized);
        }
        if (!orders.isEmpty()) {
            sql.append(" ORDER BY ").append(String.join(", ", orders));
        }
    }

    private String buildParamPredicate(String columnExpression, Object value, CatalogDataset dataset) {
        if (value == null) {
            return columnExpression + " IS NULL";
        }
        if (value instanceof Number n) {
            return columnExpression + " = " + n;
        }
        if (value instanceof Boolean b) {
            return columnExpression + " = " + (b ? "TRUE" : "FALSE");
        }
        if (value instanceof List<?> list) {
            List<String> tokens = new ArrayList<>();
            for (Object v : list) {
                if (v == null) continue;
                if (tokens.size() >= FILTER_VALUES_MAX) break;
                if (v instanceof Number n) {
                    tokens.add(String.valueOf(n));
                } else {
                    tokens.add("'" + escapeSql(String.valueOf(v)) + "'");
                }
            }
            if (tokens.isEmpty()) {
                return null;
            }
            return columnExpression + " IN (" + String.join(",", tokens) + ")";
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        // Case-insensitive exact match for strings
        return "UPPER(TRIM(" + columnExpression + ")) = UPPER('" + escapeSql(text) + "')";
    }

    private Map<String, Object> extractParams(Map<String, Object> request) {
        if (request == null) {
            return Map.of();
        }
        Object filters = request.get("filters");
        if (filters instanceof Map<?, ?> m) {
            return toStringObjectMap(m);
        }
        Object params = request.get("params");
        if (params instanceof Map<?, ?> m) {
            return toStringObjectMap(m);
        }
        return Map.of();
    }

    private Map<String, Object> toStringObjectMap(Map<?, ?> raw) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            if (e.getKey() == null) continue;
            String k = String.valueOf(e.getKey());
            out.put(k, e.getValue());
        }
        return out;
    }

    private int extractInt(Map<String, Object> request, String key, int defaultValue) {
        if (request == null || key == null) return defaultValue;
        Object v = request.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            String s = String.valueOf(v).trim();
            if (!s.isEmpty() && s.chars().allMatch(Character::isDigit)) {
                try {
                    return Integer.parseInt(s);
                } catch (Exception ignored) {}
            }
        }
        return defaultValue;
    }

    private List<String> asStringList(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item == null) continue;
                out.add(String.valueOf(item));
            }
            return out;
        }
        return List.of();
    }

    private String escapeSql(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private void ensureSafeIdentifier(String identifier) {
        String text = identifier != null ? identifier.trim() : "";
        if (text.isEmpty() || text.length() > 128) {
            throw new IllegalArgumentException("字段名不合法");
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ';' || c == '\'' || c == '"' || c == '`' || c == '\n' || c == '\r' || c == '\t') {
                throw new IllegalArgumentException("字段名不合法");
            }
        }
    }

    private List<ApiFieldDto> parseFields(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            List<ApiFieldDto> list = new ArrayList<>();
            for (JsonNode item : node) {
                String name = textValue(item, "name");
                if (!StringUtils.hasText(name)) continue;
                String type = textValue(item, "type");
                boolean masked = item.path("masked").asBoolean(false);
                String description = textValue(item, "description");
                list.add(new ApiFieldDto(name, type, masked, description));
            }
            return list;
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText(null);
    }

    private String qualifyDatasetTable(CatalogDataset dataset) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset must not be null");
        }
        boolean postgres = dataset.getType() != null && dataset.getType().trim().equalsIgnoreCase("POSTGRES");
        String schema = trimToNull(dataset.getHiveDatabase());
        String table = trimToNull(dataset.getHiveTable());
        if (table == null) {
            table = trimToNull(dataset.getName());
        }
        if (table == null) {
            throw new IllegalStateException("数据集未配置表名");
        }
        String tableName = quoteIdentifier(table, postgres);
        if (schema != null) {
            return quoteIdentifier(schema, postgres) + "." + tableName;
        }
        return tableName;
    }

    private String quoteIdentifier(String identifier, boolean postgres) {
        String trimmed = identifier.trim();
        if (postgres) {
            return "\"" + trimmed.replace("\"", "\"\"") + "\"";
        }
        return "`" + trimmed.replace("`", "``") + "`";
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
