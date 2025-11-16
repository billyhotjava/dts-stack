package com.yuzhi.dts.admin.web.rest.dto.personnel;

import java.time.Instant;

public record PersonnelProfileView(
    Long id,
    String personCode,
    String externalId,
    String account,
    String fullName,
    String deptCode,
    String deptName,
    String lifecycleStatus,
    String email,
    String phone,
    Instant lastSyncedAt
) {}
