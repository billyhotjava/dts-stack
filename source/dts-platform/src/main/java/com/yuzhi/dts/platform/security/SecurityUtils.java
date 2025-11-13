package com.yuzhi.dts.platform.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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
     * Get the technical identifier (subject) of the current user if present.
     *
     * @return the subject/identifier of the current user.
     */
    public static Optional<String> getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String sub = token.getToken().getSubject();
                if (sub == null) {
                    Object claim = token.getToken().getClaims().get("sub");
                    if (claim != null) {
                        sub = String.valueOf(claim);
                    }
                }
                return Optional.ofNullable(sub);
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof DefaultOidcUser oidcUser) {
                Object sub = oidcUser.getAttributes().get("sub");
                if (sub != null) {
                    return Optional.of(String.valueOf(sub));
                }
            }
            if (principal instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal oauth) {
                String sub = oauth.getAttribute("sub");
                if (sub != null && !sub.isBlank()) {
                    return Optional.of(sub);
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static Optional<String> getCurrentUserDisplayName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        try {
            if (authentication instanceof JwtAuthenticationToken token) {
                String display = extractDisplayNameFromClaims(token.getToken().getClaims());
                if (display != null) {
                    return Optional.of(display);
                }
            }
            Object principal = authentication.getPrincipal();
            if (principal instanceof DefaultOidcUser oidcUser) {
                String display = extractDisplayNameFromClaims(oidcUser.getAttributes());
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal oauth) {
                String display = extractDisplayNameFromClaims(oauth.getAttributes());
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof UserDetails springSecurityUser) {
                String display = firstNonBlank(
                    springSecurityUser.getUsername(),
                    springSecurityUser.getClass().getSimpleName()
                );
                if (display != null) {
                    return Optional.of(display);
                }
            } else if (principal instanceof String s) {
                String text = s == null ? null : s.trim();
                if (text != null && !text.isEmpty()) {
                    return Optional.of(text);
                }
            }
        } catch (Exception ignored) {}
        return Optional.empty();
    }

    public static boolean isOpAdminAccount() {
        // Prefer role-based check
        if (hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.OP_ADMIN)) {
            return true;
        }
        // Backward-compatible fallback: legacy username special-case
        return getCurrentUserLogin()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .filter("opadmin"::equals)
            .isPresent();
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication instanceof JwtAuthenticationToken jwt) {
            Map<String, Object> claims = jwt.getToken().getClaims();
            String preferred = stringClaim(claims, "preferred_username");
            if (hasText(preferred)) {
                return preferred;
            }
            String username = firstNonBlank(
                claims.get(org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.USERNAME),
                claims.get("user_name"),
                claims.get("username"),
                claims.get("upn")
            );
            if (hasText(username)) {
                return String.valueOf(username).trim();
            }
            String sub = stringClaim(claims, "sub");
            if (hasText(sub)) {
                return sub;
            }
            String tokenName = jwt.getName();
            if (hasText(tokenName)) {
                return tokenName.trim();
            }
            return null;
        } else if (authentication.getPrincipal() instanceof DefaultOidcUser) {
            Map<String, Object> attributes = ((DefaultOidcUser) authentication.getPrincipal()).getAttributes();
            if (attributes.containsKey("preferred_username")) {
                return (String) attributes.get("preferred_username");
            }
        } else if (authentication.getPrincipal() instanceof org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal principal) {
            // Opaque token (portal session) path: attributes carry username/sub
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

    private static String stringClaim(Map<String, Object> claims, String key) {
        if (claims == null || key == null) {
            return null;
        }
        Object value = claims.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
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

    private static Stream<String> getAuthorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities = authentication instanceof JwtAuthenticationToken
            ? extractAuthorityFromClaims(((JwtAuthenticationToken) authentication).getToken().getClaims())
            : authentication.getAuthorities();
        return authorities.stream().map(GrantedAuthority::getAuthority);
    }

    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        return mapRolesToGrantedAuthorities(getRolesFromClaims(claims));
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> getRolesFromClaims(Map<String, Object> claims) {
        return (Collection<String>) claims.getOrDefault(
            "groups",
            claims.getOrDefault("roles", claims.getOrDefault(CLAIMS_NAMESPACE + "roles", new ArrayList<>()))
        );
    }

    private static List<GrantedAuthority> mapRolesToGrantedAuthorities(Collection<String> roles) {
        return roles
            .stream()
            .map(role -> {
                if (role == null) {
                    return null;
                }
                String normalized = role.trim();
                if (normalized.isEmpty()) {
                    return null;
                }
                normalized = normalized.toUpperCase(Locale.ROOT);
                if (!normalized.startsWith("ROLE_")) {
                    normalized = "ROLE_" + normalized;
                }
                return new SimpleGrantedAuthority(normalized);
            })
            .filter(authority -> authority != null)
            .distinct()
            .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private static String extractDisplayNameFromClaims(Map<String, Object> claims) {
        if (claims == null || claims.isEmpty()) {
            return null;
        }
        Object direct = firstNonBlank(
            claims.get("fullName"),
            claims.get("full_name"),
            claims.get("displayName"),
            claims.get("display_name"),
            claims.get("name")
        );
        if (direct != null) {
            String text = String.valueOf(direct).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        Object given = claims.get("given_name");
        Object family = claims.get("family_name");
        String combined = joinNameParts(given, family);
        if (combined != null) {
            return combined;
        }
        Object preferred = claims.get("preferred_username");
        if (preferred != null) {
            String text = String.valueOf(preferred).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        if (claims.containsKey("attributes")) {
            Object attributes = claims.get("attributes");
            if (attributes instanceof Map<?, ?> attrMap) {
                Object attrName = firstNonBlank(attrMap.get("fullName"), attrMap.get("displayName"));
                if (attrName != null) {
                    String text = String.valueOf(attrName).trim();
                    if (!text.isEmpty()) {
                        return text;
                    }
                }
                if (attrMap.containsKey("fullName")) {
                    Object value = attrMap.get("fullName");
                    if (value instanceof Collection<?> collection && !collection.isEmpty()) {
                        String candidate = String.valueOf(collection.iterator().next()).trim();
                        if (!candidate.isEmpty()) {
                            return candidate;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String firstNonBlank(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?> collection) {
                for (Object element : collection) {
                    String candidate = String.valueOf(element).trim();
                    if (!candidate.isEmpty()) {
                        return candidate;
                    }
                }
            }
            String text = String.valueOf(value).trim();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return null;
    }

    private static String joinNameParts(Object given, Object family) {
        String givenText = given == null ? null : String.valueOf(given).trim();
        String familyText = family == null ? null : String.valueOf(family).trim();
        if ((givenText == null || givenText.isEmpty()) && (familyText == null || familyText.isEmpty())) {
            return null;
        }
        if (givenText != null && !givenText.isEmpty() && familyText != null && !familyText.isEmpty()) {
            return givenText + " " + familyText;
        }
        return givenText != null && !givenText.isEmpty() ? givenText : familyText;
    }
}
