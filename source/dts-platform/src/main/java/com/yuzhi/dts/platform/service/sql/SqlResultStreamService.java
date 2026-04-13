package com.yuzhi.dts.platform.service.sql;

import com.yuzhi.dts.platform.service.sql.dto.ResultMetaDto;
import com.yuzhi.dts.platform.service.sql.dto.ResultPageDto;
import java.util.UUID;

public interface SqlResultStreamService {
    ResultMetaDto getMeta(UUID executionId);
    ResultPageDto getPage(UUID executionId, int page, int pageSize);
    java.util.stream.Stream<java.util.Map<String, Object>> streamRange(UUID executionId, long from, long to);
}
