package com.yuzhi.dts.platform.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.config.DbtProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DbtModelDiagnosticsServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void diagnoseShouldReportUpstreamRowCountsAndEmptyCurrentModel() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.biz_dwd_project_node_v2": {
                  "name": "biz_dwd_project_node_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dwd_project_node_v2",
                  "path": "models/dwd/biz_dwd_project_node_v2.sql"
                },
                "model.demo.biz_dws_progress_monthly_v2": {
                  "name": "biz_dws_progress_monthly_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dws_progress_monthly_v2",
                  "path": "models/dws/biz_dws_progress_monthly_v2.sql",
                  "depends_on": {
                    "nodes": ["model.demo.biz_dwd_project_node_v2"]
                  }
                }
              },
              "sources": {}
            }
            """
        );

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", UUID.randomUUID(), "biadmin", "public", Map.of()),
                null,
                null,
                null
            )
        );

        DbtProperties dbtProperties = new DbtProperties();
        dbtProperties.setEnabled(true);
        dbtProperties.setProjectDir(tempDir.toString());

        DbtTargetConnectionFactory connectionFactory = mock(DbtTargetConnectionFactory.class);
        DbtTargetConnectionFactory.TargetWarehouse target = new DbtTargetConnectionFactory.TargetWarehouse(
            UUID.randomUUID(),
            "biadmin",
            "public",
            "postgres",
            "jdbc:postgresql://warehouse/biadmin",
            "biadmin",
            "secret"
        );
        when(connectionFactory.resolveTarget()).thenReturn(target);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tablesCurrent = mock(ResultSet.class);
        ResultSet tablesUpstream = mock(ResultSet.class);
        ResultSet countCurrent = mock(ResultSet.class);
        ResultSet countUpstream = mock(ResultSet.class);

        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);

        when(metadata.getTables(null, "public", "biz_dws_progress_monthly_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesCurrent);
        when(metadata.getTables(null, "public", "biz_dwd_project_node_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesUpstream);
        when(tablesCurrent.next()).thenReturn(true, false);
        when(tablesUpstream.next()).thenReturn(true, false);

        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dws_progress_monthly_v2\"")).thenReturn(countCurrent);
        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dwd_project_node_v2\"")).thenReturn(countUpstream);
        when(countCurrent.next()).thenReturn(true);
        when(countCurrent.getLong(1)).thenReturn(0L);
        when(countUpstream.next()).thenReturn(true);
        when(countUpstream.getLong(1)).thenReturn(12L);

        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        when(dbtRunResultService.loadLatestSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                tempDir.toString(),
                tempDir.resolve("target/run_results.json").toString(),
                tempDir.resolve("target/manifest.json").toString(),
                "inv-1",
                Instant.parse("2026-04-08T12:00:00Z").toString(),
                "dbt build --select biz_dws_progress_monthly_v2",
                "SUCCESS",
                1,
                1,
                0,
                0,
                List.of(),
                List.of()
            )
        );

        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(false);

        DbtModelDiagnosticsService service = new DbtModelDiagnosticsService(
            new ObjectMapper(),
            dbtProperties,
            configService,
            connectionFactory,
            dbtRunResultService,
            airflowClient,
            airflowProperties
        );

        DbtModelDiagnosticsService.ModelDiagnostics result = service.diagnose("biz_dws_progress_monthly_v2");

        assertThat(result.success()).isTrue();
        assertThat(result.current().rowCount()).isZero();
        assertThat(result.upstreams()).hasSize(1);
        assertThat(result.upstreams().get(0).stats().rowCount()).isEqualTo(12L);
        assertThat(result.findings()).anyMatch(item -> item.contains("当前模型 0 行"));
        assertThat(result.findings()).anyMatch(item -> item.contains("plan_year"));
        assertThat(result.recommendedQueries()).anyMatch(item -> item.contains("FROM biz_dwd_project_node_v2"));
        assertThat(result.recommendedQueries()).anyMatch(item -> item.contains("FROM ods_project_subject_domain_v2"));
    }

    @Test
    void diagnoseShouldIncludeLatestAirflowRunAndLogSnippet() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.biz_dws_progress_monthly_v2": {
                  "name": "biz_dws_progress_monthly_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dws_progress_monthly_v2",
                  "path": "models/dws/biz_dws_progress_monthly_v2.sql"
                }
              },
              "sources": {}
            }
            """
        );

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", UUID.randomUUID(), "biadmin", "public", Map.of()),
                null,
                null,
                null
            )
        );

        DbtProperties dbtProperties = new DbtProperties();
        dbtProperties.setEnabled(true);
        dbtProperties.setProjectDir(tempDir.toString());

        DbtTargetConnectionFactory connectionFactory = mock(DbtTargetConnectionFactory.class);
        DbtTargetConnectionFactory.TargetWarehouse target = new DbtTargetConnectionFactory.TargetWarehouse(
            UUID.randomUUID(),
            "biadmin",
            "public",
            "postgres",
            "jdbc:postgresql://warehouse/biadmin",
            "biadmin",
            "secret"
        );
        when(connectionFactory.resolveTarget()).thenReturn(target);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tablesCurrent = mock(ResultSet.class);
        ResultSet countCurrent = mock(ResultSet.class);

        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(null, "public", "biz_dws_progress_monthly_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesCurrent);
        when(tablesCurrent.next()).thenReturn(true, false);
        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dws_progress_monthly_v2\"")).thenReturn(countCurrent);
        when(countCurrent.next()).thenReturn(true);
        when(countCurrent.getLong(1)).thenReturn(8L);

        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        when(dbtRunResultService.loadLatestSummary(20)).thenReturn(DbtRunResultService.DbtRunSummary.empty("none"));

        AirflowClient airflowClient = mock(AirflowClient.class);
        when(airflowClient.listDagRuns("dbt_load", 5)).thenReturn(
            Optional.of(Map.of("dag_runs", List.of(Map.of("dag_run_id", "manual__1", "state", "failed", "logical_date", "2026-04-08T20:00:00Z"))))
        );
        when(airflowClient.listTaskInstances("dbt_load", "manual__1")).thenReturn(
            new ObjectMapper().readTree("{\"task_instances\":[{\"task_id\":\"dbt_run\"}]}")
        );
        when(airflowClient.getTaskInstanceLog("dbt_load", "manual__1", "dbt_run", 1)).thenReturn("dbt run failed because relation is empty");

        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");

        DbtModelDiagnosticsService service = new DbtModelDiagnosticsService(
            new ObjectMapper(),
            dbtProperties,
            configService,
            connectionFactory,
            dbtRunResultService,
            airflowClient,
            airflowProperties
        );

        DbtModelDiagnosticsService.ModelDiagnostics result = service.diagnose("biz_dws_progress_monthly_v2");

        assertThat(result.success()).isTrue();
        assertThat(result.runtime().airflowRun().present()).isTrue();
        assertThat(result.runtime().airflowRun().dagRunId()).isEqualTo("manual__1");
        assertThat(result.runtime().airflowRun().taskId()).isEqualTo("dbt_run");
        assertThat(result.runtime().airflowRun().logSnippet()).contains("relation is empty");
        assertThat(result.findings()).anyMatch(item -> item.contains("Airflow DAG run 失败"));
        assertThat(result.recommendedQueries()).isNotEmpty();
    }

    @Test
    void diagnoseShouldIgnoreUnrelatedLatestRuntimeForOtherModels() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.biz_dws_progress_monthly_v2": {
                  "name": "biz_dws_progress_monthly_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dws_progress_monthly_v2",
                  "path": "models/dws/biz_dws_progress_monthly_v2.sql"
                }
              },
              "sources": {}
            }
            """
        );

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", UUID.randomUUID(), "biadmin", "public", Map.of()),
                null,
                null,
                null
            )
        );

        DbtProperties dbtProperties = new DbtProperties();
        dbtProperties.setEnabled(true);
        dbtProperties.setProjectDir(tempDir.toString());

        DbtTargetConnectionFactory connectionFactory = mock(DbtTargetConnectionFactory.class);
        DbtTargetConnectionFactory.TargetWarehouse target = new DbtTargetConnectionFactory.TargetWarehouse(
            UUID.randomUUID(),
            "biadmin",
            "public",
            "postgres",
            "jdbc:postgresql://warehouse/biadmin",
            "biadmin",
            "secret"
        );
        when(connectionFactory.resolveTarget()).thenReturn(target);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tablesCurrent = mock(ResultSet.class);
        ResultSet countCurrent = mock(ResultSet.class);

        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(null, "public", "biz_dws_progress_monthly_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesCurrent);
        when(tablesCurrent.next()).thenReturn(true, false);
        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dws_progress_monthly_v2\"")).thenReturn(countCurrent);
        when(countCurrent.next()).thenReturn(true);
        when(countCurrent.getLong(1)).thenReturn(10L);

        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        when(dbtRunResultService.loadLatestSummary(20)).thenReturn(
            new DbtRunResultService.DbtRunSummary(
                true,
                tempDir.toString(),
                tempDir.resolve("target/run_results.json").toString(),
                tempDir.resolve("target/manifest.json").toString(),
                "inv-other",
                Instant.parse("2026-04-08T12:00:00Z").toString(),
                "dbt build --select biz_dws_quality_monthly_v2",
                "SUCCESS",
                1,
                1,
                0,
                0,
                List.of(),
                List.of()
            )
        );

        AirflowClient airflowClient = mock(AirflowClient.class);
        when(airflowClient.listDagRuns("dbt_load", 5)).thenReturn(
            Optional.of(Map.of("dag_runs", List.of(Map.of(
                "dag_run_id",
                "manual__other",
                "state",
                "success",
                "logical_date",
                "2026-04-08T20:00:00Z",
                "conf",
                Map.of("models", "biz_dws_quality_monthly_v2")
            ))))
        );

        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(true);
        airflowProperties.setDagId("dbt_load");

        DbtModelDiagnosticsService service = new DbtModelDiagnosticsService(
            new ObjectMapper(),
            dbtProperties,
            configService,
            connectionFactory,
            dbtRunResultService,
            airflowClient,
            airflowProperties
        );

        DbtModelDiagnosticsService.ModelDiagnostics result = service.diagnose("biz_dws_progress_monthly_v2");

        assertThat(result.success()).isTrue();
        assertThat(result.runtime().dbtRun().present()).isFalse();
        assertThat(result.runtime().airflowRun().present()).isFalse();
    }

    @Test
    void diagnoseShouldReturnModelSpecificQueriesForQualityDws() throws Exception {
        Path targetDir = Files.createDirectories(tempDir.resolve("target"));
        Files.writeString(
            targetDir.resolve("manifest.json"),
            """
            {
              "nodes": {
                "model.demo.biz_dwd_quality_issue_v2": {
                  "name": "biz_dwd_quality_issue_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dwd_quality_issue_v2",
                  "path": "models/dwd/biz_dwd_quality_issue_v2.sql"
                },
                "model.demo.biz_dws_quality_monthly_v2": {
                  "name": "biz_dws_quality_monthly_v2",
                  "resource_type": "model",
                  "schema": "public",
                  "alias": "biz_dws_quality_monthly_v2",
                  "path": "models/dws/biz_dws_quality_monthly_v2.sql",
                  "depends_on": {
                    "nodes": ["model.demo.biz_dwd_quality_issue_v2"]
                  }
                }
              },
              "sources": {}
            }
            """
        );

        DbtConfigService configService = mock(DbtConfigService.class);
        when(configService.loadConfig()).thenReturn(
            new DbtConfigService.DbtConfigView(
                true,
                new DbtConfigService.DbtWorkspaceConfig(true, tempDir.toString(), "/tmp/profiles", "dts", "dev", UUID.randomUUID(), "biadmin", "public", Map.of()),
                null,
                null,
                null
            )
        );

        DbtProperties dbtProperties = new DbtProperties();
        dbtProperties.setEnabled(true);
        dbtProperties.setProjectDir(tempDir.toString());

        DbtTargetConnectionFactory connectionFactory = mock(DbtTargetConnectionFactory.class);
        DbtTargetConnectionFactory.TargetWarehouse target = new DbtTargetConnectionFactory.TargetWarehouse(
            UUID.randomUUID(),
            "biadmin",
            "public",
            "postgres",
            "jdbc:postgresql://warehouse/biadmin",
            "biadmin",
            "secret"
        );
        when(connectionFactory.resolveTarget()).thenReturn(target);

        Connection connection = mock(Connection.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        Statement statement = mock(Statement.class);
        ResultSet tablesCurrent = mock(ResultSet.class);
        ResultSet tablesUpstream = mock(ResultSet.class);
        ResultSet countCurrent = mock(ResultSet.class);
        ResultSet countUpstream = mock(ResultSet.class);

        when(connectionFactory.open(target)).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(connection.createStatement()).thenReturn(statement);

        when(metadata.getTables(null, "public", "biz_dws_quality_monthly_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesCurrent);
        when(metadata.getTables(null, "public", "biz_dwd_quality_issue_v2", new String[] { "TABLE", "VIEW" })).thenReturn(tablesUpstream);
        when(tablesCurrent.next()).thenReturn(true, false);
        when(tablesUpstream.next()).thenReturn(true, false);
        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dws_quality_monthly_v2\"")).thenReturn(countCurrent);
        when(statement.executeQuery("SELECT COUNT(*) FROM \"public\".\"biz_dwd_quality_issue_v2\"")).thenReturn(countUpstream);
        when(countCurrent.next()).thenReturn(true);
        when(countCurrent.getLong(1)).thenReturn(0L);
        when(countUpstream.next()).thenReturn(true);
        when(countUpstream.getLong(1)).thenReturn(6L);

        DbtRunResultService dbtRunResultService = mock(DbtRunResultService.class);
        when(dbtRunResultService.loadLatestSummary(20)).thenReturn(DbtRunResultService.DbtRunSummary.empty("none"));

        AirflowClient airflowClient = mock(AirflowClient.class);
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setEnabled(false);

        DbtModelDiagnosticsService service = new DbtModelDiagnosticsService(
            new ObjectMapper(),
            dbtProperties,
            configService,
            connectionFactory,
            dbtRunResultService,
            airflowClient,
            airflowProperties
        );

        DbtModelDiagnosticsService.ModelDiagnostics result = service.diagnose("biz_dws_quality_monthly_v2");

        assertThat(result.success()).isTrue();
        assertThat(result.findings()).anyMatch(item -> item.contains("issue_year"));
        assertThat(result.recommendedQueries()).anyMatch(item -> item.contains("FROM biz_dwd_quality_issue_v2"));
        assertThat(result.recommendedQueries()).anyMatch(item -> item.contains("FROM ods_quality_issue_v2"));
    }
}
