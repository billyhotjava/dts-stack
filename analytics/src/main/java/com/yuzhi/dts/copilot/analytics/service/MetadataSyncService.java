package com.yuzhi.dts.copilot.analytics.service;

import com.yuzhi.dts.copilot.analytics.domain.AnalyticsDatabase;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsField;
import com.yuzhi.dts.copilot.analytics.domain.AnalyticsTable;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsDatabaseRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsFieldRepository;
import com.yuzhi.dts.copilot.analytics.repository.AnalyticsTableRepository;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetadataSyncService {

    private final AnalyticsDatabaseRepository databaseRepository;
    private final AnalyticsTableRepository tableRepository;
    private final AnalyticsFieldRepository fieldRepository;
    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;

    public MetadataSyncService(
            AnalyticsDatabaseRepository databaseRepository,
            AnalyticsTableRepository tableRepository,
            AnalyticsFieldRepository fieldRepository,
            ExternalDatabaseDataSourceRegistry dataSourceRegistry) {
        this.databaseRepository = databaseRepository;
        this.tableRepository = tableRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRegistry = dataSourceRegistry;
    }

    @Transactional
    public SyncSummary syncDatabaseSchema(long databaseId) throws SQLException {
        AnalyticsDatabase database = databaseRepository
                .findById(databaseId)
                .orElseThrow(() -> new IllegalArgumentException("Database not found: " + databaseId));

        HikariDataSource dataSource = dataSourceRegistry.get(databaseId);
        List<DiscoveredTable> discoveredTables = discoverTablesAndFields(dataSource);

        Map<TableKey, AnalyticsTable> existingTablesByKey = new HashMap<>();
        for (AnalyticsTable table : tableRepository.findAllByDatabaseIdOrderBySchemaNameAscNameAsc(database.getId())) {
            existingTablesByKey.put(TableKey.of(table.getSchemaName(), table.getName()), table);
        }

        Set<TableKey> discoveredKeys = new HashSet<>();
        int createdTables = 0;
        int updatedTables = 0;

        Map<TableKey, AnalyticsTable> persistedTablesByKey = new HashMap<>(existingTablesByKey.size());
        for (DiscoveredTable discovered : discoveredTables) {
            String normalizedSchema = normalizeSchema(discovered.schemaName());
            TableKey key = TableKey.of(normalizedSchema, discovered.name());
            discoveredKeys.add(key);

            AnalyticsTable table = existingTablesByKey.get(key);
            if (table == null) {
                table = new AnalyticsTable();
                table.setDatabaseId(database.getId());
                table.setSchemaName(normalizedSchema);
                table.setName(discovered.name());
                table.setDisplayName(Optional.ofNullable(discovered.displayName()).orElse(discovered.name()));
                table.setActive(true);
                table.setVisibilityType("normal");
                table = tableRepository.save(table);
                createdTables += 1;
            } else {
                boolean changed = false;
                if (!Objects.equals(table.getSchemaName(), normalizedSchema)) {
                    table.setSchemaName(normalizedSchema);
                    changed = true;
                }
                if (!Objects.equals(table.getName(), discovered.name())) {
                    table.setName(discovered.name());
                    changed = true;
                }
                String newDisplayName = Optional.ofNullable(table.getDisplayName()).orElse(discovered.name());
                if (!Objects.equals(table.getDisplayName(), newDisplayName)) {
                    table.setDisplayName(newDisplayName);
                    changed = true;
                }
                if (!table.isActive()) {
                    table.setActive(true);
                    changed = true;
                }
                if (changed) {
                    table = tableRepository.save(table);
                    updatedTables += 1;
                }
            }
            persistedTablesByKey.put(key, table);
        }

        int disabledTables = 0;
        for (AnalyticsTable existing : existingTablesByKey.values()) {
            TableKey key = TableKey.of(existing.getSchemaName(), existing.getName());
            if (!discoveredKeys.contains(key) && existing.isActive()) {
                existing.setActive(false);
                tableRepository.save(existing);
                disabledTables += 1;
            }
        }

        int createdFields = 0;
        int updatedFields = 0;
        int disabledFields = 0;

        for (DiscoveredTable discovered : discoveredTables) {
            String normalizedSchema = normalizeSchema(discovered.schemaName());
            TableKey key = TableKey.of(normalizedSchema, discovered.name());
            AnalyticsTable table = persistedTablesByKey.get(key);
            if (table == null) {
                continue;
            }

            Map<String, AnalyticsField> existingFieldsByName = new HashMap<>();
            for (AnalyticsField field : fieldRepository.findAllByTableIdOrderByPositionAscIdAsc(table.getId())) {
                existingFieldsByName.put(field.getName(), field);
            }

            Set<String> discoveredFieldNames = new HashSet<>();
            for (DiscoveredField discoveredField : discovered.fields()) {
                discoveredFieldNames.add(discoveredField.name());

                AnalyticsField field = existingFieldsByName.get(discoveredField.name());
                if (field == null) {
                    field = new AnalyticsField();
                    field.setDatabaseId(database.getId());
                    field.setTableId(table.getId());
                    field.setName(discoveredField.name());
                    field.setDisplayName(Optional.ofNullable(discoveredField.displayName()).orElse(discoveredField.name()));
                    field.setBaseType(discoveredField.baseType());
                    field.setEffectiveType(Optional.ofNullable(discoveredField.effectiveType()).orElse(discoveredField.baseType()));
                    field.setPosition(discoveredField.position());
                    field.setActive(true);
                    field.setVisibilityType("normal");
                    fieldRepository.save(field);
                    createdFields += 1;
                } else {
                    boolean changed = false;
                    if (!Objects.equals(field.getDisplayName(), discoveredField.displayName())) {
                        field.setDisplayName(Optional.ofNullable(discoveredField.displayName()).orElse(discoveredField.name()));
                        changed = true;
                    }
                    if (!Objects.equals(field.getBaseType(), discoveredField.baseType())) {
                        field.setBaseType(discoveredField.baseType());
                        changed = true;
                    }
                    String effectiveType = Optional.ofNullable(discoveredField.effectiveType()).orElse(discoveredField.baseType());
                    if (!Objects.equals(field.getEffectiveType(), effectiveType)) {
                        field.setEffectiveType(effectiveType);
                        changed = true;
                    }
                    if (field.getPosition() != discoveredField.position()) {
                        field.setPosition(discoveredField.position());
                        changed = true;
                    }
                    if (!field.isActive()) {
                        field.setActive(true);
                        changed = true;
                    }
                    if (changed) {
                        fieldRepository.save(field);
                        updatedFields += 1;
                    }
                }
            }

            for (AnalyticsField existingField : existingFieldsByName.values()) {
                if (!discoveredFieldNames.contains(existingField.getName()) && existingField.isActive()) {
                    existingField.setActive(false);
                    fieldRepository.save(existingField);
                    disabledFields += 1;
                }
            }
        }

        return new SyncSummary(
                databaseId,
                createdTables,
                updatedTables,
                disabledTables,
                createdFields,
                updatedFields,
                disabledFields);
    }

    private static List<DiscoveredTable> discoverTablesAndFields(HikariDataSource dataSource) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();

            List<DiscoveredTable> tables = new ArrayList<>();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, "%", new String[] {"TABLE", "VIEW"})) {
                while (rs.next()) {
                    String schema = rs.getString("TABLE_SCHEM");
                    String name = rs.getString("TABLE_NAME");
                    String type = rs.getString("TABLE_TYPE");
                    if (name == null || name.isBlank()) {
                        continue;
                    }
                    if (isSystemSchema(schema)) {
                        continue;
                    }

                    List<DiscoveredField> fields = new ArrayList<>();
                    try (ResultSet columns = meta.getColumns(connection.getCatalog(), schema, name, "%")) {
                        while (columns.next()) {
                            String columnName = columns.getString("COLUMN_NAME");
                            if (columnName == null || columnName.isBlank()) {
                                continue;
                            }
                            int sqlType = columns.getInt("DATA_TYPE");
                            int position = columns.getInt("ORDINAL_POSITION");
                            String baseType = MetabaseTypeMapper.toBaseType(sqlType);
                            fields.add(new DiscoveredField(columnName, columnName, baseType, baseType, position));
                        }
                    }

                    String displayName = name;
                    if (schema != null && !schema.isBlank()) {
                        displayName = schema + "." + name;
                    }
                    if ("VIEW".equalsIgnoreCase(type)) {
                        displayName = displayName + " (view)";
                    }

                    tables.add(new DiscoveredTable(schema, name, displayName, fields));
                }
            }

            return tables;
        }
    }

    private static String normalizeSchema(String schema) {
        if (schema == null) {
            return "";
        }
        String trimmed = schema.trim();
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static boolean isSystemSchema(String schema) {
        if (schema == null) {
            return false;
        }
        String lower = schema.toLowerCase(Locale.ROOT);
        return "information_schema".equals(lower)
                || lower.startsWith("pg_")
                || "sys".equals(lower)
                || "mysql".equals(lower)
                || "performance_schema".equals(lower);
    }

    public record SyncSummary(
            long databaseId,
            int createdTables,
            int updatedTables,
            int disabledTables,
            int createdFields,
            int updatedFields,
            int disabledFields) {}

    private record DiscoveredTable(String schemaName, String name, String displayName, List<DiscoveredField> fields) {}

    private record DiscoveredField(String name, String displayName, String baseType, String effectiveType, int position) {}

    private record TableKey(String schemaName, String name) {
        static TableKey of(String schemaName, String name) {
            String normalizedSchema = schemaName == null ? "" : schemaName;
            String normalizedName = name == null ? "" : name;
            return new TableKey(normalizedSchema, normalizedName);
        }
    }

    private static final class MetabaseTypeMapper {
        private static final Map<Integer, String> BASE_TYPES = Map.ofEntries(
                Map.entry(java.sql.Types.BOOLEAN, "type/Boolean"),
                Map.entry(java.sql.Types.BIT, "type/Boolean"),
                Map.entry(java.sql.Types.TINYINT, "type/Integer"),
                Map.entry(java.sql.Types.SMALLINT, "type/Integer"),
                Map.entry(java.sql.Types.INTEGER, "type/Integer"),
                Map.entry(java.sql.Types.BIGINT, "type/BigInteger"),
                Map.entry(java.sql.Types.FLOAT, "type/Float"),
                Map.entry(java.sql.Types.REAL, "type/Float"),
                Map.entry(java.sql.Types.DOUBLE, "type/Float"),
                Map.entry(java.sql.Types.NUMERIC, "type/Decimal"),
                Map.entry(java.sql.Types.DECIMAL, "type/Decimal"),
                Map.entry(java.sql.Types.DATE, "type/Date"),
                Map.entry(java.sql.Types.TIME, "type/Time"),
                Map.entry(java.sql.Types.TIMESTAMP, "type/DateTime"),
                Map.entry(java.sql.Types.TIMESTAMP_WITH_TIMEZONE, "type/DateTimeWithLocalTZ"),
                Map.entry(java.sql.Types.CHAR, "type/Text"),
                Map.entry(java.sql.Types.VARCHAR, "type/Text"),
                Map.entry(java.sql.Types.LONGVARCHAR, "type/Text"),
                Map.entry(java.sql.Types.NVARCHAR, "type/Text"),
                Map.entry(java.sql.Types.LONGNVARCHAR, "type/Text"),
                Map.entry(java.sql.Types.CLOB, "type/Text"));

        static String toBaseType(int sqlType) {
            return BASE_TYPES.getOrDefault(sqlType, "type/Text");
        }
    }
}
