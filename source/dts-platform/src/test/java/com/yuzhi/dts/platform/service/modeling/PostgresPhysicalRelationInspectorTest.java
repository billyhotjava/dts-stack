package com.yuzhi.dts.platform.service.modeling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.RelationLocator;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.TargetContext;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresPhysicalRelationInspectorTest {

    private static final Instant NOW =
        Instant.parse("2026-07-27T16:00:00Z");

    @Test
    void observesPostgresTypeAndOrderedColumnMetadata()
        throws Exception {
        DbtTargetConnectionFactory targets = mock(
            DbtTargetConnectionFactory.class
        );
        RuntimeTarget target = target();
        Connection connection = mock(Connection.class);
        PreparedStatement relation = mock(PreparedStatement.class);
        PreparedStatement columns = mock(PreparedStatement.class);
        ResultSet relationRows = mock(ResultSet.class);
        ResultSet columnRows = mock(ResultSet.class);
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.prepareStatement(any()))
            .thenReturn(relation, columns);
        when(relation.executeQuery()).thenReturn(relationRows);
        when(relationRows.next()).thenReturn(true, false);
        when(relationRows.getString("relkind")).thenReturn("r");
        when(columns.executeQuery()).thenReturn(columnRows);
        when(columnRows.next()).thenReturn(true, true, false);
        when(columnRows.getInt("ordinal_position"))
            .thenReturn(1, 2);
        when(columnRows.getString("column_name"))
            .thenReturn("project_id", "amount");
        when(columnRows.getString("data_type"))
            .thenReturn("uuid", "numeric(18,2)");
        when(columnRows.getBoolean("nullable"))
            .thenReturn(false, true);
        var inspector = new PostgresPhysicalRelationInspector(
            targets,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var observation = inspector.observe(
            context(),
            locator()
        );

        assertThat(observation.exists()).isTrue();
        assertThat(observation.actualType())
            .isEqualTo(ExpectedRelationType.TABLE);
        assertThat(observation.columns())
            .extracting(column -> column.name())
            .containsExactly("project_id", "amount");
        assertThat(observation.columnsChecksum())
            .matches("^[0-9a-f]{64}$");
        assertThat(observation.observedAt()).isEqualTo(NOW);
        assertThat(observation.errorCode()).isNull();
        verify(relation).setQueryTimeout(10);
        verify(columns).setQueryTimeout(10);
        verify(relation).setString(1, "finance");
        verify(relation).setString(2, "dwd_finance");
    }

    @Test
    void returnsExplicitAbsenceWithoutInventingMetadata()
        throws Exception {
        DbtTargetConnectionFactory targets = mock(
            DbtTargetConnectionFactory.class
        );
        RuntimeTarget target = target();
        Connection connection = mock(Connection.class);
        PreparedStatement relation = mock(PreparedStatement.class);
        ResultSet relationRows = mock(ResultSet.class);
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.prepareStatement(any()))
            .thenReturn(relation);
        when(relation.executeQuery()).thenReturn(relationRows);
        when(relationRows.next()).thenReturn(false);
        var inspector = new PostgresPhysicalRelationInspector(
            targets,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        var observation = inspector.observe(
            context(),
            locator()
        );

        assertThat(observation.exists()).isFalse();
        assertThat(observation.actualType()).isNull();
        assertThat(observation.columns()).isEmpty();
        assertThat(observation.errorCode())
            .isEqualTo("MODEL_PHYSICAL_RELATION_NOT_FOUND");
    }

    private static RuntimeTarget target() {
        return new RuntimeTarget(
            UUID.fromString(
                "10000000-0000-0000-0000-000000000001"
            ),
            "warehouse",
            "finance",
            "PostgreSQL",
            "jdbc:postgresql://db:5432/warehouse",
            "dbt",
            "must-not-leak",
            "sha256:" + "a".repeat(64)
        );
    }

    private static TargetContext context() {
        return new TargetContext(
            "postgres-primary",
            "postgres",
            "warehouse",
            "finance",
            "sha256:" + "a".repeat(64)
        );
    }

    private static RelationLocator locator() {
        return new RelationLocator(
            "warehouse",
            "finance",
            "dwd_finance",
            ExpectedRelationType.TABLE,
            List.of("project_id", "amount")
        );
    }
}
