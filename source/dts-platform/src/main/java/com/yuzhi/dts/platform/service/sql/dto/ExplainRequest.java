package com.yuzhi.dts.platform.service.sql.dto;

import java.util.UUID;

public record ExplainRequest(String sql, String engine, UUID datasourceId, String catalog) {}
