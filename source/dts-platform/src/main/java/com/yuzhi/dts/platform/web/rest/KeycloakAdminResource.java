package com.yuzhi.dts.platform.web.rest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/keycloak")
public class KeycloakAdminResource {

    // --- In-memory stores ---
    static final class Stores {
        final Map<String, Map<String, Object>> users = new ConcurrentHashMap<>();
        final Map<String, Map<String, Object>> roles = new ConcurrentHashMap<>();
        final Map<String, Map<String, Object>> groups = new ConcurrentHashMap<>();
        final Map<String, Set<String>> groupMembers = new ConcurrentHashMap<>();
        final AtomicLong groupSeq = new AtomicLong(1);

        Stores() {
            // seed roles
            roles.put("DATA_VIEWER", Map.of("name", "DATA_VIEWER", "description", "数据查看者"));
            roles.put("DATA_ADMIN", Map.of("name", "DATA_ADMIN", "description", "数据管理员"));
            // seed users
            putUser("u-alice", "alice", true, List.of("DATA_VIEWER"));
            putUser("u-bob", "bob", true, List.of("DATA_ADMIN"));
            // seed groups
            putGroup("g-1", "dev");
            putGroup("g-2", "ops");
            groupMembers.put("g-1", new HashSet<>(List.of("u-alice")));
        }

        private void putUser(String id, String username, boolean enabled, List<String> realmRoles) {
            Map<String, Object> u = new HashMap<>();
            u.put("id", id);
            u.put("username", username);
            u.put("email", username + "@example.com");
            u.put("enabled", enabled);
            u.put("realmRoles", new ArrayList<>(realmRoles));
            users.put(id, u);
        }

        private void putGroup(String id, String name) {
            Map<String, Object> g = new LinkedHashMap<>();
            g.put("id", id);
            g.put("name", name);
            g.put("path", "/" + name);
            g.put("attributes", Map.of());
            g.put("subGroups", List.of());
            groups.put(id, g);
        }
    }

    private final Stores stores = new Stores();

    // ---- Users ----
    @GetMapping("/users")
    public List<Map<String, Object>> listUsers(@RequestParam(required = false) Integer first, @RequestParam(required = false) Integer max) {
        return new ArrayList<>(stores.users.values());
    }

