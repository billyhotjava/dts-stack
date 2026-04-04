package com.yuzhi.dts.ingestion.service.dto;

public record ColumnInfo(String name, String inferredType, int typeConfidence) {}
