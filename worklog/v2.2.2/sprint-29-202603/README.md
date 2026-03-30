# Sprint-29: 大屏权限重构 — 融入平台统一资产权限体系

**时间**: 2026-03
**状态**: READY
**目标**: 删除 analytics 独立 ACL 系统，大屏作为 asset_type=SCREEN 融入平台统一资产权限（asset_ownership + asset_grant），支持密级设置与 USER/DEPT/ROLE 三种授权方式

## 背景

当前大屏分享使用 analytics 内部的 `analytics_screen_acl` 表，与平台统一资产权限体系（`asset_ownership` + `asset_grant`）存在双重权限冲突：
- `PlatformPermissionFilter` 调用平台 API 检查权限，不认 analytics ACL
- `ScreenAclService` 查询 analytics ACL 表，不走平台权限
- 导致分享了大屏但被分享用户看不到

重构目标：统一到平台权限体系，删除 analytics ACL。

## 权限模型

| 权限 | 说明 |
|------|------|
| READ | 查看已发布大屏 |
| EDIT | 编辑、保存、发布大屏 |
| OWNER | 创建者自动拥有，EDIT + 删除 + 分发权限 |

### 角色基线权限

| 角色 | 基线权限 | 范围 |
|------|---------|------|
| OP_ADMIN | OWNER 级 | 所有大屏 |
| INST_DATA_OWNER | EDIT | 所有大屏 |
| INST_LEADER | EDIT | 所有大屏 |
| DEPT_DATA_OWNER | EDIT | 本部门大屏 |
| DEPT_LEADER | EDIT | 本部门大屏 |
| EMPLOYEE | 无基线 | 需要显式授权 |

### 密级

- 发布时手动选择（公开/内部/秘密/机密）
- 当前预留字段，不做查看时校验
- 后续与人员密级联动

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 大屏权限重构 | 13 | READY |

## 完成标准
- [ ] analytics_screen_acl 表已删除
- [ ] 大屏创建自动注册 platform asset_ownership
- [ ] 分享面板通过 platform asset_grant 授权（USER/DEPT/ROLE）
- [ ] 大屏列表按 platform 权限过滤
- [ ] 发布时可设置密级
- [ ] OP_ADMIN/数据管理员/领导角色有正确的基线权限
- [ ] 旧代码（ScreenAclService、PlatformPermissionFilter SCREEN 逻辑）已清理
