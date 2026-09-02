package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

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
    void shouldRejectDagIdPathTraversalWithoutTouchingFilesOutsideDagDirectory() throws Exception {
        String outsideStem = tempDir.getFileName() + "-outside";
        Path outsideFile = tempDir.resolveSibling(outsideStem + ".py");
        Files.writeString(outsideFile, "keep");
        IngestionTask task = task("manual", "../" + outsideStem);

        try {
            assertThatThrownBy(() -> dagService.rebuildDagForTask(task))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DAG ID");
            assertThat(Files.readString(outsideFile)).isEqualTo("keep");
        } finally {
            Files.deleteIfExists(outsideFile);
        }
    }

    @Test
    void shouldBindRequestedDagIdToPersistedTaskIdentity() {
        IngestionTask first = task("manual", "shared_dag");
        first.setId(101L);
        IngestionTask second = task("manual", "shared_dag");
        second.setId(202L);

        String firstDagId = dagService.rebuildDagForTask(first);
        String secondDagId = dagService.rebuildDagForTask(second);

        assertThat(firstDagId).isEqualTo("shared_dag_task_101");
        assertThat(secondDagId).isEqualTo("shared_dag_task_202");
        assertThat(firstDagId).isNotEqualTo(secondDagId);
        assertThat(tempDir.resolve(firstDagId + ".py")).exists();
        assertThat(tempDir.resolve(secondDagId + ".py")).exists();
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
        assertThat(dagSource).contains("/opt/addax/addax-env-runner.jar");
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
        assertThat(dagSource).contains("Mount(target=\"/decrypted\", source=None, type=\"tmpfs\", read_only=False)");
        assertThat(dagSource).contains("\"TMPDIR\": \"/decrypted\",");
        assertThat(dagSource).contains("\"DTS_INFRA_ENCRYPTION_KEY\": os.environ.get(\"DTS_INFRA_ENCRYPTION_KEY\", \"\"),");
        assertThat(dagSource).contains("\"DTS_INFRA_KEY_VERSION\": os.environ.get(\"DTS_INFRA_KEY_VERSION\", \"v1\"),");
        // docker.types.Mount 的 source 是必填位置参数；任何 Mount(...) 缺 source= 都会在 DAG 导入期抛 TypeError。
        // 该断言守住此不变量（曾因 tmpfs mount 漏写 source 导致现场 AIRFLOW_DAG_NOT_READY_TIMEOUT）。
        assertThatEveryMountHasSource(dagSource);
    }

    private void assertThatEveryMountHasSource(String dagSource) {
        // 仅校验单行完整 Mount(...) 调用（同时含 "Mount(" 与 ")"）；
        // 多行 Mount( opener 不含闭括号，其 source= 在后续行，自然被排除。
        dagSource.lines()
            .filter(line -> line.contains("Mount(") && line.contains(")"))
            .forEach(line -> assertThat(line)
                .as("每个 Mount(...) 调用都必须显式带 source=（docker-py 必填位置参数）: %s", line.trim())
                .contains("source="));
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
        assertThat(dagSource).contains("/opt/addax/addax-env-runner.jar");
        assertThat(dagSource).contains("entrypoint=\"java\"");
        assertThat(dagSource).contains("literal(ADDAX_RUNNER_JAR)");
        assertThat(dagSource).contains("\"com.yuzhi.dts.addax.AddaxEnvRunner\"");
        assertThat(dagSource).contains("\"/opt/addax/jobs/order.json\",");
        assertThat(dagSource).contains("\"/opt/addax/jobs/item.json\",");
        assertThat(dagSource).doesNotContain("entrypoint=\"/opt/addax/bin/addax.sh\"");
    }

    @Test
    void shouldGenerateThinApiDagThatCallsInternalIngestionEndpoint() throws Exception {
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

        String dagId = dagService.rebuildDagForTask(task);

        String dag = readDag(dagId);
        // API DAG uses PythonOperator as a thin trigger, not Addax DockerOperator or embedded API runtime.
        assertThat(dag).contains("from airflow.operators.python import PythonOperator");
        assertThat(dag).contains("python_callable=_trigger_api_ingestion");
        assertThat(dag).doesNotContain("from airflow.providers.docker.operators.docker import DockerOperator");
        assertThat(dag).contains("/internal/api-ingestion/executions");
        assertThat(dag).contains("X-DTS-Service");
        assertThat(dag).contains("DTS_INGESTION_INTERNAL_BASE_URL");
        assertThat(dag).contains("os.getenv(\"DTS_AIRFLOW_TO_INGESTION_TOKEN\", \"\")");
        assertThat(dag).contains("SERVICE_NAME = \"dts-airflow\"");
        assertThat(dag).doesNotContain("os.getenv(\"DTS_SERVICE_TOKEN\"");
        assertThat(dag).contains("DTS_API_INGESTION_POLL_TIMEOUT_SECONDS\", \"1800\"");
        assertThat(dag).contains("\"execution_timeout\": timedelta(seconds=1800)");
        assertThat(dag).contains("urllib.request.ProxyHandler({})");
        assertThat(dag).contains("_DTS_INTERNAL_HTTP_OPENER.open(request, timeout=30)");
        assertThat(dag).contains("\"taskId\": INGESTION_TASK_ID");
        assertThat(dag).contains("\"batchId\": batch_id");
        assertThat(dag).contains("response.get(\"id\") or response.get(\"executionId\")");
        // Schedule expression is "None" for manual
        assertThat(dag).contains("schedule=None");
        // Business logic and credential semantics must live in dts-ingestion Java runtime.
        assertThat(dag).doesNotContain("SOURCE_CONFIG_JSON");
        assertThat(dag).doesNotContain("\"baseUrl\":\"https://api.example.com\"");
        assertThat(dag).doesNotContain("DTS_API_BEARER_TOKEN");
        assertThat(dag).doesNotContain("DTS_API_KEY");
        assertThat(dag).doesNotContain("DTS_API_USERNAME");
        assertThat(dag).doesNotContain("DTS_API_PASSWORD");
        assertThat(dag).doesNotContain("psycopg2");
        assertThat(dag).doesNotContain("DTS_TARGET_DB_PASSWORD");
        assertThat(dag).doesNotContain("_ensure_landing_table");
        assertThat(dag).doesNotContain("dts_api_ingestion_checkpoint");
        assertThat(dag).doesNotContain("_dts_raw_record JSONB");
        assertThat(dag).doesNotContain("_request_json");
    }

    @Test
    void stagedAddaxDagShouldBeRevisionScopedPausedAndRegisterExactExecutionBeforeWorkload() throws Exception {
        IngestionTask task = task("cron:0 0 * * *", "ods_orders_task_99_revision_12");
        task.setId(99L);

        AirflowDagService.StagedDag staged = dagService.stageDagForTask(task, List.of(), 13L, "checksum-r13");
        String source = Files.readString(staged.stagedPath());

        assertThat(staged.dagId()).isEqualTo("ods_orders_task_99_revision_13");
        assertThat(source).contains("is_paused_upon_creation=True");
        assertThat(source).contains("DTS_REVISION_ID = 13");
        assertThat(source).contains("DTS_CONFIG_CHECKSUM = \"checksum-r13\"");
        assertThat(source).contains("DTS_DAG_ID = \"ods_orders_task_99_revision_13\"");
        assertThat(source).contains("/internal/api-ingestion/scheduled-executions");
        assertThat(source.indexOf("on_execute_callback=_register_dts_execution"))
            .isLessThan(source.indexOf("tty=False"));

        dagService.retireDagStrict("ods_orders_task_99_revision_12", task);
        verify(airflowClient).setDagPausedStrict("ods_orders_task_99_revision_12", true);
    }

    @Test
    void stagedApiDagShouldCarryExactRevisionContract() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setId(88L);
        task.setName("api-orders");
        task.setSourceType("api");
        task.setSyncSchedule("manual");
        task.setAirflowDagId("ods_api_orders_task_88_revision_3");

        AirflowDagService.StagedDag staged = dagService.stageDagForTask(task, List.of(), 4L, "checksum-r4");
        String source = Files.readString(staged.stagedPath());

        assertThat(staged.dagId()).isEqualTo("ods_api_orders_task_88_revision_4");
        assertThat(source).contains("INGESTION_REVISION_ID = 4");
        assertThat(source).contains("INGESTION_CONFIG_CHECKSUM = \"checksum-r4\"");
        assertThat(source).contains("\"revisionId\": INGESTION_REVISION_ID");
        assertThat(source).contains("\"airflowDagId\": INGESTION_DAG_ID");
        assertThat(source).contains("is_paused_upon_creation=True");
    }

    @Test
    void shouldCarryImmutableClassificationSealIntoAirflowOpenLineageDatasets() throws Exception {
        IngestionTask task = task("manual", "task_openlineage_classification");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        task.setTableMapping(
            mapper.readTree("[{\"source\":\"public.customer\",\"target\":\"ods.ods_customer\"}]")
        );
        task.setClassificationSeal(
            mapper.readTree(
                """
                {
                  "sealId":"seal-s72",
                  "subjectType":"ASSET",
                  "subjectKey":"data-source:s72",
                  "effectiveLevel":"SECRET",
                  "snapshotVersion":7,
                  "checksum":"0123456789abcdef0123456789abcdef",
                  "sealedAt":"2026-07-26T00:00:00Z"
                }
                """
            )
        );

        dagService.rebuildDagForTask(task);

        String dag = readDag("task_openlineage_classification");
        assertThat(dag).contains("make_openlineage_callback(\"COMPLETE\", LINEAGE_DATASETS)");
        assertThat(dag).contains("\"dtsGovernance\"");
        assertThat(dag).contains("\"classification\":\"SECRET\"");
        assertThat(dag).contains("\"sealId\":\"seal-s72\"");
        assertThat(dag).contains("\"snapshotVersion\":7");
        assertThat(dag).contains("\"subjectKey\":\"data-source:s72\"");
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
        assertThat(dag).contains("ALTER TABLE \\\"ods_duplicate_columns\\\" ADD COLUMN IF NOT EXISTS");
        assertThat(dag).doesNotContain("DROP TABLE");
        assertThat(dag).doesNotContain("CASCADE");
    }

    @Test
    void shouldUseManagedFileLandingTargetInsteadOfWriterPlaceholder() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setName("managed-file-target");
        task.setSourceType("txtfilereader");
        task.setSyncSchedule("manual");
        task.setAirflowDagId("managed_file_target");
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        task.setSourceConfig(mapper.readTree(
            "{\"_fileColumns\":[{\"name\":\"order_id\",\"type\":\"string\"}],"
            + "\"_fileLanding\":{\"landingMode\":\"create_new\",\"targetTable\":\"ods_orders\"}}"
        ));
        task.setDestinationConfig(mapper.readTree("{\"table\":[\"${table}\"]}"));

        dagService.rebuildDagForTask(task);

        String dag = readDag("managed_file_target");
        assertThat(dag).contains("CREATE TABLE IF NOT EXISTS \\\"ods_orders\\\"");
        assertThat(dag).doesNotContain("${table}");
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
