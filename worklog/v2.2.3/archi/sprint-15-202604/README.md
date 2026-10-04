# Sprint-15: 平台工作台 · 领导视角重构

**时间**: 2026-04
**状态**: READY
**类型**: Implementation（UI 重构 + 后端聚合端点新增 + 遗弃功能清理）
**目标**: 把 `dts-platform-webapp` 工作台首页从"数据治理 / 资产沉淀"通用视角重构为**领导视角概览**，只保留**报表**与**数据资产**两块，按登录人角色（员工 / 部门领导 / 所领导）自适应默认范围；同时彻底清理已失联的收藏功能。

## 背景

### 现状问题

1. 现有 KPI（我创建的资产 / 今日新增资产 / 待办 / 质量检查）对领导无感。
2. 主视区是"资产增长趋势"折线图——领导不关心数条走势。
3. 有"我的收藏"前后端代码但菜单早已移除，属于孤岛；后端 `PortalUserFavorite` 表亦滞留。
4. 没有**业务域**和**部门**两个筛选维度，领导无法按口径切片。
5. 审批块混在一起，语义不清——本次**不重新定义审批工作流**，直接移除。

### 本 Sprint 解决什么

1. 上线领导视角工作台，三层角色差异化呈现。
2. 引入"部门 × 业务域 × 时间"三维过滤，整屏联动。
3. 业务域做**软依赖**于"主题域管理"，API 失败整屏仍可用。
4. 彻底删除收藏相关前后端代码 + `portal_user_favorite` 表。

**设计文档**: `docs/superpowers/specs/2026-04-24-platform-workbench-leader-overview-design.md`（commit `90147dcd9`）

## 约束与非目标

### 硬约束

- 业务域对 `CatalogDomain` 的依赖必须是软依赖：API 失败时筛选器 + 色块矩阵静默隐藏，整页仍以"部门 × 时间"工作，不允许 toast / 全局错误态。
- 后端新增聚合端点 `GET /workbench/leader-overview` 必须做 `scope` 参数角色权限降级保护，避免越权。
- 收藏相关数据库迁移前必须有 `portal_user_favorite` 全表 dump 作回滚物料。

### 非目标

- 不重新定义审批工作流；不渲染任何"审批 / 我的申请"块。
- 不新建业务域字典，沿用既有 `CatalogDomain`。
- 不改造报表中心 / 资产目录子页面，工作台仅聚合 TOP N。
- 不在工作台上做全文搜索。

## Feature 列表

| ID | Feature | Task 数 | 状态 |
|----|---------|---------|------|
| F1 | 后端聚合端点与业务域过滤 | 7 | READY |
| F2 | 收藏功能彻底清理 | 5 | READY |
| F3 | 前端角色与筛选器 | 5 | READY |
| F4 | 前端 KPI 与业务域矩阵 | 4 | READY |
| F5 | 前端报表块与核心资产块 | 5 | READY |
| F6 | 埋点与 E2E | 4 | READY |

**合计 30 个 task。**

## 依赖图

```
F2（收藏清理）─┐
                ├─可并行
F1（后端）──┬──────────────────┐
              │                    ↓
              └──→ F3（筛选器）──┬→ F4（KPI/矩阵）
                                  └→ F5（报表/资产/壳）
                                       ↓
                                    F6（埋点/E2E）
```

执行顺序建议：
1. F1 与 F2 先并行启动（后端聚合接口 + 清理旧遗产）。
2. F3 完成后再开始 F4、F5（都依赖过滤器与 hook 的产物）。
3. F6 放在 F1-F5 基本完成后执行；E2E 依赖真实后端接口。

## 完成标准

- [ ] `GET /workbench/leader-overview` 三种 scope 下均返回正确聚合数据，含 KPI / TOP 报表 / TOP 资产 /（所领导）业务域矩阵。
- [ ] 前端 `LeaderOverviewPage` 按 `useWorkbenchRole` 输出的角色正确渲染（员工 3 KPI / 部门领导 3 KPI / 所领导 4 KPI + 色块矩阵）。
- [ ] `CatalogDomain` API 失败时业务域筛选器与色块矩阵同时隐藏，整页可用。
- [ ] 所领导切部门 / 切业务域 / 切时间段 → 整屏联动刷新，色块矩阵同步更新。
- [ ] 后端 `scope=ALL` 非所领导访问时强制降级为 `scope=DEPT`；`scope=DEPT` 非领导访问时强制降级为 `scope=MINE`。
- [ ] `portal_user_favorite` 表通过 Liquibase changeset 删除；前后端无残留引用；`/api/workbench/favorites/*` 端点返回 404。
- [ ] 审计埋点 `WORKBENCH_OVERVIEW_VIEW` / `WORKBENCH_FILTER_CHANGE` / `WORKBENCH_DOMAIN_DRILL` 正常入库。
- [ ] Playwright E2E：所领导登录 → 切业务域 → 切部门 → 打开一条 TOP 报表 → 回归工作台，全链无错误。
- [ ] 单元测试覆盖：`useWorkbenchRole` 所有角色识别分支、软依赖降级分支；`WorkbenchLeaderOverviewService` KPI / TOP 聚合核心路径。
- [ ] `it/` 目录内有真实 IT 证据（接口 curl / 截图 / E2E trace）。

## 验证入口

- 后端接口：`GET http://<host>:<port>/api/workbench/leader-overview?scope=ALL&timeRange=MONTH`
- 前端页面：登录后访问 `/workbench`
- 回滚物料：`portal_user_favorite` 全表 dump（路径见 F2/T01）

## 回滚计划

- **前端**：新文件独立，删除操作集中在 `workbench/index.tsx` 与 `workbenchService.ts`；一次 revert 即恢复。
- **后端聚合端点**：`WorkbenchLeaderOverviewService` + Resource 方法独立，revert 即可。
- **收藏删除**：涉及 Liquibase，回滚需要逆向 changeset（建表 + 从 dump 恢复数据）；上线前必须有 dump。

## 开放问题（规划阶段决策结果）

设计稿"开放问题（进入规划阶段再定）"四条，本 sprint 决策如下：

1. **核心资产是否带"访问量"副排序** → **不带**。第一期只按密级 + 更新时间，实现简单，后续按需加。
2. **色块矩阵颜色映射** → **同色系浓淡**（按当前过滤下访问量深浅）。避免业务域固定语义色随时间漂移。
3. **未配置 `bizDomain` 的报表如何归类** → 归到"其他"桶，色块矩阵最后一格展示。
4. **未来订阅关系落地后是否平替"我常用的报表"** → 是，但不在本 sprint 做。当前口径"近 30 天访问过"作为第一期实现。
