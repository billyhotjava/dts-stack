package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.config.InfraSecurityProperties;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.InfraSettingsCryptoService;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 单元测试：AddaxJobService
 * 测试Addax Job JSON生成和保存功能
 */
@ExtendWith(MockitoExtension.class)
class AddaxJobServiceTest {

    @Mock
    private AddaxProperties addaxProperties;

    @Mock
    private IngestionSettingsService settingsService;

    @Mock
    private JdbcMetadataService jdbcMetadataService;

    @Mock
    private IngestionSourceResolver sourceResolver;

    private ObjectMapper objectMapper;
    private AddaxJobService addaxJobService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test")
            .withProperty("spring.datasource.username", "test")
            .withProperty("spring.datasource.password", "test");
        InfraSecurityProperties securityProperties = new InfraSecurityProperties();
        securityProperties.setEncryptionKey(Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        ));
        securityProperties.setKeyVersion("v1");
        InfraSettingsCryptoService cryptoService = new InfraSettingsCryptoService(securityProperties);
        cryptoService.init();
        addaxJobService = new AddaxJobService(
            addaxProperties,
            settingsService,
            objectMapper,
            jdbcMetadataService,
            new AddaxJdbcConfigNormalizer(objectMapper),
            mockEnv,
            cryptoService
        );
        addaxJobService.setSourceResolver(sourceResolver);

        // Mock settings service
        IngestionSettingsService.SettingsSnapshot settingsSnapshot = new IngestionSettingsService.SettingsSnapshot(
            Map.of(
                "jobDir", tempDir.toString(),
                "dagsDir", tempDir.toString(),
                "layerDir", ""
            )
        );
        lenient().when(settingsService.getSettings(anyString())).thenReturn(settingsSnapshot);
    }

    @Test
    void shouldGenerateMySQLToPostgresJob() throws Exception {
        // Given
        String taskName = "test-mysql-sync";
        String readerType = "mysqlreader";
        Map<String, Object> readerConfig = Map.of(
            "username", "root",
            "password", "password",
            "host", "mysql-host",
            "port", 3306,
            "database", "testdb",
            "table", "users"
        );
        String writerType = "postgresqlwriter";
        Map<String, Object> writerConfig = Map.of(
            "username", "postgres",
            "password", "password",
            "host", "pg-host",
            "port", 5432,
            "database", "targetdb",
            "table", "users"
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            taskName,
            readerType,
            readerConfig,
            writerType,
            writerConfig,
            null
        );

        // Then
        assertThat(result).isNotNull();
        assertThat(result.jobName()).contains("test-mysql-sync");
        assertThat(result.jobName()).endsWith(".json");
        assertThat(result.jobPath()).startsWith(tempDir.toString());

        // Verify file exists
        Path jobPath = Path.of(result.jobPath());
        assertThat(Files.exists(jobPath)).isTrue();

        String sealedContent = Files.readString(jobPath);
        assertThat(sealedContent)
            .startsWith(AddaxJobService.SEALED_JOB_PREFIX)
            .doesNotContain("password");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(jobPath))).isEqualTo("rw-r-----");
        Map<String, Object> jobConfig = addaxJobService.readManagedJob(jobPath);
        assertThat(jobConfig).containsKey("job");

        Map<String, Object> job = (Map<String, Object>) jobConfig.get("job");
        assertThat(job).containsKeys("content", "setting");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepTargetDataSourceMetadataOutOfWriterParameters() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of("jdbcUrl", "jdbc:mysql://mysql:3306/source", "table", java.util.List.of("orders")),
            "username", "root",
            "password", "secret"
        );
        Map<String, Object> writerConfig = new java.util.LinkedHashMap<>();
        writerConfig.put("targetDataSourceId", "11111111-2222-3333-4444-555555555555");
        writerConfig.put("destinationDataSourceId", "22222222-3333-4444-5555-666666666666");
        writerConfig.put("dataSourceId", "33333333-4444-5555-6666-777777777777");
        writerConfig.put("connection", Map.of("jdbcUrl", "jdbc:postgresql://pg:5432/biadmin", "table", java.util.List.of("orders")));
        writerConfig.put("username", "biadmin");
        writerConfig.put("password", "secret");

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "metadata-strip",
            "rdbmsreader",
            readerConfig,
            "rdbmswriter",
            writerConfig,
            null
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> parameter = (Map<String, Object>) writer.get("parameter");
        assertThat(parameter).doesNotContainKeys("targetDataSourceId", "destinationDataSourceId", "dataSourceId");
        assertThat(parameter.get("username")).isEqualTo("biadmin");
    }

    @Test
    void shouldCreateJobFromIngestionTask() throws Exception {
        // Given
        IngestionTask task = new IngestionTask();
        task.setName("test-task");
        task.setSourceType("mysqlreader");

        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("username", "root");
        sourceConfig.put("password", "password");
        sourceConfig.put("host", "localhost");
        sourceConfig.put("port", 3306);
        sourceConfig.put("database", "testdb");
        task.setSourceConfig(sourceConfig);

        task.setDestinationType("postgresqlwriter");
        ObjectNode destConfig = objectMapper.createObjectNode();
        destConfig.put("username", "postgres");
        destConfig.put("password", "password");
        destConfig.put("host", "localhost");
        destConfig.put("port", 5432);
        destConfig.put("database", "targetdb");
        task.setDestinationConfig(destConfig);

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(task);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.jobName()).contains("test-task");
        assertThat(Files.exists(Path.of(result.jobPath()))).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeReaderTypeForExcelFileByMetadata() throws Exception {
        // Given
        IngestionTask task = new IngestionTask();
        task.setName("file-type-fallback-excel");
        task.setSourceType("txtfilereader");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("_fileType", "excel");
        sourceConfig.put("_originalName", "ods_finance_own_fund.xlsx");
        sourceConfig.put("path", "/opt/airflow/dags/exchange/excel/ods_finance_own_fund.xlsx.enc");
        sourceConfig.put("table", "ods_finance_own_fund");
        task.setSourceConfig(sourceConfig);

        task.setDestinationType("postgresqlwriter");
        ObjectNode destinationConfig = objectMapper.createObjectNode();
        destinationConfig.put("username", "biadmin");
        destinationConfig.put("password", "password");
        destinationConfig.put("host", "127.0.0.1");
        destinationConfig.put("port", 5432);
        destinationConfig.put("database", "biadmin");
        destinationConfig.put("table", "ods_finance_own_fund");
        task.setDestinationConfig(destinationConfig);

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(task);

        // Then
        Map<String, Object> jobConfig = result.jobConfig();
        Map<String, Object> job = (Map<String, Object>) jobConfig.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        assertThat(reader.get("name")).isEqualTo("excelreader");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldForceExcelReaderFromPathWhenSourceTypeIsLegacyTxt() throws Exception {
        // Given
        IngestionTask task = new IngestionTask();
        task.setName("legacy-file-source-type");
        task.setSourceType("txt");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("path", "/opt/airflow/dags/upload/ods_finance_own_fund.xlsx.enc");
        sourceConfig.put("table", "ods_finance_own_fund");
        task.setSourceConfig(sourceConfig);

        task.setDestinationType("postgresqlwriter");
        ObjectNode destinationConfig = objectMapper.createObjectNode();
        destinationConfig.put("username", "biadmin");
        destinationConfig.put("password", "password");
        destinationConfig.put("host", "127.0.0.1");
        destinationConfig.put("port", 5432);
        destinationConfig.put("database", "biadmin");
        destinationConfig.put("table", "ods_finance_own_fund");
        task.setDestinationConfig(destinationConfig);

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(task);

        // Then
        Map<String, Object> jobConfig = result.jobConfig();
        Map<String, Object> job = (Map<String, Object>) jobConfig.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        assertThat(reader.get("name")).isEqualTo("excelreader");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNormalizeReaderTypeWhenHostPathHintsExcel() throws Exception {
        // Given
        IngestionTask task = new IngestionTask();
        task.setName("host-path-host-hint");
        task.setSourceType("mysqlreader");
        ObjectNode sourceConfig = objectMapper.createObjectNode();
        sourceConfig.put("hostPath", "/opt/airflow/dags/upload/ods_finance_own_fund.xlsx.enc");
        sourceConfig.put("_fileColumns", "[]");
        sourceConfig.put("table", "ods_finance_own_fund");
        task.setSourceConfig(sourceConfig);

        task.setDestinationType("postgresqlwriter");
        ObjectNode destinationConfig = objectMapper.createObjectNode();
        destinationConfig.put("username", "biadmin");
        destinationConfig.put("password", "password");
        destinationConfig.put("host", "127.0.0.1");
        destinationConfig.put("port", 5432);
        destinationConfig.put("database", "biadmin");
        destinationConfig.put("table", "ods_finance_own_fund");
        task.setDestinationConfig(destinationConfig);

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(task);

        // Then
        Map<String, Object> jobConfig = result.jobConfig();
        Map<String, Object> job = (Map<String, Object>) jobConfig.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        assertThat(reader.get("name")).isEqualTo("excelreader");
    }

    @Test
    void shouldNormalizeDriverAndJdbcUrl() throws Exception {
        // Given
        String taskName = "normalize-test";
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of("jdbcUrl", "jdbc:dm://10.0.0.1:5236/DMHR")
        );
        Map<String, Object> writerConfig = Map.of(
            "connection", Map.of("jdbcUrl", "[\"jdbc:postgresql://10.0.0.2:5432/biadmin\"]")
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            taskName,
            "rdbmsreader",
            readerConfig,
            "rdbmswriter",
            writerConfig,
            null
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> readerParams = (Map<String, Object>) reader.get("parameter");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");

        assertThat(readerParams.get("driver")).isEqualTo("dm.jdbc.driver.DmDriver");
        assertThat(writerParams.get("driver")).isEqualTo("org.postgresql.Driver");

        Map<String, Object> writerConn = (Map<String, Object>) ((java.util.List<?>) writerParams.get("connection")).get(0);
        assertThat(writerConn.get("jdbcUrl")).isInstanceOf(String.class);
    }

    @Test
    void shouldReplaceWriterTablePlaceholder() throws Exception {
        // Given
        String taskName = "placeholder-test";
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:dm://10.0.0.1:5236/DMHR",
                "table", java.util.List.of("city", "department")
            )
        );
        Map<String, Object> writerConfig = Map.of(
            "connection", Map.of("jdbcUrl", "jdbc:postgresql://10.0.0.2:5432/biadmin", "table", java.util.List.of("${table}"))
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            taskName,
            "rdbmsreader",
            readerConfig,
            "rdbmswriter",
            writerConfig,
            null
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        java.util.List<Map<String, Object>> contentList = (java.util.List<Map<String, Object>>) job.get("content");
        assertThat(contentList).hasSize(2);

        Map<String, Object> firstWriter = (Map<String, Object>) contentList.get(0).get("writer");
        Map<String, Object> firstParams = (Map<String, Object>) firstWriter.get("parameter");
        Map<String, Object> firstConn = (Map<String, Object>) ((java.util.List<?>) firstParams.get("connection")).get(0);
        assertThat(firstConn.get("table")).isEqualTo(java.util.List.of("city"));

        Map<String, Object> secondWriter = (Map<String, Object>) contentList.get(1).get("writer");
        Map<String, Object> secondParams = (Map<String, Object>) secondWriter.get("parameter");
        Map<String, Object> secondConn = (Map<String, Object>) ((java.util.List<?>) secondParams.get("connection")).get(0);
        assertThat(secondConn.get("table")).isEqualTo(java.util.List.of("department"));
    }

    @Test
    void shouldSaveJobJson() throws Exception {
        // Given
        Long taskId = 123L;
        String jobJson = """
            {
                "job": {
                    "content": [],
                    "setting": {}
                }
            }
            """;

        // When
        String jobPath = addaxJobService.saveJobJson(jobJson, taskId);

        // Then
        assertThat(jobPath).startsWith(tempDir.toString());
        assertThat(jobPath).contains("task_123");
        assertThat(Files.exists(Path.of(jobPath))).isTrue();

        String savedContent = Files.readString(Path.of(jobPath));
        assertThat(savedContent).startsWith(AddaxJobService.SEALED_JOB_PREFIX).doesNotContain("content", "setting");
        assertThat(addaxJobService.readManagedJob(Path.of(jobPath))).containsKey("job");
    }

    @Test
    void shouldDetectMalformedJobConfig() throws Exception {
        // Given
        String badJobJson = """
            {
              "job": {
                "content": [
                  {
                    "writer": {
                      "name": "rdbmswriter",
                      "parameter": {
                        "connection": [
                          { "jdbcUrl": "[\\"jdbc:postgresql://localhost:5432/db\\"]" }
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """;
        Path jobPath = tempDir.resolve("bad-job.json");
        Files.writeString(jobPath, badJobJson);

        // When
        boolean malformed = addaxJobService.isJobConfigMalformed(jobPath);

        // Then
        assertThat(malformed).isTrue();
    }

    @Test
    void shouldTreatWriterJdbcUrlListAsMalformed() throws Exception {
        // Given
        String badJobJson = """
            {
              "job": {
                "content": [
                  {
                    "writer": {
                      "name": "rdbmswriter",
                      "parameter": {
                        "connection": [
                          { "jdbcUrl": ["jdbc:postgresql://localhost:5432/db"] }
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """;
        Path jobPath = tempDir.resolve("bad-job-list.json");
        Files.writeString(jobPath, badJobJson);

        // When
        boolean malformed = addaxJobService.isJobConfigMalformed(jobPath);

        // Then
        assertThat(malformed).isTrue();
    }


    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepOnlyPerTableSqlAfterSplit() throws Exception {
        // Given
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:dm://10.0.0.1:5236/ERPDEMO",
                "table", java.util.List.of("ERPDEMO.CUSTOMER", "ERPDEMO.EMPLOYEE")
            ),
            "sourceSystem", "ERP"
        );
        Map<String, Object> writerConfig = Map.of(
            "username", "biadmin",
            "password", "fixture-only-password",
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
                "table", java.util.List.of("ods_customer", "ods_employee")
            ),
            "preSql", java.util.List.of(
                "TRUNCATE TABLE ods_customer",
                "TRUNCATE TABLE ods_employee"
            ),
            "postSql", java.util.List.of(
                "UPDATE ods_customer SET source_system = 'ERP' WHERE TRUE",
                "UPDATE ods_employee SET source_system = 'ERP' WHERE TRUE"
            )
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "split-sql-test",
            "rdbmsreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        java.util.List<Map<String, Object>> contentList = (java.util.List<Map<String, Object>>) job.get("content");
        assertThat(contentList).hasSize(2);

        Map<String, Object> firstWriter = (Map<String, Object>) contentList.get(0).get("writer");
        Map<String, Object> firstParams = (Map<String, Object>) firstWriter.get("parameter");
        java.util.List<String> firstPreSql = (java.util.List<String>) firstParams.get("preSql");
        java.util.List<String> firstPostSql = (java.util.List<String>) firstParams.get("postSql");
        assertThat(firstPreSql).contains("TRUNCATE TABLE ods_customer");
        assertThat(firstPreSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_employee"));
        assertThat(firstPostSql).allMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_customer"));
        assertThat(firstPostSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_employee"));
        assertThat(firstPostSql).anyMatch(sql -> sql.contains("\"_dts_source_table\" = 'CUSTOMER'"));

        Map<String, Object> secondWriter = (Map<String, Object>) contentList.get(1).get("writer");
        Map<String, Object> secondParams = (Map<String, Object>) secondWriter.get("parameter");
        java.util.List<String> secondPreSql = (java.util.List<String>) secondParams.get("preSql");
        java.util.List<String> secondPostSql = (java.util.List<String>) secondParams.get("postSql");
        assertThat(secondPreSql).contains("TRUNCATE TABLE ods_employee");
        assertThat(secondPreSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_customer"));
        assertThat(secondPostSql).allMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_employee"));
        assertThat(secondPostSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_customer"));
        assertThat(secondPostSql).anyMatch(sql -> sql.contains("\"_dts_source_table\" = 'EMPLOYEE'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldResolveFileSourceSystemFromObjectMetadata() throws Exception {
        // Given
        Map<String, Object> readerConfig = Map.of(
            "_originalName", Map.of("name", "专利测试数据_三年1000条.xlsx"),
            "_containerPath", "/opt/airflow/dags/exchange/excel/demo/source.xlsx",
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/demo/source.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "ods_patent_info"
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "file-source-system-test",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");

        assertThat(postSql)
            .anyMatch(sql -> sql.contains("_dts_source_system") && sql.contains("'专利测试数据_三年1000条.xlsx'"));
        assertThat(postSql)
            .anyMatch(sql -> sql.contains("\"_dts_source_table\" = '专利测试数据_三年1000条.xlsx'"));
        assertThat(postSql)
            .noneMatch(sql -> sql.contains("_dts_source_system\" = 'unknown'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldInjectDtsRuntimeColumnsIntoPostSql() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:dm://10.0.0.1:5236/ERPDEMO",
                "table", java.util.List.of("ERPDEMO.CUSTOMER")
            ),
            "sourceSystem", "ERP"
        );
        Map<String, Object> writerConfig = Map.of(
            "username", "biadmin",
            "password", "fixture-only-password",
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
                "table", java.util.List.of("ods_customer")
            )
        );
        Map<String, Object> runtimeContext = Map.of(
            "batchId", "batch-task-1-abc",
            "executionId", "101",
            "taskId", "1"
        );

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "runtime-column-test",
            "rdbmsreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null,
            "full_refresh",
            null,
            runtimeContext
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");

        assertThat(postSql).anyMatch(sql -> sql.contains("_dts_batch_id") && sql.contains("'batch-task-1-abc'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("_dts_execution_id") && sql.contains("'101'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("_dts_task_id") && sql.contains("'1'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("\"_dts_source_table\" = 'CUSTOMER'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldInjectFileLineageColumnsIntoFileSourceJob() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "_fileId", "file-001",
            "_fileColumns", java.util.List.of(
                Map.of("safeName", "project_code", "type", "string"),
                Map.of("safeName", "plan_date", "type", "date")
            ),
            "_originalName", "项目计划.xlsx",
            "_sheetName", "计划表",
            "_fileHash", "sha256-demo",
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/demo/source.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "ods_project_plan"
        );

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "file-lineage-test",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> readerParams = (Map<String, Object>) reader.get("parameter");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");
        String createSql = preSql.stream()
            .filter(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
            .findFirst()
            .orElseThrow();

        assertThat(readerParams).doesNotContainKey("_fileId");
        assertThat(createSql).contains("\"_dts_source_file\" VARCHAR(500) DEFAULT '项目计划.xlsx'");
        assertThat(createSql).contains("\"_dts_source_sheet\" VARCHAR(500) DEFAULT '计划表'");
        assertThat(createSql).contains("\"_dts_file_hash\" VARCHAR(500) DEFAULT 'sha256-demo'");
        assertThat(createSql).contains("\"_dts_row_number\" INTEGER");
        assertThat(postSql).anyMatch(sql -> sql.contains("\"_dts_source_file\" = '项目计划.xlsx'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("\"_dts_source_sheet\" = '计划表'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("\"_dts_file_hash\" = 'sha256-demo'"));
        assertThat(postSql).anyMatch(sql -> sql.contains("row_number() OVER (ORDER BY ctid) + 1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeduplicateFileSourceWriterColumnsAndReserveTechnicalColumns() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "_fileColumns", java.util.List.of(
                Map.of("safeName", "newcolumn", "type", "string"),
                Map.of("safeName", "newcolumn", "type", "string"),
                Map.of("safeName", "_dts_source_system", "type", "string")
            ),
            "_originalName", "duplicate-columns.xlsx",
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/demo/source.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "ods_duplicate_columns"
        );

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "file-duplicate-columns-test",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> writerColumns = (java.util.List<String>) writerParams.get("column");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");
        String createSql = preSql.stream()
            .filter(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
            .findFirst()
            .orElseThrow();

        assertThat(writerColumns).containsExactly("\"newcolumn\"", "\"newcolumn_2\"", "\"_dts_source_system_2\"");
        assertThat(createSql).contains("\"newcolumn\" varchar(500)");
        assertThat(createSql).contains("\"newcolumn_2\" varchar(500)");
        assertThat(createSql).contains("\"_dts_source_system_2\" varchar(500)");
        assertThat(createSql).contains("\"_dts_source_system\" VARCHAR(500) DEFAULT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepDropAndCreatePreSqlForFileSourceFullRefresh() throws Exception {
        // Given
        Map<String, Object> readerConfig = Map.of(
            "_fileColumns", java.util.List.of(
                Map.of("safeName", "patent_no", "type", "string"),
                Map.of("safeName", "application_date", "type", "date")
            ),
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/demo/source.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "ods_patent_info"
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "file-full-refresh-test",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null,
            "full_refresh"
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");

        assertThat(preSql).isNotNull();
        assertThat(preSql).anyMatch(sql -> sql.startsWith("DROP TABLE IF EXISTS"));
        assertThat(preSql).anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"));
        assertThat(preSql).noneMatch(sql -> sql.startsWith("TRUNCATE TABLE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldForceFileSourceColumnsToLandAsText() throws Exception {
        // Given
        Map<String, Object> readerConfig = Map.of(
            "_fileColumns", java.util.List.of(
                Map.of("safeName", "project_code", "type", "string"),
                Map.of("safeName", "plan_date", "type", "date"),
                Map.of("safeName", "risk_score", "type", "double"),
                Map.of("safeName", "is_key_node", "type", "boolean")
            ),
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/demo/source.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "ods_project_subject_domain"
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "file-text-landing-test",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");
        String createSql = preSql.stream()
            .filter(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"))
            .findFirst()
            .orElseThrow();

        assertThat(createSql).contains("\"project_code\" varchar(500)");
        assertThat(createSql).contains("\"plan_date\" varchar(500)");
        assertThat(createSql).contains("\"risk_score\" varchar(500)");
        assertThat(createSql).contains("\"is_key_node\" varchar(500)");
        assertThat(createSql).doesNotContain("double precision");
        assertThat(createSql).doesNotContain("\"plan_date\" date");
        assertThat(createSql).doesNotContain("\"is_key_node\" boolean");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldAddTruncatePreSqlForRdbmsFullRefresh() throws Exception {
        // Given
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:dm://10.0.0.1:5236/ERPDEMO",
                "table", java.util.List.of("ERPDEMO.CUSTOMER")
            )
        );
        Map<String, Object> writerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://10.0.0.2:5432/biadmin",
                "table", java.util.List.of("ods_customer")
            )
        );

        // When
        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "rdbms-full-refresh-test",
            "rdbmsreader",
            readerConfig,
            "rdbmswriter",
            writerConfig,
            null,
            "full_refresh"
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");

        assertThat(preSql).contains("TRUNCATE TABLE ods_customer");
    }

    @Test
    void shouldThrowExceptionWhenTaskIsNull() {
        // When & Then
        assertThatThrownBy(() -> addaxJobService.createJobFromTask(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IngestionTask cannot be null");
    }

    @Test
    @SuppressWarnings("unchecked")
    void managedDestinationShouldResolveRuntimeCredentialsAndOverrideHistoricalJobSecrets() throws Exception {
        java.util.UUID targetId = java.util.UUID.randomUUID();
        IngestionTask task = new IngestionTask();
        task.setName("managed-target");
        task.setSourceType("mysqlreader");
        task.setDestinationType("postgresqlwriter");
        task.setSyncMode("incremental");
        task.setSourceConfig(objectMapper.createObjectNode().set("table", objectMapper.valueToTree(java.util.List.of("orders"))));
        ObjectNode destination = objectMapper.createObjectNode();
        destination.put("targetDataSourceId", targetId.toString());
        destination.put("password", "historical-task-secret");
        destination.set("table", objectMapper.valueToTree(java.util.List.of("ods_orders")));
        task.setDestinationConfig(destination);
        task.setAddaxConfig(objectMapper.readTree("""
            {
              "job": {
                "content": [{
                  "reader": {"name":"mysqlreader","parameter":{"password":"historical-reader-secret"}},
                  "writer": {"name":"postgresqlwriter","parameter":{"password":"historical-writer-secret"}}
                }]
              }
            }
            """));
        when(sourceResolver.resolveJdbcInfo(targetId)).thenReturn(new JdbcMetadataService.JdbcConnectionInfo(
            "jdbc:postgresql://managed-db:5432/lake",
            "managed_user",
            "managed_password",
            "org.postgresql.Driver",
            "42.7.5",
            Map.of("sslmode", "disable")
        ));
        Map<String, Object> runtimeReader = new java.util.LinkedHashMap<>();
        runtimeReader.put("jdbcUrl", "jdbc:mysql://source-db:3306/orders");
        runtimeReader.put("username", "source_user");
        runtimeReader.put("password", "source_runtime_password");
        runtimeReader.put("table", java.util.List.of("orders"));

        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(
            task,
            "mysqlreader",
            runtimeReader
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        Map<String, Object> writerConnection = (Map<String, Object>) ((java.util.List<?>) writerParams.get("connection")).get(0);
        assertThat(writerParams)
            .containsEntry("username", "managed_user")
            .containsEntry("password", "managed_password");
        assertThat(writerConnection)
            .containsEntry("jdbcUrl", "jdbc:postgresql://managed-db:5432/lake?sslmode=disable");
        assertThat(result.jobConfig().toString())
            .doesNotContain("historical-task-secret", "historical-reader-secret", "historical-writer-secret");
    }

    @Test
    void shouldThrowExceptionWhenReaderWriterTypesMissing() {
        // When & Then
        assertThatThrownBy(() -> addaxJobService.createJob(
            "test",
            null, // missing reader type
            Map.of(),
            "postgresqlwriter",
            Map.of(),
            null
        ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("缺少 Addax Reader/Writer 类型");
    }

    @Test
    void deleteJobIfExists_deletesOnlyRegularFileDirectlyUnderManagedRoot() throws Exception {
        Path job = Files.createFile(tempDir.resolve("task-41.json"));

        assertThat(addaxJobService.deleteJobIfExists(job.toString())).isTrue();
        assertThat(Files.exists(job)).isFalse();
        assertThat(addaxJobService.deleteJobIfExists(job.toString())).isFalse();
    }

    @Test
    void deleteJobIfExists_rejectsPathOutsideManagedRoot() throws Exception {
        Path nested = Files.createDirectories(tempDir.resolve("unmanaged"));
        Path job = Files.createFile(nested.resolve("task-41.json"));

        assertThatThrownBy(() -> addaxJobService.deleteJobIfExists(job.toString()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADDAX_JOB_PATH_OUTSIDE_MANAGED_ROOT");
        assertThat(Files.exists(job)).isTrue();
    }

    @Test
    void legacyPlaintextMigrationShouldSealInPlaceWithoutChangingAgain() throws Exception {
        Path legacy = tempDir.resolve("task_71_1234abcd.json");
        Files.writeString(legacy, """
            {"job":{"content":[{"reader":{"parameter":{"password":"legacy-secret"}}}],"setting":{}}}
            """);

        addaxJobService.migrateLegacyPlaintextJobFiles();
        String first = Files.readString(legacy);
        addaxJobService.migrateLegacyPlaintextJobFiles();

        assertThat(first).startsWith(AddaxJobService.SEALED_JOB_PREFIX).doesNotContain("legacy-secret");
        assertThat(Files.readString(legacy)).isEqualTo(first);
        assertThat(addaxJobService.readManagedJob(legacy).toString()).contains("legacy-secret");
    }

    @Test
    void splitJobsShouldRemainSealedAndOwnerGroupReadableOnly() throws Exception {
        String base = addaxJobService.saveJobJson("""
            {"job":{"setting":{},"content":[
              {"reader":{"parameter":{"password":"reader-secret"}},"writer":{"parameter":{"table":["orders"]}}},
              {"reader":{"parameter":{"password":"reader-secret"}},"writer":{"parameter":{"table":["customers"]}}}
            ]}}
            """, 72L);

        java.util.List<AddaxJobService.PerTableJob> split = addaxJobService.splitJobIntoPerTableFiles(base);

        assertThat(split).hasSize(2);
        for (AddaxJobService.PerTableJob job : split) {
            Path path = Path.of(job.hostJobPath());
            assertThat(Files.readString(path))
                .startsWith(AddaxJobService.SEALED_JOB_PREFIX)
                .doesNotContain("reader-secret");
            assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(path))).isEqualTo("rw-r-----");
        }
    }
}
