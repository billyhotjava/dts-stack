package com.yuzhi.dts.platform.service.development.dto;

public record ScriptUpdateRequest(
    String name,
    String description,
    String scriptType,
    String status,
    String ownerDept,
    Boolean enabled
) {}
