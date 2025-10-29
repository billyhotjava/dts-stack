package com.yuzhi.dts.admin.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final String CLAIMS_NAMESPACE = "https://www.jhipster.tech/";

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    /**
     * 获取当前用户在审计日志中使用的账号标识；若未登录或为匿名用户则返回 "system"。
     */
    public static String getCurrentAuditableLogin() {
        return sanitizeLogin(getCurrentUserLogin().orElse(null));
    }

    public static String sanitizeLogin(String login) {
        if (!StringUtils.hasText(login)) {
            return "system";
        }
        String trimmed = login.trim();
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if ("anonymous".equals(lowered) || "anonymoususer".equals(lowered) || "unknown".equals(lowered)) {
            return "system";
        }
        if (looksLikeJwt(trimmed)) {
            return maskToken(trimmed);
        }
        return abbreviate(trimmed, 120);
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken jwt) {
            Map<String, Object> claims = jwt.getToken().getClaims();
            String preferred = extractStringClaim(claims, "preferred_username");
            if (StringUtils.hasText(preferred)) {
                return preferred;
            }
            String username = extractStringClaim(claims, "username");
            if (StringUtils.hasText(username)) {
                return username;
            }
            String legacyUserName = extractStringClaim(claims, "user_name");
            if (StringUtils.hasText(legacyUserName)) {
                return legacyUserName;
            }
            Object sub = claims.get("sub");
            if (sub != null && StringUtils.hasText(sub.toString())) {
                return sub.toString();
            }
            return jwt.getName();
        } else if (authentication instanceof BearerTokenAuthentication bearer) {
            Map<String, Object> attributes = bearer.getTokenAttributes();
            String preferred = extractStringClaim(attributes, "preferred_username");
            if (StringUtils.hasText(preferred)) {
                return preferred;
            }
            String username = extractStringClaim(attributes, "username");
            if (StringUtils.hasText(username)) {
                return username;
            }
            String legacyUserName = extractStringClaim(attributes, "user_name");
            if (StringUtils.hasText(legacyUserName)) {
                return legacyUserName;
            }
            String subject = extractStringClaim(attributes, "sub");
            if (StringUtils.hasText(subject)) {
                return subject;
            }
            return bearer.getName();
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
            String preferred = principal.getAttribute("preferred_username");
            if (preferred != null && !preferred.isBlank()) return preferred;
            String username = principal.getAttribute(org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.USERNAME);
            if (username != null && !username.isBlank()) return username;
            String sub = principal.getAttribute("sub");
            if (sub != null && !sub.isBlank()) return sub;
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    public static List<String> getCurrentUserAuthorities() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return List.of();
        }
        return getAuthorities(authentication).collect(Collectors.toList());
    }

    public static String getCurrentUserPrimaryAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        return resolvePrimaryAuthority(getAuthorities(authentication).collect(Collectors.toList()));
    }

    public static String resolvePrimaryAuthority(Collection<String> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> normalized = authorities
            .stream()
            .filter(java.util.Objects::nonNull)
            .map(SecurityUtils::normalizeRole)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (normalized.isEmpty()) {
            return null;
        }
        for (String candidate : PRIMARY_ROLE_PRIORITY) {
            if (normalized.contains(candidate)) {
                return candidate;
            }
        }
        return normalized.iterator().next();
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication instanceof JwtAuthenticationToken
            ? extractAuthorityFromClaims(((JwtAuthenticationToken) authentication).getToken().getClaims())
            : authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority);
    }

    private static String extractStringClaim(Map<String, Object> claims, String key) {
        if (claims == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        return value.toString();
    }

    private static boolean looksLikeJwt(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        int dotCount = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '.') {
                dotCount++;
                if (dotCount >= 2) {
                    break;
                }
            }
        }
        return dotCount >= 2 && value.startsWith("eyJ");
    }

    private static String maskToken(String token) {
        String trimmed = token.trim();
        int len = trimmed.length();
        int take = Math.min(8, len);
        String suffix = trimmed.substring(len - take);
        return "token:" + suffix;
    }

    private static String abbreviate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (maxLength <= 0 || trimmed.length() <= maxLength) {
            return trimmed;
        }
        if (maxLength <= 3) {
            return trimmed.substring(0, maxLength);
        }
        return trimmed.substring(0, maxLength - 3) + "...";
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        List<String> roles = new ArrayList<>();

        collectRoles(claims.get("groups"), roles);
        collectRoles(claims.get("roles"), roles);
        collectRoles(claims.get(CLAIMS_NAMESPACE + "roles"), roles);

        Object realmAccess = claims.get("realm_access");
        if (realmAccess instanceof Map<?, ?> realmMap) {
            collectRoles(realmMap.get("roles"), roles);
        }

        Object resourceAccess = claims.get("resource_access");
        if (resourceAccess instanceof Map<?, ?> resourceMap) {
            for (Object resource : resourceMap.values()) {
                if (resource instanceof Map<?, ?> resourceEntry) {
                    collectRoles(resourceEntry.get("roles"), roles);
                }
            }
        }

        return roles;
    }

    private static void collectRoles(Object source, Collection<String> target) {
        if (source == null) {
            return;
        }
        if (source instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (value instanceof String s && !s.isBlank()) {
                    target.add(s);
                }
            }
        } else if (source instanceof String s && !s.isBlank()) {
            target.add(s);
        }
    }

    private static List<GrantedAuthority> mapRolesToGrantedAuthorities(Collection<String> roles) {
        return roles
            .stream()
            .map(SecurityUtils::normalizeRole)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    private static final List<String> PRIMARY_ROLE_PRIORITY = List.of(
        AuthoritiesConstants.SYS_ADMIN,
        AuthoritiesConstants.AUTH_ADMIN,
        AuthoritiesConstants.AUDITOR_ADMIN,
        AuthoritiesConstants.OP_ADMIN,
        AuthoritiesConstants.ADMIN
    );

    private static final Map<String, String> ROLE_ALIASES = Map.ofEntries(
        // Canonical triad (no prefix aliases)
        Map.entry("SYSADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("AUTHADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("AUDITADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("SECURITYAUDITOR", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("OPADMIN", AuthoritiesConstants.OP_ADMIN),

        // Canonical triad (prefixed variants commonly seen in legacy realms)
        Map.entry("ROLE_SYSADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("ROLE_SYSTEM_ADMIN", AuthoritiesConstants.SYS_ADMIN),
        Map.entry("ROLE_AUTHADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("ROLE_IAM_ADMIN", AuthoritiesConstants.AUTH_ADMIN),
        Map.entry("ROLE_AUDITOR_ADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("ROLE_AUDIT_ADMIN", AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry("ROLE_SECURITYAUDITOR", AuthoritiesConstants.AUDITOR_ADMIN),

        // Already-canonical names map to themselves (idempotent)
        Map.entry(AuthoritiesConstants.SYS_ADMIN, AuthoritiesConstants.SYS_ADMIN),
        Map.entry(AuthoritiesConstants.AUTH_ADMIN, AuthoritiesConstants.AUTH_ADMIN),
        Map.entry(AuthoritiesConstants.AUDITOR_ADMIN, AuthoritiesConstants.AUDITOR_ADMIN),
        Map.entry(AuthoritiesConstants.OP_ADMIN, AuthoritiesConstants.OP_ADMIN)
    );

    private static String canonicalizeGovernanceRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        String compact = role.replaceAll("[^A-Z0-9]", "");
        if (compact.isEmpty()) {
            return null;
        }
        if (compact.startsWith("SYS")) {
            return AuthoritiesConstants.SYS_ADMIN;
        }
        if ((compact.startsWith("AUTH") || compact.startsWith("IAM")) && compact.contains("ADMIN")) {
            return AuthoritiesConstants.AUTH_ADMIN;
        }
        if (
            compact.startsWith("AUDIT") ||
            compact.startsWith("AUDITOR") ||
            compact.contains("SECURITYAUDITOR")
        ) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        if (compact.startsWith("SECURITY") && compact.contains("AUDITOR")) {
            return AuthoritiesConstants.AUDITOR_ADMIN;
        }
        if (compact.startsWith("OP") && compact.contains("ADMIN")) {
            return AuthoritiesConstants.OP_ADMIN;
        }
        return null;
    }

    public static String normalizeRole(String role) {
        if (role == null) {
            return null;
        }
        String trimmed = role.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        // First, try exact alias match (handles both prefixed and non-prefixed forms)
        String alias = ROLE_ALIASES.get(upper);
        if (alias != null) return alias;

        // If it's already prefixed, normalize common underscores and retry alias map
        if (upper.startsWith("ROLE_")) {
            String noPrefix = upper.substring(5);
            String aliasNoPrefix = ROLE_ALIASES.get(noPrefix);
            if (aliasNoPrefix != null) return aliasNoPrefix;
            String canonical = canonicalizeGovernanceRole(noPrefix);
            if (canonical != null) {
                return canonical;
            }
            return upper; // keep as-is
        }

        String canonical = canonicalizeGovernanceRole(upper);
        if (canonical != null) {
            return canonical;
        }

        // As a last resort, prefix unknown role names
        return "ROLE_" + upper;
    }
}
