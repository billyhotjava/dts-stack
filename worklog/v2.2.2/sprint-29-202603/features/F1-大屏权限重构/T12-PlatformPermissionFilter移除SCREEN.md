# T12: PlatformPermissionFilter 移除 SCREEN 逻辑

**优先级**: P1
**状态**: READY
**依赖**: T07

## 目标
从 PlatformPermissionFilter 中移除 SCREEN_PATTERN，大屏权限完全由 ScreenPermissionService 在 Controller 层处理

## 技术设计

修改 `PlatformPermissionFilter.java`：
- 删除 `SCREEN_PATTERN` 常量
- 删除 `resolveAsset()` 中 SCREEN_PATTERN 匹配分支
- `/api/screens` 已在 SKIP_PREFIXES 中（之前的修改），确认保留

CARD、DASHBOARD、TABLE 的 Filter 逻辑保持不变。

## 影响范围
- 修改: `PlatformPermissionFilter.java` — 移除 SCREEN 相关逻辑

## 验证
- [ ] SCREEN 请求不再经过 PlatformPermissionFilter
- [ ] CARD/DASHBOARD/TABLE 请求仍正常走 Filter
- [ ] 编译通过

## 完成标准
- [ ] SCREEN_PATTERN 已删除
- [ ] 大屏访问不受 Filter 拦截
