package com.yuzhi.dts.admin.web.rest.dto.personnel;

import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.util.CollectionUtils;

public record PersonnelPayloadRequest(
    String personCode,
    String externalId,
    String account,
    @NotBlank(message = "fullName 不能为空")
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
    public PersonnelPayload toPayload() {
        return new PersonnelPayload(
            personCode,
            externalId,
            account,
            fullName,
            nationalId,
            deptCode,
            deptName,
            deptPath,
            title,
            grade,
            email,
            phone,
            status,
            activeFrom,
            activeTo,
            CollectionUtils.isEmpty(attributes) ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes)
        );
    }
}
