package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.QueryLogDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.io.OutputStream;
import java.util.UUID;

public interface SqlResultStreamService {
    ResultMetaDto getMeta(UUID executionId);
    ResultPageDto getPage(UUID executionId, int page, int pageSize);
    java.util.stream.Stream<java.util.Map<String, Object>> streamRange(UUID executionId, long from, long to);
    void exportCsv(UUID executionId, OutputStream out);
    void exportJson(UUID executionId, OutputStream out);
    void exportExcel(UUID executionId, OutputStream out);
    QueryLogDto getLog(UUID executionId);
}
