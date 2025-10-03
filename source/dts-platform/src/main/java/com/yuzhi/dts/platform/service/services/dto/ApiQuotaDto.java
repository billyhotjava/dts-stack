package com.yuzhi.dts.platform.service.services.dto;

public record ApiQuotaDto(int qpsLimit, int dailyLimit, int dailyRemaining) {}
