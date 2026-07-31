package com.yuzhi.dts.ingestion.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.ingestion.domain.IngestionTask;
import com.yuzhi.dts.ingestion.domain.IngestionTaskRevision;
import com.yuzhi.dts.ingestion.repository.IngestionTaskRepository;
import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import com.yuzhi.dts.ingestion.service.etl.CsvParseService;
import com.yuzhi.dts.ingestion.service.etl.ExcelParseService;
import com.yuzhi.dts.ingestion.service.etl.StagingTableService;
import com.yuzhi.dts.ingestion.service.etl.FileUploadService;
import com.yuzhi.dts.ingestion.service.infra.PlatformInfraClient;
import com.yuzhi.dts.ingestion.service.IngestionAccessContractService;
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
    private PlatformInfraClient platformInfraClient;

    @MockBean
    private FileUploadService fileUploadService;

    @MockBean
    private IngestionAccessContractService accessContractService;

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

        mockMvc.perform(post("/api/ingestion/tasks/44/parse"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRows").value(1))
            .andExpect(jsonPath("$.stagingTableName").value("tmp_ingestion_44"));

        verify(fileUploadService).readPlainBytes(csv);
        verify(csvParseService).parse(any(InputStream.class));
        verify(excelParseService, never()).parse(any(InputStream.class));
    }

    @Test
    void parseActiveEditShouldUseLatestDraftFileAndPersistOnlyDraftRevision() throws Exception {
        Path activeFile = tempDir.resolve("active.csv");
        Path draftFile = tempDir.resolve("draft.csv");
        Files.writeString(activeFile, "project,cost\nold,1\n");
        Files.writeString(draftFile, "project,cost\nnew,2\n");

        IngestionTask active = new IngestionTask();
        active.setId(46L);
        active.setStatus("active");
        active.setSourceType("csv");
        active.setSourceConfig(objectMapper.valueToTree(Map.of("_filePath", activeFile.toString(), "_fileType", "csv")));
        IngestionTask draft = new IngestionTask();
        draft.setId(46L);
        draft.setStatus("draft");
        draft.setSourceType("csv");
        draft.setSourceConfig(objectMapper.valueToTree(Map.of("_filePath", draftFile.toString(), "_fileType", "csv")));
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(600L);
        revision.setRevisionNumber(2);
        revision.setState("DRAFT");

        List<ColumnInfo> columns = List.of(new ColumnInfo("project", "STRING", 100), new ColumnInfo("cost", "LONG", 100));
        List<List<String>> rows = List.of(List.of("new", "2"));
        when(taskRepository.findById(46L)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(46L)).thenReturn(Optional.of(revision));
        when(accessContractService.materializeLatestDraft(active)).thenReturn(draft);
        when(fileUploadService.readPlainBytes(draftFile)).thenReturn(Files.readAllBytes(draftFile));
        when(csvParseService.parse(any(InputStream.class))).thenReturn(new ParseResult(1, columns, List.of(), List.of(), rows));
        when(stagingTableService.create(any(UUID.class), eq(46L), eq(columns))).thenReturn("tmp_ingestion_46");

        mockMvc.perform(post("/api/ingestion/tasks/46/parse"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stagingTableName").value("tmp_ingestion_46"));

        verify(fileUploadService).readPlainBytes(draftFile);
        verify(fileUploadService, never()).readPlainBytes(activeFile);
        verify(accessContractService).recordDraftRevision(draft, null, true);
        verify(taskRepository, never()).save(any(IngestionTask.class));
        org.assertj.core.api.Assertions.assertThat(active.getSourceConfig().path("_filePath").asText())
            .isEqualTo(activeFile.toString());
        org.assertj.core.api.Assertions.assertThat(draft.getPreCheckStatus()).isEqualTo("PENDING");
    }

    @Test
    void editingAStagingCellMustInvalidatePassedDraftPreCheck() throws Exception {
        IngestionTask active = new IngestionTask();
        active.setId(47L);
        active.setStatus("active");
        IngestionTask draft = new IngestionTask();
        draft.setId(47L);
        draft.setStatus("draft");
        draft.setStagingTableName("tmp_ingestion_47");
        draft.setPreCheckStatus("PASSED");
        IngestionTaskRevision revision = new IngestionTaskRevision();
        revision.setId(601L);
        revision.setRevisionNumber(2);
        revision.setState("DRAFT");

        when(taskRepository.findById(47L)).thenReturn(Optional.of(active));
        when(accessContractService.findLatestDraftRevision(47L)).thenReturn(Optional.of(revision));
        when(accessContractService.materializeLatestDraft(active)).thenReturn(draft);

        mockMvc.perform(put("/api/ingestion/tasks/47/staging/9")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"column\":\"amount\",\"value\":\"128\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rowNum").value(9));

        verify(stagingTableService).updateCell("tmp_ingestion_47", 9, "amount", "128");
        verify(accessContractService).recordDraftRevision(draft, null, true);
        verify(taskRepository, never()).save(any(IngestionTask.class));
        org.assertj.core.api.Assertions.assertThat(draft.getPreCheckStatus()).isEqualTo("PENDING");
    }

    @Test
    void preCheckMustNotTreatConnectionIdAsDatasetId() throws Exception {
        IngestionTask task = new IngestionTask();
        task.setId(45L);
        task.setName("file-quality-contract");
        task.setStatus("draft");
        task.setStagingTableName("tmp_ingestion_45");
        task.setSourceDataSourceId(UUID.fromString("00000000-0000-0000-0000-000000000045"));
        task.setSourceConfig(objectMapper.createObjectNode());
        when(taskRepository.findById(45L)).thenReturn(Optional.of(task));
        when(accessContractService.findQualityDatasetId(45L, "draft")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/ingestion/tasks/45/pre-check"))
            .andExpect(status().isBadRequest());

        verify(platformInfraClient, never()).preCheckStagingData(any(), any(), any(Integer.class));
    }

    @Test
    void preCheckMustReportRuleCountsSeparatelyFromRowCounts() throws Exception {
        UUID datasetId = UUID.fromString("00000000-0000-0000-0000-000000000048");
        IngestionTask task = new IngestionTask();
        task.setId(48L);
        task.setStatus("draft");
        task.setStagingTableName("tmp_ingestion_48");
        when(taskRepository.findById(48L)).thenReturn(Optional.of(task));
        when(accessContractService.findQualityDatasetId(48L, "draft")).thenReturn(Optional.of(datasetId));
        when(stagingTableService.countRows("tmp_ingestion_48")).thenReturn(100);
        when(platformInfraClient.preCheckStagingData("tmp_ingestion_48", datasetId, 100)).thenReturn(
            Map.of(
                "totalRows", 100,
                "passedRows", 91,
                "failedRows", 9,
                "totalRules", 3,
                "passedRules", 2,
                "failedRules", 1,
                "errorsByRule", List.of(
                    Map.of("ruleName", "主键非空", "failCount", 0),
                    Map.of("ruleName", "金额非负", "failCount", 9)
                )
            )
        );

        mockMvc.perform(post("/api/ingestion/tasks/48/pre-check"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.totalRules").value(3))
            .andExpect(jsonPath("$.passedRules").value(2))
            .andExpect(jsonPath("$.failedRules").value(1))
            .andExpect(jsonPath("$.totalRows").value(100))
            .andExpect(jsonPath("$.passedRows").value(91))
            .andExpect(jsonPath("$.failedRows").value(9))
            .andExpect(jsonPath("$.failedRuleNames[0]").value("金额非负"));
    }

    @Test
    void legacySubmitEndpointMustBeGoneAndMustNotWriteOds() throws Exception {
        mockMvc.perform(post("/api/ingestion/tasks/49/submit"))
            .andExpect(status().isGone());

        verify(taskRepository, never()).findById(49L);
        verify(stagingTableService, never()).transferToTarget(any(), any());
    }
}
