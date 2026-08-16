# 交付基线探针结果（Gate G0）

**探针日期**：2026-08-16
**环境**：本地 v2.2.3 运行栈，入口 Host `bi.yuzhicloud.com`（通过 127.0.0.1 访问代理）
**结论**：PASS_WITH_GAPS；真实登录、受保护 API、现代 Chrome 菜单走查、Schema 和迁移 preview 已可用；隔离验收样本、Chrome 95、部门越权负向、构建和最终 E2E 仍是 G0/G4 缺口。

| # | 探针 | 结果 | 证据 | 阻断项 |
|---|---|---|---|---|
| P1 | 可运行实例 | PASS | dts-platform、platform-webapp、Airflow、dbt、OM、Kafka、PG 均 running；具备 healthcheck 的 platform/Airflow/Kafka/PG 均 healthy | - |
| P2 | 登录路径 | PASS | 2026-08-16 以 xiezm 从真实登录页进入受保护目标页；`/api/session/status`=200，`authenticated=true`，角色含 `ROLE_INST_DATA_OWNER` | - |
| P3 | Schema 状态 | PASS | 关键资产/投影/血缘/质量表均可查询；`20260810-02`、`20260811-01～03`、`20260814-01` Liquibase changeset 均为 EXECUTED；只读 normalization preview 连续两次 hash 一致 | - |
| P4 | 代表数据 | GAP | 有 367 个目录资产、43 个模型投影、53 条当前表血缘、119 条质量运行；字段血缘仍为 0，质量仍只覆盖 1 个 dataset 且全部 FAILED | F0/T01、F4/T01 |
| P5 | API 验收 harness | PASS | 登录会话内 `/api/catalog/assets-v2?page=0&size=1`、`/stats-projection`、模型 workbench 及质量页面请求均返回 200 | - |
| P6 | UI 验收 harness | PASS_WITH_GAPS | 本地 Chrome 150 从真实菜单/受保护路由打开模型、资产、元数据、血缘、质量；干净重载 console=0 error，关键 Network=200，已归档截图；Chrome 95 与四态集中验收未执行 | F6/T01 |
| P7 | Build/test 命令 | GAP | 本轮为文档立项，未运行 dts-platform tests 或 webapp build | 各实施 Task、F6/T01 |
| P8 | 外部依赖 | PASS_WITH_GAPS | OM version API=200、Airflow health=200、dbt-core=1.10.22、Kafka healthy；OM cache=0、字段血缘=0，集成结果仍未闭合 | F2/T01、F4/T01 |

## 阻断项与处置

| 阻断 | 影响 Feature | 处置 | Task |
|---|---|---|---|
| 未验证部门范围负向与写命令 | F6 | xiezm 登录和只读页面/API已通过；写命令及部门越权在最终隔离样本上验证 | F6/T01 |
| 无真实字段血缘样本 | F4/F6 | 准备一个含列级映射的 dbt manifest 和实际物化链 | F0/T01、F4/T01 |
| 全量 producer/evidence 自动解析率未知 | F1/T02 | 当前 normalization 子集为 3/3 自动、0 歧义；367 个资产的生产观察来源仍须由 F1/T01 接入并对账 | F1/T01、F1/T02 |
| Chrome 95 未执行 | F5/F6 | 代码全部完成后集中运行一次 Chrome95 smoke | F6/T01 |

## 2026-08-16 F0 实施证据

- 详细过程与结果：[`evidence/20260816-f0-browser-runtime-baseline.md`](evidence/20260816-f0-browser-runtime-baseline.md)。
- 真实登录后的资产概览截图：[`evidence/20260816-xiezm-asset-overview.png`](evidence/20260816-xiezm-asset-overview.png)。
- normalization preview：`totalPending=3`、`rowCount=3`、`truncated=false`，连续两次 `previewHash=f336327365a4ffebca6357fb20573c3f0030a500f5b5842d225dcaeb7f446c52`；三条均 `automatic=true` 且 `issueCode=null`，未执行 apply。
- 同一账号默认页面可见统计不一致：资产概览为 200、资产目录为 286、数据库全量为 367。该结果与既知 overview 200 行扫描上限一致，但最终口径收敛仍由 F1/F5 通过契约测试确认。

## 本 Sprint 验收路径约定

- 后端：dts-platform 聚焦单测、Testcontainers IT、真实受保护 API。
- 数据：对账 SQL、迁移 preview/apply/rollback、outbox/serving/quality/lineage 断言。
- UI：xiezm 从真实菜单进入模型工作台、资产概览/目录/详情、元数据、血缘和质量页面；Chrome 95 验证四态、控制台和网络。
- 证据：只写入 `it/README.md` 指定 IT 槽位；未执行项保持 PENDING，不放占位截图。
