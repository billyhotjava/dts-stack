package com.yuzhi.dts.ingestion.service.etl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.formula.eval.ErrorEval;
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

    @Test
    void validateFormulaCells_acceptsValidFormula() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("sheet1");
            sheet.createRow(0).createCell(0).setCellValue("value");
            var row = sheet.createRow(1);
            row.createCell(0, CellType.FORMULA).setCellFormula("1+1");
            workbook.write(out);
            bytes = out.toByteArray();
        }

        assertThatCode(() -> service.validateFormulaCells(bytes)).doesNotThrowAnyException();
    }

    private Object invokeIsFormulaCellUsable(Cell cell, FormulaEvaluator evaluator) throws Exception {
        Method method = ExcelParseService.class.getDeclaredMethod("isFormulaCellUsable", Cell.class, FormulaEvaluator.class);
        method.setAccessible(true);
        return method.invoke(service, cell, evaluator);
    }

    @Test
    void isFormulaCellUsable_returnsUsableWhenEvaluatorThrowsButCachedTypeExists() throws Exception {
        FormulaEvaluator evaluator = mock(FormulaEvaluator.class);
        Cell cell = mock(Cell.class);
        when(evaluator.evaluate(cell)).thenThrow(new IllegalStateException("FormulaParse"));
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.STRING);
        when(cell.getRowIndex()).thenReturn(1);
        when(cell.getColumnIndex()).thenReturn(0);

        assertThat(invokeIsFormulaCellUsable(cell, evaluator)).isEqualTo(true);
    }

    @Test
    void isFormulaCellUsable_allowsCachedValueWhenEvaluatorReturnsError() throws Exception {
        FormulaEvaluator evaluator = mock(FormulaEvaluator.class);
        Cell cell = mock(Cell.class);
        when(evaluator.evaluate(cell)).thenReturn(org.apache.poi.ss.usermodel.CellValue.getError(ErrorEval.NAME_INVALID.getErrorCode()));
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.STRING);
        when(cell.getRowIndex()).thenReturn(1);
        when(cell.getColumnIndex()).thenReturn(0);

        assertThat(invokeIsFormulaCellUsable(cell, evaluator)).isEqualTo(true);
    }

    @Test
    void isFormulaCellUsable_returnsFalseWhenEvaluatorAndCacheAllFail() throws Exception {
        FormulaEvaluator evaluator = mock(FormulaEvaluator.class);
        Cell cell = mock(Cell.class);
        when(evaluator.evaluate(cell)).thenThrow(new IllegalStateException("FormulaParse"));
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.ERROR);
        when(cell.getRowIndex()).thenReturn(1);
        when(cell.getColumnIndex()).thenReturn(0);

        assertThat(invokeIsFormulaCellUsable(cell, evaluator)).isEqualTo(false);
    }

    @Test
    void isFormulaCellUsable_allowsCachedValueWhenEvaluatorIsNull() throws Exception {
        Cell cell = mock(Cell.class);
        when(cell.getCachedFormulaResultType()).thenReturn(CellType.NUMERIC);

        assertThat(invokeIsFormulaCellUsable(cell, null)).isEqualTo(true);
    }
}
