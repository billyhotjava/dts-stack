package com.yuzhi.dts.opmanager.packageinfo;

import java.util.List;

public record PackageValidationResult(
    boolean valid,
    String packageId,
    String product,
    String version,
    String targetArch,
    String sourcePath,
    List<String> messages
) {}
