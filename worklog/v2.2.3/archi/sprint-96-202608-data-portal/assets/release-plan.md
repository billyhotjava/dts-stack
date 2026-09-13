# 发布安全计划（Gate G3）

**变更类型**：向后兼容 API 扩展 + 菜单数据原位迁移 + 新消费页面
**风险等级**：中（涉及发布态/权限消费边界与全局菜单入口）
**当前结论**：READY_WITH_GAP——代码可发布且迁移有保护性 rollback；尚未在隔离 PostgreSQL 实例实际演练 apply/rollback。

## 1. 迁移策略

| 阶段 | 内容 | 本次是否包含 | rollback |
|---|---|---|---|
| Expand | `GET /api/screens` 新增可选 `publishedOnly=false`；新增门户路由 | 是 | 回滚应用镜像即可，旧调用不传参不变 |
| Migrate | 原位更新 `sys.nav.portal.biScreens` 的 metadata.title/externalLink 与 seed hash | 是 | changelog 从 system_config snapshot 恢复 metadata 与旧 seed hash |
| Contract | 删除旧 API、旧管理路由、菜单行或 visibility | 否 | 不适用 |

迁移不建表、不加列、不回填大屏、不改菜单 ID/parent/sort/visibility。目标菜单不是恰好一条时 HALT；snapshot 已存在时 HALT；rollback 检测菜单在迁移后是否被再次修改，发生漂移则拒绝盲目覆盖。

## 2. 兼容性

| 消费方 | 证据 | 影响 | 处置 |
|---|---|---|---|
| `/bi/screens` 管理页及其他 `listScreens()` 调用 | `analyticsApi.ts` 的参数可选且默认不发送 | 无语义变化 | 保留旧管理路由和默认 draft preview |
| 数据门户 | `/bi/portal/:screenId` | 新增 | 只发送 `publishedOnly=true`，详情强制 published/no-fallback |
| 大屏预览/设计器 | `ScreenPreviewPage` | 默认仍读取 draft | 仅显式 `mode=published` 时 fail-closed；`embed=1` 只隐藏预览 FAB |
| dts-admin 门户菜单 | `sys.nav.portal.biScreens` | 客户可见标题/入口变化 | 原 menu ID 原位更新，角色绑定不动；管理入口移入门户页 |
| `BiReportLink` 镜像消费者 | Sprint ADR-96-01 | 不切换 owner | 本 Sprint 不读取或改写该小时级镜像 |

## 3. 回填策略

- 无大屏数据回填；未发布大屏不会被自动发布或自动归类。
- 无法匹配治理主题域的大屏只在运行时进入“未归类”，不改写 `domain_id`。
- 菜单仅有一条受控原位迁移，不分批。

## 4. 部署与回滚

### 部署顺序

1. 构建并部署 `dts-analytics`，确认旧 `/api/screens` 与新 `publishedOnly=true` 均正常。
2. 构建并部署 `dts-platform-webapp`，直接访问 `/bi/portal` 验证空态/填充态和发布详情。
3. 最后部署 `dts-admin` 并执行 Liquibase，使全局菜单入口切到 `/bi/portal`。
4. 验证菜单 ID 和 `portal_menu_visibility` 行数未变化，再开放验收。

### 回滚

1. 先将 `dts-admin` 回滚到前一镜像并执行 Liquibase 对应 changeSet rollback；确认 snapshot 恢复旧 metadata/seed hash。
2. 将 `dts-platform-webapp` 回滚到前一镜像；旧 `/bi/screens` 仍可管理和预览。
3. 如需继续回滚，将 `dts-analytics` 回滚到前一镜像；本 Sprint 没有 schema/data contract，未产生新格式数据。

**演练结果**：未演练。当前共享实例不是隔离验收库，本次未获授权执行数据库回滚，因此 G3 保留 GAP。
**不可逆部分**：无；本 Sprint 不发布客户草稿、不删除数据、不发外部通知。

## 5. 影响面与停止条件

- 影响模块：`dts-analytics`、`dts-platform-webapp`、`dts-admin` 菜单迁移。
- GitNexus 预编辑影响均为 LOW；索引重建后仍需在提交前运行 staged detect。
- 任一条件触发即停止：目标菜单数不等于 1、发布目录出现 `publishedVersionNo=null`、published detail 回退 draft、菜单 visibility 数量变化、门户请求出现 401/403/5xx 异常峰值。
