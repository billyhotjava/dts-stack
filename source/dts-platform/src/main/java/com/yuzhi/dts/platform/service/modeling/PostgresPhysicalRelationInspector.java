package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalRelationObservation;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** PostgreSQL catalog adapter; performs metadata-only parameterized queries with a hard timeout. */
@Component
public class PostgresPhysicalRelationInspector
    implements PhysicalRelationInspector {

    private static final String RELATION_SQL = """
        select c.relkind::text as relkind
          from pg_catalog.pg_class c
          join pg_catalog.pg_namespace n
            on n.oid = c.relnamespace
         where n.nspname = ?
           and c.relname = ?
           and c.relkind in ('r', 'p', 'v', 'm', 'f')
        """;
    private static final String COLUMNS_SQL = """
        select a.attnum as ordinal_position,
               a.attname as column_name,
               pg_catalog.format_type(
                   a.atttypid,
                   a.atttypmod
               ) as data_type,
               not a.attnotnull as nullable
          from pg_catalog.pg_attribute a
          join pg_catalog.pg_class c
            on c.oid = a.attrelid
          join pg_catalog.pg_namespace n
            on n.oid = c.relnamespace
         where n.nspname = ?
           and c.relname = ?
           and a.attnum > 0
           and not a.attisdropped
         order by a.attnum
        """;

    private final DbtTargetConnectionFactory targets;
    private final Clock clock;

    @Autowired
    public PostgresPhysicalRelationInspector(
        DbtTargetConnectionFactory targets
    ) {
        this(targets, Clock.systemUTC());
    }

    PostgresPhysicalRelationInspector(
        DbtTargetConnectionFactory targets,
        Clock clock
    ) {
        this.targets = Objects.requireNonNull(
            targets,
            "targets is required"
        );
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Override
    public String adapter() {
        return "postgres";
    }

    @Override
    public boolean dataTypeMatches(
        String expectedType,
        String actualType
    ) {
        return ModelFieldPhysicalTypeContract.postgresTypesMatch(
            expectedType,
            actualType
        );
    }

    @Override
    public PhysicalRelationObservation observe(
        TargetContext context,
        RelationLocator locator
    ) {
        if (
            context == null ||
            locator == null ||
            !adapter().equals(context.adapter())
        ) {
            throw failure(
                "MODEL_PHYSICAL_RELATION_ADAPTER_UNSUPPORTED",
                "Physical relation adapter is unsupported"
            );
        }
        RuntimeTarget target;
        try {
            target = targets.resolveRuntimeTarget();
        } catch (RuntimeException unavailable) {
            throw failure(
                "MODEL_PHYSICAL_RELATION_CREDENTIAL_UNAVAILABLE",
                "Physical relation credential is unavailable",
                unavailable
            );
        }
        requireBoundTarget(context, locator, target);
        try (Connection connection = targets.open(target)) {
            requireDatabase(connection, locator.databaseName());
            ExpectedRelationType actualType = inspectType(
                connection,
                locator
            );
            if (actualType == null) {
                return new PhysicalRelationObservation(
                    false,
                    null,
                    List.of(),
                    null,
                    clock.instant(),
                    "MODEL_PHYSICAL_RELATION_NOT_FOUND"
                );
            }
            List<PhysicalColumn> columns = inspectColumns(
                connection,
                locator
            );
            if (columns.isEmpty()) {
                throw failure(
                    "MODEL_PHYSICAL_RELATION_COLUMNS_UNAVAILABLE",
                    "Physical relation columns are unavailable"
                );
            }
            return new PhysicalRelationObservation(
                true,
                actualType,
                columns,
                columnsChecksum(columns),
                clock.instant(),
                null
            );
        } catch (PhysicalRelationInspectionException stable) {
            throw stable;
        } catch (SQLException unavailable) {
            throw failure(
                "MODEL_PHYSICAL_RELATION_PROBE_FAILED",
                "Physical relation metadata probe failed",
                unavailable
            );
        }
    }

    private static void requireBoundTarget(
        TargetContext context,
        RelationLocator locator,
        RuntimeTarget target
    ) {
        String type = target.type() == null
            ? ""
            : target.type()
                .replace("-", "")
                .replace("_", "")
                .toLowerCase(Locale.ROOT);
        if (
            !java.util.Set.of(
                "postgres",
                "postgresql"
            ).contains(type) ||
            !Objects.equals(
                context.credentialVersionRef(),
                target.credentialVersionRef()
            ) ||
            !equalsIgnoreCase(
                context.databaseName(),
                locator.databaseName()
            ) ||
            !equalsIgnoreCase(
                context.schemaName(),
                locator.schemaName()
            ) ||
            !equalsIgnoreCase(
                target.database(),
                locator.databaseName()
            )
        ) {
            throw failure(
                "MODEL_PHYSICAL_RELATION_TARGET_MISMATCH",
                "Physical relation target does not match the build lease"
            );
        }
    }

    private static void requireDatabase(
        Connection connection,
        String expectedDatabase
    ) throws SQLException {
        String actualDatabase = connection.getCatalog();
        if (
            actualDatabase == null ||
            !actualDatabase.equalsIgnoreCase(expectedDatabase)
        ) {
            throw failure(
                "MODEL_PHYSICAL_RELATION_TARGET_MISMATCH",
                "Connected database does not match the manifest relation"
            );
        }
    }

    private static ExpectedRelationType inspectType(
        Connection connection,
        RelationLocator locator
    ) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                RELATION_SQL
            )
        ) {
            query.setQueryTimeout(10);
            query.setString(1, locator.schemaName());
            query.setString(2, locator.identifier());
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return null;
                }
                String kind = rows.getString("relkind");
                return switch (kind == null ? "" : kind) {
                    case "r", "p", "f" ->
                        ExpectedRelationType.TABLE;
                    case "v" -> ExpectedRelationType.VIEW;
                    case "m" ->
                        ExpectedRelationType.MATERIALIZED_VIEW;
                    default -> throw failure(
                        "MODEL_PHYSICAL_RELATION_TYPE_UNSUPPORTED",
                        "Physical relation type is unsupported"
                    );
                };
            }
        }
    }

    private static List<PhysicalColumn> inspectColumns(
        Connection connection,
        RelationLocator locator
    ) throws SQLException {
        List<PhysicalColumn> columns = new ArrayList<>();
        try (
            PreparedStatement query = connection.prepareStatement(
                COLUMNS_SQL
            )
        ) {
            query.setQueryTimeout(10);
            query.setString(1, locator.schemaName());
            query.setString(2, locator.identifier());
            try (ResultSet rows = query.executeQuery()) {
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
            }
        }
        return List.copyOf(columns);
    }

    private static String columnsChecksum(
        List<PhysicalColumn> columns
    ) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(
                "SHA-256 is unavailable",
                impossible
            );
        }
        for (PhysicalColumn column : columns) {
            String canonical =
                column.ordinalPosition() +
                "\u0000" +
                column.name().toLowerCase(Locale.ROOT) +
                "\u0000" +
                column.dataType().toLowerCase(Locale.ROOT) +
                "\u0000" +
                column.nullable() +
                "\u0000";
            digest.update(
                canonical.getBytes(StandardCharsets.UTF_8)
            );
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static boolean equalsIgnoreCase(
        String left,
        String right
    ) {
        return left != null &&
        right != null &&
        left.equalsIgnoreCase(right);
    }

    private static PhysicalRelationInspectionException failure(
        String code,
        String message
    ) {
        return new PhysicalRelationInspectionException(code, message);
    }

    private static PhysicalRelationInspectionException failure(
        String code,
        String message,
        Throwable cause
    ) {
        return new PhysicalRelationInspectionException(
            code,
            message,
            cause
        );
    }
}
