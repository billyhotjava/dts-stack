# T05: ScreenResource 端点改造

**优先级**: P0
**状态**: READY
**依赖**: T03, T04

## 目标

改造 `ScreenResource` 所有涉及权限的端点，适配本地权限表。移除 `PlatformContext`、`resolveGranteeUsername`、ID 翻译等过渡逻辑。

## 技术设计

### roles 解析（统一入口）

```java
// 从 X-DTS-Roles 请求头解析，空安全
private List<String> extractRoles(HttpServletRequest request) {
    String header = request.getHeader("X-DTS-Roles");
    if (header == null || header.isBlank()) return List.of();
    return Arrays.stream(header.split(","))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .toList();
}
```

### GET /api/screens（列表）

```java
List<String> roles = extractRoles(request);
List<Long> accessibleIds = screenPermissionService.listAccessibleScreenIds(user, roles);

List<AnalyticsScreen> screens;
if (screenPermissionService.isSuperuserSentinel(accessibleIds)) {
    screens = screenRepository.findAllByArchivedFalseOrderByIdDesc();
} else if (accessibleIds.isEmpty()) {
    screens = List.of();
} else {
    screens = screenRepository.findAllByIdInAndArchivedFalse(accessibleIds);
}
```

### POST /api/screens（创建）

```java
// 创建后插入 OWNER 记录，granteeId = userId 数字字符串
AnalyticsUser creator = user.orElseThrow();
screenOwnershipService.createGrant(
    screen.getId(), "USER",
    String.valueOf(creator.getId()),
    "OWNER",
    creator.getId());
// 删除: registerOwnership、createGrant(平台)、invalidateCacheForUser
```

### PUT /api/screens/{id}/grants（授权）

```java
// granteeId 直接使用前端传来的值：
// USER 时 = analytics user.id 字符串（无需翻译，直接存）
// ROLE 时 = 角色名字符串
String granteeType = body.path("granteeType").asText(null);
String granteeId   = body.path("granteeId").asText(null);
String permission  = body.path("permission").asText(null);

// 权限校验：需要 OWNER 或 MANAGER
PermissionSnapshot perms = screenPermissionService.snapshot(screen, user.orElseThrow(), roles);
if (!perms.isOwner()) return forbidden();

// 写本地表
AnalyticsScreenAccess grant = screenOwnershipService.createGrant(
    screen.getId(), granteeType, granteeId,
    permission, user.orElseThrow().getId());

return ResponseEntity.ok(toGrantResponse(grant));
// 删除: resolveGranteeUsername、invalidateCacheForUser、平台 REST 调用
```

### GET /api/screens/{id}/grants（权限列表）

```java
List<AnalyticsScreenAccess> grants = screenOwnershipService.listGrants(screen.getId());
return ResponseEntity.ok(grants.stream().map(this::toGrantResponse).toList());
```

响应序列化辅助方法：
```java
private Map<String, Object> toGrantResponse(AnalyticsScreenAccess g) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id",          g.getId());
    m.put("granteeType", g.getGranteeType());
    m.put("granteeId",   g.getGranteeId());
    m.put("permission",  g.getPermission());
    m.put("grantedBy",   g.getGrantedBy());
    m.put("createdDate", g.getCreatedAt());
    return m;
}
```

### DELETE /api/screens/{id}/grants/{grantId}（撤权）

```java
screenOwnershipService.revokeGrant(grantId);
// 删除: invalidateCacheForUser
```

### DELETE /api/screens/{id}（删除大屏）

```java
screenOwnershipService.removeAllGrants(screen.getId());
// ON DELETE CASCADE 已保护，但显式调用更清晰
```

## 删除清单

在 `ScreenResource` 中需删除：
- `resolveGranteeUsername(String)` 方法（ID 翻译，不再需要）
- `PlatformContext` 的所有引用（`context.dept()`, `context.roles()`）
- `screenPermissionService.invalidateCacheForUser(...)` 所有调用
- `AnalyticsUserRepository userRepository` 字段（仅用于翻译，现在可删）
- `backfillGrants` 端点中对平台的调用（T06 重写）
- `admin/backfill-grants` 和 `admin/migrate-platform-grants` 中的平台 REST 调用

## 影响范围

- 修改: `web/rest/ScreenResource.java`（大量改动）
- 删除字段: `userRepository`（若仅用于 resolveGranteeUsername）
- 修改签名: `screenPermissionService.snapshot(screen, user, roles)` — 第三参数改为 `List<String>`

## 验证

- [ ] 创建大屏后 `analytics_screen_access` 中有 OWNER 记录
- [ ] 授权后立即可查到（无缓存延迟）
- [ ] 撤权后权限消失
- [ ] 非 OWNER/MANAGER 调用授权接口返回 403
- [ ] 权限列表返回正确的 granteeId（数字字符串）

## 完成标准

- [ ] `PlatformContext` 在权限相关路径零引用
- [ ] `resolveGranteeUsername` 已删除
- [ ] 编译通过，无废弃方法调用
