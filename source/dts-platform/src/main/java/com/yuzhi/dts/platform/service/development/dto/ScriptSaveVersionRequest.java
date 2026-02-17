package com.yuzhi.dts.platform.service.development.dto;

public record ScriptSaveVersionRequest(
    String content,
    String changeSummary,
    String status
) {}
