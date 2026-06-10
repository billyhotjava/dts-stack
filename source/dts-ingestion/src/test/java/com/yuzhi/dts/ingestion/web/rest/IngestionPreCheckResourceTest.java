package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import com.yuzhi.dts.ingestion.service.etl.BuiltInRuleChecker;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.StagingTableService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IngestionPreCheckResource.class)
@AutoConfigureMockMvc(addFilters = false)
class IngestionPreCheckResourceTest {

    private static final String STAGING_TABLE = "tmp_ingestion_1234567890abcdef1234567890abcdef";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IngestionTaskRepository taskRepository;

    @MockBean
    private ExcelParseService excelParseService;

    @MockBean
    private CsvParseService csvParseService;

    @MockBean
    private StagingTableService stagingTableService;

    @MockBean
    private BuiltInRuleChecker builtInRuleChecker;

    @MockBean
    private PlatformInfraClient platformInfraClient;

    @MockBean
    private FileUploadService fileUploadService;

    @TempDir
    Path tempDir;

    @Test
    void parseShouldRouteCsvToCsvParserAndRecreateStagingTable() throws Exception {
        Path csv = tempDir.resolve("project.csv");
        Files.writeString(csv, "project,cost\nalpha,88\n");

        IngestionTask task = new IngestionTask();
        task.setId(42L);
        task.setName("csv-projects");
        task.setSourceType("csv");
        task.setSourceConfig(objectMapper.valueToTree(Map.of("_filePath", csv.toString(), "_fileType", "csv")));
        task.setStagingTableName("tmp_ingestion_old");

        List<ColumnInfo> columns = List.of(new ColumnInfo("project", "STRING", 100), new ColumnInfo("cost", "LONG", 100));
        List<List<String>> rows = List.of(List.of("alpha", "88"));
        when(taskRepository.findById(42L)).thenReturn(Optional.of(task));
        when(fileUploadService.readPlainBytes(csv)).thenReturn(Files.readAllBytes(csv));
        when(csvParseService.parse(any(InputStream.class))).thenReturn(new ParseResult(1, columns, List.of(), List.of(), rows));
        when(stagingTableService.create(any(UUID.class), eq(42L), eq(columns))).thenReturn(STAGING_TABLE);
        when(builtInRuleChecker.check(STAGING_TABLE, columns)).thenReturn(Map.of());

        mockMvc.perform(post("/api/ingestion/tasks/42/parse"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1))
            .andExpect(jsonPath("$.stagingTableName").value(STAGING_TABLE))
            .andExpect(jsonPath("$.builtInErrorCount").value(0));

        verify(csvParseService).parse(any(InputStream.class));
        verify(excelParseService, never()).parse(any(InputStream.class));
        verify(stagingTableService).drop("tmp_ingestion_old");
        verify(stagingTableService).create(any(UUID.class), eq(42L), eq(columns));
        verify(stagingTableService).bulkInsert(STAGING_TABLE, columns, rows);
        verify(taskRepository).save(task);
    }

    @Test
    void parseShouldUseHostPathFromSourceConfig() throws Exception {
        Path csv = tempDir.resolve("project-hostpath.csv");
        Files.writeString(csv, "project,cost\nbeta,128\n");

        IngestionTask task = new IngestionTask();
        task.setId(43L);
        task.setName("csv-hostpath");
        task.setSourceType("csv");
        task.setSourceConfig(objectMapper.valueToTree(Map.of("hostPath", csv.toString(), "_fileType", "csv")));
        task.setStagingTableName("tmp_ingestion_old");

        List<ColumnInfo> columns = List.of(new ColumnInfo("project", "STRING", 100), new ColumnInfo("cost", "LONG", 100));
        List<List<String>> rows = List.of(List.of("beta", "128"));
        when(taskRepository.findById(43L)).thenReturn(Optional.of(task));
        when(fileUploadService.readPlainBytes(csv)).thenReturn(Files.readAllBytes(csv));
        when(csvParseService.parse(any(InputStream.class))).thenReturn(new ParseResult(1, columns, List.of(), List.of(), rows));
        when(stagingTableService.create(any(UUID.class), eq(43L), eq(columns))).thenReturn("tmp_ingestion_43");
        when(builtInRuleChecker.check("tmp_ingestion_43", columns)).thenReturn(Map.of());

        mockMvc.perform(post("/api/ingestion/tasks/43/parse"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1))
            .andExpect(jsonPath("$.stagingTableName").value("tmp_ingestion_43"));

        verify(fileUploadService).readPlainBytes(csv);
        verify(csvParseService).parse(any(InputStream.class));
        verify(stagingTableService).create(any(UUID.class), eq(43L), eq(columns));
    }

    @Test
    void parseShouldTreatCsvEncByName() throws Exception {
        Path csv = tempDir.resolve("project-enc.csv.enc");
        Files.writeString(csv, "project,cost\nenc,256\n");

        IngestionTask task = new IngestionTask();
        task.setId(44L);
        task.setName("csv-enc");
        task.setSourceType("excel");
        task.setSourceConfig(objectMapper.valueToTree(Map.of("hostPath", csv.toString())));
        task.setStagingTableName("tmp_ingestion_old");

        List<ColumnInfo> columns = List.of(new ColumnInfo("project", "STRING", 100), new ColumnInfo("cost", "LONG", 100));
        List<List<String>> rows = List.of(List.of("enc", "256"));
        when(taskRepository.findById(44L)).thenReturn(Optional.of(task));
        when(fileUploadService.readPlainBytes(csv)).thenReturn(Files.readAllBytes(csv));
        when(csvParseService.parse(any(InputStream.class))).thenReturn(new ParseResult(1, columns, List.of(), List.of(), rows));
        when(stagingTableService.create(any(UUID.class), eq(44L), eq(columns))).thenReturn("tmp_ingestion_44");
        when(builtInRuleChecker.check("tmp_ingestion_44", columns)).thenReturn(Map.of());

        mockMvc.perform(post("/api/ingestion/tasks/44/parse"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1))
            .andExpect(jsonPath("$.stagingTableName").value("tmp_ingestion_44"));

        verify(fileUploadService).readPlainBytes(csv);
        verify(csvParseService).parse(any(InputStream.class));
        verify(excelParseService, never()).parse(any(InputStream.class));
    }
}
