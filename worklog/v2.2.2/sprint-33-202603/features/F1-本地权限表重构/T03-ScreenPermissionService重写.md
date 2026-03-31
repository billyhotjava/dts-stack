# T03: ScreenPermissionService 重写

**优先级**: P0
**状态**: READY
**依赖**: T02

## 目标

完整重写 `ScreenPermissionService`，所有权限判断查本地 `analytics_screen_access` 表，彻底移除 `PlatformPermissionClient` 调用。

## 技术设计

```java
@Service
public class ScreenPermissionService {

    private final AnalyticsScreenAccessRepository accessRepo;

    public ScreenPermissionService(AnalyticsScreenAccessRepository accessRepo) {
        this.accessRepo = accessRepo;
    }

    // ---- Permission snapshot ----

    public record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner) {
        public static PermissionSnapshot all()      { return new PermissionSnapshot(true, true, true); }
        public static PermissionSnapshot manageOnly(){ return new PermissionSnapshot(true, false, true); }
        public static PermissionSnapshot readOnly() { return new PermissionSnapshot(true, false, false); }
        public static PermissionSnapshot none()     { return new PermissionSnapshot(false, false, false); }
    }

    /**
     * 权限快照。superuser 直接返回 all()，其余查本地表。
     * roles 为空时安全处理（不传空 IN 子句）。
     */
    public PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user,
                                       List<String> roles) {
        if (user == null) return PermissionSnapshot.none();
        if (user.isSuperuser()) return PermissionSnapshot.all();

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = roles == null || roles.isEmpty()
            ? List.of("__NO_ROLE__") : roles;

        return accessRepo.findTopPermission(screen.getId(), userId, safeRoles)
            .map(p -> switch (p) {
                case "OWNER"   -> PermissionSnapshot.all();
                case "MANAGER" -> PermissionSnapshot.manageOnly();
                case "VIEWER"  -> PermissionSnapshot.readOnly();
                default        -> PermissionSnapshot.none();
            })
            .orElse(PermissionSnapshot.none());
    }

    /**
     * 列出用户可访问的大屏 ID。
     * superuser 返回哨兵 ["*"]，其余查本地表。
     */
    public List<Long> listAccessibleScreenIds(AnalyticsUser user, List<String> roles) {
        if (user == null) return List.of();
        if (user.isSuperuser()) return List.of(-1L); // 哨兵：调用方检测到 -1L 代表全量

        String userId = String.valueOf(user.getId());
        List<String> safeRoles = roles == null || roles.isEmpty()
            ? List.of("__NO_ROLE__") : roles;

        return accessRepo.findAccessibleScreenIds(userId, safeRoles);
    }

    public boolean isSuperuserSentinel(List<Long> ids) {
        return ids != null && ids.size() == 1 && ids.get(0) == -1L;
    }
}
```

**重要**：
- 移除所有旧方法：`invalidateCacheForUser`、`checkClassification`、`checkDataSourceAccess`、`isAllAccessible`
- 移除 `PlatformContext` 参数（不再需要 `dept` 或 `roles` header 解析）
- `roles` 参数由 `ScreenResource` 从 `X-DTS-Roles` 请求头解析后传入，Service 层不感知 HTTP

## 影响范围

- 重写: `service/ScreenPermissionService.java`
- 删除依赖: `PlatformPermissionClient`（权限查询路径）
- 修改调用方: `ScreenResource`（传 roles 列表而非 PlatformContext）

## 验证

- [ ] superuser 用户 snapshot 返回 all()
- [ ] OWNER 记录 → snapshot 返回 all()
- [ ] MANAGER 记录 → canRead=true, canEdit=false, isOwner=true
- [ ] VIEWER 记录 → canRead=true, canEdit=false, isOwner=false
- [ ] 无记录 → none()
- [ ] roles 为 null 或空时不抛异常
- [ ] ROLE 类型授权正常生效

## 完成标准

- [ ] 单元测试全部通过
- [ ] `PlatformPermissionClient` 在此类中零引用
