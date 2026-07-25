package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;

public record CatalogTagPageDto(List<CatalogTagDto> content, long total, int page, int size) {}
