package com.yuzhi.dts.opmanager.configfiles;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ConfigApplyRequest(@NotBlank String packageRegistrationId, @NotBlank String path, @NotNull ConfigApplyAction action) {}
