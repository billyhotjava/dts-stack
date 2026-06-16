package com.yuzhi.dts.platform.service.workbench.dto;

public record WorkbenchComponentDescriptor(
    String key,
    String title,
    String description,
    boolean enabled,
    String disabledReason
) {}
