package com.yuzhi.dts.platform.service.modeling.serving;

import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.QueryResult;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** PostgreSQL-only evidence-bound row query adapter. */
@Component
public class PostgresPhysicalPreviewQueryAdapter implements PhysicalPreviewQueryPort {

    private static final int QUERY_TIMEOUT_SECONDS = 5;

    private final DbtTargetConnectionFactory targets;
    private final Clock clock;

    @Autowired
    public PostgresPhysicalPreviewQueryAdapter(DbtTargetConnectionFactory targets) {
        this(targets, Clock.systemUTC());
    }

    public PostgresPhysicalPreviewQueryAdapter(DbtTargetConnectionFactory targets, Clock clock) {
        this.targets = targets;
        this.clock = clock;
    }

    @Override
    public Instant verifyStructure(RelationEvidence evidence) {
        return withVerifiedRelation(evidence, connection -> clock.instant());
    }

    @Override
    public QueryResult query(RelationEvidence evidence, List<String> selectedColumns, Integer requestedLimit) {
        requireIdentifier(evidence.schemaName());
        requireIdentifier(evidence.identifier());
        evidence.columns().forEach(column -> requireIdentifier(column.name()));
        if (
            selectedColumns == null || selectedColumns.isEmpty() ||
            selectedColumns.stream().anyMatch(column ->
                !evidence.columns().stream().map(PhysicalColumn::name).toList().contains(column)
            )
        ) {
            throw error("PHYSICAL_PREVIEW_MASKING_UNAVAILABLE", HttpStatus.LOCKED);
        }
        selectedColumns.forEach(PostgresPhysicalPreviewQueryAdapter::requireIdentifier);
        int limit = requestedLimit == null ? 100 : requestedLimit;
        return withVerifiedRelation(evidence, connection -> sample(connection, evidence, selectedColumns, limit));
    }

    private <T> T withVerifiedRelation(RelationEvidence evidence, VerifiedRelationAction<T> action) {
        requireIdentifier(evidence.schemaName());
        requireIdentifier(evidence.identifier());
        evidence.columns().forEach(column -> requireIdentifier(column.name()));
        try {
            RuntimeTarget target = targets.resolveRuntimeTarget();
            requireTarget(target, evidence);
            try (Connection connection = targets.open(target)) {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                if (!Objects.equals(target.database(), connection.getCatalog())) {
                    throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
                }
                lockRelation(connection, evidence);
                ExpectedRelationType actualType = readRelationType(connection, evidence);
                if (actualType == null) {
                    throw error("PHYSICAL_PREVIEW_RELATION_NOT_FOUND", HttpStatus.NOT_FOUND);
                }
                if (actualType != evidence.actualType()) {
                    throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
                }
                List<PhysicalColumn> columns = readColumns(connection, evidence);
                if (!sameColumns(evidence.columns(), columns)) {
                    throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
                }
                T result = action.execute(connection);
                connection.rollback();
                return result;
            }
        } catch (PhysicalPreviewException expected) {
            throw expected;
        } catch (SQLTimeoutException timeout) {
            throw error("PHYSICAL_PREVIEW_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
        } catch (SQLException failure) {
            if ("57014".equals(failure.getSQLState())) {
                throw error("PHYSICAL_PREVIEW_TIMEOUT", HttpStatus.GATEWAY_TIMEOUT);
            }
            if ("42P01".equals(failure.getSQLState())) {
                throw error("PHYSICAL_PREVIEW_RELATION_NOT_FOUND", HttpStatus.NOT_FOUND);
            }
            throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
        } catch (RuntimeException failure) {
            throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
        }
    }

    @FunctionalInterface
    private interface VerifiedRelationAction<T> {
        T execute(Connection connection) throws SQLException;
    }

    private void lockRelation(Connection connection, RelationEvidence evidence) throws SQLException {
        String sql = "lock table " + quote(evidence.schemaName()) + "." + quote(evidence.identifier()) +
            " in access share mode";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.execute();
        }
    }