    @GetMapping("/users/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String username) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> u : stores.users.values()) {
            if (String.valueOf(u.get("username")).contains(username)) out.add(u);
        }
        return out;
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id) {
        Map<String, Object> u = stores.users.get(id);
        return u == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(u);
    }

    @PostMapping("/users")
    public Map<String, Object> createUser(@RequestBody Map<String, Object> body) {
        String id = (String) body.getOrDefault("id", UUID.randomUUID().toString());
        String username = String.valueOf(body.getOrDefault("username", id));
        stores.putUser(id, username, Boolean.TRUE.equals(body.getOrDefault("enabled", Boolean.TRUE)), List.of());
        return ok("用户创建成功");
    }

    @PutMapping("/users/{id}")
    public Map<String, Object> updateUser(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> u = stores.users.get(id);
        if (u == null) return error("用户不存在");
        u.putAll(body);
        u.put("id", id);
        return ok("用户更新成功");
    }

    @DeleteMapping("/users/{id}")
    public Map<String, Object> deleteUser(@PathVariable String id) {
        stores.users.remove(id);
        return ok("用户已删除");
    }

    @PostMapping("/users/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable String id) { return ok("密码重置成功"); }

    @PutMapping("/users/{id}/enabled")
    public Map<String, Object> setEnabled(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> u = stores.users.get(id);
        if (u == null) return error("用户不存在");
        u.put("enabled", body.getOrDefault("enabled", Boolean.TRUE));
        return ok("状态更新成功");
    }

    @GetMapping("/users/{id}/roles")
    public List<Map<String, Object>> getUserRoles(@PathVariable String id) {
        Map<String, Object> u = stores.users.get(id);
        if (u == null) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (String name : (List<String>) u.getOrDefault("realmRoles", List.of())) {
            Map<String, Object> r = stores.roles.get(name);
            if (r != null) out.add(r);
        }
        return out;
    }

    @PostMapping("/users/{id}/roles")
    public Map<String, Object> assignRoles(@PathVariable String id, @RequestBody List<Map<String, Object>> roles) {
        Map<String, Object> u = stores.users.get(id);
        if (u == null) return error("用户不存在");
        Set<String> set = new HashSet<>((List<String>) u.getOrDefault("realmRoles", new ArrayList<>()));
        for (Map<String, Object> r : roles) set.add(String.valueOf(r.get("name")));
        u.put("realmRoles", new ArrayList<>(set));
        return ok("角色绑定成功");
    }

    @DeleteMapping("/users/{id}/roles")
    public Map<String, Object> removeRoles(@PathVariable String id, @RequestBody List<Map<String, Object>> roles) {
        Map<String, Object> u = stores.users.get(id);
        if (u == null) return error("用户不存在");
        Set<String> toRemove = new HashSet<>();
        for (Map<String, Object> r : roles) toRemove.add(String.valueOf(r.get("name")));
        List<String> names = (List<String>) u.getOrDefault("realmRoles", new ArrayList<>());
        names.removeIf(toRemove::contains);
        u.put("realmRoles", names);
        return ok("角色解绑成功");
    }

    // ---- Roles ----
    @GetMapping("/roles")
    public List<Map<String, Object>> listRoles() { return new ArrayList<>(stores.roles.values()); }

    @GetMapping("/roles/{name}")
    public ResponseEntity<Map<String, Object>> getRole(@PathVariable String name) {
        Map<String, Object> r = stores.roles.get(name);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    @PostMapping("/roles")
    public Map<String, Object> createRole(@RequestBody Map<String, Object> body) {
        String name = String.valueOf(body.get("name"));
        stores.roles.put(name, Map.of("name", name, "description", body.getOrDefault("description", "")));
        return ok("角色创建成功");
    }

    @PutMapping("/roles/{name}")
    public Map<String, Object> updateRole(@PathVariable String name, @RequestBody Map<String, Object> body) {
        stores.roles.put(name, Map.of("name", name, "description", body.getOrDefault("description", "")));
        return ok("角色更新成功");
    }

    @DeleteMapping("/roles/{name}")
    public Map<String, Object> deleteRole(@PathVariable String name) {
        stores.roles.remove(name);
        return ok("角色已删除");
    }

    // ---- Groups ----
    @GetMapping("/groups")
    public List<Map<String, Object>> listGroups() { return new ArrayList<>(stores.groups.values()); }

    @GetMapping("/groups/{id}")
    public ResponseEntity<Map<String, Object>> getGroup(@PathVariable String id) {
        Map<String, Object> g = stores.groups.get(id);
        return g == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(g);
    }

    @GetMapping("/groups/{id}/members")
    public List<String> groupMembers(@PathVariable String id) { return new ArrayList<>(stores.groupMembers.getOrDefault(id, Set.of())); }

    @PostMapping("/groups")
    public Map<String, Object> createGroup(@RequestBody Map<String, Object> body) {
        String id = "g-" + stores.groupSeq.getAndIncrement();
        String name = String.valueOf(body.getOrDefault("name", id));
        stores.putGroup(id, name);
        stores.groupMembers.put(id, new HashSet<>());
        return ok("用户组创建成功");
    }

    @PutMapping("/groups/{id}")
    public Map<String, Object> updateGroup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> g = stores.groups.get(id);
        if (g == null) return error("用户组不存在");
        g.putAll(body);
        g.put("id", id);
        return ok("用户组更新成功");
    }

    @DeleteMapping("/groups/{id}")
    public Map<String, Object> deleteGroup(@PathVariable String id) {
        stores.groups.remove(id);
        stores.groupMembers.remove(id);
        return ok("用户组已删除");
    }

    @PostMapping("/groups/{id}/members/{userId}")
    public Map<String, Object> addMember(@PathVariable String id, @PathVariable String userId) {
        stores.groupMembers.computeIfAbsent(id, k -> new HashSet<>()).add(userId);
        return ok("加入成功");
    }

    @DeleteMapping("/groups/{id}/members/{userId}")
    public Map<String, Object> removeMember(@PathVariable String id, @PathVariable String userId) {
        stores.groupMembers.computeIfAbsent(id, k -> new HashSet<>()).remove(userId);
        return ok("移除成功");
    }

    @GetMapping("/groups/user/{userId}")
    public List<Map<String, Object>> getUserGroups(@PathVariable String userId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : stores.groups.entrySet()) {
            if (stores.groupMembers.getOrDefault(e.getKey(), Set.of()).contains(userId)) out.add(e.getValue());
        }
        return out;
    }

    // ---- UserProfile ----
    @GetMapping("/userprofile/config")
    public Map<String, Object> userProfileConfig() { return Map.of("attributes", List.of(), "groups", List.of(), "unmanagedAttributePolicy", "ENABLED"); }

    @PutMapping("/userprofile/config")
    public Map<String, Object> updateUserProfileConfig(@RequestBody Map<String, Object> cfg) { return ok("配置已更新"); }

    @GetMapping("/userprofile/configured")
    public Map<String, Object> userProfileConfigured() { return Map.of("configured", Boolean.FALSE); }

    @GetMapping("/userprofile/attributes")
    public List<String> userProfileAttributeNames() { return List.of(); }

    @GetMapping("/userprofile/test")
    public Map<String, Object> userProfileTest() { return Map.of("configured", false, "message", "ok", "attributeCount", 0); }

    private Map<String, Object> ok(String message) { return Map.of("status", 200, "message", message, "data", null); }
    private Map<String, Object> error(String message) { return Map.of("status", -1, "message", message, "data", null); }
}

