package com.yuzhi.dts.ingestion.service.etl;

import com.yuzhi.dts.ingestion.service.dto.ColumnInfo;
import com.yuzhi.dts.ingestion.service.dto.ParseResult;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CsvParseService {

    private static final int MAX_ROWS = 100_000;
    private static final int TYPE_CONFIDENCE_THRESHOLD = 80;
    private static final Pattern LONG_PATTERN = Pattern.compile("^-?\\d+$");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("^-?\\d+\\.\\d+$");
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("^(?i:true|false|yes|no|0|1)$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATETIME_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}(:\\d{2})?.*$");

    public ParseResult parse(InputStream inputStream) throws IOException {
        List<List<String>> records = readRecords(inputStream);
        if (records.isEmpty()) {
            return new ParseResult(0, List.of(), List.of(), List.of(), List.of());
        }
        List<String> headers = records.get(0);
        if (!headers.isEmpty()) {
            headers.set(0, stripBom(headers.get(0)));
        }

        int colCount = headers.size();
        List<List<String>> rows = new ArrayList<>();
        List<Integer> emptyRows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            if (i > MAX_ROWS) {
                throw new IllegalArgumentException("CSV 行数过多，最大支持 " + MAX_ROWS + " 行");
            }
            List<String> row = normalizeRow(records.get(i), colCount);
            if (row.stream().allMatch(value -> value == null || value.isBlank())) {
                emptyRows.add(i);
            }
            rows.add(row);
        }

        return new ParseResult(rows.size(), inferColumns(headers, rows), List.of(), emptyRows, rows);
    }

    public List<FileUploadService.FileColumn> parseHeaders(InputStream inputStream) throws IOException {
        List<List<String>> records = readRecords(inputStream);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> headers = records.get(0);
        if (!headers.isEmpty()) {
            headers.set(0, stripBom(headers.get(0)));
        }
        List<String> sample = records.size() > 1 ? records.get(1) : List.of();
        Set<String> used = new LinkedHashSet<>();
        List<FileUploadService.FileColumn> columns = new ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String label = headers.get(i) == null ? "" : headers.get(i).trim();
            String name = SqlFieldNameResolver.resolve(label, i, used);
            String type = i < sample.size() ? inferType(sample.get(i)) : "STRING";
            columns.add(new FileUploadService.FileColumn(name, type.toLowerCase(), label));
        }
        return columns;
    }

    private List<ColumnInfo> inferColumns(List<String> headers, List<List<String>> rows) {
        List<ColumnInfo> columns = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (int col = 0; col < headers.size(); col++) {
            int longCount = 0;
            int doubleCount = 0;
            int booleanCount = 0;
            int dateCount = 0;
            int timestampCount = 0;
            int total = 0;
            for (List<String> row : rows) {
                String value = col < row.size() ? row.get(col) : null;
                if (value == null || value.isBlank()) {
                    continue;
                }
                total++;
                switch (inferType(value)) {
                    case "LONG" -> longCount++;
                    case "DOUBLE" -> doubleCount++;
                    case "BOOLEAN" -> booleanCount++;
                    case "DATE" -> dateCount++;
                    case "TIMESTAMP" -> timestampCount++;
                    default -> {
                    }
                }
            }

            String type = "STRING";
            int best = 0;
            if (longCount > best) {
                type = "LONG";
                best = longCount;
            }
            if (doubleCount > best) {
                type = "DOUBLE";
                best = doubleCount;
            }
            if (booleanCount > best) {
                type = "BOOLEAN";
                best = booleanCount;
            }
            if (dateCount > best) {
                type = "DATE";
                best = dateCount;
            }
            if (timestampCount > best) {
                type = "TIMESTAMP";
                best = timestampCount;
            }
            int confidence = total == 0 ? 0 : (int) ((best * 100L) / total);
            if (confidence < TYPE_CONFIDENCE_THRESHOLD) {
                type = "STRING";
            }
            String label = headers.get(col) == null ? "" : headers.get(col).trim();
            columns.add(new ColumnInfo(SqlFieldNameResolver.resolve(label, col, used), type, confidence));
        }
        return columns;
    }

    private String inferType(String value) {
        if (value == null || value.isBlank()) {
            return "STRING";
        }
        String trimmed = value.trim();
        if (LONG_PATTERN.matcher(trimmed).matches()) return "LONG";
        if (DECIMAL_PATTERN.matcher(trimmed).matches()) return "DOUBLE";
        if (BOOLEAN_PATTERN.matcher(trimmed).matches()) return "BOOLEAN";
        if (DATETIME_PATTERN.matcher(trimmed).matches()) return "TIMESTAMP";
        if (DATE_PATTERN.matcher(trimmed).matches()) return "DATE";
        return "STRING";
    }

    private List<String> normalizeRow(List<String> row, int colCount) {
        List<String> normalized = new ArrayList<>(colCount);
        for (int i = 0; i < colCount; i++) {
            normalized.add(i < row.size() ? row.get(i) : null);
        }
        return normalized;
    }

    private List<List<String>> readRecords(InputStream inputStream) throws IOException {
        List<List<String>> records = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            List<String> record = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean inQuotes = false;
            int ch;
            while ((ch = reader.read()) != -1) {
                if (ch == '"') {
                    reader.mark(1);
                    int next = reader.read();
                    if (inQuotes && next == '"') {
                        field.append('"');
                    } else {
                        inQuotes = !inQuotes;
                        if (next != -1) {
                            reader.reset();
                        }
                    }
                    continue;
                }
                if (ch == ',' && !inQuotes) {
                    record.add(field.toString());
                    field.setLength(0);
                    continue;
                }
                if ((ch == '\n' || ch == '\r') && !inQuotes) {
                    if (ch == '\r') {
                        reader.mark(1);
                        int next = reader.read();
                        if (next != '\n' && next != -1) {
                            reader.reset();
                        }
                    }
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                    continue;
                }
                field.append((char) ch);
            }
            if (!record.isEmpty() || field.length() > 0) {
                record.add(field.toString());
                records.add(record);
            }
        }
        return records;
    }

    private String stripBom(String value) {
        if (value != null && value.startsWith("\uFEFF")) {
            return value.substring(1);
        }
        return value;
    }
}
