# 大屏权限重构设计 — 融入平台统一资产权限体系

**日期**: 2026-03-30
**Sprint**: Sprint-29
**状态**: APPROVED

## 1. 背景

当前大屏分享使用 analytics 内部的 `analytics_screen_acl` 表，与平台统一资产权限体系存在双重权限冲突：
- `PlatformPermissionFilter`（Servlet Filter）调用平台 API 检查权限，不认 analytics ACL
- `ScreenAclService`（Controller 层）查询 analytics ACL 表，不走平台权限
- 结果：分享了大屏但被分享用户看不到

## 2. 方案

删除 analytics 独立 ACL 系统，大屏作为 `asset_type=SCREEN` 完全融入平台统一资产权限（`asset_ownership` + `asset_grant`）。

## 3. 权限模型

### 权限等级

| 权限 | 说明 |
|------|------|
| READ | 查看已发布大屏 |
| EDIT | 编辑、保存、发布大屏 |
| OWNER | 创建者自动拥有，EDIT + 删除 + 分发权限 |

### 角色基线权限（不需要显式授权）

| 角色 | 基线权限 | 范围 |
|------|---------|------|
| OP_ADMIN | OWNER 级 | 所有大屏 |
| INST_DATA_OWNER | EDIT | 所有大屏 |
| INST_LEADER | EDIT | 所有大屏 |
| DEPT_DATA_OWNER | EDIT | 本部门大屏 |
| DEPT_LEADER | EDIT | 本部门大屏 |
| EMPLOYEE | 无基线 | 需要显式授权 |

### 显式授权（通过分享面板）

分发时可选 READ 或 EDIT，支持三种 grantee：USER、DEPT、ROLE。

### 权限判定优先级（取最高权限）

1. superuser → OWNER 级
2. 创建者 → OWNER
3. 密级校验 → 不通过直接拒绝（当前预留，不实现）
4. 角色基线 → 按上表
5. 显式授权 → asset_grant 中的记录
6. 默认拒绝

### 谁可以分发权限

OWNER（创建者）和 OP_ADMIN。

## 4. 密级

- 发布时手动选择（公开/内部/秘密/机密）
- 当前只存储 `classification` 字段，不做查看时校验
- 后续与人员密级（`X-DTS-Personnel-Level`）联动

## 5. 数据模型变更

### 删除

- `analytics_screen_acl` 表（清空后删除）
- `ScreenAclService.java`、`AnalyticsScreenAclRepository.java`、`AnalyticsScreenAcl.java`
- `PlatformPermissionFilter.java` 中 SCREEN_PATTERN
- `ScreenSharePanel.tsx`

### 复用平台表（不新建）

| 表 | 用途 |
|---|------|
| `asset_ownership` | 大屏归属（asset_type=SCREEN） |
| `asset_grant` | 大屏授权（grantee_type: USER/ROLE/DEPT, permission: READ/EDIT） |
| `asset_permission_audit` | 审计日志（预留 oa_reference 字段） |

### analytics_screen 新增字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `classification` | varchar(32) | 密级，发布时设置 |
| `owner_dept_code` | varchar(64) | 创建者部门代码 |

## 6. API 变更

| 操作 | 原来 | 改造后 |
|------|------|--------|
| 创建大屏 | 写 analytics_screen_acl OWNER | 调用 platform /api/asset-ownership |
| 发布大屏 | 无密级 | 新增 classification 参数 |
| 分享大屏 | PUT /api/screens/{id}/acl | PUT /api/screens/{id}/grants → platform /api/asset-grants |
| 查看授权 | GET /api/screens/{id}/acl | GET /api/screens/{id}/grants |
| 大屏列表 | 全量 + ACL 过滤 | platform accessible-ids 获取可见 ID |
| 单个大屏 | PlatformPermissionFilter 拦截 | Controller 内 ScreenPermissionService 检查 |
| 删除大屏 | ACL 检查 OWNER | ScreenPermissionService 检查 isOwner |

## 7. 前端变更

### 分享面板（ScreenSharePanel → ScreenGrantPanel）

- 拥有者信息展示
- 当前授权列表（支持修改权限/撤销）
- 添加授权（类型切换 USER/DEPT/ROLE + 搜索 + 权限选择）
- 预留审批区域

### 发布对话框

新增密级选择下拉（公开/内部/秘密/机密）。

## 8. 预留接口

- 密级校验：`ScreenPermissionService.checkClassification()` — 当前返回 true
- 数据源权限：`ScreenPermissionService.checkDataSourceAccess()` — 当前返回 true
- 审批流：`asset_grant.grant_reason` 字段预留 OA 单号

## 9. 迁移策略

清空 `analytics_screen_acl` 表数据后删除表。不做数据迁移。

## 10. 实施顺序

Phase 1（后端基础）→ Phase 2（后端端点）→ Phase 3（前端）→ Phase 4（清理）

详见 Sprint-29 Task 列表。
