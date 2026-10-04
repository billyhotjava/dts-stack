package com.yuzhi.dts.admin.service.audit;

public record AuditCategoryOption(
    String moduleKey,
    String moduleTitle,
    String entryKey,
    String entryTitle
) {}
