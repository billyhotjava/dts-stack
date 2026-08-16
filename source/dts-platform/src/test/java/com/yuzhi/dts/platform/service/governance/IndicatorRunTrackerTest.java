package com.yuzhi.dts.platform.service.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yuzhi.dts.platform.domain.governance.GovIndicatorDefinition;
import com.yuzhi.dts.platform.domain.governance.GovIndicatorRun;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorDefinitionRepository;
import com.yuzhi.dts.platform.repository.governance.GovIndicatorRunRepository;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory;
import com.yuzhi.dts.platform.service.etl.DbtTargetConnectionFactory.RuntimeTarget;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IndicatorRunTrackerTest {

    @Test
    void readsBoundMeasureFromConfiguredWarehouseAndLatestAvailablePeriod() throws Exception {
        GovIndicatorDefinitionRepository indicatorRepo = mock(GovIndicatorDefinitionRepository.class);
        GovIndicatorRunRepository runRepo = mock(GovIndicatorRunRepository.class);
        DbtTargetConnectionFactory targets = mock(DbtTargetConnectionFactory.class);
        RuntimeTarget target = new RuntimeTarget(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "biadmin", "public", "postgres", "jdbc:postgresql://db/biadmin",
            "dbt", "secret", "sha256:" + "9".repeat(64)
        );
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet columns = mock(ResultSet.class);
        PreparedStatement valueStatement = mock(PreparedStatement.class);
        PreparedStatement countStatement = mock(PreparedStatement.class);
        ResultSet valueRows = mock(ResultSet.class);
        ResultSet countRows = mock(ResultSet.class);

        GovIndicatorDefinition definition = new GovIndicatorDefinition();
        definition.setId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
        definition.setCode("pjm_budg_total");
        definition.setMeasureField("total_budget");
        definition.setTargetModelName("biz_ads_budget_kpi_v2");
        definition.setDirection("HIGHER_BETTER");

        when(indicatorRepo.findByStatusIn(List.of("COMMITTED", "PUBLISHED"))).thenReturn(List.of(definition));
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getIdentifierQuoteString()).thenReturn("\"");
        when(metadata.getColumns("biadmin", "public", "biz_ads_budget_kpi_v2", null)).thenReturn(columns);
        when(columns.next()).thenReturn(true, true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("total_budget", "snapshot_date");
        when(connection.prepareStatement(
            "SELECT \"total_budget\" FROM \"public\".\"biz_ads_budget_kpi_v2\" " +
            "ORDER BY \"snapshot_date\" DESC LIMIT 1"
        )).thenReturn(valueStatement);
        when(valueStatement.executeQuery()).thenReturn(valueRows);
        when(valueRows.next()).thenReturn(true);
        when(valueRows.getBigDecimal(1)).thenReturn(new BigDecimal("1250.50"));
        when(connection.prepareStatement(
            "SELECT count(*) FROM \"public\".\"biz_ads_budget_kpi_v2\""
        )).thenReturn(countStatement);
        when(countStatement.executeQuery()).thenReturn(countRows);
        when(countRows.next()).thenReturn(true);
        when(countRows.getInt(1)).thenReturn(2);
        when(runRepo.findTopByIndicatorIdOrderByRunAtDesc(definition.getId())).thenReturn(Optional.empty());
        when(runRepo.findTop3ByIndicatorIdOrderByRunAtDesc(definition.getId())).thenReturn(List.of());

        new IndicatorRunTracker(indicatorRepo, runRepo, targets).captureResults("dbt-run-1");

        ArgumentCaptor<GovIndicatorRun> saved = ArgumentCaptor.forClass(GovIndicatorRun.class);
        verify(runRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(saved.getValue().getComputedValue()).isEqualByComparingTo("1250.50");
        assertThat(saved.getValue().getRowsProcessed()).isEqualTo(2);
        assertThat(saved.getValue().getErrorMessage()).isNull();
        verify(connection).setReadOnly(true);
        verify(valueStatement).setQueryTimeout(5);
        verify(countStatement).setQueryTimeout(5);
        verify(connection).close();
    }

    @Test
    void persistsFailureWhenConfiguredMeasureIsMissingFromPhysicalTable() throws Exception {
        GovIndicatorDefinitionRepository indicatorRepo = mock(GovIndicatorDefinitionRepository.class);
        GovIndicatorRunRepository runRepo = mock(GovIndicatorRunRepository.class);
        DbtTargetConnectionFactory targets = mock(DbtTargetConnectionFactory.class);
        RuntimeTarget target = new RuntimeTarget(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "biadmin", "public", "postgres", "jdbc:postgresql://db/biadmin",
            "dbt", "secret", "sha256:" + "9".repeat(64)
        );
        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        ResultSet columns = mock(ResultSet.class);
        GovIndicatorDefinition definition = new GovIndicatorDefinition();
        definition.setId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
        definition.setCode("pjm_missing");
        definition.setMeasureField("missing_measure");
        definition.setTargetModelName("biz_ads_budget_kpi_v2");

        when(indicatorRepo.findByStatusIn(List.of("COMMITTED", "PUBLISHED"))).thenReturn(List.of(definition));
        when(targets.resolveRuntimeTarget()).thenReturn(target);
        when(targets.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getIdentifierQuoteString()).thenReturn("\"");
        when(metadata.getColumns("biadmin", "public", "biz_ads_budget_kpi_v2", null)).thenReturn(columns);
        when(columns.next()).thenReturn(true, false);
        when(columns.getString("COLUMN_NAME")).thenReturn("snapshot_date");
        when(runRepo.findTopByIndicatorIdOrderByRunAtDesc(definition.getId())).thenReturn(Optional.empty());
        when(runRepo.findTop3ByIndicatorIdOrderByRunAtDesc(definition.getId())).thenReturn(List.of());

        new IndicatorRunTracker(indicatorRepo, runRepo, targets).captureResults("dbt-run-2");

        ArgumentCaptor<GovIndicatorRun> saved = ArgumentCaptor.forClass(GovIndicatorRun.class);
        verify(runRepo).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(saved.getValue().getComputedValue()).isNull();
        assertThat(saved.getValue().getErrorMessage()).contains("missing_measure");
    }
}
