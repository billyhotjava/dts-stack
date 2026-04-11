package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.FormulaCell;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Full-scan Excel parser that reads all rows, detects formula cells,
 * infers column types from statistics, and returns all data as strings.
 */
@Service
public class ExcelParseService {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelParseService.class);
    private static final int TYPE_CONFIDENCE_THRESHOLD = 80;
    /** Maximum file size: 20MB */
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    /** Maximum rows to parse */
    private static final int MAX_ROWS = 100_000;

    public ParseResult parse(MultipartFile file) throws Exception {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "Excel 文件过大（" + (file.getSize() / 1024 / 1024) + "MB），最大支持 20MB");
        }
        return parse(file.getInputStream());
    }

    public ParseResult parse(java.io.InputStream inputStream) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                return new ParseResult(0, List.of(), List.of(), List.of(), List.of());
            }

            // Create evaluator for formula calculation (consistent with ExcelImportService)
            FormulaEvaluator evaluator = null;
            try {
                evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            } catch (Exception e) {
                LOG.warn("Cannot create FormulaEvaluator, will fallback to cached values: {}", e.getMessage());
            }
            DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

            // 1. Read header row (row 0)
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return new ParseResult(0, List.of(), List.of(), List.of(), List.of());
            }

            int colCount = headerRow.getLastCellNum();
            List<String> headers = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                Cell cell = headerRow.getCell(c);
                String name = getCellStringValue(cell);
                headers.add(name != null ? name.trim() : "column_" + c);
            }

            // 2. Iterate all data rows
            List<List<String>> rows = new ArrayList<>();
            List<FormulaCell> formulaCells = new ArrayList<>();
            List<Integer> emptyRows = new ArrayList<>();
            // Type stats per column: type -> count
            Map<Integer, Map<String, Integer>> typeStats = new HashMap<>();
            Map<Integer, Integer> nonNullCounts = new HashMap<>();

            int lastRowNum = sheet.getLastRowNum();
            if (lastRowNum > MAX_ROWS) {
                throw new IllegalArgumentException(
                    "Excel 行数过多（" + lastRowNum + " 行），最大支持 " + MAX_ROWS + " 行");
            }
            int totalRows = lastRowNum; // data rows = row 1..lastRowNum

            for (int r = 1; r <= lastRowNum; r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    emptyRows.add(r);
                    rows.add(nullRow(colCount));
                    continue;
                }

                List<String> rowData = new ArrayList<>(colCount);
                boolean allBlank = true;

                for (int c = 0; c < colCount; c++) {
                    Cell cell = row.getCell(c);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        formulaCells.add(new FormulaCell(r, headers.get(c), cell.getCellFormula()));
                        // Try evaluating the formula first, fallback to cached value
                        String formulaValue = evaluateFormulaCell(cell, evaluator, dataFormatter);
                        if (formulaValue == null) {
                            formulaValue = getFormulaCachedValue(cell);
                        }
                        rowData.add(formulaValue);
                        if (formulaValue != null) {
                            allBlank = false;
                            String inferred = inferCellType(formulaValue);
                            typeStats.computeIfAbsent(c, k -> new HashMap<>())
                                .merge(inferred, 1, Integer::sum);
                            nonNullCounts.merge(c, 1, Integer::sum);
                        }
                        continue;
                    }

                    String value = getCellStringValue(cell);
                    rowData.add(value);

                    if (value != null) {
                        allBlank = false;
                        // Track type statistics
                        String inferred = inferCellType(value);
                        typeStats.computeIfAbsent(c, k -> new HashMap<>())
                            .merge(inferred, 1, Integer::sum);
                        nonNullCounts.merge(c, 1, Integer::sum);
                    }
                }

                if (allBlank) {
                    emptyRows.add(r);
                }
                rows.add(rowData);
            }

            // 3. Infer column types from stats
            List<ColumnInfo> columns = new ArrayList<>(colCount);
            for (int c = 0; c < colCount; c++) {
                String colName = headers.get(c);
                Map<String, Integer> stats = typeStats.getOrDefault(c, Map.of());
                int total = nonNullCounts.getOrDefault(c, 0);

                if (total == 0) {
                    columns.add(new ColumnInfo(colName, "STRING", 0));
                    continue;
                }

                // Find dominant type
                String bestType = "STRING";
                int bestCount = 0;
                for (var entry : stats.entrySet()) {
                    if (entry.getValue() > bestCount) {
                        bestCount = entry.getValue();
                        bestType = entry.getKey();
                    }
                }

                int confidence = (int) ((bestCount * 100L) / total);
                if (confidence < TYPE_CONFIDENCE_THRESHOLD) {
                    bestType = "STRING";
                }
                columns.add(new ColumnInfo(colName, bestType, confidence));
            }

            LOG.info("Parsed Excel: {} data rows, {} columns, {} formula cells, {} empty rows",
                totalRows, colCount, formulaCells.size(), emptyRows.size());

            return new ParseResult(totalRows, columns, formulaCells, emptyRows, rows);
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield formatDateTime(cell.getLocalDateTimeCellValue());
                }
                yield formatNumeric(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK -> null;
            default -> null;
        };
    }

    private static final double SAFE_INTEGER_LIMIT = 1E15;

    private String formatNumeric(double num) {
        if (Double.isNaN(num) || Double.isInfinite(num)) {
            return String.valueOf(num);
        }
        if (num == Math.floor(num) && Math.abs(num) < SAFE_INTEGER_LIMIT) {
            return String.valueOf((long) num);
        }
        if (num == Math.floor(num)) {
            // >15 digits: double has already lost precision (IEEE 754 limit).
            // Log warning — the cell should have been stored as text in Excel.
            LOG.warn("Numeric cell with value {} exceeds 15-digit safe integer range, "
                + "precision may be lost. Consider storing as text in Excel.", BigDecimal.valueOf(num).toPlainString());
            return BigDecimal.valueOf(num).toPlainString();
        }
        return BigDecimal.valueOf(num).stripTrailingZeros().toPlainString();
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        if (dateTime.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return dateTime.toLocalDate().toString();
        }
        return dateTime.toString().replace('T', ' ');
    }

    private String inferCellType(String value) {
        if (value == null || value.isBlank()) {
            return "STRING";
        }
        if (value.matches("-?\\d+")) {
            return "LONG";
        }
        if (value.matches("-?\\d+\\.\\d+")) {
            return "DOUBLE";
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(:\\d{2})?.*")) {
            return "TIMESTAMP";
        }
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return "DATE";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    /**
     * Evaluate a formula cell using FormulaEvaluator + DataFormatter.
     * Uses formatter.formatCellValue() for consistent output with ExcelImportService,
     * with special handling for dates (which need our formatDateTime) and large numbers.
     */
    private String evaluateFormulaCell(Cell cell, FormulaEvaluator evaluator, DataFormatter formatter) {
        if (evaluator == null) {
            return null;
        }
        try {
            // For date-formatted cells, use our formatDateTime to preserve full datetime
            CellValue cellValue = evaluator.evaluate(cell);
            if (cellValue == null) {
                return null;
            }
            if (cellValue.getCellType() == CellType.ERROR) {
                return null;
            }
            if (cellValue.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return formatDateTime(cell.getLocalDateTimeCellValue());
            }
            // For all other cases, use DataFormatter for consistent display formatting
            String formatted = formatter.formatCellValue(cell, evaluator);
            if (formatted == null || formatted.isBlank()) {
                return null;
            }
            return formatted.trim();
        } catch (Exception e) {
            LOG.debug("Formula evaluation failed at row {}, col {}: {}",
                cell.getRowIndex(), cell.getColumnIndex(), e.getMessage());
            return null;
        }
    }

    private String getFormulaCachedValue(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield formatDateTime(cell.getLocalDateTimeCellValue());
                    }
                    yield formatNumeric(cell.getNumericCellValue());
                }
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                default -> null;
            };
        } catch (Exception e) {
            LOG.debug("Cannot read cached formula value at row {}: {}", cell.getRowIndex(), e.getMessage());
            return null;
        }
    }

    private List<String> nullRow(int colCount) {
        List<String> row = new ArrayList<>(colCount);
        for (int i = 0; i < colCount; i++) {
            row.add(null);
        }
        return row;
    }
}
