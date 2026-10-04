package com.yuzhi.dts.opmanager.packageinfo;

import jakarta.validation.constraints.NotBlank;

public record PackageRegistrationRequest(@NotBlank String path) {}