    private ExpectedRelationType readRelationType(Connection connection, RelationEvidence evidence) throws SQLException {
        try (
            PreparedStatement statement = connection.prepareStatement(
                """
                select c.relkind
                  from pg_catalog.pg_class c
                  join pg_catalog.pg_namespace n on n.oid = c.relnamespace
                 where n.nspname = ? and c.relname = ?
                   and c.relkind in ('r', 'p', 'v', 'm', 'f')
                """
            )
        ) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setString(1, evidence.schemaName());
            statement.setString(2, evidence.identifier());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                ExpectedRelationType type = switch (rows.getString("relkind")) {
                    case "r", "p", "f" -> ExpectedRelationType.TABLE;
                    case "v" -> ExpectedRelationType.VIEW;
                    case "m" -> ExpectedRelationType.MATERIALIZED_VIEW;
                    default -> null;
                };
                if (rows.next()) throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
                return type;
            }
        }
    }

    private List<PhysicalColumn> readColumns(Connection connection, RelationEvidence evidence) throws SQLException {
        try (
            PreparedStatement statement = connection.prepareStatement(
                """
                select a.attnum as ordinal_position,
                       a.attname as column_name,
                       pg_catalog.format_type(a.atttypid, a.atttypmod) as data_type,
                       not a.attnotnull as nullable
                  from pg_catalog.pg_attribute a
                  join pg_catalog.pg_class c on c.oid = a.attrelid
                  join pg_catalog.pg_namespace n on n.oid = c.relnamespace
                 where n.nspname = ? and c.relname = ?
                   and a.attnum > 0 and not a.attisdropped
                 order by a.attnum
                """
            )
        ) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setString(1, evidence.schemaName());
            statement.setString(2, evidence.identifier());
            try (ResultSet rows = statement.executeQuery()) {
                List<PhysicalColumn> columns = new ArrayList<>();
                while (rows.next()) {
                    columns.add(
                        new PhysicalColumn(
                            rows.getInt("ordinal_position"),
                            rows.getString("column_name"),
                            rows.getString("data_type"),
                            rows.getBoolean("nullable")
                        )
                    );
                }
                return List.copyOf(columns);
            }
        }
    }

    private QueryResult sample(
        Connection connection,
        RelationEvidence evidence,
        List<String> selectedColumnNames,
        int limit
    ) throws SQLException {
        String selectedColumns = selectedColumnNames
            .stream()
            .map(PostgresPhysicalPreviewQueryAdapter::quote)
            .collect(java.util.stream.Collectors.joining(", "));
        String sql = "select " + selectedColumns + " from " + quote(evidence.schemaName()) + "." +
            quote(evidence.identifier()) + " limit ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setInt(1, limit + 1);
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData metadata = rows.getMetaData();
                int count = metadata.getColumnCount();
                List<Map<String, Object>> result = new ArrayList<>();
                boolean truncated = false;
                while (rows.next()) {
                    if (result.size() == limit) {
                        truncated = true;
                        break;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= count; index++) {
                        row.put(metadata.getColumnLabel(index), rows.getObject(index));
                    }
                    result.add(row);
                }
                return new QueryResult(List.copyOf(result), clock.instant(), truncated);
            }
        }
    }

    private static void requireTarget(RuntimeTarget target, RelationEvidence evidence) {
        if (
            target == null ||
            !"postgres".equalsIgnoreCase(target.type()) ||
            !"postgres".equalsIgnoreCase(evidence.adapter()) ||
            !Objects.equals(target.database(), evidence.databaseName()) ||
            target.schema() == null ||
            !target.schema().equalsIgnoreCase(evidence.schemaName()) ||
            !Objects.equals(target.credentialVersionRef(), evidence.credentialVersionRef())
        ) {
            throw error("PHYSICAL_PREVIEW_STALE_RELATION", HttpStatus.CONFLICT);
        }
    }

    private static boolean sameColumns(List<PhysicalColumn> expected, List<PhysicalColumn> actual) {
        if (expected.size() != actual.size()) return false;
        for (int index = 0; index < expected.size(); index++) {
            PhysicalColumn left = expected.get(index);
            PhysicalColumn right = actual.get(index);
            if (
                left.ordinalPosition() != right.ordinalPosition() ||
                !left.name().equals(right.name()) ||
                !normalizeType(left.dataType()).equals(normalizeType(right.dataType())) ||
                left.nullable() != right.nullable()
            ) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeType(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String quote(String identifier) {
        requireIdentifier(identifier);
        return '"' + identifier + '"';
    }

    private static void requireIdentifier(String identifier) {
        if (identifier == null || !PhysicalRelationInspector.IDENTIFIER.matcher(identifier).matches()) {
            throw error("PHYSICAL_PREVIEW_IDENTIFIER_INVALID", HttpStatus.CONFLICT);
        }
    }

    private static PhysicalPreviewException error(String code, HttpStatus status) {
        return new PhysicalPreviewException(code, status, null);
    }
}
