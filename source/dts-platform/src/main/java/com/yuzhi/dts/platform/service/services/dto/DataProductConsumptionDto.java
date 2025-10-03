package com.yuzhi.dts.platform.service.services.dto;

import java.util.List;

public record DataProductConsumptionDto(
    RestChannel rest,
    JdbcChannel jdbc,
    FileChannel file
) {
    public record RestChannel(String endpoint, String auth) {}
    public record JdbcChannel(String driver, String url) {}
    public record FileChannel(String objectStorePath, String sharedPath, List<String> formats) {}
}
