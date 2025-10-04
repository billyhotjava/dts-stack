package com.yuzhi.dts.platform.web.rest;

import com.yuzhi.dts.platform.security.AuthoritiesConstants;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry;
import com.yuzhi.dts.platform.security.session.PortalSessionRegistry.PortalSession;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak/auth")
public class KeycloakAuthResource {

    private final PortalSessionRegistry sessionRegistry;

    public KeycloakAuthResource(PortalSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public record LoginPayload(String username, String password) {}
    public record RefreshPayload(String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginPayload payload) {
        String username = payload.username() == null ? "" : payload.username().trim();
        String norm = username.toLowerCase(Locale.ROOT);
        if (List.of("sysadmin", "authadmin", "auditadmin").contains(norm)) {
            return ResponseEntity.status(401).body(ApiResponses.error("系统管理角色用户不能登录业务平台"));
        }
        if (!StringUtils.hasText(username)) {
            return ResponseEntity.status(401).body(ApiResponses.error("用户名或密码错误"));
        }

        PortalAuthProfile profile = resolveProfile(norm);
        PortalSession session = sessionRegistry.createSession(username, profile.roles(), profile.permissions());

        Map<String, Object> user = Map.of(
            "id",
            UUID.nameUUIDFromBytes(username.getBytes()).toString(),
            "email",
            username + "@example.com",
            "username",
            username,
            "firstName",
            username,
            "enabled",
            Boolean.TRUE,
            "roles",
            profile.roles(),
            "permissions",
            profile.permissions()
        );

        Map<String, Object> data = Map.of(
            "user",
            user,
            "accessToken",
            session.accessToken(),
            "refreshToken",
            session.refreshToken()
        );
        return ResponseEntity.ok(ApiResponses.ok(data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@RequestBody(required = false) RefreshPayload payload) {
        if (payload != null && StringUtils.hasText(payload.refreshToken())) {
            sessionRegistry.invalidateByRefreshToken(payload.refreshToken());
        }
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody RefreshPayload payload) {
        try {
            PortalSession refreshed = sessionRegistry.refreshSession(payload.refreshToken());
            Map<String, String> data = Map.of(
                "accessToken",
                refreshed.accessToken(),
                "refreshToken",
                refreshed.refreshToken()
            );
            return ResponseEntity.ok(ApiResponses.ok(data));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(401).body(ApiResponses.error("刷新令牌无效，请重新登录"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(401).body(ApiResponses.error("会话已过期，请重新登录"));
        }
    }

    private PortalAuthProfile resolveProfile(String normalizedUsername) {
        List<String> roles = new ArrayList<>();
        List<String> permissions = new ArrayList<>();

        // Default viewer permissions
        permissions.add("portal.view");
        roles.add(AuthoritiesConstants.USER);

        if ("opadmin".equalsIgnoreCase(normalizedUsername)) {
            roles.add(AuthoritiesConstants.ADMIN);
            roles.add(AuthoritiesConstants.OP_ADMIN);
            roles.add(AuthoritiesConstants.CATALOG_ADMIN);
            roles.add(AuthoritiesConstants.GOV_ADMIN);
            roles.add(AuthoritiesConstants.IAM_ADMIN);
            permissions.add("portal.manage");
            permissions.add("catalog.manage");
            permissions.add("governance.manage");
            permissions.add("iam.manage");
        } else if (normalizedUsername.endsWith("catalog")) {
            roles.add(AuthoritiesConstants.CATALOG_ADMIN);
            permissions.add("catalog.manage");
        } else if (normalizedUsername.endsWith("governance")) {
            roles.add(AuthoritiesConstants.GOV_ADMIN);
            permissions.add("governance.manage");
        } else if (normalizedUsername.endsWith("iam")) {
            roles.add(AuthoritiesConstants.IAM_ADMIN);
            permissions.add("iam.manage");
        }

        return new PortalAuthProfile(roles.stream().distinct().toList(), permissions.stream().distinct().toList());
    }

    private record PortalAuthProfile(List<String> roles, List<String> permissions) {}
}
