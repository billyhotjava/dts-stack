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
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
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
    @Transactional(readOnly = true)
    public ResultMetaDto getMeta(UUID executionId) {
        QueryExecution execution = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found: " + executionId));

        if (execution.getResultSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not available for execution: " + executionId);
        }

        ResultSet rs = resultSetRepository
            .findById(execution.getResultSetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not found: " + execution.getResultSetId()));

        List<ColumnMetaDto> columns = parseColumns(rs.getColumns());
        long rowCount = rs.getRowCount() != null ? rs.getRowCount() : 0L;
        int chunkCount = rs.getChunkCount() != null ? rs.getChunkCount() : 0;

        return new ResultMetaDto(executionId, rs.getId(), rowCount, chunkCount, columns);
    }

    @Override
    @Transactional(readOnly = true)
    public ResultPageDto streamRange(UUID executionId, long from, int size) {
        QueryExecution execution = queryExecutionRepository
            .findById(executionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "query execution not found: " + executionId));

        if (execution.getResultSetId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not available for execution: " + executionId);
        }

        ResultSet rs = resultSetRepository
            .findById(execution.getResultSetId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "result set not found: " + execution.getResultSetId()));

        int safeSize = Math.max(MIN_PAGE_SIZE, Math.min(MAX_PAGE_SIZE, size));
        long totalRows = rs.getRowCount() != null ? rs.getRowCount() : 0L;
        long safeFrom = Math.max(0L, from);

        List<QueryExecutionChunk> chunks = chunkRepository.findByExecutionIdOrderByChunkIndexAsc(executionId);

        List<String> headers = parseHeaderList(rs.getColumns());
        List<Map<String, Object>> rows;

        if (chunks.isEmpty()) {
            // Legacy fallback: read from preview blob
            rows = readLegacyRows(rs.getPreviewColumns(), safeFrom, safeSize);
            if (totalRows == 0L) {
                totalRows = rows.size();
            }
        } else {
            rows = readFromChunks(chunks, safeFrom, safeSize);
        }

        long actualTo = safeFrom + rows.size();
        boolean hasMore = actualTo < totalRows;

        return new ResultPageDto(executionId, safeFrom, actualTo, totalRows, hasMore, headers, rows);
    }

    private List<Map<String, Object>> readFromChunks(List<QueryExecutionChunk> chunks, long from, int size) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryExecutionChunk chunk : chunks) {
            long chunkStart = chunk.getRowStart();
            long chunkEnd = chunk.getRowEnd();

            if (chunkEnd <= from) {
                continue;
            }
            if (chunkStart >= from + size) {
                break;
            }

            List<Map<String, Object>> chunkRows = parseChunkRows(chunk.getRowsJson());
            long overlapStart = Math.max(from, chunkStart);
            long overlapEnd = Math.min(from + size, chunkEnd);
            int localFrom = (int) (overlapStart - chunkStart);
            int localTo = (int) (overlapEnd - chunkStart);

            if (localFrom < chunkRows.size()) {
                int actualLocalTo = Math.min(localTo, chunkRows.size());
                result.addAll(chunkRows.subList(localFrom, actualLocalTo));
            }

            if (result.size() >= size) {
                break;
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

    private List<ColumnMetaDto> parseColumns(String columnsStr) {
        if (!StringUtils.hasText(columnsStr)) {
            return List.of();
        }
        return Arrays.stream(columnsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(name -> new ColumnMetaDto(name, "string"))
            .toList();
    }

    private List<String> parseHeaderList(String columnsStr) {
        if (!StringUtils.hasText(columnsStr)) {
            return List.of();
        }
        return Arrays.stream(columnsStr.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
    }
}
