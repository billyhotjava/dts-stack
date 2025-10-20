package com.yuzhi.dts.platform.security;

import com.yuzhi.dts.platform.security.policy.PersonnelLevel;
import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;
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
    // Ranking: higher means more sensitive. "TOP_SECRET" retained as compatibility alias for legacy data.
    private static final Map<String, Integer> RANK = Map.of(
        "PUBLIC", 1,
        "INTERNAL", 2,
        "SECRET", 3,
        "CONFIDENTIAL", 4,
        "TOP_SECRET", 5
    );

    private final Environment env;

    public ClassificationUtils(Environment env) {
        this.env = env;
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
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_TOP_SECRET")) return "CONFIDENTIAL";
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_SECRET")) return "SECRET";
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_INTERNAL")) return "INTERNAL";
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_PUBLIC")) return "PUBLIC";
        // 3) Final fallback: application default
        return Optional.ofNullable(env.getProperty("dts.platform.default-user-classification", String.class)).orElse("INTERNAL");
    }

    public boolean canAccess(String resourceLevel) {
        String userLevel = getCurrentUserMaxLevel();
        return rank(resourceLevel) <= rank(userLevel);
    }

    private int rank(String level) {
        if (level == null) return 0;
        return RANK.getOrDefault(level.toUpperCase(), 0);
    }

    /**
     * Map personnel_level (GENERAL/IMPORTANT/CORE) to maximum classification.
     * GENERAL -> SECRET, IMPORTANT -> CONFIDENTIAL, CORE -> TOP_SECRET (compat; maps to CONFIDENTIAL data set)
     */
    private String resolveMaxLevelFromPersonnelClaim() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("personnel_level");
                if (v == null) v = token.getToken().getClaims().get("person_security_level");
                String text = firstTextValue(v);
                if (text != null) return mapPersonnelToClassification(text);
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof OAuth2AuthenticatedPrincipal p) {
                Object v = p.getAttribute("personnel_level");
                if (v == null) v = p.getAttribute("person_security_level");
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
