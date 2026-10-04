# F1: 后端本地权限表重构

**状态**: READY
**优先级**: P0

## 目标

用本地 `analytics_screen_access` 表完整替换 `PlatformPermissionClient` 的权限查询路径，同时彻底删除所有旧 ACL 代码。

## Task 列表

| Task | 说明 | 状态 |
|------|------|------|
| T01 | Liquibase migration — analytics_screen_access 表 | READY |
| T02 | JPA Entity + Repository | READY |
| T03 | ScreenPermissionService 重写 | READY |
| T04 | ScreenOwnershipService 改造 | READY |
| T05 | ScreenResource 端点改造 | READY |
| T06 | 存量数据迁移接口 | READY |
| T07 | 旧代码删除与清理 | READY |
