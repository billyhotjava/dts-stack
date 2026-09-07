package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.sql.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelTargetGuardTest {
    @Test
    void validatesExistingStructureWithoutAnyDdlOrDataMutation() throws Exception {
        var platform = mock(PlatformInfraClient.class);
        var task = new IngestionTask();
        task.setDestinationConfig(new ObjectMapper().readTree("""
            {"targetDataSourceId":"source-1","modelTarget":{"dataSourceId":"source-1","databaseName":"warehouse",
             "schemaName":"ods","tableName":"orders","columns":[{"name":"id","dataType":"bigint","nullable":false,"primaryKey":true}]}}
            """));
        var connection = mock(Connection.class);
        when(connection.getCatalog()).thenReturn("warehouse");
        var query = mock(PreparedStatement.class); when(connection.prepareStatement(anyString())).thenReturn(query);
        var columns = mock(ResultSet.class); when(query.executeQuery()).thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString(1)).thenReturn("id"); when(columns.getString(2)).thenReturn("bigint");
        when(columns.getBoolean(3)).thenReturn(true);
        var metadata = mock(DatabaseMetaData.class); when(connection.getMetaData()).thenReturn(metadata);
        var keys = mock(ResultSet.class); when(metadata.getPrimaryKeys(null, "ods", "orders")).thenReturn(keys);
        when(keys.next()).thenReturn(true, false); when(keys.getString("COLUMN_NAME")).thenReturn("id");
        new ModelTargetGuard(platform).validate(task, connection, "ods", "orders", List.of(new JdbcMetadataService.ColumnMeta("id", Types.BIGINT, "int8", 19, 0)));
        verify(platform).validateModelIngestionTarget(task.getDestinationConfig().get("modelTarget"));
        verify(connection).prepareStatement(startsWith("select a.attname"));
        verify(connection, never()).createStatement(); verify(query, never()).executeUpdate();
    }
    @Test
    void rejectsStaleBindingsBeforeReadingOrWritingTheTarget() throws Exception {
        var task = new IngestionTask(); task.setDestinationConfig(new ObjectMapper().readTree("{\"modelTarget\":{\"schemaVersion\":1}}"));
        var platform = mock(PlatformInfraClient.class);
        doThrow(new IllegalStateException("MODEL_INGESTION_TARGET_STALE")).when(platform).validateModelIngestionTarget(any());
        var connection = mock(Connection.class);
        assertThatThrownBy(() -> new ModelTargetGuard(platform).validate(task, connection, "ods", "orders", List.of())).hasMessageContaining("STALE");
        verifyNoInteractions(connection);
    }
    @Test
    void doesNotAcceptTypeNarrowingOrUndeclaredColumns() {
        assertThat(ModelTargetGuard.compatible(new JdbcMetadataService.ColumnMeta("id", Types.BIGINT, "int8", 19, 0), "integer")).isFalse();
        assertThat(ModelTargetGuard.compatible(new JdbcMetadataService.ColumnMeta("label", Types.VARCHAR, "varchar", 80, null), "varchar(32)")).isFalse();
        assertThat(ModelTargetGuard.compatible(new JdbcMetadataService.ColumnMeta("id", Types.INTEGER, "int4", 10, 0), "bigint")).isTrue();
        assertThat(ModelTargetGuard.type("timestamp with time zone")).isEqualTo("timestamptz");
    }
}
