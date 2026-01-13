package com.yuzhi.dts.platform.service.modeling;

import com.yuzhi.dts.platform.domain.modeling.DataStandard;
import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import jakarta.persistence.EntityNotFoundException;
import java.lang.reflect.Array;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Shared security helpers for Data Standard operations.
 * Centralizes role checks for Data Standard operations.
 */
@Component
public class DataStandardSecurity {

    private static final String[] INSTITUTE_AUTHORITIES = new String[] {
        AuthoritiesConstants.ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.INST_DATA_OWNER,
        AuthoritiesConstants.INST_LEADER
    };

    private static final String[] DEPARTMENT_AUTHORITIES = new String[] {
        AuthoritiesConstants.DEPT_DATA_OWNER,
        AuthoritiesConstants.DEPT_LEADER,
        AuthoritiesConstants.EMPLOYEE
    };

    public boolean hasInstituteScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(INSTITUTE_AUTHORITIES);
    }

    public boolean hasDepartmentScope() {
        return SecurityUtils.hasCurrentUserAnyOfAuthorities(DEPARTMENT_AUTHORITIES);
    }

    public String resolveActiveDept(String activeDeptHeader) {
        String candidate = trimToNull(activeDeptHeader);
        if (candidate != null) {
            return candidate;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                candidate = extractDept(token.getToken().getClaims().get("dept_code"));
                if (candidate == null) {
                    candidate = extractDept(token.getToken().getClaims().get("deptCode"));
                }
                if (candidate == null) {
                    candidate = extractDept(token.getToken().getClaims().get("department"));
                }
            } else if (authentication != null && authentication.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                candidate = extractDept(principal.getAttribute("dept_code"));
                if (candidate == null) {
                    candidate = extractDept(principal.getAttribute("deptCode"));
                }
                if (candidate == null) {
                    candidate = extractDept(principal.getAttribute("department"));
                }
            }
        } catch (Exception ignored) {}
        return trimToNull(candidate);
    }

    public void ensureReadable(DataStandard standard, String activeDeptHeader) {
        if (standard == null) {
            throw new EntityNotFoundException("数据标准不存在");
        }
    }

    public void ensureWritable(DataStandard standard, String activeDeptHeader) {
        ensureReadable(standard, activeDeptHeader);
    }

    /**
     * Determine the domain value to persist for create/update operations.
     *
     * <p>Note: Data Standard {@code domain} is used as a "主题域" (e.g. 财务/库存/项目),
     * and is no longer bound to login department.
     */
    public String enforceUpsertDomain(String requestedDomain, String activeDeptHeader) {
        return trimToNull(requestedDomain);
    }

    private String extractDept(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) {
            return null;
        }
        return trimToNull(flattened.toString());
    }

    private Object flatten(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        if (raw.getClass().isArray()) {
            int length = Array.getLength(raw);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
