package com.yuzhi.dts.opmanager.configfiles;

import jakarta.validation.constraints.NotBlank;

public record ConfigLineApplyRequest(
    @NotBlank String packageRegistrationId,
    @NotBlank String path,
    Integer localLineNumber,
    Integer packageLineNumber,
    Integer insertAfterLocalLineNumber,
    String expectedLocalText,
    String expectedPackageText
) {}
