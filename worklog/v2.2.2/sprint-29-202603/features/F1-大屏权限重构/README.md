# F1: 大屏权限重构

**优先级**: P0
**状态**: READY

## 目标
删除 analytics 独立 ACL，大屏权限完全融入平台统一资产权限体系（asset_ownership + asset_grant）

## Task 列表

| ID | Task | 优先级 | 状态 | 依赖 | Phase |
|----|------|--------|------|------|-------|
| T01 | ScreenPermissionService 封装 platform 权限调用 | P0 | READY | - | Phase 1 |
| T02 | ScreenOwnershipService 管理 ownership 生命周期 | P0 | READY | T01 | Phase 1 |
| T03 | Liquibase migration（加字段、删旧表） | P0 | READY | - | Phase 1 |
| T04 | ScreenResource 改造（替换旧 ACL 调用） | P0 | READY | T01, T02 | Phase 1 |
| T05 | grants 端点（GET/PUT/DELETE） | P0 | READY | T04 | Phase 2 |
| T06 | 发布端点加 classification 参数 | P1 | READY | T03 | Phase 2 |
| T07 | 列表/详情端点用 platform 权限过滤 | P0 | READY | T01 | Phase 2 |
| T08 | ScreenGrantPanel 前端（USER/DEPT/ROLE 授权） | P0 | READY | T05 | Phase 3 |
| T09 | ScreenHeader 改造（发布对话框加密级） | P1 | READY | T06 | Phase 3 |
| T10 | 删除旧 ScreenSharePanel 和相关引用 | P1 | READY | T08 | Phase 3 |
| T11 | 删除 ScreenAclService/Repository/Domain | P1 | READY | T04 | Phase 4 |
| T12 | PlatformPermissionFilter 移除 SCREEN 逻辑 | P1 | READY | T07 | Phase 4 |
| T13 | 端到端测试验证 | P0 | READY | T08, T09, T11, T12 | Phase 4 |

## 依赖图

```
T03 ──────────────────→ T06 → T09
                                    ↘
T01 → T02 → T04 → T05 → T08 → T10 → T13
  ↘              ↗    ↘         ↗
   → T07 ────────      T11 ──→
                       T12 ──→
```

## 完成标准
- [ ] 所有 Task 状态为 DONE
- [ ] 旧 ACL 代码和表完全清理
- [ ] opadmin 可创建、发布、分享大屏给任意用户/部门/角色
- [ ] 被分享用户可在大屏列表看到并查看大屏
- [ ] 角色基线权限生效（数据管理员/领导自动有编辑权限）
