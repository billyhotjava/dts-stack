package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.SecurityUtils;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminGatewayIdentityResource {

    @GetMapping("/whoami")
    public ApiResponse<WhoAmI> whoAmI() {
        String username = SecurityUtils.getCurrentUserLogin().orElse(null);
        boolean allowed = SecurityUtils.isAuthenticated() && SecurityUtils.hasCurrentUserAnyOfAuthorities(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES);
        List<String> authorities = SecurityContextHolder.getContext().getAuthentication() == null
            ? List.of()
            : SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .map(item -> item.getAuthority())
                .filter(value -> value != null && !value.isBlank())
                .toList();
        String role = authorities
            .stream()
            .filter(value -> !"ROLE_USER".equals(value) && !"ROLE_ANONYMOUS".equals(value))
            .findFirst()
            .orElseGet(() -> authorities.stream().findFirst().orElse(null));
        if (role == null && allowed) {
            role = Arrays.stream(AuthoritiesConstants.INSTITUTE_PRIVILEGED_ROLES)
                .filter(SecurityUtils::hasCurrentUserAnyOfAuthorities)
                .findFirst()
                .orElse(null);
        }
        return ApiResponses.ok(new WhoAmI(allowed, role, username, null));
    }

    public record WhoAmI(boolean allowed, String role, String username, String email) {}
}
