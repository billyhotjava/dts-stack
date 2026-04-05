package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.FormulaCell;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
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
                        // Extract cached formula result value instead of discarding
                        String cachedValue = getFormulaCachedValue(cell);
                        rowData.add(cachedValue);
                        if (cachedValue != null) {
                            allBlank = false;
                            String inferred = inferCellType(cachedValue);
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
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num) && !Double.isInfinite(num)) {
                    yield String.valueOf((long) num);
                }
                yield String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case BLANK -> null;
            default -> null;
        };
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
        if (value.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
            return "DATE";
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    private String getFormulaCachedValue(Cell cell) {
        try {
            return switch (cell.getCachedFormulaResultType()) {
                case STRING -> cell.getStringCellValue();
                case NUMERIC -> {
                    if (DateUtil.isCellDateFormatted(cell)) {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    }
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num) && !Double.isInfinite(num)) {
                        yield String.valueOf((long) num);
                    }
                    yield String.valueOf(num);
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
