package com.yuzhi.dts.copilot.analytics.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Service for exporting query results to various formats.
 *
 * <p>Supports CSV, Excel (XLSX), and JSON export formats with streaming
 * for large result sets.
 */
@Service
public class QueryExportService {

    private static final String CSV_DELIMITER = ",";
    private static final String CSV_LINE_SEPARATOR = "\n";
    private static final int EXCEL_ROW_ACCESS_WINDOW_SIZE = 100;

    private final ObjectMapper objectMapper;

    public QueryExportService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Export query result to CSV format.
     *
     * @param result the query result
     * @param output the output stream
     * @param options export options
     * @throws IOException if writing fails
     */
    public void exportToCsv(DatasetQueryService.DatasetResult result, OutputStream output, ExportOptions options)
            throws IOException {
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            // Write BOM for Excel compatibility if requested
            if (options.includeBom()) {
                writer.write('\uFEFF');
            }

            // Write header row
            if (options.includeHeader()) {
                List<String> headers = extractColumnNames(result.cols());
                writer.write(escapeCsvRow(headers, options.delimiter()));
                writer.write(CSV_LINE_SEPARATOR);
            }

            // Write data rows
            for (List<Object> row : result.rows()) {
                writer.write(formatCsvRow(row, options.delimiter()));
                writer.write(CSV_LINE_SEPARATOR);
            }
        }
    }

    /**
     * Export query result to Excel format (XLSX).
     *
     * @param result the query result
     * @param output the output stream
     * @param options export options
     * @throws IOException if writing fails
     */
    public void exportToExcel(DatasetQueryService.DatasetResult result, OutputStream output, ExportOptions options)
            throws IOException {
        // Use SXSSFWorkbook for streaming large datasets
        try (Workbook workbook = new SXSSFWorkbook(EXCEL_ROW_ACCESS_WINDOW_SIZE)) {
            Sheet sheet = workbook.createSheet(options.sheetName() != null ? options.sheetName() : "Query Results");

            int rowIndex = 0;

            // Create header style
            CellStyle headerStyle = createHeaderStyle(workbook);

            // Write header row
            if (options.includeHeader()) {
                Row headerRow = sheet.createRow(rowIndex++);
                List<String> headers = extractColumnNames(result.cols());
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers.get(i));
                    cell.setCellStyle(headerStyle);
                }
            }

            // Write data rows
            for (List<Object> dataRow : result.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < dataRow.size(); i++) {
                    Cell cell = row.createCell(i);
                    setCellValue(cell, dataRow.get(i));
                }
            }

            // Auto-size columns (only for small datasets to avoid performance issues)
            if (result.rows().size() <= 1000 && result.cols().size() <= 50) {
                for (int i = 0; i < result.cols().size(); i++) {
                    sheet.autoSizeColumn(i);
                }
            }

            workbook.write(output);

            // Clean up streaming workbook
            if (workbook instanceof SXSSFWorkbook sxssf) {
                sxssf.dispose();
            }
        }
    }

    /**
     * Export query result to JSON format.
     *
     * @param result the query result
     * @param output the output stream
     * @param options export options
     * @throws IOException if writing fails
     */
    public void exportToJson(DatasetQueryService.DatasetResult result, OutputStream output, ExportOptions options)
            throws IOException {
        try (JsonGenerator generator = objectMapper.getFactory().createGenerator(output)) {
            generator.writeStartObject();

            // Write metadata
            generator.writeNumberField("row_count", result.rows().size());
            generator.writeStringField("timezone", result.resultsTimezone());

            // Write columns
            generator.writeFieldName("columns");
            generator.writeStartArray();
            for (Map<String, Object> col : result.cols()) {
                generator.writeObject(col);
            }
            generator.writeEndArray();

            // Write rows
            generator.writeFieldName("rows");
            generator.writeStartArray();

            if (options.jsonRowFormat() == JsonRowFormat.ARRAY) {
                // Array format: [[val1, val2], [val3, val4]]
                for (List<Object> row : result.rows()) {
                    generator.writeObject(row);
                }
            } else {
                // Object format: [{"col1": val1, "col2": val2}, ...]
                List<String> columnNames = extractColumnNames(result.cols());
                for (List<Object> row : result.rows()) {
                    generator.writeStartObject();
                    for (int i = 0; i < row.size() && i < columnNames.size(); i++) {
                        generator.writeFieldName(columnNames.get(i));
                        writeJsonValue(generator, row.get(i));
                    }
                    generator.writeEndObject();
                }
            }

            generator.writeEndArray();
            generator.writeEndObject();
        }
    }

    /**
     * Get the content type for the export format.
     *
     * @param format the export format
     * @return the content type
     */
    public String getContentType(ExportFormat format) {
        return switch (format) {
            case CSV -> "text/csv; charset=utf-8";
            case EXCEL -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case JSON -> "application/json; charset=utf-8";
        };
    }

    /**
     * Get the file extension for the export format.
     *
     * @param format the export format
     * @return the file extension
     */
    public String getFileExtension(ExportFormat format) {
        return switch (format) {
            case CSV -> ".csv";
            case EXCEL -> ".xlsx";
            case JSON -> ".json";
        };
    }

    private List<String> extractColumnNames(List<Map<String, Object>> cols) {
        return cols.stream()
                .map(col -> {
                    Object name = col.get("display_name");
                    if (name == null) {
                        name = col.get("name");
                    }
                    return name != null ? name.toString() : "";
                })
                .toList();
    }

    private String escapeCsvRow(List<String> values, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(escapeCsvValue(values.get(i)));
        }
        return sb.toString();
    }

    private String formatCsvRow(List<Object> row, String delimiter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) {
                sb.append(delimiter);
            }
            sb.append(formatCsvValue(row.get(i)));
        }
        return sb.toString();
    }

    private String formatCsvValue(Object value) {
        if (value == null) {
            return "";
        }
        return escapeCsvValue(value.toString());
    }

    private String escapeCsvValue(String value) {
        if (value == null) {
            return "";
        }
        // Quote if contains delimiter, quotes, or newlines
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
        } else if (value instanceof BigDecimal decimal) {
            cell.setCellValue(decimal.doubleValue());
        } else {
            String strValue = value.toString();
            // Excel cell limit is 32767 characters
            if (strValue.length() > 32767) {
                strValue = strValue.substring(0, 32767);
            }
            cell.setCellValue(strValue);
        }
    }

    private void writeJsonValue(JsonGenerator generator, Object value) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else if (value instanceof Number) {
            generator.writeNumber(((Number) value).toString());
        } else if (value instanceof Boolean bool) {
            generator.writeBoolean(bool);
        } else {
            generator.writeString(value.toString());
        }
    }

    /**
     * Export format options.
     */
    public enum ExportFormat {
        CSV,
        EXCEL,
        JSON
    }

    /**
     * JSON row format options.
     */
    public enum JsonRowFormat {
        ARRAY,
        OBJECT
    }

    /**
     * Export options.
     */
    public record ExportOptions(
            boolean includeHeader,
            boolean includeBom,
            String delimiter,
            String sheetName,
            JsonRowFormat jsonRowFormat) {

        public static ExportOptions defaults() {
            return new ExportOptions(true, true, CSV_DELIMITER, "Query Results", JsonRowFormat.OBJECT);
        }

        public static ExportOptions forCsv() {
            return new ExportOptions(true, true, CSV_DELIMITER, null, null);
        }

        public static ExportOptions forExcel() {
            return new ExportOptions(true, false, null, "Query Results", null);
        }

        public static ExportOptions forJson() {
            return new ExportOptions(false, false, null, null, JsonRowFormat.OBJECT);
        }

        public ExportOptions withHeader(boolean includeHeader) {
            return new ExportOptions(includeHeader, this.includeBom, this.delimiter, this.sheetName, this.jsonRowFormat);
        }

        public ExportOptions withDelimiter(String delimiter) {
            return new ExportOptions(this.includeHeader, this.includeBom, delimiter, this.sheetName, this.jsonRowFormat);
        }

        public ExportOptions withSheetName(String sheetName) {
            return new ExportOptions(this.includeHeader, this.includeBom, this.delimiter, sheetName, this.jsonRowFormat);
        }

        public ExportOptions withJsonRowFormat(JsonRowFormat format) {
            return new ExportOptions(this.includeHeader, this.includeBom, this.delimiter, this.sheetName, format);
        }
    }
}
