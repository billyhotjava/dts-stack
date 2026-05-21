package com.yuzhi.dts.opmanager.workspace;

import jakarta.validation.constraints.NotBlank;

public record WorkspaceRootRequest(@NotBlank String packageRoot) {}
