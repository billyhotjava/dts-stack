package com.yuzhi.dts.opmanager.configfiles;

import java.util.List;

public record ConfigFileReview(
    String path,
    ConfigCategory category,
    ConfigFileStatus status,
    ConfigRisk risk,
    boolean localExists,
    boolean packageExists,
    long localSize,
    long packageSize,
    List<String> localLines,
    List<String> packageLines,
    boolean contentOmitted,
    String message,
    List<ConfigApplyAction> allowedActions
) {}
