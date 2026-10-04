package com.yuzhi.dts.platform.service.sql.dto;

public record SqlViolation(String code, String message, boolean blocking) {}
