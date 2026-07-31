package com.yuzhi.dts.platform.security;

import com.yuzhi.dts.common.security.SecurityLevelCatalog;
import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ClassificationUtils {
    private final Environment env;

    public ClassificationUtils(Environment env) {
        this.env = env;
    }

    /**
     * Resolve only an explicitly assigned user clearance.
     *
     * <p>Unlike {@link #getCurrentUserMaxLevel()}, this method never falls back to the
     * application default. Mutation entry points use it when an absent clearance must
     * fail closed.
     */
    public Optional<String> getCurrentUserExplicitMaxLevel() {
        String fromAbac = resolveMaxLevelFromPersonnelClaim();
        if (fromAbac != null) return Optional.of(fromAbac);
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_CONFIDENTIAL", "ROLE_TOP_SECRET")) {
            return Optional.of(SecurityLevelCatalog.DataSecurityLevel.CONFIDENTIAL.code());
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_SECRET")) {
            return Optional.of(SecurityLevelCatalog.DataSecurityLevel.SECRET.code());
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_INTERNAL")) {
            return Optional.of(SecurityLevelCatalog.DataSecurityLevel.INTERNAL.code());
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_PUBLIC")) {
            return Optional.of(SecurityLevelCatalog.DataSecurityLevel.PUBLIC.code());
        }
        return Optional.empty();
    }

    /**
     * Resolve current user's maximum allowed classification level.
     * Prefer ABAC personnel_level/person_security_level.
     * Fallback to realm roles: ROLE_CONFIDENTIAL (legacy ROLE_TOP_SECRET) > ROLE_SECRET > ROLE_INTERNAL > ROLE_PUBLIC.
     * Then to property dts.platform.default-user-classification (default INTERNAL).
     */
    public String getCurrentUserMaxLevel() {
        // 1) Prefer ABAC: personnel_level/person_security_level claim
        String fromAbac = resolveMaxLevelFromPersonnelClaim();
        if (fromAbac != null) return fromAbac;
        // 2) Fallback to legacy realm roles
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_TOP_SECRET")) {
            return SecurityLevelCatalog.DataSecurityLevel.CONFIDENTIAL.code();
        }
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_SECRET")) return SecurityLevelCatalog.DataSecurityLevel.SECRET.code();
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_INTERNAL")) return SecurityLevelCatalog.DataSecurityLevel.INTERNAL.code();
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_PUBLIC")) return SecurityLevelCatalog.DataSecurityLevel.PUBLIC.code();
        // 3) Final fallback: application default
        return Optional
            .ofNullable(env.getProperty("dts.platform.default-user-classification", String.class))
            .orElse(SecurityLevelCatalog.DEFAULT_DATA_SECURITY_LEVEL.code());
    }

    public boolean canAccess(String resourceLevel) {
        String userLevel = getCurrentUserMaxLevel();
        return rank(resourceLevel) <= rank(userLevel);
    }

    /**
     * 返回当前用户允许访问的所有 classification 取值（PUBLIC/INTERNAL/SECRET/CONFIDENTIAL）。
     * 用于 DashboardAccessGuard 等需要把"密级判定"以纯数据形式注入的场景。复用 getCurrentUserMaxLevel
     * 已有的 personnel_level claim → ROLE_xxx fallback → property default 三级解析，
     * 与 canAccess 完全等价，行为不分叉。
     */
    public java.util.Set<String> currentAllowedClassifications() {
        String maxCode = getCurrentUserMaxLevel();
        java.util.List<String> order = java.util.List.of("PUBLIC", "INTERNAL", "SECRET", "CONFIDENTIAL");
        int idx = maxCode == null ? -1 : order.indexOf(maxCode.trim().toUpperCase(java.util.Locale.ROOT));
        if (idx < 0) {
            // 未识别的 max code 视作最低权限（仅 PUBLIC），保守拒绝。
            return java.util.Set.of("PUBLIC");
        }
        return new java.util.LinkedHashSet<>(order.subList(0, idx + 1));
    }

    private int rank(String level) {
        return SecurityLevelCatalog.dataRank(level);
    }

    /**
     * Map personnel_level (GENERAL/IMPORTANT/CORE) to maximum classification.
     * GENERAL -> SECRET, IMPORTANT -> CONFIDENTIAL, CORE -> CONFIDENTIAL (system capped at CONFIDENTIAL)
     */
    private String resolveMaxLevelFromPersonnelClaim() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("personnel_level");
                if (v == null) v = token.getToken().getClaims().get("person_security_level");
                // Compatibility for historical typo
                if (v == null) v = token.getToken().getClaims().get("person_ssecurity_level");
                String text = firstTextValue(v);
                if (text != null) return mapPersonnelToClassification(text);
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof OAuth2AuthenticatedPrincipal p) {
                Object v = p.getAttribute("personnel_level");
                if (v == null) v = p.getAttribute("person_security_level");
                if (v == null) v = p.getAttribute("person_ssecurity_level");
                String text = firstTextValue(v);
                if (text != null) return mapPersonnelToClassification(text);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String mapPersonnelToClassification(String level) {
        PersonnelLevel personnel = PersonnelLevel.normalize(level);
        return personnel != null ? personnel.maxClassification() : null;
    }

    private String firstTextValue(Object raw) {
        Object flattened = flatten(raw);
        if (flattened == null) return null;
        String text = flattened.toString();
        return text == null || text.isBlank() ? null : text;
    }

    private Object flatten(Object raw) {
        if (raw == null) return null;
        if (raw instanceof Collection<?> collection) {
            return collection.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
        if (raw.getClass().isArray()) {
            int len = Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                Object element = Array.get(raw, i);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
        return raw;
    }
}
