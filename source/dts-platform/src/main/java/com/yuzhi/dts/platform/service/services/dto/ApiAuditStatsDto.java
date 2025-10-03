package com.yuzhi.dts.platform.service.services.dto;

public record ApiAuditStatsDto(long last24hCalls, long maskedHits, long denies) {}
