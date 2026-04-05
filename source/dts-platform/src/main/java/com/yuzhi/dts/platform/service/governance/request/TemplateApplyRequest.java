package com.yuzhi.dts.platform.service.governance.request;

import java.util.List;
import java.util.Map;

public record TemplateApplyRequest(
    String sourceTable,
    String sourceLayer,
    String datasetId,
    Map<String, String> fieldMapping,
    List<String> selectedBlueprints,
    Map<String, Map<String, Object>> overrides
) {}
