package com.yuzhi.dts.platform.service.permission;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.ClassificationUtils;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.lang.reflect.Array;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 集中把 Spring SecurityContext + ClassificationUtils 拼装为 DashboardAccessGuard.Caller，
 * 避免 BiReportLinkService / DashboardShareService 等多处重复实现。
 */
@Component
public class DashboardCallerResolver {

    private final ClassificationUtils classificationUtils;

    public DashboardCallerResolver(ClassificationUtils classificationUtils) {
        this.classificationUtils = classificationUtils;
    }

    public DashboardAccessGuard.Caller current() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        Set<String> allowed = classificationUtils.currentAllowedClassifications();
        Set<String> roles = currentAuthorities();
        String dept = currentClaim("dept_code");
        boolean institutePrivileged = SecurityUtils.hasCurrentUserAnyOfAuthorities(
            AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES
        );
        boolean superAdmin =
            SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.CATALOG_MAINTAINERS)
            || SecurityUtils.isOpAdminAccount();
        return new DashboardAccessGuard.Caller(username, allowed, roles, dept, institutePrivileged, superAdmin);
    }

    private Set<String> currentAuthorities() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities() == null) return Set.of();
            Set<String> set = new LinkedHashSet<>();
            for (var a : auth.getAuthorities()) {
                if (a == null) continue;
                String s = a.getAuthority();
                if (!StringUtils.hasText(s)) continue;
                set.add(normalizeRole(s));
            }
            set.remove(null);
            return set;
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private String currentClaim(String name) {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken token) {
                return toClaimText(token.getToken().getClaims().get(name));
            }
            if (auth != null && auth.getPrincipal() instanceof OAuth2AuthenticatedPrincipal principal) {
                return toClaimText(principal.getAttribute(name));
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String toClaimText(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) return element;
            }
            return null;
        }
        return raw;
    }

    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) return null;
        String upper = role.trim().toUpperCase(Locale.ROOT);
        return upper.startsWith("ROLE_") ? upper : "ROLE_" + upper;
    }
}
