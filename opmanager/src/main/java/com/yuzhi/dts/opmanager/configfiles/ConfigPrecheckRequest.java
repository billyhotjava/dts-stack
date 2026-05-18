package com.yuzhi.dts.opmanager.configfiles;

import jakarta.validation.constraints.NotBlank;

public record ConfigPrecheckRequest(@NotBlank String packageRegistrationId) {}
