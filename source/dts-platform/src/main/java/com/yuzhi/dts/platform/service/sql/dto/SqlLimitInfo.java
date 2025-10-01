package com.yuzhi.dts.platform.service.sql.dto;

public record SqlLimitInfo(boolean enforced, Integer limit, String reason) {}
