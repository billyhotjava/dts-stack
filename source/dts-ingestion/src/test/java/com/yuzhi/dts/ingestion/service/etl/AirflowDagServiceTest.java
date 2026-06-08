package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.AirflowProperties;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AirflowDagServiceTest {

    @Mock
    private IngestionSettingsService settingsService;

    @Mock
    private AirflowClient airflowClient;

    @TempDir
    Path tempDir;

    private AirflowDagService dagService;

    @BeforeEach
    void setUp() {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setDagsDir(tempDir.toString());

        AddaxProperties addaxProperties = new AddaxProperties();
        addaxProperties.setImage("dts-addax:test");
        addaxProperties.setJobDir("/opt/addax/jobs");

        org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test")
            .withProperty("spring.datasource.username", "test")
            .withProperty("spring.datasource.password", "test");
        dagService = new AirflowDagService(airflowProperties, addaxProperties, settingsService, airflowClient, mockEnv);
        lenient().when(settingsService.getSettings(anyString())).thenReturn(new IngestionSettingsService.SettingsSnapshot(Map.of()));
    }

    @Test
    void shouldGenerateNoneScheduleForManualTasks() throws Exception {
        IngestionTask task = task("manual", "task_manual_demo");

        dagService.rebuildDagForTask(task);

        String dagSource = readDag("task_manual_demo");
        assertThat(dagSource).contains("schedule=None");
        assertThat(dagSource).doesNotContain("timedelta(");
    }

    @Test
    void shouldTranslateCronScheduleForSingleTaskDag() throws Exception {
        IngestionTask task = task("cron:0 0 * * *", "task_cron_demo");

        dagService.rebuildDagForTask(task);

        String dagSource = readDag("task_cron_demo");
        assertThat(dagSource).contains("schedule=\"0 0 * * *\"");
        assertThat(dagSource).doesNotContain("schedule=\"cron:0 0 * * *\"");
    }

    @Test
    void shouldTranslateIntervalScheduleForSingleTaskDag() throws Exception {
        IngestionTask task = task("interval:60", "task_interval_demo");

        dagService.rebuildDagForTask(task);

        String dagSource = readDag("task_interval_demo");
        assertThat(dagSource).contains("from datetime import datetime, timedelta");
        assertThat(dagSource).contains("schedule=timedelta(minutes=60)");
        assertThat(dagSource).doesNotContain("schedule=\"interval:60\"");
        assertThat(dagSource).contains("ADDAX_RUNNER_JAR = os.getenv(\"ADDAX_RUNNER_JAR\"");
        assertThat(dagSource).contains("from airflow.utils.template import literal");
        assertThat(dagSource).contains("entrypoint=\"java\"");
        assertThat(dagSource).contains("\"-cp\",");
        assertThat(dagSource).contains("literal(ADDAX_RUNNER_JAR)");
        assertThat(dagSource).contains("\"com.yuzhi.dts.addax.AddaxEnvRunner\"");
        assertThat(dagSource).contains("run_cmd,");
        assertThat(dagSource).doesNotContain("entrypoint=\"/opt/addax/bin/addax.sh\"");
    }

    @Test
    void shouldInjectTmpfsAndEncryptionEnvForAddaxDag() throws Exception {
        IngestionTask task = task("manual", "task_addax_security_demo");

        dagService.rebuildDagForTask(task);

        String dagSource = readDag("task_addax_security_demo");
        assertThat(dagSource).contains("Mount(target=\"/decrypted\", type=\"tmpfs\", read_only=False)");
        assertThat(dagSource).contains("\"TMPDIR\": \"/decrypted\",");
        assertThat(dagSource).contains("\"DTS_INFRA_ENCRYPTION_KEY\": os.environ.get(\"DTS_INFRA_ENCRYPTION_KEY\", \"\"),");
        assertThat(dagSource).contains("\"DTS_INFRA_KEY_VERSION\": os.environ.get(\"DTS_INFRA_KEY_VERSION\", \"v1\"),");
    }

    @Test
    void shouldTranslateIntervalScheduleForMultiTaskDag() throws Exception {
        IngestionTask task = task("interval:90", "task_interval_multi_demo");

        dagService.rebuildDagForTask(
            task,
            List.of(
                new AddaxJobService.PerTableJob("ods_order", "/opt/addax/jobs/order.json", "/tmp/order.json"),
                new AddaxJobService.PerTableJob("ods_item", "/opt/addax/jobs/item.json", "/tmp/item.json")
            )
        );

        String dagSource = readDag("task_interval_multi_demo");
        assertThat(dagSource).contains("from datetime import datetime, timedelta");
        assertThat(dagSource).contains("schedule=timedelta(minutes=90)");
        assertThat(dagSource).doesNotContain("schedule=\"interval:90\"");
        assertThat(dagSource).contains("ADDAX_RUNNER_JAR = os.getenv(\"ADDAX_RUNNER_JAR\"");
        assertThat(dagSource).contains("entrypoint=\"java\"");
        assertThat(dagSource).contains("literal(ADDAX_RUNNER_JAR)");
        assertThat(dagSource).contains("\"com.yuzhi.dts.addax.AddaxEnvRunner\"");
        assertThat(dagSource).contains("\"/opt/addax/jobs/order.json\",");
        assertThat(dagSource).contains("\"/opt/addax/jobs/item.json\",");
        assertThat(dagSource).doesNotContain("entrypoint=\"/opt/addax/bin/addax.sh\"");
    }

    @Test
    void shouldGenerateApiDagWithoutPlaintextPasswordAndWithPythonOperator() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setId(99L);
        task.setName("api-orders-mock");
        task.setSourceType("api");
        task.setSyncSchedule("manual");
        task.setAirflowDagId("ods_api_orders_mock");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        task.setSourceConfig(mapper.readTree(
            "{\"baseUrl\":\"https://api.example.com\","
            + "\"defaultHeaders\":{\"Accept\":\"application/json\"},"
            + "\"auth\":{\"provider\":\"none\"},"
            + "\"resource\":{\"resourceId\":\"orders\",\"path\":\"/v1/orders\","
            + "\"method\":\"GET\",\"recordPath\":\"data.items\","
            + "\"query\":{\"page\":1,\"size\":50},\"targetTable\":\"ods_api_orders\"}}"
        ));

        dagService.rebuildDagForTask(task);

        String dag = readDag("ods_api_orders_mock");
        // No plaintext password embedded (regression guard for AirflowDagService password leak)
        assertThat(dag).doesNotContain("Devops123");
        assertThat(dag).doesNotContain("password=os.getenv(\"DTS_TARGET_DB_PASSWORD\",");
        // Strict env-only password lookup
        assertThat(dag).contains("os.environ[\"DTS_TARGET_DB_PASSWORD\"]");
        // API DAG uses PythonOperator (not Addax DockerOperator)
        assertThat(dag).contains("from airflow.operators.python import PythonOperator");
        assertThat(dag).contains("python_callable=_run_api_ingestion");
        assertThat(dag).doesNotContain("from airflow.providers.docker.operators.docker import DockerOperator");
        // Source config is embedded as JSON literal so the worker can parse it
        assertThat(dag).contains("SOURCE_CONFIG_JSON");
        assertThat(dag).contains("\"baseUrl\":\"https://api.example.com\"");
        // Schedule expression is "None" for manual
        assertThat(dag).contains("schedule=None");
        // ODS landing table includes raw_record + technical columns
        assertThat(dag).contains("_dts_raw_record JSONB");
        assertThat(dag).contains("_dts_batch_id");
    }

    @Test
    void shouldDeduplicateFileSourceColumnsInInitTableDdl() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setName("file-duplicate-columns");
        task.setSourceType("txtfilereader");
        task.setSyncSchedule("manual");
        task.setAirflowDagId("file_duplicate_columns");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        task.setTableMapping(mapper.readTree("[{\"target\":\"ods_duplicate_columns\"}]"));
        task.setSourceConfig(mapper.readTree(
            "{\"_autoId\":true,\"_fileColumns\":["
            + "{\"name\":\"id\",\"type\":\"string\"},"
            + "{\"name\":\"newcolumn\",\"type\":\"string\"},"
            + "{\"name\":\"newcolumn\",\"type\":\"string\"},"
            + "{\"name\":\"_dts_source_system\",\"type\":\"string\"}"
            + "]}"
        ));

        dagService.rebuildDagForTask(task);

        String dag = readDag("file_duplicate_columns");
        assertThat(dag).contains("\\\"id_2\\\" varchar(500)");
        assertThat(dag).contains("\\\"newcolumn\\\" varchar(500)");
        assertThat(dag).contains("\\\"newcolumn_2\\\" varchar(500)");
        assertThat(dag).contains("\\\"_dts_source_system_2\\\" varchar(500)");
        assertThat(dag).doesNotContain("\\\"_dts_source_system\\\" varchar(500)");
    }

    private IngestionTask task(String syncSchedule, String dagId) {
        IngestionTask task = new IngestionTask();
        task.setName("dm8test");
        task.setSourceType("dm");
        task.setSyncSchedule(syncSchedule);
        task.setAirflowDagId(dagId);
        return task;
    }

    private String readDag(String dagId) throws Exception {
        return Files.readString(tempDir.resolve(dagId + ".py"));
    }
}
