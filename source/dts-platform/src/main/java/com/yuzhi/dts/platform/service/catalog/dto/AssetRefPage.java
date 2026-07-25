package com.yuzhi.dts.platform.service.catalog.dto;

import java.util.List;

public record AssetRefPage(List<AssetRef> content, long total, int page, int size) {}
