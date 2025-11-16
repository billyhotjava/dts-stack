package com.yuzhi.dts.admin.service.personnel;

import com.yuzhi.dts.admin.service.dto.personnel.PersonnelPayload;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class PersonnelPayloadMapper {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private PersonnelPayloadMapper() {}

    static PersonnelPayload fromMap(Map<String, ?> raw) {
        if (raw == null) {
            throw new PersonnelImportException("记录不能为空");
        }
        Map<String, Object> working = new LinkedHashMap<>(raw);
        String personCode = extract(working, "person_code", "personCode", "code", "emp_code", "人员编号", "职工号");
        String externalId = extract(working, "external_id", "externalId", "empId", "员工ID", "external");
        String account = extract(working, "account", "accountName", "username", "login", "user_name", "账号");
        String fullName = extract(working, "full_name", "fullName", "name", "姓名");
        String nationalId = extract(working, "id_card", "idCard", "national_id", "nationalId", "证件号码");
        String deptCode = extract(working, "dept_code", "deptCode", "departmentCode", "部门编码", "org_code");
        String deptName = extract(working, "dept_name", "deptName", "departmentName", "部门名称", "org_name");
        String deptPath = extract(working, "dept_path", "deptPath", "departmentPath", "部门路径", "org_path");
        String title = extract(working, "title", "jobTitle", "岗位", "post");
        String grade = extract(working, "grade", "rank", "职级");
        String email = extract(working, "email", "mail");
        String phone = extract(working, "phone", "mobile", "手机号");
        String status = extract(working, "status", "state", "employmentStatus", "在岗状态");
        Instant activeFrom = parseInstant(working.getOrDefault("active_from", working.get("activeFrom")));
        if (activeFrom == null) {
            activeFrom = parseInstant(working.get("entry_date"));
        }
        Instant activeTo = parseInstant(working.getOrDefault("active_to", working.get("activeTo")));
        sanitize(working);
        return new PersonnelPayload(
            emptyToNull(personCode),
            emptyToNull(externalId),
            emptyToNull(account),
            emptyToNull(fullName),
            emptyToNull(nationalId),
            emptyToNull(deptCode),
            emptyToNull(deptName),
            emptyToNull(deptPath),
            emptyToNull(title),
            emptyToNull(grade),
            emptyToNull(email),
            emptyToNull(phone),
            emptyToNull(status),
            activeFrom,
            activeTo,
            working
        );
    }

    private static void sanitize(Map<String, Object> attrs) {
        attrs.remove("active_from");
        attrs.remove("activeFrom");
        attrs.remove("active_to");
        attrs.remove("activeTo");
    }

    private static String extract(Map<String, ?> raw, String... keys) {
        for (String key : keys) {
            if (raw.containsKey(key)) {
                Object value = raw.remove(key);
                String text = toString(value);
                if (text != null) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (value instanceof Number number) {
            return number.toString();
        }
        return value.toString().trim();
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Instant parseInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant();
        }
        String text = toString(value);
        if (text == null) {
            return null;
        }
        try {
            return Instant.parse(text);
        } catch (Exception ignore) {
            try {
                LocalDate localDate = LocalDate.parse(text, DATE_FORMAT);
                return localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
