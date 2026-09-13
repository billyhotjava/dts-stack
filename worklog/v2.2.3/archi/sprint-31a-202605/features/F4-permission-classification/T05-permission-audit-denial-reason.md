# T05: 权限审计和拒绝原因

**优先级**: P1
**状态**: DONE
**依赖**: T01-T04

## 目标

为资产访问、拒绝、授权、密级变更和 fallback 命中提供统一审计。

## 技术设计

- `/api/internal/asset-permission/check` 写入 `CHECK_ALLOW` / `CHECK_DENY` 权限审计。
- 审计 detail 记录 action、assetKey、decision、reason、requiredPermission、classificationDecision、grantSource。
- PermissionAuditPage 增加 `CHECK_ALLOW` / `CHECK_DENY` 过滤。
- 运维页可按 action、操作者、目标用户、OA 单号、时间范围查询。

## 影响范围

- AuditService
- PlatformEventOutbox
- PermissionAuditPage

## 验证

- [x] 拒绝访问可追踪。
- [x] 授权变更能查到操作者和原因。
- [x] 权限检查结果能按 `CHECK_ALLOW` / `CHECK_DENY` 查询。

## 完成标准

- [x] 权限问题可运营、可排查。

## 交付物

- `AssetPermissionAuditService.recordDecision`
- `AssetPermissionInternalResource` 权限检查审计写入
- `PermissionAuditPage` 检查结果过滤
- `worklog/v2.2.3/sprint-31a-202605/assets/permission-audit-denial-reason.md`
