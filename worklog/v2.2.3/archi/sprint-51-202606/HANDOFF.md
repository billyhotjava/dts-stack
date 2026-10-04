# Sprint-51 交接简报（冷启动用）

> 给新会话冷启动：读完本文即可直接实施 Sprint-51，无需上一会话上下文。

## 一句话

在 `/opt/prod/s10/v2.2.3`（分支 **v2.2.3**）实施 `worklog/v2.2.3/sprint-51-202606`：把 v2.2.4 的「字典/血缘/治理/元数据/状态编排」横切职责域思想，落到 **v2.2.3 现有页面**上做收敛/复用/串联——**不新增页面、不建 `/v2` 命名空间、后端 API 只补真实缺口**。

## 为什么是这条路（背景结论）

- 曾在 **v2.2.4 分支**做过"全新 `/v2` IA 整体回植"（`src/v2/`，已推 origin/v2.2.4）——**改动太大**，且离线鲲鹏现场升级风险高（详见 [[elt-redesign-replant-versioning]] 评估：硬隔离=改库+重建 ARM 镜像+重型 `--restore-db` 回滚）。
- 故弃用大改路线，改为 Sprint-51 这条**精简线**：现有页面增强，几乎不动后端、不动数据模型，升级=换前端，风险最低。
- v2.2.4 分支**保留为参考**：其 `worklog/v2.2.4/replant/wave2-api-mapping.md` 已侦察出各横切域的**真实端点 + 字段映射 + 防御式适配器**，接 API 时直接借鉴（端点都已核实存在）。

## 重构边界（硬约束，编码前必读）

- 页面来源 = v2.2.3 现有页面为第一事实源；**默认不新增菜单/页面/`/v2`**。
- 实施顺序：先页面矩阵与交互闭环，再抽服务/补 API。
- 后端：现有 endpoint 能承载就不补；缺口必须从**页面验收项倒推**并登记。
- 兼容：保持 Sprint-45~50 的路由/工作台/**Chrome 95** 约束（禁 oklch/`:has`/容器查询/subgrid）。
- 验收：**source-contract + 页面 smoke + API 缺口登记**必须同步。

## 关键文件

| 用途 | 路径 |
|---|---|
| Sprint 总览 | `README.md` |
| 横切域→现有页面+真实端点 矩阵 | `assets/existing-page-cross-domain-matrix.md` |
| API 缺口登记（TO_VERIFY） | `assets/api-gap-register.md` |
| 集成测试计划 | `it/README.md` |
| Feature/Task | `features/F0..F5/` |
| v2.2.4 真实端点/字段映射（参考） | `git show v2.2.4:worklog/v2.2.4/replant/wave2-api-mapping.md` |

## Feature 顺序与状态

```
F0 现有页面事实源与重构边界(P0, 编码前 Gate)
 → F1 字典域现有页面收敛(P0, IN_PROGRESS)
 → F2 血缘与元数据详情闭环(P0)
 → F3 治理域跨页面复用(P0)
 → F4 工作台主链路串联(P1)
 → F5 API 缺口与验收证据(P0, 贯穿全程)
```

## 当前工作树状态（v2.2.3，均未提交）

- **F1-T01 已实现、tsc 零错误**：
  - 新增 `source/dts-platform-webapp/src/api/services/dictionaryService.ts`（`/platform/dict/system-types`，防御式取列表）。
  - 改 `src/pages/foundation/DataSourceFormModal.tsx`（系统类型下拉接字典 + 内置 `TYPE_OPTIONS` 兜底 + 空态提示，优雅降级）。
  - 改 `src/pages/foundation/DataSourcesPage.source-contract.test.ts`、`worklog/v2.2.3/sprint-queue.md`。
- ⚠️ `services/dts-airflow/dags/addax-env-runner.jar` 也在 modified——与本 sprint 无关，**提交时勿夹带**。

## 第一步动作（建议）

1. 先提交已完成的 F1-T01（**只 add 前端 4 文件 + sprint-51 目录 + HANDOFF**，不要 `addax-env-runner.jar`）：
   ```
   git add source/dts-platform-webapp/src/api/services/dictionaryService.ts \
           source/dts-platform-webapp/src/pages/foundation/DataSourceFormModal.tsx \
           source/dts-platform-webapp/src/pages/foundation/DataSourcesPage.source-contract.test.ts \
           worklog/v2.2.3/sprint-queue.md worklog/v2.2.3/sprint-51-202606
   git commit -m "feat(sprint-51/F1-T01): 数据源表单系统类型接字典(/platform/dict/system-types)+兜底"
   ```
2. 走 F0 Gate（确认页面矩阵、无新增页面），再按 F1→F5 实施。
3. 每项：现网 `pnpm exec tsc --noEmit` + 生产构建 + source-contract/Chrome95 smoke。

## 提交约定

- 分支：v2.2.3（待 v2.3.0 任务确定后再议合并）。
- commit 前缀：`feat(sprint-51/F{n}-T{nn}): ...` / `fix(...)`。
- 任何后端新增 → 先写进 `assets/api-gap-register.md`（页面/字段/失败态/source-contract）。
