package com.yuzhi.dts.ingestion.service.dto;

import java.util.List;

public record ParseResult(
    int totalRows,
    List<ColumnInfo> columns,
    List<FormulaCell> formulaCells,
    List<Integer> emptyRows,
    List<List<String>> rows
) {}
