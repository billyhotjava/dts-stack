package com.yuzhi.dts.platform.service.workbench.dto;

import java.util.List;

public record WorkbenchPreferencesResponse(
    int version,
    List<WorkbenchComponentDescriptor> availableComponents,
    List<WorkbenchPreferenceItem> items
) {}
