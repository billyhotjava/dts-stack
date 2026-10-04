# T01: ScreenSharePanel 显示名称修复

**优先级**: P1
**状态**: READY
**依赖**: F1/T05

## 背景

后端 `listGrants` 返回的 `granteeId` 现在是 analytics 数字 ID 字符串（如 `"42"`）。`ScreenSharePanel.resolveUserNames` 调用 `analyticsApi.getUser(id)` 时传入此 ID，正好匹配 `/bi/api/user/{id}` 的数字 ID 参数，**无需改动**。

同时，`existingUserIds` 过滤逻辑用 `String(u.id)` 比对 `granteeId`，两者均为数字字符串，**去重过滤正确**。

## 需要确认的点

1. `analyticsApi.getUser(id)` 的参数类型是 `string | number`，传入 `"42"` 时后端是否正确解析为 Long
2. ROLE 类型的 grant（`granteeId = "ROLE_XXX"`）在 UI 中应显示为`角色 ROLE_XXX`——确认现有渲染逻辑 `角色 ${entry.subjectId}` 已覆盖此情况

## 验证步骤

1. opadmin 授权 test230916（VIEWER）
2. 打开 ScreenSharePanel，确认显示 test230916 的显示名称（非 "用户 42"）
3. 确认 test230916 不再出现在搜索结果（已授权过滤生效）
4. 撤权后 test230916 重新出现在搜索结果

## 完成标准

- [ ] 授权列表正确显示用户名称
- [ ] 重复授权过滤正常（不能重复添加已授权用户）
- [ ] ROLE 类型 grant 正确渲染
