package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.util.UUID;

public interface SqlResultStreamService {

    /**
     * Returns column metadata and row/chunk counts for the given execution.
     *
     * @param executionId the query execution id
     * @return result metadata DTO
     * @throws org.springframework.web.server.ResponseStatusException with 404 if not found
     */
    ResultMetaDto getMeta(UUID executionId);

    /**
     * Returns a page of rows for the given execution using chunked storage.
     * Falls back to legacy preview blob if no chunks exist.
     *
     * @param executionId the query execution id
     * @param from        zero-based absolute row offset (inclusive)
     * @param size        number of rows to return (clamped to 1–500)
     * @return result page DTO
     * @throws org.springframework.web.server.ResponseStatusException with 404 if not found
     */
    ResultPageDto streamRange(UUID executionId, long from, int size);
}
