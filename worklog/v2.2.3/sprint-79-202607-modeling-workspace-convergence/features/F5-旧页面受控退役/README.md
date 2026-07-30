# F5：旧页面受控退役

**优先级**：P0  
**状态**：PASS_WITH_GAPS（T01 源码收敛完成；T02 受两版本零访问和客户画像门禁阻塞）

## 目标

在新工作台功能等价、访问观测和回滚条件满足后，删除重复页面与旧兼容路由。

## 契约

| 类别 | 策略 | 门禁 |
|---|---|---|
| canonical 旧页 | 抽面板后 route redirect | 参数保真、两版本稳定 |
| 8 条 compatibility route | 观测后删除 | 所有部署两版本零访问、旧对象已映射 |
| legacy API/表 | 只形成 proposal | 客户 dry-run、备份、审批、独立 migration |

## UI/UX

旧书签在兼容期无感带参数进入新工作台；无法映射时给 recovery。正式退役后的不存在路径按统一 404，不保留第二套旧 UI。

## Tasks

| ID | Task | 状态 | 依赖 |
|---|---|---|---|
| T01 | canonical 页面转面板并收敛路由 | PASS_WITH_GAPS | F1～F4 |
| T02 | 移除兼容路由并形成旧表退役提案 | BLOCKED_BY_OBSERVATION | T01、F0/T02、两版本观测 |

## Definition of Ready

- [ ] 新工作台功能等价 IT 全部 PASS。
- [ ] 每条路由有 90 天/两版本访问证据。
- [ ] 回滚发布包和角色/menu binding 核对完成。

## 完成标准

- [ ] 重复页源码删除，旧深链迁移或明确 404。
- [ ] 菜单、角色、收藏、帮助链接、测试无悬挂引用。
- [ ] 旧表未在无审批情况下物理删除。

## 当前决策

- canonical plans/dimensions/models/metric deep link、菜单和帮助入口已收敛到 `/modeling/workbench`。
- dts-admin migration 只软删除旧菜单，并以 `sprint79-menu-convergence` 标记保障定向回滚。
- 8 条 compatibility route 继续保留；没有两版本零访问、unresolved=0 和客户环境画像前，不删除 route helper 或 legacy 表。
