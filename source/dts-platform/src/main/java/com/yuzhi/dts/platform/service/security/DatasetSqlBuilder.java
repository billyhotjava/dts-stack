package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.DataLevelSqlHelper;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 负责构建带有行级密级过滤的样例查询 SQL，确保与人员密级判定一致。
 */
@Component
public class DatasetSqlBuilder {

    private static final int LIMIT_MIN = 1;
    private static final int LIMIT_MAX = 500;

    private final AccessChecker accessChecker;
    private final DatasetSecurityMetadataResolver metadataResolver;

    public DatasetSqlBuilder(
        AccessChecker accessChecker,
        DatasetSecurityMetadataResolver metadataResolver
    ) {
        this.accessChecker = accessChecker;
        this.metadataResolver = metadataResolver;
    }

    /**
     * 基于数据集信息构造带密级过滤的预览 SQL，直接访问底表并附带权限约束。
     */
    public String buildSampleQuery(CatalogDataset dataset, int requestedLimit) {
        if (dataset == null) {
            throw new IllegalArgumentException("dataset must not be null");
        }
        int limit = sanitizeLimit(requestedLimit);
        String type = resolveType(dataset);

        if ("POSTGRES".equals(type)) {
            return buildPostgresSelect(dataset, limit);
        }
        return buildHiveSelect(dataset, limit);
    }

    /**
     * 供其他组件复用的密级过滤条件（结合别名，例如 {@code t}）。
     */
    public Optional<String> resolveDataLevelPredicate(CatalogDataset dataset, String tableAlias) {
        QuoteDialect dialect = resolveDialect(dataset);
        return resolveDataLevelPredicate(dataset, tableAlias, dialect);
    }

    private int sanitizeLimit(int limit) {
        if (limit <= 0) {
            return LIMIT_MIN;
        }
        return Math.min(limit, LIMIT_MAX);
    }

    private String resolveType(CatalogDataset dataset) {
        if (!StringUtils.hasText(dataset.getType())) {
            return "";
        }
        return dataset.getType().trim().toUpperCase(Locale.ROOT);
    }

    private String buildHiveSelect(CatalogDataset dataset, int limit) {
        String table = resolveText(dataset.getHiveTable());
        if (table == null) {
            table = resolveText(dataset.getName());
        }
        if (table == null) {
            throw new IllegalStateException("数据集未配置 Hive 表名");
        }
        String qualified = qualifyHive(dataset.getHiveDatabase(), table);
        StringBuilder builder = new StringBuilder("SELECT * FROM ").append(qualified);
        // resolveDataLevelPredicate(dataset, null, QuoteDialect.HIVE).ifPresent(predicate -> builder.append(" WHERE ").append(predicate));
        builder.append(" LIMIT ").append(limit);
        return builder.toString();
    }

    private String buildPostgresSelect(CatalogDataset dataset, int limit) {
        String schema = resolveText(dataset.getHiveDatabase());
        String table = resolveText(dataset.getHiveTable());
        if (table == null) {
            table = resolveText(dataset.getName());
        }
        if (table == null) {
            throw new IllegalStateException("数据集未配置 PostgreSQL 表名");
        }
        String qualified = qualifyPostgres(schema, table);
        StringBuilder builder = new StringBuilder("SELECT * FROM ").append(qualified);
        // resolveDataLevelPredicate(dataset, null, QuoteDialect.POSTGRES).ifPresent(predicate -> builder.append(" WHERE ").append(predicate));
        builder.append(" LIMIT ").append(limit);
        return builder.toString();
    }

    private Optional<String> resolveDataLevelPredicate(CatalogDataset dataset, String tableAlias, QuoteDialect dialect) {
        List<DataLevel> allowedLevels = accessChecker.resolveAllowedDataLevels();
        if (allowedLevels == null || allowedLevels.isEmpty()) {
            return Optional.empty();
        }
        Optional<String> columnOpt = metadataResolver.findDataLevelColumn(dataset);
        if (columnOpt.isEmpty()) {
            return Optional.empty();
        }
        String columnName = columnOpt.orElseThrow();
        String quoted = quoteIdentifier(columnName, dialect);
        String columnExpression;
        if (StringUtils.hasText(tableAlias)) {
            columnExpression = tableAlias + "." + quoted;
        } else {
            columnExpression = quoted;
        }
        return Optional.ofNullable(DataLevelSqlHelper.buildPredicate(columnExpression, allowedLevels));
    }

    private String qualifyHive(String database, String table) {
        String tableName = quoteIdentifier(table, QuoteDialect.HIVE);
        if (StringUtils.hasText(database)) {
            return quoteIdentifier(database.trim(), QuoteDialect.HIVE) + "." + tableName;
        }
        return tableName;
    }

    private String qualifyPostgres(String schema, String table) {
        String tableName = quoteIdentifier(table, QuoteDialect.POSTGRES);
        if (StringUtils.hasText(schema)) {
            return quoteIdentifier(schema.trim(), QuoteDialect.POSTGRES) + "." + tableName;
        }
        return tableName;
    }

    private String quoteIdentifier(String identifier, QuoteDialect dialect) {
        if (!StringUtils.hasText(identifier)) {
            return identifier;
        }
        String trimmed = identifier.trim();
        return switch (dialect) {
            case HIVE -> "`" + trimmed.replace("`", "``") + "`";
            case POSTGRES -> "\"" + trimmed.replace("\"", "\"\"") + "\"";
        };
    }

    private String resolveText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private QuoteDialect resolveDialect(CatalogDataset dataset) {
        return "POSTGRES".equals(resolveType(dataset)) ? QuoteDialect.POSTGRES : QuoteDialect.HIVE;
    }

    private enum QuoteDialect {
        HIVE,
        POSTGRES
    }
}
