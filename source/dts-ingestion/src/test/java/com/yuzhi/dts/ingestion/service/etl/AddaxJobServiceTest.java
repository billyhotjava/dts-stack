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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.argThat;
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
        addaxJobService = newAddaxJobServiceWithKey(mockEnv, "0123456789abcdef0123456789abcdef");

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

    private AddaxJobService newAddaxJobServiceWithKey(
        org.springframework.mock.env.MockEnvironment mockEnv,
        String keyMaterial
    ) {
        InfraSecurityProperties securityProperties = new InfraSecurityProperties();
        securityProperties.setEncryptionKey(Base64.getEncoder().encodeToString(
            keyMaterial.getBytes(StandardCharsets.UTF_8)
        ));
        securityProperties.setKeyVersion("v1");
        InfraSettingsCryptoService cryptoService = new InfraSettingsCryptoService(securityProperties);
        cryptoService.init();
        AddaxJobService service = new AddaxJobService(
            addaxProperties,
            settingsService,
            objectMapper,
            jdbcMetadataService,
            new AddaxJdbcConfigNormalizer(objectMapper),
            mockEnv,
            cryptoService
        );
        service.setSourceResolver(sourceResolver);
        return service;
    }

    @Test
    void boundModelFullRefreshProducesInsertJobWithoutDestructiveSql() throws Exception {
        Map<String, Object> reader = Map.of("jdbcUrl", "jdbc:mysql://localhost:3306/source", "username", "test", "password", "test", "table", "orders", "column", java.util.List.of("id"));
        Map<String, Object> writer = Map.of("jdbcUrl", "jdbc:postgresql://localhost:5432/warehouse", "username", "test", "password", "test", "table", "ods.orders", "column", java.util.List.of("id"), "modelTarget", Map.of("schemaVersion", 1));
        var result = addaxJobService.createJob("bound-model", "mysqlreader", reader, "postgresqlwriter", writer, null, "full_refresh");
        String json = objectMapper.writeValueAsString(result.jobConfig());
        assertThat(json).contains("ods.orders").doesNotContain("TRUNCATE", "DROP TABLE", "CREATE TABLE", "ALTER TABLE", "modelTarget");
    }

    @Test
    void boundModelRejectsCustomWriterSql() {
        Map<String, Object> writer = Map.of("modelTarget", Map.of("schemaVersion", 1), "preSql", java.util.List.of("truncate table ods.orders"));
        assertThatThrownBy(() -> addaxJobService.createJob("bound-model", "mysqlreader", Map.of(), "postgresqlwriter", writer, null, "full_refresh"))
            .hasMessageContaining("MODEL_INGESTION_CUSTOM_SQL_FORBIDDEN");
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
    @SuppressWarnings("unchecked")
    void shouldApplyPrefixWhenReplacingWriterTablePlaceholder() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:mysql://mysql:3306/source",
                "table", java.util.List.of("city", "department")
            )
        );
        Map<String, Object> writerConfig = Map.of(
            "tablePrefix", "ods_",
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://pg:5432/biadmin",
                "table", java.util.List.of("${table}")
            )
        );

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "prefixed-placeholder-test",
            "mysqlreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        java.util.List<Map<String, Object>> contentList = (java.util.List<Map<String, Object>>) job.get("content");
        assertThat(contentList).hasSize(2);
        Map<String, Object> firstWriter = (Map<String, Object>) contentList.get(0).get("writer");
        Map<String, Object> firstParams = (Map<String, Object>) firstWriter.get("parameter");
        Map<String, Object> firstConn = (Map<String, Object>) ((java.util.List<?>) firstParams.get("connection")).get(0);
        assertThat(firstConn.get("table")).isEqualTo(java.util.List.of("ods_city"));
        Map<String, Object> secondWriter = (Map<String, Object>) contentList.get(1).get("writer");
        Map<String, Object> secondParams = (Map<String, Object>) secondWriter.get("parameter");
        Map<String, Object> secondConn = (Map<String, Object>) ((java.util.List<?>) secondParams.get("connection")).get(0);
        assertThat(secondConn.get("table")).isEqualTo(java.util.List.of("ods_department"));
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
        assertThat(firstPreSql).anyMatch(sql -> sql.contains("\"_dts_source_table\"") && sql.contains("DEFAULT 'CUSTOMER'"));

        Map<String, Object> secondWriter = (Map<String, Object>) contentList.get(1).get("writer");
        Map<String, Object> secondParams = (Map<String, Object>) secondWriter.get("parameter");
        java.util.List<String> secondPreSql = (java.util.List<String>) secondParams.get("preSql");
        java.util.List<String> secondPostSql = (java.util.List<String>) secondParams.get("postSql");
        assertThat(secondPreSql).contains("TRUNCATE TABLE ods_employee");
        assertThat(secondPreSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_customer"));
        assertThat(secondPostSql).allMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_employee"));
        assertThat(secondPostSql).noneMatch(sql -> sql.toLowerCase(java.util.Locale.ROOT).contains("ods_customer"));
        assertThat(secondPreSql).anyMatch(sql -> sql.contains("\"_dts_source_table\"") && sql.contains("DEFAULT 'EMPLOYEE'"));
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
        java.util.List<String> preSql = (java.util.List<String>) writerParams.getOrDefault("preSql", java.util.List.of());

        assertThat(preSql)
            .anyMatch(sql -> sql.contains("_dts_source_system") && sql.contains("DEFAULT '专利测试数据_三年1000条.xlsx'"));
        assertThat(preSql)
            .anyMatch(sql -> sql.contains("_dts_source_table") && sql.contains("DEFAULT '专利测试数据_三年1000条.xlsx'"));
        assertThat(preSql)
            .noneMatch(sql -> sql.contains("_dts_source_system\" = 'unknown'"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldApplyDtsRuntimeColumnsAsInsertDefaultsWithoutRewritingHistory() throws Exception {
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
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");

        assertThat(preSql).anyMatch(sql -> sql.contains("_dts_batch_id") && sql.contains("DEFAULT 'batch-task-1-abc'"));
        assertThat(preSql).anyMatch(sql -> sql.contains("_dts_execution_id") && sql.contains("DEFAULT '101'"));
        assertThat(preSql).anyMatch(sql -> sql.contains("_dts_task_id") && sql.contains("DEFAULT '1'"));
        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("TRUNCATE"));
        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("DROP TABLE"));
        assertThat(postSql).anyMatch(sql -> sql.contains("DELETE FROM ods_customer") && sql.contains("<> '101'"));
        assertThat(postSql).noneMatch(sql -> sql.contains("WHERE TRUE"));
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
        assertThat(postSql).anyMatch(sql -> sql.contains("row_number() OVER (ORDER BY ctid) + 1"));
        assertThat(postSql).noneMatch(sql -> sql.contains("WHERE TRUE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldKeepManagedFileLandingContractOutOfAddaxReaderAndDestructiveSql() throws Exception {
        Map<String, Object> readerConfig = Map.of(
            "_fileId", "file-landing-001",
            "_fileColumns", java.util.List.of(Map.of("safeName", "order_no", "type", "string")),
            "_fileLanding", Map.of(
                "version", 1,
                "landingMode", "recreate_existing",
                "referenceTable", "public.ods_orders",
                "targetTable", "public.ods_orders",
                "recreateConfirmed", true
            ),
            "path", java.util.List.of("/opt/airflow/dags/exchange/excel/orders.xlsx")
        );
        Map<String, Object> writerConfig = Map.of(
            "jdbcUrl", "jdbc:postgresql://127.0.0.1:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", "public.ods_orders"
        );

        AddaxJobService.AddaxJobResult result = addaxJobService.createJob(
            "managed-file-landing-contract",
            "excelreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null,
            "full_refresh",
            null,
            Map.of("executionId", "902", "batchId", "batch-902", "taskId", "10")
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> readerParams = (Map<String, Object>) reader.get("parameter");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");

        assertThat(readerParams).doesNotContainKeys("_fileLanding", "_fileColumns", "_fileId");
        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("DROP TABLE"));
        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("TRUNCATE TABLE"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void managedFileTaskShouldResolveWriterPlaceholderFromLandingTarget() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setName("managed-file-placeholder");
        task.setSourceType("txtfilereader");
        task.setSyncMode("full_refresh");
        task.setSourceConfig(objectMapper.valueToTree(Map.of(
            "_fileId", "file-landing-002",
            "_fileType", "xlsx",
            "_fileColumns", java.util.List.of(Map.of("safeName", "project_no", "type", "string")),
            "_fileLanding", Map.of(
                "version", 1,
                "landingMode", "create_new",
                "targetTable", "ods_project_subject_domain"
            ),
            "path", java.util.List.of("/decrypted/project-domain.xlsx")
        )));
        task.setDestinationType("postgresqlwriter");
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "tablePrefix", "ods_",
            "connection", java.util.List.of(Map.of(
                "jdbcUrl", java.util.List.of("jdbc:postgresql://dts-pg:5432/biadmin"),
                "table", java.util.List.of("${table}"),
                "tables", java.util.List.of("${table}")
            ))
        )));
        task.setTableMapping(objectMapper.valueToTree(java.util.List.of(Map.of(
            "source", "legacy_sheet",
            "target", "ods_stale_target"
        ))));

        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(
            task,
            null,
            null,
            null,
            Map.of("executionId", "28", "batchId", "batch-28", "taskId", "4")
        );

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        Map<String, Object> writerConnection = (Map<String, Object>) ((java.util.List<?>) writerParams.get("connection")).get(0);

        assertThat(writerConnection.get("table")).isEqualTo(java.util.List.of("ods_project_subject_domain"));
        assertThat(writerParams.toString()).doesNotContain("${table}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonFileTaskShouldIgnoreManagedFileLandingMarker() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setName("database-task-with-stale-file-marker");
        task.setSourceType("mysqlreader");
        task.setSyncMode("incremental");
        task.setSourceConfig(objectMapper.valueToTree(Map.of(
            "table", java.util.List.of("orders"),
            "_fileLanding", Map.of(
                "landingMode", "create_new",
                "targetTable", "ods_hijacked"
            )
        )));
        task.setDestinationType("postgresqlwriter");
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", java.util.List.of("ods_orders")
        )));

        AddaxJobService.AddaxJobResult result = addaxJobService.createJobFromTask(task);

        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        Map<String, Object> writerConnection = (Map<String, Object>) ((java.util.List<?>) writerParams.get("connection")).get(0);

        assertThat(writerConnection.get("table")).isEqualTo(java.util.List.of("ods_orders"));
        assertThat(writerParams.toString()).doesNotContain("ods_hijacked");
    }

    @Test
    void managedFileTaskShouldRejectUnsafeLandingTargetIdentifier() {
        IngestionTask task = new IngestionTask();
        task.setName("managed-file-unsafe-target");
        task.setSourceType("excelreader");
        task.setSyncMode("full_refresh");
        task.setSourceConfig(objectMapper.valueToTree(Map.of(
            "_fileType", "xlsx",
            "_fileColumns", java.util.List.of(Map.of("safeName", "project_no", "type", "string")),
            "_fileLanding", Map.of(
                "landingMode", "create_new",
                "targetTable", "ods_safe; DROP TABLE protected_table"
            ),
            "path", java.util.List.of("/decrypted/project-domain.xlsx")
        )));
        task.setDestinationType("postgresqlwriter");
        task.setDestinationConfig(objectMapper.valueToTree(Map.of(
            "jdbcUrl", "jdbc:postgresql://dts-pg:5432/biadmin",
            "username", "biadmin",
            "password", "fixture-only-password",
            "table", java.util.List.of("${table}")
        )));

        assertThatThrownBy(() -> addaxJobService.createJobFromTask(
            task,
            null,
            null,
            null,
            Map.of("executionId", "28", "batchId", "batch-28", "taskId", "4")
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("文件落地目标表格式不合法");
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
    void shouldPreserveDtsNamedDatabaseSourceColumnsWithDeterministicLandingNames() throws Exception {
        String jobPath = addaxJobService.saveJobJson("""
            {
              "job": {
                "setting": {"speed": {"channel": 1}},
                "content": [{
                  "reader": {
                    "name": "mysqlreader",
                    "parameter": {
                      "jdbcUrl": "jdbc:mysql://source-db:3306/source",
                      "username": "reader",
                      "password": "reader-password",
                      "table": ["ods_orders"],
                      "column": ["*"]
                    }
                  },
                  "writer": {
                    "name": "postgresqlwriter",
                    "parameter": {
                      "jdbcUrl": "jdbc:postgresql://target-db:5432/lake",
                      "username": "writer",
                      "password": "writer-password",
                      "table": ["ods_copy_orders"],
                      "column": ["*"]
                    }
                  }
                }]
              }
            }
            """, 812L);
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("source-db")),
            org.mockito.ArgumentMatchers.eq("ods_orders")
        )).thenReturn(databaseColumns("id", "amount"));
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("target-db")),
            org.mockito.ArgumentMatchers.eq("ods_copy_orders")
        )).thenReturn(databaseColumns(
            "id",
            "amount",
            "_dts_source_system_2",
            "_dts_source_table_2",
            "_dts_import_time_2",
            "_dts_batch_id_2",
            "_dts_execution_id_2",
            "_dts_task_id_2"
        ));

        addaxJobService.resolveWriterColumnsIfNeeded(jobPath);

        Map<String, Object> stored = addaxJobService.readManagedJob(Path.of(jobPath));
        Map<String, Object> job = (Map<String, Object>) stored.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> readerParams = (Map<String, Object>) reader.get("parameter");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        assertThat((java.util.List<String>) readerParams.get("column")).containsExactly(
            "id", "amount", "_dts_source_system", "_dts_source_table", "_dts_import_time",
            "_dts_batch_id", "_dts_execution_id", "_dts_task_id"
        );
        assertThat((java.util.List<String>) writerParams.get("column")).containsExactly(
            "id", "amount", "_dts_source_system_2", "_dts_source_table_2", "_dts_import_time_2",
            "_dts_batch_id_2", "_dts_execution_id_2", "_dts_task_id_2"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldValidateExplicitReaderColumnsAgainstRenamedWriterColumns() throws Exception {
        String jobPath = addaxJobService.saveJobJson("""
            {
              "job": {"content": [{
                "reader": {"name":"mysqlreader","parameter":{
                  "jdbcUrl":"jdbc:mysql://source-db:3306/source","table":["orders"],
                  "column":["id","_dts_source_system"]}},
                "writer": {"name":"postgresqlwriter","parameter":{
                  "jdbcUrl":"jdbc:postgresql://target-db:5432/lake","table":["ods_orders"],"column":["*"]}}
              }]}
            }
            """, 815L);
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("target-db")),
            org.mockito.ArgumentMatchers.eq("ods_orders")
        )).thenReturn(databaseColumns("id", "_dts_source_system_2"));

        addaxJobService.resolveWriterColumnsIfNeeded(jobPath);

        Map<String, Object> stored = addaxJobService.readManagedJob(Path.of(jobPath));
        Map<String, Object> job = (Map<String, Object>) stored.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        assertThat((java.util.List<String>) ((Map<String, Object>) reader.get("parameter")).get("column"))
            .containsExactly("id", "_dts_source_system");
        assertThat((java.util.List<String>) ((Map<String, Object>) writer.get("parameter")).get("column"))
            .containsExactly("id", "_dts_source_system_2");
    }

    @ParameterizedTest
    @ValueSource(strings = { "db2reader", "sqlitereader" })
    @SuppressWarnings("unchecked")
    void shouldNotPersistWriterColumnsWhenDatabaseReaderMetadataCannotBeResolved(String readerName) throws Exception {
        String jobPath = addaxJobService.saveJobJson("""
            {
              "job": {
                "content": [{
                  "reader": {
                    "name": "%s",
                    "parameter": {
                      "jdbcUrl": "jdbc:mysql://source-db:3306/source",
                      "table": ["ods_orders"],
                      "column": ["*"]
                    }
                  },
                  "writer": {
                    "name": "postgresqlwriter",
                    "parameter": {
                      "jdbcUrl": "jdbc:postgresql://target-db:5432/lake",
                      "table": ["ods_copy_orders"],
                      "column": ["*"]
                    }
                  }
                }]
              }
            }
            """.formatted(readerName), 813L);
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("source-db")),
            org.mockito.ArgumentMatchers.eq("ods_orders")
        )).thenReturn(java.util.List.of());
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("target-db")),
            org.mockito.ArgumentMatchers.eq("ods_copy_orders")
        )).thenReturn(databaseColumns("id", "amount"));

        assertThatThrownBy(() -> addaxJobService.resolveWriterColumnsIfNeeded(jobPath))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("拒绝保存不对齐");

        Map<String, Object> stored = addaxJobService.readManagedJob(Path.of(jobPath));
        Map<String, Object> job = (Map<String, Object>) stored.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        assertThat((java.util.List<String>) ((Map<String, Object>) reader.get("parameter")).get("column"))
            .containsExactly("*");
        assertThat((java.util.List<String>) ((Map<String, Object>) writer.get("parameter")).get("column"))
            .containsExactly("*");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldNotPersistDatabaseColumnsWhenReaderAndWriterCountsDiffer() throws Exception {
        String jobPath = addaxJobService.saveJobJson("""
            {
              "job": {
                "content": [{
                  "reader": {
                    "name": "mysqlreader",
                    "parameter": {
                      "jdbcUrl": "jdbc:mysql://source-db:3306/source",
                      "table": ["ods_orders"],
                      "column": ["*"]
                    }
                  },
                  "writer": {
                    "name": "postgresqlwriter",
                    "parameter": {
                      "jdbcUrl": "jdbc:postgresql://target-db:5432/lake",
                      "table": ["ods_copy_orders"],
                      "column": ["*"]
                    }
                  }
                }]
              }
            }
            """, 814L);
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("source-db")),
            org.mockito.ArgumentMatchers.eq("ods_orders")
        )).thenReturn(databaseColumns("id", "amount"));
        when(jdbcMetadataService.getTableColumns(
            argThat(info -> info != null && info.jdbcUrl().contains("target-db")),
            org.mockito.ArgumentMatchers.eq("ods_copy_orders")
        )).thenReturn(databaseColumns("id", "amount", "status"));

        assertThatThrownBy(() -> addaxJobService.resolveWriterColumnsIfNeeded(jobPath))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("数量不一致");

        Map<String, Object> stored = addaxJobService.readManagedJob(Path.of(jobPath));
        Map<String, Object> job = (Map<String, Object>) stored.get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> reader = (Map<String, Object>) content.get("reader");
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        assertThat((java.util.List<String>) ((Map<String, Object>) reader.get("parameter")).get("column"))
            .containsExactly("*");
        assertThat((java.util.List<String>) ((Map<String, Object>) writer.get("parameter")).get("column"))
            .containsExactly("*");
    }

    private java.util.List<JdbcMetadataService.ColumnMeta> databaseColumns(String... businessColumns) {
        java.util.List<JdbcMetadataService.ColumnMeta> columns = new java.util.ArrayList<>();
        for (String businessColumn : businessColumns) {
            columns.add(new JdbcMetadataService.ColumnMeta(businessColumn, java.sql.Types.VARCHAR, "VARCHAR", 200, null));
        }
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_source_system", java.sql.Types.VARCHAR, "VARCHAR", 200, null));
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_source_table", java.sql.Types.VARCHAR, "VARCHAR", 300, null));
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_import_time", java.sql.Types.TIMESTAMP, "TIMESTAMP", null, null));
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_batch_id", java.sql.Types.VARCHAR, "VARCHAR", 128, null));
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_execution_id", java.sql.Types.VARCHAR, "VARCHAR", 128, null));
        columns.add(new JdbcMetadataService.ColumnMeta("_dts_task_id", java.sql.Types.VARCHAR, "VARCHAR", 64, null));
        return columns;
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldProvisionFileSourceWithoutDroppingPreviousSuccessfulTable() throws Exception {
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
            "full_refresh",
            null,
            Map.of("executionId", "901", "batchId", "batch-901", "taskId", "9")
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.get("preSql");

        assertThat(preSql).isNotNull();
        assertThat(preSql).anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS"));
        assertThat(preSql).anyMatch(sql -> sql.startsWith("ALTER TABLE"));
        assertThat(preSql).noneMatch(sql -> sql.startsWith("DROP TABLE IF EXISTS"));
        assertThat(preSql).noneMatch(sql -> sql.startsWith("TRUNCATE TABLE"));
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");
        assertThat(postSql).anyMatch(sql -> sql.contains("DELETE FROM ods_patent_info") && sql.contains("<> '901'"));
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
    void shouldDeletePreviousRdbmsBatchOnlyAfterSuccessfulFullRefreshWrite() throws Exception {
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
            "full_refresh",
            null,
            Map.of("executionId", "902", "batchId", "batch-902", "taskId", "10")
        );

        // Then
        Map<String, Object> job = (Map<String, Object>) result.jobConfig().get("job");
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        java.util.List<String> preSql = (java.util.List<String>) writerParams.getOrDefault("preSql", java.util.List.of());

        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("TRUNCATE TABLE"));
        assertThat(preSql).noneMatch(sql -> sql.toUpperCase(java.util.Locale.ROOT).contains("DROP TABLE"));
        assertThat(preSql).anyMatch(sql -> sql.contains("_dts_execution_id") && sql.contains("DEFAULT '902'"));
        java.util.List<String> postSql = (java.util.List<String>) writerParams.get("postSql");
        assertThat(postSql).anyMatch(sql -> sql.contains("DELETE FROM ods_customer") && sql.contains("<> '902'"));
    }

    @Test
    void shouldRejectFullRefreshWithoutCurrentExecutionId() {
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:mysql://10.0.0.1:3306/source",
                "table", java.util.List.of("customer")
            )
        );
        Map<String, Object> writerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://10.0.0.2:5432/biadmin",
                "table", java.util.List.of("ods_customer")
            )
        );

        assertThatThrownBy(() -> addaxJobService.createJob(
            "rdbms-full-refresh-without-execution-test",
            "rdbmsreader",
            readerConfig,
            "rdbmswriter",
            writerConfig,
            null,
            "full_refresh",
            null,
            Map.of()
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("full_refresh 缺少当前执行 ID");
    }

    @Test
    void shouldRejectMismatchedDatabaseTableCountsBeforeGeneratingJob() {
        Map<String, Object> readerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:mysql://source-db:3306/source",
                "table", java.util.List.of("orders", "customers")
            )
        );
        Map<String, Object> writerConfig = Map.of(
            "connection", Map.of(
                "jdbcUrl", "jdbc:postgresql://target-db:5432/lake",
                "table", java.util.List.of("ods_orders")
            )
        );

        assertThatThrownBy(() -> addaxJobService.createJob(
            "mismatched-tables",
            "mysqlreader",
            readerConfig,
            "postgresqlwriter",
            writerConfig,
            null
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Reader/Writer 表数量不一致");
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
    void startupMigrationShouldNotDecryptAlreadySealedJobsFromRetiredKey() throws Exception {
        org.springframework.mock.env.MockEnvironment mockEnv = new org.springframework.mock.env.MockEnvironment()
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test")
            .withProperty("spring.datasource.username", "test")
            .withProperty("spring.datasource.password", "test");
        AddaxJobService retiredKeyService = newAddaxJobServiceWithKey(
            mockEnv,
            "abcdef0123456789abcdef0123456789"
        );
        Path sealedJob = Path.of(retiredKeyService.saveJobJson(
            """
            {"job":{"content":[{"reader":{"parameter":{"password":"retired-secret"}}}],"setting":{}}}
            """,
            72L
        ));
        String sealedContent = Files.readString(sealedJob);
        Files.setPosixFilePermissions(sealedJob, PosixFilePermissions.fromString("rw-rw-rw-"));

        assertThatCode(addaxJobService::migrateLegacyPlaintextJobFiles).doesNotThrowAnyException();

        assertThat(Files.readString(sealedJob)).isEqualTo(sealedContent).doesNotContain("retired-secret");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(sealedJob))).isEqualTo("rw-r-----");
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
