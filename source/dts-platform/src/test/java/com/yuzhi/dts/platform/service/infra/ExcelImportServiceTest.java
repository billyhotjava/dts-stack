package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitBatch;
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitIssue;
import com.yuzhi.dts.platform.domain.infra.InfraProjectCockpitRow;
import com.yuzhi.dts.platform.repository.infra.InfraExternalExchangeFileRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitBatchRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitIssueRepository;
import com.yuzhi.dts.platform.repository.infra.InfraProjectCockpitRowRepository;
import com.yuzhi.dts.platform.service.audit.AuditService;
import com.yuzhi.dts.platform.service.infra.dto.ExcelImportParseRequest;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock
    private InfraExternalExchangeFileRepository repository;

    @Mock
    private InfraProjectCockpitBatchRepository projectCockpitBatchRepository;

    @Mock
    private InfraProjectCockpitRowRepository projectCockpitRowRepository;

    @Mock
    private InfraProjectCockpitIssueRepository projectCockpitIssueRepository;

    @Mock
    private AuditService auditService;

    private ExcelImportService excelImportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        AirflowProperties airflowProperties = new AirflowProperties();
        airflowProperties.setDagsDir(tempDir.toString());
        excelImportService = new ExcelImportService(
            airflowProperties,
            repository,
            projectCockpitBatchRepository,
            projectCockpitRowRepository,
            projectCockpitIssueRepository,
            auditService,
            new ObjectMapper()
        );
    }

    @Test
    void parseShouldWriteEvaluatedFormulaValuesIntoCsv() throws Exception {
        UUID fileId = UUID.randomUUID();
        Path source = tempDir.resolve("exchange/excel/formula/source.xlsx");
        Files.createDirectories(source.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(source)) {
            var sheet = workbook.createSheet("sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("col_a");
            header.createCell(1).setCellValue("col_b");
            header.createCell(2).setCellValue("calc_value");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(7);
            row.createCell(1).setCellValue(5);
            row.createCell(2).setCellFormula("A2+B2");

            workbook.write(out);
        }

        InfraExternalExchangeFile file = buildFile(fileId, source, "formula.xlsx");
        when(repository.findById(fileId)).thenReturn(Optional.of(file));

        var response = excelImportService.parse(
            new ExcelImportParseRequest(fileId, "sheet1", null, 1, 2, ",", 20, true, true, "yyyy-MM-dd HH:mm:ss"),
            "tester",
            "信息科",
            true
        );

        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(Files.readAllLines(Path.of(response.csvPath()))).containsExactly(
            "col_a,col_b,calc_value",
            "7,5,12"
        );
    }

    @Test
    void parseShouldTreatFormulaErrorsAsEmptyCellValues() throws Exception {
        UUID fileId = UUID.randomUUID();
        Path source = tempDir.resolve("exchange/excel/formula-error/source.xlsx");
        Files.createDirectories(source.getParent());
        try (XSSFWorkbook workbook = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(source)) {
            var sheet = workbook.createSheet("sheet1");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("col_a");
            header.createCell(1).setCellValue("formula_value");

            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("demo");
            var formula = row.createCell(1);
            formula.setCellFormula("1/0");
            formula.setCellErrorValue(FormulaError.DIV0.getCode());

            workbook.write(out);
        }

        InfraExternalExchangeFile file = buildFile(fileId, source, "formula-error.xlsx");
        when(repository.findById(fileId)).thenReturn(Optional.of(file));

        var response = excelImportService.parse(
            new ExcelImportParseRequest(fileId, "sheet1", null, 1, 2, ",", 20, true, true, "yyyy-MM-dd HH:mm:ss"),
            "tester",
            "信息科",
            true
        );

        assertThat(response.rowCount()).isEqualTo(1);
        assertThat(response.errorCount()).isZero();
        assertThat(Files.readAllLines(Path.of(response.csvPath()))).containsExactly(
            "col_a,formula_value",
            "demo,"
        );
    }

    @Test
    void loadProjectCockpitBatchShouldExposeAcceptedRejectedAndFailedRows() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Path baseDir = tempDir.resolve("exchange/excel/load-batch");
        Files.createDirectories(baseDir);
        Path csvPath = baseDir.resolve("data.csv");
        Path errorPath = baseDir.resolve("error.csv");
        Files.writeString(
            csvPath,
            """
            project_no,subsystem,node_task,dept,owner,project_manager,plan_date,actual_date,completion_status,risk_level
            P-001,System-A,Node-1,Dept-A,Alice,PM-A,2026-03-01,2026-03-02,已完成,低
            ,System-B,Node-2,Dept-B,Bob,PM-B,2026-03-03,,进行中,中
            P-003,System-C,Node-3,Dept-C,Carol,PM-C,/,2026-03-04,进行中,高
            P-004,,Node-4,Dept-D,Dan,PM-D,2026/03/05,bad-date,,/
            """.stripIndent() + "\n"
        );
        Files.writeString(errorPath, "");

        InfraExternalExchangeFile file = buildParsedFile(fileId, csvPath, errorPath, ",", 4, 0);
        when(repository.findById(fileId)).thenReturn(Optional.of(file));
        when(repository.saveAndFlush(any(InfraExternalExchangeFile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<InfraProjectCockpitRow> savedRows = new ArrayList<>();
        when(projectCockpitRowRepository.save(any(InfraProjectCockpitRow.class))).thenAnswer(invocation -> {
            InfraProjectCockpitRow row = invocation.getArgument(0);
            savedRows.add(row);
            return row;
        });
        List<InfraProjectCockpitIssue> savedIssues = new ArrayList<>();
        when(projectCockpitIssueRepository.save(any(InfraProjectCockpitIssue.class))).thenAnswer(invocation -> {
            InfraProjectCockpitIssue issue = invocation.getArgument(0);
            savedIssues.add(issue);
            return issue;
        });
        when(projectCockpitBatchRepository.findByExternalExchangeFileId(fileId)).thenReturn(Optional.empty());
        when(projectCockpitBatchRepository.findById(batchId)).thenAnswer(invocation -> Optional.ofNullable(findBatchById(invocation.getArgument(0), batchId, fileId)));
        when(projectCockpitBatchRepository.saveAndFlush(any(InfraProjectCockpitBatch.class))).thenAnswer(invocation -> {
            InfraProjectCockpitBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(batchId);
            }
            return batch;
        });
        when(projectCockpitIssueRepository.findByBatchIdOrderByRowNoAscIdAsc(batchId)).thenAnswer(invocation -> new ArrayList<>(savedIssues));

        var loadResponse = excelImportService.loadProjectCockpitBatch(fileId, "tester", "信息科", true);

        assertThat(loadResponse.loadedRowCount()).isEqualTo(4);
        assertThat(loadResponse.acceptedRowCount()).isEqualTo(2);
        assertThat(loadResponse.rejectedRowCount()).isEqualTo(2);
        assertThat(loadResponse.warningRowCount()).isEqualTo(1);
        assertThat(loadResponse.issueCount()).isEqualTo(3);
        assertThat(savedRows).hasSize(4);
        assertThat(savedRows).extracting(InfraProjectCockpitRow::getParseStatus)
            .containsExactly("PARSED", "BLOCKED", "BLOCKED", "PARSED_WITH_WARNINGS");
        assertThat(savedIssues)
            .extracting(InfraProjectCockpitIssue::getSeverity, InfraProjectCockpitIssue::getIssueCode)
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple("ERROR", "MISSING_PROJECT_NO"),
                org.assertj.core.groups.Tuple.tuple("ERROR", "MISSING_PLAN_DATE"),
                org.assertj.core.groups.Tuple.tuple("WARN", "INVALID_ACTUAL_DATE,MISSING_SUBSYSTEM,MISSING_COMPLETION_STATUS,MISSING_RISK_LEVEL")
            );

        var failurePreview = excelImportService.projectCockpitIssuePreview(batchId, "ERROR", 20, "tester", "信息科", true);
        assertThat(failurePreview.issueRowCount()).isEqualTo(2);
        assertThat(failurePreview.rows()).hasSize(2);
        assertThat(failurePreview.rows())
            .extracting(row -> row.rowIndex(), row -> row.issueCode(), row -> row.projectNo(), row -> row.planDate())
            .containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(2, "MISSING_PROJECT_NO", "", "2026-03-03"),
                org.assertj.core.groups.Tuple.tuple(3, "MISSING_PLAN_DATE", "P-003", "/")
            );

        Path acceptedCsv = baseDir.resolve("accepted.csv");
        Path rejectedCsv = baseDir.resolve("rejected.csv");
        assertThat(Files.readAllLines(acceptedCsv)).hasSize(3);
        assertThat(Files.readAllLines(rejectedCsv)).hasSize(3);
    }

    @Test
    void loadProjectCockpitBatchShouldEmitStructuredIssueLogs() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        Path baseDir = tempDir.resolve("exchange/excel/issue-log");
        Files.createDirectories(baseDir);
        Path csvPath = baseDir.resolve("data.csv");
        Path errorPath = baseDir.resolve("error.csv");
        Files.writeString(
            csvPath,
            """
            project_no,subsystem,node_task,dept,owner,project_manager,plan_date,actual_date,completion_status,risk_level
            P-001,System-A,Node-1,Dept-A,Alice,PM-A,2026-03-01,2026-03-02,已完成,低
            ,System-B,Node-2,Dept-B,Bob,PM-B,2026-03-03,,进行中,中
            P-003,,Node-3,Dept-C,Carol,PM-C,2026/03/05,bad-date,,/
            """.stripIndent() + "\n"
        );
        Files.writeString(errorPath, "4,计划日期格式异常\n");

        InfraExternalExchangeFile file = buildParsedFile(fileId, csvPath, errorPath, ",", 3, 1);
        when(repository.findById(fileId)).thenReturn(Optional.of(file));
        when(repository.saveAndFlush(any(InfraExternalExchangeFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectCockpitRowRepository.save(any(InfraProjectCockpitRow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectCockpitIssueRepository.save(any(InfraProjectCockpitIssue.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(projectCockpitBatchRepository.findByExternalExchangeFileId(fileId)).thenReturn(Optional.empty());
        when(projectCockpitBatchRepository.saveAndFlush(any(InfraProjectCockpitBatch.class))).thenAnswer(invocation -> {
            InfraProjectCockpitBatch batch = invocation.getArgument(0);
            if (batch.getId() == null) {
                batch.setId(batchId);
            }
            return batch;
        });

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ExcelImportService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            excelImportService.loadProjectCockpitBatch(fileId, "tester", "信息科", true);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        List<String> messages = appender.list
            .stream()
            .map(ILoggingEvent::getFormattedMessage)
            .filter(message -> message.contains("[excel-import-issue]"))
            .toList();

        assertThat(messages).hasSize(3);
        assertThat(messages).anySatisfy(message -> {
            assertThat(message).contains("severity=ERROR");
            assertThat(message).contains("issueCode=MISSING_PROJECT_NO");
            assertThat(message).contains("batch=excel-test-");
            assertThat(message).contains("fileId=" + fileId);
            assertThat(message).contains("row=2");
        });
        assertThat(messages).anySatisfy(message -> {
            assertThat(message).contains("severity=WARN");
            assertThat(message).contains("issueCode=INVALID_ACTUAL_DATE,MISSING_SUBSYSTEM,MISSING_COMPLETION_STATUS,MISSING_RISK_LEVEL");
            assertThat(message).contains("row=3");
            assertThat(message).contains("projectNo=P-003");
        });
        assertThat(messages).anySatisfy(message -> {
            assertThat(message).contains("severity=WARN");
            assertThat(message).contains("issueCode=PARSE_WARNING");
            assertThat(message).contains("row=4");
            assertThat(message).contains("rawLine=4,计划日期格式异常");
        });
    }

    private InfraExternalExchangeFile buildFile(UUID fileId, Path source, String fileName) {
        InfraExternalExchangeFile file = new InfraExternalExchangeFile();
        file.setId(fileId);
        file.setEntryKey("EXCEL_IMPORT");
        file.setFileName(fileName);
        file.setFilePath(source.toString());
        file.setBatchCode("excel-test-" + fileId.toString().substring(0, 8));
        file.setStatus("RECEIVED");
        file.setReceivedAt(Instant.parse("2026-03-18T08:00:00Z"));
        file.setOwnerDept("信息科");
        file.setEnabled(Boolean.TRUE);
        file.setProps(new ObjectMapper().valueToTree(java.util.Map.of("format", "xlsx", "sheets", List.of())).toString());
        return file;
    }

    private InfraExternalExchangeFile buildParsedFile(
        UUID fileId,
        Path csvPath,
        Path errorPath,
        String delimiter,
        int rowCount,
        int errorCount
    ) {
        InfraExternalExchangeFile file = new InfraExternalExchangeFile();
        file.setId(fileId);
        file.setEntryKey("EXCEL_IMPORT");
        file.setFileName("project-subject-domain.xlsx");
        file.setFilePath(csvPath.getParent().resolve("source.xlsx").toString());
        file.setBatchCode("excel-test-" + fileId.toString().substring(0, 8));
        file.setStatus("PARSED");
        file.setReceivedAt(Instant.parse("2026-03-18T08:00:00Z"));
        file.setOwnerDept("信息科");
        file.setEnabled(Boolean.TRUE);
        file.setProps(
            new ObjectMapper()
                .valueToTree(
                    Map.of(
                        "format", "xlsx",
                        "sheetName", "sheet1",
                        "csvPath", csvPath.toString(),
                        "errorPath", errorPath.toString(),
                        "delimiter", delimiter,
                        "rowCount", rowCount,
                        "errorCount", errorCount
                    )
                )
                .toString()
        );
        return file;
    }

    private InfraProjectCockpitBatch findBatchById(UUID actual, UUID expectedBatchId, UUID fileId) {
        if (!expectedBatchId.equals(actual)) {
            return null;
        }
        InfraProjectCockpitBatch batch = new InfraProjectCockpitBatch();
        batch.setId(expectedBatchId);
        batch.setExternalExchangeFileId(fileId);
        batch.setBatchCode("excel-test-" + fileId.toString().substring(0, 8));
        batch.setOwnerDept("信息科");
        batch.setStatus("LOADED");
        return batch;
    }
}
