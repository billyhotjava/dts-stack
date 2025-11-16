package com.yuzhi.dts.admin.web.rest.dto.personnel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ManualImportRequest(
    String reference,
    boolean dryRun,
    @NotEmpty(message = "records 不能为空")
    List<@Valid PersonnelPayloadRequest> records
) {}
