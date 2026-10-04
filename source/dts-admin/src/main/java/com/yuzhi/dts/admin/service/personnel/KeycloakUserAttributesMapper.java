package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

/**
 * 纯函数：将 PersonnelPayload 映射为 Keycloak user attributes Map。
 * 所有 attribute key 统一为 snake_case，对齐 Sprint-2 F2 MDM 约定。
 */
public final class KeycloakUserAttributesMapper {

    private KeycloakUserAttributesMapper() {}

    public static Map<String, List<String>> toAttributes(PersonnelPayload payload) {
        Map<String, List<String>> attrs = new HashMap<>();
        String secLevelRaw = safe(
            payload.attributes().getOrDefault("securityLevel", payload.attributes().get("person_security_level"))
        );
        String secLevel = normalizeSecurityLevel(secLevelRaw);
        if (StringUtils.isBlank(secLevel)) {
            secLevel = SecurityLevelCatalog.DEFAULT_PERSONNEL_SECURITY_LEVEL.code();
        }
        putNonBlank(attrs, "person_security_level", secLevel);
        putNonBlank(attrs, "person_code", payload.personCode());
        putNonBlank(attrs, "external_id", payload.externalId());
        putNonBlank(attrs, "full_name", payload.fullName());
        putNonBlank(attrs, "national_id", payload.nationalId());
        putNonBlank(attrs, "dept_code", payload.deptCode());
        putNonBlank(attrs, "dept_name", payload.deptName());
        putNonBlank(attrs, "dept_path", payload.deptPath());
        putNonBlank(attrs, "title", payload.title());
        putNonBlank(attrs, "grade", payload.grade());
        putNonBlank(attrs, "email", payload.email());
        putNonBlank(attrs, "phone", payload.phone());
        payload.safeAttributes().forEach((k, v) -> {
            if (v != null && !attrs.containsKey(k)) {
                attrs.put(k, List.of(String.valueOf(v)));
            }
        });
        return attrs;
    }

    private static void putNonBlank(Map<String, List<String>> attrs, String key, String value) {
        if (StringUtils.isNotBlank(value)) {
            attrs.put(key, List.of(value.trim()));
        }
    }

    private static String safe(Object obj) {
        return obj == null ? "" : String.valueOf(obj);
    }

    static String normalizeSecurityLevel(String level) {
        return SecurityLevelCatalog.normalizePersonnelCode(level);
    }
}
