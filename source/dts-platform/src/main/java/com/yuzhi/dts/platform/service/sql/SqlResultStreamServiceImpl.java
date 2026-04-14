package com.yuzhi.dts.platform.service.sql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yuzhi.dts.platform.domain.explore.QueryExecution;
import com.yuzhi.dts.platform.domain.explore.QueryExecutionChunk;
import com.yuzhi.dts.platform.domain.explore.ResultSet;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionChunkRepository;
import com.yuzhi.dts.platform.repository.explore.QueryExecutionRepository;
import com.yuzhi.dts.platform.repository.explore.ResultSetRepository;
import com.yuzhi.dts.platform.service.sql.dto.ColumnMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.QueryLogDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class SqlResultStreamServiceImpl implements SqlResultStreamService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 500;

    private final QueryExecutionRepository queryExecutionRepository;
    private final ResultSetRepository resultSetRepository;
    private final QueryExecutionChunkRepository chunkRepository;
    private final ObjectMapper objectMapper;

    public SqlResultStreamServiceImpl(
        QueryExecutionRepository queryExecutionRepository,
        ResultSetRepository resultSetRepository,
        QueryExecutionChunkRepository chunkRepository,
        ObjectMapper objectMapper
    ) {
        this.queryExecutionRepository = queryExecutionRepository;
        this.resultSetRepository = resultSetRepository;
        this.chunkRepository = chunkRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public ResultMetaDto getMeta(UUID executionId) {
        QueryExecution exec = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found: " + executionId));

        if (exec.getResultSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not available for execution: " + executionId);
        }

        ResultSet rs = resultSetRepository
            .findById(exec.getResultSetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not found: " + exec.getResultSetId()));

        List<ColumnMetaDto> columns = parseColumns(rs.getColumns());
        long totalRows = rs.getRowCount() != null ? rs.getRowCount() : 0L;
        boolean truncated = exec.getLimitApplied() != null && exec.getLimitApplied();
        String status = exec.getStatus() == null ? null : exec.getStatus().name();

        return new ResultMetaDto(
            executionId,
            status,
            columns,
            totalRows,
            truncated,
            exec.getElapsedMs(),
            exec.getBytesProcessed()
        );
    }

    @Override
    public ResultPageDto getPage(UUID executionId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.min(MAX_PAGE_SIZE, Math.max(MIN_PAGE_SIZE, pageSize));
        long fromRow = (long) (safePage - 1) * safePageSize;
        long toRow = fromRow + safePageSize;

        ResultMetaDto meta = getMeta(executionId);
        List<Map<String, Object>> rows = streamRange(executionId, fromRow, toRow).toList();

        return new ResultPageDto(rows, meta.columns(), safePage, safePageSize, meta.totalRows(), meta.truncated());
    }

    @Override
    public Stream<Map<String, Object>> streamRange(UUID executionId, long from, long to) {
        QueryExecution exec = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found: " + executionId));

        if (exec.getResultSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not available for execution: " + executionId);
        }

        ResultSet rs = resultSetRepository
            .findById(exec.getResultSetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not found: " + exec.getResultSetId()));

        List<QueryExecutionChunk> chunks = chunkRepository.findByExecutionIdOrderByChunkIndexAsc(executionId);

        if (chunks.isEmpty()) {
            // Legacy fallback: read from preview blob
            List<Map<String, Object>> rows = readLegacyRows(rs.getPreviewColumns(), from, (int) Math.min(to - from, MAX_PAGE_SIZE));
            return rows.stream();
        }

        return readFromChunks(chunks, from, to).stream();
    }

    private List<Map<String, Object>> readFromChunks(List<QueryExecutionChunk> chunks, long from, long to) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryExecutionChunk chunk : chunks) {
            long chunkStart = chunk.getRowStart();
            long chunkEnd = chunk.getRowEnd();

            if (chunkEnd <= from) {
                continue;
            }
            if (chunkStart >= to) {
                break;
            }

            List<Map<String, Object>> chunkRows = parseChunkRows(chunk.getRowsJson());
            long overlapStart = Math.max(from, chunkStart);
            long overlapEnd = Math.min(to, chunkEnd);
            int localFrom = (int) (overlapStart - chunkStart);
            int localTo = (int) (overlapEnd - chunkStart);

            if (localFrom < chunkRows.size()) {
                int actualLocalTo = Math.min(localTo, chunkRows.size());
                result.addAll(chunkRows.subList(localFrom, actualLocalTo));
            }
        }
        return result;
    }

    private List<Map<String, Object>> readLegacyRows(String previewJson, long from, int size) {
        if (!StringUtils.hasText(previewJson)) {
            return List.of();
        }
        try {
            Map<?, ?> payload = objectMapper.readValue(previewJson, Map.class);
            Object rawRows = payload.get("rows");
            if (!(rawRows instanceof List<?> list)) {
                return List.of();
            }
            List<Map<String, Object>> allRows = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null) {
                            row.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    allRows.add(row);
                }
            }
            int safeFrom = (int) Math.min(from, allRows.size());
            int safeTo = (int) Math.min((long) safeFrom + size, allRows.size());
            return safeFrom >= safeTo ? List.of() : new ArrayList<>(allRows.subList(safeFrom, safeTo));
        } catch (Exception ex) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseChunkRows(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    @Override
    public void exportCsv(UUID executionId, java.io.OutputStream out) {
        ResultMetaDto meta = getMeta(executionId);
        // BOM for Excel-friendly UTF-8 — write to OutputStream BEFORE wrapping in writer
        try {
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8));
        // Header
        writer.println(meta.columns().stream().map(c -> escapeCsv(c.name())).reduce((a, b) -> a + "," + b).orElse(""));
        streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < meta.columns().size(); i++) {
                if (i > 0) sb.append(',');
                Object v = row.get(meta.columns().get(i).name());
                sb.append(escapeCsv(v == null ? "" : String.valueOf(v)));
            }
            writer.println(sb);
        });
        writer.flush();
    }

    private static String escapeCsv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }

    @Override
    public void exportJson(UUID executionId, java.io.OutputStream out) {
        ResultMetaDto meta = getMeta(executionId);
        com.fasterxml.jackson.core.JsonFactory factory = objectMapper.getFactory();
        try (com.fasterxml.jackson.core.JsonGenerator gen = factory.createGenerator(out, com.fasterxml.jackson.core.JsonEncoding.UTF8)) {
            gen.writeStartArray();
            streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
                try {
                    gen.writeStartObject();
                    for (var col : meta.columns()) {
                        Object v = row.get(col.name());
                        gen.writeFieldName(col.name());
                        objectMapper.writeValue(gen, v);
                    }
                    gen.writeEndObject();
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
            gen.writeEndArray();
            gen.flush();
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public void exportExcel(UUID executionId, java.io.OutputStream out) {
        ResultMetaDto meta = getMeta(executionId);
        try (org.apache.poi.xssf.streaming.SXSSFWorkbook wb = new org.apache.poi.xssf.streaming.SXSSFWorkbook(100)) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("results");
            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < meta.columns().size(); i++) {
                header.createCell(i).setCellValue(meta.columns().get(i).name());
            }
            int[] rowIndex = { 1 };
            streamRange(executionId, 0, meta.totalRows()).forEach(row -> {
                org.apache.poi.ss.usermodel.Row r = sheet.createRow(rowIndex[0]++);
                for (int c = 0; c < meta.columns().size(); c++) {
                    Object v = row.get(meta.columns().get(c).name());
                    if (v == null) {
                        r.createCell(c).setBlank();
                    } else if (v instanceof Number n) {
                        r.createCell(c).setCellValue(n.doubleValue());
                    } else if (v instanceof Boolean b) {
                        r.createCell(c).setCellValue(b);
                    } else {
                        r.createCell(c).setCellValue(String.valueOf(v));
                    }
                }
            });
            wb.write(out);
            // wb.dispose() removed: POI 5.x close() (called by try-with-resources) invokes dispose() internally
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Override
    public QueryLogDto getLog(UUID executionId) {
        QueryExecution e = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found: " + executionId));

        // TODO F5 followup: store rewritten separately
        return new QueryLogDto(
            e.getId(),
            e.getSqlText(),
            e.getSqlText(),
            e.getStatus() == null ? null : e.getStatus().name(),
            e.getStartedAt(),
            e.getFinishedAt(),
            e.getRowCount(),
            e.getElapsedMs(),
            e.getErrorMessage(),
            e.getBytesProcessed()
        );
    }

    private List<ColumnMetaDto> parseColumns(String columnsStr) {
        if (!StringUtils.hasText(columnsStr)) {
            return List.of();
        }
        return Arrays.stream(columnsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(name -> new ColumnMetaDto(name, "string", true))
            .toList();
    }
}
