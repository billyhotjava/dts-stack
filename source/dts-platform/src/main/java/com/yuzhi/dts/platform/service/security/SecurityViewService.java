package com.yuzhi.dts.platform.service.security;

import com.yuzhi.dts.platform.domain.catalog.CatalogAccessPolicy;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.catalog.CatalogSecureView;
import com.yuzhi.dts.platform.repository.catalog.CatalogColumnSchemaRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogSecureViewRepository;
import com.yuzhi.dts.platform.repository.catalog.CatalogTableSchemaRepository;
import com.yuzhi.dts.platform.security.policy.DataLevel;
import com.yuzhi.dts.platform.security.policy.DataLevelSqlHelper;
import com.yuzhi.dts.platform.service.security.dto.SecurityViewExecutionResult;
import com.yuzhi.dts.platform.service.security.dto.StatementExecutionResult;
import com.yuzhi.dts.platform.service.security.DatasetSecurityMetadataResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityViewService {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityViewService.class);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SQL_KEYWORDS = Set.of(
        "and",
        "or",
        "not",
        "is",
        "null",
        "in",
        "like",
        "between",
        "exists",
        "case",
        "when",
        "then",
        "else",
        "end",
        "true",
        "false",
        "select",
        "from",
        "where",
        "join",
        "inner",
        "left",
        "right",
        "outer",
        "on",
        "distinct",
        "group",
        "by",
        "order",
        "asc",
        "desc",
        "union",
        "all",
        "any",
        "some",
        "as",
        "coalesce",
        "cast",
        "substr",
        "substring",
        "length",
        "upper",
        "lower",
        "trim",
        "sum",
        "count",
        "avg",
        "min",
        "max",
        "current_date",
        "current_timestamp",
        "date_add",
        "date_sub",
        "if",
        "elseif"
    );
    private static final Set<String> FORBIDDEN_TOKENS = Set.of(
        "scope",
        "share_scope"
    );

    private final CatalogSecureViewRepository viewRepository;
    private final HiveStatementExecutor hiveStatementExecutor;
    private final CatalogTableSchemaRepository tableRepository;
    private final CatalogColumnSchemaRepository columnRepository;

    public SecurityViewService(
        CatalogSecureViewRepository viewRepository,
        HiveStatementExecutor hiveStatementExecutor,
        CatalogTableSchemaRepository tableRepository,
        CatalogColumnSchemaRepository columnRepository
    ) {
        this.viewRepository = viewRepository;
        this.hiveStatementExecutor = hiveStatementExecutor;
        this.tableRepository = tableRepository;
        this.columnRepository = columnRepository;
    }

    /**
     * Build preview SQLs for a dataset according to policy under the "view route" (backup plan when no Ranger).
     * This does not execute DDL; it only returns statements for operators to apply on Hive/Trino side.
     */
    public Map<String, String> previewViews(CatalogDataset ds, CatalogAccessPolicy policy) {
        String table = ds.getHiveTable() != null && !ds.getHiveTable().isBlank() ? ds.getHiveTable() : ds.getName();
        String db = ds.getHiveDatabase();
        String trino = ds.getTrinoCatalog();
        String source = (trino != null && !trino.isBlank() ? (trino + ".") : "") +
            (db != null && !db.isBlank() ? (db + ".") : "") + table;
        Map<String, String> sql = new LinkedHashMap<>();

        // Resolve available columns for conditional filtering
        ColumnMetadata metadata = resolveColumns(ds, table);
        String dataLevelColumn = metadata.dataLevelColumn();
        boolean hasDataLevel = dataLevelColumn != null && metadata.columnMap().containsKey(dataLevelColumn.toLowerCase(Locale.ROOT));

        String selectCols = "*";
        String rowFilter = policy != null && policy.getRowFilter() != null ? policy.getRowFilter().trim() : null;
        rowFilter = sanitizeRowFilter(ds, rowFilter, metadata);

        sql.put("sv_" + table + "_public",
            "CREATE OR REPLACE VIEW sv_" + table + "_public AS\n" +
            "SELECT " + selectCols + " FROM " + source + whereClause(
                hasDataLevel ? levelPredicate(dataLevelColumn, List.of(DataLevel.DATA_PUBLIC)) : null,
                rowFilter
            ));
        sql.put("sv_" + table + "_internal",
            "CREATE OR REPLACE VIEW sv_" + table + "_internal AS\n" +
            "SELECT " + selectCols + " FROM " + source + whereClause(
                hasDataLevel ? levelPredicate(dataLevelColumn, List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL)) : null,
                rowFilter
            ));
        sql.put("sv_" + table + "_secret",
            "CREATE OR REPLACE VIEW sv_" + table + "_secret AS\n" +
            "SELECT " + selectCols + " FROM " + source + whereClause(
                hasDataLevel
                    ? levelPredicate(
                        dataLevelColumn,
                        List.of(DataLevel.DATA_PUBLIC, DataLevel.DATA_INTERNAL, DataLevel.DATA_SECRET)
                    )
                    : null,
                rowFilter
            ));
        sql.put("sv_" + table + "_top_secret",
            "CREATE OR REPLACE VIEW sv_" + table + "_top_secret AS\n" +
            "SELECT " + selectCols + " FROM " + source + whereClause(null, rowFilter));

        // GRANT example
        sql.put("grant_public", "GRANT SELECT ON TABLE sv_" + table + "_public TO ROLE_PUBLIC");
        sql.put("grant_internal", "GRANT SELECT ON TABLE sv_" + table + "_internal TO ROLE_INTERNAL");
        sql.put("grant_secret", "GRANT SELECT ON TABLE sv_" + table + "_secret TO ROLE_SECRET");
        sql.put("grant_top_secret", "GRANT SELECT ON TABLE sv_" + table + "_top_secret TO ROLE_TOP_SECRET");

        return sql;
    }

    @Transactional
    public SecurityViewExecutionResult applyViews(CatalogDataset dataset, CatalogAccessPolicy policy, String refreshOption) {
        Map<String, String> statements = previewViews(dataset, policy);
        String schemaHint = dataset.getHiveDatabase();
        List<StatementExecutionResult> executionResults = hiveStatementExecutor.execute(statements, schemaHint);
        boolean hasFailure = executionResults
            .stream()
            .anyMatch(r -> r.status() != StatementExecutionResult.Status.SUCCEEDED);
        boolean success = !hasFailure;

        int persisted = 0;
        if (success) {
            List<CatalogSecureView> existing = viewRepository.findByDataset(dataset);
            if (!existing.isEmpty()) {
                viewRepository.deleteAll(existing);
            }
            List<CatalogSecureView> toSave = new ArrayList<>();
            for (Map.Entry<String, String> entry : statements.entrySet()) {
                if (!entry.getKey().startsWith("sv_")) {
                    continue; // skip grant helper rows
                }
                CatalogSecureView view = new CatalogSecureView();
                view.setDataset(dataset);
                view.setViewName(entry.getKey());
                view.setLevel(resolveLevel(entry.getKey()));
                view.setRefresh(refreshOption != null ? refreshOption : "NONE");
                view.setRowFilter(policy != null ? policy.getRowFilter() : null);
                toSave.add(view);
            }
            if (!toSave.isEmpty()) {
                viewRepository.saveAll(toSave);
                persisted = toSave.size();
            }
        }
        return new SecurityViewExecutionResult(Map.copyOf(statements), List.copyOf(executionResults), persisted, success);
    }

    public ColumnMetadata resolveColumns(CatalogDataset dataset, String tableName) {
        try {
            Optional<com.yuzhi.dts.platform.domain.catalog.CatalogTableSchema> tableOpt = tableRepository.findFirstByDatasetAndNameIgnoreCase(
                dataset,
                tableName
            );
            if (tableOpt.isEmpty()) {
                return ColumnMetadata.empty();
            }
            var table = tableOpt.orElseThrow();
            var columns = columnRepository.findByTable(table);
            if (columns.isEmpty()) {
                return ColumnMetadata.empty();
            }
            Map<String, String> nameMap = new HashMap<>();
            for (var col : columns) {
                if (col.getName() == null) continue;
                nameMap.put(col.getName().toLowerCase(Locale.ROOT), col.getName());
            }
            String dataLevelColumn = DatasetSecurityMetadataResolver.resolveFromColumns(nameMap).orElse(null);
            return new ColumnMetadata(nameMap, dataLevelColumn);
        } catch (Exception ex) {
            return ColumnMetadata.empty();
        }
    }

    private String quoteIdentifier(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        return "`" + name.replace("`", "``") + "`";
    }

    private String levelPredicate(String column, List<DataLevel> levels) {
        if (column == null) {
            return null;
        }
        return DataLevelSqlHelper.buildPredicate(quoteIdentifier(column), levels);
    }

    private String whereClause(String levelCondition, String rowFilter) {
        List<String> conditions = new ArrayList<>();
        if (levelCondition != null && !levelCondition.isBlank()) {
            conditions.add(levelCondition);
        }
        if (rowFilter != null && !rowFilter.isBlank()) {
            conditions.add("(" + rowFilter + ")");
        }
        if (conditions.isEmpty()) {
            return "";
        }
        return " WHERE " + String.join(" AND ", conditions);
    }

    private String resolveLevel(String key) {
        if (key.endsWith("_public")) {
            return "PUBLIC";
        }
        if (key.endsWith("_internal")) {
            return "INTERNAL";
        }
        if (key.endsWith("_secret")) {
            return "SECRET";
        }
        return "TOP_SECRET";
    }

    public String sanitizeRowFilter(CatalogDataset dataset, String rowFilter, ColumnMetadata metadata) {
        if (rowFilter == null || rowFilter.isBlank()) {
            return null;
        }
        if (metadata == null || metadata.columnMap().isEmpty()) {
            LOG.debug("Dropping row filter for dataset {} because column metadata is unavailable", dataset.getName());
            return null;
        }
        Map<String, String> columnMap = metadata.columnMap();
        Set<String> allowed = new HashSet<>(columnMap.keySet());
        if (dataset.getHiveTable() != null) {
            allowed.add(dataset.getHiveTable().toLowerCase(Locale.ROOT));
        }
        if (dataset.getName() != null) {
            allowed.add(dataset.getName().toLowerCase(Locale.ROOT));
        }
        String stripped = rowFilter.replaceAll("'([^']|'')*'", " ").replaceAll("\"([^\"]|\"\")*\"", " ");
        Matcher matcher = TOKEN_PATTERN.matcher(stripped);
        while (matcher.find()) {
            String token = matcher.group();
            String normalized = token.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_TOKENS.contains(normalized)) {
                LOG.warn(
                    "Dropping row filter '{}' for dataset {} because token '{}' is no longer supported",
                    rowFilter,
                    dataset.getName(),
                    token
                );
                return null;
            }
            if (allowed.contains(normalized) || SQL_KEYWORDS.contains(normalized)) {
                continue;
            }
            LOG.warn(
                "Discarding row filter '{}' for dataset {} because column '{}' is not present in metadata ({})",
                rowFilter,
                dataset.getName(),
                token,
                columnMap.values()
            );
            return null;
        }
        return rowFilter;
    }

    private record ColumnMetadata(Map<String, String> columnMap, String dataLevelColumn) {
        static ColumnMetadata empty() {
            return new ColumnMetadata(Map.of(), null);
        }
    }
}
