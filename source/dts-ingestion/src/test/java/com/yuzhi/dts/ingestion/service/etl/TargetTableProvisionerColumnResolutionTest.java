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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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

    @Test
    void shouldDropExistingCreateNewTargetBeforeRecreatingWithoutCascade() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);
        Statement statement = targetConnection.createStatement();
        IngestionTask task = managedFileTask("create_new", true);

        provisioner.ensureTargetTables(task);

        org.mockito.InOrder ddlOrder = org.mockito.Mockito.inOrder(statement);
        ddlOrder.verify(statement).execute("DROP TABLE \"public\".\"ods_orders\"");
        ddlOrder.verify(statement).execute(
            org.mockito.ArgumentMatchers.startsWith("create table \"public\".\"ods_orders\"")
        );
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues()).noneMatch(value -> value.toUpperCase(Locale.ROOT).contains("CASCADE"));
        verify(targetConnection).commit();
    }

    @Test
    void shouldRollbackCreateNewDropWhenReplacementCreateFails() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);
        Statement statement = targetConnection.createStatement();
        doThrow(new java.sql.SQLException("replacement create failed"))
            .when(statement)
            .execute(org.mockito.ArgumentMatchers.startsWith("create table"));

        assertThatThrownBy(() -> provisioner.ensureTargetTables(managedFileTask("create_new", true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("replacement create failed");

        verify(statement).execute("DROP TABLE \"public\".\"ods_orders\"");
        verify(targetConnection).rollback();
        verify(targetConnection, never()).commit();
    }

    @Test
    void shouldRejectManagedFileReplacementOutsideFullRefresh() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        IngestionTask task = managedFileTask("create_new", true);
        task.setSyncMode("incremental");

        assertThatThrownBy(() -> provisioner.ensureTargetTables(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("文件落地仅支持全量导入");

        verify(metadataService, never()).openConnection(any());
    }

    @Test
    void shouldRejectExistingFileTargetReplacementOutsidePostgres() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);
        Statement statement = targetConnection.createStatement();
        IngestionTask task = managedFileTask("create_new", true);
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "jdbcUrl", "jdbc:mysql://target-db:3306/lake",
            "username", "writer",
            "password", "writer-password",
            "table", List.of("public.ods_orders")
        )));

        assertThatThrownBy(() -> provisioner.ensureTargetTables(task))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("替换已有文件落地表仅支持 PostgreSQL");

        verify(statement, never()).execute(org.mockito.ArgumentMatchers.startsWith("DROP TABLE"));
    }

    @Test
    void shouldProvisionManagedFileTargetWhenPersistedTableMappingIsEmpty() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, false);
        Statement statement = targetConnection.createStatement();
        IngestionTask task = managedFileTask("create_new", false);
        ObjectNode sourceConfig = (ObjectNode) task.getSourceConfig();
        sourceConfig.put("_originalName", "ods_orders.xlsx");
        ((ObjectNode) sourceConfig.get("_fileLanding")).put("targetTable", "ods_orders");
        task.setTableMapping(null);
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "jdbcUrl", "jdbc:postgresql://target-db:5432/lake",
            "username", "writer",
            "password", "writer-password",
            "connection", List.of(Map.of(
                "jdbcUrl", List.of("jdbc:postgresql://target-db:5432/lake"),
                "table", List.of("${table}")
            )),
            "column", List.of("*")
        )));

        provisioner.ensureTargetTables(task);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues())
            .anyMatch(value -> value.startsWith("create table \"public\".\"ods_orders\""));
    }

    @Test
    void shouldRecreateConfirmedExistingFileTargetWithoutCascadeAndApplyComments() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);
        Statement statement = targetConnection.createStatement();
        IngestionTask task = managedFileTask("recreate_existing", true);

        provisioner.ensureTargetTables(task);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(statement, atLeastOnce()).execute(sql.capture());
        assertThat(sql.getAllValues())
            .contains("DROP TABLE \"public\".\"ods_orders\"")
            .anyMatch(value -> value.startsWith("create table \"public\".\"ods_orders\""))
            .anyMatch(value -> value.contains("\"amount\" decimal(18,4)"))
            .contains("COMMENT ON COLUMN \"public\".\"ods_orders\".\"order_no\" IS '订单编号'");
        assertThat(sql.getAllValues()).noneMatch(value -> value.toUpperCase(Locale.ROOT).contains("CASCADE"));
        verify(targetConnection).commit();
    }

    @Test
    void shouldRejectRecreateWithoutExplicitConfirmation() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            mock(IngestionSchemaSnapshotService.class),
            mock(IngestionSourceResolver.class)
        );
        targetConnection(metadataService, true);

        assertThatThrownBy(() -> provisioner.ensureTargetTables(managedFileTask("recreate_existing", false)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("未确认全量重建");
    }

    @Test
    void shouldRollbackWhenExistingTargetCannotBeDroppedBecauseOfDependencies() throws Exception {
        JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
        IngestionSchemaSnapshotService snapshotService = mock(IngestionSchemaSnapshotService.class);
        TargetTableProvisioner provisioner = new TargetTableProvisioner(
            metadataService,
            objectMapper,
            snapshotService,
            mock(IngestionSourceResolver.class)
        );
        Connection targetConnection = targetConnection(metadataService, true);
        Statement statement = targetConnection.createStatement();
        doThrow(new java.sql.SQLException("cannot drop table because other objects depend on it"))
            .when(statement)
            .execute("DROP TABLE \"public\".\"ods_orders\"");

        assertThatThrownBy(() -> provisioner.ensureTargetTables(managedFileTask("recreate_existing", true)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("other objects depend on it");

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

    private IngestionTask managedFileTask(String landingMode, boolean recreateConfirmed) {
        IngestionTask task = task(List.of("upload.xlsx"), List.of("public.ods_orders"));
        task.setSourceType("excelreader");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("_fileId", "file-001");
        source.put("_fileColumns", List.of(
            Map.of(
                "name", "order_no",
                "safeName", "order_no",
                "type", "string",
                "description", "订单编号"
            ),
            Map.of(
                "name", "amount",
                "safeName", "amount",
                "type", "NUMERIC",
                "precision", 18,
                "scale", 4
            )
        ));
        source.put("_fileLanding", Map.of(
            "version", 1,
            "structureMode", "reference_existing",
            "landingMode", landingMode,
            "referenceTable", "public.ods_orders",
            "targetTable", "public.ods_orders",
            "recreateConfirmed", recreateConfirmed
        ));
        task.setSourceConfig(objectMapper.valueToTree(source));
        task.setTableMapping(objectMapper.valueToTree(List.of(
            Map.of("source", "upload.xlsx", "target", "public.ods_orders")
        )));
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
