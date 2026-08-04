package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TargetTableProvisionerColumnResolutionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldPreserveJdbcTypesWhenExplicitColumnsAreSelected() {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            new ObjectMapper(),
            snapshotService,
            mock(IngestionSourceResolver.class)
        );
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = new JdbcMetadataService.JdbcConnectionInfo(
            "jdbc:postgresql://127.0.0.1:5432/source",
            "user",
            "pwd",
            "org.postgresql.Driver",
            null,
            Map.of()
        );
        when(metadataService.getTableColumns(sourceInfo, "public.orders")).thenReturn(List.of(
            new JdbcMetadataService.ColumnMeta("id", Types.BIGINT, "BIGINT", null, null),
            new JdbcMetadataService.ColumnMeta("amount", Types.NUMERIC, "NUMERIC", 18, 2),
            new JdbcMetadataService.ColumnMeta("created_at", Types.TIMESTAMP, "TIMESTAMP", null, null)
        ));

        List<JdbcMetadataService.ColumnMeta> columns = provisioner.resolveColumns(
            sourceInfo,
            "public.orders",
            Map.of("column", List.of("amount", "created_at"))
        );

        assertThat(columns).extracting(JdbcMetadataService.ColumnMeta::name)
            .containsExactly("amount", "created_at");
        assertThat(columns.get(0).jdbcType()).isEqualTo(Types.NUMERIC);
        assertThat(columns.get(0).columnSize()).isEqualTo(18);
        assertThat(columns.get(0).decimalDigits()).isEqualTo(2);
        assertThat(columns.get(1).jdbcType()).isEqualTo(Types.TIMESTAMP);
    }

    @Test
    void shouldFallbackToTextWhenExplicitColumnMetadataIsUnavailable() {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            new ObjectMapper(),
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        JdbcMetadataService.JdbcConnectionInfo sourceInfo = new JdbcMetadataService.JdbcConnectionInfo(
            null,
            null,
            null,
            null,
            null,
            Map.of()
        );

        List<JdbcMetadataService.ColumnMeta> columns = provisioner.resolveColumns(
            sourceInfo,
            "orders",
            Map.of("column", List.of("amount"))
        );

        assertThat(columns).hasSize(1);
        assertThat(columns.get(0).name()).isEqualTo("amount");
        assertThat(columns.get(0).jdbcType()).isEqualTo(Types.VARCHAR);
        assertThat(columns.get(0).typeName()).isEqualTo("TEXT");
    }

    @Test
    void shouldResolveManagedDestinationCredentialsForProvisioning() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        IngestionSourceResolver sourceResolver = mock(IngestionSourceResolver.class);
        UUID targetDataSourceId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        JdbcMetadataService.JdbcConnectionInfo managedTarget = new JdbcMetadataService.JdbcConnectionInfo(
            "jdbc:postgresql://dts-pg:5432/biadmin",
            "biadmin",
            "managed-password",
            "org.postgresql.Driver",
            null,
            Map.of()
        );
        when(sourceResolver.resolveJdbcInfo(targetDataSourceId)).thenReturn(managedTarget);
        targetConnection(metadataService, false);
        when(metadataService.getTableColumns(any(), eq("orders"))).thenReturn(List.of(column("id")));
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            snapshotService,
            sourceResolver
        );
        IngestionTask task = task(List.of("orders"), List.of("ods_orders"));
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "targetDataSourceId",
            targetDataSourceId.toString(),
            "table",
            List.of("ods_orders"),
            "column",
            List.of("*")
        )));

        provisioner.ensureTargetTables(task);

        verify(sourceResolver).resolveJdbcInfo(targetDataSourceId);
        ArgumentCaptor<JdbcMetadataService.JdbcConnectionInfo> targetInfo = ArgumentCaptor.forClass(
            JdbcMetadataService.JdbcConnectionInfo.class
        );
        verify(metadataService).openConnection(targetInfo.capture());
        assertThat(targetInfo.getValue().jdbcUrl()).isEqualTo("jdbc:postgresql://dts-pg:5432/biadmin?sslmode=disable");
        assertThat(targetInfo.getValue().username()).isEqualTo("biadmin");
        assertThat(targetInfo.getValue().password()).isEqualTo("managed-password");
        assertThat(task.getDestinationConfig().has("password")).isFalse();
    }

    @Test
    void shouldPreserveCompleteDtsNamedSourceFieldsByRenamingThem() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            snapshotService,
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, false);

        when(metadataService.getTableColumns(any(), eq("ods_orders"))).thenReturn(managedOdsColumns("id", "amount"));

        provisioner.ensureTargetTables(task(
            List.of("ods_orders"),
            List.of("ods_copy_orders")
        ));

        Statement statement = targetConnection.createStatement();
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(statement).execute(sql.capture());
        assertThat(sql.getValue())
            .contains("\"id\"")
            .contains("\"amount\"")
            .contains("\"_dts_source_system_2\"")
            .contains("\"_dts_task_id_2\"");
        assertThat(occurrences(sql.getValue(), "\"_dts_source_system\"")).isEqualTo(1);
    }

    @Test
    void shouldPreservePartialDtsNameCollisionWithoutDroppingExistingTarget() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            snapshotService,
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);

        when(metadataService.getTableColumns(any(), eq("orders"))).thenReturn(List.of(column("id")));
        when(metadataService.getTableColumns(any(), eq("broken_orders"))).thenReturn(List.of(column("id"), column("_dts_source_system")));

        provisioner.ensureTargetTables(task(
            List.of("orders", "broken_orders"),
            List.of("ods_orders", "ods_broken_orders")
        ));

        verify(targetConnection.createStatement(), never()).execute(org.mockito.ArgumentMatchers.startsWith("DROP TABLE"));
        verify(targetConnection.createStatement(), atLeastOnce()).execute(
            org.mockito.ArgumentMatchers.contains("\"_dts_source_system_2\"")
        );
    }

    @Test
    void shouldPreserveSourceWithOnlyDtsNamedFields() {
        assertThat(DtsOdsTechnicalColumns.businessColumns(managedOdsColumns()))
            .extracting(JdbcMetadataService.ColumnMeta::name)
            .containsExactly(
                "_dts_source_system_2",
                "_dts_source_table_2",
                "_dts_import_time_2",
                "_dts_batch_id_2",
                "_dts_execution_id_2",
                "_dts_task_id_2"
            );
    }

    @Test
    void shouldRejectPartialTableMappingBeforeOpeningTargetConnection() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        IngestionTask task = task(List.of("orders", "customers"), List.of("ods_orders", "ods_customers"));
        task.setTableMapping(objectMapper.valueToTree(List.of(
            Map.of("source", "orders", "target", "ods_orders"),
            Map.of("source", "customers", "target", "")
        )));

        assertThatThrownBy(() -> provisioner.ensureTargetTables(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("create table mapping validation failed");

        verify(metadataService, never()).openConnection(any());
    }

    @Test
    void shouldRollbackPostgresFullRefreshWhenCreateFails() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            snapshotService,
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, false);
        Statement statement = targetConnection.createStatement();
        when(targetConnection.getAutoCommit()).thenReturn(true);
        when(metadataService.getTableColumns(any(), eq("orders"))).thenReturn(List.of(column("id")));
        doThrow(new java.sql.SQLException("create failed"))
            .when(statement)
            .execute(org.mockito.ArgumentMatchers.argThat(sql -> sql != null && sql.startsWith("create table")));

        assertThatThrownBy(() -> provisioner.ensureTargetTables(task(
            List.of("orders"),
            List.of("ods_orders")
        )))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("create failed");

        verify(targetConnection).setAutoCommit(false);
        verify(targetConnection).rollback();
        verify(targetConnection, never()).commit();
        verify(snapshotService, never()).saveSnapshot(
            any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
        );
    }

    private Connection targetConnection(JdbcMetadataService metadataService, boolean exists) throws Exception {
        Connection connection = mock(Connection.class);
        DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
        ResultSet tables = mock(ResultSet.class);
        ResultSet columns = mock(ResultSet.class);
        Statement statement = mock(Statement.class);
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(databaseMetaData.getTables(any(), any(), any(), any())).thenReturn(tables);
        when(databaseMetaData.getColumns(any(), any(), any(), any())).thenReturn(columns);
        when(tables.next()).thenReturn(exists);
        when(columns.next()).thenReturn(false);
        when(connection.createStatement()).thenReturn(statement);
        return connection;
    }

    private IngestionTask task(List<String> sources, List<String> targets) {
        IngestionTask task = new IngestionTask();
        task.setName("managed-ods-copy");
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("full_refresh");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("jdbcUrl", "jdbc:mysql://source-db:3306/source");
        source.put("username", "reader");
        source.put("password", "reader-password");
        source.put("table", sources);
        source.put("column", List.of("*"));
        task.setSourceConfig(objectMapper.valueToTree(source));
        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("jdbcUrl", "jdbc:postgresql://target-db:5432/lake");
        destination.put("username", "writer");
        destination.put("password", "writer-password");
        destination.put("table", targets);
        destination.put("column", List.of("*"));
        task.setDestinationConfig(objectMapper.valueToTree(destination));
        List<Map<String, String>> mappings = new ArrayList<>();
        for (int i = 0; i < Math.min(sources.size(), targets.size()); i++) {
            mappings.add(Map.of("source", sources.get(i), "target", targets.get(i)));
        }
        task.setTableMapping(objectMapper.valueToTree(mappings));
        return task;
    }

    private List<JdbcMetadataService.ColumnMeta> managedOdsColumns(String... businessColumns) {
        List<JdbcMetadataService.ColumnMeta> columns = new ArrayList<>();
        for (String businessColumn : businessColumns) {
            columns.add(column(businessColumn));
        }
        columns.add(column("_dts_source_system"));
        columns.add(column("_dts_source_table"));
        columns.add(column("_dts_import_time"));
        columns.add(column("_dts_batch_id"));
        columns.add(column("_dts_execution_id"));
        columns.add(column("_dts_task_id"));
        return columns;
    }

    private JdbcMetadataService.ColumnMeta column(String name) {
        return new JdbcMetadataService.ColumnMeta(name, Types.VARCHAR, "VARCHAR", 200, null);
    }

    private int occurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
