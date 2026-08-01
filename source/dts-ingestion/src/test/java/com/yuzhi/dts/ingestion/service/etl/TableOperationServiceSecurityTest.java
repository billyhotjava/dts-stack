package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TableOperationServiceSecurityTest {

    private final JdbcMetadataService metadataService = mock(JdbcMetadataService.class);
    private final AddaxJobService addaxJobService = mock(AddaxJobService.class);
    private final Connection connection = mock(Connection.class);
    private final DatabaseMetaData databaseMetaData = mock(DatabaseMetaData.class);
    private final Statement statement = mock(Statement.class);
    private final JdbcMetadataService.JdbcConnectionInfo connectionInfo = new JdbcMetadataService.JdbcConnectionInfo(
        "jdbc:test",
        "user",
        "secret",
        null,
        null,
        Map.of()
    );
    private TableOperationService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new TableOperationService(metadataService, addaxJobService, new ObjectMapper());
        when(metadataService.openConnection(any())).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(databaseMetaData);
        when(connection.createStatement()).thenReturn(statement);
    }

    @Test
    void truncateQuotesEveryIdentifierAndEscapesDatabaseQuoteCharacters() throws Exception {
        when(databaseMetaData.getIdentifierQuoteString()).thenReturn("\"");

        service.truncateTable(connectionInfo, "Finance数据", "Order\"Lines");

        verify(statement).execute("TRUNCATE TABLE \"Finance数据\".\"Order\"\"Lines\"");
    }

    @Test
    void dropUsesTheTargetDatabaseQuoteConvention() throws Exception {
        when(databaseMetaData.getIdentifierQuoteString()).thenReturn("`");

        service.dropTable(connectionInfo, "sales", "order`line");

        verify(statement).execute("DROP TABLE IF EXISTS `sales`.`order``line` CASCADE");
    }

    @Test
    void destructiveOperationsRejectSqlStructureAndExtraQualificationBeforeExecution() throws Exception {
        when(databaseMetaData.getIdentifierQuoteString()).thenReturn("\"");

        for (String unsafe : new String[] {
            "orders; DROP TABLE audit_log",
            "orders--comment",
            "orders/*comment*/",
            "inner.orders",
            "orders\nnext"
        }) {
            assertThatThrownBy(() -> service.dropTable(connectionInfo, "public", unsafe))
                .isInstanceOf(RuntimeException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }

        verify(statement, never()).execute(any(String.class));
    }

    @Test
    void destructiveOperationsFailClosedWhenDriverCannotQuoteIdentifiers() throws Exception {
        when(databaseMetaData.getIdentifierQuoteString()).thenReturn(" ");

        assertThatThrownBy(() -> service.truncateTable(connectionInfo, "public", "orders"))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("does not expose an identifier quote");

        verify(statement, never()).execute(any(String.class));
    }
}
