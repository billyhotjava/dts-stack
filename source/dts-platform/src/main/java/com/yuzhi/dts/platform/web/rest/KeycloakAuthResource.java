package com.yuzhi.dts.platform.web.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak/auth")
public class KeycloakAuthResource {

    public record LoginPayload(String username, String password) {}
    public record RefreshPayload(String refreshToken) {}

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody LoginPayload payload) {
        String username = payload.username() == null ? "" : payload.username().trim();
        // 业务端仅允许普通/运维账号登录，屏蔽系统管理角色（示例规则）
        if (List.of("sysadmin", "authadmin", "auditadmin").contains(username.toLowerCase())) {
            return ResponseEntity.status(401).body(ApiResponses.error("系统管理角色用户不能登录业务平台"));
        }
        // 简化：接受任意非空用户名，返回固定角色与权限
        if (username.isBlank()) {
            return ResponseEntity.status(401).body(ApiResponses.error("用户名或密码错误"));
        }
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
            List.of("OADMIN"),
            "permissions",
            List.of("portal.view", "portal.manage")
        );
        Map<String, Object> data = Map.of(
            "user",
            user,
            "accessToken",
            UUID.randomUUID().toString(),
            "refreshToken",
            UUID.randomUUID().toString()
        );
        return ResponseEntity.ok(ApiResponses.ok(data));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        return ResponseEntity.ok(ApiResponses.ok(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Map<String, String>>> refresh(@RequestBody RefreshPayload payload) {
        Map<String, String> data = Map.of("accessToken", UUID.randomUUID().toString(), "refreshToken", payload.refreshToken());
        return ResponseEntity.ok(ApiResponses.ok(data));
    }
}

