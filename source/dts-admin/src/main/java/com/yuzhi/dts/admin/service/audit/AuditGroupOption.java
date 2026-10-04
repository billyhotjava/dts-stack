package com.yuzhi.dts.admin.service.audit;

public record AuditGroupOption(
    String key,
    String title,
    String module,
    String groupDisplayName,
    String sourceSystem
) {}
