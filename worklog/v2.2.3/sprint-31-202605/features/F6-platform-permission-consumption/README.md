# F6: 消费层发布与 platform 权限统一

**优先级**: P0
**状态**: READY
**目标**: 稳住本版本权限边界：platform 成为唯一权限事实源，analytics 本地权限只读/应急 fallback。

## 任务

| Task | 内容 | 验收 |
|---|---|---|
| T01 | 迁移存量 `analytics_screen_access` 到 `asset_grant` | 批处理可重复执行，输出迁移数量和冲突报告 |
| T02 | local fallback 命中报表 | fallback 命中进入审计/告警/诊断页 |
| T03 | 默认关闭 local write | `analytics.local-iam.read-only=true` 生效，本地写只能 break-glass |
| T04 | 大屏列表权限回归 | PUBLIC 无授权可见；INTERNAL/SECRET/CONFIDENTIAL 必须授权或密级满足 |
| T05 | platform-webapp 只保留入口和消费态 | 权限判断和授权数据不在前端重复实现 |

## 代码关注点

- `AssetPermissionService`
- `AssetPermissionInternalResource`
- `ScreenPermissionService`
- `ScreenOwnershipService`
- `ScreenResource`
