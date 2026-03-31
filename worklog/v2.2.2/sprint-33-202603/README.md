# Sprint-33: 大屏权限本地化重构 — 替换平台权限查询路径

**时间**: 2026-03
**状态**: READY
**目标**: 废弃平台资产权限 API 在大屏权限查询路径的使用，改用 analytics 本地 `analytics_screen_access` 表统一承载所有大屏权限。消除 analytics 数字 ID 与平台 Keycloak username 的 ID 错配根因，彻底清理旧 ACL 代码。

## 背景

Sprint-29 计划将大屏融入平台资产权限体系（`asset_ownership` + `asset_grant`），但实践中暴露了以下不可绕过的问题：

- 前端 `searchUsers` 返回 analytics 数字 ID（如 `42`），平台 grant 系统用 Keycloak username（`test230916`）匹配，导致授权后被授权方始终看不到大屏
- `platform_username` 字段依赖 `resolveOrProvision` 或 `refreshKnownUserAttributes`，已有 session 的用户在某些网络环境下无法正确回填
- Caffeine 缓存失效时序与平台 API 网络延迟叠加，导致跨机器行为不一致
- 涉密系统要求零默认权限、零角色硬编码（superuser 除外）

**本 sprint 决策**：Sprint-29 标记为 CANCELLED，改用本地表方案彻底替换权限查询路径。

## 权限模型

| permission | 查看大屏 | 编辑内容 | 管理授权 | 删除 |
|------------|:-------:|:-------:|:-------:|:---:|
| OWNER      | ✓       | ✓       | ✓       | ✓   |
| MANAGER    | ✓       |         | ✓       |     |
| VIEWER     | ✓       |         |         |     |

**superuser 例外**：`analytics_user.superuser = true` 的用户跳过权限表，直接拥有所有大屏完整权限。代码中不出现任何角色名称字符串硬判（superuser 字段由 `PlatformTrustedUserService` 通过 DB 字段维护）。

**grantee_type**：`USER`（analytics user.id 数字）或 `ROLE`（角色名字符串）。不支持 DEPT 级授权。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 后端本地权限表重构 | 7 | READY |
| F2 | 前端权限 UI 对齐 | 3 | READY |

**统计**: READY=10, IN_PROGRESS=0, DONE=0, BLOCKED=0

## 完成标准

- [ ] opadmin 在本机 Chrome 创建大屏，授权 test230916 VIEWER，test230916 登录后工作台和大屏管理列表均可见
- [ ] test230916 无法编辑大屏内容（canEdit=false）
- [ ] OWNER 可添加/移除授权（ScreenSharePanel 正常工作）
- [ ] superuser 用户不经权限表可见所有大屏
- [ ] 平台 `PlatformPermissionClient` 权限查询方法在大屏路径上零调用
- [ ] 旧 ACL 代码（Sprint-29 遗留 + 更早的 analytics ACL）完全删除，无残留

## 关联

- **取代**: Sprint-29（CANCELLED）
- **依赖**: Sprint-32（当前 IN_PROGRESS，不阻塞本 sprint）
