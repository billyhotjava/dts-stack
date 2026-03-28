# T01: Screen ACL 迁移脚本

**优先级**: P0
**状态**: READY
**依赖**: F1/T01

## 目标

将 analytics 数据库中现有的 Screen ACL 数据迁移到 platform 的 asset_grant 表。

## 技术设计

### 迁移映射

```
analytics_screen_acl → asset_grant:
  asset_type      = 'SCREEN'
  asset_id        = screen_acl.screen_id
  grantee_type    = screen_acl.subject_type (USER/ROLE)
  grantee_id      = screen_acl.subject_id
  permission      = screen_acl.permission (READ/EDIT/PUBLISH→EDIT/MANAGE)
  valid_from      = NULL
  valid_to        = NULL (永久)
  granted_by      = 'MIGRATION'
  grant_reason    = 'Migrated from analytics_screen_acl'
```

### 权限映射

| Screen ACL | asset_grant |
|-----------|-------------|
| READ | READ |
| EDIT | EDIT |
| PUBLISH | EDIT |
| MANAGE | MANAGE |

### 执行方式

Liquibase changelog 中的 SQL 迁移脚本，跨库查询 analytics DB。

## 影响范围

- `source/dts-platform/src/main/resources/config/liquibase/changelog/` — 迁移脚本

## 验证

- [ ] 迁移后记录数与原表一致
- [ ] 权限映射正确
- [ ] 迁移脚本幂等（可重复执行）

## 完成标准

- [ ] 全部 Screen ACL 数据迁移到 asset_grant
