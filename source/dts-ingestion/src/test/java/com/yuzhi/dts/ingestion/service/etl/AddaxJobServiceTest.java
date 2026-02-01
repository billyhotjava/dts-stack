package com.yuzhi.dts.ingestion.service.etl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuzhi.dts.ingestion.config.AddaxProperties;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.service.infra.IngestionSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
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
    private IngestionSettingsService.SettingsSnapshot settingsSnapshot;

    @Mock
    private JdbcMetadataService jdbcMetadataService;

    private ObjectMapper objectMapper;
    private AddaxJobService addaxJobService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        objectMapper = new ObjectMapper();
        addaxJobService = new AddaxJobService(addaxProperties, settingsService, objectMapper, jdbcMetadataService);

        // Mock settings service
        when(settingsService.getSettings(anyString())).thenReturn(settingsSnapshot);
        when(settingsSnapshot.getString(anyString(), anyString())).thenReturn(tempDir.toString());
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

        // Verify JSON structure
        String jsonContent = Files.readString(jobPath);
        Map<String, Object> jobConfig = objectMapper.readValue(jsonContent, Map.class);
        assertThat(jobConfig).containsKey("job");

        Map<String, Object> job = (Map<String, Object>) jobConfig.get("job");
        assertThat(job).containsKeys("content", "setting");
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
        Map<String, Object> content = (Map<String, Object>) ((java.util.List<?>) job.get("content")).get(0);
        Map<String, Object> writer = (Map<String, Object>) content.get("writer");
        Map<String, Object> writerParams = (Map<String, Object>) writer.get("parameter");
        Map<String, Object> writerConn = (Map<String, Object>) ((java.util.List<?>) writerParams.get("connection")).get(0);

        assertThat(writerConn.get("table")).isEqualTo(java.util.List.of("city", "department"));
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
        assertThat(savedContent).isEqualTo(jobJson);
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
    void shouldThrowExceptionWhenTaskIsNull() {
        // When & Then
        assertThatThrownBy(() -> addaxJobService.createJobFromTask(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("IngestionTask cannot be null");
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
}
