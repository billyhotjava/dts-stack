package com.yuzhi.dts.platform.service.development.dto;

public record ScriptCreateRequest(
    String name,
    String description,
    String scriptType,
    String content,
    String ownerDept
) {}
