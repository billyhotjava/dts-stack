package com.yuzhi.dts.ingestion.service.etl.api;

import java.util.Map;

public record ApiAuthProviderField(
    String name,
    String label,
    String type,
    Boolean required,
    Boolean sensitive,
    String description,
    Map<String, Object> constraints
) {}

