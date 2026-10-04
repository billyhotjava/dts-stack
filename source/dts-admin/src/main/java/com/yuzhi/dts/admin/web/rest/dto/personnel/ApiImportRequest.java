package com.yuzhi.dts.admin.web.rest.dto.personnel;

public record ApiImportRequest(
    String reference,
    String cursor,
    boolean dryRun
) {}
