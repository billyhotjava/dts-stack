package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class ExcelParseServiceTest {

    private final ExcelParseService service = new ExcelParseService();

    @Test
    void parseShouldNormalizeNegativeStringVariants() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("sheet1");
            sheet.createRow(0).createCell(0).setCellValue("metric");
            sheet.createRow(1).createCell(0, CellType.STRING).setCellValue("{900}");
            sheet.createRow(2).createCell(0, CellType.STRING).setCellValue("(1,234.50)");
            sheet.createRow(3).createCell(0, CellType.STRING).setCellValue("－900");
            sheet.createRow(4).createCell(0, CellType.STRING).setCellValue("（88）");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        var result = service.parse(new ByteArrayInputStream(bytes));

        assertThat(result.rows())
            .containsExactly(
                java.util.List.of("-900"),
                java.util.List.of("-1234.50"),
                java.util.List.of("-900"),
                java.util.List.of("-88")
            );
    }
}
