package com.yuzhi.dts.platform.service.catalog;

import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Opt-in adapter for PostgreSQL relations explicitly tagged as DTS-managed copies. */
@Component
@Order(10)
public class ManagedPostgresCopyDestructionAdapter implements CatalogManagedCopyDestructionAdapter {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private final DataSource dataSource;
    private final boolean enabled;

    public ManagedPostgresCopyDestructionAdapter(
        DataSource dataSource,
        @Value("${dts.lifecycle.destruction.postgres-enabled:false}") boolean enabled
    ) {
        this.dataSource = dataSource;
        this.enabled = enabled;
    }

    @Override
    public boolean supports(CatalogDataset dataset) {
        if (dataset == null || dataset.getType() == null || dataset.getTags() == null) {
            return false;
        }
        String type = dataset.getType().trim().toLowerCase(Locale.ROOT);
        String tags = dataset.getTags().toLowerCase(Locale.ROOT);
        return List.of("jdbc", "postgres", "postgresql").contains(type) && tags.contains("dts-managed-copy");
    }

    @Override
    public DestructionResult destroy(CatalogDataset dataset, String actionRef) {
        if (!enabled) {
            return DestructionResult.blocked(
                "DTS_POSTGRES_MANAGED_COPY",
                "PostgreSQL managed-copy destruction is disabled by configuration"
            );
        }
        try {
            String schema = identifier(dataset.getHiveDatabase(), "schema");
            String table = identifier(dataset.getHiveTable(), "table");
            String qualified = quote(schema) + "." + quote(table);
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            String relkind = null;
            try (
                ResultSet result = statement.executeQuery(
                    "select c.relkind::text from pg_class c join pg_namespace n on n.oid=c.relnamespace " +
                    "where n.nspname='" + sqlLiteral(schema) + "' and c.relname='" + sqlLiteral(table) + "'"
                )
            ) {
                if (result.next()) {
                    relkind = result.getString(1);
                }
            }
            if ("v".equals(relkind) || "m".equals(relkind)) {
                statement.execute(("m".equals(relkind) ? "drop materialized view if exists " : "drop view if exists ") + qualified);
            } else {
                statement.execute("drop table if exists " + qualified);
            }
            return new DestructionResult(
                true,
                "DTS_POSTGRES_MANAGED_COPY",
                List.of("postgres:" + schema + "." + table),
                false,
                Map.of("relationKind", String.valueOf(relkind), "actionRef", actionRef),
                null
            );
            }
        } catch (Exception failure) {
            return DestructionResult.blocked("DTS_POSTGRES_MANAGED_COPY", failure.getMessage());
        }
    }

    private static String identifier(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Unsafe PostgreSQL " + label + " identifier");
        }
        return normalized;
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
