package com.yuzhi.dts.copilot.analytics.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DatasetQueryServiceTest {

    @Mock
    private ExternalDatabaseDataSourceRegistry dataSourceRegistry;

    @Mock
    private FederatedNativeSqlQualifier federatedNativeSqlQualifier;

    @Mock
    private HikariDataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private PreparedStatement statement;

    @Test
    void shouldQualifyFederatedNativeSqlAtFinalExecutionGate() throws Exception {
        DatasetQueryService service = new DatasetQueryService(dataSourceRegistry, federatedNativeSqlQualifier);
        String sql = "SELECT COUNT(*) FROM public.xycyl_ads_flowerbiz_sale_summary";
        String qualifiedSql = "SELECT COUNT(*) FROM postgres.public.xycyl_ads_flowerbiz_sale_summary";
        DatasetQueryService.DatasetConstraints constraints = DatasetQueryService.DatasetConstraints.defaults();

        when(federatedNativeSqlQualifier.qualify(9L, sql)).thenReturn(qualifiedSql);
        when(dataSourceRegistry.get(9L)).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(qualifiedSql)).thenReturn(statement);
        when(statement.execute()).thenReturn(false);

        service.runNative(9L, sql, constraints, List.of());

        verify(connection).prepareStatement(qualifiedSql);
    }
}
