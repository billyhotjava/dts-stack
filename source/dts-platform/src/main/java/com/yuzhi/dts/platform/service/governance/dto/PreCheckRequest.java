package com.yuzhi.dts.platform.service.governance.dto;

import java.util.UUID;

public record PreCheckRequest(String stagingTableName, UUID datasetId, int totalRows) {}
