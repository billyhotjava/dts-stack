# Metabase 仿造功能渐进退役路线

**定稿日期**：2026-08-17
**决策**：Metabase 形态功能**不在 Sprint-94 退役**。退役拆成 5 个阶段，每阶段一个门禁，跨多个 Sprint 执行；本 Sprint 只完成 S0。
**理由**：32 条 `bi/*` 路由中 28 条菜单不可见但调用量 UNKNOWN（见 `route-inventory.md`）。在调用量未知时做重定向或迁移 apply，等于用生产用户验证假设。

## 阶段总览

| 阶段 | 名称 | 承接 Sprint | 进入门禁 | 允许的动作 | 明确禁止 |
|---|---|---|---|---|---|
| **S0** | 盘点、冻结与建立观测口 | **Sprint-94** | 无 | 全量静态 inventory；新建产物入口收敛到 canonical；旧 surface 登记 source/window/status，缺历史则建立观测起点 | 重定向、删除、迁移 apply、feature flag 关闭、把 UNKNOWN 写成 0 |
| **S1** | 只读兼容准备 | Sprint-95+ | S0 观测口连续 ≥14 天可读，旧写调用方与回切责任明确 | 旧写入口挂 feature flag（**默认仍开**）；旧读全保留；UI 标"兼容只读" | 关闭旧写、删除路由 |
| **S2** | 兼容重定向 | Sprint-96+ | S1 期间旧写调用趋零且有 pilot 部门验证 | 按 `LegacyDataModelingRedirect` 样板做重定向；旧写默认关闭、可紧急回切 | 删除路由 handler、DROP 表 |
| **S3** | 迁移 apply | Sprint-97+ | S2 稳定 ≥30 天；三分类 fixture 齐备；备份/恢复演练通过 | preview/apply/replay/rollback 分批迁移；大屏复用已发布分析 | DROP 任何表/列 |
| **S4** | 物理退役 | 独立变更评审 | 连续 30 天零调用 + 全量 inventory + backup/restore proof | DROP 表/列/route handler | 无门禁的批量清理 |

**当前位置：S0（Sprint-94）。**

## S0 的完成定义（Sprint-94 范围）

- [ ] `route-inventory.md` 32 行全部有组件宿主、菜单可见性、处置和阶段归属。
- [ ] 后端旧写面（`/api/card` write、MBQL execute、public/embed）盘点完成，与前端路由表分开记录。
- [ ] 每个旧 surface 都记录 `source + retention/window + value/status`；没有历史来源时保留 UNKNOWN、记录 owner 和观测起始时间。
- [ ] 新建分析产物只从 canonical 入口产生（ADR-94-13）；旧入口仍可用但不再是新建主线。
- [ ] 无任何重定向、删除、flag 关闭动作落地。

## 从原 F5 移出的内容及去向

Sprint-94 原 F5「大屏复用与 Metabase 退役」整体顺延，拆分如下：

| 原 Task | 内容 | 去向 |
|---|---|---|
| F5/T01 | 大屏复用已发布 Analysis revision | **S3**（依赖 F2/F4 的 published revision，本就排在最后） |
| F5/T02 迁移通道 | preview/apply/rollback 迁移 API 与批次表 | **S3** |
| F5/T02 路由收敛 | collections/models/trash 重定向 | **S2** |
| F5/T02 旧写 flag | `DTS_ANALYTICS_LEGACY_CARD_WRITE_ENABLED` 关闭 | **S1→S2** |
| 原 F5/T02 盘点部分 | 静态 inventory、后端旧写 surface 与动态菜单 | **S0 = 本 Sprint F0/T02** |
| 原 F5/T02 观测部分 | 角色、容量、调用来源/窗口与观测起点 | **S0 = 本 Sprint F0/T03** |

原 F5 已从活跃 Feature 移除；`ScreenAnalysisSource`、迁移三分类、批次表 schema 与 API 语义保存在 `deferred-scope-handoff.md`。后续 Sprint 将其作为输入，但仍须重新过当期 G0/G1、fresh impact 和阶段门禁。

## 不变的硬约束

无论走到哪个阶段，以下约束贯穿全程：

1. 旧读路径在 S4 之前**永不中断**。
2. 不可证明等价的 MBQL 只读保留，任何阶段都不做近似转换。
3. 每个阶段的关闭动作都必须可在一轮发布内回切。
4. "菜单不可见"永远不能作为"无人使用"的证据。
5. 阶段不可跳跃：S2 不能在 S1 未取得趋零证据时启动。
