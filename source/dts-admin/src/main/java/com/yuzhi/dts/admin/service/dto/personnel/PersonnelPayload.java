package com.yuzhi.dts.admin.service.dto.personnel;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record PersonnelPayload(
    String personCode,
    String externalId,
    String account,
    String fullName,
    String nationalId,
    String deptCode,
    String deptName,
    String deptPath,
    String title,
    String grade,
    String email,
    String phone,
    String status,
    Instant activeFrom,
    Instant activeTo,
    Map<String, Object> attributes
) {
    public Map<String, Object> safeAttributes() {
        return attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
