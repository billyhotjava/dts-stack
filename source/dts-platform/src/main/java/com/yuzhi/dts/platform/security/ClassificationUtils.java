package com.yuzhi.dts.platform.security;

import java.util.Map;
import java.util.Optional;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class ClassificationUtils {
    // Ranking: higher means more sensitive
    // Four official levels; "CONFIDENTIAL" kept as alias between SECRET and TOP_SECRET if encountered
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
     * Fallback to realm roles: ROLE_TOP_SECRET > ROLE_SECRET > ROLE_INTERNAL > ROLE_PUBLIC.
     * Then to property dts.platform.default-user-classification (default INTERNAL).
     */
    public String getCurrentUserMaxLevel() {
        // 1) Prefer ABAC: personnel_level/person_security_level claim
        String fromAbac = resolveMaxLevelFromPersonnelClaim();
        if (fromAbac != null) return fromAbac;
        // 2) Fallback to legacy realm roles
        if (SecurityUtils.hasCurrentUserAnyOfAuthorities("ROLE_TOP_SECRET")) return "TOP_SECRET";
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
     * GENERAL -> INTERNAL, IMPORTANT -> SECRET, CORE -> TOP_SECRET
     */
    private String resolveMaxLevelFromPersonnelClaim() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            if (auth instanceof JwtAuthenticationToken token) {
                Object v = token.getToken().getClaims().get("personnel_level");
                if (v == null) v = token.getToken().getClaims().get("person_security_level");
                if (v instanceof String s) return mapPersonnelToClassification(s);
            }
            Object principal = auth.getPrincipal();
            if (principal instanceof OAuth2AuthenticatedPrincipal p) {
                String v = p.getAttribute("personnel_level");
                if (v == null) v = p.getAttribute("person_security_level");
                if (v != null && !v.isBlank()) return mapPersonnelToClassification(v);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String mapPersonnelToClassification(String level) {
        if (level == null) return null;
        String v = level.trim().toUpperCase();
        return switch (v) {
            case "CORE" -> "TOP_SECRET";
            case "IMPORTANT" -> "SECRET";
            case "GENERAL" -> "INTERNAL";
            default -> null;
        };
    }
}
