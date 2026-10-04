package com.yuzhi.dts.platform.service.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.infra.AdminInfraClient;
import com.yuzhi.dts.platform.service.infra.InfraSecretService;
import com.yuzhi.dts.platform.service.sql.dto.CatalogColumnDto;
import com.yuzhi.dts.platform.service.sql.dto.TableInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SqlMetadataServiceCacheTest {

    @Test
    void emptyTableMetadataDoesNotHideTablesCreatedLater() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-cache-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = new InfraDataSource();
        datasource.setId(datasourceId);
        datasource.setName("metadata-cache-test");
        datasource.setType("POSTGRESQL");
        datasource.setJdbcUrl(jdbcUrl);
        datasource.setUsername(username);

        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        AtomicBoolean tableExists = new AtomicBoolean(false);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSchemas()).thenAnswer(ignored -> schemaResultSet());
        when(metadata.getTables(isNull(), eq("public"), eq("%"), any(String[].class)))
            .thenAnswer(ignored -> tableResultSet(tableExists.get()));

        SqlMetadataService cachedService = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns")
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                .when(() -> DriverManager.getConnection(jdbcUrl, username, password))
                .thenReturn(connection);

            assertThat(cachedService.listTables(datasourceId, null)).isEmpty();

            tableExists.set(true);

            assertThat(cachedService.listTables(datasourceId, null))
                .extracting(TableInfo::name)
                .containsExactly("ods_risk_info_v2");

            tableExists.set(false);

            assertThat(cachedService.listTables(datasourceId, null))
                .extracting(TableInfo::name)
                .containsExactly("ods_risk_info_v2");
            verify(metadata, times(2)).getTables(isNull(), eq("public"), eq("%"), any(String[].class));
        }
    }

    @Test
    void cachedTablesStillRequireDatasourceAuthorization() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-cache-auth-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = datasource(datasourceId, jdbcUrl, username);
        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSchemas()).thenAnswer(ignored -> schemaResultSet());
        when(metadata.getTables(isNull(), eq("public"), eq("%"), any(String[].class)))
            .thenAnswer(ignored -> tableResultSet(true));

        SqlMetadataService cachedService = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns")
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                .when(() -> DriverManager.getConnection(jdbcUrl, username, password))
                .thenReturn(connection);
            assertThat(cachedService.listTables(datasourceId, "allowed")).hasSize(1);

            ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该数据源");
            doThrow(forbidden).when(accessGuard).assertReadable(datasourceId, "denied");

            assertThatThrownBy(() -> cachedService.listTables(datasourceId, "denied")).isSameAs(forbidden);
        }
    }

    @Test
    void cachedColumnsStillRequireDatasourceAuthorization() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-column-cache-auth-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = datasource(datasourceId, jdbcUrl, username);
        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(isNull(), eq("public"), eq("ods_risk_info_v2"), eq("%")))
            .thenAnswer(ignored -> columnResultSet());

        SqlMetadataService cachedService = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns")
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                .when(() -> DriverManager.getConnection(jdbcUrl, username, password))
                .thenReturn(connection);
            assertThat(cachedService.listColumns(datasourceId, "public", "ods_risk_info_v2", "allowed")).hasSize(1);

            ResponseStatusException forbidden = new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该数据源");
            doThrow(forbidden).when(accessGuard).assertReadable(datasourceId, "denied");

            assertThatThrownBy(() ->
                cachedService.listColumns(datasourceId, "public", "ods_risk_info_v2", "denied")
            ).isSameAs(forbidden);
        }
    }

    @Test
    void searchesTablesByTableOrColumnKeywordWithStableDeduplicationAndLimit() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-search-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = datasource(datasourceId, jdbcUrl, username);
        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getSchemas()).thenAnswer(ignored -> schemaResultSet());
        when(metadata.getTables(isNull(), eq("public"), eq("%"), any(String[].class)))
            .thenAnswer(ignored -> tableResultSet("orders_archive", "customers", "suppliers"));
        when(metadata.getColumns(isNull(), eq("public"), eq("%"), eq("%")))
            .thenAnswer(ignored -> searchableColumnResultSet());

        SqlMetadataService service = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns")
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(jdbcUrl, username, password)).thenReturn(connection);

            assertThat(service.searchTables(datasourceId, "allowed", "order", 2))
                .extracting(table -> table.schema() + "." + table.name())
                .containsExactly("public.orders_archive", "public.customers");
        }
    }

    @Test
    void columnMetadataIncludesDescriptionDefaultAutoIncrementAndOrdinalPosition() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-column-details-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = datasource(datasourceId, jdbcUrl, username);
        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(isNull(), eq("public"), eq("orders"), eq("%")))
            .thenAnswer(ignored -> detailedColumnResultSet());

        SqlMetadataService service = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns")
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(jdbcUrl, username, password)).thenReturn(connection);

            assertThat(service.listColumns(datasourceId, "public", "orders", "allowed"))
                .singleElement()
                .satisfies(column ->
                    assertThat(column)
                        .containsEntry("description", "订单编号")
                        .containsEntry("defaultValue", "nextval('orders_id_seq')")
                        .containsEntry("autoIncrement", true)
                        .containsEntry("ordinalPosition", 1)
                        .containsEntry("columnSize", 18)
                        .containsEntry("decimalDigits", 4)
                );
        }
    }

    @Test
    void catalogColumnCacheEntriesDoNotLeakIntoMetadataColumns() throws Exception {
        UUID datasourceId = UUID.randomUUID();
        String jdbcUrl = "jdbc:postgresql://metadata-column-cache-isolation-test/db";
        String username = "metadata_user";
        String password = "metadata_password";

        InfraDataSource datasource = datasource(datasourceId, jdbcUrl, username);
        InfraDataSourceRepository repository = mock(InfraDataSourceRepository.class);
        InfraSecretService secretService = mock(InfraSecretService.class);
        AdminInfraClient adminInfraClient = mock(AdminInfraClient.class);
        DataSourceAccessGuard accessGuard = mock(DataSourceAccessGuard.class);
        when(repository.findById(datasourceId)).thenReturn(Optional.of(datasource));
        when(secretService.readSecrets(datasource)).thenReturn(Map.of("password", password));

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getColumns(isNull(), eq("public"), eq("ods_risk_info_v2"), eq("%")))
            .thenAnswer(ignored -> columnResultSet());

        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("sqlIdeTables", "sqlIdeColumns");
        cacheManager
            .getCache("sqlIdeColumns")
            .put(
                datasourceId + ":public.ods_risk_info_v2",
                List.of(new CatalogColumnDto("catalog_shape", "text", true, null, 1))
            );
        SqlMetadataService cachedService = new SqlMetadataService(
            repository,
            secretService,
            adminInfraClient,
            accessGuard,
            cacheManager
        );

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                .when(() -> DriverManager.getConnection(jdbcUrl, username, password))
                .thenReturn(connection);

            List<Map<String, Object>> columns = cachedService.listColumns(
                datasourceId,
                "public",
                "ods_risk_info_v2",
                "allowed"
            );

            assertThat(columns)
                .singleElement()
                .satisfies(column -> assertThat(column).containsEntry("type", "varchar"));
            verify(metadata).getColumns(isNull(), eq("public"), eq("ods_risk_info_v2"), eq("%"));
        }
    }

    private static InfraDataSource datasource(UUID datasourceId, String jdbcUrl, String username) {
        InfraDataSource datasource = new InfraDataSource();
        datasource.setId(datasourceId);
        datasource.setName("metadata-cache-test");
        datasource.setType("POSTGRESQL");
        datasource.setJdbcUrl(jdbcUrl);
        datasource.setUsername(username);
        return datasource;
    }

    private static ResultSet schemaResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("TABLE_SCHEM")).thenReturn("public");
        return resultSet;
    }

    private static ResultSet tableResultSet(boolean tableExists) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(tableExists, false);
        when(resultSet.getString("TABLE_NAME")).thenReturn("ods_risk_info_v2");
        when(resultSet.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(resultSet.getString("TABLE_SCHEM")).thenReturn("public");
        return resultSet;
    }

    private static ResultSet tableResultSet(String... tableNames) throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        Boolean[] nextValues = new Boolean[tableNames.length + 1];
        for (int i = 0; i < tableNames.length; i++) nextValues[i] = true;
        nextValues[tableNames.length] = false;
        when(resultSet.next()).thenReturn(nextValues[0], java.util.Arrays.copyOfRange(nextValues, 1, nextValues.length));
        when(resultSet.getString("TABLE_NAME")).thenReturn(tableNames[0], java.util.Arrays.copyOfRange(tableNames, 1, tableNames.length));
        when(resultSet.getString("TABLE_TYPE")).thenReturn("TABLE");
        when(resultSet.getString("TABLE_SCHEM")).thenReturn("public");
        return resultSet;
    }

    private static ResultSet searchableColumnResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, true, false);
        when(resultSet.getString("TABLE_SCHEM")).thenReturn("public");
        when(resultSet.getString("TABLE_NAME")).thenReturn("orders_archive", "customers");
        when(resultSet.getString("COLUMN_NAME")).thenReturn("order_id", "order_code");
        return resultSet;
    }

    private static ResultSet columnResultSet() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.next()).thenReturn(true, false);
        when(resultSet.getString("COLUMN_NAME")).thenReturn("project_no");
        when(resultSet.getString("TYPE_NAME")).thenReturn("varchar");
        when(resultSet.getString("IS_NULLABLE")).thenReturn("YES");
        return resultSet;
    }

    private static ResultSet detailedColumnResultSet() throws Exception {
        ResultSet resultSet = columnResultSet();
        when(resultSet.getString("REMARKS")).thenReturn("订单编号");
        when(resultSet.getString("COLUMN_DEF")).thenReturn("nextval('orders_id_seq')");
        when(resultSet.getString("IS_AUTOINCREMENT")).thenReturn("YES");
        when(resultSet.getInt("ORDINAL_POSITION")).thenReturn(1);
        when(resultSet.getInt("COLUMN_SIZE")).thenReturn(18);
        when(resultSet.getInt("DECIMAL_DIGITS")).thenReturn(4);
        return resultSet;
    }
}
