package com.yuzhi.dts.platform.service.governance;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Resolves the data-quality department context without trusting arbitrary request headers. */
@Service
public class QualityEffectiveDepartmentResolver {

    private static final Pattern DEPT_CODE_ALLOWLIST = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    public String resolve(String requestedDepartment) {
        boolean institutePrivileged =
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES) ||
            SecurityUtils.isOpAdminAccount();
        String userDepartment = trimAndAllowlist(SecurityUtils.getCurrentUserDept().orElse(null));
        if (!institutePrivileged) {
            return userDepartment;
        }
        String requested = trimAndAllowlist(requestedDepartment);
        return requested != null ? requested : userDepartment;
    }

    private static String trimAndAllowlist(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return DEPT_CODE_ALLOWLIST.matcher(trimmed).matches() ? trimmed : null;
    }
}
