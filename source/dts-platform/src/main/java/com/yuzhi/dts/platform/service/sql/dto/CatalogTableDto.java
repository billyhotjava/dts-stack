package com.yuzhi.dts.platform.service.sql.dto;

public record CatalogTableDto(String name, String type, String comment, Long rowCountEstimate) {}
