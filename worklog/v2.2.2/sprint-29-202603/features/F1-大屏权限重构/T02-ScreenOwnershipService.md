# T02: ScreenOwnershipService 管理 ownership 生命周期

**优先级**: P0
**状态**: READY
**依赖**: T01

## 目标
新建 ScreenOwnershipService，在大屏创建/删除时同步 platform 的 asset_ownership 记录

## 技术设计

新建 `com.yuzhi.dts.analytics.service.ScreenOwnershipService`：

```java
public class ScreenOwnershipService {
    // 创建大屏时调用：注册 ownership 到 platform
    void registerOwnership(Long screenId, String ownerUsername, String ownerDeptCode)
    // POST /api/asset-ownership { assetType: "SCREEN", assetId, ownerDeptCode, ownerUsername, assignedBy: "SYSTEM" }

    // 删除大屏时调用：清理 ownership 和所有 grants
    void removeOwnership(Long screenId)
    // DELETE /api/asset-ownership?assetType=SCREEN&assetId={id}

    // 变更归属部门（预留）
    void transferOwnership(Long screenId, String newOwnerUsername, String newDeptCode)
}
```

通过 `PlatformPermissionClient`（或新的 HTTP 调用）与 platform 的 `/api/asset-ownership` 端点交互。

## 影响范围
- 新建: `service/ScreenOwnershipService.java`
- 改造: `ScreenResource.create()` 创建后调用 registerOwnership
- 改造: `ScreenResource.delete()` 删除前调用 removeOwnership

## 验证
- [ ] 创建大屏后 platform 的 asset_ownership 表有记录
- [ ] 删除大屏后 asset_ownership 记录已清理
- [ ] platform API 不可用时不阻塞大屏创建（降级处理）

## 完成标准
- [ ] 创建/删除生命周期与 platform ownership 同步
- [ ] 异常降级不阻塞核心流程
