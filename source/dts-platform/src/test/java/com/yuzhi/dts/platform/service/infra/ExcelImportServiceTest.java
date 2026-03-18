package com.yuzhi.dts.platform.service.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.config.AirflowProperties;
import com.yuzhi.dts.platform.domain.infra.InfraExternalExchangeFile;
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
import java.util.List;
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
        when(repository.save(any(InfraExternalExchangeFile.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
}
