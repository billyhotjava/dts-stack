package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.yuzhi.dts.platform.config.GovernanceProperties;
import com.yuzhi.dts.platform.domain.catalog.CatalogDataset;
import com.yuzhi.dts.platform.domain.governance.GovQualityRun;
import com.yuzhi.dts.platform.domain.service.InfraDataSource;
import com.yuzhi.dts.platform.repository.governance.GovQualityFailingRowRepository;
import com.yuzhi.dts.platform.repository.service.InfraDataSourceRepository;
import com.yuzhi.dts.platform.service.sql.JdbcSqlExecutor;
import com.yuzhi.dts.platform.service.sql.SqlValidationService;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class F8QualityExecutionContractTest {
    private static final String SQL = "SELECT project_code FROM public.projects WHERE project_code IS NULL";

    @Test
    void violationCountDoesNotRequireAnIdColumn() throws Exception {
        Fixture f = new Fixture();
        var result = f.executor.execute(f.run, Map.of("sql", SQL));
        assertThat(result.failingRowCount()).isEqualTo(2);
        assertThat(result.results()).noneMatch(r -> "RESULT_ID_REQUIRED".equals(r.errorCode()));
        verify(f.connection, never()).prepareStatement(contains("quality_statement.id"));
    }

    @Test
    void sampleFailureCannotEraseTheViolation() throws Exception {
        Fixture f = new Fixture();
        when(f.sample.executeQuery()).thenThrow(new SQLException("sample failed", "08006"));
        var result = f.executor.execute(f.run, Map.of("sql", SQL));
        assertThat(result.failingRowCount()).isEqualTo(2);
        assertThat(result.results()).anyMatch(r -> r.message().contains("2 条不符合规则"));
    }

    @Test
    void rejectedSqlNeverConnectsToTarget() throws Exception {
        Fixture f = new Fixture();
        var result = f.executor.execute(f.run, Map.of("sql", "SELECT * FROM public.other_table"));
        assertThat(result.results()).anyMatch(r -> "DATASET_SCOPE_BLOCKED".equals(r.errorCode()));
        verifyNoInteractions(f.jdbc);
    }

    private static class Fixture {
        final JdbcSqlExecutor jdbc = mock(JdbcSqlExecutor.class);
        final Connection connection = mock(Connection.class);
        final PreparedStatement sample = mock(PreparedStatement.class);
        final GovQualityRun run = new GovQualityRun();
        final QualityDatasetStatementExecutor executor;
        Fixture() throws Exception {
            var guard = mock(DefaultLakeDatasetGuard.class);
            var sources = mock(InfraDataSourceRepository.class);
            var dataset = new CatalogDataset();
            dataset.setId(UUID.randomUUID()); dataset.setSourceId(UUID.randomUUID());
            dataset.setHiveDatabase("public"); dataset.setHiveTable("projects"); dataset.setName("projects");
            var source = new InfraDataSource(); source.setId(dataset.getSourceId());
            source.setType("POSTGRESQL"); source.setStatus("ACTIVE"); source.setJdbcUrl("jdbc:postgresql://test/db");
            run.setId(UUID.randomUUID()); run.setDatasetId(dataset.getId());
            when(guard.requireDefaultLakeDataset(dataset.getId())).thenReturn(dataset);
            when(sources.findById(source.getId())).thenReturn(Optional.of(source));
            when(jdbc.getConnection(source)).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);
            var statement = mock(Statement.class); var rows = mock(ResultSet.class);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(anyString())).thenReturn(rows);
            when(rows.next()).thenReturn(true); when(rows.getLong(1)).thenReturn(10L);
            var count = mock(PreparedStatement.class); var violations = mock(ResultSet.class);
            when(connection.prepareStatement(startsWith("SELECT count(*) FROM ("))).thenReturn(count);
            when(count.executeQuery()).thenAnswer(invocation -> {
                // The old id-based statistics query must fail just like a real table without id.
                return violations;
            });
            when(violations.next()).thenReturn(true); when(violations.getLong(1)).thenReturn(2L);
            when(connection.prepareStatement(SQL)).thenReturn(sample);
            when(sample.executeQuery()).thenReturn(mock(ResultSet.class));
            executor = new QualityDatasetStatementExecutor(guard, sources, jdbc, new SqlValidationService(),
                mock(GovQualityFailingRowRepository.class), new GovernanceProperties());
        }
    }
}
