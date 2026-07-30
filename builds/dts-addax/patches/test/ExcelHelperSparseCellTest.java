package com.yuzhi.dts.addax.patch;

import com.wgzhao.addax.core.element.Record;
import com.wgzhao.addax.core.exception.AddaxException;
import com.wgzhao.addax.core.spi.ErrorCode;
import com.wgzhao.addax.core.transport.record.DefaultRecord;
import com.wgzhao.addax.plugin.reader.excelreader.ExcelHelper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class ExcelHelperSparseCellTest {

    private ExcelHelperSparseCellTest() {}

    public static void main(String[] args) throws Exception {
        readsSparseRowsAtHeaderWidth();
        preservesMiddleGapsWithoutHeader();
        doesNotSilentlyDropColumnsBeyondHeader();
        System.out.println("ExcelHelperSparseCellTest: all tests passed");
    }

    private static void readsSparseRowsAtHeaderWidth() throws Exception {
        Path workbookPath = Files.createTempFile("addax-excelreader-sparse-", ".xlsx");
        try {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("data");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("col_a");
                header.createCell(1).setCellValue("col_b");
                header.createCell(2).setCellValue("col_c");
                header.createCell(3).setCellValue("col_d");
                Row data = sheet.createRow(1);
                data.createCell(0).setCellValue("value_a");
                data.createCell(2).setCellValue("value_c");
                try (var output = Files.newOutputStream(workbookPath)) {
                    workbook.write(output);
                }
            }

            Record record = readFirstDataRecord(workbookPath);
            assertEquals(4, record.getColumnNumber(), "fixed-width sparse row");
            assertEquals("value_a", record.getColumn(0).asString(), "column A");
            assertEquals("", record.getColumn(1).asString(), "missing column B");
            assertEquals("value_c", record.getColumn(2).asString(), "column C");
            assertEquals("", record.getColumn(3).asString(), "missing trailing column D");
        } finally {
            Files.deleteIfExists(workbookPath);
        }
    }

    private static void doesNotSilentlyDropColumnsBeyondHeader() throws Exception {
        Path workbookPath = Files.createTempFile("addax-excelreader-wide-row-", ".xlsx");
        try {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("data");
                Row header = sheet.createRow(0);
                header.createCell(0).setCellValue("col_a");
                header.createCell(1).setCellValue("col_b");
                header.createCell(2).setCellValue("col_c");
                Row data = sheet.createRow(1);
                data.createCell(0).setCellValue("value_a");
                data.createCell(4).setCellValue("unexpected_value_e");
                try (var output = Files.newOutputStream(workbookPath)) {
                    workbook.write(output);
                }
            }

            try {
                readFirstDataRecord(workbookPath);
                throw new AssertionError("expected a wider-than-header row to fail");
            } catch (AddaxException exception) {
                assertEquals(ErrorCode.ILLEGAL_VALUE, exception.getErrorCode(), "error code");
                assertContains(
                        exception.getMessage(),
                        "5 columns, exceeding the 3-column header schema",
                        "schema mismatch message");
            }
        } finally {
            Files.deleteIfExists(workbookPath);
        }
    }

    private static void preservesMiddleGapsWithoutHeader() throws Exception {
        Path workbookPath = Files.createTempFile("addax-excelreader-headerless-", ".xlsx");
        try {
            try (Workbook workbook = new XSSFWorkbook()) {
                Sheet sheet = workbook.createSheet("data");
                Row data = sheet.createRow(0);
                data.createCell(0).setCellValue("value_a");
                data.createCell(2).setCellValue("value_c");
                Row widerData = sheet.createRow(1);
                widerData.createCell(0).setCellValue("next_a");
                widerData.createCell(4).setCellValue("next_e");
                try (var output = Files.newOutputStream(workbookPath)) {
                    workbook.write(output);
                }
            }

            ExcelHelper helper = new ExcelHelper(false, 0);
            helper.open(workbookPath.toString());
            try {
                Record firstRecord = requireRecord(helper);
                assertEquals(3, firstRecord.getColumnNumber(), "headerless sparse row");
                assertEquals("value_a", firstRecord.getColumn(0).asString(), "headerless column A");
                assertEquals("", firstRecord.getColumn(1).asString(), "headerless missing column B");
                assertEquals("value_c", firstRecord.getColumn(2).asString(), "headerless column C");

                Record secondRecord = requireRecord(helper);
                assertEquals(5, secondRecord.getColumnNumber(), "wider headerless row");
                assertEquals("next_a", secondRecord.getColumn(0).asString(), "wider column A");
                assertEquals("", secondRecord.getColumn(3).asString(), "wider missing column D");
                assertEquals("next_e", secondRecord.getColumn(4).asString(), "wider column E");
            } finally {
                helper.close();
            }
        } finally {
            Files.deleteIfExists(workbookPath);
        }
    }

    private static Record readFirstDataRecord(Path workbookPath) {
        return readFirstRecord(workbookPath, true);
    }

    private static Record readFirstRecord(Path workbookPath, boolean header) {
        ExcelHelper helper = new ExcelHelper(header, 0);
        helper.open(workbookPath.toString());
        try {
            return requireRecord(helper);
        } finally {
            helper.close();
        }
    }

    private static Record requireRecord(ExcelHelper helper) {
        Record record = helper.readLine(new DefaultRecord());
        if (record == null) {
            throw new AssertionError("expected one data record");
        }
        return record;
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(label + ": expected [" + expected + "] but got [" + actual + "]");
        }
    }

    private static void assertContains(String actual, String expectedFragment, String label) {
        if (actual == null || !actual.contains(expectedFragment)) {
            throw new AssertionError(
                    label + ": expected [" + actual + "] to contain [" + expectedFragment + "]");
        }
    }
}
