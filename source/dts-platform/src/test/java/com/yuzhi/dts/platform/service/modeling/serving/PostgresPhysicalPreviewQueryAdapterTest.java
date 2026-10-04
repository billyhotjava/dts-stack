package com.yuzhi.dts.platform.service.modeling.serving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.ExpectedRelationType;
import com.yuzhi.dts.platform.service.modeling.PhysicalRelationInspector.PhysicalColumn;
import com.yuzhi.dts.platform.service.modeling.serving.PhysicalPreviewContract.RelationEvidence;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostgresPhysicalPreviewQueryAdapterTest {

    @Test
    void structureVerificationNeverPreparesARowSelect() throws Exception {
        DbtTargetConnectionFactory targets = mock(DbtTargetConnectionFactory.class);
        RuntimeTarget target = new RuntimeTarget(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "warehouse", "finance", "postgres", "jdbc:postgresql://db/warehouse",
            "dbt", "secret", "sha256:" + "9".repeat(64)
        );
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement relation = mock(PreparedStatement.class);
        PreparedStatement columns = mock(PreparedStatement.class);
        ResultSet relationRows = mock(ResultSet.class);
        ResultSet columnRows = mock(ResultSet.class);
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.prepareStatement(anyString())).thenReturn(lock, relation, columns);
        when(relation.executeQuery()).thenReturn(relationRows);
        when(relationRows.next()).thenReturn(true, false);
        when(relationRows.getString("relkind")).thenReturn("r");
        when(columns.executeQuery()).thenReturn(columnRows);
        when(columnRows.next()).thenReturn(true, false);
        when(columnRows.getInt("ordinal_position")).thenReturn(1);
        when(columnRows.getString("column_name")).thenReturn("select");
        when(columnRows.getString("data_type")).thenReturn("text");
        when(columnRows.getBoolean("nullable")).thenReturn(true);
        PostgresPhysicalPreviewQueryAdapter adapter = new PostgresPhysicalPreviewQueryAdapter(
            targets,
            Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC)
        );

        assertThat(adapter.verifyStructure(evidence())).isEqualTo(Instant.parse("2026-08-02T10:00:00Z"));

        verify(connection, times(3)).prepareStatement(anyString());
        verify(connection).rollback();
    }

    @Test
    void rechecksMetadataThenQuotesSegmentsAndBindsLimit() throws Exception {
        DbtTargetConnectionFactory targets = mock(DbtTargetConnectionFactory.class);
        RuntimeTarget target = new RuntimeTarget(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "warehouse", "finance", "postgres", "jdbc:postgresql://db/warehouse",
            "dbt", "secret", "sha256:" + "9".repeat(64)
        );
        Connection connection = mock(Connection.class);
        PreparedStatement lock = mock(PreparedStatement.class);
        PreparedStatement relation = mock(PreparedStatement.class);
        PreparedStatement columns = mock(PreparedStatement.class);
        PreparedStatement sample = mock(PreparedStatement.class);
        ResultSet relationRows = mock(ResultSet.class);
        ResultSet columnRows = mock(ResultSet.class);
        ResultSet sampleRows = mock(ResultSet.class);
        ResultSetMetaData sampleMeta = mock(ResultSetMetaData.class);
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getCatalog()).thenReturn("warehouse");
        when(connection.prepareStatement(anyString())).thenReturn(lock, relation, columns, sample);
        when(relation.executeQuery()).thenReturn(relationRows);
        when(relationRows.next()).thenReturn(true, false);
        when(relationRows.getString("relkind")).thenReturn("r");
        when(columns.executeQuery()).thenReturn(columnRows);
        when(columnRows.next()).thenReturn(true, false);
        when(columnRows.getInt("ordinal_position")).thenReturn(1);
        when(columnRows.getString("column_name")).thenReturn("select");
        when(columnRows.getString("data_type")).thenReturn("text");
        when(columnRows.getBoolean("nullable")).thenReturn(true);
        when(sample.executeQuery()).thenReturn(sampleRows);
        when(sampleRows.next()).thenReturn(true, false);
        when(sampleRows.getMetaData()).thenReturn(sampleMeta);
        when(sampleMeta.getColumnCount()).thenReturn(1);
        when(sampleMeta.getColumnLabel(1)).thenReturn("select");
        when(sampleRows.getObject(1)).thenReturn("ok");
        PostgresPhysicalPreviewQueryAdapter adapter = new PostgresPhysicalPreviewQueryAdapter(
            targets,
            Clock.fixed(Instant.parse("2026-08-02T10:00:00Z"), ZoneOffset.UTC)
        );

        var result = adapter.query(evidence(), 20);

        assertThat(result.rows()).containsExactly(java.util.Map.of("select", "ok"));
        verify(relation).setString(1, "finance");
        verify(relation).setString(2, "order");
        verify(columns).setString(1, "finance");
        verify(columns).setString(2, "order");
        verify(sample).setInt(1, 21);
        verify(connection).setReadOnly(true);
        verify(connection).setAutoCommit(false);
        verify(lock).execute();
        verify(connection).rollback();
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(connection, times(4)).prepareStatement(sql.capture());
        assertThat(sql.getAllValues().get(0))
            .isEqualTo("lock table \"finance\".\"order\" in access share mode");
    }

    private static RelationEvidence evidence() {
        return new RelationEvidence(
            "tenant-a", UUID.fromString("60000000-0000-0000-0000-000000000001"),
            UUID.fromString("30000000-0000-0000-0000-000000000001"), 4, "a".repeat(64),
            7, "b".repeat(64), UUID.fromString("20000000-0000-0000-0000-000000000001"),
            11, "PUBLISHED", 2, "COMPLETED",
            UUID.fromString("50000000-0000-0000-0000-000000000001"), "BUILT", 1,
            "postgres", "sha256:" + "9".repeat(64), "warehouse", "finance", "order",
            ExpectedRelationType.TABLE, true, true,
            List.of(new PhysicalColumn(1, "select", "text", true)),
            "c".repeat(64), Instant.parse("2026-08-02T09:59:00Z")
        );
    }
}
