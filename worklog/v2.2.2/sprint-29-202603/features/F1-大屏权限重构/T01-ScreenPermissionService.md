# T01: ScreenPermissionService 封装 platform 权限调用

**优先级**: P0
**状态**: READY
**依赖**: 无

## 目标
新建 ScreenPermissionService，封装对 platform 统一资产权限 API 的调用，提供 canRead/canEdit/isOwner/snapshot 方法

## 技术设计

新建 `com.yuzhi.dts.analytics.service.ScreenPermissionService`：

```java
public class ScreenPermissionService {
    // 权限快照（替代旧 ScreenAclService.PermissionSnapshot）
    record PermissionSnapshot(boolean canRead, boolean canEdit, boolean isOwner)

    // 单个大屏权限检查
    PermissionSnapshot snapshot(AnalyticsScreen screen, AnalyticsUser user, PlatformContext context)

    // 角色基线判定
    // - OP_ADMIN → OWNER 级（所有大屏）
    // - INST_DATA_OWNER, INST_LEADER → EDIT（所有大屏）
    // - DEPT_DATA_OWNER, DEPT_LEADER → EDIT（本部门大屏，比对 screen.ownerDeptCode）
    // - 以上都不满足 → 调用 PlatformPermissionClient.check()

    // 密级校验预留（当前空实现）
    boolean checkClassification(AnalyticsScreen screen, String personnelLevel)

    // 数据源权限预留（当前空实现）
    boolean checkDataSourceAccess(AnalyticsScreen screen, AnalyticsUser user)
}
```

调用 `PlatformPermissionClient` 已有的 `check()` 和 `listAccessibleAssetIds()` 方法。

## 影响范围
- 新建: `service/ScreenPermissionService.java`
- 依赖: `PlatformPermissionClient`, `PlatformContext`

## 验证
- [ ] superuser 返回 OWNER 级权限
- [ ] 创建者返回 OWNER
- [ ] INST_DATA_OWNER 返回 canEdit=true
- [ ] DEPT_LEADER 对本部门大屏返回 canEdit=true，对其他部门返回 false
- [ ] EMPLOYEE 无基线，依赖 platform grant 结果

## 完成标准
- [ ] ScreenPermissionService 通过单元测试
- [ ] 预留方法（checkClassification、checkDataSourceAccess）存在且返回 true
