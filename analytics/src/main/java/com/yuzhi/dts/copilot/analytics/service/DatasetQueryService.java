package com.yuzhi.dts.copilot.analytics.service;

import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DatasetQueryService {

    private static final long JS_SAFE_INTEGER_MAX = 9_007_199_254_740_991L;
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final ExternalDatabaseDataSourceRegistry dataSourceRegistry;
    private final FederatedNativeSqlQualifier federatedNativeSqlQualifier;

    public DatasetQueryService(ExternalDatabaseDataSourceRegistry dataSourceRegistry) {
        this(dataSourceRegistry, FederatedNativeSqlQualifier.noop());
    }

    @Autowired
    public DatasetQueryService(
            ExternalDatabaseDataSourceRegistry dataSourceRegistry,
            FederatedNativeSqlQualifier federatedNativeSqlQualifier) {
        this.dataSourceRegistry = dataSourceRegistry;
        this.federatedNativeSqlQualifier =
                federatedNativeSqlQualifier == null ? FederatedNativeSqlQualifier.noop() : federatedNativeSqlQualifier;
    }

    public DatasetResult runNative(long databaseId, String sql, DatasetConstraints constraints) throws SQLException {
        return runNative(databaseId, sql, constraints, List.of());
    }

    public DatasetResult runNative(long databaseId, String sql, DatasetConstraints constraints, List<Object> bindings) throws SQLException {
        String executableSql = federatedNativeSqlQualifier.qualify(databaseId, sql);
        HikariDataSource dataSource = dataSourceRegistry.get(databaseId);
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(executableSql)) {
            if (constraints.queryTimeoutSeconds() > 0) {
                statement.setQueryTimeout(constraints.queryTimeoutSeconds());
            }
            if (constraints.maxResults() > 0) {
                statement.setMaxRows(constraints.maxResults());
            }

            if (bindings != null && !bindings.isEmpty()) {
                for (int i = 0; i < bindings.size(); i++) {
                    statement.setObject(i + 1, bindings.get(i));
                }
            }

            boolean hasResultSet = statement.execute();
            if (!hasResultSet) {
                return new DatasetResult(List.of(), List.of(), List.of(), constraints.resultsTimezone());
            }

            try (ResultSet rs = statement.getResultSet()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();

                List<ColumnSpec> columnSpecs = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    String label = meta.getColumnLabel(i);
                    if (label == null || label.isBlank()) {
                        label = meta.getColumnName(i);
                    }
                    String baseType = MetabaseTypes.toBaseType(meta.getColumnType(i));
                    columnSpecs.add(new ColumnSpec(label, baseType));
                }

                List<List<Object>> rows = new ArrayList<>();
                List<ColumnFingerprintBuilder> fingerprintBuilders = new ArrayList<>(columnCount);
                for (int i = 0; i < columnCount; i++) {
                    fingerprintBuilders.add(new ColumnFingerprintBuilder(columnSpecs.get(i).baseType()));
                }

                while (rs.next()) {
                    List<Object> row = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        Object raw = rs.getObject(i);
                        Object value = normalizeValue(raw);
                        row.add(value);
                        fingerprintBuilders.get(i - 1).accept(value);
                    }
                    rows.add(row);
                }

                List<Map<String, Object>> cols = columnSpecs.stream().map(ColumnSpec::toMetabaseCol).toList();
                List<Map<String, Object>> resultsMetadataColumns = new ArrayList<>(columnCount);
                for (int i = 0; i < columnCount; i++) {
                    resultsMetadataColumns.add(columnSpecs.get(i).toMetabaseResultsMetadata(fingerprintBuilders.get(i).build()));
                }

                return new DatasetResult(rows, cols, resultsMetadataColumns, constraints.resultsTimezone());
            }
        }
    }

    private static Object normalizeValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer || raw instanceof Short || raw instanceof Byte) {
            return raw;
        }
        if (raw instanceof Long value) {
            if (Math.abs(value) > JS_SAFE_INTEGER_MAX) {
                return Long.toString(value);
            }
            return value;
        }
        if (raw instanceof BigInteger value) {
            try {
                long asLong = value.longValueExact();
                if (Math.abs(asLong) > JS_SAFE_INTEGER_MAX) {
                    return value.toString();
                }
                return asLong;
            } catch (ArithmeticException ignore) {
                return value.toString();
            }
        }
        if (raw instanceof BigDecimal value) {
            return value;
        }
        if (raw instanceof java.sql.Date date) {
            LocalDate localDate = date.toLocalDate();
            return localDate.toString();
        }
        if (raw instanceof Timestamp timestamp) {
            Instant instant = timestamp.toInstant();
            return ISO_INSTANT.format(instant);
        }
        if (raw instanceof Time time) {
            return time.toLocalTime().toString();
        }
        if (raw instanceof Boolean) {
            return raw;
        }
        return raw.toString();
    }

    public record DatasetConstraints(int maxResults, int queryTimeoutSeconds, String resultsTimezone) {

        public static DatasetConstraints defaults() {
            return new DatasetConstraints(2000, 60, ZoneId.systemDefault().getId());
        }
    }

    public record DatasetResult(
            List<List<Object>> rows,
            List<Map<String, Object>> cols,
            List<Map<String, Object>> resultsMetadataColumns,
            String resultsTimezone) {}

    private record ColumnSpec(String name, String baseType) {

        Map<String, Object> toMetabaseCol() {
            return Map.of(
                    "display_name",
                    name,
                    "source",
                    "native",
                    "field_ref",
                    List.of("field", name, Map.of("base-type", baseType)),
                    "name",
                    name,
                    "base_type",
                    baseType,
                    "effective_type",
                    baseType);
        }

        Map<String, Object> toMetabaseResultsMetadata(Map<String, Object> fingerprint) {
            Map<String, Object> column = new HashMap<>();
            column.put("display_name", name);
            column.put("field_ref", List.of("field", name, Map.of("base-type", baseType)));
            column.put("name", name);
            column.put("base_type", baseType);
            column.put("effective_type", baseType);
            column.put("semantic_type", null);
            column.put("fingerprint", fingerprint);
            return column;
        }
    }

    private static final class ColumnFingerprintBuilder {

        private final String baseType;
        private int total;
        private int nullCount;
        private final Set<String> distinct = new HashSet<>();

        private Double numericMin;
        private Double numericMax;
        private double numericSum;
        private int numericCount;

        private ColumnFingerprintBuilder(String baseType) {
            this.baseType = baseType;
        }

        void accept(Object value) {
            total += 1;
            if (value == null) {
                nullCount += 1;
                return;
            }
            distinct.add(value.toString());

            if (!MetabaseTypes.isNumericBaseType(baseType)) {
                return;
            }

            Double numeric = toDouble(value);
            if (numeric == null) {
                return;
            }
            numericSum += numeric;
            numericCount += 1;
            if (numericMin == null || numeric < numericMin) {
                numericMin = numeric;
            }
            if (numericMax == null || numeric > numericMax) {
                numericMax = numeric;
            }
        }

        Map<String, Object> build() {
            Map<String, Object> global = new HashMap<>();
            global.put("distinct-count", distinct.size());
            if (total <= 0) {
                global.put("nil%", 0.0);
            } else {
                global.put("nil%", (double) nullCount / (double) total);
            }

            Map<String, Object> fingerprint = new HashMap<>();
            fingerprint.put("global", global);

            if (!MetabaseTypes.isNumericBaseType(baseType) || numericCount <= 0) {
                fingerprint.put("type", Map.of());
                return fingerprint;
            }

            Map<String, Object> numberStats = new HashMap<>();
            numberStats.put("min", numericMin);
            numberStats.put("q1", numericMin);
            numberStats.put("q3", numericMax);
            numberStats.put("max", numericMax);
            numberStats.put("sd", null);
            numberStats.put("avg", numericSum / (double) numericCount);

            fingerprint.put("type", Map.of("type/Number", numberStats));
            return fingerprint;
        }

        private static Double toDouble(Object value) {
            if (value instanceof Number n) {
                return n.doubleValue();
            }
            try {
                return Double.valueOf(value.toString());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
    }

    private static final class MetabaseTypes {
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

        static boolean isNumericBaseType(String baseType) {
            return "type/Integer".equals(baseType)
                    || "type/BigInteger".equals(baseType)
                    || "type/Float".equals(baseType)
                    || "type/Decimal".equals(baseType);
        }
    }
}
