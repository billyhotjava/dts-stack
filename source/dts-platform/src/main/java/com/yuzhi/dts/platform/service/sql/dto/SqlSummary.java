package com.yuzhi.dts.platform.service.sql.dto;

import java.util.List;

public record SqlSummary(List<SqlTableRef> tables, Integer limit, List<SqlColumnMeta> columns) {}
